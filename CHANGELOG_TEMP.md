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

## 工作 06

记录日期：2026/9/22（第六轮：3.0 重构·阶段 2「结构性去重」；尚未定版）。

### 更改（用户可见 / 行为变更）

- **本轮仅一处有意的行为变更（已获开发者批准）**：`core/src/main/java/com/xujiayao/discord_mc_chat/server/linking/OpSyncManager.java` 的 OP 同步条件由 `opLevel > 0` 改为 `opLevel >= 0`。原逻辑对"账号已绑定、但当前身份组解析出的 OP 等级为 0"的用户跳过下发，导致其身份组被移除后 Minecraft 侧仍残留旧 OP 权限；现会下发等级 0 完成降权，与该同步"全量重置"的语义一致（`-1` 表示无法解析，仍然跳过）。两条 op-levels 收集循环同时收敛为 `buildOpLevels(...)`，JavaDoc 已写明该语义。
- **一处缺陷消除（输出不变）**：`minecraft/src/main/java/com/xujiayao/discord_mc_chat/minecraft/events/MinecraftEventHandler.java` 的 Discord→MC 组件构建原有两份近似重复实现，被实际调用的那份在 `TextSegment#text` 为 `null` 时会抛 `NullPointerException` 并中断整条聊天中继。去重后统一复用带 `null` 保护的 `buildComponentPart(...)`（`null` 渲染为空串），正常输入下的输出逐字不变。
- **一处不可达的语义差异（备案）**：`LoggerImpl` 改为继承 SLF4J `LegacyAbstractLogger` 后，形如 `logger.error("{}", arg, throwable)` 的"末位为 Throwable 的多参调用"会按 SLF4J 语义打印堆栈（原实现把该 Throwable 当作第 3 个格式参数而丢栈）。已核全仓库 112 处多参调用与 36 处三参调用，**无任何调用点末位是 Throwable**，故当前代码不可达。
- 本轮**未更新 `README_CN.md`**：无用户可见的功能、配置或命令语义变化。

### 更改（代码结构，对用户不可见）

- 按 5 个分区并行去重 24 个 Java 文件，共 **净减 867 行**（`git diff --shortstat` = 24 files changed, +789 / −1656）：

| 分区 | 范围 | 文件数 | 行数变化 |
| --- | --- | --- | --- |
| P1 | `core/.../server/message/**` | 3 | 2490 → 2135（−355） |
| P2 | `minecraft/src/main/java/**` + `core/.../network/message/TextSegment.java` | 5 | 1431 → 1320（−111） |
| P3 | `core/.../logging/**` | 1 | 477 → 203（−274） |
| P4 | `core/.../commands/**` + `core/.../network/NetworkManager.java` | 19 | 2706 → 2602（−104） |
| P5 | `core/.../server/ServerHandler.java`、`server/discord/**`、`server/linking/**` | 5 | 2677 → 2654（−23，另消除约 40 行逐字重复） |
| 合计 | | 24（去重后） | 9781 → 8914（−867） |

- **主要抽取成果**：
    - P1（最大单项）：`DiscordMessageParser` 1471 → 1174。7 个模板构建方法收敛为 `buildTemplateSegments(JsonNode, UnaryOperator<String>, UnaryOperator<String>, MessageContentInserter)`；8 个 mention 收集器参数化为 4 个（新增 `boolean spoiler`，`parseRawContent` 内的调用顺序逐字保留，因其影响同起点 token 的稳定排序）；11 处 split（两个解析器 + `MessageParserCommon`）收敛为 `MessageParserCommon.splitSegments(List<TextSegment>, Pattern, TokenFactory)` + `buildLinkSegment(...)`；`removeOverlaps` 泛型化（`interface Span`）并删除重复的 `removeMarkdownOverlaps`；两份私有的 `MarkdownState` 合一到 `MessageParserCommon`（新增 `copy()`，`MinecraftMessageParser` 的逐字段拷贝改为 `state = lineState`）；抽出 `appendAnsiSegment(...)`。
    - P3：`LoggerImpl` 477 → 203。删掉 26 个 TRACE/DEBUG 空方法与 25 个 `(Marker, …)` 转发重载，改为实现 `LegacyAbstractLogger` 的两个抽象方法（`getFullyQualifiedCallerName()`、`handleNormalizedLoggingCall(...)`）+ 5 个 `isXEnabled()`；日志文件写入、ANSI 着色、异常堆栈、`shutdown()` 行为逐字保留。
    - P2：`MinecraftEventHandler` 1048 → 960。11 处广播循环收敛为 `broadcast(PlayerList, Component)`；mention 通知与"回复+正文"两段重复收敛为 `sendMentionNotifications(...)`、`broadcastReplyAndMain(...)`（刻意传 `List<TextSegment>` 以保持"先广播回复、后构建正文"的求值次序）；`CommandSourceStack` 构造收敛为 `buildCommandSource(DmccRconConsoleSource, int)`；`buildClickable(String, ClickEvent, String)`；`RegistryOps` 提升为按 `serverInstance` 失效的缓存。`MinecraftCommands` 201 → 165：8 个子命令注册收敛为 `sub(name, defaultLevel, senderFactory, args...)`，两个 sender 的 `reply`/四级权限探测改由 `SourceBackedSender` 接口承载（`stats` 节点故意不套用，避免凭空多出裸 `/dmcc stats`）。`TextSegment` 新增 `copyWithText(String)`（逐字段复制全部 8 个可变字段）。
    - P4：`CommandArgument` 由匿名 interface 改为 `record CommandArgument(String name, String description)`（10 处匿名类），`Command` 新增 `default String usage()` 取代 `CommandManager` 与 `CommandAutoCompleter` 的手工拼接（输出逐字一致）；`ConsoleCommand`/`ExecuteCommand` 的重复目标解析下沉为 `CommandManager.resolveTarget(sender, target, i18nPrefix)` 与 `isValidTarget(...)`；`StatsCommand` 的统计读取收敛为 `loadStatValues(Path, String, String)`；`NetworkManager` 两段等待循环合并为单段（锁对象作参数传入，清空/广播/超时/快照时序逐字保留）。
    - P5：握手拒绝 6 处收敛为 `reject(ctx, serverName, reasonKey, args...)`（5 种 reason + 认证失败分支的"无 return"语义保留）；两条 relay 组装收敛为 `newRelayPacket(...)` + `dispatchRelay(...)`；reaction 双 lambda 收敛为 `broadcastReaction(...)`；`DiscordManager` 的"standalone 走 webhook / 否则走 bot"两处收敛为 `sendToChannelOrWebhook(...)`；`ChannelUpdateManager` 三处 `getCustomMessages()` 判空改为三元表达式；`DiscordEventHandler` 的 8 处 sender 构造提前到 switch 之前一次完成。
- **注释总量上升 80 行（1654 → 1734）**：本轮 diff 新增注释 112 行，全部落在 21 个新 helper 的契约说明上（`null` 语义、`cursor == 0` 的隐含前提、OpSync 全量重置、`RegistryOps` 失效条件等）。已逐块审阅，无复述式噪音；仅 `broadcast(...)` 的"给每个在线玩家发送组件"一句属可删的复述。
- **保留/拒绝的项**：`getMentionNotificationText`、`formatDiscordTimestampsForPlainText`、`cmd`/`CommandManager` 的默认权限等级 4、`LocalCommandSender` 空标记接口、`sendWebhookMessageSync` 单调用点包装、`applyPlaceholders` 的 7 次连续 replace（级联替换语义）、`LinkedAccountManager` 整文件未改；`sourcePlaceholders(...)` 抽取（会把 `Map.of()` 换成可变 `HashMap`，改变 `null` 值语义）、Msg/Say/Emote 三个 Mixin 的公共 helper（省 6 行却引入跨类耦合）经论证后放弃。

### 验证

