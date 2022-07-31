import static prefs.Prefs.*;

import javafx.scene.control.*;

public class Messages {
    static void displayMessage(Alert.AlertType msgType, String msg, String title) {
        if (title.trim().length() != 0) {
            Alert alert = new Alert(msgType);
            alert.setTitle(title);
            alert.setHeaderText(msg);
            alert.setContentText("");
            alert.showAndWait();
        } else
            new Alert(msgType, msg).showAndWait();
    }

    static boolean getConfirmation(Alert.AlertType eventType, String msg, String title, ButtonType button) {
        //ButtonType btnYes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
        //ButtonType btnNo = new ButtonType("No", ButtonBar.ButtonData.NO);
        ButtonType[] buttons = null;
        if (button == ButtonType.YES)
            buttons = new ButtonType[]{ ButtonType.YES, ButtonType.NO };
        if (button == ButtonType.OK)
            buttons = new ButtonType[]{ ButtonType.OK, ButtonType.CANCEL };
        Alert alert = new Alert(eventType, msg, buttons);
        alert.setTitle(title);
        alert.setHeaderText(msg);
        alert.setContentText("");
        return alert.showAndWait().get() == button;
    }

    static void displayError(ErrorCode errCode, String title) {
        displayMessage(Alert.AlertType.ERROR, errMessage[errCode.ordinal()], title);
    }

    static void displayError(ErrorCode errCode, String title, String param) {
        displayMessage(Alert.AlertType.ERROR, String.format(errMessage[errCode.ordinal()], param), title);
    }

    static void displayError(ErrorCode errCode, String title, String ... param) {
        if (param != null)
            displayMessage(Alert.AlertType.ERROR,
                String.format(errMessage[errCode.ordinal()]+" - "+param[0], param[1]), title);
    }

    static String getInputValue(String title, String msg, String prompt, String value) {
        TextInputDialog d = new TextInputDialog(value);
        if (title.trim().length() != 0) {
            d.setTitle(title);
            d.setHeaderText(msg);
            d.setContentText(prompt);
        }
        return d.showAndWait().orElse(null);
    }

    // подтвердить замену существующего файла
    static boolean confirmReplacement(boolean isFile, String name) {
        String entry = isFile ? "File" : "Folder";
        return getConfirmation(Alert.AlertType.WARNING,
                entry + " '" + name + "' already exists at destination.\n" +
                        "Would you like to replace it?",
                entry + " already exists", ButtonType.YES);
    }

    // подтвердить удаление
    // - файла/пустой папки (защита от случайных нажатий)
    // - неполностью скопированного файла
    static boolean getRemovalConfirmation(boolean isFile, String name, boolean direct) {
        return getConfirmation(direct ? Alert.AlertType.WARNING : Alert.AlertType.ERROR,
                direct
                    ? "Please confirm removal of "+(isFile ? "file" : "folder")+" '"+name+"'"
                    : "An error occurred while downloading the file '"+name+"' -\n"+
                      "it was not completely downloaded. Remove this incomplete file?",
                "Removal confirmation", ButtonType.OK);
    }

    static boolean confirmRetryCopying() {
        return Messages.getConfirmation(Alert.AlertType.ERROR,
                "Copy error has been occurred.\nWould you like to retry?",
                "Copy error", ButtonType.YES);
    }
}