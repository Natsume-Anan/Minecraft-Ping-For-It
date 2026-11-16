# Ping For It — 26.3 Forge 迁移方案

> 状态：**F0–F3 已完成**（2026-11-14）；**F4 已完成**（2026-11-16，`204e42e`）。`26.3-Forge` 已从旧的 `407fbc6` 同步到 `26.3` @ `2954fcf`；本地原有的 `9a723c2` 仅重复添加 `.agents-work/`，rebase 时被安全跳过。F0 基线 `:forge:compileJava` 实跑成功（骨架 `NO-SOURCE`）；F1 已接好 common 依赖/资源合并、metadata、121 pack 和 8 条本地 mixin；F2 已添加入口、14 个 channel、6 个 SPI 实现和 6 个 services，`:forge:runServer` 实跑输出 `[PingForIt] Init` 与 `Done (1.937s)`。F3 已加入客户端入口、8 个 S2C handler、按键/reload/HUD/命令/配置界面与声音注册；`:forge:runClient` 进入主菜单，mixin 配置成功加载，outline pipeline 创建成功。F4 观察基线已记录（2026-11-15）：当前 runClient 已进入单人世界并完成 3 次 block ping，但 world-submit 仍是 no-op。**F4 已完成**（2026-11-16，`204e42e`）：Forge-local `LevelRendererSubmitMixin` 注入 `LevelRenderer#submitFeatures` 的 RETURN，取代 no-op；`:forge:compileJava` / `:forge:prepareDevMod` / `:forge:jar` 实跑通过，独立审查 PASS、无 P0/P1；22:13–22:14 的 runClient 已观察到 `bridge active` 与 `submitted=1..8`、无 routing/pass 失败。用户已确认普通方块/门床两半/entity_block 视觉正常，F4 门禁通过。**F5 暂停，进行 Forge/Fabric/NeoForge parity 审查（2026-11-16）**：结论见 §11（P0 = 0、P1 = 2、P2 = 14、P3 = 8），逐项报告在 `.agents-work/parity/parity-analysis.md`。
> 分支：`26.3-Forge`（完成后合并回 `26.3`）
> 目标：恢复 Forge 26.3 的核心 ping/marker/配置/网络/HUD/outline；首轮不追求 Create/Flywheel 等可选集成 parity。
> 参考：`MIGRATION-26.3-NEOFORGE.md`、`AGENTS.md`、`docs/`（产品行为 owner；本文件不替代 docs）

## 0. 目标、范围与不做的事

### 0.1 目标

1. 在 `forge/` 恢复 26.3 入口、网络、6 个 SPI 实现、资源、metadata、本地 mixin 配置。
2. Forge 只承担 loader 适配；共享逻辑留在 `common/`，不复制 common 实现，不改 common 的 loader 无关边界。
3. 首轮覆盖：核心 ping/marker、客户端/服务端配置、网络、HUD、方块/实体 outline、vanilla 门/床/大箱子 presentation。
4. 每个阶段按“分析 → 编写 → 静态审查 → 编译/测试 → 运行/日志审查”推进；审查通过后再提交。
5. 明确区分“编译通过”“静态检查”“Forge 客户端/服务端实跑”“游戏内交互验证”，不混写。

### 0.2 非目标

- 首轮不迁移 Create、Flywheel、WorldAware、Contraption、WaterWheel/Create mixin 等可选集成；它们依赖 26.3 已删除的 common 几何框架，或只有 1.21.1 的 Create/Flywheel。
- 不修改 `docs/`。产品行为、配置语义、协议和渲染边界仍以 docs owner 为准。
- 不把 `common` 改成 Forge 专用；不在 common 引入 `net.minecraftforge`。
- 不恢复旧的 `DistExecutor`、旧 `@Mod.EventBusSubscriber` 自动订阅或 MixinGradle 插件。
- 不在 Forge 首轮静默丢弃 outline；如果 F4 无法通过，必须显式记录限制并重新评审范围。

## 1. F0 基线事实

- `26.3-Forge` 原为 `407fbc6`，是 `26.3` 的祖先；F0 用 rebase 把它同步到 `26.3` @ `2954fcf`。
- 本地 `26.3` / `26.3-NeoForge` 指向 `2954fcf`；`26.3-Forge` 基于 `2954fcf`，F0 只新增本文档。
- F1 前的 `forge/build.gradle` 是骨架：ForgeGradle `7.0.40`、`mappings channel: 'official'`、`minecraft.dependency("net.minecraftforge:forge:26.3-66.1.0")`、client/server run 指向 `runs/skeleton/forge-*`、`test` 禁用；F1 已接入 common、metadata、pack、mixin 与 `runs/forge-*`，见 §3。
- 旧 Forge 源码在 `mod-source-isolated/forge/src`：7 个 main Java（入口 2 个、SPI 实现 4 个、WorldAware adapter 1 个）、`mods.toml`、`pack.mcmeta`、`pingforit.mixins.json`、4 个 services。
- 旧 Forge 只有 4 个 SPI 实现；common 现在有 6 个 SPI，缺少 `ClientNetworkServiceImpl` 与 `PlatformClientContextServiceImpl`。
- common 是 Java 25 + NeoForm 26.3-1；Fabric/NeoForge 都采用“单 jar 合并 common classes/resources”的模型，Forge 应沿用同一模型。
- 本地 26.3 工件：Forge `26.3-66.1.0`、ForgeGradle `7.0.40`、eventbus `7.0.6`、vanilla `mixin 0.8.7`；JDK 25。
- 旧资源值已过期：`pack.mcmeta` 是 1.21.1 的 `pack_format 9` / `forge:*_format 8/9`；Forge 26.3 universal jar 使用 `min_format: 121` / `max_format: 121`。
- 旧 `mods.toml` 把 `loaderVersion` 写成 `[${forge_loader_version},)`（66）。Forge 26.3 自己的 `META-INF/mods.toml` 使用 `loaderVersion="[24,)"`；`loaderVersion` 是语言 loader 版本，不要继续用 Forge artifact 版本 66 代替。Forge 依赖的 `versionRange` 仍可用 `[66,)`。

### 1.1 F0 实跑证据

