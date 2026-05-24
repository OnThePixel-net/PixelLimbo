package de.leos.limbo;

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
            System.out.println("[PixelLimo] Config geladen aus " + FILE.toAbsolutePath());
        } else {
            try (var out = Files.newOutputStream(FILE)) {
                props.store(out, "PixelLimo Konfiguration");
            }
            System.out.println("[PixelLimo] Default-Config geschrieben nach " + FILE.toAbsolutePath());
        }

        // PotatoCloud schreibt Port in server.properties — der überschreibt limbo.properties
        if (Files.exists(PC_FILE)) {
            Properties pc = new Properties();
            try (InputStream in = Files.newInputStream(PC_FILE)) {
                pc.load(in);
            }
            String pcPort = pc.getProperty("server-port");
            if (pcPort != null && !pcPort.isBlank()) {
                props.setProperty("server.port", pcPort);
                System.out.println("[PixelLimo] Port-Override aus server.properties: " + pcPort);
            }
            // PotatoCloud SetupProxyStep schreibt forwarding-secrets + velocity-modern
            String fwdSecret = pc.getProperty("forwarding-secrets");
            String velocityModern = pc.getProperty("velocity-modern");
            if ("true".equals(velocityModern) && fwdSecret != null && !fwdSecret.isBlank()) {
                props.setProperty("server.velocity-secret", fwdSecret);
                System.out.println("[PixelLimo] Velocity-Forwarding aktiviert via server.properties");
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
        return p;
    }

    private Config() {}
}
