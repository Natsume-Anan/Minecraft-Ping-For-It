# Ping For It — 26.3 Forge 迁移方案

> 状态：**F0–F3 已完成**（2026-11-14）；**F4 已完成**（2026-11-16，`204e42e`）。`26.3-Forge` 已从旧的 `407fbc6` 同步到 `26.3` @ `2954fcf`；本地原有的 `9a723c2` 仅重复添加 `.agents-work/`，rebase 时被安全跳过。F0 基线 `:forge:compileJava` 实跑成功（骨架 `NO-SOURCE`）；F1 已接好 common 依赖/资源合并、metadata、121 pack 和 8 条本地 mixin；F2 已添加入口、14 个 channel、6 个 SPI 实现和 6 个 services，`:forge:runServer` 实跑输出 `[PingForIt] Init` 与 `Done (1.937s)`。F3 已加入客户端入口、8 个 S2C handler、按键/reload/HUD/命令/配置界面与声音注册；`:forge:runClient` 进入主菜单，mixin 配置成功加载，outline pipeline 创建成功。F4 观察基线已记录（2026-11-15）：当前 runClient 已进入单人世界并完成 3 次 block ping，但 world-submit 仍是 no-op。**F4 已完成**（2026-11-16，`204e42e`）：Forge-local `LevelRendererSubmitMixin` 注入 `LevelRenderer#submitFeatures` 的 RETURN，取代 no-op；`:forge:compileJava` / `:forge:prepareDevMod` / `:forge:jar` 实跑通过，独立审查 PASS、无 P0/P1；22:13–22:14 的 runClient 已观察到 `bridge active` 与 `submitted=1..8`、无 routing/pass 失败。用户已确认普通方块/门床两半/entity_block 视觉正常，F4 门禁通过。**F5 暂停，进行 Forge/Fabric/NeoForge parity 审查（2026-11-16）**：结论见 §11（P0 = 0、P1 = 2、P2 = 14、P3 = 8），逐项报告在 `.agents-work/parity/parity-analysis.md`。**方案 A 已执行**（2026-11-17，`14c44f9`）：`mods.toml` 恢复 `displayTest`，`:forge:jar` 重建通过；P1-1 关闭，P1-2 于 2026-11-18 裁决 B（不纳入）关闭。**第二轮 parity 逐项复核完成（2026-11-18）**：P1 open = 0（P1-2 经用户裁决 **B：不纳入**，按 docs 的 NeoForge-only 保持现状）/ P2 = 14 / P3 = 12，结论见 §11，报告在 `.agents-work/parity2/analysis.md`；F5 暂停，待修复范围决定。**P3 小修复已执行**（2026-11-18，`224e0c2`）：C2S try/finally + server 守卫、world-submit 多监听、`ModIdentityTest` 第 14 包；`:common:test` 1193/1193、`:forge:compileJava` 实跑通过。
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

- 首轮不迁移 Create、Simulated、Flywheel、WorldAware、Contraption、WaterWheel/Create mixin 等可选集成；它们依赖 26.3 已删除的 common 几何框架，或只有 1.21.1 的 Create/Flywheel。完整延后清单见 §6.2。
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
- **构建证据**（2026-11-16，JDK 25 + 工作区 `GRADLE_USER_HOME` + `--configure-on-demand`）：`:forge:compileJava` BUILD SUCCESSFUL（12s）；`:forge:prepareDevMod` 后 `build/devmod/nx/pingwheel/common/mixin/LevelRendererSubmitMixin.class` 存在、devmod 内 9 个 mixin class、`pingforit.mixins.json` 9 条、`META-INF/MANIFEST.MF` 仍为 `MixinConfigs: pingforit.mixins.json`；`:forge:jar` 822518 B / 504 entries / 432 classes / 0 重复条目，新 class 仅 1 次（方案 A 恢复 `displayTest` 后重建为 822548 B，+30 B 即该行，见 §11.10.1/§11.16.2）。审查子代理独立复跑（含 `--rerun-tasks`）复核通过：P0/P1 无，P2 三条（日志措辞已修；`require = 0` 静默失效见 §9.10；PoseStack 偏离记录在本节）。
- **运行时证据**（2026-11-16 22:13–22:17，两轮 `:forge:runClient`，单人世界）：`debug.log` 出现 `Preparing pingforit.mixins.json (9)`、`Mixing LevelRendererSubmitMixin ... into net.minecraft.client.renderer.LevelRenderer`、首帧 `block outline: Forge world-submit bridge active (LevelRenderer#submitFeatures RETURN)`；本次会话 32 条 `block outline: submitted=`（1–8），并出现 `entity outline pass active`；无 `pass failed` / `routing mixin inactive` / `bridge failed` / `Mixin apply failed`，`latest.log` 无 mod ERROR/FATAL，客户端正常退出。`Class version 69` 警告对新类仍出现，但 Mixing 与注入行为均正常。
- **视觉确认**（2026-11-16）：用户确认普通方块方框、门/床两半、`entity_block` 原生发光均正常；F4 游戏内验收通过。after-terrain 半透明效果与 F4 后的服务端回归仍归 F5。

F4 游戏内验收（缺一不可，2026-11-16 通过）：日志侧已满足——`Preparing pingforit.mixins.json (9)`、`Mixing net.minecraft.client.renderer.LevelRenderer`、`block outline: Forge world-submit bridge active`、`block outline: submitted=<n>`（n>0）均已出现；视觉侧已由用户确认——普通方块、门/床两半、`entity_block` 均正常。日志 grep：

```powershell
Select-String -Path runs/forge-client/logs/debug.log -Pattern 'Preparing pingforit.mixins.json','Mixing net.minecraft.client.renderer.LevelRenderer','world-submit bridge active','block outline: submitted='
Select-String -Path runs/forge-client/logs/*.log -Pattern 'Forge world-submit outline is unavailable','block outline: pass failed','routing mixin inactive'
```

### 1.7 F4 runClient 警告复核（2026-11-16 22:15–22:17）

同一会话（2026-11-16 22:15–22:17；该日志随后被 22:44 会话覆盖）的 `latest.log` 共 6 条 WARN/ERROR，全部归类如下；**pingforit 自身 0 条 WARN/ERROR**，无 unknown sound/texture/model 警告。

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
- 可选集成范围（2026-11-18 用户裁决 **B**）：Create door / Simulated docking presentation resolver 按 `docs/` 为 NeoForge-only（`docs/integrations/create.md:1`、`docs/integrations/simulated.md:3`），**不纳入 Forge**；Forge 只走 common 通用 presentation 路径，不新增 `forge/.../integration` 包。Sable / Distant Horizons / Voice Chat / FTB Teams 仍由 common 反射 + `isModLoaded` fail-soft（三端共享，未验证）。
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
| F5 | 客户端/服务端运行时验证 | client/server 均有 `[PingForIt] Init`；进世界、ping、HUD、outline、重连；无 ERROR/FATAL；**F5 前先完成 parity 修复范围决定（P1-2 已裁决 B，见 §11.15）** |
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

## 11. Forge 与 Fabric/NeoForge 的 parity 逐项复核（2026-11-18 第二轮）

> 定位：F5 仍暂停。第一轮审查（2026-11-16）在 F4 完成后给出 P0 = 0、P1 = 2、P2 = 14、P3 = 8；2026-11-17 的方案 A 关闭了 P1-1（`displayTest`），P1-2 于 2026-11-18 裁决 B（不纳入）关闭。第二轮（2026-11-18，`26.3-Forge` @ `516d2b7`）重做逐项复核，并**修正了旧节若干结论**（HUD/F1、join 时机、render frame 频率、计数等，见 §11.13）。本轮只做分析与文档：未改产品代码、未改 `docs/`、未 commit、未跑 runClient/runServer/build。逐条证据与完整清单在 `.agents-work/parity2/analysis.md`（365 行）。**旧 §11 与本轮冲突处，以本轮为准。**

### 11.1 范围、方法与证据分级

- 三方源码：`forge/src/main`（java/resources/templates/build.gradle）vs 只读对照 `fabric/src/main` + `fabric/src/client`、`neoforge/src/main`（含 `integration`）、共享 `common/src/main`；`mod-source-isolated/` 只读。
- 行为权威：`docs/`（只读）与 `AGENTS.md`。`docs/` 把 Create/Simulated 明确定义为 NeoForge-only（`docs/integrations/create.md:1`、`docs/integrations/simulated.md:3`），所以基准分两层：按 parity 目标以 NeoForge in-tree 实现为参照；按 docs，这两项不属于 Forge 的契约缺口。
- 机制核实：逐文件映射 + SPI/事件调用链 grep + loader 发布源码（Forge 26.3-66.1.0 patched、NeoForge 26.3.0.0-beta、Fabric API 27.2.0）+ 既有 `runs/` 日志；本节的 jar 事实是本轮直接读取现有 jar（未重建）。
- 证据分级（沿用 `AGENTS.md` §9，全文逐条标注）：

