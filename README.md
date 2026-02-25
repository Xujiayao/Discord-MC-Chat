# Discord-MC-Chat (DMCC) 重构设计文档

## 1. 项目概述

Discord-MC-Chat (DMCC) 是一个 Minecraft 模组，旨在为 Discord 和 Minecraft 服务器之间建立一个功能强大、可高度定制的双向通信桥梁。

本次重构的核心目标是实现一个**统一的、基于"服务端-客户端 (Server-Client)"的通信架构**
。在此架构下，所有运行模式都将复用同一套核心逻辑，以达到最大程度的代码复用、架构一致性和未来的可扩展性。

项目初期将优先实现对 **NeoForge 1.21.10** 的兼容。但为了未来能够无缝支持 Fabric，整体架构设计严格遵循平台无关原则，所有核心代码中
**不得含有任何启动器专属的调用**，仅通过 Mixin 进行注入。

## 2. 核心功能需求

### 2.1 通信功能

- **双向消息转发**: 在 Discord 和 Minecraft 之间实时转发聊天消息。
- **格式兼容**: 支持 Markdown、Discord 表情符号、Unicode 表情符号、`@` 提及、图片和超链接在两个平台间的自动转换。
- **Webhook 支持**: 可选通过 Webhook 以玩家头像发送 Minecraft 消息到 Discord。
- **自定义格式**: 允许用户高度自定义所有消息的显示格式。

### 2.2 事件通知

- **玩家事件**: 玩家加入/离开服务器、死亡、获得进度/成就。
- **服务器事件**: 服务器启动/关闭。
- **性能监控**: 当服务器 MSPT (每 tick 毫秒数) 超过阈值时，在 Discord 发出警告。

### 2.3 管理功能

- 通过 Discord 和游戏内命令查询服务器状态。
- 通过 Discord 执行 Minecraft 控制台命令。
- 通过 Discord 查看和过滤（如玩家 IP 地址）服务器日志。
- 重载 DMCC 配置文件。
- 远程启动/关闭子服务器（仅限多服务器模式）。

### 2.4 账户链接功能

- **身份绑定**: 支持将 Minecraft 玩家账户与 Discord 账户进行关联（一个 Discord 账户可关联多个 Minecraft 账户，但一个
  Minecraft 账户只能关联一个 Discord 账户）。
- **角色颜色同步**: 游戏内聊天消息显示与玩家 Discord 角色的颜色一致。
- **跨平台提及**: 在 Discord 中`@`用户时，对应的 Minecraft 玩家会收到游戏内提醒。
- **权限同步**: 可选基于 Discord 角色自动授予玩家游戏内的 OP 权限。

## 3. 系统架构

### 3.1 统一的"服务端-客户端 (Server-Client)"架构

DMCC 所有运行模式都基于一个统一的通信模型，该模型包含两个核心组件：

1. **服务端 (Server)**: 整个系统的"大脑"。它作为后台服务运行，是**唯一**负责与 Discord API (通过 JDA)
   直接通信的组件。它处理所有核心逻辑，如消息格式化、命令解析和权限验证。**此组件在任何情况下都不得包含任何 `net.minecraft`
   的导入（反射除外）**，以确保其可以在没有 Minecraft 环境（Standalone 模式）的情况下独立运行。
2. **客户端 (Client)**: 部署在每个 Minecraft 服务器上的"触手"。它作为 Minecraft 模组运行，负责捕获游戏内的所有事件，将其发送给
   **服务端 (Server)**。并执行来自 **服务端 (Server)** 的指令和本地命令。

两者之间通过基于 **Netty** 的 TCP 协议进行通信。

### 3.2 运行模式与部署

1. **单体服务器模式 (`single_server`)**: 在后台**同时启动一个内部服务端和一个内部客户端**。内部客户端自动通过本地回环地址连接到内部服务端。这是为单个
   Minecraft 服务器提供的开箱即用的解决方案。
