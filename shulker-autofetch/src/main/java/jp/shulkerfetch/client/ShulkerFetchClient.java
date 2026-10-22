package jp.shulkerfetch.client;

import fi.dy.masa.malilib.event.InitializationHandler;
import jp.shulkerfetch.client.config.FetchInitHandler;
import jp.shulkerfetch.common.Protocol;
import jp.shulkerfetch.common.Protocol.Status;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

public class ShulkerFetchClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FabricLoader loader = FabricLoader.getInstance();
		for (String required : new String[] {"litematica", "malilib"}) {
			if (!loader.isModLoaded(required)) {
				throw new IllegalStateException("Shulker AutoFetch requires the mod '" + required + "' on the client");
			}
		}
		InitializationHandler.getInstance().registerInitializationHandler(new FetchInitHandler());

		ClientPlayNetworking.registerGlobalReceiver(Protocol.HELLO, (client, handler, buf, sender) -> {
			int version = buf.readVarInt();
			client.execute(() -> FetchController.onHello(version));
		});
		ClientPlayNetworking.registerGlobalReceiver(Protocol.RESPONSE, (client, handler, buf, sender) -> {
			int id = buf.readVarInt();
			Status status = Status.byId(buf.readVarInt());
			int amount = buf.readVarInt();
			int slot = buf.readVarInt();
			client.execute(() -> FetchController.onResponse(id, status, amount, slot));
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> FetchController.reset());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FetchController.reset());
		ClientTickEvents.END_CLIENT_TICK.register((MinecraftClient client) -> FetchController.onTick());
	}
}
