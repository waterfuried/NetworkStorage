import prefs.*;
import authService.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static prefs.Prefs.*;
import static prefs.Prefs.ErrorCode.*;

public class ClientHandler {
    private final DataInputStream is;
    private final DataOutputStream os;
    private final AuthService DBService;
    private long freeSpace;
    private Path userFolder;
    private final EventLogger logger;
    private final ArrayList<TransferOp> transfer = new ArrayList<>();
    private String uploadTarget;
    private int FSType = FS_UNK;

    public ClientHandler(EchoServer server, Socket socket, AuthService dbs, EventLogger logger) throws IOException {
        is = new DataInputStream(socket.getInputStream());
        os = new DataOutputStream(socket.getOutputStream());
        this.DBService = dbs;
        this.logger = logger;

        server.getThreadPool().execute(() -> {
            try { while (true) readInputStream(); }
            catch (Exception ex) { logger.logError(ex); }
            finally {
                server.onSocketClose(this);
                try { socket.close(); }
                catch (IOException ex) { logger.logError(ex); }
                logger.closeHandlers();
                // завершение работы обработчика клиента может происходить по инициативе клиента
                if (server.latch != null) server.latch.countDown();
            }
        });
    }

    // отправка ответа на запрос/команду пользователя
    public void sendResponse(String msg) {
        try { os.writeUTF(msg); }
        catch (IOException ex) { logger.logError(ex); }
    }

    // для избавления от обработки лишних запросов после авторизации, регистрации,
    // а также успешно выполненных операций с файлами клиенту, помимо ответа
    // на соответствующий запрос, отправляются также обновленния списка файлов
    // в его папке и размер свободного места в ней,
    // при авторизации/регистрации - также извещение о типе ФС сервера
    private void sendFreeSpace() {
        sendResponse(getCommand(SRV_ACCEPT, COM_GET_SPACE, freeSpace+""));
    }
    private void sendFilesList(String folder, boolean extractPath) {
        Path path = userFolder;
        int errCode = -1, sz = 0;
        String list = "", ofFolder = folder;
        if (!folder.equals("."))
            try {
                int i = extractPath ? folder.lastIndexOf(File.separatorChar) : -1;
                ofFolder = i < 0
                        ? extractPath ? "" : folder
                        : folder.substring(0,i);
                path = path.resolve(ofFolder);
            } catch (InvalidPathException ex) { errCode = ERR_NO_SUCH_FILE.ordinal(); }
        if (errCode < 0) {
            //List<FileInfo> list = FileInfo.getItemsInfo(path);
            // можно заменить Files.list(path).map(p -> new FileInfo(p)).toList();
            // p -> new FileInfo(p) заменяется ссылкой на конструктор FileInfo::new
            try (Stream<Path> pathStream = Files.list(path)) {
                list = pathStream
                        .map(FileInfo::new)
                        .map(fi -> encodeSpaces(fi.getName())+":"+fi.getSize()+":"+fi.getModifiedAsLong())
                        .collect(Collectors.joining("\n"));
                sz = list.split("\n").length;
                if (sz == 1 && list.length() == 0) sz = 0;
            } catch (IOException ex) {
                errCode = ERR_NO_SUCH_FILE.ordinal();
                logger.logError(ex);
            }
        }
        sendResponse(errCode < 0
                ? getCommand(SRV_ACCEPT, COM_GET_FILES, sz +" "+ ofFolder + (sz == 0 ? "" : " "+list))
                : getCommand(SRV_REFUSE, ERR_NO_SUCH_FILE+""));
    }
    private void sendFSType() {
        if (FSType == FS_UNK) FSType = getFSType(userFolder);
        sendResponse(getCommand(SRV_ACCEPT, COM_GET_FS, FSType+""));
    }

