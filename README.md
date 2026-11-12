# SonOyuncu-Clone

A local Minecraft 1.8 network inspired by SonOyuncu: a BungeeCord proxy, 7 game servers, a web control panel and a custom client.

[Türkçe](#türkçe)

## Features

- BungeeCord network with Lobby, Survival, SkyBlock, BedWars, SkyWars and Factions servers
- Web panel to start, stop and watch every server from the browser
- Live console, player list and console commands per server
- MottoClient plugin that only lets players in through the custom client
- One-click client launcher with AuthMe auto-login

## Setup

**Requirements:** Windows 10/11, Java 17 ([Adoptium](https://adoptium.net/)), Python 3.10+, 8 GB RAM

Server JARs are not included in the repo. Place each one in its folder:

| File | Folder |
|------|--------|
| `bungeecord.jar` | `servers/bungeecord/` |
| `cakmalobi.jar` | `servers/cakmalobi/` |
| `lobi.jar` | `servers/lobi/` |
| `skyblock.jar` | `servers/opskyblock/` |
| `bedwars.jar` | `servers/bedwars/` |
| `Skywars.jar` | `servers/skywars/` |
| `Survival.jar` | `servers/titanyum/` |
| `faction.jar` | `servers/faction/` |

Start the panel:

```
MottoSMP Baslat.bat
```

Open `http://127.0.0.1:7979` and click **Tumunu Baslat** (Start All). Then launch the game:

```
CraftRise\start.bat
```

The client connects to `127.0.0.1:25565` automatically.

## Servers

| Server | Port |
|--------|------|
| BungeeCord (entry) | 25565 |
| CakmaLobi | 25566 |
| Lobi | 25567 |
| OpSkyBlock | 25568 |
| BedWars | 25569 |
| SkyWars | 25570 |
| Titanyum (Survival) | 25571 |
| Faction | 25572 |

## Support

Join the Discord: [discord.gg/6g3XHNbdP6](https://discord.gg/6g3XHNbdP6)
Contact: motto08 / kjhyuga

## Dependencies

- Spigot 1.8.8
- BungeeCord
- ProtocolLib 5.4.0
- Python 3.10+ (standard library only)