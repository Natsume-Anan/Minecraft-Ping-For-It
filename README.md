# isharmla · Minecraft 26.3 Fabric 模组

基于 Fabric 官方模板 `fabric-example-mod` 搭建的模组工程，目标版本：

| 组件 | 版本 |
| --- | --- |
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.165.0+26.3 |
| Gradle | 9.8.2（wrapper） |
| JDK | **25** |

> 当前状态：**编译已通过**（2026-10-28 14:28 / 14:30 两次，产物见 `build/libs/`），**客户端已成功启动到主菜单**（14:34，`Hello Fabric world!` 已打印，无 Mixin 报错）；尚无自有功能、无服务端验证、无自动化测试。
> 命名已统一：模组 id = `isharmla`，Java 包与 maven group = `com.isharmla`，`rootProject.name = 'isharmla'`（类名不变）。**改名后尚未重新构建**，`build/libs/` 里仍是改名前的 jar。
> 遗留：图标文件还在 `assets/modid/icon.png`，而 `fabric.mod.json` 已指向 `assets/isharmla/icon.png` → 运行时会提示图标缺失（非致命，待手动搬移）。
> 详细清点、风险与待办见 [AGENTS.md](AGENTS.md) 与 [docs/survey/project-survey.md](docs/survey/project-survey.md)。

## 环境要求

- **JDK 25**：Loom 1.19.3 要求构建 JVM ≥ 25，用 JDK 24 会在插件解析阶段直接失败。
- 依赖缓存位于 `D:\Gradle`（约 1.4 GB），命令行构建请复用，避免重新下载。

## 构建与运行

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-25"
export GRADLE_USER_HOME="D:/Gradle"

./gradlew -g D:/Gradle build
./gradlew -g D:/Gradle runClient
./gradlew -g D:/Gradle runServer
```

- `build` 的产物在 `build/libs/`，文件名随 `rootProject.name`（改名重跑后应为 `isharmla-1.0.0.jar`，jar 内 `id` = `isharmla`）。
- **不要把这些命令连同行尾注释一起粘贴进 Windows cmd**：`#` 在 cmd 里不是注释符，会被当成任务名（已实际踩坑 4 次）。

Windows cmd：`set JAVA_HOME=C:\Program Files\Java\jdk-25`，再执行 `gradlew.bat -g D:\Gradle build`。

## 目录

```
src/main/java/com/isharmla/           主入口与通用 mixin
src/main/resources/                   fabric.mod.json、mixins 配置、assets/isharmla/
src/client/java/com/isharmla/client/  客户端入口与客户端 mixin（split 环境）
src/client/resources/                 isharmla.client.mixins.json
```

## 许可

模板代码沿用 CC0-1.0（见 [LICENSE](LICENSE)）。正式发布前请确认模组自身的授权协议。
