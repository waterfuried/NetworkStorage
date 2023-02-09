package netty.handler;

import prefs.*;
import authService.*;

import cloud.*;
import cloud.request.*;
import cloud.response.*;
import io.netty.channel.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static prefs.Prefs.*;
import static prefs.Prefs.ErrorCode.*;
/*
    если обработчик запросов клиента находится на серверной стороне (где он и должен быть),
    операции в пользовательской папке можно писать не в коде ответа на запрос, а прямо здесь,
    но тогда и весь код будет здесь, а ответы на запрос будут почти пустышками (обертками),
    кроме того, если при выполнении запросов возникают ошибки, о которых желательно сообщать
    в ответе, их как передавать в класс ответа на запрос? как аргумент его конструктора?
    это выглядит несколько странно, поэтому формирование ответов стоит оставить в реализации
    их классов, сам же обработчик будет выполнять лишь функции получения клиентских запросов,
    передачи значений из запросов обработчикам (создателям) ответов и отправкой созданных
    ответов клиенту, а также корректировать пути в пользовательской папке на основании
    приходящих запросов и хранить (и передавать) информацию о свободном месте в ней
    соответствующим обработчикам.
    код создателей ответов (обработчиков запросов) все равно будет выполняться на сервере,
    но его, по крайней мере, будет проще редактировать при необходимости
*/
public class CloudFileHandler extends SimpleChannelInboundHandler<CloudMessage> {
    private long freeSpace;
    private Path userFolder;
    private String uploadTarget, userLogin;
    private int FSType = FS_UNK;
    private final AuthService DBService;
    private final EventLogger logger;

    private final ArrayList<TransferOp> transfer = new ArrayList<>();

    public CloudFileHandler(AuthService dbs, EventLogger logger) {
        DBService = dbs;
        this.logger = logger;
    }

