# CHANGELOG_TEMP

> 本文件为临时记录，供后续发布新版本时由 AI session 据此生成正式的 CHANGELOG.md。

## 工作 01

记录日期：2026/9/8（项目重启开发的首批维护性更改，尚未定版）。

### 版本核对

以下由开发者手动填入 `gradle.properties` / `gradle-wrapper.properties` 的新版本号已逐一到官方 Maven/registry 核对，**全部确认为当前最新版本，无需修改**：

| 依赖 | 版本 | 核对结果 |
| --- | --- | --- |
| Gradle | 9.7.1 | ✅ 最新稳定版（下一版为 9.8.0-milestone） |
| Fabric Loom | 1.17-SNAPSHOT | ✅ 最新 snapshot 线（对应 1.17.20；1.18 仍为 alpha） |
| Fabric Loader | 0.19.5 | ✅ 最新 |
| Shadow (com.gradleup.shadow) | 9.6.1 | ✅ 最新 |
| JDA | 6.6.0 | ✅ 最新 |
| slf4j-api | 2.0.19 | ✅ 最新稳定版（2.1.0 仍为 alpha1） |
| Jackson (tools.jackson) | 3.2.2 | ✅ 最新 |
| OkHttp | 5.5.0 | ✅ 最新 |
| Netty | 4.2.17.Final | ✅ 最新 |
| jemoji | 2.0.0 | ✅ 最新 |
| Vanish mod | 1.6.15+26.2 | ✅ 26.2 最新版（Modrinth） |
| Minecraft | 26.2 | ✅ 最新正式版（26.3 仍为 pre/snapshot） |

### 更改

- 将 GitHub Actions 更新至最新大版本：`actions/checkout` v6→v7、`actions/setup-java` v5→v6、`gradle/actions/setup-gradle` v5→v6、`actions/upload-artifact` v6→v7

- 移除冗余的 `gradle/actions/wrapper-validation` CI 步骤——`gradle/actions/setup-gradle` 自 v4 起已自动执行 wrapper 校验

- 更新文档依赖：VitePress ^2.0.0-alpha.17→^2.0.0-alpha.20（npm `next` dist-tag）、Vue ^3.5.33→^3.5.42；通过 `ncu` + `yarn install` 刷新 `docs/yarn.lock`

- 重新生成 Gradle wrapper（JAR 与脚本）以匹配 Gradle 9.7.1；properties 文件新增标准字段 `validateDistributionUrl=true`

- 将 `build.gradle` `processResources` 中已弃用的 `Project.getProperties()` 替换为官方推荐的 `providers.gradleProperty(...)` Provider API，并在 gradle.properties 条目之外显式补充项目 `version`——消除全部 Gradle 弃用警告（兼容 Gradle 10）

### 验证

- 在 Java 25.0.4.1 LTS + Gradle 9.7.1 + Loom 1.17.20 + Minecraft 26.2 上 `./gradlew clean build` 构建成功，`--warning-mode all` 下零弃用警告

- 构建 JAR 中所有版本 token（`${version}`、`${mod_version}`、`${jda_version}` 等）展开正确，`fabric.mod.json`、配置 YAML 及 `THIRD-PARTY-LICENSES.txt` 无残留占位符

- 在 VitePress 2.0.0-alpha.20 + Vue 3.5.42 下 `yarn docs:build` 构建成功

## 工作 02

记录日期：2026/9/12（项目重启后的第二轮维护：复核提交 45c3de95 + 依赖再核对；尚未定版）。

### 复核提交 45c3de95「更新所有依赖（正式开始 vibe coding 世代）」

> 本节为内部复核记录，生成正式 CHANGELOG 时可略过。

结论：**该提交的全部改动均正确可用，未发现功能性缺陷**。逐项独立验证如下：

