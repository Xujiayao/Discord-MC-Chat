# Discord-MC-Chat (DMCC) 重构设计文档

## 1. 项目概述

Discord-MC-Chat (DMCC) 是一个 Minecraft 模组，旨在为 Discord 和 Minecraft 服务器之间建立一个功能强大、可高度定制的双向通信桥梁。

本次重构的核心目标是实现一个**统一的、基于“服务端-客户端 (Server-Client)”的通信架构**
。在此架构下，所有运行模式都将复用同一套核心逻辑，以达到最大程度的代码复用、架构一致性和未来的可扩展性。

项目初期将优先实现对 **NeoForge 1.21.10** 的兼容。但为了未来能够无缝支持 Fabric，整体架构设计严格遵循平台无关原则，所有核心代码中
**不得含有任何启动器专属的调用**，仅通过 Mixin 进行注入。

## 2. 核心功能需求

### 2.1 通信功能

- **双向消息转发**: 在 Discord 和 Minecraft 之间实时转发聊天消息。
- **格式兼容**: 支持 Markdown、Discord 表情符号、提及和超链接在两个平台间的自动转换。
- **Webhook 支持**: 可选通过 Webhook 以玩家头像发送 Minecraft 消息到 Discord。
- **自定义格式**: 允许用户高度自定义所有消息的显示格式。

### 2.2 事件通知

- **玩家事件**: 玩家加入/离开服务器、死亡、获得进度/成就。
- **服务器事件**: 服务器启动/关闭。
- **性能监控**: 当服务器 MSPT (每 tick 毫秒数) 超过阈值时，在 Discord 发出警告。

### 2.3 管理功能

- 通过 Discord 和游戏内命令查询服务器状态。
- 通过 Discord 和游戏内命令执行 Minecraft 控制台命令。
- 查看和过滤服务器日志。
- 重载 DMCC 配置文件。
- 远程启动/关闭子服务器（仅限多服务器模式）。

### 2.4 账户链接功能

- **身份绑定**: 支持将 Minecraft 玩家账户与 Discord 账户进行一对一关联。
- **角色颜色同步**: 游戏内聊天消息显示与玩家 Discord 角色的颜色一致。
- **跨平台提及**: 在 Discord 中`@`用户时，对应的 Minecraft 玩家会收到游戏内提醒。
- **权限同步**: 可选基于 Discord 角色自动授予玩家游戏内的 OP 权限。

## 3. 系统架构

### 3.1 统一的“服务端-客户端 (Server-Client)”架构

DMCC 所有运行模式都基于一个统一的通信模型，该模型包含两个核心组件：

1. **服务端 (Server)**: 整个系统的“大脑”。它作为后台服务运行，是**唯一**负责与 Discord API (通过 JDA)
   直接通信的组件。它处理所有核心逻辑，如消息格式化、命令解析和权限验证。**此组件在任何情况下都不得包含任何 `net.minecraft`
   的导入（反射除外）**，以确保其可以在没有 Minecraft 环境的情况下独立运行。
2. **客户端 (Client)**: 部署在每个 Minecraft 服务器上的“触手”。它作为 Minecraft 模组运行，负责捕获游戏内的所有事件，将其发送给
   **服务端 (Server)**。并执行来自 **服务端 (Server)** 的指令和本地命令。

两者之间通过基于 **Netty** 的 TCP 协议进行通信，使用 **JSON** 对数据包进行序列化和反序列化，并采用**哈希质询-响应机制**
进行安全认证。

### 3.2 运行模式与部署

1. **单体服务器模式 (`single_server`)**: 在后台**同时启动一个内部服务端和一个内部客户端**。内部客户端自动通过本地回环地址连接到内部服务端。这是为单个
   Minecraft 服务器提供的开箱即用的解决方案。
2. **多服务器-客户端模式 (`multi_server_client`)**: **只启动一个客户端**，此客户端会连接到一个外部独立运行的服务端。用于将多个
   Minecraft 服务器连接到一个中央服务端。
3. **独立模式 (`standalone`)**: **只启动一个服务端**，监听网络端口，等待一个或多个外部客户端连接。作为多服务器架构的中央“大脑”而存在。

### 3.3 配置文件策略

1. **`mode.yml`**: 用户首先在此文件选择一种运行模式。若在非 Minecraft 环境下直接运行 JAR，则自动认定为 `standalone` 模式。
2. **`config.yml`**: DMCC 会根据选择的模式，从内部模板生成一份对应的 `config.yml`。此配置文件将被严格验证。`standalone`
   模式首次启动时，会自动在 `config.yml` 中生成一个高强度的 `shared_secret`，用户需将其手动同步到所有 `client` 的配置文件中。

