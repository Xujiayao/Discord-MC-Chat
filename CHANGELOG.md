# 更新日志 Changelog

MCDiscordChat 2.0.0-alpha.1 for Minecraft 1.16.x/1.17.x/1.18.x - 2022/3/24

## ⚠ 提醒 Reminder

此版本与之前的版本相比有重大变化，请务必备份你的旧 MCDiscordChat 配置文件 (mcdiscordchat.json)，重新阅读 [MCDiscordChat 文档](https://blog.xujiayao.top/posts/4ba0a17a/) ，并且不要直接从旧配置文件中复制和粘贴任何内容，因为这可能会导致错误！

这是 MCDiscordChat 的 alpha 版本，这意味着你可能会遇到重大错误或其他问题。欢迎提交错误报告或其他建议。请在报告问题时说明你使用的确切版本。

This version has a significant change compared with previous versions. Please make sure to backup your old MCDiscordChat config file (mcdiscordchat.json), re-read the [MCDiscordChat Docs](https://blog.xujiayao.top/posts/4ba0a17a/) and do not copy and paste anything directly from the old config file, as this may cause errors!

This is an alpha version of MCDiscordChat, which means you may encounter significant bugs or other issues. Bug reports or other suggestions are welcome. Please state the exact version you are using when reporting an issue.

## 新特性 New Features

- 启动服务器时打印 MCDC 介绍
  Print MCDC introduction when starting the server
  @Xujiayao

- 新版本可用时显示更新日志
  Show changelog when a new version is available
  @Xujiayao

- 添加 GitHub 议题模板
  Add GitHub issue templates
  @Xujiayao

- Unicode 表情符号（非服务器自定义表情符号）也可以在游戏中高亮显示
  Unicode emoji (non-server custom emoji) can also be highlighted in-game
  @Xujiayao

- 机器人活动状态可以在「正在玩」和「正在听」之间切换
  Bot activity status can be switched between "playing" and "listening"
  @Xujiayao

- 服务器控制台和 Discord 控制台日志频道可打印来自 Discord 的聊天消息
  Server console and Discord console log channel can print chat messages from Discord
  @Xujiayao

- 使用 Discord 应用（斜杠）命令
  Use Discord Application (Slash) Command
  @Xujiayao

- 添加 /log 命令使管理员可直接从 Discord 获取最新的服务器日志
  Add /log command which allows admins to get the latest server logs directly from Discord
  @Xujiayao

## 更改 Changes

- 重写所有文件并优化代码和逻辑
  Rewrite all files and optimize code and logic
  @Xujiayao

- 修改项目描述
  Modify project description
  @Xujiayao

- 更改消息冷却的实现方式
  Change the implementation method of message cooldown
  @Xujiayao

- 更改 Discord 控制台日志频道消息的默认格式
  Change the default format of Discord console log channel messages
  @Xujiayao

- 使用临时文件检查更新以兼容 v1 版本
  Check for updates using temporary files for v1 compatibility
  @Xujiayao

- 使用 JDA.shutdownNow() 解决停止服务器时的挂起问题
  Use JDA.shutdownNow() to resolve hang when stopping the server
  @Xujiayao

## 移除 Removed

- 不再向 Discord 频道发送异常堆栈跟踪
  Exception stack traces are no longer sent to Discord channel
  @Xujiayao

- 移除在 Discord 修改管理员列表的功能
  Remove the function of modifying the admin list in Discord
  @Xujiayao

- 移除超级管理员列表
  Remove super admin list
  @Xujiayao

## 详细信息 Detailed Information

https://github.com/Xujiayao/MCDiscordChat/compare/1.12.1...master