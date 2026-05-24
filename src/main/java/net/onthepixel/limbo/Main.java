package net.onthepixel.limbo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.ping.Status;
import net.minestom.server.world.DimensionType;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public final class Main {

    public static void main(String[] args) throws Exception {
        Properties cfg = Config.load();

        String host = cfg.getProperty("server.host", "0.0.0.0");
        int port = Integer.parseInt(cfg.getProperty("server.port", "25565"));
        boolean onlineMode = Boolean.parseBoolean(cfg.getProperty("server.online-mode", "false"));
        String velocitySecret = cfg.getProperty("server.velocity-secret", "");
        String schemPath = cfg.getProperty("schematic.file", "afk_lobby.schem");
        int pasteX = Integer.parseInt(cfg.getProperty("schematic.paste.x", "0"));
        int pasteY = Integer.parseInt(cfg.getProperty("schematic.paste.y", "64"));
        int pasteZ = Integer.parseInt(cfg.getProperty("schematic.paste.z", "0"));
        boolean freeze = Boolean.parseBoolean(cfg.getProperty("limbo.freeze", "false"));
        String motd = cfg.getProperty("server.motd", "§bPixelLimo §8| §7AFK Lobby");
        String tabHeader = cfg.getProperty("tab.header", "§b§lPixelLimo");
        String tabFooter = cfg.getProperty("tab.footer", "§7AFK-Lobby");
        Component tabHeaderC = LegacyComponentSerializer.legacySection().deserialize(tabHeader);
        Component tabFooterC = LegacyComponentSerializer.legacySection().deserialize(tabFooter);

        Auth auth;
        if (onlineMode) {
            auth = new Auth.Online();
        } else if (!velocitySecret.isBlank()) {
            auth = new Auth.Velocity(velocitySecret);
        } else {
            auth = new Auth.Offline();
        }

        MinecraftServer server = MinecraftServer.init(auth);

        // Welt-Instance erzeugen (leerer Overworld-Dimension-Typ, voll im RAM)
        InstanceContainer instance = MinecraftServer.getInstanceManager()
                .createInstanceContainer(DimensionType.OVERWORLD);
        instance.setChunkSupplier(LightingChunk::new);
        instance.setTimeRate(0);                 // Zeit anhalten
        instance.setTime(6000);                  // Mittag
        // Generator: ringsum Air. Boden kommt aus der Schematic.
        instance.setGenerator(unit -> {});

        // Schematic laden + pasten
        Pos pasteOrigin = new Pos(pasteX, pasteY, pasteZ);
        SchematicLoader.Loaded loaded = SchematicLoader.load(
                Path.of(schemPath), instance, pasteOrigin);
        System.out.printf("[PixelLimo] Schematic geladen: %dx%dx%d ab %s%n",
                loaded.width(), loaded.height(), loaded.length(), pasteOrigin);

        // Spawn fest auf 0, 0, 0 — Blickrichtung Osten (yaw=-90)
        Pos spawn = new Pos(0, 0, 0, -90f, 0f);
        System.out.printf("[PixelLimo] Spawn-Point: %s%n", spawn);

        // Chunks rundherum vorladen (3 Chunk-Radius)
        int radius = Math.max(3, (Math.max(loaded.width(), loaded.length()) / 16) + 1);
        int spawnChunkX = spawn.chunkX();
        int spawnChunkZ = spawn.chunkZ();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                instance.loadChunk(spawnChunkX + x, spawnChunkZ + z).join();
            }
        }

        GlobalEventHandler events = MinecraftServer.getGlobalEventHandler();

        // Spawn-Logik (Skin kommt automatisch via Online-Mode Mojang-Auth)
        events.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();
            event.setSpawningInstance(instance);
            player.setRespawnPoint(spawn);
            player.setGameMode(GameMode.ADVENTURE);
        });

        events.addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();
            player.teleport(spawn);
            player.setInvulnerable(true);
            player.sendPlayerListHeaderAndFooter(tabHeaderC, tabFooterC);
        });

        // Blöcke nicht abbaubar machen
        events.addListener(PlayerBlockBreakEvent.class, event -> event.setCancelled(true));

        // Optional: Spieler einfrieren (Movement immer auf Spawn zurücksetzen)
        if (freeze) {
            MinecraftServer.getSchedulerManager().buildTask(() -> {
                for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                    if (p.getInstance() == instance && p.getPosition().distanceSquared(spawn) > 4) {
                        p.teleport(spawn);
                    }
                }
            }).repeat(Duration.ofMillis(500)).schedule();
        }

        // MOTD
        final int maxPlayers = Integer.parseInt(cfg.getProperty("server.max-players", "100"));
        events.addListener(net.minestom.server.event.server.ServerListPingEvent.class, e -> {
            int online = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
            e.setStatus(Status.builder()
                    .description(Component.text(motd))
                    .playerInfo(online, maxPlayers)
                    .build());
        });

        // BrandName
        MinecraftServer.setBrandName("PixelLimo");

        // Shutdown-Hook für Debug (falls JVM unerwartet beendet wird)
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                System.out.println("[PixelLimo] JVM Shutdown — Server wird beendet")));

        // Start
        server.start(host, port);
        System.out.printf("[PixelLimo] Server läuft auf %s:%d (online-mode=%s)%n", host, port, onlineMode);

        // PotatoCloud: Service als RUNNING markieren (nur wenn unter PC gestartet)
        PotatoCloudConnector.notifyStartedIfManaged();

        // Main-Thread am Leben halten falls Minestom irgendwann nur Daemon-Threads hätte
        // (z.B. in PotatoCloud-Containern wo sonst der Prozess sofort wieder stirbt)
        Object keepAlive = new Object();
        synchronized (keepAlive) {
            try {
                keepAlive.wait();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Main() {}
}
