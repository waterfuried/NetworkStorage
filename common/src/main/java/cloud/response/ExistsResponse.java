package cloud.response;

import cloud.CloudMessage;
import java.nio.file.*;

public class ExistsResponse implements CloudMessage {
    private long exists;

    public ExistsResponse(Path path) {
        try {
            exists = Files.exists(path)
                    ? Files.isDirectory(path) ? -1L : Files.size(path)
                    : -2L;
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public long getExists() { return exists; }
}