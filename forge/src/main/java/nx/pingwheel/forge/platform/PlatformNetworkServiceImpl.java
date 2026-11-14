package nx.pingwheel.forge.platform;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.PacketDistributor;
import nx.pingwheel.common.network.IPacket;
import nx.pingwheel.common.platform.IPlatformNetworkService;

import java.util.HashMap;
import java.util.Map;

public class PlatformNetworkServiceImpl implements IPlatformNetworkService {

	public static final Map<Identifier, EventNetworkChannel> CHANNEL_MAP = new HashMap<>();

	@Override
	public void sendToClient(IPacket packet, ServerPlayer player) {
		EventNetworkChannel channel = CHANNEL_MAP.get(packet.getId());

		if (channel == null || !channel.isRemotePresent(player.connection.getConnection())) {
			return;
		}

		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		packet.write(buffer);
		channel.send(buffer, PacketDistributor.PLAYER.with(player));
	}
}
