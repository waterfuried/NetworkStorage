package cloud.response;

import cloud.CloudMessage;
import prefs.Prefs;

import java.io.IOException;
import java.nio.file.*;

public class RemovalResponse implements CloudMessage {
    private int errCode;

    public RemovalResponse(String path) {
        errCode = 0;
        try {
            Files.delete(Prefs.serverURL.resolve(path));
        } catch (IOException ex) {
            errCode = Prefs.ERR_NO_SUCH_FILE;
            if (ex instanceof DirectoryNotEmptyException)
                errCode = Prefs.ERR_NOT_EMPTY;
        }
    }

    public int getErrCode() { return errCode; }
}
