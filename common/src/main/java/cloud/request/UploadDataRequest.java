package cloud.request;

import cloud.CloudMessage;

public class UploadDataRequest implements CloudMessage {
    private final int id, size;
    private final byte[] data;

    public UploadDataRequest(int id, int size, byte[] data) {
        this.id = id;
        this.size = size;
        this.data = data;
    }

    public int getId() { return id; }
    public int getSize() { return size; }
    public byte[] getData() { return data; }
}