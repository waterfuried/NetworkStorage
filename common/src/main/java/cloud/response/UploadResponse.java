package cloud.response;

import cloud.CloudMessage;
import prefs.Prefs;

import java.io.*;
import java.nio.file.*;

public class UploadResponse implements CloudMessage {
    private int errCode, id;
    private boolean checkSpace, updateModified;
    private long oldSize;

    public UploadResponse(Path dst, long size, long modified) {
        int errCode = -1;
        oldSize = 0L;
        checkSpace = false;
        updateModified = false;
        if (Files.exists(dst)) {
            try {
                oldSize = Files.isDirectory(dst) ? -1L : Files.size(dst);
            } catch (Exception ex) { ex.printStackTrace(); }
            if (size > 0L)
                checkSpace = true;
            else {
                updateModified = oldSize <= 0L;
                if (oldSize > 0L)
                    if (Prefs.resetFile(dst))
                        updateModified = true;
                    else
                        errCode = Prefs.ErrorCode.ERR_CANNOT_COMPLETE.ordinal();
            }
        } else
            if (size <= 0L)
                if (size < 0L ? new File(dst.toString()).mkdir() : Prefs.resetFile(dst))
                    updateModified = true;
                else
                    errCode = Prefs.ErrorCode.ERR_CANNOT_COMPLETE.ordinal();
            else
                checkSpace = true;
        if (errCode < 0 && updateModified && modified > 0
                && !new File(dst.toString()).setLastModified(modified))
            // обновление даты и времени происходит только для папок и пустых файлов,
            // если по какой-то причине оно не произошло, операция в целом не выполнена
                errCode = Prefs.ErrorCode.ERR_CANNOT_COMPLETE.ordinal();
    }

    public UploadResponse(int id) { this.id = id; }

    public int getErrCode() { return errCode; }
    public void setErrCode(Prefs.ErrorCode errCode) { this.errCode = errCode.ordinal(); }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public boolean isUpdateModified() { return updateModified; }
    public boolean isCheckSpace() { return checkSpace; }
    public long getOldSize() { return oldSize; }
}