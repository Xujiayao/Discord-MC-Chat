# Discord-MC-Chat (DMCC) 设计文档 (v3)

## 1. 项目概述

Discord-MC-Chat (DMCC) 是一个 Minecraft 模组，旨在为 Discord 和 Minecraft 服务器之间建立一个功能强大、可高度定制的双向通信桥梁。

本次 v3 重构的核心目标是实现一个**统一的、基于"服务端-客户端 (Server-Client)"的通信架构**
。在此架构下，所有运行模式都将复用同一套核心逻辑，以达到最大程度的代码复用、架构一致性和未来的可扩展性。

项目当前只支持 **Minecraft 26.2**，并同时提供 **Fabric** 与 **NeoForge** 两个加载器的构建产物（两个 JAR，见第 11 节）。
整体架构严格遵循平台无关原则：所有核心代码中**不得含有任何启动器专属的调用**，游戏侧仅通过 Mixin 注入，
而 core 与游戏平台之间只通过一个显式的平台适配接口通信（见 3.4）。

## 2. 核心功能需求

### 2.1 双向通信与格式处理

- **全方位消息转发**: 在 Discord 和 Minecraft 之间实时转发普通聊天。不仅如此，系统还会拦截并转发原版系统广播（如 `/say` 和
  `/tellraw @a`），确保 Discord 玩家不会错过任何游戏内公共事件。

- **玩家指令广播与过滤**: 支持将玩家在游戏内执行的命令广播至 Discord。内置强大的正则过滤列表（`excluded_commands`
  ），自动拦截私密指令（如 `/login`, `/msg`），保障服务器与玩家安全。默认列表同时覆盖：私聊类（`/msg`、`/tell`、`/w`
  、`/tellraw`，但通过负向断言放行面向 `@a` 的广播）、队伍消息（`/teammsg`、`/tm`）以及认证类指令（`/login`
  、`/l`、`/register`、`/reg`、`/account`、`/auth`）。为避免刷屏，玩家指令转发另有限速保护（单位时间内最多 10 条）。

- **富文本与多媒体解析**: 支持 Markdown、Discord 表情符号、Unicode 表情符号、`@` 提及、图片、GIF 和超链接在两个平台间的自动双向解析与渲染。
  完整的解析能力矩阵见下表（各能力均可通过 `message_parsing` 下的开关独立控制）：

| 解析能力                                                                      | Discord → MC | MC → Discord | MC → MC | 配置开关                                   |
|:--------------------------------------------------------------------------|:-------------|:-------------|:--------|:---------------------------------------|
| Markdown 样式（`**粗体**`、`*斜体*`、`__下划线__`、`~~删除线~~`）                        | ✅            | —            | ✅       | `markdown`                             |
| 剧透 `\|\|文本\|\|`（MC 端以 obfuscated 渲染，并通过 hover 显示原文）                      | ✅            | —            | ✅       | `markdown`                             |
| 引用行（`> `）与标题行（`#` ~ `######`）                                              | ✅            | —            | ✅       | `markdown`                             |
| 行内代码与围栏代码块（含语言标识）                                                         | ✅            | —            | ✅       | `markdown`                             |
| ANSI 颜色代码块（SGR 0/1/3/4/9 与 30–37 前景色）                                      | ✅            | —            | —        | `ansi_code_blocks`                     |
| Discord 自定义表情 `<:name:id>` / `<a:name:id>`                                 | ✅            | ✅            | ✅       | `custom_emojis`                        |
| 别名表情 `:name:`（jemoji 可识别时）                                                 | ✅            | ✅            | ✅       | `custom_emojis`                        |
| Unicode 表情                                                                 | ✅            | —            | ✅       | `unicode_emojis`                       |
| 用户 / 角色提及（`[@name]`、`[@role]`）                                            | ✅            | ✅            | ✅       | `mentions`                             |
| `@everyone` / `@here`                                                      | ✅            | ✅            | ✅       | `mentions`                             |
| 频道提及（`[#channel]`）                                                        | ✅            | —            | —        | `mentions`                             |
| 超链接（Markdown 链接与裸链接，可点击 + hover 提示）                                         | ✅            | —            | ✅       | `hyperlinks`                           |
| Discord 时间戳 `<t:…>`（t/T/d/D/f/F/s/S/R 全量样式，按 DMCC 语言本地化）                   | ✅            | —            | ✅       | `timestamps`                           |
| 附件（图片 / 视频 / 文件，支持剧透附件）                                                    | ✅            | —            | —        | `attachments`                          |
| 贴纸 (Stickers)                                                              | ✅            | —            | —        | `stickers`                             |
| 嵌入消息 (Embeds)                                                              | ✅            | —            | —        | `embeds`                               |
| 交互组件 (Components)                                                          | ⚠️ 部分        | —            | —        | `components`                           |
| 投票 (Polls)                                                                 | ✅            | —            | —        | `polls`                                |

  > 说明：MC → Discord 方向只做提及与自定义表情的转换，其余内容原样透传；MC → MC 方向用于跨子服转发与原版消息覆盖渲染。
  > 消息在进入 Minecraft 前会按平台做长度截断（主消息最多 6 行 / 200 字符，中日韩字符按 400 计；回复行最多 1 行 /
  > 20~40 字符），「交互组件」目前仅渲染为一个占位标记，尚未解析按钮标签。

