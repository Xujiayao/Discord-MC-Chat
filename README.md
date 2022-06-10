<div align="right">
Language: English <a href="/README_CN.md">中文</a>
</div>

<p align="center">
<img width=128 src="https://cdn.jsdelivr.net/gh/Xujiayao/MCDiscordChat@master/src/main/resources/assets/mcdiscordchat/icon.png">
</p>

# MCDiscordChat

[![License](https://img.shields.io/github/license/xujiayao/MCDiscordChat?logo=github)](https://github.com/Xujiayao/MCDiscordChat/blob/master/LICENSE)
[![Release](https://img.shields.io/github/v/release/xujiayao/MCDiscordChat?logo=github)](https://github.com/Xujiayao/MCDiscordChat/releases)
[![GitHub Downloads](https://img.shields.io/github/downloads/xujiayao/MCDiscordChat/total?logo=github)](https://github.com/Xujiayao/MCDiscordChat/releases)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/mcdiscordchat?label=modrinth%20downloads)](https://modrinth.com/mod/mcdiscordchat)
[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_mcdiscordchat_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/mcdiscordchat)
[![Versions Supported](https://cf.way2muchnoise.eu/versions/mcdiscordchat.svg)](https://www.curseforge.com/minecraft/mc-mods/mcdiscordchat)

MCDiscordChat (MCDC), a practical and powerful Fabric Minecraft <> Discord chat bridge inspired by BRForgers/DisFabric

More information + Docs: [MCDiscordChat Docs | Xujiayao's Blog](https://blog.xujiayao.top/posts/4ba0a17a/)

## 🥳 Welcome to MCDC v2!

I highly recommend you update MCDC and take advantage of the new features! Bug reports or other suggestions are welcome!

MCDC is completely rewritten, so there may be some bugs. Please state the exact version you are using when reporting an issue.

![0001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/0001.png)

![001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/001.png)

## Introduction

[MCDiscordChat](https://github.com/Xujiayao/MCDiscordChat) (abbreviated as MCDC), a practical and powerful Fabric Minecraft <> Discord chat bridge inspired by BRForgers/DisFabric.

## Download

All releases can be downloaded at the following sites:

- [Modrinth](https://modrinth.com/mod/mcdiscordchat/versions)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/mcdiscordchat/files)
- [GitHub](https://github.com/Xujiayao/MCDiscordChat/releases)

You can find the latest build JAR files at [GitHub Actions](https://github.com/Xujiayao/MCDiscordChat/actions).

## Support

If there is a bug or suggestion, or something you don't understand, you can [submit an issue](https://github.com/Xujiayao/MCDiscordChat/issues/new/choose) on GitHub.

## Features

- Support multi-server mode (multi-server operation on the same Discord channel)
- Support multiple languages (English / Chinese)
- Support displaying server status using Discord channel topic feature
- Minecraft <> Discord cross server chat
  - Support Discord Webhook feature
    - Customizable Webhook Avatar API
	- Use the bot itself to send chat messages when Webhook URL is not filled
  - Support in-game Markdown parsing
  - Support highlighting and using default and server emoji in-game
  - Support highlighting and mentions (@) in-game
    - Support disabling mentions (@) in-game
  - Support highlighting and opening hyperlinks in-game
  - Support in-game display of Discord user role colour
  - Support in-game display of response messages
  - Broadcast player command execution
    - Players who execute commands can also see the commands themselves
  - Broadcast server console log
- Server Commands available
  - Normal Commands
    - /info                    | Query server running status
    - /help                    | Get a list of available commands
    - /update                  | Check for update
    - /stats \<type\> \<name\> | Query the scoreboard of a statistic
  - Admin Commands
    - /reload                  | Reload MCDiscordChat config file (admin only)
    - /console \<command\>     | Execute a command in the server console (admin only)
    - /log                     | Get the latest server log (admin only)
    - /stop                    | Stop the server (admin only)
- Fully customizable message format
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
    - Server sent a console log message
- Use admin list to configure user permissions to use special commands
- Support Hot Reloading of the config file
  - Backup every time the config file is loaded
- Check for updates regularly

## Contributors

[![Contributors](https://contrib.rocks/image?repo=xujiayao/mcdiscordchat)](https://github.com/Xujiayao/mcdiscordchat/graphs/contributors)