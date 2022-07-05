package prefs;

public class TransferOp {
    String path;
    long modified;
    int oldSize, newSize, received;

    public TransferOp(String path, int oldSize, int newSize, long modified) {
        this.path = path;
        this.oldSize = oldSize;
        this.newSize = newSize;
        this.modified = modified;
        this.received = 0;
    }

    public String getPath() { return path; }
    public int getOldSize() { return oldSize; }
    public int getNewSize() { return newSize; }
    public long getModified() { return modified; }
    public int getReceived() { return received; }
    public void setReceived(int received) { this.received = received; }
}