- **原版消息覆盖与回声控制**: `overwrite_minecraft_source_messages`
  开启后，DMCC 会取消原版对玩家聊天、`/say`、`/msg`、`/me`、`/tellraw`
  的广播，改为广播 DMCC 解析后的内容（包括发送者自己的客户端）。由于原版本身不会把玩家自己输入的命令回显给该玩家，DMCC 另提供
  `echo_player_command_to_source` 与 `echo_player_change_game_mode_to_source` 两个独立开关，按需补上这一回显。

- **跨子服消息互转**: 在 `standalone + multi_server_client`
  架构下，可将任一子服产生的聊天、指令、事件转发给**其它**子服（`broadcasts.minecraft_to_minecraft.*`，逐事件开关），实现多服玩家"同处一室"的观感。

- **伪用户 Webhook 支持**: 可选通过 Webhook 发送 Minecraft 消息，在 Discord 端完美呈现玩家的独立头像与游戏名。头像来源可依次选择：子服清单中的
  `avatar_url`、`discord.webhook.avatar_url` 模板（默认 `https://mc-heads.net/avatar/{player_name}`），或绑定的 Discord 账户头像（
  `account_linking.discord_user_avatar_for_webhooks`）。Webhook 会复用机器人名下已有的同名 Webhook，不存在时自动创建。

- **自定义格式与国际化**: 允许用户高度自定义所有消息的显示格式（基于 YAML
  模板）。此外，系统内置动态翻译拉取机制，自动从官方源获取各语言资产，精准翻译成就、进度与死亡信息。翻译资产优先读取本地缓存，缺失时按需下载；同时会扫描
  `mods/*.jar` 与已启用的数据包以补充模组与数据包翻译，并以 `en_us` 作为兜底。

- **提及提醒样式**: 跨平台提及可配置为 `action_bar`、`title` 或 `chat` 三种呈现方式（默认 `title`），并附带提示音；
  `@everyone` / `@here` 会通知所有在线玩家。

### 2.2 状态同步与事件通知

- **玩家与服务器事件**: 实时播报玩家加入/离开服务器、死亡、获得进度/成就、切换游戏模式，以及服务器启动/关闭事件。
  每个事件都拥有**独立的频道配置项**（配置为空字符串即关闭该事件的播报）。

- **频道看板与状态监控 (Channel Monitor)**:
    - **文字频道 Topic**: 定期动态更新频道的 Topic（如在线人数、历史总玩家数、各子服状态）。
    - **语音频道名称**: 动态更新指定的语音频道名称，用作服务器状态（Status: Online）和人数（Players: X/Y）的直观数据看板。服务器状态与人数使用**两个独立的语音频道**。
    - 支持的占位符变量：`{online_player_count}`、`{max_player_count}`、`{players_ever_joined}`、
      `{online_server_count}`、`{online_server_list}`、`{server_started_time}`、`{last_update_time}`。
    - 更新间隔由 `channel_updating.interval_minutes` 控制（默认 10 分钟，最小 1 分钟，启动后首次更新约延迟 10 秒）。

- **Bot 状态展示**: 根据游戏内在线人数与状态，实时同步更新 Discord Bot 的活动状态（Activity）和在线状态。
  在线状态为三态语义：无任何子服在线 → 勿扰 (DND)；有子服在线但无玩家 → 空闲 (Idle)；否则 → 在线 (Online)，默认每 30 秒刷新一次。
  活动文案可在 `custom_messages` 中自定义。

- **性能预警**: 定期监控服务器 MSPT（每 tick 毫秒数），超过设定阈值时在 Discord 频道自动发出警告。
  预警分为"首次超阈"、"持续超阈"、"首次恢复"三种不同消息，均可在 `custom_messages` 中自定义，并支持 `{mspt}`、`{threshold}`、
  `{next_check_time}` 占位符。检查间隔采用指数退避（上限约 640 秒）以避免持续告警刷屏，且刚连接不足 60 秒的子服会被豁免，防止开服瞬间误报。

- **自动更新检查**: 自动校验 DMCC 版本并对比兼容性，发现新版本时在指定频道发送更新日志。
  数据来源于项目的 `update/versions.json`，按当前 Minecraft 版本匹配第一个兼容条目；若不存在兼容版本会单独提示。自动通知有节流（同一新版本每
  4 次检查才重复提示），而手动执行 `/update` 则总是返回结果。通知会同时投递到控制台与 Discord 频道，并可在单机模式下转发给游戏内。

- **Vanish 模组兼容**: 检测到 Vanish 模组时，在线玩家列表与人数统计会自动排除所有隐身玩家。

### 2.3 深度管理与控制功能

