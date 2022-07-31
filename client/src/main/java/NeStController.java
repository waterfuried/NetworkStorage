/*
    > на сервере у пользователя есть папка,
      в пределах которой он может размещать файлы и папки.
    > их число не ограничено,
      но суммарный их размер не должен превышать N.
    > выйти за пределы своей папки пользователь не может.

      папка имеет уникальное имя; поскольку имена пользователей могут повторяться, а как их имена
      (или никнеймы), так и логины - содержать недопустмые для ФС символы, использовать их для
      имени папки не стоит - ее имя имеет вид "userN", где N определяется по запросу к БД:
        для каждого пользователя в ней хранятся указанные при регистрации его логин,
        пароль, имя, личные данные (типа ФИО (и/или адреса email)) и номер N его папки.
        Для нового пользователя создается папка или с первым свободным (папки некоторых
        пользователей могли быть удалены), или с номером, равным максимальному
        из наденных плюс 1.

    команды/запросы, возвращают
        - в случае успешного завершения команды/запроса:
            NEST_DONE [имя_команды/запроса] дополнительный_код;
             доп. код в большинстве случаев равен 0, если он имеет
             особое значение, это указывается в описании;
             поскольку без авторизации/регистрации никакие действия не могут быть выполнены,
             при регистрации новый пользователь автоматически авторизуется,
             различать ответы на запрос авторизации/регистрации
             и возвращать после NEST_DONE имя запроса нет смысла;
        - в случае ошибки:
            NEST_ERR код-ошибки [имя_команды/запроса] -
              0 - не верное имя пользователя или пароль,
              1 - запрошенный файл/папка отсутствует,
              2 - недостаточно места для загрузки файла,
              3 - ошибка чтения файла/папки,
              4 - полученный список файлов содержит ошибки,
              5 - папка не пуста и не может быть удалена,
              6 - внутренняя ошибка сервера,
              7 - ошибка регистрации пользователя,
              8 - количество зарегистрированных пользователей достигло максимума,
              9 - невозможно заменить файл папкой,
             10 - новое имя файла/папки содержит недопустимые символы,
             11 - доступ к папке не возможен,
             12 - команда переименования не предназначена для перемещения.
          ! в процессе разработки в протокол было внесено дополнение:
          ! несмотря на то, что коды ошибок более или менее однозначно определяют запрос/команду,
          ! при которых они произошли, во избежание недоразумений, для некоторых запросов/команд,
          ! например, при отправке файла с/на сервер, после кода ошибки укзывается имя команды
          ! (запроса) - это позволит корректно обрабатывать ситуации неполной передачи данных;

    1. авторизация пользователя по логину и паролю;
       длина пароля должна быть не меньше 4 и не более 12:
         /user логин хеш-пароля
       после доп. кода 0 возвращается имя пользователя, если он зарегистрирован
    2. завершить сеанс пользователя:
         /quit
         /exit
    3. вернуть список файлов в папке пользователя на сервере.
       если нужно вернуть содержимое корневой папки пользователя,
       имя указывается как ".";
       если запрошенной папки нет на сервере, вернуть код ошибки 1:
         /files имя
       если в запрашиваемой папке нет файлов, доп. код равен 0, иначе он равен
       числу файлов в ней, за которым возвращается список соответствующей длины,
       содержащий последовательность из имени, размера и даты файла/папки,
       элементы последовательности разделены знаком :,
       элементы списка - переносом на новую строку
    4. вернуть размер свободного места на сервере:
         /space,
       размер в байтах возвращается в значении доп. кода.
    5. копировать файл/папку с клиента на сервер:
       5.1. инициировать передачу
         /upload имя-файла/папки-на-компьютере-клиента путь-на-сервере размер дата [1]*
             *замена существующего файла: если аргумент не указан, будет выведен запрос
       сервер возвращает:
        - 0, при успешном создании передаваемго элемента, если это файл нулевого размера или папка,
             а также после завершения передачи данных (файла ненулевого размера)
        - id передачи данных, если передаваемый элемент - файл ненулевого размера
        - код ошибки:
            - не удалось создать файл/папку (ошибка ФС сервера),
            - недостаточно места для сохранения копируемого файла,
            - файл/папка уже существует (возможны варианты, например, попытка скопировать папку,
              когда существует файл с тем же именем, допустимый вариант - замена файла файлом,
              но в этом случае нужно подтверждение от пользователя)
       5.2. передать блок данных файла на сервер
         /upld id размер-блока-данных блок-данных
    6. копировать файл/папку с сервера на клиент:
         /download путь-с-именем-файла/папки-на-сервере
    7. удалить файл/папку на сервере:
         /remove имя_файла/папки
       папка не может быть удалена, если она не пуста
    8. зарегистрировать нового пользователя;
       длина пароля должна быть не меньше 4 и не более 12 символов:
         /reg логин пароль имя-пользователя email
       после доп. кода 0 возвращается имя пользователя, если он зарегистрирован
    9. переименовать файл/папку
         /rename путь_к_существующему_имени новое_имя [1=заменять при совпадении имени]

    команды 5 и 6 работают по принципу "1 за раз" - например,
      при копировании папки на сервер (размер указывается как -1)
      она только создается на нем, но ничем не наполняется,
      даже если на компьютере клиента в ней что-либо есть;
    команды копирования и переименования (5, 6, 9)
       NB: в ФС семейств FAT и NTFS регистр символов в именах файлов не важен,
           в ФС, используемых семейством ОС Unix - ext, ext2, ext3...  - он имеет значение,
           поэтому не всегда есть гарантия, что файл/папка существует в месте назначения,
           и его/ее наличие нужно проверять локально, используя сервис ФС
*/
import prefs.*;

