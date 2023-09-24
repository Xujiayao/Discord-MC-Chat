# Changelog 更新日志

## MC-Discord-Chat 2.2.2 for Minecraft 1.14+ - 2023/9/24

MCDC now accurately pushes MCDC version updates by checking the compatibility of the server's Minecraft version.

This new feature ensures no more situations where an old MCDC with an old Minecraft version pushes a new MCDC version that doesn't support that particular Minecraft version.

MCDC 现在通过检查服务器 Minecraft 版本的兼容性来准确推送 MCDC 版本更新。

此新功能可确保不再出现旧 Minecraft 版本中安装的旧 MCDC 推送不支持该特定 Minecraft 版本的新 MCDC 版本的情况。

## New Features 新特性

- Compatible with version 1.20.2 (#189)
  兼容 1.20.2 版本

- Push MCDC version updates accurately by checking server Minecraft version compatibility
  通过检查服务器 Minecraft 版本兼容性来准确推送 MCDC 版本更新

## Changes 更改

- Fix channel topic monitor not using the correct level name for getting stats information (#139)
  修复频道主题监视器不使用正确的存档名称来获取统计信息

- Optimize JAR file size
  优化 JAR 文件大小

- Force not to use cache to obtain player profile
  强制不使用缓存来获取玩家资料

## Removed 移除

- No longer compatible with version 1.19.3
  不再兼容 1.19.3 版本

## Contributors 贡献者

- @Xujiayao

## Detailed Information 详细信息

https://github.com/Xujiayao/MC-Discord-Chat/compare/2.2.1...2.2.2