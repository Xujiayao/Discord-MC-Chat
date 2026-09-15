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

### 第 1 轮补充（用户测试通过后的小改动）

- `DmccNeoForge` 改名为 **`NeoForgeDMCC`**，与 `FabricDMCC` 命名对齐（`README_CN.md` §11.1 同步）。
- `SmokeTest` 精简为只保留"版本资源可解析"一项（该命名仍然准确：它证明测试来源集可运行，
  且构建期资源展开真的到达了运行期类路径），其余测试删除。
- **通用 JAR 成为唯一产物**：移除按加载器拆分并写入根 `build/` 的 `loaderJar` 任务；
  两个加载器的中间 JAR 仍留在 `fabric/build/libs/` 与 `neoforge/build/libs/`，仅作为 `universalJar` 的输入；
  `universalJar` 结束时删除临时目录，使根 `build/` 只剩那一个 JAR。
- 构建改用用户家目录的 `~/.gradle`（不再创建临时 GRADLE_USER_HOME），每轮交付前执行 `./gradlew --stop`。
- 验证：`./gradlew clean build --warning-mode all` **BUILD SUCCESSFUL**（1m16s，热缓存）；通用 JAR 12.61 MB，
  内含 `fabric.mod.json`、`META-INF/neoforge.mods.toml`、`dmcc.mixins.json`、`FabricDMCC.class`、
  `NeoForgeDMCC.class`（已无残留 `DmccNeoForge.class`）、`MinecraftPlatformHost.class`、三份配置模板、
  standalone `Main-Class`，以及被 Shadow 正确重定位的 SLF4J 服务注册文件
  `META-INF/services/dmcc_dep.org.slf4j.spi.SLF4JServiceProvider`。

## 工作 07

记录日期：2026/9/15（**第 1.3 轮：日志多语言化 + 构建产物与开发环境净化**；尚未定版）。

### 更改（用户可见）

- **日志文案全部多语言**。DMCC 唯一一条英文单语日志 `DMCC platform: {}` 改为走内部翻译：
  - `en_us`：`DMCC is running on platform {}`
  - `zh_cn`：`DMCC 正在 {} 平台上运行`

  至此 DMCC 自有的每一条日志都取自 `lang/<语言>.yml`。经全仓扫描（197 处日志调用），剩余的非 i18n 日志只有四类：
  ① 启动横幅（ASCII 艺术字 + 品牌信息）、② 内部语言文件损坏时那两条兜底警告（此时翻译系统已经不可用）,
  ③ 转发 Discord / 控制台原文的日志（如 `[子服名] 内容`）与 YAML 校验的 `  - 键名` 前缀，
  ④ `Logger` 包装器自身。①②已按你的确认豁免翻译。
- **根 `build/` 目录里只会有一个 JAR**：`universalJar` 收尾时除了删除自己的临时目录，还会删除 `build/tmp` ——
  Gradle 为每次 `zipTree` 建立的 `build/tmp/.cache/expanded/zip_<哈希>` 空目录就产生在这里。
- **不再提供开发服务端运行配置**：项目里已没有 `runServer` / `runClient` 任务，也不会再生成 `run/` 目录；
  验证改动的方式改为"构建成功 + 用产物 JAR 在真实服务器上人工测试"。

### 更改（开发环境，对用户不可见）

- `neoforge/build.gradle` 删除 `runs { server { ... } }` 开发运行块。
- `.gitignore` 删除 `run/` 条目（该目录只由开发运行产生）。
- **SLF4J 服务注册文件移回标准位置**：`core/src/main/shadow-resources/META-INF/services/...` →
  `core/src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider`，目录 `shadow-resources/`
  与 `shadowJar` 里的 `from("src/main/shadow-resources")` 一并删除。
  该目录当初纯粹是为"开发类路径上不要出现 DMCC 的 SLF4J provider"而设（runServer 专用），按你的要求清除。
  **发布 JAR 的内容两种放法完全一致**（已核对：Shadow 仍把它重定位为
  `META-INF/services/dmcc_dep.org.slf4j.spi.SLF4JServiceProvider`）。

### 验证

- `./gradlew clean build --warning-mode all`：**BUILD SUCCESSFUL**（26s），
  `SmokeTest.versionIsResolvedFromTheBuildResource()` **PASSED**，全量日志零弃用 / 零警告。
- **构建不再产生任何 `logs/` 目录**：`clean build` 之后全仓库（除 `node_modules`）扫描 `logs` 目录：**零命中**
  （改法见下节）。
- 构建结束后根 `build/` 目录内容：**只有 `Discord-MC-Chat-3.0.0-beta.2.jar`**（`build/tmp` 已不再残留）。
- 产物核对（6,879 条目、**0 重复条目**）：`fabric.mod.json`、`META-INF/neoforge.mods.toml`、`dmcc.mixins.json`、
  `dmcc_version.txt`、`FabricDMCC.class`、`NeoForgeDMCC.class`、
  `minecraft/events/MinecraftPlatformHost.class`、三份配置模板（`config_single_server.yml` /
  `config_multi_server_client.yml` / `config_standalone.yml`）、`custom_messages/{en_us,zh_cn}.yml`、
  `META-INF/THIRD-PARTY-LICENSES.txt` 全部在；`config/mode.yml` 确认不存在；
  清单仍含 `Main-Class: com.xujiayao.discord_mc_chat.standalone.StandaloneDMCC`；
  重定位后的 SLF4J 服务注册文件仍指向 `com.xujiayao.discord_mc_chat.logging.impl.ServiceProvider`。
