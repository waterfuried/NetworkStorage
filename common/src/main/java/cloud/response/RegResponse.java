package cloud.response;

import cloud.CloudMessage;
import static prefs.Prefs.*;
import static prefs.Prefs.ErrorCode.*;

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

    public RegResponse(String username, int userNumber, Path userFolder) {
        this.username = username;
        errCode = NO_ERROR;
        if (username != null) {
            boolean b;
            try {
                // при регистрации нового пользователя определенная для него папка не должна существовать
                b = !new File(userFolder.toString()).exists() && new File(userFolder.toString()).mkdir();
            } catch (Exception ex) {
                b = false;
                ex.printStackTrace();
            }
            if (!b) errCode = ERR_INTERNAL_ERROR.ordinal();
        } else
            errCode = userNumber == 0
                ? ERR_WRONG_REG.ordinal()
                : userNumber == -1
                    ? ERR_INTERNAL_ERROR.ordinal()
                    : ERR_DB_OVERFLOW.ordinal();
    }

    public String getUsername() { return username; }
    public int getErrCode() { return errCode; }
}