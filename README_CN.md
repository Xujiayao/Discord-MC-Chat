<div align="right">
语言：中文 <a href="/README.md">English</a>
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

更多介绍 + 文档：[Discord-MC-Chat 文档 | Xujiayao's Blog](https://blog.xujiayao.com/posts/4ba0a17a/)

## 简介

[Discord-MC-Chat](https://github.com/Xujiayao/Discord-MC-Chat) (DMCC)，前身为 MC-Discord-Chat 和 MCDiscordChat (MCDC)，一个实用且功能强大的 Fabric 和 Quilt Minecraft <> Discord 跨服聊天工具，灵感来自 BRForgers/DisFabric。

![0001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/0001.png)

![001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/001.png)

## 下载

所有发行版均可以在以下站点下载：

- [Modrinth](https://modrinth.com/mod/discord-mc-chat/versions)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/discord-mc-chat/files)
- [GitHub](https://github.com/Xujiayao/Discord-MC-Chat/releases)

你可以在 [GitHub Actions](https://github.com/Xujiayao/Discord-MC-Chat/actions) 找到最新的构建 JAR 文件。

## 帮助

如果有 bug 或建议，或者有什么不懂的，可以 [提交 issue](https://github.com/Xujiayao/Discord-MC-Chat/issues/new/choose)。

## 贡献

**欢迎你为 DMCC 做出贡献！**

如果你有兴趣为 DMCC 做出贡献，你可以在 GitHub 上提交拉取请求。

对于代码贡献，构建文件位于 `/build/` 文件夹中。

对于翻译贡献，语言文件位于 `/wrapper/src/main/resources/lang/` 文件夹中。复制 `en_us.json` 并将新的文件重命名为你的语言代码以开始翻译。请为整个文件中所有键提供翻译。

## 功能特色

- 全面的多语言支持
- 支持多服务器模式（在同一个 Discord 服务器中运行多个装有 DMCC 的 Minecraft 服务器）
- Minecraft <> Discord 跨服聊天
    - 支持禁用 Discord 和游戏内聊天广播
    - 支持使用 Discord 频道主题功能显示服务器状态
    - 支持使用机器人 Discord 在线状态中显示服务器状态
    - 支持使用机器人 Discord 活动状态显示服务器玩家数
    - 支持 Discord Webhook 功能
        - 动态创建 DMCC Webhook
        - 可自定义 Webhook 玩家头像 API
        - 可禁用 Webhook，使用机器人自身发送聊天消息
    - 支持游戏内 Markdown 解析
    - 支持游戏内高亮和使用默认 Unicode 和服务器自定义表情符号
    - 支持游戏内高亮贴纸
    - 支持游戏内高亮和提及 (@)
        - 可自定义允许游戏内提及 (@) 的范围
        - 支持禁用游戏内提及 (@)
    - 支持游戏内高亮和打开超链接和 GIF
    - 支持禁用所有解析
    - 支持游戏内显示 Discord 用户身份组颜色
    - 支持游戏内显示回复的消息
    - 支持限制 Discord 消息的换行次数
    - 可广播玩家指令执行
        - 执行指令的玩家也可以看到自己执行的指令
        - 可排除执行特定指令的广播
    - 可将所有控制台日志消息发送到 Discord
- 可使用 Discord 命令
    - 可使用 Discord 管理可执行 DMCC 命令的频道
    - 当有人执行 DMCC 命令时通知游戏内玩家
    - /console 命令支持 Minecraft 命令自动补全
    - 普通命令
        - /help                    | 获取可用命令列表
        - /info                    | 查询服务器运行状态
        - /stats \<type\> \<name\> | 查询该统计信息的排行榜
        - /update                  | 检查更新
    - 管理员命令
        - /console \<command\>     | 在服务器控制台中执行命令（仅限管理员）
        - /log \<file\>            | 获取指定的服务器日志（仅限管理员）
        - /reload                  | 重新加载 Discord-MC-Chat 配置文件（仅限管理员）
        - /stop                    | 停止服务器（仅限管理员）
    - 可调整权限的命令
        - /whitelist \<player\>    | 添加玩家至服务器白名单
- 可使用 Minecraft 命令
    - 仅限管理员的命令仅对 4 级管理员可用
    - 普通命令
        - /dmcc help                    | 获取可用命令列表
        - /dmcc info                    | 查询服务器运行状态
        - /dmcc stats \<type\> \<name\> | 查询该统计信息的排行榜
        - /dmcc update                  | 检查更新
    - 管理员命令
        - /dmcc reload                  | 重新加载 Discord-MC-Chat 配置文件（仅限管理员）
    - 可调整权限的命令
        - /dmcc whitelist \<player\>    | 添加玩家至服务器白名单
- 可完全自定义所有消息格式
    - 可禁用向 Discord 频道发送特定服务器消息
    - 游戏内
        - 来自 Discord 的聊天消息
        - 来自 Discord 的回复消息
        - 来自其它 DMCC 服务器的消息
    - Discord
        - 在服务器启动时
        - 在服务器关闭时
        - 在玩家加入服务器时
        - 在玩家离开服务器时
        - 在玩家达成进度 / 达成目标 / 完成挑战时
            - 多语言支持
            - 支持显示进度 / 挑战 / 目标的描述
        - 在玩家死亡时
            - 多语言支持
        - 在服务器 MSPT 高于预警值时
- 可使用管理员名单配置用户使用特殊命令的权限
- 支持配置文件热重载
    - 每次加载配置文件时进行备份
- 定期检查更新
    - 通过检查服务器 Minecraft 版本兼容性来准确推送 DMCC 版本更新
    - 可自定义更新通知频道
    - 可禁用检查更新

目前可用的语言：

| 代码      | 语言                    |
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

## 贡献者

[![Contributors](https://contrib.rocks/image?repo=Xujiayao/Discord-MC-Chat)](https://github.com/Xujiayao/Discord-MC-Chat/graphs/contributors)

## 星标历史

[![Stargazers over time](https://starchart.cc/Xujiayao/Discord-MC-Chat.svg)](https://starchart.cc/Xujiayao/Discord-MC-Chat)

## 许可证

本项目采用 [MIT 许可证](https://github.com/Xujiayao/Discord-MC-Chat/blob/master/LICENSE) 进行授权。

作为例外，`src/main/java/com/xujiayao/discord_mc_chat/utils/MarkdownParser.java` 文件于 2020 年 12 月 31 日从 BRForgers/DisFabric 获取，采用 Mozilla 公共许可证 2.0 (MPL-2.0) 进行授权。

> `src/main/java/com/xujiayao/discord_mc_chat/utils/MarkdownParser.java` 文件的更多详情：
>
> 作者：allanf181 (Allan Fernando)
>
> 链接到原始来源：
> https://github.com/BRForgers/DisFabric/blob/e0c7601405ee1b3f1de3c3168bc4ddd520501565/src/main/java/br/com/brforgers/mods/disfabric/utils/MarkdownParser.java
>
> 链接到许可证：
> https://github.com/BRForgers/DisFabric/blob/e0c7601405ee1b3f1de3c3168bc4ddd520501565/LICENSE
>
> 请注意，新创建的 BRForgers/DisFabric-and-DisForge 项目中使用的 "Don't Be a Jerk" 许可证与旧项目 BRForgers/DisFabric 是分开的。
>
> - 项目名称和许可证的任何近期变更都不会追溯影响到之前特定时刻（即 2020 年 12 月 31 日）所获取的代码的许可条款。
> - 从 BRForgers/DisFabric 获取的任何文件仍仅受 MPL-2.0 许可证条款的约束。
>
> 链接到 "Don't Be a Jerk" 许可证：
> https://github.com/BRForgers/DisFabric-and-DisForge/blob/d1468a6c9b50ba24a250ec370cf645d58dccdfd1/LICENSE.md