package jp.shulkerfetch.common;

import jp.shulkerfetch.common.Protocol.Status;
import jp.shulkerfetch.common.ShulkerInventory.Match;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/** The inventory change itself: take one stack out of a shulker box into the held slot, or change nothing. */
public final class FetchTransaction {
	/** Outcome of one request; amount and slot are only meaningful for {@link Status#OK}. */
	public record Result(Status status, int amount, int slot) {
		public static Result of(Status status) {
			return new Result(status, 0, -1);
		}
	}

	/**
	 * Puts the item into the hotbar slot the player is holding. A stack already in that slot is moved to an
	 * empty slot (never discarded); if there is none, nothing is changed.
	 */
	public static Result run(PlayerInventory inv, ItemStack requested, int selectedSlot) {
		if (selectedSlot < 0 || selectedSlot > 8) {
			return Result.of(Status.BAD_REQUEST);
		}
		ItemStack target = requested.copy();
		target.setCount(1);
		if (ShulkerInventory.isAvailableInInventory(inv, target)) {
			return Result.of(Status.ALREADY_AVAILABLE);
		}
		Match match = ShulkerInventory.find(inv, target);
		if (match == null) {
			return Result.of(Status.NOT_FOUND);
		}
		// Edit a copy of the box and commit everything together, so no failure can lose or duplicate items.
		ItemStack boxCopy = inv.main.get(match.invSlot()).copy();
		ItemStack taken = ShulkerInventory.extract(boxCopy, match, target);
		if (taken.isEmpty()) {
			return Result.of(Status.NOT_FOUND);
		}
		// The box we took from may itself be the held stack; then the (updated) box is what gets displaced.
		ItemStack displaced = match.invSlot() == selectedSlot ? boxCopy : inv.main.get(selectedSlot).copy();
		int spare = -1;
		if (!displaced.isEmpty()) {
			spare = ShulkerInventory.findEmptySlot(inv);
			if (spare < 0) {
				return Result.of(Status.INVENTORY_FULL);
			}
		}
		if (match.invSlot() != selectedSlot) {
			inv.main.set(match.invSlot(), boxCopy);
		}
		if (spare >= 0) {
			inv.main.set(spare, displaced);
		}
		inv.main.set(selectedSlot, taken);
		return new Result(Status.OK, taken.getCount(), selectedSlot);
	}

	private FetchTransaction() {
	}
}
