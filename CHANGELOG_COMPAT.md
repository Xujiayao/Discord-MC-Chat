# Changelog 更新日志

## Discord-MC-Chat 2.3.0-compat for Minecraft 1.14.4+ - 2024/2/17

## New Features 新特性

N/A

## Changes 更改

- Fix messages are ignored if user IDs are put into the botIds list (#207)
  修复将用户 ID 放入 botIds 列表时消息被忽略

- Fix server becomes temporarily unresponsive when sending Webhook messages (#210)
  修复服务器在发送 Webhook 消息时暂时无响应

- Fix inconsistent player names used in MCDC messages to Discord (#208)
  修复发送到 Discord 的 MCDC 消息中使用的玩家名称不一致

- Fix querying "/dmcc stats" in-game displays CR characters
  修复游戏内查询 /dmcc stats 会显示 CR 字符

- Refactor: Migrate to Mojang Mappings & Utilize Listener for Minecraft Events (#216)
  重构：迁移到 Mojang 的映射并利用监听器处理 Minecraft 事件

- Fix using Carpet mod to modify server TPS results in incorrect TPS display (#217)
  修复使用 Carpet 模组修改服务器 TPS 导致 TPS 显示错误

- Fix sending links in-game with Markdown formatting characters results in incorrect parsed links (#218)
  修复游戏内发送带有 Markdown 格式字符的链接时解析出错误链接

## Removed 移除

N/A

## Contributors 贡献者

- @Xujiayao

## Detailed Information 详细信息

https://github.com/Xujiayao/Discord-MC-Chat/compare/2.2.5-compat...2.3.0-compat