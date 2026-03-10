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

- 通过 Discord、游戏内命令和终端命令查询服务器状态。
- 通过 Discord 执行 Minecraft 控制台命令。
- 通过 Discord 查看和过滤（如玩家 IP 地址）服务器日志。
- 重载 DMCC 配置文件。
- 远程启动/关闭子服务器（仅限多服务器模式）。
- 提供账户绑定管理命令（`link` / `unlink` / `links`）并支持跨端协同。

## 3. 系统架构

### 3.1 统一的"服务端-客户端 (Server-Client)"架构

DMCC 所有运行模式都基于一个统一的通信模型，该模型包含两个核心组件：

1. **服务端 (Server)**: 整个系统的"大脑"与中央路由。它作为后台服务运行，是**唯一**负责与 Discord API (通过 JDA)
   直接通信的组件。它处理所有跨服路由、身份解析和鉴权凭证下发。**此组件不得包含任何 `net.minecraft` 的导入（反射除外）**。
2. **客户端 (Client)**: 部署在每个 Minecraft 服务器上的"触手"。负责捕获游戏内的所有事件发送给 Server，并接收来自 Server
   的指令（执行本地命令与委托鉴权）。

两者之间通过基于 **Netty** 的 TCP 协议（一次性哈希质询认证）进行安全通信。

### 3.2 运行模式与部署

1. **单体服务器模式 (`single_server`)**: 在同一 JVM 中同时启动内部 Server 和内部 Client。
2. **多服务器-客户端模式 (`multi_server_client`)**: 只启动 Client 端，连接到外部独立运行的 Server。
3. **独立模式 (`standalone`)**: 只启动 Server 端，作为多服务器架构的中央中枢。

## 4. 账户绑定系统 (Account Linking)

账户绑定系统是连接无状态 Discord 社区和有状态 Minecraft 世界的数据总线，也是 DMCC 权限系统的基石。

### 4.1 非对称映射关系

- **一个 Discord 账户可关联多个 Minecraft 账户**（方便玩家管理大号与小号）。
- **一个 Minecraft 账户只能关联一个 Discord 账户**（确保游戏内身份的绝对唯一性）。
- **数据持久化**: 绑定关系作为永久数据存储在 `Server` 端的 `account_linking/links.json` 中，以 Discord ID 为主键。
- **存储约束**: `links.json` 中仅存储绑定关系最小必要字段（Discord ID、Minecraft UUID、添加时间），**不得额外存储**
  Discord 用户名或 Minecraft 玩家名；显示名称在查询时实时解析。

### 4.2 安全绑定工作流 (严格的 MC 优先原则)

为防止在正版/离线服务器中出现身份冒用，**严禁在 Discord 端直接输入游戏名进行绑定**。

1. **玩家进服自动检查**: 玩家每次进入 Minecraft 服务器时，Client 检查其 UUID 是否已绑定。
2. **自动生成凭证**: 若未绑定，生成一个 6 位临时验证码（如 `A7X9P2`），单发消息指引玩家在 Discord 执行 `/link A7X9P2`，验证码
   5 分钟有效。
3. **手动刷新凭证**: Minecraft 端提供 `/dmcc link` 命令：
    - 若该玩家存在未过期验证码，返回同一验证码并将过期时间重置为“当前时间 + 5 分钟”；
    - 若验证码已过期或不存在，生成并返回新验证码（同样 5 分钟有效）。
4. **确认所有权**: 玩家前往 Discord，使用斜杠命令 `/link A7X9P2`。
5. **完成绑定**: Server 验证代码有效后，将该 Discord ID 与 MC UUID 写入 `links.json`，并使该验证码失效。

### 4.3 解绑与查询工作流

- **Discord `/unlink`**: 直接取消该 Discord 用户名下的所有 Minecraft 绑定（无需二次确认）。
- **Minecraft `/dmcc unlink`**: 直接取消“当前执行玩家”对应的绑定关系（无需二次确认）。
- **查询展示规则**: `links` 查询时实时解析显示名称，若无法解析则回退显示 UUID / Discord ID。

