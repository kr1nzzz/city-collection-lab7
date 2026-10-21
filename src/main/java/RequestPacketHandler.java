import java.io.IOException;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class RequestPacketHandler {
    private static final long MESSAGE_TTL_MILLIS = 30_000;
    private final Map<String, MessageState> incompleteMessages = new HashMap<>();

    public CommandRequest handle(ReceivedPacket packet) throws IOException, ClassNotFoundException {
        cleanupOldMessages();

        DatagramChunk chunk = DatagramChunker.readChunk(packet.getData());
        String key = makeKey(packet.getClientAddress(), chunk.getMessageId());
        MessageState state = incompleteMessages.computeIfAbsent(key, ignored -> new MessageState(chunk.getTotalChunks()));

        state.add(chunk);
        if (!state.isComplete()) {
            return null;
        }

        incompleteMessages.remove(key);
        byte[] requestBytes = DatagramChunker.join(state.getChunks());
        Object object = Serializer.fromBytes(requestBytes);
        if (!(object instanceof CommandRequest request)) {
            throw new IOException("Получен объект неизвестного типа: " + object.getClass().getName());
        }
        return request;
    }

    private String makeKey(SocketAddress clientAddress, UUID messageId) {
        return clientAddress.toString() + "#" + messageId;
    }

    private void cleanupOldMessages() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, MessageState>> iterator = incompleteMessages.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, MessageState> entry = iterator.next();
            if (now - entry.getValue().getLastUpdateMillis() > MESSAGE_TTL_MILLIS) {
                iterator.remove();
            }
        }
    }

    private static class MessageState {
        private final DatagramChunk[] chunks;
        private int receivedChunks;
        private long lastUpdateMillis;

        private MessageState(int totalChunks) {
            this.chunks = new DatagramChunk[totalChunks];
            this.lastUpdateMillis = System.currentTimeMillis();
        }

        private void add(DatagramChunk chunk) throws IOException {
            if (chunk.getTotalChunks() != chunks.length) {
                throw new IOException("Получен фрагмент с неверным общим количеством частей.");
            }
            int index = chunk.getChunkIndex();
            if (chunks[index] == null) {
                chunks[index] = chunk;
                receivedChunks++;
            }
            lastUpdateMillis = System.currentTimeMillis();
        }

        private boolean isComplete() {
            return receivedChunks == chunks.length;
        }

        private DatagramChunk[] getChunks() {
            return chunks;
        }

        private long getLastUpdateMillis() {
            return lastUpdateMillis;
        }
    }
}