- **独立模式实测（产物 JAR，非开发运行）**：把 JAR 复制到空目录后 `java -jar` 启动，
  `logs/DMCC_<时间戳>.log` 正常生成且内容为正确的 UTF-8 中文（"未检测到控制台…"三条 + 关闭成功），
  证明简化后的日志器仍然正常落盘。
- 翻译完整性交叉校验：代码中出现的 **218 个 `getDmccTranslation` 键在 `en_us` 与 `zh_cn` 中均存在**；
  两个语言文件的真实键集完全一致（脚本报出的 4 处"仅英文有"是频道看板模板块标量里的
  `Version:` / `Mode:` / `Uptime:` 文案，不是键，属误报）。

### 修复：构建不再生成 `logs/` 目录（用户反馈后追加）

`./gradlew build` 会连带执行 `:core:test`，而 SLF4J 服务注册文件搬回 `src/main/resources` 后也出现在
测试类路径上，于是测试 JVM 里"仅仅取得一个 logger"就会让 `LoggerImpl` 建出 `core/logs/DMCC_<时间戳>.log`
（空文件）。改法是两处，都很小：

1. **测试的工作目录改到 `build/` 里面**（`core/build.gradle`）：DMCC 的日志器按
   `./logs`（相对工作目录）落盘，因此让测试从 `build/test-run/` 启动即可 —— 日志出现在
   `core/build/test-run/logs/DMCC_<时间戳>.log`，随 `./gradlew clean` 一起被清掉，源码树保持干净。
   同时打开 `testLogging.showStandardStreams`，DMCC 打到标准输出的日志会直接显示在
   Gradle 的构建输出里（**测试期间 DMCC 的日志器是真实生效的**，不再被静音）。
2. **日志文件改为"首次真正写日志时才创建"**（`LoggerImpl`）：原先在**构造 logger 时**就
   `Files.createDirectories("logs")`，现在提取为 `fileWriter()` 惰性访问器，构造函数只记录环境。
   这样"取得 logger 但一条都没记"（IDE 里单跑测试、工具扫描类路径等）不会留下空日志文件；
   真正记日志时行为与以前完全一致。

验证用的临时测试（1 项，交付前已删除）：断言测试的工作目录是 `build/test-run`，通过
`Constants.LOGGER.info(...)`（走 SLF4J → `LoggerImpl`）真实写出日志，读取
`build/test-run/logs/DMCC_*.log` 并确认内容含该消息，同时确认 `core/logs` 与仓库根 `logs` 都不存在。
结果 PASSED，且该日志行在同一次构建输出里以
`[15:41:39] [Test worker/INFO]: probe message into the log file` 的形式可见。
删除临时测试后再跑 `./gradlew clean build :core:test --warning-mode all`：
**BUILD SUCCESSFUL，全仓库零 `logs` 目录，根 `build/` 只有那个 JAR**。

> 给后续轮次的提醒：测试的工作目录不再是项目目录，测试里读工程文件要用 classpath 资源或绝对路径。
> 这条已写进 `core/build.gradle` 的注释与 `TEMP_TODO.md`。

> 备用回滚点：若在 IDEA 同步或开发环境再次遇到 `Failed to initialize DMCC Logger` / 类初始化递归，
> 把 `core/src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider` 移回
> `core/src/main/shadow-resources/META-INF/services/`，并在 `shadowJar` 里加回
> `from("src/main/shadow-resources")`。发布 JAR 的内容两种放法完全一致。

## 工作 08

记录日期：2026/9/15（**第 2 轮：解析层统一 + 报文层换代 + 命令层收敛 + 去重与死代码清理**；尚未定版）。

> 本轮**功能零删减**：只做结构统一、去重、换实现与删死代码。所有对外行为、配置项、命令、
> 日志文案与渲染结果都保持不变（下文"已知的有意行为修正"一节逐条列出并说明了唯一几处刻意的修正）。

### 一、解析层统一（R2-1）

**改动前**：`DiscordMessageParser`(1592) + `MinecraftMessageParser`(781) + `MessageParserCommon`(289)
+ `TextSegment`(120) + `TextSegmentUtils`(80) = 2862 行，其中同一套"找 token → 切段 → 加样式"逻辑按
token 类型各写一份（约 16 个 `splitSegmentsByXxx` 方法），7 个 `buildXxxSegments` 结构同形，
两套 markdown 状态机各写一份。

**改动后**（`core/.../server/message/`）：

| 文件 | 行数 | 职责 |
|:--|--:|:--|
| `DiscordMessageParser` | 745 | Discord → MC 的解析入口与各事件模板渲染（**已彻底脱离 JDA**） |
| `MinecraftMessageParser` | 452 | MC → Discord / MC → MC 的解析与提及目录 |
| `MessageParserCommon` | 462 | 共享 token 正则表、通用切分器、时间戳、占位符、多语言提及通知 |
| `MarkdownParser` | 337 | 统一的 markdown 扫描器（两种方言） |
| `MessageTemplates` | 142 | `custom_messages` 模板渲染器 |
| `MentionResolver` | 45 | 提及解析接口（**不含 JDA**） |
| `MessageExtras` | 42 | 消息的非文本部分（附件/贴纸/嵌入/组件/投票） |
| `TextSegment` | 163 | 富文本片段模型（吸收了原 `TextSegmentUtils`） |

1. **通用 token 表**：`MessageParserCommon.TokenRule(pattern, styler)` + `splitByPattern(...)`
   取代了 12 个 `splitSegmentsByXxx`；另有一个**单次左到右扫描**的 `splitByRules(...)`
   用于"markdown 关闭"的路径，保证 `||<@1>||` 这类剧透提及整体优先于其中的普通提及，
   且**已经渲染出来的文本不会被下一条规则再次匹配**。这一条是写测试时抓到的真实回归：
   最初的实现把剧透提及先替换成 `[@everyone]`，随后普通 everyone 规则又匹配到其中的
   `@everyone`，把结果切成了 `[` + `[@everyone]` + `]`。