- **双向控制台日志流 (Console Forwarding)**:
    - **实时推流**: 专设后台线程将 `latest.log` 增量实时切片推送到 Discord 指定控制台频道。默认每秒轮询一次，每批最多
      80 行 / 6000 字符，超长行会被截断；检测到日志轮转（文件标识变化或文件变小）时会自动从新文件头部继续。客户端与 DMCC
      服务端断开期间产生的日志会先入队，重连后补发。
    - **频道直连终端**: 拥有权限的管理员可直接在该 Discord 控制台频道中发送文本，系统会将其等同于控制台指令在游戏内直接执行。
    - **敏感信息过滤**: 考虑到有些人会选择公开此 Discord 频道，因此提供可选但默认启用的敏感信息过滤器（如 IP 地址），确保安全。
      过滤器由正则列表 `console_forwarding.filter_regex` 定义（默认内置 IPv4 规则），命中内容在发送前会被替换为 `redacted`。
    - **起止提示**: 控制台转发的开始与结束提示消息同样可自定义。

- **Discord 侧斜杠命令**: 提供 `/info`, `/stats`, `/log`, `/whitelist` 等管理命令。支持通过 Discord 查阅完整日志文件（以附件形式传输，`.gz`
  归档会先解压再发送）与统计数据排行。命令参数补全由客户端按请求方权限动态生成。

- **Discord 消息互动转发**: 除普通聊天外，Discord 侧的**表情回应 (reaction)**、**消息编辑 (edit)**、**消息删除 (delete)**
  与斜杠命令同样可转发到 Minecraft（`broadcasts.discord_to_minecraft.*`，逐项开关）。

- **系统重载**: 动态重载 DMCC 配置文件（`/dmcc reload` 或 Discord `/reload`）。

- **账户绑定管辖**: 提供完整的账户绑定管理命令（`link` / `unlink` / `links`），支持跨端协同认证。

- **独立模式终端**: `standalone` 模式提供交互式命令行终端，可直接输入全部 DMCC 命令进行运维（详见 3.3）。

## 3. 系统架构

### 3.1 统一的"服务端-客户端 (Server-Client)"架构

DMCC 所有运行模式都基于一个统一的通信模型，该模型包含两个核心组件：

1. **服务端 (Server)**: 整个系统的"大脑"与中央路由。它作为后台服务运行，是**唯一**负责与 Discord API (通过 JDA)
   直接通信的组件。它处理所有跨服路由、身份解析和鉴权凭证下发。**此组件不得包含任何 `net.minecraft` 的导入（反射除外）**。
2. **客户端 (Client)**: 部署在每个 Minecraft 服务器上的"触手"。负责捕获游戏内的所有事件发送给 Server，并接收来自 Server
   的指令（执行本地命令与委托鉴权）。

两者之间通过基于 **Netty** 的 TCP 协议进行通信，使用共享密钥与一次性哈希质询完成身份认证（详见第 9 节）。

> [!IMPORTANT]
> 该传输通道目前**不对内容加密**（明文 TCP，项目未引入 TLS）：共享密钥与一次性质询只解决"对端身份认证"与"防重放"，不能防止中间人窃听。
> 因此请勿将 `multi_server.connection.port` 直接暴露到公网，推荐部署在受信任的内网或经由隧道/代理保护。

### 3.2 运行模式与部署

1. **单体服务器模式 (`single_server`)**: 在同一 JVM 中同时启动内部 Server 和内部 Client，直接连通 Discord。推荐绝大多数普通服主使用。
   此模式下内部通信使用回环临时端口与一次性内存密钥，不落盘、不需配置。
2. **多服务器-客户端模式 (`multi_server_client`)**: 只启动 Client 端，不连接 Discord API，而是作为子服连接到外部独立运行的
   DMCC Server。此模式**不运行 Discord 机器人**，因此其命令能力需由 Standalone 通过 `execute` 委托抵达。
3. **独立模式 (`standalone`)**: 只启动 Server 端（无 Minecraft 进程），作为多服务器架构的中央中枢，负责聚合各个子服的消息与状态，并与
   Discord 进行数据交换。

| 能力          | `single_server` | `multi_server_client` | `standalone` |
|:------------|:----------------|:----------------------|:-------------|
| 连接 Discord  | ✅               | ❌                     | ✅            |
| 捕获游戏内事件     | ✅               | ✅                     | ❌            |
| Minecraft 侧命令 | ✅               | ✅                     | ❌            |
| Discord 斜杠命令 | ✅               | ❌（仅可由 Standalone 委托）   | ✅            |
| 跨子服消息互转     | ❌               | ❌                     | ✅            |
| 交互式终端       | ❌               | ❌                     | ✅            |

### 3.3 独立模式交互式终端

`standalone` 模式会在标准输入上启动一个交互式终端线程，用于无 Minecraft 进程时的日常运维：

- 支持输入**全部** DMCC 命令（命令名与 Discord 侧一致，无需前缀），`execute <at> <command>` 之后的参数会被整体视为一条命令。
- 输入以 `/` 开头时会自动去掉该前缀。
- 命令回复逐行打印到控制台；若命令返回文件（例如 `log`），文件会被写入 `./config/discord_mc_chat/cache/log`。
- 终端不支持 `log` 命令（会提示直接访问 `./logs` 目录），因为终端无法接收附件。
- 启动参数 `--disable-ascii` 可关闭控制台 ANSI 颜色输出。

