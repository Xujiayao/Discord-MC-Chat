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