2. **markdown 扫描器共享、方言显式**：`MarkdownParser` 提供
   `parseDiscordMarkup`（反斜杠转义**会被消费**、分隔符必须**成对闭合**）
   与 `parseMinecraftMarkup`（反斜杠**原样保留**、已激活的分隔符**切换关闭**、样式可跨行延续）。
   两者共享分隔符匹配、闭合查找、样式记账与片段产出，但**扫描主体刻意不合并**：
   差异不是风格问题而是语义问题（已用差分测试证明强行合并会在两处产生不同输出）。
   据此，"转义不对称"**不是 bug 而是刻意保留**——Discord 的 markdown 规范本来就定义 `\` 为转义，
   而 MC 聊天文本没有转义语义，若统一消费 `\`，玩家输入的 `C:\new` 会变成 `C:new`。
3. **模板渲染收敛**：`MessageTemplates.of(node).with(k, v).content(...).render()`
   取代 11 个 `buildXxxSegments`。一个直接收益见下文"行为修正"。
4. **解析器脱离 JDA**：`MentionResolver` + `MessageExtras` 把"取提及"和"取附件/嵌入/投票"
   抽象成纯数据，JDA 只在新的 `server/discord/DiscordMessageAdapter` 里出现。
   由此解析算法可以用纯字符串做单元测试（本轮 17 个解析测试就是这么跑的）。

### 二、报文层换代（R2-2）

**改动前**：`network/packets/` 5 个类文件共 30 个嵌套数据包类 + `network/serialization/`
的 `JavaSerializerEncoder/Decoder`，载荷为 **Java 原生序列化**。

**改动后**：`core/.../network/protocol/`：

- `Packet`（接口，只有一个 `type()`）、`PacketType`（23 个取值）、
  `Packets`（全部报文以 **record** 形式定义）、`PacketCodec`、
  `JsonPacketEncoder` / `JsonPacketDecoder`、`CommandFileAssembler`。
- 线格式是两字段 JSON 信封 `{"type":"…","payload":{…}}`，解码走**显式的
  `PacketType → record` 映射表**（不是类名反射，对端无法诱导接收方实例化任意类）。
- **Java 原生序列化已从协议中彻底移除**（`ObjectInputStream` / `ObjectOutputStream` 不再出现在
  任何地方），关闭了认证前反序列化对端字节的 RCE 面。
- **大文件分帧**：`/log` 返回的文件按 **256 KiB 原始字节 → base64** 切片，每片一个
  `CommandFileChunk` 帧，接收端按**片序号**重组（与到达顺序无关，>64 MiB 直接拒绝），
  因此**超过 1 MiB 的日志不再撑爆分帧上限、不再打断连接**。
  （备注：最初把分片塞进同一个 `CommandResult` 里，等于没解决问题——一帧仍是 4 MiB；
  写测试时发现并改成了真正的逐帧分片。）
- **报文合并**：`Console`/`Execute` 的自动补全请求与响应（4 个类完全相同）合并为
  `AutoCompleteRequest` / `AutoCompleteResult`（用 `RpcKind` 区分）；
  两个方向的命令请求合并为 `CommandRequest`，命令/更新结果合并为 `CommandResult`。
- **不可变**：所有报文都是 record，`InfoSnapshot` 提供 `withServerName/withMinecraftVersion/
  withConnectionLatency` 三个拷贝方法取代原来的字段直改。

### 三、命令层收敛（R2-3）

- `Command.CommandArgument` 由接口改为 **record**，删掉 9 处匿名内部类（每个 12 行）。
- 新增 `Command.usage(args...)`，`CommandManager`（2 处）与 `CommandAutoCompleter`（1 处）
  重复的 usage 拼接收敛为一次调用。
- 新增 `CommandTargets`：`/console` 与 `/execute` 的**目标校验与解析完全合并**
  （两处逐字重复的 `isValidTarget` 与两段相同的 `all_online_clients` / 离线 / 非法目标分支）。
- 删除 13 个只为了"显式"而存在的空构造器与随之失效的 import。

### 四、server / discord 去重（R2-4）

- `ServerHandler.channelRead0`（原 170 行、两个 30 分支 switch + 内联握手）拆分为
  `handleHandshake` / `handleAuthResponse` / `handleMinecraftEvent` / `handleCommandResult` /
  `handleCommandRequest` / `handleAutoCompleteResult` / `handleLinkRequest` / `handleUnlinkRequest`，
  并把重复 5 次的"翻译原因 → 记日志 → 发 `Disconnect` → `close()`"提取为
  `reject(ctx, peerName, key, args...)`。
- `DiscordManager`：`sendBotMessage` 与 `sendBotMessageSync` 合并；standalone→webhook /
  single_server→bot 的**同一套分派与日志前缀逻辑**（原来在 `sendMinecraftSystemMessage`、
  `clientBroadcast`、`sendMsptMonitoringMessage`、`sendConsoleChunk` 里各写一遍）
  提取为 `postServerMessage(...)`。
- **每消息重编译正则改为按配置变更缓存**：`ServerHandler.isExcludedMinecraftCommand`
  原先用 `Pattern.matches(...)` **对每条玩家命令重新编译**每个排除规则；
  `DiscordManager.applySensitiveRedaction` 原先**对每一行控制台输出重新编译**每个脱敏规则。
  现在两者都按"规则列表指纹"缓存已编译的 `Pattern`，配置 reload 后自动失效重建
  （语法错误只在配置变化时告警一次，而不是每行一次）。
- `broadcastMinecraftRelay` 与 `broadcastMinecraftTellRawRelay` 的两套
  "是否转发给其它子服 / 是否回显给源"判定收敛为 `relayTargets(...)` + `RelayTargets`。
- `ChannelUpdateManager`：删除 `buildOfflineContext()` 与 4 个一行转发包装
  （`updateXxxAsync/Sync`），调用点直接用 3 参方法。
- 新增多语言键 `server.network.invalid_excluded_command_regex`（en/zh 同步补齐）。

### 五、minecraft-common 去重（R2-5）

- `buildComponentFromSegments` 与 `buildComponentPart` 是**同一套样式管线的两份实现** →
  只留一份（前者改为循环调用后者）；顺带让 `segment.text == null` 不再抛异常而是渲染为空串。
- 删除 `copySegmentWithText`（与 `TextSegment.copyOf` 逐字相同）。
- 3 处 `serverInstance.execute(() -> { try {…} catch (Exception ignored) {} })` 外壳提取为
  `onServerThread(Consumer<MinecraftServer>)`。
- 6 个广播方法里共 **12 处逐玩家发送循环**提取为 `broadcast(Component)`；
  `broadcastDiscordChat` 与 `broadcastMinecraftRelay` 里逐字重复的提及通知块提取为
  `notifyMentionedPlayers(...)`。
- 两处逐字相同的 11 行 `new CommandSourceStack(...)` 提取为 `dmccSource(...)` + `DMCC_SOURCE_NAME`。
- `RegistryOps.create(...)` 由**每消息重建**改为 `onServerStarted` 时建一次并缓存。
- `MinecraftCommands`：两个嵌套 record 的 `reply()` 与 4 步权限探测提取为共享静态方法；
  `getPlayerUuid()` / `getPlayerName()` 返回 null 的问题**修掉**（改为构造时捕获玩家身份）。
- `MixinServerGamePacketListenerImpl` 两个注入方法体完全相同 → 提取 `postPlayerCommand(...)`。

### 六、死代码清理（R2-6）

- 删除 `network/packets/`（30 个类）与 `network/serialization/`（2 个类）整棵树。
- 删除 `TextSegmentUtils`（4 个方法并入 `TextSegment`）。
- `LoggerImpl` 删除 10 行被注释掉的 `// log("TRACE", …)` 死代码，并补注释说明
  TRACE/DEBUG 为何是刻意的空实现。
