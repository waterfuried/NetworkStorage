package cloud.response;

import cloud.CloudMessage;

/**
 * ответ сервера на запрос авторизации
 * возвращает:
 * ник пользователя, если он зарегистрирован
 *      пока нет поиска в БД,
 *      вместо ника возвращается логин из запроса авторизации
 * null - в противном случае
 */
public class AuthResponse implements CloudMessage {
    private final String nickname;

    public AuthResponse(String nickname) {
        this.nickname = nickname;
    }

    public String getNickname() { return nickname; }
}
