package netty;

import netty.handler.*;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.*;

import prefs.Prefs;
import authService.*;

public class CloudServer {
    public CloudServer() {
        EventLoopGroup auth = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();

        AuthService tmp;
        do {
            tmp = new AuthServiceDB();
            if (!tmp.isServiceActive()) tmp.close();
        } while (!tmp.isServiceActive());
        final AuthService authService = tmp;

        try {
            ServerBootstrap server = new ServerBootstrap();
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
            System.out.println("Server is ready");
            future.channel().closeFuture().sync();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            auth.shutdownGracefully();
            worker.shutdownGracefully();
            authService.close();
        }
    }

    public static void main(String[] args) {
        new CloudServer();
    }
}