| 级别 | 含义 | 本轮示例 |
| --- | --- | --- |
| 源码推导（本轮） | 只读源码/构建脚本/loader 发布源码/现有产物 | 14 路由注册、`submitFeatures` RETURN、jar 504 entries、`Hud.java` 的 F1 分支 |
| 他轮日志（未复跑） | 2026-11-16 22:44–22:47 的 `runs/forge-client/logs/`、`runs/forge-server/logs/` | `bridge active`、2 次 ping、wheel open/close |
| 未验证 / 需 F5 | 静态只能推出“应当如此”，或需要真实会话 | HUD 视觉、F1、声音、命令输出、多人/重连 |

本轮没有复跑客户端/服务端会话；§11.16 引用的每一行都能在 `runs/` 找到原文。不得把“源码推导”或“他轮日志”写成“本轮已验证”。

### 11.2 结论摘要

| 计数项 | 值 | 说明 |
| --- | --- | --- |
| P0 | 0 | 未发现 Forge 独有的功能缺失或错误接线 |
| P1（未关闭） | 0 | P1-1 已于 `14c44f9` 关闭；P1-2 经 2026-11-18 用户裁决 **B（不纳入）** 关闭，按 docs 的 NeoForge-only 保持现状 |
| P2 | 14 | loader 必要机制、等价实现、边缘时序/门控差异 |
| P3 | 12 | 风格、防御性、打包/文档卫生、仓库级已知缺口 |
| loader-necessary（机制清单，不计独立缺陷） | 8 | 见 §11.12.4；其中 P2-1/P2-3/P2-14 同时计入 P2 的 14 项，8 不是额外缺陷数 |
| 非 mod 噪声（不计缺陷） | 6 | 见 §11.10.2 |

一句话结论：Forge 端**不是另写一套行为**——除已裁决为范围外的“可选集成 resolver”外，逐项核对没有发现用户明显看到/听到不同的功能缺失；world submit 与 Fabric 注入同一方法的同一位置，14 条路由、8 S2C + 6 C2S、HUD/wheel/命令/配置界面/reload/声音/按键、服务端 tick/logout 都走 common 同一路径。

最大风险按顺序：① HUD 层叠与 F1 门控三家不同（Forge 最严格，属跨 loader 差异，不是 Forge 回归）；② HUD 视觉、命令输出、声音、多人/重连仍只有静态或链路证据，属 F5；③ P1-2 已裁决 B（不纳入），docs 的 NeoForge-only 范围已写进 §6.2，不再是开放风险。

### 11.3 文件级对照

| Forge 文件（`forge/src/main`） | Fabric 对端 | NeoForge 对端 | 差异性质 |
| --- | --- | --- | --- |
| `java/nx/pingwheel/forge/ForgeMain.java` | `FabricMain.java` | `NeoMain.java` | 同一职责（`CommonServer.init`、14 路由、sounds、6 个 C2S handler）；Forge 用 `@Mod` 构造注入 + 14 个 `EventNetworkChannel`，不引用客户端类 |
| `java/nx/pingwheel/forge/ForgeClient.java` | `FabricClient.java` | `NeoClient.java` | 同一职责（8 个 S2C handler、reload、客户端命令、配置界面）；Forge 无按键（在 SPI impl）、无可选集成；**Fabric 无配置界面** |
| `java/nx/pingwheel/forge/ForgeClientBootstrap.java` | 无（client initializer 即打包层） | 无（`NeoMain` 内 `new NeoClient(modBus)`） | Forge 独有防御性间接层，只为把 `ForgeClient` 移出 `ForgeMain` 常量池 |
| `platform/PlatformContextServiceImpl.java` | 同名 | 同名 | 等价（mod 版本/game dir/config dir/isModLoaded） |
| `platform/PlatformClientContextServiceImpl.java` | 同名（组合 delegate） | 同名（组合 delegate） | Forge 用 `extends`；行为等价（P3-5） |
| `platform/PlatformNetworkServiceImpl.java` | 同名（`ServerPlayNetworking`） | 同名（`PacketDistributor`） | 等价；presence 门控实现不同（`isRemotePresent` vs `canSend`/`hasChannel`） |
| `platform/ClientNetworkServiceImpl.java` | 同名（`ClientPlayNetworking`） | 同名（`ClientPacketDistributor`） | 等价 |
| `platform/PlatformClientEventServiceImpl.java` | 同名（6 个 Fabric API 事件） | 同名（6 个 NeoForge 事件） | 6 方法全接；world submit 走 mixin bridge（§11.6） |
| `platform/PlatformServerEventServiceImpl.java` | 同名（连接/tick 事件） | 同名（`PlayerEvent`/`ServerTickEvent`） | logout/tick 等价；事件家族不同 |
| `java/nx/pingwheel/common/mixin/LevelRendererSubmitMixin.java` | Fabric API `fabric-rendering-v1` 内部 mixin 提供 | NeoForge `SubmitCustomGeometryEvent` 提供 | **Forge 独有文件**（第 9 条 mixin，只进 Forge 本地配置） |
| `resources/pingforit.mixins.json` | 不单独存在（用 common 8 条） | 本地 8 条（`JAVA_25`） | Forge 本地 9 条 + `JAVA_21`（loader 必要） |
| `resources/pack.mcmeta` | 无（Loom 资源包） | `min_format 121` / `max_format [121,0]` | Forge `121/121`（int）；两者都被加载（`runs/forge-client/logs/latest.log:42`） |
| `resources/META-INF/services/*`（6） | 3 main + 3 client 分源集 | 6 在 main | universal jar 的必然 |
| `templates/META-INF/mods.toml` | `templates/fabric.mod.json` | `templates/META-INF/neoforge.mods.toml` | 模板机制一致；Forge 的 `displayTest` 现在生效 |
| `forge/build.gradle` | `fabric/build.gradle` | `neoforge/build.gradle` | 同为“common classes/resources 单 jar 合并 + 本地 mixin 配置胜出”；Forge 额外 `prepareDevMod`（dev 专用） |

“有/无对端”清单：

- **仅 Forge 有**：`ForgeClientBootstrap`、`LevelRendererSubmitMixin`（类在 Forge 模块内）、`prepareDevMod` 任务 + dev 目录 `META-INF/MANIFEST.MF`。
- **Forge 缺、NeoForge 有**：`neoforge/.../integration/create/CreateDoorPresentationResolver.java`、`.../integration/simulated/SimulatedDockingConnectorPresentationResolver.java`，以及 `neoforge/src/test` 的两个 resolver 测试；Forge 无 `integration` 包、无 test 源集。
- **三方都无（延后/隔离区）**：Create contraption raycast、Create entity outline、Flywheel geometry、WaterWheel、Create mixin、WorldAware adapter——这些 loader 专属 adapter 与共享几何框架都未恢复。
- **loader 专属 adapter 无、common 实现仍在（未验证）**：Sable、DistantHorizons、VoiceChat、FTBTeams 的 loader 侧 adapter 不存在，但 common 的反射 + `isModLoaded` 实现在三端共享（`common/.../integration/ModContext.java:15-28`；§11.11）。
- **基线不对称**：Fabric 当前无配置界面（`ModMenuIntegration` 在 `mod-source-isolated/fabric/src/main/java/nx/pingwheel/fabric/integration/`）；Fabric 的 6 个 services 分两个源集。

### 11.4 六个 SPI 逐项

`IPlatform*Service` 的 `INSTANCE` 都是 `ServiceLoader` 首次访问时加载（`common/src/main/java/nx/pingwheel/common/platform/IPlatformClientEventService.java:13-15`），所以“服务端不加载客户端实现”取决于服务端代码是否触碰客户端接口——Forge 侧成立（§11.8）。

