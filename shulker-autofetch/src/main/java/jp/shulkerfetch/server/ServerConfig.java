package jp.shulkerfetch.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-side switches, stored in {@code config/shulkerfetch-server.json}. */
public final class ServerConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("shulkerfetch");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Master switch: when false the server answers every request with DISABLED. */
	public boolean enabled = true;
	/** Minimum ticks between two accepted requests of the same player. */
	public int cooldownTicks = 5;

	public static ServerConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("shulkerfetch-server.json");
		ServerConfig cfg = new ServerConfig();
		if (Files.isRegularFile(path)) {
			try (Reader r = Files.newBufferedReader(path)) {
				ServerConfig read = GSON.fromJson(r, ServerConfig.class);
				if (read != null) {
					cfg = read;
				}
			} catch (Exception e) {
				LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		cfg.cooldownTicks = Math.max(1, Math.min(cfg.cooldownTicks, 200));
		try (Writer w = Files.newBufferedWriter(path)) {
			GSON.toJson(cfg, w);
		} catch (IOException e) {
			LOGGER.warn("Could not write {}", path, e);
		}
		return cfg;
	}
}
