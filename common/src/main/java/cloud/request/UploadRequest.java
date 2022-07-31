package cloud.request;

import cloud.CloudMessage;

public class UploadRequest implements CloudMessage {
    private final String srcPath, dstPath;
    private final long size, modified;
    private final boolean replace;

    public UploadRequest(String srcPath, String dstPath, long size, long modified, boolean replace) {
        this.srcPath = srcPath;
        this.dstPath = dstPath;
        this.size = size;
        this.modified = modified;
        this.replace = replace;
    }

    public String getSrcPath() { return srcPath; }
    public String getDstPath() { return dstPath; }
    public long getSize() { return size; }
    public long getModified() { return modified; }
    public boolean replace() { return replace; }
}