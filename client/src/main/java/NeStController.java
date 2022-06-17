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
            NEST_ERR код-ошибки -
             0 - не верное имя пользователя или пароль,
             1 - запрошенный файл/папка отсутствует,
             2 - недостаточно места для загрузки файла,
             3 - ошибка копирования файла/папки.
          ! несмотря на то, что коды ошибок более или менее однозначно определяют запрос/команду,
          ! при котороых они произошли, тем не менее, во избежание недоразумений, скорее всего,
          ! перед кодом ошибки нужно также добавить имя_команды/запроса;
          ! поскольку проектирование происходит "на ходу" и затягивается, эта идея будет реализована
          ! позже, если в дальнейшей реализации проекта останется смысл.

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
       числу файлов в ней, за которым возвращается список соответствующей длины
    4. вернуть размер свободного места на сервере:
        /space,
       размер в байтах возвращается в значении доп. кода.
    5. залить файл/папку с клиента на сервер:
        /upload имя_файла/папки-на-компьютере-клиента путь-на-компьютере-клиента имя-на-сервере размер
    6. скачать файл/папку с сервера на клиент:
        /download имя-на-сервере путь-на-компьютере-клиента размер

    команды 5 и 6 работают по принципу "1 за раз" - например,
    при заливке папки на сервер (размер указывается как -1)
    она только создается на нем, но ничем не наполняется,
    даже если на компьютере клиента в ней что-либо есть.
    > если папка содержит несколько подпапок и/или файлов,
      перед заливкой ее на сервер нужно убедиться в наличии
      достаточного свободного места.
*/
import prefs.Prefs;

import javafx.application.Platform;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ResourceBundle;

public class NeStController implements Initializable {
    @FXML VBox clientView, serverView;
    // копирование файлов на/с сервера хотел сделать через drag-n-drop, но пока не соображу,
    // как информировать методы перетаскивания файлов панелей о получении фокуса одной из них
    @FXML MenuItem menuItemLogIn, menuItemLogOut, menuItemUploadFile, menuItemDownloadFile;

    private Network network;

    private Stage stage, regStage;
    private RegController regController;

    PanelController cliCtrl, srvCtrl;

    boolean authorized = false, clientFocused, serverFocused;

    String user, password;

    // цикл обработки ответов сервера на запросы/команды
    private void readLoop() {
        try {
            while (true) {
                String cmd = network.read();
                if (cmd.startsWith(Prefs.COM_ID)) {
                    if (!authorized) {
                        // без авторизации (или после завершения пользователем своего сеанса)
                        // ожидается только команда авторизации (ответ сервера на нее)
                        if (cmd.startsWith(Prefs.getCommand(Prefs.SRV_REFUSE))) {
                            String[] val = cmd.split(" ", 2);
                            Platform.runLater(() ->
                                    regController.textArea.appendText("Login failed with code " + val[1]));
                        } else if (cmd.startsWith(Prefs.getCommand(Prefs.SRV_ACCEPT))) {
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
                                switch (arg[1]) {
                                    case Prefs.COM_GET_SPACE:
                                        long l = Prefs.MAXSIZE;
                                        try { l = Long.parseLong(arg[2]); } catch (Exception ex) {}
                                        final long free = l;
                                        Platform.runLater(() -> srvCtrl.updateFreeSpace(free));
                                        break;
                                    case Prefs.COM_GET_FILES:
                                        break;
                                    case Prefs.COM_UPLOAD:
                                        // обновить список файлов сервера после успешной передачи
                                        Platform.runLater(() -> {
                                            srvCtrl.updateFilesList(Paths.get(srvCtrl.getCurPath()));
                                            displayMessage("Uploaded successfully");
                                        });
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
                            String[] response = cmd.split(" ", 2);
                            int errCode;
                            try { errCode = Integer.parseInt(response[1]); }
                            catch (Exception ex) { errCode = 0; }
                            final String errMsg = Prefs.errMessage[errCode];
                            Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, errMsg).showAndWait());
                        }
                    }
                }
            }
        } catch (Exception e) { System.err.println("Connection lost"); }
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

    // запрос на копирование файла/папки на сервер
    @FXML void upload() {
        // /upload source_name source_path relative_destination_path size
        // TODO: отправлять также дату файла
        String dst = Paths.get(Prefs.serverURL).relativize(Paths.get(srvCtrl.getCurPath())).toString();
        if (dst.length() == 0) dst = ".";
        sendCmdOrRequest(Prefs.getCommand(Prefs.COM_UPLOAD,
                cliCtrl.getSelectedFilename(),
                cliCtrl.getCurPath(),
                dst,
                cliCtrl.filesTable.getSelectionModel().getSelectedItem().getSize() + ""));
    }

    // запрос на копирование файла/папки с сервера
    @FXML void download() {
        // /download server_source_path destination_path size
        // TODO: отправлять также дату файла
        sendCmdOrRequest(Prefs.getCommand(Prefs.COM_DOWNLOAD,
                srvCtrl.getSelectedFilename(),
                cliCtrl.getCurPath(),
                srvCtrl.filesTable.getSelectionModel().getSelectedItem().getSize()+""));
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

    void updateFileOps() {
        menuItemUploadFile.setDisable(!(clientFocused & srvCtrl.isServerMode()));
        menuItemDownloadFile.setDisable(!(serverFocused & srvCtrl.isServerMode()));
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        Platform.runLater(() -> stage = (Stage)clientView.getScene().getWindow());

        cliCtrl = (PanelController)clientView.getProperties().get("ctrlRef");
        srvCtrl = (PanelController)serverView.getProperties().get("ctrlRef");

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
            network = new Network(Prefs.PORT);
            Thread readThread = new Thread(() -> readLoop());
            readThread.setDaemon(true);
            readThread.start();
        } catch (Exception ex) { System.err.println(ex.getMessage()); }
    }
}