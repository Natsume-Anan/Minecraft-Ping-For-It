package nx.pingwheel.common;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix4f;

import nx.pingwheel.common.client.ClientPingRuntime;
import nx.pingwheel.common.client.MinecraftLocalErrorSink;
import nx.pingwheel.common.client.SelectionToggleNoticeState;
import nx.pingwheel.common.client.duration.ClientSyncDurationPolicy;
import nx.pingwheel.common.client.hud.MarkerHudRenderer;
import nx.pingwheel.common.client.hud.WheelHudRenderer;
import nx.pingwheel.common.client.rate.ClientRateLimitPolicy;
import nx.pingwheel.common.client.marker.MarkerOverlayState;
import nx.pingwheel.common.config.ClientConfig;
import nx.pingwheel.common.core.GameContext;
import nx.pingwheel.common.domain.EntityLocator;
import nx.pingwheel.common.interaction.MinecraftEntityTargetAdapter;
import nx.pingwheel.common.name.ClientTargetNameDecoder;
import nx.pingwheel.common.network.MarkerCreatedS2CPacket;
import nx.pingwheel.common.network.MarkerRejectedS2CPacket;
import nx.pingwheel.common.network.MarkerRemovedS2CPacket;
import nx.pingwheel.common.network.MarkerWinnerChangedS2CPacket;
import nx.pingwheel.common.network.PingLocationS2CPacket;
import nx.pingwheel.common.network.RateLimitPolicyS2CPacket;
import nx.pingwheel.common.network.ServerConfigSnapshotS2CPacket;
import nx.pingwheel.common.network.SyncDurationPolicyS2CPacket;
import nx.pingwheel.common.network.UpdateChannelC2SPacket;
import nx.pingwheel.common.platform.IPlatformClientEventService;
import nx.pingwheel.common.platform.IClientPlatformContextService;
import nx.pingwheel.common.platform.IPlatformContextService;
import nx.pingwheel.common.platform.IPlatformNetworkService;
import nx.pingwheel.common.interaction.state.InteractionTimeSource;
import nx.pingwheel.common.interaction.state.PingInteractionPhase;
import nx.pingwheel.common.render.WorldRenderContext;
import nx.pingwheel.common.screen.SettingsScreen;
import nx.pingwheel.common.util.InputUtils;

import static nx.pingwheel.common.Global.LOGGER;
import static nx.pingwheel.common.util.InputUtils.KEY_BINDING_PING;
import static nx.pingwheel.common.util.InputUtils.KEY_BINDING_SETTINGS;

public class CommonClient {

	public static final CommonClient INSTANCE = new CommonClient();
	public static Minecraft Game = null;

	private static ClientPingRuntime pingRuntime;
	private static ClientRateLimitPolicy storedRateLimitPolicy = ClientRateLimitPolicy.DEFAULT;
	private static ClientSyncDurationPolicy storedSyncDurationPolicy = ClientSyncDurationPolicy.DEFAULT;
	private static final InteractionTimeSource INTERACTION_TIME_SOURCE = InteractionTimeSource.system();
	/** One-shot guard so a failing HUD pass cannot spam the log every frame. */
	private static boolean hudFailureLogged = false;

	private CommonClient() {}

	public void onInit() {
		ClientConfig.HANDLER.load();

		IPlatformClientEventService.INSTANCE.registerTickStartEvent(this::onTickStart);
		IPlatformClientEventService.INSTANCE.registerJoinServerEvent(this::onJoinServer);
		IPlatformClientEventService.INSTANCE.registerLeaveServerEvent(this::onLeaveServer);
		// The single per-frame interaction-timing hook (S1). It must not live in
		// the HUD pass: 26.3 skips HUD elements while F1 hides the HUD. Registered
		// exactly once here, so onRenderFrame() can never run twice per frame.
		IPlatformClientEventService.INSTANCE.registerRenderFrameEvent(this::onRenderFrame);

		IClientPlatformContextService.INSTANCE.registerKeyMapping(KEY_BINDING_PING);
		IClientPlatformContextService.INSTANCE.registerKeyMapping(InputUtils.KEY_BINDING_TOGGLE_PASS_THROUGH_TRANSPARENT_BLOCKS);
		IClientPlatformContextService.INSTANCE.registerKeyMapping(InputUtils.KEY_BINDING_TOGGLE_MARK_BLACKLISTED_TARGETS);
		IClientPlatformContextService.INSTANCE.registerKeyMapping(InputUtils.KEY_BINDING_TOGGLE_MARK_FLUIDS);
		IClientPlatformContextService.INSTANCE.registerKeyMapping(KEY_BINDING_SETTINGS);

		ClientTargetNameDecoder.setLogger(ClientTargetNameDecoder.Logger.global());
	}

