# TEMP_TODO —— 第 2 / 3 轮交接清单

> **这份文件是给"上下文被压缩后的我"看的。** 它自包含地记录了项目现状、不可违反的红线、
> 关键代码地图，以及第 2 轮与第 3 轮要做的每一件事。开工前请完整读一遍。
> 交付完成、两轮都结束后，把本文件内容归纳进 `CHANGELOG_TEMP.md` 并删除本文件。

---

## 0. 当前状态一句话

**第 1 轮（骨架 + 双加载器 + 模组兼容扩展点）已完成、已由用户测试通过并提交**（commit `0bd5d192`）。
随后的小改动均已提交：`工作 05`（第 1.1 轮：修复 IDE 同步 + 单一通用 JAR）、
`工作 06`（第 1.2 轮：修复 NeoForge 无法启动 + 首启指引换行）、
`工作 07`（第 1.3 轮：日志多语言 + 构建产物与开发环境净化，commit `49bf1082`；
用户实测 **Fabric / NeoForge / Standalone 三种用法均正常**，其中包括把 SLF4J 服务注册文件移回标准目录这一步）。
**当前状态**：第 1.3 轮的追加修复"`./gradlew build` 绝不生成 `logs/` 目录"已改完、待用户审阅 commit
（只涉及 `core/build.gradle`、`LoggerImpl.java` 与两份文档）。用户 commit 后即开始第 2 轮。

---

## 1. 项目速览

- **DMCC (Discord-MC-Chat) v3**：Minecraft ↔ Discord 双向聊天桥。Java 25 / Gradle 9.7.1 / **仅 Minecraft 26.2**。
- **模块布局**（`settings.gradle`：`:core`、`:fabric`、`:neoforge`）：
  - `core/` —— 平台无关：DMCC 主控、Server/Client、Netty 协议、命令、账户绑定、配置、i18n、工具。
    **不得出现 `net.minecraft` 导入**（唯一例外：`utils/EnvironmentUtils` 用反射探测）。
  - `minecraft-common/` —— **共享源码目录，不是 Gradle 项目**：11 个 Mixin、`MinecraftEventHandler`
    （28 个静态钩子）、`MinecraftPlatformHost`、`ModIntegration(s)`、`TranslationManager`、
    `MinecraftCommands`（Brigadier `/dmcc` 树）、`DmccRconConsoleSource`。只依赖原版类，不依赖任何加载器 API。
  - `fabric/`、`neoforge/` —— 入口点 + 模组元数据 + 加载器专属模组兼容（各不到 100 行）。
    两个加载器用 `sourceSets.main.java.srcDir("../minecraft-common/src/main/java")` 共享源码。
- **产物**：根 `build/Discord-MC-Chat-<版本>.jar` 是**单个通用 JAR**，同一文件可用于 Fabric、
  NeoForge、以及 `java -jar` 独立运行（`Main-Class` 保留）。不再有 `-fabric`/`-neoforge` 后缀产物。
- **关键版本**：`gradle.properties` 里的 `mod_version=3.0.0-beta.2`、`minecraft_version=26.2`、
  `loader_version=0.19.5`、`loom_version=1.17-SNAPSHOT`、`neo_version=26.2.0.87`、
  `moddev_version=2.0.147`、`shadow_version=9.6.1`、`junit_version=6.1.3`、JDA 6.6.0、
  slf4j 2.0.19、Jackson(tools.jackson) 3.2.2、OkHttp 5.5.0、Netty 4.2.18.Final、jemoji 2.0.0、
  Vanish 1.6.15+26.2（仅 Fabric，compileOnly）。
- **构建命令**（已放宽权限，**直接用用户家目录的 `~/.gradle`，不要再建临时 GRADLE_USER_HOME**）：
  - `./gradlew clean build --warning-mode all` —— **只验证"构建成功"**，这是唯一要跑的验证。
  - `./gradlew :core:test`
  - **不再执行 `runServer` / `runClient`**：用户明确说了以后不再跑开发服务端，相关设计已全部删除
    （`neoforge` 的 `runs {}` 块、`.gitignore` 的 `run/`）。不要为"开发服务端能不能起来"做任何改动。
  - **每次交付前的固定收尾**：`./gradlew clean`（让工作区在文件管理器里也干净）→ `./gradlew --stop`。

---

## 2. 不可违反的红线（用户明确要求）

1. **只能用 Jackson（`tools.jackson` 3.2.2），禁止引入 GSON。** v2 用的是 GSON，只可作为"设计证据"引用，不得复制其实现。
2. **仅支持 Minecraft 26.2**，不做多 MC 版本；**DMCC 服务端与客户端必须完全同版本**，因此协议**不需要任何兼容层**。
3. **两套消息系统保持现状**：`lang/*.yml` 是内部翻译（用户不应改），`custom_messages/*.yml` 是用户可改的模板。
   不要把两者合并。
4. **配置模板里预填的是用户自用的测试参数**（`xujiayao`、`SMP`/`CMP`、`in-game-chat`、`111111`/`222222`、
   `your_token_here` 等）。**必须原样保留**，不要"清理成示例值"，否则用户每次测试都要重填。
