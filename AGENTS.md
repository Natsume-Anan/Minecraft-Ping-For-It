# AGENTS.md · Ping For It（MC 26.3 / Fabric 单端）协作说明

> 团队唯一事实来源：现状、目录、构建、已验证结论、已知坑、待办。
> 配套文档：[docs/PORTING-PLAN.md](docs/PORTING-PLAN.md)｜[docs/PORTING-PROGRESS.md](docs/PORTING-PROGRESS.md)｜[docs/FEATURES.md](docs/FEATURES.md)
> 维护者：Lead｜**最后更新：2026-10-30（事故已恢复，tag `restore-2026-10-29`）**

## 0. 2026-10-29 数据事故与恢复（先读）

**已恢复（2026-10-30）**：工程目录曾被外部操作清空、`.git` 丢失、IDEA Local History 只回了空壳。重建后基线为 tag **`restore-2026-10-29`**（提交 `bca73e0`，事故记录 `87bceb2`）。

| 项 | 恢复后状态 |
|---|---|
| 规模 | `src/main` **162** java + `src/client` **55** java + `deferred/` **96** java（事故前为 165/54/92，差异与保真度见 `docs/reviews/14-restore-verification.md`、`15-restore-baseline-audit.md`） |
| 编译 | `tools/compile-check` **0 error / 0 error**（两源集）；用户终端 `gradlew clean build` **通过**（2026-10-30 12:07） |
| 产物 | `build/libs/Ping-For-It-0.4.2-pfi-beta1-fabric-26.3.jar`：**649 KB / 345 class / major 69 / `id=pingforit` / `version=0.4.2-pfi-beta1`** |
| 依赖 | `.gradle/` 已重建；`D:\Gradle\caches` 里的 MC 26.3 与 Fabric API jar 未受影响 |
| git 历史 | 原 tag（`pre-port`/`port-M2`/`port-M3`/`port-M4`/`port-M6.5`）随 `.git` 丢失；**语义保留在 `docs/PORTING-PROGRESS.md` 的阶段记录里**，恢复基线以 `restore-2026-10-29` 起算 |
| 上游源码 | `Mod_Project/Minecraft-Ping-For-It-0.4.2-pfi-beta1`（从 Downloads 的 tar.gz 重新解压，617 文件 / 513 java 完好） |

> 教训：**不要再依赖 IDEA Local History 做恢复**——它对工具写入的文件只存元数据、不存内容。

## 1. 定位与现状

- 上游 **Ping For It `0.4.2-pfi-beta1`** 的 **MC 26.3 + Fabric 单端**移植（决策 D1，只保留 Fabric 端）。
- 身份：`mod id = pingforit`、`rootProject.name = pingforit`、Java 包 **`nx.pingwheel`**（D3）、maven group `isharmla.mod`。
- 版本：`0.4.2-pfi-beta1`，**必须符合 `x.x.x-pfi-qualifier`**（`config/PingForItVersion.java` 的正则）。
- 规模（恢复后实测）：`src/main` **162** java + `src/client` **55** java + `deferred/` **96** java（不参与编译）。
- 进度：**M0–M6.5 已完成**；原 tag 随 `.git` 丢失，语义见 `docs/PORTING-PROGRESS.md`，恢复基线 tag `restore-2026-10-29`。**M5、M7+ 未开始**。

## 2. 目录职责

```
build.gradle / settings.gradle / gradle.properties   构建入口（id/version 唯一来源）
gradle/wrapper/                                      固定 Gradle 9.8.2
src/main/java/            162 java  双端共用业务层（marker/core/network/config/domain/math/…）
src/main/resources/             assets/pingforit（lang、音效、贴图）、icon.png
src/main/templates/fabric.mod.json  元数据模板（构建时生成到 build/generated/modMetadata）
src/client/java/           55 java  仅客户端：HUD、输入、命令、平台适配、客户端 mixin
src/client/resources/            pingforit.mixins.json（JAVA_25，client 列表）
deferred/                  96 java  26.3 无对应原语的渲染/UI 子系统，不参与编译，M7 取回
tools/compile-check.sh|.ps1       本地 javac 两源集校验（已重建），不启动 Gradle
docs/                             PLAN / PROGRESS / FEATURES / reviews / TEAM.md
Mod_Project/                      上游源码（已重新解压，**.gitignore 排除**）
run/ build/ .gradle/ .idea/       运行与构建产物（gitignore）
```