| 改动点 | 验证方式 | 结论 |
| --- | --- | --- |
| Gradle wrapper JAR | 计算本地文件 SHA-256，与 Gradle 官方 9.7.1 wrapper 校验和比对 | ✅ 完全一致：`7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d` |
| `gradlew` / `gradlew.bat` | 在 Gradle 9.7.1 下真实执行 `./gradlew wrapper` 重新生成后比对 | ✅ 重新生成后 `git` 无任何 diff，确为 9.7.1 原生生成结果（9.7.1 生成的首行注释为 "gradlew"；Fabric 示例仓库中的 "Gradle" 系旧版 Gradle 的输出，不可作为比对基准） |
| `gradle-wrapper.properties` | 同上重新生成后比对（`validateDistributionUrl` / `retries` / `retryBackOffMs` / `zipStoreBase` / `zipStorePath` 均为 9.7.1 标准字段） | ✅ 无差异 |
| `build.gradle`：`Project.getProperties()` → `providers.gradleProperty(...)` | 在 Gradle 9.7.1 上实测调用 `project.properties` | ✅ 该 API 确已弃用，实测警告原文为 "The Project.getProperties method has been deprecated. This will fail with an error in Gradle 10."，替换必要且符合官方推荐 |
| `processResources` 属性白名单（7 个 gradle 属性 + 项目 `version`） | 扫描全部资源文件中的 `${...}` 占位符 | ✅ 白名单恰好覆盖全部实际使用的 token（`version`、`mod_version`、`jda_version`、`jackson_version`、`okhttp_version`、`netty_version`、`jemoji_version`），不存在 MissingPropertyException 风险 |
| 移除 `gradle/actions/wrapper-validation` 步骤 | 查阅 `gradle/actions` v6 的 `setup-gradle/action.yml` | ✅ 其 `validate-wrappers` 输入默认即为 `true`，会自动校验仓库内所有 wrapper JAR 并在校验和不符时失败，删除独立步骤无副作用 |
| GitHub Actions 大版本升级 | GitHub API 查询各仓库 tag 列表 | ✅ `actions/checkout@v7`、`actions/setup-java@v6`、`gradle/actions/setup-gradle@v6`、`actions/upload-artifact@v7` 均存在且为当前最新大版本（各自主线最新补丁：7.0.1 / 6.0.1 / 6.3.0 / 7.0.1，使用大版本 tag 会自动跟随） |
| 文档依赖 VitePress / Vue | npm registry dist-tags + `ncu` | ✅ `2.0.0-alpha.20` 为 npm `next` 标签最新发布，`vue@3.5.42` 为 `latest` 稳定版；`yarn.lock` 已同步解析至这两者，`yarn install --frozen-lockfile` 通过 |

### 更改

- **Netty `4.2.17.Final` → `4.2.18.Final`**：`4.2.18.Final` 于 2026-09-09 发布（比上次提交晚一天），是 4.2 补丁线当前最新稳定版。JDA 的 POM 未锁定 Netty，版本由本项目声明决定；升级后依赖树中全部 netty 组件一致解析为 `4.2.18.Final`，无冲突

- **补全 `CHANGELOG_TEMP.md` 文件末尾换行符**（原文件末尾缺换行）

- 按你的决定，`gradlew` / `gradlew.bat` / `gradle-wrapper.properties`「以执行 `./gradlew wrapper` 后刷新的文件为准」：执行后与仓库现有内容**完全一致**，故无需改动；该次执行本身即完成了对这 3 个文件的重新生成与验证

> 上述均为维护性改动，不含任何用户可见的功能或行为变更。

### 验证

环境：Java 25.0.4.1 LTS（Temurin HotSpot）+ Gradle 9.7.1 + Fabric Loom 1.17.20 + Minecraft 26.2

- `./gradlew clean build --warning-mode all` **BUILD SUCCESSFUL**（12 个任务，47s）；对全量构建日志扫描 `deprecat` / `warning:` 关键字：**零命中**，无任何弃用或编译警告

- 产物 `build/Discord-MC-Chat-3.0.0-beta.2.jar`（13,245,613 字节）：`fabric.mod.json` 版本为 `3.0.0-beta.2`，`META-INF/THIRD-PARTY-LICENSES.txt` 显示 `io.netty:netty-handler:4.2.18.Final`，`config/*.yml` 版本正确，**全部资源无 `${...}` 残留占位符**

- `./gradlew :core:dependencies --configuration shadow`：netty 全家族（handler / common / resolver / buffer / transport / codec-base / transport-native-unix-common）一致为 `4.2.18.Final`

- 文档：`yarn install --frozen-lockfile` 通过（lockfile 与 package.json 同步）；`yarn docs:build` 在 vitepress 2.0.0-alpha.20（vite 8.2.2）下 **build complete in 2.03s**

### 依赖现况总表（核对时间 2026/9/12）