### 3.4 平台适配层与模组兼容扩展点

DMCC 的代码分为三层，任何新平台都只影响最外面一层：

| 模块                 | 职责                                                             | 是否含游戏/加载器依赖              |
|:-------------------|:---------------------------------------------------------------|:-------------------------|
| `core`             | DMCC 主控、Server/Client、Netty 协议、命令、账户绑定、配置、i18n                | ❌ 完全没有（`net.minecraft` 仅以反射探测） |
| `minecraft-common` | 12 个 Mixin、组件渲染、翻译拉取、Brigadier 命令树、平台适配实现                        | ✅ 只依赖原版类，**不依赖任何加载器 API** |
| `fabric` / `neoforge` | 入口点、模组元数据、加载器专属的模组兼容实现                                          | ✅ 各自的加载器 API              |

- **平台适配接口（`PlatformHost`）**：core 需要"在游戏里做事"时（执行命令、广播消息、下发 OP 等级、通知绑定结果……）
  只调用这一个接口，平台实现再转交给 `MinecraftEventHandler`。历史上这里是一套泛型事件总线，现已完全移除。
- **独立模式没有平台**：`standalone` 注册空实现（`NoopPlatformHost`），因此 core 代码无需到处判空。
- **模组兼容扩展点（`ModIntegration`）**：为特定模组做的兼容（例如 Vanish）实现该接口并注册到 `ModIntegrations`。
  注册发生在对应加载器模块内，因此 **core 永远不知道是哪个模组**。当前唯一实现是 Fabric 侧的 Vanish
  （26.2 的 Vanish 只有 fabric/quilt 构建，NeoForge 侧注册表为空）。新增一个模组兼容 = 写一个类 + 一行注册。
- **能力缺失时的行为**：平台不具备某项能力时（例如未来的服务端插件没有 Mixin、无法拦截 `/say`），
  相关功能记一次日志后跳过，不影响其余功能。
- **两个加载器共享同一份游戏侧源码**：因为 Minecraft 26.1 起官方已不再混淆代码、Fabric 也不再使用
  intermediary，Fabric 与 NeoForge 运行时使用同一套官方名称，因此同一份 Mixin 源码可以直接被两个加载器编译。

## 4. 账户绑定系统 (Account Linking)

账户绑定系统是连接无状态 Discord 社区和有状态 Minecraft 世界的数据总线，也是 DMCC 权限系统的基石。

### 4.1 非对称映射关系

- **一个 Discord 账户可关联多个 Minecraft 账户**（方便玩家管理大号与小号）。
- **一个 Minecraft 账户只能关联一个 Discord 账户**（确保游戏内身份的绝对唯一性）。
- **数据持久化**: 绑定关系作为永久数据存储在 `Server` 端的 `account_linking/links.json` 中，以 Discord ID 为主键。查询时，Discord
  显示名与正版玩家名均通过 API 实时动态解析，离线玩家则保留绑定时的历史快照。
- 内存中同时维护一份 UUID → Discord ID 的反向索引，用于快速判定"该玩家是否已绑定"。任何绑定变更都会立即写盘。

### 4.2 安全绑定工作流 (严格的 MC 优先原则)

为防止在正版/离线服务器中出现身份冒用，**严禁在 Discord 端直接输入游戏名进行绑定**。

1. **玩家进服自动检查**: 玩家每次进入 Minecraft 服务器时，Client 通过网络包请求 Server 检查其 UUID 是否已绑定。
2. **自动生成凭证**: 若未绑定，Server 生成一个 6 位临时验证码（如 `A7X9P2`），Client 在游戏内发送包含内联可点击元素的消息指引玩家在
   Discord 执行 `/link A7X9P2`，验证码 5 分钟有效。验证码字符集刻意排除了易混淆字符（不使用 `I`、`O`、`0`、`1`），且为一次性使用。
3. **手动刷新凭证**: Minecraft 端提供 `/dmcc link` 命令进行验证码续期或重新生成。若原验证码尚未过期，续期会沿用同一个码，仅重置有效期。
4. **确认所有权**: 玩家前往 Discord，使用斜杠命令 `/link A7X9P2` 完成最终绑定。
5. **身份提示**: 已绑定的玩家再次进服时，游戏内会提示其当前绑定的 Discord 用户，并给出解除绑定的可点击入口。

### 4.3 跨平台交互反馈

- **同频渲染**: 玩家在游戏内的聊天名字颜色，将自动同步为其绑定的 Discord 账户的最高角色颜色（`account_linking.use_discord_role_color_for_mc_chats`）。
- **跨平台艾特提醒**: 跨平台提及 (`@`) 时，自动在游戏内通过 Action Bar、Title 或 Chat 高亮提醒对应的玩家，并附带提示音。

## 5. 零信任委托权限系统 (Delegated Authorization)

DMCC v3 彻底摒弃了在 Discord 端硬编码判定命令权限的做法，转而采用 **“身份映射 + 边缘委托鉴权”** 的微服务安全架构。