5. **`.github/ISSUE_TEMPLATE/bug.yml` 已被用户亲自改过**（Minecraft 版本下拉恢复了完整列表，说明文字改成
   "Only DMCC v2 and v3 versions are supported."）。**不要再去裁剪它。**
6. **功能零删减**：ANSI、投票、贴纸、嵌入、`/log` 文件传输、精确截断规则、配置校验提醒、
   交互组件占位符全部保留。精简只能来自去重 / 删死代码 / 换更简洁的实现。
7. **`Capability` 能力枚举暂时不要加**：当前没有第二个平台会读它，加了就是新的死抽象。
   平台扩展点用现有的 `PlatformHost` + `ModIntegration` 两个接口即可。等真正做 Paper 时再加。
8. **测试策略**：每轮都写临时测试保证"行为输出不变"；**交付前删除本轮临时测试**，
   只保留 `core/src/test/java/com/xujiayao/discord_mc_chat/SmokeTest.java`（现仅 1 个测试：版本资源可解析）。
9. **每轮必须**：更新 `CHANGELOG_TEMP.md`（追加 `## 工作 NN` 小节）+ 更新 `README_CN.md`
   （`README.md` 是翻译件，只在发布新版本时同步，平时不动）+ 给用户一份**人工测试清单**。
10. 用户会**亲自审阅并 commit**；不要自行 commit。有疑问用问卷问，不要猜。
11. **DMCC 自己的每一条日志都必须多语言**（`lang/en_us.yml` + `lang/zh_cn.yml` 同时补键），
    不允许在代码里写英文单语日志。**仅两个例外**：启动横幅（ASCII 艺术字 + 品牌信息）、
    以及"内部语言文件自身损坏"时那两条兜底警告（那时翻译系统已经不可用了）。
    转发 Discord / 控制台原文的日志（`[子服名] 内容`、`LOGGER.info(line)` 之类）不算 DMCC 文案。
    新增 lang 键后**必须两个文件都加**，`getDmccTranslation` 查不到键会直接把键名原样打出来。
12. **不再为 `runServer` 做任何设计**：用户只验证 `./gradlew build` 成功，不再启动开发服务端。
    项目里已经没有 `runs {}` 块、没有 `run/` 目录；不要为了"能在开发环境里跑起来"去调整文件布局。
13. **交付前工作区必须干净**（用户会亲自看文件管理器，不只 `git status`）：
    `./gradlew clean` 删掉所有 `build/`，临时文件、临时脚本、临时测试一律不留。
    **`./gradlew build` 本身也不得留下 `logs/` 目录**（做法见第 3 节第 1.3 轮第 5 条）。

---

## 3. 第 1 轮已完成的内容（不要重复做）

- 事件总线三件套（`EventManager`/`CoreEvents`/`MinecraftEvents`）已删除；Mixin 直接调用钩子方法。
- `core/platform/` 已建立：`PlatformHost`（core → 平台的 13 个动作）、`Platform`（注册点）、
  `NoopPlatformHost`（独立模式空实现）、`StatsProvider`（原 `StatsCommand` 内部接口）。
- `ModIntegration` / `ModIntegrations` 扩展点已建立；Fabric 侧 `FabricDMCC.VanishIntegration` 是**模板实现**。
- 首启流程：`mode.yml` 与 `ModeManager` 已删除，`mode` 键并入 `config.yml`，首启直接生成完整配置 +
  控制台打印绝对路径与三步指引；新增"环境与模式"匹配校验（standalone 只能在独立 JAR 里跑，反之亦然）。
- 版本改由资源 `core/src/main/resources/dmcc_version.txt`（内容 `${mod_version}`）提供。
- 用户修复（**保留，勿改**）：
  - `MixinReloadableServerResources` 已删除，其功能并入 `MixinMinecraftServer.reloadResources`
    （原因：NeoForge 会改写 `ReloadableServerResources` 的合成 lambda，导致 `InvalidInjectionException`）。
  - 通用 JAR（`universalJar` 任务）已在根 `build.gradle` 实现：以 `:core:shadowJar` 为基底，
    先并入 `:fabric:jar`（INCLUDE），再并入 `:neoforge:jar`（EXCLUDE 去重），保留 shadow 清单。

### 第 1.3 轮（用户测试通过后的 4 项小改动，已改完、待用户 commit）

1. **交付前工作区必须干净**：`./gradlew clean build` 验证 → `./gradlew clean` → `./gradlew --stop`。
2. **根 `build/` 里只剩 JAR**：`universalJar` 收尾时除了删自己的临时目录，还会删掉 `build/tmp`
   （Gradle 为 `zipTree` 建立的 `build/tmp/.cache/expanded/zip_<hash>` 空目录就出在这里）。