| 依赖 | 当前值 | 核对结果 |
| --- | --- | --- |
| Gradle | 9.7.1 | ✅ 最新稳定版（services.gradle.org/versions/current） |
| Fabric Loom | 1.17-SNAPSHOT（解析为 1.17.20） | ✅ 与官方 fabric-example-mod 当前推荐值完全一致；1.18 仍为 alpha |
| Fabric Loader | 0.19.5 | ✅ 最新，且与 fabric-example-mod 一致 |
| Minecraft | 26.2 | ✅ 最新正式版（26.3-rc-2 仍为快照） |
| Shadow (com.gradleup.shadow) | 9.6.1 | ✅ 最新 |
| JDA | 6.6.0 | ✅ 最新 |
| slf4j-api | 2.0.19 | ✅ 最新稳定版（2.1.0-alpha1 为预览） |
| Jackson (tools.jackson) | 3.2.2 | ✅ 最新（jackson-core / jackson-databind / jackson-dataformat-yaml 均为 3.2.2） |
| OkHttp | 5.5.0 | ✅ 最新 |
| Netty | 4.2.18.Final | ✅ 最新稳定版（5.0.0.Alpha2 为预览） |
| jemoji | 2.0.0 | ✅ 最新 |
| Vanish mod | 1.6.15+26.2 | ✅ 26.2 最新 |
| VitePress | 2.0.0-alpha.20 | ✅ npm `next` 最新（1.6.4 为旧 1.x 稳定线） |
| Vue | 3.5.42 | ✅ `latest` 稳定版（3.6.0 尚为 rc） |
| actions/checkout | v7 | ✅ 最新大版本 |
| actions/setup-java | v6 | ✅ 最新大版本 |
| gradle/actions/setup-gradle | v6 | ✅ 最新大版本 |
| actions/upload-artifact | v7 | ✅ 最新大版本 |
| 本机全局 npm 工具 | hexo-cli 4.3.2 / npm-check-updates 23.1.0 / npm 12.0.2 / yarn 1.22.22 | ✅ 均为各自最新版 |

### 观察与建议（本次未改动，供你决定）

- `build.gradle` 中 `java.toolchain.languageVersion`、`java.source/targetCompatibility` 与 `options.release = 25` 三者并存，实际以 `options.release` 为准，前两者冗余，可择机精简（无功能影响）

- `build.gradle` 末尾的 `project.afterEvaluate { delete file("build") }` 会在**每次配置阶段**删除根目录 `build/`，意味着任何 Gradle 命令（含 IDE 同步、`gradlew tasks`）都会清掉已构建的产物；若非刻意为之，建议改由 `clean` 任务处理

- `docs/package.json` 无 `license` 字段，`yarn` 每次都会打印 "No license field" 警告；补上 `"license": "MIT"`（或 `"private": true`）即可消除

- `.github/ISSUE_TEMPLATE/bug.yml` 说明文字仍写 "Only DMCC v2 versions are supported."，但下拉选项已含 `3.0.0-beta.2`，属残留文案（按你的决定本次未动）

- `README.md` / `README_CN.md` 项目概述仍写 "prioritizes compatibility with **Fabric 26.1.2**"，而当前主线已是 26.2（按你的决定本次未动）

- `fabric.mod.json` 中 `fabricloader: ">=0.18.4"` 低于实际编译所用的 0.19.5；作为最低要求属合理宽松策略，如无特殊考虑可维持现状

> **状态更新（工作 03）**：以上第 1、2、6 条与第 5 条的 README 部分已采纳落实；第 3、4 条（docs 的 license 字段、bug.yml 残留文案）仍未处理；第 5 条的 `README.md`（英文翻译件）按既定策略留待发布时同步。

## 工作 03

记录日期：2026/9/12（第三轮：落实维护建议 + README 功能审计与大幅扩写；尚未定版）。

### 更改（用户可见）

- **最低 Fabric Loader 要求由 `>=0.18.4` 提升至 `>=0.19.5`**（`fabric.mod.json`）：与实际编译所用的 Loader 版本对齐。注意此项会收窄兼容范围——Loader 低于 0.19.5 的实例将无法加载本模组，且它同时适用于 26.1.2 与 26.2 两个受支持的游戏版本，发布时应在 CHANGELOG 中明确提示。

### 更改（构建维护，对用户不可见）

