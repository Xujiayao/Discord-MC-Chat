# Changelog 更新日志

## Discord-MC-Chat 2.3.5 for Minecraft 1.14.4+ - 2024/10/29

DMCC Discord Server is now public! Join now through: https://discord.gg/kbXkV6k2XU

DMCC now supports switching to Brazilian Portuguese. Note that DMCC no longer releases Active and Compat versions separately.

As this is a minor release, new features that involve modifying the config file will not be released in this version.

DMCC Discord 服务器现已公开！通过以下链接加入：https://discord.gg/kbXkV6k2XU

DMCC 现在支持切换到巴西葡萄牙语。留意现在 DMCC 不再分开发布 Active 和 Compat 版本。

由于这是一个次要版本，涉及到修改配置文件的新功能暂时不会发布。

## New Features 新特性

- Brazilian Portuguese translation (#243)
  巴西葡萄牙语翻译

- Supports broadcasting messages sent using the /tellraw @a command (#132 and #250)
  支持广播使用 /tellraw @a 命令发送的消息

- Restore /say command broadcast feature in versions 1.18.2 and below (#197)
  恢复在 1.18.2 及更早版本中广播 /say 命令的功能

- Throws exception when Webhook fails to send (#249)
  在 Webhook 发送失败时抛出异常

- Compatible with Minecraft 1.21.2 and 1.21.3 (#258)
  兼容 Minecraft 1.21.2 和 1.21.3 版本

## Changes 更改

- Changed the way DMCC handles the /say command (#197)
  更改 DMCC 处理 /say 命令的方式

- Webhook check only displays an error message when Guild permission is insufficient
  Webhook 检查在 Guild 权限不足时仅显示错误消息

- No longer release Active and Compat versions separately
  不再分开发布 Active 和 Compat 版本

## Removed 移除

N/A

## Contributors 贡献者

- @Xujiayao
- @rodrigoaddor

## Detailed Information 详细信息

https://github.com/Xujiayao/Discord-MC-Chat/compare/2.3.4...2.3.5