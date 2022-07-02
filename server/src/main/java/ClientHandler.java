import prefs.*;
import authService.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.stream.*;

public class ClientHandler implements Runnable {
    private final DataInputStream is;
    private final DataOutputStream os;
    private final AuthService DBService;
    private long freeSpace;
    private Path userFolder;
    private final EventLogger logger;

    public Path getUserFolder() { return userFolder; }

    public void setUserFolder(String folder) { this.userFolder = Prefs.serverURL.resolve(folder); }

    public ClientHandler(Socket socket, AuthService dbs, EventLogger logger) throws IOException {
        is = new DataInputStream(socket.getInputStream());
        os = new DataOutputStream(socket.getOutputStream());
        this.DBService = dbs;
        this.logger = logger;
        System.out.println("Client accepted");
    }

    // отправка ответа на запрос/команду пользователя
    public void sendResponse(String msg) {
        try { os.writeUTF(msg); }
        catch (IOException ex) { logger.logError(ex); }
    }

    // для избавления от обработки лишних запросов после авторизации, регистрации,
    // а также успешно выполненных операций с файлами клиенту, помимо ответа
    // на соответствующий запрос, отправляются также обновленния списка файлов
    // в его папке и размер свободного места в ней
    private void sendFreeSpace() {
        sendResponse(Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.COM_GET_SPACE, freeSpace+""));
    }
    private void sendFilesList(String folder) {
        Path path = getUserFolder();
        int errCode = -1, sz = 0;
        String list = "";
        boolean subfolder = !folder.equals(".");
        if (subfolder)
            try { path = path.resolve(folder); }
            catch (InvalidPathException ex) { errCode = Prefs.ErrorCode.ERR_NO_SUCH_FILE.ordinal(); }
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
                logger.logError(ex);
            }
        }
        sendResponse(errCode < 0
                ? Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.COM_GET_FILES,
                sz + " " + folder + (sz == 0 ? "" : " "+list))
                : Prefs.getCommand(Prefs.SRV_REFUSE, Prefs.ErrorCode.ERR_NO_SUCH_FILE+""));
    }
    private void sendOpFailedResponse(int errCode) {
        sendResponse(Prefs.getCommand(Prefs.SRV_REFUSE, errCode));
    }
    private void sendOpFailedResponse(String ... param) {
        sendResponse(Prefs.getCommand(Prefs.SRV_REFUSE, param));
    }

    // обработка команд/запросов клиента
    @Override public void run() {
        try {
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
                        if (newUser != null)
                            if (newUser.length() > 0) {
                                String[] userdata = newUser.split("\t");
                                if (userdata.length > 1) {
                                    newUser = userdata[0];
                                    setUserFolder("user" + userdata[1]);
                                    boolean b;
                                    try {
                                        b = new File(getUserFolder().toString()).exists();
                                        if (!b) b = new File(getUserFolder().toString()).mkdir();
                                    } catch (Exception ex) {
                                        b = false;
                                        logger.logError(ex);
                                    }
                                    if (!b) errCode = Prefs.ErrorCode.ERR_INTERNAL_ERROR.ordinal();
                                }
                            } else
                                errCode = Prefs.ErrorCode.ERR_INTERNAL_ERROR.ordinal();
                        else
                            errCode = Prefs.ErrorCode.ERR_WRONG_AUTH.ordinal();
                        // в случае успеха вернуть доп. код 0 и имя пользователя,
                        // в случае ошибки - соответствующий ей код
                        if (errCode < 0) {
                            freeSpace = Prefs.MAXSIZE - FileInfo.getSizes(getUserFolder());
                            sendResponse(Prefs.getCommand(Prefs.SRV_ACCEPT,
                                    Prefs.SRV_SUCCESS+"", newUser));
                            sendFreeSpace();
                            sendFilesList(".");
                        } else
                            sendOpFailedResponse(errCode);
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
                                // при регистрации нового пользователя
                                // определенная для него папка не должна существовать
                                b = !new File(userFolder.toString()).exists()
                                  && new File(userFolder.toString()).mkdir();
                            } catch (Exception ex) {
                                b = false;
                                logger.logError(ex);
                            }
                            if (!b) errCode = Prefs.ErrorCode.ERR_INTERNAL_ERROR.ordinal();
                        } else
                            errCode = number == 0
                                        ? Prefs.ErrorCode.ERR_WRONG_REG.ordinal()
                                        : number == -1
                                            ? Prefs.ErrorCode.ERR_INTERNAL_ERROR.ordinal()
                                            : Prefs.ErrorCode.ERR_DB_OVERFLOW.ordinal();
                        // в случае успеха вернуть доп. код 0 и имя пользователя,
                        // в случае ошибки - соответствующий ей код
                        if (errCode < 0) {
                            freeSpace = Prefs.MAXSIZE;
                            sendResponse(Prefs.getCommand(Prefs.SRV_ACCEPT,
                                    Prefs.SRV_SUCCESS+"", newUser));
                            sendFreeSpace();
                            sendFilesList(".");
                        } else
                            sendOpFailedResponse(errCode);
                    }
                    // команда завершения сеанса
                    // получив от клиента запрос на завершение сеанса, отправить запрос обратно
                    if (Prefs.isExitCommand(s)) sendResponse(cmd);
                    // запрос содержимого папки пользователя
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_GET_FILES))) {
                        String[] arg = cmd.split(" ", 2);
                        sendFilesList(arg.length > 1 ? arg[1] : ".");
                    }
                    // запрос количества свободного места в папке пользователя
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_GET_SPACE))) sendFreeSpace();
                    // /upload source_path destination_path size [date] [overwrite]
                    // "." for root destination
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_UPLOAD))) {
                        String[] arg = cmd.split(" ", 6);
                        long size = 0L;
                        try { size = Long.parseLong(arg[3]); }
                        catch (Exception ex) { logger.logError(ex); }
                        int overWrite = -1;
                        String modified = null;
                        if (arg.length >= 5) {
                            String option = null;
                            if (arg.length == 6) {
                                if (Prefs.isValidOption(arg[5])) option = arg[5];
                                modified = arg[4];
                            } else
                                if (Prefs.isValidOption(arg[4])) option = arg[4];
                            if (option != null && option.equals(Prefs.COM_OPTION_OVERWRITE)) overWrite = 1;
                        }
                        arg[1] = Prefs.decodeSpaces(arg[1]);
                        if (arg[2].equals(".")) arg[2] = "";
                        String src = Paths.get(arg[1]).toString(),
                               entry = arg[1].substring(arg[1].lastIndexOf(File.separatorChar)+1);
                        Path dst = getUserFolder().resolve(Prefs.decodeSpaces(arg[2])).resolve(entry);
                        // перед выполнением проверять наличие файла и достаточного свободного места
                        long oldSize = 0L; // добавление нового файла/папки
                        boolean exists = Files.exists(dst);
                        if (exists)
                            if (overWrite < 0)
                                sendResponse(Prefs.getCommand(Prefs.SRV_REFUSE,
                                        Prefs.CONFIRM_OVERWRITE + "",
                                        Prefs.encodeSpaces(entry), Files.isDirectory(dst) ? "0" : "1"));
                            else
                                oldSize = Files.size(dst); // замена существующего файла/папки
                        else
                            overWrite = 0;
                        if (overWrite >= 0) {
                            if (size >= 0 && size >= oldSize && freeSpace-(size-oldSize) <= 0)
                                sendOpFailedResponse(Prefs.ErrorCode.ERR_OUT_OF_SPACE.ordinal());
                            else {
                                boolean isFile = exists ? !Files.isDirectory(dst) : size >= 0,
                                        success = (isFile && size >= 0) || (!isFile && size < 0);
                                if (success)
                                    if (size >= 0) { // файл
                                        Prefs.doCopying(src, dst.toString());
                                        success = Files.size(dst) == size;
                                    } else // папка
                                        success = new File(dst.toString()).mkdir();
                                if (success) {
                                    freeSpace -= size-oldSize;
                                    // установить дату и время последней модификации как у оригинала
                                    if (modified != null)
                                        try {
                                            FileInfo fi = new FileInfo(dst);
                                            fi.setModified(modified);
                                            // если по какой-то причине дату/время применить не удалось,
                                            // считать это неудачей всей операции в целом не стоит
                                            new File(dst.toString()).setLastModified(fi.getModifiedAsLong());
                                        } catch (NumberFormatException ex) { logger.logError(ex); }
                                    sendResponse(Prefs.getCommand(Prefs.SRV_ACCEPT,
                                            Prefs.COM_UPLOAD, Prefs.SRV_SUCCESS+""));
                                    sendFreeSpace();
                                    sendFilesList(arg[2].length() == 0 ? "." : arg[2]);
                                } else
                                    sendOpFailedResponse(Prefs.ErrorCode.ERR_CANNOT_COMPLETE+"",
                                            Prefs.COM_UPLOAD);
                            }
                        }
                    }
                    // /download server_source_path destination_path size [date]
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_DOWNLOAD))) {
                        String[] arg = cmd.split(" ", 5);
                        long size = 0L;
                        try { size = Long.parseLong(arg[3]); }
                        catch (Exception ex) { logger.logError(ex); }
                        arg[1] = Prefs.decodeSpaces(arg[1]);
                        String src = getUserFolder().resolve(arg[1]).toString(),
                               dst = Prefs.decodeSpaces(Paths.get(arg[2], arg[1].substring(arg[1]
                                       .lastIndexOf(File.separatorChar)+1)).toString());
                        boolean isFile = !new File(dst).isDirectory(),
                                success = (isFile && size >= 0) || (!isFile && size < 0);
                        if (success)
                            if (size >= 0) { // файл
                                Prefs.doCopying(src, dst);
                                success = new File(dst).length() == size;
                            } else // папка
                                success = new File(dst).mkdir();
                        // установить дату и время последней модификации как у оригинала
                        if (success && arg.length == 5)
                            try {
                                FileInfo fi = new FileInfo(Paths.get(dst));
                                fi.setModified(arg[4]);
                                new File(dst).setLastModified(fi.getModifiedAsLong());
                            } catch (NumberFormatException ex) { logger.logError(ex); }
                        sendResponse(success
                            ? Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.COM_DOWNLOAD, Prefs.SRV_SUCCESS+"")
                            : Prefs.getCommand(Prefs.SRV_REFUSE, Prefs.ErrorCode.ERR_CANNOT_COMPLETE+"",
                                Prefs.COM_DOWNLOAD));
                    }
                    // /remove entry_name
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_REMOVE))) {
                        String[] arg = cmd.split(" ", 2);
                        int errCode = -1;
                        long freed = 0;
                        try {
                            Path p = getUserFolder().resolve(Prefs.decodeSpaces(arg[1]));
                            freed = Files.size(p);
                            Files.delete(p);
                        } catch (IOException ex) {
                            errCode = Prefs.ErrorCode.ERR_NO_SUCH_FILE.ordinal();
                            if (ex instanceof DirectoryNotEmptyException)
                                errCode = Prefs.ErrorCode.ERR_NOT_EMPTY.ordinal();
                        }
                        if (errCode < 0) {
                            sendResponse(Prefs.getCommand(Prefs.SRV_ACCEPT,
                                    Prefs.COM_REMOVE, Prefs.SRV_SUCCESS+""));
                            freeSpace += freed;
                            sendFreeSpace();
                            int i = arg[1].lastIndexOf(File.separatorChar);
                            sendFilesList(i < 0 ? "." : arg[1].substring(0, i));
                        } else
                            sendOpFailedResponse(errCode+"", Prefs.COM_REMOVE);
                    }
                }
            }
        } catch (Exception ex) { System.err.println("Connection was broken"); logger.logError(ex); }
    }
}