环境：Java 25.0.4.1 LTS（Temurin HotSpot）+ Gradle 9.7.1 + Fabric Loom 1.17.21 + Minecraft 26.3

- **编译、测试与打包**：`./gradlew build --console=plain` **BUILD SUCCESSFUL**（10s）；`core/build/test-results/test/` 下 16 个结果文件合计 **145 tests / 0 failures / 0 errors / 0 skipped**（含阶段 2 新增的 15 个特征化测试类 144 用例）。

- **字节码逐类等价性比对**：以阶段 1 冻结产物为基线，`javap -p -c -constants` 逐类 diff 得 **identical 159 / changed 31 / added 7 / removed 12**。removed 恰为 10 个匿名 `CommandArgument` 类（`ConsoleCommand$1..3`、`ExecuteCommand$1..2`、`LinkCommand$1`、`LogCommand$1`、`StatsCommand$1..2`、`WhitelistCommand$2`）与 2 个各自私有的 `MarkdownState`；added 为 7 个新 helper/内部类型（`CommandManager$ResolvedTarget`、`StatsCommand$StatValue`、`MinecraftCommands$SourceBackedSender`、`DiscordMessageParser$MessageContentInserter`、`DiscordMessageParser$Span`、`MessageParserCommon$MarkdownState`、`MessageParserCommon$TokenFactory`）；changed 31 全部落在本轮被改文件及其内部类内。

- **新增差分验证工装（覆盖 JUnit 无法触及的主聊天路径）**：JUnit 层无法构造 JDA `Message`/`Member`，因此 `buildChatSegments`、`parseMessageContent`、`buildReplySegments`、`collectMentionedPlayerUuids` 等入口此前无测试覆盖，而这正是 P1 改动最集中的地方。为此在仓库外（`%TEMP%\dmcc-verify\harness`）建了一次性差分工装：用 `Proxy` 伪造 JDA `Message`/`Mentions`/`User`，对 33 条语料（Markdown 全语法、spoiler、```ansi 代码块、`<@id>`/`<@&role>`/`<#channel>`/`@everyone`、自定义与 unicode emoji、`<t:...:R>`、多行、`|` 前缀、代理对、2100 字符截断、长文本）各调用 11 个解析入口，另有 9 个固定调用（三个模板构建、mention 文案、`getRoleColorHex(null)` 等），共 **440 行确定性转储**；对阶段 1 类树（独立 `javac` 编译出 164 个 class）与阶段 2 类树逐行比对，结果 **0 差异**（转储中 0 条异常、0 条 `null` 结果，证明比对非空转）；并以 `-verbose:class` 确认两侧分别从 `p1classes` 与 `core/build/classes/java/main` 加载类，排除"两次跑的是同一份类"的假阳性。

- **产物 `build/Discord-MC-Chat-3.0.0-beta.3.jar`（13,237,683 字节；阶段 1 为 13,244,428）**：6894 个条目、197 个 `com/xujiayao/*.class`（阶段 1 为 202，减少来自匿名类与私有内部类的合并）；无签名残留文件、无 `module-info.class`。

- **全仓统计（92 个 Java 文件）**：总行 15594 → **14727**，代码 11800 → 10956、注释 1654 → **1734**、空行 2140 → 2037。相对本次重构前的基线（17240 / 11866 / 3210 / 2164）**累计净减 2513 行**。

### 待办（供发布时处理）

