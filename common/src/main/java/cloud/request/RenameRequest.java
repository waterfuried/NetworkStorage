package cloud.request;

import cloud.CloudMessage;

public class RenameRequest implements CloudMessage {
    private final String curName, newName;

    public RenameRequest(String currentName, String newName) {
        this.curName = currentName;
        this.newName = newName;
    }

    public String getCurName() { return curName; }
    public String getNewName() { return newName; }
}