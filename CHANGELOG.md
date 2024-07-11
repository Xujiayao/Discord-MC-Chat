# Changelog 更新日志

## Discord-MC-Chat 2.3.4 for Minecraft 1.19.4+ - 2024/7/11

DMCC will support the [Placeholder API](https://placeholders.pb4.eu/) in the next release.

`botPlayingStatus` and `botListeningStatus` have been renamed to `botPlayingActivity` and `botListeningActivity`. Please modify them before restarting the server to avoid losing any existing changes.

In addition, the check for updates feature has been fixed. You will be able to receive update notifications for future new versions when using version 2.3.4.

DMCC 将在下一个版本中支持 [Placeholder API](https://placeholders.pb4.eu/)。

`botPlayingStatus` 和 `botListeningStatus` 已被重命名为 `botPlayingActivity` 和 `botListeningActivity`。请在重新启动服务器之前进行修改，以免丢失现有的更改。

此外，检查更新的功能已经修复。在使用 2.3.4 版本时，你将能够接收到未来新版本的更新推送。

## New Features 新特性

- Add the ability to display server status in the bot's Discord online status (#95)
  添加在机器人的 Discord 在线状态中显示服务器状态的功能

## Changes 更改

- Improved detection logic for dynamic-created DMCC Webhooks (#233)
  改进 DMCC Webhook 动态创建的检测逻辑

- Fix number of players in bot activity does not decrease when player leaves the game (#235)
  修复机器人活动状态中的玩家数量在玩家离开游戏时不会减少

- Fix DMCC update notifications never being sent (#231, #234 and #239)
  修复 DMCC 更新通知从未发送

- Fix exception when the world/stats folder does not exist (#38)
  修复 world/stats 文件夹不存在时报错

- No longer delays checking for updates by an hour (#52)
  不再延迟一小时开始检查更新

- Fix exception when /stats command message exceeds 2000 characters (#212)
  修复 /stats 命令消息超过 2000 个字符时报错

- Rename botPlayingStatus and botListeningStatus -> botPlayingActivity and botListeningActivity (#95)
  重命名 botPlayingStatus 和 botListeningStatus -> botPlayingActivity 和 botListeningActivity

## Removed 移除

N/A

## Contributors 贡献者

- @Xujiayao

## Detailed Information 详细信息

https://github.com/Xujiayao/Discord-MC-Chat/compare/2.3.3...2.3.4