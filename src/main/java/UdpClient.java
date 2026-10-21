import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.List;

public class UdpClient implements AutoCloseable {
    private final DatagramSocket socket;
    private final InetAddress serverAddress;
    private final int serverPort;
    private final ResponsePacketHandler responsePacketHandler = new ResponsePacketHandler();

    public UdpClient(String host, int port, int timeoutMillis) throws IOException {
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(timeoutMillis);
        this.serverAddress = InetAddress.getByName(host);
        this.serverPort = port;
    }

    public CommandResponse sendAndReceive(CommandRequest request) throws IOException, ClassNotFoundException {
        responsePacketHandler.clear();
        sendRequest(request);

        while (true) {
            byte[] incomingBuffer = new byte[Serializer.MAX_UDP_PACKET_SIZE];
            DatagramPacket incomingPacket = new DatagramPacket(incomingBuffer, incomingBuffer.length);
            socket.receive(incomingPacket);

            CommandResponse response = responsePacketHandler.handle(incomingPacket);
            if (response != null) {
                return response;
            }
        }
    }

    private void sendRequest(CommandRequest request) throws IOException {
        List<byte[]> datagrams = DatagramChunker.splitObject(request);
        for (byte[] requestDatagram : datagrams) {
            DatagramPacket outgoingPacket = new DatagramPacket(
                    requestDatagram,
                    requestDatagram.length,
                    serverAddress,
                    serverPort
            );
            socket.send(outgoingPacket);
        }
    }

    public CommandResponse sendWithRetries(CommandRequest request, int attempts) throws IOException, ClassNotFoundException {
        SocketTimeoutException lastTimeout = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return sendAndReceive(request);
            } catch (SocketTimeoutException e) {
                lastTimeout = e;
                System.out.println("Сервер временно недоступен, попытка " + attempt + " из " + attempts + "...");
            }
        }
        throw lastTimeout == null ? new SocketTimeoutException("Нет ответа от сервера.") : lastTimeout;
    }

    @Override
    public void close() {
        socket.close();
    }
}
