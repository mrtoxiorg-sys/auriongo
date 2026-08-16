# AurionGo

AurionGo is a custom Paper plugin built for the Aurion server.

It focuses on the parts players and staff touch every day: chat, moderation, player data, and clean config-driven formatting. The goal is simple. Keep the plugin practical, easy to tune, and free from hardcoded message spam.

## What it does

- Chat formatting with MiniMessage, legacy colors, PlaceholderAPI support, local and global channels, `/msg`, and `/r`
- Player moderation tools like `/ban`, `/kick`, `/mute`, `/warn`, list commands, and removal commands with global broadcasts
- Server monitor scoreboard with TPS, MSPT, CPU, RAM, online, and ping
- Voice mute sync with Simple Voice Chat, so muted players cannot talk in voice either
- Player profile storage with UUID, nickname, IP, first join, last join, mute state, ban state, and warn count
- Custom gamemode commands like `/gamemode`, `/gmc`, `/gms`, `/gmsp`, and `/gma`
- Centralized localization in one `messages.yml` file
- Configurable theme colors through `<fcolor>`, `<fcolor:1>`, `<fcolor:2>`, and more

## Tech notes

- Platform: Paper `1.21.11`
- Java: `21`
- Storage: `SQLite`, `MySQL`, `MariaDB`
- Runtime JDBC libraries are loaded by Paper from `plugin.yml`

## Configuration

The plugin is built around configs.

- `config.yml` — controls modules, storage, and the global theme palette
- `messages.yml` — contains all player-facing messages and most visible formatting
- `chat.yml` — contains chat logic and routing settings
- `punishments.yml` — contains punishment logic, toggles, and blocked commands
- `gamemodes.yml` — gamemode command settings
- `servermonitor.yml` — scoreboard layout and update intervals
- `integrations.yml` — controls PlaceholderAPI and Simple Voice Chat hooks

Theme colors are defined in `config.yml`:

```yml
theme:
  fcolors:
    "1": "color:#4FD6FF"
    "2": "color:#4FFF88"
    "3": "color:#FF4F4F"
```

You can swap them to solid colors or gradients, then reuse them in `messages.yml` with tags like `<fcolor>` or `<fcolor:2>`.

## Commands and permissions

Each command has exactly one permission node. **One command = one permission.**

### Chat

| Command | Permission |
|---|---|
| `/msg`, `/tell`, `/m`, `/w` | `auriongo.command.chat.msg` |
| `/r` | `auriongo.command.chat.reply` |
| (legacy colors) | `auriongo.command.chat.color` |
| (MiniMessage tags) | `auriongo.command.chat.minimessage` |

### Misc

| Command | Permission |
|---|---|
| `/auriongo reload` | `auriongo.command.misc.reload` |
| `/checkplayer` | `auriongo.command.misc.checkplayer` |
| `/servermonitor` | `auriongo.command.misc.servermonitor` |
| `/gamemode`, `/gm`, `/gmc`, `/gms`, `/gmsp`, `/gma` | `auriongo.command.misc.gamemode` |
| (gamemode notifications) | `auriongo.command.misc.gamemode.notify` |

### Punishment

| Command | Permission |
|---|---|
| `/ban` | `auriongo.command.punishment.ban` |
| `/banlist` | `auriongo.command.punishment.banlist` |
| `/unban` | `auriongo.command.punishment.unban` |
| `/kick` | `auriongo.command.punishment.kick` |
| `/mute` | `auriongo.command.punishment.mute` |
| `/mutelist` | `auriongo.command.punishment.mutelist` |
| `/unmute` | `auriongo.command.punishment.unmute` |
| `/warn` | `auriongo.command.punishment.warn` |
| `/warnlist` | `auriongo.command.punishment.warnlist` |
| `/unwarn` | `auriongo.command.punishment.unwarn` |

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

AurionGo is still growing, but the current base is already usable for a real server. The codebase is organized in modules, so adding new systems later should stay manageable.
