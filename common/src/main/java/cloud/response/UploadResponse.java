package cloud.response;

import cloud.CloudMessage;

public class UploadResponse implements CloudMessage {
    private final int errCode;
    private final int id;

    public UploadResponse(int id, int errCode) {
        this.id = id;
        this.errCode = errCode;
    }

    public int getErrCode() { return errCode; }
    public int getId() { return id; }
}