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
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.ping.Status;
import net.minestom.server.world.DimensionType;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public final class Main {

    private static final String LOG_TAG = "[PixelLimo]";

    public static void main(String[] args) throws Exception {
        Properties cfg = Config.load();

        String host = cfg.getProperty("server.host", "0.0.0.0");
        int port = Integer.parseInt(cfg.getProperty("server.port", "25565"));
        boolean onlineMode = Boolean.parseBoolean(cfg.getProperty("server.online-mode", "false"));
        String velocitySecret = cfg.getProperty("server.velocity-secret", "");
        String schemPath = cfg.getProperty("schematic.file", "afk_lobby.schem");
        int pasteX = Integer.parseInt(cfg.getProperty("schematic.paste.x", "0"));
        int pasteY = Integer.parseInt(cfg.getProperty("schematic.paste.y", "0"));
        int pasteZ = Integer.parseInt(cfg.getProperty("schematic.paste.z", "0"));
        boolean freeze = Boolean.parseBoolean(cfg.getProperty("limbo.freeze", "false"));
        int maxPlayers = Integer.parseInt(cfg.getProperty("server.max-players", "100"));
        String motd = cfg.getProperty("server.motd", "§bPixelLimo §8| §7AFK Lobby");
        String tabHeader = cfg.getProperty("tab.header", "§b§lPixelLimo");
        String tabFooter = cfg.getProperty("tab.footer", "§7AFK-Lobby");
        Component tabHeaderC = LegacyComponentSerializer.legacySection().deserialize(tabHeader);
        Component tabFooterC = LegacyComponentSerializer.legacySection().deserialize(tabFooter);

        // Velocity forwarding takes precedence over online-mode: when a Velocity
        // secret is configured we sit behind a proxy that already does Mojang
        // auth, and the backend must use Auth.Velocity or it rejects proxy
        // connections.
        Auth auth;
        if (!velocitySecret.isBlank()) {
            auth = new Auth.Velocity(velocitySecret);
            System.out.println(LOG_TAG + " Auth: Velocity (forwarding secret set)");
        } else if (onlineMode) {
            auth = new Auth.Online();
            System.out.println(LOG_TAG + " Auth: Online (Mojang)");
        } else {
            auth = new Auth.Offline();
            System.out.println(LOG_TAG + " Auth: Offline");
        }

        MinecraftServer server = MinecraftServer.init(auth);

        InstanceContainer instance = MinecraftServer.getInstanceManager()
                .createInstanceContainer(DimensionType.OVERWORLD);
        instance.setChunkSupplier(LightingChunk::new);
        instance.setTimeRate(0);
        instance.setTime(6000);
        instance.setGenerator(unit -> {});

        Pos pasteOrigin = new Pos(pasteX, pasteY, pasteZ);
        SchematicLoader.Loaded loaded = SchematicLoader.load(Path.of(schemPath), instance, pasteOrigin);
        System.out.printf("%s Schematic loaded: %dx%dx%d at %s%n",
                LOG_TAG, loaded.width(), loaded.height(), loaded.length(), pasteOrigin);

        // Spawn at world origin, facing east
        Pos spawn = new Pos(0, 0, 0, -90f, 0f);

        int radius = Math.max(3, (Math.max(loaded.width(), loaded.length()) / 16) + 1);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                instance.loadChunk(spawn.chunkX() + x, spawn.chunkZ() + z).join();
            }
        }

        GlobalEventHandler events = MinecraftServer.getGlobalEventHandler();

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

        events.addListener(PlayerBlockBreakEvent.class, event -> event.setCancelled(true));

        if (freeze) {
            MinecraftServer.getSchedulerManager().buildTask(() -> {
                for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                    if (p.getInstance() == instance && p.getPosition().distanceSquared(spawn) > 4) {
                        p.teleport(spawn);
                    }
                }
            }).repeat(Duration.ofMillis(500)).schedule();
        }

        events.addListener(ServerListPingEvent.class, e -> e.setStatus(Status.builder()
                .description(Component.text(motd))
                .playerInfo(MinecraftServer.getConnectionManager().getOnlinePlayers().size(), maxPlayers)
                .build()));

        MinecraftServer.setBrandName("PixelLimo");

        // Bind dual-stack on IPv6 wildcard when host is the IPv4 wildcard.
        // Netty epoll clients (e.g. PotatoCloud's Velocity plugin) connect
        // via IPv4-mapped-IPv6, which an IPv4-only listener refuses even
        // though the bind itself succeeds. Note: `new InetSocketAddress(port)`
        // is NOT enough — Java's anyLocalAddress() returns Inet4Address by
        // default, so we explicitly resolve "::" to force IPv6.
        SocketAddress bindAddr;
        if (host == null || host.isBlank() || "0.0.0.0".equals(host)) {
            bindAddr = new InetSocketAddress(InetAddress.getByName("::"), port);
        } else {
            bindAddr = new InetSocketAddress(host, port);
        }
        server.start(bindAddr);
        System.out.printf("%s Server listening on %s%n", LOG_TAG, bindAddr);

        PotatoCloudConnector.notifyStartedIfManaged();

        // Block main forever — Minestom runs on its own non-daemon threads
        // but the bind has been observed to fail-fast in some containers,
        // so keep main alive as a safety net.
        Object holdAlive = new Object();
        synchronized (holdAlive) {
            holdAlive.wait();
        }
    }

    private Main() {}
}
