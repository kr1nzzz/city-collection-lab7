import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class DatagramChunker {
    public static final int CHUNK_PAYLOAD_SIZE = 8_000;

    private DatagramChunker() {
    }

    public static List<byte[]> splitObject(Serializable object) throws IOException {
        return splitBytes(Serializer.toBytes(object));
    }

    public static List<byte[]> splitBytes(byte[] source) throws IOException {
        UUID messageId = UUID.randomUUID();
        int totalChunks = Math.max(1, (int) Math.ceil(source.length / (double) CHUNK_PAYLOAD_SIZE));
        List<byte[]> datagrams = new ArrayList<>(totalChunks);

        for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
            int start = chunkIndex * CHUNK_PAYLOAD_SIZE;
            int end = Math.min(source.length, start + CHUNK_PAYLOAD_SIZE);
            byte[] payload = Arrays.copyOfRange(source, start, end);
            DatagramChunk chunk = new DatagramChunk(messageId, chunkIndex, totalChunks, payload);
            byte[] datagramBytes = Serializer.toBytes(chunk);

            if (datagramBytes.length > Serializer.MAX_UDP_PACKET_SIZE) {
                throw new IOException("Фрагмент сообщения превысил максимальный размер UDP-датаграммы.");
            }
            datagrams.add(datagramBytes);
        }
        return datagrams;
    }

    public static DatagramChunk readChunk(byte[] datagramBytes) throws IOException, ClassNotFoundException {
        Object object = Serializer.fromBytes(datagramBytes);
        if (!(object instanceof DatagramChunk chunk)) {
            throw new IOException("Получена UDP-датаграмма неизвестного формата.");
        }
        return chunk;
    }

    public static byte[] join(DatagramChunk[] chunks) throws IOException {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
            for (int i = 0; i < chunks.length; i++) {
                DatagramChunk chunk = chunks[i];
                if (chunk == null) {
                    throw new IOException("Нельзя собрать сообщение: отсутствует фрагмент " + i);
                }
                byteStream.write(chunk.getPayload());
            }
            return byteStream.toByteArray();
        }
    }
}
