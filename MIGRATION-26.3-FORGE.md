# Ping For It — 26.3 Forge 迁移方案

> 状态：**F0–F1 已完成**（2026-11-14）。`26.3-Forge` 已从旧的 `407fbc6` 同步到 `26.3` @ `2954fcf`；本地原有的 `9a723c2` 仅重复添加 `.agents-work/`，rebase 时被安全跳过。F0 基线 `:forge:compileJava` 实跑成功（骨架 `NO-SOURCE`）；F1 已接好 common 依赖/资源合并、metadata、121 pack 和 8 条本地 mixin，`:forge:processResources` 与 `:forge:jar` 实跑成功。F2 尚未开始。
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
- 使用 `annotationProcessor "org.spongepowered:mixin:0.8.7:processor"`，并在 Gradle 的 `JavaCompile` 参数中加 `-mixin.config=pingforit.mixins.json`；不要恢复 MixinGradle。
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
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(event -> ForgeClient.init(context));
    }
}
```

- 使用构造参数注入的 `FMLJavaModLoadingContext`；不要用已弃用的 `FMLJavaModLoadingContext.get()` / `ModLoadingContext.get()` 作为首选。
- 客户端初始化放在 `FMLClientSetupEvent`，并由 `FMLEnvironment.dist == Dist.CLIENT` 保护；不要用已删除的 `DistExecutor`。
- `ForgeMain` 不直接 `new ForgeClient()`，避免 dedicated server 加载客户端类。

### 4.2 事件映射

| common SPI 回调 | Forge 26.3 映射 |
| --- | --- |
| `registerTickStartEvent` | `TickEvent.ClientTickEvent.Pre.BUS.addListener(event -> callback.run())` |
| `registerJoinServerEvent` | `ClientPlayerNetworkEvent.LoggingIn.BUS` |
| `registerLeaveServerEvent` | `ClientPlayerNetworkEvent.LoggingOut.BUS` |
| `registerRenderFrameEvent` | `TickEvent.RenderTickEvent.Pre.BUS`；F3 验证每帧一次，且无 level 时 common 回调自行 guard |
| `registerHudRenderEvent` | `AddGuiOverlayLayersEvent.BUS` + `event.getLayeredDraw().add(Identifier.parse("pingforit:hud"), (graphics, delta) -> callback.render(graphics, delta))` |
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
- `ForgeClient`：`FMLClientSetupEvent` 中初始化；S2C handler；HUD/reload/key/commands/config screen；不包含 WorldAware。
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
| F2 | 入口、服务端网络、SPI、6 个 services | `:forge:compileJava` 通过；`:forge:runServer` 启动并出现 `[PingForIt] Init`，无 ERROR/FATAL |
| F3 | 客户端事件、HUD、按键、reload、配置界面 | `:forge:runClient` 进入 26.3 主菜单；mixin 加载无致命错误；HUD 注册不崩 |
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
- `verifyModIdentity` 仍依赖 `:forge:build`；F6 之前不要运行，也不要把 Forge 骨架的 compile 成功说成身份校验通过。
- `:common:test` 仍应保持 1193/1193；Forge 迁移不应修改 common 测试源集。
- 运行日志路径：`runs/forge-client/logs/latest.log`、`runs/forge-server/logs/latest.log`。

## 9. 风险与未决问题

1. **Mixin 0.8.7 / Java 25**：最高兼容级别 `JAVA_21`；Java 25 class 文件可能无法被 mixin 读取。F1/F2 首次 client 启动是 go/no-go；必要时把 Forge/mixin 目标类编译到 Java 21 字节码，或改用 Forge 实际支持的更新 mixin 栈。不要伪造 `JAVA_25`。
2. **World submit 没有等价事件**：F4 的 `LevelRenderer` mixin 是唯一低风险路径；`AddFramePassEvent` 没有 pose/collector，不能直接复用 common pass。若 F4 失败，Forge 首轮 outline 不可宣称完成。
3. **事件总线 7 迁移**：旧 `MinecraftForge.EVENT_BUS` + `@SubscribeEvent` 只是兼容层；新代码统一用静态 `BUS`，避免自动订阅和类加载顺序问题。
4. **客户端类隔离**：`ForgeMain` 不得直接引用 `ForgeClient` / `CommonClient` / `SettingsScreen`；client handler 和 config screen 只在 `FMLClientSetupEvent` 之后加载。
5. **metadata 版本字段**：`loaderVersion` 与 Forge 依赖 `versionRange` 不是同一个东西；旧 `forge_loader_version=66` 不能直接作为 `loaderVersion`。F1 以 Forge universal metadata 和实际启动日志复核。
6. **可选集成**：Create/Flywheel 没有 26.3 artifact；不要加 1.21.1 compileOnly，也不要在 Forge metadata 声明可选依赖。
7. **运行环境**：默认 Gradle 用户目录在受限环境可能不可写；使用工作区 `GRADLE_USER_HOME` 仅作为本机 workaround，不写入项目构建逻辑。

## 10. 审查基线

F7 独立审查至少检查：

- 分支：`26.3-Forge` 基于 `2954fcf`，diff 只含 Forge 迁移与本文档；未修改 `docs/`。
- 编译：F2/F3/F5 的 Gradle 命令与结果；不是只读源码推导。
- 运行：client/server 日志中的 loader、MC 版本、`[PingForIt] Init`、错误/致命日志。
- 产物：精确 jar 名、common class/resource 合并、6 services、8 mixin、`mods.toml` 展开、`pack.mcmeta` 121、LICENSE/THIRD-PARTY。
- 未验证项：多人、可选集成、HUD/outline 视觉细节、Create/Flywheel parity；不要用编译或静态检查替代。
