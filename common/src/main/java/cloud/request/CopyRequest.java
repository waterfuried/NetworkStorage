package cloud.request;

import cloud.CloudMessage;

public class CopyRequest implements CloudMessage {
    private final String src, dst;
    private final boolean move;

    public CopyRequest(String src, String dst, boolean move) {
        this.src = src;
        this.dst = dst;
        this.move = move;
    }

    public String getSrc() { return src; }
    public String getDst() { return dst; }
    public boolean moved() { return move; }
}