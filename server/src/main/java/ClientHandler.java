import prefs.*;
import authService.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class ClientHandler implements Runnable {
    private final DataInputStream is;
    private final DataOutputStream os;
    private final AuthService DBService;
    private long freeSpace;
    private Path userFolder;
    private final EventLogger logger;
    private final ArrayList<TransferOp> transfer = new ArrayList<>();
    private String uploadTarget;

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

    private int startTransfer(String path, long oldSize, long newSize, long modified) {
        int id = 0;
        if (transfer.size() > 0)
            while (id < transfer.size() && transfer.get(id) != null) id++;
        if (id == transfer.size())
            transfer.add(new TransferOp(path, oldSize, newSize, modified));
        else
            transfer.set(id, new TransferOp(path, oldSize, newSize, modified));
        return id;
    }

    // обработка команд/запросов клиента
    @Override public void run() {
        try {
            while (true) {
                String cmd = is.readUTF();
                System.out.println("received: "+
                        (cmd.length() <= Prefs.SHOWABLE_SIZE ? cmd : cmd.substring(0, Prefs.SHOWABLE_SIZE)));
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
                    // /upload source_path destination_path size [date]
                    // "." for root destination
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_UPLOAD))) {
                        String[] arg = cmd.split(" ", 5);
                        long size = 0L;
                        try { size = Long.parseLong(arg[3]); }
                        catch (Exception ex) { logger.logError(ex); }
                        long modified = arg.length > 4 ? Long.parseLong(arg[4]) : 0L;
                        arg[1] = Prefs.decodeSpaces(arg[1]);
                        uploadTarget = arg[2].equals(".") ? "" : Prefs.decodeSpaces(arg[2]);
                        Path dst = getUserFolder().resolve(uploadTarget).resolve(arg[1]);
                        // перед выполнением проверять наличие файла и достаточного свободного места
                        int id = 0, errCode = -1;
                        long oldSize = 0L;
                        boolean updateModified = false, checkSpace = false;
                        if (Files.exists(dst)) {
                            oldSize = Files.isDirectory(dst) ? -1L : Files.size(dst);
                            if (size > 0L)
                                checkSpace = true;
                            else {
                                updateModified = oldSize <= 0L;
                                if (oldSize > 0L)
                                    if (Prefs.resetFile(dst)) {
                                        updateModified = true;
                                        freeSpace += oldSize;
                                    } else
                                        errCode = Prefs.ErrorCode.ERR_CANNOT_COMPLETE.ordinal();
                            }
                        } else
                            if (size <= 0L)
                                if (size < 0L ? new File(dst.toString()).mkdir() : Prefs.resetFile(dst))
                                    updateModified = true;
                                else
                                    errCode = Prefs.ErrorCode.ERR_CANNOT_COMPLETE.ordinal();
                            else
                                checkSpace = true;
                        if (errCode < 0) {
                            if (checkSpace)
                                if (freeSpace-(size-oldSize) <= 0L)
                                    errCode = Prefs.ErrorCode.ERR_OUT_OF_SPACE.ordinal();
                                else
                                    id = 1+startTransfer(dst.toString(), oldSize, size, modified);
                            // обновление даты и времени происходит только для папок и пустых файлов,
                            // если по какой-то причине оно не произошло, операция в целом не выполнена
                            if (updateModified && modified > 0
                                    && !new File(dst.toString()).setLastModified(modified))
                                errCode = Prefs.ErrorCode.ERR_CANNOT_COMPLETE.ordinal();
                        }
                        if (errCode < 0) {
                            sendResponse(Prefs.getCommand(Prefs.SRV_ACCEPT,
                                    Prefs.COM_UPLOAD, (id > 0 ? id : Prefs.SRV_SUCCESS) + ""));
                            if (id == 0 && (size < 0 || size != oldSize || updateModified)) {
                                if (size != oldSize) sendFreeSpace();
                                sendFilesList(arg[2]);
                            }
                        } else
                            sendOpFailedResponse(errCode+"", Prefs.COM_UPLOAD);
                    }
                    // /upld id block_size data_block
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_UPLOAD_DATA))) {
                        String[] arg = cmd.split(" ", 4);
                        int id = Integer.parseInt(arg[1])-1;
                        long size = Long.parseLong(arg[2]);
                        byte[] buf = Arrays.copyOf(Base64.getDecoder().decode(arg[3]),arg[3].length());
                        // при использовании любых стандартных однобайтных кодировок
                        // происходит преобразование символов - старше 127 кодируются в другие,
                        // UTF-8 кодирует символы до 128 одним байтом, со 128 - двумя
                        //byte[] buf = Arrays.copyOf(arg[3].getBytes(StandardCharsets.US_ASCII),arg[3].length());
                        String dst = transfer.get(id).getPath();
                        boolean success = true;
                        try (BufferedOutputStream bos = new BufferedOutputStream(
                                 new FileOutputStream(dst, true), Prefs.BUF_SIZE)) {
                            bos.write(buf, 0, (int)size);
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
                                    catch (NumberFormatException ex) { logger.logError(ex); }
                                size = transfer.get(id).getNewSize();
                                long oldSize = transfer.get(id).getOldSize();
                                freeSpace -= size-oldSize;
                                transfer.remove(id);
                                sendResponse(Prefs.getCommand(Prefs.SRV_ACCEPT,
                                        Prefs.COM_UPLOAD, Prefs.SRV_SUCCESS+""));
                                if (size != oldSize) sendFreeSpace();
                                sendFilesList(uploadTarget.length() == 0 ? "." : uploadTarget);
                            }
                        } else
                            sendOpFailedResponse(Prefs.ErrorCode.ERR_CANNOT_COMPLETE+"",
                                    Prefs.COM_UPLOAD);
                    }
                    // только отправка файлов ненулевого размера
                    // /download source_path// destination_path//size [date]
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_DOWNLOAD))) {
                        String[] arg = cmd.split(" ", 2);
                        arg[1] = Prefs.decodeSpaces(arg[1]);
                        byte[] buf = new byte[Prefs.BUF_SIZE];
                        try (BufferedInputStream bis = new BufferedInputStream(
                                Files.newInputStream(getUserFolder().resolve(arg[1])), Prefs.BUF_SIZE)) {
                            int bytesRead;
                            while ((bytesRead = bis.read(buf)) > 0)
                                sendResponse(Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.COM_DOWNLOAD,
                                                bytesRead+"", Base64.getEncoder().encodeToString(buf)));
                        } catch (Exception ex) { ex.printStackTrace(); }
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
                    // /exists entry_name
                    // возвращает
                    //     - истинный размер элемента, если он существует (-1 для папок, 0+ для файлов)
                    //     - -2, если элемент не существует
                    if (s.startsWith(Prefs.getCommand(Prefs.COM_EXISTS))) {
                        String[] arg = cmd.split(" ", 2);
                        if (arg.length == 2) {
                            Path dst = getUserFolder().resolve(Prefs.decodeSpaces(arg[1]));
                            sendResponse(
                                Prefs.getCommand(Prefs.SRV_ACCEPT, Prefs.COM_EXISTS,
                                    (Files.exists(dst)
                                        ? Files.isDirectory(dst) ? -1L : Files.size(dst)
                                        : -2L)+""));
                        }
                    }
                }
            }
        } catch (Exception ex) { System.err.println("Connection was broken"); logger.logError(ex); }
    }
}