import cloud.*;
import cloud.request.*;
import cloud.response.*;

import javafx.application.Platform;
import javafx.fxml.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;

import static prefs.Prefs.*;
import static prefs.Prefs.ErrorCode.*;

public class NeStController implements Initializable {
    @FXML private VBox clientView, serverView;
    @FXML private Menu ActionMenu;
    @FXML private MenuItem
            menuItemLogIn, menuItemLogOut,
            menuItemUpload, menuItemDownload,
            menuItemRename, menuItemRemove,
            menuItemViewLeft, menuItemViewRight;

    private Network network;
    private NettyNetwork nettyNetwork;

    private Stage stage, regStage;
    private RegController regController;

    private PanelController cliCtrl, srvCtrl;

    private boolean authorized = false, clientFocused, serverFocused;
    private final boolean useNetty = false;

    private String user, newName; // имя пользователя; новое имя при переименовании файла/папки
    private int dragSrc; // панель-источник перетаскивания: 0=клиент, 1=сервер
    private long restSize = 0L; // размер оставшихся данных для передачи с сервера клиенту

    public boolean isAuthorized() { return authorized; }

    // цикл обработки ответов сервера на запросы/команды
    private void readLoop() {
        try {
            while (true) {
                String cmd = network.read();
                if (!cmd.startsWith(COM_ID)) continue;
                if (!authorized) {
                    // без авторизации (или после завершения пользователем своего сеанса)
                    // ожидается только ответ сервера на запрос авторизации
                    if (cmd.startsWith(getCommand(SRV_REFUSE))) {
                        String[] val = cmd.split(" ", 2);
                        onLoginFailed(Integer.parseInt(val[1]));
                    }
                    if (cmd.startsWith(getCommand(SRV_ACCEPT))) {
                        String[] val = cmd.split(" ", 4);
                        user = val[2];
                        doLogInActions();
                    }
                } else {
                    // после авторизации могут идти любые команды/запросы, кроме команды авторизации
                    // завершение сеанса пользователя обрабатыать особо
                    if (isExitCommand(cmd)) doLogOutActions();
                    // обработать прочие успешно выполненные команды/запросы
                    if (cmd.startsWith(getCommand(SRV_ACCEPT))) {
                        String[] arg = cmd.split(" ", 0);
                        if (arg.length < 3) continue;
                        System.out.println("server completed " + arg[1] + " and return " + arg[2]);
                        long l;
                        switch (arg[1].toLowerCase()) {
                            case COM_GET_SPACE:
                                try { l = Long.parseLong(arg[2]); }
                                catch (Exception ex) { l = MAXSIZE; }
                                final long free = l;
                                Platform.runLater(() -> srvCtrl.updateFreeSpace(free));
                                break;
                            case COM_GET_FILES:
                                try { l = Long.parseLong(arg[2]); }
                                catch (Exception ex) { ex.printStackTrace(); break; }
                                if (arg[3].equals(".")) arg[3] = "";
                                if (l == 0) {
                                    srvCtrl.setServerFolder(null);
                                    try {
                                        Semaphore semaphore = new Semaphore(0);
                                        Platform.runLater(() -> {
                                            srvCtrl.updateServerFilesList(arg[3]);
                                            semaphore.release();
                                        });
                                        semaphore.acquire();
                                    } catch (InterruptedException ex) {}
                                    break;
                                }
                                boolean correct = true;
                                List<String> list =
                                        new ArrayList<>(Arrays.asList(arg[4].split("\n")));
                                if (list.size() > 0 && l == list.size()) {
                                    List<FileInfo> fi = new ArrayList<>();
                                    int i = 0;
                                    while (i < l && correct) {
                                        String[] item = list.get(i++).split(":");
                                        if (item.length == 3)
                                            try {
                                                fi.add(new FileInfo(decodeSpaces(item[0]),
                                                        Long.parseLong(item[1]),
                                                        FileInfo.getModified(Long.parseLong(item[2]))));
                                            } catch (Exception ex) {
                                                correct = false;
                                                ex.printStackTrace();
                                            }
                                        else
                                            correct = false;
                                    }
                                    if (correct) srvCtrl.setServerFolder(fi);
                                } else
                                    correct = false;
                                if (correct) {
                                    // чем расхлебывать неожиданности параллельных выполнений
                                    // этого же метода в основном потоке, стоит дождаться его
                                    // окончания здесь; вместо семафора можно использовать
                                    // CountDownLatch
                                    try {
                                        Semaphore semaphore = new Semaphore(0);
                                        Platform.runLater(() -> {
                                            srvCtrl.updateServerFilesList(arg[3]);
                                            semaphore.release();
                                        });
                                        semaphore.acquire();
                                    } catch (InterruptedException ex) {}
                                } else
                                    Platform.runLater(() -> Messages.displayError(ERR_WRONG_LIST, ""));
                                break;
                            case COM_UPLOAD:
                                //TODO(?): синхронизировать отправку следующего пакета
                                // с получением отклика сервера на предыдущий -
                                // учитывать здесь нужно многое:
                                // 1) число прочитанных байт при очередном чтении из файла
                                //    может быть меньше размера буфера не только в случае
                                //    последнего блока (как остаток), но и при любом другом -
                                //    из-за ошибки чтения с диска, и блок тогда нужно перечитать;
                                // 2) при пересылке нужно указывать номер блока, сверяя его
                                //    с полученным в любом ответе сервера - DONE/REFUSE;
                                //    соответственно, в ответах нужно вводить новые поля;
                                // 3) при ответе сервера DONE номер блока увеличить,
                                //    при REFUSE - переслать повторно
                                int id = Integer.parseInt(arg[2]);
                                if (id == SRV_SUCCESS) onUploaded(); else uploadData(id);
                                break;
                            case COM_DOWNLOAD:
                                downloadData(Integer.parseInt(arg[2]), Base64.getDecoder().decode(arg[3]));
                                break;
                            case COM_REMOVE: onRemoved(); break;
                            case COM_RENAME: onRenamed();
                        }
                    }
                    // обработать ошибки при выполнении команд/запросов
                    // /nest_err code [cmd]
                    if (cmd.startsWith(getCommand(SRV_REFUSE))) {
                        String[] response = cmd.split(" ", 0);
                        int errCode;
                        try { errCode = Integer.parseInt(response[1]); }
                        catch (Exception ex) {
                            errCode = ERR_INTERNAL_ERROR.ordinal();
                        }
                        final int err = errCode;
                        Platform.runLater(() -> onFailed(response.length > 2 ? response[2] : "",
                                ErrorCode.values()[err]));
                    }
                }
            }
        } catch (Exception ex) { System.err.println("Connection lost"); ex.printStackTrace(); }
    }

