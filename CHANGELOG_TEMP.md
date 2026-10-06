# CHANGELOG_TEMP

> 临时记录，供发布新版本时由 AI session 据此生成正式 CHANGELOG.md。

## 工作 01 · 2026/9/8 · 依赖与构建维护
- 核对 `gradle.properties` 中全部版本号（Gradle 9.7.1、Loom 1.17.20、Loader 0.19.5、Shadow 9.6.1、JDA 6.6.0、slf4j 2.0.19、Jackson 3.2.2、OkHttp 5.5.0、Netty 4.2.17.Final、jemoji 2.0.0、Vanish 1.6.15+26.2、MC 26.2）——均为当时最新，无需修改。
- CI 与工具链：四个 Actions 升至最新大版本（`checkout@v7`、`setup-java@v6`、`setup-gradle@v6`、`upload-artifact@v7`）并删除冗余的 `wrapper-validation`（setup-gradle v4+ 已自动校验）；按 Gradle 9.7.1 重新生成 wrapper（新增 `validateDistributionUrl=true`）；VitePress `^2.0.0-alpha.20` + Vue `^3.5.42`、刷新 `docs/yarn.lock`。
- 把 `processResources` 中已弃用的 `Project.getProperties()` 换成 `providers.gradleProperty(...)`，消除全部 Gradle 弃用警告（兼容 Gradle 10）。
- 验证：`clean build` 成功、`--warning-mode all` 零警告；JAR 内 `${...}` 全部展开；`yarn docs:build` 成功。

## 工作 02 · 2026/9/12 · 复核提交 45c3de95 + 依赖再核对
- 复核结论：**该提交全部改动正确可用，无功能性缺陷**（wrapper JAR 校验和与 Gradle 官方 9.7.1 一致、三个 wrapper 文件重新生成后零 diff、属性白名单恰好覆盖全部实际 token、删除 `wrapper-validation` 无副作用、四个 Actions tag 均存在）。
- 改动：Netty `4.2.17.Final` → `4.2.18.Final`（依赖树中全部 netty 组件一致解析）；补 `CHANGELOG_TEMP.md` 末尾换行。
- 验证：`clean build --warning-mode all` 47s 零警告；产物无残留占位符；`yarn install --frozen-lockfile` 与 `yarn docs:build` 通过。

## 工作 03 · 2026/9/12 · 维护建议落实 + README 审计与扩写
- 用户可见：`fabric.mod.json` 最低 Fabric Loader 由 `>=0.18.4` 提升至 `>=0.19.5`（与实际编译所用一致，**收窄兼容范围**，发布时需提示）。
- 构建维护：删除冗余的 `java { sourceCompatibility / targetCompatibility }`（保留 `toolchain` 与 `options.release = 25`）；根 `build.gradle` 的 `afterEvaluate { delete file("build") }` 改为对根项目应用 `base` 插件（副作用：`gradlew build` 不再自动清旧产物）。
- `README_CN.md` 大幅扩写（169 → 296 行）：补齐消息解析能力矩阵、跨子服互转、反应/编辑/删除转发、事件广播矩阵、频道看板占位符、MSPT 告警与退避、账户绑定细节、模式能力对照、独立模式终端与 `/dmcc` 命令表，并新增配置体系、网络与安全、日志与运维三节。
- `README_CN.md` 六处描述偏差修正：计分板排行 → 统计数据排行；`unlink` 按发送端拆两行；`log <file>` 是“以附件形式传输”；`whitelist`/`stats`/`log` 在 `multi_server_client` 列加脚注（须经 Standalone `execute` 委托）；补“明文 TCP、无 TLS，勿暴露公网”；绑定 OP 0 仅为兜底下限；第 10 行改为“以 **Fabric 26.2** 为主要兼容目标”。
- 功能审计：全仓无 `TODO`/`FIXME`/未实现项，README 的每一项声明都已实现。
- 验证：`clean build --warning-mode all` 40s 零警告；单独 `clean` 能移除根 `build/`。

