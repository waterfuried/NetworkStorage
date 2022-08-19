package prefs;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public class FileInfo implements Serializable {
    private String filename;
    private final long size; //определяет также тип элемента: -1 - папка, иначе - файл
    private final LocalDateTime modified;

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public long getSize() { return size; }

    public LocalDateTime getModified() { return modified; }
    public long getModifiedAsLong() { return getModified().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    public static LocalDateTime getModified(long time) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()); // or ZoneOffset.UTC
    }

    public FileInfo(Path path) {
        try {
            filename = path.getFileName().toString();
            size = Files.isDirectory(path) ? -1L : Files.size(path);
            modified = LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());
        } catch (IOException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    public FileInfo(String filename, long size, LocalDateTime modified) {
        this.filename = filename;
        this.size = size;
        this.modified = modified;
    }

    // получить список элементов (папок и файлов) в папке
    public static List<String> getItems(Path path) {
        String[] list = new File(path.toString()).list();
        if (list == null) list = new String[] { "" };
        return Arrays.asList(list);
    }

    public static List<FileInfo> getItemsInfo(Path path) {
        String[] nameList = new File(path.toString()).list();
        List<FileInfo> list = new ArrayList<>();
        if (nameList != null)
            for (String s : nameList)
                list.add(new FileInfo(Paths.get(path.toString(), s)));
        return list;
    }

    // вычислить суммарный размер файлов в папке, включая ее подпапки
    public static long getSizes(Path path) {
        if (!Files.exists(path)) return 0L;
        long sum = 0L;
        List<String> list = getItems(path);
        // для прохода по пути с получением всех его элементов
        // можно использовать метод Files.walk
        for (String l : list) {
            Path fullPath = Paths.get(path.toString(), l);
            try {
                sum += Files.isDirectory(path) ? getSizes(fullPath) : Files.size(fullPath);
            } catch (IOException ex) { ex.printStackTrace(); }
        }
        return sum;
    }
}