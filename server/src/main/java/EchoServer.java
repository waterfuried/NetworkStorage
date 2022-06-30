import prefs.*;
import authService.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

// TODO:
//  1. сервер должен иметь возможность нормального завершения своей работы
//  2. поскольку обработчики запросов клиентов выполняют однотипные задачи,
//     для них стоит использовать пул потоков
public class EchoServer {
    public static void main(String[] args) throws IOException {
        final EventLogger logger = new EventLogger(EchoServer.class.getName(), null);
        AuthService authService;
        do {
            authService = new AuthServiceDB(logger);
            if (!authService.isServiceActive()) authService.close();
        } while (!authService.isServiceActive());
        try (ServerSocket server = new ServerSocket(Prefs.PORT)) {
            System.out.println("Server started");
            while (true) {
                Socket socket = server.accept();
                ClientHandler handler = new ClientHandler(socket, authService, logger);
                new Thread(handler).start();
            }
        } finally {
            logger.closeHandlers();
        }
    }
}