- **`build.gradle` 精简冗余的 Java 版本声明**：移除 `java { sourceCompatibility / targetCompatibility = VERSION_25 }`，保留 `java.toolchain.languageVersion = JavaLanguageVersion.of(25)`（保证使用 JDK 25 编译）与 `options.release = 25`（保证字节码目标为 25，实际以它为准）

- **根目录 `build/` 产物改由 `clean` 任务清理**：删除原先的 `project.afterEvaluate { delete file("build") }`（配置阶段即删除产物，任何 Gradle 命令都会误清），改为对根项目应用 `base` 插件——由此根项目获得自己的 `clean` 任务，`./gradlew clean` 会同时清理根 `build/`。副作用：`gradlew build`（不带 clean）不再自动清除上一次的旧产物

- **README_CN.md 大幅扩写**（169 行 → 296 行）。作为项目文档系统的主要参考蓝本，补齐了设计文档缺失的实现细节：消息解析能力矩阵（含剧透、引用/标题、代码块与 ANSI 色码、时间戳、附件、贴纸、嵌入、投票、截断策略）、原版消息覆盖与回声、跨子服消息互转、Discord 反应/编辑/删除转发、事件广播矩阵、频道看板占位符变量、Bot 三态语义、MSPT 三段式告警与退避策略、更新检查数据源与节流、账户绑定细节（验证码字符集/一次性/刷新语义、名称解析策略、OP 同步触发时机与传输路径）、模式能力对照表、独立模式交互式终端、游戏内 `/dmcc` 命令表、命令可见性与补全范围，以及全新的第 8/9/10 节：配置文件体系（校验与 i18n）、网络与安全、日志与运维

- **README_CN.md 六处描述与实现不一致的修正**（均已按你的逐项确认执行）：
    1. “计分板排行 / 查看计分板与统计数据” → “统计数据排行”：代码只读原版 statistics，全项目无任何 scoreboard objective 读取
    2. `unlink` 命令行按发送端拆为“Minecraft 端”与“Discord 端”两行：实现是按发送者类型分流，而非一个 ✅ 覆盖三种模式
    3. `log <file>` 的“打包传输” → “以附件形式传输”：实际是读取单个文件（`.gz` 先解压）后作为附件发送，没有打包/压缩步骤
    4. `whitelist` / `stats` / `log` 在 `multi_server_client` 列的 ✅ 增加脚注 ①：该模式不运行 Discord 机器人，只能经 Standalone 的 `execute <at> <command>` 委托抵达
    5. §3.1 补充安全说明：传输为明文 TCP（未启用 TLS），共享密钥与一次性质询只解决认证与防重放，不提供加密，请勿将端口直接暴露公网
    6. §5.2 措辞修正：“user_mappings（优先级最高）” → 与 `role_mappings` 统一取最大值；绑定的 OP 0 仅为兜底下限，不压低更高的映射结果

- **README_CN.md 第 10 行**：“优先实现 Fabric 26.1.2 兼容” → “当前以 **Fabric 26.2** 为主要兼容目标（同时兼容 26.1.2）”

> 按你的既定策略，`README.md`（英文翻译件）本次**未同步**，留待发布新版本时一并更新。

### 功能性代码审计结论（内部记录）

对全部 Java 源码（约 1.5 万行，`core` + `minecraft`）做了逐模块功能盘点，与 README 声明双向比对：

- **README 现有的每一项声明都已实现，没有任何 TODO / 占位 / 未实现项**。全仓库（除 `core/build.gradle` 的两处历史 `// TODO` 注释外）grep 不到任何 `TODO` / `FIXME` / `XXX` / "not implemented" 标记
- README 第 7 节命令表与三份配置模板中的默认 OP 等级**逐项完全一致**；v3 移除 `/stop` 的说明也属实
- 仅有前述 6 处描述性偏差（功能本身均已实现），已全部修正
- **审计过程中出现并已排除的一个误判**：曾怀疑 standalone 模式下 `broadcasts.minecraft_to_minecraft.server.start/.stop` 与 `player.advancement.<type>` 键名不匹配会导致拆箱 NPE。实际核对 `ServerHandler.java:385-386、406、440` 后确认，传入该键名的是 `channelNode`（第一个字符串参数），而 YAML 中的 `server.started` / `server.stopped` / `player.advancement` 正是该值，配置与代码一致，**不存在该缺陷**

