# 移植进度 · Ping For It → MC 26.3

> 每完成一个阶段更新本文件。施工方案见 [PORTING-PLAN.md](PORTING-PLAN.md)，摘要见 [../AGENTS.md](../AGENTS.md) §11。
> **最后更新：2026-10-28（M2 阶段）**

## 阶段总览

| 阶段 | 内容 | 状态 | 完成时间 | 回滚点 |
|---|---|---|---|---|
| M0 | 基线冻结 | ✅ 完成 | 2026-10-28 | tag `pre-port` |
| M1 | 构建骨架（Fabric 单端） | ✅ **完成**（构建配置与产物命名均已修复；真实构建验证随 M3 一并通过） | 2026-10-28 | 不单独打 tag（并入 `port-M3`） |
| M2 | 源码导入与源集拆分 | ✅ 完成（源码提交 `c48c640`） | 2026-10-28 | tag `port-M2` → `8135ea0`（已打） |
| M3 | 编译期 API 迁移 + 渲染最小编译修复 | ✅ **完成**：`gradlew clean build` 通过，产物 `Ping-For-It-1.0.0-fabric-26.3.jar`（615 KB / 345 class / major 69） | 2026-10-29 | tag `port-M3` → `c09e808` |
| M4 | MVP 闭环（无描边） | ✅ **完成**：冒烟 22/22 配对、HUD 0 失败、正常退出；tag `port-M4`（annotated `5cb059c` → `a6cef45`） | 2026-10-29 | tag `port-M4` |
| M5 | 服务端权威完善 | ⬜ 待办 | — | — |
| M6 | 轮盘与 7 类型 | ✅ 完成（评审 `08-m6-review.md`） | 2026-10-29 | — |
| M6.5 | 标记 HUD 上游对齐 | ✅ 完成（`d6015d7`；原 tag `port-M6.5` 随 `.git` 丢失，语义保留在该提交说明里） | 2026-10-29 | ~~tag `port-M6.5`（`78c944a`）~~（已丢失，见 §事故恢复） |
| M7 | 渲染重建 | ⬜ 待办 | — | — |
| M8 | 配置 / 命令 / UI | ⬜ 待办（含**资源重载 API 迁移**，消除 deprecation note） | — | — |
| M9 | 可选集成 | ⬜ 待办 | — | — |
| M10 | 验证与发布 | ⬜ 待办（**含合规：LICENSE 换 MIT + THIRD-PARTY-NOTICES**） | — | — |
| 事故恢复 | 目录重建 + 重新验证 | ✅ 完成 | 2026-10-30 | tag `restore-2026-10-29` |

> **合规待办（M10 处理，非 M3 缺陷）**：已实测 jar 内 `LICENSE_pingforit` 内容是工程自带的 **CC0 1.0** 模板，而上游 Ping For It 为 **MIT** → M10 需替换为上游 MIT 全文，并补 `THIRD-PARTY-NOTICES`（上游仓库已有 `THIRD-PARTY-NOTICES.md` 可作底本）。

---

## M0 · 基线冻结（2026-10-28）✅

**目标**：固定回滚点，确认工作区干净。

**做法**
- 确认 `git status` 干净（0 变更）
- 打 tag `pre-port`（指向 `06b71dc`，含分析文档与移植方案）

**验收**
- `git tag -l` 可见 `pre-port`
- 当时 HEAD：`06b71dc docs: add porting plan, feature reference and phase decisions`

**备注**：`Mod_Project/`（上游源码 + 分析报告）已被 .gitignore 排除，仅本地保留。

---

## M1 · 构建骨架（2026-10-28）✅

**目标**：把工程调整成「Fabric 单端 + 固定版本矩阵」，且样例代码仍能构建。

**改动**（提交 `2f8fd0a build: M1 skeleton - pin Loom 1.19.3, raise heap, artifact naming`）

| 文件 | 改动 | 原因 |
|---|---|---|
| `gradle.properties` | `loom_version` 1.19-SNAPSHOT → **1.19.3** | 钉死版本，避免 SNAPSHOT 漂移 |
| `gradle.properties` | `org.gradle.jvmargs` -Xmx1G → **-Xmx3G** | 首次全量 remap 内存不足风险 |
| `build.gradle` | 新增 `base { archivesName = "Ping-For-It" }`（`:6-8`） | 决策 D4 产物命名 |
| `build.gradle` | ① `4077eeb` 曾用 `tasks.matching { remapJar }.configureEach`（**无效，已删**）→ ② `5003f48` 改为 `jar { archiveClassifier = "fabric-26.3" }`（`:74-80`） | 期望产出 `Ping-For-It-1.0.0-fabric-26.3.jar`（no-remap 分支真正产出工件的任务就是 `jar`） |

