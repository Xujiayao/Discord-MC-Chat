# Changelog 更新日志

## MCDiscordChat 2.1.2 for Minecraft 1.14+ - 2023/1/7

The 'chat message validation failure' issue in 1.19.3 has been fixed in this new version.
You may remove the 'no-chat-reports' mod and set `formatChatMessages` back to `true` in MCDC config now.

1.19.3 中的 "聊天消息验证失败" 问题已在此新版本中修复。
你现在可以删除 "no-chat-reports" 模组并在 MCDC 配置中将 `formatChatMessages` 改回 `true`。

## New Features 新特性

- Re-add support for version 1.19.2 (#101)
  重新添加对 1.19.2 版本的支持

- Add option to disable Discord and in-game chat broadcasts (#103)
  添加禁用 Discord 和游戏内聊天广播的选项

## Changes 更改

- Fix chat message validation failure (#100)
  修复聊天记录验证失败报错

- Fix in-game highlighted mentions not being escaped (#104)
  修复游戏内高亮提及没有进行转义

## Removed 移除

N/A

## Contributors 贡献者

- @Xujiayao

## Detailed Information 详细信息

https://github.com/Xujiayao/MCDiscordChat/compare/2.1.1...2.1.2