# TEMP_TODO —— 第 3 轮交接清单

> **这份文件是给"上下文被压缩后的我"看的。** 它自包含地记录了项目现状、不可违反的红线、
> 关键代码地图，以及第 3 轮要做的每一件事。开工前请完整读一遍。
> 第 3 轮交付完成后，把本文件内容归纳进 `CHANGELOG_TEMP.md` 并删除本文件。

---

## 0. 当前状态一句话

**第 1 轮**（骨架 + 双加载器 + 模组兼容扩展点）与**第 2 轮**（解析层统一 + 报文层换代 +
命令层收敛 + 去重 + 死代码清理）**均已完成并交付**（第 2 轮详见 `CHANGELOG_TEMP.md` 的 `## 工作 08`；
净减 2110 行主源码）。

**第 3 轮（用户无感知的内部瘦身 + 并发/性能/正确性修正）也已完成**，详见 `## 工作 09`
（该轮相对第 2 轮**净增约 1257 行**：新增的都是真实机制而非样板，取舍理由已写在那一节）。

**下一步：第 4 轮**（用户可见的 YAML 可读性改造 + 文档收尾），内容见第 7 节。
两轮刻意分开，便于逐轮核对：第 3 轮理论上用户零感知，第 4 轮才会动用户读得到的东西。

---

## 1. 项目速览

- **DMCC (Discord-MC-Chat) v3**：Minecraft ↔ Discord 双向聊天桥。Java 25 / Gradle 9.7.1 / **仅 Minecraft 26.2**。
- **模块布局**（`settings.gradle`：`:core`、`:fabric`、`:neoforge`）：
  - `core/` —— 平台无关：DMCC 主控、Server/Client、Netty 协议、命令、账户绑定、配置、i18n、工具。
    **不得出现 `net.minecraft` 导入**（唯一例外：`utils/EnvironmentUtils` 用反射探测）。
  - `minecraft-common/` —— **共享源码目录，不是 Gradle 项目**：11 个 Mixin、`MinecraftEventHandler`、
    `MinecraftPlatformHost`、`ModIntegration(s)`、`TranslationManager`、`MinecraftCommands`、
    `DmccRconConsoleSource`。只依赖原版类，不依赖任何加载器 API。
  - `fabric/`、`neoforge/` —— 入口点 + 模组元数据 + 加载器专属模组兼容（各不到 100 行）。
- **产物**：根 `build/Discord-MC-Chat-<版本>.jar` 是**单个通用 JAR**，同一文件可用于 Fabric、
  NeoForge、以及 `java -jar` 独立运行（`Main-Class` 保留）。
- **关键版本**：`gradle.properties` 里的 `mod_version=3.0.0-beta.2`、`minecraft_version=26.2`、
  `loader_version=0.19.5`、`loom_version=1.17-SNAPSHOT`、`neo_version=26.2.0.87`、
  `moddev_version=2.0.147`、`shadow_version=9.6.1`、`junit_version=6.1.3`、JDA 6.6.0、
  slf4j 2.0.19、Jackson(tools.jackson) 3.2.2、OkHttp 5.5.0、Netty 4.2.18.Final、jemoji 2.0.0、
  Vanish 1.6.15+26.2（仅 Fabric，compileOnly）。