3. **runServer 相关设计全部删除**：`neoforge/build.gradle` 的 `runs { server { ... } }` 块、
   `.gitignore` 的 `run/` 条目；SLF4J 服务注册文件从非标准的 `core/src/main/shadow-resources/`
   移回 `core/src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider`，
   `shadow-resources/` 目录与 `shadowJar` 里的 `from("src/main/shadow-resources")` 一并删除。
   ⚠️ **若 IDEA 同步或开发环境再出现 "Failed to initialize DMCC Logger" / 类初始化递归，就把这一处改回去**
   （发布 JAR 的内容两种放法完全一致，只影响开发类路径）。
4. **日志全部多语言**：`DMCC.java` 里唯一的英文单语日志 `DMCC platform: {}` 改为走 i18n 键
   `main.init.platform`（en: `DMCC is running on platform {}`，zh: `DMCC 正在 {} 平台上运行`）。
   启动横幅与"内部语言文件损坏"两条兜底警告按用户要求**保持硬编码**，不需要翻译。
5. **构建绝不生成 `logs/` 目录（用户第二次反馈后追加修复）**。`./gradlew build` 会连带跑 `:core:test`，
   而 SLF4J 服务注册文件回到 `src/main/resources` 后测试类路径上也有 DMCC 的 provider，
   日志器会按"相对工作目录"建 `./logs`。做法：
   - `core/build.gradle` 的 `test` 任务把**工作目录设为 `layout.buildDirectory.dir("test-run")`**
     （并用 `doFirst { mkdirs() }` 保证目录先存在，否则测试 JVM 起不来），于是测试日志落在
     `core/build/test-run/logs/DMCC_<时间戳>.log`，随 `clean` 一起消失，源码树保持干净；
     同时 `testLogging.showStandardStreams = true`，DMCC 日志会直接显示在构建输出里。
     **测试期间 DMCC 的日志器是真实生效的**（不是被静音），需要看日志时直接看 Gradle 输出或那个文件。
   - `LoggerImpl` 的日志文件改为**首次真正写日志时才创建**（`fileWriter()` 惰性访问器），
     构造函数不再 `Files.createDirectories("logs")`，因此"取得 logger 但什么都没记"也不会留下空 `logs/`。
   - ⚠️ **写测试时必须记住：测试的工作目录是 `core/build/test-run`，不是项目目录。**
     读工程文件请用 classpath 资源或绝对路径，不要用 `Path.of("src/...")` 之类的相对路径。
     需要临时目录就用 `build/` 下的路径（这样 `clean` 能一并清理）。

---

## 4. 关键代码地图（改代码前必看）

### 4.1 core → 平台（`core/platform/PlatformHost.java`，13 个方法）

```
String name();  StatsProvider stats();
executeCommand(CommandSender, String, CompletableFuture<Void>)
autoCompleteCommand(String, int, List<String>)
sendLinkCode(String uuid, String code, boolean alreadyLinked, String discordName)
sendUnlinkResult(String uuid, boolean success, String discordName)
applyOpLevels(Map<String,Integer>)            // key 是 UUID 字符串，与既有数据模型一致
broadcastDiscordChat(List<TextSegment>, List<TextSegment> reply, String mentionText, String mentionStyle, List<String> uuids, boolean everyone)
broadcastDiscordCommand(List<TextSegment>)  broadcastDiscordReaction(...)  broadcastDiscordEdit(...)  broadcastDiscordDelete(...)
broadcastMinecraftRelay(List<TextSegment>, String componentJson, String componentPlaceholder, String mentionText, String mentionStyle, List<String> uuids, boolean everyone)
```
调用方式：`Platform.host().xxx(...)`。**没有能力探测**——平台缺功能时自己记日志跳过。
实现方：`minecraft-common/.../events/MinecraftPlatformHost.java`（薄委托，转给 `MinecraftEventHandler`）。

### 4.2 平台 → 逻辑（`MinecraftEventHandler` 的 28 个静态方法）

- 由 Mixin 调用（16 个）：`onServerStarted(MinecraftServer)`、`onServerStopping()`、`onServerStopped()`、
  `onPlayerJoin(ServerPlayer)`、`onPlayerQuit`、`onPlayerDie`、`onPlayerAdvancement(AdvancementHolder, ServerPlayer, AdvancementProgress)`、
  `onPlayerChangeGameMode(GameType, ServerPlayer)`、`onPlayerChat(PlayerChatMessage, ServerPlayer)`、
  `onPlayerCommand(String, ServerPlayer)`、`onSourceSay(CommandContext, PlayerChatMessage)`、
  `onSourceTellRaw(CommandContext, Component)`、`onSourceMsg(...)`、`onSourceMe(...)`、
  `onCommandRegister(CommandDispatcher)`、`onReloadResources()`。
- 由 `MinecraftPlatformHost` 调用（12 个）：`executeCommand`、`autoCompleteCommand`、`sendLinkCode`、
  `sendUnlinkResult`、`applyOpLevels`、`broadcastDiscord*`（5 个）、`broadcastMinecraftRelay`、`statsProvider()`。

### 4.3 其它要点

