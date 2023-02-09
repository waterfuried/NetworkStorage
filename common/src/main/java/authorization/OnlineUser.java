package authorization;

public class OnlineUser extends User {
    private final String login;
    private final String password;

    public OnlineUser(Authorization authorization, String login, String password) {
        super(authorization);
        this.login = login;
        this.password = password;
    }

    public String getLogin() { return login; }
    public String getPassword() { return password; }

    @Override
    public void authorize() { authorization.authorize(login, password); }
}