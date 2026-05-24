package net.onthepixel.limbo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Config {

    private static final Path FILE = Path.of("limbo.properties");
    private static final Path PC_FILE = Path.of("server.properties");

    public static Properties load() throws IOException {
        Properties props = defaults();
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                props.load(in);
            }
            System.out.println("[PixelLimo] Config loaded from " + FILE.toAbsolutePath());
        } else {
            try (var out = Files.newOutputStream(FILE)) {
                props.store(out, "PixelLimo configuration");
            }
            System.out.println("[PixelLimo] Default config written to " + FILE.toAbsolutePath());
        }

        // PotatoCloud writes the port and proxy info into server.properties.
        // Override our settings from there so PotatoCloud-managed services
        // pick up the right port automatically.
        if (Files.exists(PC_FILE)) {
            Properties pc = new Properties();
            try (InputStream in = Files.newInputStream(PC_FILE)) {
                pc.load(in);
            }
            String pcPort = pc.getProperty("server-port");
            if (pcPort != null && !pcPort.isBlank()) {
                props.setProperty("server.port", pcPort);
                System.out.println("[PixelLimo] Port override from server.properties: " + pcPort);
            }
            String fwdSecret = pc.getProperty("forwarding-secrets");
            String velocityModern = pc.getProperty("velocity-modern");
            if ("true".equals(velocityModern) && fwdSecret != null && !fwdSecret.isBlank()) {
                props.setProperty("server.velocity-secret", fwdSecret);
                System.out.println("[PixelLimo] Velocity forwarding enabled via server.properties");
            }
        }

        return props;
    }

    private static Properties defaults() {
        Properties p = new Properties();
        p.setProperty("server.host", "0.0.0.0");
        p.setProperty("server.port", "25565");
        p.setProperty("server.motd", "§bPixelLimo §8| §7AFK Lobby");
        p.setProperty("server.max-players", "100");
        p.setProperty("server.online-mode", "false");
        p.setProperty("server.velocity-secret", "");
        p.setProperty("schematic.file", "afk_lobby.schem");
        p.setProperty("schematic.paste.x", "0");
        p.setProperty("schematic.paste.y", "0");
        p.setProperty("schematic.paste.z", "0");
        p.setProperty("limbo.freeze", "false");
        p.setProperty("tab.header", "§b§lPixelLimo");
        p.setProperty("tab.footer", "§7AFK-Lobby");
        // Multi-version gateway: a Netty front-end binds the public
        // server.port. Minestom binds a Unix Domain Socket (no second TCP
        // port) and the gateway forwards translated bytes through it.
        // Non-matching protocol versions get a clean Login Disconnect today;
        // future work plugs ViaVersion translation in there.
        p.setProperty("gateway.enabled", "true");
        p.setProperty("gateway.socket-path", "");  // empty = auto in tmpdir
        // Minestom 2026.05.17-1.21.11 speaks protocol 774 (Java 1.21.11).
        // MC 26.1 / 26.1.2 = protocol 775 — different protocol, gets kicked.
        p.setProperty("gateway.native-protocol", "774");
        return p;
    }

    private Config() {}
}