	public void onJoinServer() {
		// Fresh per-connection runtime: interaction and marker state never
		// leak across worlds/servers. Created only when a live level/player
		// already exists; otherwise onTickStart creates it lazily once the
		// world is in.
		storedRateLimitPolicy = ClientRateLimitPolicy.DEFAULT;
		storedSyncDurationPolicy = ClientSyncDurationPolicy.DEFAULT;
		// A new world deserves a fresh HUD failure report: a transient first-frame
		// failure must not silence a later, different failure forever.
		hudFailureLogged = false;
		pingRuntime = createPingRuntimeIfInWorld();

		IPlatformNetworkService.INSTANCE.sendToServer(new UpdateChannelC2SPacket(ClientConfig.HANDLER.getConfig().getChannel()));
	}

	public void onLeaveServer() {
		if (pingRuntime != null) {
			pingRuntime.close();
		}

		pingRuntime = null;
		storedRateLimitPolicy = ClientRateLimitPolicy.DEFAULT;
		storedSyncDurationPolicy = ClientSyncDurationPolicy.DEFAULT;
		hudFailureLogged = false;
		MarkerOverlayState.INSTANCE.clear();
		GameContext.clearRenderEntityLookupCache();
		SelectionToggleNoticeState.INSTANCE.clear();

		// A disconnect while the ping key is still held must not leak the
		// armed hold into the next connection.
		InputUtils.resetPingHold();

		SettingsScreen.notifyServerDisconnected();
	}

	/**
	 * Receives the post-{@code KeyMapping.click} raw press edge. Arbitration is
	 * resolved here while the mapping counters still represent this physical
	 * event; a claimed edge is captured and minted immediately on the client
	 * thread.
	 */
	public void onKeyMappingClick(InputConstants.Key rawKey) {
		// Sample before any mapping arbitration or world lookup.  This is the
		// physical event timestamp, not the later tick/frame time.
		long eventTimeMillis = INTERACTION_TIME_SOURCE.nowMillis();
		Game = Minecraft.getInstance();
		InputUtils.handleToggleClick(rawKey, eventTimeMillis);

		if (!InputUtils.claimPingClick(rawKey)) {
			return;
		}

		// 26.3: Minecraft#screen moved to Minecraft#gui.screen().
		if (Game.gui.screen() != null) {
			abortInteractionIfActive(false);
			return;
		}

		if (pingRuntime == null) {
			pingRuntime = createPingRuntimeIfInWorld();
		}

		if (pingRuntime != null) {
			pingRuntime.onPress(eventTimeMillis);
			if (Game.level == null || Game.player == null) {
				// The runtime may have become invalid between arbitration and the
				// client-thread capture. Do not carry the claimed raw key into a
				// later world.
				InputUtils.resetPingHold();
			}
		} else {
			// A click in a menu must never arm an interaction that can leak into
			// a later world.
			InputUtils.resetPingHold();
		}
	}

	/** Receives a post-{@code KeyMapping.set} raw release edge. */
	public void onKeyMappingState(InputConstants.Key rawKey, boolean isDown) {
		Game = Minecraft.getInstance();

		if (!InputUtils.observeKeyState(rawKey, isDown)) {
			return;
		}

		if (pingRuntime != null) {
			pingRuntime.onRelease();
		}
	}

	/**
	 * Aborts the current interaction when Minecraft clears all key mappings,
	 * such as on focus loss. No default ping is produced.
	 */
	public void onInputReset() {
		abortInteractionIfActive(true);
	}

