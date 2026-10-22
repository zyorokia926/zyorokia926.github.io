package jp.shulkerfetch.client.config;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import jp.shulkerfetch.common.Protocol;

/** Registers the config file, the hotkeys and the config screen with MaLiLib. */
public class FetchInitHandler implements IInitializationHandler, IKeybindProvider {
	@Override
	public void registerModHandlers() {
		ConfigManager.getInstance().registerConfigHandler(Protocol.MOD_ID, new FetchConfigs());
		InputEventHandler.getKeybindManager().registerKeybindProvider(this);
		for (ConfigBooleanHotkeyed toggle : FetchConfigs.TOGGLES) {
			toggle.getKeybind().setCallback(new ToggleCallback(toggle));
		}
		FetchConfigs.OPEN_GUI.getKeybind().setCallback((action, key) -> {
			GuiBase.openGui(new FetchConfigGui());
			return true;
		});
	}

	@Override
	public void addKeysToMap(IKeybindManager manager) {
		FetchConfigs.ALL_HOTKEYS.forEach(h -> manager.addKeybindToMap(h.getKeybind()));
	}

	@Override
	public void addHotkeys(IKeybindManager manager) {
		manager.addHotkeysForCategory("Shulker AutoFetch", "shulkerfetch.hotkeys.category", FetchConfigs.ALL_HOTKEYS);
	}

	/**
	 * Flips the option and shows the new state above the hotbar, the way Litematica / Tweakeroo do. MaLiLib's own
	 * toggle message has no Japanese text, so this one comes from the mod's lang files.
	 */
	public static class ToggleCallback implements IHotkeyCallback {
		private final ConfigBooleanHotkeyed config;

		public ToggleCallback(ConfigBooleanHotkeyed config) {
			this.config = config;
		}

		@Override
		public boolean onKeyAction(KeyAction action, IKeybind key) {
			this.config.toggleBooleanValue();
			boolean on = this.config.getBooleanValue();
			String color = on ? GuiBaseColors.GREEN : GuiBaseColors.RED;
			String state = StringUtils.translate(on ? "shulkerfetch.message.value.on" : "shulkerfetch.message.value.off");
			InfoUtils.printActionbarMessage("shulkerfetch.message.toggled", this.config.getPrettyName(), color + state + GuiBaseColors.RESET);
			return true;
		}
	}

	private static final class GuiBaseColors {
		static final String GREEN = fi.dy.masa.malilib.gui.GuiBase.TXT_GREEN;
		static final String RED = fi.dy.masa.malilib.gui.GuiBase.TXT_RED;
		static final String RESET = fi.dy.masa.malilib.gui.GuiBase.TXT_RST;
	}
}
