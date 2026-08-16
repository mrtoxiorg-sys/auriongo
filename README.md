# AurionGo

AurionGo is a modular Paper plugin built for the Aurion server.

It covers everything players and staff touch every day — chat, AFK, gamemodes, player modes, moderation, player data, server monitoring, and world switching — and keeps it all config-driven: no hardcoded messages, one `messages.yml` for localization, and a reusable theme palette.

## Features

### Chat
- MiniMessage chat formatting with legacy color translation (`&` / `§`), configurable per permission
- PlaceholderAPI support inside chat messages and formats
- Local channel with radius (default 150 blocks) and global channel with `!` prefix
- Action bar notice when nobody is around to hear the local message
- Private messages `/msg` (aliases `/tell`, `/m`, `/w`) with hover-to-write, reply tracking via `/r`, and configurable incoming-message sound
- Self-notes by messaging yourself
- Staff message spy `/spy`
- Server-wide announcements `/broadcast` (alias `/ac`)
- Join / quit / world-switch messages with configurable formats
- InteractiveChat compatibility hooks
- Muted players cannot chat, and muted command execution is blocked

### AFK
- `/afk` toggle with server announcements
- Automatic AFK after a configurable idle period (`auto-afk.idle-seconds`)
- Activity tracking (movement, chat, interactions) wakes players up automatically
- PlaceholderAPI expansion `%auriongo_afk%` with configurable online/AFK indicators
- AFK announcements respect player hide settings and vanished (SuperVanish) players

### Player data
- Player profiles stored in SQLite, MySQL, or MariaDB: UUID, nickname, IP history, first/last join, hide settings, spy state
- First-join announcement
- `/checkplayer <nick>` — full profile with punishment statistics (active/total per type)
- `/checkips <ip>` — alt account detection from IP history
- `/hide <joinleavemsg|afkmsg>` — per-player hiding of system messages
- `/togglenametag` — hide your nametag above your head (scoreboard teams with TAB API support)
- `auriongo.bypass.nametag` — see nametags of players who hid them

### Punishments
- `/ban`, `/kick`, `/mute`, `/warn` with duration parsing (e.g. `30d`, `12h`, `45m`, `10s`) and optional reason; permanent punishments supported
- Removal commands `/unban`, `/unmute`, `/unwarn` by nickname or punishment ID
- Paginated lists `/banlist`, `/mutelist`, `/warnlist` with hover details and interactive page navigation
- Nickname search `/banlistsearch`, `/warnlistsearch` (partial match)
- Global broadcasts on apply and remove with hoverable `[ПОДРОБНЕЕ]` details
- Ban screen shown on login attempt, kick screen on disconnect
- Notification when a banned player tries to join
- Mute blocks chat and a configurable list of commands (`me`, `msg`, `tell`, `m`, `w`, `r`, `do`, ...)
- Mute sync with Simple Voice Chat — muted players cannot talk in voice either

### Gamemode
- `/gamemode` / `/gm` plus shortcuts `/gmc`, `/gms`, `/gmsp`, `/gma`, with optional target player
- Staff notifications on gamemode changes (`auriongo.command.misc.gamemode.notify`)

### Player modes
- `/god [player]` — toggle invulnerability for yourself or another player
- `/fly [player]` — toggle flight for yourself or another player
- Staff notifications on god/fly changes (`auriongo.command.misc.playermode.notify`)

### Server monitor
- Toggleable scoreboard with TPS, MSPT, RAM, CPU, online players, and average ping
- Fully configurable title, lines, and sample/render intervals

### World switching
- `/world` — switch between two configured BungeeCord servers with pretty display names
- Cooldown, combat tag blocking (damage-based), connect delay, and 5-second lookup timeout
- Portal particles and teleport sound on switch
- World-switch chat message, join/quit messages suppressed on switch to avoid spam

### Integrations
- **PlaceholderAPI** — chat formats, join/quit messages, `%auriongo_afk%` expansion
- **Simple Voice Chat** — mute mirroring
- **SuperVanish** — vanished players stay hidden from local chat, AFK announcements, and local recipient checks
- **TAB** — nametag hiding via the TAB API
- **InteractiveChat** — chat compatibility hooks
- **DiscordSRV** — declared as a soft dependency (hooks reserved for future releases)

## Commands and permissions

Each command has exactly one permission node. **One command = one permission.**

### Chat

