package jp.shulkerfetch.client;

import fi.dy.masa.litematica.util.EasyPlaceProtocol;
import fi.dy.masa.litematica.util.PlacementHandler;
import java.util.ArrayDeque;
import jp.shulkerfetch.client.config.FetchConfigs;
import jp.shulkerfetch.common.FetchTransaction;
import jp.shulkerfetch.common.Protocol;
import jp.shulkerfetch.common.Protocol.Status;
import jp.shulkerfetch.common.ShulkerInventory;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client half of the fetch, called at the start of Litematica's pick-block routine.
 * <p>
 * If the item is already in the inventory nothing happens and Litematica behaves exactly as before.
 * Only when it is missing and a shulker box holds it, the very same transfer the server will perform is applied
 * to the local inventory <em>immediately</em> (so the item is in the held slot when the original routine continues
 * in the same tick) and a request is sent. The server re-checks everything and is authoritative: it syncs the
 * real inventory after every request and resends the whole state if it refuses, which corrects the local guess.
 */
public final class FetchController {
	private static final Logger LOGGER = LoggerFactory.getLogger("shulkerfetch");
	/** Requests that have not been answered yet; further fetches wait until there is room. */
	private static final int MAX_IN_FLIGHT = 4;
	private static final int TIMEOUT_TICKS = 100;
	/** After a failed lookup the same item is not scanned/requested again for this long. */
	private static final int RETRY_COOLDOWN_TICKS = 40;

	public enum Source {
		NONE, WHEEL_CLICK, EASY_PLACE
	}

	private record Pending(int id, int startTick, ItemStack stack) {
	}

	/** Set by the Litematica entry-point mixins for the duration of one call; cleared every tick as well. */
	public static Source source = Source.NONE;

	private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
	private static int tick;
	private static int nextId = 1;
	private static boolean serverReady;
	private static boolean warnedServer;
	private static ItemStack blockedStack = ItemStack.EMPTY;
	private static int blockedUntil;

	private FetchController() {
	}

	public static void beforePick(ItemStack stack, MinecraftClient mc) {
		if (stack.isEmpty() || mc.player == null || mc.currentScreen != null || !isSourceEnabled(mc)) {
			return;
		}
		PlayerInventory inv = mc.player.getInventory();
		ItemStack wanted = stack.copy();
		wanted.setCount(1);
		if (ShulkerInventory.isAvailableInInventory(inv, wanted)) {
			return; // the inventory comes first: Litematica handles it as usual
		}
		if (isBlocked(wanted) || PENDING.size() >= MAX_IN_FLIGHT) {
			return;
		}
		if (!serverReady || !ClientPlayNetworking.canSend(Protocol.REQUEST)) {
			if (!warnedServer) {
				warnedServer = true;
				LOGGER.warn("[shulkerfetch] the server does not have Shulker AutoFetch installed (or its version differs); fetching from shulker boxes is off");
			}
			block(wanted);
			return;
		}
		int selected = inv.selectedSlot;
		// Same code the server runs; changes the local inventory only if the transfer is possible.
		FetchTransaction.Result result = FetchTransaction.run(inv, wanted, selected);
		if (result.status() != Status.OK) {
			debug("{}: {}", wanted, result.status());
			block(wanted);
			return;
		}
		int id = nextId++;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(Protocol.VERSION);
		buf.writeVarInt(id);
		buf.writeVarInt(selected);
		buf.writeItemStack(wanted);
		ClientPlayNetworking.send(Protocol.REQUEST, buf);
		PENDING.add(new Pending(id, tick, wanted));
		debug("took {}x {} into slot {} (request #{})", result.amount(), wanted, result.slot(), id);
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

	/** Called on the client thread when the server answers a request (its inventory sync arrived just before). */
	public static void onResponse(int id, Status status, int amount, int slot) {
		Pending found = null;
		for (Pending p : PENDING) {
			if (p.id() == id) {
				found = p;
				break;
			}
		}
		if (found == null) {
			debug("ignoring stale response #{}", id);
			return;
		}
		PENDING.remove(found);
		debug("response #{}: {} amount={} slot={}", id, status, amount, slot);
		if (status != Status.OK) {
			// The server has already resent the real inventory, replacing our guess.
			block(found.stack());
		}
	}

	public static void onTick() {
		tick++;
		source = Source.NONE;
		while (!PENDING.isEmpty() && tick - PENDING.peekFirst().startTick() > TIMEOUT_TICKS) {
			Pending timedOut = PENDING.pollFirst();
			debug("request #{} timed out", timedOut.id());
			block(timedOut.stack());
		}
	}

	public static void onHello(int serverVersion) {
		serverReady = serverVersion == Protocol.VERSION;
		warnedServer = false;
		if (!serverReady) {
			LOGGER.warn("[shulkerfetch] server protocol {} differs from this client's {}; shulker fetching disabled", serverVersion, Protocol.VERSION);
		}
	}

	public static void reset() {
		PENDING.clear();
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

	private static void debug(String msg, Object... args) {
		if (FetchConfigs.DEBUG_LOG.getBooleanValue()) {
			LOGGER.info("[shulkerfetch] " + msg, args);
		}
	}
}
