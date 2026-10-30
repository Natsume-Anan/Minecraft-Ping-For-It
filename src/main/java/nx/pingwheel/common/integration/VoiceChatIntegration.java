package nx.pingwheel.common.integration;

/**
 * Stub for the optional Simple Voice Chat plugin entry point.
 *
 * <p>TODO(M9): restore the real {@code VoicechatPlugin} implementation and
 * re-add the {@code voicechat} entry point in fabric.mod.json. The plugin API
 * types are not on the classpath, so the lifecycle callbacks below are kept as
 * {@code Object}-typed shells with the exact upstream names and the plugin id;
 * {@link VoiceChatWrapper} returns empty until M9.
 *
 * <p>Restored surface from the 10/26 build (decompiled): {@code getPluginId()}
 * returning {@code "pingforit"}, {@code registerEvents(...)} plus the two
 * event callbacks that cached the server/client API handles.
 */
public class VoiceChatIntegration {

	/** The plugin id the voice chat mod keys this integration by. */
	public static final String PLUGIN_ID = "pingforit";

	/** TODO(M9): {@code VoicechatServerApi} once the API is on the classpath. */
	public static Object serverApi = null;

	/** TODO(M9): {@code VoicechatClientApi} once the API is on the classpath. */
	public static Object clientApi = null;

	public String getPluginId() {
		return PLUGIN_ID;
	}

	/** TODO(M9): register the server-started and client-init callbacks. */
	public void registerEvents(Object registration) {
		// Intentionally empty: the voice chat event API is not on the classpath.
	}

	/** TODO(M9): cache the server API handle from the server-started event. */
	private void onServerStarted(Object event) {
		// Intentionally empty: the voice chat event API is not on the classpath.
	}

	/** TODO(M9): cache the client API handle from the client-init event. */
	private void onClientInitialized(Object event) {
		// Intentionally empty: the voice chat event API is not on the classpath.
	}
}
