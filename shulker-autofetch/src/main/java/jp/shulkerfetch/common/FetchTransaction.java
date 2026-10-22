package jp.shulkerfetch.common;

import jp.shulkerfetch.common.Protocol.Status;
import jp.shulkerfetch.common.ShulkerInventory.Match;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/** The inventory change itself: take one stack out of a shulker box into an empty slot, or change nothing. */
public final class FetchTransaction {
	/** Outcome of one request; amount and slot are only meaningful for {@link Status#OK}. */
	public record Result(Status status, int amount, int slot) {
		public static Result of(Status status) {
			return new Result(status, 0, -1);
		}
	}

	public static Result run(PlayerInventory inv, ItemStack requested) {
		ItemStack target = requested.copy();
		target.setCount(1);
		if (ShulkerInventory.isAvailableInInventory(inv, target)) {
			return Result.of(Status.ALREADY_AVAILABLE);
		}
		Match match = ShulkerInventory.find(inv, target);
		if (match == null) {
			return Result.of(Status.NOT_FOUND);
		}
		int dest = ShulkerInventory.findEmptySlot(inv);
		if (dest < 0) {
			return Result.of(Status.INVENTORY_FULL);
		}
		// Edit a copy of the box and commit both stacks together, so no failure can lose or duplicate items.
		ItemStack boxCopy = inv.main.get(match.invSlot()).copy();
		ItemStack taken = ShulkerInventory.extract(boxCopy, match, target);
		if (taken.isEmpty()) {
			return Result.of(Status.NOT_FOUND);
		}
		inv.main.set(match.invSlot(), boxCopy);
		inv.main.set(dest, taken);
		return new Result(Status.OK, taken.getCount(), dest);
	}

	private FetchTransaction() {
	}
}
