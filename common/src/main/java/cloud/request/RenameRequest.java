package cloud.request;

import cloud.CloudMessage;

public class RenameRequest implements CloudMessage {
    private final String curName, newName;
    private final boolean replaceExisting;

    public RenameRequest(String currentName, String newName, boolean replaceExisting) {
        this.curName = currentName;
        this.newName = newName;
        this.replaceExisting = replaceExisting;
    }

    public String getCurName() { return curName; }
    public String getNewName() { return newName; }
    public boolean shouldBeReplaced() { return replaceExisting; }
}