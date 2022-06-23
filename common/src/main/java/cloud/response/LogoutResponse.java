package cloud.response;

import cloud.CloudMessage;

public class LogoutResponse implements CloudMessage {
    private final String login;

    public LogoutResponse(String login) { this.login = login; }

    public String getLogin() { return login; }
}
