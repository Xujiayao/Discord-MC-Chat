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

## 工作 22 · 2026/11/16 · Discord 昵称同步彻底重写（可靠性 + 线程隔离）
- **用户可见修复**：① 开启 `nickname_sync` 后，启动/`reload` 不再出现“0 个成员被检查”（旧实现从不请求成员分块，大公会成员缓存几乎为空）；② 普通成员绑定后自行修改 Discord 昵称一定会被改回（包括重启 DMCC 后第一次改名——旧实现只监听 `GuildMemberUpdateNicknameEvent`，而 JDA 对未缓存成员只发通用事件）；③ 昵称写入失败/事件遗漏会在 5 分钟校准内自愈；④ 服务器所有者与角色层级不低于机器人的成员会被明确跳过并说明（Discord 限制，原先只有笼统警告）；⑤ MC 玩家改名后，下次进服（或重启 DMCC）昵称即跟随新名字；⑥ 模板超长时按 code point 截断，不会再把 emoji 劈成半个代理对；模板中的换行/控制字符会被清洗。
- **根因**：`JDABuilder.createDefault(...)` 默认 `ChunkingFilter.NONE`，DMCC 只设了 `MemberCachePolicy.ALL`，JDA 从不请求成员分块；同步只读缓存且缓存未命中静默跳过；JDA 对未缓存成员只触发 `GuildMemberUpdateEvent`；无任何周期补偿；3 秒前缘防抖会吞事件；`MojangUtils` 玩家名缓存进程内永久有效；失败只打一条 WARN 不重试。
- **实现**：`NicknameSyncManager` 重写为单线程 `DMCC-NicknameSync` 调度器：开启功能时 JDA builder 显式 `setChunkingFilter(ChunkingFilter.ALL)`（仅该功能开启才付启动代价）；启动扫描先检查每个 guild 的成员缓存完整性、必要时异步 `Guild#loadMembers()` 再扫；单用户同步在缓存未命中时回退 `Guild#retrieveMemberById`（10 秒超时 + 60 秒负缓存）；监听通用 `GuildMemberUpdateEvent` 覆盖未缓存成员；每用户 1.5 秒尾缘去抖替代前缘丢弃；每 5 分钟对已绑定用户做一次校准；新增 `rememberPlayerName(...)`，由进服/Link 请求与绑定成功处用包内的当前 MC 名更新（不再受 Mojang 名称永久缓存影响）；模板渲染做控制字符清洗 + 按 code point 截断；所有跳过/失败/汇总均有明确日志（每原因限频一次，扫描结束一条 INFO 汇总）。
- **线程**：所有成员加载、REST 查询、昵称写入、校准均只跑在专用 `DMCC-NicknameSync` 线程（或 JDA 自己的事件/回调线程），扫描入口只做 `execute` 提交；`rememberPlayerName` 仅是并发 Map 写入。**未向 Minecraft 主线程新增任何工作**，不会造成 `Can't keep up` 的服务器卡顿。
- 验证：`./gradlew clean` + `./gradlew build`（守护进程）全绿、零编译警告/零 Gradle 弃用警告；**临时 JUnit（NicknameSyncHelpersTest，9 例）全部通过后删除**，最终只剩 SmokeTest（1 test / 0 failures）；覆盖模板渲染/清洗/code point 截断、最新可解析账号选择、Mojang 返回 UUID/`N/A` 视为失败等纯逻辑。
- `README_CN.md` §4.4 已按实际行为重写（触发时机、启动加载全部成员、5 分钟校准、所有者/高角色成员限制、模板修改需 reload 且 reload 会全量重推、截断与换行清洗）。
- 建议实机测试：见交付说明中的昵称同步清单。