- `MinecraftEventHandler` 目前约 1280 行，其中 `buildInfoResponse` 仍调用
  `StatsCommand.countStatResultEntries(...)` 与 `StatsCommand.normalizeMinecraftNamespace(...)`
  （平台层反向依赖 core 命令类的最后两处，第 2 轮随 `StatsReader` 移出）。
- `DMCC.init(PlatformHost)` 接收平台实现并 `Platform.set(...)`；`reload()` 复用同一个 host。
- 配置目录：`./config/discord_mc_chat/config.yml`；`custom_messages/<lang>.yml`；缓存 `./config/discord_mc_chat/cache/`。
- 日志：独立模式写 `./logs/DMCC_<时间戳>.log`（`.gitignore` 已忽略 `logs/`、`config/`）。
  **该文件是惰性创建的**：`LoggerImpl` 只在真正写下第一条日志时才建 `logs/` 与文件；
  测试的工作目录被设在 `core/build/test-run`，所以测试日志只会出现在 `core/build/test-run/logs/` 里。
  `R3-1` 要重构 `LoggerImpl`（反射派发 → `enum`）时**不要破坏这两条性质**。

---

## 5. 第 2 轮：去重与瘦身主体（目标 ≈ −4,800 行）

> 目标：15,400 行 → 约 10,300 行。**功能零删减**，只做结构统一、去重、删死代码。
> 测试策略：先写"黄金样例"固化旧行为，再替换实现并逐例比对。

### R2-1 解析层统一（预计 −1,800 行，最大单块）

**现状**：`core/server/message/` 下 `DiscordMessageParser`(1593) + `MinecraftMessageParser`(782) +
`MessageParserCommon`(268) = 2,643 行；另有 `core/network/message/TextSegment`(103) 与
`core/utils/TextSegmentUtils`(72)。**重复的具体形态**：
- `DiscordMessageParser` 里约 10 个 `collectXxxTokens`（user/role/channel/everyone/剧透×4/timestamp）+
  约 6 个 `splitSegmentsByXxx`（user/role/channel/everyone/custom emoji/alias emoji）是同一套
  "找 token → 切段 → 加样式"逻辑按 token 类型各写一份。
