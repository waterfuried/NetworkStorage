import prefs.Prefs;

import javafx.application.Platform;
import javafx.scene.control.*;

public class Messages {
    static void displayMessage(Alert.AlertType msgType, String msg, String title) {
        if (title.trim().length() != 0) {
            Alert alert = new Alert(msgType);
            alert.setTitle(title);
            alert.setHeaderText(msg);
            alert.setContentText(msg);
            alert.showAndWait();
        } else
            new Alert(msgType, msg).showAndWait();
    }

    static boolean getConfirmation(Alert.AlertType eventType, String msg, String title) {
        //ButtonType btnYes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
        //ButtonType btnNo = new ButtonType("No", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(eventType, msg, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(title);
        //alert.setContentText(msg);
        return alert.showAndWait().get() == ButtonType.YES;
    }

    static void displayInfo(String msg, String title) {
        displayMessage(Alert.AlertType.INFORMATION, msg, title);
    }

    static void displayError(String errMsg, String title) {
        displayMessage(Alert.AlertType.ERROR, errMsg, title);
    }

    static void displayError(Prefs.ErrorCode errCode, String title) {
        displayMessage(Alert.AlertType.ERROR, Prefs.errMessage[errCode.ordinal()], title);
    }

    static void displayErrorFX(String errMsg, String title) {
        Platform.runLater(() -> displayError(errMsg, title));
    }

    static void displayErrorFX(int errCode, String title) {
        Platform.runLater(() -> displayError(Prefs.errMessage[errCode], title));
    }

    static void displayErrorFX(Prefs.ErrorCode errCode, String title) {
        Platform.runLater(() -> displayError(Prefs.errMessage[errCode.ordinal()], title));
    }
}