package nx.pingwheel.forge.platform;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import nx.pingwheel.common.platform.IClientPlatformContextService;

public class PlatformClientContextServiceImpl extends PlatformContextServiceImpl implements IClientPlatformContextService {

	@Override
	public void registerKeyMapping(KeyMapping keyMapping) {
		RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(keyMapping));
	}
}
