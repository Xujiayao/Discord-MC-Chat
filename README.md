<div align="right">
Language: English <a href="/README_CN.md">中文</a>
</div>

<p align="center">
<img width=128 src="https://cdn.jsdelivr.net/gh/Xujiayao/MCDiscordChat@master/src/main/resources/assets/mcdiscordchat/icon.png">
</p>

# MCDiscordChat

[![License](https://img.shields.io/github/license/xujiayao/MCDiscordChat?logo=github)](https://github.com/Xujiayao/MCDiscordChat/blob/master/LICENSE)
[![Release](https://img.shields.io/github/v/release/xujiayao/MCDiscordChat?logo=github)](https://github.com/Xujiayao/MCDiscordChat/releases)
[![jsDelivr Hits](https://data.jsdelivr.com/v1/package/gh/Xujiayao/MCDiscordChat/badge?style=rounded)](https://www.jsdelivr.com/package/gh/Xujiayao/MCDiscordChat)
[![GitHub Downloads](https://img.shields.io/github/downloads/xujiayao/MCDiscordChat/total?logo=github)](https://github.com/Xujiayao/MCDiscordChat/releases)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/mcdiscordchat?label=modrinth%20downloads)](https://modrinth.com/mod/mcdiscordchat)
[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_mcdiscordchat_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/mcdiscordchat)
[![Versions Supported](https://cf.way2muchnoise.eu/versions/mcdiscordchat.svg)](https://www.curseforge.com/minecraft/mc-mods/mcdiscordchat)

MCDiscordChat (MCDC), a practical and powerful Fabric and Quilt Minecraft <> Discord chat bridge inspired by BRForgers/DisFabric

More information + Docs: [MCDiscordChat Docs | Xujiayao's Blog](https://blog.xujiayao.top/posts/4ba0a17a/)

## Introduction

[MCDiscordChat](https://github.com/Xujiayao/MCDiscordChat) (abbreviated as MCDC), a practical and powerful Fabric and Quilt Minecraft <> Discord chat bridge inspired by BRForgers/DisFabric.

![0001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/0001.png)

![001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/001.png)

## Download

All releases can be downloaded at the following sites:

- [Modrinth](https://modrinth.com/mod/mcdiscordchat/versions)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/mcdiscordchat/files)
- [GitHub](https://github.com/Xujiayao/MCDiscordChat/releases)

You can find the latest build JAR files at [GitHub Actions](https://github.com/Xujiayao/MCDiscordChat/actions).

## Support

If there is a bug or suggestion, or something you don't understand, you can [submit an issue](https://github.com/Xujiayao/MCDiscordChat/issues/new/choose) on GitHub.

## Contributing

**You are welcome to contribute to MCDC!**

If you are interested in contributing to MCDC, you can submit a pull request on GitHub.

For code contributions, the build file is located in the `/wrapper/build/libs/` folder.

For translation contributions, language files are located in the `/wrapper/src/main/resources/lang/` folder. Copy `en_us.json` and rename the new one to your language code to get started. Please provide translations for all keys in the entire file, except those keys beginning with the `death` prefix, which are the official Minecraft translations.

## Features

- Full multi-language support
- Support multi-server mode (multi-server operation on the same Discord channel)
- Minecraft <> Discord cross server chat
  - Support displaying server status using Discord channel topic feature
  - Support displaying server player count in bot activity status
  - Support Discord Webhook feature
    - Customizable Webhook Avatar API
    - Use the bot itself to send chat messages when Webhook URL is not filled
  - Support in-game Markdown parsing
  - Support highlighting and using default Unicode and server custom emoji in-game
  - Support highlighting stickers in-game
  - Support highlighting and mentions (@) in-game
    - Support disabling mentions (@) in-game
  - Support highlighting and opening hyperlinks and GIFs in-game
  - Support disabling all parsing
  - Support in-game display of Discord user role colour
  - Support in-game display of response messages
  - Support limiting the number of newlines for Discord messages
  - Broadcast player command execution
    - Players who execute commands can also see the commands themselves
    - Exclude broadcasts for execution of specific commands
  - Send all console log messages to Discord
- Discord Commands available
  - Use Discord to manage channels that can execute MCDC commands
  - Notify in-game players when someone executes an MCDC command
  - /console command supports Minecraft command auto-completion
  - Normal Commands
    - /info                    | Query server running status
    - /help                    | Get a list of available commands
    - /update                  | Check for update
    - /stats \<type\> \<name\> | Query the scoreboard of a statistic
  - Admin Commands
    - /reload                  | Reload MCDiscordChat config file (admin only)
    - /console \<command\>     | Execute a command in the server console (admin only)
    - /log                     | Get the specified server log (admin only)
    - /stop                    | Stop the server (admin only)
- Minecraft Commands available
  - Admin-only commands require a level 4 operator at minimum
  - Normal Commands
    - /mcdc info                    | Query server running status
    - /mcdc help                    | Get a list of available commands
    - /mcdc update                  | Check for update
    - /mcdc stats \<type\> \<name\> | Query the scoreboard of a statistic
  - Admin Commands
    - /mcdc reload                  | Reload MCDiscordChat config file (admin only)
- Fully customizable message format
  - Sending specific server messages to Discord can be disabled
  - In-game
    - Chat messages from Discord
    - Response messages from Discord
    - Messages from other servers
  - Discord
    - Server started
    - Server stopped
    - Player joined server
    - Player left server
    - Player reached a progress / achieved a goal / completed a challenge
    - Player died
    - Server MSPT is higher than a certain value
- Use admin list to configure user permissions to use special commands
- Support Hot Reloading of the config file
  - Backup every time the config file is loaded
- Check for updates regularly
  - Customizable update notification channel
  - Check for updates can be disabled

Languages currently available:

| Code | Language |
| ----- | ----- |
| `en_us` | English (US) |
| `zh_cn` | 中文（简体） |
| `ru_ru` | Русский (Россия) |

## Contributors

[![Contributors](https://contrib.rocks/image?repo=xujiayao/mcdiscordchat)](https://github.com/Xujiayao/mcdiscordchat/graphs/contributors)