package jp.shulkerfetch.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import jp.shulkerfetch.common.FetchTransaction;
import jp.shulkerfetch.common.FetchTransaction.Result;
import jp.shulkerfetch.common.Protocol;
import jp.shulkerfetch.common.Protocol.Status;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authoritative side of the fetch. The client only asks; every precondition is re-checked here
 * against the real inventory, and all changes are made on the server thread in one step.
 */
public final class ServerFetchHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger("shulkerfetch");

	private final ServerConfig config;
	private final Map<UUID, Integer> lastAccepted = new HashMap<>();

	public ServerFetchHandler(ServerConfig config) {
		this.config = config;
	}

	public void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeVarInt(Protocol.VERSION);
			buf.writeString(FabricLoader.getInstance().getModContainer(Protocol.MOD_ID)
					.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
			sender.sendPacket(Protocol.HELLO, buf);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> lastAccepted.remove(handler.player.getUuid()));
		ServerPlayNetworking.registerGlobalReceiver(Protocol.REQUEST, this::onRequest);
	}

	private void onRequest(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler,
			PacketByteBuf buf, PacketSender responder) {
		// Read everything now: the buffer must not be touched after this method returns.
		int protocol;
		int requestId;
		int selectedSlot;
		ItemStack requested;
		try {
			protocol = buf.readVarInt();
			requestId = buf.readVarInt();
			selectedSlot = buf.readVarInt();
			requested = buf.readItemStack();
			if (buf.isReadable()) {
				requested = ItemStack.EMPTY; // trailing garbage: treat as malformed
			}
		} catch (RuntimeException e) {
			return; // undecodable, nobody to answer
		}
		ItemStack target = requested;
		server.execute(() -> reply(player, requestId, protocol, selectedSlot, target));
	}

	private void reply(ServerPlayerEntity player, int requestId, int protocol, int selectedSlot, ItemStack target) {
		Result result = process(player, protocol, selectedSlot, target);
		if (result.status() != Status.OK) {
			// The client applies the same change optimistically; on any refusal resend the authoritative state.
			player.playerScreenHandler.syncState();
		}
		PacketByteBuf out = PacketByteBufs.create();
		out.writeVarInt(requestId);
		out.writeVarInt(result.status().ordinal());
		out.writeVarInt(result.amount());
		out.writeVarInt(result.slot());
		ServerPlayNetworking.send(player, Protocol.RESPONSE, out);
	}

	/** Performs the whole transaction on the server thread. Public for tests. */
	public Result process(ServerPlayerEntity player, int protocol, int selectedSlot, ItemStack requested) {
		if (!config.enabled) {
			return Result.of(Status.DISABLED);
		}
		if (protocol != Protocol.VERSION) {
			return Result.of(Status.PROTOCOL_MISMATCH);
		}
		if (requested.isEmpty() || requested.getCount() != 1 || !player.isAlive() || player.isSpectator()) {
			return Result.of(Status.BAD_REQUEST);
		}
		// Another screen open means a cursor stack / container transaction may be in flight.
		if (player.currentScreenHandler != player.playerScreenHandler) {
			return Result.of(Status.BUSY);
		}
		int now = player.server.getTicks();
		Integer last = lastAccepted.get(player.getUuid());
		if (last != null && now - last < config.cooldownTicks) {
			return Result.of(Status.BUSY);
		}
		lastAccepted.put(player.getUuid(), now);

		PlayerInventory inv = player.getInventory();
		if (selectedSlot != inv.selectedSlot) {
			return Result.of(Status.BAD_REQUEST); // client and server disagree about the held slot
		}
		Result result = FetchTransaction.run(inv, requested, inv.selectedSlot);
		if (result.status() == Status.OK) {
			inv.markDirty();
			player.playerScreenHandler.sendContentUpdates();
			LOGGER.debug("{} took {}x {} from a shulker box into slot {}", player.getName().getString(),
					result.amount(), requested.getItem(), result.slot());
		}
		return result;
	}
}
