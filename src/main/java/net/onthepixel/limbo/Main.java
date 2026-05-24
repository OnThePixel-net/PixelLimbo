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

        // Velocity-Forwarding hat Vorrang vor Online-Mode:
        // Wenn ein Velocity-Secret vorhanden ist, sitzen wir hinter einem Velocity-Proxy
        // der schon Mojang-Auth + Skins macht — Backend muss Auth.Velocity nutzen,
        // sonst lehnt Limbo Verbindungen vom Proxy ab.
        Auth auth;
        if (!velocitySecret.isBlank()) {
            auth = new Auth.Velocity(velocitySecret);
            System.out.println("[PixelLimo] Auth-Mode: Velocity (Forwarding-Secret gesetzt)");
        } else if (onlineMode) {
            auth = new Auth.Online();
            System.out.println("[PixelLimo] Auth-Mode: Online (Mojang)");
        } else {
            auth = new Auth.Offline();
            System.out.println("[PixelLimo] Auth-Mode: Offline");
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

        // ==== DEBUG: System-Infos vorm Start ====
        Runtime rt = Runtime.getRuntime();
        System.out.printf("[PixelLimo][debug] JVM=%s %s, max-heap=%dMB, processors=%d%n",
                System.getProperty("java.vm.name"),
                System.getProperty("java.version"),
                rt.maxMemory() / (1024 * 1024),
                rt.availableProcessors());
        System.out.printf("[PixelLimo][debug] cwd=%s%n", System.getProperty("user.dir"));
        System.out.printf("[PixelLimo][debug] potatocloud.service.name=%s, potatocloud.node.port=%s%n",
                System.getProperty("potatocloud.service.name"),
                System.getProperty("potatocloud.node.port"));

        // Shutdown-Hook für Debug (zeigt warum JVM endet — Stack-Trace im Log)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[PixelLimo][shutdown] JVM Shutdown initiiert");
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                System.out.println("[PixelLimo][shutdown]   noch lebender Thread: " + t.getName() + " daemon=" + t.isDaemon());
            }
        }));

        // Start
        try {
            server.start(host, port);
        } catch (Throwable t) {
            System.err.println("[PixelLimo][FATAL] server.start fehlgeschlagen: " + t);
            t.printStackTrace();
            throw t;
        }
        System.out.printf("[PixelLimo] Server läuft auf %s:%d (online-mode=%s)%n", host, port, onlineMode);

        // Self-Bind-Test + Kernel-Listen-Dump: was sehen wir wirklich?
        new Thread(() -> {
            try {
                Thread.sleep(500);
                // Netzwerk-Interfaces ausgeben
                try {
                    java.util.Enumeration<java.net.NetworkInterface> nets = java.net.NetworkInterface.getNetworkInterfaces();
                    while (nets.hasMoreElements()) {
                        java.net.NetworkInterface ni = nets.nextElement();
                        if (!ni.isUp()) continue;
                        java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                        while (addrs.hasMoreElements()) {
                            System.out.printf("[PixelLimo][net] iface=%s addr=%s%n", ni.getName(), addrs.nextElement().getHostAddress());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[PixelLimo][net] iface-dump fail: " + e.getMessage());
                }

                // Self-connect-Tests
                for (String testHost : new String[]{"127.0.0.1", "0.0.0.0", host}) {
                    try (java.net.Socket s = new java.net.Socket()) {
                        s.connect(new java.net.InetSocketAddress(testHost, port), 2000);
                        System.out.printf("[PixelLimo][selftest] OK: %s:%d erreichbar%n", testHost, port);
                    } catch (Exception e) {
                        System.err.printf("[PixelLimo][selftest] FAIL: %s:%d → %s%n", testHost, port, e.getMessage());
                    }
                }

                // Kernel-Sicht IPv4 + IPv6
                for (String file : new String[]{"/proc/net/tcp", "/proc/net/tcp6"}) {
                    try {
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of(file));
                        System.out.println("[PixelLimo][kernel] LISTEN-Sockets aus " + file + ":");
                        for (String line : lines) {
                            String[] parts = line.trim().split("\\s+");
                            if (parts.length < 4) continue;
                            if (!"0A".equals(parts[3])) continue;
                            System.out.printf("[PixelLimo][kernel]   %s%n", parts[1]);
                        }
                    } catch (Exception e) {
                        System.err.println("[PixelLimo][kernel] " + file + " lesen fail: " + e.getMessage());
                    }
                }

                // Reachability-Test: andere PC-Services + Velocity erreichbar?
                System.out.println("[PixelLimo][reach] Test zu anderen PC-Services:");
                int[] testPorts = {25565, 30000, 30001, 30002, 30003, 30004, 30005, 16000};
                String[] testHosts = {"127.0.0.1", "172.18.0.1"};
                for (String th : testHosts) {
                    for (int tp : testPorts) {
                        try (java.net.Socket s = new java.net.Socket()) {
                            s.connect(new java.net.InetSocketAddress(th, tp), 500);
                            System.out.printf("[PixelLimo][reach] OK   %s:%d%n", th, tp);
                        } catch (Exception e) {
                            // Nur loggen wenn refused (bedeutet "kein Host"). Timeout/unreachable = silent.
                            String msg = e.getMessage() == null ? "" : e.getMessage();
                            if (msg.contains("refused") || msg.contains("Connection")) {
                                System.out.printf("[PixelLimo][reach] FAIL %s:%d (%s)%n", th, tp, msg);
                            }
                        }
                    }
                }

                // Hostname für Debug
                try {
                    System.out.println("[PixelLimo][reach] hostname=" + java.net.InetAddress.getLocalHost().getHostName());
                } catch (Exception ignored) {}
            } catch (InterruptedException ignored) {}
        }, "limbo-selftest").start();

        // PotatoCloud: Service als RUNNING markieren (nur wenn unter PC gestartet)
        PotatoCloudConnector.notifyStartedIfManaged();

        // Heartbeat alle 30s — sehen wir im Log wenn JVM steht
        Thread hb = new Thread(() -> {
            long started = System.currentTimeMillis();
            while (true) {
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) { return; }
                long up = (System.currentTimeMillis() - started) / 1000;
                long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                int online = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
                System.out.printf("[PixelLimo][heartbeat] uptime=%ds, heap=%dMB, players=%d%n",
                        up, usedMb, online);
            }
        }, "limbo-heartbeat");
        hb.setDaemon(true);
        hb.start();

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