## 工作 23 · 2026/11/16 · 昵称同步名称来源修复与告警降噪
- **实测定位（非推断）**：Standalone 启动扫描对正版账号 69ca1318-24ef-4560-b35e-85865d85ab49 的 Mojang 名称查询返回 **HTTP 429**（用户网络正常，属 Mojang 会话服务器侧限流）；此前 22:37 的两次 Cannot resolve 即同一原因，60 秒负缓存导致 reload 后重复出现、恢复后自愈。旧实现每次启动都依赖该外部查询，且 links.json 未保存玩家名（offlinePlayerName 仅对 v3 UUID 落盘）。
- **修复**：LinkEntry 新增持久化 playerName（绑定成功时写入，之后每次玩家进服由 Link 请求更新；旧 links.json 缺该字段视为 null，自动向后兼容、无需手工迁移）；昵称解析优先级改为 内存进服名 → links.json 持久化名 → 离线 UUID 保存名 → Mojang 查询兜底，正常情况不再发起外部请求；xujiayao 这类“没有昵称可改”的未绑定高角色成员不再产生层级 WARN，只有真正需要改动昵称时才告警。
- **日志增强（上一轮未提交改动并入本工作）**：Mojang 查询失败现在带出真实原因（HTTP 状态码/异常/负缓存上次原因）；updated/update_failed/cannot_manage_member/name_unresolved 使用稳定的 Discord 用户名而不是可变昵称，避免 Discord member +Xujiayao ... to "+Xujiayao" 这类废话。
- 验证：./gradlew clean + ./gradlew build（守护进程）全绿、零编译警告/零 Gradle 弃用警告；临时 JUnit（NicknameSyncHelpersTest 12 例 + LinkEntryJsonTest 2 例，覆盖 links.json 旧格式兼容/新格式往返、名称优先级、模板辅助）全部通过后删除，最终只剩 SmokeTest（1 test / 0 failures）。
- README_CN.md §4.4 已同步名称来源与告警行为；用户可见变化：不再因 Mojang 限流而跳过昵称同步（旧记录在玩家首次进服后也会写入持久化名称）。
- 建议实机测试：① 重启 Standalone（不先让绑定玩家进服）应不再出现名称解析 WARN；② 绑定玩家改名后进服，昵称应跟随新名字；③ 未绑定且无昵称的高角色成员不再刷层级 WARN，有昵称仍会 WARN；④ 连续 reload 不再触发 Mojang 查询与 429。

## 工作 24 · 2026/11/16 · 合并 links.json 名称字段为 playerName
- **数据模型合并**：LinkEntry 由 {minecraftUuid, linkedAt, offlinePlayerName, playerName} 合并为 {minecraftUuid, playerName, linkedAt}（playerName 按要求放在中间）。offlinePlayerName 只是历史遗留：v3 离线 UUID 在绑定时记录的名字，与 playerName 语义重合且从不更新。
- **自动迁移**：load() 改为解析 JsonNode 并迁移旧字段——旧文件只有 offlinePlayerName 时自动填入 playerName，并立即重写一次文件；同时存在两个字段时优先 playerName；缺名字的旧 v4 记录保持 null，继续走 Mojang 兜底。旧文件无需手工迁移。
- **消费方统一**：/link、/links、Discord 提及别名表不再为已持久化的名字调用 Mojang（改为 LinkedAccountManager.resolvePlayerName(entry)，仅旧记录兜底），顺带减少外部请求与 429 风险；NicknameSyncManager 删除 offlinePlayerName 分支，解析优先级简化为 内存进服名 → playerName → Mojang 兜底；删除 isOfflineUuid(...)。
- **兼容性提示**：新版 links.json 不再写 offlinePlayerName；回滚到旧版 DMCC 时，离线 UUID 的名称显示会退化为 N/A/UUID（正版 UUID 不受影响，旧版会查 Mojang）。建议不要在使用离线 UUID 绑定时回滚。
- 验证：./gradlew clean + ./gradlew build（守护进程）全绿、零编译警告/零 Gradle 弃用警告；临时 JUnit（NicknameSyncHelpersTest 11 例 + LinkEntryJsonTest 4 例，覆盖旧格式迁移/双字段优先/新格式往返与字段顺序/名称优先级）通过后删除，最终只剩 SmokeTest（1 test / 0 failures）。
- README_CN.md §4.4 无需再改（名称来源描述不变）；用户可见变化：links.json 结构更简洁，启动不再为已有名字的账号查询 Mojang。