## 工作 04 · 2026/9/20 · 升级至 Minecraft 26.3 + 移除模组兼容代码 + 引入 Gradle 测试
- 用户可见（收窄）：`minecraft_version` 26.2 → 26.3，`fabric.mod.json` 的 `">=26.1.0"` 收窄为 `"~26.3"`，**26.1.2 / 26.2 及更早不再允许加载**，发布时需明确提示；`config_standalone.yml` 示例同步为 `"26.3"`。
- 彻底移除“与其它模组兼容”代码：`vanish_version` 属性与 `compileOnly "maven.modrinth:vanish:..."`、Modrinth 仓库、`Constants.MOD_VANISH_INSTALLED`、`FabricDMCC` 的 `isModLoaded("vanish"/"melius-vanish")` 探测、`buildInfoResponse` 中排除隐身玩家的分支。副作用：`/info` 与 Discord 信息查询**不再排除其它模组隐藏的玩家**。
- 引入 JUnit 6.1.3（所有子项目 `testImplementation` + `useJUnitPlatform()`）与唯一测试 `SmokeTest.java`（打印编译期写入 `mode.yml` 的版本号）；`test` 任务开启 `showStandardStreams` 并把 `workingDir` 指向项目 `build/`，使 `LoggerImpl` 的日志不落在源码树。
- 26.2 → 26.3 API 迁移（编译期强制）：`DisplayInfo` 访问器改 record 风格（`shouldAnnounceChat` → `announceToChat` 等）、`CommandSourceStack` 构造去掉 `String` 文本名参数、`Pack#open()` 返回 `Stream<PackResources>`；其余破坏性变更集中在客户端渲染/输入，纯服务端模组不受影响。`bug.yml` 版本下拉框加 `"26.3"`；删除 `primer.md`。
- 验证：`clean build --warning-mode all` 43s 零警告；`SmokeTest` 通过；`javap` 对照 26.3 官方映射 jar 复核全部 Mixin 目标与 `@Shadow` 存在且签名一致；**本轮曾用 Loom `:minecraft:runServer` 真实启动一次服务端**（RCON 实测 `/dmcc info` 返回 26.3），**此后按开发者决定不再使用该手段**。

## 工作 05 · 2026/9/21 · 3.0 重构·阶段 1「纯清理」
- **对用户零感知**：未改动任何用户可见行为、日志文本、配置键、i18n 键、协议字段、命令语义与资源文件（以字节码逐类比对证明等价）；本轮未更新 `README_CN.md`。
- 7 个分区并行清理 90 个 Java 文件：**+129 / −1777（净减 1646 行）**。四类内容：① 复述式注释与 JavaDoc（含删除全部 92 处 `@author Xujiayao`；注释 3210 → 1654 行，保留真正承载契约的说明）；② 可证明零引用的死代码（每处先 grep 全仓确认）；③ Java 25 惯用写法（`HexFormat`、`Path.of()`、`.toList()`、静态 `Pattern` 等 30 余处）；④ 空行与行尾规范化。
- 保留的既有契约（后续不可破坏）：1 MB 帧上限、`IdleStateHandler(30,0,0)`、`opList` 防御性拷贝、`CommandSender` 默认 `getOpLevel() == 4`、`ConfigManager` 的两套 null 语义、`Packet` 的 `protected` 构造器与 `serialVersionUID`。
- 验证：`build -x test` 9s；字节码 **identical 182 / changed 20 / added 0 / removed 0**（与各分区申报集合完全一致）；全仓 17240 → 15594 行。
- 教训：两处编译错误源于我给分区代理的审计建议本身有误（`import okhttp3.Cache;` 被误判无用；`Reader#transferTo` 返回 `long`）——后续新写法先核实签名。

## 工作 06 · 2026/9/22 · 3.0 重构·阶段 2「结构性去重」
- **唯一有意的行为变更（已获批准）**：`OpSyncManager` 的 OP 同步条件由 `opLevel > 0` 改为 `opLevel >= 0` —— 原来“已绑定但身份组解析出的 OP 为 0”的用户被跳过下发，导致身份组被移除后 MC 侧残留旧 OP；现会下发等级 0 完成降权（`-1` 表示无法解析，仍跳过）。
- 一处缺陷消除（输出不变）：Discord → MC 组件构建的两份近似重复实现合一，改用具 `null` 保护的 `buildComponentPart(...)`（原先 `TextSegment#text` 为 `null` 会抛 NPE 并中断整条中继）。本轮未更新 `README_CN.md`。
- 5 个分区去重 24 个文件：**+789 / −1656（净减 867）**。要点：`DiscordMessageParser` 1471 → 1174（7 个模板构建方法收敛为 `buildTemplateSegments(...)`、11 处 split 收敛到 `MessageParserCommon`）；`LoggerImpl` 477 → 203（删 26 个空方法与 25 个转发重载，改实现 `LegacyAbstractLogger`）；`MinecraftEventHandler` 1048 → 960（11 处广播循环收敛、`RegistryOps` 改按 `serverInstance` 失效）；`MinecraftCommands` 201 → 165（8 个子命令注册收敛为 `sub(...)`）；`CommandArgument` 改 record、`Command` 新增 `default String usage()`；握手拒绝收敛为 `reject(...)`。刻意放弃 `sourcePlaceholders(...)`（会把 `Map.of()` 变可变 `HashMap`）。
- 验证：`build` 10s；**145 tests / 0 failures**；字节码 **identical 159 / changed 31 / added 7 / removed 12**（removed 恰为 10 个匿名 `CommandArgument` 类与 2 个私有 `MarkdownState`）；JUnit 无法构造 JDA `Message`/`Member`，故在仓库外建一次性差分转储工装（`Proxy` 伪造 × 33 条语料 × 11 个入口 = 440 行），两阶段类树 **0 差异**（并用 `-verbose:class` 排除假阳性）；全仓 15594 → 14727 行。

