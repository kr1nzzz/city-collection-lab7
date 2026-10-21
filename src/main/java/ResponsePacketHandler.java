import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ResponsePacketHandler {
    private static final long MESSAGE_TTL_MILLIS = 30_000;
    private final Map<UUID, MessageState> incompleteMessages = new HashMap<>();

    public CommandResponse handle(DatagramPacket packet) throws IOException, ClassNotFoundException {
        cleanupOldMessages();

        byte[] datagramBytes = Arrays.copyOf(packet.getData(), packet.getLength());
        DatagramChunk chunk = DatagramChunker.readChunk(datagramBytes);
        MessageState state = incompleteMessages.computeIfAbsent(chunk.getMessageId(), ignored -> new MessageState(chunk.getTotalChunks()));

        state.add(chunk);
        if (!state.isComplete()) {
            return null;
        }

        incompleteMessages.remove(chunk.getMessageId());
        byte[] responseBytes = DatagramChunker.join(state.getChunks());
        Object object = Serializer.fromBytes(responseBytes);
        if (!(object instanceof CommandResponse response)) {
            throw new IOException("Сервер вернул объект неизвестного типа: " + object.getClass().getName());
        }
        return response;
    }

    public void clear() {
        incompleteMessages.clear();
    }

    private void cleanupOldMessages() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, MessageState>> iterator = incompleteMessages.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MessageState> entry = iterator.next();
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