    @Override public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("New client connected");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, CloudMessage cloudMessage) throws Exception {
        /*
           чтобы узнать об ошибке передачи сообщения в канал, можно использовать следующее:
                ChannelFuture cf = ctx.writeAndFlush(...);
                if (!cf.isSuccess()) System.out.println("error="+cf.cause());
         */
        // запрос авторизации
        if (cloudMessage instanceof AuthRequest) {
            AuthRequest rq = (AuthRequest)cloudMessage;
            userLogin = rq.getLogin();
            String newUser = DBService.getUserInfo(userLogin, rq.getPasswordHash());
            if (newUser != null) {
                String[] userdata = newUser.split("\t");
                if (userdata.length > 1) {
                    newUser = userdata[0];
                    userFolder = serverURL.resolve("user" + userdata[1]);
                    freeSpace = new SpaceResponse(userFolder).getSpace();
                } else
                    newUser = null;
            }
            AuthResponse rs = new AuthResponse(newUser, userFolder);
            if (newUser != null && rs.getErrCode() < 0) {
                sendFreeSpace(ctx);
                sendFSType(ctx);
                sendFilesList(ctx, "");
            }
            if (rs.getErrCode() >= 0)
                logger.logError(
                        String.format(SERVER_ERROR_HANDLER, COM_AUTHORIZE, errMessage[rs.getErrCode()]));
            ctx.writeAndFlush(rs);
        }
        // запрос регистрации
        if (cloudMessage instanceof RegRequest) {
            RegRequest rq = (RegRequest)cloudMessage;
            int number = 0;
            String newUser = rq.getPassword().length() < MIN_PWD_LEN
                    || rq.getPassword().length() > MAX_PWD_LEN
                    ? null
                    : (number = DBService.registerUser(
                            rq.getLogin(), encode(rq.getPassword(), false),
                            rq.getUsername(), rq.getEmail())) > 0
                       ? rq.getUsername() : null;
            if (newUser != null) {
                userFolder = serverURL.resolve("user" + number);
                freeSpace = MAXSIZE;
            }
            RegResponse rs = new RegResponse(newUser, number, userFolder);
            if (newUser != null && rs.getErrCode() < 0) {
                sendFreeSpace(ctx);
                sendFSType(ctx);
                sendFilesList(ctx, "");
            }
            if (rs.getErrCode() >= 0)
                logger.logError(
                        String.format(SERVER_ERROR_HANDLER, COM_REGISTER, errMessage[rs.getErrCode()]));
            ctx.writeAndFlush(rs);
        }
        // запрос завершения сеанса пользователя
        if (cloudMessage instanceof LogoutRequest)
            ctx.writeAndFlush(new LogoutResponse(userLogin));
        // запрос размера свободного места в пользовательской папке:
        // вместо выполнения запроса передать хранящееся во freeSpace значение -
        // оно всегда актуально, поскольку обрабываются ВСЕ операции передачи
        // и удаления файлов на сервере
        if (cloudMessage instanceof SpaceRequest) sendFreeSpace(ctx);
        // запрос списка файлов/папок в пользовательской папке
        if (cloudMessage instanceof FilesListRequest) {
            FilesListRequest rq = (FilesListRequest)cloudMessage;
            FilesListResponse rs = new FilesListResponse(userFolder.resolve(rq.getPath()), rq.getPath());
            if (rs.getErrCode() >= 0)
                logger.logError(
                        String.format(SERVER_ERROR_HANDLER, COM_GET_FILES, errMessage[rs.getErrCode()]));
            ctx.writeAndFlush(rs);
        }
        if (cloudMessage instanceof SizeRequest) {
            Path p = userFolder.resolve(((SizeRequest)cloudMessage).getPath());
            ctx.writeAndFlush(new SizeResponse(FileInfo.getSizes(p), FileInfo.getItems(p).size()));
        }
        // запрос копирования файла/папки с клиента на сервер
        if (cloudMessage instanceof UploadRequest) {
            UploadRequest rq = (UploadRequest)cloudMessage;
            uploadTarget = rq.getDstPath();
            Path dst = userFolder.resolve(rq.getDstPath()).resolve(rq.getSrcPath());
            boolean exists = false;
            try { exists = Files.exists(dst); }
            catch (Exception ex) { ex.printStackTrace(); }
            long curSize = exists
                    ? Files.isDirectory(dst) ? -1L : Files.size(dst)
                    : 0L;
            UploadResponse rs = new UploadResponse(dst, rq.getSize(), rq.getModified());
            if (rs.getErrCode() < 0) {
                int id = SRV_SUCCESS;
                if (curSize >= 0L && rq.getSize() == 0L)
                    freeSpace += curSize;
                else
                    if (rq.getSize() > 0L && freeSpace-(rq.getSize()-curSize) <= 0L)
                        rs.setErrCode(ERR_OUT_OF_SPACE);
                    else
                        if (rq.getSize() > 0L)
                            rs.setId(id = 1+startTransfer(dst.toString(), curSize, rq.getSize(), rq.getModified()));
                if (id == SRV_SUCCESS && rs.getErrCode() < 0) {
                    if (rq.getSize() == 0L && curSize != 0L) sendFreeSpace(ctx);
                    FilesListResponse flrs =
                            new FilesListResponse(userFolder.resolve(uploadTarget), uploadTarget);
                    if (flrs.getErrCode() >= 0)
                        logger.logError(
                                String.format(SERVER_ERROR_HANDLER, COM_UPLOAD, errMessage[flrs.getErrCode()]));
                    ctx.writeAndFlush(flrs);
                }
            }
            if (rs.getErrCode() >= 0)
                logger.logError(
                        String.format(SERVER_ERROR_HANDLER, COM_UPLOAD, errMessage[rs.getErrCode()]));
            ctx.writeAndFlush(rs);
        }
        // запрос копирования файла/папки с клиента на сервер
        if (cloudMessage instanceof UploadDataRequest) {
            UploadDataRequest rq = (UploadDataRequest)cloudMessage;

            int id = rq.getId()-1;
            boolean spaceChanged = rq.getSize() != transfer.get(id).getCurSize();
            byte[] buf = rq.getData();
            String dst = transfer.get(id).getPath();
            boolean success = true, done = false;
            try (
                    BufferedOutputStream bos = new BufferedOutputStream(
                            new FileOutputStream(dst, true), BUF_SIZE)) {
                bos.write(buf, 0, rq.getSize());
            } catch (Exception ex) {
                logger.logError(ex);
                success = false;
            }
            if (success) {
                transfer.get(id).setReceived(transfer.get(id).getReceived()+rq.getSize());
                if (transfer.get(id).getReceived() == transfer.get(id).getNewSize()) {
                    // установить дату и время последней модификации как у оригинала
                    if (transfer.get(id).getModified() > 0)
                        try {
                            success = applyDateTime(dst, transfer.get(id).getModified());
                            freeSpace -= transfer.get(id).getNewSize()-transfer.get(id).getCurSize();
                            done = true;
                        }
                        catch (Exception ex) {
                            logger.logError(ex);
                            success = false;
                        }
                    transfer.remove(id);
                }
            }
            UploadResponse rs = new UploadResponse(done ? SRV_SUCCESS : id+1);
            if (!success)
                rs.setErrCode(ERR_CANNOT_COMPLETE);
            else
                /* по недосмотру список файлов на сервере обновлялся после каждого получения
                   блока передаваемого файла, и увеличивающийся размер создаваемого на сервере
                   файла можно было видеть, так сказать, в динамике */
                if (done) {
                    if (spaceChanged) sendFreeSpace(ctx);
                    FilesListResponse flrs =
                            new FilesListResponse(userFolder.resolve(uploadTarget), uploadTarget);
                    if (flrs.getErrCode() >= 0)
                        logger.logError(
                                String.format(SERVER_ERROR_HANDLER, COM_UPLOAD, errMessage[flrs.getErrCode()]));
                    ctx.writeAndFlush(flrs);
                }
            if (rs.getErrCode() >= 0)
                logger.logError(
                        String.format(SERVER_ERROR_HANDLER, COM_UPLOAD, errMessage[rs.getErrCode()]));
            ctx.writeAndFlush(rs);
        }
        // запрос копирования файла/папки с сервера на клиент
        if (cloudMessage instanceof DownloadRequest) {
            DownloadRequest rq = (DownloadRequest)cloudMessage;
            byte[] buf = new byte[BUF_SIZE];
            try (BufferedInputStream bis = new BufferedInputStream(
                    Files.newInputStream(userFolder.resolve(rq.getSrcPath())), BUF_SIZE)) {
                int bytesRead;
                while ((bytesRead = bis.read(buf)) > 0)
                    ctx.writeAndFlush(new DownloadResponse(bytesRead, buf));
            } catch (Exception ex) {
                logger.logError(ex);
                ctx.writeAndFlush(new DownloadResponse(0, null));
            }
        }
        // запрос удаления в пользовательской папке
        if (cloudMessage instanceof RemovalRequest) {
            RemovalRequest rq = (RemovalRequest)cloudMessage;
            long freed = 0;
            try { freed = Files.size(userFolder.resolve(rq.getPath())); }
            catch (IOException ex) { logger.logError(ex); }
            RemovalResponse rs = new RemovalResponse(userFolder.resolve(rq.getPath()));
            if (rs.getErrCode() < 0) {
                freeSpace += freed;
                sendFreeSpace(ctx);
                sendFilesList(ctx, rq.getPath());
            }
            if (rs.getErrCode() >= 0)
                logger.logError(
                        String.format(SERVER_ERROR_HANDLER, COM_REMOVE, errMessage[rs.getErrCode()]));
            ctx.writeAndFlush(rs);
        }
        // запрос на переименование файла/папки
        if (cloudMessage instanceof RenameRequest) {
            RenameRequest rq = (RenameRequest)cloudMessage;
            Path p = userFolder.resolve(rq.getCurName());
            long freed = Files.exists(p.resolveSibling(rq.getNewName()))
                    ? Files.size(p.resolveSibling(rq.getNewName()))-Files.size(p)
                    : 0L;
            if (freed < 0L) freed = Math.min(Files.size(p.resolveSibling(rq.getNewName())),Files.size(p));
            RenameResponse rs = new RenameResponse(userFolder.resolve(rq.getCurName()), rq.getNewName());
            if (rs.getErrCode() < 0) {
                if (freed > 0L) {
                    freeSpace += freed;
                    sendFreeSpace(ctx);
                }
                sendFilesList(ctx, rq.getCurName());
            }
            if (rs.getErrCode() >= 0)
                logger.logError(
                        String.format(SERVER_ERROR_HANDLER, COM_RENAME, errMessage[rs.getErrCode()]));
            ctx.writeAndFlush(rs);
        }
        // запрос локального копирования/перемещения файла/папки
        if (cloudMessage instanceof CopyRequest) {
            CopyRequest rq = (CopyRequest)cloudMessage;
            if (!rq.moved() && freeSpace-Files.size(userFolder.resolve(rq.getSrc())) <= 0L)
                ctx.writeAndFlush(new CopyResponse(ERR_OUT_OF_SPACE));
            else
                try {
                    int i = rq.getSrc().lastIndexOf(File.separatorChar);
                    String entry = i < 0 ? rq.getSrc() : rq.getSrc().substring(i+1);
                    copy(userFolder.resolve(rq.getSrc()), userFolder.resolve(rq.getDst()).resolve(entry),
                            rq.moved());
                    if (rq.moved())
                        sendFilesList(ctx, rq.getSrc());
                    else {
                        freeSpace -= Files.size(userFolder.resolve(rq.getSrc()));
                        sendFreeSpace(ctx);
                    }
                    sendFilesList(ctx, rq.getDst()+File.separatorChar+entry);
                    ctx.writeAndFlush(new CopyResponse(entry, rq.moved()));
                } catch (Exception ex) {
                    ctx.writeAndFlush(new CopyResponse(ERR_CANNOT_COMPLETE));
                }
        }
    }

    // извещения о свободном месте в папке пользователя и типе ее ФС
    private void sendFreeSpace(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(new SpaceResponse(freeSpace));
    }
    private void sendFSType(ChannelHandlerContext ctx) {
        if (FSType == FS_UNK) FSType = getFSType(userFolder);
        ctx.writeAndFlush(new FSTypeNotice(FSType));
    }

    private void sendFilesList(ChannelHandlerContext ctx, String name) {
        int i = name.lastIndexOf(File.separatorChar);
        String folder = i < 0 ? "" : name.substring(0, i);
        ctx.writeAndFlush(new FilesListResponse(i < 0 ? userFolder : userFolder.resolve(folder), folder));
    }

    private int startTransfer(String path, long oldSize, long newSize, long modified) {
        int id = 0;
        if (transfer.size() > 0)
            while (id < transfer.size() && transfer.get(id) != null) id++;
        if (id == transfer.size())
            transfer.add(new TransferOp(path, oldSize, newSize, modified));
        else
            transfer.set(id, new TransferOp(path, oldSize, newSize, modified));
        return id;
    }
}