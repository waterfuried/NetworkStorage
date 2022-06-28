package cloud.request;

import cloud.CloudMessage;

/**
 * запрос регистрации
 */
public class RegRequest implements CloudMessage {
    private final String login, password, email, username;

    public RegRequest(String login, String password, String email, String username) {
        this.login = login;
        this.password = password;
        this.email = email;
        this.username = username;
    }

    public String getLogin() { return login; }
    public String getPassword() { return password; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
}