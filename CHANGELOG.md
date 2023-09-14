# Changelog 更新日志

## MC-Discord-Chat 2.2.1 for Minecraft 1.14+ - 2023/9/14

MCDiscordChat has been renamed to MC-Discord-Chat.

The comprehensive list of supported Minecraft versions for each MCDC version is now available on Modrinth, CurseForge, and the MCDC Docs.

MCDiscordChat 已更名为 MC-Discord-Chat。

从现在开始，你可以在 Modrinth、CurseForge 和 MCDC 文档中找到每个 MCDC 版本所支持的 Minecraft 版本的全面列表。

## New Features 新特性

- Customizable messages when Webhook is disabled (#163)
  可自定义禁用 Webhook 时发送的消息

- Dynamically generate MCDC help messages (#167)
  动态生成 MCDC 帮助消息

- Adjustable permissions for the MCDC /whitelist command (#167)
  可调整 MCDC /whitelist 命令的使用权限

## Changes 更改

- Fix Quilt compatibility issue (#164)
  修复 Quilt 兼容性问题

- Fix exception when players register before the server is fully started (#168)
  修复玩家在服务器未完全启动前注册时报错

- Fix chat messages not being sent when using multi-server mode
  修复使用多服务器模式时没有发送聊天消息

- Send MSPT warnings to main channel when console log channel is enabled (#171)
  启用控制台日志频道时将 MSPT 警告发送到主频道

- Fix console log messages may be too long (#174)
  修复控制台日志消息有机会过长

## Removed 移除

N/A

## Contributors 贡献者

- @Xujiayao
- @aria1th

## Detailed Information 详细信息

https://github.com/Xujiayao/MC-Discord-Chat/compare/2.2.0...2.2.1