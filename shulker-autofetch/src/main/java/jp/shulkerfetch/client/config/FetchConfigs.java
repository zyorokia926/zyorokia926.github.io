package jp.shulkerfetch.client.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import java.io.File;
import java.util.List;

/**
 * Client settings, stored by MaLiLib in {@code config/shulkerfetch.json}.
 * <p>
 * Names are unique (MaLiLib looks translations up by the lower-cased name, globally), and every on/off option
 * has its own toggle hotkey. Display names, comments and messages come from the mod's lang files (en_us / ja_jp);
 * the English strings below are only the fallback.
 */
public class FetchConfigs implements IConfigHandler {
	private static final String FILE_NAME = "shulkerfetch.json";

	public static final ConfigBooleanHotkeyed ENABLED = new ConfigBooleanHotkeyed("shulkerFetchEnabled", true, "M,T",
			"Master switch for the whole mod.", "shulkerfetch.config.pretty.enabled");
	public static final ConfigBooleanHotkeyed PRO_PLACE = new ConfigBooleanHotkeyed("shulkerFetchProPlace", true, "",
			"Fetch from shulker boxes while Easy Place runs with an accurate placement protocol (V3 / V2 / Slabs only).",
			"shulkerfetch.config.pretty.pro_place");
	public static final ConfigBooleanHotkeyed EASY_PLACE = new ConfigBooleanHotkeyed("shulkerFetchEasyPlace", true, "",
			"Fetch from shulker boxes when Litematica Easy Place needs an item.", "shulkerfetch.config.pretty.easy_place");
	public static final ConfigBooleanHotkeyed WHEEL_CLICK = new ConfigBooleanHotkeyed("shulkerFetchWheelClick", true, "",
			"Fetch from shulker boxes when picking a schematic block (wheel click / pick block hotkeys).",
			"shulkerfetch.config.pretty.wheel_click");
	public static final ConfigBooleanHotkeyed DEBUG_LOG = new ConfigBooleanHotkeyed("shulkerFetchDebugLog", false, "",
			"Write debug messages to the game log.", "shulkerfetch.config.pretty.debug_log");
	public static final ConfigHotkey OPEN_GUI = new ConfigHotkey("shulkerFetchOpenGui", "M,F",
			"Opens this configuration screen.");

	/** The on/off options; each carries its own toggle hotkey. */
	public static final List<ConfigBooleanHotkeyed> TOGGLES = ImmutableList.of(ENABLED, PRO_PLACE, EASY_PLACE, WHEEL_CLICK, DEBUG_LOG);
	/** Everything shown in the config screen. */
	public static final List<IConfigBase> ALL = ImmutableList.<IConfigBase>builder().addAll(TOGGLES).add(OPEN_GUI).build();
	/** Every hotkey that must be registered with MaLiLib's key handling. */
	public static final List<IHotkey> ALL_HOTKEYS = ImmutableList.<IHotkey>builder().addAll(TOGGLES).add(OPEN_GUI).build();

	@Override
	public void load() {
		File file = new File(FileUtils.getConfigDirectory(), FILE_NAME);
		if (file.isFile() && file.canRead()) {
			JsonElement element = JsonUtils.parseJsonFile(file);
			if (element != null && element.isJsonObject()) {
				JsonObject root = element.getAsJsonObject();
				ConfigUtils.readConfigBase(root, "Generic", TOGGLES);
				ConfigUtils.readConfigBase(root, "Hotkeys", ImmutableList.of(OPEN_GUI));
			}
		}
	}

	@Override
	public void save() {
		File dir = FileUtils.getConfigDirectory();
		if ((dir.exists() && dir.isDirectory()) || dir.mkdirs()) {
			JsonObject root = new JsonObject();
			ConfigUtils.writeConfigBase(root, "Generic", TOGGLES);
			ConfigUtils.writeConfigBase(root, "Hotkeys", ImmutableList.of(OPEN_GUI));
			JsonUtils.writeJsonToFile(root, new File(dir, FILE_NAME));
		}
	}
}
