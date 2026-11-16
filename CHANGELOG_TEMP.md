# CHANGELOG_TEMP

> 临时记录，供发布新版本时由 AI session 据此生成正式 CHANGELOG.md。

## 工作 01–04 · 2026/9/8–9/21 · v3 分支上的先行工作（**未随 3.0.0-beta.3 发布**，属于下一版内容）
- 归属核查（2026/10/27）：标签 `3.0.0-beta.3` 指向 `67fc4166`（2026/9/20 11:37），其父依次为 `0f09956a`、`e14dd129`，再往前直接是 `3.0.0-beta.2`（06/19）——即 beta.3 ≈ beta.2 + `e14dd129`（3 个文件 25 行：`gradle.properties` 把 MC 26.2 → 26.3、Loader 0.19.3 → 0.19.5，`MinecraftEventHandler`、`TranslationManager` 各改几行）+ `0f09956a`（1 个文件 1+/3−）+ 版本号与发布文件。该标签的树里：`README_CN.md` 仍是 169 行、没有 `core/src/test`（无 JUnit / SmokeTest）、`minecraft/` 还是**单模块**（只有 `build.gradle` + `src`，只支持 Fabric，**没有 NeoForge**）、wrapper 仍 Gradle 9.5.1、JDA 6.4.2、Netty 4.2.15.Final、Shadow 9.4.2，`fabric.mod.json` 的 loader 下限仍是 `>=0.18.4`、MC 依赖仍是 `>=26.1.0`，且还带着 `vanish_version=1.6.14+26.2`。本节四个工作的提交 `45c3de95`、`c51a4a46`、`f4f46a27`、`83eb291a` 用 `git merge-base --is-ancestor` 对标签全部 **False**、对 `43b9d119 Merge tag '3.0.0-beta.3' into v3-fork`（2026/9/21 20:56）全部 **True**——它们到 9/21 晚才合入主线，**beta.3 用户从未见到本节任何一项**；beta.3 的 26.3 支持来自开发者本人同日的 `e14dd129`，与工作 04 无关（工作 04 是 fork 线在 9/21 另做的一次迁移）。
- 依赖核对与 CI：四个 Actions 升到最新大版本（`checkout@v7`、`setup-java@v6`、`setup-gradle@v6`、`upload-artifact@v7`），删掉冗余的 `wrapper-validation`；`processResources` 改用 `providers.gradleProperty(...)`，Gradle 弃用警告清零。
- **用户可见（收窄）**：`fabric.mod.json` 最低 Fabric Loader 由 `>=0.18.4` 提到 `>=0.19.5`（工作 17 再提到 `>=0.20.1`）；`minecraft_version` 26.2 → 26.3，`minecraft` 依赖由 `">=26.1.0"` 收窄（先 `"~26.3"`，后由开发者改为 `">=26.3.0"`），26.1 / 26.2 不再允许加载。注意：beta.3 发布的树本身已是 26.3，所以这项对 beta.3 用户不是“升级”，只是收窄兼容范围。
- `README_CN.md` 169 → 296 行扩写（消息解析能力矩阵、事件广播矩阵、频道看板占位符、MSPT 告警、模式能力对照、`/dmcc` 命令表、配置体系、网络与安全、日志与运维），并修正六处描述偏差。
- 彻底移除“与其它模组兼容”代码（Vanish 依赖与探测、`/info` 排除隐身玩家）；引入 JUnit 6.1.3 与唯一测试 `SmokeTest`；完成 26.2 → 26.3 API 迁移（`DisplayInfo` 访问器、`CommandSourceStack`、`Pack#open()`）。

## 工作 05 · 2026/9/21 · 3.0 重构·阶段 1「纯清理」
- **对用户零感知**：无任何行为、日志文本、配置键、i18n 键、协议字段、命令语义变化（逐类比对字节码证明等价）。90 个 Java 文件 **+129 / −1777（净减 1646）**：删复述式注释（注释 3210 → 1654 行）、可证明零引用的死代码、改 Java 25 惯用写法（`HexFormat`、`Path.of()`、`.toList()`、静态 `Pattern`）、空行与行尾规范化。
- 保留的契约（后续不可破坏）：1 MB 帧上限、`IdleStateHandler(30,0,0)`、`opList` 防御性拷贝、`CommandSender` 默认 `getOpLevel() == 4`、`ConfigManager` 的两套 null 语义、`Packet` 的 `protected` 构造器与 `serialVersionUID`。
- 验证：字节码 identical 182 / changed 20；全仓 17240 → 15594 行。

