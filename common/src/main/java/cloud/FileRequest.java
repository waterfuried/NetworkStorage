package cloud;

// отправка файла с сервера клиенту (download)
public class FileRequest implements CloudMessage {
    private final String name;

    public FileRequest(String name) { this.name = name; }

    public String getName() { return name; }
}