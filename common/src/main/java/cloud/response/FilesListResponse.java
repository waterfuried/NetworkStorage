package cloud.response;

import cloud.CloudMessage;
import prefs.*;
import static prefs.Prefs.*;

import java.io.*;

import java.nio.file.*;

import java.util.*;
import java.util.stream.*;

/**
 * ответ на запрос списка файлов и папок в папке пользователя на сервере
 * возвращает код ошибки, если указанный путь в папке не существует
 */
public class FilesListResponse implements CloudMessage {
    private List<FileInfo> entries;
    private final String folder;
    private int entriesCount, errCode;

    public FilesListResponse(Path path, String folder) {
        this.folder = folder;
        errCode = NO_ERROR;
        try (Stream<Path> pathStream = Files.list(path)) {
            entries = pathStream
                    .map(FileInfo::new)
                    .collect(Collectors.toList());
            entriesCount = entries.size();
            if (entriesCount == 1 && entries.get(0).getName().length() == 0) entriesCount = 0;
        } catch (IOException ex) {
            errCode = Prefs.ErrorCode.ERR_NO_SUCH_FILE.ordinal();
        }
    }

    public List<FileInfo> getEntries() { return entries; }
    public int getErrCode() { return errCode; }
    public int getEntriesCount() { return entriesCount; }
    public String getFolder() { return folder; }
}