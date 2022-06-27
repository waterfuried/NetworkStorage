package cloud.response;

import cloud.CloudMessage;
import prefs.*;

import java.io.*;
import java.nio.file.Paths;

public class DownloadResponse implements CloudMessage {
    private int errCode;

    // /download server_source_path destination_path size [date]
    public DownloadResponse(String srcPath, String dstPath, long size, long modified) {
        byte[] buf = new byte[Prefs.BUF_SIZE];
        boolean success;
        String src = Prefs.serverURL.resolve(srcPath).toString(),
               dst = Paths.get(dstPath, srcPath.substring(srcPath
                        .lastIndexOf(File.separatorChar)+1)).toString();
        if (size >= 0) {
            // файл
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src), Prefs.BUF_SIZE);
                 BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dst), Prefs.BUF_SIZE)) {
                int bytesRead;
                while ((bytesRead = bis.read(buf)) >= 0) bos.write(buf, 0, bytesRead);
            } catch (Exception ex) { ex.printStackTrace(); }
            success = new File(dst).length() == size;
            // установить дату и время последней модификации как у оригинала
            if (success && modified > 0)
                try {
                    FileInfo fi = new FileInfo(Paths.get(dst));
                    fi.setModified(modified);
                    new File(dst).setLastModified(fi.getModifiedAsLong());
                } catch (NumberFormatException ex) { ex.printStackTrace(); }
        } else
            // папка
            success = new File(dst).mkdir();
        if (!success) errCode = Prefs.ERR_CANNOT_COMPLETE;
    }

    public int getErrCode() { return errCode; }
}