命令（在 `26.3-Forge` 工作区）：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
$env:GRADLE_USER_HOME = '<workspace>\.agents-work\gradle-home'
.gradlew.bat --no-daemon --offline --configure-on-demand --console=plain :forge:compileJava
```

结果：

- `BUILD SUCCESSFUL in 10s`
- `> Configure project :forge`，`Minecraft Maven is up-to-date`
- `:forge:compileJava NO-SOURCE`（骨架无源文件，符合预期）
- JDK：`25.0.4.1`；Gradle：`9.8.2`；Forge：`26.3-66.1.0`

说明：默认 `%USERPROFILE%\.gradle` 在当前受限运行环境里无法写 wrapper 锁；F0 用工作区内可写的 `GRADLE_USER_HOME` 和 `--configure-on-demand` 避开无关的 Fabric 配置期下载。该 workaround 不是项目构建要求。

### 1.2 F1 实跑证据

- `:forge:compileJava`：`BUILD SUCCESSFUL`；当前仍无 Forge Java 源，任务为 `NO-SOURCE`。common 依赖与编译类路径已解析。
- `:forge:processResources`：`BUILD SUCCESSFUL`；生成 `build/generated/modMetadata/META-INF/mods.toml`，无未展开占位符。
- 生成 metadata：`loaderVersion = "[24,)"`、`versionRange = "[66,)"`、`modId = "pingforit"`、`version = "0.4.2-pfi-beta1"`。
- `pack.mcmeta`：`min_format=121` / `max_format=121`。
- 本地 mixin：8 条，与 common 列表一致，`compatibilityLevel=JAVA_21`；common 的 `pingforit.mixins.json` 已被排除。
- `:forge:jar`：`BUILD SUCCESSFUL`；
  - 产物：`Ping-For-It-0.4.2-pfi-beta1-forge-26.3.jar`
  - 484 entries / 421 classes / 0 duplicate / 0 test class
  - `icon.png`、`assets/pingforit/`、`pack.mcmeta`、`META-INF/mods.toml`、`pingforit.mixins.json` 均在 jar 中
  - `META-INF/MANIFEST.MF` 含 `MixinConfigs: pingforit.mixins.json`
  - `META-INF/services` 为 0 是预期：F2 才补 6 个 SPI 实现

F1 没有验证 Forge 客户端/服务端启动，也没有验证 mixin 在 Java 25 字节码下的实际注入；这些属于 F2/F3。

### 1.3 F2 实跑证据

- 新增 7 个 Forge Java 类：`ForgeMain` + 6 个 SPI 实现。
- 新增 6 个 `META-INF/services` 文件，指向 6 个实现类。
- `:forge:compileJava`：`BUILD SUCCESSFUL`。
- `:forge:runServer`：`[PingForIt] Init`、`Saved config type=ServerConfig`、`Done (1.937s)`；无 mod 加载 ERROR/FATAL。`server.properties` 首次缺失与 Windows 上的 netty kqueue/epoll 探测错误是非致命环境信息，未阻止服务器启动。
- `:forge:jar`：`BUILD SUCCESSFUL`；501 entries / 429 classes / 6 个 service 文件 / 0 duplicate entry。

F2 未启动客户端，未验证 S2C handler、HUD、输入或 mixin 注入；这些属于 F3。

### 1.4 F3 实跑证据

F3 新增：

- `ForgeClient`：8 个 S2C handler、`CommonClient.INSTANCE.onInit()`、reload listener、客户端命令、配置界面；
- `ForgeClientBootstrap`：只在 `FMLEnvironment.dist.isClient()` 时从 `ForgeMain` 构造期加载 `ForgeClient`；
- `ForgeMain` 的声音 `DeferredRegister`；
- HUD 层改挂 `ForgeLayeredDraw.POST_SLEEP_STACK`；
- `prepareDevMod` 生成只用于 dev 的 `META-INF/MANIFEST.MF`，让 Mixin 在目录型 dev mod 中发现配置。

实跑（2026-11-14，JDK 25，Forge 26.3-66.1.0）：

```text
:forge:compileJava  BUILD SUCCESSFUL
:forge:build        BUILD SUCCESSFUL
:forge:runClient    Minecraft* Forge 26.3 主菜单窗口
:forge:runServer    [PingForIt] Init / Loaded config type=ServerConfig / Done (0.215s)
```

F3 的 `:forge:jar` 产物：503 entries / 431 classes / 6 个 services / 0 duplicate，包含 `ForgeClient.class` 与 `ForgeClientBootstrap.class`；生产 manifest 仍含 `MixinConfigs: pingforit.mixins.json`。独立只读审查 PASS（构造期注册、HUD `POST_SLEEP_STACK`、8 个 S2C、服务端隔离、dev manifest 无生产副作用）。

`:forge:runClient` 的关键日志：

```text
[mixin/]: Compatibility level set to JAVA_21
[pingforit/]: [PingForIt] Init
[pingforit/]: [PingForIt] Loaded config type=ServerConfig
[pingforit/]: [PingForIt] Loaded config type=ClientConfig
[pingforit/]: ping outline pipeline registered (depth test disabled, cull off, translucent)
[pingforit/]: block outline pass pipeline ready; common client registers the world-submit callback
```

- `runs/forge-client/options.txt` 出现 5 个 `key_key.pingforit.*` 项，证明按键进入 `Options.keyMappings`；这是 F3 构造期注册生效的直接证据。
- `runs/forge-client/logs/debug.log` 显示 config 只注册一次（`source=SecureJarResource(pingforit)`）；主菜单阶段应用 7 条 mixin，2026-11-15 进世界后第 8 条 `SubmitNodeCollectionMixin` 才在 `SubmitNodeCollection` 加载时应用。debug 日志同时有 `Class version 69 ... JAVA_21 supports class version 65` 警告，但 accessor 已使 outline pipeline 创建成功；F5 必须验证按键桥和 world-submit 是否真正注入。
- `runs/forge-client/logs/latest.log` 无 mod 加载 ERROR/FATAL；Realm 认证失败与 Windows netty 平台探测错误是环境噪声。
- `runServer` 未加载 `ForgeClient`/`CommonClient`，说明 universal jar 的客户端隔离有效。

### 1.5 F4/F5 观察基线（2026-11-15）

本轮只记录当前 runClient 的运行信息和警告，供 F4（outline/world-submit）与 F5（运行时验证）使用；没有改代码。

日志：

- `runs/forge-client/logs/latest.log`：2026-11-15 22:39:58–22:41:08，进入单人世界、完成 3 次 block ping、正常退出。
- `runs/forge-client/logs/debug.log`：同一次运行，含 mixin、14 channel 握手和 ping 状态机细节。
- `runs/forge-client/options.txt`：5 个 `key_key.pingforit.*` 仍在。
- 本次 `runClient` 第 1 行使用默认 assets 路径 `C:/Users/65653/AppData/Roaming/.minecraft/assets` 并成功进世界；assets 路径是环境相关，F5 前确认窗口前台、assets 可读，不要把早期 F3 的 workspace `APPDATA` workaround 当成固定要求。

#### 1.5.1 已观察到的正常路径

| 观察点 | 日志证据 | 结论 |
| --- | --- | --- |
| 客户端入口/配置 | `latest.log:33-35`：Init、ServerConfig、ClientConfig | F3 构造期初始化生效 |
| outline pipeline | `latest.log:36-37`：pipeline registered / ready | accessor mixin 可工作 |
| 集成服务器 | `latest.log:81` 起；`latest.log:100-105` | 单人世界启动、玩家加入、channel update |
| 网络握手 | `debug.log:614-654`：14 个 channel 全部 ACCEPTED | optional channel 与 S2C 注册可用 |
| ping 输入/服务端权威 | `debug.log:688-697` capture；`latest.log:121-126`、`139-141` | 3 次 block ping 成功发送并创建 marker |
| marker 生命周期 | `debug.log:700-712`、`750-751`、`789` | created/winner/lifetime/disconnect cleanup 正常 |
| 退出 | `latest.log:146-155` | 单人服务器和客户端正常停止 |
| 声音 | 未出现 `Unable to play unknown soundEvent` | 只能说明没有该错误，不能证明听到了声音 |

`options.txt:177-181` 的按键为：ping=`mouse.5`、pass-through=`K`、blacklisted=`left.control`、fluids=`J`、open_settings=`unknown`。`open_settings` 默认未绑定是 common `InputUtils` 的既有设计；三个 toggle 键本次没有被实际按下，F5 仍需验证。

这些结果把 F5 的「核心 ping/marker 路径」推进了一步，但仍是单人集成服务器、block target、无视觉断言的局部证据。

#### 1.5.2 警告与观察项

| 级别 | 日志证据 | 判断 | F4/F5 动作 |
| --- | --- | --- | --- |
| **F4 阻塞** | `latest.log:38`、`debug.log:286`：Forge world-submit outline is unavailable until the F4 LevelRenderer bridge is implemented | 当前 F4 仍是 no-op；没有 `block outline: submitted=` 记录 | 实现 §5 的 `LevelRenderer#submitFeatures` bridge，移除该警告 |
| **高** | `debug.log:164`：`Compatibility level JAVA_21 ... higher than the maximum level supported by this version of mixin (JAVA_13)`；紧接 `:165` 才 `set to JAVA_21` | 启动早期 Mixin 上限提示，随后实际设置 JAVA_21 并继续；不能只引用 `latest.log:12` 而忽略这条 | F4 新 mixin 同样观察；若出现 `Mixin apply failed` 再评估字节码/栈 |
| **高** | `debug.log:198-200`：`Preparing pingforit.mixins.json (8)` / `Prepared 8 mixins`；`229-232`、`280-300`、`673-674`：8 条都有 `Class version 69 ... JAVA_21 supports class version 65` | 8/8 都出现 Mixing，`SubmitNodeCollectionMixin` 在世界加载时应用；但没有 `Mixin apply failed` 不等于注入语义已证明 | F4/F5 验证按键桥、EntityRenderer/BlockEntity/LevelExtractor/SubmitNodeCollection 的实际注入；若失败，评估 Java 21 字节码或更新 mixin 栈 |
| **已知 vanilla bug** | `latest.log:107`、`debug.log:672`：Requested post effect does not exist: minecraft:end_of_frame | 2026-11-16 复核确认是 Mojira **MC-310992**：26.3 在登录世界/重载资源包时请求 `minecraft:end_of_frame`，即使没有资源包定义它；vanilla 与 Forge-patched `GameRenderer.update()` 都会无条件加入。非本 mod、非致命 | 不修；按 vanilla 噪声记录，见 §1.7 |
| **环境** | `debug.log:540`、`563`：sun.misc.Unsafe unavailable / Native access ... io.netty.common | JDK 25 + Netty 的启动回退；集成服务器仍启动 | 不阻塞 F4/F5；若做自动化测试，记录 JVM 参数/环境 |
| **环境** | `latest.log:17-18`：Assets URL ... union ... uses unexpected schema | ForgeGradle dev 资源路径；客户端仍加载资源 | 视觉测试前确认资源包与 assets；不要当 mod 回归 |
| **环境** | `latest.log:44-53`、`69-79`：Realm 认证/JWT/连接失败 | 开发账号无 Realm 会话 | 忽略；不要作为 ERROR 计入 mod |
| **环境** | `latest.log:54`：Forge 66.1.0 vs 66.1.8 | 版本检查提示 | F6/版本决策再评估；F4 不升级 |
| **环境** | `latest.log:83-89`：forge-server.toml 修正 | 新建世界首次配置默认值 | 正常；不是 pingforit 配置错误 |
| **F5 待确认** | `debug.log:750-751`：marker created 后约 9.0 s 才 `lifetime ended`；`pingforit.json:24` `markerDisplayDuration: 0`（跟随服务端），`pingforit.server.json:6` `syncDuration: 7`；日志没有 SyncDurationPolicy 应用记录 | 9 s 与 7 s 的差异无法从当前日志解释 | F5 用秒表复测，并临时记录 SyncDurationPolicy 应用值；确认是策略、宽限还是计时误差 |
| **正常** | `latest.log:108-120`、`135-138`：DynamicGpuDataStorage 扩容 | 首次进世界分配缓冲 | 正常，不当作 leak/回归 |
| **调试噪声** | `debug.log:71-78`、`610`：JarJar metadata / SOMAXCONN | DEBUG 级别，非失败 | 不进入 F4/F5 结论 |
| **历史环境** | 归档 F3 运行曾有 Cannot acquire minimized window | 窗口最小化时截图/渲染可能失败 | F4/F5 视觉测试必须保持窗口前台且未最小化 |