| SPI 方法 | Forge 触发条件（`PlatformClientEventServiceImpl.java`） | 线程/频率 | Fabric / NeoForge 对端 | 判定 |
| --- | --- | --- | --- | --- |
| `registerTickStartEvent` | `TickEvent.ClientTickEvent.Pre`（:36-38） | client 主线程，每 client tick 1 次 | Fabric `START_CLIENT_TICK`；Neo `ClientTickEvent.Pre` | 等价；Forge 在 `Minecraft.tick()` 头之后（P2-6） |
| `registerJoinServerEvent` | `ClientPlayerNetworkEvent.LoggingIn`（:41-43） | client 主线程，每连接 1 次 | Fabric `JOIN`（handleLogin RETURN）；Neo `LoggingIn` | Forge/Neo 在 `handleLogin` 中途 fire（P2-7） |
| `registerLeaveServerEvent` | `LoggingOut`（:46-48） | client 主线程，每连接 1 次 | Fabric `DISCONNECT`；Neo `LoggingOut` | 清理幂等（`CommonClient.java:116-136`），P2-8 |
| `registerRenderFrameEvent` | `TickEvent.RenderTickEvent.Pre`（:51-53） | render 线程，每渲染帧 1 次（含主菜单） | Fabric `END_EXTRACTION`（有 level 才触发）；Neo `RenderFrameEvent.Pre` | 世界内等价（P2-5） |
| `registerHudRenderEvent`（common 调 2 次，`CommonClient.java:86-87`） | `AddGuiOverlayLayersEvent` + `ForgeLayeredDraw.POST_SLEEP_STACK`（:56-65），2 个独立 layer id | render 线程，每帧 2 层各 1 次 | Fabric `HudElementRegistry.addLast`；Neo `registerAboveAll` | 都接 2 次、顺序 marker→notice；F1/层叠不同（P2-9） |
| `registerWorldSubmitEvent` | 只存 `volatile` 回调（:27、:68-74）；由 `LevelRendererSubmitMixin` 在 `submitFeatures` RETURN 调 `dispatchWorldSubmit`（:81-115） | render 线程，每渲染帧 1 次 | Fabric `LevelRenderEvents.COLLECT_SUBMITS`；Neo `SubmitCustomGeometryEvent` | Forge 与 Fabric 同方法同注入点（P2-3） |

客户端 context 的 5 个 key（`PlatformContextServiceImpl.java`）：

| key | Forge 实现 | 判定 |
| --- | --- | --- |
| `getSelfModVersion` | `ModList.getModContainerById(MOD_ID)`（:14-18） | 等价（Fabric `FabricLoader` / Neo `ModList`）；mod 构造期可读 |
| `resolveGameDir` | `FMLPaths.GAMEDIR`（:20-23） | 等价 |
| `resolveConfigDir` | `FMLPaths.CONFIGDIR`（:25-28） | 等价（config 文件路径/迁移一致） |
| `isModLoaded` | `ModList.isLoaded`（:30-33） | 等价（DH/VoiceChat/FTB/Sable 的 fail-soft 入口） |
| `registerKeyMapping` | `RegisterKeyMappingsEvent.BUS`（`PlatformClientContextServiceImpl.java:9-12`） | 等价；5 个按键（`CommonClient.java:90-94`），options.txt 已见 5 个 key |

服务端 SPI（`PlatformServerEventServiceImpl.java`）：

| 方法 | Forge | 判定 |
| --- | --- | --- |
| `registerPlayerLogoutEvent` | `PlayerEvent.PlayerLoggedOutEvent` + `ServerPlayer` 过滤（:14-20） | Fabric 是“连接断开”；Forge/Neo 是“玩家登出”，都调 `ServerCore.onPlayerDisconnect`（`CommonServer.java:73-75`）→ 时序略不同，清理幂等（P2-8） |
| `registerServerTickEvent` | `TickEvent.ServerTickEvent.Post`（:23-25） | 等价（Fabric `END_SERVER_TICK` / Neo `Post`）；`ServerCore.onServerTick` 内按 `ACTIVE_SERVER` 重建 store |

### 11.5 客户端可观测效果矩阵

判定口径：先问“Forge 是否真的走到 common 同一路径”，再问“时机/次数/层叠是否一致”。每行同时给出最坏效果——若该项未接线，玩家会失去什么。本轮未发现未接线项；缺失项只有 §11.11 的可选集成 resolver。

| 可观测效果 | 走 common 同一路径 | 触发时机/次数 | 效果差异/风险 | 证据 |
| --- | --- | --- | --- | --- |
| HUD marker 层（图标/名字/距离/方向/状态） | 是：第 1 个 `registerHudRenderEvent` → `onRenderHud` | 每帧 1 次（HUD extract 阶段） | 单次绘制、界面之下；F1 时整层不画（`POST_SLEEP_STACK` gate） | `PlatformClientEventServiceImpl.java:56-65`；`ForgeLayeredDraw.java:450-465`；`CommonClient.java:298-326` |
| HUD toggle notice 层 | 是：第 2 个回调 → `onRenderToggleNotice` | 每帧 1 次，marker 之后 | 2s 不透明 + 1s 淡出由 common 状态机决定 | `CommonClient.java:86-87,340-355` |
| wheel（轮盘） | 是：`onRenderHud` 内 `WheelHudRenderer.render` | 仅 `WHEEL_OPEN`，每帧 | 依赖 `onRenderFrame` 推进；Forge 该回调世界内每帧 → 一致 | `CommonClient.java:317`；`latest.log:123-145` |
| F1 隐藏 HUD | 是（层挂 `POST_SLEEP_STACK`） | 隐藏期间不画（Forge） | **三家不同**：Forge 有 `!gui.isHidden()` gate，F1 时不画；Fabric 在 F1 + 打开 in-game UI 时**仍可能绘制（背景之后、控件之前）**（vanilla 仍 deferred 调 subtitle overlay，取决于 `Screen.isInGameUi()`）；Neo manager 无 gate、调用方未核实。不存在每帧两次绘制 | `ForgeLayeredDraw.java:450-465`；`Hud.java:223-252,262-269`；`HudElementRegistryImpl.java:74,85-88`；`Screen.java:445-447` |
| ping 反馈（本地 invalid-target / 服务端拒绝） | 是：common 运行时 + chat 组件 | 提交时/收到拒绝时 1 次 | 无 loader 分支；颜色 `#FF5555`、前缀 `[ping for it]` 由 common/资源决定 | `docs/UI/ping-feedback.md:9-58`；common `ClientPingRuntime` |
| chat / 名字输出 | 是：common `name` 包 | 收到新 marker 时 1 次 | 无 loader 分支；声音有同维度过滤、chat 无 | `docs/architecture/rendering/names_chat.md:5-9,76-94` |
| block outline（model glow / BER / shape） | 是：`BlockOutlinePass.onWorldSubmit` ← mixin bridge | 每帧 1 次（`submitFeatures` RETURN） | 与 Fabric 同点；`new PoseStack()` 等价（§11.6） | `LevelRendererSubmitMixin.java:38-51`；`latest.log:100,115-118` |
| entity outline | 是：`EntityRendererMixin` + `EntityBlockGlowState`（common） | 每帧；实体被 marker 命中时 | 与 Fabric/Neo 共用 common；Forge 另有 `BlockEntityRenderDispatcherMixin`/`SubmitNodeCollectionMixin` | common `mixin/*`；`debug.log` 9/9 `Mixing` |
| after-terrain / `improvedTransparency` | 是：`SubmitNodeCollectionMixin` 路由 render type | 每提交 1 次 | 与 Fabric 同提交点；Forge 的提交在 outline 桶清理之后，不会被清 | `SubmitNodeCollectionMixin.java:53-86`；patched `LevelRenderer.java:341-351` |
| world submit（outline 几何提交） | 是 | 每帧 1 次 | 见 §11.6 | 同上 |
| `/pingforit help`、`channel`、`config` | 是：`RegisterClientCommandsEvent` → `ClientCommandBuilder` | **每次登录**重建 dispatcher 时注册 | 与 Fabric（init 注册）等价；输出走 `sendSuccess`/`sendFailure` | `ForgeClient.java:60-67`；`ClientCommandHandler.java:44-64` |
| 配置界面打开/保存 | 是：`ConfigScreenHandler.ConfigScreenFactory` → `SettingsScreen` | mods 列表按钮，打开 1 次 | Forge 与 Neo 等价；Fabric 当前无 | `ForgeClient.java:69-72`；`docs/UI/settings-screen.md:70,86` |
| reload listener | 是：`RegisterClientReloadListenersEvent` → `ResourceReloadListener` | 构造期注册；资源包 reload 时执行 | 只影响自定义 ping 贴图数量；Forge API 无稳定 id（P3-4） | `ForgeClient.java:57-58` |
| 声音播放 | 是：`DeferredRegister<SoundEvent>` 注册 `pingforit:ping` | 双端注册；每个新 marker 1 次 | 与 Fabric（仅 client 注册）/Neo（双端）等价；ID/ogg 一致 | `ForgeMain.java:39-44,76`；`ResourceConstants.java:13-14` |
| rate limit / syncDuration | 是：S2C handler 已注册，policy 存 client | 收到即存；join 时重置 | 与 Fabric/Neo 同；多人未验证 | `ForgeClient.java:52-55`；`CommonClient.java:104-105,580-612`（join 重置 + 收包存值） |

