package cloud.response;

import cloud.CloudMessage;

public class FSTypeNotice implements CloudMessage {
    private final int FSType;
    public FSTypeNotice(int FSType) { this.FSType = FSType; }
    public int getFSType() { return FSType; }
}