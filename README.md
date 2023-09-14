<div align="right">
Language: English <a href="/README_CN.md">中文</a>
</div>

<p align="center">
<img width=128 src="https://cdn.jsdelivr.net/gh/Xujiayao/MC-Discord-Chat@master/src/main/resources/assets/mcdiscordchat/icon.png">
</p>

# MC-Discord-Chat

[![License](https://img.shields.io/github/license/xujiayao/MC-Discord-Chat?logo=github)](https://github.com/Xujiayao/MC-Discord-Chat/blob/master/LICENSE)
[![Release](https://img.shields.io/github/v/release/xujiayao/MC-Discord-Chat?logo=github)](https://github.com/Xujiayao/MC-Discord-Chat/releases)
[![jsDelivr Hits (Old)](https://data.jsdelivr.com/v1/package/gh/Xujiayao/MCDiscordChat/badge?style=rounded)](https://www.jsdelivr.com/package/gh/Xujiayao/MCDiscordChat)
[![jsDelivr Hits (New)](https://data.jsdelivr.com/v1/package/gh/Xujiayao/MC-Discord-Chat/badge?style=rounded)](https://www.jsdelivr.com/package/gh/Xujiayao/MC-Discord-Chat)
[![GitHub Downloads](https://img.shields.io/github/downloads/xujiayao/MC-Discord-Chat/total?logo=github)](https://github.com/Xujiayao/MC-Discord-Chat/releases)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/mcdiscordchat?label=modrinth%20downloads)](https://modrinth.com/mod/mcdiscordchat)
[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_548539_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/mcdiscordchat)
[![Versions Supported](https://cf.way2muchnoise.eu/versions/548539.svg)](https://www.curseforge.com/minecraft/mc-mods/mcdiscordchat)

MC-Discord-Chat (MCDC), formerly known as MCDiscordChat, a practical and powerful Fabric and Quilt Minecraft <> Discord chat bridge inspired by BRForgers/DisFabric

More information + Docs: [MC-Discord-Chat Docs | Xujiayao's Blog](https://blog.xujiayao.top/posts/4ba0a17a/)

## Introduction

[MC-Discord-Chat](https://github.com/Xujiayao/MC-Discord-Chat) (MCDC), formerly known as MCDiscordChat, a practical and powerful Fabric and Quilt Minecraft <> Discord chat bridge inspired by BRForgers/DisFabric.

![0001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/0001.png)

![001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/001.png)

## Download

All releases can be downloaded at the following sites:

- [Modrinth](https://modrinth.com/mod/mcdiscordchat/versions)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/mcdiscordchat/files)
- [GitHub](https://github.com/Xujiayao/MC-Discord-Chat/releases)

You can find the latest build JAR files at [GitHub Actions](https://github.com/Xujiayao/MC-Discord-Chat/actions).

## Support

If there is a bug or suggestion, or something you don't understand, you can [submit an issue](https://github.com/Xujiayao/MC-Discord-Chat/issues/new/choose) on GitHub.

## Contributing

**You are welcome to contribute to MCDC!**

If you are interested in contributing to MCDC, you can submit a pull request on GitHub.

For code contributions, the build file is located in the `/wrapper/build/libs/` folder.

For translation contributions, language files are located in the `/wrapper/src/main/resources/lang/` folder. Copy `en_us.json` and rename the new one to your language code to get started. Please provide translations for the entire file for all keys except those starting with the `advancements` and `death` prefixes. Those messages are official translations of Minecraft, which may sync from the official translations at any time.

## Features

- Full multi-language support
- Support multi-server mode (multi-server operation on the same Discord channel)
- Minecraft <> Discord cross server chat
  - Support disabling Discord and in-game chat broadcasts
  - Support displaying server status using Discord channel topic feature
  - Support displaying server player count in bot activity status
  - Support Discord Webhook feature
    - Dynamic-created MCDC Webhook
    - Customizable Webhook Avatar API
    - Use the bot itself to send chat messages when Webhook is disabled
  - Support in-game Markdown parsing
  - Support highlighting and using default Unicode and server custom emoji in-game
  - Support highlighting stickers in-game
  - Support highlighting and mentions (@) in-game
    - Customizable scope of allowed mentions (@) in-game
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
    - /help                    | Get a list of available commands
    - /info                    | Query server running status
    - /stats \<type\> \<name\> | Query the scoreboard of a statistic
    - /update                  | Check for update
    - /whitelist \<player\>    | Add a player to the server whitelist
  - Admin Commands
    - /console \<command\>     | Execute a command in the server console (admin only)
    - /log \<file\>            | Get the specified server log (admin only)
    - /reload                  | Reload MC-Discord-Chat config file (admin only)
    - /stop                    | Stop the server (admin only)
- Minecraft Commands available
  - Admin-only commands require a level 4 operator at minimum
  - Normal Commands
    - /mcdc help                    | Get a list of available commands
    - /mcdc info                    | Query server running status
    - /mcdc stats \<type\> \<name\> | Query the scoreboard of a statistic
    - /mcdc update                  | Check for update
    - /mcdc whitelist \<player\>    | Add a player to the server whitelist
  - Admin Commands
    - /mcdc reload                  | Reload MC-Discord-Chat config file (admin only)
- Fully customizable message format
  - Sending specific server messages to Discord can be disabled
  - In-game
    - Chat messages from Discord
    - Response messages from Discord
    - Messages from other MCDC servers
  - Discord
    - Server started
    - Server stopped
    - Player joined server
    - Player left server
    - Player reached a progress / achieved a goal / completed a challenge
      - Multilingual support
      - Support displaying descriptions for task/challenge/goal messages
    - Player died
      - Multilingual support
    - Server MSPT is higher than a certain value
- Use admin list to configure user permissions to use special commands
- Support Hot Reloading of the config file
  - Backup every time the config file is loaded
- Check for updates regularly
  - Customizable update notification channel
  - Check for updates can be disabled

Languages currently available:

| Code    | Language             |
|---------|----------------------|
| `en_us` | English (US)         |
| `fr_fr` | Français (France)    |
| `ko_kr` | 한국어（대한민국）            |
| `no_no` | Norsk Bokmål (Norge) |
| `pl_pl` | Polski (Polska)      |
| `ru_ru` | Русский (Россия)     |
| `zh_cn` | 简体中文（中国大陆）           |
| `zh_hk` | 繁體中文（香港特別行政區）        |

## Contributors

[![Contributors](https://contrib.rocks/image?repo=Xujiayao/MC-Discord-Chat)](https://github.com/Xujiayao/MC-Discord-Chat/graphs/contributors)

## Stargazers over time

[![Stargazers over time](https://starchart.cc/Xujiayao/MC-Discord-Chat.svg)](https://starchart.cc/Xujiayao/MC-Discord-Chat)