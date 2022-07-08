package cloud.request;

import cloud.CloudMessage;

public class UploadRequest implements CloudMessage {
    private final String srcPath, dstPath;
    private final long size, modified;

    public UploadRequest(String srcPath, String dstPath, long size, long modified) {
        this.srcPath = srcPath;
        this.dstPath = dstPath;
        this.size = size;
        this.modified = modified;
    }

    public String getSrcPath() { return srcPath; }
    public String getDstPath() { return dstPath; }
    public long getSize() { return size; }
    public long getModified() { return modified; }
}