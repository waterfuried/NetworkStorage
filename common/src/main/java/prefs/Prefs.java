package prefs;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static prefs.Prefs.ErrorCode.*;

public class Prefs {
    public static final int PORT = 8189; // порт подключения
    //public static final int TIMEOUT = 120; // время на прохождение авторизации, секунды

    public static final long MAXSIZE = 1_000_000_000; // максимальный размер папки на сервере - 1 Гб
    public static final int BUF_SIZE = 10*1024; // размер буфера чтения/записи
    public static final int MIN_PWD_LEN = 4, MAX_PWD_LEN = 12; // минимальная и максимальная длина пароля
    public static final int VIEWABLE_SIZE = 256; // !!! отладочное: макс. длина отображаемого ответа сервера

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
    public static final String COM_UPLOAD_DATA = "upld";
    public static final String COM_DOWNLOAD = "download";
    public static final String COM_REMOVE = "remove";
    public static final String COM_RENAME = "rename";

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
    public static final String ERR_OPERATION_FAILED = "Operation failed";

    public enum ErrorCode {
        ERR_WRONG_AUTH,
        ERR_NO_SUCH_FILE,
        ERR_OUT_OF_SPACE,
        ERR_CANNOT_COMPLETE,
        ERR_WRONG_LIST, // может произойти только из-за ошибок передачи от сервера клиенту
        ERR_NOT_EMPTY,
        ERR_INTERNAL_ERROR,
        ERR_WRONG_REG,
        ERR_DB_OVERFLOW,
        ERR_WRONG_REPLACEMENT,
        ERR_INVALID_NAME,
        ERR_FOLDER_ACCESS_DENIED,
        ERR_REPLACEMENT_NOT_ALLOWED
    }
    public static final String[] errMessage = {
            "Authorization error",
            "No such file or folder",
            "Out of free space",
            "Cannot %s selected item",
            "Failed to get list of entries",
            "Folder is not empty",
            "Internal server error;\ntry to repeat operation later",
            "Registration error",
            "Number of users already at maximum, please inform administrator",
            "It is not possible to replace a file with a folder",
            "There are invalid characters in the name",
            "Folder access denied",
            "Rename operation does not support moving"
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

    /*
      методы обработки команд
    */
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

    public static String getAllExitCommands(String separator) {
        StringBuilder sb = new StringBuilder();
        List<String> s = getExitCommand();
        for (int i = 0; i < s.size(); i++) {
            sb.append(s.get(i));
            if (i < s.size()-1) sb.append(separator);
        }
        return sb.toString();
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

    /*
     методы обработки путей к файлам/папкам
    */
    public static boolean isValidPath(Path path) {
        return !serverURL.relativize(path).toString().startsWith("..");
    }

    public static boolean isRootPath(Path path, boolean atServer) {
        return atServer ? serverURL.equals(path.normalize()) : path.getParent() == null;
    }

    public static Path getRootPath() { return serverURL; }

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

    // проверить наличие недопустимых символов в имени файла/папки
    public static boolean hasInvalidCharacters(String s) {
        // \f - form feed, \b - backspace, \0 - ascii 0, \s - any whitespace
        return s.matches(".*[\\\\\n\r\t\0\f\b\"/?*<>|:].*");
    }

    /*
      методы обработки файлов/папок
    */
    /**
     * обнулить файл, если он существует, иначе создать новый
     * @param dst имя файла
     * @throws IOException
     */
    public static void resetFile(Path dst) throws IOException {
        Files.write(dst, new byte[]{});
    }

    /**
     * удалить существующий файл
     * @param dst имя файла
     */
    public static void removeFile(Path dst) {
        try { Files.delete(dst); } catch (IOException ex) {}
    }

    // с точки зрения ФС проверить совпадение нового/исходного имени с текущим/целевым
    public static boolean similarNames(Path dstName, String srcName) throws IOException {
        return dstName.toString().equalsIgnoreCase(dstName.resolveSibling(srcName).toString()) &&
                Files.isSameFile(dstName, dstName.resolveSibling(srcName));
    }

    /**
     * копирование (создание) файла нулевого размера или папки
     * @param dst  имя копируемого файла/папки
     * @param size размер копируемого файла/папки
     * @throws IOException
     */
    public static void makeFolderOrZero(Path dst, long size) throws IOException {
        if (size < 0L) Files.createDirectory(dst); else resetFile(dst);
    }

    /**
     * переименование файла/папки: не только файлы, но и папки
     * могут быть переименованы с помощью метода Files.move -
     * независимо от наличия/отсутствия у них содержимого
     * @param curName         текущее имя файла/папки
     * @param newName         новое имя файла/папки
     * @param replaceExisting замещать существующий файл при совпадении текущего и нового имен
     * @return  код ошибки или -1, если ошибки нет
     */
    public static int rename(Path curName, String newName, boolean replaceExisting) {
        int errCode = -1;
        Path p = curName;
        try {
            // если с точки зрения ФС новое имя совпадает с текущим,
            // временно переименовать в нечто промежуточное
            if (similarNames(curName, newName)) {
                Path tmp;
                boolean done = true;
                do {
                    tmp = Paths.get(curName.toString()
                                    .substring(0, curName.toString().lastIndexOf(File.separatorChar)),
                            "_tmp_" + System.nanoTime());
                    try { Files.move(curName, tmp); }
                    catch (Exception ex) { done = false; }
                } while (!done);
                p = tmp;
            }
            if (replaceExisting)
                Files.move(p, p.resolveSibling(newName), StandardCopyOption.REPLACE_EXISTING);
            else
                Files.move(p, p.resolveSibling(newName));
        /*
          1. поскольку пользователь может использовать файловый менеджер
             и в нем находиться внутри папки, имя которой он пытается
             изменить здесь, помимо исключения "уже существует"
             может вылезти исключение попытки совместного доступа
          2. поскольку исключение вылезло при изменении имени на то, которое в списке
             отсутствует, ФС диска - FAT/NTFS и можно искать его в списке, игнорируя регистр;
             NB: одно и то же исключение вылезет при попытках переименовать файл в файл/папку,
             и папку в папку/файл
        */
        } catch (FileAlreadyExistsException ex) {
            errCode = ERR_NO_SUCH_FILE.ordinal();
        } catch (IOException ex) {
            errCode = ERR_CANNOT_COMPLETE.ordinal();
        }
        return errCode;
    }

    /*
     методы кодирования информации
    */
    // простое кодирование сложением по модулю 2
    public static String encode(String s, boolean direct) {
        if (s == null || s.length() == 0) return s;
        StringBuilder sb = new StringBuilder();
        int c;
        if (direct) {
            c = s.charAt(0)^s.charAt(1);
            for (int i = 0; i < s.length()-1; i++)
                sb.append(Character.toString(s.charAt(i) ^ s.charAt(i+1)));
            sb.append(Character.toString(s.charAt(s.length()-1) ^ c));
        } else {
            sb.append(Character.toString(c = s.charAt(s.length()-1) ^ s.charAt(0)));
            for (int i = s.length()-2; i >= 0; i--)
                sb.append(Character.toString(c = s.charAt(i) ^ c));
            sb.reverse();
        }
        return sb.toString();
    }

    // простой хеш - сумма произведений кода символа и степени 2,
    // его значение не превысит 2^20 = 1048576, учитывая ограничения
    // на длину строки и использование в ней букв латиницы, цифр и строчных знаков
    public static int getHash(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) h += s.charAt(i)*Math.pow(2,s.length()-i-1);
        return h;
    }
}