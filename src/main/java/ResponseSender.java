import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.List;

public class ResponseSender {
    private static final Logger logger = LogManager.getLogger(ResponseSender.class);

    public void send(DatagramChannel channel, SocketAddress clientAddress, CommandResponse response) throws IOException {
        List<byte[]> datagrams = DatagramChunker.splitObject(response);
        logger.info("Ответ клиенту {} разбит на {} UDP-фрагмент(ов)", clientAddress, datagrams.size());

        for (byte[] datagram : datagrams) {
            ByteBuffer buffer = ByteBuffer.wrap(datagram);
            while (buffer.hasRemaining()) {
                int sentBytes = channel.send(buffer, clientAddress);
                if (sentBytes == 0) {
                    Thread.yield();
                }
            }
        }
    }
}
