package nx.pingwheel.common.integration;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Stub for the optional Distant Horizons integration.
 *
 * <p>TODO(M9): restore the real adapter (async distant ray trace + API version
 * diagnostics). Stubbed so the M3a build does not require the DH API artifact.</p>
 *
 * <p>Public surface unchanged: {@link #traceDistantAsync} returns {@code false},
 * which is the documented "gated off, no completion will ever be invoked"
 * contract, so callers fall back to their own synchronous ray trace.</p>
 */
public class DistantHorizonsIntegration {
	private DistantHorizonsIntegration() {}

	public static boolean traceDistantAsync(Vec3 rayStart, Vec3 direction, Consumer<Optional<BlockHitResult>> completion) {
		return false;
	}

	public static void logVersionDiagnostics() {
	}

	public static void logUnguardedLinkFailure(LinkageError error) {
	}
}
