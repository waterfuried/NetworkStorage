package cloud.response;

import cloud.CloudMessage;

public class DownloadResponse implements CloudMessage {
    private final int size;
    private final byte[] data;

    public DownloadResponse(int size, byte[] data) {
        this.size = size;
        this.data = data;
    }

    public int getSize() { return size; }
    public byte[] getData() { return data; }
}