package cloud.response;

import cloud.CloudMessage;

import java.io.*;
import java.nio.file.*;

import static prefs.Prefs.*;
import static prefs.Prefs.ErrorCode.*;

public class UploadResponse implements CloudMessage {
    private int errCode, id;

    public UploadResponse(Path dst, long size, long modified) {
        errCode = -1;
        // создать новую папку или файл нулевого размера
        try {
            makeFolderOrZero(dst, size);
            // обновление даты и времени: если по какой-то причине оно не произошло,
            // операция в целом не выполнена
            if (!new File(dst.toString()).setLastModified(modified))
                errCode = ERR_CANNOT_COMPLETE.ordinal();
        } catch (Exception ex) {
            errCode = ERR_CANNOT_COMPLETE.ordinal();
        }
    }

    public UploadResponse(int id) { this.id = id; }
    public UploadResponse(ErrorCode errCode) { this.errCode = errCode.ordinal(); }

    public int getErrCode() { return errCode; }
    public void setErrCode(ErrorCode errCode) { this.errCode = errCode.ordinal(); }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
}