## 工作 06 · 2026/9/22 · 阶段 2「结构性去重」
- **唯一有意的行为变更（已获批准）**：`OpSyncManager` 的 OP 同步条件由 `opLevel > 0` 改为 `opLevel >= 0` —— 原来“已绑定但身份组解析出的 OP 为 0”的用户被跳过下发，身份组被移除后 MC 侧残留旧 OP；现会下发等级 0 完成降权（`-1` 表示无法解析，仍跳过）。
- 消除一处缺陷（输出不变）：Discord → MC 组件构建的两份近似重复实现合一，改用具 `null` 保护的 `buildComponentPart(...)`（原先 `TextSegment#text` 为 `null` 会抛 NPE 并中断整条中继）。
- 24 个文件 **+789 / −1656（净减 867）**：`DiscordMessageParser` 1471 → 1174、`LoggerImpl` 477 → 203、`MinecraftEventHandler` 1048 → 960、`MinecraftCommands` 201 → 165；`CommandArgument` 改 record、握手拒绝收敛为 `reject(...)`。验证：145 tests / 0 failures。

## 工作 07 · 2026/9/22 · 阶段 3「并发与性能」（唯一净增行数的阶段，+530）
- **用户可见修复（无功能/配置/命令变化）**：① 自动补全不再阻塞 Netty 事件循环（原最坏 3 次解析 × 3 秒 ≈ 卡 9 秒并卡住全部网络收发，现提交服务器线程、最多等 500 毫秒）；② Discord 用户/成员解析改内存缓存 + 未命中回退 REST + 60 秒负缓存（副作用：改名/头像/角色名最长 60 秒后反映）；③ Webhook 按频道缓存（128 / 5 分钟），稳态 REST 归零（原来**每条**消息都全量 `retrieveWebhooks().complete()`）；④ 链接数据写盘异步化（置脏 + 后台合并落盘，退出/`reload`/`shutdown` 先 flush；`kill -9` 可能丢最后一次变更）；⑤ 停机更快（最坏约 5.5 秒，bind 失败不再泄漏事件循环组）；⑥ `@everyone` 通知删掉恒空的 subtitle 包（200 人在线 800 → 600）；⑦ standalone 终端 stdin EOF 不再空转、`cache/log` 只留 10 个日志、断连待发日志上限 1000 行（原无界可 OOM）；⑧ 翻译加载改同步完成 + 单次 volatile 快照发布；⑨ HTTP 统一 connect 10s / read 20s / call 30s；⑩ MC → Discord 解析按需构建（无 `@`、无 `:emoji:` 不触发 REST），`hasClosingDelimiter` 由 O(n²) 改为每行 O(n)。
- 实现约束：所有缓存有界且都有失效路径，统一为 `DiscordManager.BoundedCache`（access-order `LinkedHashMap` + `removeEldestEntry`，全 `synchronized`，**锁内绝不做 REST**）。
- 验证：145 tests / 0 failures；算法级差分 3,238,452 + 131,715 条用例 0 mismatch；字节码 identical 168 / changed 29。

## 工作 08 · 2026/9/22 · 阶段 4「缺陷修复」
- **安全**：① `/dmcc log` 不再能读取 `logs/` 之外的文件（原来 `/dmcc log ../../config/discord_mc_chat/config.yml` 可读出 bot token 与 shared_secret）；② 单次日志读取上限 8 MiB（`.gz` 按解压后体量计，防 gzip 炸弹）；③ 握手新增状态校验（必须先收到 `HandshakePacket`）与连续失败 3 次限流；④ 原生反序列化加 `ObjectInputFilter` 白名单（仅 DMCC 包 + `java.util.*` / `java.lang.*`，深度 32、引用/流/数组各 1 MiB）。
- **正确性**：`/dmcc execute` 前导空白/Tab 参数错位；`/dmcc info` 快照按请求隔离（原共用一个全局缓存、并发互相清空、中途断开必等到超时）、延迟按 `sentAtMillis` 配对、补 10 秒执行超时；`/dmcc update` 只在“存在兼容且更新的最高版本”时提示；TPS 不再 `Infinity`；Discord → MC 中继 null 文本不再 NPE；`multi_server_client` 下自定义消息缺失不再 NPE（13 处模板查找改可空访问）；Webhook 复用判定改按 ID（原引用比较几乎永不相等 → 反复创建，撞每频道 15 个上限）；机器人不再中继自己的消息；验证码 `Locale.ROOT` 且消除三条路径竞态；`/dmcc help` 按码点对齐；MSPS 恢复通知 `{next_check_time}` 由 `-1` 改真实时间戳；`StringUtils.format` 非法格式串回退原串；代码块分块不再切断 emoji 代理对；事件处理器异常不再互相中断。
- **稳定性**：4 处 Reader 未关闭改 try-with-resources（Windows 上文件不再被锁）；日志改显式 UTF-8；`LoggerImpl.shutdown()` 关闭并重置 writer；shutdown 时回收 OkHttp 连接池。
- **结构**：39 个文件 +1003 / −312，5 个新 i18n 键（两语言各 237 个叶子键、零差异）。验证：149 tests / 0 failures；字节码 identical 164 / changed 40。

