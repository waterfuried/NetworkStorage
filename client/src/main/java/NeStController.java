/*
    > на сервере у пользователя есть папка,
      в пределах которой он может размещать файлы и папки.
    > их число не ограничено,
      но суммарный их размер не должен превышать N.
    > выйти за пределы своей папки пользователь не может.

    команды/запросы, возвращают
        - в случае успешного завершения команды/запроса:
            NEST_DONE имя_команды/запроса дополнительный_код;
             доп. код в большинстве случаев равен 0, если он имеет
             особое значение, это указывается в описании;
             поскольку без авторизации никакие действия не могут быть выполнены,
             возвращать после NEST_DONE имя запроса авторизации нет смысла;
        - в случае ошибки:
            NEST_ERR код-ошибки [имя_команды/запроса] -
             0 - не верное имя пользователя или пароль,
             1 - запрошенный файл/папка отсутствует,
             2 - недостаточно места для загрузки файла,
             3 - ошибка копирования файла/папки.
          ! в процессе разработки в протокол было внесено дополнение:
          ! несмотря на то, что коды ошибок более или менее однозначно определяют запрос/команду,
          ! при которых они произошли, во избежание недоразумений, для некоторых запросов/команд,
          ! например, при отправке файла с/на сервер, после кода ошибки укзывается имя команды
          ! (запроса) - это позволит корректно обрабатывать ситуации неполной передачи данных;

    1. авторизация пользователя по имени;
       указание пароля требуется, но пока он не проверяется;
       длина пароля должна быть не меньше 4 (первоначально):
        /user имя пароль
       после доп. кода 0 возвращаются полученные имя и пароль
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
        /upload имя_файла/папки-на-компьютере-клиента путь-на-компьютере-клиента имя-на-сервере размер [дата]
    6. скачать файл/папку с сервера на клиент:
        /download имя-на-сервере путь-на-компьютере-клиента размер [дата]

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
*/
import prefs.Prefs;

import cloud.*;

import javafx.application.Platform;
import javafx.fxml.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.io.*;

import java.net.URL;

import java.nio.file.Paths;

import java.util.*;

public class NeStController implements Initializable {
    @FXML VBox clientView, serverView;
    // копирование файлов на/с сервера хотел сделать через drag-n-drop, но пока не соображу,
    // как информировать методы перетаскивания файлов панелей о получении фокуса одной из них
    @FXML MenuItem menuItemLogIn, menuItemLogOut, menuItemUploadFile, menuItemDownloadFile;

    private Network network;
    private NettyNetwork nettyNetwork;

    private Stage stage, regStage;
    private RegController regController;

    PanelController cliCtrl, srvCtrl;

    boolean authorized = false, clientFocused, serverFocused, useNetty = false;

