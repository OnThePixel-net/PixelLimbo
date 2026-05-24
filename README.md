# PixelLimo

A custom Minecraft limbo server built on [Minestom](https://github.com/Minestom/Minestom), targeting **Java 1.21.11** (protocol 774) with embedded [ViaProxy](https://github.com/ViaVersion/ViaProxy) for multi-version client support up to **26.1.2**.

Loads a SpongeSchematic as a static world and parks players there — designed as an AFK lobby, fallback service or queue server inside a PotatoCloud network.

```
                                                ┌──────────────────────┐
   Client (1.7 – 26.1.2)  ───► public TCP 25565 │ ViaProxy (embedded)  │
                                                │  • protocol detect    │
                                                │  • packet translation │
                                                └──────────┬───────────┘
                                                           │ 127.0.0.1:ephemeral
                                                           ▼
                                                ┌──────────────────────┐
                                                │ Minestom (1.21.11)    │
                                                │  • schematic world    │
                                                │  • event handlers     │
                                                │  • spawn / tab / MOTD │
                                                └──────────────────────┘
```

Both pieces run in the **same JVM**, single jar, no external proxy process.

## Features

- **Multi-version client support** (1.7 – 26.1.2) via embedded ViaProxy
- **SpongeSchematic v2 + v3 loader** — own implementation, no external lib
- **Online-mode auth** with proper Mojang skin loading
- **Velocity-modern forwarding** for use inside Velocity/PotatoCloud networks
- **PotatoCloud custom-platform compatible** — reads `server.properties` for port + forwarding secret
- **Adventure mode**, invulnerable players, no block break
- Configurable **MOTD**, **tab header/footer**, **spawn point**, **schematic paste offset**
- Optional player **freezing** (teleport back on movement)

## Requirements

- **JDK 25+** (required by Minestom)
- **Gradle 9.1+** (bundled via `gradlew`, Linux x86_64 for the epoll-based UDS transport ViaProxy ships)

## Quick start

```bash
./gradlew shadowJar
java -Xms256M -Xmx1G -jar build/libs/pixellimo.jar
```

On first start a `limbo.properties` file is generated with defaults. Place your `.schem` next to it (default name `afk_lobby.schem`). Connect to `localhost:25565`.

For PotatoCloud, point your `platforms.yml` at the always-current release jar:

```yaml
pixellimo:
  base: limbo
  custom: true
  proxy: false
  prepare-steps:
    - default-files
    - port
    - setup-proxy
  versions:
    - version: latest
      download: https://github.com/OnThePixel-net/PixelLimbo/releases/latest/download/pixellimo.jar
```

Then `group create lobby pixellimo latest`.

## Configuration (`limbo.properties`)

| Key | Default | Description |
|---|---|---|
| `server.host` | `0.0.0.0` | Public bind address (used by ViaProxy when gateway is on) |
| `server.port` | `25565` | Public bind port |
| `server.motd` | `§bPixelLimo §8\| §7AFK Lobby` | Server-list MOTD (legacy `§` color codes) |
| `server.max-players` | `100` | Reported max-players in server list ping |
| `server.online-mode` | `false` | Mojang auth on Minestom side (skip when behind Velocity) |
| `server.velocity-secret` | *(empty)* | Velocity-modern forwarding secret |
| `schematic.file` | `afk_lobby.schem` | Path to schematic, gzip-NBT SpongeSchematic format |
| `schematic.paste.x/y/z` | `0/0/0` | World offset for the schematic origin |
| `tab.header` | `§b§lPixelLimo` | Tab-list header |
| `tab.footer` | `§7AFK-Lobby` | Tab-list footer |
| `limbo.freeze` | `false` | Teleport players back to spawn whenever they move |
| `gateway.enabled` | `true` | Run ViaProxy in front of Minestom (for multi-version) |
| `gateway.native-protocol` | `774` | Minestom's server protocol (1.21.11) |
| `gateway.backend-port` | `0` | Loopback port Minestom binds. `0` = random ephemeral; set a fixed number for predictable firewall rules |

If a `server.properties` file is present (PotatoCloud writes one), `server-port`, `forwarding-secrets` and `velocity-modern` are taken from there and override the values above.

## Auth modes (priority)

1. `server.velocity-secret` set → `Auth.Velocity` (sits behind Velocity, Velocity does Mojang auth)
2. `server.online-mode=true` → `Auth.Online` (Mojang auth on Minestom)
3. Otherwise → `Auth.Offline`

Velocity-forwarding wins over online-mode because, behind a proxy, Minestom would otherwise reject proxy connections that don't carry a real Mojang session token.

## Technical notes

### Why a gateway in front of Minestom?

Minestom is a "single protocol version" server library — every release builds against one specific Minecraft protocol number. There's no built-in multi-version mechanism. ViaVersion exists for that, but it expects to be wired into a Netty pipeline owned by the host platform (Paper, Velocity, Bungee). Minestom uses Java NIO `SocketChannel` directly instead of Netty, so `ViaDecodeHandler` / `ViaEncodeHandler` (both Netty handlers) can't be plugged in.

The two workable options are:
1. Write a custom Minestom ViaPlatform — substantial work (a few hundred LOC of platform glue per Via subproject)
2. Put a Netty-based proxy *in front of* Minestom that handles version translation

PixelLimo takes option 2 but does it **in the same JVM**: ViaProxy is added as a library dependency and started directly from `Main`, the same pattern LoohpJames' [ViaLimbo](https://github.com/LOOHP/ViaLimbo) uses for the LOOHP Limbo server. Players see one public port; internally there's one extra loopback TCP socket between the two components.

### Port layout

| Where | Port | Visibility |
|---|---|---|
| ViaProxy (front-end) | `server.port` (default 25565) | Public — this is what clients connect to |
| Minestom backend | `gateway.backend-port` (default 0 → ephemeral) | `127.0.0.1` only, not reachable from outside |

Random ephemeral ports keep things conflict-free and discourage anything from talking to Minestom directly. Set a fixed number if you need a predictable firewall rule or want PotatoCloud's `forwarding-secrets` workflow to target a known port.

A truly socket-less in-process bridge (Netty `LocalChannel`, Unix Domain Sockets) isn't possible right now because ViaProxy's `setTargetAddress` only takes an `InetSocketAddress`. Switching to UDS would require patching ViaProxy.

### Schematic loader

SpongeSchematic v2 and v3 are both supported, including the v3 layout where `Width`/`Height`/`Length`, `Palette` and `BlockData` live under a `Blocks` subcompound. Block states with properties (`minecraft:oak_stairs[facing=east,half=top]`) are parsed and applied via `Block.withProperties(...)`. Blocks that don't exist in the running Minestom version (e.g. newer registry entries) fall back to air rather than throwing.

The reader uses `BinaryTagIO.unlimitedReader()` because adventure-nbt's default 128 KiB cap rejects anything beyond a tiny build.

### PotatoCloud integration

PotatoCloud `base: limbo` writes `server-port` and the velocity-modern handshake secret to a `server.properties` file in the per-service work directory before launching the JVM. PixelLimo's config layer reads that file in addition to `limbo.properties` so the assigned port and forwarding secret are picked up automatically with no glue code on the cloud side. The packaged `potatocloud-plugin-limbo.jar` PotatoCloud copies into `plugins/` is harmless — Minestom doesn't load it.

A minimal `PotatoCloudConnector` opens a raw TCP socket to `127.0.0.1:potatocloud.node.port` and writes the `ServiceStartedPacket` (id 3, UTF-8 service name) so PotatoCloud flips the service status from `STARTING` to `RUNNING`. The connection is kept open as a liveness signal; `ServiceProcessChecker` on the node side watches the OS process for actual lifecycle.

### Container / dual-stack note

When the limbo runs standalone (gateway disabled) and `server.host=0.0.0.0`, the bind is forced to the IPv6 wildcard `::` to get a dual-stack socket. Netty epoll clients (PotatoCloud's Velocity plugin in particular) connect via IPv4-mapped-IPv6 addresses; an IPv4-only listener refuses those connections even though `ss -ltn` shows the bind as successful. Binding via `InetAddress.getByName("::")` fixes that. When the gateway is enabled, ViaProxy handles the public binding and this only matters for the loopback backend.

## Releases

Every push to `main` automatically:
1. Builds the shadow jar
2. Cuts a new semver tag (patch-bumped from the latest `v*.*.*` tag)
3. Attaches `pixellimo.jar` as a release asset
4. Marks it as the latest release

The URL `https://github.com/OnThePixel-net/PixelLimbo/releases/latest/download/pixellimo.jar` always serves the newest build, which is what the PotatoCloud `platforms.yml` snippet above relies on.

Manual major/minor bumps are available via the **Run workflow** button on the `Release` workflow with the `bump` input set to `minor` or `major`.

## Building from source

```bash
git clone https://github.com/OnThePixel-net/PixelLimbo
cd PixelLimbo
./gradlew shadowJar
# Output: build/libs/pixellimo.jar
```

A `.schem` file next to the jar is the only runtime requirement. The first start writes a fully-defaulted `limbo.properties` you can then edit.

## License

MIT
