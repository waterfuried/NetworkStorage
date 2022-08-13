package cloud.response;

import cloud.CloudMessage;
import static prefs.Prefs.ErrorCode;

public class CopyResponse implements CloudMessage {
    private int errCode;
    private String name;
    private boolean moved;

    public CopyResponse(String name, boolean moved) {
        this.name = name;
        this.moved = moved;
        errCode = -1;
    }

    public CopyResponse(ErrorCode errCode) { this.errCode = errCode.ordinal(); }

    public int getErrCode() { return errCode; }
    public void setErrCode(ErrorCode errCode) { this.errCode = errCode.ordinal(); }
    public String getName() { return name; }
    public boolean moved() { return moved; }
}