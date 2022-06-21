package cloud;

import prefs.Prefs;

public class SpaceRequest implements CloudMessage {
    private final long space;

    public SpaceRequest() { this.space = Prefs.MAXSIZE; }

    public long getSpace() { return space; }
}