package nx.pingwheel.forge.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import nx.pingwheel.common.platform.IPlatformServerEventService;

import java.util.function.Consumer;

public class PlatformServerEventServiceImpl implements IPlatformServerEventService {

	@Override
	public void registerPlayerLogoutEvent(Consumer<ServerPlayer> callback) {
		PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				callback.accept(player);
			}
		});
	}

	@Override
	public void registerServerTickEvent(Consumer<MinecraftServer> callback) {
		TickEvent.ServerTickEvent.Post.BUS.addListener(event -> callback.accept(event.server()));
	}
}
