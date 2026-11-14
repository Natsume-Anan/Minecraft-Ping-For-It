package nx.pingwheel.forge.platform;

import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.event.TickEvent;
import nx.pingwheel.common.platform.IPlatformClientEventService;

import java.util.concurrent.atomic.AtomicInteger;

import static nx.pingwheel.common.Global.LOGGER;
import static nx.pingwheel.common.Global.MOD_ID;

public class PlatformClientEventServiceImpl implements IPlatformClientEventService {

	private static final AtomicInteger HUD_LAYER_COUNTER = new AtomicInteger();

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
		// F4: Forge 26.3 has no SubmitCustomGeometryEvent.  The LevelRenderer
		// bridge will call this callback once its mixin injection point is
		// verified; keeping registration a no-op preserves client startup.
		// Do not pretend the callback is wired: the common outline pipeline
		// is created, but it cannot submit marker outlines yet.
		LOGGER.warn("Forge world-submit outline is unavailable until the F4 LevelRenderer bridge is implemented");
	}
}