## 4. 网络协议与安全

### 4.1 哈希质询-响应认证机制

为保证通信安全，防止共享密钥在传输中被窃取及防止重放攻击，系统采用一次性的哈希质询-响应机制进行认证。

**核心协议包 (`Packets.java`):**

```java
// 客户端发起连接，表明身份和版本
public record ClientHello(String serverName, String version) implements Packet {
}

// 服务端收到ClientHello后，发送随机质询
public record ServerChallenge(String challenge) implements Packet {
}

// 客户端收到质询后，计算并发送响应
public record ClientResponse(String responseHash) implements Packet {
}

// 服务端验证通过后，发送最终的成功响应
public record HandshakeSuccess(String messageKey, String language) implements Packet {
}

// 任何阶段失败，都可以发送一个失败响应
public record HandshakeFailure(String messageKey) implements Packet {
}
```

**认证流程:**

1. **客户端发起连接**: `Client` 连接成功后，立即发送 `ClientHello` 包，包含其在 `config.yml` 中配置的 `serverName` 和自身的
   DMCC 版本号。
2. **服务端验证与质询**: `Server` 收到 `ClientHello` 后：
   a. **白名单检查**: `Server` 检查 `serverName` 是否在 `config.yml` 的 `multi_server.servers` 列表中。如果不在，返回
   `HandshakeFailure("handshake.error.not_whitelisted")` 并断开连接。
   b. **版本检查**: 对比 `Client` 版本和自身版本。如果不兼容，返回 `HandshakeFailure("handshake.error.invalid_version")`
   并断开连接。
   c. **生成质询**: 如果通过，`Server` 生成一个唯一的、一次性的随机字符串作为 `challenge`，并暂存于内存中。
   d. **发送质询**: `Server` 向 `Client` 发送 `ServerChallenge` 包。
3. **客户端计算响应**: `Client` 收到 `ServerChallenge` 后：
   a. 从 `config.yml` 读取 `shared_secret`。
   b. 使用 **HMAC-SHA256** 算法计算哈希值: `responseHash = hmac_sha256(key = shared_secret, data = challenge)`。
   c. 向 `Server` 发送 `ClientResponse` 包。
4. **服务端验证响应**: `Server` 收到 `ClientResponse` 后：
   a. 取出内存中为该 `Client` 暂存的 `challenge`。
   b. 以完全相同的方式计算出 `expectedResponseHash`。
   c. **比对哈希**: 如果不一致，返回 `HandshakeFailure("handshake.error.authentication_failed")` 并断开连接。
5. **握手成功**: 如果哈希一致，认证通过。`Server` 发送 `HandshakeSuccess` 包，其中包含配置的全局 `language`。

### 4.2 全局语言同步

为确保所有实例的输出信息语言统一，系统采用握手时同步的策略：

1. `Client` 启动时，默认加载 `en_us` 语言文件。
2. 当 `Client` 收到 `Server` 发来的 `HandshakeSuccess` 包后，它会解析出 `language` 字段（如 `zh_cn`）。
3. 如果该语言与当前加载的语言不同，`Client` 会调用 `I18nManager.load("zh_cn")` 重新加载对应的语言文件。
4. 从此，该 `Client` 生成的所有消息都将使用与 `Server` 统一的语言。

## 5. 命令系统设计

### 5.1 命令职责划分

为消除歧义，命令根据其作用域和目标被严格划分：

| 意图                | `standalone`/Discord 命令       | 游戏内命令 (`/dmcc ...`) | 说明                           |
|:------------------|:------------------------------|:--------------------|:-----------------------------|
| 启动/停止**远程MC服务器**  | `start <name>`, `stop <name>` | （不可用）               | 管理 `config.yml` 中定义的外部服务器进程。 |
| 启用/禁用**本地DMCC功能** | （不可用）                         | `enable`, `disable` | 管理当前 MC 服务器上 DMCC 模组自身的运行状态。 |
| 重载**本地DMCC配置**    | `reload`                      | `reload`            | 应用内重载配置并重启所有服务，Bot 会短暂离线。    |
| 关闭**整个DMCC应用**    | `shutdown`                    | （不可用）               | 彻底关闭 `standalone` 的 JVM 进程。  |

### 5.2 命令可用性矩阵

