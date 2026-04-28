# Discord-MC-Chat (DMCC) Design Document (v3)

## 1. Project Overview

Discord-MC-Chat (DMCC) is a Minecraft mod designed to establish a powerful, highly customizable bidirectional communication bridge between a Discord server and some Minecraft servers.

The core goal of this v3 refactoring is to implement a **unified communication architecture based on "Server-Client"**. Under this architecture, all operation modes reuse the same core logic to achieve maximum code reuse, architectural consistency, and future extensibility.

In the early stage, the project prioritizes compatibility with **Fabric 26.1.2**. However, to seamlessly support other loaders like NeoForge in the future, the overall architecture design strictly follows platform-agnostic principles. All core code **must not contain any loader-specific calls**, and injection is performed exclusively via Mixins.

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
