import java.io.Serializable;
import java.util.Arrays;
import java.util.UUID;

public class DatagramChunk implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID messageId;
    private final int chunkIndex;
    private final int totalChunks;
    private final byte[] payload;

    public DatagramChunk(UUID messageId, int chunkIndex, int totalChunks, byte[] payload) {
        if (messageId == null) {
            throw new IllegalArgumentException("messageId не может быть null");
        }
        if (totalChunks <= 0) {
            throw new IllegalArgumentException("Количество фрагментов должно быть положительным");
        }
        if (chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new IllegalArgumentException("Некорректный номер фрагмента");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload не может быть null");
        }
        this.messageId = messageId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.payload = Arrays.copyOf(payload, payload.length);
    }

    public UUID getMessageId() {
        return messageId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