- **构建命令**（**直接用用户家目录的 `~/.gradle`，不要再建临时 GRADLE_USER_HOME**）：
  - `./gradlew clean build :core:test --warning-mode all` —— 唯一要跑的验证（**不跑 `runServer`/`runClient`**）。
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
14. **不要为了"减少重复"而合并语义不同的实现**。第 2 轮的两处教训：
    两套 markdown 扫描器（Discord 消费 `\` 且要求成对闭合；MC 保留 `\` 且切换关闭、跨行延续）
    与 `queue(_ -> {}, _ -> {})`（单参 `queue()` 会让 JDA 把预期内的限流丢弃打成错误日志）
    都是**看起来重复、实则行为不同**的代码。合并前先写差分测试。
15. **改协议 = 同时改 `PacketType` 与 `PacketCodec.CLASSES`**（外加 `Packages` 里的 record）。
    协议不需要兼容层（两端强制同版本），但漏改一处只会在运行期暴露。
16. **写测试时的工作目录是 `core/build/test-run`**，读工程文件要用 classpath 资源或绝对路径；
    临时文件放 `build/` 下。临时测试交付前必须删除（只留 `SmokeTest`）。

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

### 第 1.3 轮（用户测试通过后的 4 项小改动，已完成并提交）

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

- `MinecraftEventHandler` 约 1105 行。`buildInfoResponse` 仍调用
  `StatsCommand.countStatResultEntries(...)` 与 `StatsCommand.normalizeMinecraftNamespace(...)`
  （平台层反向依赖 core 命令类的最后两处，`R3-1`/`R3-3` 随 `StatsReader` 移出；
  注意 `countStatResultEntries` 会触发 vanilla `PlayerList.saveAll()`，**从非主线程调用有线程安全隐患**）。
- **报文层（第 2 轮新建）**：`core/network/protocol/`
  `Packet`（接口，只有 `type()`）/ `PacketType` / `Packets`（全部报文为 record）/
  `PacketCodec`（JSON 信封 + 显式 `PacketType → record` 映射）/ `JsonPacketEncoder`/`Decoder` /
  `CommandFileAssembler`（大文件分片重组）/ `ProtocolException`。
  **改协议时必须同步改 `PacketType` 与 `PacketCodec.CLASSES` 两处**，否则运行期才会报
  "Unknown packet type"。`Packets.Disconnect.args` 是 `String[]`（不是 `Object[]`）；
  `DiscordRelay` / `MinecraftEvent` 的事件类型组件叫 `eventType`（不能叫 `type`，
  否则与 `Packet.type()` 冲突）。
- **解析层（第 2 轮重建）**：`core/server/message/` + `core/server/discord/DiscordMessageAdapter`
  （唯一允许碰 JDA 的解析相关类）+ `core/network/message/TextSegment`。
  `MessageParserCommon` 是 **public** 的（`mentionNotification(...)` 被 `ServerHandler` 用）。
  `DiscordMessageParser` 里的 `truncateMainRaw` / `truncateReplyRaw` / `enforceSingleLine`
  是**包级可见**的，方便同包测试直接调用。
- `DMCC.init(PlatformHost)` 接收平台实现并 `Platform.set(...)`；`reload()` 复用同一个 host。
- 配置目录：`./config/discord_mc_chat/config.yml`；`custom_messages/<lang>.yml`；缓存 `./config/discord_mc_chat/cache/`。
- 日志：独立模式写 `./logs/DMCC_<时间戳>.log`（`.gitignore` 已忽略 `logs/`、`config/`）。
  **该文件是惰性创建的**：`LoggerImpl` 只在真正写下第一条日志时才建 `logs/` 与文件；
  测试的工作目录被设在 `core/build/test-run`，所以测试日志只会出现在 `core/build/test-run/logs/` 里。
  `R3-1` 要重构 `LoggerImpl`（反射派发 → `enum`）时**不要破坏这两条性质**。
- **Gson 的例外说明**：红线 1 的"只能用 Jackson"针对的是 **DMCC 自己的 JSON**（配置、语言文件、
  自定义消息、网络协议），这些全部是 Jackson。`MinecraftEventHandler` 里出现的
  `com.mojang.serialization.JsonOps` + `com.google.gson.JsonElement` 是**原版
  `ComponentSerialization.CODEC` 的固有接口**（改动前就在用），不是 DMCC 新引入的 Gson 依赖。

---

## 5. 第 2 轮已完成的内容（不要重复做）

详细记录见 `CHANGELOG_TEMP.md` 的 `## 工作 08`。一句话版：