- `ExecutorServiceUtils.shutdownAnExecutor`：原来 `catch (Exception ignored)` 连
  `InterruptedException` 一起吞掉且丢弃中断标志 → 改为只捕获 `InterruptedException`
  并 `Thread.currentThread().interrupt()` 恢复中断状态。
- `StatsCommand` 两处 `catch (Exception ignored)` 缩窄为 `catch (IllegalArgumentException)`
  （那里唯一可能抛出的就是 `UUID.fromString`），并注明"不是玩家存档文件，跳过"。
- `OpSyncManager` 的 `RejectedExecutionException` 空捕获补注释说明为何可以静默丢弃。
- 保留但已确认**属于刻意行为**的空捕获（未改动）：`EnvironmentUtils` 的类存在性探测、
  `NettyClient` 关停期等待、`JsonUtils.getStat` 读不到统计文件即返回 0、
  `DiscordManager` 非法 URL 跳过 click 事件、`ChannelUpdateManager` 限流丢弃
  （这里**刻意不用 `queue()`**：单参 `queue()` 会让 JDA 把每一次"预期内的限流丢弃"都打成错误日志）。

### 已知的有意行为修正（共 4 处，其余为零变化）

1. **`{server_color}` 占位符以前是失效的**：旧实现按"先替换 `{server}`、再替换 `{server_color}`"
   的顺序做字符串替换，而 `{server}` 是 `{server_color}` 的前缀，于是
   `color: "{server_color}"` 被替换成 `SMP_color`（非法颜色，最终回退默认色）。
   新实现改为**单次扫描**替换，因此 **`[子服名]` 前缀现在真的会按配置上色**，
   替换结果也不会被二次扫描。
2. **Discord → MC 方向现在也支持 `{display_name}`**：`xxxxx_to_minecraft.user_message` /
   `system_message` 是 D→MC 与 MC→MC 共用的模板，旧实现只在 MC→MC 方向替换 `{display_name}`。
3. **同一片段里多个 `{message}` 现在会被依次插入**（旧实现只处理前两个分段，
   第二个 `{message}` 之后的文本会被丢弃）。
4. **无法解析的 `<t:…>` 时间戳在两个解析路径下表现一致**（都保留原 token 并标黄）；
   旧实现里"markdown 关闭"路径会把它当普通文本。

> 以上 4 处都是"旧实现自相矛盾/明显失效"的地方，且都不删减功能。如希望保持旧观感请告知。

### 验证

- **差分测试（临时，交付前已删除）**：把改动前的解析实现**逐字复制**成测试夹具
  `LegacyDiscordParser`，与新区块做逐片段比对（文本 + 5 个样式位 + 颜色 + click + hover）：
  - Discord markdown 扫描器：固定语料 50 例 + **随机 4000 例**全等；
  - Minecraft markdown 扫描器：固定语料 41 例 + **随机 4000 例**全等；
  - D→MC 整条内容管线（含 ANSI 代码块）：固定语料 × markdown 开/关 + **随机 2000 例 × 2** 全等；
  - 截断（主行 6 行/200/400、CJK、回复 1 行/20~40）：**随机 3000 例 + CJK 200 例**全等。
  这套差分测试在开发过程中**抓到 2 个真实回归**（Minecraft 方言在 `__` 不可消费时未回退到 `_`；
  markdown 开启时剧透提及被二次匹配），均已修复。