#### 1.5.3 F4 注入点结论

Forge 26.3 的 `LevelRenderer` 实际方法（来自缓存 injected sources）：

```java
private void submitFeatures(LevelRenderState levelRenderState,
                            SubmitNodeCollector submitNodeCollector,
                            boolean renderOutline) {
    PoseStack poseStack = new PoseStack();
    this.submitEntities(poseStack, levelRenderState, submitNodeCollector);
    this.submitBlockEntities(poseStack, levelRenderState, submitNodeCollector);
    ...
}
```

F4 首选注入：目标 `LevelRenderer#submitFeatures(LevelRenderState, SubmitNodeCollector, boolean)` 的 `RETURN`；捕获方法参数 `submitNodeCollector` + 局部变量 `poseStack`（`LocalCapture.CAPTURE_FAILHARD` 或等价 `@Local`）；转发 `BlockOutlinePass.onWorldSubmit(new WorldSubmitContext(poseStack, collector, Minecraft.getInstance().gameRenderer.mainCamera(), 0.0f))`。

为什么不是 HEAD：`submitFeatures` 内先提交 entity/block entity，Fabric 的 `COLLECT_SUBMITS` 也在 `submitFeatures` 的 RETURN；HEAD 会让 `EntityBlockGlowState` 尚不完整。为什么不是 `AddFramePassEvent`/`RenderHighlightEvent`：前者没有 `PoseStack`/`SubmitNodeCollector`，后者只在准星高亮时触发。

配置：新增 Forge-local mixin（建议类放 `nx.pingwheel.common.mixin` 包以匹配本地配置），只加入 Forge 的 `pingforit.mixins.json`，不要加入 common 配置，避免 Fabric/NeoForge 重复提交。失败出口：若 bridge 无法应用，必须保留 no-op 警告并停止 F5 的 outline 门禁，不能把「pipeline ready」当成「outline 已画」。

F4 验收至少要有：1) `block outline: submitted>0` 的 debug 日志；2) 普通方块可见 outline；3) 门/床两半、`entity_block` 至少各一个可见 outline；4) 与 `SubmitNodeCollectionMixin` 的 after-terrain 路由日志/行为一致。

### 1.6 F4 实现记录（2026-11-16，`204e42e`）

实现已提交到 `26.3-Forge`：`feat(forge): bridge 26.3 outline world-submit via LevelRenderer mixin`（3 文件，+115/−7）。

- **注入点**：`forge/src/main/java/nx/pingwheel/common/mixin/LevelRendererSubmitMixin.java`，`@Mixin(LevelRenderer.class)`，`@Inject(method = "submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V", at = @At("RETURN"), require = 0)`。包名必须是 `nx.pingwheel.common.mixin`，只加入 Forge 本地 `pingforit.mixins.json` 的 `client` 段（第 9 条）；common 配置与 Fabric/NeoForge 未动。
- **描述符证据**：`javap -p -c -l` 读 `recompiled.jar` 与 `injected.jar`，`submitFeatures` 均为 private、3 参数、唯一方法、单 RETURN（pc 229），注解描述符逐字符一致；`poseStack` 局部在 slot 4、作用域覆盖到 RETURN。
- **PoseStack 偏离**：§1.5.3/§5 原写“捕获局部 `PoseStack`”，实现改用 `new PoseStack()`。依据：该局部本身在方法头 `new PoseStack()` 创建，RETURN 前 `checkPoseStack` 已断言为空栈，而 `BlockOutlinePass` 只 push/translate/pop、从不读已有矩阵；不依赖 `@Local` 解析（解析失败可能被 `required: true` 升级为致命）。这是对 spike 方案的等价实现，不是契约变化。
- **接线**：`PlatformClientEventServiceImpl.registerWorldSubmitEvent` 只保存 `volatile` 回调；新增 `dispatchWorldSubmit(PoseStack, SubmitNodeCollector)` 在 dispatch 内取 `Minecraft.getInstance().gameRenderer.mainCamera()`、partialTick 传 `0.0f`（与 Fabric 适配器一致），首帧一次性 INFO `block outline: Forge world-submit bridge active (LevelRenderer#submitFeatures RETURN)`，异常用一次性 WARN 隔离。旧 no-op WARN 已删除。
- **构建证据**（2026-11-16，JDK 25 + 工作区 `GRADLE_USER_HOME` + `--configure-on-demand`）：`:forge:compileJava` BUILD SUCCESSFUL（12s）；`:forge:prepareDevMod` 后 `build/devmod/nx/pingwheel/common/mixin/LevelRendererSubmitMixin.class` 存在、devmod 内 9 个 mixin class、`pingforit.mixins.json` 9 条、`META-INF/MANIFEST.MF` 仍为 `MixinConfigs: pingforit.mixins.json`；`:forge:jar` 822518 B / 504 entries / 432 classes / 0 重复条目，新 class 仅 1 次。审查子代理独立复跑（含 `--rerun-tasks`）复核通过：P0/P1 无，P2 三条（日志措辞已修；`require = 0` 静默失效见 §9.10；PoseStack 偏离记录在本节）。
- **运行时证据**（2026-11-16 22:13–22:17，两轮 `:forge:runClient`，单人世界）：`debug.log` 出现 `Preparing pingforit.mixins.json (9)`、`Mixing LevelRendererSubmitMixin ... into net.minecraft.client.renderer.LevelRenderer`、首帧 `block outline: Forge world-submit bridge active (LevelRenderer#submitFeatures RETURN)`；本次会话 32 条 `block outline: submitted=`（1–8），并出现 `entity outline pass active`；无 `pass failed` / `routing mixin inactive` / `bridge failed` / `Mixin apply failed`，`latest.log` 无 mod ERROR/FATAL，客户端正常退出。`Class version 69` 警告对新类仍出现，但 Mixing 与注入行为均正常。
- **视觉确认**（2026-11-16）：用户确认普通方块方框、门/床两半、`entity_block` 原生发光均正常；F4 游戏内验收通过。after-terrain 半透明效果与 F4 后的服务端回归仍归 F5。

F4 游戏内验收（缺一不可，2026-11-16 通过）：日志侧已满足——`Preparing pingforit.mixins.json (9)`、`Mixing net.minecraft.client.renderer.LevelRenderer`、`block outline: Forge world-submit bridge active`、`block outline: submitted=<n>`（n>0）均已出现；视觉侧已由用户确认——普通方块、门/床两半、`entity_block` 均正常。日志 grep：

```powershell
Select-String -Path runs/forge-client/logs/debug.log -Pattern 'Preparing pingforit.mixins.json','Mixing net.minecraft.client.renderer.LevelRenderer','world-submit bridge active','block outline: submitted='
Select-String -Path runs/forge-client/logs/*.log -Pattern 'Forge world-submit outline is unavailable','block outline: pass failed','routing mixin inactive'
```

### 1.7 F4 runClient 警告复核（2026-11-16 22:15–22:17）

同一会话的 `latest.log` 共 6 条 WARN/ERROR，全部归类如下；**pingforit 自身 0 条 WARN/ERROR**，无 unknown sound/texture/model 警告。

| 警告 | 次数 | 归因 | 是否阻塞 F4 |
| --- | --- | --- | --- |
| `Requested post effect does not exist: minecraft:end_of_frame` | 1 | vanilla 26.3 已知 bug **MC-310992**（登录世界/重载资源包触发；Forge-patched `GameRenderer.update()` 也无条件请求） | 否 |
| `oshi ... PerfmonDisabled ... Invalid registry value type ... PerfOS` | 1 | 本机 Windows 性能计数器注册表类型；oshi 硬件探测 | 否 |
| `Assets URL 'union:...forge-26.3-66.1.0.jar%230!/assets/.mcassetsroot' uses unexpected schema` | 2 | ForgeGradle dev 的 assets union；开发环境噪声 | 否 |
| Realm `Failed to fetch Realms feature flags` / `Couldn't connect to realms` | 2 | 开发账号无 Realm 会话 | 否 |
| Mixin `Class version 69 ... JAVA_21 supports class version 65` | 9 条（DEBUG） | 既有 mixin 限制；9/9 都随后 `Mixing ...`，含新增 `LevelRendererSubmitMixin` | 否 |
| `Mixin apply failed` / `InvalidInjectionException` | 0 | — | — |

