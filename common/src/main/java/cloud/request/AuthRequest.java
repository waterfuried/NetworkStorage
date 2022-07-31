package cloud.request;

import cloud.CloudMessage;

/**
 * запрос авторизации
 */
public class AuthRequest implements CloudMessage {
    private final String login;
    private final int pwdHash;

    public AuthRequest(String login, int pwdHash) {
        this.login = login;
        this.pwdHash = pwdHash;
    }

    public String getLogin() { return login; }
    public int getPasswordHash() { return pwdHash; }
}