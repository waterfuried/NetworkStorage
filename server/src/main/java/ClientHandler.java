import prefs.*;
import authService.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.stream.*;

public class ClientHandler implements Runnable {
    private DataInputStream is;
    private DataOutputStream os;
    private final AuthService DBService;
    private long freeSpace;
    private Path userFolder;

    public Path getUserFolder() { return userFolder; }

    public void setUserFolder(String folder) { this.userFolder = Prefs.serverURL.resolve(folder); }

    public ClientHandler(Socket socket, AuthService dbs) throws IOException {
        is = new DataInputStream(socket.getInputStream());
        os = new DataOutputStream(socket.getOutputStream());
        this.DBService = dbs;
        System.out.println("Client accepted");
    }

    // отправка ответа на запрос/команду пользователя
    public void sendResponse(String msg) {
        try { os.writeUTF(msg); }
        catch (IOException ex) { ex.printStackTrace(); }
    }

    @Override public void run() {
        byte[] buf = new byte[Prefs.BUF_SIZE];
        try {
            // обработка команд/запросов клиента
            while (true) {
                String cmd = is.readUTF();
                System.out.println("received: " + cmd);
                if (cmd.startsWith(Prefs.COM_ID)) {
                    // s - только для обработки команд, регистр в ее аргументах важен - для них cmd
                    String s = cmd.toLowerCase();
                    // команда авторизации
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_AUTHORIZE))) {
                        int errCode = -1;
                        String[] val = cmd.split(" ", 3);
                        String newUser = val[2].length() >= Prefs.MIN_PWD_LEN
                                ? DBService.getUserInfo(val[1], val[2])
                                : null;
                        if (newUser != null) {
                            String[] userdata = newUser.split("\t");
                            if (userdata.length > 1) {
                                newUser = userdata[0];
                                setUserFolder("user"+userdata[1]);
                                boolean b;
                                try {
                                    b = new File(getUserFolder().toString()).exists();
                                    if (!b) b = new File(getUserFolder().toString()).mkdir();
                                } catch (Exception ex) {
                                    b = false;
                                    ex.printStackTrace();
                                }
                                if (!b) errCode = Prefs.ErrorCode.ERR_INTERNAL_ERROR.ordinal();
                            }
                        } else
                            errCode = Prefs.ErrorCode.ERR_WRONG_AUTH.ordinal();
                        // в случае успеха вернуть доп. код 0 и имя пользователя,
                        // в случае ошибки - соответствующий ей код
                        sendResponse(errCode < 0
                            ? Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.SRV_SUCCESS+"", newUser)
                            : Prefs.getCommand(Prefs.SRV_REFUSE, errCode));
                    }
                    // команда регистрации
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_REGISTER))) {
                        // /reg логин пароль имя-пользователя email
                        int errCode = -1;
                        String[] val = cmd.split(" ", 5);
                        int number = 0;
                        String newUser = val[2].length() < Prefs.MIN_PWD_LEN
                                ? null
                                : (number = DBService.registerUser(val[1], val[2], val[3], val[4])) > 0
                                    ? val[3] : null;
                        if (newUser != null) {
                            setUserFolder("user"+number);
                            boolean b;
                            try {
                                b = new File(getUserFolder().toString()).exists();
                                if (!b) b = new File(getUserFolder().toString()).mkdir();
                            } catch (Exception ex) {
                                b = false;
                                ex.printStackTrace();
                            }
                            if (!b) errCode = Prefs.ErrorCode.ERR_INTERNAL_ERROR.ordinal();
                        } else
                            errCode = Prefs.ErrorCode.ERR_WRONG_REG.ordinal();
                        // в случае успеха вернуть доп. код 0 и имя пользователя,
                        // в случае ошибки - соответствующий ей код
                        sendResponse(errCode < 0
                                ? Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.SRV_SUCCESS+"", newUser)
                                : Prefs.getCommand(Prefs.SRV_REFUSE, errCode));
                    }
                    // команда завершения сеанса
                    // получив от клиента запрос на завершение сеанса, отправить запрос обратно
                    if (Prefs.isExitCommand(s)) sendResponse(cmd);
                    // запрос на информацию об элементах в папке пользователя на сервере
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_GET_FILES))) {
                        String[] arg = cmd.split(" ", 2);
                        Path path = getUserFolder();
                        int errCode = -1, sz = 0;
                        String list = "";
                        boolean subfolder = false;
                        if (arg.length > 1) {
                            subfolder = true;
                            try { path = path.resolve(arg[1]); }
                            catch (InvalidPathException ex) { errCode = Prefs.ErrorCode.ERR_NO_SUCH_FILE.ordinal(); }
                        }
                        if (errCode < 0) {
                            //List<FileInfo> list = FileInfo.getItemsInfo(path);
                            // можно заменить Files.list(path).map(p -> new FileInfo(p)).toList();
                            // p -> new FileInfo(p) заменяется ссылкой на конструктор FileInfo::new
                            try (Stream<Path> pathStream = Files.list(path)) {
                                list = pathStream
                                        .map(FileInfo::new)
                                        .map(fi -> Prefs.encodeSpaces(fi.getFilename())+":"+fi.getSize()+":"+fi.getModifiedAsLong())
                                        .collect(Collectors.joining("\n"));
                                sz = list.split("\n").length;
                                if (sz == 1 && list.length() == 0) sz = 0;
                            } catch (IOException ex) {
                                errCode = Prefs.ErrorCode.ERR_NO_SUCH_FILE.ordinal();
                                ex.printStackTrace();
                            }
                        }
                        sendResponse(errCode < 0
                                ? Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.COM_GET_FILES,
                                  sz + " " + (subfolder ? arg[1] : ".") + (sz == 0 ? "" : " "+list))
                                : Prefs.getCommand(Prefs.SRV_REFUSE, Prefs.ErrorCode.ERR_NO_SUCH_FILE+""));
                    }
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_GET_SPACE))) {
                        freeSpace = Prefs.MAXSIZE - FileInfo.getSizes(getUserFolder());
                        sendResponse(Prefs.getCommand(Prefs.SRV_ACCEPT,
                                Prefs.COM_GET_SPACE, freeSpace+""));
                    }
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_UPLOAD))) {
                        // /upload source_path destination_path size [date]; "." for root destination
                        String[] arg = cmd.split(" ", 5);
                        long sz = 0L;
                        try { sz = Long.parseLong(arg[3]); }
                        catch (Exception ex) { ex.printStackTrace(); }
                        // перед выполнением проверять наличие достаточного свободного места
                        if (sz >= 0 && freeSpace-sz <= 0) // поправка для папок
                            sendResponse(Prefs.getCommand(Prefs.SRV_REFUSE, Prefs.ErrorCode.ERR_OUT_OF_SPACE+""));
                        else {
                            boolean success;
                            arg[1] = Prefs.decodeSpaces(arg[1]);
                            if (arg[2].equals(".")) arg[2] = "";
                            String src = Paths.get(arg[1]).toString(),
                                   dst = getUserFolder().resolve(Prefs.decodeSpaces(arg[2]))
                                           .resolve(arg[1].substring(arg[1]
                                                   .lastIndexOf(File.separatorChar)+1)).toString();
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
                                    if (arg.length == 5)
                                        try {
                                            FileInfo fi = new FileInfo(Paths.get(dst));
                                            fi.setModified(arg[4]);
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
                                    : Prefs.getCommand(Prefs.SRV_REFUSE, Prefs.ErrorCode.ERR_CANNOT_COMPLETE+"", Prefs.COM_UPLOAD));
                        }
                    }
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_DOWNLOAD))) {
                        // /download server_source_path destination_path size [date]
                        String[] arg = cmd.split(" ", 5);
                        long sz = 0L;
                        try { sz = Long.parseLong(arg[3]); }
                        catch (Exception ex) { ex.printStackTrace(); }
                        boolean success;
                        arg[1] = Prefs.decodeSpaces(arg[1]);
                        String src = getUserFolder().resolve(arg[1]).toString(),
                               dst = Prefs.decodeSpaces(Paths.get(arg[2], arg[1].substring(arg[1]
                                       .lastIndexOf(File.separatorChar)+1)).toString());
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
                            : Prefs.getCommand(Prefs.SRV_REFUSE, Prefs.ErrorCode.ERR_CANNOT_COMPLETE+"", Prefs.COM_DOWNLOAD));
                    }
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_REMOVE))) {
                        // /remove entry_name
                        String[] arg = cmd.split(" ", 2);
                        int errCode = -1;
                        try {
                            Files.delete(getUserFolder().resolve(Prefs.decodeSpaces(arg[1])));
                        } catch (IOException ex) {
                            errCode = Prefs.ErrorCode.ERR_NO_SUCH_FILE.ordinal();
                            if (ex instanceof DirectoryNotEmptyException)
                                errCode = Prefs.ErrorCode.ERR_NOT_EMPTY.ordinal();
                        }
                        sendResponse(errCode < 0
                                ? Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.COM_REMOVE, Prefs.SRV_SUCCESS+"")
                                : Prefs.getCommand(Prefs.SRV_REFUSE, errCode+"", Prefs.COM_REMOVE));
                    }
                }
            }
        } catch (Exception ex) { System.err.println("Connection was broken"); ex.printStackTrace(); }
    }
}