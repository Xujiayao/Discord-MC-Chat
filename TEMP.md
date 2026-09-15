# TEMP.md — 交接说明：`f4f46a27..HEAD` 这 6 个 commit 到底做了什么

> **读者**：接手 Discord-MC-Chat v3 的下一个 Coding Agent（你完全没有参与这批改动）。
>
> **作者**：完成这批改动的上一个 Agent。**我不在场，无法回答你的提问**，所以本文件必须自包含；
> 凡是需要你自己复核的地方，我都写了具体命令。
>
> **立场**：写这份文件的目的**是说服你保留这批改动**——每一项都会说明"改之前是什么样、为什么那是问题、
> 证据在哪、如果你回退会失去什么"。但**第 4 节（我的不足与可继续优化空间）是诚实的自我批评**，
> 那一节请你独立判断，不要因为我写了理由就照单全收。
>
> **事实基准**：`git log f4f46a27..HEAD`（6 个 commit）、`git diff f4f46a27 HEAD`、以及仓库内的
> `CHANGELOG_TEMP.md`（`## 工作 05` ~ `## 工作 09`，是我自己写的逐轮记录）与 `TEMP_TODO.md`（第 4 轮计划）。
> 本文与它们**不冲突**：`CHANGELOG_TEMP.md` 是"做了什么"的流水账，本文是"为什么应该保留 + 哪里还不够好"。

---

## 1. 三分钟速览

### 1.1 这 6 个 commit

| # | commit | 内容（按 `git show --stat` 逐个核对，不是按印象） | 规模 |
|:--|:--|:--|:--|
| 1 | `0bd5d192` 第一轮第一次提交 | **第 1 轮主体**：模块重构（`:core` + `:minecraft` → `:core` + `:fabric` + `:neoforge` + 共享源码目录 `minecraft-common`）、新增 NeoForge 支持、建立平台抽象层（`core/platform/`：`PlatformHost`/`Platform`/`NoopPlatformHost`/`StatsProvider`）、删除事件总线三件套（`EventManager`/`CoreEvents`/`MinecraftEvents`）、删除 `mode.yml`+`ModeManager`、**Mixin 合成 lambda 修复**（删 `MixinReloadableServerResources`，功能并入 `MixinMinecraftServer.reloadResources`）、新增 JUnit 测试框架 + `SmokeTest`、新增 `foojay-resolver` 工具链解析、CI 与依赖维护 | +2974 / −2123 |
| 2 | `beed73aa` 第一轮第二次提交 | **第 1 轮补充（用户实测通过后）**：`DmccNeoForge` 改名 `NeoForgeDMCC`（与 `FabricDMCC` 对齐）；`SmokeTest` 精简为 1 项；**通用 JAR 成为唯一产物**（移除按加载器拆分写入根 `build/` 的 `loaderJar` 任务，`build.gradle` −71 行）；新建 `TEMP_TODO.md` | +408 / −106 |
| 3 | `4cd8c553` 第一轮第三次提交（IDEA 自动整理） | **纯格式**：导入排序、删除未使用导入、空行规范化。我用 `--ignore-all-space` 复核过，剩余差异全部是 import 行的位置移动，**无语义改动** | +149 / −153 |
| 4 | `49bf1082` 第一轮第四次提交 | **第 1.3 轮前半**：按用户要求**删除 runServer 相关设计**（`neoforge/build.gradle` 的 `runs { server { … } }`——注意这个块是 commit 1 加的；`.gitignore` 的 `run/` 条目）；**SLF4J 服务注册文件从 `core/src/main/shadow-resources/` 移回标准的 `core/src/main/resources/META-INF/services/`**（`git` 显示为一个纯路径 rename，0 行变化）；`DMCC platform: {}` 这条唯一的英文单语日志改走 i18n（新增 `main.init.platform` 键） | +122 / −39 |
| 5 | `33cf7b77` 第一轮第五次提交 | **第 1.3 轮后半（用户第二次反馈"不接受存在 logs 文件夹"）**：`core/build.gradle` 把**测试工作目录设为 `build/test-run`**（+ 开启 `showStandardStreams`）；`LoggerImpl` 日志文件改为**惰性创建** → 于是 `./gradlew build` 不再在源码树里留下 `logs/` | +99 / −18 |
| 6 | `3572427a` 第二三轮第一次提交 | **第 2 轮 + 第 3 轮**：解析层统一、报文层从 Java 原生序列化换成 JSON + 大文件逐帧分片、命令层收敛、server/discord 与 minecraft-common 两处去重、死代码清理；随后是内部瘦身 + 并发/性能/正确性修正 | +5806 / −4682 |

> 说明：`CHANGELOG_TEMP.md` 里的 `## 工作 05`~`## 工作 09` 是按**轮次**组织的，与 commit 不是一对一
> （例如 `工作 06` 与 `工作 07` 的内容分散在 commit 4、5 里）。上面这张表是按 commit 核对的版本。

### 1.2 总体规模（请先看这一行，避免误判）

```
126 files changed, 8822 insertions(+), 6385 deletions(-)   →  净 +2437 行
```

**这不是一次瘦身。** 它是"**加能力 + 换实现**"：

- 第 1 轮**新增了 NeoForge 支持**和一层平台抽象，这必然加行；
- 第 2 轮**净删 2110 行**（解析层与报文层的去重）；
- 第 3 轮**净加约 1257 行**（并发/性能机制，见 2.12）。

`*/src/main/**/*.java` 现在 **100 个文件 / 16101 非空行**；`f4f46a27` 时是 **91 个文件**。
Java 文件变多是因为拆出了 `minecraft-common/`（共享 Mixin 与事件桥）与 `core/platform/`、`core/network/protocol/`。

### 1.3 一句话结论

**这批改动把一个"只能跑 Fabric 的单体模组"变成了"Fabric + NeoForge + 独立运行三用的、平台无关的、协议安全的项目"，
同时把解析与报文两层重写成了可测试、去重后的实现。** 第 4 节列出的不足都是"还可以更好"，不是"方向错了"。

---

## 2. 逐项改动与采纳理由

每一项的格式：**改之前 → 为什么是问题 → 做法 → 证据 → 你若回退会失去什么**。

### 2.1 模块骨架与平台无关化（commit 1；见 `CHANGELOG_TEMP.md` 工作 05 与 `TEMP_TODO.md` 第 3 节）

- **改之前**：`settings.gradle` 只有 `:core` 与 `:minecraft` 两个模块，`:minecraft` 直接 `apply plugin: "net.fabricmc.fabric-loom"`，
  产物名 `Discord-MC-Chat-minecraft`，`fabric.mod.json` 放在 `minecraft/src/main/resources/`。**完全没有 NeoForge 支持。**
- **为什么是问题**：用户明确说"DMCC 未来可能不只是一个模组，也可以是一个插件""起码会兼容 NeoForge，并向服务端插件看齐"。
  一个把加载器 API 混在业务代码里的模块结构无法支撑这件事。
- **做法**：拆成
  - `core/` —— **平台无关**，**禁止出现 `net.minecraft` 导入**（唯一例外：`utils/EnvironmentUtils` 用反射探测环境）；
  - `minecraft-common/` —— **共享源码目录，不是 Gradle 项目**，两个加载器用
    `sourceSets.main.java.srcDir("../minecraft-common/src/main/java")` 各自编译一份；
  - `fabric/`、`neoforge/` —— 只放入口点与模组元数据（各不到 100 行）；
  - `core/platform/` —— `PlatformHost`（13 个方法，core → 平台的动作）、`Platform`（注册点）、
    `NoopPlatformHost`（独立模式空实现）、`StatsProvider`。调用方式 `Platform.host().xxx(...)`。
