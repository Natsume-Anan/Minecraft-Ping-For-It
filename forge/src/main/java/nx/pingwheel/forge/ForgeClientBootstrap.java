package nx.pingwheel.forge;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Keeps the client-only entrypoint out of {@link ForgeMain}'s direct references.
 *
 * <p>The Forge mod jar is universal, but the dedicated server must never load
 * {@code ForgeClient} or any Minecraft client class.  Client registration runs
 * synchronously from the mod constructor: Forge 26.3 fires the HUD-layer,
 * key-mapping and client-reload events inside the {@code Minecraft} constructor,
 * before {@code FMLClientSetupEvent}.  Registering from the setup event would be
 * silent and too late because those events are self-destructing.</p>
 */
public final class ForgeClientBootstrap {
	private ForgeClientBootstrap() {}

	public static void register(FMLJavaModLoadingContext context) {
		ForgeClient.init(context);
	}
}
