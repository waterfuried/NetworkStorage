package cloud.response;

import cloud.CloudMessage;
import prefs.Prefs;

import java.io.File;
import java.nio.file.Path;

/**
 * ответ на запрос регистрации
 * возвращает:
 * имя пользователя, если он зарегистрирован
 * null - в противном случае
 */
public class RegResponse implements CloudMessage {
    private final String username;
    private int errCode;

    public RegResponse(String username, Path userFolder) {
        this.username = username;
        errCode = -1;
        if (username != null) {
            boolean b;
            try {
                b = new File(userFolder.toString()).exists();
                if (!b) b = new File(userFolder.toString()).mkdir();
            } catch (Exception ex) {
                b = false;
                ex.printStackTrace();
            }
            if (!b) errCode = Prefs.ErrorCode.ERR_INTERNAL_ERROR.ordinal();
        }
        else
            errCode = Prefs.ErrorCode.ERR_WRONG_REG.ordinal();
    }

    public String getUsername() { return username; }
    public int getErrCode() { return errCode; }
}