### 11.6 outline / world submit

- **注入点**：`LevelRendererSubmitMixin` 在 `submitFeatures(LevelRenderState, SubmitNodeCollector, boolean)` 的 `RETURN`（`LevelRendererSubmitMixin.java:38-51`，`require = 0`）。patched vanilla 顺序：`submitEntities` → `submitBlockEntities` → block outline（`LevelRenderer.java:341`）→ gizmos（:344-346）→ `!shouldShowEntityOutlines` 时清理 outline 桶（:347-351）→ `checkPoseStack`（:353）→ RETURN。
- **Fabric 对照（源码级）**：`fabric-rendering-v1` 的 `LevelRendererMixin` 用 `@ModifyExpressionValue` 抓 `submitFeatures` 里的 `NEW PoseStack`（`:84-88`），并在同方法 `@At("RETURN")` 触发 `COLLECT_SUBMITS`（`:90-93`）。所以 Forge 与 Fabric **同方法、同注入点**；`BlockOutlinePass.java:224-226` 的注释也以这个顺序为前提。
- **PoseStack 等价性**：vanilla 在 RETURN 前用 `checkPoseStack` 断言局部栈为空（`LevelRenderer.java:353`），`BlockOutlinePass` 只 `push/translate/pop`（`BlockOutlinePass.java:242-254`），所以 Forge 的 `new PoseStack()` 与该空栈状态等价；camera 取 `gameRenderer.mainCamera()`、partialTick 传 `0.0f`（Fabric 适配器同样不读它）。
- **after-terrain / improvedTransparency**：`SubmitNodeCollectionMixin` 把自定义 render type 提交到 `afterTerrain`（经典半透明）或 `outline` 相位（`improvedTransparency` 打开时，`:72-83`）。Forge 的提交在 RETURN，晚于 vanilla 的 outline 桶清理；因此 `shouldShowEntityOutlines == false`（主菜单全景/无玩家）时 Forge/Fabric 的提交不被清，NeoForge 更早的事件提交会被清（P2-4，边缘场景）。
- **异常隔离**：`dispatchWorldSubmit` 捕获 `RuntimeException | LinkageError`，每会话只 WARN 一次（`PlatformClientEventServiceImpl.java:106-114`），首帧打一次 INFO（:96-99）——这是 `require = 0` 静默失效时唯一的正信号。
- **静默失效风险**：描述符漂移时 mixin 不注入、不报错，日志没有 `bridge active`。既有日志已出现该行（`latest.log:100`），但 F5 仍必须把“bridge active + submitted > 0 + 视觉”作为门禁。

### 11.7 网络与线程

**14 条路由（8 S2C + 6 C2S）全部注册**，名称 = common 的 packet ID（`pingforit-c2s:*` / `pingforit-s2c:*`）：

- Forge：`ForgeMain.java:46-73` 创建 14 个 `ChannelBuilder.named(id).optional().eventNetworkChannel()`；`:87-101` 填 `CHANNEL_MAP`；`:103-114` 注册 6 个 C2S listener；8 个 S2C listener 在 `ForgeClient.java:40-55`。Fabric 由 `FabricMain.java:45-58`（2 个流）+ `FabricClient.java:38-79` 注册；NeoForge 由 `NeoMain.java:71-103`（6 C2S）+ `NeoClient.java:62-79`（8 S2C）。**覆盖无缺失**。
- **optional / presence**：Forge 每条 channel 都 `.optional()`（= optionalServer + optionalClient），即对端缺该 channel 也能连，与 Fabric payload type / NeoForge `.optional()` 语义等价。presence 检查：Forge `channel.isRemotePresent(connection)`（`PlatformNetworkServiceImpl.java:23`、`ClientNetworkServiceImpl.java:23`），Fabric `canSend`，NeoForge `connection.hasChannel`。
- **线程**：Forge channel listener 在 network 线程触发；服务端 `context.enqueueWork`（`ForgeMain.java:129`）转到 server 主线程；客户端 `enqueueWork`（`ForgeClient.java:88`）转到 client 主线程。与 Fabric `client.execute()`/`server.execute()`、NeoForge 默认 MAIN 线程一致。
- **`setPacketHandled`**：客户端 handler 在 `finally` 置 true（`ForgeClient.java:90-95`）；服务端 handler 在 `if` 块之后置 true（`ForgeMain.java:132`），没有 `finally`（P3-2）。因为 `readSafe` 对坏包返回 corrupt sentinel 而不抛（`PacketHandler.java:16-33`），实际漏置概率极低。
- **join 握手**：`CommonClient.onJoinServer` 发 `UpdateChannelC2SPacket`（`CommonClient.java:113`）→ `ClientNetworkServiceImpl.sendToServer` 先查 `isRemotePresent`（:23-30）。既有日志 `Channel update: Dev -> default`（`latest.log:97`）证明单机连接下握手成功。
- **leave / 重连清理**：`LoggingOut` → `CommonClient.onLeaveServer`（清 runtime、marker store、render lookup、notice、hold，通知 `SettingsScreen`，`CommonClient.java:116-136`）；与 Fabric/Neo 同一路径 → 重连清理等价。
- **专用服务器类加载边界**：`ForgeMain` 只 import 服务端安全类；客户端实现只在 `ForgeClient`（经 `ForgeClientBootstrap` 间接）与 `PlatformClientEventServiceImpl`（只由 client 侧 SPI 触碰）里；6 个 services 文件不强制加载。`runs/forge-server/logs/latest.log`：`[PingForIt] Init`、`Loaded config type=ServerConfig`、`Done (0.259s)`，无客户端类错误。
- **与 payload type 的语义差异（loader 必要）**：Forge 通道传裸 `FriendlyByteBuf`，不用 `CustomPacketPayload` codec；可靠性与 Fabric/Neo 等价（`readSafe` 容错 + corrupt sentinel）。差异只在握手形状：Forge 是 14 个 `NetworkInstance`（server-list ping 的 channel 条目 14 个），NeoForge 是 1 个 registrar（`pingforit`）→ 用户不可见。

### 11.8 服务端

- `CommonServer.onInit`（`CommonServer.java:27-43`）：`ServerConfig.HANDLER.load()` → `ModContext.indexMods()` → `ServerCore.init()` → 一次性注册 `initMarkers`、logout、server tick。Forge 在 `ForgeMain` 构造期调用一次（`ForgeMain.java:78`），与 Fabric main init / Neo 构造等价；`serverEventsRegistered` 防重复（`CommonServer.java:37-42`）。
- **C2S 入口**：6 个 handler 全部经 `enqueueWork` 到 server 线程，转 `CommonServer.INSTANCE.on*Packet(server, player, packet)`（`ForgeMain.java:103-114,122-133`）——服务端权威路径与 Fabric/Neo 完全同一份 common 代码（`ServerCore`）。
- **server tick / logout**：`ServerTickEvent.Post` → `ServerCore.onServerTick`（过期、winner 同步、5 tick 外部刷新）；`PlayerLoggedOutEvent` → `ServerCore.onPlayerDisconnect`（移除 owner markers、同步、清 channel/rate/syncDuration）。两者都在 server 线程。
- **server config**：`ServerConfig.HANDLER.load()` 在构造期；config 更新经 `ServerConfigUpdateC2SPacket` → `ServerCore.onServerConfigUpdate`；快照回包走 `IPlatformNetworkService.sendToClient`。Forge 的 `sendToClient` 有 presence 门控（`PlatformNetworkServiceImpl.java:20-30`），与 Fabric/Neo 等价。
- **专用服务器不加载客户端类**：设计上成立（§11.7）；既有 `runServer` 日志无客户端类错误；服务端日志也没有 `PlatformClient*Service` 的加载失败记录。
- **与 Fabric/Neo 的差异**：只有事件家族（logout/tick）和线程转交方式（`enqueueWork` vs `server.execute`），语义等价（P2-8）。

### 11.9 配置、命令、reload、声音与按键