    // цикл обработки ответов Netty-сервера на запросы/команды
    private void readLoopNetty() {
        try {
            while (true) {
                CloudMessage message = nettyNetwork.read();
                if (message != null) System.out.println("response="+message);
                // запрос авторизации
                if (message instanceof AuthResponse) {
                    AuthResponse rs = (AuthResponse)message;
                    System.out.println("authorization, server return "+rs.getUsername());
                    user = rs.getUsername();
                    if (user != null && user.length() > 0 && rs.getErrCode() < 0)
                        doLogInActions();
                    else {
                        user = null;
                        onLoginFailed(rs.getErrCode());
                    }
                }
                // запрос регистрации
                if (message instanceof RegResponse) {
                    RegResponse rs = (RegResponse)message;
                    System.out.println("registration, server return "+rs.getUsername());
                    if ((user = rs.getUsername()) != null && rs.getErrCode() < 0)
                        doLogInActions();
                    else
                        onLoginFailed(rs.getErrCode());
                }
                // запрос на завершение сеанса пользователя
                if (message instanceof LogoutResponse) {
                    LogoutResponse rs = (LogoutResponse)message;
                    System.out.println("logout, server return "+rs.getLogin());
                    if ((rs.getLogin()).equals(user)) doLogOutActions();
                }
                // запрос размера свободного места в папке пользователя
                if (message instanceof SpaceResponse) {
                    SpaceResponse rs = (SpaceResponse)message;
                    System.out.println("free space, server return "+rs.getSpace());
                    Platform.runLater(() -> srvCtrl.updateFreeSpace(rs.getSpace()));
                }
                // запрос списка файлов в указанной папке
                // TODO: ответы именно на запросы списков элементов почему-то не появляются среди полученных,
                //  хотя обработчик запросов на сервере их принимает и отправляет ответы
                if (message instanceof FilesListResponse) {
                    FilesListResponse rs = (FilesListResponse)message;
                    System.out.println("files, server return:" +
                            "\nerr=" + rs.getErrCode() +
                            "\nentries=" + rs.getEntriesCount() +
                            "\npath=" + rs.getFolder() +
                            "\nlist=" + rs.getEntries());
                    if (rs.getErrCode() < 0) {
                        srvCtrl.setServerFolder(rs.getEntriesCount() == 0 ? null : rs.getEntries());
                        srvCtrl.updateServerFilesList(srvCtrl.getCurPath());
                    } else
                        Messages.displayError(ErrorCode.values()[rs.getErrCode()], "");
                }
                // запрос копирования с клиента на сервер
                if (message instanceof UploadResponse) {
                    UploadResponse rs = (UploadResponse)message;
                    if (rs.getErrCode() < 0)
                        if (rs.getId() == SRV_SUCCESS) onUploaded(); else uploadData(rs.getId());
                    else
                        onFailed(COM_UPLOAD, ErrorCode.values()[rs.getErrCode()]);
                }
                // запрос копирования с сервера на клиент
                if (message instanceof DownloadResponse) {
                    DownloadResponse rs = (DownloadResponse)message;
                    if (rs.getData() == null)
                        onFailed(COM_DOWNLOAD, ERR_CANNOT_COMPLETE);
                    else
                        downloadData(rs.getSize(), rs.getData());
                }
                // запрос удаления
                if (message instanceof RemovalResponse) {
                    RemovalResponse rs = (RemovalResponse)message;
                    if (rs.getErrCode() < 0)
                        onRemoved();
                    else
                        onFailed(COM_REMOVE, ErrorCode.values()[rs.getErrCode()]);
                }
                // запрос переименования
                if (message instanceof RenameResponse) {
                    RenameResponse rs = (RenameResponse)message;
                    if (rs.getErrCode() < 0)
                        onRenamed();
                    else
                        onFailed(COM_REMOVE, ErrorCode.values()[rs.getErrCode()]);
                }
            }
        } catch (Exception ex) { System.err.println("Connection lost"); ex.printStackTrace(); }
    }

