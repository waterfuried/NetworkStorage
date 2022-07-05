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
    private String uploadTarget;
    AuthService DBService;

    private ArrayList<TransferOp> transfer = new ArrayList<>();

    public void setUserFolder(String folder) { this.userFolder = Prefs.serverURL.resolve(folder); }

    public CloudFileHandler(AuthService dbs) {
        DBService = dbs;
    }

    @Override public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("New client connected");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, CloudMessage cloudMessage) throws Exception {
        if (cloudMessage != null) System.out.println("request="+cloudMessage);
        // запрос авторизации
        if (cloudMessage instanceof AuthRequest) {
            AuthRequest auth = (AuthRequest)cloudMessage;
            System.out.println("authorization request: "+auth.getLogin()+" "+auth.getPassword());
            String newUser = auth.getPassword().length() < Prefs.MIN_PWD_LEN
                    ? null
                    : DBService.getUserInfo(auth.getLogin(), auth.getPassword());
            if (newUser != null) {
                String[] userdata = newUser.split("\t");
                if (userdata.length > 1) {
                    newUser = userdata[0];
                    setUserFolder("user" + userdata[1]);
                    freeSpace = new SpaceResponse(userFolder).getSpace();
                } else
                    newUser = null;
            }
            AuthResponse r = new AuthResponse(newUser, userFolder);
            ctx.writeAndFlush(r);
            if (newUser != null && r.getErrCode() < 0) {
                sendFreeSpace(ctx);
                ctx.writeAndFlush(new FilesListResponse(userFolder));
            }
        }
        if (cloudMessage instanceof RegRequest) {
            RegRequest reg = (RegRequest)cloudMessage;
            System.out.println("registration request: login="+reg.getLogin()
                    +" pwd="+reg.getPassword()
                    +" email="+reg.getEmail()
                    +" username="+reg.getUsername());
            int number = 0;
            String newUser = reg.getPassword().length() < Prefs.MIN_PWD_LEN
                    ? null
                    : (number = DBService.registerUser(reg.getLogin(),
                        reg.getPassword(), reg.getUsername(), reg.getEmail())) > 0
                        ? reg.getUsername() : null;
            if (newUser != null) {
                setUserFolder("user" + number);
                freeSpace = Prefs.MAXSIZE;
            }
            RegResponse r = new RegResponse(newUser, number, userFolder);
            ctx.writeAndFlush(r);
            if (newUser != null && r.getErrCode() < 0) {
                sendFreeSpace(ctx);
                ctx.writeAndFlush(new FilesListResponse(userFolder));
            }
        }
        // запрос завершения сеанса пользователя
        if (cloudMessage instanceof LogoutRequest) {
            System.out.println("logout request");
            LogoutRequest logout = (LogoutRequest)cloudMessage;
            ctx.writeAndFlush(new LogoutResponse(logout.getLogin()));
        }
        // запрос свободного места в пользовательской папке
        if (cloudMessage instanceof SpaceRequest) {
            System.out.println("free space request");
            // вместо выполнения запроса можно передать хранящееся во freeSpace значение
            // при нормальных условиях, все операции передачи файлов на сервер здесь
            // контролируются, и хранящаяся информация о свободном месте актуальна
            sendFreeSpace(ctx);
        }
        // запрос списка файлов/папок в пользовательской папке
        if (cloudMessage instanceof FilesListRequest) {
            FilesListRequest files = (FilesListRequest)cloudMessage;
            System.out.println("files list request: '"+files.getPath()+"'");
            ctx.writeAndFlush(new FilesListResponse(userFolder.resolve(files.getPath())));
        }
        // запрос копирования файла/папки с клиента на сервер
        if (cloudMessage instanceof UploadRequest) {
            UploadRequest upload = (UploadRequest)cloudMessage;
            System.out.println("upload request:"+
                    "\n"+upload.getSrcPath()+
                    "\n"+upload.getDstPath()+
                    "\n"+upload.getSize()+
                    "\n"+upload.getModified());

            uploadTarget = upload.getDstPath();
            // перед выполнением проверять наличие файла и достаточного свободного места
            Path dst = userFolder.resolve(upload.getDstPath()).resolve(upload.getSrcPath());
            boolean exists = Files.exists(dst);
            long size = upload.getSize(), oldSize = exists ? Files.isDirectory(dst) ? -1L : Files.size(dst) : 0L;
            int id = 0, errCode = -1;
            if (size >= 0 && size >= oldSize && freeSpace-(size-oldSize) <= 0)
                errCode = Prefs.ErrorCode.ERR_OUT_OF_SPACE.ordinal();
            else {
                boolean isFile = exists ? !Files.isDirectory(dst) : size >= 0,
                        success = (isFile && size >= 0) || (!isFile && size < 0);
                if (success)
                    if (size > 0) { // файл с данными
                        id = startTransfer(dst.toString(), (int) oldSize, (int) size, upload.getModified()) + 1;
                        if (exists) success = Prefs.resetFile(dst);
                    } else { // пустой файл или папка
                        if (size < 0) success = new File(dst.toString()).mkdir();
                        if (isFile && oldSize > 0) success = Prefs.resetFile(dst);
                        // установить дату и время последней модификации как у оригинала
                        if (success) {
                            if (upload.getModified() > 0)
                                try {
                                    // если по какой-то причине дату/время применить не удалось,
                                    // считать это неудачей всей операции в целом не стоит
                                    new File(dst.toString()).setLastModified(upload.getModified());
                                } catch (NumberFormatException ex) { ex.printStackTrace(); }
                            freeSpace -= size-oldSize;
                        }
                    }
                if (!success) errCode = Prefs.ErrorCode.ERR_CANNOT_COMPLETE.ordinal();
                UploadResponse r = new UploadResponse(id, errCode);
                ctx.writeAndFlush(r);
                if (success)
                    if (id == 0 && (size < 0 || size != oldSize)) {
                        if (size != oldSize) sendFreeSpace(ctx);
                        ctx.writeAndFlush(new FilesListResponse(userFolder.resolve(upload.getDstPath())));
                    }
            }
        }
        // запрос копирования файла/папки с клиента на сервер
        if (cloudMessage instanceof UploadDataRequest) {
            UploadDataRequest upload = (UploadDataRequest)cloudMessage;

            int id = upload.getId()-1, size = upload.getSize(), errCode = -1, oldSize = 0;
            byte[] buf = upload.getData();
            String dst = transfer.get(id).getPath();
            boolean success = true;
            try (
                    BufferedOutputStream bos = new BufferedOutputStream(
                            new FileOutputStream(dst, true), Prefs.BUF_SIZE)) {
                bos.write(buf, 0, size);
            } catch (Exception ex) {
                ex.printStackTrace();
                success = false;
            }
            if (success) {
                transfer.get(id).setReceived(transfer.get(id).getReceived()+size);
                if (transfer.get(id).getReceived() == transfer.get(id).getNewSize()) {
                    // установить дату и время последней модификации как у оригинала
                    if (transfer.get(id).getModified() > 0)
                        try { new File(dst).setLastModified(transfer.get(id).getModified()); }
                        catch (Exception ex) { ex.printStackTrace(); }
                    size = transfer.get(id).getNewSize();
                    oldSize = transfer.get(id).getOldSize();
                    freeSpace -= size-oldSize;
                    transfer.remove(id);
                }
            } else
                errCode = Prefs.ErrorCode.ERR_CANNOT_COMPLETE.ordinal();
            UploadResponse r = new UploadResponse(id, errCode);
            ctx.writeAndFlush(r);
            if (success) {
                if (size != oldSize) sendFreeSpace(ctx);
                ctx.writeAndFlush(new FilesListResponse(userFolder.resolve(uploadTarget)));
            }
        }
        // запрос копирования файла/папки с сервера на клиент
        if (cloudMessage instanceof DownloadRequest) {
            DownloadRequest download = (DownloadRequest)cloudMessage;
            System.out.println("download request:\n"+download.getSrcPath());
            byte[] buf = new byte[Prefs.BUF_SIZE];
            try (BufferedInputStream bis = new BufferedInputStream(
                    Files.newInputStream(userFolder.resolve(download.getSrcPath())), Prefs.BUF_SIZE)) {
                int bytesRead;
                while ((bytesRead = bis.read(buf)) > 0)
                    ctx.writeAndFlush(new DownloadResponse(bytesRead, buf));
            } catch (Exception ex) {
                ex.printStackTrace();
                ctx.writeAndFlush(new DownloadResponse(0, null));
            }
        }
        // запрос удаления в пользовательской папке
        if (cloudMessage instanceof RemovalRequest) {
            RemovalRequest rm = (RemovalRequest)cloudMessage;
            System.out.println("removal request: '"+rm.getPath()+"'");
            long freed = 0;
            try { freed = Files.size(userFolder.resolve(rm.getPath())); }
            catch (IOException ex) { ex.printStackTrace(); }
            RemovalResponse r = new RemovalResponse(userFolder.resolve(rm.getPath()));
            ctx.writeAndFlush(r);
            if (r.getErrCode() < 0) {
                freeSpace += freed;
                sendFreeSpace(ctx);
                int i = rm.getPath().lastIndexOf(File.separatorChar);
                ctx.writeAndFlush(new FilesListResponse(
                        i < 0 ? userFolder : userFolder.resolve(rm.getPath().substring(0, i))));
            }
        }
        // запрос на проверку существования файла/папки в пользовательской папке
        if (cloudMessage instanceof ExistsRequest) {
            ExistsRequest eq = (ExistsRequest)cloudMessage;
            System.out.println("exists request: '"+eq.getPath()+"'");
            ctx.writeAndFlush(new ExistsResponse(userFolder.resolve(eq.getPath())));
        }
    }

    private void sendFreeSpace(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(new SpaceResponse(freeSpace));
    }

    private int startTransfer(String path, int oldSize, int newSize, long modified) {
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