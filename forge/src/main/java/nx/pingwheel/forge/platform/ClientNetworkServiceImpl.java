package nx.pingwheel.forge.platform;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.PacketDistributor;
import nx.pingwheel.common.network.IPacket;
import nx.pingwheel.common.platform.IClientNetworkService;

public class ClientNetworkServiceImpl implements IClientNetworkService {

	@Override
	public void sendToServer(IPacket packet) {
		var connection = Minecraft.getInstance().getConnection();

		if (connection == null) {
			return;
		}

		EventNetworkChannel channel = PlatformNetworkServiceImpl.CHANNEL_MAP.get(packet.getId());

		if (channel == null || !channel.isRemotePresent(connection.getConnection())) {
			return;
		}

		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		packet.write(buffer);
		channel.send(buffer, PacketDistributor.SERVER.noArg());
	}
}
