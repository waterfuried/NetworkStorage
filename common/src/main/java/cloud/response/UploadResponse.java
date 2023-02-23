package cloud.response;

import cloud.CloudMessage;

import java.nio.file.*;

import static prefs.Prefs.*;
import static prefs.Prefs.ErrorCode.*;

public class UploadResponse implements CloudMessage {
    private int errCode, id;
    private final boolean started;

    public UploadResponse(Path dst, long size, long modified) {
        errCode = NO_ERROR;
        started = false;
        // создать новую папку или файл нулевого размера
        // обновление даты и времени: если по какой-то причине оно не произошло,
        // операция в целом не выполнена
        try { makeFolderOrZero(dst, size, modified); }
        catch (Exception ex) { errCode = ERR_CANNOT_COMPLETE.ordinal(); }
    }

    public UploadResponse(int id) { this.id = id; errCode = NO_ERROR; started = true; }

    public int getErrCode() { return errCode; }
    public void setErrCode(ErrorCode errCode) { this.errCode = errCode.ordinal(); }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public boolean started() { return started; }
}