	/**
	 * Aborts an interaction at the screen transition boundary.  The identity
	 * check keeps repeated calls for the same screen cheap while still handling
	 * both opening and closing a menu before a swallowed release can arrive.
	 */
	public void onScreenChanged(Screen nextScreen) {
		Game = Minecraft.getInstance();

		// 26.3: Minecraft#screen moved to Minecraft#gui.screen().
		if (Game.gui.screen() == nextScreen) {
			return;
		}

		abortInteractionIfActive(false);
	}

	private void abortInteractionIfActive(boolean resetAllInputState) {
		boolean claimedInput = InputUtils.isPingHotkeyDown();
		boolean activeInteraction = pingRuntime != null
			&& pingRuntime.phase() != PingInteractionPhase.IDLE;
		boolean compatibilityState = pingRuntime != null && pingRuntime.hasCompatibilityState();

		if (resetAllInputState) {
			InputUtils.resetPingHold();
		} else {
			InputUtils.resetPingInteraction();
		}
		if (activeInteraction || claimedInput || compatibilityState) {
			if (pingRuntime != null) {
				pingRuntime.abort();
			}
		}
	}

	public void onTickStart() {
		Game = Minecraft.getInstance();
		GameContext.updateDimension();

		if (pingRuntime == null) {
			pingRuntime = createPingRuntimeIfInWorld();
		}

		if (pingRuntime != null) {
			pingRuntime.onTick();
		}

		if (Game.level == null || Game.player == null) {
			// onTick() aborts the runtime interaction when the world disappears;
			// this also clears the raw claim so a later join cannot inherit it.
			InputUtils.resetPingHold();
		}

		if (KEY_BINDING_SETTINGS.consumeClick()) {
			Game.setScreenAndShow(new SettingsScreen());
		}
	}

	/**
	 * The M4/M6.5 HUD pass: the single per-frame client hook that exists in 26.3
	 * before the M7 render rebuild.
	 *
	 * <p>It is registered as a Fabric {@code HudElement}
	 * ({@code HudElementRegistry.addLast}) by the Fabric client initializer, so
	 * it runs once per rendered frame while a level is present, after the world
	 * has been extracted for that frame (the main camera is therefore already
	 * updated).
	 *
	 * <p>Interaction timing is deliberately <em>not</em> advanced here: 26.3
	 * skips HUD elements while {@code F1} hides the HUD, which would freeze the
	 * hold/wheel timers. {@link #onRenderFrame()} is registered once from
	 * {@link #onInit()} through
	 * {@link IPlatformClientEventService#registerRenderFrameEvent(Runnable)}
	 * (Fabric: {@code LevelExtractionEvents.END_EXTRACTION}, fired once per frame
	 * in the extraction phase, i.e. before this HUD pass). This method only
	 * prepares {@link MarkerOverlayState} and draws:
	 * <ol>
	 *   <li>{@link #onRenderWorld(WorldRenderContext)} rebuilds
	 *       {@link MarkerOverlayState} for this frame from a view-projection
	 *       matrix derived from the live camera;</li>
	 *   <li>{@link MarkerHudRenderer} draws each projected marker: the 5x5
	 *       45-degree diamond (or a 16x16 item icon), the distance and target
	 *       name labels, and the safe-zone edge arrow for markers behind the
	 *       camera or outside the viewport (M6.5, upstream
	 *       {@code OverlayRenderer} / {@code PingLocationRenderer} /
	 *       {@code DirectionIndicatorRenderer});</li>
	 *   <li>{@link WheelHudRenderer} draws the ping-type wheel. It runs only in
	 *       {@link PingInteractionPhase#WHEEL_OPEN} and is the 26.3 replacement
	 *       for the deferred {@code WheelOverlayRenderer} (M6); it also feeds
	 *       the current mouse selection back to the runtime, so the existing
	 *       state machine keeps owning commit/cancel/timeout.</li>
	 * </ol>
	 *
	 * <p>TODO(M7): the deferred {@code SelectionToggleNoticeRenderer} (toggle
	 * notice) attaches to this same pass once its render code is restored.
	 */
	public void onRenderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Game = Minecraft.getInstance();

		if (Game.level == null || Game.player == null) {
			return;
		}