**保持不变（本阶段有意不动）**
- `splitEnvironmentSourceSets()` + 样例代码（isharmla.mod）
- `generateModMetadata` 模板生成链路
- mod id 仍是 `ping-for-it`、包名仍是 `isharmla.mod`
  —— 决策 D2（id → `pingforit`）与 D3（包名 `nx.pingwheel`）**放到 M2 与源码导入一起做**，避免改两次。

### 验证结果（2026-10-28 19:xx）

| 项 | 结果 | 依据 |
|---|---|---|
| IDEA Gradle 同步 | ✅ 成功（IDE 侧确认，仓库内无直接证据） | Lead 报；`build.gradle:83-88` 已改为 lazy 配置并提交 `4077eeb` |
| 产物命名配置 | ✅ **已修复（`5003f48`）** | 3号 复核确认：插件 id 是 `net.fabricmc.fabric-loom`（**no-remap** 分支），`remapJar` 根本不会被创建，原 `tasks.matching{...}` 是静默 no-op（`docs/reviews/03-m1-build-review.md:16-23,49`）；`5003f48` 已删掉该段并把 classifier 放到 `jar` 任务（`build.gradle:74-80`），方案同 3号 建议的 A |
| 产物命名 `Ping-For-It-1.0.0-fabric-26.3.jar` | 🟡 **配置已就位，产物待验证** | `build.gradle:7` `archivesName` + `:80` `archiveClassifier` 已齐；`build/libs` 现有仍是修复前的 `ping-for-it-1.0.0.jar` / `-sources.jar`，尚未重新构建 |
| 一次完整 `clean build` | ⏳ **与 M2 合并执行** | 沙箱内 Gradle 不可用（wrapper 锁 + 子进程管道限制）；M2 导入完成后一并跑一次 |

**结论**：M1 的构建配置与产物命名**均已在代码层面修复**（`5003f48`：classifier 移到 `jar`，删除静默失效的 `tasks.matching{remapJar}`）。唯一未完成的是「跑一次真实构建看产物名」，该验证**与 M2 合并执行**；M1 不单独打 tag。

**待执行验证（终端）**
```
set JAVA_HOME=C:\Program Files\Java\jdk-25
gradlew -g D:\Gradle clean build
dir build\libs
```

**打 tag 条件**：`build/libs` 出现目标名产物且 jar 内 `fabric.mod.json` 的 `version` = `1.0.0`，才可打 `port-M1`。

---

## M2 · 源码导入与源集拆分（2026-10-28）✅

**状态**：✅ **完成**（2号 施工，提交 `c48c640 feat(M2): import Ping For It sources, split main/client source sets`）。
**独立复核**：3号 `docs/reviews/04-m2-structure-review.md` —— 7 个检查面 **6 项通过**；发现 **S1（严重，已修）** 与 **S2（M3 前置，已转 M3）**。

**范围**

| # | 工作项 | 说明 |
|---|---|---|
| 1 | 源码导入 | 上游 **298 个 Java**（拆分后 **main 164 / client 134**）；加 M2 新增 4 个类（`ProtocolLimits`、`IClientPlatformContextService`、`PlatformClientContextServiceImpl`、`ClientTeamContextHelper`）后为 **main 165 / client 137 = 302**（实测，`src/main/java`+`src/client/java`） |
| 2 | 资源导入 | **19 个资源文件**（main 16 + client 3）：8 个 lang、`sounds.json`、`ping.ogg`、`arrow.png`、`ping.png`、`icon.png`、`pingforit.mixins.json` 等 |
| 3 | 源集拆分 | 按 `PORTING-PLAN.md:56-60`：客户端子树 116 文件 + 3 个放错源集的类（ClientConfig / ClientCommandBuilder / Raycast）进 `src/client`；`TeamContextHandler` 按方法切；`IPlatformContextService` 拆 client 子接口 |
| 4 | 协议独立 | 抽出 `network/ProtocolLimits.MAX_CHANNEL_LENGTH`，解开 3 个网络包对 `ClientConfig` 的依赖 |
| 5 | Fabric 接入 | 8 个文件改名接入：`FabricMain`→`PingForIt`、`FabricClient`→`PingForItClient`，4 个 `*ServiceImpl` 按源集落位（D5 暂保留 ServiceLoader） |
| 6 | D2 · mod id | `ping-for-it` → **`pingforit`**（元数据、mixin 配置名、assets 命名空间、通道命名空间同步） |
| 7 | D3 · 包名 | **保留 `nx.pingwheel`**（298 个文件全在此包下，改包是纯噪声，末段再统一） |

**本阶段 8 项自检**