同一会话的 F4 证据：22:17:02 首帧 `block outline: Forge world-submit bridge active`；`submitted` 峰值 4（2 个 `block` + 2 个 `entity_block` 目标，含 `entity-block glow colour resolved`）；`SubmitNodeCollectionMixin` 在 22:17:02 世界加载时应用；客户端 22:17:29 正常停止。

## 2. 目标架构

```text
common/    Java 25 + NeoForm 26.3-1
           ├─ 共享/服务端逻辑
           ├─ 客户端/HUD/outline/配置
           ├─ 6 个 platform SPI
           └─ assets/pingforit、icon.png、pingforit.mixins.json

forge/     ForgeGradle 7.0.40，Java 25，单一 universal 源集
           ├─ ForgeMain / ForgeClient
           ├─ 6 个 SPI 实现（client/server 分文件，同一 jar）
           ├─ 本地 mods.toml / pingforit.mixins.json / pack.mcmeta
           ├─ 6 个 META-INF/services
           └─ jar 合并 common classes + common resources
```

关键决策：

1. `forge` 用 `implementation(project(':common')) { transitive = false }`，只消费 common 类，避免 NeoForm/测试/可选依赖泄漏。
2. 资源经 `processResources.from(project(':common').sourceSets.main.resources)` 合并一次；排除 common 的 `pingforit.mixins.json`，使用 Forge 本地配置。
3. `jar.from(project(':common').sourceSets.main.output.classesDirs)` 合并 common 类；资源只经 `processResources` 进入，避免重复 entry。
4. Forge 是 universal jar；client-only 类仍进 jar，靠 `Dist` 守卫和 ServiceLoader 按需加载。`ForgeMain` 不得直接引用 `CommonClient` 或 `ForgeClient`。
5. 不把 common 源码并入 Forge 源集；common 仍是独立 Java 25 模块。

## 3. 构建接线

`forge/build.gradle` 的目标内容：

- `base { archivesName = archive_name }`、`version = mod_version`。
- `java { toolchain.languageVersion = JavaLanguageVersion.of(java_version) }`。
- `minecraft { mappings channel: 'official', version: minecraft_version; runs { client { workingDir = rootProject.file('runs/forge-client') } server { workingDir = rootProject.file('runs/forge-server') } } }`。
- `evaluationDependsOn(':common')` 只用于配置期读取 common source set；Forge 仍在独立 `forge/` 模块中，不 source common 源码。
- `repositories { minecraft.mavenizer(it); maven fg.forgeMaven; maven fg.minecraftLibsMaven }`。
- `dependencies { implementation minecraft.dependency("net.minecraftforge:forge:${minecraft_version}-${forge_version}"); implementation(project(':common')) { transitive = false } }`。
- metadata：优先用 `generateModMetadata`（Sync）从 `src/main/templates` 展开 `META-INF/mods.toml`，避免 IntelliJ 的 `MatchingCopyAction` 警告；也可用 `processResources` 的 `filesMatching/expand`，但必须保证 `verifyModIdentity` 能读到最终 `mods.toml`。
- `processResources`：依赖 metadata 生成；`from(project(':common').sourceSets.main.resources) { exclude("${mod_id}.mixins.json") }`。
- `jar`：`archiveClassifier = 'forge-26.3'`；manifest 写 `MixinConfigs: pingforit.mixins.json`；`dependsOn :common:classes`；`from common.classesDirs`；附 `LICENSE`（重命名为 `LICENSE_pingforit`）与 `THIRD-PARTY-NOTICES.md`。
- `test` 首轮保持禁用；Forge 没有 test 源集。需要 Forge 聚焦测试时再单独立项。
- 不引入 `multiloader-loader`；`commonJava/commonResources` 在 26.3 已不存在。

目标产物：

```text
forge/build/libs/Ping-For-It-0.4.2-pfi-beta1-forge-26.3.jar
```

### 3.1 元数据与资源值

`mods.toml` 目标：

```toml
modLoader = "javafml"
loaderVersion = "[24,)"
license = "${license}"
issueTrackerURL = "${link_issues}"

[[mods]]
modId = "${mod_id}"
version = "${version}"
displayName = "${mod_name}"
displayURL = "${link_src}"
logoFile = "icon.png"
authors = "${mod_author}, ${contributors}"
description = '''${description}'''
displayTest = "IGNORE_SERVER_VERSION"

[[dependencies.${mod_id}]]
modId = "forge"
mandatory = true
versionRange = "[66,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.${mod_id}]]
modId = "minecraft"
mandatory = true
versionRange = "${forge_compatible_range}"
ordering = "NONE"
side = "BOTH"
```

注意：

- `loaderVersion="[24,)"` 是从 Forge 26.3 universal jar 的 `META-INF/mods.toml` 核实到的语言 loader 版本；不要写 `[66,)`。
- `versionRange="[66,)"` 是 Forge artifact/依赖版本，不是 `loaderVersion`。
- `pack.mcmeta` 使用：

  ```json
  {
    "pack": {
      "description": "Ping For It resources",
      "max_format": 121,
      "min_format": 121
    }
  }
  ```

- `logoFile` / `displayTest` 保留旧字段，F1 启动时检查 Forge 26.3 是否给出弃用或缺失警告；如果 descriptor 校验改名，按实际校验结果调整，不凭猜测删除。
- 所有 `${...}` 必须由 metadata 生成任务展开；不要把未展开的模板打进 jar。

### 3.2 Mixin

- Forge 26.3 使用 `org.spongepowered:mixin:0.8.7`；已从源码核实其 `MixinEnvironment.CompatibilityLevel` 最高为 `JAVA_21`。
- Forge 本地 `pingforit.mixins.json` 应列 common 26.3 的 8 条 client mixin，`package` 保持 `nx.pingwheel.common.mixin`，不写 refmap。
- 26.3 no-remap 下本地配置不写 refmap；`jar` manifest 写 `MixinConfigs: pingforit.mixins.json`。不要依赖 `org.spongepowered:mixin:0.8.7:processor` 分类器：当前缓存可能没有该 artifact，F1 已将它移除并成功加载。
- **FML dev run 的类路径**：ForgeGradle 7 把项目 classes 和 resources 作为两个 classpath root；common 依赖又作为独立 jar。直接运行会让 ModLauncher 看到重复的 `main`/`pingforit` 模块，或让 common 类落在另一个 classloader。F2 用 `prepareDevMod` 把 Forge + common classes/resources 合并到 `build/devmod`，并在 `runClient`/`runServer` 中用该目录替换项目输出、排除 common jar；这是 dev 运行配置，不影响发布 jar 的 common 合并。
- **dev 目录的 Mixin manifest**：Forge 26.3 的 Mixin 通过容器 manifest 的 `MixinConfigs` 发现配置；`build/devmod` 是目录而不是 jar，直接运行会让 8 条 common client mixin **静默不加载**。F3 在 `prepareDevMod` 的 `doLast` 中只对 dev 目录写入 `META-INF/MANIFEST.MF`，生产 jar 仍由 `jar.manifest` 写 `MixinConfigs`。不要把 dev manifest 复制进 `src/main/resources`。
- **阻塞风险**：项目按 Java 25 编译（class file 69），vanilla mixin 0.8.7 的兼容级别最高只有 `JAVA_21`（class file 65）。F1/F2 的首次 Forge client 启动必须验证 common mixin 是否能被应用；若失败，优先评估把 Forge 模块（以及被 mixin 的 common 类）编译到 Java 21 字节码，或引入 Forge 实际使用的更新 mixin 栈。**不要简单把 `compatibilityLevel` 改成不存在的 `JAVA_25`。**
- common mixin 配置与 Forge 本地配置必须同步；新增 common mixin 时同时更新两处。

## 4. 入口、事件与网络映射

### 4.1 入口

```java
@Mod(MOD_ID)
public final class ForgeMain {
    public ForgeMain(FMLJavaModLoadingContext context) {
        CommonServer.INSTANCE.onInit();
        // register server/C2S channels and handlers here
        if (FMLEnvironment.dist.isClient()) {
            ForgeClientBootstrap.register(context); // synchronously loads ForgeClient
        }
    }
}
```

