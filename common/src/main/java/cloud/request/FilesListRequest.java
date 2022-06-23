package cloud.request;

import cloud.CloudMessage;

/**
 * запрос списка файлов и папок в папке пользователя на сервере
 */
public class FilesListRequest implements CloudMessage {
    private final String path;

    public FilesListRequest(String path) { this.path = path; }

    public String getPath() { return path; }
}
