package cloud.response;

import cloud.CloudMessage;
import prefs.*;

import java.io.*;
import java.nio.file.*;

public class UploadResponse implements CloudMessage {
    private int errCode;

    // /upload source_name source_path destination_path size [date]; "." for root destination
    public UploadResponse(String srcPath, String dstPath, long size, long modified, long freeSpace, Path userFolder) {
        byte[] buf = new byte[Prefs.BUF_SIZE];
        errCode = -1;
        // перед выполнением проверять наличие достаточного свободного места
        if (size >=0 && freeSpace-size <= 0)
            errCode = Prefs.ErrorCode.ERR_OUT_OF_SPACE.ordinal();
        else {
            boolean success;
            String src = Paths.get(srcPath).toString(),
                   dst = userFolder
                         .resolve(dstPath)
                         .resolve(srcPath.substring(srcPath.lastIndexOf(File.separatorChar)+1)).toString();
            if (size >= 0) {
                // файл
                // NB: "заливка" файла на сервер будет происходить посылкой через сеть блоков
                // по 1К для, например, файла размером 500М - полмиллиона мелких пакетов
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src), Prefs.BUF_SIZE);
                     BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dst), Prefs.BUF_SIZE)) {
                    int bytesRead;
                    while ((bytesRead = bis.read(buf)) >= 0) bos.write(buf, 0, bytesRead);
                } catch (Exception ex) { ex.printStackTrace(); }
                success = new File(dst).length() == size;
            } else
                // папка
                success = new File(dst).mkdir();
            // установить дату и время последней модификации как у оригинала
            if (success && modified > 0)
                try {
                    FileInfo fi = new FileInfo(Paths.get(dst));
                    fi.setModified(modified);
                    // если по какой-то причине дату/время применить не удалось,
                    // считать это неудачей всей операции в целом не стоит
                    new File(dst).setLastModified(fi.getModifiedAsLong());
                } catch (NumberFormatException ex) { ex.printStackTrace(); }
            errCode = success ? -1 : Prefs.ErrorCode.ERR_CANNOT_COMPLETE.ordinal();
        }
    }

    public int getErrCode() { return errCode; }
}