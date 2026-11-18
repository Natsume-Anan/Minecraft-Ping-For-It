package nx.pingwheel.forge.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.event.TickEvent;
import nx.pingwheel.common.platform.IPlatformClientEventService;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static nx.pingwheel.common.Global.LOGGER;
import static nx.pingwheel.common.Global.MOD_ID;

public class PlatformClientEventServiceImpl implements IPlatformClientEventService {

	private static final AtomicInteger HUD_LAYER_COUNTER = new AtomicInteger();

	/**
	 * Registered from {@code CommonClient#onInit} during mod construction and iterated once per
	 * frame on the render thread. Copy-on-write keeps registration lock-free and makes the list
	 * safe to read while it is being updated.
	 *
	 * <p>Every callback receives the bridge's scratch {@link PoseStack}. It is empty at the
	 * {@code submitFeatures} RETURN, so a callback must leave it balanced (push/pop or
	 * translate/pop). A callback that unbalances it only corrupts the later callbacks of the
	 * same frame; vanilla's own stack is never touched.
	 */
	private static final CopyOnWriteArrayList<WorldSubmitCallback> worldSubmitCallbacks =
		new CopyOnWriteArrayList<>();

	/** Render thread only: one "bridge active" line per session. */
	private static boolean worldSubmitActiveLogged;

	/** Render thread only: one failure report, same policy as {@code BlockOutlinePass}. */
	private static boolean worldSubmitFailureLogged;

	@Override
	public void registerTickStartEvent(Runnable callback) {
		TickEvent.ClientTickEvent.Pre.BUS.addListener(event -> callback.run());
	}

	@Override
	public void registerJoinServerEvent(Runnable callback) {
		ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> callback.run());
	}

	@Override
	public void registerLeaveServerEvent(Runnable callback) {
		ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> callback.run());
	}

	@Override
	public void registerRenderFrameEvent(Runnable callback) {
		TickEvent.RenderTickEvent.Pre.BUS.addListener(event -> callback.run());
	}

	@Override
	public void registerHudRenderEvent(HudRenderCallback callback) {
		Identifier layerId = Identifier.fromNamespaceAndPath(
			MOD_ID, "hud-layer-" + HUD_LAYER_COUNTER.incrementAndGet());
		AddGuiOverlayLayersEvent.BUS.addListener(event ->
			// POST_SLEEP_STACK is gated by !gui.isHidden(), matching vanilla HUD
			// visibility, so the hide-HUD key (F1) hides this layer as well.
			// Adding to the root would keep drawing while the HUD is hidden.
			event.getLayeredDraw().add(
				ForgeLayeredDraw.POST_SLEEP_STACK, layerId, (ForgeLayer) callback::render));
	}

	@Override
	public void registerWorldSubmitEvent(WorldSubmitCallback callback) {
		// F4: Forge 26.3 has no SubmitCustomGeometryEvent / COLLECT_SUBMITS equivalent, so
		// the outline pass is driven from the RETURN of LevelRenderer#submitFeatures by
		// nx.pingwheel.common.mixin.LevelRendererSubmitMixin.  The native context mapping
		// (camera, partialTick) stays here, exactly like the Fabric adapter does.
		worldSubmitCallbacks.add(callback);
	}

	/**
	 * Bridge target for {@code nx.pingwheel.common.mixin.LevelRendererSubmitMixin}; runs on
	 * the render thread once per frame, between {@code submitFeatures} and
	 * {@code prepareFrame}, so nodes submitted here still belong to the current frame.
	 */
	public static void dispatchWorldSubmit(PoseStack poseStack, SubmitNodeCollector collector) {
		var callbacks = worldSubmitCallbacks;

		// Covers "mixin already applied, callback not registered yet" without touching the list twice.
		if (callbacks.isEmpty()) {
			return;
		}

		try {
			Minecraft game = Minecraft.getInstance();

			if (game == null) {
				return;
			}

			if (!worldSubmitActiveLogged) {
				worldSubmitActiveLogged = true;
				LOGGER.info("block outline: Forge world-submit bridge active (LevelRenderer#submitFeatures RETURN)");
			}

			// Same contract as the Fabric adapter: this is the camera the frame was extracted
			// with (extractCamera ran before LevelRenderer#render) and BlockOutlinePass never
			// reads partialTick, so 0.0f keeps both adapters identical.
			WorldSubmitContext context = new WorldSubmitContext(
				poseStack, collector, game.gameRenderer.mainCamera(), 0.0f);

			for (WorldSubmitCallback callback : callbacks) {
				try {
					callback.submit(context);
				} catch (RuntimeException | LinkageError failure) {
					// One failing listener must not starve the others: isolate it and keep going.
					reportWorldSubmitFailure(failure);
				}
			}
		} catch (RuntimeException | LinkageError failure) {
			// We sit at the RETURN of a vanilla submit pass: an exception escaping here would
			// abort the whole frame.  Report once and keep rendering.
			reportWorldSubmitFailure(failure);
		}
	}

	private static void reportWorldSubmitFailure(Throwable failure) {
		if (!worldSubmitFailureLogged) {
			worldSubmitFailureLogged = true;
			LOGGER.warn("block outline: world-submit bridge failed, further failures suppressed this session ({})",
				failure.getClass().getSimpleName());
		}
	}
}
