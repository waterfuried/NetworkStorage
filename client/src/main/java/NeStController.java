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
              2 - недостаточно места для размещения файла,
              3 - ошибка чтения файла/папки,
              4 - полученный список файлов содержит ошибки,
              5 - папка не пуста и не может быть удалена,
              6 - внутренняя ошибка сервера,
              7 - ошибка регистрации пользователя,
              8 - количество зарегистрированных пользователей достигло максимума,
              9 - недопустимое замещение - файл может быть заменен только файлом,
             10 - имя файла/папки содержит недопустимые символы,
             11 - доступ к папке не возможен,
             12 - команда переименования не предназначена для перемещения.
          ! в процессе разработки в протокол было внесено дополнение:
          ! несмотря на то, что коды ошибок более или менее однозначно определяют запрос/команду,
          ! при которых они произошли, во избежание недоразумений, для некоторых запросов/команд,
          ! например, при отправке файла с/на сервер, после кода ошибки укзывается имя команды
          ! (запроса) - это позволит корректно обрабатывать ситуации неполной передачи данных;

    1. авторизация пользователя по логину и паролю:
         /user логин хеш-пароля
       !! длина пароля должна быть не меньше 4 и не более 12:
       !! после доп. кода 0 возвращается имя пользователя, если он зарегистрирован
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
         /upload имя-файла/папки-на-компьютере-клиента путь-на-сервере размер дата
       сервер возвращает:
        - 0, при успешном создании передаваемго элемента, если это файл нулевого размера или папка,
             а также после завершения передачи данных (файла ненулевого размера)
        - id передачи данных, если передаваемый элемент - файл ненулевого размера
        - код ошибки:
            - не удалось создать файл/папку (ошибка ФС сервера),
            - недостаточно места для сохранения копируемого файла
       5.2. передать блок данных файла на сервер
         /upld id размер-блока-данных блок-данных
    6. копировать файл/папку с сервера на клиент:
         /download путь-с-именем-файла/папки-на-сервере
    7. удалить файл/папку на сервере:
         /remove имя_файла/папки
       папка не может быть удалена, если она не пуста
    8. зарегистрировать нового пользователя:
         /reg логин пароль имя-пользователя email
       !! длина пароля должна быть не меньше 4 и не более 12 символов:
       !! после доп. кода 0 возвращается имя пользователя, если он зарегистрирован
       !! имя пользователя допускает наличие пробелов
    9. переименовать файл/папку
         /rename путь_к_существующему_имени новое_имя
   10. тип ФС сервера (0=extFS-подобная, 1=FAT/NTFS-подобная, -1=не удалось определить)
         /fs
   11. копировать/переместить файл
         /copy путь_к_исходному_имени путь_назначения [1=переместить]
   12. получить размер папки:
         /size имя_папки
       возвращает размер в байтах (в значении доп. кода) и число элементов в папке

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
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

import prefs.*;
import network.*;
import authorization.*;

import cloud.*;
import cloud.request.*;
import cloud.response.*;

import static prefs.Prefs.*;
import static prefs.Prefs.ErrorCode.*;

import com.github.kwhat.jnativehook.*;
import com.github.kwhat.jnativehook.keyboard.*;

public class NeStController implements Initializable, NativeKeyListener, Authorization {
    @FXML private VBox clientView, serverView;
    @FXML private Menu ActionMenu;
    @FXML private MenuItem
            menuItemLogIn, menuItemLogOut,
            menuItemUpload, menuItemDownload,
            menuItemRename, menuItemRemove,
            menuItemGetSize;
    @FXML private CheckMenuItem menuItemViewLeft, menuItemViewRight;

    private Network network;
    private NettyNetwork nettyNetwork;

    private Stage stage, regStage;
    private RegController regController;

    private PanelController cliCtrl, srvCtrl;

    private boolean authorized = false, clientFocused, serverFocused;
    private boolean isShiftDown;
    private final boolean useNetty = false;
    private int serverFS;
    /*  при копировании файлов кусками обработка каждого куска происходит достаточно часто -
        они идут друг за другом, поэтому для уменьшения расхода памяти создание буфера стоило
        бы сделать один раз (а не при обработке каждого куска), однако при параллельной
        обработке теоретически возможно следующее: когда запись одного куска еще не закончена,
        но началась обработка следующего, то буфер, используемый для записи информации
        предыдущего куска, заполняется данными нового.
        Использование раздельных буферов такую возможность исключает.

        Теоретически возможно обходиться и одним буфером при параллельной обработке,
        сделав ее псевдопараллельной (синхронизированной) - поставить на буфер монитор
        и запрещать (блокировать) запись в буфер, пока чтение из него не закончено -
        но такое его использование отразится на быстродействии */
    // private byte[] buf;