    String user, password;

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
                            Platform.runLater(() ->
                                    regController.textArea.appendText("Login failed with code " + val[1]));
                        } else
                            if (cmd.startsWith(Prefs.getCommand(Prefs.SRV_ACCEPT))) {
                            String[] val = cmd.split(" ", 4);
                            user = val[2];
                            password = val[3];
                            authorized = true;
                            Platform.runLater(() -> {
                                setTitle();
                                regController.textArea.appendText("Logged in as " + user);
                                regController.btnReg.setDisable(true);
                                srvCtrl.setServerMode();
                                menuItemLogIn.setDisable(true);
                                menuItemLogOut.setDisable(false);
                            });
                            // сразу после авторизации запросить у сервера список файлов
                            // из папки пользователя и размер оставшегося в ней свободного места
                            requestFiles("");
                            requestFreeSpace();
                        }
                    } else {
                        // после авторизации могут идти любые команды/запросы, кроме команды авторизации
                        // завершение сеанса пользователя обрабатыать особо
                        if (Prefs.isExitCommand(cmd)) {
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
                                                                fi.add(new FileInfo(item[0],
                                                                        Long.parseLong(item[1]),
                                                                        FileInfo.getModified(Long.parseLong(item[2]))));
                                                            } catch (Exception ex) {
                                                                correct = false;
                                                                ex.printStackTrace();
                                                            }
                                                        }
                                                    }
                                                    if (correct) srvCtrl.setList(fi);
                                                }
                                                list.clear();
                                            } else
                                                srvCtrl.setList(null);
                                            if (correct)
                                                srvCtrl.updateFilesList(folder);
                                            else
                                                displayError(Prefs.ERR_WRONG_LIST);
                                        }
                                        break;
                                    case Prefs.COM_UPLOAD:
                                        // обновить список файлов сервера после успешной передачи
                                        Platform.runLater(() -> displayMessage("Uploaded successfully"));
                                        requestFiles(srvCtrl.getCurPath());
                                        break;
                                    case Prefs.COM_DOWNLOAD:
                                        // обновить список файлов клиента после успешной передачи
                                        Platform.runLater(() -> {
                                            cliCtrl.updateFilesList(Paths.get(cliCtrl.getCurPath()));
                                            displayMessage("Downloaded successfully");
                                        });
                                        break;
                                }
                            }
                        }
                        // обработать ошибки при выполнении команд/запросов
                        if (cmd.startsWith(Prefs.getCommand(Prefs.SRV_REFUSE))) {
                            String[] response = cmd.split(" ", 3);
                            int errCode;
                            try { errCode = Integer.parseInt(response[1]); }
                            catch (Exception ex) { errCode = 0; }
                            final String errMsg = Prefs.errMessage[errCode];
                            int opCode = 0;
                            // в случае неудачного копирования файла/папки
                            // вывести вопрос на повтор операции
                            if (response.length == 3) {
                                if (response[2].equals(Prefs.COM_UPLOAD) &&
                                        !menuItemUploadFile.isDisable()) opCode = 1;
                                if (response[2].equals(Prefs.COM_DOWNLOAD) &&
                                        !menuItemDownloadFile.isDisable()) opCode = 2;
                            }
                            // при медленном соединении операция передачи может выполняться какое-то
                            // время, за которое пользователь может изменить выбор файла/папки
                            // в списке, поэтому вывод запроса на повтор операции может вызывать
                            // недоразумения - передается всегда выбранный в данный момент элемент;
                            // чтобы их избежать, в ответе на запрос передачи нужно было бы возвращать
                            // имя передаваемого файла/папки, но это будет уже перегрузкой протокола
                            if (opCode > 0) {
                                final int op = opCode;
                                Platform.runLater(() -> {
                                    if (getConfirmation(Alert.AlertType.ERROR,
                                            "Error has been occurred during " +
                                                    response[2] + "ing.\nWould you like to retry?",
                                            Prefs.capitalize(response[2]) + "ing error"))
                                        if (op == 1) tryUpload(); else tryDownload();
                                });
                            } else
                                displayError(errMsg);
                        }
                    }
                }
            }
        } catch (Exception ex) { System.err.println("Connection lost"); ex.printStackTrace(); }
    }

    private void readLoopNetty() {
        try {
            while (true) {
                CloudMessage message = nettyNetwork.read();
                if (message instanceof AuthRequest) {
                    AuthRequest auth = (AuthRequest)message;
                    System.out.println(auth.getUser()+" "+auth.getPassword());
                } else
                // запрос на список файлов на сервере
                if (message instanceof FilesList) {
                    FilesList filesList = (FilesList)message;
                    Platform.runLater(() -> {
                    });
                } else
                    // получение файла клиентом от сервера
                    if (message instanceof FileMessage) {
                        FileMessage fileMessage = (FileMessage)message;
                        Platform.runLater(() -> {
                        });
                    }
            }
        } catch (Exception ex) { System.err.println("Connection lost"); ex.printStackTrace(); }
    }

    void sendCmdOrRequest(String cmdReq) {
        if (network != null)
            try {
                network.getOs().writeUTF(cmdReq);
                //network.getOs().flush();
            } catch (IOException ex) { ex.printStackTrace(); }
    }

    // отдельные команды/запросы к серверу
    // запрос авторизации
    void authorize(String login, String password) {
        if (useNetty)
            try {
                nettyNetwork.write(new AuthRequest(login, password));
            } catch (IOException ex) { ex.printStackTrace(); }
        else
            sendCmdOrRequest(String.format(
                Prefs.getCommand(Prefs.COM_AUTHORIZE, "%s %s"), login, password));
    }

    // запрос на завершение сеанса пользователя
    @FXML void logOut(/*ActionEvent actionEvent*/) {
        sendCmdOrRequest(Prefs.getExitCommand().get(0));
    }

    // запрос списка файлов в пользовательской папке (или в ее подпапке) на сервере
    void requestFiles(String folder) {
        sendCmdOrRequest(Prefs.getCommand(Prefs.COM_GET_FILES, folder));
    }

    // запрос свободного места в пользовательской папке на сервере
    void requestFreeSpace() {
        sendCmdOrRequest(Prefs.getCommand(Prefs.COM_GET_SPACE));
    }

    int checkPresence(boolean atServer) {
        List<FileInfo> list = (atServer ? srvCtrl : cliCtrl).filesTable.getItems();
        if (list != null && list.size() > 0) {
            String name = (atServer ? cliCtrl : srvCtrl)
                    .filesTable.getSelectionModel().getSelectedItem().getFilename();
            boolean present = false, isFile = false;
            int i = 0;
            while (i < list.size() && !present) {
                present = list.get(i).getFilename().equals(name);
                if (present) isFile = list.get(i).getSize() >= 0; else i++;
            }
            if (present)
                return isFile ? 0 : 1;
        }
        return -1;
    }

    boolean getReplaceConfirmation(String entry, String name) {
        return (getConfirmation(Alert.AlertType.WARNING,
                entry+" "+name + " already exists at destination.\n" +
                        "Would you like to replace it?",
                entry+" already exists"));
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
        // /upload source_name source_path relative_destination_path size [date]
        //String dst = Paths.get(Prefs.serverURL).relativize(Paths.get(srvCtrl.getCurPath())).toString();
        String dst = Paths.get(Prefs.serverURL, srvCtrl.getCurPath()).toString();
        if (dst.length() == 0) dst = ".";
        FileInfo fi = cliCtrl.filesTable.getSelectionModel().getSelectedItem();
        if (useNetty)
            try {
                nettyNetwork.write(new FileMessage(Paths.get(cliCtrl.getCurPath(), cliCtrl.getSelectedFilename())));
            } catch (IOException ex) { ex.printStackTrace(); }
        else
            sendCmdOrRequest(Prefs.getCommand(Prefs.COM_UPLOAD,
                cliCtrl.getSelectedFilename(),
                cliCtrl.getCurPath(),
                dst,
                fi.getSize()+"",
                fi.getModifiedAsLong()+""));
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
        // /download server_source_path destination_path size [date]
        FileInfo fi = srvCtrl.filesTable.getSelectionModel().getSelectedItem();
        if (useNetty)
            try {
                nettyNetwork.write(new FileRequest(srvCtrl.getSelectedFilename()));
            } catch (IOException ex) { ex.printStackTrace(); }
        else
            sendCmdOrRequest(Prefs.getCommand(Prefs.COM_DOWNLOAD,
                srvCtrl.getSelectedFilename(),
                cliCtrl.getCurPath(),
                fi.getSize()+"",
                fi.getModifiedAsLong()+""));
    }

    private List<String> getFiles(String path) {
        String[] list = new File(path).list();
        assert list != null;
        return Arrays.asList(list);
    }

    // прочие вспомогательные методы
    private void setTitle() {
        Platform.runLater(() -> {
            String title = "";
            if (user != null && user.length() > 0) title = "[ " + user + " ] ";
            stage.setTitle(title + Prefs.FULL_TITLE);
        });
    }

    @FXML public void performExit(/*ActionEvent ev*/) {
        Platform.exit();
    }

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

    @FXML public void showRegForm(/*ActionEvent actionEvent*/) {
        if (regStage == null) createRegStage();
        regStage.show();
    }

    void displayMessage(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(msg);
        alert.setHeaderText(msg);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    void displayError(String errMsg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, errMsg).showAndWait());
    }

    void displayError(int errCode) {
        displayError(Prefs.errMessage[errCode]);
    }

    boolean getConfirmation(Alert.AlertType eventType, String msg, String title) {
        //ButtonType btnYes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
        //ButtonType btnNo = new ButtonType("No", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(eventType, msg, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(title);
        //alert.setContentText(msg);
        return alert.showAndWait().get() == ButtonType.YES;
    }

    void updateFileOps() {
        menuItemUploadFile.setDisable(!(clientFocused & srvCtrl.isServerMode()));
        menuItemDownloadFile.setDisable(!(serverFocused & srvCtrl.isServerMode()));
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        Platform.runLater(() -> stage = (Stage)clientView.getScene().getWindow());

        cliCtrl = (PanelController)clientView.getProperties().get("ctrlRef");
        srvCtrl = (PanelController)serverView.getProperties().get("ctrlRef");
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
                    clientFocused = newValue;
                    updateFileOps();
                });
        srvCtrl.filesTable.getSelectionModel().selectedIndexProperty().addListener(
                (observableValue, oldValue, newValue) -> {
                    serverFocused = newValue.intValue() >=0;
                    updateFileOps();
                });
        srvCtrl.filesTable.focusedProperty().addListener(
                (observableValue, oldValue, newValue) -> {
                    serverFocused = newValue;
                    updateFileOps();
                });

        try {
            /*
            homeDir = "client_files";
            clientView.getItems().clear();
            clientView.getItems().addAll(getFiles(homeDir));
            */
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