- **协议测试（临时，交付前已删除）**：全部 23 种报文逐一 JSON 往返（用
  `encode(decode(encode(p))) == encode(p)` 做逐字节等价比对，可发现任何字段丢失/静默默认值）；
  线格式确认是 JSON 且不含 Java 序列化魔数；未知字段容忍；畸形帧/未知类型/缺 payload
  一律抛 `ProtocolException`；3 MiB + 12,345 字节随机文件切分后**每一帧都 < 1 MiB**，
  打乱顺序喂给重组器后与原文件逐字节相等；缺片时返回 null 而不是半截数据；分片元数据校验。
- 交付前完整协议命令 `./gradlew clean build :core:test --warning-mode all`：**BUILD SUCCESSFUL（24s）**，
  无警告、无弃用提示。
- 产物核对：根 `build/` **只有** `Discord-MC-Chat-3.0.0-beta.2.jar`（13,232,980 字节、6873 条目、**0 重复**），
  内含两个加载器元数据、`dmcc.mixins.json`、`dmcc_version.txt`、两个入口点、
  `MinecraftPlatformHost.class`、三份配置模板与两份 `custom_messages`，
  `Main-Class` 仍为 `StandaloneDMCC`；旧的 `network/packets/Packet.class`、
  `network/serialization/JavaSerializerDecoder.class` 与 `config/mode.yml` **确认不存在**。

### 行数变化

- **主源码（`*/src/main/**/*.java`）：2108 增 / 4218 删 = 净 −2110 行**。
- 文档与资源：+18 行（README §9/§8.3 改写、lang 新增 1 键 ×2 语言）。
- 工作区整体：净 −2094 行。

> 与第 2 轮计划（目标 ≈ −4800）有差距，原因见下节"与计划的偏差"，均为主动选择而非遗漏。

### 与计划的偏差（及理由）

1. **R2-1 只拿到 −518（解析层），而非 −1800**。原因是新结构带完整 javadoc 的
   `MarkdownParser` / `MessageTemplates` / `MentionResolver` / `MessageExtras` 共 566 行，
   把去重收益吃掉了大半；而两套 markdown 扫描器经差分测试证明**不能**强行合并
   （语义不同），因此保留了双实现。
2. **R2-2 只拿到约 −530，而非 −950**。报文改为 record 后注释量上升；
   `CommandFileChunk` + `CommandFileAssembler` + 打包器是**新增**的正确性代码
   （真正的逐帧分片）。
3. **R2-3 未做"13 个命令类按域合并为 4 个"**：逐条核对后，各命令的业务逻辑差异较大，
   合并主要是**搬家**而不是**删代码**，还会让 `CommandManager` 的注册表变得间接；
   本轮只做了证据充分的三处去重（`CommandArgument` record、`usage()`、`CommandTargets`）。
4. **R2-4 未做 `DiscordManager` 的物理拆分**（JDA 生命周期 / 消息派发 / 控制台转发）：
   控制台转发那一块与 `DiscordManager` 的私有状态（`jda`、webhook、头像解析、日志脱敏）
   耦合较深，拆出去需要把一批私有方法提升为包级可见，收益（可读性）与风险（改动
   `/log`、控制台转发这两条用户天天用的路径）不成正比。本轮改为**在类内部消重**
   （见第四节），把拆分留到有实际需求时再做。
5. **R2-5 的 `broadcastToPlayers(Component, List<String>)` 未新增**：核对后文件里没有
   对应的调用点，加了就是新的死代码。
6. **`ChannelUpdateManager` 的 `queue(_ -> {}, _ -> {})` 未改成单参 `queue()`**：
   单参 `queue()` 会让 JDA 把每一次预期内的限流丢弃都打成错误日志，属行为变化，故保留双空回调。

### 人工测试清单（请在真机上逐项确认）

