<div align="center">

# Discord-MC-Chat (DMCC)

**A powerful, highly customizable bidirectional chat bridge between Discord and Minecraft.**

[Documentation](https://dmcc.xujiayao.com/) · [Modrinth](https://modrinth.com/mod/discord-mc-chat) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/discord-mc-chat) · [Discord](https://discord.gg/kbXkV6k2XU) · [中文说明 (README_CN)](README_CN.md)

</div>

> [!NOTE]
> **DMCC v3 (`3.0.0-beta.x`) is a complete rewrite** with a new "Server–Client" architecture, multi-server support, a zero-trust permission system, and many other new features. v3 is **not** configuration-compatible with v2 — the old single JSON config has been replaced by several **YAML** files. If you are migrating, do not copy your old config; generate fresh files and re-apply your settings.

---

## Table of Contents

- [What is DMCC?](#what-is-dmcc)
- [Requirements](#requirements)
- [Installation](#installation)
- [Discord Bot Setup](#discord-bot-setup)
- [First-Time Configuration Walkthrough](#first-time-configuration-walkthrough)
- [Configuration Files Reference](#configuration-files-reference)
- [Where Things Live (Quick Lookup)](#where-things-live-quick-lookup)
- [Message Formatting (`custom_messages`)](#message-formatting-custom_messages)
- [Key `config.yml` Sections Explained](#key-configyml-sections-explained)
- [Operation Modes](#operation-modes)
- [Commands](#commands)
- [Troubleshooting](#troubleshooting)
- [Building From Source](#building-from-source)
- [Architecture & Design Reference](#architecture--design-reference)
- [License](#license)

---

## What is DMCC?

Discord-MC-Chat (DMCC), formerly known as MC-Discord-Chat / MCDiscordChat (MCDC), is a server-side Fabric mod (and optional standalone application) that bridges chat and events between one or many Minecraft servers and a Discord server. It forwards chat in both directions, mirrors in-game events (joins, deaths, advancements, etc.), exposes Discord slash commands for server management, and supports an account-linking + permission system that maps Discord identities to Minecraft OP levels.

---

## Requirements

| Component | Requirement | Notes |
| :-- | :-- | :-- |
| **Minecraft** | `>= 26.1.0` | Tested on **26.2** and **26.1.2**. Other versions may work but are unverified. |
| **Mod loader** | **Fabric Loader** `>= 0.18.4` | NeoForge is planned but not yet supported. |
| **Java** | **Java 25** or newer | This is required — DMCC will not load on older Java versions. |
| **Side** | **Server only** | `"environment": "server"`. Do not install it on the client. |
| **Fabric API** | **Not required** | DMCC has no dependency on Fabric API. |

All other libraries (JDA, Jackson, OkHttp, Netty, jemoji, …) are **bundled inside the jar** (shaded). You do not need to install them separately.

---

## Installation

### As a Minecraft mod (modes `single_server` and `multi_server_client`)

1. Make sure your server runs **Fabric Loader 0.18.4+** on **Minecraft 26.1+** with **Java 25+**.
2. Download the DMCC jar from [Modrinth](https://modrinth.com/mod/discord-mc-chat), [CurseForge](https://www.curseforge.com/minecraft/mc-mods/discord-mc-chat), or [GitHub Releases](https://github.com/Xujiayao/Discord-MC-Chat/releases).
3. Place the jar in your server's `mods/` folder.
4. Start the server **once**. DMCC generates `config/discord_mc_chat/mode.yml` and then stops its own initialization, asking you to configure it (see [First-Time Configuration](#first-time-configuration-walkthrough)).

### As a standalone hub (mode `standalone`)

The standalone hub has **no Minecraft**; it is the central router for a multi-server setup.

1. Install **Java 25+**.
2. Download the **same** DMCC jar.
3. Run it **from a real terminal** (a console/TTY is required — double-clicking the jar is not supported):

   ```bash
   java -jar discord-mc-chat-<version>.jar
   ```

   Optional flag: `--disable-ascii` disables ANSI colors in the console output.
4. On first run it generates `config/discord_mc_chat/mode.yml` (already set to `standalone` if you run the standalone entry point) and exits. Edit the configuration, then start it again.

> [!TIP]
> The standalone process is interactive: it reads commands directly from its own terminal. Run it inside `screen`/`tmux` or as a service so it keeps a controlling terminal.

---

## Discord Bot Setup

DMCC talks to Discord through a bot you create. **Channels are matched by name** (case-insensitive) for text channels, and by **numeric ID** for the optional voice "dashboard" channels.

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications) → **New Application**.
2. Open the **Bot** tab and copy the **token** (used for `discord.bot.token`).
3. Under **Privileged Gateway Intents**, enable **all three** of the following — DMCC will fail to connect or behave correctly without them:
   - **Message Content Intent** (`MESSAGE_CONTENT`)
   - **Server Members Intent** (`GUILD_MEMBERS`)
   - **Presence Intent is _not_ required**, but message reactions are used (`GUILD_MESSAGE_REACTIONS`, not privileged).
4. Invite the bot to your server (OAuth2 → URL Generator → `bot` scope). Recommended permissions:
   - **View Channels**, **Send Messages**, **Read Message History**
   - **Manage Webhooks** — required for "fake-user style" messages; DMCC auto-creates a webhook named **`DMCC Webhook`** in the chat channels.
   - **Manage Channels** — only if you use channel **topic** updates or the voice-channel status dashboard.
5. Create the text channels you reference in the config (default name: `in-game-chat`, and `console` for console forwarding). The channel **names must match** the values in `config.yml`.

---

## First-Time Configuration Walkthrough

DMCC uses a **two-stage generate-then-edit** workflow. Files live under `config/discord_mc_chat/`.

1. **First start** → DMCC creates `mode.yml`.
2. **Edit `mode.yml`** and set `mode:` to one of `single_server`, `multi_server_client`, or `standalone`.
3. **Reload** (`/dmcc reload` in-game, or restart a standalone process). DMCC now generates the matching `config.yml` **and** the `custom_messages/<language>.yml` file.
4. **Edit `config.yml`** — at minimum set `discord.bot.token` and the channel names under `broadcasts.minecraft_to_discord`.
5. **Reload again** to apply your changes.

```text
First boot ──► mode.yml created
                   │ (set mode, reload)
                   ▼
            config.yml + custom_messages/<lang>.yml created
                   │ (set token & channels, reload)
                   ▼
              DMCC connects to Discord ✔
```

> [!IMPORTANT]
> - In **Minecraft modes**, a failed config load does **not** crash the server — fix the file and run `/dmcc reload`.
> - In **standalone mode**, the process exits on a failed/missing config — fix the file and start it again (there is no live reload from the host process for the initial bootstrap).
> - Do **not** edit `version:` or `mode:` at the top of generated files by hand.

---

## Configuration Files Reference

Everything DMCC reads or writes lives under **`config/discord_mc_chat/`** (relative to the server / process working directory):

```text
config/discord_mc_chat/
├── mode.yml                      # Selects the operating mode (you edit this first)
├── config.yml                    # Main configuration (generated to match the chosen mode)
├── custom_messages/
│   └── en_us.yml                 # ★ ALL message FORMATTING lives here (per language)
├── account_linking/
│   └── links.json                # Discord ↔ Minecraft link data (managed by DMCC)
└── cache/
    ├── lang/                     # Downloaded vanilla translations (advancements, deaths, …)
    └── log/                      # Standalone log transfer cache
```

| File | What it controls | Edit it? |
| :-- | :-- | :-- |
| `mode.yml` | Which of the three modes DMCC runs as. | ✅ Yes — set `mode:`. |
| `config.yml` | Bot token, channels, broadcasts, parsing toggles, console forwarding, MSPT alerts, channel/voice updating, account linking, OP mappings, per-command permission levels, update checks. | ✅ Yes — your main config. |
| `custom_messages/<lang>.yml` | **The text/JSON-text format of every message** (chat layout, colors, prefixes, embeds, event templates, topic strings, voice channel names, MSPT alert text, …). | ✅ Yes — this is where formatting lives. |
| `account_linking/links.json` | Stored link relationships, keyed by Discord ID. | ⚠️ Managed by DMCC; only edit while stopped if you know what you're doing. |
| `cache/` | Auto-managed caches. | ❌ No. |

> [!NOTE]
> `config.yml` is **mode-specific**. A `single_server`, `multi_server_client`, and `standalone` instance each generate a different `config.yml`. If you change the mode, regenerate the config (back up the old one first — DMCC will refuse to load a config whose `mode:` doesn't match `mode.yml`).

---

## Where Things Live (Quick Lookup)

A common point of confusion (especially coming from v2's single JSON file) is **which file** holds a given feature. Use this table:

| I want to change… | File | Key / location |
| :-- | :-- | :-- |
| **The look of chat messages** (colors, `<name>`, prefixes) | `custom_messages/<lang>.yml` | `xxxxx_to_minecraft`, `discord_to_minecraft`, `minecraft_to_discord` |
| **Join / leave / death / advancement text** | `custom_messages/<lang>.yml` | `minecraft_to_xxxxx.player.*`, `.server.*`, `.source.*` |
| **Channel topic / voice channel name text** | `custom_messages/<lang>.yml` | `channel_topic_updating`, `voice_channels_updating` |
| **MSPT warning wording / bot activity text** | `custom_messages/<lang>.yml` | `mspt_monitoring`, `activity` |
| **Which Discord channel receives each event** | `config.yml` | `broadcasts.minecraft_to_discord.*` (channel name; empty = disabled) |
| **Enable/disable a direction or event type** | `config.yml` | `broadcasts.*`, `message_parsing.*` |
| **Markdown / emoji / mention parsing toggles** | `config.yml` | `message_parsing.*` |
| **Bot token, webhook avatar, allowed mentions** | `config.yml` | `discord.*` |
| **Console forwarding & sensitive-data filtering** | `config.yml` | `console_forwarding.*` |
| **Hide private commands from being broadcast** | `config.yml` | `broadcasts.excluded_commands` |
| **Who can run which Discord command** | `config.yml` | `command_permission_levels.*` |
| **Map Discord roles/users to OP levels** | `config.yml` | `account_linking.op_sync.*` |
| **The language used** | `config.yml` | `language:` (must have a matching `custom_messages/<lang>.yml`) |

---

## Message Formatting (`custom_messages`)

> This section directly answers the most common complaint: *"I couldn't find where message formatting lives."* **It is not in `config.yml`.** It lives in `config/discord_mc_chat/custom_messages/<language>.yml`.

This file is generated the first time DMCC fully loads (after `config.yml` exists), copied from the built-in template for your `language`. Editing it lets you fully control how every message is rendered. Built-in languages: `en_us`, `zh_cn`.

> [!NOTE]
> `custom_messages` is **not loaded** in `multi_server_client` mode — a client doesn't talk to Discord, so formatting is handled by the `standalone`/`single_server` instance that owns the Discord connection.

### How the format is structured

Minecraft-bound messages are defined as a **list of text segments**, each with `text`, `bold`, and `color`. They are concatenated to build a single Minecraft chat line. Discord-bound messages are plain strings that support **Discord Markdown** and `:emoji:` shortcodes.

Example (Discord → Minecraft user message):

```yaml
xxxxx_to_minecraft:                # applies to discord→mc and mc→mc
  user_message:
    - text: "[{server}] "
      bold: true
      color: "{server_color}"
    - text: "<{effective_name}> "
      bold: false
      color: "{role_color}"
    - text: "{message}"
      bold: false
      color: "gray"
```

Example (Minecraft → Discord event strings, with Markdown + emoji):

```yaml
minecraft_to_xxxxx:
  server:
    start: ":white_check_mark: **Server started!**"
  player:
    join: ":wave: **{display_name} joined the server**"
    die: ":skull: **{death_message}**"
```

### Top-level groups in `custom_messages`

| Group | Direction | Purpose |
| :-- | :-- | :-- |
| `xxxxx_to_minecraft` | →MC | Shared layout for Discord→MC and MC→MC chat (`user_message`, `system_message`, `mentioned`). |
| `overwrite` | →MC | Layout used when `overwrite_minecraft_source_messages` is on (per `single_server` / `standalone`). |
| `discord_to_minecraft` | D→MC | Reply quotes, executed-command, reaction, edit/delete notifications. |
| `minecraft_to_xxxxx` | MC→ | **System** event templates: server start/stop, join/quit, death, advancement (task/challenge/goal), game-mode change, `/me`. |
| `minecraft_to_discord` | MC→D | **User** chat formatting for Discord, incl. fake-user webhook username/content. |
| `activity` | →D | Bot "Playing …" activity text. |
| `channel_topic_updating` | →D | Text channel topic strings (online/offline). |
| `voice_channels_updating` | →D | Voice channel status & player-count names. |
| `mspt_monitoring` | →D | MSPT threshold warning / recovery messages. |
| `console_forwarding` | →D | "Started/stopped forwarding console" notices. |

### Placeholders

Placeholders are written as `{name}` and are substituted at runtime. The most useful ones:

| Placeholder | Meaning |
| :-- | :-- |
| `{message}` | The message body. |
| `{effective_name}` / `{display_name}` | Discord display name / Minecraft display name. |
| `{server}`, `{server_color}` | Sub-server name and its configured color (multi-server). |
| `{role_color}` | Color of the user's highest Discord role. |
| `{command}` | The executed command. |
| `{emoji}` | The reaction emoji. |
| `{death_message}` | Vanilla death message text. |
| `{title}`, `{description}` | Advancement title / description. |
| `{mode}` | New game mode (game-mode change event). |
| `{action}` | The `/me` action text. |
| `{player_name}` | Used by `discord.webhook.avatar_url` to build the head URL. |
| `{online_player_count}`, `{max_player_count}` | Player counts. |
| `{players_ever_joined}`, `{online_server_count}`, `{online_server_list}` | Aggregate stats (topic strings). |
| `{server_started_time}`, `{last_update_time}`, `{next_check_time}` | Unix timestamps (use Discord `<t:...:f>` styling). |
| `{mspt}`, `{threshold}` | Current MSPT and the configured threshold. |

**Colors** accept any vanilla Minecraft color name (`white`, `gray`, `dark_gray`, `blue`, `yellow`, `green`, …) or the dynamic placeholders `{server_color}` / `{role_color}`.

---

## Key `config.yml` Sections Explained

The exact keys differ slightly per mode; below covers `single_server` (the most common). See the generated file's inline comments for the authoritative, mode-specific layout.

### `discord`
- `bot.token` — your bot token (**required**).
- `bot.enable_status` / `enable_activity` — bot presence (Online/Idle/DND by player count) and "Playing …" activity.
- `webhook.enable_fake_user_style` — send player chat via webhooks so each message shows the player's name/avatar.
- `webhook.avatar_url` — avatar URL template, e.g. `https://mc-heads.net/avatar/{player_name}`.
- `allow_mentions` — which mention types (`everyone`, `users`, `roles`) DMCC is allowed to ping.

### `broadcasts`
- `discord_to_minecraft.*` — `true`/`false` toggles for forwarding chat, reactions, edits, deletes, and command notices into Minecraft.
- `minecraft_to_discord.*` — for each event, the **Discord channel name** to post to; **leave empty to disable** that event. Events include `server.started/stopped`, `player.join/quit/chat/command/die/advancement/change_game_mode`, and `source.say/tell_raw/msg/me` (intercepted vanilla broadcasts).
- `excluded_commands` — **regex** list of commands that must **not** be broadcast (e.g. `/login`, `/msg` private messages). The defaults already block common auth/private commands.
- `echo_player_command_to_source` / `echo_player_change_game_mode_to_source` — echo the parsed message back to the player who triggered it.

### `message_parsing`
Fine-grained on/off switches for rich-content parsing in each direction (`discord_to_minecraft`, `minecraft_to_discord`, `minecraft_to_minecraft`): markdown, attachments, stickers, unicode/custom emojis, mentions, hyperlinks, embeds, components, timestamps, ANSI code blocks, polls.
- `overwrite_minecraft_source_messages` — when `true`, DMCC **cancels** the vanilla message and re-broadcasts its own formatted version (using the `overwrite` block in `custom_messages`).

### `mspt_monitoring`
Periodically samples server MSPT and warns in `channel` when it stays above `threshold`. Tune `interval_seconds` and `threshold`.

### `console_forwarding`
- `enable` — stream `latest.log` to a Discord `channel` (default name `console`).
- `execute_messages_from_channel` — treat messages typed in that channel as console commands (**powerful — restrict who can post there**).
- `filter_regex` — regex list to redact sensitive data (IP addresses by default).

### `channel_updating`
- `channel_topic_updating` — periodically rewrite the topic of listed text channels.
- `voice_channel_updating` — rewrite voice channel **names** (by **numeric ID** in `server_status_channel_id` / `player_count_channel_id`) as a live status dashboard.

### `account_linking` (always enabled)
- `op_sync.sync_op_level_to_minecraft` — when `true`, DMCC becomes the source of truth and force-syncs in-game OP based on Discord identity.
- `op_sync.user_mappings` / `role_mappings` — map a Discord user/role to an OP level (`-1`–`4`). The **highest** match wins. In multi-server configs, entries can carry `server_overrides`.
- `mention_notifications.style` — how cross-platform `@` pings appear in-game: `action_bar`, `title`, or `chat`.
- `use_discord_role_color_for_mc_chats` — color in-game names with the linked Discord role color.

### `command_permission_levels`
Minimum OP level required to run each Discord command. `-1` = anyone (even unlinked). See the [Commands](#commands) table for defaults.

### `check_for_updates` / `shutdown`
- `check_for_updates` — announce new DMCC versions to a channel.
- `shutdown.graceful_shutdown` — perform a clean shutdown sequence.

---

## Operation Modes

Set in `mode.yml`. For full details see [§3.2 in the reference](#32-operation-modes-and-deployment).

| Mode | Connects to Discord? | Runs Minecraft? | Use when |
| :-- | :--: | :--: | :-- |
| `single_server` | ✅ | ✅ | The standard all-in-one setup. **Most users want this.** |
| `multi_server_client` | ❌ | ✅ | A sub-server that reports to a central `standalone` hub. |
| `standalone` | ✅ | ❌ | The central hub aggregating multiple `multi_server_client` servers. |

For multi-server setups, the `standalone` config defines a `multi_server` block (host/port/`shared_secret` and the allowed `servers` whitelist). Each client's `mode.yml`→`config.yml` provides its `server_name` and the matching connection details.

---

## Commands

Discord slash commands and their default required OP level (configurable via `command_permission_levels`). In-game, the mod also registers `/dmcc <help|info|reload|stats|link|unlink|update>`. The full per-mode availability table is in [§7 of the reference](#7-command-list-and-permission-reference).

---

## Troubleshooting

| Symptom | Likely cause & fix |
| :-- | :-- |
| **Mod doesn't load / `UnsupportedClassVersionError`** | You're on Java < 25. Install **Java 25+** and point the server at it. |
| **Server starts but DMCC says it created `mode.yml` and stopped** | Expected on first run. Edit `mode.yml`, then `/dmcc reload`. |
| **"mode mismatch" / config refuses to load** | `mode:` in `config.yml` ≠ `mode:` in `mode.yml`. Back up and regenerate `config.yml` for the correct mode. |
| **Bot won't connect / no messages** | Check `discord.bot.token`; ensure **Message Content** and **Server Members** privileged intents are enabled in the Developer Portal. |
| **Messages reach Discord but not the right channel** | Channel is matched **by name**. Make sure the text channel name exactly matches the value in `broadcasts.minecraft_to_discord.*` (and `console`, etc.). |
| **No player avatars / "fake user" style not working** | The bot needs **Manage Webhooks**; DMCC creates a `DMCC Webhook` in the channel. |
| **Voice channel dashboard not updating** | Voice channels use **numeric IDs** (not names). Set `server_status_channel_id` / `player_count_channel_id`, and grant **Manage Channels**. |
| **I edited formatting in `config.yml` but nothing changed** | Message formatting is in `custom_messages/<lang>.yml`, **not** `config.yml`. Edit there, then `/dmcc reload`. |
| **Private commands (e.g. `/login`) appear in Discord** | Add/adjust a regex in `broadcasts.excluded_commands`. |
| **A change to `config.yml` didn't apply** | Run `/dmcc reload` (Minecraft) or restart the `standalone` process. |
| **Standalone won't start / "headless not supported"** | Run it from a real terminal (`java -jar …`), not by double-clicking. Use `screen`/`tmux` or a service. |
| **Players can't join (whitelist) so can't link** | Have them get a base role (mapped to OP 0) and run `/whitelist <name>` on Discord, then join and link normally. |

If you're still stuck, ask in the [Discord server](https://discord.gg/kbXkV6k2XU) or open an issue with your `mode.yml`, the relevant parts of `config.yml`, and the server log.

---

## Building From Source

Requires **JDK 25**.

```bash
git clone https://github.com/Xujiayao/Discord-MC-Chat.git
cd Discord-MC-Chat
./gradlew build        # use gradlew.bat on Windows
```

The project is multi-module: `:core` (platform-agnostic Server/Client logic, no `net.minecraft` imports) and `:minecraft` (the Fabric mod, integrating via Mixins). Built artifacts are produced by the Gradle Shadow plugin.

---

# Architecture & Design Reference

> The sections below document the internal design and behavior of DMCC v3. They are useful for advanced configuration and contributors.

## 1. Project Overview

Discord-MC-Chat (DMCC) is a Minecraft mod designed to establish a powerful, highly customizable bidirectional communication bridge between a Discord server and some Minecraft servers.

The core goal of this v3 refactoring is to implement a **unified communication architecture based on "Server-Client"**. Under this architecture, all operation modes reuse the same core logic to achieve maximum code reuse, architectural consistency, and future extensibility.

In the early stage, the project prioritizes compatibility with the **Fabric** loader on **Minecraft 26.1+** (tested on 26.2 and 26.1.2), running on **Java 25**. However, to seamlessly support other loaders like NeoForge in the future, the overall architecture design strictly follows platform-agnostic principles. All core code **must not contain any loader-specific calls**, and injection is performed exclusively via Mixins.

## 2. Core Feature Requirements

### 2.1 Bidirectional Communication and Format Handling

- **Omnidirectional message forwarding**: Forward normal chat between Discord and Minecraft in real time. Furthermore, the system intercepts and forwards vanilla system broadcasts (such as `/say` and `/tellraw @a`), ensuring Discord players never miss any in-game public events.
- **Player command broadcasting and filtering**: Supports broadcasting commands executed by players in-game to Discord. A built-in powerful regex filter list (`excluded_commands`) automatically blocks private commands (e.g., `/login`, `/msg`) to ensure server and player safety.
- **Rich text and media parsing**: Supports automatic bidirectional parsing and rendering of Markdown, Discord emojis, Unicode emojis, `@` mentions, images, GIFs, and hyperlinks between the two platforms.
- **Pseudo-user Webhook support**: Optionally send Minecraft messages via Webhooks to perfectly present a player's individual avatar and in-game name on the Discord side.
- **Custom formatting and internationalization**: Highly customize display formats for all messages (based on YAML templates). Additionally, the system has a built-in dynamic translation fetching mechanism that automatically retrieves language assets from official sources, accurately translating advancements, statistics, and death messages.

### 2.2 Status Synchronization and Event Notifications

- **Player and server events**: Broadcast player join/leave, death, advancement/achievement, and server start/stop events in real time.
- **Channel Monitor**:
    - **Text Channel Topic**: Dynamically update the channel topic periodically (e.g., online player count, total historical players, sub-server status).
    - **Voice Channel Name**: Dynamically update specified voice channel names to serve as an intuitive dashboard for server status (Status: Online) and player count (Players: X/Y).
- **Bot Status Display**: Synchronize and update the Discord bot's activity and online status in real time based on in-game player counts and status.
- **Performance Alerts**: Periodically monitor server MSPT (milliseconds per tick), and automatically issue a warning in the Discord channel when a set threshold is exceeded.
- **Auto Update Check**: Automatically verify the DMCC version and compare compatibility, sending update logs to a specified channel when a new version is found.

### 2.3 In-Depth Management and Control Features

- **Bidirectional Console Log Stream (Console Forwarding)**:
    - **Real-time push**: A dedicated background thread incrementally slices `latest.log` in real time and pushes it to a designated Discord console channel.
    - **Channel Direct Terminal**: Administrators with permission can directly send text in this Discord console channel, and the system will treat it as a console command executed directly in-game.
    - **Sensitive Information Filtering**: Considering some may choose to make this Discord channel public, an optional (but enabled by default) sensitive information filter (e.g., IP addresses) is provided to ensure security.
- **Discord-side slash commands**: Provide management commands such as `/info`, `/stats`, `/log`, `/whitelist`. Supports viewing complete log files (packaged transfer) and scoreboard leaderboards via Discord.
- **System reload**: Dynamically reload DMCC configuration files (`/dmcc reload` or Discord `/reload`).
- **Account binding governance**: Provide full account binding management commands (`link` / `unlink` / `links`), supporting cross-side co-authentication.

## 3. System Architecture

### 3.1 Unified "Server-Client" Architecture

All DMCC operation modes are based on a unified communication model consisting of two core components:

1. **Server**: The "brain" and central router of the entire system. It runs as a background service and is **the only** component responsible for direct communication with the Discord API (via JDA). It handles all cross-server routing, identity resolution, and credential issuance. **This component must not contain any `net.minecraft` imports (except via reflection)**.
2. **Client**: The "tentacle" deployed on each Minecraft server. It is responsible for capturing all in-game events and sending them to the Server, and receiving instructions from the Server (executing local commands and delegated authentication).

The two communicate securely over a Netty-based TCP protocol (shared secret and one-time hash challenge authentication).

### 3.2 Operation Modes and Deployment

1. **Single Server Mode (`single_server`)**: Starts both an internal Server and internal Client in the same JVM, connecting directly to Discord. Recommended for the vast majority of regular server owners.
2. **Multi-Server Client Mode (`multi_server_client`)**: Starts only the Client, does not connect to the Discord API, but connects as a sub-server to an externally running independent DMCC Server.
3. **Standalone Mode (`standalone`)**: Starts only the Server (no Minecraft process), acting as the central hub of a multi-server architecture, aggregating messages and statuses from various sub-servers and exchanging data with Discord.

## 4. Account Linking System

The account linking system is the data bus connecting a stateless Discord community and a stateful Minecraft world, and is also the cornerstone of DMCC's permission system.

### 4.1 Asymmetric Mapping Relationship

- **One Discord account can be linked to multiple Minecraft accounts** (allowing players to manage main accounts and alt accounts).
- **One Minecraft account can only be linked to one Discord account** (ensuring absolute uniqueness of in-game identity).
- **Data Persistence**: Binding relationships are stored as permanent data in `account_linking/links.json` on the `Server` side, using the Discord ID as the primary key. During queries, Discord display names and premium player names are resolved dynamically in real time via the API, while offline players retain the snapshot from the time of binding.

### 4.2 Secure Binding Workflow (Strict MC-First Principle)

To prevent identity spoofing on premium/cracked servers, **entering a game name directly on the Discord side to bind is strictly prohibited**.

1. **Auto-check on player join**: Every time a player joins a Minecraft server, the Client requests the Server via a network packet to check if their UUID is already bound.
2. **Automatic credential generation**: If not bound, the Server generates a 6-digit temporary verification code (e.g., `A7X9P2`). The Client sends a message in-game containing an inline clickable element instructing the player to use `/link A7X9P2` on Discord. The code is valid for 5 minutes.
3. **Manual credential refresh**: The Minecraft end provides the `/dmcc link` command to renew or regenerate the verification code.
4. **Confirm ownership**: The player goes to Discord and uses the slash command `/link A7X9P2` to complete the final binding.

### 4.3 Cross-Platform Interaction Feedback

- **Synced Name Color**: A player's in-game chat name color is automatically synchronized to the highest role color of their bound Discord account.
- **Cross-Platform Mention Alerts**: When cross-platform mentions (`@`) occur, the corresponding player is automatically highlighted in-game via Action Bar, Title, or Chat.

## 5. Zero-Trust Delegated Authorization System

DMCC v3 completely abandons hard-coding command permission checks on the Discord side, adopting a microservice security architecture of **"Identity Mapping + Edge Delegated Authorization"**.

### 5.1 Extended Permission Baseline (-1 to 4)

All permissions align with the native Minecraft OP levels, extended downward:

- **`-1` Level**: Any Discord user (including unlinked guests). Suitable for harmless query commands (e.g., `help`, `info`).
- **`0` Level**: Basic linked player, or an unlinked user granted a basic trust identity, corresponding to Minecraft native OP level 0.
- **`1 ~ 4` Levels**: Correspond to Minecraft native OP levels 1-4.

### 5.2 Identity and OP Mappings

A Discord user's identity will be settled into a specific **OP level credential** on the Server side based on the following rules:

1. **`user_mappings`**: Assign OP level based on a specific Discord User ID (highest priority).
2. **`role_mappings`**: Iterate over the user's Discord roles, taking the highest mapped OP level.
3. **Linked Account Fallback**: If the user has a linked Minecraft player, they are guaranteed an OP level of 0.

*(Note: The system takes the highest OP level matched from the above conditions as the final credential for that request.)*

### 5.3 Core Routing and Delegated Authorization

When a Discord user initiates a command (e.g., `/reload` or `/execute SMP reload`):

1. **The Server does not intercept business logic**. It only calculates the user's OP credential (e.g., OP=2), packages the "command content" together with the "credential tag", and sends it to the corresponding target Client via Netty.
2. **Client Edge Authorization**: Upon receiving the request, the target Client reads the security threshold set in its local `config.yml` (e.g., `reload: 4`), finds the credential (OP 2) is insufficient, and the Minecraft client directly rejects the request.
3. **Dynamic UI**: Parameter auto-completion for Discord-side slash commands also attaches OP credentials to requests sent to the Client. The Client only returns completion items that the credential is authorized to view (e.g., auto-completing system filenames only for administrators).

### 5.4 Elegantly Resolving the Whitelist Catch-22

A player cannot join the server due to the whitelist -> cannot prove identity binding -> cannot be added to the whitelist.
**Solution**:
DMCC provides an independent `whitelist` proxy command on the Client side (default required permission is level `0`). After a player joins the Discord channel and obtains a base role (mapped to OP 0), they can execute `/whitelist <their ID>` on Discord to be pre-whitelisted, then go through the normal binding process after joining the server.

### 5.5 Minecraft OP Force Sync Mechanism (`sync_op_level_to_minecraft`)

When this is enabled, DMCC becomes the **Single Source of Truth (SSOT)** for server permissions:

- Each sync performs a "full recomputation + forced overwrite".
- Discord identity mappings will be hard-written into the server's native OP list; any native `/op` grants manually executed by an admin in-game will be flushed and overwritten by DMCC according to the configuration during the next sync.
- Unlinking an account triggers automatic OP revocation and demotion.

## 6. Multi-Server Configuration Model (Standalone + Multi Server)

When DMCC is in the `standalone + multi_server_client` architecture, different sub-servers can use different OP mapping strategies.

In the `user_mappings` and `role_mappings` of the `standalone` configuration, each entry contains a top-level `op_level` (used for the Standalone's own queries) and a `server_overrides` list dictionary. If there is no corresponding entry in `server_overrides` for a particular sub-server, that sub-server automatically falls back to using the top-level `op_level` as the default.

## 7. Command List and Permission Reference

| Command                  | Default OP Level | Mod (multi_server_client) | Mod (single_server) | Standalone | Description                                                                                                       |
|:-------------------------|:-----------------|:--------------------------|:--------------------|:-----------|:------------------------------------------------------------------------------------------------------------------|
| `console <command>`      | `0`              | ❌                         | ✅                   | ❌          | Execute any command via Discord simulating the console.                                                           |
| `console <at> <command>` | `0`              | ❌                         | ❌                   | ✅          | Execute any command via Discord simulating a remote sub-server console.                                           |
| `execute <at> <command>` | `-1`             | ❌                         | ❌                   | ✅          | Remote execution hub, only exists in Standalone. Delegates the request along with auth credentials to the Client. |
| `help`                   | `-1`             | ✅                         | ✅                   | ✅          | Dynamically display available commands that the user is authorized to execute.                                    |
| `info`                   | `-1`             | ✅                         | ✅                   | ✅          | View status. Client shows only its own data, Server aggregates global data.                                       |
| `link`                   | `0`              | ✅                         | ✅                   | ❌          | Minecraft side generates or refreshes a 6-digit verification code.                                                |
| `link <code>`            | `0`              | ❌                         | ✅                   | ✅          | Discord side uses the verification code to complete binding.                                                      |
| `links`                  | `4`              | ❌                         | ✅                   | ✅          | Admin queries all linked account relationship data.                                                               |
| `log <file>`             | `4`              | ✅                         | ✅                   | ✅          | Package and transfer the specified background log file to Discord, supports auto-completion.                      |
| `reload`                 | `4`              | ✅                         | ✅                   | ✅          | Reload the DMCC configuration.                                                                                    |
| `shutdown`               | `4`              | ❌                         | ❌                   | ✅          | Used only to safely shut down the Standalone central process.                                                     |
| `stats <type> <stat>`    | `-1`             | ✅                         | ✅                   | ❌          | View scoreboard and statistics. Supports dynamic auto-completion filtered by permission.                          |
| `unlink`                 | `0`              | ✅                         | ✅                   | ✅          | Unlink the current MC player / all associated players of the current Discord user.                                |
| `update`                 | `-1`             | ✅                         | ✅                   | ✅          | Check for DMCC updates.                                                                                           |
| `whitelist <player>`     | `0`              | ✅                         | ✅                   | ❌          | DMCC-specific low-permission whitelist command proxy.                                                             |

> v3 removes the `/stop` shortcut command provided in v2. If you need to shut down the Minecraft server from Discord, an administrator with level 4 permission should directly use `/console stop` to simulate a safe shutdown via the console.

---

## License

Discord-MC-Chat is released under the [MIT License](LICENSE).
