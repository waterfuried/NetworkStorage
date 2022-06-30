package netty.handler;

import prefs.*;
import authService.*;

import cloud.*;
import cloud.request.*;
import cloud.response.*;
import io.netty.channel.*;

import java.io.*;
import java.nio.file.*;

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
    AuthService DBService;

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
                ctx.writeAndFlush(new SpaceResponse(freeSpace));
                ctx.writeAndFlush(new FilesListResponse("", userFolder));
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
                ctx.writeAndFlush(new SpaceResponse(freeSpace));
                ctx.writeAndFlush(new FilesListResponse("", userFolder));
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
            ctx.writeAndFlush(new SpaceResponse(freeSpace));
        }
        // запрос списка файлов/папок в пользовательской папке
        if (cloudMessage instanceof FilesListRequest) {
            FilesListRequest files = (FilesListRequest)cloudMessage;
            System.out.println("files list request: '"+files.getPath()+"'");
            ctx.writeAndFlush(new FilesListResponse(files.getPath(), userFolder));
        }
        // запрос копирования файла/папки с клиента на сервер
        if (cloudMessage instanceof UploadRequest) {
            UploadRequest upload = (UploadRequest)cloudMessage;
            System.out.println("upload request:"+
                    "\n"+upload.getSrcPath()+
                    "\n"+upload.getDstPath()+
                    "\n"+upload.getSize()+
                    "\n"+upload.getModified()+
                    "\n"+upload.mustOverwrite());
            UploadResponse r =
                    new UploadResponse(upload.getSrcPath(), upload.getDstPath(),
                            upload.getSize(), upload.getModified(), upload.mustOverwrite(),
                            freeSpace, userFolder);
            ctx.writeAndFlush(r);
            if (r.getErrCode() < 0) {
                if (r.getErrCode() < 0 && upload.getSize() > 0) freeSpace -= upload.getSize()-r.getOldSize();
                ctx.writeAndFlush(new SpaceResponse(freeSpace));
                ctx.writeAndFlush(new FilesListResponse(upload.getDstPath(), userFolder));
            }
        }
        // запрос копирования файла/папки с сервера на клиент
        if (cloudMessage instanceof DownloadRequest) {
            DownloadRequest download = (DownloadRequest)cloudMessage;
            System.out.println("download request:"+
                    "\n"+download.getSrcPath()+
                    "\n"+download.getDstPath()+
                    "\n"+download.getSize()+
                    "\n"+download.getModified());
            ctx.writeAndFlush(new DownloadResponse(download.getSrcPath(), download.getDstPath(),
                    download.getSize(), download.getModified(), userFolder));
        }
        // запрос удаления в пользовательской папке
        if (cloudMessage instanceof RemovalRequest) {
            RemovalRequest rm = (RemovalRequest)cloudMessage;
            System.out.println("removal request: '"+rm.getPath()+"'");
            long freed = 0;
            try { freed = Files.size(userFolder.resolve(rm.getPath())); }
            catch (IOException ex) { ex.printStackTrace(); }
            RemovalResponse r = new RemovalResponse(rm.getPath(), userFolder);
            ctx.writeAndFlush(r);
            if (r.getErrCode() < 0) {
                freeSpace += freed;
                ctx.writeAndFlush(new SpaceResponse(freeSpace));
                int i = rm.getPath().lastIndexOf(File.separatorChar);
                ctx.writeAndFlush(new FilesListResponse(i < 0 ? "" : rm.getPath().substring(0, i), userFolder));
            }
        }
    }
}