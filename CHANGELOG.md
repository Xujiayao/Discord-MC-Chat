# Changelog 更新日志

## MCDiscordChat 2.0.0-alpha.7 for Minecraft 1.14+ - 2022/7/26

## New Features 新特性

- Customizable update notification channel (#66)
  可自定义更新通知频道

- Add practical logs for multi-server feature
  为多服务器功能添加实用日志

- Sending specific server messages to Discord can be disabled (#70)
  可禁用向 Discord 频道发送特定服务器消息

- Send all console log messages to Discord console log channel (#72)
  将所有控制台日志消息发送到 Discord 控制台日志频道

- Add the ability for /log command to specify the log file to send
  添加 /log 命令指定要发送的日志文件的功能

## Changes 更改

- Fix exclude option for broadcast command execution does not work (#64)
  修复广播指令执行的排除选项不起作用

- Fix NPE when someone sends a message before server fully starts (#68)
  修复服务器完全启动前接收消息时的空指针异常

## Removed 移除

N/A

## Contributors 贡献者

- @Xujiayao
- @LofiTurtle

## Detailed Information 详细信息

https://github.com/Xujiayao/MCDiscordChat/compare/2.0.0-alpha.6...2.0.0-alpha.7