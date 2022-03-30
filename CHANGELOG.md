# Changelog 更新日志 | 🥳 Welcome to MCDC v2!

## MCDiscordChat 2.0.0-alpha.1 for Minecraft 1.16.x/1.17.x/1.18.x - 2022/3/31

I highly recommend you update MCDC and take advantage of the new features! Bug reports or other suggestions are welcome!

MCDC is completely rewritten, so there may be some bugs. Please state the exact version you are using when reporting an issue.

我强烈推荐你更新 MCDC 并使用新功能！欢迎提交错误报告或其他建议！

MCDC 已完全重写，因此可能存在一些错误。请在报告问题时说明你使用的确切版本。

## ⚠ Reminder 提醒

This version has a significant change compared with previous versions. Please make sure to backup your old MCDC config file (mcdiscordchat.json), re-read the [MCDiscordChat Docs](https://blog.xujiayao.top/posts/4ba0a17a/) and do not copy and paste anything directly from the old config file, as this may cause errors!

此版本与之前的版本相比有重大变化，请务必备份你的旧 MCDC 配置文件 (mcdiscordchat.json)，重新阅读 [MCDiscordChat 文档](https://blog.xujiayao.top/posts/4ba0a17a/) ，并且不要直接从旧配置文件中复制和粘贴任何内容，因为这可能会导致错误！

## New Features 新特性

- Print the MCDC introduction when starting the server
  启动服务器时打印 MCDC 介绍
  @Xujiayao

- Show changelog when a new version is available
  新版本可用时显示更新日志
  @Xujiayao

- Add GitHub issue templates
  添加 GitHub 议题模板
  @Xujiayao

- Unicode emoji (non-server custom emoji) can also be highlighted in-game
  Unicode 表情符号（非服务器自定义表情符号）也可以在游戏中高亮显示
  @Xujiayao

- Bot activity status can be switched between 'playing' and 'listening'
  机器人活动状态可以在「正在玩」和「正在听」之间切换
  @Xujiayao

- Server console and Discord console log channel can print chat messages from Discord
  服务器控制台和 Discord 控制台日志频道可打印来自 Discord 的聊天消息
  @Xujiayao

- Use Discord Application (Slash) Command
  使用 Discord 应用（斜杠）命令
  @Xujiayao

- Add /log command to allow admins to get the latest server logs directly from Discord
  添加 /log 命令使管理员可直接从 Discord 获取最新的服务器日志
  @Xujiayao

## Changes 更改

- Rewrite all files and optimize code and logic
  重写所有文件并优化代码和逻辑
  @Xujiayao

- Modify the project description
  修改项目描述
  @Xujiayao

- Change the implementation method of message cooldown
  更改消息冷却的实现方式
  @Xujiayao

- Change the default format of Discord console log messages
  更改 Discord 控制台日志消息的默认格式
  @Xujiayao

- Check for updates using temporary files for v1 compatibility
  使用临时文件检查更新以兼容 v1 版本
  @Xujiayao

- Use JDA.shutdownNow() to resolve process hangs when stopping the server
  使用 JDA.shutdownNow() 解决停止服务器时的进程挂起问题
  @Xujiayao

- Fix backslashes still exist when ignoring (escaping) Markdown formatting
  修复忽略（转义）Markdown 格式时反斜杠仍然存在的问题
  @Xujiayao

- Modify the format of the MCDC help menu
  修改 MCDC 帮助菜单的格式
  @Xujiayao

- Change the implementation method of multi-server
  更改消息冷却的实现方式
  @Xujiayao

## Removed 移除

- Exception stack traces are no longer sent to the Discord channel
  不再向 Discord 频道发送异常堆栈跟踪
  @Xujiayao

- Remove the function of modifying the admin list in Discord
  移除在 Discord 修改管理员列表的功能
  @Xujiayao

- Remove the super admin list
  移除超级管理员列表
  @Xujiayao

## Detailed Information 详细信息

https://github.com/Xujiayao/MCDiscordChat/compare/1.12.1...master