    /*
       обертки для отправки запросов/команд серверу
     */
    void sendToServer(String cmdReq) {
        if (network != null)
            try {
                network.getOs().writeUTF(cmdReq);
                //network.getOs().flush();
            } catch (IOException ex) { ex.printStackTrace(); }
    }

    void sendToServer(CloudMessage message) {
        if (nettyNetwork != null)
            try { nettyNetwork.write(message); }
            catch (IOException ex) { ex.printStackTrace(); }
    }

    /*
       команды/запросы к серверу
    */
    // запрос авторизации
    void authorize(String login, String password) {
        if (useNetty)
            sendToServer(new AuthRequest(login, getHash(password)));
        else
            sendToServer(String.format(
                getCommand(COM_AUTHORIZE, "%s %s"), login, getHash(password)));
    }

    void register(String login, String password, String email, String username) {
        if (useNetty)
            sendToServer(new RegRequest(login, encodeSpaces(encode(password, true)), email, username));
        else
            sendToServer(String.format(getCommand(COM_REGISTER, "%s %s %s %s"),
                    login, encode(password, true), username, email));
    }

    // запрос на завершение сеанса пользователя
    @FXML void logOut(/*ActionEvent actionEvent*/) {
        if (useNetty) {
            sendToServer(new LogoutRequest());
        } else
            sendToServer(getExitCommand().get(0));
    }

    // запрос списка файлов в пользовательской папке (или в ее подпапке)
    void requestFiles(String folder) {
        if (useNetty)
            sendToServer(new FilesListRequest(folder));
        else
            sendToServer(getCommand(COM_GET_FILES, folder));
    }

    // запрос на удаление файла/папки
    void removeEntry(String name) {
        if (useNetty)
            sendToServer(new RemovalRequest(name));
        else
            sendToServer(getCommand(COM_REMOVE, encodeSpaces(name)));
    }

    // запрос на переименование файла/папки
    void renameEntry(String newName, boolean replace) {
        if (this.newName == null) this.newName = newName;
        if (useNetty)
            sendToServer(new RenameRequest(srvCtrl.getFullSelectedFilename(), newName, replace));
        else
            sendToServer(getCommand(COM_RENAME,
                    encodeSpaces(srvCtrl.getFullSelectedFilename()),
                    encodeSpaces(newName),
                    replace ? "1" : ""));
    }

    // копирование файла/папки на сервер
    // с предварительной проверкой наличия (и выводом запроса на замену)
    // одноименного элемента в папке назначения
    @FXML void tryUpload() { startTransfer(true); }

    // отправка запроса
    // /upload source_name destination_path size date [1=replace_existing]
    private void upload(boolean replace) {
        //TODO: при копировании больших файлов следовало бы отображать индикатор копирования
        String dst = encodeSpaces(Paths.get(srvCtrl.getCurPath()).toString());
        if (dst.length() == 0) dst = useNetty ? "" : ".";
        FileInfo fi = cliCtrl.getFilesTable().getSelectionModel().getSelectedItem();
        if (useNetty)
            sendToServer(new UploadRequest(cliCtrl.getSelectedFilename(),
                    dst, fi.getSize(), fi.getModifiedAsLong(), replace));
        else
            sendToServer(getCommand(COM_UPLOAD,
                    encodeSpaces(cliCtrl.getSelectedFilename()),
                    dst, fi.getSize()+"", fi.getModifiedAsLong()+"", replace ? "1" : ""));
    }

    // продолжать передачу файла на сервер
    void uploadData(int id) {
        if (id == SRV_SUCCESS)
            onUploaded();
        else {
            byte[] buf = new byte[BUF_SIZE];
            try (BufferedInputStream bis = new BufferedInputStream(
                    Files.newInputStream(
                            Paths.get(Paths.get(cliCtrl.getCurPath(),
                                    cliCtrl.getSelectedFilename()).toString())), BUF_SIZE)) {
                int bytesRead;
                while ((bytesRead = bis.read(buf)) > 0)
                    if (useNetty)
                        sendToServer(new UploadDataRequest(id, bytesRead, buf));
                    else
                        sendToServer(getCommand(COM_UPLOAD_DATA,
                                id + "", bytesRead + "",
                                Base64.getEncoder().encodeToString(buf)));
            } catch (Exception ex) {
                ex.printStackTrace();
                onFailed(COM_UPLOAD, ERR_CANNOT_COMPLETE);
            }
        }
    }