- **证据**：`git diff --name-status f4f46a27 0bd5d192` 可以看到 11 个 Mixin 与 `MinecraftEventHandler`/`MinecraftCommands`/
  `TranslationManager` 等以 `R08x`/`R1xx` 形式从 `minecraft/` 迁到 `minecraft-common/`；
  `core/` 里 `net.minecraft` 的出现次数为 0（除 `EnvironmentUtils` 的反射字符串）。
- **回退会失去**：NeoForge 支持本身；以及"core 可以在没有 Minecraft 的环境里编译"这一性质
  （它是第 2 轮能把解析器做成**纯字符串可测**的前提）。

> **采纳理由（重要）**：`core/platform/PlatformHost` 这 13 个方法是"core 需要平台做某件事"的**完整清单**。
> 如果你将来要加 Paper 插件支持，**只需要实现这个接口**，不用碰 core 的任何业务代码。
> 这是我建议你保留的最重要的一条结构性投资。

### 2.2 双加载器与"单一通用 JAR"（commit 4、5）

- **改之前**：只有 Fabric 产物。中途（commit 4）一度产出三个 JAR（通用 + `-fabric` + `-neoforge` 备用件）。
- **做法（最终态）**：根 `build/Discord-MC-Chat-<版本>.jar` 是**唯一产物**，同一个文件三种用法：
  Fabric 的 `mods/`、NeoForge 的 `mods/`、`java -jar` 独立运行。实现是根 `build.gradle` 的 `universalJar` 任务：
  以 `:core:shadowJar` 为基底 → 并入 `:fabric:jar`（`DuplicatesStrategy.INCLUDE`）→ 并入 `:neoforge:jar`
  （`DuplicatesStrategy.EXCLUDE`，去重）→ 保留 shadow 清单（`Main-Class` 因此保留）→ 删除中间临时目录。
- **证据**：`docs`/`CHANGELOG_TEMP.md` 工作 05 记录了 6880 条目、**0 重复条目**；工作 06 的补充记录确认
  已无 `DmccNeoForge.class` 残留；我刚复核过当前 JAR：**6875 条目、0 重复**，
  含 `fabric.mod.json` + `META-INF/neoforge.mods.toml` + `dmcc.mixins.json` + 两个入口点 +
  三份配置模板 + `custom_messages/{en_us,zh_cn}.yml` + `Main-Class: ...StandaloneDMCC`。
- **为什么两个加载器能共用一份**：每个加载器只读自己的元数据、只加载自己的入口点；共享的
  `minecraft-common` 类在包内只有一份（靠上面的 EXCLUDE 去重保证）。
- **回退会失去**：用户只需要管理一个文件；以及"同一份代码在三处行为一致"这一保证。

> 用户在第 1 轮实测过：**Fabric / NeoForge / Standalone 三种用法均正常**（`CHANGELOG_TEMP.md` 工作 07 开头）。
> 这是这批改动里唯一经过真机验证的部分，其余只有构建与测试证据。

### 2.3 首启流程与配置（commit 1）

- **改之前**：`config/discord_mc_chat/mode.yml` 是独立文件，`ModeManager` 负责读它，
  然后据此生成 `config.yml`；两个文件先改哪个、怎么 reload 都要用户自己搞清。
- **做法**：**删除 `mode.yml` 与 `ModeManager`**，`mode` 键并入 `config.yml`；
  首启直接生成完整配置 + 在控制台打印**绝对路径与三步指引**；
  新增"环境与模式匹配校验"（`standalone` 只能在独立 JAR 里跑，`single_server`/`multi_server_client` 只能在 Minecraft 内跑）。
- **证据**：`core/src/main/resources/config/mode.yml` 被删除（34 行）；
  `lang/*.yml` 里 `mode.yml` 时代的提示被替换为 `config.yml` 时代的提示 + 首启三步指引
  （`first_run_guide` / `first_run_steps` / `first_run_apply`）。
- **我对配置键做过独立核对**：`config_single_server.yml` **113 个键、前后完全一致（增删均为 0）**；
  `config_multi_server_client.yml` 18 个、`config_standalone.yml` 153 个，同样零增删；
  `config/custom_messages/{en_us,zh_cn}.yml` **逐行相同**。
  **这意味着老用户的 `config.yml` 与自定义消息模板不需要任何改动。**
- **回退会失去**：首启体验（用户现在不需要猜"我该先改哪个文件"）。
- **代价（须知）**：老用户如果本来有 `mode.yml`，需要自己把 `mode` 值挪进 `config.yml`。
  这是本区间**用户可见**的变化之一（见第 3 节）。

### 2.4 日志系统（commit 5 + 第 3 轮）

- **改之前的三个问题**：
  1. 只有一条英文单语日志能看，其余日志文案…实际上全项目 197 处 logger 调用里混着硬编码英文；
  2. `LoggerImpl` 用 `Map<String, Method>` + 日志级别字符串查表做反射派发；每行日志 `new SimpleDateFormat("HH:mm:ss")`；
  3. `./gradlew build` 会连带跑 `:core:test`，而测试 JVM 里"仅仅取得一个 logger"就让 `LoggerImpl` 构造时
     `Files.createDirectories("logs")` → **源码树里凭空出现 `core/logs/`**。
- **做法**：
  - **所有 DMCC 自己的日志全部走 `I18nManager.getDmccTranslation(key, args)`**，`en_us` 与 `zh_cn` 同步补齐。
    唯二例外（用户明确批准）：**启动横幅**（ASCII 艺术字 + 品牌信息）、以及"内部语言文件自身损坏"时那两条兜底警告
    （那时翻译系统已经不可用）。转发 Discord / 控制台原文的日志（`[子服名] 内容`）不算 DMCC 文案。
  - `LoggerImpl` 改为 `enum Level`（每个常量缓存 ANSI 颜色与两个反射 `Method`），时间戳改 `DateTimeFormatter` 常量；
    `StringUtils.escape()` 无特殊字符时直接返回原串；`StringUtils.format()` 里每次调用都重编译的正则改为预编译。
  - 日志文件**惰性创建**（`fileWriter()`），并且**测试的工作目录被设为 `core/build/test-run`**
    ——于是测试期 DMCC 的日志器**真实生效**，日志落在 `core/build/test-run/logs/DMCC_<时间戳>.log`，
    随 `clean` 一起消失；`testLogging.showStandardStreams = true` 让日志直接出现在构建输出里。
- **证据**：`lang/en_us.yml` 与 `zh_cn.yml` 的**真实键集一致**（我写过提取脚本核对；脚本报出的 4 处
  "仅英文有"经复核是 `/dmcc info` 模板块标量里的 `Version:`/`Mode:`/`Uptime:` 文案，不是键，属脚本误报）；
  第 3 轮新增的 4 个键中英同步。第 3 轮我用临时探针实测生成了
  `core/build/test-run/logs/DMCC_20260915_205036.log`，内容 `[20:50:36] [Test worker/INFO]: …`。
- **回退会失去**：中文用户能看懂的日志；以及"构建不会污染源码树"这一用户明确提过两次的要求。

### 2.5 NeoForge 无法启动的两个根因修复（commit 5，见工作 06）

这两条**非常值得保留**，因为它们是"看起来能用、实际 FATAL"的那类问题，而且根因都不显眼：

1. **Mixin 打在合成 lambda 上**：`MixinReloadableServerResources` 注入的是
   `ReloadableServerResources.lambda$loadResources$3`，而 **NeoForge 会 patch 这个类**，
   合成 lambda 的形状与 Fabric 不同（Fabric `(ReloadableServerResources, Object, CallbackInfoReturnable)`；
   NeoForge `(ReloadableServerResources, List, CallbackInfo)`）→ `InvalidInjectionException` → **FATAL 中止启动**。
   **修法**：改成注入**真实方法** `MinecraftServer.reloadResources(Collection)`，并在其返回的
   `CompletableFuture` 完成后刷新翻译（时机比原来更准）；删除 `MixinReloadableServerResources`，Mixin 12 → 11。
