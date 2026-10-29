package nx.pingwheel.common.integration;

import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Stub for the optional Simple Voice Chat integration.
 *
 * <p>TODO(M9): restore the real adapter. Returning empty degrades the team
 * context to FTB Teams or the vanilla scoreboard team, which is the documented
 * behaviour when voice chat is absent.</p>
 */
public class VoiceChatWrapper {
	private VoiceChatWrapper() {}

	public static Optional<UUID> getGroupId(Player player) {
		return Optional.empty();
	}

	public static Optional<UUID> getSelfGroupId() {
		return Optional.empty();
	}
}