- `update/versions.json` 需在**发布时**新增 `"compatibility": ["26.3"]` 记录（沿用工作 04/05 的待办）
- `.github/ISSUE_TEMPLATE/bug.yml` 的 "Only DMCC v2 versions are supported." 残留文案（仍未处理）
- `README.md` 英文翻译件的同步，留待发布新版本时处理
- i18n 键 `utils.i18n.check_failed` 已成为无引用死键（仍在 `lang/en_us.yml` 与 `lang/zh_cn.yml` 中）；待统一清理资源时移除
- `core/src/test/java/**` 下的临时特征化测试与仓库外的差分工装，将在交付前整体删除/丢弃，只保留 `SmokeTest.java`
- 新增：`CommandManager.resolveTarget(...)`/`isValidTarget(...)` 暂挂在 `CommandManager`（public static），因本轮规则禁止新增文件；后续若需内聚可下沉到独立工具类
- 新增：以下需在阶段 2–4 一并交付时由开发者在真实环境手工验证（JUnit 与差分工装均无法覆盖）：
    1. 在 Discord 发出覆盖全部语法的消息（粗体/斜体/下划线/删除线/剧透/行内代码/```ansi 代码块/附件/贴纸/自定义 emoji/unicode emoji/@某人/@everyone/超链接/embed/按钮/投票/`<t:...:R>`），逐字符比对 Minecraft 端输出；再分别验证"回复 / 编辑 / 加 reaction / 删除"四种模板行的渲染；
    2. 真实绑定账号后的 @ 提及转换与自定义 emoji 转换是否与绑定前一致；
    3. `MinecraftEventHandler` 的 Discord→MC 组件渲染（颜色、粗体、clickEvent/hoverEvent、`    ┌──── ` 前缀）与 OP 同步（含工作 06 的等级 0 降权修复）；
    4. 运行目录下 `./logs/DMCC_<yyyyMMdd_HHmmss>.log` 的首行时间戳与异常堆栈是否照常落文件（覆盖 `LoggerImpl` 改造）。

## 工作 07

记录日期：2026/9/22

第七轮：3.0 重构·阶段 3「并发与性能」。目标是把审计中确认的热路径阻塞、重复 REST、无界缓存与调度器竞态一次性收口；**无功能、配置、命令或消息语法变化**，本轮不更新 `README_CN.md`。

### 更改（用户可见 / 行为变更）

- **自动补全不再阻塞网络线程**：原来 Minecraft 客户端每次补全请求都在 Netty 事件循环线程里直接跑 Brigadier 解析（最多 3 次解析 × 3 秒超时 ≈ 9 秒），期间 DMCC 的全部网络收发（含聊天中继）被卡住；现在解析提交到服务器线程并最多等待 500 毫秒，超时/异常一律返回空建议列表。补全在最坏情况下的表现由"卡 9 秒后可能有结果"变为"最多 500 毫秒后返回空"。
- **Discord 侧解析改为内存缓存优先**：用户/成员解析先读 JDA 网关维护的内存缓存（`MemberCachePolicy.ALL`），只有缓存未命中才回退 REST；无法解析的用户/成员进入 60 秒负缓存，避免同一条消息对同一 ID 反复打 REST。副作用：改名/头像/角色名最长 60 秒后才反映。
- **Webhook 按频道缓存**：原来**每条** webhook 消息都会 `retrieveWebhooks().complete()` 全量拉取该频道 webhook 列表；现按频道缓存（上限 128、5 分钟），解析/创建失败、发送失败、同步发送抛错时立即失效重建，稳态下 0 次 REST。
- **链接数据写盘异步化**：`LinkedAccountManager.save()` 改为置脏标记 + 后台单线程落盘（相近的多次变更合并成一次最新状态写），正常退出、`/dmcc reload`、`/dmcc shutdown` 都会先 flush 再返回。副作用：异常退出（`kill -9`、断电）可能丢失最后一次尚未落盘的链接变更。
- **停机更快**：Netty 两组事件循环由默认参数（2 秒静默 + 15 秒超时、串行等待，最坏约 30 秒）改为显式 500 毫秒静默 + 5 秒超时并并行等待（最坏约 5.5 秒）；bind 失败路径不再泄漏事件循环组（原来失败后线程池从不关闭）。
- **@everyone 提及通知少发一个包**：删掉恒为 `Component.empty()` 的 subtitle 包，标题/动画/音效不变；200 人在线时该次通知的包数由 800 降到 600。
- **standalone 终端修复**：stdin 到达 EOF（`dmcc </dev/null`、管道关闭、Ctrl-D、systemd 服务方式启动）时不再 100% CPU 空转；`cache/log` 目录只保留最近 10 个日志文件（保留刚写入的那个，删除为 best-effort 且不输出任何日志）。
- **断连日志缓冲有上限**：控制台日志在 Discord 断连期间的待发队列上限 1000 行，超出丢弃最旧（原为无界增长，可导致 OOM）。
- **翻译加载更稳**：加载在调用线程同步完成（去掉"建线程池后立刻 `.get()`"的假异步），快照以单次 volatile 写发布 —— 读者要么看到上一份完整快照、要么看到新的完整快照，不会看到半加载状态；加载失败时保留上一份好快照；缺失键每次加载只警告一次；`ServerStarted` 之前不再反复新建/销毁线程。
- **显式 HTTP 超时**：DMCC 自身的 HTTP 请求统一 connect 10 秒 / read 20 秒 / call 30 秒（Mojang 档案查询 5 秒 callTimeout）。原来无 callTimeout，极端网络下可长时间挂起。
- **MC → Discord 消息解析按需构建**：既无 `@` 提及也无 `:emoji:` 的消息（或 `parse_for_minecraft = false`）不再遍历链接账号、公会成员、角色与 emoji，也不再触发任何 REST；`hasClosingDelimiter` 由"每次出现回扫（整行 O(n²)）"改为每行一次 O(n) 扫描 + O(1) 查询。
- **调度器竞态修复**：`MsptMonitor`/`BotPresenceManager` 的定时任务原来在 `synchronized` 之外创建，并发调用（Netty 线程与 JDA 线程确实会并发调用）可能各建一个调度器并泄漏一个；现在创建移入锁内、被替换的实例在锁外关闭。MSPS 通知的键、顺序与占位符完全不变，只是发送时点从持锁期移到锁释放后（锁内不再做任何 Discord/网络 IO）。
- **线程上下文类加载器统一**：3 处仍使用裸线程工厂的线程池（`ServerDMCC`、`ClientDMCC`、`ConsoleLogTailer`）改用统一的 `ExecutorServiceUtils.newThreadFactory(...)`，与其余 11 处一致（Fabric 环境下 SLF4J/ServiceLoader 的关键修复）。

### 更改（代码结构，对用户不可见）

- 按 5 个分区并行改造 25 个 Java 文件，另有两轮纯注释精简（25 个文件、零代码改动）。**本阶段是唯一净增行数的阶段**（新增缓存、线程安全与超时逻辑本身需要代码和契约注释），main 源码 14,712 → 15,242（+530），其中注释精简已回收 516 行。

| 分区 | 范围 | 净变化 |
| --- | --- | --- |
| R1 | `core/.../server/discord/DiscordManager.java`、`DiscordEventHandler.java` | +128（新增 `BoundedCache` 与 5 个缓存） |
| R2 | `minecraft/src/main/java/**`（除 `TranslationManager.java`） | +111（`MinecraftEventHandler` 960 → 1071） |
| R3 | `core/.../{config,utils}/**`、`Constants.java`、`logging/impl/LoggerImpl.java` | +50 |
| R4 | `core/.../server/NettyServer.java`、`server/discord/MsptMonitor.java`、`BotPresenceManager.java`、`update/UpdateCheckManager.java`、`DMCC.java`、`standalone/TerminalManager.java`、`client/ConsoleLogTailer.java` | +85 |
| R5 | `core/.../server/ServerHandler.java`、`server/linking/LinkedAccountManager.java`、`server/message/MinecraftMessageParser.java`、`minecraft/.../translations/TranslationManager.java` | +191 |
| 注释精简 | 两轮 25 个文件（21 + 4） | −516 |

- **缓存清单（全部有界、全部有失效路径）**：`DiscordManager` 的 `RESOLVED_USERS`（4096/LRU/60 秒）、`UNRESOLVED_USERS` + `UNRESOLVED_MEMBERS`（各 4096/LRU/60 秒固定不续期）、`WEBHOOK_CACHE`（128/LRU/5 分钟，三类失败即时失效）、`CONSOLE_FILTER_PATTERNS`（256/LRU/无 TTL，key 即配置正则源串故无需失效，编译失败不缓存）、`DISCORD_NAME_CACHE`（4096/LRU）；`ServerHandler.EXCLUDED_COMMAND_PATTERNS`（64/LRU，惰性编译且仍在原循环位置抛 `PatternSyntaxException`）；`ConfigManager.PATH_PARTS_CACHE`（256，满则整体清空）；`MojangUtils.NAME_CACHE`（4096，满则清空）与 `FAILURE_CACHE`（4096/60 秒）；`EnvironmentUtils` 的 Minecraft 环境与版本各 1 条（失败也缓存）；`LoggerImpl.LOG_TIME_FORMATTER`、`StringUtils.INDEXED_PRINTF_PLACEHOLDER`、`YamlUtils.PATH_SEPARATOR`、`HttpUtils.NO_CACHE`、`MinecraftEventHandler` 的 `RegistryOps`（按 `serverInstance` 身份失效）。统一实现 `DiscordManager.BoundedCache`：access-order `LinkedHashMap` + `removeEldestEntry`，方法全部 `synchronized`，且**锁内绝不做 REST**（查→解锁→REST→回填）。
- **抽取/收敛要点**：`LinkedAccountManager` 的 `save()`/`writeIfDirty()`/`writeNow()`/`getOrCreateWriteExecutor()`/`flushPendingWrites()`/`shutdown()`（`writeNow()` 必须持类锁，因为 map 的 value 是可变 `ArrayList`；屏障 `.get()` 在锁外以避免与写线程争锁）；`MinecraftMessageParser.MentionContext`（单次 parse 内惰性记忆，派生表**刻意不跨消息缓存**，因为本文件可达范围内没有可靠的失效钩子）、`mentionAliasesByFirstChar`（按 `Character.toUpperCase(首字符)` 分桶，与 `regionMatches(true, ...)` 的首字符比较方式一致，桶内保持"最长别名优先"）、`ClosingDelimiterLookup`（每行一次反向 O(n) 扫描算出 `reachable[]`，转义规则为反斜杠跳过下一个字符）、`SIMPLE_MENTION_PATTERN.matcher(text).region(...)` 取代每处子串分配；`TranslationManager.loadAll()` + `loadingThread` 重入守卫（同步加载后必须防嵌套递归）；`MinecraftEventHandler.computeSuggestionsWithTimeout(...)`（在服务器线程上内联解析以防自死锁）与具名 `ScheduledThreadPoolExecutor`（`DMCC-Command-Timeout`）取代公共 `ForkJoinPool.commonPool` 与 `for + Thread.sleep` 轮询；`MsptMonitor.PendingNotification`（锁内收集、锁外派发）；`NettyServer.shutdownEventLoopGroups()`（幂等，置空后可重复调用）；`TerminalManager.pruneLogCache(...)`；`ConsoleLogTailer.addPendingLine(...)`。
- **保留/拒绝的项**：`countStatResultEntries` 不做缓存也无失效钩子，且 info 响应由 `NetworkManager` 在 Netty 线程上同步索取（后台结果无法回填）—— 该缺陷留待阶段 4，本轮不引入半成品缓存；`ServerHandler` 仍每命令顺序扫描配置数组（首个命中项决定结果，替换扫描会改变可观察顺序）；`playerList.deop(...)` 的线性扫描不改写（会绕过权限刷新，无法证明等价）；`LinkedAccountManager.load()` 仍是同步阻塞读（启动路径，无事件循环调用者）；jsdelivr 下载、扫 `/mods`、扫数据包仍留在调用线程（按开发者选择的保守方案 A）；`HttpUtils.NO_CACHE` 的三条指令全部保留（会序列化进请求头，删掉即改变真实请求）；`LoggerImpl` 的 `Map<String, Method>` 反射缓存保留。
- **一项前提被纠正**：`String.split("\\.")` 在 JDK 9+ 命中快路径、本身不编译正则；本轮为配置路径拆分与 `YamlUtils` 预编译 `Pattern` 省下的是每次调用的 `ArrayList`/`String[]` 分配，而非正则编译开销。另外 `StringUtils` 的 `matches(".*%\\d+\\$s.*")` 预编译为 Pattern 时**必须继续用 `matches()` 而非 `find()`**（`find()` 在 22 个样本中有 8 个结果不同，且特征化测试钉死了"换行后的 `%1$s` 不触发 `String.format`"）。

### 验证

环境：Java 25.0.4.1 LTS（Temurin HotSpot）+ Gradle 9.7.1 + Fabric Loom 1.17.21 + Minecraft 26.3

- **编译、测试与打包**：`./gradlew build --console=plain` **BUILD SUCCESSFUL**（首轮 11s，注释精简后复跑 10s）；`core/build/test-results/test/` 下 16 个结果文件合计 **145 tests / 0 failures / 0 errors / 0 skipped**（注释精简前后各跑一次，结果相同）。
- **一次真实编译失败**：R1 提交的 `DiscordManager` 中，匿名 `LinkedHashMap` 子类体内简单名 `Entry` 解析为继承来的 `Map.Entry`（需 2 个类型参数）而非 `BoundedCache.Entry`，报 `DiscordManager.java:1115: 错误: 类型变量数目错误; 需要2`；改为 `Map.Entry<K, BoundedCache.Entry<V>>` 后通过。教训：分区代理自报的 `javac exit 0` 不能替代编排侧的完整构建（该错误只有 `./gradlew build` 抓得到）。另确认 `ServerHandler.java:65-70` 与 `DiscordEventHandler.java:49-53` 的同构写法不受影响（value 分别是 `Pattern` 与 `CachedMessage`，不是简单名 `Entry`）。
- **主聊天路径差分验证**（沿用阶段 2 的 440 行确定性转储工装，JUnit 无法构造 JDA `Message`/`Member`）：阶段 2 类树 vs 阶段 3 类树 **440 行 / 0 差异、0 条异常**；以 `-verbose:class` 确认两侧分别从 `p2classes` 与 `p3classes` 加载类。
- **字节码逐类比对**（`phase2.jar` → `phase3.jar`）：**identical 168 / changed 29 / added 8 / removed 0**；added 8 为 `MinecraftEventHandler$SuggestionsResult`、`DiscordEventHandler$2`、`DiscordManager$BoundedCache`（+`$1`、`$Entry`）、`MsptMonitor$PendingNotification`、`MinecraftMessageParser$ClosingDelimiterLookup`、`ServerHandler$2`；changed 29 与 R1–R5 + 线程工厂统一所触及的文件集合完全一致，无遗漏也无多出。
- **注释精简的独立取证**：以精简前的 classes 目录快照与精简后的 classes 目录**同口径**逐类 `javap -p -c -constants` 比对 → **165/165 完全一致、0 changed、0 added、0 removed**，证明两轮注释精简（净减 516 行）未改动任何字节码。另：先前"jar 产物 vs classes 目录"比出的 49 个 changed 类已查明为 **shadow 重定位的产物**（`io.netty.*` → `dmcc_dep.io.netty.*` 及随之位移的常量池索引），非代码差异 —— 说明字节码比对必须同口径（jar 对 jar、classes 对 classes）。
- **算法级差分验证**（R5 在仓库外的一次性工装）：`hasClosingDelimiter` 新 DP 与旧内联实现在 3,238,452 条穷举/随机用例上 **0 mismatch**；mention 分桶 + `region` 与旧全别名扫描 + 子串实现在 131,715 条随机用例（含 `@`、`@everyone`、`\@alice`、以 `@` 结尾等边界）上 **0 mismatch**。R3 的等价性工装：正则 `matches` 版 22/22 等价（`find` 版 8/22 不等价）、路径拆分 13/13、时间戳格式化 5000/5000。
- **产物 `build/Discord-MC-Chat-3.0.0-beta.3.jar`**：13,255,420 字节（阶段 2 为 13,237,683）、6902 个条目、**205** 个 `com/xujiayao/*.class`（阶段 2 为 197）；无签名残留文件、无 `module-info.class`。
- **全仓统计（91 个 main Java 文件，不含 `SmokeTest`）**：总行 15,242、代码 11,438、注释 1,665、空行 2,139。相对本次重构前的基线（17,225 行 / 代码 11,866 / 注释 3,210 / 空行 2,164，阶段 1 分区表口径）**累计净减 1,983 行**（注释 −1,545、代码 −428、空行 −25）。

### 待办（供发布时处理）

- `update/versions.json` 需在**发布时**新增 `"compatibility": ["26.3"]` 记录（沿用工作 04/05/06 的待办）
- `.github/ISSUE_TEMPLATE/bug.yml` 的 "Only DMCC v2 versions are supported." 残留文案（仍未处理）
- `README.md` 英文翻译件的同步，留待发布新版本时处理
- i18n 键 `utils.i18n.check_failed` 已成为无引用死键（仍在 `lang/en_us.yml` 与 `lang/zh_cn.yml` 中）；待统一清理资源时移除
- `core/src/test/java/**` 下的临时特征化测试与仓库外的差分工装，将在交付前整体删除/丢弃，只保留 `SmokeTest.java`
- `CommandManager.resolveTarget(...)`/`isValidTarget(...)` 暂挂在 `CommandManager`（public static），因本轮规则禁止新增文件；后续若需内聚可下沉到独立工具类
- 阶段 4 待修（本轮**刻意未动**，避免把行为修复混进性能阶段）：`log` 命令的路径穿越读取（可读出 `config.yml` 中的 bot token 与 shared_secret）、`AuthResponsePacket` 缺状态校验与失败限流、`JavaSerializerDecoder` 原生反序列化面、`ExecuteCommand` 的参数错位、Info 快照串台与延迟采样张冠李戴、`tps` 除零、`Component.literal(null)`、`MsptMonitor` 的 `-1` 占位符、验证码 `Locale.ROOT`、`ConsoleLogTailer` 的 UTF-16 代理对切分、`readLogFile` 缺尺寸上限、`EventManager` 无异常隔离、`LoggerImpl.shutdown()` 不重置状态
- 以下需在阶段 3–4 一并交付时由开发者在真实环境手工验证（JUnit 与差分工装均无法覆盖）：
    1. 自动补全：正常负载下逐个字母、`/` 后空格、部分子命令 token、尾随空格、退格等输入的建议与旧版一致；在服务器卡顿（mspt 高）时确认不再卡住聊天中继，且超时后表现为"建议为空"而不是长时间无响应；
    2. Discord 侧缓存：改名/换头像/换角色色后最长 60 秒内 Minecraft 端显示更新；被删除或改名后重新解析不报错；webhook 被手工删除或权限被收回时下一次发送能自动重建（失败时应有日志且后续消息仍能发出）；
    3. 链接数据：`/dmcc link`、`/dmcc unlink` 后立刻查看 `config/discord_mc_chat/links.json`（可能延迟数百毫秒），再正常 `/dmcc shutdown`/`stop` 关闭服务器后确认文件内容与内存一致（不应丢失最后一次变更）；
    4. MSPS 通知：超出阈值、持续超出、恢复三类通知的文案、`{next_check_time}` 占位符与触发顺序与旧版一致；
    5. 翻译：`/dmcc reload` 后切换语言仍生效；人为制造加载失败（如临时改名语言文件）时确认旧翻译仍可用、日志中缺失键警告只出现一次；
    6. standalone：`dmcc </dev/null` 或关掉输入管道后进程 CPU 占用应为 0%、不再空转；`cache/log` 目录运行多轮后只保留约 10 个日志文件；
    7. @everyone 提及：Minecraft 端只应看到一次标题 + 音效（不再有多余的空 subtitle 包），200 人规模时确认不再出现明显卡顿。

## 工作 08

记录日期：2026/9/22

第八轮：3.0 重构·阶段 4「缺陷修复」。四个阶段审计中确认的真实缺陷在本轮一次性收口（安全、正确性、资源、稳定性四类），并补齐 5 个多语言键。**除下文明确列出的修复外，行为与 3.0.0-beta.3 保持一致**；本轮不更新 `README_CN.md`（留到阶段 5/6 收尾时统一处理）。

### 更改（用户可见 / 行为变更）

**安全**

- **`/dmcc log` 不再能读取 `logs/` 之外的文件**：原来把文件名直接拼到 `./logs/` 下解析，`/dmcc log ../../config/discord_mc_chat/config.yml` 可以读出 bot token 与 shared_secret。现在做规范化后的目录包含性校验，越界/非法名/超限一律回既有「文件未找到」提示（不泄露目标是否存在）。
- **单次日志读取上限 8 MiB**（`.gz` 按解压后体量边解压边计数）：超限即拒绝并记 `commands.log.read_failed`，避免把整个 `latest.log` 或 gzip 炸弹读进内存再作为 Discord 附件上传。
- **握手认证新增状态校验与失败限流**：必须先收到 `HandshakePacket` 才能处理 `AuthResponsePacket`（原来 nonce 为 null 时会退化成比较 `"null" + shared_secret` 的路径）；同一连接连续失败 3 次后直接拒绝。可感知面仅限「异常客户端被更早拒绝」。
- **原生反序列化加白名单过滤器**：只允许 DMCC 自身包、`java.util.*` 与 `java.lang.*`（排除 `reflect`/`invoke`），并限制深度 32、引用数/流大小/数组长度各 1 MiB；越界数据包被拒绝并记录 `utils.network.packet_rejected`（新增键）。正常客户端不受影响。

**正确性**

- **`/dmcc execute` 带前导空白或 Tab 时参数不再错位**（原来命令名按 trim 后切分、参数却基于未 trim 的原串，导致首个参数被重复）。
- **`/dmcc info` 不再串台或白等**：多客户端快照改为按请求隔离（原来共用一份全局缓存，并发请求互相清空，客户端中途断开必然等到超时）；连接延迟采样改为按 `sentAtMillis` 配对（不再显示过期或 0 ms 的延迟）；客户端执行命令补 10 秒超时（原来可能永不回包）、控制台补全移出 Netty 事件循环、未知包类型不再有 NPE 风险。
- **`/dmcc update` 不再误报**：只在「清单中存在兼容且比当前更新的最高版本」时提示（原来取第一个命中 `compatibility` 的条目，清单排序一变就会把更旧的版本报成新版本）；`/dmcc reload` 期间被取消的旧检查任务不再补发通告，也不再写「检查失败」。
- **TPS 不再出现 `Infinity`**（mspt 为 0 且正在冲刺时的除零）；Discord → Minecraft 中继不再因某段文本为 null 抛 NPE（组件构建的两套实现合一并做 null 安全渲染）。
- **`multi_server_client` 模式下自定义消息缺失不再 NPE**：13 处模板查找改为可空节点访问，提及通知回退到内置文案。
- **Webhook 复用判定改为按 ID 比较**（原来是引用比较，几乎永不相等 → 反复创建 webhook，最终撞上 Discord 每频道 15 个的上限）；提及与角色占位符的 null 兜底补上。
- **机器人不再中继自己的消息**：自消息过滤由引用比较改为 ID 比较（编辑消息路径同样修正）。
- **验证码在土耳其语等 locale 下可正常使用**（`toUpperCase` 指定 `Locale.ROOT`），并消除生成/消费/过期三条路径的竞态（不再残留陈旧映射）。
- **`/dmcc help` 对齐按码点计算**，中文/emoji 描述不再错位。（standalone 终端空行的处理一度改为静默忽略，实机测试后已回退为原有的「未知命令」提示，见工作 09。）
- **MSPS 恢复通知的 `{next_check_time}` 由字面 `-1` 改为真实时间戳**（与另两类通知一致，通知顺序与文案不变）。
- **`StringUtils.format` 遇到非法 printf 格式串时回退原串**，不再从日志/中继内部抛异常。（同一轮里给 `StringUtils.escape` 增加的反斜杠转义已按实机测试反馈回退，见工作 09。）
- **超长代码块分块不再切断 emoji 代理对**；事件处理器抛异常不再中断其余处理器（新增 `utils.events.handler_failed` 日志）。
- **OP 同步的静默失败现在有迹可循**：调度被拒、未知模式、玩家名未知三种情况各记一条 warn（`linking.op_sync.schedule_failed`、`linking.op_sync.unsupported_mode`、`minecraft.events.op_sync_unknown_player`）。
- **未知运行模式不再「静默成功」**：`mode.yml` 中无法识别的模式会记 `main.init.failed` 并拒绝初始化（原来跳过全部初始化却仍打印成功）。

（同一轮新增的「配置路径最后一段缺失也告警」在 `multi_server_client` 下会误报 `language` 键缺失，实机测试后已回退为只对中间段告警，见工作 09。）

**保留的既有行为（经确认）**

- Bot 显示名继续使用 `getAsTag()`（`名称#1234`），与重构前逐字一致 —— 该处一度改为 JDA 6 的 `getEffectiveName()`，按「严格零感知」原则回退。
- Embed 标题截断保留修复为 50 字符（原代码判断 `> 50` 却截到 20；>50 字符的 embed 标题显示会变长，经用户确认保留）。

**资源与稳定性**

- 3 处 Reader 未关闭（`ConfigManager`、`I18nManager`、`ModeManager`）与翻译缓存 Reader 全部改为 try-with-resources（Windows 上文件不再被锁、每次 reload 不再泄漏句柄）；日志文件改为显式 UTF-8 写入（原用平台默认字符集，中文日志在 Windows 上的编码取决于系统区域设置）。
- `LoggerImpl.shutdown()` 关闭并重置 writer，之后的日志重新打开文件（不再静默写入已关闭的 writer 而丢日志）。
- 数据包语言扫描失败不再丢弃已加载的翻译（保留官方/mod 两级结果并记 `minecraft.translations.datapack_load_failed`）。
- shutdown 时回收 OkHttp 连接池（原来的 `try (Cache ignored = OK_HTTP_CLIENT.cache())` 是恒为 null 的空操作）。

### 更改（代码结构，对用户不可见）

- 按 6 个分区并行修复 39 个 Java 文件（+1003 / −312），并新增 5 个 i18n 键；两个语言文件各增 8 行（含 3 个分组头），叶子键集合完全一致（各 237 个）。

| 分区 | 范围 | 主要修复 |
| --- | --- | --- |
| F1 | `server/ServerHandler.java`、`network/**` | 认证守卫与限流、惰性数据包日志、快照按请求隔离、反序列化白名单、更新检查移出事件循环 |
| F2 | `utils/LogFileUtils.java`、`client/**` | 路径穿越、8 MiB 上限、延迟配对、补全移出事件循环、执行超时 |
| F3 | `config/**`、`utils/**`、`logging/**`、`events/EventManager.java`、`DMCC.java` | Reader 关闭、`format` 回退、缺键告警、日志 UTF-8、事件异常隔离、模式校验 |
| F4 | `commands/**`、`update/UpdateCheckManager.java`、`standalone/**` | 参数错位、版本比较、任务代次失效、帮助对齐、终端空行 |
| F5 | `server/message/**`、`server/discord/**` | 模板可空访问、webhook 归属、标题截断、presence 热更新、MSPS 时间戳、代理对 |
| F6 | `minecraft/**`、`server/linking/**` | 验证码 Locale 与互斥、OP 同步告警、数据包翻译容错 |

- `ConfigManager.getInt(String, int)` 返回类型由 `Integer` 改为 `int`（本仓库整体重编译，无兼容影响）；`ConfigManager` 新增 `getBoolean(String, boolean)`、`Logger` 新增 `info/warn(String, Throwable)`。
- 未修清单（有理由，不是疏漏）：`ServerDMCC` 在 `NettyServer.start()` 返回 -1 时仍启动 MSPS 监控；`MinecraftEventHandler.getPlayerName` 静默返回 null（`/dmcc stats` 已有 UUID 兜底，补 warn 会按玩家刷屏）；`MsptMonitor` 轮询失败复用 `discord.manager.broadcast_failed` 键；WARN/ERROR 未改到 `System.err`（会改变可观察输出流）；`YamlUtils` 两处 HashSet 的报错顺序；`countStatResultEntries` 仍在 Netty 线程做写盘 + 全量 JSON 解析（修复点在 network/command 层，留待后续）。
- Mixin 注入点本轮做了 javap 全量体检（26.3 反混淆 jar）：12 个 Mixin 的目标方法/字段全部存在；`AdvancementRewards.grant` 在 `PlayerAdvancements.award` 中只有一处且被 `isDone()` 双重判断包住（不存在「每个 criterion 重复上报」）；`lambda$loadResources$3` 的第二参数 `Object` 就是真实类型；`MinecraftServer.runServer` 中 `Util.getNanos()J` 的 `ordinal = 0` 语义正确，`stopServer`/`onServerExit` 分布在互斥收尾路径上（事件不会重复）。

### 验证

环境：Java 25.0.4.1 LTS（Temurin HotSpot）+ Gradle 9.7.1 + Fabric Loom 1.17.21 + Minecraft 26.3

- **编译、测试与打包**：`./gradlew build --console=plain` **BUILD SUCCESSFUL**（11s）；`core/build/test-results/test/` 下 16 个结果文件合计 **149 tests / 0 failures / 0 errors / 0 skipped**（阶段 3 为 145；本轮新增 4 条，覆盖 `StringUtils.escape`/`format` 回退、`ConfigManager.getBoolean(String,boolean)`、`LogFileUtils` 的越界/超限/不可变、`LoggerImpl.shutdown()` 后仍能记录）。
- **字节码逐类比对**（`phase3.jar` → `phase4.jar`，同口径 jar 对 jar）：**identical 164 / changed 40 / added 2 / removed 1**；added 2 为 `NettyClient$LatencySample`、`NetworkManager$SnapshotRequest`；removed 1 为 `ClientHandler$3`（补全改造后减少的匿名类）；changed 40 覆盖 F1–F6 全部改动文件，另有 `CommandAutoCompleter` 与 `MinecraftCommands` 属常量池索引随被调用方变化。
- **两个语言文件的结构校验**：以项目同版本的 `YAMLMapper` 解析 `en_us.yml` 与 `zh_cn.yml` → 均解析成功，5 个新键在预期路径可读；扁平化后两侧各 **237 个叶子键、零差异**。
- **分区自检**：F1 用真实 `ObjectInputFilter` 往返全部具体 Packet 子类 + 集合/数组形态（40 成功 / 0 失败），负例（`java.io.File`、包外类、60 层嵌套、2 MiB 数组）全部被拒；F4 以反射实测 17 组版本比较与 5 个选择场景；F5 用自建 launcher 跑完 145 条测试（3 条失败全部来自并发进行的 `StringUtils` 改动，已由测试代理校准）；各分区 `javac` 全量编译 exit 0。
- **产物 `build/Discord-MC-Chat-3.0.0-beta.3.jar`**：13,264,562 字节（阶段 3 为 13,255,420）、6903 个条目、**206** 个 `com/xujiayao/*.class`（阶段 3 为 205）；无签名残留文件、无 `module-info.class`。
- **全仓统计（91 个 main Java 文件，不含 `SmokeTest`）**：总行 15,917、代码 11,792、注释 1,936、空行 2,189。相对重构前基线（17,225 / 11,866 / 3,210 / 2,164）累计净减 **1,308** 行（注释 −1,274、代码 −74、空行 +25）—— 阶段 3/4 引入的缓存、并发与防御逻辑把「净减代码行」抵消掉了，这两轮的价值主要在注释削减与缺陷修复本身。

### 待办（供发布时处理）

- 阶段 5：`minecraft` 模块按 `common`/`fabric`/`neoforge` 拆分 + NeoForge 26.3 支持 + 单 jar 双加载器（`MinecraftModBootstrap.init()` 统一入口、`META-INF/neoforge.mods.toml`、manifest 卫生、签名文件排除、重复策略统一）；随后是阶段 6 的 `mode.yml`/`config.yml` 预生成。
- 后续可做（本轮仅记录）：`countStatResultEntries` 的 Netty 线程写盘与全量 JSON 解析（应改为异步并在命令/info 层同步等待）、`ServerDMCC` 的 MSPS 监控启动条件、`ServerHandler` 每命令顺序扫描配置数组、`getPlayerName` 的静默 null。
- 沿用工作 05–07 的发布待办：`update/versions.json` 的 `"compatibility": ["26.3"]`、`.github/ISSUE_TEMPLATE/bug.yml` 文案、`README.md` 英文翻译件、死键 `utils.i18n.check_failed`、临时特征化测试与仓库外差分工装在交付前删除（只留 `SmokeTest.java`）。
- 需开发者在真实环境手工验证（本轮新增，与工作 07 的 7 项一并执行）：
    1. `/dmcc log ../../config/discord_mc_chat/config.yml`、`/dmcc log ../mode.yml`、超长/超限日志、`.gz` 巨文件 → 均应得到「文件未找到」而不是文件内容；
    2. `/dmcc execute` 前导空白/Tab 的参数解析；`/dmcc help` 中文描述对齐；`/dmcc info` 在两个以上客户端并存、其中一个中途断开时的结果与耗时；
    3. `/dmcc update` 四种清单场景（无兼容、兼容但更旧、兼容且更新、预发布版本比较）；
    4. 验证码：以 `-Duser.language=tr` 启动后用含 `i` 的验证码完成绑定；快速连续刷新/消费；
    5. 反序列化过滤：正常客户端连接/聊天/命令/统计全流程；旧版本客户端连接应被明确拒绝并记录 `utils.network.packet_rejected`；
    6. Webhook：手工删除频道 webhook 或收回权限后下一条消息应自动重建；连续发送多条 webhook 消息不再创建多余 webhook；
    7. OP 同步：绑定者失去身份组时等级回落到 0（阶段 2 的 `opLevel >= 0` 变更）；关闭期触发同步、未知模式启动时各应看到一条 warn；
    8. `multi_server_client` 模式下未加载 custom_messages 时的提及通知文案；
    9. MSPS 恢复通知中的 `{next_check_time}` 为真实时间戳；standalone 终端直接回车仍输出「未知命令：""。输入 "help" 查看可用命令列表。」（该处的静默化改动已在工作 09 回退）。

## 工作 09

记录日期：2026/9/22

第九轮：3.0 重构·实机测试反馈修复。阶段 1–4 的实机测试除下列 4 项外全部通过；本轮只做「回退与补齐」，不引入新的行为变更，也不更新 `README_CN.md`（留到阶段 5/6 收尾统一处理）。

### 更改（用户可见 / 行为变更）

- **每个 Java 文件恢复类级 `@author Xujiayao`**：阶段 1 为提高信息密度把 92 处 `@author` 全部删除，本轮按用户要求补回，覆盖 91 个主源码文件（`core` 73 + `minecraft` 18）——45 个在已有类级 JavaDoc 末尾追加一行（前加一个空 ` *` 行），46 个在阶段 1 中被整体删除类级 JavaDoc 的文件补回仅含 `@author Xujiayao` 的三行 JavaDoc。`SmokeTest` 的 `@author` 自始未动。
- **日志中的反斜杠不再被重复**：`StringUtils.escape` 回退为只翻译 `\t` `\b` `\n` `\r` `\f` 五个控制字符、其余字符原样复制（阶段 4 一度让它把 `\` 写成 `\\`，导致控制台与 `.log` 文件里的 ASCII 横幅、Windows 路径都显示成 `\\`；Standalone 与 Minecraft 环境均复现）。
- **standalone 终端直接回车恢复原有提示**：空行仍走「未知命令：""。输入 "help" 查看可用命令列表。」（阶段 4 一度改为静默忽略）；阶段 4 新增的 EOF 保护保留 —— 重定向/关闭 stdin（`dmcc </dev/null`、服务方式启动、Ctrl-D）时不再 100% CPU 空转。
- **不再误报「配置路径未找到：language」**：`ConfigManager.getConfigNode` 回退为只对中间段缺失告警、末段缺失静默（阶段 4 新增的「末段缺失也告警」在 `multi_server_client` 模式下必然触发，因为该模式的 `config.yml` 模板本就没有 `language` 键，语言由 standalone 侧下发；其余路径的告警行为不受影响）。

### 更改（代码结构，对用户不可见）

- 共 92 个文件 +233 / −20：91 个主源码文件的 `@author` 插入（+228 行 = 46 文件 × 3 行 + 45 文件 × 2 行）、三处回退的代码/注释删除（16 行：`StringUtils` 的反斜杠分支与相应 JavaDoc 说明、`TerminalManager` 的空行守卫与注释、`ConfigManager` 的末段告警块与注释）、`CHANGELOG_TEMP.md`。

### 验证

- `./gradlew build --console=plain` → **BUILD SUCCESSFUL**（35s）；`core/build/test-results/test/` 下 16 个结果文件合计 **149 tests / 0 failures / 0 errors / 0 skipped**。测试期望值同步回退：`StringUtilsTest` 的 7 处（两处 `@DisplayName`、两处注释、`escape("a\b")`、`escape("C:\path")`、二次转义）改回「反斜杠原样保留」，其中 `C:\path` 用例保留并改为断言「原样不变」，用于锁死「再次引入反斜杠转义」；测试树全量搜索确认没有其它文件隐含「反斜杠被转义」的假设。
- **`@author` 插入的自查**：`git diff -U0` 的 233 个新增行只可能是 `/**`、` * @author Xujiayao`、` */`、` *` 四种形式（`CHANGELOG_TEMP.md` 的 Markdown 行除外）；代理另以编辑前基线快照逐文件比对，确认「删除/改动行数 = 0」，即纯插入；终态结构校验 91/91 恰好一个 `@author Xujiayao`、位于最外层类型 JavaDoc 的 `*/` 之前、注解位于 JavaDoc 之后、无连续空 ` *` 行、LF 与末尾换行未变、无 BOM。
- **产物 `build/Discord-MC-Chat-3.0.0-beta.3.jar`**：13,264,470 字节、6903 个条目、**206** 个 `com/xujiayao/*.class`、无签名残留文件、无 `module-info.class`。

### 待办（供发布时处理）

- 阶段 5（`minecraft` 模块 `common`/`fabric`/`neoforge` 拆分 + NeoForge 26.3 + 单 jar 双加载器）与阶段 6（`mode.yml`/`config.yml` 预生成）**尚未开始**，需用户对本轮修复做实机确认后再继续。
- 沿用工作 05–08 的发布待办：`update/versions.json` 的 `"compatibility": ["26.3"]`、`.github/ISSUE_TEMPLATE/bug.yml` 文案、`README.md` 英文翻译件、死键 `utils.i18n.check_failed`、临时特征化测试（`core/src/test/java/com/`）与仓库外差分工装在交付前删除（只留 `SmokeTest.java`）。

## 工作 10

记录日期：2026/9/22

第十轮：3.0 重构·阶段 5–7 一并交付 —— 阶段 5「平台拆分 + NeoForge 26.3 + 单 jar 双加载器」、阶段 6「首次加载预生成 `mode.yml`/`config.yml`」、阶段 7「收尾」，并顺带交付用户新增要求的 **IPv6 过滤规则**。至此重构的全部阶段完成。

### 更改（用户可见 / 行为变更）

- **同一份 jar 同时支持 Fabric 与 NeoForge 26.3**：产物 `build/Discord-MC-Chat-3.0.0-beta.X.jar` 可直接放入任一加载器的 `mods/`，不再分别产出 `-fabric.jar` / `-neoforge.jar`。加载器元数据（`fabric.mod.json` 与 `META-INF/neoforge.mods.toml`）、Mixin 配置（`dmcc.mixins.json`）与全部游戏内逻辑都在这一个 jar 里；Fabric 入口仍为 `FabricDMCC`，NeoForge 入口为 `NeoForgeDMCC`，两者都只调用统一的 `MinecraftModBootstrap.init()`。`fabric.mod.json` 的描述文案去掉了 "Fabric" 字样（同一 jar 现在也由 NeoForge 加载）。
- **首次加载不再需要手动选择运行模式**：Minecraft 环境首次加载时，`mode.yml` 直接以预选的 `single_server` 写入（不再留下 `mode: your_option_here` 等用户手改），并随即据此生成对应的 `config.yml`，用户只需填写 `discord.bot.token` 等必要项；日志顺序与文案不变（仍是「未找到 → 正在创建 → 请编辑配置文件」）。Standalone 模式不涉及 `mode.yml`，固定以 `standalone` 运行。
- **控制台日志的敏感信息过滤默认同时覆盖 IPv6**：`console_forwarding.filter_regex` 的默认规则由 1 条（IPv4）变为 2 条（IPv4 + IPv6）。实测：`2001:db8::1`、`fe80::1%eth0`、`fd00::abcd`、`::1`、`abcd::`、8 组全写等形式均被替换为 `redacted`；玩家地址的 `地址:端口` 形式（`/2001:db8::1:25565`）整段替换（不会因端口冒号而漏报）；同时**不误伤**时间戳 `[10:44:27]`、`std::map`、`foo::bar`、MAC 地址、`12:34`、UUID 与普通玩家名；方括号形式 `[::1]:25565` → `[redacted]:25565`（端口保留，与 IPv4 规则行为一致）。**注意：这只是一条模板默认值 —— 已经生成过 `config.yml` 的用户不会自动获得该规则**，需手动把模板中的 IPv6 行补进自己的 `console_forwarding.filter_regex`（按用户裁决，不改为代码内置兜底）。
- 工作 09 记录的三处回退（日志反斜杠、standalone 终端空行、配置末段告警）已在最终产物中确认（详见下方字节码比对）。

### 更改（代码结构，对用户不可见）

- **模块拆分**：`minecraft` 拆为三个子模块 —— `:minecraft:common`（18 个文件：全部 Mixin、事件适配与游戏内逻辑，新增统一入口 `MinecraftModBootstrap`）、`:minecraft:fabric`（`FabricDMCC` + `fabric.mod.json`）、`:minecraft:neoforge`（`NeoForgeDMCC` + `META-INF/neoforge.mods.toml`）；`:core` 保持不变（平台无关核心 + Standalone）。
- `settings.gradle` 改为四个 `include`；`gradle.properties` 新增 `mod_id` / `mod_name` / `mod_license` / `minecraft_version_range` / `neo_version=26.3.0.8-beta` / `moddev_version=2.0.147`，并纳入根 `build.gradle` 的 `propertiesToExpand`（供 `neoforge.mods.toml` 的 `${}` 展开）。
- `core/build.gradle` 的 `mergeJars` 改为合并 `:minecraft:common` + `:minecraft:fabric` + `:minecraft:neoforge` 三个 jar（嵌套项目不在 `rootProject.subprojects` 中，故改为显式列表），并在每次合并前清空 `build/merged_temp`，避免上次构建的残留文件混入产物。
- `:minecraft:fabric` **不应用 Loom**（否则报 `Configuration 'minecraft' has no dependencies`；该模块只编译 `DedicatedServerModInitializer`，不需要 MC 类），改为普通 `java-library` + 显式 Fabric 仓库 + `fabric-loader`；两个加载器模块都用脚本顶层的 `files(project(":minecraft:common").tasks.named("jar").flatMap { it.archiveFile })` 依赖 common 的 jar（不能写在 `dependencies {}` 内，那里的委托是 `DependencyHandler`）。
- NeoForge 侧使用 ModDevGradle（`net.neoforged.moddev` 2.0.147），入口类用 `@Mod(value = NeoForgeDMCC.MOD_ID, dist = Dist.DEDICATED_SERVER)`；`:minecraft:common` 继续用 Loom 获取 Minecraft 类与 Mixin 注解处理器。
- `ModeManager` 新增 `DEFAULT_MODE = "single_server"` 与 `MODE_PLACEHOLDER = "your_option_here"`，创建分支由 `Files.copy` 改为「读模板 → 替换占位值 → `Files.writeString`」并返回 `true`；删除零引用的 i18n 键 `utils.config.mode.edit_prompt`（两份语言文件各 −1 行）；`mode.yml` 模板注释改写为「已预选推荐模式」，锚点行 `mode: your_option_here` 保留。
- **交付清理**：删除 `core/src/test/java/com/`（16 个临时特征化测试与 `TestEnv`），只保留 `SmokeTest.java`；构建产物与仓库外差分工装按规则丢弃。
- 行数：main 源码 93 个文件（阶段 4 为 91，新增 `MinecraftModBootstrap` 与 `NeoForgeDMCC`），总 **16,178** / 代码 **11,806** / 注释 **2,175** / 空行 **2,197**。相对重构前基线（17,225 / 11,866 / 3,210 / 2,164）累计净减 **1,047** 行（注释 −1,035、代码 −60、空行 +33）；与阶段 4 相比注释回涨 239 行，主要来自工作 09 按实机反馈补回的 228 行类级 `@author` JavaDoc。

### 验证

环境：Java 25.0.4.1 LTS（Temurin HotSpot）+ Gradle 9.7.1 + Fabric Loom 1.17.21 + ModDevGradle 2.0.147 + Minecraft 26.3

- **构建**：`./gradlew projects` 层级正确（Root + `:core` + `:minecraft`{`common`,`fabric`,`neoforge`}）；`./gradlew build --console=plain` **BUILD SUCCESSFUL**（首次含 NeoForge 产物解析与 7301 个 MC 源文件重编译；修复后复跑 12s；删测试后复跑 11s，仅 `SmokeTest > version()` 运行并打印 `Compiling DMCC Version: 3.0.0-beta.3`）。
- **测试**：删除临时测试前，`core/build/test-results/test/` 下 16 个文件合计 **149 tests / 0 failures / 0 errors / 0 skipped**；删除后只剩 `SmokeTest`（1 test / 0 failures）。
- **首次加载端到端验收**（仓库外工装：用 `net.minecraft.SharedConstants` 桩类让 `IS_MINECRAFT_ENV` 为 true，在全新空目录里启动真实产物）：`DMCC.init()` 返回 `false`（按设计停下等用户填 token）；生成的 `mode.yml` 与模板逐字相同、仅 `mode` 值被替换（不含 `your_option_here`）；`config.yml` 同时生成、`language: "to_be_auto_replaced"` 被替换为检测到的语言；再次 `ModeManager.load()` 返回 `true`（真实校验路径通过）。该验收抓出一个真实缺陷并已修复：占位符常量曾写成整行 `mode: your_option_here`，而替换值只有 `single_server`，导致生成文件末行变成裸的 `single_server`（根节点变字符串、下次启动校验必失败）。
- **IPv6 规则实测**（仓库外工装，按 `DiscordManager` 的真实管线顺序 `IPv4 → IPv6` 逐个 `replaceAll("redacted")`）：**27 项检查全部通过、零失败**。规则本身不含反斜杠，因此源码值即运行时值（IPv4 那条因 `processResources` 的 Groovy `expand` 会折叠 `\\`，源码里需写 4 个反斜杠）。
- **单 jar 产物取证**：`build/Discord-MC-Chat-3.0.0-beta.3.jar` = **13,266,623 字节 / 6906 条目 / 208 个 `com/xujiayao/*.class` / 重复条目名 0**；`fabric.mod.json`、`META-INF/neoforge.mods.toml`、`dmcc.mixins.json`、`config/mode.yml`、`config/config_single_server.yml`、`icon/icon.png` 各恰好 1 个；无 `module-info.class`、无签名文件、无 `net/minecraft/**` 条目（未泄漏 MC 类）；依赖服务文件仍为重定位后的 `dmcc_dep.*`。
- **字节码逐类比对**（阶段 4 产物 → 阶段 6 产物，同口径 jar 对 jar）：**identical 201 / changed 5 / added 2 / removed 0**；added 为 `minecraft.MinecraftModBootstrap` 与 `minecraft.NeoForgeDMCC`（阶段 5 新增）；changed 为 `config.ModeManager`（阶段 6）、`minecraft.FabricDMCC`（改为调用统一入口）与工作 09 已回退的 `utils.StringUtils`（`lookupswitch` 6 → 5 个分支）、`standalone.TerminalManager`、`config.ConfigManager` —— 与工作 09 记录的回退逐项对应。
- **语言文件结构校验**：以项目同版本的 `YAMLMapper` 解析 `en_us.yml` 与 `zh_cn.yml`，扁平化后各 **236 个叶子键、零差异**。
- **文档**：`README_CN.md` 已同步 —— §1 改为「同一 jar 同时兼容 Fabric 与 NeoForge 26.3」并新增四模块结构与构建链说明；§2.3 敏感信息过滤改为「默认内置 IPv4 与 IPv6 两条规则」；§8.1「首次运行生成」补充 `mode.yml` 预选 `single_server` 与 `config.yml` 随之生成的说明。

### 待办（供发布时处理）

- **存量用户的 `config.yml` 需手动补 IPv6 规则**（按用户裁决只改模板，不加代码内置兜底）。
- 沿用工作 05–09 的发布待办：`update/versions.json` 的 `"compatibility": ["26.3"]`、`.github/ISSUE_TEMPLATE/bug.yml` 文案、`README.md` 英文翻译件（`README_CN.md` 已更新，英文翻译件留待发布时同步）、死键 `utils.i18n.check_failed`。
- 需开发者在真实环境手工验证（本阶段新增，与工作 07–08 的清单可合并执行）：
    1. **双加载器**：把同一个 jar 分别放进 Fabric 26.3 与 NeoForge 26.3 服务端的 `mods/`，各自应正常加载（NeoForge 的 mods 列表中显示 Discord-MC-Chat 3.0.0-beta.3）、无 Mixin 应用失败日志、`/dmcc` 命令与 Discord 双向通信均可用；
    2. **NeoForge 侧注入点**：逐一触发聊天、命令（含 `/msg`、`/emote`、`/gamemode`）、玩家进出、成就上报等路径，确认 12 个 Mixin 在 NeoForge 下都生效（Fabric 侧此前已实测）；
    3. **首次加载**：全新目录首次启动 → `config/discord_mc_chat/mode.yml` 末行为 `mode: single_server`、同目录已生成 `config.yml`；填好 token 后 `/dmcc reload` 或重启应正常进入初始化；
    4. **IPv6 过滤**：用 IPv6 地址连接或用日志制造含 IPv6 的行，确认 Discord 控制台频道里显示 `redacted`（含 `地址:端口` 形式），且时间戳/MAC/UUID/`std::map` 不被误替换；把模板里的 IPv6 行补进存量 `config.yml` 后同样生效；
    5. **常规回归**：单服务器与多服务器-客户端两种模式的启动、双向消息、`/dmcc info`、`/dmcc stats`、`/dmcc log`、`/dmcc update`、`/dmcc reload`、`/dmcc shutdown`、控制台转发、以及 standalone 模式的终端命令。