- **配置加载**：`ClientConfig.HANDLER.load()` 在 `CommonClient.onInit`（`CommonClient.java:73`）；`ServerConfig.HANDLER.load()` 在 `CommonServer.onInit`（`CommonServer.java:32`）。Forge 分别发生在 `ForgeClient.init`（mod 构造期）与 `ForgeMain` 构造期；config dir 来自 `FMLPaths.CONFIGDIR`。既有日志两行 `Loaded config type=ServerConfig/ClientConfig`（`latest.log:38-39`）。
- **客户端命令**：`RegisterClientCommandsEvent`（`ForgeClient.java:60-67`）→ `ClientCommandBuilder.build(...)`（`/pingforit help`、`/pingforit channel`、`/pingforit config`）。Forge 的该事件在每次登录重建 dispatcher 时 post（`ClientCommandHandler.java:44-64`），不会重复注册；输出 `sendSuccess(..., false)` / `sendFailure`，在客户端 source 上都只加本地聊天行。
- **reload**：`RegisterClientReloadListenersEvent`（`ForgeClient.java:57-58`）在 `Minecraft` 构造期注册；`ResourceReloadListener` 只更新自定义贴图计数（`ResourceReloadListener.java:26-34`）。Forge API 不取 id → P3-4；行为等价。
- **声音**：`DeferredRegister<SoundEvent>` 注册 `pingforit:ping`（`ForgeMain.java:39-44`，`:76` 挂 mod bus）；播放由 common 的 `DirectionalSoundInstance(PING_SOUND_EVENT, ...)`（`ClientPingRuntime.java:1037-1043`），且有同维度过滤（:1031）。资源 `assets/pingforit/sounds.json` + `sounds/ping.ogg` 已在 jar 内。
- **按键**：`RegisterKeyMappingsEvent.BUS`（`PlatformClientContextServiceImpl.java:9-12`）在 `Minecraft` 构造期 fire；common 注册 5 个（`CommonClient.java:90-94`），`runs/forge-client/options.txt` 已出现 5 个 `key_key.pingforit.*`。按键边沿由 common `KeyMappingMixin` 桥接，Forge mixin 列表包含它且运行日志已 `Mixing`（`debug.log:233`）→ 与 Fabric/Neo 同一路径；**实际按键行为属 F5**。
- **配置界面**：`ConfigScreenHandler.ConfigScreenFactory`（`ForgeClient.java:69-72`）→ `SettingsScreen(parent)`；Forge 与 Neo 等价，Fabric 当前无（基线不对称，见 §11.3）。

### 11.10 资源、metadata、构建与发布物

#### 11.10.1 jar 事实（本轮直接读取 `forge/build/libs/Ping-For-It-0.4.2-pfi-beta1-forge-26.3.jar`，2026-11-17 13:12，未重建）

- **822548 B / 504 entries / 432 classes / 9 mixins / 6 services**；条目/类数与 §1.6 的 F4 构建相同，字节数以 §11.16.2 的 `displayTest` 重建结果为准。
- `META-INF/mods.toml`：`loaderVersion = "[24,)"`、`displayTest = "IGNORE_SERVER_VERSION"`（L14）、forge 依赖 `versionRange = "[66,)"`、minecraft `versionRange = "[26.3]"`、`logoFile = "icon.png"`；模板 `forge/src/main/templates/META-INF/mods.toml:14` 与 generated/jar 一致。
- `pack.mcmeta`：`min_format` / `max_format` = 121（`forge/src/main/resources/pack.mcmeta:3-5`）；既有日志 `Reloading ResourceManager: ... mod:pingforit`（`latest.log:42`）证明资源包被接受。
- mixin：Forge 本地 9 条（8 common + `LevelRendererSubmitMixin`），`compatibilityLevel: "JAVA_21"`、`required: true`、`defaultRequire: 1`、无 refmap（`forge/src/main/resources/pingforit.mixins.json:1-20`）；common/NeoForge 为 8 条 `JAVA_25`。运行日志 9/9 `Mixing`：启动期 8 条（`debug.log:233,235,284,286,288,299,301,304`）+ 世界期 1 条 `SubmitNodeCollectionMixin`（`:661`）；`Preparing pingforit.mixins.json (9)` 在 `:198`。
- 资源：`assets/pingforit/`（lang ×8、`sounds.json`、`sounds/ping.ogg`、`textures/ping.png`、`textures/arrow.png`）、`icon.png`、`LICENSE_pingforit`、`THIRD-PARTY-NOTICES.md` 均在 jar；无 `assets/pingwheel`/旧 mixin 名。
- 构建接线（`forge/build.gradle`）：`evaluationDependsOn(:common)`（:8）、common 依赖 `transitive=false`（:42-45）、metadata Sync + expand（:69-75）、`processResources` 合并 common 资源并排除 common mixin 配置（:77-86）、`prepareDevMod`（:97-118）、run 类路径替换（:120-141）、`jar`（:143-157）、`test` 禁用（:160-162）。与 Fabric/NeoForge 的“单 jar 合并”结构一致。

#### 11.10.2 非 mod 缺陷/环境噪声（6，不计入 parity）

1. `Class version 69 ... JAVA_21 supports class version 65` ×9（`debug.log`）：mixin 0.8.7 上限；9/9 仍 `Mixing` 且 outline 工作。升级 mixin 栈需单独评估，不属 parity 缺陷。
2. `Requested post effect does not exist: minecraft:end_of_frame`：vanilla 26.3 已知 bug MC-310992（见 §1.7）。
3. oshi WMI COM 异常 / Realm 认证失败 / `Assets URL ... unexpected schema`：环境噪声；2026-11-16 的 runClient `latest.log` 共 6 类 / 10 行 WARN/ERROR，全部非 mod 噪声（pingforit 自身 0 条）。
4. Forge `66.1.0` 是迁移钉死版本（最新已更高）：升级属依赖变更，不是 parity 缺陷。
5. 旧 1.21.1 jar 与旧 sources/javadoc 是方案 A 会话的本地清理（未提交；`build/` 被 ignore，`14c44f9` 本身只改 `mods.toml`）；当前 `forge/build/libs` 只有 26.3 jar（本轮 `Get-ChildItem` 复核）。
6. `mods.toml` 的 `minecraft versionRange="[26.3]"` 是精确区间，正确；不是缺陷。

### 11.11 可选集成

| 集成 | NeoForge（in-tree） | Forge | docs 权限 | 结论 |
| --- | --- | --- | --- | --- |
| Create door presentation | `CreateDoorPresentationResolver`（只 import vanilla+common；`CREATE_DOOR_IDS` + `DoorBlock && EntityBlock`，复用 vanilla door resolver，加 lower→upper coverage relation），含单测 | 无 `integration` 包 | `docs/integrations/create.md:1` = “NeoForge Create integration” | **P1-2（已裁决 B：不纳入）**：装 Create 时 Forge 门上半可能多一圈 wireframe；resolver 无 Create 类依赖，可移植 |
| Simulated paired docking connector | `SimulatedDockingConnectorPresentationResolver`（`BuiltInRegistries` 查 `simulated:*`，验证 FACING 对向 + POWERED + BE，返回 `PROXY_TO_OWNER` 或 handled-empty），含单测 | 无 | `docs/integrations/simulated.md:3` = “optional, NeoForge-only” | **P1-2（已裁决 B：不纳入）**：装 Simulated 时 Forge 不会把配对连接器代理到 owner；无 mod 类依赖，可移植 |
| join 时重试 | `NeoClient.java:54-58` 在 join 时再次 `loadCreateDoorResolver()` / `loadSimulatedResolver()`；`:99-119` 为 `isModLoaded` + 注册 + 反射/失败 WARN（111-119） | 无 | 同上 | 已裁决不纳入；若未来改为 A（纳入），Forge 需要 join 重试（mod 可能在首次加载时未就绪） |
| Create contraption raycast / entity outline / Flywheel / WaterWheel / Create mixin | 隔离区（`mod-source-isolated/neoforge/src/...`），未 in-tree | 无 | create.md 三路由；当前 Neo 也延后 | 不是 Forge 独缺；不纳入本轮 |
| WorldAware block model | 隔离区 | 隔离区 | §6.2 明确延后 | 三端同层 |
| Sable / DistantHorizons / VoiceChat / FTBTeams | common 反射 + `isModLoaded` + fail-soft（`ModContext.java:15-28`） | 同（`PlatformContextServiceImpl.isModLoaded` 已实现） | compatibility.md 的 fail-soft 条款 | 机制一致；未验证 |

结论：Forge 缺的两个 resolver **只 import common+vanilla**（`CreateDoorPresentationResolver.java:8-19`、`SimulatedDockingConnectorPresentationResolver.java:6-21`），没有 26.3 Create/Simulated 工件阻塞。用户已于 2026-11-18 裁决 **B：不纳入**；Forge 保持无 `integration` 包，按 docs 的 NeoForge-only 范围执行，不修代码。

### 11.12 行为差异清单