## 工作 25 · 2026/11/17 · 修复待发队列的身份判定与丢弃诊断
- **实测问题**：初次把 SMP 从 single_server 切到 multi_server_client 时，DMCC 初始化失败期间入队的 1 条 SERVER_STARTED 在首次成功连接时被当作“服务端地址已变更”丢弃；同时 single_server 每次 reload 的内部回环端口都是临时端口，导致 reload 保留的队列必然被判定为身份变更而丢弃，与“队列跨 reload 存活”的设计意图矛盾。
- **修复**：PendingPacketQueue.onClientIdentity 改为接收稳定的身份键；**空身份→首次身份不再视为变更**，初始化前暂存的消息保留并在登录后按序补发（INFO，含类型统计）；之后的身份变更仍按安全策略丢弃，但 WARN 现在带旧/新身份与按类型统计（如 SERVER_STARTED×1, CONSOLE_LOG×2）。身份赋值与队列清空/保留在同一把队列锁内完成，避免与并发入队交错。
- **single_server 稳定身份**：ClientDMCC 新增 identityKey 构造参数，single_server 传入 "single_server/Internal"（与临时端口无关），reload 不再假性丢弃；multi_server_client 的 4 参数构造仍使用 host:port/serverName 作为身份，行为不变。
- **文档**：README_CN §9 增加“断线期间的消息暂存”说明（缓存范围、reload/关服语义、身份变化与初始化前暂存的补发规则）；i18n 中英同步更新（新增 kept_before_identity，改写 discarded_identity_changed）。
- 验证：./gradlew clean + ./gradlew build（守护进程）全绿、零编译警告/零 Gradle 弃用警告；临时 JUnit（身份采用/相同身份/变更丢弃/单服稳定身份）通过后删除，最终只剩 SmokeTest（1 test / 0 failures）。
- 建议实机测试：① 故意让 DMCC 初始化失败一段时间后再修复连接，日志应出现 kept_before_identity 且随后 drained 补发；② single_server 模式下制造待发消息后 /dmcc reload，不应再出现身份变更警告且消息按序补发；③ 修改 multi_server 地址或 server_name 后重连，应看到带旧/新身份与类型统计的丢弃警告。

## 工作 26 · 2026/11/18 · 配置版本改为独立整数并解耦 DMCC 版本来源（#346）
- **用户可见（破坏性变更）**：`config.yml` / `mode.yml` / `custom_messages/<lang>.yml` 的 `version` 由 DMCC 版本字符串（如 `3.0.0-beta.3`）改为**独立整数**（当前均为 `1`），三个文件分别与各自模板比对；版本不匹配（包括仍为旧版字符串）时拒绝加载。
- **实现**：`YamlUtils.validate` 版本校验改为整数比较（非整数版本按通用版本不匹配处理）；DMCC 自身版本号不再从 mode.yml 模板读取，新增内部资源 `dmcc_version.txt`（构建期展开 `${mod_version}`），`EnvironmentUtils.getDmccVersion()` 改为读取该资源。
- **文档**：设计文档 §8.1 更新版本语义与 beta.1/2/3 手工迁移步骤；三个配置模板、mode.yml 与 custom_messages 的头部注释同步改为“configuration version”。
- **验证**：`./gradlew clean` + `./gradlew build`（守护进程）全绿、零编译警告/零 Gradle 弃用警告；临时 JUnit（整数匹配/整数不匹配/旧字符串版本/缺版本/版本资源加载）通过后删除，最终只剩 SmokeTest（1 test / 0 failures）；jar 内 `dmcc_version.txt` 已展开为当前版本，banner 显示正常。
- **建议实机测试**：① 用 beta.3 配置启动，应报告配置版本不匹配并拒绝加载；② 把三个文件的 version 改为 1、按日志补齐缺失键后启动正常；③ 全新目录首次启动生成的三个文件 version 均为 1。

