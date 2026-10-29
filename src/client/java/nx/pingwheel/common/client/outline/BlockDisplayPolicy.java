package nx.pingwheel.common.client.outline;

import java.util.List;
import java.util.Objects;

/**
 * Minimal M3b placeholder for the compiled block-display policy.
 *
 * <p>The upstream implementation resolves whitelist entries against the block
 * registry and caches per-{@code BlockState} render decisions. That whole
 * pipeline lives in {@code deferred/client/outline/} until M7 (rendering /
 * outline rebuild), so this class only keeps the API shape that
 * {@code ClientConfig} needs.
 *
 * <p>TODO(M7): restore the real policy from
 * {@code deferred/client/outline/BlockDisplayPolicy.java}. The policy is a
 * transient, non-serialized cache, so replacing this placeholder does not
 * change the persisted JSON shape.
 */
public final class BlockDisplayPolicy {

	private static final BlockDisplayPolicy DEFAULT = new BlockDisplayPolicy(List.of("*:*"), List.of());

	private final List<String> whitelist;
	private final List<String> blacklist;

	private BlockDisplayPolicy(List<String> whitelist, List<String> blacklist) {
		this.whitelist = whitelist;
		this.blacklist = blacklist;
	}

	public static BlockDisplayPolicy defaults() {
		return DEFAULT;
	}

	/**
	 * TODO(M7): return a policy that actually resolves registry ids, tags and
	 * block shapes. Until then the compiled form only retains the source
	 * entries so diagnostics and callers keep working.
	 */
	public static BlockDisplayPolicy compile(List<String> whitelist, List<String> blacklist) {
		return new BlockDisplayPolicy(
			List.copyOf(Objects.requireNonNullElse(whitelist, List.of())),
			List.copyOf(Objects.requireNonNullElse(blacklist, List.of())));
	}

	/** The whitelist entries this policy was compiled from. */
	public List<String> whitelist() {
		return whitelist;
	}

	/** The shape-blacklist entries this policy was compiled from. */
	public List<String> blacklist() {
		return blacklist;
	}
}
