package cloud.request;

import cloud.CloudMessage;

public class LogoutRequest implements CloudMessage {
    private final String login;

    public LogoutRequest(String login) { this.login = login; }

    public String getLogin() { return login; }
}