## 工作 07 · 2026/9/22 · 3.0 重构·阶段 3「并发与性能」
- **用户可见修复（无功能/配置/命令/语法变化）**：① 自动补全不再阻塞 Netty 事件循环（原来最坏 3 次解析 × 3 秒 ≈ 卡 9 秒并卡住全部网络收发；现提交到服务器线程、最多等 500 毫秒）；② Discord 用户/成员解析改内存缓存优先，未命中回退 REST、失败进 60 秒负缓存（副作用：改名/头像/角色名最长 60 秒后反映）；③ Webhook 按频道缓存（128 / 5 分钟；原来**每条**消息都全量 `retrieveWebhooks().complete()`，稳态下现为 0 次 REST）；④ 链接数据写盘异步化（置脏 + 后台合并落盘，退出/`reload`/`shutdown` 先 flush；副作用：`kill -9` 可能丢最后一次变更）；⑤ 停机更快（最坏约 5.5 秒，bind 失败不再泄漏事件循环组）；⑥ `@everyone` 通知删掉恒为空的 subtitle 包（200 人在线时包数 800 → 600）；⑦ standalone 终端 stdin EOF 不再空转、`cache/log` 只留 10 个日志、断连待发日志上限 1000 行（原无界可 OOM）；⑧ 翻译加载改为同步完成 + 单次 volatile 快照发布（不会看到半加载状态）；⑨ HTTP 统一 connect 10s / read 20s / call 30s；⑩ MC → Discord 解析按需构建（无 `@`、无 `:emoji:` 不触发 REST；`hasClosingDelimiter` 由 O(n²) 改为每行 O(n)）；⑪ `MsptMonitor`/`BotPresenceManager` 调度器创建移入锁内、通知发送移到锁外；⑫ 3 处裸线程工厂改用 `ExecutorServiceUtils.newThreadFactory(...)`。
- **唯一净增行数的阶段**：5 个分区改造 25 个文件 + 两轮纯注释精简（25 文件、零代码改动），main 源码 14,712 → 15,242（+530，注释精简回收 516 行）。所有缓存都有界且都有失效路径，统一实现 `DiscordManager.BoundedCache`（access-order `LinkedHashMap` + `removeEldestEntry`，方法全 `synchronized`，**锁内绝不做 REST**）。
- 纠正一处前提：`String.split("\\.")` 在 JDK 9+ 命中快路径、本身不编译正则；`StringUtils` 的 `matches(".*%\\d+\\$s.*")` 预编译为 `Pattern` 时**必须继续用 `matches()` 而非 `find()`**（22 个样本中 8 个结果不同）。
- 验证：`build` 11s；**145 tests / 0 failures**；一次真实编译失败已修（`DiscordManager` 匿名 `LinkedHashMap` 子类体内简单名 `Entry` 解析为 `Map.Entry`，报 `类型变量数目错误; 需要2`）——教训：分区代理自报的 `javac exit 0` 不能替代编排侧的完整构建；差分转储 440 行 / 0 差异；字节码 **identical 168 / changed 29 / added 8 / removed 0**；注释精简以 classes 对 classes 同口径比对 **165/165 一致**（“jar 对 classes”比出的 49 个 changed 是 shadow 重定位产物）；算法级差分 3,238,452 + 131,715 条用例 0 mismatch。

