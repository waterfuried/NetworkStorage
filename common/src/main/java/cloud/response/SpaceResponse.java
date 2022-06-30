package cloud.response;

import cloud.CloudMessage;
import prefs.*;
import java.nio.file.*;

/**
 * ответ на запрос свободного места
 * возвращает значение в байтах
 */
public class SpaceResponse implements CloudMessage {
    private final long space;

    public SpaceResponse(Path userFolder) { this.space = Prefs.MAXSIZE - FileInfo.getSizes(userFolder); }
    public SpaceResponse(long space) { this.space = space; }

    public long getSpace() { return space; }
}