    private void sendOpFailedResponse(int errCode) {
        sendResponse(getCommand(SRV_REFUSE, errCode));
    }
    private void sendOpFailedResponse(int errCode, String cmd) {
        sendResponse(getCommand(SRV_REFUSE, errCode+"", cmd));
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
    void readInputStream() throws Exception {
        String cmd = is.readUTF();
        if (!cmd.startsWith(COM_ID)) return;
        // s - только для обработки команд, регистр в ее аргументах важен - для них cmd
        String s = cmd.toLowerCase();

        // запрос авторизации
        if (s.startsWith(getCommand(COM_AUTHORIZE))) {
            int errCode = -1;
            String[] val = cmd.split(" ", 3);
            String newUser = DBService.getUserInfo(val[1], Integer.parseInt(val[2]));
            if (newUser != null)
                if (newUser.length() > 0) {
                    String[] userdata = newUser.split("\t");
                    if (userdata.length > 1) {
                        newUser = userdata[0];
                        userFolder = serverURL.resolve("user" + userdata[1]);
                        boolean b;
                        try {
                            b = new File(userFolder.toString()).exists();
                            if (!b) b = new File(userFolder.toString()).mkdir();
                        } catch (Exception ex) {
                            b = false;
                            logger.logError(ex);
                        }
                        if (!b) errCode = ERR_INTERNAL_ERROR.ordinal();
                    }
                } else
                    errCode = ERR_INTERNAL_ERROR.ordinal();
            else
                errCode = ERR_WRONG_AUTH.ordinal();
            // в случае успеха вернуть доп. код 0 и имя пользователя,
            // в случае ошибки - соответствующий ей код
            if (errCode < 0) {
                freeSpace = MAXSIZE - FileInfo.getSizes(userFolder);
                sendResponse(getCommand(SRV_ACCEPT, SRV_SUCCESS + "", newUser));
                sendFreeSpace();
                sendFilesList(".", false);
                sendFSType();
            } else
                sendOpFailedResponse(errCode);
        }

        // запрос регистрации
        if (s.startsWith(getCommand(COM_REGISTER))) {
            // /reg логин пароль имя-пользователя email
            int errCode = -1;
            String[] val = cmd.split(" ", 5);
            int number = 0;
            String newUser = val[2].length() < MIN_PWD_LEN || val[2].length() > MAX_PWD_LEN
                    ? null
                    : (number = DBService.registerUser(
                            // с шаблоном "Строитель"
                            new AuthData.AuthBuilder()
                                    .login(val[1])
                                    .password(encode(decodeSpaces(val[2]), false))
                                    .username(val[3])
                                    .userData(new String[]{ val[4] })
                                    .build())) > 0
                            // ... и без него
                            // val[1], encode(decodeSpaces(val[2]),false), val[3], val[4])) > 0
                        ? val[3] : null;
            if (newUser != null) {
                userFolder = serverURL.resolve("user" + number);
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
                if (!b) errCode = ERR_INTERNAL_ERROR.ordinal();
            } else
                errCode = number == 0
                            ? ERR_WRONG_REG.ordinal()
                            : number == -1
                                ? ERR_INTERNAL_ERROR.ordinal()
                                : ERR_DB_OVERFLOW.ordinal();
            // в случае успеха вернуть доп. код 0 и имя пользователя,
            // в случае ошибки - соответствующий ей код
            if (errCode < 0) {
                freeSpace = MAXSIZE;
                sendResponse(getCommand(SRV_ACCEPT, SRV_SUCCESS + "", newUser));
                sendFreeSpace();
                sendFilesList(".", false);
                sendFSType();
            } else
                sendOpFailedResponse(errCode);
        }

        // команда (запрос) завершения сеанса
        // получив от клиента запрос на завершение сеанса, отправить запрос обратно
        if (isExitCommand(s)) sendResponse(cmd);

        // запрос содержимого папки пользователя
        if (s.startsWith(getCommand(COM_GET_FILES))) {
            String[] arg = cmd.split(" ");
            sendFilesList(arg.length > 1 ? arg[1] : ".", false);
        }

        // запрос количества свободного места в папке пользователя
        if (s.startsWith(getCommand(COM_GET_SPACE))) sendFreeSpace();

        // запрос размера подпапки в папке пользователя
        if (s.startsWith(getCommand(COM_GET_SIZE))) {
            String[] arg = cmd.split(" ");
            Path p = userFolder.resolve(arg[1]);
            sendResponse(getCommand(SRV_ACCEPT, COM_GET_SIZE,
                    FileInfo.getSizes(p)+" "+FileInfo.getItems(p).size()));
        }

        // запрос копирования файла/папки на сервер
        // /upload source_name destination_path size date
        // "." for root destination
        if (s.startsWith(getCommand(COM_UPLOAD))) {
            String[] arg = cmd.split(" ");
            long size, modified;
            try {
                size = Long.parseLong(arg[3]);
                modified = Long.parseLong(arg[4]);
            } catch (Exception ex) {
                sendOpFailedResponse(ERR_INTERNAL_ERROR.ordinal(), COM_UPLOAD);
                return;
            }
            arg[1] = decodeSpaces(arg[1]);
            uploadTarget = arg[2].equals(".") ? "" : decodeSpaces(arg[2]);
            Path dst = userFolder.resolve(uploadTarget).resolve(arg[1]);

            int errCode = -1;
            long curSize = 0L;
            try {
                if (Files.exists(dst))
                    curSize = Files.isDirectory(dst) ? -1L : Files.size(dst);
            } catch (Exception ex) { ex.printStackTrace(); }

            // создать новую папку или файл нулевого размера
            // обновление даты и времени: если по какой-то причине оно не произошло,
            // операция в целом не выполнена
            try { makeFolderOrZero(dst, size, modified); }
            catch (Exception ex) { errCode = ERR_CANNOT_COMPLETE.ordinal(); }
            if (errCode < 0) {
                int id = SRV_SUCCESS;
                if (curSize >= 0L && size == 0L)
                    // освобождение места при замене непустого файла пустым
                    freeSpace += curSize;
                else
                    // проверить наличие достаточного места для копирования непустого файла
                    if (size > 0L && freeSpace-(size-curSize) <= 0L)
                        errCode = ERR_OUT_OF_SPACE.ordinal();
                    else
                        if (size > 0L)
                            id = 1+startTransfer(dst.toString(), curSize, size, modified);
                if (id == SRV_SUCCESS && errCode < 0) {
                    if (size != curSize) sendFreeSpace();
                    sendFilesList(arg[2], false);
                }
                sendResponse(getCommand(SRV_ACCEPT, COM_UPLOAD, id+""));
                if (errCode < 0) return;
            }
            sendOpFailedResponse(errCode, COM_UPLOAD);
        }

        // передача данных копируемого на сервер файла
        // /upld id block_size data_block
        if (s.startsWith(getCommand(COM_UPLOAD_DATA))) {
            String[] arg = cmd.split(" ", 4);
            int id = Integer.parseInt(arg[1])-1;
            long size = Long.parseLong(arg[2]);
            boolean spaceChanged = size != transfer.get(id).getCurSize();
            byte[] buf = Arrays.copyOf(Base64.getDecoder().decode(arg[3]), arg[3].length());
            // при использовании любых стандартных однобайтных кодировок
            // происходит преобразование символов - старше 127 кодируются в другие,
            // UTF-8 кодирует символы до 128 одним байтом, со 128 - двумя
            //buf = Arrays.copyOf(arg[3].getBytes(StandardCharsets.US_ASCII),arg[3].length());
            String dst = transfer.get(id).getPath();
            boolean success = true;
            try (BufferedOutputStream bos = new BufferedOutputStream(
                    new FileOutputStream(dst, true), BUF_SIZE)) {
                        bos.write(buf, 0, (int)size);
            } catch (Exception ex) {
                ex.printStackTrace();
                success = false;
            }
            if (success) {
                transfer.get(id).setReceived(transfer.get(id).getReceived() + size);
                if (transfer.get(id).getReceived() == transfer.get(id).getNewSize()) {
                    try {
                        // установить дату и время последней модификации как у оригинала
                        success = applyDateTime(dst, transfer.get(id).getModified());
                        if (success) {
                            freeSpace -= transfer.get(id).getNewSize()-transfer.get(id).getCurSize();
                            if (spaceChanged) sendFreeSpace();
                            sendFilesList(uploadTarget.length() == 0 ? "." : uploadTarget, false);
                            sendResponse(getCommand(SRV_ACCEPT, COM_UPLOAD, SRV_SUCCESS + ""));
                        }
                    } catch (Exception ex) {
                        success = false;
                        ex.printStackTrace();
                    }
                    transfer.remove(id);
                }
                if (success) return;
            }
            sendOpFailedResponse(ERR_CANNOT_COMPLETE.ordinal(), COM_UPLOAD);
        }

        // копирование файла с сервера: только отправка файлов ненулевого размера
        // /download source_path
        if (s.startsWith(getCommand(COM_DOWNLOAD))) {
            String[] arg = cmd.split(" ", 0);
            arg[1] = decodeSpaces(arg[1]);
            byte[] buf = new byte[BUF_SIZE];
            try (BufferedInputStream bis = new BufferedInputStream(
                    Files.newInputStream(userFolder.resolve(arg[1])), BUF_SIZE)) {
                int bytesRead;
                while ((bytesRead = bis.read(buf)) > 0)
                    sendResponse(getCommand(SRV_ACCEPT, COM_DOWNLOAD,
                            bytesRead + "", Base64.getEncoder().encodeToString(buf)));
            } catch (Exception ex) {
                sendOpFailedResponse(ERR_CANNOT_COMPLETE.ordinal(), COM_DOWNLOAD);
                ex.printStackTrace();
            }
        }

        // запрос удаления файла/папки
        // /remove entry_name
        if (s.startsWith(getCommand(COM_REMOVE))) {
            String[] arg = cmd.split(" ", 2);
            int errCode = -1;
            long freed = 0;
            try {
                Path p = userFolder.resolve(decodeSpaces(arg[1]));
                freed = Files.size(p);
                Files.delete(p);
            } catch (IOException ex) {
                errCode = ERR_NO_SUCH_FILE.ordinal();
                if (ex instanceof DirectoryNotEmptyException)
                    errCode = ERR_NOT_EMPTY.ordinal();
            }
            if (errCode < 0) {
                freeSpace += freed;
                sendFreeSpace();
                sendFilesList(arg[1], true);
                sendResponse(getCommand(SRV_ACCEPT, COM_REMOVE, SRV_SUCCESS + ""));
            } else
                sendOpFailedResponse(errCode, COM_REMOVE);
        }

        // запрос переименования файла/папки
        // /rename current_name_with_path new_name
        if (s.startsWith(getCommand(COM_RENAME))) {
            String[] arg = cmd.split(" ");
            arg[1] = decodeSpaces(arg[1]); arg[2] = decodeSpaces(arg[2]);
            Path p = userFolder.resolve(arg[1]);
            long freed = Files.exists(p.resolveSibling(arg[2]))
                    ? Files.size(p.resolveSibling(arg[2]))-Files.size(p)
                    : 0L;
            if (freed < 0L) freed = Math.min(Files.size(p.resolveSibling(arg[2])),Files.size(p));
            int errCode = rename(p, arg[2]);
            if (errCode < 0) {
                if (freed > 0L) {
                    freeSpace += freed;
                    sendFreeSpace();
                }
                sendFilesList(arg[1], true);
                sendResponse(getCommand(SRV_ACCEPT, COM_RENAME, SRV_SUCCESS + ""));
            } else
                sendOpFailedResponse(errCode, COM_RENAME);
        }

        // запрос локального копирования/перемещения файла/папки
        // /copy source_path destination_path [1=move]
        if (s.startsWith(getCommand(COM_COPY))) {
            String[] arg = cmd.split(" ");
            arg[1] = decodeSpaces(arg[1]); arg[2] = decodeSpaces(arg[2]);
            if (arg.length == 3 && freeSpace-Files.size(userFolder.resolve(arg[1])) <= 0L)
                sendOpFailedResponse(ERR_OUT_OF_SPACE.ordinal(), COM_COPY);
            else
                try {
                    int i = arg[1].lastIndexOf(File.separatorChar);
                    String entry = i < 0 ? arg[1]: arg[1].substring(i+1);
                    copy(userFolder.resolve(arg[1]),
                            (arg[2].equals(".") ? userFolder : userFolder.resolve(arg[2])).resolve(entry),
                            arg.length > 3);
                    if (arg.length > 3)
                        sendFilesList(arg[1], true);
                    else {
                        freeSpace -= Files.size(userFolder.resolve(arg[1]));
                        sendFreeSpace();
                    }
                    sendFilesList(arg[2], false);
                    sendResponse(getCommand(SRV_ACCEPT, arg.length > 3 ? COM_MOVE : COM_COPY,
                            SRV_SUCCESS + "", encodeSpaces(entry)));
                } catch (Exception ex) {
                    sendOpFailedResponse(ERR_CANNOT_COMPLETE.ordinal(),
                            arg.length > 3 ? COM_MOVE : COM_COPY);
                }
        }
    }
}