### 4.4 同步与跨平台交互

- 玩家在游戏内的聊天名字颜色，将自动同步为其绑定的 Discord 账户的最高角色颜色。
- 跨平台提及 (`@`) 时，自动在游戏内通过 Action Bar 或 Title 提醒对应的玩家。
- 当启用 OP 同步时，绑定关系将直接影响 Minecraft 原生 OP 权限分配（详见第 5.5 节）。

## 5. 零信任委托权限系统 (Delegated Authorization)

DMCC 彻底摒弃了在 Discord 端硬编码判定命令权限的做法，转而采用**“身份映射 + 边缘委托鉴权”**的微服务架构。

### 5.1 扩展的权限基准 (-1 到 4)

所有权限均对齐 Minecraft 原生的 OP 等级，并向下扩展：

- **`-1` 级**: 任意 Discord 用户（包括未绑定账号的游客）。适用于无害的查询命令（如 `help`, `info`）。
- **`0` 级**: 基础绑定玩家，或被赋予基础信任身份的未绑定用户，对应 Minecraft 原生的 OP 等级 0。
- **`1 ~ 4` 级**: 对应 Minecraft 原生的 OP 等级 1-4。

### 5.2 身份与 OP 映射 (Mappings)

Discord 用户的身份将通过以下规则在 Server 端结算为一个具体的 **OP 等级凭证**：

1. **`user_mappings` (精确控制)**: 直接指定某个 Discord User ID 拥有几级 OP（例如为服主直接分配 OP 4）。
2. **`role_mappings` (群组控制)**: 遍历用户拥有的 Discord 角色，取映射的最高 OP 等级。
3. **绑定账号**: 如果未命中上述映射，则直接使用其绑定的 Minecraft 账号在游戏内的实际 OP 等级。
   *注：即使用户未绑定 MC 账号，只要命中 mappings，也能获得相应的 OP 执行权。*

取三者中最高的 OP 等级作为最终凭证。

### 5.3 核心路由与委托鉴权 (`execute` 命令)

- `/execute <at> <command>` 本身的权限要求为 `-1` 级，目的是允许所有人查询子服务器的状态。
- **Server 不负责拦截**：当 Discord 用户发起 `/execute SMP reload` 时，Server 端仅计算该用户的 OP 凭证（例如是 2 级），然后将命令
  `reload` 和 `OP=2` 打包通过 Netty 发送给 `SMP` 客户端。
- **Client 边缘鉴权**：`SMP` 客户端收到请求后，对比自身本地 `config.yml` 中设定的 `reload: 4`，发现凭证（OP 2）不足，由客户端拒绝执行。
- **动态补全**：自动补全同样附带 OP 凭证，客户端仅返回该凭证有权执行的命令列表。

### 5.4 优雅解决白名单悖论 (Whitelist Catch-22)

玩家因白名单进不去服务器 -> 无法证明身份 -> 无法获得白名单。
**解决方案**：

1. 原生 `/whitelist` 要求 OP 3，存在安全风险。DMCC 在 Client 端提供独立的 `whitelist` 命令，权限配置为 `0` 级。
2. 玩家加入 Discord 后，通过验证获取某个基础角色（该角色在 `role_mappings` 中映射为 OP 0）。
3. 玩家在 Discord 频道执行 `/execute SMP whitelist <他的ID>`。
4. 路由将 OP 0 凭证发给 SMP，SMP 校验本地 `whitelist: 0` 通过，底层执行白名单添加。玩家即可进服完成后续绑定。

### 5.5 Minecraft OP 强制同步机制 (`sync_op_level_to_minecraft`)

`sync_op_level_to_minecraft` 默认关闭（`false`），因为开启后会覆盖服务器原有 OP 分配策略；但推荐在“以 Discord
身份治理权限”为目标的部署中开启。

开启后行为如下：

