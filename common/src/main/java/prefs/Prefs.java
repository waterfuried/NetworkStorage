package prefs;

import java.io.*;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
    public static final String COM_REGISTER = "reg";
    public static final String COM_QUIT ="quit";
    public static final String COM_EXIT ="exit";

    public static final String COM_GET_SPACE = "space";
    public static final String COM_GET_FILES = "files";

    public static final String COM_UPLOAD = "upload";
    public static final String COM_DOWNLOAD = "download";
    public static final String COM_REMOVE = "remove";
    //public static final String COM_RENAME = "rename";

    public static final String COM_OPTION_OVERWRITE = "overwrite";
//    public static final String COM_OPTION_SKIP = "skip";

    // команды терминала
    public static final String COM_TERM_CAT = "cat";
    public static final String COM_TERM_CD = "cd";
    public static final String COM_TERM_LIST = "ls";
    public static final String COM_TERM_HELP = "help";
    public static final String[] COM_TERM_USAGE = {
            "filename", // cat
            "folder_name" // cd
    };

    // ответы сервера на запросы
    public static final String SRV_ACCEPT = "NEST_DONE";
    public static final int SRV_SUCCESS = 0; // выполнено успешно

    public static final String SRV_REFUSE = "NEST_ERR";
    public static final String ERR_CANNOT_REMOVE = "Cannot remove";
    public static final int CONFIRM_OVERWRITE = 10_000;

    public enum ErrorCode {
        ERR_WRONG_AUTH,
        ERR_NO_SUCH_FILE,
        ERR_OUT_OF_SPACE,
        ERR_CANNOT_COMPLETE,
        ERR_WRONG_LIST,
        ERR_NOT_EMPTY,
        ERR_INTERNAL_ERROR,
        ERR_WRONG_REG,
        ERR_DB_OVERFLOW
    }
    public static final String[] errMessage = {
            "Authorization error",
            "No such file or folder",
            "Out of free space",
            "Cannot copy selected",
            "Failed to get list of entries",
            "Folder is not empty",
            "Internal server error;\ntry to repeat operation later",
            "Registration error",
            "Number of users already at maximum, please inform administrator"
    };

    // папка для имитации сетевого адреса сервера
    // System.getProperty("user.home") рекурсивное вычисление размера домашней папки (~14Гб, ~80K файлов)
    // вызывает переполнение стека
    public static final Path serverURL = Paths.get("temp").normalize().toAbsolutePath();

    // имя папки с журналами сервера
    public static final String logFolder = "log";

    // используемый шаблон времени/даты
    public static final DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // приглашение ввода в текстовом терминале
    public static final String terminalPrompt = ">> ";

    public static String getCommand(String cmdName, String ... args) {
        if (args == null || args.length == 0) return COM_ID + cmdName;

        StringBuilder sb = new StringBuilder(COM_ID + cmdName);
        for (String s : args)
            if (s.length() > 0) sb.append(" ").append(s);
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
        l.add(getCommand(COM_QUIT));
        l.add(getCommand(COM_EXIT));
        return l;
    }

    public static String getCmdHelp(int cmd_id) {
        return getCmdHelp(cmd_id, true);
    }

    public static String getCmdHelp(int cmd_id, boolean showUsage) {
        String s = "";
        if (cmd_id < COM_TERM_USAGE.length) {
            switch (cmd_id) {
                case 0: s = COM_TERM_CAT; break;
                case 1: s = COM_TERM_CD;
            }
            if (showUsage) s = "command usage: "+s;
            if (COM_TERM_USAGE[cmd_id] != null) s += " "+COM_TERM_USAGE[cmd_id];
        }
        return s;
    }

    public static String getHelp() {
        return "Available terminal commands are:" +
                "\n\r\t" + getCmdHelp(0, false) +
                "\n\r\t" + getCmdHelp(1, false) +
                "\n\r\t" + COM_TERM_LIST +
                "\n\r\t" + COM_TERM_HELP +
                "\n\r\t" + COM_QUIT +
                "\n\r\t" + COM_EXIT;
    }

    public static boolean isValidPath(Path path) {
        return !Prefs.serverURL.relativize(path).toString().startsWith("..");
    }

    public static boolean isRootPath(Path path) {
        return Prefs.serverURL.equals(path.normalize());
    }

    public static Path getRootPath() { return serverURL; }

    public static String capitalize(String s) {
        if (s == null || s.trim().length() == 0) return s;
        return s.substring(0, 1).toUpperCase()+s.substring(1);
    }

    // поскольку в текстовом протоколе аргументы команд разделяются пробелами,
    // а имена файлов и папок также могут содержать пробелы, последние необходимо
    // заменять и восстанавливать при передаче имен файлов в качестве аргументов
    // тех команд, где они используются, например, копирования и удаления
    public static String encodeSpaces(String s) {
        if (s == null || s.trim().length() == 0 || !s.contains(" ")) return s;
        return s.replace(" ", "\"");
    }
    public static String decodeSpaces(String s) {
        if (s == null || s.trim().length() == 0 || !s.contains("\"")) return s;
        return s.replace("\"", " ");
    }

    public static boolean isValidOption(String option) {
        return option.equalsIgnoreCase(COM_OPTION_OVERWRITE);
    }

    public static void doCopying(String src, String dst) {
        byte[] buf = new byte[BUF_SIZE];
        try (BufferedInputStream bis = new BufferedInputStream(
                new FileInputStream(src), BUF_SIZE);
             BufferedOutputStream bos = new BufferedOutputStream(
                     new FileOutputStream(dst), BUF_SIZE)) {
            int bytesRead;
            while ((bytesRead = bis.read(buf)) >= 0) bos.write(buf, 0, bytesRead);
        } catch (Exception ex) { ex.printStackTrace(); }
    }
}