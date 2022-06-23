package cloud.request;

import cloud.CloudMessage;

public class RemovalRequest implements CloudMessage {
    private final String path;

    public RemovalRequest(String path) { this.path = path; }

    public String getPath() { return path; }
}
