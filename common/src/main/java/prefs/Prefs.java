package prefs;

import java.io.*;
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
    public static final String COM_GET_FS = "fs";
    public static final String COM_GET_SIZE = "size";
    // название пункта меню для команды определения размера файла/папки
    public static final String COM_GET_SIZE_TITLE = "Display size";

    public static final String COM_UPLOAD = "upload";
    public static final String COM_UPLOAD_DATA = "upld";
    public static final String COM_DOWNLOAD = "download";
    public static final String COM_REMOVE = "remove";
    public static final String COM_RENAME = "rename";
    public static final String COM_COPY = "copy";
    public static final String COM_MOVE = "move";

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
            "%s with same name already exists.\nOnly file can be replaced with a file",
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

    // тип ФС
    public static final int FS_EXTFS = 0;
    public static final int FS_NTFS = 1;
    public static final int FS_UNK = -1;

    /*
      методы обработки команд

      примечание:
        используемый в нескольких методах класс StringBuilder (появившийся с версии Java 1.5)
        является примером реализации паттерна проектирования Строитель (Builder)
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
     * @throws IOException при ошибках записи в существующий файл или создании нового
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
     * @param dst       имя копируемого файла/папки
     * @param size      размер копируемого файла/папки
     * @param modified  дата и время последнего изменения файла/папки
     * @throws IOException при ошибках создания файла/папки и изменения даты и времени модификации
     */
    public static void makeFolderOrZero(Path dst, long size, long modified) throws IOException {
        if (size < 0L) Files.createDirectory(dst); else resetFile(dst);
        if (!applyDateTime(dst.toString(), modified)) throw new IOException();
    }

    /**
     * переименование файла/папки: не только файлы, но и папки
     * могут быть переименованы с помощью метода Files.move -
     * независимо от наличия/отсутствия у них содержимого
     * @param curName         текущее имя файла/папки
     * @param newName         новое имя файла/папки
     * @return  код ошибки или -1, если ошибки нет
     */
    public static int rename(Path curName, String newName) {
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
                            createTemporaryName());
                    try { Files.move(curName, tmp); }
                    catch (Exception ex) { done = false; }
                } while (!done);
                p = tmp;
            }
            Files.move(p, p.resolveSibling(newName), StandardCopyOption.REPLACE_EXISTING);
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
        } catch (IOException ex) {
            errCode = ERR_CANNOT_COMPLETE.ordinal();
        }
        return errCode;
    }

    /**
     * копировать/переместить файл/папку локально
     * @param src    путь к исходному файлу
     * @param dst    путь к конечному файлу
     * @param move   признак перемещения файла
     * @throws IOException если при выполнении произошла ошибка
     */
    public static void copy(Path src, Path dst, boolean move) throws IOException {
        if (Files.exists(dst)) Files.delete(dst);
        long modified = Files.getLastModifiedTime(src).toMillis();
        if (move) Files.move(src, dst); else Files.copy(src, dst);
        applyDateTime(dst.toString(), modified);
    }

    // установить дату и время последней модификации
    public static boolean applyDateTime(String entry, long modified) {
        return new File(entry).setLastModified(modified);
    }

    // создать временное имя
    private static String createTemporaryName() { return "_tmp_" + System.nanoTime(); }

    /**
     * определить тип ФС
     * @param rootPath любой каталог ФС
     * @return -1, если ФС определить не удалось<br>
     * 0, если ФС типа extFS<br>
     * 1, если ФС типа FAT/NTFS
     */
    public static int getFSType(Path rootPath) {
        Path p1 = null, p2 = null;
        try {
            String name, upperName;
            do {
                name = createTemporaryName();
                p1 = rootPath.resolve(name);
            } while (Files.exists(p1));
            Files.write(p1, new byte[]{});
            upperName = name.toUpperCase();
            p2 = p1.resolveSibling(upperName);
            if (Files.exists(p2)) {
                boolean same = Files.isSameFile(p1, p2);
                Files.delete(p1);
                return same ? FS_NTFS : FS_EXTFS;
            }
            // создание нового файла вообще может не произойти и по другим причинам -
            // диск защищен от записи, диск полон, ошибка создания (сбойный сектор)
            Files.write(p2, new byte[]{}, StandardOpenOption.CREATE_NEW);
            Files.delete(p2);
            return FS_EXTFS;
        } catch (Exception ex) {
            try {
                if (p2 != null && Files.exists(p2) && !Files.isDirectory(p2)) Files.delete(p2);
                if (p1 != null && Files.exists(p1)) Files.delete(p1);
            } catch (IOException ex2) {}
            return (ex instanceof FileAlreadyExistsException) ? FS_NTFS : FS_UNK;
        }
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
    // его значение не превысит 2^20 (около 1 миллиона), учитывая ограничения
    // на длину строки и использование в ней букв латиницы, цифр и строчных знаков
    public static int getHash(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) h += s.charAt(i)*Math.pow(2,s.length()-i-1);
        return h;
    }

    // преобразовать строку к виду с заглавной буквы
    public static String capitalize(String s) {
        // с версии Java 11 в классе String введен метод isBlank(),
        // проверяющий строку на пустоту, с учетом присутствия пробельных (whitespace) символов
        if (s == null || s.trim().length() == 0) return s;
        return s.substring(0, 1).toUpperCase()+s.substring(1);
    }
}