2. **多服务器-客户端模式 (`multi_server_client`)**: **只启动一个客户端**，此客户端会连接到一个外部独立运行的服务端。用于将多个
   Minecraft 服务器连接到一个中央服务端。
3. **独立模式 (`standalone`)**: **只启动一个服务端**，监听网络端口，等待一个或多个外部客户端连接。作为多服务器架构的中央"
   大脑"而存在。

### 3.3 配置文件策略

1. **`mode.yml`**: 用户首先在此文件选择一种运行模式。若在非 Minecraft 环境下直接运行 JAR，则自动认定为 `standalone` 模式。
2. **`config.yml`**: DMCC 会根据选择的模式，从内部模板生成一份对应的 `config.yml`。此配置文件将被严格验证。`standalone`
   模式首次启动时，会自动在 `config.yml` 中生成一个高强度的 `shared_secret`，用户需将其手动同步到所有 `client` 的配置文件中。

## 4. 网络协议与安全

为保证通信安全，防止共享密钥在传输中被窃取及防止重放攻击，系统采用一次性的哈希质询-响应机制进行认证。

**认证流程:**

1. **客户端发起连接**: `Client` 连接成功后，发送 `HandshakePacket`，包含其在 `config.yml` 中配置的 `serverName`、自身的
   DMCC 版本号和 Minecraft 版本号。
2. **服务端验证与质询**: `Server` 收到 `HandshakePacket` 后：
   a. **模式检查**: 若 `Server` 处于 `single_server` 模式，且 `serverName` 不为 `Internal`，则返回 `DisconnectPacket`
   （包含原因）并断开连接。
   b. **白名单检查**: 若 `Server` 处于 `standalone` 模式，检查 `serverName` 是否在 `config.yml` 的 `multi_server.servers`
   列表中。如果不在，返回 `DisconnectPacket`（包含原因）并断开连接。
   c. **版本检查**: 对比 `Client` 的 DMCC 版本和 Minecraft 版本与 `Server` 端的期望值。如果不兼容，返回 `DisconnectPacket`
   （包含原因）并断开连接。
   d. **生成质询**: 如果通过，`Server` 生成一个唯一的、一次性的随机字符串作为 `nonce`，并暂存于内存中。
   e. **发送质询**: `Server` 向 `Client` 发送 `ChallengePacket`，包含该随机 `salt`。
3. **客户端计算响应**: `Client` 收到 `ChallengePacket` 后：
   a. 从 `config.yml` 读取 `shared_secret`。
   b. 使用 SHA-256 计算哈希值：`SHA-256(salt + shared_secret)`。
   c. 向 `Server` 发送 `AuthResponsePacket`，包含该哈希值。
4. **服务端验证响应**: `Server` 收到 `AuthResponsePacket` 后：
   a. 取出内存中为该 `Client` 暂存的 `nonce`。
   b. 以同样的方式计算 `SHA-256(nonce + shared_secret)`。
   c. **比对哈希**: 如果不一致，返回 `DisconnectPacket`（包含原因）并断开连接。
5. **握手成功**: 如果哈希一致，认证通过。`Server` 发送 `LoginSuccessPacket`，其中包含服务端所配置的 `language`
   ，作为全局同步的语言，客户端收到后会重载语言。

## 5. 命令系统设计

### 5.1 权限模型

DMCC 采用**基于数值 OP 等级的细粒度权限模型**。每个命令在 `config.yml` 中都有一个独立的 `command_permission_levels`
配置项，映射到 Minecraft 原生的 OP 等级 (0-4)。

对于 Minecraft 游戏内命令（通过 `/dmcc` 执行），使用 Brigadier 的 `.requires(source -> source.hasPermission(level))`
进行原生权限检查。

对于 Discord 斜杠命令，权限通过已绑定的 Minecraft 账户的 OP 等级来判定。