### 验证

- `./gradlew clean build --warning-mode all` **BUILD SUCCESSFUL**（40s），全量日志零弃用/警告
- `./gradlew clean` 单独执行后，根 `build/` 目录与其内的 jar 均被正确移除（此前不会）
- 产物 `build/Discord-MC-Chat-3.0.0-beta.2.jar` 内 `fabric.mod.json` 的 depends 为 `fabricloader: ">=0.19.5"`、`minecraft: ">=26.1.0"`、`java: ">=25.0.0"`，全部资源无 `${...}` 残留占位符

### 待办

- `docs/package.json` 补 `license` 字段以消除 yarn 警告（未做）
- `.github/ISSUE_TEMPLATE/bug.yml` 的 "Only DMCC v2 versions are supported." 残留文案（未做）
- `README.md` 英文翻译件与新 README_CN.md 的同步，留待发布新版本时处理

## 工作 04

记录日期：2026/9/20（第四轮：升级至 Minecraft 26.3 + 移除全部模组兼容代码 + 引入 Gradle 测试；尚未定版）。

### 更改（用户可见 / 行为变更）

- **全面升级至 Minecraft 26.3，并且仅兼容 26.3**。`gradle.properties` 的 `minecraft_version` 由 `26.2` 改为 `26.3`；`fabric.mod.json` 的依赖由 `"minecraft": ">=26.1.0"` 收窄为 `"minecraft": "~26.3"`。**这是一项收窄性变更**：26.1.2 / 26.2 及更早版本将不再允许加载本模组，发布时应在 CHANGELOG 中明确提示。`config_standalone.yml` 中 `multi_server.servers` 的示例 `minecraft_version` 同步更新为 `"26.3"`。

- **彻底移除所有"与其它模组兼容"相关的代码**（此后不再考虑兼容任何模组）：
    - Gradle：删除 `gradle.properties` 的 `# Compile-only Compatibility Hooks` 段与 `vanish_version` 属性；删除 `minecraft/build.gradle` 中的 `compileOnly "maven.modrinth:vanish:..."` 依赖；并删除根 `build.gradle` 中仅服务于该依赖的 Modrinth Maven 仓库声明（全仓库已无任何 `maven.modrinth` 依赖）
    - Java：删除 `Constants.MOD_VANISH_INSTALLED` 标志位、`FabricDMCC` 中基于 `FabricLoader#isModLoaded("vanish" / "melius-vanish")` 的探测逻辑，以及 `MinecraftEventHandler#buildInfoResponse` 中排除隐身玩家的分支（连带清理 `me.drex.vanish.api.VanishAPI`、`net.fabricmc.loader.api.FabricLoader`、`Constants` 三处已无用的 import）
    - 受此影响，`/info`（及 Discord 端信息查询）的在线玩家列表与人数统计**不再排除其它模组隐藏的玩家**

### 更改（构建与测试）

- **新增 Gradle 测试支持**：根 `build.gradle` 的 `subprojects` 块内为所有子项目声明 `testImplementation "org.junit.jupiter:junit-jupiter:${junit_version}"` 与 `testRuntimeOnly "org.junit.platform:junit-platform-launcher"`，并为 `test` 任务启用 `useJUnitPlatform()`

- **`gradle.properties` 新增 `# Test Dependencies` 段**：新增 `junit_version=6.1.3`（JUnit Jupiter 当前最新版）

- **新增唯一测试文件 `core/src/test/java/SmokeTest.java`**：按既定要求仅放入该冒烟测试（默认包），原样保留给定实现，仅补上编译所需的 `import com.xujiayao.discord_mc_chat.Constants;` 与 `import org.junit.jupiter.api.Test;`。该测试打印编译期写入 `mode.yml` 的 DMCC 版本号，用于快速验证"依赖解析 + 资源占位符展开 + 类路径"整条链路

- **测试任务配置（`subprojects` 块内的 `test` 任务）**：
    - `testLogging { showStandardStreams = true }`：Gradle 默认只在 XML/HTML 报告中捕获测试的标准输出，控制台不显示。开启后 `SmokeTest` 的 `Compiling DMCC Version: 3.0.0-beta.2` 会直接出现在 `> Task :core:test` 之下
    - `workingDir = layout.buildDirectory.get().asFile`：`LoggerImpl` 会在**工作目录**下创建 `logs/DMCC_<时间戳>.log`，因此把测试的工作目录指向项目自身的 `build/`。测试产物落在 `core/build/logs/DMCC_<时间戳>.log`，随 `clean` 一并清理，源码树与仓库中不再产生任何 `logs/` 目录，也无需为其添加 `.gitignore` 规则

