# Changelog 更新日志

## MCDiscordChat 2.1.4 for Minecraft 1.14+ - 2023/5/30

This version still supports versions 1.19.2 and 1.19.3, which have been integrated into the same file.

此版本仍然支持 1.19.2 和 1.19.3 版本，已集成到同一个文件中。

## New Features 新特性

- Send chat messages sent by /say command to Discord (#125)
  发送 /say 指令所发送的聊天消息至 Discord

- Announce high MSPT at the console log channel when the console log feature is enabled (#125)
  启用控制台日志功能时将 MSPT 警告发送到控制台日志频道

- Add an option to flexibly choose whether to wait for the rate limit to reset (#129)
  添加选项供灵活选择是否等待速率限制重置

- Korean translation (#136)
  韩语翻译

- French translation (#137)
  法语翻译

## Changes 更改

- Fix chat message validation failure when using excluded commands (#127)
  修复使用已排除的指令时报错聊天记录验证失败

- Optimize the logic of handling rate limit when stopping the server (#129)
  优化停止服务器时处理速率限制的逻辑

- Fix description of /log <file> command out of date (#137)
  修复 /log <file> 命令的描述过时

- Fix exception when a Discord user who does not have any role executes a command (#137)
  修复没有任何用户组的 Discord 用户在执行命令时报错

## Removed 移除

N/A

## Contributors 贡献者

- @Xujiayao
- @Clem-Fern
- @MeowOnLong
- @Vocatis

## Detailed Information 详细信息

https://github.com/Xujiayao/MCDiscordChat/compare/2.1.3...2.1.4