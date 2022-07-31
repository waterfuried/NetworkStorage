package cloud.response;

import cloud.CloudMessage;

import java.nio.file.*;

import static prefs.Prefs.*;

public class RenameResponse implements CloudMessage {
    private final int errCode;

    public RenameResponse(Path curName, String newName, boolean replaceExisting) {
        errCode = rename(curName, newName, replaceExisting);
    }

    public int getErrCode() { return errCode; }
}