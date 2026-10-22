package jp.shulkerfetch.client.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import java.io.File;
import java.util.List;

/** Client settings, stored by MaLiLib in {@code config/shulkerfetch.json}. */
public class FetchConfigs implements IConfigHandler {
	private static final String FILE_NAME = "shulkerfetch.json";

	public static final ConfigBoolean ENABLED = new ConfigBoolean("enabled", true,
			"Master switch for the whole mod.\nMod全体の有効/無効");
	public static final ConfigBoolean PRO_PLACE = new ConfigBoolean("proPlaceIntegration", true,
			"Fetch from shulker boxes while Easy Place runs with an accurate placement protocol (V3 / V2 / slabs only).\n"
					+ "Easy Place Protocol (V3/V2/Slabs) 使用時の自動取得");
	public static final ConfigBoolean EASY_PLACE = new ConfigBoolean("easyPlaceIntegration", true,
			"Fetch from shulker boxes when Litematica Easy Place needs an item.\nEasy Place 設置時の自動取得");
	public static final ConfigBoolean WHEEL_CLICK = new ConfigBoolean("wheelClickFetch", true,
			"Fetch from shulker boxes when picking a schematic block (wheel click / pick block hotkeys).\n"
					+ "設計図ブロックのホイールクリック(Pick Block)時の自動取得");
	public static final ConfigBoolean DEBUG_LOG = new ConfigBoolean("debugLog", false,
			"Write debug messages to the game log.\nデバッグログ");
	public static final ConfigHotkey OPEN_GUI = new ConfigHotkey("openConfigGui", "M,S",
			"Opens this configuration screen.\n設定画面を開く");

	public static final List<IConfigBase> OPTIONS = ImmutableList.of(ENABLED, PRO_PLACE, EASY_PLACE, WHEEL_CLICK, DEBUG_LOG);
	public static final List<ConfigHotkey> HOTKEYS = ImmutableList.of(OPEN_GUI);
	/** Everything shown in the config screen. */
	public static final List<IConfigBase> ALL = ImmutableList.<IConfigBase>builder().addAll(OPTIONS).addAll(HOTKEYS).build();

	@Override
	public void load() {
		File file = new File(FileUtils.getConfigDirectory(), FILE_NAME);
		if (file.isFile() && file.canRead()) {
			JsonElement element = JsonUtils.parseJsonFile(file);
			if (element != null && element.isJsonObject()) {
				JsonObject root = element.getAsJsonObject();
				ConfigUtils.readConfigBase(root, "Generic", OPTIONS);
				ConfigUtils.readConfigBase(root, "Hotkeys", HOTKEYS);
			}
		}
	}

	@Override
	public void save() {
		File dir = FileUtils.getConfigDirectory();
		if ((dir.exists() && dir.isDirectory()) || dir.mkdirs()) {
			JsonObject root = new JsonObject();
			ConfigUtils.writeConfigBase(root, "Generic", OPTIONS);
			ConfigUtils.writeConfigBase(root, "Hotkeys", HOTKEYS);
			JsonUtils.writeJsonToFile(root, new File(dir, FILE_NAME));
		}
	}
}