## 工作 09 · 2026/9/22 · 实机测试反馈修复（阶段 1–4）
- 91 个主源码文件恢复类级 `@author Xujiayao`（阶段 1 曾删掉全部 92 处）。
- 日志里的反斜杠不再被重复：`StringUtils.escape` 只翻译 `\t \b \n \r \f`、其余原样复制（阶段 4 一度把 `\` 写成 `\\`，ASCII 横幅与 Windows 路径显示成 `\\`）。
- standalone 终端直接回车恢复原有“未知命令”提示；`ConfigManager.getConfigNode` 只对中间段缺失告警（`multi_server_client` 的模板本就没有 `language` 键）。
- 验证：149 tests / 0 failures；`@author` 插入经双重自查，删除/改动行数 = 0。

## 工作 10 · 2026/9/22 · 阶段 5–7（单 jar 双加载器 / 首次加载 / IPv6）
- **用户可见**：① **同一份 jar 同时支持 Fabric 与 NeoForge 26.3**（`FabricDMCC` / `NeoForgeDMCC` 都只调用统一的 `MinecraftModBootstrap.init()`）；② 首次加载直接以预选的 `single_server` 写入 `mode.yml` 并随即生成 `config.yml`，用户只需填 `discord.bot.token`（Standalone 不涉及 `mode.yml`）；③ 控制台日志过滤默认同时覆盖 IPv6（`2001:db8::1`、`fe80::1%eth0`、`::1` 等整段替换为 `redacted`，不误伤时间戳/`std::map`/MAC/UUID/玩家名；**[!] 仅为模板默认值，已生成过 `config.yml` 的用户需手动补该行**）。
- 模块拆分：`minecraft` 拆为 `:minecraft:common`（18 文件 + 统一入口 `MinecraftModBootstrap`）/`:minecraft:fabric`/`:minecraft:neoforge`；`mergeJars` 改为显式合并三个嵌套子项目 jar（嵌套项目不在 `rootProject.subprojects` 中）。
- 行数：93 个 main 文件 16,178 行（代码 11,806 / 注释 2,175 / 空行 2,197），相对重构前基线净减 1,047；删除 16 个临时测试只留 `SmokeTest`。
- 验证：单 jar 取证 6906 条目 / 208 个类 / 重复条目 0；IPv6 规则实测 27 项零失败；首次加载在仓库外空目录、用全新产物端到端跑通，并抓出一个真实缺陷（占位符常量曾写成整行 `mode: your_option_here`，生成文件末行变成裸值）。

## 工作 11 · 2026/9/22 · NeoForge 启动崩溃修复
- **问题**：NeoForge 26.3 启动即崩——`InvalidInjectionException Invalid descriptor on dmcc.mixins.json:MixinReloadableServerResources ... @Inject::lambda$loadResources$3(...)` → `MixinTransformerError`。
- **根因**：NeoForge 的 MC jar 是“反编译 → 打补丁 → 重新编译”的产物，javac 会**重新编号合成 lambda**——Fabric 上 `lambda$loadResources$3(ReloadableServerResources, Object)`，NeoForge 上变成 `lambda$loadResources$5(..., Void)`，而 `lambda$loadResources$3(..., List)V` 是 NeoForge 自己插入的。**结论：合成 lambda 名不可作为跨加载器锚点。**
- **修复**：改注入具名、两 jar 字节码一致的 `loadResources`（`@At("RETURN")`），用 `cir.getReturnValue().whenComplete((res, err) -> EventManager.post(new MinecraftEvents.ReloadResources()))` 投递，事件语义零变化。
- 顺带：`mergeJars` 内访问 `project` 的两条 Gradle 弃用警告清零（改为任务注册前预解析）；记录开发者本人的 `279c9ee2`（`ModeManager` 改为直拷模板并继续走正常读取 + 校验路径、`gradle.properties` 拆出 `minecraft_version_range_fabric` / `minecraft_version_range_neoforge`）与 `6988b6cf`（`mode.yml` 关闭 `errorOnUnmodified`）。
- 验证：`checkmixins.ps1` 对 12 个 Mixin 的每个 `@Inject` 目标（含完整描述符）与每个 `@Shadow`，在 NeoForge patched jar 与 Fabric merged jar 上分别校验 → 两 jar 均 checked=21 failed=0；Fabric 与 NeoForge 实机复测（含 `/reload`）通过。

## 工作 12 · 2026/9/22 · Standalone 关服不刷新频道
- **用户可见修复**：Standalone 用 `shutdown` 关服（终端、Discord 斜杠、JVM 钩子三条路径）时，在断开 bot 之前同步推一次 offline 状态，频道主题与语音频道不再停留于“有服务器在线”（此前只有 `single_server` 覆盖该路径，Standalone 正好落在 10 分钟周期任务的空窗里）。
- 实现：`ChannelUpdateManager.updateOfflineForStandaloneShutdownAndWait()` 与原 `updateOfflineForSingleServerShutdownAndWait()` 共用新私有方法 `updateOfflineAndWait()`；`DMCC.shutdownInternal(boolean)` 在 `NetworkManager.clear()` 之后、`if (!forReload)` 调用（`reload` 不推，避免离线闪现）；主题/名字与当前值相同则跳过 REST，`setTopic/setName` 加 10 秒 timeout（避免被频道编辑桶拖住关服）。
- 未修（按用户裁决）：`multi_server_client` 子服关服后 Standalone 最长仍要等 10 分钟才刷新（频道主题含“最后更新于”时间戳，每次更新必然变化）。

## 工作 13 · 2026/9/22 · DMCC 不可达时的有序待发队列
- **用户可见修复**：DMCC 服务端不可达期间的事件不再丢失。聊天、加入/退出、死亡、进度、指令与控制台日志批次会进入静态队列（跨 `/dmcc reload` 存活），在下次认证成功后按原顺序匀速补发；原先 `NettyClient.sendPacket` 在连接不可用或缓冲区已满时只打一条 WARN 就丢弃，初始连接失败时更是零日志静默丢弃。
- 实现：新增 `PendingPacketQueue`（白名单只放行 `MinecraftEventPacket` 与 `ConsoleLogBatchPacket`，其余沿用“丢弃 + 警告”；上限 2000 条、满则丢最旧并限频告警；补发每 250 ms 一批、每批 5 条 ≈ 20 条/秒；单条失败放回队首保序），新增 6 个 i18n 键 × 2 语言。
- 一期未做（按用户裁决留作二期）：初始化失败后自动重连，仍需手动 `/dmcc reload` 才会重新登录并补发。

## 工作 14 · 2026/10/7 · 账户绑定强化（#326 / #337 / #340 / #343）
- **用户可见（含两处行为变更）**：① 新增 `account_linking.require_linking.enable`（默认 `true`）与 `unlinked_player_action`（`none` / `freeze` / `kick`，默认 `freeze`）——未绑定玩家进服时会被提醒，默认还会被冻结在原地、无法移动与交互（聊天与命令不受影响，绑定成功立即解除）；`none` 等同旧行为（只提醒），`enable: false` 完全静默。② 新增 `account_linking.max_minecraft_accounts_per_discord_user`（默认 `1`）——**默认值本身即行为变更**：一个 Discord 账户默认只能绑定 1 个 Minecraft 账户（`0` = 不限制）。③ Discord `/link` 的 `code` 改为可选：省略时公开回报调用者的绑定状态。④ 新增 `account_linking.nickname_sync.{enable,template}`（默认 `false` / `"+{effective_name}"`）：启用后已绑定成员的昵称固定为模板渲染值、**未绑定成员的昵称被清除**（只能用 Discord 用户名，杜绝改昵称假扮他人），成员自行篡改会被改回，解绑即清除。
- 协议与判定：`LoginSuccessPacket` 增 `requireLinkingEnabled` / `unlinkedPlayerAction`（档位由服务端下发、客户端统一小写归一）；`Link.ResponsePacket` 增 `joinCheck`；新增 `Link.StatePacket` + `CoreEvents.LinkStateEvent`。判定**只在进服时进行一次**，在线被解绑不追溯。
- 冻结实现：`PlayerFreezeManager`（记录维度与坐标、每秒最多抓回一次）+ `MixinServerGamePacketListenerImpl` 两个注入——丢弃移动包，以及放置/攻击/交互/物品栏/容器/载具/切换物品等 15 个操作包（`require = 15`，锚点缺失即启动失败）；聊天与命令刻意不碰。fail-open：DMCC 不可达时既不冻结也不踢出。
- 昵称同步实现：`NicknameSyncManager`（单线程 + 同一用户去重 + 先比较再写即零 REST；触发点为绑定/解绑、已绑定玩家进服、`onGuildMemberJoin`、`onGuildMemberUpdateNickname` 防篡改、JDA ready 后一次全量扫描）；缺少 `NICKNAME_MANAGE` 权限或成员不可管理时跳过并限频告警。
- **破坏性变更（需手工补键）**：DMCC 不做配置迁移，5 个新键不会自动补进已有 `config.yml`，升级后启动会报告缺失键，发布说明需给出可直接粘贴的片段。
- 验证：`javap` 对 Fabric 与 NeoForge 产物复核 15 个注入点全部命中（`handleAcceptTeleportPacket` 存在但**不能**拦截，否则服务端收不到传送确认）；合并 jar 已含新类与新 Mixin。实机回归清单（三种档位、`enable:false` 静默、`/link` 无参、绑定上限、昵称同步含未绑定清空、OP 同步与跨服中继）待开发者执行。
- 四个提交：`6d37fc17`（#326）、`7c0bed92`（#340）、`884f784e`（#343）、`0e6eb942`（#337）。`README_CN.md` 已同步：§4.1 绑定上限、§4.2 进服判定与可选 `code`、新增 §4.4、§7.1 命令表、§8.1 升级补键、§8.3 占位符。

## 工作 15 · 2026/10/7 · 配置校验失败后关服报错
- **问题**：`config.yml` 校验失败（例如升级后缺少工作 14 的 5 个键）或因文件缺失而生成默认配置时，DMCC 以 `-1` 退出，关服钩子再补一段 `NullPointerException: Cannot invoke "java.lang.Boolean.booleanValue()" ...`，并让 `DMCC.java:183` 之后的 client/server 收尾被跳过。
- **根因**：`ConfigManager.config` 只在 `YamlUtils.validate(...)` 通过后才赋值，`getConfigNode` 对未加载的配置返回 missing node，于是**无默认值**的 `getBoolean(String)` 返回 `null`；`ChannelUpdateManager.isAllUpdateDisabled()` 直接对两个可空 `Boolean` 取反参与 `&&`，触发拆箱 NPE。
- **修复**：`ChannelUpdateManager` 四处改用带默认值的 `getBoolean(path, false)`，`updateOfflineAndWait()` 增加 `isJdaUnavailable(...)` 前置判定，`start()` 顺序对齐；`DiscordManager` 的 `shutdown.graceful_shutdown` 也改带默认值 `true`（原 NPE 被 `catch (Exception ignored)` 吞掉、会误走 5 秒超时分支）。
- 验证：用未修复/已修复 jar 各跑“缺键”与“文件不存在”两个场景，修复前均复现与实机一致的 NPE，修复后不再抛出。

## 工作 16 · 2026/10/7 · 冻结档被怪物攻击、踢出原因丢失、无敌泄漏
- **三个问题**：① `kick` 档下控制台总有完整原因，客户端有时只显示通用的“连接中断”；② `freeze` 档下玩家仍被怪物吸引并攻击；③（①② 修好后实测发现的回归）**凡被冻结过一次的玩家再也受不到怪物攻击与伤害**，跨重登持续。
- **根因**：① 原因包确实发得出去（`ServerCommonPacketListenerImpl.disconnect(DisconnectionDetails)` 先发包再断连），丢原因是客户端侧竞态——玩家刚进服、客户端还在加载世界就被断开，断开画面来不及渲染原因；② `LivingEntity.canBeSeenAsEnemy()` 是 `!isInvulnerable() && canBeSeenByAnyone()`，未设无敌的玩家必然被怪物选为目标；③ `setPermanentlyInvulnerable(true)` 会被 `saveWithoutId` 以 NBT 键 `Invulnerable` 写进玩家数据，而 `PlayerList.remove` **先保存再移除**、DMCC 的解冻发生在保存之后——一次冻结即永久无敌。
- **修复**：① 冻结 = 收回移动与交互 + **仅冻结期间无敌**（记录原值、`unfreeze(ServerPlayer)` 精确还原、**刻意不改游戏模式**），并新增 `PlayerFreezeManager.repairOnJoin` 在进服最前面兜底清除残留（进服瞬间不可能处于冻结状态）；② 踢出改为延迟 2 秒（`KICK_DELAY_MILLIS` + `PENDING_KICKS` + `handlerScheduler` 线程），**不往聊天栏发原因**，延迟窗口内完成绑定即可留下。
- **曾被撤回**：初版 `73261c82` 的“破坏/放置方块立即回正”实测毫无作用，全部撤回，恢复原 15 个方法的打包注入（`require = 15`）。
- **教训**：① `MinecraftServer` 的 `TickTask` **不能**用作延迟——`shouldRun` 是 `tick + 3 < tickCount || haveTime()`，`haveTime()` 为真时未来任务会立刻执行，延迟统一改用 `ScheduledExecutorService`；② 任何写进 `Entity` 的状态都要假定会随玩家数据持久化，必须有显式还原与进服兜底。

## 工作 17 · 2026/10/27 · 依赖升级与 `kick` 档改为登录阶段拒绝
- **依赖与工具链（不含文档）**：Gradle 9.7.1 → **9.8.2**（Loom 1.19.3 已要求 Gradle ≥ 9.8，故直接手改 wrapper 的 `distributionUrl` 由旧 wrapper 引导新发行版）；Fabric Loom 1.17-SNAPSHOT → **1.19-SNAPSHOT**（保持仓库既有写法：写 `-SNAPSHOT` 才能在 Loom 发布 1.19.4 时自动跟进；当前最新发布版是 1.19.3，钉死 1.19.3 会停在这一版）；Fabric Loader 0.19.5 → **0.20.1**（`fabric.mod.json` 的 `depends` 同步提到 `>=0.20.1`）；NeoForge 26.3.0.8-beta → **26.3.0.99**；ModDevGradle 2.0.147 → **2.0.148**；JDA 6.6.0 → **6.7.0**；Jackson 3.2.2 → **3.2.4**；OkHttp 5.5.0 → **5.5.1**；Netty 4.2.18 → **4.2.19.Final**；slf4j 2.0.19 → **2.0.20**；jemoji 2.0.0 → **2.1.0**；Shadow 9.6.1、JUnit 6.1.3 已是最新，未动。Java 侧要求随之明确为 **JDK 25**（开发者已升至 25.0.5.0.3），**本项不需要改任何源码**，`build` 一次通过。
- **相对 3.0.0-beta.3 的净变化（写正式 changelog 时以这组为准）**：Gradle 9.5.1 → 9.8.2、Shadow 9.4.2 → 9.6.1、Fabric Loom 1.17-SNAPSHOT → 1.19-SNAPSHOT、Fabric Loader 0.19.5 → 0.20.1、JDA 6.4.2 → 6.7.0、Jackson 3.2.0 → 3.2.4、OkHttp 5.4.0 → 5.5.1、Netty 4.2.15 → 4.2.19.Final、slf4j 2.0.18 → 2.0.20、jemoji 2.0.0 → 2.1.0；`fabric.mod.json` 的 loader 下限 `>=0.18.4` → `>=0.20.1`、MC 依赖 `>=26.1.0` → `>=26.3.0`。NeoForge 支持（`neo_version` 26.3.0.8-beta → 26.3.0.99、ModDevGradle 2.0.147 → 2.0.148）在 beta.3 里完全不存在（beta.3 只有单模块 `minecraft/`、纯 Fabric），属于下一版的**新功能**而不是“升级”。中间步骤散落在工作 01–03 与本工作，最终值以本组为准。
- **`kick` 档改为登录阶段拒绝（用户可见）**：未绑定玩家在**登录阶段**就被拒绝，行为与原版白名单一致——玩家始终进不了世界，不会出现在玩家列表里，也不产生任何进出服务器的消息（Discord 频道与在线玩家**完全无感知**，不再被反复进出刷屏）；只有控制台与日志留下 `Disconnecting <name>: <原因>` 记录，断开界面上直接显示带验证码的绑定提示。`freeze` / `none` 档行为不变。
- 实现：新增 `LoginGate`（`minecraft/common/.../linking/LoginGate.java`）与 `MixinServerLoginPacketListenerImpl`（注入 `verifyLoginAndFinishConnectionSetup` 的 HEAD，`cancellable`）——cancel 后登录停在 `VERIFYING`，原版下一 tick 自动重试，天然形成“挂起登录”，无需额外调度；判定前先跑原版 `PlayerList.canPlayerLogin(...)`，封禁/白名单/IP 封禁/满员仍由原版给出自己的原因（**原版先判**）。通道不可用或 5 秒内无结论则放行（远未到原版 30 秒登录超时），与原“失败放行”一致。原进服后延迟 2 秒踢出的整套逻辑（`KICK_DELAY_MILLIS` / `PENDING_KICKS` / `kickPendingPlayer`）随之删除。
- `README_CN.md` §4.2 / §4.4 已同步：`kick` 的判定时机、与原版白名单的先后顺序（原先的“白名单死结”不再成立）、失败放行的 5 秒超时。**实机验证待开发者执行**（登录挂起、放行、拒绝、原版白名单先判）。

## 工作 18 · 2026/11/10 · 依赖升级（Gradle 9.9.0 / NeoForge 26.3.0.102）
- **依赖与工具链（不含 docs）**：Gradle 9.8.2 → **9.9.0**（用 `wrapper --gradle-version 9.9.0` 跑两轮重新生成，`gradle-wrapper.jar` 与 `gradlew.bat` 随模板更新，`gradlew` 无变化）；NeoForge 26.3.0.99 → **26.3.0.102**；Jackson 3.2.4 → **3.3.0**；Netty 4.2.19.Final → **4.2.20.Final**。其余已是各自最新正式稳定版，未动：Minecraft 26.3（26.4 仅有快照）、Fabric Loader 0.20.1、Fabric Loom `1.19-SNAPSHOT`（按仓库约定，本次解析到 1.19.4）、ModDevGradle 2.0.148、Shadow 9.6.1、JDA 6.7.0、slf4j-api 2.0.20、OkHttp 5.5.1、jemoji 2.1.0、JUnit 6.1.3；CI 的四个 Actions 仍是最新大版本的浮动标签（`checkout@v7`、`setup-java@v6`、`setup-gradle@v6`、`upload-artifact@v7`）。
- **唯一源码级改动**：Gradle 9.9 弃用 Ant builder（`Task.getAnt()`，Gradle 10 将移除），`mergeJars` 里把三个 `:minecraft:*` jar 并入 shadow jar 的 `ant.zip(update: true, ...)` 改用 JDK zipfs（`FileSystems.newFileSystem` + `Files.copy(..., REPLACE_EXISTING)`）；合并语义与产物不变——6943 个条目、无重复条目、`testzip` 通过，`FabricDMCC` / `NeoForgeDMCC` 与 `fabric.mod.json` / `neoforge.mods.toml` / `dmcc.mixins.json` 均在最终 jar 内。
- 验证：`./gradlew clean --no-daemon` + `./gradlew build --no-daemon --warning-mode all` 全绿（`BUILD SUCCESSFUL`），**零编译警告、零 Gradle 弃用警告**（原先的 “Deprecated Gradle features” 汇总行已消失）；1 test / 0 failures。
- `README_CN.md` §1 中过时的 `moddev_version=2.0.147` 与 `neo_version` 26.3.0.8-beta 修正为 **2.0.148 / 26.3.0.102**；用户可见行为无变化。

## 工作 19 · 2026/11/11 · 依赖复核（Gradle 9.9.1）与 `mergeJars` 改用 Gradle `Zip` 任务
- **依赖复核（不含 docs）**：Gradle 9.9.0 → **9.9.1**（2026/11/10 发布的补丁版，修复 3 个安全漏洞；`wrapper` 只改 `gradle-wrapper.properties` 的 `distributionUrl`，`gradle-wrapper.jar` / `gradlew` / `gradlew.bat` 与 9.9.0 模板一致，git 无变化）。其余逐项核对均为当前最新正式稳定版：Minecraft **26.3**（Mojang manifest 最新 release；26.4 只有 `26.4-snapshot-8`）、Fabric Loader **0.20.1**、Fabric Loom **`1.19-SNAPSHOT`**（解析到 **1.19.4**，1.20.0 仅有 alpha）、NeoForge **26.3.0.102**、ModDevGradle **2.0.148**、Shadow **9.6.1**、JDA **6.7.0**、slf4j-api **2.0.20**、Jackson **3.3.0**、OkHttp **5.5.1**、Netty **4.2.20.Final**、jemoji **2.1.0**、JUnit **6.1.3**；CI 四个 Actions 的浮动大版本标签均为最新（`checkout@v7` / `setup-java@v6` / `setup-gradle@v6` / `upload-artifact@v7`，对应最新 patch 7.0.1 / 6.2.0 / 6.4.1 / 7.0.2）。
- **Ant 弃用的最终方案**：Gradle 官方博客与 9.9.1 升级指南都建议用 `Zip` / `Jar` 任务替代 Ant 的 zip 操作。工作 18 的 zipfs 写法虽然能消除弃用警告，但它在 `mergeJars` 执行时**原地改写 `shadowJar` 的输出**，绕开了 Gradle 的任务输入/输出跟踪，`shadowJar` 与 `mergeJars` 的产物边界也不清晰。本工作把 `mergeJars` 改为真正的 `Zip` 任务：输入为 `shadowJar` 与三个 `:minecraft:*` jar，输出到 `core/build/merged/Discord-MC-Chat-<version>.jar`，再由 `:core:build` 复制到根 `build/`；`shadowJar` 保持未合并的中间产物，不再被改写。实现上先放入 shadow jar 的 `META-INF/MANIFEST.MF`（保留 `Main-Class`、`Class-Path`、`Multi-Release`），再放三个模块 jar（排除各自 manifest），最后放 shadow jar 其余条目；`duplicatesStrategy = EXCLUDE` 加上“模块先于 shadow 其余条目”的顺序，保持原先 `ant.zip(update: true)` 的“同名条目由模块覆盖”语义。临时目录与 zipfs 代码全部删除，`mergeJars` 现在可被 Gradle 判定 `UP-TO-DATE`。
- 验证：`./gradlew clean` + `./gradlew build --warning-mode all`（守护进程）全绿，**零编译警告、零 Gradle 弃用警告**；1 test / 0 failures。最终 jar **6943 个条目**，与改动前基线**条目集合完全一致**，逐条目解压校验 0 失败，**0 重复条目**；每个条目的 size/CRC 与 shadow jar / 三个模块 jar 的对应源条目一致；`JarInputStream` 能读到 manifest 与 `Main-Class`；`FabricDMCC` / `NeoForgeDMCC` / `fabric.mod.json` / `neoforge.mods.toml` / `dmcc.mixins.json` 均在最终 jar 内；`core/build/libs` 的 shadow jar 已确认**不含** `FabricDMCC.class`（未被原地合并）。
- `README_CN.md` 已检查：§1 的 `moddev_version=2.0.148` / `neo_version=26.3.0.102` 仍准确，全文没有写死 Gradle 版本，无需改动。工作 18 条目中的 zipfs 描述已被本工作取代，发布 changelog 以本工作为准。
- 建议实机测试（构建/打包层改动，预期零行为变化）：① 同一份 `build/Discord-MC-Chat-3.0.0-beta.3.jar` 分别以 Fabric 与 NeoForge 26.3 启动，确认模组列表版本/加载器元数据正常；② 进服后 `/dmcc` 命令可用、Discord 连接与聊天中继正常；③ 如需要，Standalone 模式启动一次，确认 `Main-Class` 与依赖重定位未受影响。

## 工作 20 · 2026/11/16 · 依赖升级（NeoForge 26.3.0.103）
- **依赖与工具链（不含 docs）**：NeoForge 26.3.0.102 → **26.3.0.103**（2026/11/12 发布的 26.3 稳定线构建，无 `-beta` 后缀）。其余逐项核对均为当前最新正式稳定版，未动：Gradle 9.9.1（`services.gradle.org/versions/current` 仍为 9.9.1 final，wrapper jar 校验和与官方 9.9.1 一致）、Java 25（MC 26.3 的 `javaVersion.majorVersion` 为 25，JDK 25.0.5 为最新 LTS 补丁；JDK 27 虽已 GA 但不适用于 MC 26.3）、Minecraft 26.3（26.4 仅有快照）、Fabric Loader 0.20.1、Fabric Loom `1.19-SNAPSHOT`（最新稳定发布 1.19.4，1.20 仍只有 alpha）、ModDevGradle 2.0.148、Shadow 9.6.1、JDA 6.7.0、slf4j-api 2.0.20、Jackson 3.3.0、OkHttp 5.5.1、Netty 4.2.20.Final、jemoji 2.1.0、JUnit 6.1.3（6.2.0-M1 为里程碑）；CI 四个 Actions 仍是各自最新大版本的浮动标签（`checkout@v7` / `setup-java@v6` / `setup-gradle@v6` / `upload-artifact@v7`）。
- 本项**不需要改任何源码**；`fabric.mod.json`、`neoforge.mods.toml`、`THIRD-PARTY-LICENSES.txt` 均通过构建期属性展开，无需手改；`docs/` 与 `update/versions.json` 不在本次范围。
- 验证：`./gradlew clean` + `./gradlew build`（守护进程）全绿，**零编译警告、零 Gradle 弃用警告**；1 test / 0 failures。
- `README_CN.md` §1 的 `neo_version` 文本同步为 26.3.0.103；用户可见行为无变化。

## 工作 21 · 2026/11/16 · 修复 Unicode 表情解析崩溃并隔离服务端单包异常
- **用户可见修复**：① Discord 消息里的 `:heart:`（`❤️`）、`☺️`、`✌🏻`、`❤️‍🔥` 等多码点表情不再抛 `NoSuchElementException`，不会因为一条消息/一个带爱心的玩家名让 DMCC 通道断开重连；② MC 系统消息/玩家名中的 Unicode 字符（MC→MC、overwrite 路径）同样修复；③ Discord reaction 的 Unicode 表情别名解析增加兜底；④ 服务端数据包处理新增异常隔离：单个事件处理异常只记录日志，不再关闭 DMCC 连接（协议/解码异常仍按原逻辑断开）。
- **根因**：`MessageParserCommon.splitSegmentsByUnicodeEmoji` 用自写的单码点正则拆分，`❤️` 被拆成 `❤` + `VS16`；jemoji 对裸 `❤` 命中 `discordAliases` 为空的 unqualified 条目，`getDiscordAliases().getFirst()` 抛异常。该异常从 `ServerHandler.channelRead0` 逃出，触发 Netty `exceptionCaught` 关闭通道。
- **实现**：新增 `EmojiUtils.preferredDiscordAlias(...)`（discord→github→slack→原字符兜底，绝不对空列表 `getFirst`）；`splitSegmentsByUnicodeEmoji` 改用 `EmojiManager.extractEmojisInOrderWithIndex(...)` 按完整表情序列切段（VS16、肤色、ZWJ、keycap、旗帜整串保留）；reaction 路径复用兜底；`ServerHandler` 把包处理主体抽到 `handlePacket(...)` 并用 try/catch 隔离，新增 i18n key `server.network.packet_handler_failed`（zh/en 双语齐平）；删除手写 `UNICODE_EMOJI_PATTERN`。
- **线程**：解析仍全部在 DMCC 服务端网络线程（Netty worker）/JDA 事件线程上完成，未向 Minecraft 主线程新增任何工作；异常隔离也发生在 Netty 线程。
- 验证：`./gradlew clean` + `./gradlew build`（守护进程）全绿、零编译警告/零 Gradle 弃用警告；**临时 JUnit（EmojiParsingTest，8 例）全部通过后删除**，最终只剩 SmokeTest（1 test / 0 failures）；临时测试覆盖 `❤️`、`☺️`、`✌🏻`、`❤️‍🔥`、`😄`、`💟`、裸 `❤`、混排、`Sted❤ joined the game` 以及 jemoji 全部表情的不抛异常断言。
- `README_CN.md` 已检查：解析矩阵本就声称支持 Unicode 表情，本次修复使该声称成立，无需改文案。
- 建议实机测试：Discord 发 `:heart:`/`❤️`/`☺️`/`✌🏻`/`❤️‍🔥`，与 `:smile:`/`:heart_decoration:` 对照；MC 内粘贴 `❤️`（开启 overwrite 或跨服中继）；reaction 用 ❤️；确认不再出现 `NoSuchElementException` 与 DMCC 断线重连。

## 发布待办（汇总）
- 待处理（按用户裁决都先不动）：`.github/ISSUE_TEMPLATE/bug.yml` 的 v2 残留文案；`docs/package.json` 缺 `license` 字段；`README.md`（英文翻译件）与 `README_CN.md` 的同步；配置迁移工具（发布前会提供在线版本，届时新键不再需要手工补）。
- 已核对无需改动：`update/versions.json` 的 `3.0.0-beta.3` 条目本就是 `"compatibility": ["26.3"]`；存量用户 `config.yml` 补 IPv6 规则按开发者裁决暂不处理。
- 后续可做（收益很小）：`countStatResultEntries` 在 `/dmcc info` 时于请求线程触发 `playerList.saveAll()` + 整目录 stats 扫描；`ServerHandler` 之外仍有 5 处按名线性扫描 `multi_server.servers`；`getPlayerName` 静默 null（消费方已用 UUID 兜底）；`CommandManager.resolveTarget(...)` 是否下沉到独立工具类；Standalone 初始化失败后自动重连（工作 13 二期）。
