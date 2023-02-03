package authService;

public interface AuthService {
    /**
     * получить имя (или ник) пользователя по логину и паролю
     * @return null если учетная запись не найдена, имя (или ник) - в противном случае
     **/
    String getUsername (String login, int pwdHash, String ... userdata);

    /**
     * получить имя (или ник) и другие данные пользователя по логину и паролю
     * @return null если учетная запись не найдена, данные пользователя - в противном случае
     **/
    String getUserInfo (String login, int pwdHash, String ... userdata);

    /**
     * выполнить регистрацию учетной записи
     * @return положительное число при успешной регистрации,
     * 0 - в противном случае (если логин/никнейм заняты)
     **/
    int registerUser(String login, String password, String username, String ... userdata);
    // регистрация в контракте интерфейса необходима из-за того, что
    // сервер (обработчик запросов клиента) использует ссылку на него
    int registerUser(AuthData data);

    /**
     * обновить данные пользователя - например, изменить имя (ник)
     * @return true при успешном обновлении, false - в противном случае
     **/
    boolean updateData(String oldVal, String newVal);

    /**
     * проверить наличие зарегистрированного пользователя с определенным именем (ником)
     * @return true если пользователь уже зарегистрирован, false - в противном случае
     **/
    boolean alreadyRegistered(String username);

    /**
     * проверить запуск сервиса
     * @return true при успешном запуске, false - в противном случае
     **/
    boolean isServiceActive();

    /**
     * завершить работу сервиса
     **/
    default void close() {}
}