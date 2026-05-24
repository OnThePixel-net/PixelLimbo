package net.onthepixel.limbo;

import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal PotatoCloud node connector.
 *
 * If the system properties {@code potatocloud.service.name} and
 * {@code potatocloud.node.port} are set, connect to the node and send
 * {@code ServiceStartedPacket} (packet id 3) so PotatoCloud flips the
 * service status from STARTING to RUNNING.
 *
 * Wire format (see NettyPacketEncoder in PotatoCloud):
 * <pre>
 *   int32 totalLength    // = 4 (packetId) + 4 (stringLen) + nameBytes.length
 *   int32 packetId       // 3 = SERVICE_STARTED
 *   int32 stringLength   // UTF-8 byte count
 *   byte[] nameBytes     // UTF-8
 * </pre>
 *
 * The socket is kept open so the node continues to see us as alive.
 * Broadcasts from the node (memory updates etc.) are silently discarded —
 * status stays RUNNING and PotatoCloud's ProcessChecker watches the OS
 * process directly.
 */
public final class PotatoCloudConnector {

    private static final int PACKET_ID_SERVICE_STARTED = 3;
    private static final String NODE_HOST = "127.0.0.1";

    public static void notifyStartedIfManaged() {
        String serviceName = System.getProperty("potatocloud.service.name");
        String nodePortStr = System.getProperty("potatocloud.node.port");
        if (serviceName == null || nodePortStr == null) {
            return;
        }
        int nodePort;
        try {
            nodePort = Integer.parseInt(nodePortStr);
        } catch (NumberFormatException e) {
            System.err.println("[PixelLimo] potatocloud.node.port is not a number: " + nodePortStr);
            return;
        }

        Thread t = new Thread(() -> {
            try {
                Socket sock = new Socket();
                sock.connect(new InetSocketAddress(NODE_HOST, nodePort), 5000);
                sock.setKeepAlive(true);
                sock.setTcpNoDelay(true);

                byte[] nameBytes = serviceName.getBytes(StandardCharsets.UTF_8);
                DataOutputStream out = new DataOutputStream(sock.getOutputStream());
                int total = 4 + 4 + nameBytes.length;
                out.writeInt(total);
                out.writeInt(PACKET_ID_SERVICE_STARTED);
                out.writeInt(nameBytes.length);
                out.write(nameBytes);
                out.flush();

                System.out.printf("[PixelLimo] PotatoCloud: notified node, service=%s, node=:%d%n",
                        serviceName, nodePort);

                // Hold the connection open until the process exits or the node
                // closes it from its end.
                //noinspection ResultOfMethodCallIgnored
                sock.getInputStream().read();
            } catch (Exception e) {
                System.err.println("[PixelLimo] PotatoCloud connector error: " + e.getMessage());
            }
        }, "PotatoCloudConnector");
        t.setDaemon(true);
        t.start();
    }

    private PotatoCloudConnector() {}
}
