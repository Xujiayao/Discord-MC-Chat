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

> **状态更新**：以上三条已在工作 04 中全部完成。

## 工作 04

记录日期：2026/9/14（**重构第 1 轮：骨架、双加载器与模组兼容扩展点**；尚未定版）。

### 更改（用户可见）

- **同时支持 Fabric 与 NeoForge**：构建产出两个可安装的模组 JAR —— `Discord-MC-Chat-fabric-<版本>.jar` 与
  `Discord-MC-Chat-neoforge-<版本>.jar`。核心逻辑只打包一份，两个加载器共用同一份游戏侧源码；
  请按服务器使用的加载器选择对应 JAR，不要同时安装。
- **Minecraft 支持收紧为仅 26.2**：`fabric.mod.json` 与 `neoforge.mods.toml` 都声明 26.2 专属依赖；
  用于问题反馈的 Minecraft 版本下拉框也只保留 26.2。
- **首启配置流程重做**：不再有 `mode.yml`。在 Minecraft 内首次启动会**直接生成完整的 `single_server` 版
  `config.yml`**（独立 JAR 则生成 `standalone` 版），并在控制台打印文件绝对路径与三步"接下来做什么"的指引；
  把 `mode` 改成 `multi_server_client` 后，DMCC 会明确告诉你该模板需要哪些键。
  另外新增"环境与模式匹配"校验：`standalone` 不能在 Minecraft 内运行，反之亦然。
- **配置模板头部注释修正**：`mode` 现在是用户可改的模式选择键，`version` 仍不可手改；三份模板的说明按此重写。
- 顺手修：`docs/package.json` 补 `name`/`private`/`license`（消除 yarn 警告）；
  `.github/ISSUE_TEMPLATE/bug.yml` 的 v2 残留文案改为"服务端与客户端必须同版本"。

### 更改（架构，对用户不可见）

- **模块重构**：`core`（平台无关）/ `minecraft-common`（共享游戏侧源码目录，不是 Gradle 项目）/
  `fabric` / `neoforge`。两个加载器各自的 `build.gradle` 以 `srcDir` 共享 `minecraft-common`，
  不引入 Architectury。
- **删除事件总线三件套**（`EventManager`、`CoreEvents`、`MinecraftEvents`，约 400 行）：
  Mixin 现在直接调用 `MinecraftEventHandler` 的 28 个静态钩子方法；core 侧 14 处事件投递改为
  调用新的平台接口。同一模块内的自循环投递彻底消失。
- **新增 `core/platform/`**：`PlatformHost`（core → 平台的 13 个动作）、`Platform`（注册点）、
  `NoopPlatformHost`（独立模式下的空实现，core 无需判空）、`StatsProvider`（从 `StatsCommand` 内部移出）。
  `DMCC.init(PlatformHost)` 接收平台实现；`StatsCommand` 不再持有静态 provider。
- **新增模组兼容扩展点**：`ModIntegration` + `ModIntegrations`。Fabric 侧的 Vanish 兼容迁移为该接口的
  **模板实现**（不再是全局 `Constants.MOD_VANISH_INSTALLED` 开关）；NeoForge 侧今日为空注册表，
  未来加模组兼容 = 写一个类 + 一行注册，core 始终不知道是哪个模组。
- **版本来源改由 `/dmcc_version.txt` 提供**（`mode.yml` 已删除，原先从它读版本）。
- **构建体系**：根项目新增共用的 `registerLoaderJar` 任务（把加载器类与元数据合并进 core 的 shadow JAR，
  并保留 standalone 的 `Main-Class`）；`gradle.properties` 新增 `neo_version=26.2.0.87`、
  `moddev_version=2.0.147`、`junit_version=6.1.3`、`minecraft_version_range=[26.2]`；
  NeoForge 模块从 `:core` 排除 netty 与 slf4j（Minecraft 严格锁定并自带这两个库，DMCC 的副本在最终
  JAR 中是重定位的，因此不影响运行）。