| # | 自检项 | 通过标准 |
|---|---|---|
| 1 | 导入完整性 | Java 合计 298；main / client 分别为 161 / 129，无遗漏、无重复 |
| 2 | 客户端归属 | 客户端子树 116 文件 + 3 个放错源集的类（ClientConfig / ClientCommandBuilder / Raycast）全部在 `src/client/java` |
| 3 | TeamContextHandler | `getSelfContext` 在 client、其余方法留 main，双方均无悬空引用 |
| 4 | 协议解耦 | `ProtocolLimits.MAX_CHANNEL_LENGTH` 存在，3 个网络包不再 import `ClientConfig` |
| 5 | 平台接口拆分 | `IPlatformContextService` 的 KeyMapping 方法移入 client 子接口，main 侧无 `KeyMapping` 引用 |
| 6 | Fabric 接入 | `PingForIt` / `PingForItClient` 与 4 个 ServiceImpl 落位正确，`META-INF/services` 四件套齐全 |
| 7 | 身份一致（D2） | 模组 id / mixins 文件名 / assets 目录 / 通道命名空间统一为 `pingforit`，`grep -r "ping-for-it" src` 为 0 |
| 8 | grep 门禁 | `src/main` 下 `grep -rn "net\.minecraft\.client\|com\.mojang\.blaze3d"` 命中 **0** |

**独立复核结果（3号，7 个检查面 / 6 通过 + 1 严重已修）**

| # | 检查面 | 结果 |
|---|---|---|
| 1 | 源集正确性（main 内 client/blaze3d 引用、跨源集 FQN） | ✅ 0 命中 |
| 2 | 拆分三特例（TeamContextHandler / 三整类 / ProtocolLimits） | ✅ 全部正确 |
| 3 | 平台层（接口拆分 + 5 个 services 一一对应） | ✅ 通过（建议 N1） |
| 4 | 元数据（id / 4 entrypoint / mixin package / icon） | ⚠️ 发现 **S1** → 已修复（见下） |
| 5 | 资源（12 个 assets 文件 SHA-256 与上游逐一相同 + icon） | ✅ 逐字节一致 |
| 6 | 残留与重复（`isharmla`/`ping-for-it`/同 FQN/旧样例类） | ✅ 0；`PingForIt` 31 处为上游自身命名 |
| 7 | build.gradle（`loom.mods` 键 / 无 datagen / classifier 在 `jar` / 无 remapJar） | ✅ 通过 |

**S1（严重，已修复）**：`templates/fabric.mod.json` 原以**字符串**声明 mixins（`"mixins": ["pingforit.mixins.json"]`），而该文件位于 `src/client/resources`；Fabric Loader 对字符串形式一律按 **universal** 处理（loader 0.20.1 的 `V1ModMetadataParser`：*"All mixin configs specified via string are assumed to be universal"*），dev `runServer` 的 classpath 无该资源 → `FabricMixinBootstrap` 抛 `RuntimeException`，**启动即失败**（生产 jar 不受影响，Loom 会合并 client 资源）。
→ **已修**：改为对象形式 `{ "config": "pingforit.mixins.json", "environment": "client" }`（`templates/fabric.mod.json:35-40`，实测已生效）。

**S2（转 M3，第一批编译错误来源）**：`build.gradle` 尚未声明任何第三方 `compileOnly` 依赖，但导入的源码仍在引用：
`lombok`（8 文件 / 16 处，D7 说移除但 M2 未删）、`com.terraformersmc.modmenu`（1）、`de.maxhenkel.voicechat`（1）、`com.seibel.distanthorizons`（1）、`dev.ftb.mods.ftbteams`（1）、`dev.ryanhcode.sable`（1）。
→ M3 开头一次性补齐 `compileOnly`，或按 D9 对暂不移植的集成明确跳过；**这是 M3 的第一批编译错误**，不是 M2 的结构问题。

**顺带修正**：`settings.gradle:13` `rootProject.name` 已改为 `pingforit`（原 `ping-for-it`，对应 04 报告建议 N3）。

**预期：本阶段结束时工程编不过属正常（已验证）**

- 导入的是 1.21.1 源码，而工程是 26.3；`ResourceLocation → Identifier`、`FastColor → ARGB`、`RenderType` 换包等 **API 迁移安排在 M3**（`PORTING-PLAN.md:65-74`）。
- M2 的验收**不是「能编译」**，而是「**文件归属正确 + 8 项自检全过 + grep 门禁为 0**」，这三点已全部达成；编译错误清单本身就是 M3 的输入。

**回滚点**：tag **`port-M2` → `8135ea0`**（M2 进度文档提交，已存在；其父提交 `c48c640` 为 M2 源码提交）。回退顺序：`8135ea0`（M2 完成态）→ `pre-port`（移植前）。

---

## M3 · 编译期 API 迁移 + 渲染最小编译修复（2026-10-28 → 10-29 构建通过）✅