1. **全量同步原则**: 每次同步均执行“全量重算 + 全量应用”，而非增量补丁。
2. **强制覆盖原则**: DMCC 将重置 Minecraft 服务器当前 OP 列表，并依据 DMCC 配置中的映射规则重新分配。
3. **绑定缺失回退**: 若某玩家解除绑定后在 `links.json` 中不再出现，则在下一次全量同步中该玩家 OP 等级将被重置为 0。
4. **关闭时不干预**: 若 `sync_op_level_to_minecraft=false`，解绑不触发 OP 回收，服务器维持原样。
5. **与原生 `/op` 的关系**: 开启后，管理员在游戏内使用原生命令 `/op` 手动授予的结果会在下一次 DMCC 全量同步时被覆盖，这是预期行为。

> 说明：由于原生 `/op` 命令无法指定 OP 等级，DMCC 同步通过 API 层直接写入权限等级，而非依赖 `/op` 命令。

## 6. 多服务器配置模型（Standalone + Multi Server）

当 DMCC 处于 `standalone + multi_server_client` 架构时，不同子服务器可使用不同 OP 映射策略（例如 SMP 权限低、CMP 权限高）。

### 6.1 分服映射结构

在 `standalone` 配置中，`user_mappings` 与 `role_mappings` 中每个条目包含一个顶层 `op_level`（给 Standalone DMCC
服务端自身使用），以及一个 `servers` 列表（给每个 `multi_server_client` 客户端使用）。在 `single_server` 配置中，映射条目仅包含一个扁平的
`op_level`，无需 `servers` 列表。

### 6.2 强校验规则（配置验证阶段执行）

- `multi_server.servers` 中定义了多少个子服务器，`user_mappings[].servers` 与 `role_mappings[].servers`
  就必须为每个子服务器提供一条对应配置。
- 不允许缺失、不允许重复、不允许引用不存在的 `server` 名称。
- 若校验失败，配置加载应直接失败并输出明确错误，阻止系统在不一致权限模型下启动。

## 7. 命令列表与配置参考

| 命令                       | 默认 OP 等级 | 模组运行 `multi_server_client` | 模组运行 `single_server` | 独立运行 `standalone` | 说明                                   |
|:-------------------------|:---------|:---------------------------|:---------------------|:------------------|:-------------------------------------|
| `execute <at> <command>` | `-1`     | ❌                          | ❌                    | ✅                 | 远程执行中枢，仅 Standalone 存在，委托 Client 鉴权。 |
| `help`                   | `-1`     | ✅                          | ✅                    | ✅                 | 动态显示有权执行的命令。                         |
| `info`                   | `-1`     | ✅                          | ✅                    | ✅                 | 查看状态。Client 只显自身，Server 显全局。         |
| `log <file>`             | `4`      | ✅                          | ✅                    | ✅                 | 获取日志文件，自动补全。                         |
| `reload`                 | `4`      | ✅                          | ✅                    | ✅                 | 重新加载 DMCC 配置。                        |
| `shutdown`               | `4`      | ❌                          | ❌                    | ✅                 | 关闭 Standalone 应用程序。                  |
| `stats <type> <stat>`    | `-1`     | ✅                          | ✅                    | ❌                 | 查看统计数据。支持基于权限的自动补全。                  |
| `whitelist <player>`     | `0`      | ✅                          | ✅                    | ❌                 | DMCC 专用的白名单命令代理。                     |
| `link`                   | `0`      | ✅                          | ✅                    | ❌                 | Minecraft 端生成/刷新验证码。                 |
| `link <code>`            | `0`      | ❌                          | ✅                    | ✅                 | Discord 端使用验证码完成绑定。                  |
| `unlink`                 | `0`      | ✅                          | ✅                    | ✅                 | 解绑当前 MC 玩家 / 当前 Discord 用户的所有玩家。     |
| `links`                  | `4`      | ❌                          | ✅                    | ✅                 | 查看所有绑定关系。                            |

*(注：游戏内与终端控制台均属于 `LocalCommandSender`，终端默认为最高权限 OP 4，游戏内走原生判定。Terminal
不提供 `link`、`unlink`，但在 Standalone 下提供 `links`。)*
