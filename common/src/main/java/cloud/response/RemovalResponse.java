package cloud.response;

import cloud.CloudMessage;
import prefs.Prefs;

import java.io.IOException;
import java.nio.file.*;

public class RemovalResponse implements CloudMessage {
    private int errCode;

    public RemovalResponse(String path, Path userFolder) {
        errCode = -1;
        try {
            Files.delete(userFolder.resolve(path));
        } catch (IOException ex) {
            errCode = Prefs.ErrorCode.ERR_NO_SUCH_FILE.ordinal();
            if (ex instanceof DirectoryNotEmptyException)
                errCode = Prefs.ErrorCode.ERR_NOT_EMPTY.ordinal();
        }
    }

    public int getErrCode() { return errCode; }
}