## 工作 08 · 2026/9/22 · 3.0 重构·阶段 4「缺陷修复」
- **安全**：① `/dmcc log` 不再能读取 `logs/` 之外的文件（原来 `/dmcc log ../../config/discord_mc_chat/config.yml` 可读出 bot token 与 shared_secret）；② 单次日志读取上限 8 MiB（`.gz` 按解压后体量计数，防 gzip 炸弹）；③ 握手新增状态校验（必须先收到 `HandshakePacket`）与连续失败 3 次限流；④ 原生反序列化加 `ObjectInputFilter` 白名单（仅 DMCC 包 + `java.util.*` + `java.lang.*`，深度 32、引用/流/数组各 1 MiB）。
- **正确性**：`/dmcc execute` 前导空白/Tab 参数错位；`/dmcc info` 快照按请求隔离（原共用一个全局缓存、并发互相清空、中途断开必等到超时）、延迟按 `sentAtMillis` 配对、补 10 秒执行超时；`/dmcc update` 只在“存在兼容且更新的最高版本”时提示；TPS 不再 `Infinity`；Discord → MC 中继 null 文本不再 NPE；`multi_server_client` 下自定义消息缺失不再 NPE（13 处模板查找改可空访问）；Webhook 复用判定改按 ID（原引用比较几乎永不相等 → 反复创建，撞每频道 15 个上限）；机器人不再中继自己的消息；验证码 `Locale.ROOT` + 三条路径竞态消除；`/dmcc help` 按码点对齐；MSPS 恢复通知 `{next_check_time}` 由 `-1` 改真实时间戳；`StringUtils.format` 非法格式串回退原串；代码块分块不再切断 emoji 代理对；事件处理器异常不再互相中断；OP 同步三处静默失败各记 warn。
- **保留的既有行为**：Bot 显示名继续用 `getAsTag()`（`名称#1234`；一度改为 `getEffectiveName()`，按“严格零感知”回退）；Embed 标题截断保留修复为 50 字符（原判断 `> 50` 却截到 20）。
- **资源与稳定性**：4 处 Reader 未关闭改 try-with-resources（Windows 上文件不再被锁）；日志改显式 UTF-8；`LoggerImpl.shutdown()` 关闭并重置 writer（不再静默丢日志）；数据包语言扫描失败不再丢弃已加载翻译；shutdown 时回收 OkHttp 连接池。
- **结构**：6 个分区修复 39 个文件（+1003 / −312）+ 5 个新 i18n 键（两语言文件各 237 个叶子键、零差异）；`ConfigManager.getInt(String, int)` 返回 `int`、新增 `getBoolean(String, boolean)`、`Logger` 新增 `info/warn(String, Throwable)`。未修（有理由）：`ServerDMCC` 在 `NettyServer.start()` 返回 -1 时仍启动 MSPS 监控、`MinecraftEventHandler.getPlayerName` 静默 null、`MsptMonitor` 轮询失败复用 `discord.manager.broadcast_failed` 键、WARN/ERROR 未改 `System.err`、`countStatResultEntries` 仍在 Netty 线程写盘。
- 验证：`build` 11s；**149 tests / 0 failures**；字节码 **identical 164 / changed 40 / added 2 / removed 1**；Mixin 做了 javap 全量体检（12 个目标全部存在）。**注：其中“`lambda$loadResources$3` 的第二参数 `Object` 就是真实类型”后来在 NeoForge 实机上被推翻——见工作 11。**