### 5.1 扩展的权限基准 (-1 到 4)

所有权限均对齐 Minecraft 原生的 OP 等级，并向下扩展：

- **`-1` 级**: 任意 Discord 用户（包括未绑定账号的游客）。适用于无害的查询命令（如 `help`, `info`）。
- **`0` 级**: 基础绑定玩家，或被赋予基础信任身份的未绑定用户，对应 Minecraft 原生的 OP 等级 0。
- **`1 ~ 4` 级**: 对应 Minecraft 原生的 OP 等级 1-4。

### 5.2 身份与 OP 映射 (Mappings)

Discord 用户的身份将通过以下规则在 Server 端结算为一个具体的 **OP 等级凭证**：

1. **`user_mappings`**: 基于特定 Discord User ID（或用户名）指定 OP 等级。
2. **`role_mappings`**: 遍历用户拥有的 Discord 角色，取映射的最高 OP 等级。
3. **绑定账号保底**: 如果用户有绑定 Minecraft 玩家，且上述映射均未命中，则保底获得 OP 0 等级。

*(注：系统取上述条件中命中的最高 OP 等级作为该请求的最终凭证——即 `user_mappings` 与 `role_mappings` 是取最大值的关系，并非"用户映射直接覆盖角色映射"。绑定账号带来的 OP 0 只是兜底下限，不会压低更高的映射结果。)*

### 5.3 核心路由与委托鉴权

当 Discord 用户发起命令时（如 `/reload` 或 `/execute SMP reload`）：

1. **Server 不负责拦截业务逻辑**，它仅计算出该用户的 OP 凭证（例如：OP=2），并将“指令内容”连同“凭证标签”一起打包，通过 Netty
   发送给对应的目标 Client。
2. **Client 边缘鉴权**：目标客户端收到请求后，读取自身本地 `config.yml` 中设定的安全阀值（例如 `reload: 4`），发现凭证（OP
   2）不足，由 Minecraft 客户端直接驳回请求。
3. **动态 UI**：Discord 端斜杠命令的参数补全，同样附带 OP 凭证请求给 Client，Client 仅返回该凭证有权查看的补全项（例如仅为管理员自动补全系统文件名）。

### 5.4 优雅解决白名单悖论 (Whitelist Catch-22)

玩家因白名单进不去服务器 -> 无法证明身份绑定 -> 无法获得白名单。
**解决方案**：
DMCC 在 Client 端提供独立的 `whitelist` 代理命令（默认所需权限为 `0` 级）。玩家加入 Discord 频道并获得基础角色（映射为 OP
0）后，即可在 Discord 执行 `/whitelist <他的ID>` 预先上白名单，进服后再走正常绑定流程。

### 5.5 Minecraft OP 强制同步机制 (`sync_op_level_to_minecraft`)

开启此项后，DMCC 将成为服务器权限的“唯一真理来源”（Single Source of Truth）：

- 每次同步均执行“全量重算 + 强制覆盖”。
- Discord 身份映射将被硬写入服务器的原生 OP 列表中；管理员在游戏内手打的原生 `/op` 授权，会在下一次同步时被 DMCC 依据配置冲刷覆盖。
- 解绑账号将触发 OP 自动降级与回收。
- 触发时机：账号绑定 / 解绑时，以及每个 DMCC 客户端首次有玩家加入时的一次初始全量同步。同步只会下发大于 0 的等级。
- 传输路径：`single_server` 通过本地事件（同一 JVM 内）触达客户端；`standalone` 通过 `OpSync` 网络包下发到对应的各个子服。

## 6. 多服务器配置模型（Standalone + Multi Server）

当 DMCC 处于 `standalone + multi_server_client` 架构时，不同子服务器可使用不同 OP 映射策略。

在 `standalone` 配置的 `user_mappings` 与 `role_mappings` 中，每个条目包含一个顶层 `op_level`（给 Standalone 自身查询使用），以及一个
`server_overrides` 列表字典。若 `server_overrides` 中没有某子服务器的对应条目，则该子服务器自动降级使用顶层 `op_level`
作为默认回退值。

子服务器清单（`multi_server.servers`）同时充当**连接白名单**，并描述了每个子服的展示信息：

| 字段                 | 作用                                          |
|:-------------------|:--------------------------------------------|
| `name`             | 子服唯一名称；Client 必须以此名连接，否则被拒绝                 |
| `minecraft_version` | 该子服的 Minecraft 版本，用于连接校验与更新兼容性比对             |
| `avatar_url`       | 该子服消息在 Discord 端 Webhook 中使用的头像地址（留空则使用默认模板） |
| `color`            | 该子服在 Minecraft 端消息中的展示颜色（如 `yellow`、`green`） |

## 7. 命令列表与权限参考

### 7.1 Discord 斜杠命令

