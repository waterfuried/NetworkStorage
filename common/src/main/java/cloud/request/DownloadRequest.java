package cloud.request;

import cloud.CloudMessage;

public class DownloadRequest implements CloudMessage {
    private final String srcPath;

    public DownloadRequest(String srcPath) { this.srcPath = srcPath; }

    public String getSrcPath() { return srcPath; }
}