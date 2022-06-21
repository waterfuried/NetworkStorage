import java.io.*;

import java.nio.file.*;

import java.time.*;

import java.util.*;

public class FileInfo {
    private String filename;
    private long size; //определяет также тип элемента: -1 - папка, иначе - файл
    private LocalDateTime modified;

    String getFilename() { return filename; }
    void setFilename(String filename) { this.filename = filename; }

    long getSize() { return size; }
    void setSize(long size) { this.size = size; }

    LocalDateTime getModified() { return modified; }
    long getModifiedAsLong() { return getModified().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    static LocalDateTime getModified(long time) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()); // or ZoneOffset.UTC
    }

    void setModified(LocalDateTime modified) { this.modified = modified; }
    void setModified(String modified) throws NumberFormatException {
        this.modified = getModified(Long.parseLong(modified));
    }

    FileInfo(Path path) {
        try {
            filename = path.getFileName().toString();
            size = Files.isDirectory(path) ? -1L : Files.size(path);
            modified = LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());
        } catch (IOException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    FileInfo(String filename, long size, LocalDateTime modified) {
        this.filename = filename;
        this.size = size;
        this.modified = modified;
    }

    // получить список элементов (папок и файлов) в папке
    static List<String> getItems(Path path) {
        String[] list = new File(path.toString()).list();
        if (list == null) list = new String[] { "" };
        return Arrays.asList(list);
    }

    static List<FileInfo> getItemsInfo(Path path) {
        String[] nameList = new File(path.toString()).list();
        List<FileInfo> list = new ArrayList<>();
        if (nameList != null)
            for (String s : nameList)
                list.add(new FileInfo(Paths.get(path.toString(), s)));
        return list;
    }

    // вычислить суммарный размер файлов в папке, включая ее подпапки
    static long getSizes(Path path) {
        long sum = 0;
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