- **`.github/ISSUE_TEMPLATE/bug.yml` 的 Minecraft 版本下拉框顶部新增 `"26.3"`**，以保证新版本的问题反馈能正确选择当前受支持版本

### 代码迁移明细（26.2 → 26.3，全部为编译期强制要求的破坏性 API 变更）

| 位置 | 26.2 写法 | 26.3 写法 | 依据 |
| --- | --- | --- | --- |
| `MinecraftEventHandler`（开发者成就广播） | `DisplayInfo#shouldAnnounceChat()` | `DisplayInfo#announceToChat()` | 官方映射改名 |
| 同上 | `DisplayInfo#getType()` / `getTitle()` / `getDescription()` | `DisplayInfo#type()` / `title()` / `description()` | `DisplayInfo` 已改为 record，访问器随之为 record 风格 |
| `MinecraftEventHandler`（命令执行桥 / 命令补全桥，共 2 处） | `new CommandSourceStack(source, pos, rot, level, perms, "DMCC", Component.literal("DMCC"), server, null)` | `new CommandSourceStack(source, pos, rot, level, perms, Component.literal("DMCC"), server)` | 26.3 移除了 `String` 文本名参数；`NamesProvider.constant(Component)` 的 `textName()` 即 `Component#getString()`，故 `getTextName()` 仍返回 `"DMCC"`，行为完全等价 |
| `TranslationManager#loadTranslations`（数据包语言文件扫描） | `try (PackResources packResources = pack.open())` | `try (Stream<PackResources> s = pack.open())` 后逐个 `forEach` | `Pack#open()` 返回值由单个 `PackResources` 改为 `Stream<PackResources>`；且 `PackResources` 在 26.3 已不再是 `AutoCloseable`，无需关闭单个资源 |

> 说明：`Options`/`InputConstants`/Renderpearl/Shader/OIT 等 26.3 的其余破坏性变更全部集中在客户端渲染与输入子系统，本模组为纯服务端模组（`"environment": "server"`）且不含任何客户端代码，故均不受影响。世界生成、战利品表、数据组件等 26.3 重构亦与本模组无交集。

### 验证

环境：Java 25.0.4.1 LTS（Temurin HotSpot）+ Gradle 9.7.1 + Fabric Loom 1.17.21 + Fabric Loader 0.19.5 + Minecraft 26.3

- `./gradlew clean build --warning-mode all` **BUILD SUCCESSFUL**（43s，15 个任务全部实际执行）；全量日志扫描 `deprecat` / `warning` 关键字**零命中**

- **JUnit 冒烟测试通过且输出可见**：构建日志中 `> Task :core:test` 之下直接打印 `Compiling DMCC Version: 3.0.0-beta.2`；`TEST-SmokeTest.xml` 记录 `tests="1" failures="0" errors="0"`——证明 `mode.yml` 的 `${mod_version}` 占位符展开与 `Constants.VERSION` 读取链路在 26.3 下正常；测试日志落在 `core/build/logs/DMCC_20260921_001335.log`，源码树中无任何残留

- **产物 `build/Discord-MC-Chat-3.0.0-beta.2.jar`（13,245,200 字节）**：解包后 `fabric.mod.json` 的 depends 为 `fabricloader: ">=0.19.5"`、`minecraft: "~26.3"`、`java: ">=25.0.0"`；`config/mode.yml` 版本为 `3.0.0-beta.2`；扫描全部 `.json` / `.yml` / `.txt` / `.toml` 资源**无 `${...}` 残留占位符**

- **一次性运行期验证（仅本轮做过，此后不再采用）**：本轮额外以 Loom `:minecraft:runServer` 真实启动过一次 Minecraft 26.3 服务端，确认 12 个 Mixin 中的 10 个随目标类加载完成注入（其余 2 个需真实玩家连接才会加载目标类），并以 RCON 实测 `/dmcc info` 正确返回 Minecraft 版本 `26.3`。**已按开发者决定停止使用该手段：后续所有轮次仅以编译期成功为准，不再启动真实服务端。**