#### 11.12.1 P1（未关闭 = 1）

| # | 项 | 可观测后果 | 证据 | 状态/性质 |
| --- | --- | --- | --- | --- |
| P1-1 | `mods.toml` 缺 `displayTest` | 修复前：客户端/服务端 pingforit 版本不一致时，默认 `MATCH_VERSION` 让服务器列表/连接出现版本不匹配 | 模板 `mods.toml:14`；jar `META-INF/mods.toml` L14；fmlcore `ModContainer.java:51-66` | **已关闭**（2026-11-17，`14c44f9`）：迁移丢失已恢复 |
| P1-2 | Forge 缺 Create door / Simulated docking resolver | 装 Create：门上半可能多一圈 wireframe（无 lower→upper coverage）；装 Simulated：配对连接器不代理到 owner，outline/名字目标可能不同 | Neo 两个 resolver + 两个测试；Forge 无 `integration`；docs NeoForge-only | **已关闭（2026-11-18 用户裁决 B：不纳入）**：按 docs NeoForge-only 保持现状；不是缺陷，不修代码 |

#### 11.12.2 P2（14）

| # | 项 | Forge 做法/效果 | 对照与后果 | 证据 | 分类 |
| --- | --- | --- | --- | --- | --- |
| P2-1 | 网络传输 | 14 个 `EventNetworkChannel` + 裸 `FriendlyByteBuf` | Fabric payload type/StreamCodec；Neo 1 registrar + payload → 包 ID/方向/optional/主线程等价，握手条目形状不同 | `ForgeMain.java:46-114`；`FabricMain.java:45-58`；`NeoMain.java:71-103` | loader 必要 |
| P2-2 | presence 门控 | `channel.isRemotePresent(connection)` | Fabric `canSend`；Neo `hasChannel` → 对 vanilla/缺 mod 对端都不发，等价 | `PlatformNetworkServiceImpl.java:23`；`ClientNetworkServiceImpl.java:23` | 等价 |
| P2-3 | world submit 注入点 | `submitFeatures` RETURN + `new PoseStack()` | Fabric 同方法 RETURN；Neo 事件更早 → Forge == Fabric，空栈等价 | `LevelRendererSubmitMixin.java:38-51`；Fabric `LevelRendererMixin.java:84-93`；patched `LevelRenderer.java:329-354` | loader 必要 |
| P2-4 | after-terrain 桶清理 | 提交在 `!shouldShowEntityOutlines` 清理之后，不被清 | Fabric 同；Neo 事件在清理之前 → 主菜单全景/无玩家时 Neo 可能丢提交，Forge 不会 | patched `LevelRenderer.java:347-351`；`SubmitNodeCollectionMixin.java:72-83` | 边缘 |
| P2-5 | render frame 事件 | `RenderTickEvent.Pre`：extract 后、render 前，每帧；无 level 也触发 | Fabric `END_EXTRACTION` 只在有 level；Neo 每帧 → 世界内等价，菜单里多跑 common 回调（自行 guard） | patched `Minecraft.java:1309,1316-1318`；`CommonClient.java:490-504` | 边缘 |
| P2-6 | tick start 位置 | `ClientTickEvent.Pre` 在 `Minecraft.tick()` 头之后 | Fabric/Neo 在 tick 头 → 回调只读世界状态，无可观测差异 | patched `Minecraft.java:1819-1830` | 可忽略 |
| P2-7 | join 时机 | `LoggingIn` 在 `handleLogin` 中途（level/player 已设，尚未 setId/addEntity） | Fabric 在 handleLogin RETURN；Neo 同 Forge → runtime 提前创建、`UpdateChannelC2SPacket` 提前发；需实跑确认同 tick 内 marker 包 | patched `ClientPacketListener.java:524-537`；Fabric `ClientPlayNetworkAddon.java:60` | 边缘 |
| P2-8 | leave 时机 | `LoggingOut` 在 `Minecraft.clearClientLevel`（level 未置 null） | Fabric `DISCONNECT` 在连接层；Neo 同 Forge → 清理幂等；建集成服务器时也可能 fire | patched `Minecraft.java:2192`；`CommonClient.java:116-136` | 等价 |
| P2-9 | HUD 宿主/层叠/F1 | `POST_SLEEP_STACK`（`!gui.isHidden()`）；单次、界面之下 | Fabric 挂 SUBTITLES 根，F1 + in-game UI 仍可能绘制（背景之后、控件之前）；Neo manager 无 gate、调用方未核实 → 三家门控不同，Forge 最严格、非回归 | `PlatformClientEventServiceImpl.java:56-65`；`ForgeLayeredDraw.java:450-465`；`Hud.java:223-252`；`HudElementRegistryImpl.java:74,85-88` | 跨 loader 视觉差异 |
| P2-10 | 按键注册 | `RegisterKeyMappingsEvent` | Fabric `KeyMappingHelper`；Neo mod bus → 等价 | `PlatformClientContextServiceImpl.java:9-12` | 等价 |
| P2-11 | 声音注册范围 | 双端 `DeferredRegister`（mod bus） | Fabric 仅 client 直接注册；Neo 双端 → 音频等价 | `ForgeMain.java:39-44,76`；`FabricClient.java:46`；`NeoMain.java:53-61` | 等价 |
| P2-12 | 客户端命令注册 | 每次登录重建 dispatcher 时注册 | Fabric client init 注册；Neo 事件 → 行为等价，不会重复根命令 | `ForgeClient.java:60-67`；`ClientCommandHandler.java:44-64` | 等价 |
| P2-13 | 配置界面 | `ConfigScreenHandler.ConfigScreenFactory` | Neo `IConfigScreenFactory`；Fabric 当前无 → Forge/Neo 等价，Fabric 缺（基线） | `ForgeClient.java:69-72`；`NeoClient.java:49-52` | 基线 |
| P2-14 | mixin 配置/风险 | 9 条、`JAVA_21`、`require=0`（world submit 与 `SubmitNodeCollection` 两处静默失效点） | Fabric/Neo 8 条、`JAVA_25` → 描述符漂移会静默不画；唯一正信号 `bridge active`/`Mixing` | `pingforit.mixins.json:1-20`；`LevelRendererSubmitMixin.java:38-43`；`SubmitNodeCollectionMixin.java:53,102` | loader 必要 + 风险 |

#### 11.12.3 P3（12）

| # | 项 | 现状 | 证据 | 建议/性质 |
| --- | --- | --- | --- | --- |
| P3-1 | C2S 缺 `ServerPlayer`/server 守卫 | **已修**（2026-11-18，`224e0c2`）：`enqueueWork` 内取 `sender.level().getServer()` 并判空 | `ForgeMain.java:122-141`；Neo `NeoMain.java:105-112` 有守卫 | 已完成 |
| P3-2 | 服务端 `setPacketHandled` 不在 `finally` | **已修**（2026-11-18，`224e0c2`）：`setPacketHandled(true)` 移入 `finally` | `ForgeMain.java:139-141` | 已完成 |
| P3-3 | world submit 单槽 `volatile` | **已修**（2026-11-18，`224e0c2`）：`CopyOnWriteArrayList` 多监听，逐回调独立 catch | `PlatformClientEventServiceImpl.java:24-35,89-140` | 已完成；重入会累积，见 P3-7 |
| P3-4 | reload listener 无 id | Forge API 不取 id | `ForgeClient.java:57-58` | API 限制，不修 |
| P3-5 | client context 用 `extends` | 而非 delegate | `PlatformClientContextServiceImpl.java:7` | 风格 |
| P3-6 | `ForgeClientBootstrap` 间接层 | 防御性 | `ForgeClientBootstrap.java:15-21` | 保留 |
| P3-7 | `CommonClient.onInit` 无 once guard | `CommonServer` 有 `serverEventsRegistered` | `CommonClient.java:72-97`；`CommonServer.java:37-42` | 当前只调用一次；未来多路径调用需 guard |
| P3-8 | 打包卫生 | `pack.mcmeta` int vs Neo 数组；manifest 只有 `MixinConfigs` | jar 内 `pack.mcmeta`、`META-INF/MANIFEST.MF` | 不影响行为 |
| P3-9 | `displayTest` 在 NeoForge inert | Neo 26.3 sources 0 命中 | `neoforge.mods.toml:14` | 不要以“对齐 Neo”为理由；文档注意 |
| P3-10 | `ModIdentityTest` 缺 `SyncDurationPolicyS2CPacket` | **已修**（2026-11-18，`224e0c2`）：补第 14 个包并改名 `allFourteenPacketIdsUseNewNamespaces` | `ModIdentityTest.java:24,63-94`；聚焦 7 tests / 全量 1193 tests 通过 | 已完成 |
| P3-11 | Fabric 缺配置界面 | 基线不对称 | `fabric/src` 无 `ConfigScreen`/`ModMenu` 引用；`ModMenuIntegration` 在隔离区 | 不是 Forge 缺陷 |
| P3-12 | dev manifest / `prepareDevMod` | 只服务 dev | `forge/build.gradle:97-118` | 构建期，不改发布物 |