    // создать папку/пустой файл или начать передачу
    void doTransfer(Path dst, long size, boolean toServer, boolean replace) throws IOException {
        if (toServer)
            upload(replace);
        else {
            if (Files.exists(dst) && !replace)
                throw new FileAlreadyExistsException(dst.toString());
            else {
                if (similarNames(dst, srvCtrl.getSelectedFilename())) removeFile(dst);
                makeFolderOrZero(dst, size);
            }
            if (size > 0L) download(); else onDownloaded();
        }
    }

    // общий алгоритм копирования файла/папки на/с сервера
    void startTransfer(boolean toServer) {
        PanelController srcPC = toServer ? cliCtrl : srvCtrl, dstPC = toServer ? srvCtrl : cliCtrl;
        Path dst = Paths.get(dstPC.getCurPath(), srcPC.getSelectedFilename());
        int idx = -1;
        long size = srcPC.getSelectedFileSize();
        int entryType = dstPC.getFilesTable().getItems() == null
                ? -1
                : dstPC.checkPresence(srcPC.getSelectedFilename());
        if (entryType < 0) {
            try {
                doTransfer(dst, size, toServer, false);
            // обработка исключений только при копировании с сервера
            } catch (FileAlreadyExistsException ex) {
                idx = cliCtrl.getIndexOfAnyMatch(srvCtrl.getSelectedFilename());
                entryType = cliCtrl.getFilesTable().getItems().get(idx).getSize() < 0 ? 1 : 0;
            } catch (IOException ex) {
                Messages.displayError(ERR_CANNOT_COMPLETE, ERR_OPERATION_FAILED, COM_DOWNLOAD);
                return;
            }
        }
        // перед копированием на сервер задать вопрос замены, если файл/папка существует в списке
        if (entryType == 0) // в случае существования файла...
            if (size < 0L) { // ...он не может быть заменен папкой
                Messages.displayError(ERR_WRONG_REPLACEMENT, ERR_OPERATION_FAILED);
                return;
            } else {// ...заменяет существующий
                String name = idx < 0
                        ? srcPC.getSelectedFilename()
                        : cliCtrl.getFilesTable().getItems().get(idx).getFilename();
                Platform.runLater(() -> {
                    if (Messages.confirmReplacement(true, name))
                        try { doTransfer(dst, size, toServer, true); }
                        // обработка исключений только при копировании с сервера
                        catch (IOException ex) {
                            Messages.displayError(ERR_CANNOT_COMPLETE, ERR_OPERATION_FAILED, COM_DOWNLOAD);
                        }
                });
            }
        // в случае существования папки ее не заменить ни папкой, ни файлом - копирование внутрь
        if (entryType == 1)
            Messages.displayError(ERR_CANNOT_COMPLETE,
                    ERR_OPERATION_FAILED,
                    "folder " + dst + " already exist",
                    toServer ? COM_UPLOAD : COM_DOWNLOAD);
    }

    @FXML void tryDownload() { startTransfer(false); }

    // отправка запроса
    // /download source_path
    private void download() {
        //TODO: при копировании больших файлов следовало бы отображать индикатор копирования
        if (useNetty)
            sendToServer(new DownloadRequest(srvCtrl.getFullSelectedFilename()));
        else
            sendToServer(getCommand(COM_DOWNLOAD, encodeSpaces(srvCtrl.getSelectedFilename())));
    }

    // продолжать передачу файла с сервера
    void downloadData(int size, byte[] data) {
        boolean success = true;
        if (size > 0) {
            if (restSize == 0L) restSize = srvCtrl.getSelectedFileSize();
            byte[] buf = Arrays.copyOf(data, size);
            try (BufferedOutputStream bos = new BufferedOutputStream(
                    new FileOutputStream(Paths.get(cliCtrl.getCurPath(),
                            srvCtrl.getSelectedFilename()).toString(), true), BUF_SIZE)) {
                bos.write(buf, 0, size);
            } catch (Exception ex) {
                ex.printStackTrace();
                success = false;
            }
            if (success && ((restSize -= size) <= 0L)) {
                restSize = 0L;
                onDownloaded();
            }
        } else
            if (restSize > 0L) success = false;
        if (!success)
            onFailed(COM_DOWNLOAD, ERR_CANNOT_COMPLETE);
    }

    /*
       реализация копирования drag-and-drop-перетаскиванием
    */
    void dragStarted(boolean client, MouseEvent ev) {
        dragSrc = client ? 0 : 1;
        Dragboard db = (client ? cliCtrl : srvCtrl).getFilesTable().startDragAndDrop(TransferMode.ANY);
        ClipboardContent c = new ClipboardContent();
        c.putString((client ? cliCtrl : srvCtrl).getSelectedFilename());
        db.setContent(c);
        ev.consume();
    }

