package cloud.response;

import cloud.CloudMessage;
import static prefs.Prefs.*;
import static prefs.Prefs.ErrorCode.*;

import java.io.File;
import java.nio.file.Path;

/**
 * ответ на запрос авторизации
 * возвращает:
 * имя пользователя, если он зарегистрирован
 * null - в противном случае
 */
public class AuthResponse implements CloudMessage {
    private final String username;
    private int errCode;

    public AuthResponse(String username, Path userFolder) {
        this.username = username;
        errCode = NO_ERROR;
        if (username != null) {
            if (username.length() > 0) {
                boolean b;
                try {
                    b = new File(userFolder.toString()).exists();
                    if (!b) b = new File(userFolder.toString()).mkdir();
                } catch (Exception ex) {
                    b = false;
                    ex.printStackTrace();
                }
                if (!b) errCode = ERR_INTERNAL_ERROR.ordinal();
            } else
                errCode = ERR_INTERNAL_ERROR.ordinal();
        } else
            errCode = ERR_WRONG_AUTH.ordinal();
    }

    public String getUsername() { return username; }
    public int getErrCode() { return errCode; }
}