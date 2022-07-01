<div align="right">
语言：中文 <a href="/README.md">English</a>
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

MCDiscordChat (MCDC), a practical and powerful Fabric Minecraft <> Discord chat bridge

更多介绍 + 文档：[MCDiscordChat 文档 | Xujiayao's Blog](https://blog.xujiayao.top/posts/4ba0a17a/)

## 🥳 Welcome to MCDC v2!

我强烈推荐你更新 MCDC 并使用新功能！欢迎提交错误报告或其他建议！

MCDC 已完全重写，因此可能存在一些错误。请在报告问题时说明你使用的确切版本。

![0001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/0001.png)

![001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/001.png)

## 简介

[MCDiscordChat](https://github.com/Xujiayao/MCDiscordChat)（简称为 MCDC），一个实用且功能强大的 Fabric Minecraft <> Discord 跨服聊天工具，灵感来自 BRForgers/DisFabric。

## 下载

所有发行版均可以在以下站点下载：

- [Modrinth](https://modrinth.com/mod/mcdiscordchat/versions)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/mcdiscordchat/files)
- [GitHub](https://github.com/Xujiayao/MCDiscordChat/releases)

你可以在 [GitHub Actions](https://github.com/Xujiayao/MCDiscordChat/actions) 找到最新的构建 JAR 文件。

## 帮助

如果有 bug 或建议，或者有什么不懂的，可以 [发表 issue](https://github.com/Xujiayao/MCDiscordChat/issues/new/choose)。

## 功能特色

- 支持多服务器模式（同 Discord 频道多服务器运行 MCDC）
- 支持多语言（英文 / 中文）
- 支持使用 Discord 频道主题功能显示服务器状态
- Minecraft <> Discord 跨服聊天
  - 支持 Discord Webhook 功能
    - 可自定义 Webhook 玩家头像 API
    - 未填写 Webhook URL 时使用机器人自身发送聊天消息
  - 支持游戏内 Markdown 解析
  - 支持游戏内高亮和使用默认 Unicode 和服务器自定义表情符号
  - 支持游戏内高亮贴纸
  - 支持游戏内高亮和提及 (@)
    - 支持禁用游戏内提及 (@)
  - 支持游戏内高亮和打开超链接和 GIF
  - 支持禁用所有解析
  - 支持游戏内显示 Discord 用户身份组颜色
  - 支持游戏内显示回复的消息
  - 可广播玩家指令执行
    - 执行指令的玩家也可以看到自己执行的指令
  - 可广播服务器控制台日志
- 可使用服务器命令
  - 可使用 Discord 管理可执行 MCDC 命令的频道
  - 普通命令
    - /info                    | 查询服务器运行状态
    - /help                    | 获取可用命令列表
    - /update                  | 检查更新
    - /stats \<type\> \<name\> | 查询该统计信息的排行榜
  - 管理员命令
    - /reload                  | 重新加载 MCDiscordChat 配置文件（仅限管理员）
    - /console \<command\>     | 在服务器控制台中执行命令（仅限管理员）
    - /log                     | 获取服务器最新日志（仅限管理员）
    - /stop                    | 停止服务器（仅限管理员）
- 可完全自定义所有消息格式
  - 游戏内
    - 来自 Discord 的聊天消息
    - 来自 Discord 的回复消息
    - 来自其它服务器的消息
  - Discord
    - 在服务器自动时
    - 在服务器关闭时
    - 在玩家加入服务器时
    - 在玩家离开服务器时
    - 在玩家达成进度 / 达成目标 / 完成挑战时
    - 在玩家死亡时
    - 在服务器 MSPT 高于预警值时
    - 在服务器发送控制台日志消息时
- 可使用管理员名单配置用户使用特殊命令的权限
- 支持配置文件热重载
  - 每次加载配置文件时进行备份
- 定期检查更新

## 贡献者

[![Contributors](https://contrib.rocks/image?repo=xujiayao/mcdiscordchat)](https://github.com/Xujiayao/mcdiscordchat/graphs/contributors)