## 3. 构建与运行

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-25"
export GRADLE_USER_HOME="D:/Gradle"
./gradlew -g D:/Gradle build
./gradlew -g D:/Gradle runClient
```

**沙箱限制**：DSH 会话内跑不了 Gradle；本地回归用 `tools/compile-check.sh`（直接 javac），真实产物由用户在终端跑 `gradlew clean build`。

## 4. 构建脚本要点

- `splitEnvironmentSourceSets()`：main 双端共用、client 仅客户端；**main 绝不能 import `net.minecraft.client.**` / `com.mojang.blaze3d.**`**（grep 门禁）。
- `loom.mods { "pingforit" { sourceSet main; sourceSet client } }`。
- `generateModMetadata`（**Sync** 任务）：模板 → `build/generated/modMetadata` 并替换版本；`inputs.property "version"` 必须保留；不要改回 `processResources { filesMatching { expand } }`。
- `java { release 25; withSourcesJar() }`；`jar { archiveClassifier = "fabric-26.3" }`（no-remap 插件下没有 `remapJar`）。
- `base { archivesName = "Ping-For-It" }` → 产物 `Ping-For-It-<version>-fabric-26.3.jar`。

## 5. 版本矩阵

| 组件 | 值 |
| --- | --- |
| Minecraft | `26.3`（class major 69） |
| Java | `25`（release 25） |
| Fabric Loader | `0.20.1` |
| Fabric API | `0.165.0+26.3` |
| Loom | `1.19.3`（no-remap 插件） |
| Gradle | `9.8.2` |

## 6. 已验证结论

| 验证点 | 证据 |
| --- | --- |
| 构建通过（事故前） | `Ping-For-It-0.4.2-pfi-beta1-fabric-26.3.jar`，346 class / major 69 / `id=pingforit` |
| 启动不卡加载条 | 20:11:23 启动 → 20:11:34 `Sound engine started` + 12 atlas |
| 进世界 + 闭环 | 22 对 `send ping` / `marker created` 1:1 |
| M6 轮盘 | 3 次 select → send → marker，pingType 三方 3/3 一致 |
| M6.5 HUD | 菱形/物品图标+装饰/三段文本+头颅/2.5px 箭头，与上游逐项对齐；`compile-check` main 264 class / client 85 class，0 error |
| **恢复后（2026-10-30）** | `compile-check` 两源集 **0 error**；`gradlew clean build` **通过**（12:07）；jar **649 KB / 345 class / major 69 / id=pingforit**；CI workflow 已改为本项目的 `./gradlew build` + 上传 `Ping-For-It-*.jar` |

## 7. 已知坑与约束

1. **`deferred/` 不在编译路径**：类找不到是正常的，M7 取回；不要为「修红」重写。
2. **26.3 的 HUD/渲染 API 与 1.21.1 完全不同**：HUD 走 `HudElementRegistry.addLast` + `GuiGraphicsExtractor`；旧 `WorldRenderEvents`/`MultiBufferSource`/`OutlineBufferSource`/`RenderStateShard`/`LevelRenderer.renderLevel` 都不存在。
3. **`InputConstants` 是 SDL 语义、鼠标键 1 基**：`MOUSE_BUTTON_LEFT=1 … MOUSE_BUTTON_5=5`、`KEY_LSHIFT=225`，`Type` 只剩 `KEYBOARD/MOUSE`。
4. **配置版本号必须是 `x.x.x-pfi-qualifier`**，否则配置迁移/入口异常。
5. **禁止用 fabric-resource-loader v0 注册资源重载**（会让资源重载永远完不成、卡加载条）；M8 走 v1。
6. **`run/` 下日志与世界只读**：排障时复制出去。
7. **标记尺寸是「距离补偿」设计**：`scale = max(1, 2/d^0.3) * 0.5 * pingSize`，远处恒 0.5 倍是上游刻意行为。
8. **屏外箭头「最终 2.5px」是上游行为**，不要放大。
9. **26.3 物品渲染拆两步**：`item(stack,x,y)` 只画模型，必须再调 `itemDecorations(font,stack,x,y)` 才有数量/耐久条。
10. **不要再依赖 IDEA Local History 恢复**（只存打开过的文件内容）。

## 8. 团队分工与写入范围

| 角色 | 职责 | 写入范围 |
| --- | --- | --- |
| 1号 repo-analyst | 清点、文档、日志归因 | `AGENTS.md`、`docs/` |
| 2号 implementer | 功能实现、构建脚本、资源 | `src/**`、`deferred/**` |
| 3号 code-reviewer | 独立审核（只读源码） | `docs/reviews/` |
| 4号 build-engineer | 构建、反编译、jar 核验、编译校验工具 | `build/`、`run/`、`tools/` |
| ~~5号 log-monitor~~ | **已退役**，职责并入 1号 | — |

> 花名册与退役记录：[docs/TEAM.md](docs/TEAM.md)。**Lead 直接承担部分实现与排障**，子代理只向 Lead 汇报。

## 9. 待办

| 阶段 | 待办 | 状态 |
| --- | --- | --- |
| 事故恢复 | 源码重建、compile-check 重建、文档重写、git 重建 | ✅ 完成（2026-10-30，tag `restore-2026-10-29`） |
| M5 | 服务端权威完善（受众/赢家/限流/移除/过期） | ⬜ |
| M7 | 渲染重建：`deferred/` 描边/世界渲染按 26.3 管线重写 | ⬜（最大风险，预研见 `docs/reviews/12-m7-feasibility.md`） |
| M8 | 配置/命令/UI + 资源重载迁 v1 | ⬜ |
| M9 | 可选集成（ModMenu/VoiceChat/FTB/DH/Sable） | ⬜ |
| M10 | 验证与发布：`runServer`、CI、测试基座、LICENSE → MIT + THIRD-PARTY-NOTICES | ⬜ |

## 10. 改动后自检

- [ ] `gradlew -g D:/Gradle build` 通过，产物名 `Ping-For-It-0.4.2-pfi-beta1-fabric-26.3.jar`
- [ ] jar 内 `fabric.mod.json` 的 `id=pingforit`、version 与 `gradle.properties` 一致；mixins 对象形式且 `environment: client`
- [ ] `runClient` 不卡加载条，无 `Mixin apply failed`
- [ ] 进世界后按键产生成对 `send ping` / `marker created`
- [ ] `src/main` 下 grep 不到 `net.minecraft.client.` / `com.mojang.blaze3d`
- [ ] 未把 `deferred/` 误加回编译路径

## 11. 移植阶段与估算

| 阶段 | 目标 |
|---|---|
| M0–M4 | 基线 / 构建骨架 / 源码导入与拆分 / 编译迁移 / MVP 闭环（已完成） |
| M6 / M6.5 | 轮盘与 7 类型（按 target type 分桶）/ 标记 HUD 上游对齐（已完成） |
| M7 | 渲染重建（5–10d，最大风险） |
| M8–M10 | 配置/命令/UI、可选集成、验证与发布 |

## 12. 移植决策记录

| # | 决策 |
|---|---|
| D1 | 只保留 Fabric 端 |
| D2 | mod id 改为 `pingforit` |
| D3 | Java 包名保留 `nx.pingwheel` |
| D4 | 产物名 `Ping-For-It-<version>-fabric-26.3.jar` |
| D5 | 暂时保留 ServiceLoader 平台层 |
| D6 | 不引入 Parchment |
| D7 | 移除 Lombok |
| D8 | 188 个测试类在 M10 重建 |
| D9 | 可选集成最后做 |
