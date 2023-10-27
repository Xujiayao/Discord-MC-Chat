<div align="right">
语言：中文 <a href="/README.md">English</a>
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

更多介绍 + 文档：[MC-Discord-Chat 文档 | Xujiayao's Blog](https://blog.xujiayao.top/posts/4ba0a17a/)

## 简介

[MC-Discord-Chat](https://github.com/Xujiayao/MC-Discord-Chat) (MCDC)，前身为 MCDiscordChat，一个实用且功能强大的 Fabric 和 Quilt Minecraft <> Discord 跨服聊天工具，灵感来自 BRForgers/DisFabric。

![0001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/0001.png)

![001.png](https://cdn.jsdelivr.net/gh/Xujiayao/BlogSource@master/source/file/posts/4ba0a17a/001.png)

## 下载

所有发行版均可以在以下站点下载：

- [Modrinth](https://modrinth.com/mod/mcdiscordchat/versions)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/mcdiscordchat/files)
- [GitHub](https://github.com/Xujiayao/MC-Discord-Chat/releases)

你可以在 [GitHub Actions](https://github.com/Xujiayao/MC-Discord-Chat/actions) 找到最新的构建 JAR 文件。

## 帮助

如果有 bug 或建议，或者有什么不懂的，可以 [提交 issue](https://github.com/Xujiayao/MC-Discord-Chat/issues/new/choose)。

## 贡献

**欢迎你为 MCDC 做出贡献！**

如果你有兴趣为 MCDC 做出贡献，你可以在 GitHub 上提交拉取请求。

对于代码贡献，构建文件位于 `/wrapper/build/libs/` 文件夹中。

对于翻译贡献，语言文件位于 `/wrapper/src/main/resources/lang/` 文件夹中。复制 `en_us.json` 并将新的文件重命名为你的语言代码以开始翻译。请为整个文件中所有键提供翻译。

## 功能特色

- 全面的多语言支持
- 支持多服务器模式（同 Discord 频道多服务器运行 MCDC）
- Minecraft <> Discord 跨服聊天
  - 支持禁用 Discord 和游戏内聊天广播
  - 支持使用 Discord 频道主题功能显示服务器状态
  - 支持使用机器人活动状态显示服务器玩家数
  - 支持 Discord Webhook 功能
    - 动态创建 MCDC Webhook
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
  - 可使用 Discord 管理可执行 MCDC 命令的频道
  - 当有人执行 MCDC 命令时通知游戏内玩家
  - /console 命令支持 Minecraft 命令自动补全
  - 普通命令
    - /help                    | 获取可用命令列表
    - /info                    | 查询服务器运行状态
    - /stats \<type\> \<name\> | 查询该统计信息的排行榜
    - /update                  | 检查更新
  - 管理员命令
    - /console \<command\>     | 在服务器控制台中执行命令（仅限管理员）
    - /log \<file\>            | 获取指定的服务器日志（仅限管理员）
    - /reload                  | 重新加载 MC-Discord-Chat 配置文件（仅限管理员）
    - /stop                    | 停止服务器（仅限管理员）
  - 可调整权限的命令
    - /whitelist \<player\>    | 添加玩家至服务器白名单
- 可使用 Minecraft 命令
  - 仅限管理员的命令仅对 4 级管理员可用
  - 普通命令
    - /mcdc help                    | 获取可用命令列表
    - /mcdc info                    | 查询服务器运行状态
    - /mcdc stats \<type\> \<name\> | 查询该统计信息的排行榜
    - /mcdc update                  | 检查更新
  - 管理员命令
    - /mcdc reload                  | 重新加载 MC-Discord-Chat 配置文件（仅限管理员）
  - 可调整权限的命令
    - /mcdc whitelist \<player\>    | 添加玩家至服务器白名单
- 可完全自定义所有消息格式
  - 可禁用向 Discord 频道发送特定服务器消息
  - 游戏内
    - 来自 Discord 的聊天消息
    - 来自 Discord 的回复消息
    - 来自其它 MCDC 服务器的消息
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
  - 通过检查服务器 Minecraft 版本兼容性来准确推送 MCDC 版本更新
  - 可自定义更新通知频道
  - 可禁用检查更新

目前可用的语言：

| 代码      | 语言                   |
|---------|----------------------|
| `en_us` | English (US)         |
| `fr_fr` | Français (France)    |
| `ko_kr` | 한국어（대한민국）            |
| `no_no` | Norsk Bokmål (Norge) |
| `pl_pl` | Polski (Polska)      |
| `ru_ru` | Русский (Россия)     |
| `zh_cn` | 简体中文（中国大陆）           |
| `zh_hk` | 繁體中文（香港特別行政區）        |
| `zh_tw` | 繁體中文（台灣）             |

## 贡献者

[![Contributors](https://contrib.rocks/image?repo=Xujiayao/MC-Discord-Chat)](https://github.com/Xujiayao/MC-Discord-Chat/graphs/contributors)

## 星标历史

[![Stargazers over time](https://starchart.cc/Xujiayao/MC-Discord-Chat.svg)](https://starchart.cc/Xujiayao/MC-Discord-Chat)