| 命令                       | 默认 OP 等级 | 模组运行 `multi_server_client` | 模组运行 `single_server` | 独立运行 `standalone` | 说明                                                                 |
|:-------------------------|:---------|:---------------------------|:---------------------|:------------------|:-------------------------------------------------------------------|
| `console <command>`      | `0`      | ❌                          | ✅                    | ❌                 | 通过 Discord 模拟控制台执行任意指令。                                             |
| `console <at> <command>` | `0`      | ❌                          | ❌                    | ✅                 | 通过 Discord 模拟远程子服务器控制台执行任意指令。                                       |
| `execute <at> <command>` | `-1`     | ❌                          | ❌                    | ✅                 | 远程执行中枢，仅 Standalone 存在。负责将请求连同鉴权凭证委托给 Client。                       |
| `help`                   | `-1`     | ✅                          | ✅                    | ✅                 | 动态显示用户当前有权执行的可用命令。                                                 |
| `info`                   | `-1`     | ✅ ①                        | ✅                    | ✅                 | 查看状态。Client 只显自身数据，Server 聚合全局数据。                                   |
| `link`                   | `0`      | ✅                          | ✅                    | ❌                 | Minecraft 端生成或刷新 6 位验证码。                                            |
| `link <code>`            | `0`      | ❌                          | ✅                    | ✅                 | Discord 端使用验证码完成最终绑定。                                               |
| `links`                  | `4`      | ❌                          | ✅                    | ✅                 | 管理员查询所有已绑定的账户关系数据。                                                 |
| `log <file>`             | `4`      | ✅ ①                        | ✅                    | ✅                 | 将指定的后台日志文件以附件形式传输至 Discord，支持自动补全。                                  |
| `reload`                 | `4`      | ✅                          | ✅                    | ✅                 | 重新加载 DMCC 配置。                                                       |
| `shutdown`               | `4`      | ❌                          | ❌                    | ✅                 | 仅用于安全关闭 Standalone 中央进程。                                           |
| `stats <type> <stat>`    | `-1`     | ✅ ①                        | ✅                    | ❌                 | 查看统计数据排行。支持基于权限过滤的动态自动补全。                                          |
| `unlink`（Minecraft 端）    | `0`      | ✅                          | ✅                    | ❌                 | 解绑当前 MC 玩家与 Discord 用户的绑定。                                          |
| `unlink`（Discord 端）      | `0`      | ❌                          | ✅                    | ✅                 | 解绑当前 Discord 用户的所有关联玩家。                                             |
| `update`                 | `-1`     | ✅                          | ✅                    | ✅                 | 检查 DMCC 更新。                                                         |
| `whitelist <player>`     | `0`      | ✅ ①                        | ✅                    | ❌                 | DMCC 专用的低权限白名单命令代理。                                                |

> ① `multi_server_client` 模式不运行 Discord 机器人，因此该命令在该模式下**没有原生命令入口**；标记 ✅ 表示它可以由 Standalone 通过
> `execute <at> <command>` 委托抵达对应子服执行。

> v3 移除了 v2 提供的 `/stop` 快捷指令。若需在 Discord 端关闭 Minecraft 服务器，拥有 4 级权限的管理员请直接使用
`/console stop` 模拟控制台发起安全停机。

### 7.2 游戏内命令

游戏内提供 `/dmcc` 命令树（仅 `single_server` 与 `multi_server_client` 存在）。权限同样由各模式 `config.yml` 的
`command_permission_levels` 控制：

| 游戏内命令                        | 默认 OP 等级 | 说明                  |
|:-----------------------------|:---------|:--------------------|
| `/dmcc`                      | `-1`     | 等同于 `/dmcc help`。   |
| `/dmcc help`                 | `-1`     | 显示当前玩家有权执行的命令列表。    |
| `/dmcc info`                 | `-1`     | 查看本服状态。             |
| `/dmcc reload`               | `4`      | 重新加载 DMCC 配置。       |
| `/dmcc stats <type> <stat>`  | `-1`     | 查看统计数据排行。           |
| `/dmcc link`                 | `0`      | 生成或刷新账户绑定验证码。       |
| `/dmcc unlink`               | `0`      | 解除本玩家的账户绑定。         |
| `/dmcc update`               | `-1`     | 检查 DMCC 更新。         |

> `console`、`execute`、`log`、`links`、`whitelist`、`shutdown` **没有**游戏内入口：前两者分别属于单机/独立模式的 Discord
> 侧能力，`log` 依赖附件能力，`links` 属于管理员数据查询，后两者分别是低权限代理与独立进程关闭。

### 7.3 命令可见性与自动补全

- **别名**: DMCC 命令**没有**别名，命令名即唯一入口。
- **默认权限**: 若某命令在配置中缺少对应的 `command_permission_levels` 条目，将默认按 **OP 4** 处理。
- **帮助可见性**: `console`、`links` 以及面向本地发送者的 `whitelist`、`log` 默认不在帮助列表中显示；`link` /
  `unlink` 在发送者既不是游戏内玩家、也不是 Discord 用户时同样隐藏。
- **自动补全范围**: 命令名（`link` / `unlink` 除外）；参数层面客户端支持 `stats`、`log`、`whitelist`，Discord 侧支持
  `execute`、`console`、`log`、`stats`。所有补全结果都会按请求方 OP 凭证过滤，且上限为 25 项。