- 使用构造参数注入的 `FMLJavaModLoadingContext`；不要用已弃用的 `FMLJavaModLoadingContext.get()` / `ModLoadingContext.get()` 作为首选。
- **客户端注册必须在 mod 构造期完成，不能延到 `FMLClientSetupEvent`。** Forge 26.3 的 `AddGuiOverlayLayersEvent`、`RegisterKeyMappingsEvent`、`RegisterClientReloadListenersEvent` 都在 `Minecraft` 构造期、`FMLClientSetupEvent` 之前 fire，且都是 `SelfDestructing`；事后 `addListener` 不报错但永不执行。HUD 层/按键/reload 因此会静默丢失。
- `ForgeClientBootstrap` 只引用 `ForgeClient`，并由 `FMLEnvironment.dist.isClient()` 保护；`ForgeMain` 不直接 import/reference `ForgeClient`、`CommonClient` 或 `SettingsScreen`，避免 dedicated server 加载客户端类。不要用已删除的 `DistExecutor`。

### 4.2 事件映射

| common SPI 回调 | Forge 26.3 映射 |
| --- | --- |
| `registerTickStartEvent` | `TickEvent.ClientTickEvent.Pre.BUS.addListener(event -> callback.run())` |
| `registerJoinServerEvent` | `ClientPlayerNetworkEvent.LoggingIn.BUS` |
| `registerLeaveServerEvent` | `ClientPlayerNetworkEvent.LoggingOut.BUS` |
| `registerRenderFrameEvent` | `TickEvent.RenderTickEvent.Pre.BUS`；F3 验证每帧一次，且无 level 时 common 回调自行 guard |
| `registerHudRenderEvent` | `AddGuiOverlayLayersEvent.BUS` + `event.getLayeredDraw().add(ForgeLayeredDraw.POST_SLEEP_STACK, id, (graphics, delta) -> callback.render(graphics, delta))`；只用 root 会在 F1 隐藏 HUD 时继续绘制 |
| `registerWorldSubmitEvent` | **无等价事件**；见 §5 的 F4 spike |
| `registerKeyMapping` | `RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(keyMapping))` |
| 资源重载 | `RegisterClientReloadListenersEvent.BUS.addListener(event -> event.registerReloadListener(new ResourceReloadListener()))` |
| 客户端命令 | `RegisterClientCommandsEvent.BUS`；用 `event.getDispatcher()` 注册 |
| 配置界面 | 客户端 setup 中 `MinecraftForge.registerConfigScreen(...)`，或显式 `context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, ...)`；必须在 client-only 路径 |
| 玩家登出 | `PlayerEvent.PlayerLoggedOutEvent.BUS`；`event.getEntity()` 强转 `ServerPlayer` |
| 服务端 tick 结束 | `TickEvent.ServerTickEvent.Post.BUS`；`event.server()` |

补充：

- eventbus 7 的 bus 是 `static final EventBus<T>`；用 `BUS.addListener(...)`，不要恢复旧 `MinecraftForge.EVENT_BUS.register(this)` + `@SubscribeEvent` 作为主路径。`EventBusMigrationHelper` 只是兼容层。
- `AddGuiOverlayLayersEvent` 的 accessor 是 `getLayeredDraw()`；`ForgeLayer` 的函数式方法是 `extract(GuiGraphicsExtractor, DeltaTracker)`。
- `RegisterClientCommandsEvent` 是 record，accessor 为 `getDispatcher()` / `getBuildContext()`。
- `RegisterClientReloadListenersEvent` 的 listener 注册到默认 bus；它不是 mod bus 事件。

### 4.3 网络

Forge 26.3 仍可沿用 `EventNetworkChannel`，但 API 形状变了：

- `ChannelBuilder.named(Identifier)`；common `IPacket.getId()` 已返回 `Identifier`，直接传，不要转回 `ResourceLocation`。
- 14 个 channel 的 ID 继续用 packet ID；保持旧 channel 的 `optional()` 语义。
- `PlatformNetworkServiceImpl.CHANNEL_MAP` 的 key 改为 `Identifier`。
- 发送仍用 `chan.send(buf, PacketDistributor.PLAYER.with(player))` / `PacketDistributor.SERVER.noArg()`；先检查 `isRemotePresent`。
- `EventNetworkChannel.addListener(Consumer<CustomPayloadEvent>)`；服务端 handler 在 `ctx.enqueueWork(...)` 中调 `CommonServer`，客户端 handler 在 `ctx.enqueueWork(...)` 中调 `CommonClient`。
- clientbound handler 只能放在 `ForgeClient`；`ForgeMain` 只注册 C2S handler 和 channel map。

## 5. Outline / World Submit：F4 独立 spike

common 的 `BlockOutlinePass.onWorldSubmit(WorldSubmitContext)` 需要 `PoseStack` + `SubmitNodeCollector` + `Camera` + partialTick。Forge 26.3 的情况：

- `AddFramePassEvent` 只提供 `FramePassManager.PassDefinition`；`extracts(LevelTargetBundle, FramePass, LevelRenderState)` / `executes(LevelRenderState)` 没有 `PoseStack` 或 `SubmitNodeCollector`，不能直接承载现有 pass。
- `RenderHighlightEvent.Block/Entity` 的 custom renderer 有 `SubmitNodeCollector` / `PoseStack`，但只在 vanilla 已经产生 selection highlight 时触发；它不能覆盖所有 marker 的 outline。
- 因此 F4 需要 spike，优先级：

  1. **首选**：Forge-local mixin 到 `LevelRenderer#submitFeatures(LevelRenderState, SubmitNodeCollector, boolean)` 的 `RETURN`。该点已提交 entity/block entity，和 Fabric `COLLECT_SUBMITS` 等价；`PoseStack` 在实现中改用 `new PoseStack()`（RETURN 处局部栈已由 `checkPoseStack` 断言为空，等价性见 §1.6），把 `PoseStack` / `SubmitNodeCollector` / `mainCamera()` 转给 common `BlockOutlinePass.onWorldSubmit`。注入点必须以 Forge 26.3 实际 `LevelRenderer` 方法签名为准，不能照搬 NeoForge 的 `SubmitCustomGeometryEvent`，也不要用没有 pose/collector 的 `AddFramePassEvent`。
  2. **备选**：把 `BlockOutlinePass` 重写成 Forge frame-pass 渲染器；工作量大，且需要自己处理 pose/camera/collector 生命周期。
  3. **不可接受**：静默不画 outline 或只画当前准星方块。
- F4 的出口：Forge client 进世界后，普通方块、门/床两半、`entity_block` 至少各有一个可见 outline；若 mixin 失败，记录失败日志并暂停 F5，不在 F6 假装完成。实现与当前证据见 §1.6。

## 6. 源码迁移分类

### 6.1 首轮改写/保留

- `ForgeMain`：`@Mod` + 构造注入；14 个 `EventNetworkChannel`；C2S handler；不引用客户端类。
- `ForgeClient`：在 `@Mod` 构造期由 `ForgeClientBootstrap` 同步初始化；S2C handler；HUD/reload/key/commands/config screen；不包含 WorldAware。不要改回 `FMLClientSetupEvent`（HUD/key/reload 事件更早且 self-destruct）。
- `PlatformContextServiceImpl`：版本、game/config dir、`isModLoaded`。
- `PlatformClientContextServiceImpl`：新增；继承/组合 context，实现 `registerKeyMapping`。
- `PlatformNetworkServiceImpl`：服务端 `sendToClient`；`CHANNEL_MAP` 用 `Identifier`。
- `ClientNetworkServiceImpl`：新增；客户端 `sendToServer`。
- `PlatformClientEventServiceImpl`：6 个方法全部实现；world submit 由 Forge-local `LevelRendererSubmitMixin` 在 `submitFeatures` RETURN 驱动（F4，见 §1.6）。
- `PlatformServerEventServiceImpl`：logout + server tick Post。
- 6 个 `META-INF/services`；旧 4 个补 2 个。
- `mods.toml` 模板、`pack.mcmeta`、本地 `pingforit.mixins.json`。

### 6.2 首轮延后

- `ForgeWorldAwareBlockModelOutlineAdapter`：26.3 common 已由 `BlockModelGlowPass` 取代；不要直接恢复。
- 任何 Create/Flywheel/Simulated/WorldAware/Contraption 适配器。
- 旧 Forge 的 `@Mod.EventBusSubscriber` 自动订阅路径。
- Forge 测试源集（首轮不做；common 的 1193 tests 仍是主覆盖）。

## 7. 分阶段执行与门禁