**状态**：✅ **完成**。本地 javac 校验：`main` **0 error / 263 class**、`client` **0 error / 82 class**；随后用户终端 `gradlew clean build` **通过**（详见文末「构建验收」）。
**tag**：`port-M3` → `c09e808`。

M3 拆成两轮，避免「渲染层大改」把机械改名拖成长期红。

### M3a · 去阻塞 + 机械改名（提交 `bed57c3`）✅

| # | 工作项 | 内容 | 依据 |
|---|---|---|---|
| 1 | 移除 Lombok | 8 个文件手写 getter/setter（D7）；删除 `annotationProcessor` 依赖意图 | `PORTING-PLAN.md:73`；04 报告 S2 |
| 2 | 补 annotations | `compileOnly "org.jetbrains:annotations:<ver>"`，覆盖 16 个引用 `@NotNull/@Nullable` 的文件 | 04 报告 S2 |
| 3 | 桩化可选集成 | DH / VoiceChat / FTB Teams / Sable 的源码保留但**隔离成桩**（不参与编译或退化为空实现）；**ModMenu 移到 deferred/**，不参与 M3 编译 | 04 报告 S2；D9 |
| 4 | 机械改名 | `ResourceLocation → Identifier`（35 文件）、`FastColor → ARGB`（2）、`LightTexture → Lightmap`（2）、`RenderType` 换包（4）、`PayloadTypeRegistry.playC2S/playS2C → serverboundPlay/clientboundPlay`（1）、`KeyBindingHelper → KeyMappingHelper`（1）、`Minecraft.setScreen → setScreenAndShow`（1）、`ClipContext` 换包（2） | `PORTING-PLAN.md:65-74`、`05-api-delta.md` |

### M3b · 渲染层最小编译修复 + 子系统搬迁（提交 `d8d610f`，121 文件）✅

只做「能编译」所需的最小修复，**不重建渲染管线**；无法编译的渲染/UI 子系统整体搬到 `deferred/`（保留原目录结构，便于 M7 原样取回）。

**搬迁清单（`deferred/` 共 92 个 java）**

| 目录 | 文件数 | 内容 |
|---|---|---|
| `deferred/client/outline/` | 66 | 方块/实体描边全部实现（26.3 删除了相关渲染原语） |
| `deferred/render/` | 10 | 世界内 HUD/箭头/位置标记渲染 |
| `deferred/screen/` | 8 | `SettingsScreen` 及布局/导航模型 |
| `deferred/mixin/` | 5 | 依赖上述渲染/UI 的 mixin |
| `deferred/fabric/integration/` | 1 | Fabric 侧适配器 |
| `deferred/resource/` | 1 | `ResourceReloadListener` |
| `deferred/modmenu/` | 1 | `ModMenuIntegration`（M3a 已移入） |

> 本次 M3b 为 **86 个重命名 + 5 个新增门面/占位类**（`BlockDisplayPolicy`、`BlockDisplayWhitelist`、`ExternalBlockOutlineTransform`、`WorldRenderContext`、`SettingsScreen` 的 deferred 版本）；M3a 已移入 `ModMenuIntegration` 1 个，合计 92。源码集现状：`src/main/java` **164**、`src/client/java` **51**。

**新增本地校验工具**（4号 负责维护）

- `tools/compile-check.sh` / `tools/compile-check.ps1`：不启动 Gradle，直接用 `javac` 分别校验 main / client 两个源集，输出 error 数与 class 数。
- 用途：沙箱内快速回归；真实产物与 remap/打包仍须 `gradlew build`。

**顺带修复 S1（严重）**：`ClientConfig` 在移除 Lombok 时丢失 `hashCode()`，导致按值查找/缓存失效、配置保存静默不生效 → 已手写 `equals`/`hashCode`（`src/client/java/nx/pingwheel/common/config/ClientConfig.java`），并已复核。

### 26.3 API 迁移清单（M3 实际改动，供后续查阅）

| 旧 API（1.21.1） | 新 API（26.3） |
|---|---|
| `Minecraft#screen` 字段访问 | `Minecraft#screen()`（方法化） |
| `GameProfile#getName()` | `GameProfile#name()` |
| `ServerPlayer#getLevel()` | `ServerPlayer#level()` |
| `InputConstants` 按键读取 | 26.3 对应按键 API |
| `Component.Serializer` 一类序列化 | `ComponentSerialization` |
| `Team#getColor()` | `Team#getColor()` → `Optional<TeamColor>`，取色用 `.rgb()` |
| `Item#getDescriptionId()` | 26.3 对应方法 |
| `PayloadTypeRegistry.playC2S/playS2C` | `serverboundPlay/clientboundPlay` |
| `ResourceLocation` | `Identifier` |
| `FastColor` / `LightTexture` / `RenderType` / `ClipContext` | 各自的 26.3 新包/新类型 |
| `KeyBindingHelper` | `KeyMappingHelper` |
| `Minecraft.setScreen` | `setScreenAndShow` |

### 收尾与验收

**构建验收（2026-10-29，用户终端执行 `gradlew clean build`）✅**

| 项 | 结果 | 实测证据 |
|---|---|---|
| 构建 | ✅ `BUILD SUCCESSFUL` | — |
| 产物 | `build/libs/Ping-For-It-1.0.0-fabric-26.3.jar`，**615 091 B**（+ `-sources.jar` 345 103 B） | 目录实测 |
| 类 | **345 个 `.class`**，**major 69**（Java 25） | 解包统计 |
| 元数据 | `id=pingforit`、`version=1.0.0` | jar 内 `fabric.mod.json` |
| mixins | 2 项声明，均为对象形式 `{config, environment: client}` | jar 内 `fabric.mod.json` |
| 平台层 | `META-INF/services/` 下 **5 个** SPI 文件 | 解包统计 |
| 资源 | `assets/**` 12 个文件 + `icon.png`；`LICENSE_pingforit` 已打入 | 解包统计 |
| tag | **`port-M3` → `c09e808`** | `git tag -l` |

- ✅ 其余验收：`grep -rn "ResourceLocation" src` 无命中；`grep -rn "import lombok" src` 无命中；`build.gradle` 已无 Lombok 依赖。
- 回滚点：`port-M2`（`8135ea0`）→ 当前 `port-M3`（`c09e808`）。

---

## M4 · MVP 闭环（2026-10-29）✅

**目标**：证明「按键 → 捕获 → C2S → 服务端受理 → S2C 广播 → 客户端 HUD 打点」这条最小链路通。**不做描边**（描边整体在 M7）。

**已实现（提交 `313efb5 feat(M4): minimal HUD marker loop with send/receive logging`，5 文件 +219/−5）**

| 项 | 实现 | 证据 |
|---|---|---|
| HUD 输出 | `HudElementRegistry.addLast(...)` 注册一个 HUD 元素，用 26.3 的 `GuiGraphicsExtractor` 状态提取管线绘制标记点 | `src/client/java/nx/pingwheel/fabric/FabricClient.java:56`、`common/client/hud/MarkerHudRenderer.java:54` |
| 世界→屏幕投影 | 用 `Camera` 的 view-rotation-projection 把世界坐标投到屏幕，离屏/背后剔除 | `MarkerHudRenderer`（100 行） |
| 状态复用 | 直接复用既有 `MarkerOverlayState` / `MarkerView`，未新造 marker 存储 | `CommonClient.onRenderHud(...)`（`:338`） |
| 发送日志 | `LOGGER.info("send ping: requestId=%d kind=%s pingType=%s")` | `client/ClientPingActionDispatcher.java:127` |
| 接收日志 | `LOGGER.info("marker created: markerId=%d kind=%s pingType=%s at=(...)")` | `client/ClientPingRuntime.java:1016` |
| 编译验证 | 本地 javac 两遍 0 error（client 源集 **83** class） | `tools/compile-check.*` |

**冒烟结果（2026-10-29 20:11–20:15，用户 `runClient` 实录）✅**

| 项 | 结果 | 证据（`run/logs/latest.log`） |
|---|---|---|
| 启动 | 20:11:23 起，20:11:34 `Sound engine started` + **12 个 atlas** 创建完成 | `:112`（sound）、`:113-124`（12 atlas） |
| 资源重载 | 正常完成，不再卡加载条（移除 v0 注册后 2 秒内） | 见下「阶段发现」 |
| 进世界 | 20:13:32 集成服务器启动，20:13:34 玩家登录 | `:136`、`:156` 附近 |
| 发送 | **22 条** `send ping:`，`requestId=0..21` | `:177-198` |
| 接收 | **22 条** `marker created:`，`markerId=0..21` | `:233-254` |
| 配对 | requestId ↔ markerId **1:1 全部配对**，全为 `kind=BLOCK pingType=attention` | 上两段逐行比对 |
| HUD | 日志中 **0 条** HUD 失败/`pass failed` 记录（`latest.log` 与 `debug.log` 均无）；标记点可见性由人工冒烟确认 | 全文检索 |
| 退出 | 20:14:59 `Stopping!`，无 crash、无 `crash-reports/` | `:268` |

**tag**：`port-M4`（annotated，`5cb059c` → 提交 **`a6cef45`**，注记为「M4: minimal HUD ping loop verified (22/22 send->created pairs in single-player)」）。

### M4 阶段发现：资源重载 v0 注册会让 26.3 启动卡死（重要）

| 项 | 内容 |
|---|---|
| 现象 | 上一轮 `runClient` 停在加载条，**无异常、无 crash、无超时日志**；不是渲染或 mixin 问题 |
| 根因 | `fabric-resource-loader-v0` 的 `ResourceManagerHelper.get(...).registerReloadListener(new IdentifiableResourceReloadListener(){...})` 在 26.3 下**资源重载永远完不成**（该 API 已 `@Deprecated`，语义与新管线不兼容） |
| 定位手法 | 二分：先摘掉该注册 → 启动 2 秒内完成重载 → 回填即复现 |
| 临时处置 | 提交 `a6cef45` **摘除**该注册，`FabricClient.java:101-106` 留 TODO(M8) 注明原因与替代 API |
| 永久修复 | M8 用 v1 恢复：`ResourceLoader.get(PackType).registerReloadListener(...)` / `SimpleReloadListener<T>`（详见 M8 待办 5） |
| 影响面 | **这是功能阻断级**，不是「消除 deprecation note」这种整洁性问题；已知坑已登记到 `AGENTS.md §7-10` 与 `PORTING-PLAN.md` M8 |

**回滚点**：`port-M3`（`c09e808`）。

## M6.5 · 标记 HUD 上游对齐（2026-10-29）✅

**目标**：把 M4 的「实心方块占位」对齐上游 1.21.1 的标记视觉，并让轮盘回归上游结构；不动世界空间渲染（描边仍留 M7）。

**提交与 tag**

| 项 | 值 |
|---|---|
| 主提交 | `d6015d7 feat(M6.5): port upstream marker HUD geometry, labels and edge arrow` |
| 文档提交 | `5884277 docs: refresh line references in 11-m6.5-review.md after the D1/D2 fixes` |
| tag | **`port-M6.5`**（annotated `78c944a`） |
| 新增文件 | `src/client/java/nx/pingwheel/common/client/hud/MarkerLabelRenderer.java`（192 行）<br>`src/main/java/nx/pingwheel/common/interaction/state/WheelSelectionDiagnostics.java`（23 行） |

**交付项**

| # | 交付 | 上游依据 | 当前实现 |
|---|---|---|---|
| A | 标记几何 | 5×5 方块绕 Z 轴 45° 旋转成菱形（`DrawContext.java:76-82`） | `MarkerHudRenderer.java:87-92`（`ICON_SIZE=5` + pose 旋转 + 居中平移） |
| B | 物品图标 | `renderItem(stack,-8,-8)` 16×16（`DrawContext.java:72-74`） | `MarkerHudRenderer` 用 `item(...)` 16×16 **并补 `itemDecorations(...)`**（数量/耐久条，`GuiGraphicsExtractor:914-926`） |
| C | 文本层 | 距离 `-1.5`、目标名 `-2.75`、玩家名 `1.75`、8×8 头颅、黑色半透明底（`PingLocationRenderer.java:40-57`、`DrawContext.java:33-62`） | `MarkerLabelRenderer.java:62-71`（偏移常量）、`:167-189`（底块 + 文本 + `PlayerFaceExtractor` 头颅） |
| D | 屏外/背后箭头 | safe-zone 内缩 + 边相交 + 0.25× 缩放（`DirectionIndicatorRenderer.java:18-83`） | `MarkerHudRenderer.java:102-113`（64×64 `arrow.png` → 10px 四边形 → **最终 2.5px**，与上游一致） |
| E | 层级 + 轮盘回归 | z 阶梯 `-views*16` + 逐条 `+16`（`OverlayRenderer.java:29-45`）；轮盘图标 6×6 染色 `ping.png`（`WheelOverlayRenderer.java:73,234-299`） | 用**绘制顺序**等价 z 阶梯（`MarkerHudRenderer.java:64-71`，26.3 `fill` 无 z 参数）；轮盘删除自加楔形填充、图标改回 6×6（`WheelHudRenderer.java:89,466`） |

**两轮审查结论**

- 第一轮（`docs/reviews/11-m6.5-review.md` 初版）：D1 = 菱形尺寸/旋转与上游一致 ✅；D2 = 文本三行偏移与底块一致 ✅；指出 D3 物品图标缺 `itemDecorations`、D4 箭头缩放推导需复核。
- 第二轮（`5884277` 后刷新行号）：D3/D4 已修；补充 `wheel select` 诊断字段 `scale=/mouse=/sector=` 与命中测试同源（`getScaledXPos`，`WheelHudRenderer.java:269-272`）。
- 尺寸专项：`docs/reviews/10-m6-marker-size-verify.md` 用 d=1/4/10/64 的推导表确认「近大远小、d≳10 后恒定」是上游行为，不是 bug。

**已知微差（登记，不在 M6.5 修）**

| # | 微差 | 归属 |
|---|---|---|
| D6 | α 通道 float vs double 的极小舍入差，视觉不可见 | 不修（记录在 11 号评审） |
| D7 | 自定义资源包贴图分支（`hasCustomTexture()` → 12px `ping.png`）当前只保留判定与注释，未真正走贴图路径 | **M8**（随资源重载 v1 一起做） |
| D8 | 世界空间描边 / 实体发光 / 虚拟方块展示 | **M7** |

**回滚点**：`port-M4`（`5cb059c`）；当前 `port-M6.5`（`78c944a`）。

---
## M8 · 配置 / 命令 / UI（未开工，待办登记）⬜

> M5、M7 尚未开工，故不建小节（M6、M6.5 见上）；本小节只登记 M8 已确定的待办，避免遗漏。

| # | 待办 | 状态 | 说明 |
|---|---|---|---|
| 1 | 双配置文件 + 版本化迁移 | ⬜ | 客户端 `pingforit.json` / 服务端 `pingforit.server.json`，含 `pingforit-version` 迁移与损坏恢复 |
| 2 | 服务端 5 个远程可改字段 | ⬜ | `defaultChannelMode` / `playerTrackingEnabled` / `msToRegenerate` / `rateLimit` / `syncDuration`（`pingDistance` 仅 JSON） |
| 3 | `/pingforit` 三个子命令 | ⬜ | `help` / `config` / `channel`，channel ≤128 字符、按服务器 IP 分桶 |
| 4 | 设置界面 | ⬜ | 现位于 `deferred/screen/`（M3b 搬迁），需按 26.3 的 `GuiGraphicsExtractor` 管线重建 |
| 5 | **消除 deprecation note：`FabricClient` 资源重载 API 迁移** | ⬜ | 见下 |

### 待办 5 明细：资源重载 API 迁移

**现象（两层）**：① 编译输出有一条 deprecation note，指向 `src/client/java/nx/pingwheel/fabric/FabricClient.java`；② **更严重**：该 API 在 26.3 会让资源重载**永远完不成**（卡加载条、无异常），属**功能阻断**——M4 阶段已实证，暂由 `a6cef45` 摘除注册并留 TODO(M8)。

| 项 | 现状 |
|---|---|
| 旧 API（v0，已 `@Deprecated`） | `import` 第 **9-10 行**：`net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener`、`ResourceManagerHelper`；使用第 **110-111 行**：`ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener(){...})` |
| 证据 | fabric-resource-loader-v0 **3.3.27** 的 `ResourceManagerHelper.class` 与 `IdentifiableResourceReloadListener.class` 常量池中均含 `Ljava/lang/Deprecated;`（本地 `javap -v` 核实） |
| 新 API（v1 3.1.0） | `ResourceLoader.get(PackType).registerReloadListener(Identifier, PreparableReloadListener)`；监听器用 `net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener<T>`（封装新 `reload(SharedState, Executor, PreparationBarrier, Executor)`，只需实现 `prepare(SharedState)` / `apply(T, SharedState)`）；注册表/特性旗标用 `ResourceLoader.REGISTRY_LOOKUP_KEY` / `FEATURE_FLAG_SET_KEY`；顺序控制用 `ResourceLoader.addListenerOrdering(...)` |
| v1 类核实 | `fabric-resource-loader-v1-3.1.0` 内确有 `api/resource/v1/ResourceLoader.class` 与 `api/resource/v1/reloader/SimpleReloadListener.class`；`ResourceLoader` 常量池含 `registerReloadListener` / `addListenerOrdering` / `REGISTRY_LOOKUP_KEY` / `FEATURE_FLAG_SET_KEY` |
| 迁移注意 | ① 当前注册**已被 `a6cef45` 摘除**（`FabricClient.java:101-106` TODO(M8)），M8 是「用 v1 重新加回」而不是「原地替换」；② `RELOAD_LISTENER_ID` 的贴图/音效预加载在 26.3 管线重写时已 defer（`:117-120` TODO），迁移只保**注册点 + id**，不要顺手恢复预加载逻辑；③ 改完必须实测启动不再卡加载条 |
| 验收 | 编译输出不再出现该 deprecation note；**启动不卡加载条**（`runClient` 能正常进主菜单/世界）；资源重载注册仍生效（进世界→资源包重载不报错） |

---

## 决策执行情况

| # | 决策 | 状态 |
|---|---|---|
| D1 | 只保留 Fabric 端 | ✅ M1 已按单端调整 |
| D2 | mod id → `pingforit` | ✅ **M2 完成**：元数据 id / mixins 文件名 / `assets/pingforit/` / 通道命名空间已统一 |
| D3 | 包名保留 `nx.pingwheel` | ✅ **M2 完成**：302 个类全部按 `nx.pingwheel.*` 落位，无半改 |
| D4 | 产物名 `Ping-For-It-<ver>-fabric-26.3.jar` | ✅ **完成（M3 构建实测）**：`build/libs/Ping-For-It-1.0.0-fabric-26.3.jar`（615 091 B）；classifier 在 `jar` 任务（`5003f48`），no-remap 插件下即最终产物 |
| D5 | 保留 ServiceLoader | ✅ **M2 完成**：5 个 SPI（main 3 + client 2）与 services 文件一一对应 |
| D6 | 不引入 Parchment | ✅ M1 保持 official |
| D7 | 移除 Lombok | ✅ **完成（M3a `bed57c3`）**：8 文件手写 getter/setter，依赖已删；副作用 S1（`ClientConfig` 丢 `hashCode`）已在 M3b 修复 |
| D8 | 测试基座放 M10 | ⬜ M10 |
| D9 | 集成放发布前 | 🟡 **已桩化并迁出**（DH/VoiceChat/FTB/Sable 桩化，ModMenu 与渲染/UI 子系统进 `deferred/`），**M9/M7 恢复** |

---

## 事故记录 · 2026-10-29 23:07 工程目录被清空

### 发生了什么

- **23:07:35**：工程目录内容被外部操作清空（IDEA 日志记录 `Cannot find project config directory`、`not a git repository ... .git`），只剩 `.idea`。删除**绕过回收站**。
- **23:20–23:40**：用 IDEA Local History 回滚 → **失败**。它只对“在 IDE 里打开过”的文件保存内容，工具写入的文件只有元数据，回滚产出 **357 个项目文件为 0 字节**（外加 621 个 `Mod_Project` 空文件）；回滚过程还把工作区**二次清空**（23:38）。
- **结论：IDEA Local History 不能作为恢复手段。**

### 怎么恢复的

| 来源 | 内容 | 保真度 |
|---|---|---|
| `Downloads\Minecraft-Ping-For-It-0.4.2-pfi-beta1.tar.gz`（3.28 MB，回收站另有一份） | 上游 1.21.1 源码 513 java | **逐字节原样** |
| DSH 会话日志（`~/.dsh/sessions/--F-Minecraft_Mod_Project-...--/*/session.v4.jsonl.zstd`，16 个会话 / 33,034 事件 / 1,361 次文件操作，多帧 zstd 解压） | src java 与 docs 共约 100 个文件，含 M6.5 的 `MarkerHudRenderer`/`MarkerLabelRenderer`/`WheelHudRenderer` | **逐字节原文**（有完整 write 的 21 个已验证 20 个一致） |
| `Downloads\ping-for-it-template-26.3.zip` | `gradle-wrapper.jar`、`gradlew`、`gradlew.bat`、`.github/workflows/build.yml` | 原样 |
| 文档与上下文 | `AGENTS.md`、`docs/reviews/09–12`、`TEAM.md` | 全文重写 |

**重建方式**：上游源码全量落位 → 重做 M2/M3 的机械迁移（`ResourceLocation→Identifier` 90 处、`PayloadTypeRegistry.*Play`、`ComponentSerialization.CODEC`、`KeyMapping.Category`、`InputConstants` 等 14 类）→ Sable 集成整体移入 `deferred/` 并留无依赖桩（`Optional.empty()`，语义 = 集成不可用回退 anchor）。

### 丢失内容（不可恢复）

1. **`.git` 全部历史**：`pre-port` / `port-M2` / `port-M3` / `port-M4` / `port-M6.5` 五个 tag 及其 commit 全部消失。新仓库从零建立，里程碑日期与验证结论保留在本文档。
2. `run/logs/*`：M4「22/22 配对」、M6「108 对 / pingType 3/3 一致」、M6.5 会话的**原始日志**没了，结论仍在文档。
3. `run/saves/*`（测试世界）、`run/config/*`（首次启动重新生成）、`run/dsh-logs/*`。
4. `build/` 与 `.gradle/` 产物（可重建）。

### 恢复后的验证

| 项目 | 结果 |
|---|---|
| `tools/compile-check.sh` | **main 0 error / 262 class；client 0 error / 83 class** |
| `gradlew -g D:/Gradle build --offline` | **通过**（用户实测，2026-10-30 00:1x） |
| 结构 | `deferred/` 96（92 原样 + 4 Sable）、`src/main/java` 162、`src/client/java` 55、资源与 tools 齐备 |
| 待补 | 实机冒烟（ping / 掉落物 / 轮盘 / 屏外箭头四场景）、47 个「无原文」文件的逐项复核 |

> **新基线**：`restore: rebuild the project after the 2026-10-29 directory wipe` 为恢复后第一个 commit，后续阶段以它为回滚点。
