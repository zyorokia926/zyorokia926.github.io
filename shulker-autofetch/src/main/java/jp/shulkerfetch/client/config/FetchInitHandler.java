package jp.shulkerfetch.client.config;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import jp.shulkerfetch.common.Protocol;

/** Registers the config file, the hotkey and the config screen with MaLiLib. */
public class FetchInitHandler implements IInitializationHandler, IKeybindProvider {
	@Override
	public void registerModHandlers() {
		ConfigManager.getInstance().registerConfigHandler(Protocol.MOD_ID, new FetchConfigs());
		InputEventHandler.getKeybindManager().registerKeybindProvider(this);
		FetchConfigs.OPEN_GUI.getKeybind().setCallback((action, key) -> {
			GuiBase.openGui(new FetchConfigGui());
			return true;
		});
	}

	@Override
	public void addKeysToMap(IKeybindManager manager) {
		FetchConfigs.HOTKEYS.forEach(h -> manager.addKeybindToMap(h.getKeybind()));
	}

	@Override
	public void addHotkeys(IKeybindManager manager) {
		manager.addHotkeysForCategory("Shulker AutoFetch", "shulkerfetch.hotkeys.category", FetchConfigs.HOTKEYS);
	}
}
