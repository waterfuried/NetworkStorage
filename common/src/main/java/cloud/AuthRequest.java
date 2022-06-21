package cloud;

public class AuthRequest implements CloudMessage {
    private final String user, password;

    public AuthRequest(String user, String password) {
        this.user = user;
        this.password = password;
    }

    public String getUser() { return user; }
    public String getPassword() { return password; }
}