- 逐项以 `javap` 对照 26.3 官方映射 jar 复核了全部 Mixin 注入目标与 `@Shadow` 成员的存在性及签名：`MinecraftServer#runServer/stopServer/onServerExit`、`Commands#<init>/dispatcher`、`PlayerList#placeNewPlayer/remove`、`ServerPlayer#die`、`ServerGamePacketListenerImpl#broadcastChatMessage/performUnsignedChatCommand/performSignedChatCommand` 及其 `player` 字段、`PlayerAdvancements#award` + `AdvancementRewards#grant`、`GameModeCommand#setGameMode`、`MsgCommand/SayCommand/EmoteCommands#lambda$register$*`、`TellRawCommand#lambda$register$0`、`ReloadableServerResources#lambda$loadResources$3`——**全部存在且签名一致**；另与 26.2 的字节码逐条比对确认 `runServer` 的注入点结构未发生变化（该静态比对方式不依赖运行游戏，可长期沿用）

### 待办（供发布时处理）

- `update/versions.json` 需在**发布时**新增一条 `"compatibility": ["26.3"]` 的版本记录，否则 26.3 环境下的 `/update` 与自动更新检查会返回"无兼容版本"（该文件是线上更新检查的数据源，属发布动作，本轮未动）
- `.github/ISSUE_TEMPLATE/bug.yml` 的 "Only DMCC v2 versions are supported." 残留文案（仍未处理）
- `README.md` 英文翻译件与本轮 README_CN.md 的同步，留待发布新版本时处理

> 本轮同时删除了开发者提供的 `primer.md`（Minecraft 26.2 → 26.3 迁移指南），因迁移已全部完成。

## 工作 05

记录日期：2026/9/21（第五轮：3.0 重构·阶段 1「纯清理」；尚未定版）。

### 更改（用户可见 / 行为变更）

- **本轮对用户零感知**：未改动任何用户可见行为、日志文本、配置键、i18n 键、协议字段、命令语义与资源文件。全部改动经字节码逐类比对证明为等价（见「验证」）。
- 本轮**未更新 `README_CN.md`**：本轮没有产生新的用户可见功能或语义变化，文档待阶段 6（首次加载自动生成 `mode.yml` + `config.yml`）时一并同步。

### 更改（代码清理，对用户不可见）

- **按 7 个分区并行清理 90 个 Java 文件**，共 **净减 1646 行**（`git diff --shortstat` = 90 files changed, +129 / −1777）：

| 分区 | 范围 | 文件数 | 行数变化 |
| --- | --- | --- | --- |
| A | `core/.../network/**`、`core/.../client/**` | 13 | 2459 → 1845（−614） |
| B | `core/.../commands/**` | 18 | 2550 → 2352（−198） |
| C | `core/.../server/discord/**`、`core/.../server/linking/**` | 12 | 3390 → 3168（−222） |
| D | `core/.../server/message/**` | 3 | 2662 → 2490（−172） |
| E | `core/.../server/*.java`（直属） | 3 | 728 → 708（−20） |
| F | `core/.../config`、`utils`、`logging`、`events`、`standalone`、`update`、`DMCC.java`、`Constants.java` | 24 | 3181 → 2971（−210） |
| G | `minecraft/src/main/java/**` | 18 | 2255 → 2045（−210） |
| 合计 | | 91（含 1 个重复计数） | 17225 → 15579（−1646） |

- **清理内容分四类**：
    - **复述式注释与 JavaDoc**：删除全部 `@author Xujiayao`（92 处）、只复述方法名/参数名的 JavaDoc、与实现不符或错位的注释（约占本轮减少量的 90%，仅注释 1654 行，从 3210 行降下来）。保留真正承载契约的说明（跨模块 future 必须完成、`suggestions` 为可变追加列表、OpSync 是"全量重置"、协议方向、`ItemStack`/`TextSegment` 字段语义、`// CRITICAL FIX: Prevent Deadlock`、`safeTruncate` 的 high-surrogate 回退、`MARKDOWN_DELIMITERS` 最长优先顺序等）。
    - **可证明零引用的死代码**：`DmccRconConsoleSource#prepareForCommand()`、`FabricDMCC` 的显式空构造器（`fabric.mod.json` 的 entrypoint 字符串未动）、`JsonUtils.readAll(Reader)`、`DMCC` 中两处被注释掉的死调用、13 个命令类的冗余显式无参构造器、`I18nManager#checkLanguageResources` 的流式探测等（每处均先 grep 全仓库确认零引用）。
    - **Java 25 惯用写法**：`HexFormat.of().formatHex()`、`GZIPInputStream#readAllBytes()`、`Path.of()`、`.toList()`、`String#formatted`、`"%mo".formatted`、`Comparator.comparing(...).reversed()`、`Collectors.joining`、`Arrays.copyOfRange` + `String.join`、静态 `DateTimeFormatter` / `Pattern`（消除热路径上的重复编译）、去掉 `boolean ignored =` 等 30 余处。
    - **空行与结构**：清理纯装饰性空行，行尾与文件尾规范化。

