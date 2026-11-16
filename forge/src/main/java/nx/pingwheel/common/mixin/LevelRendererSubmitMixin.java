package nx.pingwheel.common.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;

import nx.pingwheel.forge.platform.PlatformClientEventServiceImpl;

/**
 * F4: Forge replacement for Fabric's {@code LevelRenderEvents.COLLECT_SUBMITS}.
 *
 * <p>Forge 26.3 exposes no event that carries both a {@link PoseStack} and this frame's
 * {@link SubmitNodeCollector}, so the outline pass is driven from the RETURN of
 * {@code LevelRenderer#submitFeatures}. That is the point Fabric's {@code COLLECT_SUBMITS}
 * fires at too - after {@code submitBlockEntities} and after the block-outline submit -
 * so {@code EntityBlockGlowState} already holds this frame's glowing positions.
 *
 * <p>The submitted {@link PoseStack} is a fresh one rather than the captured local: the
 * local is itself a {@code new PoseStack()} created at the head of {@code submitFeatures}
 * and vanilla asserts it is empty right before the RETURN ({@code checkPoseStack}), while
 * {@code BlockOutlinePass} only pushes/translates/pops. The two are therefore equivalent
 * in state, and a private stack cannot disturb vanilla's own assertion or later submits.
 *
 * <p>{@code require = 0}: the local mixin config is {@code required: true} with
 * {@code defaultRequire: 1}, so a descriptor change in a future 26.3.x must not turn into
 * a {@code MixinApplyError} while {@code LevelRenderer} is loading. The signal for a
 * missed hook is the absence of the one-shot "bridge active" log line, not a crash.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSubmitMixin {

	@Inject(
		method = "submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
		at = @At("RETURN"),
		require = 0
	)
	private void pingforit$submitWorldOutlines(
		LevelRenderState levelRenderState,
		SubmitNodeCollector submitNodeCollector,
		boolean renderOutline,
		CallbackInfo ci
	) {
		PlatformClientEventServiceImpl.dispatchWorldSubmit(new PoseStack(), submitNodeCollector);
	}
}