    void dragOver(boolean client, DragEvent ev) {
        // команда subst позволяет для одного диска задать неколько букв (псевдонимов),
        // поэтому папки с одинаковыми именами на разных дисках физически могут быть
        // как разными, так и одинаковыми. такие случаи (разные имена для одного диска),
        // скорее всего, на практике редки, и следует полагать, что в большинстве случаев
        // на физически разных дисках могут находиться папки с полностью совпадающими путями
        if (ev.getDragboard().hasString() && (dragSrc == (client ? 1 : 0)) && (srvCtrl.isServerMode() ||
                (!cliCtrl.getCurPath().equals(srvCtrl.getCurPath()) ||
                        cliCtrl.getCurDisk() != srvCtrl.getCurDisk())))
            ev.acceptTransferModes(TransferMode.ANY);
        ev.consume();
    }

    void dragDropped(boolean client, DragEvent ev) {
        Dragboard db = ev.getDragboard();
        if (srvCtrl.isServerMode())
            if (client) tryDownload(); else tryUpload();
        else {
            boolean retry = false;
            Path dst = Paths.get((client ? cliCtrl : srvCtrl).getCurPath(), db.getString());
            do {
                try {
                    Files.copy(Paths.get((client ? srvCtrl : cliCtrl).getCurPath(), db.getString()), dst);
                } catch (Exception ex) {
                    retry = ex instanceof FileAlreadyExistsException
                            ? Messages.confirmReplacement(Files.isRegularFile(dst), db.getString())
                            : Messages.confirmRetryCopying();
                }
            } while (retry);
            if (!client) srvCtrl.updateFilesList(Paths.get(srvCtrl.getCurPath()));
        }
        if (client) cliCtrl.updateFilesList(Paths.get(cliCtrl.getCurPath()));
        ev.setDropCompleted(db.hasString());
        ev.consume();
    }

    /**
     *    удаление выбранного файла (папки) может происходить как по нажатию Delete,
     *    так и по команде меню Remove selected.
     *    решение о способе удаления принимается в зависимости от нахождения
     *    файла (папки) - на клиенте или на сервере:
     *    на клиенте - вызвать панельный метод физического удаления
     *       и, при необходимости, обновить содержимое второй клиентской панели;
     *    на сервере - отправить запрос серверному обработчику запросов клиента
     */
    @FXML void tryRemove(/*ActionEvent actionEvent*/) {
        PanelController pc = cliCtrl.getFilesTable().isFocused() ? cliCtrl : srvCtrl;
        pc.setSavedIdx(pc.getFilesTable().getSelectionModel().getSelectedIndex());
        if (Messages.getRemovalConfirmation(pc.getSelectedFileSize() >= 0,
                pc.getSelectedFilename(), true)) {
            boolean same = cliCtrl.getCurPath().equals(srvCtrl.getCurPath());
            if (cliCtrl.getFilesTable().isFocused()) {
                cliCtrl.removeItem();
                if (same) srvCtrl.updateFilesList(Paths.get(srvCtrl.getCurPath()));
            }
            if (srvCtrl.getFilesTable().isFocused()) {
                if (srvCtrl.isServerMode())
                    removeEntry(Paths.get(srvCtrl.getCurPath(), srvCtrl.getSelectedFilename()).toString());
                else {
                    srvCtrl.removeItem();
                    if (same) cliCtrl.updateFilesList(Paths.get(cliCtrl.getCurPath()));
                }
            }
            refreshFileOps();
        }
    }

    // переименование файла/папки
    @FXML void tryRename(/*ActionEvent actionEvent*/) {
        PanelController pc = cliCtrl.getFilesTable().isFocused() ? cliCtrl : srvCtrl;
        pc.setEditing(true);
        newName = Messages.getInputValue(
                    "Renaming '"+pc.getSelectedFilename()+"'",
                    "Input new name for '"+pc.getSelectedFilename()+"'",
                    "New name:", pc.getSelectedFilename());
        pc.setEditing(false);
        if (newName == null) return;
        int replace = pc.renameItem(pc.getSelectedFilename(), newName, false);
        if (replace != 0)
            if (srvCtrl.getFilesTable().isFocused() && srvCtrl.isServerMode())
                renameEntry(newName, replace == 1);
            else if (cliCtrl.getCurPath().equals(srvCtrl.getCurPath())) {
                cliCtrl.updateFilesList(Paths.get(pc.getCurPath()));
                cliCtrl.getFilesTable().getSelectionModel().select(pc.getIndexOf(newName));
                srvCtrl.updateFilesList(Paths.get(pc.getCurPath()));
                srvCtrl.getFilesTable().getSelectionModel().select(pc.getIndexOf(newName));
            } else {
                pc.updateFilesList(Paths.get(pc.getCurPath()));
                pc.getFilesTable().getSelectionModel().select(pc.getIndexOf(newName));
            }
    }

    // обновить доступность субменю операций с файлами/папками
    void refreshFileOps() {
        menuItemUpload.setDisable(!clientFocused || !srvCtrl.isServerMode());
        menuItemDownload.setDisable(!serverFocused || !srvCtrl.isServerMode());
        ActionMenu.setDisable(!clientFocused && !serverFocused);
        menuItemRemove.setDisable(!clientFocused && !serverFocused);
        menuItemRename.setDisable(!clientFocused && !serverFocused);
    }

