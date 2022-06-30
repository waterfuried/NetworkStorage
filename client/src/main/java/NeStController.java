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
             3 - ошибка копирования файла/папки,
             4 - полученный список файлов содержит ошибки,
             5 - папка не пуста и не может быть удалена,
             6 - внутренняя ошибка сервера,
             7 - ошибка регистрации пользователя.
          ! в процессе разработки в протокол было внесено дополнение:
          ! несмотря на то, что коды ошибок более или менее однозначно определяют запрос/команду,
          ! при которых они произошли, во избежание недоразумений, для некоторых запросов/команд,
          ! например, при отправке файла с/на сервер, после кода ошибки укзывается имя команды
          ! (запроса) - это позволит корректно обрабатывать ситуации неполной передачи данных;

    1. авторизация пользователя по логину и паролю;
       длина пароля должна быть не меньше 4 (первоначально):
        /user логин пароль
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
    5. залить файл/папку с клиента на сервер:
        /upload путь-с-именем-файла/папки-на-компьютере-клиента путь-на-сервере размер [дата]
    6. скачать файл/папку с сервера на клиент:
        /download путь-с-именем-файла/папки-на-сервере путь-на-компьютере-клиента размер [дата]
    7. удалить файл/папку на сервере:
        /remove имя_файла/папки
       папка не может быть удалена, если она не пуста
    8. регистрация нового пользователя;
       длина пароля должна быть не меньше 4 (первоначально):
        /reg логин пароль имя-пользователя email
       после доп. кода 0 возвращается имя пользователя, если он зарегистрирован
    9. TODO: переименование файла/папки

    TODO: команды №№5,6,7,9 - в ФС семейств FAT и NTFS регистр символов в именах файлов не важен,
          в ФС, используемых семейством ОС Unix - ext, ext2, ext3...  - он имеет значение

    команды 5 и 6
    - работают по принципу "1 за раз" - например,
      при заливке папки на сервер (размер указывается как -1)
      она только создается на нем, но ничем не наполняется,
      даже если на компьютере клиента в ней что-либо есть;
    - в процессе разработки список их аргументов был дополнен
      датой модификации файла/папки (необязательной) -
      если она не указана, файл/папка будет скопирован
      с текущими датой и временем.
    > если папка содержит несколько подпапок и/или файлов,
      перед заливкой ее на сервер нужно убедиться в наличии
      достаточного свободного места.

    Урок 5. Code Review
    1. Добавляем аутентификацию и создание уникальных папок для каждого пользователя при первом входе.
    На клиенте решить вопрос как показывать окна. (Регистрация, Вход, Приложение)
    Лучше использовать SQLite на стороне сервера! Или публичные открытые СУБД.
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

public class NeStController implements Initializable {
    @FXML VBox clientView, serverView;
    @FXML MenuItem menuItemLogIn, menuItemLogOut, menuItemUpload, menuItemDownload, menuItemRemove;

    private Network network;
    private NettyNetwork nettyNetwork;

    private Stage stage, regStage;
    private RegController regController;

    PanelController cliCtrl, srvCtrl;

    boolean authorized = false, clientFocused, serverFocused, useNetty = true;

    private String user; // имя пользователя
    private int dragSrc; // панель-источник перетаскивания: 0=клиент, 1=сервер