- **测试框架**：`core` 接入 JUnit 6（BOM + `junit-platform-launcher`），新增永久保留的 `SmokeTest`
  （4 项）覆盖平台默认值、模式校验、环境默认模式与版本资源展开。
- **文档**：`README_CN.md` 更新 §1（双加载器 + 仅 26.2）、新增 §3.4（平台适配层与模组兼容扩展点）、
  重写 §8.1（首启流程与模式校验）、新增 §11（构建与部署、开发环境）；`.gitignore` 忽略运行期 `logs/`、`config/`。

### 验证

- `./gradlew :core:test`：本轮临时测试 5 项 + `SmokeTest` 4 项全部通过（临时测试已按约定在交付前删除）。
  其中 `ConfigFirstRunTest` 覆盖了"首启生成 → 用户未填 token 被拒绝 → 填入 token 后通过校验"的完整链路。
- `./gradlew clean build --warning-mode all`：**BUILD SUCCESSFUL**，无弃用警告；产出
  `build/Discord-MC-Chat-fabric-3.0.0-beta.2.jar`（12.61 MB）与 `build/Discord-MC-Chat-neoforge-3.0.0-beta.2.jar`（12.60 MB）。
- 产物内容核对：两份 JAR 均含共享的 `dmcc.mixins.json`、`MinecraftEventHandler`、`PlatformHost`、
  `MinecraftPlatformHost`、`ModIntegrations`、三份配置模板与展开后的元数据
  （`fabric.mod.json` 入口点指向 `...fabric.FabricDMCC`、`minecraft: 26.2`；
  `neoforge.mods.toml` 含 `[[mixins]] config`、`[26.2.0.87,)`、`[26.2]`），
  各自只含自己的入口点，且 standalone 的 `Main-Class` 清单项保留；已无 `config/mode.yml`。
- 代码规模：15,082 → **15,400 行**（+318）。本轮买的是能力与解耦（第二个加载器、平台接口、
  模组兼容扩展点），行数回收在后续轮次。
- 文档依赖未被破坏：`yarn install --frozen-lockfile` 仍报告 Already up-to-date。

### 待办（进入第 2 轮）

- 第 2 轮：解析层统一、报文层换代（Jackson JSON + 分片）、命令层收敛、server/discord 与 minecraft-common 去重、死代码清理。
- 本轮留下的小尾巴：`StatsCommand.countStatResultEntries` 与 `normalizeMinecraftNamespace` 仍是
  平台层调用 core 命令类的两处静态工具（第 2 轮随 `StatsReader` 一起移出）；
  `fabric.mod.json` 仍引用不存在的 `icon/icon.png`（沿用旧状，未新增 PNG）。

## 工作 05

记录日期：2026/9/14（**第 1.1 轮：修复 IDE 同步 + 改为单一通用 JAR**；尚未定版）。

### 更改（用户可见）

- **发布产物收敛为一个通用 JAR**：`Discord-MC-Chat-<版本>.jar` 一个文件同时承担三种用法 ——
  放进 Fabric 的 `mods/`、放进 NeoForge 的 `mods/`、以及 `java -jar` 作为独立模式运行。
  之所以可行：该文件同时带有两套加载器元数据（`fabric.mod.json` + `META-INF/neoforge.mods.toml`）与两个入口点，
  而每个加载器只读自己的元数据、只加载自己的入口点；core 的 shadow 载荷与两个加载器共用的
  `minecraft-common` 类在包内各只有一份（已核验 0 重复条目），`Main-Class` 清单项保留。
- **产物命名按你的要求改为后缀式**：`Discord-MC-Chat-<版本>-fabric.jar` / `Discord-MC-Chat-<版本>-neoforge.jar`
  作为**备用件**（内容分别是通用 JAR 的子集），仅在排查"某加载器是否加载了正确入口"时使用；
  正常分发只需通用 JAR。两个备用件同样保留 `Main-Class`，因此也都能 `java -jar`。
