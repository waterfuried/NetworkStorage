import prefs.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.stream.*;

public class ClientHandler implements Runnable {
    private DataInputStream is;
    private DataOutputStream os;

    private long freeSpace;

    public ClientHandler(Socket socket) throws IOException {
        is = new DataInputStream(socket.getInputStream());
        os = new DataOutputStream(socket.getOutputStream());
        System.out.println("Client accepted");
    }

    // отправка ответа на запрос/команду пользователя
    public void sendResponse(String msg) {
        try { os.writeUTF(msg); }
        catch (IOException ex) { ex.printStackTrace(); }
    }

    @Override public void run() {
        Path rootServerPath = Prefs.serverURL;
        byte[] buf = new byte[Prefs.BUF_SIZE];
        try {
            // обработка команд/запросов клиента
            while (true) {
                String cmd = is.readUTF();
                System.out.println("received: " + cmd);
                if (cmd.startsWith(Prefs.COM_ID)) {
                    String s = cmd.toLowerCase();
                    // команда авторизации
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_AUTHORIZE))) {
                        String[] val = s.split(" ", 3);
                        // в случае ошибки вернуть код 0,
                        // в случае успеха - доп. код 0, имя и пароль
                        sendResponse(val[2].length() < Prefs.MIN_PWD_LEN
                            ? Prefs.getCommand(Prefs.SRV_REFUSE, Prefs.ERR_WRONG_AUTH)
                            : Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.SRV_SUCCESS+"", val[1], val[2]));
                    }
                    // команда завершения сеанса
                    // получив от клиента запрос на завершение сеанса, отправить запрос обратно
                    if (Prefs.isExitCommand(s)) sendResponse(s);
                    // запрос на информацию об элементах в папке пользователя на сервере
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_GET_FILES))) {
                        String[] arg = s.split(" ", 2);
                        Path path = rootServerPath;
                        int errCode = 0, sz = 0;
                        String list = "";
                        boolean subfolder = false;
                        if (arg.length > 1) {
                            subfolder = true;
                            try { path = path.resolve(arg[1]); }
                            catch (InvalidPathException ex) { errCode = Prefs.ERR_NO_SUCH_FILE; }
                        }
                        if (errCode == 0) {
                            //List<FileInfo> list = FileInfo.getItemsInfo(path);
                            // можно заменить Files.list(path).map(p -> new FileInfo(p)).toList();
                            // p -> new FileInfo(p) заменяется ссылкой на конструктор FileInfo::new
                            try (Stream<Path> pathStream = Files.list(path)) {
                                list = pathStream
                                        .map(FileInfo::new)
                                        .map(fi -> fi.getFilename()+":"+fi.getSize()+":"+fi.getModifiedAsLong())
                                        .collect(Collectors.joining("\n"));
                                sz = list.split("\n").length;
                                if (sz == 1 && list.length() == 0) sz = 0;
                            } catch (IOException ex) {
                                errCode = Prefs.ERR_NO_SUCH_FILE;
                                ex.printStackTrace();
                            }
                        }
                        sendResponse(errCode == 0
                                ? Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.COM_GET_FILES,
                                  sz + " " + (subfolder ? arg[1] : ".") + (sz == 0 ? "" : " "+list))
                                : Prefs.getCommand(Prefs.SRV_REFUSE, Prefs.ERR_NO_SUCH_FILE));
                    }
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_GET_SPACE))) {
                        freeSpace = Prefs.MAXSIZE - FileInfo.getSizes(rootServerPath);
                        sendResponse(Prefs.getCommand(Prefs.SRV_ACCEPT,
                                Prefs.COM_GET_SPACE, freeSpace+""));
                    }
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_UPLOAD))) {
                        // /upload source_name source_path destination_path size [date]; "." for root destination
                        String[] arg = s.split(" ", 6);
                        long sz = 0L;
                        try { sz = Long.parseLong(arg[4]); }
                        catch (Exception ex) { ex.printStackTrace(); }
                        // перед выполнением проверять наличие достаточного свободного места
                        if (sz >= 0 && freeSpace-sz <= 0) // поправка для папок
                            sendResponse(Prefs.getCommand(Prefs.SRV_REFUSE, Prefs.ERR_OUT_OF_SPACE));
                        else {
                            boolean success;
                            if (arg[3].equals(".")) arg[3] = "";
                            String src = Paths.get(arg[2], arg[1]).toString(),
                                    dst = rootServerPath.resolve(arg[3]).resolve(arg[1]).toString();
                            if (sz >= 0) {
                                // файл
                                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src), Prefs.BUF_SIZE);
                                     BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dst), Prefs.BUF_SIZE)) {
                                    int bytesRead;
                                    while ((bytesRead = bis.read(buf)) >= 0) bos.write(buf, 0, bytesRead);
                                } catch (Exception ex) { ex.printStackTrace(); }
                                success = new File(dst).length() == sz;
                                if (success) {
                                    freeSpace -= sz;
                                    // установить дату и время последней модификации как у оригинала
                                    if (arg.length == 6)
                                        try {
                                            FileInfo fi = new FileInfo(Paths.get(dst));
                                            fi.setModified(arg[5]);
                                            // если по какой-то причине дату/время применить не удалось,
                                            // считать это неудачей всей операции в целом не стоит
                                            new File(dst).setLastModified(fi.getModifiedAsLong());
                                        } catch (NumberFormatException ex) { ex.printStackTrace(); }
                                }
                            } else
                                // папка
                                success = new File(dst).mkdir();
                            sendResponse(success
                                    ? Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.COM_UPLOAD, Prefs.SRV_SUCCESS+"")
                                    : Prefs.getCommand(Prefs.SRV_REFUSE, Prefs.ERR_CANNOT_COMPLETE+"", Prefs.COM_UPLOAD));
                        }
                    }
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_DOWNLOAD))) {
                        // /download server_source_path destination_path size [date]
                        String[] arg = s.split(" ", 5);
                        long sz = 0L;
                        try { sz = Long.parseLong(arg[3]); }
                        catch (Exception ex) { ex.printStackTrace(); }
                        boolean success;
                        String src = rootServerPath.resolve(arg[1]).toString(),
                               dst = Paths.get(arg[2], arg[1].substring(arg[1]
                                       .lastIndexOf(File.separatorChar)+1)).toString();
                        if (sz >= 0) {
                            // файл
                            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src), Prefs.BUF_SIZE);
                                 BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dst), Prefs.BUF_SIZE)) {
                                int bytesRead;
                                while ((bytesRead = bis.read(buf)) >= 0) bos.write(buf, 0, bytesRead);
                            } catch (Exception ex) { ex.printStackTrace(); }
                            success = new File(dst).length() == sz;
                            // установить дату и время последней модификации как у оригинала
                            if (success && arg.length == 5)
                                try {
                                    FileInfo fi = new FileInfo(Paths.get(dst));
                                    fi.setModified(arg[4]);
                                    new File(dst).setLastModified(fi.getModifiedAsLong());
                                } catch (NumberFormatException ex) { ex.printStackTrace(); }
                        } else
                            // папка
                            success = new File(dst).mkdir();
                        sendResponse(success
                            ? Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.COM_DOWNLOAD, Prefs.SRV_SUCCESS+"")
                            : Prefs.getCommand(Prefs.SRV_REFUSE, Prefs.ERR_CANNOT_COMPLETE+"", Prefs.COM_DOWNLOAD));
                    }
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_REMOVE))) {
                        // /remove entry_name
                        String[] arg = s.split(" ", 2);
                        int errCode = 0;
                        try {
                            Files.delete(rootServerPath.resolve(arg[1]));
                        } catch (IOException ex) {
                            errCode = Prefs.ERR_NO_SUCH_FILE;
                            if (ex instanceof DirectoryNotEmptyException)
                                errCode = Prefs.ERR_NOT_EMPTY;
                        }
                        sendResponse(errCode == 0
                                ? Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.COM_REMOVE, Prefs.SRV_SUCCESS+"")
                                : Prefs.getCommand(Prefs.SRV_REFUSE, errCode+"", Prefs.COM_REMOVE));
                    }
                }
            }
        } catch (Exception ex) { System.err.println("Connection was broken"); ex.printStackTrace(); }
    }
}