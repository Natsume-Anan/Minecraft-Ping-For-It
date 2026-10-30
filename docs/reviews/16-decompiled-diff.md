# 与 10/26 构建产物（反编译）的语义差异

> 来源：回收站里我们自己 10/26 的构建 jar（508 class），vineflower 反编译到 `.tmp-decomp/src`（302 java）。
> 只比较『方法名集合』与『字符串常量集合』，decompiler 噪音（`method_*`、`catch`、lambda 展开）已过滤。

| 文件 | 缺失方法 | 缺失字符串 |
|---|---|---|
| `nx/pingwheel/common/CommonServer.java` | — | "pingforit" |
| `nx/pingwheel/common/config/ConfigHandler.java` | — | "pingforit"<br>"pingforit-version" |
| `nx/pingwheel/common/config/ConfigVersionUpdater.java` | — | "pingforit-version: " |
| `nx/pingwheel/common/config/PingForItVersion.java` | — | " + matcher.group(3) + " |
| `nx/pingwheel/common/config/ServerConfig.java` | canEqual | "ServerConfig(defaultChannelMode=" |
| `nx/pingwheel/common/core/ServerCore.java` | — | "§8[PingForIt] §cChannel couldn't be updated\\n§fMake sure y<br>"§8[PingForIt] §cUnable to send ping\\n§fMake sure your vers<br>"§8[PingForIt] §eMust be in a channel to ping location\\n§fU<br>"§8[PingForIt] §eMust be in a team or channel to ping locati |
| `nx/pingwheel/common/Global.java` | — | "pingforit-c2s"<br>"pingforit-s2c" |
| `nx/pingwheel/common/integration/DistantHorizonsIntegration.java` | rootCause, traceDistant | "distanthorizons"<br>"distanthorizons schedule failed"<br>"distanthorizons trace failed"<br>"Distant Horizons API Version: %s.%s.%s" |
| `nx/pingwheel/common/integration/FTBTeamsWrapper.java` | — | "ftbteams" |
| `nx/pingwheel/common/integration/sable/server/SableExternalBlockLocator.java` | — | " + this.x + "<br>" + this.y + " |
| `nx/pingwheel/common/integration/sable/server/SableExternalBlockServerProvider.java` | observeValidationDistance, dimensionMatches, invalidValidation, invalidMaterialization, logLiveResult, refreshTemporary | "dev.ryanhcode.sable.api.sublevel.SubLevelContainer"<br>"dev.ryanhcode.sable.sublevel.ServerSubLevel"<br>"dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTracki<br>"dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint" |
| `nx/pingwheel/common/integration/TeamContextHandler.java` | getSelfContext | — |
| `nx/pingwheel/common/integration/VoiceChatIntegration.java` | getPluginId, registerEvents, onServerStarted, onClientInitialized | "pingforit" |
| `nx/pingwheel/common/integration/VoiceChatWrapper.java` | — | "voicechat" |
| `nx/pingwheel/common/interaction/cancel/CancelCandidatePicker.java` | — | "cancelHalfConeAngleDegrees must be in [1, 90], got " |
| `nx/pingwheel/common/marker/AuthoritativeTargetValidation.java` | — | " + this.rejectReason + " |
| `nx/pingwheel/common/marker/MarkerCreateOutcome.java` | — | " + this.rejectReason + " |
| `nx/pingwheel/common/marker/TargetKey.java` | — | "dimensionId"<br>"dimensionId must not be blank" |
| `nx/pingwheel/common/math/EntityLocalGeometryRegistry.java` | — | " + failure.getClass().getName(), "<br>" + sourceId + " |
| `nx/pingwheel/common/name/TargetNameJson.java` | — | "target name JSON exceeds 32767 characters: " |
| `nx/pingwheel/common/network/MarkerCreateC2SPacket.java` | — | "pingforit-c2s" |
| `nx/pingwheel/common/network/MarkerCreatedS2CPacket.java` | — | "pingforit-s2c" |
| `nx/pingwheel/common/network/MarkerRejectedS2CPacket.java` | — | "pingforit-s2c" |
| `nx/pingwheel/common/network/MarkerRemoveC2SPacket.java` | — | "pingforit-c2s" |
| `nx/pingwheel/common/network/MarkerRemovedS2CPacket.java` | — | "pingforit-s2c" |
| `nx/pingwheel/common/network/MarkerWinnerChangedS2CPacket.java` | — | "pingforit-s2c" |
| `nx/pingwheel/common/network/PingLocationC2SPacket.java` | — | "pingforit-c2s" |
| `nx/pingwheel/common/network/PingLocationS2CPacket.java` | — | "pingforit-s2c" |
| `nx/pingwheel/common/network/RateLimitPolicyS2CPacket.java` | — | "pingforit-s2c" |
| `nx/pingwheel/common/network/ServerConfigRequestC2SPacket.java` | — | "pingforit-c2s" |
| `nx/pingwheel/common/network/ServerConfigSnapshotS2CPacket.java` | — | "pingforit-s2c" |
| `nx/pingwheel/common/network/ServerConfigUpdateC2SPacket.java` | — | "pingforit-c2s" |
| `nx/pingwheel/common/network/SyncDurationPolicyS2CPacket.java` | — | "pingforit-s2c" |
| `nx/pingwheel/common/network/UpdateChannelC2SPacket.java` | — | "pingforit-c2s" |
| `nx/pingwheel/common/resource/LanguageUtils.java` | — | ", category, "<br>", String.join("<br>")).method_10852(this.get()).method_10852(class_2561.method_<br>"[PingForIt] " |
| `nx/pingwheel/common/resource/ResourceConstants.java` | — | "pingforit" |
| `nx/pingwheel/common/client/ClientPingActionDispatcher.java` | — | "pingforit.chat.target_gone" |
| `nx/pingwheel/common/client/ClientPingRuntime.java` | — | "pingforit.chat.target_gone" |
| `nx/pingwheel/common/client/outline/BlockDisplayPolicy.java` | whitelistEntries, blacklistEntries, shouldUseNativeGlow | "whitelist"<br>"blacklist"<br>"blockDisplayWhitelist"<br>"blockShapeBlacklist" |
| `nx/pingwheel/common/client/outline/BlockDisplayWhitelist.java` | builtIn, entries, parsedEntries, matches, matchesOrdinaryBlock, matchesEntityBlock | "entries"<br>"fieldName"<br>"tagLookup"<br>"renderShape" |
| `nx/pingwheel/common/client/SelectedLocaleTranslationKeyCache.java` | — | "pingforit" |
| `nx/pingwheel/common/command/ClientCommandBuilder.java` | — | "/pingforit config"<br>"description"<br>"/pingforit channel"<br>"/pingforit channel <channel_name>" |
| `nx/pingwheel/common/CommonClient.java` | prepareEntityOutlines, prepareBlockOutlines, prepareBlockPresentations, requestEntityOutlineEffect, renderModelOutlines, renderEntityOutlines | — |
| `nx/pingwheel/common/config/ClientConfig.java` | toString, equals, canEqual, hashCode | "ClientConfig(pingVolume="<br>", pingDistance="<br>", itemIconVisible="<br>", directionIndicatorVisible=" |
| `nx/pingwheel/common/integration/sable/client/SableClientProvider.java` | resolvePresentation, getAccess, setDiagnosticsForTests, rayDirection, logPresentationFailure, logPresentationException | "attempt"<br>"sable_loaded"<br>"enabled"<br>"link_guard_disabled" |
| `nx/pingwheel/common/mixin/MinecraftMixin.java` | — | "setScreen" |
| `nx/pingwheel/common/screen/SettingsScreen.java` | registerCurrent, clearCurrent, dispatchKeyPressed, performClose, createScopeTabs, selectScopeFromTab | "reset_all"<br>"category"<br>"marker_display"<br>"input_timing" |
| `nx/pingwheel/common/util/InputUtils.java` | — | "key.category" |
| `nx/pingwheel/fabric/FabricClient.java` | registerReloadListener, getFabricId | "pingforit" |
| `nx/pingwheel/fabric/platform/PlatformContextServiceImpl.java` | registerKeyMapping | "pingforit" |

共 50 个类存在差异。
