package authService;

import prefs.*;
import java.sql.*;

public class AuthServiceDB implements AuthService {
    public static final String JDBC = "jdbc";
    private static final String DB_USERS_TABLE = "users";

    private static Connection connection;
    private static Statement st;

    private final EventLogger logger = new EventLogger(AuthServiceDB.class.getName(), null);

    public AuthServiceDB() {
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

    // проверка связи с БД - поиск таблицы пользователей
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

    // проверить наличие пользователя в таблице БД по логину и паролю
    @Override public String getUsername(String login, String password, String ... userdata) {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("SELECT * FROM %s WHERE login = ? AND pwd = ? LIMIT 1;"))) {
            ps.setString(1, login);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getString("username");
        } catch (SQLException ex) { logger.logError(ex); }
        return null;
    }

    // получить имя и номер пользователя по его логину и паролю
    @Override public String getUserInfo(String login, String password, String ... userdata) {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("SELECT * FROM %s WHERE login = ? AND pwd = ? LIMIT 1;"))) {
            ps.setString(1, login);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getString("username")+"\t"+rs.getInt("usernum");
        } catch (SQLException ex) { logger.logError(ex); }
        return null;
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

    //добавить нового зарегистрированного пользователя
    @Override public int registerUser(String login, String password, String username, String ... userdata) {
        if (userdata == null || userdata.length == 0) return 0;
        if (getUsername(login, password) == null) {
            int number = getMaxNumber()+1;
            if (number == 0) return 0;
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
        return 0;
    }

    //найти максимальный номер пользователя
    //TODO: найти первый свободный номер пользователя
    public int getMaxNumber() {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("SELECT MAX(usernum) FROM %s;"))) {
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getInt(1);
        } catch (SQLException ex) { logger.logError(ex); }
        return -1;
    }

    @Override public boolean isServiceActive() { return testDB(); }
}