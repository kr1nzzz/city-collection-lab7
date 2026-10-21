import java.net.SocketAddress;

public class ReceivedPacket {
    private final SocketAddress clientAddress;
    private final byte[] data;

    public ReceivedPacket(SocketAddress clientAddress, byte[] data) {
        this.clientAddress = clientAddress;
        this.data = data;
    }

    public SocketAddress getClientAddress() {
        return clientAddress;
    }

    public byte[] getData() {
        return data;
    }
}