| 阶段 | 内容 | 出口条件 |
| --- | --- | --- |
| F0 | 同步分支 + 基线 | `26.3-Forge` 基于 `2954fcf`；`:forge:compileJava` 成功；工作区无意外改动 |
| F1 | 构建接线、metadata、pack、mixin 配置 | **已完成**：`:forge:processResources`、`:forge:jar` 成功；metadata 无占位符、pack 121、8 条本地 mixin；静态审查通过 |
| F2 | 入口、服务端网络、SPI、6 个 services | **已完成**：`:forge:compileJava` 通过；`:forge:runServer` 输出 `[PingForIt] Init`、`Done (1.937s)`；6 个 services 已入 jar |
| F3 | 客户端事件、HUD、按键、reload、配置界面 | **已完成**：`:forge:runClient` 进入 26.3 主菜单；mixin 配置加载、outline pipeline 创建；5 个按键进入 options；无 mod ERROR/FATAL |
| F4 | outline / world-submit spike | **实现完成**（2026-11-16，`204e42e`，见 §1.6）：Forge-local `LevelRendererSubmitMixin` 注入 `LevelRenderer#submitFeatures` 的 RETURN，no-op 警告已删除；`:forge:compileJava`、`:forge:prepareDevMod`、`:forge:jar` 实跑通过（devmod/jar 9 条 mixin），独立审查 PASS、无 P0/P1。**已完成**（2026-11-16）：日志 `bridge active`、`submitted=1..8`、无 `routing mixin inactive`/`pass failed`；用户确认普通方块/门床两半/entity_block 视觉正常；F4 门禁通过 |
| F5 | 客户端/服务端运行时验证 | client/server 均有 `[PingForIt] Init`；进世界、ping、HUD、outline、重连；无 ERROR/FATAL |
| F6 | 打包/静态身份检查 | `:forge:build`；精确 jar 名、common classes/resources、6 services、9 mixin（8 common + 1 Forge-local）、metadata、pack、LICENSE |
| F7 | 独立审查 + 合并 | 审查 PASS；F0–F6 证据齐全；`26.3-Forge` 合并回 `26.3` |

F1 的 metadata 门禁至少检查：

- `build/generated/modMetadata/META-INF/mods.toml`（或最终 resources 输出）无未展开 `${...}`。
- `mods.toml` 的 `modId` / `displayName` / `version` 与 `gradle.properties` 一致。
- `pack.mcmeta` 为 121。
- 本地 mixin 配置没有 refmap；F1 时 mixin 列表与 common 的 8 条一致，F4 起为 8 common + 1 Forge-local = 9 条。

## 8. 验证命令

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
$env:GRADLE_USER_HOME = '<workspace>\.agents-work\gradle-home' # 仅当前受限运行环境需要
$env:GRADLE_OPTS = '-Dfile.encoding=UTF-8'

