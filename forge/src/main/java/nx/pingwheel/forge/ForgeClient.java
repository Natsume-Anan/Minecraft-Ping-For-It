package nx.pingwheel.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.EventNetworkChannel;
import nx.pingwheel.common.CommonClient;
import nx.pingwheel.common.command.ClientCommandBuilder;
import nx.pingwheel.common.network.MarkerCreatedS2CPacket;
import nx.pingwheel.common.network.MarkerRejectedS2CPacket;
import nx.pingwheel.common.network.MarkerRemovedS2CPacket;
import nx.pingwheel.common.network.MarkerWinnerChangedS2CPacket;
import nx.pingwheel.common.network.PingLocationS2CPacket;
import nx.pingwheel.common.network.RateLimitPolicyS2CPacket;
import nx.pingwheel.common.network.ServerConfigSnapshotS2CPacket;
import nx.pingwheel.common.network.SyncDurationPolicyS2CPacket;
import nx.pingwheel.common.resource.LanguageUtils;
import nx.pingwheel.common.resource.ResourceReloadListener;
import nx.pingwheel.common.screen.SettingsScreen;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Client-only Forge wiring.  This class is intentionally not referenced by
 * {@link ForgeMain}; {@link ForgeClientBootstrap} loads it from the client
 * branch of the mod constructor so a dedicated server never resolves Minecraft
 * client classes.  Registration must happen there rather than in
 * {@code FMLClientSetupEvent}: Forge 26.3 fires the HUD-layer, key-mapping and
 * client-reload events earlier, during the {@code Minecraft} constructor.
 */
public final class ForgeClient {
	private ForgeClient() {}

	public static void init(FMLJavaModLoadingContext context) {
		CommonClient.INSTANCE.onInit();

		registerClientHandler(ForgeMain.PING_LOCATION_CHANNEL_S2C,
			PingLocationS2CPacket::readSafe, CommonClient.INSTANCE::onPingLocationPacket);
		registerClientHandler(ForgeMain.SERVER_CONFIG_SNAPSHOT_CHANNEL_S2C,
			ServerConfigSnapshotS2CPacket::readSafe, CommonClient.INSTANCE::onServerConfigSnapshotPacket);
		registerClientHandler(ForgeMain.MARKER_CREATED_CHANNEL_S2C,
			MarkerCreatedS2CPacket::readSafe, CommonClient.INSTANCE::onMarkerCreatedPacket);
		registerClientHandler(ForgeMain.MARKER_REMOVED_CHANNEL_S2C,
			MarkerRemovedS2CPacket::readSafe, CommonClient.INSTANCE::onMarkerRemovedPacket);
		registerClientHandler(ForgeMain.MARKER_REJECTED_CHANNEL_S2C,
			MarkerRejectedS2CPacket::readSafe, CommonClient.INSTANCE::onMarkerRejectedPacket);
		registerClientHandler(ForgeMain.MARKER_WINNER_CHANGED_CHANNEL_S2C,
			MarkerWinnerChangedS2CPacket::readSafe, CommonClient.INSTANCE::onMarkerWinnerChangedPacket);
		registerClientHandler(ForgeMain.RATE_LIMIT_POLICY_CHANNEL_S2C,
			RateLimitPolicyS2CPacket::readSafe, CommonClient.INSTANCE::onRateLimitPolicyPacket);
		registerClientHandler(ForgeMain.SYNC_DURATION_POLICY_CHANNEL_S2C,
			SyncDurationPolicyS2CPacket::readSafe, CommonClient.INSTANCE::onSyncDurationPolicyPacket);

		RegisterClientReloadListenersEvent.BUS.addListener(event ->
			event.registerReloadListener(new ResourceReloadListener()));

		RegisterClientCommandsEvent.BUS.addListener(event ->
			event.getDispatcher().register(ClientCommandBuilder.build((commandContext, success, response) -> {
				if (success) {
					commandContext.getSource().sendSuccess(() -> LanguageUtils.withModPrefix(response), false);
				} else {
					commandContext.getSource().sendFailure(LanguageUtils.withModPrefix(response));
				}
			})));

		context.registerExtensionPoint(
			ConfigScreenHandler.ConfigScreenFactory.class,
			() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new SettingsScreen(parent))
		);
	}

	private static <T> void registerClientHandler(
		EventNetworkChannel channel,
		Function<FriendlyByteBuf, T> reader,
		Consumer<T> handler
	) {
		channel.addListener(event -> {
			var context = event.getSource();

			try {
				var payload = event.getPayload();

				if (payload != null) {
					T packet = reader.apply(payload);
					context.enqueueWork(() -> handler.accept(packet));
				}
			} finally {
				// A malformed packet must not leave the channel unhandled and
				// disconnect the client; the safe reader already returns a
				// corrupt sentinel for recoverable payloads.
				context.setPacketHandled(true);
			}
		});
	}
}