### 7.4 事件广播矩阵

每个事件都可以独立指定目标频道（`broadcasts.minecraft_to_discord.*`，留空即关闭），并在 Standalone 模式下单独控制是否转发给其它子服（
`broadcasts.minecraft_to_minecraft.*`）：

| 事件            | 频道 / 转发配置键                          |
|:--------------|:------------------------------------|
| 服务器启动         | `server.started`                    |
| 服务器关闭         | `server.stopped`                    |
| 玩家加入          | `player.join`                       |
| 玩家离开          | `player.quit`                       |
| 玩家聊天          | `player.chat`                       |
| 玩家指令          | `player.command`                    |
| 玩家死亡          | `player.die`                        |
| 玩家进度 / 成就     | `player.advancement`                |
| 玩家切换游戏模式      | `player.change_game_mode`           |
| `/say`        | `source.say`                        |
| `/tellraw @a` | `source.tell_raw`                   |
| `/msg @a`     | `source.msg`（含 `/tell`、`/w`）        |
| `/me`         | `source.me`                         |

## 8. 配置文件体系

### 8.1 配置校验与模式一致性

DMCC 对配置文件的完整性与一致性做了强校验，力求在启动阶段就暴露误配置，而不是运行期才报错：

- **模式来自 config.yml 本身**: DMCC 不再有独立的 `mode.yml`。`config.yml` 中的 `mode` 键决定运行模式；
  缺少该键时按环境取默认值（在 Minecraft 内 = `single_server`，独立 JAR = `standalone`）。
- **首次运行直接生成完整配置**: `config.yml` 不存在时，DMCC 会**一次性**按当前环境的模板生成完整文件
  （而不是先让用户去填一个 `mode.yml`），并在控制台打印文件的绝对路径与三步"接下来做什么"的指引。
- **环境与模式匹配校验**: `standalone` 只能在独立 JAR 中运行，`single_server` / `multi_server_client`
  只能在 Minecraft 内运行；不匹配时会给出明确原因，而不是静默异常。
- **模板版本校验**: `config.yml` / `custom_messages` 中的 `version` 必须与当前 DMCC 版本一致，否则提示前往文档升级配置。
- **键完整性校验**: 会逐项比对模板，报告**缺失的键**、**未识别的键**（多余键）与**类型不匹配**的键。
  因此把 `mode` 改成 `multi_server_client` 后，DMCC 会直接告诉你该模板需要哪些键。
- **未修改提醒**: 对必须由用户填写的关键项（`discord.bot.token`、`multi_server.name`、
  `multi_server.connection.shared_secret`）以及仍未从模板改动的键给出提醒。
- **语言自动检测**: 模板中的 `language: "to_be_auto_replaced"` 会在首次加载时被自动替换为检测到的语言代码。

### 8.2 语言与自定义消息

- 内置 `en_us` 与 `zh_cn` 内部翻译；遇到不支持的语言会给出提示并引导贡献翻译。
- 自定义消息文件（`custom_messages/<lang>.yml`）首次运行会从内置模板生成，并按模板校验键结构；允许保留未修改的默认值。
- `custom_messages` 承载了全部用户可见文案：事件播报模板、覆盖模式模板、Discord 侧事件行、MSPT 告警、控制台转发起止提示、
  频道 Topic 与语音频道名称、Bot 活动文案、Webhook 用户名与内容等。

### 8.3 消息模板占位符参考

消息模板使用**具名花括号占位符** `{name}`（注意：这不是 Gradle 构建期展开的 `${...}` 变量）。模板条目为"片段列表"，每个片段支持
`text`、`bold`、`color` 三个字段，颜色可用 Minecraft 颜色名（如 `yellow`、`gray`）或十六进制值（如 `#FFAA00`）。

| 占位符                                                                                                                              | 适用范围                    |
|:---------------------------------------------------------------------------------------------------------------------------------|:------------------------|
| `{message}`                                                                                                                      | 玩家消息内容插入点（会被解析后的富文本替换）  |
| `{display_name}` / `{effective_name}`                                                                                            | 发言者显示名（含伪用户风格）          |
| `{player_name}` / `{player_uuid}`                                                                                                | 玩家名与 UUID                |
| `{role_color}`                                                                                                                   | 发言者的 Discord 最高角色颜色     |
| `{server}` / `{server_color}`                                                                                                    | 子服名称与颜色                 |
| `{command}` / `{emoji}` / `{action}`                                                                                             | 指令内容 / 表情回应 / `/me` 动作文本 |
| `{death_message}` / `{title}` / `{description}` / `{mode}`                                                                       | 死亡信息、进度标题与描述、游戏模式       |
| `{online_player_count}` / `{max_player_count}` / `{players_ever_joined}`                                                         | 在线人数、上限、历史玩家总数          |
| `{online_server_count}` / `{online_server_list}` / `{server_started_time}` / `{last_update_time}`                                | 频道看板变量                  |
| `{mspt}` / `{threshold}` / `{next_check_time}`                                                                                   | MSPT 告警                 |
| `{player_name}`（用于 `discord.webhook.avatar_url`）                                                                                 | Webhook 头像模板            |

