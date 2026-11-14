# Ping For It — 26.3 Forge 迁移方案

> 状态：**F0–F3 已完成**（2026-11-14）。`26.3-Forge` 已从旧的 `407fbc6` 同步到 `26.3` @ `2954fcf`；本地原有的 `9a723c2` 仅重复添加 `.agents-work/`，rebase 时被安全跳过。F0 基线 `:forge:compileJava` 实跑成功（骨架 `NO-SOURCE`）；F1 已接好 common 依赖/资源合并、metadata、121 pack 和 8 条本地 mixin；F2 已添加入口、14 个 channel、6 个 SPI 实现和 6 个 services，`:forge:runServer` 实跑输出 `[PingForIt] Init` 与 `Done (1.937s)`。F3 已加入客户端入口、8 个 S2C handler、按键/reload/HUD/命令/配置界面与声音注册；`:forge:runClient` 进入主菜单，mixin 配置成功加载，outline pipeline 创建成功。F4 尚未开始。
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
- `runs/forge-client/logs/debug.log` 显示 config 只注册一次（`source=SecureJarResource(pingforit)`），主菜单阶段实际应用 7 条 mixin；`SubmitNodeCollectionMixin` 目标类要到世界提交路径才加载，F3 主菜单不能证明它已应用。debug 日志同时有 `Class version 69 ... JAVA_21 supports class version 65` 警告，但 accessor 已使 outline pipeline 创建成功；F5 必须验证按键桥和 world-submit 是否真正注入。
- `runs/forge-client/logs/latest.log` 无 mod 加载 ERROR/FATAL；Realm 认证失败与 Windows netty 平台探测错误是环境噪声。
- `runServer` 未加载 `ForgeClient`/`CommonClient`，说明 universal jar 的客户端隔离有效。
- 主菜单启动只能证明客户端入口、mixin 配置、按键/HUD 注册不崩；S2C 实际往返、HUD 视觉、命令输出、配置界面和声音仍需进世界/联机验证。

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

  1. **首选**：Forge-local mixin 到 `LevelRenderer` 的 block-entity/feature 提交路径，在合适的提交点把 `PoseStack` / `SubmitNodeCollector` / `Camera` 转给 common `BlockOutlinePass.onWorldSubmit`。注入点必须以 Forge 26.3 实际 `LevelRenderer` 方法签名为准，不能照搬 NeoForge 的 `SubmitCustomGeometryEvent`。
  2. **备选**：把 `BlockOutlinePass` 重写成 Forge frame-pass 渲染器；工作量大，且需要自己处理 pose/camera/collector 生命周期。
  3. **不可接受**：静默不画 outline 或只画当前准星方块。
- F4 的出口：Forge client 进世界后，普通方块、门/床两半、`entity_block` 至少各有一个可见 outline；若 mixin 失败，记录失败日志并暂停 F5，不在 F6 假装完成。

## 6. 源码迁移分类

### 6.1 首轮改写/保留

- `ForgeMain`：`@Mod` + 构造注入；14 个 `EventNetworkChannel`；C2S handler；不引用客户端类。
- `ForgeClient`：在 `@Mod` 构造期由 `ForgeClientBootstrap` 同步初始化；S2C handler；HUD/reload/key/commands/config screen；不包含 WorldAware。不要改回 `FMLClientSetupEvent`（HUD/key/reload 事件更早且 self-destruct）。
- `PlatformContextServiceImpl`：版本、game/config dir、`isModLoaded`。
- `PlatformClientContextServiceImpl`：新增；继承/组合 context，实现 `registerKeyMapping`。
- `PlatformNetworkServiceImpl`：服务端 `sendToClient`；`CHANNEL_MAP` 用 `Identifier`。
- `ClientNetworkServiceImpl`：新增；客户端 `sendToServer`。
- `PlatformClientEventServiceImpl`：6 个方法全部实现；world submit 先留 F4 接口。
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
| F4 | outline / world-submit spike | 进世界可见普通方块/门/床/entity_block outline；或明确记录阻塞并暂停 |
| F5 | 客户端/服务端运行时验证 | client/server 均有 `[PingForIt] Init`；进世界、ping、HUD、outline、重连；无 ERROR/FATAL |
| F6 | 打包/静态身份检查 | `:forge:build`；精确 jar 名、common classes/resources、6 services、8 mixin、metadata、pack、LICENSE |
| F7 | 独立审查 + 合并 | 审查 PASS；F0–F6 证据齐全；`26.3-Forge` 合并回 `26.3` |