| Command | Permission | Default |
|---|---|---|
| `/msg`, `/tell`, `/m`, `/w` | `auriongo.command.chat.msg` | everyone |
| `/r` | `auriongo.command.chat.reply` | everyone |
| `/broadcast`, `/ac` | `auriongo.command.chat.broadcast` | op |
| `/spy` | `auriongo.command.chat.spy` | op |
| legacy colors in chat | `auriongo.command.chat.color` | op |
| MiniMessage tags in chat | `auriongo.command.chat.minimessage` | op |

### Player data

| Command | Permission | Default |
|---|---|---|
| `/checkplayer <nick>` | `auriongo.command.misc.checkplayer` | op |
| `/checkips <ip>` | `auriongo.command.misc.checkips` | op |
| `/hide <joinleavemsg\|afkmsg>` | `auriongo.command.misc.hide` | op |
| `/togglenametag` | `auriongo.command.misc.togglenametag` | op |
| see hidden nametags | `auriongo.bypass.nametag` | op |

### AFK and misc

| Command | Permission | Default |
|---|---|---|
| `/afk` | `auriongo.command.misc.afk` | everyone |
| `/auriongo reload` | `auriongo.command.misc.reload` | op |
| `/servermonitor` | `auriongo.command.misc.servermonitor` | op |
| `/world` | `auriongo.command.misc.world` | everyone |
| `/gamemode`, `/gm`, `/gmc`, `/gms`, `/gmsp`, `/gma` | `auriongo.command.misc.gamemode` | op |
| gamemode change notifications | `auriongo.command.misc.gamemode.notify` | op |
| `/god [player]` | `auriongo.command.misc.god` | op |
| `/fly [player]` | `auriongo.command.misc.fly` | op |
| god/fly change notifications | `auriongo.command.misc.playermode.notify` | op |

### Punishments

| Command | Permission | Default |
|---|---|---|
| `/ban` | `auriongo.command.punishment.ban` | op |
| `/banlist` | `auriongo.command.punishment.banlist` | op |
| `/banlistsearch <nick>` | `auriongo.command.punishment.banlist` | op |
| `/unban` | `auriongo.command.punishment.unban` | op |
| `/kick` | `auriongo.command.punishment.kick` | op |
| `/mute` | `auriongo.command.punishment.mute` | op |
| `/mutelist` | `auriongo.command.punishment.mutelist` | op |
| `/unmute` | `auriongo.command.punishment.unmute` | op |
| `/warn` | `auriongo.command.punishment.warn` | op |
| `/warnlist` | `auriongo.command.punishment.warnlist` | op |
| `/warnlistsearch <nick>` | `auriongo.command.punishment.warnlist` | op |
| `/unwarn` | `auriongo.command.punishment.unwarn` | op |

## Placeholders

| Placeholder | Description |
|---|---|
| `%auriongo_afk%` | `&6⌚` (configurable) when the player is AFK, empty string otherwise |

## Configuration

The plugin is built around configs. Every module can be toggled in `config.yml` under `modules`.

- `config.yml` — module toggles, storage backend, global theme palette
- `messages.yml` — all player-facing messages and visible formatting
- `chat.yml` — chat parsing, channels, join/quit messages, private-message sound
- `afk.yml` — auto-AFK idle time and the `%auriongo_afk%` indicator
- `punishments.yml` — broadcasts, list page size, mute settings (voice sync, blocked commands)
- `gamemodes.yml` — gamemode command settings
- `servermonitor.yml` — scoreboard title, lines, and update intervals
- `integrations.yml` — PlaceholderAPI and Simple Voice Chat hooks
- `world.yml` — switchable servers, display names, cooldown, combat block
- `warns.yml` — reserved for the upcoming warn escalation system

Theme colors are defined in `config.yml`:

```yml
theme:
  fcolors:
    "1": gradient:#ffffff:#ffffff
    "2": gradient:#4FD6FF:#D7F4FA
    "3": color:#A9A9A9
    "4": color:#FFFAFA
```

Swap them to solid colors or gradients, then reuse them in `messages.yml` with tags like `<fcolor>` or `<fcolor:2>`.

## Storage

- SQLite (default), MySQL, and MariaDB backends, selected in `config.yml`
- JDBC drivers are loaded by Paper from `plugin.yml` (`libraries`)
- Tables: `aurion_players`, `aurion_player_ip_history`, `aurion_punishments`

## Tech notes

- Platform: Paper `1.21.11`
- Java: `21`
- Build: Gradle 9.5.1 (wrapper included)

## Development

Run a local Paper test server with:

```bash
./gradlew runServer
```

Build the plugin with:

```bash
./gradlew build
```

## Status

AurionGo is still growing, but the current base is already usable for a real server. The codebase is organized in modules, so adding new systems later should stay manageable. The warn module is the next milestone — warn storage and escalation rules are coming.