import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;

public class ServerReceiver {
    private final ByteBuffer buffer = ByteBuffer.allocate(Serializer.MAX_UDP_PACKET_SIZE);

    public ReceivedPacket receive(DatagramChannel channel) throws IOException {
        buffer.clear();
        SocketAddress clientAddress = channel.receive(buffer);
        if (clientAddress == null) {
            return null;
        }
        buffer.flip();
        byte[] data = Arrays.copyOf(buffer.array(), buffer.limit());
        return new ReceivedPacket(clientAddress, data);
    }
}
