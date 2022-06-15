package prefs;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Prefs {
    public static final int PORT = 8189; // порт подключения
    //public static final int TIMEOUT = 120; // время на прохождение авторизации, секунды

    public static final long MAXSIZE = 1_000_000_000; // максимальный размер папки на сервере - 1 Гб
    public static final int BUF_SIZE = 1024; // размер буфера чтения/записи
    public static final int MIN_PWD_LEN = 4; // минимальная длина пароля

    // название проекта
    public static final String SHORT_TITLE = "NeSt";
    public static final String FULL_TITLE = SHORT_TITLE + ", Network Storage Application";

    // признак команды
    public static final String COM_ID = "/";

    // команды сервера
    public static final String COM_AUTHORIZE = "user";
    //public static final String COM_REGISTER = "reg";
    public static final String[] COM_QUIT = { "quit", "exit" };

    public static final String COM_GET_SPACE = "space";
    public static final String COM_GET_FILES = "files";

    public static final String COM_UPLOAD = "upload";
    public static final String COM_DOWNLOAD = "download";

    // ответы сервера на запросы
    public static final String SRV_ACCEPT = "NEST_DONE";
    public static final int SRV_SUCCESS = 0; // выполнено успешно
    public static final String SRV_REFUSE = "NEST_ERR";
    public static final int ERR_WRONG_AUTH = 0;
    public static final int ERR_NO_SUCH_FILE = 1;
    public static final int ERR_OUT_OF_SPACE = 2;
    public static final int ERR_CANNOT_COMPLETE = 3;
    public static final String[] errMessage = {
            "Authorization error",
            "No such file or folder",
            "Out of free space",
            "Cannot copy selected"
    };

    // папка для имитации сетевого адреса сервера
    // рекурсивное вычисление размера домашней папки (~14Гб, ~80K файлов) вызывает переполнение стека
    // особенно ее AppData
    public static final String serverURL = Paths.get("temp").normalize().toAbsolutePath().toString();//System.getProperty("user.home");

    public static String getCommand(String cmdName, String ... args) {
        if (args == null || args.length == 0) return COM_ID + cmdName;

        StringBuilder sb = new StringBuilder(COM_ID + cmdName);
        for (String s : args) sb.append(" ").append(s);
        return sb.toString();
    }

    public static String getCommand(String cmdName, int code) {
        return COM_ID + cmdName + " " + code;
    }

    public static boolean isExitCommand(String cmdName) {
        return getExitCommand().contains(cmdName.toLowerCase());
    }

    public static List<String> getExitCommand() {
        List<String> l = new ArrayList<>();
        for (String s : COM_QUIT) l.add(getCommand(s));
        return l;
    }
}