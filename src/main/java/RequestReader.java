public class RequestReader {
    private final RequestPacketHandler packetHandler = new RequestPacketHandler();

    public CommandRequest read(ReceivedPacket packet) throws Exception {
        return packetHandler.handle(packet);
    }
}
