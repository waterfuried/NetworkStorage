package cloud.request;

import cloud.CloudMessage;

/**
 * запрос авторизации
 */
public class AuthRequest implements CloudMessage {
    private final String login, password;

    public AuthRequest(String login, String password) {
        this.login = login;
        this.password = password;
    }

    public String getLogin() { return login; }
    public String getPassword() { return password; }
}
