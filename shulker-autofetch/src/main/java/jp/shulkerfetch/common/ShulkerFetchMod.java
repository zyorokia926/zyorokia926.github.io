package jp.shulkerfetch.common;

import jp.shulkerfetch.server.ServerConfig;
import jp.shulkerfetch.server.ServerFetchHandler;
import net.fabricmc.api.ModInitializer;

/** Entry point used on every side; registers the server half (also active in single player). */
public class ShulkerFetchMod implements ModInitializer {
	@Override
	public void onInitialize() {
		new ServerFetchHandler(ServerConfig.load()).register();
	}
}
