package cloud;

import java.io.*;

import java.nio.file.*;

// отправка файла от клиента на сервер (upload)
public class FileMessage implements CloudMessage {
    private final long size;
    private final byte[] data;
    private final String name;

    public FileMessage(Path path) throws IOException {
        size = Files.size(path);
        data = Files.readAllBytes(path);
        name = path.getFileName().toString();
    }

    public long getSize() { return size; }
    public byte[] getData() { return data; }
    public String getName() { return name; }
}