- `.github/ISSUE_TEMPLATE/bug.yml` 按你的要求保持你修改后的内容，本轮未再触碰。

### 更改（开发体验）

- **修复 IntelliJ IDEA / Gradle 同步失败**：ModDevGradle 的资产下载任务 `:neoforge:downloadAssets`
  要求 Java 21，而项目工具链是 Java 25，机器上只有 25 时同步直接报
  `Cannot find a Java installation ... matching {languageVersion=21}`。
  已在 `settings.gradle` 加入官方推荐的 `org.gradle.toolchains.foojay-resolver-convention`（1.0.0，
  写入 `foojay_version`），让 Gradle 按需自动下载缺失的 JDK。
  注意：首次同步会一次性下载约 200 MB 的 Temurin JDK 21 到 Gradle 用户目录（不在仓库内）；
  模组本身仍编译为 Java 25。
- `README_CN.md` §11 重写：通用 JAR 的三种用法、两个备用件、以及工具链自动下载的说明。

### 验证

- `./gradlew clean build --warning-mode all` **BUILD SUCCESSFUL**，`SmokeTest` 4 项通过，无弃用警告；
  产出 `Discord-MC-Chat-3.0.0-beta.2.jar`（12.61 MB）、`-fabric.jar`（12.61 MB）、`-neoforge.jar`（12.60 MB）。
- 通用 JAR 结构逐项核验：6880 个条目、**0 个重复条目**；`fabric.mod.json`、`META-INF/neoforge.mods.toml`、
  `dmcc.mixins.json`、`dmcc_version.txt`、两个入口点类、共享的 `MinecraftEventHandler`/`MinecraftPlatformHost`/
  `ModIntegrations`/`PlatformHost`、三份配置模板、重定位后的 `dmcc_dep/...` 依赖全部存在；
  已删除的 `config/mode.yml` 确认不再出现；三个产物的清单项均含 `Main-Class: ...StandaloneDMCC`。
- **独立模式实测**：在空目录执行 `java -jar build/Discord-MC-Chat-3.0.0-beta.2.jar`，
  成功启动到日志初始化、内部翻译加载与无头模式检测（输出被管道重定向，故按设计提示需从命令行启动并退出）。
- **IDE 同步实测**：`./gradlew :neoforge:downloadAssets` 由失败变为 **BUILD SUCCESSFUL**；
  日志确认 foojay 自动下载并启用了 Temurin JDK 21.0.12.1。

### 观察（本轮未改，供你决定）

- 三个产物的 `META-INF/MANIFEST.MF` 里都带有 shadow 生成的 `Class-Path` 长列表（列出未打包的依赖名）。
  这是**改动前就存在**的行为，对加载器与 `java -jar` 均无害（缺失条目会被忽略），但属噪音，可在后续轮次清除。
- 在 Windows 控制台直接 `java -jar` 时，中文日志会显示为乱码（Java 18+ 默认 UTF-8 输出到 GBK 代码页的控制台）；
  日志文件内容本身是 UTF-8 正常的。若希望控制台也可读，需要在启动时处理控制台编码或提示用户 `chcp 65001`。

## 工作 06

记录日期：2026/9/14（**第 1.2 轮：修复 NeoForge 无法启动 + 首启指引换行**；尚未定版）。

### 修复（用户可见）