## 9. 网络与安全

- **传输层**: Netty TCP，4 字节长度前缀分帧，单帧上限 1 MiB；载荷使用 Java 原生序列化。**当前未启用 TLS**，请勿将端口直接暴露到公网。
- **身份认证**: Server 下发 16 字符随机质询（`SecureRandom`），Client 返回 `SHA-256(质询 + 共享密钥)`，Server 比对通过后才接受连接。
  共享密钥在 `single_server` 模式下由进程内随机生成（回环地址 + 临时端口），在其它模式下来自配置。
- **准入校验**: 除密钥外，还会校验客户端名称是否在子服白名单内、是否已有同名连接在线、DMCC 版本是否匹配、以及 Minecraft 版本是否匹配；任一不符即断开并给出明确原因。
- **登录后配置下发**: 认证成功时 Server 会把语言、`overwrite_minecraft_source_messages` 与控制台转发开关下发给 Client，保证两端行为一致。
- **心跳与超时**: Server 30 秒读空闲即断开该连接；Client 15 秒写空闲发送 KeepAlive 保活。
- **断线重连**: Client 采用指数退避重连（2 秒起步，上限 512 秒），TCP 建连成功后重置；若收到 Server 的主动断开包（如密钥错误、重名）则停止重连。
- **延迟统计**: 连接延迟由 TCP 建连耗时与 `LatencyPing` 往返共同给出，用于 `/info` 展示与控制台日志。

## 10. 日志与运维

- **DMCC 自有日志**: `standalone` 模式每次运行都会在 `logs/` 下新建 `DMCC_<时间戳>.log`，记录带时间戳、线程名与等级的日志；控制台输出按等级着色
  (INFO/WARN/ERROR)，可用 `--disable-ascii` 关闭。
- **`/info` 指标**: 包含 DMCC 版本 / 运行模式 / 运行时长 / JVM 内存占用；Discord 连接状态 / 心跳延迟 / REST 延迟；以及每个子服的连接延迟、
  Minecraft 版本、在线玩家数（含逐玩家延迟，自动排除隐身玩家）、历史玩家总数、服务器 TPS 与 MSPT、运行时长、JVM 内存等。
- **无头环境检测**: 未检测到控制台时会提示 DMCC 正在无头模式下运行，并明确不支持双击 JAR 启动，给出命令行启动方式。
- **优雅关停**: `shutdown.graceful_shutdown` 控制退出时的等待策略（默认等待任务收尾，最长 10 分钟；关闭该开关后最多等待 5 秒）。
- **接口限流与容错**: 对 Discord 频道更新等高频接口采用静默丢弃策略避免刷屏；Discord 端信息查询与自动补全均有超时保护，超时后以"无响应"提示而非阻塞主流程。

## 11. 构建与部署

### 11.1 构建产物

`./gradlew build` 会在根目录 `build/` 下产出**一个通用 JAR**：

| 产物                          | 用途                                                       |
|:----------------------------|:---------------------------------------------------------|
| `Discord-MC-Chat-<版本>.jar`  | **唯一需要分发的文件**：Fabric、NeoForge 与独立模式三种用法都由它承担                 |

同一个文件之所以三种用法通吃，是因为它**同时**带有两套加载器元数据与两个入口点，而每个加载器只会读自己的那一套：

- 放进 Fabric 服务端的 `mods/` → Fabric 读取 `fabric.mod.json`，加载 `...fabric.FabricDMCC`；
- 放进 NeoForge 服务端的 `mods/` → NeoForge 读取 `META-INF/neoforge.mods.toml`，加载 `...neoforge.NeoForgeDMCC`；
- 用 `java -jar Discord-MC-Chat-<版本>.jar` 启动 → 走 `Main-Class`，作为独立模式（standalone）的中央中枢运行（详见 3.3）。

核心逻辑（`core`）在包内只有一份，即以 `core` 的 shadow JAR 为基底，再把两个加载器的入口类与元数据合并进去；
两个加载器共用的 `minecraft-common` 类与 `dmcc.mixins.json` 也只会保留一份。

> 两个加载器各自的中间 JAR 位于 `fabric/build/libs/` 与 `neoforge/build/libs/`（不进入根 `build/`），
> 仅在排查"某个加载器是否加载了正确入口"时才会用到，正常分发不需要它们。

### 11.2 开发环境

- `./gradlew :fabric:runServer` / `./gradlew :neoforge:runServer` 分别启动 Fabric 与 NeoForge 的
  开发服务端，配置目录位于对应模块的 `run/` 下。
- `./gradlew :core:test` 运行核心模块的单元测试（JUnit）。
- 首次导入 IDE 或同步 Gradle 项目时，Gradle 会通过 `foojay-resolver-convention` 自动下载
  ModDevGradle 资产下载器所需的 JDK 21（模组本身仍编译为 Java 25）；这是 IDE 同步能通过的前提。
- 版本号统一由 `gradle.properties` 控制：`mod_version`、`minecraft_version`、`loader_version`、
  `neo_version`、`moddev_version`、`junit_version` 等。