2. **SLF4J provider 与加载器冲突**：DMCC 自带极简 SLF4J provider，其注册文件原在
   `src/main/resources/META-INF/services/`，于是开发运行时它与加载器自己的 provider 同处一个类路径并被选中
   → `Failed to initialize DMCC Logger` → `Recursive update` 崩溃。生产环境因加载器隔离而不受影响。
   **修法过程（请务必读完）**：工作 06 把注册文件挪到 `core/src/main/shadow-resources/`（只由 shadowJar 打进产物）；
   但**用户随后明确表示"以后不再执行 runServer"**，于是工作 07 又把它**移回标准的 `src/main/resources/META-INF/services/`**，
   并删掉 `shadow-resources/` 与 `shadowJar` 的 `from(...)`。
   **发布 JAR 的内容两种放法完全一致**（Shadow 会把它重定位成 `META-INF/services/dmcc_dep.org.slf4j.spi.SLF4JServiceProvider`）。
   **如果将来又在开发环境（IDE 同步 / `runServer`）看到 "Failed to initialize DMCC Logger" 或类初始化递归，
   就把这一处改回 `shadow-resources/` 方案**——这是用户自己记录在案的备用回滚点。

### 2.6 构建与工作区净化（commit 5，见工作 07）

用户对"工作区必须干净"的要求很硬（他会亲自看文件管理器，不只看 `git status`）：

- `universalJar` 收尾时除了删自己的临时目录，还会删除 `build/tmp`
  （Gradle 为每个 `zipTree()` 视图建立的 `build/tmp/.cache/expanded/zip_<hash>` 空目录就出在这里）
  → **根 `build/` 里只剩那一个 JAR**（我复核过：确实只有 JAR）。
- **删除 `runServer` 相关的一切**：`neoforge/build.gradle` 的 `runs { server { ... } }` 块、`.gitignore` 的 `run/`。
  用户明确说"你只需要验证 build 成功即可，而不用理会 runServer，以后不再执行 runServer"。
  （有意思的是：那个 `runs {}` 块本来就是 commit 1 加的，commit 4 又按用户要求删掉了——
  所以**不要在 commit 1 与 commit 4 之间做 cherry-pick**，它们是同一个决策的两半。）
- 交付前固定收尾：`./gradlew clean` → `./gradlew --stop`。
- **`./gradlew build` 绝不生成 `logs/` 目录**（做法见 2.4 的测试工作目录）。
- **`.github/ISSUE_TEMPLATE/bug.yml` 是用户亲自改的**（下拉恢复完整版本列表、说明文字改成
  "Only DMCC v2 and v3 versions are supported."）。**不要再去裁剪它。**

### 2.7 解析层统一（第 2 轮；`CHANGELOG_TEMP.md` 工作 08）

- **改之前**：`DiscordMessageParser`(1592) + `MinecraftMessageParser`(781) + `MessageParserCommon`(289)
  + `TextSegment`(120) + `TextSegmentUtils`(80) = 2862 行。其中约 **16 个 `splitSegmentsByXxx`**
  是同一套"找 token → 切段 → 加样式"逻辑按 token 类型各写一份；**11 个 `buildXxxSegments`** 结构同形；
  两套 markdown 状态机各写一份。
- **做法**：拆成 8 个职责单一的文件（`DiscordMessageParser` 745 / `MinecraftMessageParser` 452 /
  `MessageParserCommon` 427 / 新建 `MarkdownParser` 337 / `MessageTemplates` 142 /
  `MentionResolver` 45 / `MessageExtras` 42 / `TextSegment` 163，吸收 `TextSegmentUtils`）：

  1. **通用 token 表**：`MessageParserCommon.TokenRule(pattern, styler)` + `splitByPattern(...)`
     取代 12 个 `splitSegmentsByXxx`；另有一个**单次左到右扫描**的 `splitByRules(...)`
     用于"markdown 关闭"的路径，保证 `||<@1>||` 这类剧透提及整体优先于其中的普通提及，
     且**已经渲染出来的文本不会被下一条规则再次匹配**。
  2. **markdown 扫描器共享、方言显式**：`MarkdownParser.parseDiscordMarkup`（反斜杠转义**会被消费**、
     分隔符必须**成对闭合**）与 `parseMinecraftMarkup`（反斜杠**原样保留**、已激活分隔符**切换关闭**、
     样式可跨行）。两者共享分隔符匹配/闭合查找/样式记账/片段产出，但**扫描主体刻意不合并**。
  3. **模板渲染收敛**为 `MessageTemplates.of(node).with(k,v).content(...).render()`。
  4. **解析器彻底脱离 JDA**：`MentionResolver` + `MessageExtras` 把"取提及"和"取附件/嵌入/投票"抽象成纯数据，
     JDA 只出现在新的 `DiscordMessageAdapter`。
- **证据（这是本区间最强的证据）**：我写了**差分测试**——把改动前的解析实现**逐字复制**成测试夹具
  `LegacyDiscordParser`，与新区块逐片段比对（文本 + 5 个样式位 + 颜色 + clickUrl + hoverText）：
  Discord markdown 扫描器固定语料 50 例 + **随机 4000 例**全等；Minecraft 扫描器固定 41 例 + **随机 4000 例**全等；
  D→MC 整条内容管线（含 ANSI 代码块）固定语料 × markdown 开/关 + **随机 2000 例 × 2** 全等；
  截断（6 行 / 200 / 400 字符、CJK、回复 1 行 / 20~40）**随机 3000 例 + CJK 200 例**全等。
  **这套差分测试抓到 2 个我自己写的真实回归**（Minecraft 方言在 `__` 不可消费时没有回退到 `_`；
  markdown 开启时剧透提及被二次匹配），都已修复。测试本身按用户要求**交付前删除**了。
- **若要回退**：你会失去"解析器可用纯字符串测试"这一能力（差分测试就是靠它做的），
  并且会重新拥有 16 份重复的切分逻辑。

