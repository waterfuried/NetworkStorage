package cloud.request;

import cloud.CloudMessage;

public class SizeRequest implements CloudMessage {
    private final String path;

    public SizeRequest(String path) { this.path = path; }

    public String getPath() { return path; }
}