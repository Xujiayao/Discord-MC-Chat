# Changelog 更新日志

## MCDiscordChat 2.0.0-alpha.2 for Minecraft 1.16.x/1.17.x/1.18.x - 2022/5/13

## ⚠ Reminder 提醒

This version has a significant change compared with previous versions. Please make sure to repeat the steps in the [MCDiscordChat Docs](https://blog.xujiayao.top/posts/4ba0a17a/) and do not copy and paste anything directly from the old config file, as this may cause errors!

此版本与之前的版本相比有重大变化。请务必重复 [MCDiscordChat 文档](https://blog.xujiayao.top/posts/4ba0a17a/) 中的步骤，并且不要直接从旧配置文件中复制和粘贴任何内容，因为这可能会导致错误！

## New Features 新特性

- Add switch to send notification of new updates to all admins (#22)
  添加向所有管理员发送新更新的通知的开关

- Add display of max player count to /info command message
  在 /info 命令消息中添加玩家人数上限的显示

- Add switch to modify in-game chat messages (#24)
  添加修改游戏内聊天消息的开关

- Add switch to display response messages in game
  添加修改游戏内聊天消息的开关

- Add switch to display Discord server nickname in game
  添加游戏内显示 Discord 服务器昵称的开关

- Add the ability to fully customize all in-game messages (#28)
  添加完全自定义所有游戏内消息的功能

- Add the ability to update Discord channel topic (#19)
  添加完全自定义所有游戏内消息的功能

- Add the ability to customize the interval of timers
  添加自定义定时器间隔的功能

- Add switch to disable @ mentions in game (#31)
  添加游戏内禁用 @ 提及的开关

- Add the ability to mention roles in game
  添加游戏内提及身份组的功能

- Add the ability to highlight and open hyperlinks in game
  添加游戏内高亮和打开超链接的功能

- Backup every time when loading the config file
  每次加载配置文件时进行备份

- Automatically check for updates every 6 hours
  每 6 小时自动检查更新

## Changes 更改

- Modify GitHub issue templates
  修改 GitHub 议题模板

- Fix /info message missing line breaks when at least two players are online (#26)
  修复在多人在线时 /info 命令信息缺少换行的问题

- Rename some variables in the config file
  重命名配置文件中的一些变量

- Fix advancements does not send correctly (#30)
  修复进度发送错误的问题

- Allow processing of messages sent by bots (except webhooks) (#32)
  允许处理机器人发送的消息（Webhook 除外）

## Removed 移除

- Disable submission of blank GitHub issues
  禁止提交空白 GitHub 议题

- Disable HTML escaping of the config file
  禁用配置文件的 HTML 转义

## Contributors 贡献者

- @Xujiayao

## Detailed Information 详细信息

https://github.com/Xujiayao/MCDiscordChat/compare/2.0.0-alpha.1...master