| 命令            | 权限 (默认)    | `multi_server_client` | `standalone` (终端) | `single_server` | Discord | 说明与行为差异                                                                    |
|:--------------|:-----------|:----------------------|:------------------|:----------------|:--------|:---------------------------------------------------------------------------|
| `help`        | `everyone` | ✅                     | ✅                 | ✅               | ✅       | 动态显示当前环境可用且有权执行的命令。                                                        |
| `info`        | `everyone` | ✅                     | ✅                 | ✅               | ✅       | **行为**: `client` 只显示自身状态。`server` 端显示全局信息。                                 |
| `update`      | `everyone` | ✅                     | ✅                 | ✅               | ✅       | **本地执行**: 每个实例独立检查自身版本。                                                    |
| `stats`       | `everyone` | ✅                     | ✅                 | ✅               | ✅       | **服务端聚合**: `client` 收到命令后，将请求转发给 `server` 实时处理。                            |
| `console`     | `admin`    | ✅                     | ❌                 | ✅               | ✅       | 执行**所在MC服务器**的命令。`standalone` 无此命令。                                        |
| `execute`     | `admin`    | ✅                     | ✅                 | ❌               | ✅       | **`multi-server` 核心**: `single_server` 无此命令。`client` 端执行时会将请求转发给 `server`。 |
| `log`         | `admin`    | ✅                     | ✅                 | ✅               | ✅       | 获取各自实例的日志。可通过 `execute` 获取远程日志。                                            |
| `enable`      | `admin`    | ✅                     | ❌                 | ✅               | ❌       | **本地作用**: 启用当前服务器上的 DMCC 模组。                                               |
| `disable`     | `admin`    | ✅                     | ❌                 | ✅               | ❌       | **本地作用**: 禁用当前服务器上的 DMCC 模组。                                               |
| `reload`      | `admin`    | ✅                     | ✅                 | ✅               | ✅       | **应用内重启**: 通过 `shutdown()` + `init()` 实现。                                  |
| `start <srv>` | `admin`    | ❌                     | ✅                 | ❌               | ✅       | **`standalone` 独有**: 启动 `config.yml` 中定义的子服务器。                             |
| `stop <srv>`  | `admin`    | ❌                     | ✅                 | ❌               | ✅       | **`standalone` 独有**: 停止子服务器（通常通过 `execute` 执行 `console stop` 实现）。          |
| `shutdown`    | `admin`    | ❌                     | ✅                 | ❌               | ✅       | **`standalone` 独有**: 关闭 `standalone` 进程。                                   |

### 5.3 关键命令逻辑详解

#### `stats` (纯实时查询模型)

1. **转发请求**: `client` 收到命令后，封装 `ForwardedCommandPacket` 发送给 `server`。
2. **广播数据请求**: `server` 收到请求后，**立即**向所有 `client` 广播 `StatsRequestPacket`。
3. **响应数据**: 各 `client` 查询本地统计数据，并回传 `StatsDataPacket`。
4. **聚合与返回**: `server` 在短暂超时时间内聚合所有响应，生成排行榜，并通过 `CommandResponsePacket` 定向返回给最初请求的
   `client`。

#### `update` (本地独立检查模型)

- 此命令**始终在接收到命令的实例上本地执行**。
- 返回的更新信息将明确指出这是一个**手动操作**，需要管理员在所有实例上手动替换 `.jar` 文件。

#### `start <server>` (事件驱动反馈模型)

1. 管理员在 `standalone` 或 Discord 执行 `/start SMP`。
2. `standalone` 回复临时消息（“正在启动...”）并执行预设的 `start_command`。
3. 子服务器的 DMCC `client` 启动并自动连接 `standalone`。
4. `standalone` 将“新客户端注册成功”的内部事件作为该服务器成功启动的信号，并更新状态消息。

## 6. 权限管理模型 (双轨制)

1. **原生继承模型 (Minecraft -> DMCC)**: 游戏内 OP 等级高于 `minecraft_op_level_requirement` 的玩家，自动成为“DMCC
   管理员”。这是权限的基础来源。
2. **角色同步模型 (Discord -> Minecraft)**: 可选功能。可配置 Discord 角色到游戏内 OP 等级的映射。`Server` 组件会周期性检查并
   **通过向 `Client` 发送指令**来自动授予/撤销玩家的游戏内 OP 权限。一旦玩家因此获得足够 OP 等级，也将通过路径一成为“DMCC
   管理员”。