## 工作 27 · 2026/11/18 · 修复单服控制台消息误读多服配置告警（#347）
- **用户可见修复**：single_server 模式下在服务器控制台执行 `/say`、`/tell` 时，DMCC 不再输出 `Configuration path not found: multi_server.servers` 告警；控制台来源消息仍以服务器身份正常发送到 Discord（头像回退为 bot 头像）。
- **根因**：SOURCE_SAY 走 fake-user webhook 路径，玩家 UUID 为空时回退到 `getClientAvatarUrl()`，而该方法无条件读取仅在 standalone 模板中存在的 `multi_server.servers`，ConfigManager 对缺失路径打 WARN。
- **实现**：`DiscordManager.getClientAvatarUrl` 仅在 standalone 模式读取子服清单，其余模式直接返回 bot 头像。
- **验证**：`./gradlew clean` + `./gradlew build`（守护进程）全绿、零编译警告/零 Gradle 弃用警告；无新增临时 JUnit；1 test / 0 failures。
- **建议实机测试**：single_server 控制台分别执行 `say Hi` 与 `tell @a Hi`，确认 Discord 收到消息且日志中不再出现该配置路径告警；standalone 子服头像仍按 `multi_server.servers[].avatar_url` 生效。

## 工作 28 · 2026/11/18 · 修复空显示名玩家消息无法发送到 Discord（#304）
- **用户可见修复**：显示名为空（例如昵称/格式化模组把 nickname 设为纯样式组件）的玩家在游戏内发言时，消息不再因 webhook 用户名为空而发送失败；Discord 中 fake-user 用户名会显示为 `<>`（standalone 带 `[{server}]` 前缀），不再替换成玩家名；游戏内消息仍保持与未安装 DMCC 时一致的原始空显示名。
- **实现**：Minecraft 侧不再对 `display_name` 占位符做回退（保持原始值，可能为空）；核心侧仅在 fake-user 用户名模板渲染时把为空的 `display_name` 替换为 `<>`，并在最终用户名仍为空时兜底为同一值，避免 Discord 拒绝空用户名。
- **验证**：`./gradlew clean` + `./gradlew build`（守护进程）全绿、零编译警告/零 Gradle 弃用警告；临时 JUnit（空白回退 4 例）通过后删除，最终只剩 SmokeTest（1 test / 0 failures）。
- **建议实机测试**：使用会清空/隐藏 displayname 的模组（如 Styled Nicknames 等）设置空显示名后发言，确认消息出现在 Discord、用户名为 `<>`（standalone 带服务器前缀），游戏内显示与未安装 DMCC 时一致。

## 工作 29 · 2026/11/18 · 配置模板可读性与 /link 默认权限调整（#339）
- **用户可见（配置模板）**：`command_permission_levels.link` 的默认值由 `0` 改为 `-1`（single_server/standalone 的 Discord `/link`；multi_server_client 的 `link` 只作用于游戏内 `/dmcc link`）。原先的 `0` 假设用户已有 OP 0+——已绑定账户、`role_mappings` 示例中的 `Players` 身分组或管理员身分组；全新用户 OP 为 `-1`，会被 `0` 拒绝，无法完成首次绑定。改为 `-1` 后这类用户可直接使用 `/link <code>`；三个模板的注释同步说明 0 与 -1 的区别。示例值改为明确的占位符（所有 `"in-game-chat"` → `"channel-name-here"`，`"console"` → `"console-channel-name-here"`，user 映射示例改为 `"owner-username-here"`），频道类配置补充“可填名称或 ID”的注释，standalone 的子服控制台频道列表补充说明。
- **文档**：README_CN §7.1/§7.2 的 `link` 默认权限改为 `-1`，并补充默认值说明。
- **验证**：`./gradlew clean` + `./gradlew build`（守护进程）全绿、零编译警告/零 Gradle 弃用警告；无新增临时 JUnit；1 test / 0 failures。
- **建议实机测试**：① 全新生成配置后，未链接的 Discord 用户可直接执行 `/link <自己的验证码>` 完成绑定；② 未链接用户在游戏内执行 `/dmcc link` 不受影响；③ 把 link 手动改回 0 时，未链接用户会被拒绝（可用于确认行为符合预期）。