#### 11.12.4 loader 必要差异（8，不计缺陷）

1. `EventNetworkChannel`/`ChannelBuilder` 取代 payload type 注册（Forge 26.3 仍提供，且是当前唯一稳定路径）。
2. `LevelRendererSubmitMixin` 取代 `COLLECT_SUBMITS`/`SubmitCustomGeometryEvent`（Forge 没有同时带 `PoseStack` + `SubmitNodeCollector` 的事件；注入点与 Fabric 相同）。
3. `ForgeLayeredDraw.POST_SLEEP_STACK`（Forge 用 layered draw 取代 vanilla HUD 分支；root 没有 `!isHidden`）。
4. 静态 `BUS`：`RegisterKeyMappingsEvent` / `RegisterClientReloadListenersEvent` / `AddGuiOverlayLayersEvent` / `RegisterClientCommandsEvent`（eventbus 7；前三者在 `Minecraft` 构造期 fire 且 `SelfDestructing`）。
5. `ClientPlayerNetworkEvent.LoggingIn/LoggingOut` + `PlayerEvent.PlayerLoggedOutEvent`（Forge 无 Fabric 的连接事件）。
6. `compatibilityLevel = JAVA_21`（vanilla mixin 0.8.7 上限，不是 toolchain 版本）。
7. dev 目录 `META-INF/MANIFEST.MF` + `prepareDevMod`（ForgeGradle 7 类路径模型；目录型 dev mod 无 jar manifest）。
8. `ForgeClientBootstrap` 间接层（防御性；Java 符号解析本就惰性，保留不影响行为）。

#### 11.12.5 “完全不同的实现”归类

| 项 | 归类 | 说明 |
| --- | --- | --- |
| 14 × `EventNetworkChannel` 裸缓冲 | loader 必要 | 包 ID/方向/optional/线程语义等价 |
| world submit mixin | loader 必要 | 与 Fabric 同注入点；Neo 事件更早 |
| HUD layered draw | loader 必要 + 更严格 | 顺序等价；F1/界面层叠三家不同 |
| 客户端注册在 mod 构造期 | loader 必要 | `SelfDestructing` 事件在 `Minecraft` 构造期 fire |
| `prepareDevMod` / dev manifest | 构建期 | 只影响 dev run |
| `displayTest` | 迁移丢失（已修） | 只有 Forge 端生效 |
| Create/Simulated resolver | 范围决定 | 可选内容效果差异 |

### 11.13 对旧 §11（2026-11-16）的修正与新增

#### 11.13.1 修正（C1–C8）

1. **HUD / F1（旧 §11 #6）：方向保留，但理由与两轮表述都要改。** 旧文引 `HudElementRegistry.java:30-36` 的 API javadoc，说 Fabric `addLast`“不继承 render condition”；方向对（Fabric 的层挂在 SUBTITLES 根，F1 隐藏 + 打开 in-game UI 时可能仍绘制），但机制要按 vanilla `Hud.java` 重写：`extractRenderState` 在 `!isHidden` 时走 `extractSubtitleOverlay(graphics, screen == null || screen.isInGameUi())`（`:247`）；在 `isHidden && screen != null && screen.isInGameUi()` 时走 `:248-249`，**仍然** `extractSubtitleOverlay(graphics, true)`；`extractSubtitleOverlay`（`:262-269`）把 `SubtitleOverlay.extractRenderState` 放进 `deferredSubtitles`，由 vanilla 在 `Screen.extractBackground`（`Screen.java:401`）执行，即背景之后、控件之前。因此 **F1 隐藏 + 打开 in-game UI 时 Fabric 的层仍可能绘制（背景之后、控件之前）**；是否触发取决于该界面的 `Screen.isInGameUi()`（默认 false，`Screen.java:445-447`；`AbstractContainerScreen.isInGameUi():560-562` 返回 true），需 F5 实跑。旧分析稿曾把它误判为“F1 时也不绘制”，现按 vanilla `Hud.java`/`Screen.java` 源码更正；也不存在每帧两次绘制——vanilla 每帧只调用一次 `extractSubtitleOverlay`，deferred 只执行一次。Forge 的层在 `ForgeLayeredDraw.POST_SLEEP_STACK`，该 stack 有 `!gui.isHidden()` gate（`ForgeLayeredDraw.java:450-465`），F1 时确实不画、开界面时在界面之下。级别维持 P2（跨 loader 视觉差异），不修。
2. **NeoForge“无 F1 门控”被过度断言（旧 §11 #6）：** `GuiLayerManager.renderInner`（`:73-80`）确实没有 gate，但调用方（NeoForge patched `Gui`）不在可读源码内 → 降为“manager 无 gate、调用方未核实”，不得写成已核实。
3. **P1-1 `displayTest` 已关闭且语义复核：** 模板 L14 + jar L14 = `IGNORE_SERVER_VERSION`；fmlcore `ModContainer.java:51-66` 缺省 `MATCH_VERSION`；**NeoForge 26.3 sources 0 命中** → “不能以对齐 NeoForge 为理由”成立。P1 未关闭数 = 0（P1-2 经 2026-11-18 裁决 B 关闭）。
4. **render frame 频率（旧 §11 #5）：** “语义等价”在世界内成立；但 Forge/Neo 在**主菜单/无 level 时也每帧触发**，Fabric `END_EXTRACTION` 只在有 level 的 extract 阶段触发。common 回调自行 guard（`CommonClient.java:490-504`）→ 无可观测差异（P2-5）。
5. **world submit 证据升级（旧 §11 #4）：** 本轮从 Fabric API 源码确认 `@ModifyExpressionValue(NEW PoseStack)` + `@At("RETURN")`（`LevelRendererMixin.java:84-93`），并给出 patched `LevelRenderer.java:329-354` 的 outline 清理行号；“同一注入点”成立，补上 after-terrain 清理交互（P2-4）。
6. **`setPacketHandled` 理由（旧 §11 #22）：** 原按“防御性”记 P3；实际 `readSafe` 对坏包返回 corrupt sentinel、不抛（`PacketHandler.java:16-33`），漏置 true 只在 payload/sender 为 null 的极端路径。结论仍是 P3，风险描述更准确。
7. **C2S 守卫（旧 §11 #16）：** 保留 P3；`sender.level().getServer()` 在 enqueue 后取值，`ServerPlayer` 存活期非 null，仅理论边界。
8. **计数修正：** 旧 P1 = 2（含已修）/ P2 = 14 / P3 = 8 → 本轮 **P1 open = 0（P1-2 裁决 B 关闭）、P2 = 14、P3 = 12**。P3 新增来自 `CommonClient.onInit` 无 once guard、打包卫生、Fabric 配置界面缺失、dev manifest 等。

#### 11.13.2 新增（A1–A7 = 条目 9–15，旧报告未覆盖）

9. **join 时机：** Forge/Neo `LoggingIn` 在 `handleLogin` 中途（patched `ClientPacketListener.java:524-537`，玩家尚未 `addEntity`/`setId`），Fabric 在 handleLogin RETURN（`ClientPlayNetworkAddon.java:60`、`ClientPacketListenerMixin.java:54`）。当前无已知差异，但同 tick 内 marker 包/Runtime 创建顺序需要实跑（P2-7）。
10. **HUD 宿主：** Forge 层由 `Hud.extractRenderState` → `ForgeLayeredDraw.extractRenderState`（`Hud.java:223-252`）驱动，仅 `LevelLoadingScreen` 跳过 → 开普通界面时 HUD 仍在界面之下绘制。
11. **客户端命令每次登录注册**（`ClientCommandHandler.java:44-64`，每次 new dispatcher），不是 init 期一次性。
12. **jar 事实：** 504 entries / 432 classes / 9 mixins / 6 services / `displayTest` L14 / `pack.mcmeta` 121 / 无旧资源或协议命名空间（Java 包保留 `nx.pingwheel`）/ assets + LICENSE + THIRD-PARTY 齐全（本轮直接读 jar）。
13. **服务端证据：** `runs/forge-server/logs/latest.log` 的 `[PingForIt] Init`、`Loaded config type=ServerConfig`、`Done (0.259s)`（旧 §11 未引用这份日志）。
14. **Fabric 无配置界面：** `fabric/src` 无 `ConfigScreen`/`ModMenu` 引用（本轮 grep 0 命中）；`ModMenuIntegration` 仍在 `mod-source-isolated/fabric/...`。
15. **旧报告引用错位：** 旧文“Fabric addLast 不继承条件”引的是 API javadoc，不是实现；实现是 `HudElementRegistryImpl.java:74,85-88`（本轮补）。这与 C1 一起构成本轮对 HUD/F1 的最终表述。

