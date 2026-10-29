package nx.pingwheel.common.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nx.pingwheel.common.config.ServerConfigSnapshot;

/**
 * Minimal M3b placeholder for the client/server settings screen.
 *
 * <p>The real screen is built on the pre-26.3 immediate-mode GUI API
 * ({@code GuiGraphics} / {@code RenderStateShard}), which 26.3 replaced with the
 * {@code GuiGraphicsExtractor} state-extraction pipeline. Rebuilding it is an
 * M8 task, so this class stays intentionally empty while keeping the call chain
 * intact: the {@code /pingforit config} command and the settings key binding
 * still open a screen, and the S2C snapshot/disconnect notifications still have
 * a receiver.
 *
 * <p>TODO(M8): restore {@code deferred/screen/SettingsScreen.java} and its
 * category/layout/option helpers ({@code SettingsCategoryCatalog},
 * {@code SettingsScreenLayout}, {@code OptionUtils}, ...) on top of the 26.3 GUI
 * pipeline.
 */
public class SettingsScreen extends Screen {

	/** Key matches {@code assets/pingforit/lang/*.json#settings.pingforit.title}. */
	private static final String TITLE_KEY = "settings.pingforit.title";

	public SettingsScreen() {
		super(Component.translatable(TITLE_KEY));
	}

	/** TODO(M8): clear the cached server settings session once the screen owns one. */
	public static void notifyServerDisconnected() {
		// Intentionally empty: see the class TODO(M8).
	}

	/** TODO(M8): route the snapshot into the live settings session. */
	public static void notifyServerConfigSnapshot(long requestId, ServerConfigSnapshot snapshot) {
		// Intentionally empty: see the class TODO(M8).
	}
}
