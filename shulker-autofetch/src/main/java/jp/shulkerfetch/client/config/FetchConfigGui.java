package jp.shulkerfetch.client.config;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import java.util.List;
import jp.shulkerfetch.common.Protocol;

public class FetchConfigGui extends GuiConfigsBase {
	public FetchConfigGui() {
		super(10, 50, Protocol.MOD_ID, null, "shulkerfetch.gui.title");
	}

	@Override
	public List<ConfigOptionWrapper> getConfigs() {
		return ConfigOptionWrapper.createFor(FetchConfigs.ALL);
	}
}
