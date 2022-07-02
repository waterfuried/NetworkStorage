package cloud.response;

import cloud.CloudMessage;
import prefs.*;

import java.io.*;
import java.nio.file.*;

public class UploadResponse implements CloudMessage {
    private int errCode;
    private long oldSize;
    private final boolean isFile;
    private final String entry;

    // /upload source_name source_path destination_path size [date]; "." for root destination
    public UploadResponse(String srcPath, String dstPath, long size, long modified, boolean mustOverwrite,
                          long freeSpace, Path userFolder) {
        byte[] buf = new byte[Prefs.BUF_SIZE];
        errCode = -1;
        String src = Paths.get(srcPath).toString();
        entry = srcPath.substring(srcPath.lastIndexOf(File.separatorChar)+1);
        Path dst = userFolder.resolve(dstPath).resolve(entry);
        // перед выполнением проверять наличие файла и достаточного свободного места
        oldSize = 0L; // добавление нового файла/папки
        boolean exists = Files.exists(dst);
        isFile = exists ? !Files.isDirectory(dst) : size >= 0;
        if (!exists || mustOverwrite) {
            if (exists)
                oldSize = new File(dst.toString()).length(); // замена существующего файла/папки
            if (size >= 0 && size >= oldSize && freeSpace-(size-oldSize) <= 0)
                errCode = Prefs.ErrorCode.ERR_OUT_OF_SPACE.ordinal();
            else {
                boolean success = (isFile && size >= 0) || (!isFile && size < 0);
                if (success)
                    if (size >= 0) { // файл
                        // NB: "заливка" файла на сервер будет происходить посылкой через сеть блоков
                        // по 1К для, например, файла размером 500М - полмиллиона мелких пакетов
                        Prefs.doCopying(src, dst.toString());
                        success = new File(dst.toString()).length() == size;
                    } else // папка
                        success = new File(dst.toString()).mkdir();
                if (success && modified > 0)
                    // установить дату и время последней модификации как у оригинала
                    try {
                        FileInfo fi = new FileInfo(dst);
                        fi.setModified(modified);
                        // если по какой-то причине дату/время применить не удалось,
                        // считать это неудачей всей операции в целом не стоит
                        new File(dst.toString()).setLastModified(fi.getModifiedAsLong());
                    } catch (NumberFormatException ex) { ex.printStackTrace(); }
                errCode = success ? -1 : Prefs.ErrorCode.ERR_CANNOT_COMPLETE.ordinal();
            }
        }
        if (exists && !mustOverwrite) errCode = Prefs.CONFIRM_OVERWRITE;
    }

    public int getErrCode() { return errCode; }
    public long getOldSize() { return oldSize; }
    public boolean isFile() { return isFile; }
    public String getEntry() { return entry; }
}