- 两套 markdown 状态机（`DiscordMessageParser.parseNestedMarkdown` 与 `MinecraftMessageParser` 的
  定界符扫描），且**转义语义不一致**（D→MC 会消费 `\`，MC→MC 保留 `\`）。
- `MARKDOWN_DELIMITERS`、时间戳正则、别名表情正则各声明两份。
- `DiscordMessageParser` 的 7 个 `buildXxxSegments`（chat/reply/reaction/edit/edited/delete/command）
  结构同形，重复 `{message}` 拆分与占位符替换逻辑。

**目标设计**：
1. 不可变模型：`Message(List<Span> spans, boolean mentionEveryone, Set<String> mentionedUuids)`、
   `Span(String text, Style style, String clickUrl, String hoverText)`、`Style(颜色 + 5 个布尔)`。
   `TextSegment` + `TextSegmentUtils` 折叠进该模型（注意：报文里也用它，**JSON 序列化要跟着走**，
   见 R2-2；第 2 轮若报文层先改，则模型直接作为报文载荷）。
2. 一个通用 token 表：`record TokenRule(Pattern pattern, Kind kind)` + 一个
   `splitByTokens(spans, rules, resolver)`，取代那 16 个方法。剧透/时间戳等特殊处理隔离在小函数里。
3. 读写器拆分：`DiscordMarkupParser`（Discord 原文 → Message）、`MinecraftTextParser`（MC 原文 → Message）、
   `DiscordMarkupWriter`（Message → Discord 标记）、以及 `minecraft-common` 里的
   `MinecraftComponentWriter/Reader`（Message ↔ MC Component，替换 `buildComponentFromSegments` +
   `buildComponentPart` 两份重复实现）。
4. 提及解析通过 `MentionDirectory` 接口注入 → **解析器彻底脱离 JDA，可用纯字符串做单元测试**。
5. 模板渲染收敛为 `MessageTemplates`（模板片段 → spans），取代 7 个 `buildXxxSegments`。
6. **行为必须保持**：ANSI 色码块、投票、贴纸、嵌入、时间戳（9 种样式）、剧透（含剧透包裹的提及/链接）、
   附件（含剧透附件）、Markdown（粗体/斜体/下划线/删除线）、引用行、标题行、行内与围栏代码块、
   超长截断（主行 6 行/200 字符、CJK 400；回复行 1 行/20~40 字符）、`{server}`/`{server_color}` 常量。
   顺带修掉转义不对称。

**验收**：约 80 例黄金样例（markdown 嵌套、剧透、ANSI、时间戳、各类提及、表情、链接、附件、嵌入、
投票、截断、CJK、转义边界）逐例比对通过 + 用户按人工语料核对 MC 侧显示。

### R2-2 报文层换代（预计 −950 行，同时关闭两个 P0 问题）

**现状**：`core/network/packets/` 30 个数据包类（`AuthPackets` 125 + `CommandPackets` 588 +
`EventPackets` 173 + `MiscPackets` 54 + `Packet` 17）+ `network/serialization/`（2 个类，共 56 行）
= 约 1,013 行；用 **Java 原生序列化**。

**为什么必须换（回答过用户，此处备忘）**：
1. **安全**：`JavaSerializerDecoder` 在**认证之前**用 `ObjectInputStream` 反序列化对端字节，
   无 `ObjectInputFilter`、无类白名单 —— 经典 RCE 面。
2. 30 个类几乎全是"字段 + 构造器"样板，约 950 行零逻辑。
3. 每包新建 `ObjectInputStream`/`ObjectOutputStream`，流头与类描述符重复上线缆、无跨包类缓存。
4. `NetworkManager` 广播时对每个子服各序列化一次（无共享 byte[]）。
5. 字段改名/重排会**静默不兼容**；JSON 可以显式忽略未知字段。
6. 顺带修掉：`/log` 传文件走同一帧，超过 1 MiB 触发 `TooLongFrameException` →
   `exceptionCaught` 直接 `ctx.close()`，**大日志会打断客户端连接**。
7. 与 v2 一致：v2 的多服协议本来就是 Gson JSON 行协议（`MultiServer.java:64,79`），
   v3 是唯一一代用了 Java 原生序列化的。

**目标设计**：
- `core/network/protocol/`：`PacketType` 枚举 + 约 14 个 record（Auth 五种、ServerEvent、DiscordRelay、
  MinecraftRelay、ConsoleLogBatch、**CommandRpc/CommandRpcResult**、InfoSnapshot、OpSync、KeepAlive、
  LatencyPing/Pong）+ `PacketCodec`（Jackson，**显式 type → record 映射**，未知字段忽略）+
  `JsonPacketDecoder/Encoder`。
- **合并**：Execute / Console / Update / Link / Unlink 以及三种自动补全都归入 `CommandRpc` + `CommandRpcResult`
  （`kind` 区分），Info 保留独立的 `InfoSnapshot`。
- **文件负载**：`CommandRpcResult` 里用 **base64 分片（256 KiB/片）**，保留 Netty 与 1 MiB 分帧；
  这样超 1 MiB 的日志不再断连（分片重组逻辑约 30 行）。
- 保留 Netty（用户已确认）；只换报文层。

**验收/测试**：全部类型的往返编解码；未知字段容忍；畸形报文不崩；分片重组；>1 MiB 文件传输不断连；
`standalone + 一个 multi_server_client` 双进程冒烟。

### R2-3 命令层收敛（预计 −740 行）

- `record CommandArgument(String name, String description)` 取代 11 处匿名类
  （`LogCommand`、`ConsoleCommand`×2、`ExecuteCommand`、`StatsCommand`、`WhitelistCommand`、`LinkCommand` 等）。
- `CommandTargets` 工具类：合并 `ConsoleCommand.isValidTarget` ≡ `ExecuteCommand.isValidTarget`
  以及两处几乎相同的目标解析（`all_online_clients` / 离线分支 / 非法目标）。
- 统一 `CommandSender`；`WhitelistCommand` 里 16 行匿名提升类换成 `record ElevatedSender(CommandSender delegate)`。
- 合并 `CommandManager` 与 `HelpCommand`/`CommandAutoCompleter` 里重复的 usage 拼接（`<name> <arg>`）。
- 13 个命令实现类按域合并为 4 个：Link 域（link/unlink/links）、Exec 域（console/execute）、
  Query 域（info/stats/update/log）、Admin 域（reload/shutdown/whitelist/help）。
- 删除 13 个空构造器与 8 处 `new CommandArgument[0]`。

### R2-4 server/discord 去重与拆分（预计 −620 行）

- `DiscordManager`(1090) 拆分：JDA 生命周期 / 消息派发 / 控制台转发（分片+脱敏）/ 频道解析 / 限速。
- 合并成对方法：`sendBotMessage`/`sendBotMessageSync`、`sendWebhookMessage`/`sendWebhookMessageSync`、
  以及 `sendMinecraftSystemMessage` 与 `sendMsptMonitoringMessage` 共用的"standalone→webhook / single_server→bot"分派块。
- `ChannelUpdateManager`：删掉 `buildOfflineContext()` 与 5 个一行转发包装（`updateXxxAsync/Sync`），
  改用 3 参方法；`queue(_ -> {}, _ -> {})` 空 lambda 换成单参 `queue()`。
- `ServerHandler.channelRead0`（约 170 行，两个 30 分支 switch + 内联握手）拆成
  `handleHandshake` / `handleAuthResponse` / `handleAuthenticated`，并把重复 5 次的
  "翻译原因 → 记日志 → `DisconnectPacket` → `ctx.close()` → return" 提取为 `reject(ctx, key, args)`。
- `broadcastMinecraftRelay` 与 `broadcastMinecraftTellRawRelay` 合并（重复了 overwrite/echo/`minecraft_to_minecraft.<node>`
  门控与"发给除源外/发给源"）。
- `ServerHandler.isExcludedMinecraftCommand` 的**每消息重编译正则**改为配置加载时预编译
  （`DiscordManager.applySensitiveRedaction` 每行重编译脱敏正则同理）。

### R2-5 minecraft-common 去重（预计 −530 行）

- `buildComponentFromSegments` 与 `buildComponentPart` 是同一套样式管线的两份实现 → 只留一份
  （由 R2-1 的 `MinecraftComponentWriter` 承担）。
- 10 处 `if (serverInstance == null) return;` + `serverInstance.execute(() -> { try {...} catch (Exception ignored) {} })`
  外壳提取为 `onServerThread(Consumer<MinecraftServer>)`。
- mention 通知块（everyone 分支 + UUID 循环）在 `broadcastDiscordChat` 与 `broadcastMinecraftRelay` 里逐字重复 → 提取。
- 4 处"replySegments 非空则广播"块 + 约 12 处逐玩家发送循环 → 提取 `broadcast(Component)` / `broadcastReply(...)`。
- 两处逐字相同的 11 行 `new CommandSourceStack(...)` → 提取 `dmccSource(int opLevel)` + 常量 `"DMCC"`。
- `RegistryOps.create(...)` 每消息重建 → 在 ServerStarted 时建一次静态复用。
- `MinecraftCommands` 两个嵌套 record 重复的 `reply()` 与 4 步权限探测 → 提取共享方法；
  `getPlayerUuid()`/`getPlayerName()` 返回 null 的问题顺便修掉（构造时就取好）。
- `MixinServerGamePacketListenerImpl` 两个注入方法体完全相同 → 提取 `postPlayerCommand(...)`。

### R2-6 死代码清理（预计 −150 行）

- `DiscordMessageParser` 的 `record MarkdownSpan(..., String innerText, ...)`：`innerText` 写入后从不读取。
- `JavaSerializerEncoder`/`Decoder` 的显式空构造器（随 R2-2 整个删除）。
- `LoggerImpl` 里约 70 行被注释掉的 `trace`/`debug` 方法体（配合 R3-1 决定是删还是实现）。
- 空 catch 块：`ConsoleCommand`、`WhitelistCommand`、`StatsCommand`×2、`ExecutorServiceUtils`、
  `MojangUtils`、`JsonUtils` 共 7 处 + minecraft 侧 17 处。**注意**：其中 OP 同步的 5 处会静默丢失权限同步、
  `MinecraftEventHandler` 的补全那处会静默返回空结果 → 这些至少要 `LOGGER.warn/debug` 或缩小捕获类型。
- `TranslationManager` 的 `if (args == null || args.length == 0)`（varargs 永不为 null）。
- `CommandAutoCompleter` 中 guard 之后的不可达分支。

### R2-7 第 2 轮验证

1. `./gradlew :core:test` + `./gradlew clean build --warning-mode all` 全绿。
2. 黄金样例与临时测试全部通过后**删除**（保留 `SmokeTest`）。
3. 交给用户的人工清单：
   - 固定 12 条 Discord 测试语料（markdown / 剧透 / ANSI / 时间戳 / 提及 / 表情 / 链接 / 附件 / 嵌入 /
     投票 / 超长消息 / CJK），逐条核对 MC 侧显示；
   - 命令矩阵：13 命令 × OP(−1/0/4) × 三种模式；
   - 控制台转发 + `/log` 取一个 **>1 MiB** 的日志（验证不再断连）；
   - 账户绑定全流程（验证码 → `/link` → 角色颜色 → OP 同步）；
   - Fabric 与 NeoForge 各跑一遍。
4. 更新 `CHANGELOG_TEMP.md`（`## 工作 05`）与 `README_CN.md`：
   §2.1 解析能力矩阵按新实现更新、§7 命令面、§9 网络与安全（新协议：Jackson JSON + 分片、
   同版本强校验、不再有 Java 反序列化）。

