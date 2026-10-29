package nx.pingwheel.common.client.outline;

import java.util.List;

/**
 * Minimal M3b placeholder for block-display whitelist validation.
 *
 * <p>The upstream validator parses {@code namespace:path} entries and resolves
 * them against the block registry, throwing {@code ConfigValidationException}
 * for malformed input. Resolving entries needs the deferred block-presentation
 * pipeline, so validation is intentionally skipped for now: callers must not
 * throw, otherwise a stale client config file could block client start-up.
 *
 * <p>TODO(M8): restore the real validator from
 * {@code deferred/client/outline/BlockDisplayWhitelist.java} together with the
 * settings screen, and re-enable the rejection path in
 * {@code ClientConfig#validatedEntries}.
 */
public final class BlockDisplayWhitelist {

	private BlockDisplayWhitelist() {
	}

	/**
	 * TODO(M8): validate registry ids, tags and shape selectors.
	 *
	 * <p>Currently a deliberate no-op (accept everything).
	 */
	public static void validateEntries(List<String> entries, String fieldName) {
		// Intentionally empty: see the class TODO(M8).
	}
}