1. **解析与渲染（最重要）**：以下 12 条语料各发一次，核对 MC 端显示与 Discord 端显示：
   ① `**粗体** *斜体* __下划线__ ~~删除线~~`；② `||剧透内容||` 与 `||<@某人>||`；
   ③ ` ```ansi` 代码块（含 `\u001B[31m` 红色、`\u001B[1m` 粗体）；
   ④ `<t:1700000000:R>` 等 9 种时间戳样式；⑤ `<@用户>`、`<@&角色>`、`<#频道>`、`@everyone`；
   ⑥ `<:name:id>` 与 `:alias:` 表情；⑦ Markdown 链接与裸链接（点击能否打开、hover 是否有提示）；
   ⑧ 图片/视频/文件附件 + 剧透附件；⑨ 嵌入消息；⑩ 投票；⑪ 超长消息（>6 行、>200 字符、含中文）；
   ⑫ 回复一条多行消息（回复行应只有 1 行且以 `...` 结尾）。
   **重点看 ⑪⑫ 的截断位置，以及中文消息是否走 400 字符档。**
2. **`{server_color}` 修正**：把 `custom_messages/zh_cn.yml` 的
   `xxxxx_to_minecraft.user_message` 第一段保持 `color: "{server_color}"`，
   在 `standalone` 模式下确认 `[子服名]` **按子服颜色显示**（改前是白色）。
3. **命令矩阵**：13 个命令 × OP 等级（−1 / 0 / 4）× 三种模式跑一遍，
   重点确认 `/console`、`/execute` 的 `all_online_clients` 展开与错误提示（无在线子服/非法目标/子服离线）。
4. **`/log` 取大文件**：让子服 `latest.log` **超过 1 MiB**，从 Discord 执行
   `/execute at:<子服> command:/log latest.log`，确认**文件完整送达且连接不中断**
   （改前会触发分帧超限并直接断开该子服连接）。
5. **控制台转发与脱敏**：`console_forwarding.filter_regex` 配一条能命中的规则，
   确认转发内容被替换为 `redacted`；故意写一条非法正则，确认只在配置变化时告警一次。
6. **账户绑定全流程**：验证码 → `/link` → 角色颜色 → OP 同步（`/whitelist` 与
   `sync_op_level_to_minecraft`），并确认 `/dmcc info` 的每个子服信息齐全。
7. **两平台各跑一遍**：Fabric 与 NeoForge 分别加载该 JAR，确认能启动、能双向转发；
   `standalone` 模式下确认 `java -jar` 仍能启动并生成日志。
## 工作 09

记录日期：2026/9/15（**第 3 轮：内部瘦身 + 并发/性能/正确性修正**；尚未定版）。

> **本轮全部内容按定义"用户无感知"**：没有改动任何配置键、命令、消息文案、日志格式、渲染结果与
> 界面数字。原本计划里的**用户可见部分（YAML 可读性改造 + 文档收尾）已经拆到第 4 轮**，
> 见 `TEMP_TODO.md` 第 7 节——这样第 3 轮与第 4 轮可以分开核对。

### 一、日志 / 配置 / 工具瘦身

1. **`LoggerImpl` 去掉反射派发与每行格式化分配**
   - `Map<String, Method>` + `log("INFO", …)` 字符串查表 → 私有 `enum Level`，每个常量缓存自己的
     ANSI 颜色与两个反射 `Method`（`volatile`），调用点改为 `log(Level.INFO, …)`。
   - `new SimpleDateFormat("HH:mm:ss")`（**每行一次对象分配 + 一次内部锁**）→ `DateTimeFormatter` 常量。
   - `StringUtils.escape()` 先单次扫描，无特殊字符时直接返回原串（原来每行做 5 次 `replace`、
     分配 5 个中间字符串）；`StringUtils.format()` 里 `str.matches(".*%\\d+\\$s.*")`
     **每次调用都重新编译正则** → 改为预编译 `Pattern` + `find()`。
   - 日志文本格式**逐字未变**（`[时间] [线程/级别]: 消息`，文件版无色、控制台版带 ANSI）。
2. **`ConfigManager` 修正三个真实缺陷（全部校验能力原样保留）**
   - `config` / `mode` 加 `volatile`：它们由 reload 线程写、被其它线程读，原来没有任何发布保证。
   - `getConfigNode` 每次调用都 `path.split("\\.")` → 切分结果按路径缓存（一条系统消息路径要读 5–8 次）。
   - **缺失路径告警由"每次读取都打"改为"每个配置版本每条路径打一次"**：像
     `console_forwarding.channel` 这类在当前模式下合法缺失的可选键，原来会在每次握手/事件时刷 WARN。
   - 新增 `getBoolean(path, default)` 重载，并修掉 `ExecutorServiceUtils` 在"配置尚未加载就关停"时
     `ConfigManager.getBoolean(...)` 返回 `null` 拆箱 NPE 的问题。
3. **`I18nManager` 线程安全**：`DMCC_TRANSLATIONS` 原为普通 `HashMap`，会在**客户端登录时的 Netty 线程**
   被 `clear()` + 重填，而读取来自 MC / JDA / 日志线程 → 改为"构建新 map → `Map.copyOf` → 一次性
   volatile 发布"，读者永远看不到半满的表；`language` / `customMessages` 加 `volatile`。
   附带：翻译加载失败时**保留上一份可用快照**，而不是像原来那样清空成"到处显示键名"。
4. **`MojangUtils` 加 TTL 与负缓存**：原来只缓存成功、失败完全不缓存 → Mojang 故障期间每条聊天消息都会
   为每个未解析 UUID 重发一次阻塞 HTTP。现在离线 UUID 永久缓存、在线成功 24h、**失败 5 分钟**；
   且失败时若之前成功解析过，**继续返回上次已知名字**而不是退化成裸 UUID（避免可见退化）。
5. **`LogFileUtils.readLogFile` 收敛路径**：原来 `resolve(fileName).normalize()` 后直接读，
   `/log ../../server.properties` 可穿越出 `./logs`；现在校验规范化后的路径仍在 `./logs` 内。
6. **`JsonUtils`** 三个 `toStringMap` 重载收敛为一个实现。
7. **`Constants.OK_HTTP_CLIENT`** 显式设置超时（连接 10s / 读 15s / 写 15s / 整次调用 20s），
   原来完全依赖库默认值，而调用方都在延迟敏感路径上（聊天中的名字解析、`/dmcc update`）。

### 二、并发与性能修正

1. **`NetworkManager.requestInfoSnapshot` 改为按请求关联**（原来 `infoCache.clear()` 在加锁之前、
   `expectedResponses` 取快照导致中途断连必然等满超时、**响应无请求关联**，四个调用线程
   ——MSPT 每 10s、Presence 每 30s、频道看板每 10min、`/info`——会互相清缓存甚至消费上一轮的数据）。
   现在：注册与广播都在 `infoLock` 内完成、**每个调用方拥有自己的 `LinkedHashMap`**、
   等待集合每次唤醒重算（客户端断连会立即唤醒等待者）、按服务名"最新值优先"、返回结构不变。
   协议报文形状未改（`Packets.InfoSnapshot` 没有可回显的时间戳字段，因此用内部请求 id 而不是时间戳回显；
   用 `connectionLatencyMillis` 反推会受两端时钟偏差影响，反而会丢掉健康客户端的应答）。
2. **`CommandManager`**：单线程执行器 → **虚拟线程**（`DMCC-Command-<n>`，仍固定 mod 类加载器）；
   `COMMANDS` 改为 `volatile`，`initialize()` **构建新表后一次性替换**（消除 reload 期间"未知命令"窗口）；
   新增**公平读写锁**：console/execute/info/help/log/stats/links/update 走读锁（完全并发），
   reload/shutdown/link/unlink/whitelist 走写锁（保持原有的串行语义，避免与 reload 交叠）。
   `execute`/`executeAndWait` 把 volatile 执行器读进局部变量，顺带消除"另一线程 shutdown 抢在 check 与
   submit 之间"的潜在 NPE。
3. **把阻塞工作移出 Netty IO 线程**
   - `MinecraftMessageParser` 的提及目录改为**带 60 秒 TTL 的不可变缓存 + 单飞重建**：
     原来每条消息都重建全量别名表，并对每个已绑定账户做**阻塞 JDA 调用**（`retrieveUser`/`retrieveMember`）。
     新增 `invalidateMentionCache()`，并在 `OpSyncManager.syncAll()`（每次 link/unlink 成功后都会调用）
     与 `LinkedAccountManager.load()`（含手工编辑 links.json 后 reload）处失效。
   - `DiscordManager.getOrCreateWebhook` 每条 webhook 消息都做阻塞 `retrieveWebhooks().complete()`
     → 按频道缓存句柄；遇到 `UNKNOWN_WEBHOOK` 时精确失效并**只重试一次**，其它错误不重试。
   - `DiscordEventHandler.onCommandAutoCompleteInteraction` 原来阻塞单线程 JDA 事件池最长 5 秒
     （Discord 补全截止是 3 秒，期间所有 Discord 事件停摆）→ 权限快路径留在事件线程，
     候选计算移到 `DMCC-Autocomplete` 执行器。
4. **`BotPresenceManager` 真防抖**（500ms 尾随防抖 + 30 秒周期）并修掉
   "两个开关都关时提前 return、旧任务不会被取消、会按旧配置永久运行"的 bug。
5. **`ConsoleLogTailer.pendingLines` 加上限**（10000 行，超出丢最旧，每次溢出只告警一次），
   并修掉"flush 在 `isConnected()` 检查之后移除行、期间断连会静默丢整批"——现在先组装、再判连接、
   只移除已成功交接的行，发送抛异常则按原顺序放回。
6. **`LinkedAccountManager.save()` 异步 + 原子**：内存 map 仍**同步**更新（读语义不变），
   磁盘写入改为专用单线程执行器 + 同目录临时文件后替换（`ATOMIC_MOVE` → 普通 move → 原地复制兜底，
   保留原文件权限，临时文件必定清理）；`shutdown()` 会先把排队写入刷完。
7. **`MinecraftEventHandler` 两处**
   - 服务器命令执行桥的 `CompletableFuture.runAsync` + 50×100ms `Thread.sleep` 轮询
     （占用 ForkJoinPool 线程最多 5 秒）→ **虚拟线程** + 抽出 `COMMAND_OUTPUT_POLL_*` 常量。
   - `buildInfoResponse` 原本**每次 info 请求**都调用 `StatsCommand.countStatResultEntries`，
     而后者会执行 vanilla `PlayerList.saveAll()`（**必须在主线程**）并解析每个玩家的 stats 文件；
     而 info 请求每 10 秒就来一次且跑在 Netty IO 线程上。现在改为
     "启动时在主线程算一次 + 玩家加入/退出时标记失效 + 请求侧在服务端线程惰性重算 + 5 分钟兜底 TTL"。
     微小时序差异：新玩家首次加入后，`players_ever_joined` 最多滞后约 10 秒。

### 三、本轮发现并修掉的两个"自己挖的坑"（值得一提）

1. **`LoggerImpl` 的日志文件名格式**：把 `SimpleDateFormat` 换成 `DateTimeFormatter` 时，
   文件名用了 `yyyyMMdd_HHmmss` 但值是 `LocalTime.now()` —— 纯时间没有"年"字段，
   第一次写日志会抛 `UnsupportedTemporalTypeException`，异常被吞、`fileWriterInitialized` 却已被置为
   true，于是**整轮运行再也不会创建日志文件**（控制台还有输出，很难发现）。
   改为 `LocalDateTime.now()`；并用临时探针测试验证真机上确实生成了
   `logs/DMCC_20260915_205036.log`、内容为 `[20:50:36] [Test worker/INFO]: …`（测试已删除）。
2. **差点删掉 Shadow 重定位的"防护写法"**：`LoggerImpl` 里的
   `"dmcc_dep.org.slf4j.Logger".replace("dmcc_dep.", "")` 看起来像无意义的死代码，
   实际是**故意写成"已被重定位"的样子**——`core/build.gradle` 里有
   `relocate "org.slf4j", "dmcc_dep.org.slf4j"`，写成裸的 `"org.slf4j.Logger"` 会被 Shadow
   改写成 `dmcc_dep.org.slf4j.Logger`，于是 `Class.forName` 加载到 **DMCC 自带的、被重定位的 SLF4J**，
   日志会绕回这个类自己（正是"类初始化递归"的成因）。已改回原写法并加注释禁止再次"清理"，
   并**实测**发布 JAR 中 `LoggerImpl.class` 的常量池里只有 `dmcc_dep.org.slf4j.Logger{,Factory}`
   与 `dmcc_dep.`（无双重前缀），因此运行期 `replace` 后拿到的正是 Minecraft 自己的 `org.slf4j.*`。

### 验证

- `./gradlew clean build :core:test --warning-mode all` → **BUILD SUCCESSFUL（24s）**，无警告、无弃用提示。
- 产物：根 `build/` **只有** `Discord-MC-Chat-3.0.0-beta.2.jar`（13,246,449 字节、6875 条目、**0 重复**），
  两个加载器元数据、`dmcc.mixins.json`、三份配置模板、`custom_messages/{en_us,zh_cn}.yml`、
  `network/protocol/PacketCodec.class` 全在，`Main-Class` 仍为 `StandaloneDMCC`。
- **语言文件一致性**：新增 4 个键（`discord.command.autocomplete_failed`、
  `client.console_log_tailer.pending_lines_dropped`、`client.console_log_tailer.flush_failed`、
  `server.network.invalid_excluded_command_regex`）**中英同步**；两个文件真实键集一致
  （脚本报出的 4 处"仅英文有"经复核是 `/dmcc info` 模板块标量里的 `Version:`/`Mode:`/`Uptime:` 文案，
  不是键，属提取脚本误报——与第 1 轮的结论相同）。
- **临时测试全部删除**，现在只剩 `SmokeTest`（第 3 轮期间两个并行工作流各自写过的验证测试
  ——并发/性能 11 项、JDA 侧 5 项，以及我自己的日志文件探针——均已删除，只留下上文的结论）。

### 行数变化（重要：本轮是**增行**的）

- 相对**第 2 轮结束时**：**净 +1257 行**（`DiscordManager` +143、`MinecraftMessageParser` +138、
  `NetworkManager` +114、`LinkedAccountManager` +113、`CommandManager` +91、`ConsoleLogTailer` +57、
  `DiscordEventHandler` +55、`BotPresenceManager` +48 等）。
- 相对 HEAD（即第 2 + 第 3 轮合计）：`added=3845 deleted=4682` → **净 −837 行**。
- 增行的原因：本轮是**正确性/性能轮**而不是瘦身轮——新增的都是真实机制
  （每请求关联的 info 轮次、虚拟线程执行器 + 公平读写锁、提及目录 TTL 缓存、webhook 句柄缓存与
  精确重试、断连不丢批次的 flush、异步原子写 links.json、按需重算的统计指标），
  且每个新方法都带完整 javadoc。原计划里 R3 的"−600 行"预期未达成，属主动取舍：
  用可读的代码换掉了会丢数据/会阻塞/会跨线程踩状态的写法。

### 与计划的偏差（及理由）

1. **`ConfigManager` 未改成"加载时解析成类型化快照"**：那需要重写三个模块里所有配置读取点，
   收益是省一次 `JsonNode.path()` 遍历（亚微秒级），而风险是动到用户明确要求保留的校验行为
   （缺失/未知/类型/版本/未修改键）。本轮改为修掉它真正的三个缺陷（见上），并把这个取舍记录在案。
2. **`MemberCachePolicy.ALL` 未改**：JDA 的成员缓存策略会直接影响 `getAllMembers()` 的返回，
   改成惰性策略会让"按名字提及某个未缓存成员"失效 —— **那是用户可见的行为变化**，不属于本轮。
3. **"解析放专用执行器"未做**：把消息解析整体丢给执行器会破坏**消息顺序**（乱序聊天是用户可见的），
   因此本轮只把其中真正阻塞的部分（提及目录的 JDA 调用）改为缓存，其余保持同步。
4. **`MinecraftEventHandler` 的轮询结构本身保留**：只把承载它的线程从 ForkJoinPool 换成虚拟线程、
   把魔法数抽成常量；改成事件驱动需要动 RCON 输出采集链路，收益不足以承担风险。
5. **`Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES` 仍是全局 `AtomicBoolean`**：
   并入平台接口会扩大 `PlatformHost` 的接口面，而它只影响 DMCC Client 自己，暂不动。

### 人工测试清单（本轮理论上零感知，重点是"没坏"）

1. **双向聊天**：Discord ↔ MC 各发几条（含中文、表情、@提及、链接），确认显示与第 2 轮完全一致。
2. **多子服并发**：开 2 个以上子服，同时在多个子服里刷消息，确认**没有串消息/丢消息**。
3. **命令并发**：两个不同用户同时执行 `/dmcc console at:all_online_clients …`，
   确认各自的输出仍然成组、不互相混入（这是本轮把命令执行器改成虚拟线程后的**预期变化**：
   两个并发命令的输出行可能交错，但单个命令的输出内容不变）。
4. **`/info` 与频道看板**：确认每个子服的数据齐全、`players_ever_joined` 数值正确
   （新玩家首次加入后最多 10 秒内更新）；反复刷新不应出现"少一个子服"。
5. **`/log` 大文件**：再取一次 >1 MiB 的日志，确认完整送达且连接不断。
6. **控制台转发**：长时间挂机后确认转发连续、没有整批丢失；断连重连后确认能续上。
7. **`/dmcc reload`**：反复 reload 几次，确认没有"未知命令"窗口、没有异常堆栈。
8. **账户绑定**：`/link` → 解绑 → 再绑定，确认立即可用（提及缓存会被同步失效）；
   另外确认 Discord 里改昵称/角色后，最多 60 秒内提及显示会跟上（这是提及目录的 TTL）。
9. **Bot 状态**：开关 `discord.bot.enable_status`，确认状态切换正常（新防抖会让首次刷新最多晚 0.5 秒）。
10. **两平台回归**：Fabric 与 NeoForge 各加载一次，`standalone` 跑一次 `java -jar`，
    并确认 `logs/DMCC_<日期>_<时间>.log` **正常生成**（这一条专门覆盖本轮修掉的那个日志文件名 bug）。