    // цикл обработки ответов сервера на запросы/команды
    private void readLoop() {
        try {
            while (true) {
                String cmd = network.read();
                if (cmd.startsWith(Prefs.COM_ID)) {
                    if (!authorized) {
                        // без авторизации (или после завершения пользователем своего сеанса)
                        // ожидается только ответ сервера на запрос авторизации
                        if (cmd.startsWith(Prefs.getCommand(Prefs.SRV_REFUSE))) {
                            String[] val = cmd.split(" ", 2);
                            onLoginFailed(Integer.parseInt(val[1]));
                        } else
                            if (cmd.startsWith(Prefs.getCommand(Prefs.SRV_ACCEPT))) {
                            String[] val = cmd.split(" ", 4);
                            user = val[2];
                            doLogInActions();
                        }
                    } else {
                        // после авторизации могут идти любые команды/запросы, кроме команды авторизации
                        // завершение сеанса пользователя обрабатыать особо
                        if (Prefs.isExitCommand(cmd)) doLogOutActions();
                        // обработать прочие успешно выполненные команды/запросы
                        if (cmd.startsWith(Prefs.getCommand(Prefs.SRV_ACCEPT))) {
                            String[] arg = cmd.split(" ", 0);
                            if (arg.length >= 3) {
                                System.out.println("server completed " + arg[1] + " and return " + arg[2]);
                                long l = 0;
                                switch (arg[1]) {
                                    case Prefs.COM_GET_SPACE:
                                        try { l = Long.parseLong(arg[2]); }
                                        catch (Exception ex) { l = Prefs.MAXSIZE; }
                                        final long free = l;
                                        Platform.runLater(() -> srvCtrl.updateFreeSpace(free));
                                        break;
                                    case Prefs.COM_GET_FILES:
                                        boolean correct = true;
                                        try { l = Long.parseLong(arg[2]); }
                                        catch (Exception ex) { correct = false; }
                                        if (correct) {
                                            String folder = arg[3];
                                            if (folder.equals(".")) folder = "";
                                            if (l > 0) {
                                                List<String> list = new ArrayList<>(Arrays.asList(arg[4].split("\n")));
                                                correct = list.size() > 0 && l == list.size();
                                                if (correct) {
                                                    List<FileInfo> fi = new ArrayList<>();
                                                    int i = 0;
                                                    while (i < l && correct) {
                                                        String[] item = list.get(i++).split(":");
                                                        correct = item.length == 3;
                                                        if (correct) {
                                                            try {
                                                                fi.add(new FileInfo(Prefs.decodeSpaces(item[0]),
                                                                        Long.parseLong(item[1]),
                                                                        FileInfo.getModified(Long.parseLong(item[2]))));
                                                            } catch (Exception ex) {
                                                                correct = false;
                                                                ex.printStackTrace();
                                                            }
                                                        }
                                                    }
                                                    if (correct) srvCtrl.setServerFolder(fi);
                                                }
                                                list.clear();
                                            } else
                                                srvCtrl.setServerFolder(null);
                                            if (correct)
                                                srvCtrl.updateFilesList(folder);
                                            else
                                                Messages.displayErrorFX(Prefs.ErrorCode.ERR_WRONG_LIST.ordinal(), "");
                                        }
                                        break;
                                    case Prefs.COM_UPLOAD: onUploaded(); break;
                                    case Prefs.COM_DOWNLOAD: onDownloaded(); break;
                                    case Prefs.COM_REMOVE: onRemoved();
                                }
                            }
                        }
                        // обработать ошибки при выполнении команд/запросов
                        if (cmd.startsWith(Prefs.getCommand(Prefs.SRV_REFUSE))) {
                            String[] response = cmd.split(" ", 3);
                            int errCode;
                            try { errCode = Integer.parseInt(response[1]); }
                            catch (Exception ex) { errCode = Prefs.ErrorCode.ERR_INTERNAL_ERROR.ordinal(); }
                            onFailed(response.length == 3 ? response[2] : "", errCode);
                        }
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
                    AuthResponse auth = (AuthResponse)message;
                    System.out.println("authorization, server return "+auth.getUsername());
                    if ((user = auth.getUsername()) != null)
                        doLogInActions();
                    else
                        onLoginFailed(auth.getErrCode());
                }
                // запрос регистрации
                if (message instanceof RegResponse) {
                    RegResponse reg = (RegResponse)message;
                    System.out.println("registration, server return "+reg.getUsername());
                    if ((user = reg.getUsername()) != null)
                        doLogInActions();
                    else
                        onLoginFailed(reg.getErrCode());
                }
                // запрос на завершение сеанса пользователя
                if (message instanceof LogoutResponse) {
                    LogoutResponse logout = (LogoutResponse)message;
                    System.out.println("logout, server return "+logout.getLogin());
                    if ((logout.getLogin()).equals(user)) doLogOutActions();
                }
                // запрос свободного места в пользовательской папке
                if (message instanceof SpaceResponse) {
                    SpaceResponse space = (SpaceResponse)message;
                    System.out.println("free space, server return "+space.getSpace());
                    Platform.runLater(() -> srvCtrl.updateFreeSpace(space.getSpace()));
                }
                // запрос списка файлов в папке на сервере
                if (message instanceof FilesListResponse) {
                    FilesListResponse files = (FilesListResponse)message;
                    System.out.println("files, server return:"+
                            "\nerr="+files.getErrCode()+
                            "\nentries="+files.getEntriesCount()+
                            "\npath="+files.getFolder()+
                            "\nlist="+files.getEntries());
                    if (files.getErrCode() < 0) {
                    // TODO:
                    //  это вся обработка при формировании ответа в виде списка с типом элементов FileInfo,
                    //  но такие ответы сервера почему-то не появляются среди полученных (куда-то теряются)
                    //  в цикле чтения сообщений readLoopNetty
/*
                        srvCtrl.setServerFolder(files.getEntriesCount() == 0 ? null : files.getEntries());
                        srvCtrl.updateFilesList(files.getFolder());
                    } else
                        Messages.displayErrorFX(files.getErrCode(), "");
*/
                        boolean correct = true;
                        int l = files.getEntriesCount();
                        String folder = files.getFolder();
                        if (l > 0) {
                            List<String> list = new ArrayList<>(Arrays.asList(files.getEntries().split("\n")));
                            correct = list.size() > 0 && l == list.size();
                            if (correct) {
                                List<FileInfo> fi = new ArrayList<>();
                                int i = 0;
                                while (i < l && correct) {
                                    String[] item = list.get(i++).split(":");
                                    correct = item.length == 3;
                                    if (correct)
                                        try {
                                            fi.add(new FileInfo(item[0],
                                                    Long.parseLong(item[1]),
                                                    FileInfo.getModified(Long.parseLong(item[2]))));
                                        } catch (Exception ex) {
                                            correct = false;
                                            ex.printStackTrace();
                                        }
                                }
                                if (correct) srvCtrl.setServerFolder(fi);
                            }
                            list.clear();
                        } else
                            srvCtrl.setServerFolder(null);
                        if (correct)
                            srvCtrl.updateFilesList(folder);
                    } else
                        Messages.displayErrorFX(Prefs.ErrorCode.ERR_WRONG_LIST.ordinal(), "");
                }
                // запрос копирования с клиента на сервер
                if (message instanceof UploadResponse) {
                    UploadResponse upload = (UploadResponse)message;
                    if (upload.getErrCode() < 0)
                        onUploaded();
                    else
                        onFailed(Prefs.COM_UPLOAD, upload.getErrCode());
                }
                // запрос копирования с сервера на клиент
                if (message instanceof DownloadResponse) {
                    DownloadResponse download = (DownloadResponse)message;
                    if (download.getErrCode() < 0)
                        onDownloaded();
                    else
                        onFailed(Prefs.COM_DOWNLOAD, download.getErrCode());
                }
                // запрос удаления в пользовательской папке
                if (message instanceof RemovalResponse) {
                    RemovalResponse rm = (RemovalResponse)message;
                    if (rm.getErrCode() < 0)
                        onRemoved();
                    else
                        onFailed(Prefs.COM_REMOVE, rm.getErrCode());
                }
            }
        } catch (Exception ex) { System.err.println("Connection lost"); ex.printStackTrace(); }
    }

    /*
       обертки для отправки запросов/команд серверу
     */
    void sendCmdOrRequest(String cmdReq) {
        if (network != null)
            try {
                network.getOs().writeUTF(cmdReq);
                //network.getOs().flush();
            } catch (IOException ex) { ex.printStackTrace(); }
    }

    void sendCmdOrRequestNetty(CloudMessage message) {
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
            sendCmdOrRequestNetty(new AuthRequest(login, password));
        else
            sendCmdOrRequest(String.format(
                Prefs.getCommand(Prefs.COM_AUTHORIZE, "%s %s"), login, password));
    }

    void register(String login, String password, String email, String username) {
        if (useNetty)
            sendCmdOrRequestNetty(new RegRequest(login, password, email, username));
        else
            sendCmdOrRequest(String.format(
                    Prefs.getCommand(Prefs.COM_REGISTER, "%s %s %s %s"), login, password, username, email));
    }

    // запрос на завершение сеанса пользователя
    @FXML void logOut(/*ActionEvent actionEvent*/) {
        System.out.println("requesting logout");
        if (useNetty) {
            sendCmdOrRequestNetty(new LogoutRequest(user));
        } else
            sendCmdOrRequest(Prefs.getExitCommand().get(0));
    }

    // запрос списка файлов в пользовательской папке (или в ее подпапке)
    void requestFiles(String folder) {
        System.out.println("requesting files list for '"+folder+"'");
        if (useNetty)
            sendCmdOrRequestNetty(new FilesListRequest(folder));
        else
            sendCmdOrRequest(Prefs.getCommand(Prefs.COM_GET_FILES, folder));
    }

    // запрос свободного места в пользовательской папке
    void requestFreeSpace() {
        System.out.println("requesting free space");
        if (useNetty)
            sendCmdOrRequestNetty(new SpaceRequest());
        else
            sendCmdOrRequest(Prefs.getCommand(Prefs.COM_GET_SPACE));
    }

    // запрос на удаление файла/папки
    void removeEntry(String name) {
        System.out.println("requesting removal");
        if (useNetty)
            sendCmdOrRequestNetty(new RemovalRequest(name));
        else
            sendCmdOrRequest(Prefs.getCommand(Prefs.COM_REMOVE, Prefs.encodeSpaces(name)));
    }

    // проверка существования файла/папки в текущей папке на сервере/клиенте
    int checkPresence(boolean atServer) {
        List<FileInfo> list = (atServer ? srvCtrl : cliCtrl).filesTable.getItems();
        if (list != null && list.size() > 0) {
            String name = (atServer ? cliCtrl : srvCtrl)
                    .filesTable.getSelectionModel().getSelectedItem().getFilename();
            boolean present = false, isFile = false;
            int i = 0;
            while (i < list.size() && !present) {
                present = list.get(i++).getFilename().equals(name);
                if (present) isFile = list.get(i-1).getSize() >= 0;
            }
            if (present)
                return isFile ? 0 : 1;
        }
        return -1;
    }

    // запрос на копирование файла/папки на сервер
    // отправляется после предварительной проверки
    // наличия одноименного элемента в папке назначения,
    // в случае наличия выводится запрос на замену
    @FXML void tryUpload() {
        int entryType = checkPresence(true);
        if (entryType < 0)
            upload();
        else {
            String entry = entryType == 0 ? "File" : "Folder";
            String name = cliCtrl.filesTable.getSelectionModel().getSelectedItem().getFilename();
            Platform.runLater(() -> {
                if (getReplaceConfirmation(entry, name)) upload();
            });
        }
    }

    // отправка запроса
    private void upload() {
        System.out.println("requesting upload");
        // /upload source_path destination_path size [date]
        //TODO: при копировании больших файлов следовало бы отображать индикатор копирования
        String dst = Prefs.encodeSpaces(Paths.get(srvCtrl.getCurPath()).toString());
        if (dst.length() == 0) dst = useNetty ? "" : ".";
        FileInfo fi = cliCtrl.filesTable.getSelectionModel().getSelectedItem();
        if (useNetty)
            sendCmdOrRequestNetty(new UploadRequest(cliCtrl.getFullSelectedFilename(),
                    dst, fi.getSize(), fi.getModifiedAsLong()));
        else
            sendCmdOrRequest(Prefs.getCommand(Prefs.COM_UPLOAD,
                    Prefs.encodeSpaces(cliCtrl.getFullSelectedFilename()),
                    dst, fi.getSize()+"", fi.getModifiedAsLong()+""));
    }

    // запрос на копирование файла/папки с сервера
    // отправляется после предварительной проверки
    // наличия одноименного элемента в папке назначения,
    // в случае наличия выводится запрос на замену
    @FXML void tryDownload() {
        int entryType = checkPresence(false);
        if (entryType < 0)
            download();
        else {
            String entry = entryType == 0 ? "File" : "Folder";
            String name = srvCtrl.filesTable.getSelectionModel().getSelectedItem().getFilename();
            Platform.runLater(() -> {
                if (getReplaceConfirmation(entry, name)) download();
            });
        }
    }

    // отправка запроса
    private void download() {
        System.out.println("requesting download");
        // /download server_source_path destination_path size [date]
        //TODO: при копировании больших файлов следовало бы отображать индикатор копирования
        FileInfo fi = srvCtrl.filesTable.getSelectionModel().getSelectedItem();
        if (useNetty)
            sendCmdOrRequestNetty(new DownloadRequest(
                    srvCtrl.getFullSelectedFilename(),
                    cliCtrl.getCurPath(),
                    fi.getSize(), fi.getModifiedAsLong()));
        else
            sendCmdOrRequest(Prefs.getCommand(Prefs.COM_DOWNLOAD,
                    Prefs.encodeSpaces(srvCtrl.getFullSelectedFilename()),
                    Prefs.encodeSpaces(cliCtrl.getCurPath()),
                    fi.getSize()+"", fi.getModifiedAsLong()+""));
    }

    /*
       реализация копирования drag-and-drop-перетаскиванием
    */
    void dragStarted(boolean client, MouseEvent ev) {
        dragSrc = client ? 0 : 1;
        Dragboard db = (client ? cliCtrl : srvCtrl).filesTable.startDragAndDrop(TransferMode.ANY);
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
        if (db.hasString()) {
            if (!srvCtrl.isServerMode()) {
                boolean retry = false;
                Path dst = Paths.get((client ? cliCtrl : srvCtrl).getCurPath(), db.getString());
                do {
                    try {
                        Files.copy(Paths.get((client ? srvCtrl : cliCtrl).getCurPath(), db.getString()), dst);
                    } catch (Exception ex) {
                        retry = ex instanceof FileAlreadyExistsException
                                ? getReplaceConfirmation(Files.isRegularFile(dst) ? "File" : "Folder",
                                db.getString())
                                : Messages.getConfirmation(Alert.AlertType.ERROR,
                                "Copy error has been occurred.\nWould you like to retry?",
                                "Copy error");
                    }
                } while (retry);
                if (!client) srvCtrl.updateFilesList(Paths.get(srvCtrl.getCurPath()));
            } else
                if (client) tryDownload(); else tryUpload();
            if (client) cliCtrl.updateFilesList(Paths.get(cliCtrl.getCurPath()));
        }
        ev.setDropCompleted(db.hasFiles());
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
        boolean same = cliCtrl.getCurPath().equals(srvCtrl.getCurPath()),
        needUpdate = cliCtrl.filesTable.isFocused() || srvCtrl.filesTable.isFocused();
        if (cliCtrl.filesTable.isFocused()) {
            cliCtrl.removeItem();
            if (same) srvCtrl.updateFilesList(Paths.get(srvCtrl.getCurPath()));
        }
        if (srvCtrl.filesTable.isFocused()) {
            if (srvCtrl.isServerMode())
                removeEntry(Paths.get(srvCtrl.getCurPath(), srvCtrl.getSelectedFilename()).toString());
            else {
                srvCtrl.removeItem();
                if (same) cliCtrl.updateFilesList(Paths.get(cliCtrl.getCurPath()));
            }
        }
        if (needUpdate) updateFileOps();
    }

    // обновить доступность команд меню upload, download, remove
    void updateFileOps() {
        menuItemUpload.setDisable(!clientFocused || !srvCtrl.isServerMode());
        menuItemDownload.setDisable(!serverFocused || !srvCtrl.isServerMode());
        menuItemRemove.setDisable(!clientFocused && !serverFocused);
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
            regController.textArea.appendText("Logged in as " + user);
            regController.updateButtons();
            srvCtrl.setServerMode();
            menuItemLogIn.setDisable(true);
            menuItemLogOut.setDisable(false);
        });
        // сразу после авторизации запросить у сервера список файлов
        // из папки пользователя и размер оставшегося в ней свободного места
        requestFiles("");
        requestFreeSpace();
    }

    void onLoginFailed(int errCode) {
        Platform.runLater(() -> {
            regController.textArea.appendText("Login failed with "+
                    Prefs.errMessage[errCode].toLowerCase()+"\n");
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
            updateFileOps();
        });
    }

    void onUploaded() {
        // обновить список файлов сервера после успешной передачи
        Platform.runLater(() -> Messages.displayInfo("Uploaded successfully", "Uploading completed"));
        requestFiles(srvCtrl.getCurPath());
        requestFreeSpace();
    }

    void onDownloaded() {
        // обновить список файлов клиента после успешной передачи
        Platform.runLater(() -> {
            cliCtrl.updateFilesList(Paths.get(cliCtrl.getCurPath()));
            Messages.displayInfo("Downloaded successfully", "Downloading completed");
        });
    }

    void onRemoved() {
        // обновить список файлов сервера после успешного удаления
        Platform.runLater(() ->
                Messages.displayInfo("Removed successfully",
                        "Removal completed"));
        requestFiles(srvCtrl.getCurPath());
        requestFreeSpace();
    }

    /*
       обработать ошибки копирования и удаления
    */
    void onFailed(String cmd, int errCode) {
        int opCode = 0;
        String errMsg = Prefs.errMessage[errCode], title = "";
        // при неудачном копировании файла/папки вывести вопрос на повтор операции;
        // при ошибке удаления вывести соответствующее сообщение
        if (cmd.equals(Prefs.COM_UPLOAD) && !menuItemUpload.isDisable()) opCode = 1;
        if (cmd.equals(Prefs.COM_DOWNLOAD) && !menuItemDownload.isDisable()) opCode = 2;
        if (cmd.equals(Prefs.COM_REMOVE)) title = Prefs.ERR_CANNOT_REMOVE;
        // при медленном соединении операция передачи может выполняться какое-то время,
        // за которое пользователь может изменить выбор файла/папки в списке,
        // поэтому вывод запроса на повтор операции может вызывать недоразумения -
        // передается всегда выбранный в данный момент элемент;
        // чтобы их избежать, в ответе на запрос передачи нужно было бы возвращать
        // имя передаваемого файла/папки, но это будет уже перегрузкой протокола
        if (opCode > 0) {
            final int op = opCode;
            Platform.runLater(() -> {
                if (Messages.getConfirmation(Alert.AlertType.ERROR,
                        "Error has been occurred during " + cmd + "ing.\n" +
                                "Would you like to retry?", Prefs.capitalize(cmd) + "ing error"))
                    if (op == 1) tryUpload();
                    else tryDownload();
            });
        } else
            Messages.displayErrorFX(errMsg, title);
    }

    /*
       прочие вспомогательные методы
    */
    // обновить заголовок окна
    private void setTitle() {
        Platform.runLater(() -> {
            String title = "";
            if (user != null && user.length() > 0) title = "[ " + user + " ] ";
            stage.setTitle(title + Prefs.FULL_TITLE);
        });
    }

    // создать окно авторизации
    private void createRegStage() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("regForm.fxml"));
            Parent root = fxmlLoader.load();

            regStage = new Stage();
            regStage.setTitle(Prefs.SHORT_TITLE + " authorization");
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

    // вывести запрос на замену существующего файла
    boolean getReplaceConfirmation(String entry, String name) {
        return Messages.getConfirmation(Alert.AlertType.WARNING,
                entry+" "+name + " already exists at destination.\n" +
                        "Would you like to replace it?",
                entry+" already exists");
    }

    @Override public void initialize(URL url, ResourceBundle resourceBundle) {
        Platform.runLater(() -> stage = (Stage)clientView.getScene().getWindow());

        cliCtrl = (PanelController)clientView.getProperties().get("ctrlRef");
        srvCtrl = (PanelController)serverView.getProperties().get("ctrlRef");
        cliCtrl.setController(this);
        srvCtrl.setController(this);

        // отслеживать как вообще наличие фокуса на панели с файлами/папками,
        // так и выбора в ней любого элемента
        cliCtrl.filesTable.getSelectionModel().selectedIndexProperty().addListener(
                (observableValue, oldValue, newValue) -> {
                    clientFocused = newValue.intValue() >= 0;
                    updateFileOps();
                });
        cliCtrl.filesTable.focusedProperty().addListener(
                (observableValue, oldValue, newValue) -> {
                    clientFocused = newValue && cliCtrl.filesTable.getSelectionModel().getSelectedIndex() >= 0;
                    updateFileOps();
                });
        srvCtrl.filesTable.getSelectionModel().selectedIndexProperty().addListener(
                (observableValue, oldValue, newValue) -> {
                    serverFocused = newValue.intValue() >=0;
                    updateFileOps();
                });
        srvCtrl.filesTable.focusedProperty().addListener(
                (observableValue, oldValue, newValue) -> {
                    serverFocused = newValue && srvCtrl.filesTable.getSelectionModel().getSelectedIndex() >= 0;
                    updateFileOps();
                });

        // изнутри панелей невозможно определить направление перетаскивания -
        // его обработчики приходится писать и назначать извне
        cliCtrl.filesTable.setOnDragDetected(ev -> dragStarted(true, ev));
        srvCtrl.filesTable.setOnDragDetected(ev -> dragStarted(false, ev));
        cliCtrl.filesTable.setOnDragOver(ev -> dragOver(true, ev));
        srvCtrl.filesTable.setOnDragOver(ev -> dragOver(false, ev));
        cliCtrl.filesTable.setOnDragDropped(ev -> dragDropped(true, ev));
        srvCtrl.filesTable.setOnDragDropped(ev -> dragDropped(false, ev));

        try {
            Thread readThread;
            if (useNetty) {
                nettyNetwork = new NettyNetwork(Prefs.PORT);
                readThread = new Thread(this::readLoopNetty);
            } else {
                network = new Network(Prefs.PORT);
                readThread = new Thread(this::readLoop); // () -> readLoop()
            }
            readThread.setDaemon(true);
            readThread.start();
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }
}