    // обновление второй панели при необходимости,
    // вызывается контроллером панели при ее обновлении
    void onPanelUpdated() {
        if (cliCtrl.getCurPath().equals(srvCtrl.getCurPath())) {
            int csi = cliCtrl.getFilesTable().getSelectionModel().getSelectedIndex(),
                ssi = srvCtrl.getFilesTable().getSelectionModel().getSelectedIndex();
            boolean cf = clientFocused, sf = serverFocused;
            cliCtrl.updateFilesList(Paths.get(cliCtrl.getCurPath()));
            srvCtrl.updateFilesList(Paths.get(srvCtrl.getCurPath()));
            cliCtrl.getFilesTable().getSelectionModel().select(csi);
            srvCtrl.getFilesTable().getSelectionModel().select(ssi);
            if (cf) cliCtrl.getFilesTable().requestFocus();
            if (sf) srvCtrl.getFilesTable().requestFocus();
            refreshFileOps();
        }
    }

    // действия при выходе
    @FXML public void performExit(/*ActionEvent ev*/) {
        Platform.exit();
    }

    /*
       действия в случае успешного выполнения команд/запросов
    */
    void doLogInActions() {
        authorized = true;
        Platform.runLater(() -> {
            setTitle();
            // свойство всего текста в TextArea,
            // для задания цвета отдельных строк вместо нее нужно исполльзовать TextFlow
            //regController.textArea.setStyle("-fx-text-fill: green;");
            regController.addMessage("Logged in as " + user);
            regController.updateButtons();
            srvCtrl.setServerMode();
            menuItemLogIn.setDisable(true);
            menuItemLogOut.setDisable(false);
            menuItemViewRight.setText("Right list: server files");
        });
    }

    void onLoginFailed(int errCode) {
        Platform.runLater(() -> {
            regController.addMessage("Login failed with "+ errMessage[errCode].toLowerCase()+"\n");
            regController.updateButtons();
        });
    }

    void doLogOutActions() {
        authorized = false;
        Platform.runLater(() -> {
            user = "";
            setTitle();
            srvCtrl.setLocalMode(cliCtrl.getCurPath());
            menuItemLogIn.setDisable(false);
            menuItemLogOut.setDisable(true);
            refreshFileOps();
            menuItemViewRight.setText("Right list: client files");
        });
    }

    // при успешном завершении операции с файлом/папкой фокус перемещается на него/нее,
    // в случае команды REMOVE - остается на месте: на следующем (если есть) за удаленным элементе
    void onUploaded() {
        Platform.runLater(() -> {
            if (srvCtrl.isServerMode())
                srvCtrl.updateServerFilesList(srvCtrl.getCurPath());
            else
                srvCtrl.updateFilesList();
            srvCtrl.getFilesTable().getSelectionModel()
                    .select(srvCtrl.getIndexOf(cliCtrl.getSelectedFilename()));
            refreshFileOps();
        });
    }
    void onDownloaded() {
        // установить дату и время последней модификации скопированного элемента как у оригинала
        new File(Paths.get(cliCtrl.getCurPath(),
                srvCtrl.getSelectedFilename()).toString()).setLastModified(
                srvCtrl.getFilesTable().getSelectionModel().getSelectedItem().getModifiedAsLong());
        Platform.runLater(() -> {
            cliCtrl.updateFilesList();
            //int i = cliCtrl.getIndexOf(srvCtrl.getSelectedFilename());
            // следует учитывать, что индекс вообще находится во всем множестве элементов таблицы,
            // без учета текущего способа их упорядочения, однако выбор с помощью select
            // по этому индексу выбирает именно искомый элемент с учетом сортировки
            cliCtrl.getFilesTable().getSelectionModel()
                    .select(cliCtrl.getIndexOf(srvCtrl.getSelectedFilename()));
            // не срабатывает так, как ожидается
            //cliCtrl.getFilesTable().scrollTo(i);
            refreshFileOps();
        });
    }
    void onRemoved() {
        if (srvCtrl.getFilesTable().getItems() != null)
            srvCtrl.getFilesTable().getSelectionModel().select(srvCtrl.getSavedIdx());
    }
    void onRenamed() {
        int i = srvCtrl.getIndexOf(newName);
        srvCtrl.getFilesTable().getSelectionModel().select(i);
        newName = null;
    }

