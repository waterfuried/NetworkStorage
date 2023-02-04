package authService;

// реализация шаблона проектирования Строитель (Builder)
// практически целесообразна только для классов с очень большим числом полей
public class AuthData {
    private final String login;
    private final String password;
    private final String username;
    private final String[] userData;

    public AuthData(AuthBuilder builder) {
        this.login = builder.login;
        this.password = builder.password;
        this.username = builder.username;
        this.userData = builder.userData;
    }

    public String getLogin() { return login; }
    public String getPassword() { return password; }
    public String getUsername() { return username; }
    public String[] getUserData() { return userData; }

    public static class AuthBuilder {
        private String login;
        private String password;
        private String username;
        private String[] userData;

        public AuthBuilder() {}
        public AuthBuilder login(String login) { this.login = login; return this; }
        public AuthBuilder password(String password) { this.password = password; return this; }
        public AuthBuilder username(String username) { this.username = username; return this; }
        public AuthBuilder userData(String[] userData) { this.userData = userData; return this; }

        public AuthData build() { return new AuthData(this); }
    }
}