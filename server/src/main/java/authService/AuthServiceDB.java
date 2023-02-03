package authService;

import prefs.*;
import java.sql.*;
import static prefs.Prefs.*;

public class AuthServiceDB implements AuthService {
    public static final String JDBC = "jdbc";
    private static final String DB_USERS_TABLE = "users";

    private static Connection connection;
    private static Statement st;

    private final EventLogger logger;

    public AuthServiceDB(EventLogger logger) {
        this.logger = logger;
        try { connect(); }
        catch (Exception ex) { logger.logError(ex); }
    }

    private void connect() throws Exception {
        Class.forName("org.sqlite." + JDBC.toUpperCase());
        connection = DriverManager.getConnection(JDBC + ":sqlite:" + Prefs.SHORT_TITLE + ".db");
        st = connection.createStatement();
    }

    @Override public void close() {
        try {
            if (st != null) st.close();
            if (connection != null) connection.close();
        } catch (SQLException ex) { logger.logError(ex); }
    }

    private String adjustQuery(String query) { return String.format(query, DB_USERS_TABLE); }

    /**
     * проверка связи с БД - поиск таблицы пользователей
     * @return true, если связь с БД установлена, false - в противном случае
     */
    private boolean testDB() {
        try {
            if (connection == null || connection.isClosed()) return false;
            // вариант 1: через мета-данные, вместо последнего аргумента можно использовать null
            try (ResultSet rs = connection
                    .getMetaData()
                    .getTables(null, null, DB_USERS_TABLE, new String[] { "TABLE" })) {
                return rs.next() && rs.getString(3).equals(DB_USERS_TABLE);
                // вариант 2: через SQL-запрос
            /*try (ResultSet rs = st.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_USERS_TABLE + "';")) {
                return rs.next() && rs.getRow() > 0;*/
            }
        } catch (SQLException ex) {
            logger.logError(ex);
            return false;
        }
    }

    /**
     * проверить наличие пользователя в таблице БД по логину и паролю
     * @param login    - логин
     * @param pwdHash - хеш пароля
     * @param userdata - персональные данные пользователя (не обязательно)
     * @return
     *      имя пользователя, если он найден в БД
     *      пустая строка, если он не найден в БД
     *      null, если произошла ошибка обращения к БД
     */
    @Override public String getUsername(String login, int pwdHash, String ... userdata) {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("SELECT * FROM %s WHERE login = ? LIMIT 1;"))) {
            ps.setString(1, login);
            if (userdata != null && userdata.length > 0)
                for (int i = 0; i < userdata.length && userdata[i].length() > 0; i++)
                    ps.setString(i+3, userdata[i]);
            ResultSet rs = ps.executeQuery();
            if (!rs.next() || getHash(rs.getString("pwd")) != pwdHash) return null;
            return rs.getString("username");
        } catch (SQLException ex) { logger.logError(ex); }
        return null;
    }

    /**
     * получить имя и номер пользователя по его логину и паролю
     * @param login    - логин
     * @param pwdHash - хеш пароля
     * @param userdata - персональные данные пользователя (не обязательно)
     * @return
     *      имя пользователя и номер его папки, разделенные знаком табуляции, если пользователь в БД найден
     *      пустая строка, если произошла ошибка обращения к БД
     *      null, если пользователь в БД не найден
     */
    @Override public String getUserInfo(String login, int pwdHash, String ... userdata) {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("SELECT * FROM %s WHERE login = ? LIMIT 1;"))) {
            ps.setString(1, login);
            if (userdata != null && userdata.length > 0)
                for (int i = 0; i < userdata.length && userdata[i].length() > 0; i++)
                    ps.setString(i+3, userdata[i]);
            ResultSet rs = ps.executeQuery();
            if (!rs.next() || getHash(rs.getString("pwd")) != pwdHash) return null;
            return rs.getString("username")+"\t"+rs.getInt("usernum");
        } catch (SQLException ex) { logger.logError(ex); }
        return "";
    }

    // проверить наличие пользователя в таблице БД по имени пользователя
    @Override public boolean alreadyRegistered(String username) {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("SELECT username FROM %s WHERE username = ? LIMIT 1;"))) {
            ps.setString(1, username);
            return ps.executeQuery().next();
        } catch (SQLException ex) { logger.logError(ex); }
        return false;
    }

    // проверить наличие пользователя в таблице БД по уникальным полям:
    // если в таблице БД есть пользователь, у которого лишь часть УНИКАЛЬНЫХ полей
    // (например, логин и/или адрес почты) совпадают с указанными при регистрации,
    // такое совпадение есть попытка зарегистрировать уже зарегистрированного
    private boolean alreadyRegistered(String login, String userdata) {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("SELECT username FROM %s WHERE login = ? OR email = ? LIMIT 1;"))) {
            ps.setString(1, login);
            ps.setString(2, userdata);
            return ps.executeQuery().next();
        } catch (SQLException ex) { logger.logError(ex); }
        return false;
    }

    // изменить имя пользователя
    @Override public boolean updateData(String oldName, String newName) {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("UPDATE %s SET username = ? WHERE username = ?;"))) {
            ps.setString(1, newName);
            ps.setString(2, oldName);
            ps.executeUpdate();
            return true;
        } catch (SQLException ex) {
            logger.logError(ex);
            return false;
        }
    }

    /**
     * зарегистрировать нового пользователя
     * @param login    - логин
     * @param password - пароль
     * @param username - имя
     * @param userdata - персональные данные пользователя (не обязательно)
     * @return
     *      положительное целое, если пользоваетель зарегистрирован
     *      0, если пользователь УЖЕ зарегистрирован
     *      -1, если при регистрации произошла ошибка
     *      -2, если число пользователей в базе УЖЕ достигло предела
     */
    @Override public int registerUser(String login, String password, String username, String ... userdata) {
        if (userdata == null || userdata.length == 0 || alreadyRegistered(login, userdata[0]))
            return 0;
        else {
            int number = getFirstFreeNumber();
            if (number <= 0) return number == 0 ? -2 : number;
            try (PreparedStatement ps = connection.prepareStatement(
                    adjustQuery("INSERT INTO %s (login, pwd, email, username, usernum) VALUES (?, ?, ?, ?, ?);"))) {
                ps.setString(1, login);
                ps.setString(2, password);
                ps.setString(3, userdata[0]);
                ps.setString(4, username);
                ps.setInt(5, number);
                ps.executeUpdate();
                return number;
            } catch (SQLException ex) { logger.logError(ex); }
        }
        return -1;
    }

    /**
     * зарегистрировать нового пользователя: вариация с паттерном Строитель
     */
    @Override public int registerUser(AuthData data) {
        return registerUser(
                data.getLogin(),
                data.getPassword(),
                data.getUsername(),
                data.getUserData());
    }

    /**
     * найти первый свободный номер пользователя
     * @return
     *      положительное целое - найденный номер,
     *      0, если число пользователей в базе уже достигло предела,
     *      -1, если произошла ошибка работы с БД
     */
    private int getFirstFreeNumber() {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("SELECT usernum FROM %s;"))) {
            ResultSet rs = ps.executeQuery();
            if (!rs.isBeforeFirst()) return 1; // столбец пуст
            int i = 1;
            while (i < Integer.MAX_VALUE && rs.next() && rs.getInt(1) == i) i++;
            return i < Integer.MAX_VALUE ? i : 0;
        } catch (SQLException ex) { logger.logError(ex); }
        return -1;
    }

    @Override public boolean isServiceActive() { return testDB(); }
}