---

## 6. 第 3 轮：体验、正确性与文档（目标 ≈ −600 行工程改动 + 文档）

### R3-1 日志 / 配置 / 工具瘦身（预计 −715 行）

- **日志 538 → ~200**：`LoggerImpl` 删掉"级别字符串 → `Map<String,Method>` 反射派发"，改 `enum Level`；
  `new SimpleDateFormat("HH:mm:ss")`（每行 1 次分配）改 `DateTimeFormatter` 常量；
  保留极简 SLF4J Provider（standalone 必需，注册文件在 `core/src/main/resources/META-INF/services/`）；
  trace/debug 要么实现要么从接口删除（现在 `isTraceEnabled`/`isDebugEnabled` 恒 false + 70 行注释代码）。
- **配置 464 → ~380**：`ConfigManager` 改为"加载时解析成类型化快照 + volatile 发布"，
  去掉每次 `getConfigNode` 的 `path.split("\\.")` 逐级遍历与缺失即 warn（现在一条系统消息路径有 5–8 次读取）。
  **保留全部校验能力**（缺失/未知/类型/版本/未修改键）。
- **工具 838 → ~550**：`JsonUtils` 三个 `toStringMap` 重载合并；`StringUtils.format` 能换 `formatted` 的换掉；
  `MojangUtils` 加 TTL + 负缓存（现在失败结果不缓存 → 故障期每条消息对每个未缓存 UUID 重发 HTTP）；
  `LogFileUtils` 读取路径限制在 `./logs` 内（现在是 `resolve(fileName).normalize()` 后直接读，可穿越）。
