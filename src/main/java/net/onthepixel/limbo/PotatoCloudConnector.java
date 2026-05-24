package net.onthepixel.limbo;

import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimaler PotatoCloud-Node-Connector.
 *
 * Wenn das System-Property {@code potatocloud.service.name} und
 * {@code potatocloud.node.port} gesetzt sind, verbinden wir uns mit dem Node
 * und schicken den {@code ServiceStartedPacket} (Packet-ID 3). Damit setzt
 * PotatoCloud den Service-Status von STARTING auf RUNNING.
 *
 * Packet-Wire-Format (siehe NettyPacketEncoder in PotatoCloud):
 * <pre>
 *   int32 totalLength    // = 4 (packetId) + 4 (stringLen) + nameBytes.length
 *   int32 packetId       // 3 = SERVICE_STARTED
 *   int32 stringLength   // UTF-8 byte count
 *   byte[] nameBytes     // UTF-8
 * </pre>
 *
 * Wir lassen die Socket-Verbindung offen damit der Node uns als "lebt"
 * sieht (Memory-Updates und sonstige Broadcasts gehen aktuell ins Leere,
 * was OK ist — Status bleibt RUNNING, ProcessChecker watcht den OS-Prozess).
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
            System.err.println("[PixelLimo] potatocloud.node.port ist keine Zahl: " + nodePortStr);
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

                System.out.println("[PixelLimo] PotatoCloud: ServiceStarted gesendet (service=" + serviceName + ", node=:"+ nodePort + ")");

                // Verbindung offen halten — bis Prozess endet oder Node die Verbindung schließt
                sock.getInputStream().read();  // blockt bis EOF / disconnect
            } catch (Exception e) {
                System.err.println("[PixelLimo] PotatoCloud-Connector Fehler: " + e.getMessage());
            }
        }, "PotatoCloudConnector");
        t.setDaemon(true);
        t.start();
    }

    private PotatoCloudConnector() {}
}