- **保留的既有契约（后续轮次同样不可破坏）**：`dmcc.mixins.json` 的 `required`/`injectors.defaultRequire`、`LengthFieldPrepender(4)` 与 1MB 帧上限、`IdleStateHandler(30,0,0)`、`DmccRconConsoleSource` 的同步 `StringBuffer`、`MinecraftEventHandler` 的 `opList` 防御性拷贝与三重守卫、`TranslationManager#ensureTranslationsLoaded`、`CommandSender` 的默认 `getOpLevel() == 4`、`ConfigManager` 的两套 null 语义、`Packet` 的 `protected` 构造器与 `serialVersionUID`、各 packet 公有可变字段（record 化被 `Packet` 抽象类阻塞，留待 v4）。

### 验证

环境：Java 25.0.4.1 LTS（Temurin HotSpot）+ Gradle 9.7.1 + Fabric Loom 1.17.21 + Minecraft 26.3

- **编译与打包**：`./gradlew build -x test --console=plain` **BUILD SUCCESSFUL**（9s；`:core:compileJava`、`:core:shadowJar`、`:core:mergeJars`、`:minecraft:jar` 均实际执行）。

- **字节码逐类等价性比对（本轮主要验收手段）**：以改动前的 `build/Discord-MC-Chat-3.0.0-beta.3.jar` 为基线（202 个 `com/xujiayao/*.class`），对每个类执行 `javap -p -c -constants` 反汇编后逐类 diff，结果 **identical 182 / changed 20 / added 0 / removed 0**。20 个 changed 与各分区申报的"非注释改动"集合**完全一致**；全部 diff 中的"新增非注释行"共 91 行，已逐行复核确认为已论证的等价替换（比较器写法、`Path.of`、`"%-34s".formatted`、`pop` 取代 `istore_0` 等）。

- **产物 `build/Discord-MC-Chat-3.0.0-beta.3.jar`（13,244,428 字节，基线 13,245,195）**：无签名残留文件、无 `module-info.class`、条目数 6899 与类数 202 均与基线一致。

- **全仓统计（92 个 Java 文件）**：总行 17240 → **15594**，其中代码 11866 → 11800、注释 3210 → **1654**、空行 2164 → 2140。

- **过程记录（教训）**：本轮有两处编译错误源于我给出的审计建议本身有误——`DMCC.java` 的 `import okhttp3.Cache;` 被误判为无用 import 删除；`JsonUtils` 的 `Reader#transferTo(Writer)` 返回值是 `long`（我误写成链式 `.toString()`）。两处均由分区代理修复并以 `javac 25` 独立编译全部 73 个 core 源文件（exit 0，产出 164 个 class）复核。后续轮次对新写法的签名一律先核实再用。

### 待办（供发布时处理）

- `update/versions.json` 需在**发布时**新增 `"compatibility": ["26.3"]` 记录（沿用工作 04 的待办）
- `.github/ISSUE_TEMPLATE/bug.yml` 的 "Only DMCC v2 versions are supported." 残留文案（仍未处理）
- `README.md` 英文翻译件的同步，留待发布新版本时处理
- 新增：i18n 键 `utils.i18n.check_failed` 已成为无引用死键（仍在 `lang/en_us.yml` 与 `lang/zh_cn.yml` 中）；本轮资源文件禁改，待后续统一清理资源时移除
- 新增：`core/src/test/java/**` 下的临时特征化测试（阶段 2–4 的验收工装）将在交付前整体删除，只保留 `SmokeTest.java`

