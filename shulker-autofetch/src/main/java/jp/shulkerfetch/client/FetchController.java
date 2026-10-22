package jp.shulkerfetch.client;

import fi.dy.masa.litematica.util.EasyPlaceProtocol;
import fi.dy.masa.litematica.util.InventoryUtils;
import fi.dy.masa.litematica.util.PlacementHandler;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import jp.shulkerfetch.client.config.FetchConfigs;
import jp.shulkerfetch.common.Protocol;
import jp.shulkerfetch.common.Protocol.Status;
import jp.shulkerfetch.common.ShulkerInventory;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client half of the fetch. Litematica's pick-block routine calls {@link #onPick}; if the needed item is
 * not in the inventory but inside a shulker box, a request is sent to the server and the original pick is
 * replayed once the server has confirmed the transfer. All state lives on the client thread.
 */
public final class FetchController {
	private static final Logger LOGGER = LoggerFactory.getLogger("shulkerfetch");
	/** How long to wait for the server before giving up. */
	private static final int TIMEOUT_TICKS = 60;
	/** After a failure the same item is not requested/scanned again for this long. */
	private static final int RETRY_COOLDOWN_TICKS = 40;

	public enum Source {
		NONE, WHEEL_CLICK, EASY_PLACE
	}

	private record Pending(int id, ItemStack stack, BlockPos pos, int startTick) {
	}

	/** Set by the Litematica entry-point mixins for the duration of one call; cleared every tick as well. */
	public static Source source = Source.NONE;

	private static int tick;
	private static int nextId = 1;
	private static Pending pending;
	private static boolean replaying;
	private static boolean serverReady;
	private static boolean warnedServer;
	private static ItemStack blockedStack = ItemStack.EMPTY;
	private static int blockedUntil;

	private FetchController() {
	}

	/** @return true if the original Litematica pick must be skipped for now (a fetch is under way). */
	public static boolean onPick(ItemStack stack, BlockPos pos, World schematicWorld, MinecraftClient mc) {
		if (replaying || stack.isEmpty() || mc.player == null || !isSourceEnabled(mc)) {
			return false;
		}
		PlayerInventory inv = mc.player.getInventory();
		stack = stack.copy();
		stack.setCount(1);
		if (ShulkerInventory.isAvailableInInventory(inv, stack)) {
			return false; // nothing to fetch: Litematica behaves exactly as before
		}
		if (pending != null) {
			return true; // one request at a time; also swallows repeated easy-place ticks
		}
		if (isBlocked(stack)) {
			return false;
		}
		if (ShulkerInventory.find(inv, stack) == null) {
			debug("{} is in no shulker box", stack);
			block(stack);
			return false; // let Litematica do what it would do without this mod
		}
		if (!inv.getMainHandStack().isEmpty() && ShulkerInventory.findEmptySlot(inv) < 0) {
			block(stack);
			say(Status.INVENTORY_FULL);
			return false;
		}
		if (!serverReady || !ClientPlayNetworking.canSend(Protocol.REQUEST)) {
			if (!warnedServer) {
				warnedServer = true;
				mc.inGameHud.setOverlayMessage(Text.translatable("shulkerfetch.msg.no_server"), false);
			}
			block(stack);
			return false;
		}
		int id = nextId++;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(Protocol.VERSION);
		buf.writeVarInt(id);
		buf.writeItemStack(stack);
		ClientPlayNetworking.send(Protocol.REQUEST, buf);
		pending = new Pending(id, stack, pos.toImmutable(), tick);
		debug("request #{} for {} sent", id, stack);
		return true;
	}

	private static boolean isSourceEnabled(MinecraftClient mc) {
		if (!FetchConfigs.ENABLED.getBooleanValue() || mc.player.isCreative() || mc.player.isSpectator()) {
			return false;
		}
		switch (source) {
			case WHEEL_CLICK:
				return FetchConfigs.WHEEL_CLICK.getBooleanValue();
			case EASY_PLACE:
				if (!FetchConfigs.EASY_PLACE.getBooleanValue()) {
					return false;
				}
				return PlacementHandler.getEffectiveProtocolVersion() == EasyPlaceProtocol.NONE
						|| FetchConfigs.PRO_PLACE.getBooleanValue();
			default:
				return false;
		}
	}

	/** Called on the client thread when the server answers a request. */
	public static void onResponse(MinecraftClient mc, int id, Status status, int amount, int slot) {
		Pending p = pending;
		if (p == null || p.id() != id) {
			debug("ignoring stale response #{}", id);
			return;
		}
		pending = null;
		debug("response #{}: {} amount={} slot={}", id, status, amount, slot);
		if (status != Status.OK && status != Status.ALREADY_AVAILABLE) {
			block(p.stack());
			say(status);
			return;
		}
		World schematic = SchematicWorldHandler.getSchematicWorld();
		if (mc.player == null || schematic == null) {
			return;
		}
		// The server has already synced the inventory (those packets arrived before this one), so the
		// original routine now finds the item and completes normally. No new request can start meanwhile.
		replaying = true;
		try {
			InventoryUtils.schematicWorldPickBlock(p.stack(), p.pos(), schematic, mc);
		} finally {
			replaying = false;
		}
	}

	public static void onTick(MinecraftClient mc) {
		tick++;
		source = Source.NONE;
		if (pending != null && tick - pending.startTick() > TIMEOUT_TICKS) {
			debug("request #{} timed out", pending.id());
			block(pending.stack());
			pending = null;
			mc.inGameHud.setOverlayMessage(Text.translatable("shulkerfetch.msg.timeout"), false);
		}
	}

	public static void onHello(int serverVersion) {
		serverReady = serverVersion == Protocol.VERSION;
		warnedServer = false;
		if (!serverReady) {
			LOGGER.warn("Server has protocol {}, this client needs {}; shulker fetching disabled", serverVersion, Protocol.VERSION);
			MinecraftClient.getInstance().inGameHud.setOverlayMessage(Text.translatable("shulkerfetch.msg.version_mismatch"), false);
		}
	}

	public static void reset() {
		pending = null;
		serverReady = false;
		warnedServer = false;
		blockedStack = ItemStack.EMPTY;
		blockedUntil = 0;
		source = Source.NONE;
	}

	private static boolean isBlocked(ItemStack stack) {
		return tick < blockedUntil && ItemStack.canCombine(stack, blockedStack);
	}

	private static void block(ItemStack stack) {
		blockedStack = stack.copy();
		blockedUntil = tick + RETRY_COOLDOWN_TICKS;
	}

	private static void say(Status status) {
		String key = switch (status) {
			case NOT_FOUND -> "shulkerfetch.msg.not_found";
			case INVENTORY_FULL -> "shulkerfetch.msg.inventory_full";
			case BUSY -> "shulkerfetch.msg.busy";
			case DISABLED -> "shulkerfetch.msg.server_disabled";
			case PROTOCOL_MISMATCH -> "shulkerfetch.msg.version_mismatch";
			default -> "shulkerfetch.msg.failed";
		};
		MinecraftClient.getInstance().inGameHud.setOverlayMessage(Text.translatable(key), false);
	}

	private static void debug(String msg, Object... args) {
		if (FetchConfigs.DEBUG_LOG.getBooleanValue()) {
			LOGGER.info("[shulkerfetch] " + msg, args);
		}
	}
}
