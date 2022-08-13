import static prefs.Prefs.*;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.stage.Stage;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.beans.value.ChangeListener;

import java.net.URL;
import java.util.ResourceBundle;

public class RegController implements Initializable {
    @FXML private TextField loginField, emailField, usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextArea textArea;
    @FXML private Button btnAuth;
    @FXML private HBox buttonContainer;
    @FXML private GridPane gridPane;
    private Button btnReg, btnMoreLess;
    @FXML private Label emailLabel, usernameLabel;
    /*Label emailLabel, usernameLabel;
    TextField emailField, usernameField;*/

    private NeStController controller;
    private Stage stage;
    private boolean canRegister;

    void setController (NeStController controller) {
        this.controller = controller;
    }

    boolean incompleteUserData() {
        for (int i = 0; i < passwordField.getText().length(); i++)
            if (passwordField.getText().charAt(i) > 127 ||
                passwordField.getText().charAt(i) < 32) return false;
        return loginField.getText().trim().length() == 0 ||
               passwordField.getText().trim().length() < MIN_PWD_LEN ||
               passwordField.getText().trim().length() > MAX_PWD_LEN;
    }

    boolean incompleteRegData() {
        return incompleteUserData() ||
                emailField.getText().trim().length() == 0 ||
                usernameField.getText().trim().length() == 0;
    }

    @FXML void authorize(/*ActionEvent actionEvent*/) {
        if (!controller.authorized()) {
            String login = loginField.getText().trim();
            String password = passwordField.getText().trim();
            Platform.runLater(() -> {
                updateButtons();
                if (btnAuth.isDisabled()) {
                    if (login.length() == 0) loginField.requestFocus();
                    else if (password.length() == 0) passwordField.requestFocus();
                } else
                    controller.authorize(login, password);
            });
        }
    }

    @FXML public void register(/*ActionEvent actionEvent*/) {
        if (!controller.authorized()) {
            String login = loginField.getText().trim(),
                   password = passwordField.getText().trim(),
                   email = emailField.getText().trim(),
                   username = usernameField.getText().trim();
            Platform.runLater(() -> {
                updateButtons();
                if (canRegister) {
                    if (btnReg.isDisabled()) {
                        if (login.length() == 0) loginField.requestFocus();
                        else if (password.length() == 0) passwordField.requestFocus();
                        else if (canRegister && email.length() == 0) emailField.requestFocus();
                        else if (canRegister && username.length() == 0) usernameField.requestFocus();
                    } else
                        controller.register(login, password, email, username);
                } else
                    if (btnAuth.isDisabled()) {
                        if (login.length() == 0) loginField.requestFocus();
                        else if (password.length() == 0) passwordField.requestFocus();
                    } else
                        controller.authorize(login, password);
            });
        }
    }

    // авторизация и регистрация - 2 разных вида запросов -
    //  при первой вводятся логин и пароль,
    //  при второй - также данные пользователя, например, ФИО и email
    //
    //  если при авторизации пользователя в базе он не найден,
    //  то может зарегистрироваться - для этого должны отображаться
    //  поля ввода дополнительных данных (см. выше) и кнопка регистрации
    //  TODO: кнопка "свернуть/развернуть" должна не висеть посередине окна
    //        сбоку, а прилегать снизу к отображаемым полям ввода и
    //        увеличивать или уменьшать высоту окна для отображения
    //        дополнительных полей, оставаясь при этом прилегающей
    //        к нижнему полю ввода
    @FXML void showMoreOrLess(/*ActionEvent actionEvent*/) {
        canRegister = btnMoreLess.getText().equals("▼");
        stage.setTitle(SHORT_TITLE + " authorization" + (canRegister ? "/registration" : ""));

        if (buttonContainer.getChildren().size() == 1)
            buttonContainer.getChildren().add(btnReg);
        else
            buttonContainer.getChildren().remove(btnReg);
        gridPane.getChildren().remove(btnMoreLess);
        gridPane.add(btnMoreLess, 0, canRegister ? 3 : 1);
        emailLabel.setVisible(!emailLabel.isVisible());
        emailField.setVisible(!emailField.isVisible());
        usernameLabel.setVisible(!usernameLabel.isVisible());
        usernameField.setVisible(!usernameField.isVisible());
        /*if (canRegister) {
            gridPane.addRow(2, new Label(""), emailLabel, emailField);
            gridPane.addRow(3, new Label(""), usernameLabel, usernameField);
        } else {
            gridPane.getChildren().removeAll(emailLabel, emailField, usernameLabel, usernameField);
        }*/
        btnMoreLess.setText(canRegister ? "▲" : "▼");
        btnMoreLess.getTooltip().setText(canRegister ? "Show less" : "Show more");
    }

    void addMessage(String msg) { textArea.appendText(msg); }

    void updateButtons() {
        btnAuth.setDisable(controller.authorized() || incompleteUserData());
        btnReg.setDisable(controller.authorized() || incompleteRegData());
    }

    void createAdditionalControls() {
        btnReg = new Button("register");
        btnReg.setDisable(true);
        btnReg.setOnAction((ev) -> register());

        btnMoreLess = new Button("▼");
        btnMoreLess.setTextOverrun(OverrunStyle.CLIP);
        btnMoreLess.setFocusTraversable(false);
        btnMoreLess.setTooltip(new Tooltip("Show more"));
        btnMoreLess.setOnAction((ev) -> showMoreOrLess());
        gridPane.add(btnMoreLess, 0, 1);
        /*emailLabel = new Label("email");
        emailField = new TextField();
        emailField.setPromptText("email");
        emailField.setOnAction((ev) -> register());
        usernameLabel = new Label("name");
        usernameField = new TextField();
        usernameField.setPromptText("user name");
        usernameField.setOnAction((ev) -> register());*/
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Platform.runLater(() -> {
            stage = (Stage) loginField.getScene().getWindow();
            stage.setOnShown(event -> loginField.requestFocus());
            stage.setOnCloseRequest(event -> {
                textArea.clear();
                loginField.clear();
                passwordField.clear();
                emailField.clear();
                usernameField.clear();
            });
            createAdditionalControls();
            ChangeListener<String> changeListener = (observable, oldValue, newValue) -> {
                btnAuth.setDisable(controller.authorized() || incompleteUserData());
                btnReg.setDisable(controller.authorized() || incompleteRegData());
            };
            loginField.textProperty().addListener(changeListener);
            passwordField.textProperty().addListener(changeListener);
            emailField.textProperty().addListener(changeListener);
            usernameField.textProperty().addListener(changeListener);
        });
    }
}