    /*
       обработать ошибки копирования, удаления и переименования
    */
    void onFailed(String cmd, ErrorCode errCode) {
        switch (cmd) {
            case COM_RENAME:
                switch (errCode) {
                    case ERR_NO_SUCH_FILE:
                        PanelController pc = cliCtrl.getFilesTable().isFocused() ? cliCtrl : srvCtrl;
                        int n = pc.getIndexOfAnyMatch(newName);
                        if (n < pc.getFilesTable().getItems().size())
                            n = pc.getFilesTable().getItems().get(n).getSize() < 0L ? 1 : 0;
                        if (pc.isRenameable(newName, n))
                            renameEntry(newName, true);
                        else
                            newName = null;
                        break;
                    case ERR_CANNOT_COMPLETE:
                        Messages.displayError(errCode, ERR_OPERATION_FAILED, COM_RENAME);
                }
                break;
            case COM_REMOVE:
                Messages.displayError(errCode, ERR_OPERATION_FAILED, COM_REMOVE);
                break;
            case COM_DOWNLOAD:
                if (Messages.getRemovalConfirmation(true, srvCtrl.getSelectedFilename(), false))
                    removeFile(Paths.get(srvCtrl.getFullSelectedFilename()));
                break;
            case COM_UPLOAD:
                switch (errCode) {
                    case ERR_NO_SUCH_FILE:
                        int idx = srvCtrl.getIndexOfAnyMatch(cliCtrl.getSelectedFilename());
                        String name = srvCtrl.getFilesTable().getItems().get(idx).getFilename();

                        if (srvCtrl.getFilesTable().getItems().get(idx).getSize() < 0L)
                            // в случае существования папки ее не заменить ни папкой, ни файлом -
                            // копирование внутрь
                            Messages.displayError(ERR_CANNOT_COMPLETE, ERR_OPERATION_FAILED,
                                    "folder " + name + " already exist", COM_UPLOAD);
                        else
                            // в случае существования файла...
                            if (cliCtrl.getSelectedFileSize() < 0L)
                                // ...он не может быть заменен папкой
                                Messages.displayError(ERR_WRONG_REPLACEMENT, ERR_OPERATION_FAILED);
                            else
                                // ...нужно подтверждение на его замену
                                Platform.runLater(() -> {
                                    if (Messages.confirmReplacement(true, name))
                                        upload(true);
                                });
                        break;
                    case ERR_CANNOT_COMPLETE:
                        Messages.displayError(errCode, ERR_OPERATION_FAILED, COM_UPLOAD);
                }
        }
    }

    /*
       прочие вспомогательные методы
    */
    // обновить заголовок окна
    private void setTitle() {
        Platform.runLater(() -> {
            String title = "";
            if (user != null && user.length() > 0) title = "[ " + user + " ] ";
            stage.setTitle(title + FULL_TITLE);
        });
    }

    // создать окно авторизации
    private void createRegStage() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("regForm.fxml"));
            Parent root = fxmlLoader.load();
            regStage = new Stage();
            regStage.setTitle(SHORT_TITLE+" authorization");
            regStage.setScene(new Scene(root));
            regController = fxmlLoader.getController();
            regController.setController(this);
            regStage.initStyle(StageStyle.UTILITY);
            regStage.initModality(Modality.APPLICATION_MODAL);
        }
        catch (IOException ex) { ex.printStackTrace(); }
    }

    // отобразить окно авторизации/регистрации
    @FXML public void showRegForm(/*ActionEvent actionEvent*/) {
        if (regStage == null) createRegStage();
        regStage.show();
    }

    @Override public void initialize(URL url, ResourceBundle resourceBundle) {
        Platform.runLater(() -> stage = (Stage)clientView.getScene().getWindow());

        cliCtrl = (PanelController)clientView.getProperties().get("ctrlRef");
        srvCtrl = (PanelController)serverView.getProperties().get("ctrlRef");
        cliCtrl.setController(this);
        srvCtrl.setController(this);

        // отслеживать как вообще наличие фокуса на панели с файлами/папками,
        // так и выбора в ней любого элемента (выбор делается не только пользователем -
        // после каждой успешной операции фокус перемещается на новый элемент списка)
        cliCtrl.getFilesTable().getSelectionModel().selectedIndexProperty().addListener(
                (observableValue, oldValue, newValue) -> {
                    clientFocused = newValue.intValue() >= 0 && cliCtrl.getFilesTable().isFocused();
                    refreshFileOps();
                });
        cliCtrl.getFilesTable().focusedProperty().addListener(
                (observableValue, oldValue, newValue) -> {
                    clientFocused = newValue && cliCtrl.getFilesTable().getSelectionModel().getSelectedIndex() >= 0;
                    refreshFileOps();
                });
        srvCtrl.getFilesTable().getSelectionModel().selectedIndexProperty().addListener(
                (observableValue, oldValue, newValue) -> {
                    serverFocused = newValue.intValue() >=0 && srvCtrl.getFilesTable().isFocused();
                    refreshFileOps();
                });
        srvCtrl.getFilesTable().focusedProperty().addListener(
                (observableValue, oldValue, newValue) -> {
                    serverFocused = newValue && srvCtrl.getFilesTable().getSelectionModel().getSelectedIndex() >= 0;
                    refreshFileOps();
                });

        // изнутри панелей невозможно определить направление перетаскивания -
        // его обработчики пишутся и назначаются извне
        cliCtrl.getFilesTable().setOnDragDetected(ev -> dragStarted(true, ev));
        srvCtrl.getFilesTable().setOnDragDetected(ev -> dragStarted(false, ev));
        cliCtrl.getFilesTable().setOnDragOver(ev -> dragOver(true, ev));
        srvCtrl.getFilesTable().setOnDragOver(ev -> dragOver(false, ev));
        cliCtrl.getFilesTable().setOnDragDropped(ev -> dragDropped(true, ev));
        srvCtrl.getFilesTable().setOnDragDropped(ev -> dragDropped(false, ev));

        try {
            Thread readThread;
            if (useNetty) {
                nettyNetwork = new NettyNetwork(PORT);
                readThread = new Thread(this::readLoopNetty);
            } else {
                network = new Network(PORT);
                readThread = new Thread(this::readLoop); // () -> readLoop()
            }
            readThread.setDaemon(true);
            readThread.start();
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }
}