## 工作 30 · 2026/11/18 · 配置默认值与注释改进（#344 部分，/info 布局不在本轮）
- **用户可见（配置模板）**：`discord.allow_mentions` 默认由 everyone/users/roles 改为仅 `users`（注释列出可选值与风险，避免任何玩家在游戏内 @everyone 真的 ping 整个 Discord）；`command_permission_levels.console` 保持 0，但注释说明它只决定调用权限、实际执行的 MC 命令权限等于发送者 DMCC OP 等级；`whitelist` 保持 0 并注释说明白名单悖论设计；补充 `channel_updating.interval_minutes` 限流、`echo_*` 的 source client 语义、`execute_messages_from_channel` 按发送者 DMCC OP 执行的行为说明。
- **文档**：README_CN §8.1 增加安全相关默认值说明。
- **验证**：`./gradlew clean` + `./gradlew build`（守护进程）全绿、零编译警告/零 Gradle 弃用警告；无新增临时 JUnit；1 test / 0 failures。
- **建议实机测试**：① 新配置下玩家在游戏内发送 @everyone 不再 ping Discord；② `/console` 仍按发送者 DMCC OP 执行对应 MC 权限等级的指令；③ 控制台频道 `execute_messages_from_channel` 注释与实际行为一致。
- **备注**：`/info` 布局可定制部分按要求留到后续，本 issue 保持 open（提交使用 Related to #344）。

## 工作 31 · 2026/11/18 · Standalone 模式新增 DMCC 控制台频道（#348）
- **用户可见（Standalone 专属）**：`console_forwarding.channel_for_standalone` 配置后，DMCC 会从进程启动开始 tail 自身 `logs/DMCC_*.log`（含启动历史、堆栈、JDA 等全部 slf4j 日志），以 `DMCC` webhook 身份、沿用控制台转发的脱敏与行内代码块+分批规则转发到该频道，并在转发器启动/停止时发送开始/停止提示；在频道内发送文本等同于在交互式终端输入 DMCC 命令（自动去前导 `/`，支持 `execute <at> <command>` 的整体参数规则），命令执行前会像 console 频道一样回显正在执行 “xxx”...，命令回复与文件结果也以同一 `DMCC` webhook 身份返回。频道设为 `""` 可关闭；频道不可用时会告警一次并禁用至 reload。单服模式不启用该功能（DMCC 日志已随 Minecraft 日志进入 `console_forwarding.channel`）。
- **实现**：从 `TerminalManager` 抽出共享 `CommandLineParser`（终端与频道复用同一套解析）；`LoggerImpl` 暴露当前日志文件路径；新增 `StandaloneConsoleForwarder`（tail `logs/DMCC_*.log`：1 秒轮询、从文件开头回放、有界缓存、轮转/截断处理、批量发送）；`DiscordManager` 新增 `sendStandaloneConsoleBatch`、`sendStandaloneConsoleStatusMessage`、`isStandaloneConsoleChannel`，日志与结果使用 `DMCC` webhook 身份；新增 `StandaloneConsoleCommandSender`（命令回复/文件也走同一 webhook 身份）；`DiscordEventHandler` 在普通聊天/客户端控制台频道处理之前消费该频道消息，并用 `OpLevelResolver` 顶层 OP 凭据执行；`ServerDMCC` 在 standalone 启动时启动、关服/reload 时停止转发器并发送停止提示。
- **配置迁移**：standalone 的 beta.1/2/3 配置需手工补 `console_forwarding.channel_for_standalone`（不需要时设为 `""`）。
- **验证**：`./gradlew clean` + `./gradlew build`（守护进程）全绿、零编译警告/零 Gradle 弃用警告；临时 JUnit（命令行解析 5 例）通过后删除，最终只剩 SmokeTest（1 test / 0 failures）。
- **建议实机测试**：① standalone 配置频道后，启动历史与实时日志出现在频道（webhook 显示为 `DMCC`），并收到 started 提示；② 在频道内发送 `info`、`reload`（需权限）、错误命令，回复以 `DMCC` webhook 返回；③ 关服/reload 时有 stopped/started 提示且不重复回放；④ 无日志转发循环；⑤ 频道设为 `""` 时关闭，single_server 不受影响。