> **一条我强烈建议你保留、且不要"优化"的设计**：`MarkdownParser` 里两套扫描主体**不合并**不是偷懒。
> 我原本试图把它们统一成一个函数，差分测试证明会在两处产生不同输出（详见工作 08 第 1 节）。
> 同理，"转义不对称"**不是 bug**：Discord 的 markdown 规范本来就定义 `\` 为转义，而 MC 聊天文本没有转义语义，
> 若统一消费 `\`，玩家输入的 `C:\new` 会变成 `C:new`。

### 2.8 报文层换代（第 2 轮；工作 08 第二节）

- **改之前**：`network/packets/` 5 个类文件共 30 个嵌套数据包类 + `network/serialization/` 两个类，
  载荷是 **Java 原生序列化**（`ObjectInputStream`/`ObjectOutputStream`）。
- **为什么必须换（这是安全修复，不是风格问题）**：
  1. `JavaSerializerDecoder` 在**认证之前**就用 `ObjectInputStream` 反序列化对端字节，
     没有 `ObjectInputFilter`、没有类白名单 —— **经典 RCE 面**；
  2. 30 个类几乎全是"字段 + 构造器"样板，约 950 行零逻辑；
  3. 每包新建 `ObjectInputStream`/`ObjectOutputStream`，流头与类描述符重复上线缆、无跨包类缓存；
  4. 字段改名/重排会**静默不兼容**；
  5. `/log` 传文件走同一帧，超过 1 MiB 触发 `TooLongFrameException` → `exceptionCaught` 直接 `ctx.close()`
     —— **大日志会打断客户端连接**。
- **做法**：新建 `core/network/protocol/`：
  - `Packet`（接口，只有一个 `type()`）、`PacketType`（24 个取值）、`Packets`（全部报文以 **record** 定义）、
    `PacketCodec`、`JsonPacketEncoder`/`JsonPacketDecoder`、`CommandFileAssembler`、`ProtocolException`；
  - 线格式是两字段 JSON 信封 `{"type":"…","payload":{…}}`，解码走**显式的 `PacketType → record` 映射表**
    （不是类名反射，对端无法诱导接收方实例化任意类）；未知字段被忽略；畸形帧抛 `ProtocolException` 并干净断开；
  - **大文件改为真正的逐帧分片**：256 KiB 原始字节 → base64 → 每片一个 `CommandFileChunk` 帧，
    接收端**按片序号**重组（与到达顺序无关，>64 MiB 直接拒绝）；
  - **报文合并**：`Console`/`Execute` 的自动补全请求与响应（4 个类完全相同）→ `AutoCompleteRequest`/`AutoCompleteResult`
    （用 `RpcKind` 区分）；两个方向的命令请求 → `CommandRequest`；命令/更新结果 → `CommandResult`。
- **证据**：临时协议测试（交付前删除）验证了：23 种报文逐一 JSON 往返，
  用 `encode(decode(encode(p))) == encode(p)` 做**逐字节等价**比对（能发现任何字段丢失或静默默认值）；
  线格式确认为 JSON 且不含 Java 序列化魔数；未知字段容忍；畸形帧/未知类型/缺 payload 一律 `ProtocolException`；
  **3 MiB + 12345 字节随机文件切分后每一帧都 < 1 MiB**，打乱顺序喂给重组器后与原文件逐字节相等；
  缺片时返回 null 而不是半截数据。
- **回退会失去**：**去掉一个认证前的反序列化 RCE 面**；大日志不断连；以及"字段改名退化为默认值而不是整帧解析失败"这一容错。
- **须知**：协议换代后 standalone 与所有子服**必须同时升级**（用户已明确接受，因为 DMCC 强制两端同版本，协议不需要兼容层）。

> **一个反直觉的教训，值得你记住**：我最初把分片塞进**同一个 `CommandResult` 里**，以为这样就不超限了
> —— 一帧仍然是 4 MiB，等于没解决问题。是"每一帧都 < 1 MiB"这条断言把它抓出来的。
> **如果你要改协议，请保留这种"按帧断言尺寸"的测试思路。**

### 2.9 命令层收敛（第 2 轮；工作 08 第三节）

- **做法**：
  - `Command.CommandArgument` 由接口改为 **record**，删掉 9 处匿名内部类（每个 12 行）；
  - 新增 `Command.usage(args...)`，`CommandManager`（2 处）与 `CommandAutoCompleter`（1 处）
    重复的 usage 拼接收敛为一次调用；
  - 新增 `CommandTargets`：`/console` 与 `/execute` 的**目标校验与解析完全合并**
    （两处逐字重复的 `isValidTarget` + 两段相同的 `all_online_clients`/离线/非法目标分支）；
  - 删除 13 个只为"显式"而存在的空构造器。
- **证据**：`/dmcc` 的**命令名与参数没有变化**（`commands/impl` 无文件增删，配置键零增删）；
  `git diff` 显示 `ConsoleCommand` −64 行、`ExecuteCommand` −42 行。
- **回退会失去**：三处已被证明等价的去重。这一条回退风险很低（不涉及行为），但也没什么保留成本。

### 2.10 server / discord 去重（第 2 轮；工作 08 第四节）

- **做法**：
  - `ServerHandler.channelRead0`（原 170 行、两个 30 分支 switch + 内联握手）拆成
    `handleHandshake`/`handleAuthResponse`/`handleMinecraftEvent`/`handleCommandResult`/
    `handleCommandRequest`/`handleAutoCompleteResult`/`handleLinkRequest`/`handleUnlinkRequest`，
    并把重复 5 次的"翻译原因 → 记日志 → 发 `Disconnect` → `close()`"提取为 `reject(...)`；
  - `DiscordManager`：合并 `sendBotMessage*`；把在四个方法里各写一遍的
    "standalone→webhook / single_server→bot + 日志前缀"提取为 `postServerMessage(...)`；
  - **每消息重编译正则改为按配置变更缓存**：`ServerHandler.isExcludedMinecraftCommand` 原用
    `Pattern.matches(...)` **对每条玩家命令重新编译**每个排除规则；`DiscordManager.applySensitiveRedaction`
    原**对每一行控制台输出重新编译**每个脱敏规则。现在两者都按"规则列表指纹"缓存已编译的 `Pattern`，
    配置 reload 后自动失效重建（语法错误只在配置变化时告警一次，而不是每行一次）；
  - `broadcastMinecraftRelay` 与 `broadcastMinecraftTellRawRelay` 的两套"是否转发给其它子服 / 是否回显给源"
    判定收敛为 `relayTargets(...)`；`ChannelUpdateManager` 删掉 4 个一行包装与 `buildOfflineContext()`。
- **证据**：排除命令是**每条玩家聊天命令**都会走的路径，脱敏是**每行控制台输出**都会走的路径——
  这两处的正则重编译是真实的 CPU 浪费。构建与 `:core:test` 全绿。

### 2.11 minecraft-common 去重（第 2 轮；工作 08 第五节）

- **做法**：合并 `buildComponentFromSegments` 与 `buildComponentPart`（**同一套样式管线的两份实现**）；
  删 `copySegmentWithText`（与 `TextSegment.copyOf` 逐字相同）；3 处 `serverInstance.execute(() -> { try…catch })`
  外壳 → `onServerThread(...)`；6 个广播方法里共 **12 处逐玩家发送循环** → `broadcast(Component)`；
  提及通知块 → `notifyMentionedPlayers(...)`；两处逐字相同的 11 行 `new CommandSourceStack(...)` → `dmccSource(...)`
  + `DMCC_SOURCE_NAME`；`RegistryOps.create(...)` 由每消息重建改为 `onServerStarted` 时建一次；
  `MinecraftCommands` 两个嵌套 record 共享方法并**修掉 `getPlayerUuid()`/`getPlayerName()` 返回 null 的 bug**
  （改为构造时捕获玩家身份）；`MixinServerGamePacketListenerImpl` 提取 `postPlayerCommand(...)`。
- **一个刻意的取舍**：**没有**把 6 个广播 lambda 包进 `onServerThread` 的静默 catch——
  那会把目前会冒到服务端 tick 循环的异常吞掉（丢失错误日志/崩溃报告）。这是并行工作流主动拒绝做的改动，
  我认同这个判断。
- **另一个刻意的取舍**：`ChannelUpdateManager` 的 `queue(_ -> {}, _ -> {})` **保留双空回调**：
  单参 `queue()` 会让 JDA 把**每一次预期内的限流丢弃**都打成错误日志。**请不要"简化"它。**

### 2.12 第 3 轮：瘦身 + 并发 / 性能 / 正确性（commit 6；工作 09）

这一轮的定义是"**用户零感知**"，请在评估时按这个标准看它。

**瘦身（我做的）**：`LoggerImpl` 去反射查表 + `DateTimeFormatter`；`StringUtils` 两条热路径优化；
`ConfigManager` 修掉 `config`/`mode` **没有 volatile 发布**、每次读配置都 `split("\\.")`、
可选键缺失时**每次握手都刷一条 WARN**；`I18nManager` 翻译表改为**不可变快照发布**
（原来会在客户端登录的 Netty 线程上 `clear()` + 重填，而读取来自 MC/JDA/日志线程）；
`MojangUtils` 加 TTL 与**负缓存**（原来失败完全不缓存 → Mojang 故障期**每条聊天消息**都会为每个未解析 UUID
重发一次阻塞 HTTP）；`LogFileUtils` 堵住 `/log ../../server.properties` 的**路径穿越**；
`Constants.OK_HTTP_CLIENT` 显式设置超时（原来完全依赖库默认值）。

**并发 / 性能 / 正确性**：

1. **`NetworkManager.requestInfoSnapshot` 改为按请求关联**。原来的三个缺陷：
   `infoCache.clear()` 在加锁**之前**；`expectedResponses` 取快照导致中途断连**必然等满超时**；
   **响应无请求关联**，于是四个调用线程（MSPT 每 10s、Presence 每 30s、频道看板每 10min、`/info`）
   会**互相清缓存**，甚至把上一轮的响应当成本轮（陈旧数据）。现在每个调用方拥有自己的轮次状态、
   注册与广播都在锁内、等待集合每次唤醒重算、客户端断连会立即唤醒等待者。
2. **`CommandManager`**：单线程执行器 → **虚拟线程**；`COMMANDS` 改为 volatile + 构建新表后**一次性替换**
   （消除 `/dmcc reload` 期间的"未知命令"窗口）；新增**公平读写锁**
   （console/execute/info/help/log/stats/links/update 走读锁完全并发；
   reload/shutdown/link/unlink/whitelist 走写锁，保持原有的串行语义）。
3. **把阻塞工作移出 Netty IO 线程**：
   - 提及目录改 **60 秒 TTL 不可变缓存 + 单飞重建**——原来**每条聊天消息**都重建全量别名表，
     并对**每个已绑定账户**做阻塞 JDA 调用（`retrieveUser`/`retrieveMember` 内部 `.complete()`）；
   - `DiscordManager.getOrCreateWebhook` 原**每条 webhook 消息**都做阻塞 `retrieveWebhooks().complete()`
     → 按频道缓存句柄，`UNKNOWN_WEBHOOK` 时精确失效并**只重试一次**；
   - `DiscordEventHandler.onCommandAutoCompleteInteraction` 原阻塞单线程 JDA 事件池最长 5 秒
     （Discord 补全截止是 3 秒，期间**所有 Discord 事件停摆**）→ 移到专用执行器。
4. **`BotPresenceManager` 真防抖**（500ms 尾随）并修掉"两个开关都关时提前 return、
   旧任务不会被取消、会按旧配置永久运行"的 bug。
5. **`ConsoleLogTailer.pendingLines` 加上限**（10000 行，丢最旧，每次溢出只告警一次），
   并修掉"flush 在 `isConnected()` 检查**之后**移除行、期间断连会静默丢整批"。
6. **`LinkedAccountManager.save()` 异步 + 原子**：内存 map 仍**同步**更新（读语义不变），
   磁盘写入改为专用线程 + 同目录临时文件后替换（`ATOMIC_MOVE` → 普通 move → 原地复制兜底）。
7. **`MinecraftEventHandler` 两处**：命令执行桥的 `CompletableFuture.runAsync` + 50×100ms `Thread.sleep`
   轮询（占用 ForkJoinPool 线程最多 5 秒）→ **虚拟线程** + 抽出常量；
   `buildInfoResponse` 原**每次 info 请求**都跑 `StatsCommand.countStatResultEntries`，
   而它内部调用 vanilla `PlayerList.saveAll()`（**必须在主线程**）并解析每个玩家的 stats 文件，
   而 info 请求每 10 秒就来一次且跑在 Netty IO 线程上 → 改为"启动时在主线程算一次 + 玩家加入/退出时标记失效
   + 请求侧在服务端线程惰性重算 + 5 分钟兜底 TTL"。

> **这一轮我建议你保留的核心理由**：上面每一条都对应一个**具体的、可复现的**故障模式
> （互相清缓存导致看板数字错、单线程执行器导致一个用户卡住所有人、每条消息几十次阻塞 JDA 调用、
> 断连丢整批控制台输出、跨线程 `PlayerList.saveAll()`）。它们不是"代码风格"层面的改动。
>
> **但也要知道它的代价**：这一轮**净加约 1257 行**（见第 4 节第 1 条）。

### 2.13 首启指引的换行修复（commit 1 之后的小修，见工作 06）

- **现象**：日志器为了防日志注入会**刻意把换行转义**（`StringUtils.escape`），
  于是多行的 lang 值在控制台会挤成一行字面量 `\n` —— 首启指引就是这样显示出来的。
- **做法**：把 `first_run_guide` 拆成三条**单行**文案（生成路径 / 需要改什么 / 如何生效），逐行输出。
- **采纳理由**：这是"日志器设计（换行必须转义）"与"文案作者想写多行"之间的冲突，
  正确的解法是**改文案而不是改日志器**——如果你未来还想写多行 lang 值，请照这个办法拆成多条键，
  **不要**为了让多行显示正常而放宽 `escape()`（那会打开日志注入面）。

### 2.14 测试框架与版本声明（commit 1）

- **做法**：`gradle.properties` 新增 `junit_version=6.1.3`、`neo_version=26.2.0.87`、
  `moddev_version=2.0.147`、`minecraft_version_range=[26.2]`、`foojay_version=1.0.0`；
  `core/build.gradle` 引入 JUnit BOM + `junit-platform-launcher`；新增
  `core/src/test/java/com/xujiayao/discord_mc_chat/SmokeTest.java`。
- **为什么必须有测试来源集**：用户的交付协议是"每轮写临时测试固化旧行为 → 交付前删除 → 只留 `SmokeTest`"。
  没有可运行的测试来源集，这套协议就无法执行——第 2 轮那份**抓到两个真实回归**的差分测试，
  正是靠它才跑得起来。
- **须知**：`./gradlew build`（不带任务名）会在**所有项目**上执行 `build`，因此**会连带跑 `:core:test`**。
  这是"构建时不得留下 `logs/`"那个要求的起因（见 2.4）。
- **须知**：`minecraft_version_range=[26.2]` 与 `gradle.properties` 的注释一起，把"仅支持 26.2、
  且 DMCC 两端必须同版本"这件事写成了**声明式约束**；NeoForge 的 `mods.toml` 也同步了这个范围。
  请不要放宽它——协议不设兼容层正是建立在这个前提上。

---

## 3. 你必须知道的用户可见变化（不要把整批说成"零感知"）

用户在提交前专门问过我这个问题，我核实后的答案是**"不是零感知"**。请你在后续沟通中同样如实说明。

### 3.1 第 1 轮带来的可见变化（大）

| 面 | 变化 |
|:--|:--|
| 加载器支持 | 从"仅 Fabric"变成 **Fabric + NeoForge**（**新增能力**，不是重构） |
| 安装产物 | 从 `Discord-MC-Chat-minecraft` 变成根 `build/Discord-MC-Chat-<版本>.jar`，一个文件三用 |
| MC 版本要求 | `minecraft_version_range=[26.2]`；**仅支持 26.2** |
| 配置文件形态 | **`config/mode.yml` 这个文件被删除**，老用户需把 `mode` 挪进 `config.yml` |
| 首启流程 | 首次运行直接生成完整配置 + 控制台打印绝对路径与三步指引；新增环境/模式匹配校验 |
| 日志文案 | 全部改为可翻译，中文环境看到中文日志 |
| 开发环境 | `runServer`/`runClient` 相关设计全部删除（用户不再使用） |

同时**没变**（这点对老用户很重要，我独立核对过）：
三份配置模板的**配置键零增删**（113 / 18 / 153）、`custom_messages/*.yml` **逐行相同**、
`/dmcc` 的**命令名与参数零变化**。

### 3.2 第 2 轮带来的 4 处刻意行为修正

前 3 条只在用户**自己写了非默认模板**时才可能被看到；第 1 条**一定会被看到**：

1. **`{server_color}` 占位符以前是失效的**（**会看出来**）。旧代码是
   `.replace("{server}", …).replace("{server_color}", …)`，而 `{server}` 是 `{server_color}` 的前缀，
   于是 `color: "{server_color}"` 被替换成 `SMP_color`（非法颜色 → 回退默认）。
   **所以改前 `[子服名]` 前缀根本没有上色，改后才会按子服颜色显示。** 我改成单次扫描替换。
2. Discord → MC 方向现在也支持 `{display_name}`（旧实现只在 MC → MC 方向替换）。
3. 同一片段里多个 `{message}` 现在会**依次插入**（旧实现会丢掉第二处之后的文本）。
4. 无法解析的 `<t:…>` 时间戳在 markdown 开/关两条路径下表现一致（旧的前者标黄、后者当普通文本）。

> 我的判断：这 4 处都是"旧实现自相矛盾/明显失效"的地方，**修好而不是弄坏**。
> 但如果你认为"严格零行为变化"比"修掉明显 bug"更重要，第 2–4 条可以回退，第 1 条回退等于永久放弃
> `[子服名]` 的上色（而默认模板里就写着 `{server_color}`，作者原意显然是要上色的）。

### 3.3 第 3 轮的时序差异（理论不可见，但有微小可观测点）

1. `players_ever_joined` 在新玩家**首次**加入后最多滞后约 10 秒（原来每 10 秒同步重算，代价是跨线程 `saveAll()`）。
2. Bot 状态首次刷新最多晚 **0.5 秒**（新防抖）。
3. 提及别名表（玩家/Discord 昵称/角色）最多 **60 秒**才跟上 Discord 侧的改动
   （绑定/解绑是**立即**失效的；Discord 改昵称/角色靠 TTL）。
4. 两个**并发**的 `/dmcc console` / `/execute` 的输出行可能交错（各自 requestId 独立，单个命令输出不变）——
   这是"命令执行器改虚拟线程"的**预期效果**。
5. webhook 发送失败时，日志由 DMCC 打（`discord.manager.broadcast_failed`）而不是由 JDA 打。


---

## 4. 我的不足与可继续优化空间（**请独立判断，不要因为我写了就采纳**）

按"我认为的问题严重程度"排序。

### 4.1 我引入过一个真实 bug，且它现在**没有任何永久测试**保护

第 3 轮我把 `SimpleDateFormat` 换成 `DateTimeFormatter` 时，日志**文件名**用了 `yyyyMMdd_HHmmss`
却喂了 `LocalTime.now()` —— 纯时间没有"年"字段，第一次写日志抛 `UnsupportedTemporalTypeException`，
异常被吞、`fileWriterInitialized` 却已置 true → **整轮运行再也不生成日志文件**（控制台还有输出，很难发现）。
是被并行工作流发现的。我用临时探针修好并验证（`DMCC_20260915_205036.log`），但按"临时测试交付前删除"的约定
**探针也删掉了**。

**建议你采纳的改进**：给"第一条日志会创建 `logs/DMCC_<yyyyMMdd_HHmmss>.log` 且行格式不变"加一个**永久测试**
（放在 `SmokeTest` 或新建 `LoggingTest`）。这是唯一一条我建议**长期保留**的测试——
它保护的是一类"静默失效"的回归。用户的删除约定针对的是"每轮固化为断言再删"的临时测试，
一条 20 行的永久日志契约测试应该不违背其意图，但**请你自己跟用户确认**。

### 4.2 第 3 轮是**增行**的，与"瘦身"叙事冲突

- 第 2 轮净 −2110 行；第 3 轮净 **+1257 行**；整个区间净 **+2437 行**。
- 增行集中在：`DiscordManager` +143、`MinecraftMessageParser` +138、`NetworkManager` +114、
  `LinkedAccountManager` +113、`CommandManager` +91、`ConsoleLogTailer` +57、`DiscordEventHandler` +55、
  `BotPresenceManager` +48。**主因是每个新方法都带完整 javadoc**，其次是安全兜底（三级写入回退、
  重试一次、失效率缓存）。
- 原计划给第 3 轮的目标是"−600 行"。**没达成。**
- **可选的下一步**：如果你判断"可读性 > 行数"，什么都不用做；如果用户更在意行数，
  可压缩 javadoc、或把 `LinkedAccountManager` 的三级写入回退简化为两级。

### 4.3 仍然过大的文件（我拆了一半就停了）

当前最大的几个文件（非空行）：

| 行数 | 文件 | 我的评估 |
|--:|:--|:--|
| 1180 | `minecraft-common/.../MinecraftEventHandler.java` | **最大问题**。28 个 static 钩子 + 一个 `serverInstance` static 字段 + 大量 `if (serverInstance == null) return;`。真正的修法是**把状态实例化**（用 `PlatformHost` 注入一个 handler 实例），而不是继续抽静态辅助方法。我这一轮只做了抽方法，**没有动它的全局状态模型**。 |
| 1131 | `core/.../server/discord/DiscordManager.java` | 原计划要拆成"JDA 生命周期 / 消息派发 / 控制台转发 / 频道解析 / 限速"5 个类。**我没做**，因为控制台转发与类内私有状态（`jda`、webhook、头像解析、脱敏）耦合较深，而 `/log` 与控制台转发是用户天天用的路径。我只做了类内消重。**这是我认为最值得你接手的一件事。** |
| 730 | `core/.../server/message/DiscordMessageParser.java` | 已从 1592 降到 730，可接受 |
| 586 | `MinecraftMessageParser.java` | 第 3 轮加了 TTL 缓存后从 452 涨到 586；可再拆出 `MentionDirectory` 相关代码 |
| 554 / 548 / 506 | `DiscordEventHandler` / `ServerHandler` / `NetworkManager` | 都还可以再分一层 |

### 4.4 我**刻意没做**的几件事（理由已写，但你可以不同意）

1. **`ConfigManager` 没有改成"加载时解析成类型化快照"**（原计划要做）。理由：需要重写三个模块里所有配置读取点，
   收益是省一次 `JsonNode.path()` 遍历（亚微秒级），风险是动到用户明确要求保留的校验行为。
   我只修掉了它真正的三个缺陷。
   **连带问题**：`ConfigManager.getBoolean(path)` 返回 `Boolean`，**在配置尚未加载时是 `null`，
   在调用点拆箱会 NPE**。我第 3 轮只修了 `ExecutorServiceUtils` 一处并加了 `getBoolean(path, default)` 重载，
   **其余调用点仍然有这个隐患**。彻底修法是把它改成返回 `boolean`（默认值显式传入）。
2. **`MemberCachePolicy.ALL` 没改**（原计划要做）。理由：JDA 的成员缓存策略直接影响 `getAllMembers()` 的返回值，
   改成惰性策略会让"按名字提及某个未缓存成员"失效 —— **那是用户可见的行为变化**，不属于"零感知"轮次。
3. **"解析放专用执行器"没做**。理由：把消息解析整体丢给执行器会**破坏消息顺序**（乱序聊天是用户可见的），
   因此我只把其中真正阻塞的部分（提及目录的 JDA 调用）改为缓存。
4. **13 个命令类没有按域合并成 4 个**（原计划要做）。理由：逐条核对后各命令业务逻辑差异较大，
   合并主要是**搬家**而不是**删代码**，还会让 `CommandManager` 的注册表变得间接。
5. **`TextSegment` 仍然是可变 POJO（9 个 public 字段 + 无参构造器）**。原计划要改成不可变模型
   （`Message(List<Span>)` + `Style`）。理由：第 2 轮同时在换协议，两个大改动叠在一起会把爆炸半径翻倍。
   现在它只作为"解析期可变容器 + JSON 载荷"。**如果你做不可变化，请顺带处理 JSON 反序列化路径**
   （现在依赖无参构造器 + 公共字段，Jackson 直接填字段）。
6. **`Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES` 仍是全局 `AtomicBoolean`**。理由：并入平台接口会扩大
   `PlatformHost` 的接口面，而它只影响 DMCC Client 自己。
7. **第 1 轮遗留的 4 个 Mixin 仍注入在"未被 NeoForge patch 的类的合成 lambda"上**
   （`SayCommand`/`TellRawCommand`/`MsgCommand`/`EmoteCommands` 的 `lambda$register$*`）。
   今天安全（这些类不在 NeoForge 的 patch 面内），但**只要 NeoForge 未来 patch 这些类，
   就会出现与 `MixinReloadableServerResources` 完全相同的 `InvalidInjectionException` 并 FATAL 中止启动**。
   修法与第 1 轮相同：改为注入真实方法（如 `CommandSourceStack.sendSuccess` 或 `PlayerList.broadcastSystemMessage`）。
   **我两轮都没动它（收益不确定、风险明确），这是一颗定时炸弹。**

### 4.5 第 3 轮引入的残留限制（都已写进 javadoc，但你应该知道）

1. **info 轮次关联不完美**：`Packets.InfoSnapshot` 没有可回显的时间戳字段，我也不能改报文形状
   （当时协议刚换完），所以用**内部请求 id** 关联。残留：一个很晚才到的旧轮次响应**也会被新轮次记录**，
   直到新轮次的真实应答到达后被覆盖（latest-wins）。彻底修法是给 `InfoRequest`/`InfoSnapshot` 加 `requestId`
   ——**因为 DMCC 强制两端同版本，改协议完全允许**，我建议你加。
2. **命令串行化粒度偏粗**：link/unlink/whitelist 仍会排在**整个 console/execute 扇出**之后
   （与旧单线程行为一致）。更细的做法是"按命令名或按目标资源的锁映射"。
3. **`ConsoleLogTailer` 的 10000 行上限是"丢最旧"**：断连过久时会丢掉突发段的**开头**。
   如果你更在意完整性，可以改成阻塞/背压（但要小心阻塞 Netty 线程）。
4. **`MojangUtils.NAME_CACHE` 无上限**：见过的每个 UUID 都会留一条（含失败项）。
   对正常服务器无所谓，万级 UUID 时有微小内存占用。
5. **`I18nManager.loadDmccTranslations` 在加载失败时保留上一份快照**（而不是像原来那样清空成"到处显示键名"）。
   我认为这是改进，但它确实是一处行为变化。
6. **`LinkedAccountManager.save()` 在 Windows 上可能退化为"原地复制"**（当 `ATOMIC_MOVE` 因文件被占用而失败时），
   那条路径**不是崩溃原子**的。我保留了两级兜底以免回到"保存失败"，但严格做法是重试 `ATOMIC_MOVE`。

### 4.6 验证层面的不足（最重要的一条）

**第 2、3 轮我从未启动过真实服务端**——用户明确禁止 `runServer`，只允许验证 `./gradlew build` 成功。
所以：

- 第 2 轮的解析层：有**差分测试**证据（很强）；
- 第 2 轮的协议层：有**往返/分片测试**证据（很强）；
- **第 3 轮的并发改动：只有编译 + 单元测试 + 并行工作流自己写的临时测试证据，
  没有任何真机验证**。频道看板数字、控制台转发连续性、命令并发行为都需要**用户在真实多子服环境里实测**。
- 第 1 轮的 Fabric/NeoForge/Standalone **是用户实测过的**（`CHANGELOG_TEMP.md` 工作 07）。

**建议**：把工作 09 里那份"10 项人工回归清单"当成未完成的验收项，而不是已完成项。
其中优先级最高的三条：多子服并发刷消息确认**不串消息**；两个用户同时
`/dmcc console at:all_online_clients` 确认输出不互相混入；反复 `/dmcc reload` 确认没有"未知命令"窗口。

### 4.7 其它零散观察

- `fabric.mod.json` 仍引用**不存在的** `icon/icon.png`（沿用旧状，两轮都没新增 PNG）。
- `README.md`（英文翻译件）在本区间**完全没动**；只有 `README_CN.md` 是设计与维护文档。
  按用户策略，`README.md` 只在发布新版本时同步。
- 备份用的 `-fabric`/`-neoforge` 产物在第 1 轮后期被移除（现在只作为 `fabric/build/libs/`、
  `neoforge/build/libs/` 下的中间文件存在）。如果你需要排查"某加载器是否加载了正确入口"，
  得自己从这两个中间产物取。
- Windows 控制台直接 `java -jar` 时中文日志显示为乱码（Java 18+ 默认 UTF-8 输出到 GBK 代码页）；
  **日志文件本身是 UTF-8 正常的**。若要控制台也可读，需要处理控制台编码或提示 `chcp 65001`。
- 三个产物（现在是通用 JAR）的 `MANIFEST.MF` 里带有 shadow 生成的 `Class-Path` 长列表（列出未打包的依赖名）。
  对加载器与 `java -jar` 均无害（缺失条目会被忽略），属噪音，可清除。

---

## 5. 红线（用户明确要求，违反会被打回）

这些是用户在对话里逐条提出的，**请当成硬约束**：

1. **只能用 Jackson（`tools.jackson` 3.2.2），禁止引入 GSON。** v2 用的是 GSON，只可作为"设计证据"引用。
   > 唯一例外说明：`MinecraftEventHandler` 里的 `com.mojang.serialization.JsonOps` +
   > `com.google.gson.JsonElement` 是**原版 `ComponentSerialization.CODEC` 的固有接口**（改动前就在用），
   > 不是 DMCC 新引入的 Gson 依赖。DMCC 自己的 JSON（配置、语言、自定义消息、网络协议、`links.json`）全是 Jackson。
2. **仅支持 Minecraft 26.2**，不做多 MC 版本；**DMCC 服务端与客户端必须完全同版本**，
   因此协议**不需要任何兼容层**。
3. **两套消息系统保持现状**：`lang/*.yml` 是内部翻译（用户不应改），`custom_messages/*.yml` 是用户可改的模板。
   **不要把两者合并。**
4. **配置模板里预填的是用户自用的测试参数**（`xujiayao`、`SMP`/`CMP`、`in-game-chat`、`111111`/`222222`、
   `your_token_here` 等）。**必须原样保留**，不要"清理成示例值"，否则用户每次测试都要重填。
5. **`.github/ISSUE_TEMPLATE/bug.yml` 已被用户亲自改过。不要再裁剪它。**
6. **功能零删减**：ANSI、投票、贴纸、嵌入、`/log` 文件传输、精确截断规则、配置校验提醒、
   交互组件占位符全部保留。精简只能来自去重 / 删死代码 / 换更简洁的实现。
7. **`Capability` 能力枚举暂时不要加**：当前没有第二个平台会读它，加了就是新的死抽象。
   平台扩展点用现有的 `PlatformHost` + `ModIntegration` 两个接口即可。等真正做 Paper 时再加。
8. **测试策略**：每轮可写临时测试固化行为，但**交付前删除**，只保留 `SmokeTest`。
   （`SmokeTest` 当前只有 1 项测试：版本资源可解析。）
9. **每轮必须**更新 `CHANGELOG_TEMP.md`（追加 `## 工作 NN`）+ `README_CN.md`（`README.md` 平时不动）
   + 给用户一份**人工测试清单**。
10. **不要自行 commit**：用户会亲自审阅并 commit。有疑问用问卷问，不要猜。
11. **DMCC 自己的每一条日志都必须多语言**（`lang/en_us.yml` + `lang/zh_cn.yml` 同时补键）。
    仅两个例外：启动横幅、"内部语言文件自身损坏"时的两条兜底警告。
    转发 Discord/控制台原文的日志不算 DMCC 文案。
12. **不再为 `runServer` 做任何设计**：只验证 `./gradlew build` 成功；项目里没有 `runs {}`、没有 `run/` 目录。
13. **交付前工作区必须干净**（用户会亲自看文件管理器）：`./gradlew clean` 删掉所有 `build/`；
    临时文件、临时脚本、临时测试一律不留；**`./gradlew build` 本身也不得留下 `logs/` 目录**。
14. **不要为了"减少重复"而合并语义不同的实现**——两处教训：两套 markdown 扫描器（D 消费 `\` 且要求成对闭合；
    MC 保留 `\` 且切换关闭、跨行延续），以及 `queue(_ -> {}, _ -> {})`（单参 `queue()` 会让 JDA
    把预期内的限流丢弃打成错误日志）。**合并前先写差分测试。**
15. **改协议 = 同时改 `PacketType` 与 `PacketCodec.CLASSES`**（外加 `Packets` 里的 record）。
    漏改一处只会在运行期暴露。
16. **测试的工作目录是 `core/build/test-run`**，不是项目目录；读工程文件要用 classpath 资源或绝对路径，
    临时文件放 `build/` 下。

**其它已记录的易踩点**：
- `Packets.Disconnect.args` 是 `String[]`（不是 `Object[]`）。
- `Packets.DiscordRelay` / `Packets.MinecraftEvent` 的事件类型组件叫 `eventType`
  （不能叫 `type`，否则与 `Packet.type()` 冲突）。
- `MessageParserCommon` 是 **public** 的（`mentionNotification(...)` 被 `ServerHandler` 用）；
  `DiscordMessageParser` 的 `truncateMainRaw`/`truncateReplyRaw`/`enforceSingleLine` 是**包级可见**的（便于同包测试）。
- **`LoggerImpl` 里这两行不要"清理"**：
  `String loggerClassName = "dmcc_dep.org.slf4j.Logger".replace("dmcc_dep.", "");`
  它是**故意写成已被 Shadow 重定位的样子**。`core/build.gradle` 有 `relocate "org.slf4j", "dmcc_dep.org.slf4j"`，
  写成裸的 `"org.slf4j.Logger"` 会被 Shadow 改写成 `dmcc_dep.org.slf4j.Logger`，
  于是 `Class.forName` 加载到 **DMCC 自带的、被重定位的 SLF4J**，日志会绕回这个类自己
  （正是"类初始化递归"的成因）。我实测过发布 JAR 里 `LoggerImpl.class` 的常量池只有
  `dmcc_dep.org.slf4j.Logger{,Factory}` 与 `dmcc_dep.`（无双重前缀），因此运行期 `replace` 后拿到的
  正是 Minecraft 自己的 `org.slf4j.*`。**我差点把它当死代码删掉。**
- **做"死代码扫描"时要小心**：第 2 轮新建的这些文件**全部是活的**，只是名字看起来像可选抽象——
  `server/message/{MarkdownParser,MessageTemplates,MentionResolver,MessageExtras}.java`、
  `server/discord/DiscordMessageAdapter.java`、`commands/CommandTargets.java`、
  `network/protocol/*`。删除前请先 `grep` 全仓（我见过一次把 `MentionResolver` 报成"无人引用"的误判，
  实际它被 `DiscordMessageParser` 与 `DiscordMessageAdapter` 共同使用）。
- **`DiscordMessageAdapter` 是唯一允许碰 JDA 的解析相关类**。如果你让别的解析类重新 `import net.dv8tion`，
  第 2 轮"解析器可用纯字符串测试"的收益就没了。

---

## 6. 如何自行验证（命令 + 预期结果）

```powershell
# 1) 唯一被认可的验证：构建成功 + 测试
./gradlew clean build :core:test --warning-mode all
#    预期：BUILD SUCCESSFUL，无 warning/deprecat 命中，SmokeTest PASSED

# 2) 产物核对：根 build/ 只能有那一个 JAR
Get-ChildItem build -Recurse
#    预期：只有 Discord-MC-Chat-3.0.0-beta.2.jar（约 13.2 MB）
#    JAR 内应有：fabric.mod.json、META-INF/neoforge.mods.toml、dmcc.mixins.json、dmcc_version.txt、
#    FabricDMCC.class、NeoForgeDMCC.class、MinecraftPlatformHost.class、network/protocol/PacketCodec.class、
#    三份 config 模板 + custom_messages/{en_us,zh_cn}.yml；Main-Class: ...StandaloneDMCC；
#    0 个重复条目。（我复核过：6875 条目 / 0 重复）

# 3) 确认工作区没有残留
Test-Path build, logs, run, config, core/logs, core/config, build/tmp
#    预期：全部 False（注意：正常构建后 build/ 会存在并含 JAR，这是预期的；
#    交付前才需要 clean 掉）

# 4) 语言文件键集一致性（我用过的提取脚本思路）
#    en_us.yml / zh_cn.yml 的真实键集必须完全一致。
#    注意脚本误报：/dmcc info 模板块标量里的 Version:/Mode:/Uptime: 不是键。
#    第 3 轮新增的 4 个键必须在两个文件里都有：
#      discord.command.autocomplete_failed
#      client.console_log_tailer.pending_lines_dropped
#      client.console_log_tailer.flush_failed
#      server.network.invalid_excluded_command_regex

# 5) 交付前固定收尾
./gradlew clean
./gradlew --stop
```

**注意**：用户放宽了权限，`./gradlew` 直接用家目录的 `~/.gradle`，**不要再建临时 `GRADLE_USER_HOME`**。

---

## 7. 当前状态与下一步

- **工作区状态**：6 个 commit 已由用户提交（`3572427a` 是最后一批）；我写这份文件时 `git status` 干净，
  根目录有一个 `build/Discord-MC-Chat-3.0.0-beta.2.jar`（用户构建验证留下的，`.gitignore` 已忽略 `build/`）。
- **`TEMP_TODO.md` 仍然有效**：它记录了项目速览、红线、**第 3 轮已完成内容**与**第 4 轮计划**
  （`R4-1` 用户可见 YAML 可读性改造 + `R4-2` 文档收尾）。用户的意图是**第 3 轮（零感知内部改动）与
  第 4 轮（用户可见改动）分开提交**，便于逐轮核对。所以第 4 轮该做的是：
  1. 三份 `config*.yml` 模板与 `custom_messages/*.yml` 的可读性改造
     （分区大标题、每键上方一行说明 + 取值示例、"留空=禁用"等约定、逐键列出可用占位符）；
     **保留全部预填测试值**（红线 4）；**不要动键名**（我核对过键集零增删，这是可以放手美化注释的前提）；
  2. `README_CN.md` §8 配置参考重写 + §10 运维 + §1 记录最终行数；
  3. 把 `TEMP_TODO.md` 归纳进 `CHANGELOG_TEMP.md` 后删除它（以及本文件，如果你用完了）。
- **我建议的优先级**（如果你有自由度）：
  1. **真机验收第 3 轮的并发改动**（见 4.6）——这是唯一"我无法证明"的部分；
  2. 给日志文件创建加**永久测试**（见 4.1）；
  3. 拆 `DiscordManager`（见 4.3，1131 行，是最值得动的一个）；
  4. 修那 4 个 Mixin 合成 lambda 的定时炸弹（见 4.4 第 7 条）；
  5. 再考虑 `MinecraftEventHandler` 的全局状态实例化（5.3）与 `TextSegment` 不可变化（5.4 第 5 条）。
