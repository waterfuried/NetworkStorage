package prefs;

public class TransferOp {
    private final String path;
    private final long modified, curSize, newSize;
    private long received;

    public TransferOp(String path, long curSize, long newSize, long modified) {
        this.path = path;
        this.curSize = curSize;
        this.newSize = newSize;
        this.modified = modified;
        this.received = 0;
    }

    public String getPath() { return path; }
    public long getCurSize() { return curSize; }
    public long getNewSize() { return newSize; }
    public long getModified() { return modified; }
    public long getReceived() { return received; }
    public void setReceived(long received) { this.received = received; }
}