## 工作 32 · 2026/11/18 · Discord 转发消息在 Minecraft 中显示来源与内容（#327）
- **用户可见修复**：Discord 的转发消息此前在 Minecraft 中显示为空白；现在先以引用形式显示被转发的原消息内容（复用 `discord_to_minecraft.response` 模板，附件/embed 等继续走普通消息解析；快照不含原作者，引用行署名取内部翻译，英文 `Forwarded`、中文 `已转发`），再显示一行 `[Discord] {转发者} 转发了一条消息！`（`discord_to_minecraft.forwarded` 模板）；快照缺失时只显示转发通知，不发送空消息。
- **实现**：`DiscordEventHandler` 识别 `MessageReference.MessageReferenceType.FORWARD`，通过 `Message#getMessageSnapshots()` 取转发快照（`MessageSnapshot`；Discord 的 message snapshot 是 minimal subset，明确不含 `author`；JDA 的 `getReferencedMessage()` 对 forward 为空），把快照交给新增的 `buildReplySegments(MessageSnapshot)` / `parseMessageContent(MessageSnapshot, ...)` 生成引用块（正文/附件/embed/组件复用普通消息的解析逻辑），引用行署名取 `discord.message_parser.forwarded_author`；转发者交给 `buildForwardedSegments` 生成通知行；en_us/zh_cn custom_messages 的 `forwarded` 模板改为通知行。
- **配置迁移**：beta.1/2/3 的 `custom_messages` 需手工补 `discord_to_minecraft.forwarded`。
- **验证**：`./gradlew clean` + `./gradlew build`（守护进程）全绿、零编译警告/零 Gradle 弃用警告；无新增临时 JUnit；1 test / 0 failures。
- **建议实机测试**：转发一条纯文本、一条带图片、一条带 embed 的 Discord 消息，确认 Minecraft 内先显示引用内容、再显示转发者通知；普通回复消息行为不变。

## 工作 33 · 2026/11/18 · 恢复 {player_textures} 头像占位符并支持服务端皮肤来源（#328）
- **用户可见**：`discord.webhook.avatar_url` 重新支持 `{player_textures}`（v2 语义）：从服务端 GameProfile 的 `textures` 属性解析皮肤纹理哈希，可直接交给 mc-heads 等支持 texture ID 的服务；服务端没有纹理数据时回退为玩家名。覆盖 Fabric Tailor、SkinsRestorer 等会更新服务端皮肤的插件/模组，以及 Floodgate 2.0 的 Bedrock 皮肤上传；纯原版离线玩家仍只能得到默认皮肤（服务端无皮肤来源，属于 Minecraft 机制限制）。
- **实现**：新增 `SkinUtils.extractTextureHash`（Base64 → JSON → SKIN url → 哈希，失败返回空）；Minecraft 侧对玩家来源的用户消息（聊天、指令、/say、/msg、/me）注入 `player_textures`，按 UUID+属性值做有界缓存；无纹理时回退玩家名。控制台来源不注入，继续走 bot 头像。
- **文档**：配置模板的 avatar_url 注释、README §2.1 与 §8.3 占位符表补充来源、回退与离线模式限制（并说明 Bedrock 建议用 `{player_textures}`，`{player_uuid}` 取决于头像服务是否认识该 UUID）。
- **验证**：`./gradlew clean` + `./gradlew build`（守护进程）全绿、零编译警告/零 Gradle 弃用警告；临时 JUnit（纹理哈希提取合法/非法/空/非纹理 URL）通过后删除，最终只剩 SmokeTest（1 test / 0 failures）。
- **建议实机测试**：① 配 `https://mc-heads.net/avatar/{player_textures}.png` 后，Fabric Tailor/SkinsRestorer 玩家、Geyser/Floodgate 玩家头像正确；② 纯离线无皮肤插件玩家回退玩家名（仍为默认皮肤，属预期）；③ 普通正版玩家头像与之前一致。

