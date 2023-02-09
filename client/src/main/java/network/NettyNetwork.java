package network;

import cloud.*;

import io.netty.handler.codec.serialization.*;

import java.io.IOException;

import java.net.Socket;

public class NettyNetwork {
    private ObjectDecoderInputStream is;
    private ObjectEncoderOutputStream os;

    public NettyNetwork(int port) throws IOException {
        Socket socket = new Socket("localhost", port);
        os = new ObjectEncoderOutputStream(socket.getOutputStream());
        is = new ObjectDecoderInputStream(socket.getInputStream());
    }

    public CloudMessage read() throws IOException, ClassNotFoundException {
        return (CloudMessage)is.readObject();
    }

    public void write(CloudMessage msg) throws IOException {
        os.writeObject(msg);
        os.flush();
    }
}