.\gradlew.bat --no-daemon --configure-on-demand --offline :forge:compileJava
.\gradlew.bat --no-daemon --configure-on-demand --offline :forge:processResources
.\gradlew.bat --no-daemon --configure-on-demand :forge:runServer
.\gradlew.bat --no-daemon --configure-on-demand :forge:runClient
.\gradlew.bat --no-daemon --configure-on-demand :forge:build
```

- 首次解析依赖需要网络；缓存已存在时可 `--offline`。不要让 `--offline` 掩盖缺失依赖。
- ForgeGradle 7 的 `runServer` 通过 `SlimeLauncherExec` 启动，要求 **Java 8 launcher toolchain**（与项目 Java 25 编译目标不同）。Gradle 需能发现 JDK 8（本机为 `~/.gradle/jdks/temurin-8-*`）；缺少时会在任务配置阶段报 toolchain 解析失败。
- `verifyModIdentity` 仍依赖 `:forge:build`；F6 之前不要运行，也不要把 Forge 骨架的 compile 成功说成身份校验通过。
- `:common:test` 仍应保持 1193/1193；Forge 迁移不应修改 common 测试源集。
- 运行日志路径：`runs/forge-client/logs/latest.log`、`runs/forge-server/logs/latest.log`。

## 9. 风险与未决问题

1. **Mixin 0.8.7 / Java 25**：最高兼容级别 `JAVA_21`。F3 的 `:forge:runClient` 已实际输出 `Compatibility level set to JAVA_21`，custom outline pipeline 创建成功；2026-11-15 的 runClient 进入世界后 8/8 条 mixin 都出现 `Mixing ...`，`SubmitNodeCollectionMixin` 也在世界路径应用。但 debug 日志同时给出 8 次 `Class version 69 ... JAVA_21 supports class version 65` 警告，不能把「Mixing 出现」当成「注入语义正确」。F4/F5 必须验证按键桥、EntityRenderer/BlockEntity/LevelExtractor/SubmitNodeCollection 的实际行为；若失败，评估 Java 21 字节码或更新 mixin 栈。保留 `pingforit.mixins.json` 的 `JAVA_21`，不要伪造 `JAVA_25`。
2. **World submit 没有等价事件**：F4 的 `LevelRenderer` mixin 是唯一低风险路径；`AddFramePassEvent` 没有 pose/collector，不能直接复用 common pass。若 F4 失败，Forge 首轮 outline 不可宣称完成。
3. **事件总线 7 迁移**：旧 `MinecraftForge.EVENT_BUS` + `@SubscribeEvent` 只是兼容层；新代码统一用静态 `BUS`，避免自动订阅和类加载顺序问题。
4. **客户端类隔离与时序**：`ForgeMain` 不得直接引用 `ForgeClient` / `CommonClient` / `SettingsScreen`；客户端由 `ForgeClientBootstrap` 在 mod 构造期同步加载。HUD 层、按键、client reload 三个 `SelfDestructing` 事件在 `Minecraft` 构造期先 fire，不能等到 `FMLClientSetupEvent`。
5. **metadata 版本字段**：`loaderVersion` 与 Forge 依赖 `versionRange` 不是同一个东西；旧 `forge_loader_version=66` 不能直接作为 `loaderVersion`。F1 以 Forge universal metadata 和实际启动日志复核。
6. **可选集成**：Create/Flywheel 没有 26.3 artifact；不要加 1.21.1 compileOnly，也不要在 Forge metadata 声明可选依赖。
7. **运行环境**：默认 Gradle 用户目录在受限环境可能不可写；使用工作区 `GRADLE_USER_HOME` 仅作为本机 workaround，不写入项目构建逻辑。`runClient` 的 assets 路径是环境相关：早期 F3 在本机用 workspace `APPDATA` 指向缓存 assets；2026-11-15 的 runClient 使用默认 `C:/Users/.../.minecraft/assets` 并成功进世界。F5 前确认窗口前台、assets 可读；不要把某一台机器的路径写成构建要求。
8. **客户端注册时序**：HUD/key/reload 三类 `SelfDestructing` 事件早于 `FMLClientSetupEvent`；不要把客户端注册移回 setup 事件，否则不会报错但会永久丢失。`POST_SLEEP_STACK` 是 F1 隐藏语义的一部分，不要改回 root。
9. **dev Mixin manifest**：`prepareDevMod` 的 manifest 只服务目录型 dev mod；生产 jar 的 `MixinConfigs` 仍由 `jar.manifest` 提供。删除 dev manifest 会让开发运行静默失去 mixin，但发布 jar 不受影响。

10. **F4 静默失效风险**：`LevelRendererSubmitMixin` 使用 `require = 0`，描述符漂移时不会崩溃，但 outline 也不会绘制；唯一正信号是首帧一次性 INFO `block outline: Forge world-submit bridge active`。F5 必须把「`bridge active` + `block outline: submitted>0` + 视觉」作为硬门禁，缺一不可；不要用 `Preparing 9 mixins`/`Mixing` 代替 outline 已画。

## 10. 审查基线

F7 独立审查至少检查：

- 分支：`26.3-Forge` 基于 `2954fcf`，diff 只含 Forge 迁移与本文档；未修改 `docs/`。
- 编译：F2/F3/F5 的 Gradle 命令与结果；不是只读源码推导。
- 运行：client/server 日志中的 loader、MC 版本、`[PingForIt] Init`、错误/致命日志。
- 产物：精确 jar 名、common class/resource 合并、6 services、9 mixin、`mods.toml` 展开、`pack.mcmeta` 121、LICENSE/THIRD-PARTY。
- 未验证项：多人、可选集成、HUD/outline 视觉细节、Create/Flywheel parity；不要用编译或静态检查替代。

## 11. Forge 与 Fabric/NeoForge 的 parity 差异（2026-11-16 审查）

> 背景：F0–F4 让 Forge 端跑通后，用户判断 Forge 与原模组（Fabric/NeoForge 实现 + `docs/` 行为契约）仍有大量差异，要求逐项核对，F5 暂停。本节只记录**分析与建议**，不声称已修复；逐项证据见 `.agents-work/parity/parity-analysis.md`。审查期间未修改任何产品代码、未 commit、未跑 runClient/runServer。

### 11.1 审查范围与方法

- 三方源码：`fabric/src/main`+`fabric/src/client`、`neoforge/src/main`、`forge/src/main`（含 resources/templates/build.gradle dev 接线）、共享 `common/src/main`；`mod-source-isolated/` 只读，未恢复。
- 行为权威：`docs/`（只读，未改动）与 `AGENTS.md`。`docs/` 没有 loader 机制条款，但把 Create/Simulated 明确定义为 NeoForge-only（`docs/integrations/create.md:1`、`docs/integrations/simulated.md:3`）。因此基准分两层：**按用户 parity 目标**，以 NeoForge in-tree 实现为参照；**按 docs**，这两个集成属于 documented NeoForge-only scope，不是 Forge 的契约缺口。
- 机制核实不只读本仓库：读了 loader 发布的源码（Fabric API `27.2.0` 的 `LevelRendererMixin`/`HudElementRegistry`/`HudElementRegistryImpl`/`SubtitleOverlayMixin`、Forge patched `Minecraft.java`/`LevelRenderer.java`/`ForgeLayeredDraw.java`/`TickEvent.java`/`RegisterKeyMappingsEvent.java`/`EventNetworkChannel.java`、NeoForge `PayloadRegistrar`/`RegisterClientPayloadHandlersEvent`/`SubmitCustomGeometryEvent`/`RenderFrameEvent`/`RegisterGuiLayersEvent`、vanilla `LevelExtractor`/`Options`），以及既有 `runs/forge-client/logs/`（2026-11-16 22:44–22:47 会话）。
- 证据分级沿用 AGENTS §9：源码推导 / 运行时观测（既有日志）/ 未验证，三者不混写。

### 11.2 差异总表

计数：**P0 = 0，P1 = 2，P2 = 14，P3 = 8**。

| # | 级别 | 项 | Fabric / NeoForge 做法与效果 | Forge 做法与效果 | 证据 | 建议 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | **P1** | `mods.toml` 缺 `displayTest` | NeoForge 模板也写了 `displayTest = "IGNORE_SERVER_VERSION"`，但 **NeoForge 26.3 loader 没有该字段的实现 → inert**（sources jar 0 命中），不能当作对齐理由 | Forge fmlcore **支持**该字段，缺省 `MATCH_VERSION`：客户端/服务端 pingforit 版本不一致时多人列表标红（仅影响服务器列表兼容标记） | `forge/src/main/templates/META-INF/mods.toml:6-13`；`forge/build/generated/modMetadata/META-INF/mods.toml`；1.21.1 旧模板 `mod-source-isolated/forge/src/main/resources/META-INF/mods.toml:14`；fmlcore `ModContainer.java` 的 `displayTest` switch 默认分支；本节 §3.1 模板也写了该字段 | **迁移丢失**（旧模板/§3.1 有、当前模板没有）：加 1 行 + 重建；理由写"恢复既有字段"，不是"对齐 NeoForge" |
| 2 | **P1** | Forge 缺 Create door / Simulated docking presentation resolver | NeoForge in-tree：`CreateDoorPresentationResolver`、`SimulatedDockingConnectorPresentationResolver`（含 join 时重试 + 各自单测）。两者**只 import vanilla+common**（ID 字符串 + registry 查询，无 Create/Simulated 类依赖） | Forge 无 `integration` 包；装了 Create/Simulated 时 presentation 与 NeoForge 不同 | `NeoClient.java:99-120` + `integration/create`、`integration/simulated`（两 resolver 的 import 全是 vanilla/common）；Forge 无对应文件 | §0.2/§6.2 已显式延后。**不是工件阻塞**：可按 docs 的 NeoForge-only 保持现状，或由用户决定纳入 Forge 范围（届时同步 §6.2 延后清单；改 docs 需单独授权） |
| 3 | P2 | 网络机制 | payload type + StreamCodec（Fabric 2 流 14 类型；NeoForge 1 registrar `.optional()`） | 14 个独立 `EventNetworkChannel` + 裸 `FriendlyByteBuf`，不用 payload type | `FabricMain.java:45-58`；`NeoMain.java:71-103`；`ForgeMain.java:46-73,87-134` | loader 必要差异；包 ID/方向/optional/主线程语义一致，不修 |
| 4 | P2 | world submit 触发点 | Fabric `LevelRenderEvents.COLLECT_SUBMITS` = `submitFeatures` `@At("RETURN")`；NeoForge `SubmitCustomGeometryEvent` 在 `submitBlockOutline`（L341）**之后**、outline 桶清理（L347-351）**之前**（调用点约 L345） | `LevelRendererSubmitMixin` 注入同一 `submitFeatures` `RETURN`，`new PoseStack()` | Fabric API `LevelRendererMixin.java:84-93`；`LevelRendererSubmitMixin.java:38-51`；patched `LevelRenderer.java:329-354`；NeoForge patched `submitFeatures`（审查复核） | **Forge 与 Fabric 同一注入点**，PoseStack/collector/camera/partialTick 等价；NeoForge 的"更早"只指早于 outline 清理、晚于 `submitBlockOutline`；不需要改 |
| 5 | P2 | render frame 语义 | Fabric `LevelExtractionEvents.END_EXTRACTION`；NeoForge `RenderFrameEvent.Pre`（once per frame） | `TickEvent.RenderTickEvent.Pre` | patched `Minecraft.java:1304-1318`：事件在 `gameRenderer.extract(...)` 之后、`gameRenderer.render()` 之前 fire → **每帧一次、post-extract** | 语义等价；CommonClient 的注释（`END_EXTRACTION`）可补一句 Forge 映射，不必改代码 |
| 6 | P2 | HUD 注册与 F1 门控 | Fabric `addLast` 不继承 render condition：hidden 且无界面 → 不画；hidden 且开 in-game 界面 → 仍画，且经 **deferred subtitles 延后到界面之上**。NeoForge `registerAboveAll` **无任何 F1 条件**（`GuiLayerManager#renderInner` 逐层无条件调用）→ hidden 时仍画，但仍在 HUD 阶段、界面之下 | `AddGuiOverlayLayersEvent` + `ForgeLayeredDraw.POST_SLEEP_STACK`（`!gui.isHidden()`）→ hidden 时始终不画，界面之下 | `HudElementRegistry.java:30-36`、`HudElementRegistryImpl.java:85-88`；`Gui.java`（deferred subtitles）；`RegisterGuiLayersEvent.java:81-83` + `GuiLayerManager`；`ForgeLayeredDraw.java:443-463` | 三家 F1 门控都不同（Forge 最严格）；界面层叠 Fabric 在界面之上、Forge/Neo 在界面之下 → **P3 补充**，不修 |
| 7 | P2 | 按键注册 | Fabric `KeyMappingHelper.registerKeyMapping`；NeoForge `RegisterKeyMappingsEvent` | `RegisterKeyMappingsEvent.BUS` | `RegisterKeyMappingsEvent.java:37-39` | 等价；分类都用 vanilla `KeyMapping.Category.register` |
| 8 | P2 | join/leave 事件 | Fabric `JOIN`/`DISCONNECT`（play 连接） | `ClientPlayerNetworkEvent.LoggingIn`/`LoggingOut` | `ClientPlayerNetworkEvent.java:51-111`；日志 `Channel update: Dev -> default` 证明 join 期 C2S 送达 | 等价；`LoggingOut` 可能在建集成服务器时 fire，清理幂等 |
| 9 | P2 | 声音注册 | Fabric 只在 client 注册；NeoForge `DeferredRegister` 双端 | `DeferredRegister<ForgeRegistries.SOUND_EVENTS>` 双端 | `FabricClient.java:46`；`NeoMain.java:53-61`；`ForgeMain.java:39-44,76` | 机制差异；ID/资源/播放路径（common `ClientPingRuntime:1024-1043`）一致 |
| 10 | P2 | mixin 配置 | Fabric 直接用 common 8 条（`JAVA_25`）；NeoForge 本地 8 条（`JAVA_25`） | 本地 9 条（8 common + `LevelRendererSubmitMixin`，`JAVA_21`） | 三份 `pingforit.mixins.json`；`debug.log` 9 条 `Mixing` | compatibilityLevel 是 loader 必要差异；保持 `JAVA_21` |
| 11 | P2 | services 布局 | Fabric 3 main + 3 client；NeoForge 6 在 main | 6 在 main（universal） | `*/META-INF/services/*` | 覆盖无缺失；universal jar 的必然 |
| 12 | P2 | 客户端初始化 | Fabric client initializer；NeoForge 构造 + dist 分支 | 构造 + `ForgeClientBootstrap` + dist 分支 | `ForgeMain.java:80-84`；`ForgeClientBootstrap.java:15-21` | loader 必要；bootstrap 是防御性间接层，保留 |
| 13 | P2 | dev 类路径 | Loom / MDG 各自处理 | `prepareDevMod` + dev `META-INF/MANIFEST.MF` + run classpath 替换 | `forge/build.gradle:92-141` | 只影响 dev run，不影响发布 jar |
| 14 | P2 | 配置界面 | Fabric **当前无**（`ModMenuIntegration` 仍在隔离区）；NeoForge `IConfigScreenFactory` | `ConfigScreenHandler.ConfigScreenFactory` | `mod-source-isolated/fabric/.../ModMenuIntegration.java`；`NeoClient.java:49-52`；`ForgeClient.java:69-72` | Forge 与 NeoForge 等价；Fabric 反而缺，属基线不对称 |
| 15 | P2 | 服务端登出/ tick | `DISCONNECT` + `END_SERVER_TICK` | `PlayerLoggedOutEvent` + `ServerTickEvent.Post` | 三个 `PlatformServerEventServiceImpl` | 等价；时序略不同（连接断开 vs 玩家移除） |
| 16 | P2 | C2S 守卫 | NeoForge `withServerPlayer`（检查 `instanceof ServerPlayer` 且 server 非 null） | 直接 `sender.level().getServer()` | `NeoMain.java:105-112`；`ForgeMain.java:122-133` | 专用服务器恒非 null；可选补守卫（P3） |
| 17 | P3 | 命令输出 API | Fabric `sendFeedback`/`sendError` | `sendSuccess(()->..., false)`/`sendFailure` | `FabricClient.java:90-98`；`ForgeClient.java:60-67` | 客户端 source 上都只加本地聊天行；不修 |
| 18 | P3 | reload listener id | Fabric/NeoForge 带 `pingforit:reload-listener` | Forge API 不取 id | `ForgeClient.java:57-58` | 行为等价 |
| 19 | P3 | world submit 异常隔离 | Fabric 不隔离；NeoForge 事件总线记录 | 捕获 `RuntimeException|LinkageError`，每会话一次 | `PlatformClientEventServiceImpl.java:89-114` | Forge 更防御（可能掩盖 bug），可接受 |
| 20 | P3 | world submit 回调槽 | 多监听 | `volatile` 单槽（后注册覆盖） | `PlatformClientEventServiceImpl.java:73` | 当前只有 common 注册一次；如未来多监听再改列表 |
| 21 | P3 | SPI 委托方式 | Fabric/NeoForge 组合 delegate | `PlatformClientContextServiceImpl extends PlatformContextServiceImpl` | `forge/.../PlatformClientContextServiceImpl.java:7` | 风格差异 |
| 22 | P3 | packet 处理标记 | 框架侧处理 | `finally { setPacketHandled(true); }` | `ForgeClient.java:80-96`；`ForgeMain.java:122-133` | 防御性，可接受 |
| 23 | P3 | `SyncDurationPolicyS2CPacket` 身份断言 | — | — | `ModIdentityTest` 的 ID 集合仍缺该包（AGENTS 已知缺口） | 测试缺口，与 Forge 迁移无关 |
| 24 | P3 | NeoForge 订阅风格 | `NeoForge.EVENT_BUS.register(this)`+`@SubscribeEvent` | 静态 `BUS.addListener` | `NeoClient.java:45,88-97` | 风格差异；Forge 侧保持 eventbus 7 主路径 |

