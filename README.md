# PixelLimo

Custom Minecraft Limbo Server für Version **1.21.11**, gebaut auf [Minestom](https://github.com/Minestom/Minestom).

Lädt eine SpongeSchematic als statische Welt und parkt Spieler dort — gedacht als AFK-Lobby oder Fallback-Server.

## Features

- **SpongeSchematic v2/v3 Loader** (eigene Implementierung, keine externe Lib)
- **Konfigurierbar** über `limbo.properties`: Spawn, Paste-Offset, MOTD, Tab-Header/-Footer, Online-Mode, Velocity-Forwarding
- **PotatoCloud-kompatibel** als Custom-Platform — liest `server.properties` für Port + Velocity-Modern-Forwarding-Secret
- **Online-Mode** mit Mojang-Auth → Skins funktionieren out-of-the-box
- **Adventure-Mode**, invulnerable Spieler, kein Block-Break
- Optionales Player-Freezing (Spawn-Zurück-Teleport)

## Voraussetzungen

- **JDK 25+** (Minestom verlangt Java 25)
- **Gradle 9.1+** (über mitgeliefertes `gradlew`)

## Build

```bash
./gradlew shadowJar
```

Output: `build/libs/limbo-server.jar`

## Run

```bash
java -Xms256M -Xmx1G -jar build/libs/limbo-server.jar
```

Beim ersten Start wird `limbo.properties` mit Defaults generiert.

Eine `.schem`-Datei muss im gleichen Verzeichnis liegen (Default-Name: `afk_lobby.schem` — anpassbar via `schematic.file`).

## Konfiguration

`limbo.properties`:

| Key | Default | Beschreibung |
|---|---|---|
| `server.host` | `0.0.0.0` | Bind-Adresse |
| `server.port` | `25565` | Bind-Port (von `server.properties` `server-port` überschrieben falls vorhanden) |
| `server.motd` | `§bPixelLimo §8\| §7AFK Lobby` | Server-List-MOTD |
| `server.max-players` | `100` | Anzeige im Server-Browser |
| `server.online-mode` | `false` | `true` = Mojang-Auth, Skins funktionieren, nur Premium |
| `server.velocity-secret` | *(leer)* | Velocity-Modern-Forwarding-Secret |
| `schematic.file` | `afk_lobby.schem` | Pfad zur Schematic |
| `schematic.paste.x/y/z` | `0/0/0` | Welt-Offset für Schematic-Origin |
| `tab.header` | `§b§lPixelLimo` | Tab-Liste Header (`§`-Codes ok) |
| `tab.footer` | `§7AFK-Lobby` | Tab-Liste Footer |
| `limbo.freeze` | `false` | Spieler zurück-teleportieren bei Bewegung |

Wenn eine `server.properties` neben `limbo.properties` liegt, werden `server-port`, `forwarding-secrets` und `velocity-modern` daraus übernommen (für PotatoCloud-Kompatibilität).

## PotatoCloud-Integration

Als Custom-Platform einbindbar. Beispiel-Eintrag in `platforms.yml`:

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
    - version: 1.0.0
      local: true
```

Jar muss unter `platforms/pixellimo/1.0.0/pixellimo-1.0.0.jar` im PotatoCloud-Run-Verzeichnis liegen.

Dann: `group create lobby pixellimo 1.0.0`

## Lizenz

MIT