- **I18nManager**：`DMCC_TRANSLATIONS` 是普通 `HashMap`，会在**客户端登录时的 Netty 线程**被 clear+重填，
  而读取来自 MC/JDA/日志线程 → 改为 `ConcurrentHashMap` 或发布 `Map.copyOf` 快照；`language` 字段加 volatile。

### R3-2 用户可见 YAML 可读性改造（用户明确反馈：测试用户抱怨 YAML 难读）

- 范围：`config.yml`（三份模板）、`custom_messages/*.yml`。**内部 `lang/*.yml` 不用美化**（开发者可读即可）。
- **保留全部预填测试值**（见红线 4）。
- 做法：分区大标题；每个键**上方**一行说明 + 取值示例；显式写出"留空=禁用""默认值=X"等约定；
  把过深的嵌套（如 `broadcasts.minecraft_to_discord.player.*`）提浅；统一命名风格。
- `custom_messages`：**逐键列出可用占位符**并给出最小示例（这是用户最容易被坑的地方）。
  现有占位符包括：`{message}`、`{display_name}`/`{effective_name}`、`{player_name}`、`{player_uuid}`、
  `{role_color}`、`{server}`、`{server_color}`、`{command}`、`{emoji}`、`{action}`、`{death_message}`、
  `{title}`、`{description}`、`{mode}`、`{online_player_count}`、`{max_player_count}`、`{players_ever_joined}`、
  `{online_server_count}`、`{online_server_list}`、`{server_started_time}`、`{last_update_time}`、
  `{mspt}`、`{threshold}`、`{next_check_time}`。
- 面向**未来的网页面板**（用户计划做交互式配置生成器，输出树级 zip）：模板要保持**机器友好**——
  键路径稳定、无隐式继承、每键都有明确默认值；**现有校验器就是面板与模组之间的契约**。
- 验收：三份模板都能通过校验器（写测试）；`processResources` 展开正确；"全新安装"路径下生成的配置未修改即可通过校验。

### R3-3 并发与性能修正（约 +200 行，但修掉正确性问题）

按优先级：
1. **Info 快照改为按请求 ID 关联的聚合**。现状 `NetworkManager.requestInfoSnapshot`：
   入口 `infoCache.clear()` 在加锁之前、`expectedResponses` 是快照（中途断连必然等满超时）、
   **响应无请求关联**（只带 `sentAtMillis`）→ 四个调用线程（MSPT 每 10s、Presence 每 30s、
   频道更新每 10min、`/info`）会互相清缓存，甚至把上一轮的响应当成本轮（陈旧数据）。
2. **命令执行器改虚拟线程**（`CommandManager` 现在是单线程执行器，`future.get(30s)` 会阻塞所有用户；
   `all_online_clients` 最坏 N×30s）。
3. **把阻塞工作移出 Netty IO 线程**：
   - `MinecraftMessageParser.buildMentionContext` 每条消息重建全量别名表并做阻塞 JDA 调用
     （`retrieveUser`/`retrieveMember` 内部 `.complete()`，且逐 guild 循环）→ 改为带 TTL 的
     `MentionDirectory` 缓存 + 绑定/解绑/角色变更时失效（R2-1 已把它抽成接口，正好接上）；
   - `DiscordManager.getOrCreateWebhook` 每条 webhook 消息 `retrieveWebhooks().complete()` → 按频道缓存句柄；
   - `ServerHandler` 的 `/update` 请求直接做阻塞 HTTP → 移出 IO 线程；
   - 解析放专用执行器。
4. **Presence 真防抖**：`BotPresenceManager.update()` 现在是"取消旧任务 + 延迟 0 重新排程"，
   注释却写 Debounce；另有一个 bug：两个开关都关时提前 return，旧任务不会被取消，会按旧配置永久运行。
5. **自动补全异步化**：现在阻塞单线程 JDA 事件池最长 5 秒（超过 Discord 的 3 秒补全截止时间），
   期间所有 Discord 事件停摆。
6. **`ConsoleLogTailer.pendingLines` 加上限**（断连期无界积压；另注意 flush 在 `isConnected()` 检查之后移除行，
   期间断连会静默丢整批）。
7. **reload 期间 `COMMANDS`/`commandExecutor` 的 TOCTOU**（`COMMANDS.clear()` 与重注册非原子，
   窗口期命令报"未知命令"；`commandExecutor` 非 volatile）。
8. **`LinkedAccountManager.save()`** 每次 link/unlink 全量美化重写 links.json（在单线程命令执行器上，
   或 MC 路径下在 Netty IO 线程上）→ 至少改为异步/去美化。