## 工作 34 · 2026/11/18 · 修复自定义翻译键缺失时占位符未替换（#331）
- **用户可见修复**：数据包/资源包以英文文本作为翻译键（如 Blaze and Caves 的进度播报）时，翻译表未命中不再直接返回原始键；DMCC 会按 Minecraft 的方式用参数替换键中的 `%1$s`/`%2$s` 等占位符，Discord 中显示 “xxx has completed the challenge [Name]” 而不是 `%1$s has completed the challenge %2$s%3$s%4$s`。
- **实现**：`TranslationManager.get(String key, Object... args)` 未命中时保留一次性告警，并在有参数时用 `StringUtils.format` 格式化原键（非法格式回退原键）。官方的 BM 键与已知翻译行为不变。
- **文档**：README_CN §2.1 翻译段落补充该兜底行为说明。
- **验证**：`./gradlew clean` + `./gradlew build`（守护进程）全绿、零编译警告/零 Gradle 弃用警告；临时 JUnit（`%1$s…%2$s`、`%s`、`%%`、非法格式）通过后删除，最终只剩 SmokeTest（1 test / 0 failures）。
- **建议实机测试**：安装 Blaze and Caves，完成一个进度，确认 Discord 中的进度播报为正常英文句子与方括号标题；对比官方原版进度播报无变化。

## 工作 35 · 2026/11/18 · 支持从服务端 resourcepacks 目录加载翻译（#338）
- **用户可见**：新增翻译来源 `./resourcepacks/`（zip/jar），读取其中 `assets/<namespace>/lang/<language>.json`（目标语言后再用 en_us 补缺）。当数据包/模组的翻译只存在于客户端资源包（Stellarity RP、BACAP 语言包等）时，把同一份资源包放到服务端 `resourcepacks/` 目录并重启 DMCC，即可在 Discord 中显示对应翻译；使用同时包含 datapack 与 assets/lang 的 mod 版 JAR 时无需此步。
- **实现**：新增 core 工具 `TranslationFileUtils`（zip/jar 语言文件读取、损坏文件由调用方容错）；`TranslationManager` 的 mods 扫描改用它，并新增第 4 步扫描 `resourcepacks` 目录；en_us 兜底仍由 `loadAll` 的第二遍扫描覆盖（非 en_us 配置下模组/资源包只带 en_us 时也能显示）。
- **文档**：README_CN §2.1 说明新增来源与放置方式。
- **验证**：`./gradlew clean` + `./gradlew build`（守护进程）全绿、零编译警告/零 Gradle 弃用警告；临时 JUnit（语言文件读取/注释容错/无 assets/损坏归档/归档识别）通过后删除，最终只剩 SmokeTest（1 test / 0 failures）。
- **建议实机测试**：① 把 Stellarity Resource Pack 放入服务端 `resourcepacks/` 并重启，进度标题/描述在 Discord 中正常翻译；② 删除该文件后恢复为原始 key（确认来源确实生效）；③ 普通 mod（自带 assets/lang）翻译行为不变。

## 发布待办（汇总）
- 待处理（按用户裁决都先不动）：`.github/ISSUE_TEMPLATE/bug.yml` 的 v2 残留文案；`docs/package.json` 缺 `license` 字段；`README.md`（英文翻译件）与 `README_CN.md` 的同步；配置迁移工具（发布前会提供在线版本，届时新键不再需要手工补）。
- 已核对无需改动：`update/versions.json` 的 `3.0.0-beta.3` 条目本就是 `"compatibility": ["26.3"]`；存量用户 `config.yml` 补 IPv6 规则按开发者裁决暂不处理。
- 后续可做（收益很小）：`countStatResultEntries` 在 `/dmcc info` 时于请求线程触发 `playerList.saveAll()` + 整目录 stats 扫描；`ServerHandler` 之外仍有 5 处按名线性扫描 `multi_server.servers`；`getPlayerName` 静默 null（消费方已用 UUID 兜底）；`CommandManager.resolveTarget(...)` 是否下沉到独立工具类；Standalone 初始化失败后自动重连（工作 13 二期）。
