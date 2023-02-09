package authorization;

// абстрактный класс пользователя
abstract public class User {
    // ссылка на интерфейс авторизации
    protected final Authorization authorization;
    public User(Authorization authorization) { this.authorization = authorization; }

    // абстрактный метод ее выполнения
    public abstract void authorize();
}