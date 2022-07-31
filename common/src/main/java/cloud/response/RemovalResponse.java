package cloud.response;

import cloud.CloudMessage;
import static prefs.Prefs.ErrorCode.*;

import java.io.IOException;
import java.nio.file.*;

public class RemovalResponse implements CloudMessage {
    private int errCode;

    public RemovalResponse(Path path) {
        errCode = -1;
        try {
            Files.delete(path);
        } catch (IOException ex) {
            errCode = ERR_NO_SUCH_FILE.ordinal();
            if (ex instanceof DirectoryNotEmptyException)
                errCode = ERR_NOT_EMPTY.ordinal();
        }
    }

    public int getErrCode() { return errCode; }
}