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

    String filename;
    FileType type;
    long size;
    LocalDateTime modified;

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public FileType getType() { return type; }
    public void setType(FileType type) { this.type = type; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public LocalDateTime getModified() { return modified; }
    public void setModified(LocalDateTime modified) { this.modified = modified; }

    public FileInfo(Path path) {
        try {
            filename = path.getFileName().toString();
            size = Files.size(path);
            type = Files.isDirectory(path) ? FileType.FOLDER : FileType.FILE;
            if (type == FileType.FOLDER) size = -1;
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
        for (String l : list) {
            Path fullPath = Paths.get(path.toString(), l);
            try {
                sum += Files.isDirectory(path) ? getSizes(fullPath) : Files.size(fullPath);
            } catch (IOException ex) { ex.printStackTrace(); }
        }
        return sum;
    }
}