# Changelog 更新日志

## MCDiscordChat 2.2.0 for Minecraft 1.14+ - 2023/7/4

This version requires additional permission from the Discord bot, "Manage Webhooks", compared to the previous versions. Please replace the `APP_ID` of the second link below with your Application ID (get it by accessing the first link), and access it to authorize the new permission. If you do not trust the link below, you may create one by referring to the MCDC docs.

Regarding changes in the config file, this version dynamically creates MCDC Webhook and no longer requires manual creation when configuring MCDC, simplifying the steps of configuring MCDC and changing channels. Users not using Webhook may turn it off by setting the `useWebhook` option in the config file to `false`.

In addition, users may customize the range of allowed in-game mentions. Clearing the `allowed_mentions` list disallows all in-game mentions.

https://discord.com/developers/applications

https://discord.com/api/oauth2/authorize?client_id=APP_ID&permissions=537054224&scope=bot%20applications.commands

与旧版本相比，此版本需要 Discord 机器人「管理 Webhooks」的额外权限。请将下面第二个链接中的 `APP_ID` 替换为你的应用 ID（可通过访问第一个链接获取），并访问该链接以授权新权限。如果你不信任下面的链接，你也可以参考 MCDC 文档自行创建。

关于配置文件的变化，此版本动态创建 MCDC Webhook，配置 MCDC 时不再需要手动创建，简化了配置 MCDC 和更改频道的步骤。不使用 Webhook 的用户可将配置文件中的 `useWebhook` 选项设为 `false` 以禁用 Webhook。

此外，用户可以自定义允许游戏内提及的范围。清空 `allowed_mentions` 列表即为禁止所有游戏内提及。

## New Features 新特性

- Customizable scope of allowed mentions in-game (#131)
  可自定义允许游戏内提及的范围

- Add %nextUpdateTime% placeholder for channel topics (#134)
  为频道主题添加 %nextUpdateTime% 占位符

- Multilingual support for task/challenge/goal messages (#133)
  进度 / 挑战 / 目标消息提供多语言支持

- Support displaying descriptions for task/challenge/goal messages (#133)
  支持显示进度 / 挑战 / 目标的描述

- Support adding players to the server whitelist in Discord (#130)
  支持在 Discord 将玩家添加至服务器白名单

- Dynamic-created MCDC Webhook (#152)
  动态创建 MCDC Webhook

- Polish translation (#154)
  波兰语翻译

- Compatible with the new Discord username system (#158)
  兼容新的 Discord 用户名系统

- Cantonese translation (#159)
  粤语翻译

- Norwegian Bokmål translation (#161)
  书面挪威语翻译

## Changes 更改

- Improve console log formatting (#140)
  改良控制台日志格式

- Fix exception when Discord user nickname contains double quotes (#145)
  修复 Discord 用户昵称包含双引号时报错

- MSPT monitoring delays msptCheckInterval milliseconds start (#134)
  MSPT 监测延迟 msptCheckInterval 毫秒启动

- Fix exception caused by console log message being too long (#149)
  修复控制台日志消息过长导致报错

- Fix incorrect text colour when customizing in-game messages (#156)
  修复自定义游戏内消息时文本颜色错误

- Newlines in Discord messages appear as new messages in-game (#155)
  Discord 消息中的新行在游戏内显示为新消息

- Optimize the effect of the discordNewlineLimit option
  优化 discordNewlineLimit 选项的效果

## Removed 移除

N/A

## Contributors 贡献者

- @Xujiayao
- @BlissfulAlloy79
- @Bocz3k
- @Kire2oo2
- @LofiTurtle

## Detailed Information 详细信息

https://github.com/Xujiayao/MCDiscordChat/compare/1.20-2.1.4...2.2.0