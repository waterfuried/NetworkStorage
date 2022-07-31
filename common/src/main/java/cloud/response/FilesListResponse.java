package cloud.response;

import cloud.CloudMessage;
import prefs.FileInfo;
import prefs.Prefs;

import java.io.IOException;

import java.nio.file.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ответ на запрос списка файлов и папок в папке пользователя на сервере
 * возвращает код ошибки, если указанный путь в папке не существует
 * TODO: по непонятной причине этот ответ не обрабатывается (его нет среди полученных ответов)
 *       в клиентском цикле чтения ответов сервера
 */
public class FilesListResponse implements CloudMessage {
    private List<FileInfo> entries;
    private final Path folder;
    private int entriesCount, errCode;

    public FilesListResponse(Path folder) {
        this.folder = folder;
        errCode = -1;
        try (Stream<Path> pathStream = Files.list(folder)) {
            entries = pathStream
                    .map(FileInfo::new)
                    .collect(Collectors.toList());
            entriesCount = entries.size();
            if (entriesCount == 1 && entries.get(0).getFilename().length() == 0) entriesCount = 0;
        } catch (IOException ex) {
            errCode = Prefs.ErrorCode.ERR_NO_SUCH_FILE.ordinal();
            ex.printStackTrace();
        }
        System.out.println("folder='"+folder+"' err="+errCode+" count="+entriesCount+" list="+entries);
    }

    public List<FileInfo> getEntries() { return entries; }
    public int getErrCode() { return errCode; }
    public int getEntriesCount() { return entriesCount; }
    public Path getFolder() { return folder; }
}