    private String user, newName; // имя пользователя; новое имя при переименовании файла/папки
    private int dragSrc; // панель-источник перетаскивания: 0=левая (клиент), 1=правая (сервер)
    private long restSize = 0L; // размер оставшихся данных для передачи с сервера клиенту
    private long freeSpace;

    public boolean authorized() { return authorized; }

    @Override public void nativeKeyPressed(NativeKeyEvent ev) {
        if (ev.getKeyCode() == NativeKeyEvent.VC_SHIFT) isShiftDown = true;
    }
    @Override public void nativeKeyReleased(NativeKeyEvent ev) {
        if (ev.getKeyCode() == NativeKeyEvent.VC_SHIFT) isShiftDown = false;
    }

    public int getServerFS() { return serverFS; }

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
                        user = decodeSpaces(val[2]);
                        doLogInActions();
                    }
                } else {
                    // после авторизации могут идти любые команды/запросы, кроме команды авторизации
                    // завершение сеанса пользователя обрабатывать особо
                    if (isExitCommand(cmd)) doLogOutActions();
                    // обработать прочие успешно выполненные команды/запросы
                    if (cmd.startsWith(getCommand(SRV_ACCEPT))) {
                        String[] arg = cmd.split(" ", 0);
                        if (arg.length < 3) continue;
                        long l;
                        switch (arg[1].toLowerCase()) {
                            case COM_GET_SPACE:
                                try { freeSpace = Long.parseLong(arg[2]); }
                                catch (Exception ex) { freeSpace = MAXSIZE; }
                                Platform.runLater(() -> getRequestingPC().updateFreeSpace(freeSpace));
                                break;
                            case COM_GET_FILES:
                                try { l = Long.parseLong(arg[2]); }
                                catch (Exception ex) { ex.printStackTrace(); break; }
                                if (arg[3].equals(".")) arg[3] = "";
                                PanelController srv = cliCtrl.hasRequest() && srvCtrl.hasRequest()
                                        ? getSrcPC() : getRequestingPC();
                                boolean correct = true;
                                if (l == 0)
                                    srv.setServerFolder(null);
                                else {
                                    List<String> list =
                                            new ArrayList<>(Arrays.asList(arg[4].split("\n")));
                                    correct = list.size() > 0 && l == list.size();
                                    if (correct) {
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
                                        if (correct) srv.setServerFolder(fi);
                                    }
                                }
                                if (correct)
                                    // чем расхлебывать неожиданности параллельных выполнений
                                    // этого же метода в основном потоке, стоит дождаться его
                                    // окончания здесь; вместо семафора можно использовать
                                    // CountDownLatch. Иногда возможен deadlock - ожидание у
                                    // семафора начинается до запуска потока, его отключающего
                                    try {
                                        Semaphore semaphore = new Semaphore(0);
                                        Platform.runLater(() -> {
                                            srv.updateFilesList(arg[3]);
                                            semaphore.release();
                                        });
                                        semaphore.acquire();
                                    } catch (InterruptedException ex) { ex.printStackTrace(); }
                                else
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
                            case COM_RENAME: onRenamed(); break;
                            case COM_COPY: case COM_MOVE:
                                PanelController dst = getDstPC();
                                dst.getFilesTable().getSelectionModel()
                                        .select(dst.getIndexOf(decodeSpaces(arg[3])));
                                if (arg[1].equalsIgnoreCase(COM_COPY))
                                    Platform.runLater(() -> getSrcPC().updateFreeSpace(freeSpace));
                                break;
                            case COM_GET_SIZE:
                                try {
                                    long size = Long.parseLong(arg[2]);
                                    int itemCount = Integer.parseInt(arg[2]);
                                    Platform.runLater(() ->
                                            displayItemSize(true, size, itemCount));
                                } catch (Exception ex) {}
                                break;
                            case COM_GET_FS: serverFS = Integer.parseInt(arg[2]);
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
                // запрос авторизации
                if (message instanceof AuthResponse) {
                    AuthResponse rs = (AuthResponse)message;
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
                    if ((user = rs.getUsername()) != null && rs.getErrCode() < 0)
                        doLogInActions();
                    else
                        onLoginFailed(rs.getErrCode());
                }
                // запрос на завершение сеанса пользователя
                if (message instanceof LogoutResponse)
                    if (((LogoutResponse)message).getLogin().equals(user)) doLogOutActions();
                // запрос размера свободного места в папке пользователя
                if (message instanceof SpaceResponse)
                    Platform.runLater(() -> getRequestingPC()
                            .updateFreeSpace(freeSpace = ((SpaceResponse)message).getSpace()));
                // запрос списка файлов в указанной папке
                if (message instanceof FilesListResponse) {
                    FilesListResponse rs = (FilesListResponse)message;
                    if (rs.getErrCode() < 0) {
                        PanelController srv = cliCtrl.hasRequest() && srvCtrl.hasRequest()
                                ? getSrcPC() : getRequestingPC();
                        srv.setServerFolder(rs.getEntriesCount() == 0 ? null : rs.getEntries());
                        try {
                            Semaphore semaphore = new Semaphore(0);
                            Platform.runLater(() -> {
                                srv.updateFilesList(rs.getFolder());
                                semaphore.release();
                            });
                            semaphore.acquire();
                        } catch (InterruptedException ex) { ex.printStackTrace(); }
                    } else
                        Messages.displayError(ErrorCode.values()[rs.getErrCode()], "");
                }
                // запрос размера указанной папки
                if (message instanceof SizeResponse) {
                    SizeResponse rs = (SizeResponse)message;
                    Platform.runLater(() ->
                            displayItemSize(true, rs.getSize(), rs.getItemCount()));
                }
                // запрос копирования с клиента на сервер
                if (message instanceof UploadResponse) {
                    UploadResponse rs = (UploadResponse)message;
                    if (rs.getErrCode() < 0) {
                        if (rs.getId() == SRV_SUCCESS)
                            onUploaded();
                        else
                            if (!rs.started()) uploadData(rs.getId());
                    } else
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
                // извещение от сервера о типе его ФС
                if (message instanceof FSTypeNotice)
                    serverFS = ((FSTypeNotice) message).getFSType();
                // запрос копирования/перемещения
                if (message instanceof CopyResponse) {
                    CopyResponse rs = (CopyResponse)message;
                    if (rs.getErrCode() < 0) {
                        PanelController dst = getDstPC();
                        dst.getFilesTable().getSelectionModel().select(dst.getIndexOf(rs.getName()));
                        if (!rs.moved())
                            Platform.runLater(() -> getSrcPC().updateFreeSpace(freeSpace));
                    } else
                        onFailed(rs.moved() ? COM_MOVE : COM_COPY, ErrorCode.values()[rs.getErrCode()]);
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
    @Override public void authorize(String login, String password) {
        if (useNetty)
            sendToServer(new AuthRequest(login, getHash(password)));
        else
            sendToServer(String.format(
                getCommand(COM_AUTHORIZE, "%s %s"), login, getHash(password)));
    }

    @Override public void register(String login, String password, String username, String ... regData) {
        if (regData == null || regData.length == 0) return;
        if (useNetty)
            sendToServer(new RegRequest(login, encode(password, true), regData[0], username));
        else
            sendToServer(String.format(getCommand(COM_REGISTER, "%s %s %s %s"),
                    login,
                    encodeSpaces(encode(password, true)),
                    encodeSpaces(username),
                    regData[0]));
    }

    // запрос списка файлов в пользовательской папке (или в ее подпапке)
    void requestFiles(String folder) {
        if (useNetty)
            sendToServer(new FilesListRequest(folder));
        else
            sendToServer(getCommand(COM_GET_FILES, folder));
    }

    // запрос размера подпапки в пользовательской папке
    void requestSize(String folder) {
        if (useNetty)
            sendToServer(new SizeRequest(folder));
        else
            sendToServer(getCommand(COM_GET_SIZE, folder));
    }

    // запрос на удаление файла/папки
    void removeEntry(String name) {
        if (useNetty)
            sendToServer(new RemovalRequest(name));
        else
            sendToServer(getCommand(COM_REMOVE, encodeSpaces(name)));
    }

    // запрос на переименование файла/папки
    void renameEntry(String newName) {
        if (this.newName == null) this.newName = newName;
        if (useNetty)
            sendToServer(new RenameRequest(getSrcPC().getFullSelectedFilename(), newName));
        else
            sendToServer(getCommand(COM_RENAME,
                    encodeSpaces(getSrcPC().getFullSelectedFilename()),
                    encodeSpaces(newName)));
    }

    // общий алгоритм копирования/перемещения файла/папки
    // с предварительной проверкой наличия (и выводом запроса на замену)
    // одноименного элемента в папке назначения;
    // источник определяется по выбранной (сфокусированной) панели
    void makeCopy(boolean move) {
        PanelController srcPC = getSrcPC(), dstPC = getDstPC();
        String opName = bothLocal()
                ? move ? COM_MOVE : COM_COPY
                : srcPC.atServerMode() ? COM_DOWNLOAD : COM_UPLOAD;
        int i = dstPC.getIndexOf(srcPC.getSelectedFilename());
        if (i < 0 && ((dstPC.atServerMode() ? serverFS : dstPC.getClientFS()) == FS_NTFS))
            i = dstPC.getIndexOfAnyMatch(srcPC.getSelectedFilename());
        if (i >= 0)
            if (srcPC.isFileSelected() && dstPC.isFile(i)) {
                if (!Messages.confirmReplacement(srcPC.getSelectedFilename())) return;
            } else {
                Messages.displayWrongReplacement(opName,
                        dstPC.isFile(dstPC.getIndexOfAnyMatch(srcPC.getSelectedFilename())));
                return;
            }

        Path dst = Paths.get(dstPC.getCurPath(), srcPC.getSelectedFilename());
        long size = srcPC.getSelectedFileSize();
        // копировать/переместить файл/папку и обновить панели по завершению
        try {
            if (dstPC.atServerMode()) {
                if (srcPC.atServerMode()) {
                    if (move) srcPC.createRequest();
                    dstPC.createRequest();
                    copyItem(srcPC.getFullSelectedFilename(), dstPC.getCurPath(), move);
                } else
                    upload();
            } else {
                boolean redraw = false;
                if (size <= 0L) {
                    makeFolderOrZero(dst, size, srcPC.getSelectedFileTime());
                    if (size == 0L && move)
                        removeFile(Paths.get(srcPC.getFullSelectedFilename()));
                    redraw = true;
                } else
                    if (srcPC.atServerMode()) {
                        if (i >= 0)
                            removeFile(Paths.get(dstPC.getCurPath(), srcPC.getSelectedFilename()));
                        download();
                    } else {
                        copy(Paths.get(srcPC.getFullSelectedFilename()), dst, move);
                        redraw = true;
                    }
                if (redraw) {
                    String srcName = srcPC.getSelectedFilename();
                    if (move) srcPC.updateFilesList();
                    dstPC.updateFilesList();
                    dstPC.getFilesTable().getSelectionModel().select(getDstPC().getIndexOf(srcName));
                }
            }
        } catch (IOException ex) {
            Messages.displayError(ERR_CANNOT_COMPLETE, ERR_OPERATION_FAILED, opName);
        }
    }

    // отправка запроса копирования с клиента на сервер
    // /upload source_name destination_path size date
    private void upload() {
        //TODO: при копировании больших файлов следовало бы отображать индикатор копирования;
        // локальное копирование/перемещение сейчас выполняется вызовом методов
        // Files.copy/move, не предоставляющих "обратной связи" в процессе,
        // иначе говоря, чтобы иметь возможность узнать в какой-либо момент,
        // насколько процесс передачи выполнен в целом, его нужно заменить
        // на использование того же подхода, что и при передаче через сеть -
        // поблочно, через потоки файлового ввода/вывода
        PanelController cli = getSrcPC(), srv = getDstPC();
        String dst = Paths.get(srv.getCurPath()).toString();
        if (dst.length() == 0 && !useNetty) dst = ".";
        FileInfo fi = cli.getFilesTable().getSelectionModel().getSelectedItem();
        srv.createRequest();
        if (useNetty)
            sendToServer(new UploadRequest(cli.getSelectedFilename(),
                    dst, fi.getSize(), fi.getModifiedAsLong()));
        else
            sendToServer(getCommand(COM_UPLOAD,
                    encodeSpaces(cli.getSelectedFilename()),
                    encodeSpaces(dst), fi.getSize()+"", fi.getModifiedAsLong()+""));
    }

    // продолжать передачу файла с клиента на сервер
    void uploadData(int id) {
        if (id == SRV_SUCCESS)
            onUploaded();
        else {
            PanelController cli = getSrcPC();
            byte[] buf = new byte[BUF_SIZE];
            try (BufferedInputStream bis = new BufferedInputStream(
                    Files.newInputStream(
                            Paths.get(Paths.get(cli.getCurPath(),
                                    cli.getSelectedFilename()).toString())), BUF_SIZE)) {
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

    // отправка запроса копирования с сервера
    // /download source_path
    private void download() {
        //TODO: при копировании больших файлов следовало бы отображать индикатор копирования
        if (useNetty)
            sendToServer(new DownloadRequest(getSrcPC().getFullSelectedFilename()));
        else
            sendToServer(getCommand(COM_DOWNLOAD, encodeSpaces(getSrcPC().getFullSelectedFilename())));
    }

    // продолжать передачу файла с сервера
    void downloadData(int size, byte[] data) {
        boolean success = true;
        if (size > 0) {
            PanelController srv = getSrcPC();
            if (restSize == 0L) restSize = srv.getSelectedFileSize();
            byte[] buf = Arrays.copyOf(data, size);
            try (BufferedOutputStream bos = new BufferedOutputStream(
                    new FileOutputStream(Paths.get(getDstPC().getCurPath(),
                            srv.getSelectedFilename()).toString(), true), BUF_SIZE)) {
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

    // отправка запроса копирования/перемещения внутри папки на сервере
    // /copy source_name destination_path [1=move]
    private void copyItem(String src, String dst, boolean move) {
        //TODO: при копировании больших файлов следовало бы отображать индикатор копирования
        if (useNetty)
            sendToServer(new CopyRequest(src, dst, move));
        else
            sendToServer(getCommand(COM_COPY, encodeSpaces(src),
                    dst.length() == 0 ? "." : encodeSpaces(dst), (move ? "1" : "")+""));
    }

    /*
       реализация копирования drag-and-drop-перетаскиванием
    */
    void dragStarted(MouseEvent ev) {
        dragSrc = getSrcPC().equals(cliCtrl) ? 0 : 1;
        Dragboard db = getSrcPC().getFilesTable().startDragAndDrop(TransferMode.ANY);
        ClipboardContent c = new ClipboardContent();
        c.putString(getSrcPC().getSelectedFilename());
        db.setContent(c);
        ev.consume();
    }

    void dragOver(boolean client, DragEvent ev) {
        if (ev.getDragboard().hasString() && (dragSrc == (client ? 1 : 0)) &&
                (!bothLocal() || !sameContent()))
            ev.acceptTransferModes(TransferMode.ANY);
        ev.consume();
    }

    void dragDropped(DragEvent ev) {
        Dragboard db = ev.getDragboard();
        makeCopy(isShiftDown);
        ev.setDropCompleted(db.hasString());
        ev.consume();
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
            refreshMenus(true);
            refreshFileCmd();
            refreshFileOps();
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
            if (cliCtrl.atServerMode())
                cliCtrl.setLocalMode(srvCtrl.atServerMode() ? "." : srvCtrl.getCurPath());
            srvCtrl.setLocalMode(cliCtrl.getCurPath());
            cliCtrl.setServerFolder(null);
            srvCtrl.setServerFolder(null);
            refreshMenus(false);
            refreshFileCmd();
            refreshFileOps();
        });
    }

    void refreshMenus(boolean loggedIn) {
        menuItemLogIn.setDisable(loggedIn);
        menuItemLogOut.setDisable(!loggedIn);
        menuItemViewLeft.setText("Left list: client");
        menuItemViewRight.setText("Right list: "+(loggedIn ? "server" : "client"));
        menuItemViewRight.setDisable(!loggedIn);
        menuItemViewLeft.setDisable(!loggedIn);
    }

    // при успешном завершении операции с файлом/папкой фокус перемещается на него/нее,
    // в случае команды REMOVE - остается на месте: на следующем (если есть) за удаленным элементе
    void onUploaded() {
        Platform.runLater(() -> {
            PanelController srv = getDstPC();
            srv.updateFilesList();
            srv.getFilesTable().getSelectionModel()
                    .select(srv.getIndexOf(getSrcPC().getSelectedFilename()));
            refreshFileOps();
        });
    }
    void onDownloaded() {
        PanelController cli = getDstPC(), srv = getSrcPC();
        // установить дату и время последней модификации скопированного элемента как у оригинала
        applyDateTime(Paths.get(cli.getCurPath(), srv.getSelectedFilename()).toString(),
                srv.getSelectedFileTime());
        Platform.runLater(() -> {
            cli.updateFilesList();
            //int i = cliCtrl.getIndexOf(srvCtrl.getSelectedFilename());
            // следует учитывать, что индекс вообще находится во всем множестве элементов таблицы,
            // без учета текущего способа их упорядочения, однако выбор с помощью select
            // по этому индексу выбирает именно искомый элемент с учетом сортировки
            cli.getFilesTable().getSelectionModel()
                    .select(cli.getIndexOf(srv.getSelectedFilename()));
            // не срабатывает так, как ожидается
            //cliCtrl.getFilesTable().scrollTo(i);
            refreshFileOps();
        });
    }
    void onRemoved() {
        PanelController other = getDstPC();
        if (bothLocal())
            Platform.runLater(() -> other.updateFreeSpace(freeSpace));
        if (sameContent()) {
            other.setServerFolder(getSrcPC().getServerFolder());
            other.updateFilesList();
        }
        refreshFileOps();
    }
    void onRenamed() {
        onRemoved();
        newName = null;
    }

    /*
       обработать ошибки копирования, удаления и переименования
    */
    void onFailed(String cmd, ErrorCode errCode) {
        if (cmd.equals(COM_DOWNLOAD)) {
            PanelController srv = getSrcPC();
            if (Messages.getRemovalConfirmation(true, srv.getSelectedFilename(), false))
                removeFile(Paths.get(srv.getFullSelectedFilename()));
        } else switch (errCode) {
            case ERR_OUT_OF_SPACE:
                Platform.runLater(() ->
                    Messages.displayError(ERR_CANNOT_COMPLETE,
                        ERR_OPERATION_FAILED, errMessage[errCode.ordinal()], cmd));
                break;
            case ERR_INTERNAL_ERROR:
                Platform.runLater(() -> Messages.displayError(errCode, ERR_OPERATION_FAILED));
                break;
            case ERR_CANNOT_COMPLETE:
            case ERR_NOT_EMPTY:
                Platform.runLater(() -> Messages.displayError(errCode, ERR_OPERATION_FAILED, cmd));
        }
    }

    /*
       прочие вспомогательные методы
    */
    // нахождение обеих панелей в локальном (клиент/клиент или сервер/сервер) режиме
    private boolean bothLocal() {
        return ((cliCtrl.atServerMode() && srvCtrl.atServerMode()) ||
                (!cliCtrl.atServerMode() && !srvCtrl.atServerMode()));
    }

    // проверить, отображают ли (ссылаются ли на) одно и то же обе панели
    private boolean sameContent() { return cliCtrl.getCurPath().equals(srvCtrl.getCurPath()); }

    // обновить доступность субменю операций с файлами/папками
    void refreshFileOps() {
        boolean noneSelected = !clientFocused && !serverFocused, cantCopy, cantMove;
        ActionMenu.setDisable(noneSelected);
        menuItemRemove.setDisable(noneSelected);
        menuItemRename.setDisable(noneSelected);
        if (bothLocal())
            cantCopy = cantMove = noneSelected || sameContent();
        else {
            cantCopy = noneSelected || (clientFocused && cliCtrl.atServerMode()) ||
                    (serverFocused && srvCtrl.atServerMode());
            cantMove = noneSelected || (clientFocused && !cliCtrl.atServerMode()) ||
                    (serverFocused && !srvCtrl.atServerMode());
        }
        menuItemUpload.setDisable(cantCopy);
        menuItemDownload.setDisable(cantMove);
        cliCtrl.refreshFileOps(cantCopy, cantMove);
        srvCtrl.refreshFileOps(cantCopy, cantMove);
    }

    // обновить названия операций с файлами/папками
    void refreshFileCmd() {
        boolean both = bothLocal();
        String cmdCopy = capitalize(both ? COM_COPY : COM_UPLOAD),
               cmdMove = capitalize(both ? COM_MOVE : COM_DOWNLOAD);
        menuItemUpload.setText(cmdCopy);
        menuItemDownload.setText(cmdMove);
        cliCtrl.refreshFileCmd(cmdCopy, cmdMove);
        srvCtrl.refreshFileCmd(cmdCopy, cmdMove);
    }

    // обновление второй панели при необходимости,
    // вызывается контроллером панели при ее обновлении
    void onPanelUpdated() {
        if (sameContent()) {
            int csi = cliCtrl.getFilesTable().getSelectionModel().getSelectedIndex(),
                    ssi = srvCtrl.getFilesTable().getSelectionModel().getSelectedIndex();
            boolean cf = clientFocused, sf = serverFocused;
            cliCtrl.updateFilesList();
            srvCtrl.updateFilesList();
            cliCtrl.getFilesTable().getSelectionModel().select(csi);
            srvCtrl.getFilesTable().getSelectionModel().select(ssi);
            if (cf) cliCtrl.getFilesTable().requestFocus();
            if (sf) srvCtrl.getFilesTable().requestFocus();
            refreshFileOps();
        }
    }

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

    // панель с фокусом является источником при операциях копирования/перемещения
    private PanelController getSrcPC() { return cliCtrl.getFilesTable().isFocused() ? cliCtrl : srvCtrl; }
    private PanelController getDstPC() { return cliCtrl.getFilesTable().isFocused() ? srvCtrl : cliCtrl; }

    // определить панель с запросом
    private PanelController getRequestingPC() {
        return cliCtrl.atServerMode() && cliCtrl.hasRequest() ? cliCtrl : srvCtrl;
    }

    /*
      обработчики пунктов меню File
    */
    // отобразить окно авторизации/регистрации
    @FXML public void showRegForm() {
        if (regStage == null) createRegStage();
        regStage.show();
    }

    // завершение сеанса пользователя
    @FXML void logOut() {
        if (useNetty) {
            sendToServer(new LogoutRequest());
        } else
            sendToServer(getExitCommand().get(0));
    }

    // копирование файла/папки - локальное или с клиента на сервер
    @FXML void copyOrUpload() { makeCopy(false); }
    // перемещение файла/папки - локальное или с сервера на клиент
    @FXML void moveOrDownload() { makeCopy(true); }

    /**
     *    удаление выбранного файла (папки) может происходить как по нажатию Delete,
     *    так и по команде меню Remove.
     *    решение о способе удаления принимается в зависимости от нахождения файла/папки:
     *    на клиенте - выполнить физическое удаление и, при необходимости,
     *       обновить содержимое второй клиентской панели;
     *    на сервере - отправить запрос серверу
     */
    @FXML void tryRemove() {
        PanelController pc = getSrcPC();
        if (Messages.getRemovalConfirmation(pc.getSelectedFileSize() >= 0,
                pc.getSelectedFilename(), true)) {
            if (pc.atServerMode()) {
                pc.createRequest();
                removeEntry(pc.getFullSelectedFilename());
            } else
                try {
                    Files.delete(Paths.get(pc.getFullSelectedFilename()));
                    pc.updateFilesList();
                    if (sameContent()) getDstPC().updateFilesList();
                } catch (IOException ex) {
                    ErrorCode errCode = ERR_NO_SUCH_FILE;
                    if (ex instanceof DirectoryNotEmptyException) errCode = ERR_NOT_EMPTY;
                    Messages.displayError(errCode, ERR_OPERATION_FAILED, COM_REMOVE);
                }
            refreshFileOps();
        }
    }

    // переименование файла/папки
    @FXML void tryRename() {
        PanelController pc = getSrcPC();
        pc.setEditing(true);
        newName = Messages.getInputValue(
                    "Renaming '"+pc.getSelectedFilename()+"'",
                    "Input new name for '"+pc.getSelectedFilename()+"'",
                    "New name:", pc.getSelectedFilename());
        pc.setEditing(false);
        if (newName == null) return;
        if (pc.renameItem(pc.getSelectedFilename(), newName, false))
            if (pc.atServerMode()) {
                pc.createRequest();
                renameEntry(newName);
            } else
                if (sameContent())
                    (cliCtrl.getFilesTable().isFocused() ? srvCtrl : cliCtrl).updateFilesList();
    }

    /**
     *    отобразить размер выбранного элемента, вычислив его, если элемент - папка,
     *    в последнем случае, если элемент находится на сервере, только отправить ему запрос -
     *    отображение будет произведено после получения отклика сервера
     */
    //
    @FXML void getSize() {
        PanelController pc = getSrcPC();
        long size = pc.getSelectedFileSize();
        int itemCount = 0;
        boolean folder = size < 0;
        if (size < 0)
            if (pc.atServerMode())
                requestSize(pc.getFullSelectedFilename());
            else {
                Path p = Paths.get(pc.getFullSelectedFilename());
                size = FileInfo.getSizes(p);
                itemCount = FileInfo.getItems(p).size();
            }
        if (size >= 0) displayItemSize(folder, size, itemCount);
    }

    // отобразить информацию о размере выбранного файла/папки
    void displayItemSize(boolean folder, long size, int itemCount) {
        DecimalFormat fmt = (DecimalFormat)NumberFormat.getInstance();
        DecimalFormatSymbols sym = fmt.getDecimalFormatSymbols();
        sym.setGroupingSeparator(' ');
        fmt.setDecimalFormatSymbols(sym);
        String s = "Selected "+(folder ? "folder" : "file")+" "+
                (folder && size == 0 && itemCount == 0
                        ? "is empty"
                        : "size: "+fmt.format(size)+" bytes")+
                ".";
        Messages.displayMessage(Alert.AlertType.INFORMATION, s, "Selection size");
    }

    // действия при выходе
    @FXML public void performExit() {
        logOut();
        // с использованием библиотеки JNativeHook
        // вызов Platform.exit не завершает работу приложения
        System.exit(0); //Platform.exit();
    }

    /*
      обработчики пунктов меню View
    */
    @FXML void toggleViewLeft() {
        menuItemViewLeft.setText("Left list: "+
            (menuItemViewLeft.getText().endsWith("client") ? "server" : "client"));
        if (menuItemViewLeft.getText().endsWith("server")) {
            cliCtrl.setServerMode(srvCtrl.atServerMode() ? srvCtrl.getCurPath() : "");
            if (!srvCtrl.atServerMode()) {
                cliCtrl.setServerFolder(null);
                cliCtrl.createRequest();
                requestFiles("");
            } else
                cliCtrl.setServerFolder(srvCtrl.getServerFolder());
            cliCtrl.updateFilesList();
            cliCtrl.updateFreeSpace(freeSpace);
        } else
            cliCtrl.setLocalMode(srvCtrl.atServerMode() ? "." : srvCtrl.getCurPath());
        refreshFileCmd();
        refreshFileOps();
        menuItemViewLeft.setSelected(false);
    }

    @FXML void toggleViewRight() {
        menuItemViewRight.setText("Right list: "+
            (menuItemViewRight.getText().endsWith("client") ? "server" : "client"));
        if (menuItemViewRight.getText().endsWith("server")) {
            srvCtrl.setServerMode(cliCtrl.atServerMode() ? cliCtrl.getCurPath() : "");
            if (!cliCtrl.atServerMode()) {
                srvCtrl.setServerFolder(null);
                srvCtrl.createRequest();
                requestFiles("");
            } else
                srvCtrl.setServerFolder(cliCtrl.getServerFolder());
            srvCtrl.updateFilesList();
            srvCtrl.updateFreeSpace(freeSpace);
        } else
            srvCtrl.setLocalMode(cliCtrl.atServerMode() ? "." : cliCtrl.getCurPath());
        refreshFileCmd();
        refreshFileOps();
        menuItemViewRight.setSelected(false);
    }

    /*
       инициализация приложения
    */
    @Override public void initialize(URL url, ResourceBundle resourceBundle) {
        Platform.runLater(() -> {
            stage = (Stage)clientView.getScene().getWindow();
            stage.setOnCloseRequest(ev -> performExit());
        });

        try { GlobalScreen.registerNativeHook(); }
        catch (NativeHookException e) { throw new RuntimeException(e); }
        GlobalScreen.addNativeKeyListener(this);

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
        cliCtrl.getFilesTable().setOnDragDetected(this::dragStarted);
        srvCtrl.getFilesTable().setOnDragDetected(this::dragStarted);
        cliCtrl.getFilesTable().setOnDragOver(ev -> dragOver(true, ev));
        srvCtrl.getFilesTable().setOnDragOver(ev -> dragOver(false, ev));
        cliCtrl.getFilesTable().setOnDragDropped(this::dragDropped);
        srvCtrl.getFilesTable().setOnDragDropped(this::dragDropped);
        // названия пунктов меню операций с файлами/папками
        // связаны с названиями соответствующих команд
        menuItemRemove.setText(capitalize(COM_REMOVE));
        menuItemRename.setText(capitalize(COM_RENAME));
        menuItemGetSize.setText(COM_GET_SIZE_TITLE);
        refreshFileCmd();

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