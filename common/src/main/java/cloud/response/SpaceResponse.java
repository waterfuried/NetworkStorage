package cloud.response;

import cloud.CloudMessage;
import prefs.*;

/**
 * ответ на запрос свободного места
 * возвращает значение в байтах
 */
public class SpaceResponse implements CloudMessage {
    private final long space;

    public SpaceResponse() { this.space = Prefs.MAXSIZE - FileInfo.getSizes(Prefs.serverURL); }
    public SpaceResponse(long space) { this.space = space; }

    public long getSpace() { return space; }
}