### 11.14 未验证 / 需 F5 实跑

| 项 | 现状 | 需要的证据 |
| --- | --- | --- |
| HUD marker 层视觉（图标/名字/距离/方向/状态） | 静态：2 层已注册；HUD 不写正信号日志 | 进世界观察/截图（F5） |
| toggle notice 视觉/时序 | 静态：第 2 层 + common 状态机 | 按两个 toggle 键观察 2s 不透明 + 1s 淡出 |
| F1 隐藏 HUD | 源码：Forge 有 `!gui.isHidden()` gate | 按 F1 观察 marker/wheel 是否消失；再开背包/聊天确认界面层叠 |
| wheel 视觉/选择/取消 | 日志有 10 次 open/close（`latest.log:123-145`），非视觉 | 开轮盘点选 3 种 ping、中心取消、超时 |
| ping 声音（音量/方向/同维度过滤） | 静态：`DirectionalSoundInstance` + 维度过滤 | 听觉确认；异维度 marker 不应发声 |
| `/pingforit help` / `channel` / `config` 输出 | 静态：注册路径已接 | 游戏内执行看聊天行/前缀/颜色 |
| 配置界面打开/保存/server settings | 静态：extension point + common screen | 打开 mods 列表配置按钮、改值、重连 |
| 自定义 ping 贴图 reload | 静态：`ResourceReloadListener` 已注册 | 放自定义 `textures/ping.png` 后 reload |
| 5 个按键实际行为 | options.txt 已见 5 个 key；mixin 已 `Mixing` | 按住/wheel/取消/2 个 toggle |
| block outline 各路由视觉 | F4 已确认普通方块/门床/entity_block | 补 chest、shape fallback、`improvedTransparency=ON` |
| entity outline 视觉 | F4 日志 `entity outline pass active` | 实体 ping 看轮廓颜色/移除 |
| after-terrain / 半透明遮挡 | 静态：与 Fabric 同提交点 | 开 `improvedTransparency`、隔墙观察 |
| 多人（双 Forge 客户端、专用服务器） | 单机证据只有 `Channel update`/marker created | winner/限流/syncDuration/重连/两客户端 |
| 专用服务器 + Forge 客户端 | `runServer` 只证明无客户端类错误 | 真连接后的 S2C/C2S 往返 |
| Create/Simulated 可选集成 | 未实现 | 若纳入范围：装 mod 后看门/连接器 |
| `displayTest` 修复后的版本不一致表现 | 只有 jar 字段证据 | 版本不一致的 client/server 列表/连接 |
| Fabric/NeoForge 的 F1/界面层叠对照 | 静态推导（`Hud.java` 分支 + `GuiLayerManager` gate），旧分析稿曾误判（已按源码更正） | 同场景实跑 Fabric/Neo，确认绘制层级（背景之后、控件之前） |

### 11.15 范围决定与修复建议（P1-2 已裁决 B）

1. **P1-2 已裁决 B（不纳入，2026-11-18）。** Forge 保持无 `integration` 包，Create door / Simulated resolver 按 `docs/` 为 NeoForge-only（`docs/integrations/create.md:1`、`docs/integrations/simulated.md:3`）；§6.2 已写明范围。若未来改为 A（纳入），再按“复制 resolver + `isModLoaded`/反射 + join 重试 + fail-soft”实施，并单独申请 `docs/` 范围更新授权。
2. **F5 运行时矩阵（不改代码）**：按 §11.14 顺序，先 HUD/F1/outline 视觉与命令/声音，再双客户端/专用服务器/重连；只记录观察，不把日志当视觉证据。
3. **P3 小修复已完成（2026-11-18，`224e0c2`）**：ForgeMain C2S try/finally + server null 守卫；world-submit 改 COW 多监听（逐回调隔离）；`ModIdentityTest` 补 `SyncDurationPolicyS2CPacket`（聚焦 7 tests、全量 1193 tests 通过）。P3-7 的 `CommonClient.onInit` once-guard 仍为后续可选：当前只注册一次；若未来重入，COW 会累积重复提交。
4. **文档卫生**：§6.2 已列出 Forge 可选集成范围；Fabric 配置界面缺口记录在 §11.3（不改 `docs/`）。
5. **不建议做（会引入真实回归）**：把 Forge 网络改回 payload type；用 `AddFramePassEvent` 取代 mixin；把 HUD 挂回 root；把客户端注册挪到 `FMLClientSetupEvent`；为对齐 Neo 改 `compatibilityLevel`；删 dev manifest。

以上除已裁决的 P1-2（B）外，均**待用户批准**；本轮未执行任何修复。

### 11.16 既有运行证据（只证明链路通）

#### 11.16.1 2026-11-16 22:44–22:47 runClient（Forge 26.3-66.1.0，JDK 25，单人世界）

- `latest.log:37-41`：`[PingForIt] Init`、`Loaded config type=ServerConfig`、`Loaded config type=ClientConfig`、`ping outline pipeline registered`、`block outline pass pipeline ready`；`:42` 资源包 `mod:pingforit` 被加载。
- `latest.log:97`：`Channel update: Dev -> default`（`onJoinServer` 的 C2S 在真实 join 时序下送达服务端）。
- `latest.log:100`：`block outline: Forge world-submit bridge active (LevelRenderer#submitFeatures RETURN)`。
- `latest.log:115-118`、`:129-131`：**2 次** `send ping` + `marker created`（requestId 0 / 4，均为 BLOCK/attention），其间 `block model glow active`；`debug.log:664,705,764,791` 的 `block outline: submitted=0/1` 与两次 ping 对应（本会话峰值 1）。
- `latest.log:123-145`：wheel open/close **10 次**，全部 `reason=no-selection`（只证明轮盘状态机可开可关，不是视觉证据）。
- `debug.log:198`：`Preparing pingforit.mixins.json (9)`；**启动期 8 条** `Mixing ... from pingforit.mixins.json`（`:233,235,284,286,288,299,301,304`，含 `LevelRendererSubmitMixin`）+ **世界期 1 条** `SubmitNodeCollectionMixin`（`:661`）；同时 **9 条** `Class version 69 ... JAVA_21 supports class version 65`（9/9 仍应用成功）。
- 这些只证明**链路通**：入口、握手、world-submit bridge、ping/marker、轮盘状态机。不证明 HUD 视觉、F1、命令输出、声音、多人/重连。

#### 11.16.2 2026-11-17 方案 A（`14c44f9`）

- **修复**：`forge/src/main/templates/META-INF/mods.toml` 的 `[[mods]]` 在 `description` 后加 `displayTest = "IGNORE_SERVER_VERSION"`（1 行，无其它字段变化）；理由为恢复旧 1.21.1 模板/§3.1 的字段，Forge `ModContainer` 读取并接受该值，缺省为 `MATCH_VERSION`。
- **构建**：`:forge:jar` BUILD SUCCESSFUL（15s）；generated `forge/build/generated/modMetadata/META-INF/mods.toml` 与 jar 内 `META-INF/mods.toml` 均为 L14 精确值；jar **822548 B / 504 entries / 432 classes / 0 duplicate / 6 services / 9 mixins**，manifest `MixinConfigs`、pack 121、LICENSE、THIRD-PARTY、assets 齐全（本轮直接读 jar 复核）。
- **发布卫生**：删除 `forge/build/libs` 的 1.21.1 旧 jar 与旧 sources/javadoc（build 目录，不可发布物；`verifyModIdentity` 只匹配精确名，本来不会误判）；现只剩 26.3 jar。
- **审查**：`parity-reviewer` 复核 PASS（P0 = 0 / P1 = 0，可提交）；模板/generated/jar 用 tomllib 解析、无重复 key、无未展开占位符；未复跑 build/runClient/runServer。
- **未验证**：多人版本不一致提示、channel 握手兼容性仍归 F5；P1-2（Create/Simulated resolver）已于 2026-11-18 裁决 B（不纳入），按 docs NeoForge-only 保持现状。

两组证据合起来的边界：**产物与链路已证，效果未证**。§11.14 的每一项都必须在 F5 会话里单独取得观察，不能引用本节代替。
