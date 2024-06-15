<div align="right">
Language: English <a href="/README_CN.md">中文</a>
</div>

<p align="center">
<img width=128 src="https://cdn.jsdelivr.net/gh/Xujiayao/Discord-MC-Chat@master/wrapper/src/main/resources/assets/discord-mc-chat/icon.png">
</p>

# Discord-MC-Chat

[![License](https://img.shields.io/github/license/xujiayao/Discord-MC-Chat?logo=github)](https://github.com/Xujiayao/Discord-MC-Chat/blob/master/LICENSE)
[![Release](https://img.shields.io/github/v/release/xujiayao/Discord-MC-Chat?logo=github)](https://github.com/Xujiayao/Discord-MC-Chat/releases)
[![jsDelivr Hits (Very Old)](https://data.jsdelivr.com/v1/package/gh/Xujiayao/MCDiscordChat/badge?style=rounded)](https://www.jsdelivr.com/package/gh/Xujiayao/MCDiscordChat)
[![jsDelivr Hits (Old)](https://data.jsdelivr.com/v1/package/gh/Xujiayao/MC-Discord-Chat/badge?style=rounded)](https://www.jsdelivr.com/package/gh/Xujiayao/MC-Discord-Chat)
[![jsDelivr Hits (New)](https://data.jsdelivr.com/v1/package/gh/Xujiayao/Discord-MC-Chat/badge?style=rounded)](https://www.jsdelivr.com/package/gh/Xujiayao/Discord-MC-Chat)
[![GitHub Downloads](https://img.shields.io/github/downloads/xujiayao/Discord-MC-Chat/total?logo=github)](https://github.com/Xujiayao/Discord-MC-Chat/releases)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/discord-mc-chat?label=modrinth%20downloads)](https://modrinth.com/mod/discord-mc-chat)
[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_548539_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/discord-mc-chat)
[![Versions Supported](https://cf.way2muchnoise.eu/versions/548539.svg)](https://www.curseforge.com/minecraft/mc-mods/discord-mc-chat)

Discord-MC-Chat (DMCC), formerly known as MC-Discord-Chat and MCDiscordChat (MCDC), is a practical and powerful Fabric and Quilt Minecraft <> Discord chat bridge inspired by BRForgers/DisFabric

More information + Docs: [Discord-MC-Chat Docs | Xujiayao's Blog](https://blog.xujiayao.com/posts/4ba0a17a/)

## Introduction

[Discord-MC-Chat](https://github.com/Xujiayao/Discord-MC-Chat) (DMCC), formerly known as MC-Discord-Chat and MCDiscordChat (MCDC), is a practical and powerful Fabric and Quilt Minecraft <> Discord chat bridge inspired by BRForgers/DisFabric.

![0001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/0001.png)

![001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/001.png)

## Download

All releases can be downloaded at the following sites:

- [Modrinth](https://modrinth.com/mod/discord-mc-chat/versions)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/discord-mc-chat/files)
- [GitHub](https://github.com/Xujiayao/Discord-MC-Chat/releases)

You can find the latest build JAR files at [GitHub Actions](https://github.com/Xujiayao/Discord-MC-Chat/actions).

## Support

If there is a bug or suggestion, or something you don't understand, you can [submit an issue](https://github.com/Xujiayao/Discord-MC-Chat/issues/new/choose) on GitHub.

## Contributing

**You are welcome to contribute to DMCC!**

If you are interested in contributing to DMCC, you can submit a pull request on GitHub.

For code contributions, the build file is located in the `/build/` folder.

For translation contributions, language files are located in the `/wrapper/src/main/resources/lang/` folder. Copy `en_us.json` and rename the new one to your language code to get started. Please provide translations for the entire file for all keys.

## Features

- Full multi-language support
- Support multi-server mode (running more than one Minecraft server with DMCC in the same Discord guild)
- Minecraft <> Discord cross server chat
    - Support disabling Discord and in-game chat broadcasts
    - Support displaying server status using Discord channel topic feature
    - Support displaying server player count in bot activity status
    - Support Discord Webhook feature
        - Dynamic-created DMCC Webhook
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
    - Use Discord to manage channels that can execute DMCC commands
    - Notify in-game players when someone executes an DMCC command
    - /console command supports Minecraft command auto-completion
    - Normal Commands
        - /help                    | Get a list of available commands
        - /info                    | Query server running status
        - /stats \<type\> \<name\> | Query the scoreboard of a statistic
        - /update                  | Check for update
    - Admin Commands
        - /console \<command\>     | Execute a command in the server console (admin only)
        - /log \<file\>            | Get the specified server log (admin only)
        - /reload                  | Reload Discord-MC-Chat config file (admin only)
        - /stop                    | Stop the server (admin only)
    - Commands with Adjustable Permissions
        - /whitelist \<player\>    | Add a player to the server whitelist
- Minecraft Commands available
    - Admin-only commands require a level 4 operator at minimum
    - Normal Commands
        - /dmcc help                    | Get a list of available commands
        - /dmcc info                    | Query server running status
        - /dmcc stats \<type\> \<name\> | Query the scoreboard of a statistic
        - /dmcc update                  | Check for update
    - Admin Commands
        - /dmcc reload                  | Reload Discord-MC-Chat config file (admin only)
    - Commands with Adjustable Permissions
        - /dmcc whitelist \<player\>    | Add a player to the server whitelist
- Fully customizable message format
    - Sending specific server messages to Discord can be disabled
    - In-game
        - Chat messages from Discord
        - Response messages from Discord
        - Messages from other DMCC servers
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
    - Push DMCC version updates accurately by checking server Minecraft version compatibility
    - Customizable update notification channel
    - Check for updates can be disabled

Languages currently available:

| Code    | Language              |
|---------|-----------------------|
| `de_de` | Deutsch (Deutschland) |
| `en_us` | English (US)          |
| `es_es` | Español (España)      |
| `fr_fr` | Français (France)     |
| `it_it` | Italiano (Italia)     |
| `ko_kr` | 한국어（대한민국）             |
| `no_no` | Norsk Bokmål (Norge)  |
| `pl_pl` | Polski (Polska)       |
| `ru_ru` | Русский (Россия)      |
| `zh_cn` | 简体中文（中国大陆）            |
| `zh_hk` | 繁體中文（香港特別行政區）         |
| `zh_tw` | 繁體中文（台灣）              |

## Contributors

[![Contributors](https://contrib.rocks/image?repo=Xujiayao/Discord-MC-Chat)](https://github.com/Xujiayao/Discord-MC-Chat/graphs/contributors)

## Stargazers over time

[![Stargazers over time](https://starchart.cc/Xujiayao/Discord-MC-Chat.svg)](https://starchart.cc/Xujiayao/Discord-MC-Chat)

## License

This project is licensed under the [MIT license](https://github.com/Xujiayao/Discord-MC-Chat/blob/master/LICENSE).

Exceptionally, the `src/main/java/com/xujiayao/discord_mc_chat/utils/MarkdownParser.java` file was obtained from BRForgers/DisFabric on December 31, 2020, licensed under the Mozilla Public License 2.0 (MPL-2.0).

> More details of the `src/main/java/com/xujiayao/discord_mc_chat/utils/MarkdownParser.java` file:
>
> Author: allanf181 (Allan Fernando)
>
> Link to the original source:
> https://github.com/BRForgers/DisFabric/blob/e0c7601405ee1b3f1de3c3168bc4ddd520501565/src/main/java/br/com/brforgers/mods/disfabric/utils/MarkdownParser.java
>
> Link to the license:
> https://github.com/BRForgers/DisFabric/blob/e0c7601405ee1b3f1de3c3168bc4ddd520501565/LICENSE
>
> Note that the "Don't Be a Jerk" license used in the newly created project BRForgers/DisFabric-and-DisForge is separate from the old project BRForgers/DisFabric.
>
> - Any recent changes in the project name and license do not retroactively affect the license terms of the code obtained at a specific moment before, that is, December 31, 2020.
> - Any files obtained from BRForgers/DisFabric continue to be subject to the terms of the MPL-2.0 license only.
>
> Link to the "Don't Be a Jerk" license:
> https://github.com/BRForgers/DisFabric-and-DisForge/blob/d1468a6c9b50ba24a250ec370cf645d58dccdfd1/LICENSE.md