		try {
			WorldRenderContext ctx = hudRenderContext(deltaTracker);

			if (ctx == null) {
				return;
			}

			onRenderWorld(ctx);
			MarkerHudRenderer.render(graphics, MarkerOverlayState.INSTANCE.renderViews());
			// M6: the wheel pass draws on top of the marker dots and is
			// internally failure-isolated, so a broken wheel frame can never
			// take the marker pass (or the frame) down with it.
			WheelHudRenderer.render(graphics, pingRuntime);
		} catch (RuntimeException failure) {
			// A HUD pass must never crash the frame. Report once per session and
			// keep the rest of the HUD (and the frame) alive.
			if (!hudFailureLogged) {
				hudFailureLogged = true;
				Global.warnException("marker HUD pass failed", failure);
			}
		}
	}

	/**
	 * Prepares the per-frame marker render state.
	 */
	public void onRenderWorld(WorldRenderContext ctx) {
		Game = Minecraft.getInstance();
		GameContext.beginRenderEntityLookupFrame();
		MarkerOverlayState.INSTANCE.prepare(
			ctx,
			pingRuntime == null ? null : pingRuntime.store(),
			pingRuntime == null ? null : pingRuntime.nameStore());
	}

	/**
	 * Builds the frame's world render context from the live camera for the HUD
	 * pass.
	 *
	 * <p>26.3 keeps no shared world-render matrices (the pre-port
	 * {@code WorldRenderEvents} matrices are gone). {@code Camera} exposes the
	 * exact view-rotation-projection matrix the level was rendered with, and
	 * {@link nx.pingwheel.common.math.MathUtils#worldToScreen} expects
	 * camera-relative coordinates, so the view rotation and projection are
	 * supplied as one combined matrix and the model-view matrix stays identity:
	 * {@code clip = I * (P * V) * p} is exactly {@code P * V * p}.
	 *
	 * @return {@code null} while the camera has not been set up yet (for example
	 *         in the first frame of a world), so nothing is drawn with a bogus
	 *         matrix
	 */
	private static WorldRenderContext hudRenderContext(DeltaTracker deltaTracker) {
		Camera camera = Game.gameRenderer.mainCamera();

		if (camera == null || !camera.isInitialized()) {
			return null;
		}

		Matrix4f viewRotationProjection = camera.getViewRotationProjectionMatrix(new Matrix4f());
		// Same partial tick the level render state was extracted with in
		// GameRenderer#extract (getGameTimeDeltaPartialTick(false)).
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

		return WorldRenderContext.of(new Matrix4f(), viewRotationProjection, partialTick, camera);
	}

	/**
	 * Advances interaction timing once per rendered frame.
	 *
	 * <p>Registered exactly once from {@link #onInit()} via
	 * {@link IPlatformClientEventService#registerRenderFrameEvent(Runnable)}
	 * (Fabric: {@code LevelExtractionEvents.END_EXTRACTION}), never from the HUD
	 * pass, so it runs even while {@code F1} hides the HUD and can never run twice
	 * in one frame.
	 */
	public void onRenderFrame() {
		Game = Minecraft.getInstance();

		if (Game.level == null) {
			GameContext.clearRenderEntityLookupCache();
		}

		if (pingRuntime != null) {
			pingRuntime.onRenderFrame(InputUtils.isPingHotkeyDown());
		}

		if (Game.level == null || Game.player == null) {
			InputUtils.resetPingHold();
		}
	}

	/**
	 * The nullable current ping runtime: present only while in a live
	 * world/connection. The wheel renderer uses this getter so a missing
	 * runtime can never crash the GUI pass.
	 */
	public ClientPingRuntime getPingRuntime() {
		return pingRuntime;
	}

	/**
	 * Legacy S2C ping location handler.
	 *
	 * <p>Ping locations of the original protocol are no longer rendered: all
	 * ping state now arrives as authoritative marker packets (Phase 7). The
	 * handler stays registered so legacy servers keep receiving the channel
	 * handshake, but valid legacy packets are ignored without touching any
	 * client state; corrupt packets are warned about exactly like before.
	 */
	public void onPingLocationPacket(PingLocationS2CPacket packet) {
		if (packet.isCorrupt()) {
			LOGGER.warn("received invalid ping location from server");
			return;
		}

		LOGGER.debug("ignoring legacy ping location packet");
	}

	/**
	 * Marker S2C handlers (Phase 6/7).
	 *
	 * <p>The loader network services invoke every handler below on the client
	 * main thread, so the phase-7 {@link ClientPingRuntime} applies each
	 * authoritative mutation directly to its main-thread-confined marker
	 * store. A corrupt packet or a runtime that does not exist (not in a
	 * world) is dropped safely.
	 */
	public void onMarkerCreatedPacket(MarkerCreatedS2CPacket packet) {
		if (packet.isCorrupt() || pingRuntime == null) {
			return;
		}

		pingRuntime.applyCreated(packet);
	}

	public void onMarkerRemovedPacket(MarkerRemovedS2CPacket packet) {
		if (packet.isCorrupt() || pingRuntime == null) {
			return;
		}

		pingRuntime.applyRemoved(packet.markerId(), packet.reason());
	}

	public void onMarkerRejectedPacket(MarkerRejectedS2CPacket packet) {
		if (packet.isCorrupt() || pingRuntime == null) {
			return;
		}

		pingRuntime.handleRejected(packet.requestId(), packet.requestKind(), packet.reason());
	}

	public void onMarkerWinnerChangedPacket(MarkerWinnerChangedS2CPacket packet) {
		if (packet.isCorrupt() || pingRuntime == null) {
			return;
		}

		pingRuntime.applyWinnerChanged(packet.targetKey(), packet.winnerId());
	}

	/**
	 * Applies the server-authoritative client create policy. The loader network
	 * adapters invoke this method on the client thread. The stored value is
	 * updated even while no runtime exists so a lazily-created runtime starts
	 * with the latest policy received for this connection.
	 */
	public void onRateLimitPolicyPacket(RateLimitPolicyS2CPacket packet) {
		if (packet.isCorrupt()) {
			LOGGER.warn("received invalid rate limit policy from server");
			return;
		}

		ClientRateLimitPolicy nextPolicy = new ClientRateLimitPolicy(
			packet.rateLimit(), packet.msToRegenerate());

		if (storedRateLimitPolicy.equals(nextPolicy)) {
			return;
		}

		storedRateLimitPolicy = nextPolicy;

		if (pingRuntime != null) {
			pingRuntime.applyRateLimitPolicy(nextPolicy);
		}

		LOGGER.debug("client rate limit policy changed");
	}

	/**
	 * Applies the latest valid server duration policy to the connection-scoped
	 * client model.
	 */
	public void onSyncDurationPolicyPacket(SyncDurationPolicyS2CPacket packet) {
		if (packet.isCorrupt()) {
			return;
		}

		storedSyncDurationPolicy = new ClientSyncDurationPolicy(packet.syncDuration());
	}

	/**
	 * Returns the latest valid server duration policy for client-facing state.
	 */
	public ClientSyncDurationPolicy getSyncDurationPolicy() {
		return storedSyncDurationPolicy;
	}

	/**
	 * Routes server settings to the latest live settings session.
	 */
	public void onServerConfigSnapshotPacket(ServerConfigSnapshotS2CPacket packet) {
		if (packet.isCorrupt()) {
			return;
		}

		Game = Minecraft.getInstance();
		SettingsScreen.notifyServerConfigSnapshot(packet.requestId(), packet.snapshot());
	}

	/**
	 * Creates a fresh runtime only when a live level and player exist. Returns
	 * {@code null} otherwise so a menu state (including the post-leave main
	 * menu) never creates or recreates a runtime.
	 */
	private static ClientPingRuntime createPingRuntimeIfInWorld() {
		if (Game == null || Game.level == null || Game.player == null) {
			return null;
		}

		return createPingRuntime();
	}

	private static ClientPingRuntime createPingRuntime() {
		return ClientPingRuntime.create(
			new MinecraftLocalErrorSink(),
			IPlatformNetworkService.INSTANCE::sendToServer,
			storedRateLimitPolicy,
			INTERACTION_TIME_SOURCE);
	}
}