9. **`TextSegment` 可变性**：8 个 public 字段 + 跨 Netty 共享实例（R2-1 已改为不可变模型的，此处确认收尾）。
10. **`MemberCachePolicy.ALL`** + 每条消息重建全量别名表：万级公会时内存与 CPU 都随公会规模线性增长。

### R3-4 收尾

- `README_CN.md`：§8 配置参考重写（逐键说明 + 占位符总表）；§10 运维（日志、缓存、性能注意项）；
  §1 记录最终行数与精简结果。（`README.md` 不动。）
- `CHANGELOG_TEMP.md` 追加 `## 工作 06`。
- 最终验证清单（用户执行）：全新安装体验评价（最重要）、多人同时聊天/刷消息观察延迟、MSPT 预警、
  频道看板、Bot 状态、按新注释改一个 `custom_messages` 模板并 reload、两平台最终回归。
- 删除临时测试，只留 `SmokeTest`；把本文件内容归纳进 `CHANGELOG_TEMP.md` 后删除本文件。

---

## 7. 交付协议（每轮固定动作）

1. 先写测试（能固化旧行为的先固化）→ 重构 → `./gradlew clean build :core:test --warning-mode all`。
   **只验证"构建成功"**：不跑 `runServer`，也不要为了"开发环境能不能起"去改任何东西。
2. 核对产物：根 `build/` 里**只有** `Discord-MC-Chat-<版本>.jar`（若又冒出 `build/tmp`，说明
   `universalJar` 收尾的清理被破坏了）；JAR 内含 `fabric.mod.json` + `META-INF/neoforge.mods.toml` +
   `dmcc.mixins.json` + 两个入口点 + 三份配置模板 + standalone `Main-Class`，且 0 重复条目。
   若根 `build/` 里出现了 `reports/problems/`，那是 Gradle 在报弃用或问题——**去修根因，不要删报告**。
3. **删除本轮临时测试**（保留 `SmokeTest`）；清理临时文件（`.tmp-*`）。
   正常情况下 `:core:test` 已不会产生 `core/logs/`（见红线 13），交付前仍顺手扫一遍 `logs` / `config` 目录作为保险。
4. 更新 `CHANGELOG_TEMP.md`（新增 `## 工作 NN`）与 `README_CN.md`；流程/红线有变时同步本文件。
5. **工作区净化（用户明确要求）**：`./gradlew clean`（删掉所有 `build/`）→ `./gradlew --stop`。
   用户会亲自看文件管理器，而不只是 `git status`。
6. `git status` 复核（不留意外未跟踪文件）；向用户交付：
   - 改了什么（用户可见 / 架构）
   - 验证证据（构建 + 测试 + 产物核对 + 行数变化）
   - **人工测试清单**（JUnit 覆盖不到的部分，写清步骤与期望结果）
   - 与计划的偏差及理由
7. 用户审阅并 commit；**不要自行 commit**。

---

## 8. 已知问题与小尾巴（随时可能被问）

- `fabric.mod.json` 仍引用不存在的 `icon/icon.png`（沿用旧状，未新增 PNG）。
- `MinecraftEventHandler.buildInfoResponse` 在信息响应里调用 `StatsCommand.countStatResultEntries`，
  而后者会 `provider.saveAll()`（vanilla `PlayerList.saveAll()`）——**从非主线程调用有线程安全隐患**，
  且每次 info 请求都要遍历解析每个玩家的 stats JSON（MSPT 每 10s 就会触发一次）。R2-1/R3-3 一并处理。
- `MinecraftEventHandler` 的服务器命令执行桥里有 `CompletableFuture.runAsync` + 50×100ms `Thread.sleep`
  轮询（占用 ForkJoinPool 线程最多 5 秒，两个魔法数）→ R3-3 换虚拟线程 + 抽常量。
- `Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES` 仍是全局 `AtomicBoolean`（各加载器设置），
  属于"平台状态放 core"的遗留，R3-3 可考虑并入平台接口。
- 上一轮审计报告 `AUDIT_REPORT_TEMP.md` **已不在仓库里**（用户删除），本文档第 5–6 节已把其中
  第 2/3 轮需要的关键条目抄录留存。
- **Mixin 合成 lambda 的隐患（用户在第 1 轮记录）**：仍有 4 个 Mixin 注入"未被 NeoForge patch 的类的合成 lambda"
  （`SayCommand`/`TellRawCommand`/`MsgCommand`/`EmoteCommands` 的 `lambda$register$*`）。今天安全，
  但只要 NeoForge 未来 patch 这些类，就会出现与 `MixinReloadableServerResources` 相同的
  `InvalidInjectionException`；届时的修法是改为注入真实方法（如 `CommandSourceStack.sendSuccess`
  或 `PlayerList.broadcastSystemMessage`）。**若第 2/3 轮顺手能改就改，改不了也不强求。**
- 协议换代后 standalone 与所有子服必须**同时升级**（用户已明确接受，不做兼容层）。
