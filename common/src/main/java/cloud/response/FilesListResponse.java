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
 *       в клиентском цикле чтения ответов сервера, если список файлов отправлять не строкой,
 *       а именно списком из элементов FileInfo
 */
public class FilesListResponse implements CloudMessage {
    private String/*List<FileInfo>*/ entries;
    private final String folder;
    private int entriesCount, errCode;

    public FilesListResponse(String folder){
        Path path = Prefs.serverURL;
        this.folder = folder;
        errCode = 0;
        if (folder.length() > 0)
            try { path = path.resolve(folder); }
            catch (InvalidPathException ex) {
                errCode = Prefs.ERR_NO_SUCH_FILE;
            }
        if (errCode == 0) {
            try (Stream<Path> pathStream = Files.list(path)) {
                entries = pathStream
                        .map(FileInfo::new)
                        //при составлении списка из FileInfo эта строка, естественно, убирается
                        .map(fi -> fi.getFilename() + ":" + fi.getSize() + ":" + fi.getModifiedAsLong())
                        .collect(Collectors.joining("\n"));
                        //.collect(Collectors.toList());
                entriesCount = entries.split("\n").length;
                //entriesCount = entries.size();
                if (entriesCount == 1 && entries/*.get(0).getFilename()*/.length() == 0) entriesCount = 0;
            } catch (IOException ex) {
                errCode = Prefs.ERR_NO_SUCH_FILE;
                ex.printStackTrace();
            }
        }
        System.out.println("folder='"+folder+"' err="+errCode+" count="+entriesCount+" list="+entries);
    }

    public /*List<FileInfo>*/String getEntries() { return entries; }
    public int getErrCode() { return errCode; }
    public int getEntriesCount() { return entriesCount; }
    public String getFolder() { return folder; }
}