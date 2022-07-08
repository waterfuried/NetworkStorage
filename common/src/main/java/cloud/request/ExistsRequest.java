package cloud.request;

import cloud.CloudMessage;

public class ExistsRequest implements CloudMessage {
    private final String path;
    public ExistsRequest(String path) { this.path = path; }

    public String getPath() { return path; }
}