- **解析层**：`server/message/` 重构为 `DiscordMessageParser`（745，已脱离 JDA）+
  `MinecraftMessageParser`（452）+ `MessageParserCommon`（462，通用 token 表 `TokenRule` /
  `splitByPattern` / 单次扫描的 `splitByRules`）+ `MarkdownParser`（337，两种方言）+
  `MessageTemplates`（142）+ `MentionResolver`（45）+ `MessageExtras`（42）+ `TextSegment`（163，吸收
  `TextSegmentUtils`）。JDA 只出现在新的 `server/discord/DiscordMessageAdapter`。
  **两套 markdown 扫描器刻意不合并**（D 消费 `\` 且要求成对闭合；MC 保留 `\` 且已激活分隔符切换关闭，
  样式可跨行）——差分测试证明强行合并会改变输出。
- **报文层**：`network/packets/` + `network/serialization/` **整棵删除**，改为
  `network/protocol/`：`Packet` / `PacketType`（23 值）/ `Packets`（全 record）/ `PacketCodec`
  （JSON 两字段信封 + 显式类型映射）/ `JsonPacketEncoder`/`Decoder` / `CommandFileAssembler`。
  **协议里已无 Java 原生序列化**；`/log` 大文件按 256 KiB base64 **逐帧分片**，>1 MiB 不再断连。
- **命令层**：`Command.CommandArgument` 改为 record（删 9 处匿名类）、新增 `Command.usage(...)`、
  新增 `CommandTargets`（`/console` 与 `/execute` 的目标校验与解析完全合并）、删 13 个空构造器。
- **server/discord**：`ServerHandler.channelRead0` 拆成 8 个 handle* 方法 + `reject(...)`；
  `DiscordManager` 合并 `sendBotMessage*`、提取 `postServerMessage(...)`；
  **排除命令正则与脱敏正则改为按配置变更缓存**（原来每消息/每行重编译）；
  `broadcastMinecraftRelay` 与 `broadcastMinecraftTellRawRelay` 的判定收敛为 `relayTargets(...)`；
  `ChannelUpdateManager` 删掉 4 个一行包装与 `buildOfflineContext()`。
- **minecraft-common**：合并两份样式管线、删 `copySegmentWithText`、提取
  `onServerThread(...)` / `broadcast(Component)` / `notifyMentionedPlayers(...)` /
  `dmccSource(...)`；`RegistryOps` 改为 `onServerStarted` 时建一次；
  `MinecraftCommands` 的两个 record 共享方法并修掉 `getPlayerUuid()/getPlayerName()` 返回 null 的问题；
  `MixinServerGamePacketListenerImpl` 提取 `postPlayerCommand(...)`。
- **死代码**：删除两个整包 + `TextSegmentUtils` + `LoggerImpl` 的注释死代码；
  `ExecutorServiceUtils` 不再吞 `InterruptedException`；`StatsCommand` 两处捕获缩窄为
  `IllegalArgumentException`。
- **行数**：主源码 2108 增 / 4218 删 = **净 −2110**；整体净 −2094。

### 第 2 轮留下的、第 3 轮要注意的点

1. **测试的工作目录是 `core/build/test-run`**（不是项目目录），见红线 13。
2. `MinecraftEventHandler` 仍有 `serverInstance` 静态字段与 `sendLinkCode` 等处的
   `if (serverInstance == null) return;`（为了不让 `UUID.fromString(null)` 变成 NPE），
   抽 `onServerThread` 时**刻意没有**把 6 个广播 lambda 包进静默 catch——那会把现在会冒到
   服务端 tick 循环的异常吞掉。
3. `ChannelUpdateManager` 的 `queue(_ -> {}, _ -> {})` **刻意保留**双空回调：单参 `queue()`
   会让 JDA 把每一次预期内的限流丢弃都打成错误日志。
4. `DiscordManager` **没有**做物理拆分（JDA 生命周期 / 消息派发 / 控制台转发）；控制台转发与
   类内私有状态耦合较深，且是用户高频路径，本轮只做了类内消重。
5. 13 个命令类**没有**按域合并成 4 个（合并主要是搬家而非删代码），只做了三处证据充分的去重。
## 6. 第 3 轮：内部瘦身与并发/性能修正（**用户无感知**，已完成）

> 本轮的定义是"用户理论上零感知"：只做日志/配置/工具瘦身与并发、性能、正确性修正。
> 不改任何配置键、命令、消息文案、渲染结果与界面数字。**凡是会改动用户能看到/读到的东西，
> 一律挪到第 4 轮**（见第 7 节）。

### R3-1 日志 / 配置 / 工具瘦身

1. **`LoggerImpl` 去掉反射派发与每行格式化分配**
   - `Map<String, Method> logMethods/logThrowMethods` + `log("INFO", …)` 字符串查表 →
     新增私有 `enum Level`，每个常量缓存自己的 ANSI 颜色与两个反射 `Method`（`plain` /
     `withThrowable`，均 `volatile`），调用点改成 `log(Level.INFO, …)`。**日志文本格式完全不变**
     （`[时间] [线程/级别]: 消息`、文件版无色、控制台版带 ANSI）。
   - `new SimpleDateFormat("HH:mm:ss")`（每行一次对象分配 + 一次 `format` 的锁竞争）→
     `DateTimeFormatter` 静态常量；文件名时间戳同理。
   - 顺带清掉 `Class.forName("dmcc_dep.org.slf4j.Logger".replace("dmcc_dep.", ""))` 这种
     "替换了个寂寞"的写法（结果就是 `org.slf4j.Logger`）。
   - trace/debug 仍是刻意空实现（接口要求实现，但 DMCC 从不发这两级；`isXxxEnabled` 恒 false
     让调用方提前跳过，参数格式化也就不发生）。
2. **`ConfigManager` 修正三个真实缺陷（保留全部校验能力）**
   - `config` / `mode` 字段**加 `volatile`**：它们由 reload 线程写、被其它线程读，原来没有任何
     发布保证。
   - `getConfigNode` 每次调用都 `path.split("\\.")` → 改为 `PATH_SEGMENTS` 缓存切分结果
     （一条系统消息路径有 5–8 次读取）。
   - **缺失路径的告警从"每次读取都打"改为"每个配置版本每条路径打一次"**：像
     `console_forwarding.channel` 这类在当前模式下合法缺失的可选键，原来会在每次握手/事件时
     刷一条 WARN；现在 `WARNED_MISSING_PATHS` 去重，并在 `load()` 时清空，诊断价值保留、刷屏消失。
   - 未做"加载时解析成类型化快照"：那需要重写三个模块里所有配置读取点，收益是省一次
     `JsonNode.path()` 遍历（亚微秒级），而风险是动到用户明确要求保留的校验行为。**属主动取舍。**
3. **`I18nManager` 线程安全**：`DMCC_TRANSLATIONS` 原为普通 `HashMap`，会在**客户端登录时的
   Netty 线程**被 `clear()` + 重填，而读取来自 MC / JDA / 日志线程 → 改为
   "构建新 `HashMap` → `Map.copyOf` → 一次性 volatile 发布"，读者永远看不到半满的 map；
   `language` / `customMessages` 字段加 `volatile`。
4. **`MojangUtils` 加 TTL 与负缓存**：原来只缓存成功结果，失败**完全不缓存** → Mojang 故障期间
   每条聊天消息都会为每个未解析 UUID 重发一次阻塞 HTTP。
   现在：离线 UUID 永久缓存、在线成功缓存 24h、**失败缓存 5 分钟**；
   且失败时如果之前成功解析过，**继续返回上次已知的名字**而不是退化成裸 UUID（避免用户看到
   UUID 这种可见退化）。返回值与调用方行为完全一致，只是 HTTP 次数下降。
5. **`LogFileUtils.readLogFile` 收敛路径**：原来 `resolve(fileName).normalize()` 之后直接读，
   `/log ../../server.properties` 可以穿越出 `./logs`。现在校验规范化后的路径仍在
   `./logs` 之内，越界直接返回 null（行为与"文件不存在"一致）。
6. **`JsonUtils`**：三个 `toStringMap` 重载收敛为一个真正实现（`Reader`/`InputStream` 版本
   先转字符串再走同一个入口），注释说明为何用 `YAML_MAPPER` 解析 JSON。
7. **`StringUtils`**：`escape()` 先单次扫描，无特殊字符时**直接返回原串**（原来每行日志都要做
   5 次 `replace` 并分配 5 个中间字符串）；`format()` 里 `str.matches(".*%\\d+\\$s.*")`
   **每次调用都重新编译正则** → 改为预编译 `Pattern` + `find()`（语义等价）。
8. **`Constants.OK_HTTP_CLIENT`**：原来 `new OkHttpClient()` 完全依赖库默认值；现在显式设置
   连接 10s / 读 15s / 写 15s / 整次调用 20s，避免播放器名查询或 `/dmcc update` 长时间挂住
   调用线程。

### R3-3 并发与性能修正

1. **`NetworkManager.requestInfoSnapshot` 按请求关联**：修掉
   `infoCache.clear()` 在加锁之前、`expectedResponses` 取快照导致中途断连必然等满超时、
   以及**响应无请求关联**（四个调用线程互相清缓存、把上一轮响应当成本轮）这三个问题。
2. **`CommandManager` 改虚拟线程** + 关闭 reload 的 TOCTOU 窗口（`COMMANDS` 改为
   构建新 map 后一次性替换发布，`commandExecutor` 加 `volatile`）。
3. **把阻塞工作移出 Netty IO 线程**：
   - `MinecraftMessageParser.buildMentionContext` 每条消息重建全量别名表 + 对每个已绑定账户做
     阻塞 JDA 调用 → 改为带 TTL 的不可变目录缓存；
   - `DiscordManager.getOrCreateWebhook` 每条 webhook 消息 `retrieveWebhooks().complete()`
     → 按频道缓存句柄（发送失败则失效重试一次）；
   - `DiscordEventHandler.onCommandAutoCompleteInteraction` 阻塞单线程 JDA 事件池最长 5 秒
     （Discord 的补全截止是 3 秒，且期间所有 Discord 事件停摆）→ 移到专用执行器。
4. **`BotPresenceManager` 真防抖** + 修掉"两个开关都关时提前 return、旧任务不会被取消"的 bug。
5. **`ConsoleLogTailer.pendingLines` 加上限**（断连期无界积压）并修掉"flush 在 `isConnected()`
   检查之后移除行、期间断连会静默丢整批"。
6. **`LinkedAccountManager.save()` 异步化**：内存 map 同步更新（读语义不变），磁盘写入改为
   专用线程 + 临时文件原子替换，避免在命令线程/Netty IO 线程上做全量美化重写。

> `MemberCachePolicy.ALL`、`MinecraftEventHandler` 的 50×100ms 轮询、`capability` 枚举等
> 未在本轮处理，理由见第 9 节"已知问题与小尾巴"。

---

## 7. 第 4 轮：用户可见部分（**下一步执行**）

> 这一轮的全部内容都会改动"用户能读到/看到"的东西，因此单独成轮，与前两轮的
> "零感知内部重构"分开提交，便于用户逐轮核对。

### R4-1 用户可见 YAML 可读性改造（用户明确反馈：测试用户抱怨 YAML 难读）

- 范围：`config.yml`（三份模板）、`custom_messages/*.yml`。**内部 `lang/*.yml` 不用美化**（开发者可读即可）。
- **保留全部预填测试值**（见红线 4：`xujiayao`、`SMP`/`CMP`、`in-game-chat`、`111111`/`222222`、
  `your_token_here` 等，必须原样保留，不要"清理成示例值"）。
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
- 验收：三份模板都能通过校验器（写测试）；`processResources` 展开正确；
  "全新安装"路径下生成的配置未修改即可通过校验。
- ⚠️ 第 3 轮已经证实：**三份模板的配置键集合与 `custom_messages` 的键集合都没有变化**
  （113 / 18 / 153 键 + `custom_messages` 逐行相同），所以第 4 轮可以放手美化注释，
  只要不动键名与预填值，老用户的既有配置不会被破坏。

### R4-2 文档收尾

- `README_CN.md`：§8 配置参考重写（逐键说明 + 占位符总表）；§10 运维（日志、缓存、性能注意项）；
  §1 记录最终行数与精简结果。（`README.md` 是翻译件，平时不动。）
- `CHANGELOG_TEMP.md` 追加 `## 工作 NN`（第 3 轮与第 4 轮各一节）。
- 最终验证清单（用户执行）：全新安装体验评价（最重要）、多人同时聊天/刷消息观察延迟、
  MSPT 预警、频道看板、Bot 状态、按新注释改一个 `custom_messages` 模板并 reload、两平台最终回归。
- 把本文件内容归纳进 `CHANGELOG_TEMP.md` 后删除本文件。

---

## 8. 交付协议（每轮固定动作）

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

## 9. 已知问题与小尾巴（随时可能被问）

- `fabric.mod.json` 仍引用不存在的 `icon/icon.png`（沿用旧状，未新增 PNG）。
- `MinecraftEventHandler.buildInfoResponse` 在信息响应里调用 `StatsCommand.countStatResultEntries`，
  而后者会 `provider.saveAll()`（vanilla `PlayerList.saveAll()`）——**从非主线程调用有线程安全隐患**，
  且每次 info 请求都要遍历解析每个玩家的 stats JSON（MSPT 每 10s 就会触发一次）。R3-1/R3-3 一并处理。
- ~~`TextSegment` 可变性~~：**部分缓解**——协议层已不再需要 Java 序列化，`TextSegment` 现在只作为
  解析期的可变数据容器与 JSON 载荷；`R3-3` 只需确认跨 Netty 共享实例没有残留写入。
- `MinecraftEventHandler` 的服务器命令执行桥里有 `CompletableFuture.runAsync` + 50×100ms `Thread.sleep`
  轮询（占用 ForkJoinPool 线程最多 5 秒，两个魔法数）→ R3-3 换虚拟线程 + 抽常量。
- `Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES` 仍是全局 `AtomicBoolean`（各加载器设置），
  属于"平台状态放 core"的遗留，R3-3 可考虑并入平台接口。
- 上一轮审计报告 `AUDIT_REPORT_TEMP.md` **已不在仓库里**（用户删除），本文档第 5–7 节已把其中第 2/3 轮需要的关键条目抄录留存。
- **Mixin 合成 lambda 的隐患（用户在第 1 轮记录）**：仍有 4 个 Mixin 注入"未被 NeoForge patch 的类的合成 lambda"
  （`SayCommand`/`TellRawCommand`/`MsgCommand`/`EmoteCommands` 的 `lambda$register$*`）。今天安全，
  但只要 NeoForge 未来 patch 这些类，就会出现与 `MixinReloadableServerResources` 相同的
  `InvalidInjectionException`；届时的修法是改为注入真实方法（如 `CommandSourceStack.sendSuccess`
  或 `PlayerList.broadcastSystemMessage`）。**第 2 轮未动它（改动收益不确定、风险明确）；若第 3 轮顺手能改就改。**
- 协议换代后 standalone 与所有子服必须**同时升级**（用户已明确接受，不做兼容层）。
- **第 2 轮遗留的小风险**：`MinecraftEventHandler.serializeComponent`/`deserializeComponent`
  现在复用 `onServerStarted` 时缓存的 `RegistryOps`。若未来某个 MC 版本在 `/reload` 时重建
  注册表，这份缓存可能变陈旧（届时改成按 `registryAccess()` 变化失效即可）。
