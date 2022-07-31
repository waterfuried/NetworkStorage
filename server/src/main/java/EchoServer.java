import prefs.*;
import authService.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

import static prefs.Prefs.*;

public class EchoServer {
    private ServerSocket server;
    private Socket socket;
    private final List<ClientHandler> clients;
    private ExecutorService threadPool;
    final EventLogger logger;
    CountDownLatch latch;

    public EchoServer() {
        logger = new EventLogger(EchoServer.class.getName(), null);
        clients = new CopyOnWriteArrayList<>();

        AuthService tmp;
        do {
            tmp = new AuthServiceDB(logger);
            if (!tmp.isServiceActive()) tmp.close();
        } while (!tmp.isServiceActive());
        final AuthService authService = tmp;

        try {
            server = new ServerSocket(Prefs.PORT);
            logger.info("Server started");
            threadPool = Executors.newCachedThreadPool();

            // обработчики клиентских запросов организованы (помещены) в пул потоков,
            // поскольку каждый обработчик выполняет однотипные задачи
            threadPool.submit(() -> {
                try {
                    while (server != null) {
                        Socket curSocket = server.accept();
                        if (server != null) {
                            boolean newSocket = false;
                            if (curSocket != null)
                                newSocket = socket == null || curSocket != socket;
                            if (newSocket) {
                                socket = curSocket;
                                onSocketOpen(new ClientHandler(this, socket, authService, logger));
                            }
                        }
                    }
                } catch (Exception ex) { logger.logError(ex); }
                finally {
                    try { if (socket != null) socket.close(); }
                    catch (Exception ex) { logger.logError(ex); }
                }
            });

            logger.info("Type "+getAllExitCommands(" or ")+" to shut down");
            Scanner sc = new Scanner(System.in);
            boolean shutdown;
            do { shutdown = isExitCommand(sc.nextLine()); }
            while (!shutdown);
        } catch (Exception ex) { logger.logError(ex); }
        finally {
            if (clients.size() > 0) {
                latch = new CountDownLatch(clients.size());
                for (ClientHandler c : clients) c.sendResponse(Prefs.getExitCommand().get(0));
                try { latch.await(); }
                catch (InterruptedException ex) { logger.logError(ex); }
            }

            try {
                if (server != null) server.close();
                authService.close();
            } catch (IOException ex) { logger.logError(ex); }
            logger.info("Server shut down");
            logger.closeHandlers();
            threadPool.shutdown();
        }
    }

    public ExecutorService getThreadPool() { return threadPool; }

    public void onSocketOpen(ClientHandler clientHandler) {
        clients.add(clientHandler);
        logger.info("Client accepted");
    }

    public void onSocketClose(ClientHandler clientHandler) {
        logger.info("Client disconnected");
        clients.remove(clientHandler);
    }

    public static void main(String[] args) { new EchoServer(); }
}