**关于 `execute` 命令的特殊说明**：`execute` 命令本身的权限等级设为 `0`
，因为它仅作为转发中枢，实际的权限校验将根据执行者的身份，委托给远程客户端上的具体命令处理（例如：允许任何人执行
`/execute SMP help`，但拒绝无权限者执行 `/execute SMP reload`。自动补全提供的命令也应根据执行者的权限动态调整）。

**OP 等级参考:**

- Level 0 (normal): 无特殊权限。
- Level 1 (moderator): 可以绕过出生点保护。
- Level 2 (gamemaster): 可以使用更多命令和命令方块。
- Level 3 (admin): 可以使用多人游戏管理相关的命令。
- Level 4 (owner): 可以使用所有命令，包括服务器管理相关的命令。

可选的**角色同步**功能可根据 Discord 角色自动授予/撤销玩家的游戏内 OP 权限，间接赋予其对应的 DMCC 命令权限。

### 5.2 命令列表

| 命令                       | 默认 OP 等级 | 模组运行 `multi_server_client` | 模组运行 `single_server` | 独立运行 `standalone` | 说明与行为差异                                                                                            |
|:-------------------------|:---------|:---------------------------|:---------------------|:------------------|:---------------------------------------------------------------------------------------------------|
| `help`                   | `0`      | ✅                          | ✅                    | ✅                 | 动态显示当前环境可用且有权执行的命令。可用命令根据用户的权限等级动态显示。                                                              |
| `info`                   | `0`      | ✅                          | ✅                    | ✅                 | 在 `client` 执行只显示自身状态。在 `server` 端执行则显示全局信息（包含所有在线客户端和 Discord 连接状态）。                               |
| `stats <type> <stat>`    | `0`      | ✅                          | ✅                    | ❌                 | 查看 Minecraft 统计数据。支持自动补全可用的统计数据类型与名称。                                                              |
| `reload`                 | `4`      | ✅                          | ✅                    | ✅                 | 通过 `shutdown()` + `init()` 实现。                                                                     |
| `log <file>`             | `4`      | ✅                          | ✅                    | ✅                 | 获取自身实例的日志文件。Discord 独占命令（结果为文件附件）。支持 `.log` 与 `.log.gz`，自动补全可用文件名。可通过 `execute` 命令获得 `client` 的日志。 |
| `execute <at> <command>` | `0`      | ❌                          | ❌                    | ✅                 | 仅 `standalone`（Server）存在。`at` 可为具体服务器名称或 `all_online_clients`。其本身的权限等级为 0，实际鉴权委托给目标客户端上的具体命令。      |
| `shutdown`               | `4`      | ❌                          | ❌                    | ✅                 | 关闭 `standalone` 应用程序。                                                                              |

**备注：**

- `log <file>` 在 `LocalCommandSender`（终端和游戏内）的帮助列表中不显示，但仍可通过 `execute` 远程获取客户端日志。
- `execute` 命令的 `command` 参数在终端解析时，`<at>` 之后的所有内容视为单一参数。

### 5.3 Discord 斜杠命令自动补全

所有接受参数的 Discord 斜杠命令均支持自动补全：

- `execute <at>`: 建议 `all_online_clients` 和所有在线客户端名称。
- `execute <command>`: 向所有在线客户端发送 `CommandAutoCompleteRequestPacket` 实时收集建议。
- `log <file>`: 列出可用日志文件。
- `stats <type>` / `stats <stat>`: 根据指定的统计类型自动补全可用的统计数据。

## 6. 权限管理模型

1. **原生数值模型 (config → Minecraft/DMCC)**: 每个 DMCC 命令在 `command_permission_levels` 中配置一个 0-4 的 OP
   等级数值。游戏内通过 Brigadier 原生权限检查，Discord 端通过绑定账户的 OP 等级判定，Standalone 控制台直接为最高等级。
2. **角色同步模型 (Discord → Minecraft)**: 可选功能。可配置 Discord 角色到游戏内 OP 等级的映射。`Server` 组件会周期性检查并
   **通过向 `Client` 发送指令**来自动授予/撤销玩家的游戏内 OP 权限。
