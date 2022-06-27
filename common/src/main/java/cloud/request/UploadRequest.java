package cloud.request;

import cloud.CloudMessage;

public class UploadRequest implements CloudMessage {
    private final String srcName, srcPath, dstPath;
    private final long size, modified;

    public UploadRequest(String srcName, String srcPath, String dstPath, long size, long modified) {
        this.srcName = srcName;
        this.srcPath = srcPath;
        this.dstPath = dstPath;
        this.size = size;
        this.modified = modified;
    }

    public String getSrcName() { return srcName; }
    public String getSrcPath() { return srcPath; }
    public String getDstPath() { return dstPath; }
    public long getSize() { return size; }
    public long getModified() { return modified; }
}
