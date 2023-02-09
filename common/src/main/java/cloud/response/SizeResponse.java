package cloud.response;

import cloud.CloudMessage;

public class SizeResponse implements CloudMessage {
    private final long size;
    private final int itemCount;

    public SizeResponse(long size, int itemCount) {
        this.size = size;
        this.itemCount = itemCount;
    }

    public long getSize() { return size; }
    public int getItemCount() { return itemCount; }
}