## 工作 09 · 2026/9/22 · 3.0 重构·实机测试反馈修复
- 背景：阶段 1–4 的实机测试除下列 4 项外全部通过；本轮只做“回退与补齐”，不引入新行为变更，也不更新 `README_CN.md`。
- 每个 Java 文件恢复类级 `@author Xujiayao`：91 个主源码文件（`core` 73 + `minecraft` 18），45 个在既有 JavaDoc 末尾追加、46 个补回三行 JavaDoc（阶段 1 曾删掉全部 92 处）。
- 日志中的反斜杠不再被重复：`StringUtils.escape` 回退为只翻译 `\t \b \n \r \f`、其余原样复制（阶段 4 一度把 `\` 写成 `\\`，ASCII 横幅与 Windows 路径显示成 `\\`）。
- standalone 终端直接回车恢复原有“未知命令”提示（EOF 保护保留）；`ConfigManager.getConfigNode` 回退为只对中间段缺失告警（`multi_server_client` 的模板本就没有 `language` 键，否则必然误报）。
- 验证：`build` 35s；**149 tests / 0 failures**（`escape("C:\path")` 用例改为断言“原样不变”以锁死反斜杠转义）；`@author` 插入经 `git diff -U0` + 基线快照双重自查，删除/改动行数 = 0。

## 工作 10 · 2026/9/22 · 3.0 重构·阶段 5–7 一并交付
- 用户可见：① **同一份 jar 同时支持 Fabric 与 NeoForge 26.3**（Fabric 入口 `FabricDMCC`、NeoForge 入口 `NeoForgeDMCC` 都只调用统一的 `MinecraftModBootstrap.init()`）；② 首次加载直接以预选的 `single_server` 写入 `mode.yml` 并随即生成 `config.yml`，用户只需填 `discord.bot.token`（Standalone 不涉及 `mode.yml`）；③ 控制台日志过滤默认同时覆盖 IPv6（`2001:db8::1`、`fe80::1%eth0`、`::1` 等替换为 `redacted`，`地址:端口` 形式整段替换，不误伤时间戳/`std::map`/MAC/UUID/玩家名；**[!] 只是模板默认值——已生成过 `config.yml` 的用户需手动补该行**，按用户裁决不加代码兜底）。
- 模块拆分：`minecraft` 拆为 `:minecraft:common`（18 文件 + 新增统一入口 `MinecraftModBootstrap`）/`:minecraft:fabric`/`:minecraft:neoforge`；`:core` 不变，`settings.gradle` 改四个 `include`。
- 构建接线（两处 Gradle 报错已修）：`mergeJars` 改为显式合并三个嵌套子项目 jar（嵌套项目不在 `rootProject.subprojects` 中），每次合并前清空 `build/merged_temp`；`:minecraft:fabric` 不应用 Loom（否则报 `Configuration 'minecraft' has no dependencies`），改为 `java-library` + 显式 Fabric 仓库 + `fabric-loader`；两个加载器模块都在 `dependencies {}` 之外用顶层 `def commonJar = files(project(":minecraft:common").tasks.named("jar").flatMap { it.archiveFile })`（块内委托是 `DependencyHandler`）；NeoForge 用 ModDevGradle 2.0.147。
- 首次加载实现（已被工作 11 的 `279c9ee2` 替换）：当时 `ModeManager` 用 `DEFAULT_MODE`/`MODE_PLACEHOLDER` 做模板替换并提前 `return true`；删除零引用 i18n 键 `utils.config.mode.edit_prompt`。
- 交付清理：删除 `core/src/test/java/com/`（16 个临时测试与 `TestEnv`），只留 `SmokeTest.java`。行数：93 个 main 文件 16,178 行（代码 11,806 / 注释 2,175 / 空行 2,197），相对重构前基线净减 1,047。
- 验证：`gradlew projects` 层级正确；`build` 成功（首次含 NeoForge 产物解析与 7301 个 MC 源文件重编译）；删测试前 149 tests / 0 failures；首次加载端到端验收（仓库外桩类让 `IS_MINECRAFT_ENV` 为 true、全新空目录启动真实产物）抓出一个真实缺陷并已修（占位符常量曾写成整行 `mode: your_option_here` 而替换值只有 `single_server`，生成文件末行变成裸值）；IPv6 规则实测 **27 项零失败**；单 jar 取证 6906 条目 / 208 个类 / 重复条目 0；字节码 **identical 201 / changed 5 / added 2 / removed 0**；语言文件各 236 个叶子键；`README_CN.md` 已同步（§1 单 jar 双加载器、§2.3 IPv4+IPv6、§8.1 首次生成）。

## 工作 11 · 2026/9/22 · 实机测试修复（NeoForge 启动崩溃 + Gradle 弃用）
- **① NeoForge 26.3.0.8-beta 启动即崩溃 → 已修复**：崩溃为 `InvalidInjectionException Invalid descriptor on dmcc.mixins.json:MixinReloadableServerResources ... @Inject::lambda$loadResources$3(...)` → `MixinTransformerError` → `Failed to start the minecraft server`。根因：NeoForge 的 MC jar 是“反编译 → 打补丁 → 重新编译”的产物，javac 会**重新编号合成 lambda**——Fabric 的恒等 lambda 是 `lambda$loadResources$3(ReloadableServerResources, Object)`，NeoForge 上它变成 `lambda$loadResources$5(ReloadableServerResources, Void)`，而 `lambda$loadResources$3(ReloadableServerResources, List)V` 是 NeoForge 自己插入的（`ConditionContext.clear()` + `List.forEach`），故旧注入点解析到同名不同签名的方法。修复：改为注入具名、两 jar 字节码一致的 `loadResources`（`@At("RETURN")`），用 `cir.getReturnValue().whenComplete((res, err) -> EventManager.post(new MinecraftEvents.ReloadResources()))` 投递——事件语义零变化（每次调用一次、完成线程、成功与异常都触发）。**结论：合成 lambda 名不可作为跨加载器锚点。**
- **② Gradle 弃用清零**：`--warning-mode all` 原有 2 条，均在 `core/build.gradle` 的 `mergeJars` 动作内访问 `project`（Gradle 10 会报错的 `Invocation of Task.project at execution time`）；改为在任务注册前预解析 `mergedJars`，动作内用 `tempDir.deleteDir()` 与 `merged.jar.get().asFile`。
- **③ 记录开发者本人的两个提交（非本轮我改）**：
    - `279c9ee2`：`ModeManager` 删掉 `DEFAULT_MODE`/`MODE_PLACEHOLDER`，创建分支改为 `Files.copy(inputStream, MODE_FILE_PATH, StandardCopyOption.REPLACE_EXISTING)` 直拷模板并**继续走正常读取 + 校验路径**（不再提前 `return true`）；`mode.yml` 锚点行 `mode: your_option_here` → `mode: single_server`；`gradle.properties` 删掉 `mod_id`/`mod_name`/`mod_license`/`minecraft_version_range`，改为 `minecraft_version_range_fabric=>=26.3.0` 与 `minecraft_version_range_neoforge=[26.3.0,)`（`build.gradle` 的 `propertiesToExpand` 同步换名），加载器元数据随之改为字面量或改用新属性。
    - `6988b6cf`：`ModeManager.load()` 的 `YamlUtils.validate(..., true)` → `false`，即关闭 `errorOnUnmodified`（模板直拷出的 `mode.yml` 与模板逐字相同，否则会被判为“配置键仍未从模板更改”而校验失败）。
- 验证：`build` 成功、`--warning-mode all` **再无任何 `Deprecated` 行**；新工装 `checkmixins.ps1` 对 12 个 Mixin 的每个 `@Inject` 目标（含完整描述符）与每个 `@Shadow`，分别在 NeoForge `minecraft-server-patched-26.3.0.8-beta.jar` 与 Fabric `minecraft-merged-deobf-26.3.jar` 上校验 → **两个 jar 均 checked=21 failed=0**（含 `Util.getNanos()J` 的 `ordinal=0` 在两 jar 中都落在 `initServer()` 之后第一处、`AdvancementRewards.grant` 各 1 处且被 `isDone()` 守卫、`stopServer` 3 处 / `onServerExit` 9 处分布互斥）；产物 208 个类 / 12 个 mixin 类；字节码（阶段 6 产物 → 本轮）**identical 206 / changed 2 / added 0 / removed 0**（changed 仅 `MixinReloadableServerResources` 与 `ModeManager`）。
- **实机复测**：NeoForge 26.3 与 Fabric 两侧（含 `/reload` 触发 `ReloadResources`）已由开发者在实机确认无问题。

## 工作 12 · 2026/9/22 · 实机反馈修复（Standalone 关服不刷新频道）
- **用户可见修复**：Standalone 用 `shutdown` 关服（终端、Discord 斜杠、JVM 钩子三条路径）时，会在断开 bot 之前同步推一次 offline 状态，频道主题与语音频道不再停留于“有服务器在线”。此前只有 `single_server` 覆盖该路径（`ServerHandler` 的 `SERVER_STOPPING` 分支），Standalone 从未覆盖，正好落在 10 分钟周期任务的空窗里（`ChannelUpdateManager.start()` 首次延迟 10 s、之后每 `interval_minutes` 分钟）。
- 实现：`ChannelUpdateManager` 新增 `updateOfflineForStandaloneShutdownAndWait()`，与原有的 `updateOfflineForSingleServerShutdownAndWait()` 共用新私有方法 `updateOfflineAndWait()`（`isAllUpdateDisabled()` 守卫 + `doUpdateChannelsSync(buildOfflineContext())`）；`DMCC.shutdownInternal(boolean)` 在 `NetworkManager.clear()` 之后、`clientInstance`/`serverInstance.shutdown()` 之前以 `if (!forReload)` 调用。只对 `standalone` 生效（`single_server` 已在 `ServerHandler` 推过一次，重复推送会烧掉 Discord 每频道约 2 次/10 分钟的主题编辑配额）；`reload` 不推以免离线闪现。同步分支另加两层保护：主题/频道名与当前值相同则跳过 REST，`setTopic/setName` 加 10 秒 timeout（避免被 Discord 频道编辑桶拖住关服）。
- 验证：`:core:compileJava`、`:core:test`（SmokeTest 3.0.0-beta.3）通过。
- 已知同类项未修（按用户裁决）：`multi_server_client` 子服关服后 Standalone 最长要等 10 分钟才刷新频道——用户明确“不修，保持 10 分钟周期刷新”，因为频道主题含“最后更新于”时间戳、每次更新必然变化；`ServerHandler` 该分支的同步 `.complete()` 本身运行在 Netty event loop 上（既有取舍）。
- 待发队列：见工作 13。限流调研：出站限流完全依赖 JDA 6.6.0 默认 REST 队列（未设 `setRestConfig` 等），全项目无 429/Retry-After 处理；唯一自研限流器是 `player.command` 的 10 条/10 秒滑动窗口（超限静默丢弃）。

## 工作 13 · 2026/9/22 · 实机反馈修复（DMCC 不可达时的有序待发队列）
- **用户可见修复**：DMCC 服务端不可达期间的 Minecraft 事件不再丢失。原来 `NettyClient.sendPacket` 在连接不可用（含缓冲区已满）时只打一条 WARN 就丢弃，初始连接失败时 `NetworkManager.sendPacketToServer` 更是零日志静默丢弃。现在聊天、加入/退出、死亡、进度、指令与控制台日志批次会进入静态队列，在下次与 DMCC 服务端认证成功后按原顺序匀速补发。
- 实现：新增 `core/src/main/java/com/xujiayao/discord_mc_chat/client/PendingPacketQueue.java`（public，静态持有以跨 `/dmcc reload`，与 `ConsoleLogTailer` 的 `pendingLines` 同一先例）。白名单只放行 `MinecraftEventPacket` 与 `ConsoleLogBatchPacket`；其余（Request/Response/握手/心跳）沿用原「丢弃 + `send_while_disconnected` 警告」，因为它们的调用方已在计时或状态已过期。上限 2000 条（满则丢最旧并限频告警），补发每 250 ms 一批、每批 5 条（≈20 条/秒），单条写入失败即放回队首以保证顺序。
- 接线：`NettyClient.sendPacket` → `boolean trySend(Packet)`（额外检查 `isWritable()`，写失败记 `client.network.send_failed`）；`ClientDMCC.sendPacket` 发送失败转交队列；`NetworkManager.sendPacketToServer` 在 `clientInstance == null` 时同样转交队列；`ClientHandler` 的 `LoginSuccessPacket` 触发 `onAuthenticated(channel)`（挂在 channel 的 event loop 上）、`channelInactive` 触发 `onConnectionLost()`；`ClientDMCC` 构造器登记 host/port/serverName，地址变化即清空旧队列；`ClientDMCC.shutdown()` 在真关服（非 reload）时丢弃队列。
- 新增 6 个 i18n 键 × 2 语言：`client.network.send_failed`、`client.pending_queue.{queued,drained,dropped_full,send_failed,discarded_identity_changed,discarded_shutdown}`。
- 一期未做（按用户裁决留作二期）：初始化失败后自动重连——现在仍需手动 `/dmcc reload` 才会重新登录并补发。
- 验证：`gradlew.bat :core:compileJava :core:test --offline` → BUILD SUCCESSFUL（SmokeTest 3.0.0-beta.3）。

## 工作 14 · 2026/10/7 · 账户绑定强化（#326 / #337 / #340 / #343）
- **用户可见（含两处行为变更）**：① 新增 `account_linking.require_linking.enable`（默认 `true`）与 `unlinked_player_action`（`none` / `freeze` / `kick`，默认 `freeze`）——未绑定玩家进服时会被提醒，默认还会被冻结在原地、无法移动与交互（聊天与命令不受影响，绑定成功立即解除）；`none` 等同旧行为（只提醒），`enable: false` 则完全静默（既不提醒也不限制）。② 新增 `account_linking.max_minecraft_accounts_per_discord_user`（默认 `1`）——**默认值本身即行为变更**：一个 Discord 账户默认只能绑定 1 个 Minecraft 账户（`0` = 不限制）。③ Discord `/link` 的 `code` 选项改为可选：省略时公开回报调用者的绑定状态；顺带确认全仓 0 处 `setEphemeral`，所有命令回复本就对频道内所有人可见。④ 新增 `account_linking.nickname_sync.{enable,template}`（默认 `false` / `"+{effective_name}"`）：启用后已绑定成员的昵称固定为模板渲染值、**未绑定成员的昵称被清除**（只能用 Discord 用户名，杜绝改昵称假扮他人），成员自行篡改会被改回，解绑即清除，模板变更在下次进服/再次绑定时校正。
- 协议与判定：`LoginSuccessPacket` 增 `requireLinkingEnabled` / `unlinkedPlayerAction`（档位由服务端下发，客户端统一转小写归一）；`Link.ResponsePacket` 增 `joinCheck`；新增 `Link.StatePacket` + `CoreEvents.LinkStateEvent`（绑定成功后广播以解除限制）。判定**只在进服时进行一次**，在线被解绑不追溯（下次进服再判）。
- 冻结实现：新增 `PlayerFreezeManager`（记录维度与坐标，每秒最多抓回一次）与 `MixinServerGamePacketListenerImpl` 的两个注入——丢弃移动包，以及放置/攻击/交互/物品栏/容器/载具/切换物品等 15 个操作包（`require = 15`，锚点缺失即启动失败）；聊天与命令注入刻意不碰，保证玩家能读到验证码提示并执行 `/dmcc link`。fail-open：`Link.RequestPacket` 不在 `PendingPacketQueue` 白名单内，DMCC 不可达时既不会被冻结，也不会在重连后被延迟踢出。
- 昵称同步实现：新增 `NicknameSyncManager`（单线程 `DMCC-NicknameSync` + 同一用户去重 + 先比较再写即零 REST；触发点为绑定/解绑成功、已绑定玩家进服、`onGuildMemberJoin`、`onGuildMemberUpdateNickname` 防篡改，以及 JDA ready 后的一次全量扫描）；需要“管理昵称”权限，权限缺失或成员不可管理时跳过该服务器/成员并限频告警。`Permission.MANAGE_NICKNAMES` 在 JDA 6.6.0 中不存在，正确常量为 `NICKNAME_MANAGE`。
- **破坏性变更（需手工补键）**：DMCC 不做配置迁移，上述 5 个新键不会自动补进已有 `config.yml`，升级后启动会报告缺失键，发布说明需给出可直接粘贴的片段。
- 验证：`gradlew.bat build --offline` BUILD SUCCESSFUL；用 `javap` 对 Fabric `minecraft-merged-deobf-26.3.jar` 与 NeoForge recompile 产物复核 15 个注入点全部命中（`handleAcceptTeleportPacket` 存在但**不能**拦截，否则服务端收不到传送确认）；合并产物 `core/build/libs/Discord-MC-Chat-3.0.0-beta.3.jar` 已确认含 `PlayerFreezeManager`、`NicknameSyncManager` 与新 Mixin 类。实机回归清单（默认 `freeze` 档、`enable:false` 静默、`none` 档、`kick` 档、`/link` 无参公开回复、绑定上限、昵称同步含未绑定清空、以及 OP 同步与跨服中继等旧功能回归）待开发者执行。
- `README_CN.md` 已同步：§4.1 绑定上限、§4.2 进服判定与可选 `code`、新增 §4.4「绑定强制策略与昵称同步」（含白名单死结的答复：与 `kick` 档并用时会形成“进不去 → 收不到码 → 绑不了”，应先用 5.4 的 `whitelist` 代理放行，或改用 `freeze` / `none` 档）、§7.1 命令表、§8.1 升级补键、§8.3 占位符。
- 四个提交：`6d37fc17`（#326 骨架与全局开关）、`7c0bed92`（#340 绑定上限）、`884f784e`（#343 昵称同步）、`0e6eb942`（#337 冻结/踢出）。

## 工作 15 · 2026/10/7 · 实机反馈修复（配置校验失败后关服报错）
- **问题**：当 `config.yml` 校验失败（例如升级后缺少 工作 14 新增的 5 个键）或因文件缺失而生成默认配置时，DMCC 会中止初始化并以 `-1` 退出，此时关服钩子会额外打印一段 ERROR：`NullPointerException: Cannot invoke "java.lang.Boolean.booleanValue()" because the return value of "com.xujiayao.discord_mc_chat.config.ConfigManager.getBoolean(String)" is null`，栈为 `ChannelUpdateManager.isAllUpdateDisabled(ChannelUpdateManager.java:429)` ← `updateOfflineAndWait(:91)` ← `updateOfflineForStandaloneShutdownAndWait(:87)` ← `DMCC.lambda$shutdownInternal$0(DMCC.java:183)`。
- **根因**：`ConfigManager.config` 只在 `YamlUtils.validate(...)` 通过后才赋值（`core/src/main/java/com/xujiayao/discord_mc_chat/config/ConfigManager.java:106-111`），校验失败时保持 `null`；`getConfigNode` 对未加载的配置返回 missing node（`ConfigManager.java:119-124`），于是**无默认值**的 `getBoolean(String)` 返回 `null`（其 Javadoc 明示"或 null"）。`isAllUpdateDisabled()` 直接对两个可空 `Boolean` 取反参与 `&&`，触发拆箱 NPE，并让关服流程在 `DMCC.java:183` 处中断，后续的 client/server 实例收尾被跳过。
- **修复**：`core/src/main/java/com/xujiayao/discord_mc_chat/server/discord/ChannelUpdateManager.java` 四处——① `isAllUpdateDisabled()` 改用带默认值的 `getBoolean(path, false)`（键缺失即视为"未启用"，配置未加载时不再拆箱）；② `updateOfflineAndWait()` 增加 `isJdaUnavailable(DiscordManager.getJda())` 前置判定：初始化没走到 Discord 登录时没有任何频道状态可推送，直接返回；③ `start()` 把 JDA 判定提到配置读取之前，两处顺序一致；④ `updateTextChannelTopics()` 与 `updateVoiceChannelNames()` 里的同名开关读取也一并改用带默认值的重载，该类内不再有任何可空 `Boolean` 拆箱。另外把 `core/src/main/java/com/xujiayao/discord_mc_chat/server/discord/DiscordManager.java:1087` 的 `getBoolean("shutdown.graceful_shutdown")` 改为带默认值 `true`（同属可空拆箱，原先只是被 `catch (Exception ignored)` 吞掉，异常时会误走 5 秒超时分支）。
- **验证**：A/B 复现——用未修复的 jar（`C:\Users\Xujiayao\Desktop\DMCC\Discord-MC-Chat-3.0.0-beta.3.jar` 的副本）与修复后的 jar 各跑两个场景（`config.yml` 缺少新键 / `config.yml` 不存在），由一个临时驱动类按关服顺序调用 `ModeManager.load()` → `ConfigManager.load()` → `ChannelUpdateManager.updateOfflineForStandaloneShutdownAndWait()`：修复前两个场景都抛出与实机日志完全一致的 NPE，修复后两个场景都不再抛异常。`gradlew.bat build --offline` → BUILD SUCCESSFUL。

## 发布待办（汇总）
- 待处理（发布前，三项按用户裁决都先不动）：`.github/ISSUE_TEMPLATE/bug.yml` 的 v2 残留文案；`docs/package.json` 缺 `license` 字段；`README.md`（英文翻译件）与 `README_CN.md` 的同步。
- 已核对无需改动：`update/versions.json` 的 `3.0.0-beta.3` 条目本就是 `"compatibility": ["26.3"]`（`update/versions.json:3-7`）；存量用户 `config.yml` 补 IPv6 规则、四个命令类的合成 lambda 注入点两项按开发者裁决暂不处理。
- 后续可做（仅记录，收益很小）：`countStatResultEntries` 在 `/dmcc info` 时于请求线程触发 `playerList.saveAll()` + 整目录 stats 扫描；`ServerHandler` 之外仍有 5 处按名线性扫描 `multi_server.servers`；`getPlayerName` 静默 null（消费方已用 UUID 兜底）；`CommandManager.resolveTarget(...)` 是否下沉到独立工具类。
