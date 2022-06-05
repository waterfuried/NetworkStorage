import prefs.*;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.stage.Stage;
import javafx.scene.control.*;
import javafx.beans.value.ChangeListener;

import java.net.URL;
import java.util.ResourceBundle;

public class RegController implements Initializable {
    @FXML TextField loginField;
    @FXML PasswordField passwordField;
    @FXML TextArea textArea;
    @FXML Button btnReg;

    private NeStController controller;

    void setController (NeStController controller) {
        this.controller = controller;
    }

    boolean incompleteUserData() {
        return loginField.getText().trim().length() == 0 ||
               passwordField.getText().trim().length() < Prefs.MIN_PWD_LEN;
    }

    @Override
    public void initialize (URL location, ResourceBundle resources) {
        Platform.runLater(() -> {
            Stage stage = (Stage) loginField.getScene().getWindow();
            stage.setOnShown(event -> loginField.requestFocus());
            stage.setOnCloseRequest(event -> {
                textArea.clear();
                loginField.clear();
                passwordField.clear();
            });
            ChangeListener<String> changeListener = (observable, oldValue, newValue) ->
                    btnReg.setDisable(incompleteUserData());
            loginField.textProperty().addListener(changeListener);
            passwordField.textProperty().addListener(changeListener);
        });
    }

    @FXML public void register(/*ActionEvent actionEvent*/) {
        if (!controller.authorized) {
            String login = loginField.getText().trim();
            String password = passwordField.getText().trim();
            Platform.runLater(() -> {
                btnReg.setDisable(incompleteUserData());
                if (btnReg.isDisabled()) {
                    if (login.length() == 0) loginField.requestFocus();
                    else if (password.length() == 0) passwordField.requestFocus();
                } else
                    controller.authorize(login, password);
            });
        }
    }
}