- **NeoForge 无法加载模组：根因是 Mixin 打在了合成 lambda 上。**
  `MixinReloadableServerResources` 注入的是 `ReloadableServerResources.lambda$loadResources$3`，
  而 **NeoForge 会 patch 这个类**，导致它的合成 lambda 形状与 Fabric 不同（Fabric：
  `(ReloadableServerResources, Object, CallbackInfoReturnable)`；NeoForge：
  `(ReloadableServerResources, List, CallbackInfo)`），于是 NeoForge 抛
  `InvalidInjectionException` 并 FATAL 中止启动。这与打包方式无关——通用 JAR 与 `-neoforge` 备用件
  都会失败，因为问题在 Mixin 本身。
  **修法**：把该注入点从"合成 lambda"改为**真实方法** `MinecraftServer.reloadResources(Collection)`，
  并在其返回的 `CompletableFuture` 完成后再刷新翻译（时机比原来更准）。同时删除
  `MixinReloadableServerResources`，Mixin 总数 12 → 11。
- **首启指引不再显示字面量 `\n`**：日志器为了防日志注入会把换行转义（这是刻意设计），因此多行 lang 值在控制台
  会挤成一行 `\n`。现在 `first_run_guide` 拆成三条单行文案（生成路径 / 需要改什么 / 如何生效），逐行输出。
- **开发运行（`runServer`）此前完全不可用**：DMCC 自带 SLF4J provider，其注册文件原位于
  `src/main/resources/META-INF/services/`，因此在开发运行中它与加载器自己的 provider 同时出现在一个类路径上
  并被 SLF4J 选中，导致 FML 初始化递归崩溃（`Failed to initialize DMCC Logger` → `Recursive update`）。
  生产环境因加载器隔离而不受影响，所以此前只在 `runServer` 下暴露。
  **修法**：把该注册文件移到 `core/src/main/shadow-resources/`，只由 shadowJar 打进产物 →
  开发类路径干净、发布 JAR 里仍保留（Shadow 会把它重定位为 `dmcc_dep.org.slf4j.spi.SLF4JServiceProvider`，
  standalone 的日志因此不受影响）。

### 验证

- **真实服务端实测（两个加载器都跑通）**：
  - `./gradlew :neoforge:runServer`（NeoForge 26.2 专用服务端）→ 11 个 Mixin 全部应用成功，
    无 `InvalidInjectionException`、无 provider 冲突，`Done (3.155s)!`。
  - `./gradlew :fabric:runServer`（Fabric 26.2 服务端）→ 同样零 Mixin 错误，`Done (2.610s)!`。
  - 两次运行都实测到首启指引的三行真实换行输出，并正确生成 `run/config/discord_mc_chat/config.yml`。
  - 为定位问题，还逐一核对了 11 个 Mixin 的目标方法在 **NeoForge 补丁源码**中的签名（从本机
    `neoformruntime` 缓存中的 `mergeWithSources` 产物读取）：其余 10 个目标要么未被 NeoForge patch
    （`PlayerAdvancements`、`SayCommand`、`TellRawCommand`、`MsgCommand`、`EmoteCommands`、`GameModeCommand`），
    要么打的是真实方法（`Commands.<init>`、`MinecraftServer.runServer/stopServer/onServerExit`、
    `PlayerList.placeNewPlayer/remove`、`ServerPlayer.die`、`ServerGamePacketListenerImpl.*`）。
- `./gradlew clean build --warning-mode all` **BUILD SUCCESSFUL**，`SmokeTest` 4 项通过；
  三个产物（通用 + 两个备用件）与上一轮结构一致。
- 产物核对：发布 JAR 内仍含重定位后的 SLF4J 注册文件与 `ServiceProvider.class`；
  `core/build/resources/main` 内已无任何 `services` 条目（开发类路径干净）。

### 观察（供后续轮次）

- 仍有 4 个 Mixin 依赖"未被 patch 的类的合成 lambda"（`SayCommand`/`TellRawCommand`/`MsgCommand`/`EmoteCommands`
  的 `lambda$register$*`）。今天它们安全（这些类不在 NeoForge 的 patch 面内），但只要 NeoForge 未来 patch 这些类，
  就会出现同样的 `InvalidInjectionException`；届时的修法与本轮相同——改为注入真实方法（例如
  `CommandSourceStack.sendSuccess` 或 `PlayerList.broadcastSystemMessage`）。

