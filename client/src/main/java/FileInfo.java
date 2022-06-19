import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileInfo {
    enum FileType {
        FOLDER(0), FILE(1);
        private final int type;
        int getType() { return type; }
        FileType(int type) { this.type = type; }
    }

    private String filename;
    private FileType type;
    private long size;
    private LocalDateTime modified;

    String getFilename() { return filename; }
    void setFilename(String filename) { this.filename = filename; }

    FileType getType() { return type; }
    void setType(FileType type) { this.type = type; }

    long getSize() { return size; }
    void setSize(long size) { this.size = size; }

    LocalDateTime getModified() { return modified; }
    void setModified(LocalDateTime modified) { this.modified = modified; }

    FileInfo(Path path) {
        try {
            filename = path.getFileName().toString();
            size = Files.size(path);
            type = Files.isDirectory(path) ? FileType.FOLDER : FileType.FILE;
            if (type == FileType.FOLDER) size = -1L;
            modified = LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneOffset.ofHours(3));
        }
        catch (IOException ex) {
            throw new RuntimeException(ex.getMessage());
        }
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