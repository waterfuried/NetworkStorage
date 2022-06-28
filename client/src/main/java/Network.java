import java.io.*;

import java.net.Socket;

public class Network {
    private final Socket socket;
    private DataInputStream is;
    private DataOutputStream os;

    public Network(int port) throws IOException {
        socket = new Socket("localhost", port);
        is = new DataInputStream(socket.getInputStream());
        os = new DataOutputStream(socket.getOutputStream());
    }

    public DataInputStream getIs() { return is; }
    public void setIs(DataInputStream is) { this.is = is; }

    public DataOutputStream getOs() { return os; }
    public void setOs(DataOutputStream os) { this.os = os; }

    public String read() throws IOException {
        return is.readUTF();
    }

    public void write(String message) throws IOException {
        os.writeUTF(message);
        os.flush();
    }

    public void close() {
        try { if (socket != null) socket.close(); }
        catch (IOException ex) { ex.printStackTrace();}
    }
}