补充 **P3（发布卫生，未计入上表计数）**：`forge/build/libs/` 仍留着 1.21.1 旧产物 `Ping-For-It-0.4.2-pfi-beta1-forge-1.21.1.jar`（0.89 MB，2026-11-08）与旧 `sources`/`javadoc` jar。`verifyModIdentity` 只匹配精确名、不会误判，但 F6 打包/发布前应清理，避免手工取 jar 时拿错。

### 11.3 loader 必要差异（不需要"修"）

1. `EventNetworkChannel`/`ChannelBuilder` 取代 payload type 注册（Forge 26.3 仍提供该 API，且是当前唯一稳定路径）。
2. `LevelRendererSubmitMixin` 取代 `COLLECT_SUBMITS`/`SubmitCustomGeometryEvent`（Forge 没有等价事件；注入点与 Fabric 完全相同）。
3. `ForgeLayeredDraw.POST_SLEEP_STACK`（Forge 用 layered draw 取代 vanilla HUD 分支；root 没有 `!isHidden`）。
4. `RegisterKeyMappingsEvent.BUS`/`RegisterClientCommandsEvent.BUS`/`RegisterClientReloadListenersEvent.BUS`（eventbus 7 静态 bus；这三类事件在 `Minecraft` 构造期 fire 且 `SelfDestructing`）。
5. `ClientPlayerNetworkEvent.LoggingIn/Out` + `PlayerEvent.PlayerLoggedOutEvent`（Forge 无 Fabric 的连接事件）。
6. mixin `compatibilityLevel = JAVA_21`（vanilla mixin 0.8.7 上限，**不是** toolchain 版本）。
7. dev 目录 `META-INF/MANIFEST.MF` + `prepareDevMod`（ForgeGradle 7 类路径模型；目录型 dev mod 无 jar manifest）。
8. `ForgeClientBootstrap` 间接层（防御性；Java 符号解析本就惰性，保留不影响行为）。

### 11.4 待用户批准的修复清单（建议顺序）

| 序 | 动作 | 级别 | 范围 | 前置 |
| --- | --- | --- | --- | --- |
| 1 | `forge/src/main/templates/META-INF/mods.toml` 的 `[[mods]]` 加 `displayTest = "IGNORE_SERVER_VERSION"`，重建 `:forge:jar` 并确认展开结果 | P1 | 模板 1 行 | 无 |
| 2 | 补 F5 运行时验证（不改代码）：进世界观察 HUD/wheel/F1、执行 `/pingforit help`/`channel`、听 ping 声、打开配置界面、看 Controls 分类；双客户端/专用服务器验证 marker/限流/重连 | P0 排查 | 无代码 | 无 |
| 3 | 先由用户决定是否把 Create door / Simulated resolver 纳入 Forge 范围；若纳入，复制两 resolver + `isModLoaded` 门控 + join 重试 + fail-soft 日志，并同步 §6.2 延后清单 | P1 | `forge/.../integration` + `ForgeClient` | **无工件前置**（resolver 只 import common+vanilla）；docs 的 NeoForge-only 条款若要改需单独文档授权 |
| 4 | （可选）world-submit 回调改列表；C2S 补 `getServer()!=null` 守卫；`ModIdentityTest` 补 `SyncDurationPolicyS2CPacket` | P3 | 各 1-3 行 | 无 |
| 5 | （可选）Fabric 恢复 `ModMenuIntegration`；在本文件 §6.2 明确列出可选集成延后范围（Create/Simulated/WorldAware/Contraption/WaterWheel/Flywheel/Create mixin） | P3 | 文档/隔离区 | 文档授权 |

不建议做（会引入真实回归）：把 Forge 网络改回 payload type；用 `AddFramePassEvent` 取代 mixin；把 HUD 挂回 root；把客户端注册挪到 `FMLClientSetupEvent`。

### 11.5 未验证 / 需实跑（不得写成"已验证"）

| 项 | 现状 | 需要的证据 |
| --- | --- | --- |
| HUD 绘制（marker 图标/名字/距离/方向箭头/wheel/toggle notice） | 未验证（F3/F4 日志无 HUD 正信号） | 进世界观察/截图 |
| F1 隐藏 HUD 时 marker/wheel 是否消失 | 未验证（静态推导：Forge 会隐藏） | 按 F1 观察 |
| 命令输出（`/pingforit help`、`channel get/set`、`config`） | 未验证 | 游戏内执行 |
| ping 声音（音量/方向） | 未验证 | 听觉确认 |
| 配置界面：mods 列表打开、保存、重载 | 未验证 | 打开设置 + 改值 + 重载资源包 |
| Controls 里 `pingforit:name` 分类与 5 个按键 | 未验证（options.txt 已见 5 个 key） | 打开按键设置 |
| 多人：双客户端、专用服务器、winner/限流/syncDuration、重连 | 未验证 | 双端会话 + 日志 |
| `improvedTransparency=ON` 时的 outline | 未验证（Forge 与 Fabric 同在 RETURN 注入） | 开选项后观察 |
| Create/Simulated 可选集成 | 未实现（无工件阻塞，可移植） | 需要产品决定 + 复制 resolver + join 重试验证 |
| `displayTest` 修复后的多人兼容提示 | 未验证 | 版本不一致的 client/server |

### 11.6 既有运行证据（支持"P0 = 0"判断）

`runs/forge-client/logs/latest.log`（2026-11-16 22:44–22:47，Forge 26.3-66.1.0，JDK 25）：

- `[PingForIt] Init`、`Loaded config type=ServerConfig`、`Loaded config type=ClientConfig`、`ping outline pipeline registered`；
- `Channel update: Dev -> default`（`onJoinServer` 的 C2S 在真实 join 时序下送达服务端）；
- `block outline: Forge world-submit bridge active (LevelRenderer#submitFeatures RETURN)` + **2 次** `send ping`/`marker created`（22:46:44、22:46:51）+ wheel open/close（3 次 ping 属 2026-11-15 那轮会话，不要混用）；
- `debug.log`：9 条 `Mixing ... from pingforit.mixins.json`（含 `LevelRendererSubmitMixin`、`SubmitNodeCollectionMixin`）；同时有 **9 条** `Class version 69 ... JAVA_21 supports class version 65` DEBUG 警告（9/9 仍应用成功）；0 条 pingforit WARN/ERROR。

这些只证明"链路通"，不证明 HUD 视觉、命令输出、声音、多人；后者仍属 §11.5。
