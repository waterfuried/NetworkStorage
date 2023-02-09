package netty;

import authService.AuthService;
import authService.AuthServiceDB;
import netty.handler.*;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.*;

import prefs.*;

import java.util.Scanner;

import static prefs.Prefs.getAllExitCommands;
import static prefs.Prefs.isExitCommand;

public class CloudServer {
    public CloudServer() {
        final EventLogger logger = new EventLogger(CloudServer.class.getName(), null);
        EventLoopGroup auth = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();

        AuthService tmp;
        do {
            tmp = new AuthServiceDB(logger);
            if (tmp.isServiceInactive()) tmp.close();
        } while (tmp.isServiceInactive());
        final AuthService authService = tmp;

        try {
            ServerBootstrap server = new ServerBootstrap();
            //TODO: client list processing
            server.group(auth, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) {
                            socketChannel.pipeline().addLast(
                                    // в каком порядке обработчики добавлены, в таком они и будут
                                    // обрабатывать сообщения
                                    new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                    new ObjectEncoder(),
                                    new CloudFileHandler(authService)
                            );
                        }
                    });
            ChannelFuture future = server.bind(Prefs.PORT).sync();
            logger.info("Server started");
            logger.info("Type "+getAllExitCommands(" or ")+" to shut down");
            Scanner sc = new Scanner(System.in);
            boolean shutdown;
            do { shutdown = isExitCommand(sc.nextLine()); }
            while (!shutdown);
            //ожидание закрытия сокета
            future.channel().closeFuture().sync();
        } catch (Exception ex) {
            logger.logError(ex);
        } finally {
            auth.shutdownGracefully();
            worker.shutdownGracefully();
            authService.close();
            logger.closeHandlers();
        }
    }

    public static void main(String[] args) {
        new CloudServer();
    }
}