package netty.handler;

import prefs.*;
import cloud.*;
import cloud.request.*;
import cloud.response.*;
import io.netty.channel.*;

import java.io.IOException;
import java.nio.file.Files;

/*
    если обработчик запросов клиента находится на серверной стороне (где он и должен быть),
    операции в пользовательской папке можно писать не в коде ответа на запрос, а прямо здесь,
    но тогда и весь код будет здесь, а ответы на запрос будут почти пустышками (обертками),
    кроме того, если при выполнении запросов возникают ошибки, о которых желательно сообщать
    в ответе, их передавать в класс ответа на запрос как аргумент его конструктора?
    это выглядит несколько странно, поэтому формирование ответов стоит оставить в реализации
    их классов, сам же обработчик будет выполнять лишь функции получения клиентских запросов,
    передачи значений из запросов обработчикам (создателям) ответов и отправкой созданных
    ответов клиенту, а также хранить (и передавать) информацию о свободном месте
    в пользовательской папке соответствующим обработчикам.
    код создателей ответов (обработчиков запросов) все равно будет выполняться на сервере,
    но его, по крайней мере, будет проще редактировать при необходимости
*/
public class CloudFileHandler extends SimpleChannelInboundHandler<CloudMessage> {
    private long freeSpace;

    public CloudFileHandler() {
        freeSpace = new SpaceResponse().getSpace();
    }

    // метод автоматически срабатывает при подключении к серверу нового клиента
    /*@Override public void channelActive(ChannelHandlerContext ctx) throws Exception {}*/

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, CloudMessage cloudMessage) throws Exception {
        System.out.println("request="+cloudMessage);
        // запрос авторизации
        if (cloudMessage instanceof AuthRequest) {
            AuthRequest auth = (AuthRequest)cloudMessage;
            System.out.println("authorization request: "+auth.getLogin()+" "+auth.getPassword());
            ctx.writeAndFlush(
                    new AuthResponse(
                            auth.getPassword().length() < Prefs.MIN_PWD_LEN
                                    ? null
                                    : auth.getLogin()
                    )
            );
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
            ctx.writeAndFlush(new FilesListResponse(files.getPath()));
        }
        // запрос копирования файла/папки с клиента на сервер
        if (cloudMessage instanceof UploadRequest) {
            UploadRequest upload = (UploadRequest)cloudMessage;
            System.out.println("upload request:"+
                    "\n"+upload.getSrcName()+
                    "\n"+upload.getSrcPath()+
                    "\n"+upload.getDstPath()+
                    "\n"+upload.getSize()+
                    "\n"+upload.getModified());
            UploadResponse r =
                    new UploadResponse(upload.getSrcName(), upload.getSrcPath(),
                            upload.getDstPath(), upload.getSize(), upload.getModified(), freeSpace);
            if (r.getErrCode() == 0 && upload.getSize() > 0)
                freeSpace -= upload.getSize();
            ctx.writeAndFlush(r);
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
                    download.getSize(), download.getModified()));
        }
        // запрос удаления в пользовательской папке
        if (cloudMessage instanceof RemovalRequest) {
            RemovalRequest rm = (RemovalRequest)cloudMessage;
            System.out.println("removal request: '"+rm.getPath()+"'");
            long freed = 0;
            try { freed = Files.size(Prefs.serverURL.resolve(rm.getPath())); }
            catch (IOException ex) { ex.printStackTrace(); }
            RemovalResponse r = new RemovalResponse(rm.getPath());
            if (r.getErrCode() == 0) freeSpace += freed;
            ctx.writeAndFlush(r);
        }
    }
}