F1 的 metadata 门禁至少检查：

- `build/generated/modMetadata/META-INF/mods.toml`（或最终 resources 输出）无未展开 `${...}`。
- `mods.toml` 的 `modId` / `displayName` / `version` 与 `gradle.properties` 一致。
- `pack.mcmeta` 为 121。
- 本地 mixin 配置没有 refmap，mixin 列表与 common 的 8 条一致。

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

1. **Mixin 0.8.7 / Java 25**：最高兼容级别 `JAVA_21`。F3 的 `:forge:runClient` 已实际输出 `Compatibility level set to JAVA_21`，custom outline pipeline 创建成功，说明 accessor mixin 能工作；但 debug 日志同时给出 `Class version 69 ... JAVA_21 supports class version 65` 警告，且主菜单只观察到 7/8 条 mixin，`SubmitNodeCollectionMixin` 尚未经过世界提交路径。F5 必须验证按键桥和 world-submit 注入；该风险不能仅凭主菜单宣布完全解除。保留 `pingforit.mixins.json` 的 `JAVA_21`，不要伪造 `JAVA_25`。
2. **World submit 没有等价事件**：F4 的 `LevelRenderer` mixin 是唯一低风险路径；`AddFramePassEvent` 没有 pose/collector，不能直接复用 common pass。若 F4 失败，Forge 首轮 outline 不可宣称完成。
3. **事件总线 7 迁移**：旧 `MinecraftForge.EVENT_BUS` + `@SubscribeEvent` 只是兼容层；新代码统一用静态 `BUS`，避免自动订阅和类加载顺序问题。
4. **客户端类隔离与时序**：`ForgeMain` 不得直接引用 `ForgeClient` / `CommonClient` / `SettingsScreen`；客户端由 `ForgeClientBootstrap` 在 mod 构造期同步加载。HUD 层、按键、client reload 三个 `SelfDestructing` 事件在 `Minecraft` 构造期先 fire，不能等到 `FMLClientSetupEvent`。
5. **metadata 版本字段**：`loaderVersion` 与 Forge 依赖 `versionRange` 不是同一个东西；旧 `forge_loader_version=66` 不能直接作为 `loaderVersion`。F1 以 Forge universal metadata 和实际启动日志复核。
6. **可选集成**：Create/Flywheel 没有 26.3 artifact；不要加 1.21.1 compileOnly，也不要在 Forge metadata 声明可选依赖。
7. **运行环境**：默认 Gradle 用户目录在受限环境可能不可写；使用工作区 `GRADLE_USER_HOME` 仅作为本机 workaround，不写入项目构建逻辑。`runClient` 需要可写的 assets 目录；本机通过工作区 `APPDATA` 指向已复制/缓存的 assets。该路径是环境 workaround，不进入构建脚本。
8. **客户端注册时序**：HUD/key/reload 三类 `SelfDestructing` 事件早于 `FMLClientSetupEvent`；不要把客户端注册移回 setup 事件，否则不会报错但会永久丢失。`POST_SLEEP_STACK` 是 F1 隐藏语义的一部分，不要改回 root。
9. **dev Mixin manifest**：`prepareDevMod` 的 manifest 只服务目录型 dev mod；生产 jar 的 `MixinConfigs` 仍由 `jar.manifest` 提供。删除 dev manifest 会让开发运行静默失去 mixin，但发布 jar 不受影响。

## 10. 审查基线

F7 独立审查至少检查：

- 分支：`26.3-Forge` 基于 `2954fcf`，diff 只含 Forge 迁移与本文档；未修改 `docs/`。
- 编译：F2/F3/F5 的 Gradle 命令与结果；不是只读源码推导。
- 运行：client/server 日志中的 loader、MC 版本、`[PingForIt] Init`、错误/致命日志。
- 产物：精确 jar 名、common class/resource 合并、6 services、8 mixin、`mods.toml` 展开、`pack.mcmeta` 121、LICENSE/THIRD-PARTY。
- 未验证项：多人、可选集成、HUD/outline 视觉细节、Create/Flywheel parity；不要用编译或静态检查替代。
