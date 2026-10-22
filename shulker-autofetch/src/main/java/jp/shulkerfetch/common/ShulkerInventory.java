package jp.shulkerfetch.common;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/**
 * Reads and edits the contents of shulker box item stacks.
 * <p>
 * The server uses this as the authority; the client uses the very same code only as a pre-check
 * so that it does not send requests that cannot succeed. Entries are edited in place in the raw
 * NBT list, so entries this code does not understand (e.g. items of a removed mod) are preserved.
 */
public final class ShulkerInventory {
	private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";
	private static final String ITEMS = "Items";

	/** Location of one matching entry. {@code count} is the size of that entry (not the amount to take). */
	public record Match(int invSlot, int entryIndex, int count) {
	}

	public static boolean isShulkerBox(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock;
	}

	/** True if the main inventory (hotbar + 27 slots) already holds a stack that Litematica could use as is. */
	public static boolean isAvailableInInventory(PlayerInventory inv, ItemStack target) {
		for (ItemStack s : inv.main) {
			if (!s.isEmpty() && ItemStack.canCombine(target, s)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the raw item list of a shulker box, or null if the box has no readable contents
	 * (no data, loot table that has not been rolled yet, or a malformed tag). Never guesses.
	 */
	private static NbtList items(ItemStack box) {
		NbtCompound tag = box.getSubNbt(BLOCK_ENTITY_TAG);
		if (tag == null || tag.contains("LootTable") || !tag.contains(ITEMS, NbtElement.LIST_TYPE)) {
			return null;
		}
		NbtList list = tag.getList(ITEMS, NbtElement.COMPOUND_TYPE);
		return list;
	}

	/**
	 * Finds the entry to take from. Among all shulker boxes in the main inventory the smallest
	 * matching entry wins (it empties partial stacks first); ties go to the lowest inventory slot.
	 */
	public static Match find(PlayerInventory inv, ItemStack target) {
		if (target.isEmpty()) {
			return null;
		}
		Match best = null;
		for (int slot = 0; slot < inv.main.size(); slot++) {
			ItemStack box = inv.main.get(slot);
			if (!isShulkerBox(box)) {
				continue;
			}
			NbtList list = items(box);
			if (list == null) {
				continue;
			}
			for (int i = 0; i < list.size(); i++) {
				NbtCompound entry = list.getCompound(i);
				ItemStack content = ItemStack.fromNbt(entry);
				if (content.isEmpty() || !ItemStack.canCombine(target, content)) {
					continue;
				}
				if (best == null || content.getCount() < best.count()) {
					best = new Match(slot, i, content.getCount());
				}
			}
		}
		return best;
	}

	/**
	 * Removes up to one full stack of the matched entry from {@code box} and returns it.
	 * Returns {@link ItemStack#EMPTY} (and leaves the box untouched) if the entry no longer matches
	 * {@code target}. Callers must pass a copy of the real box stack and only commit it on success.
	 */
	public static ItemStack extract(ItemStack box, Match match, ItemStack target) {
		if (!isShulkerBox(box)) {
			return ItemStack.EMPTY;
		}
		NbtList list = items(box);
		if (list == null || match.entryIndex() < 0 || match.entryIndex() >= list.size()) {
			return ItemStack.EMPTY;
		}
		NbtCompound entry = list.getCompound(match.entryIndex());
		ItemStack content = ItemStack.fromNbt(entry);
		if (content.isEmpty() || !ItemStack.canCombine(target, content)) {
			return ItemStack.EMPTY;
		}
		int amount = Math.min(content.getCount(), content.getMaxCount());
		ItemStack taken = content.copy();
		taken.setCount(amount);

		int remaining = content.getCount() - amount;
		if (remaining > 0) {
			entry.putByte("Count", (byte) remaining);
		} else {
			list.remove(match.entryIndex());
			if (list.isEmpty()) {
				NbtCompound tag = box.getSubNbt(BLOCK_ENTITY_TAG);
				tag.remove(ITEMS);
				if (tag.isEmpty()) {
					box.removeSubNbt(BLOCK_ENTITY_TAG);
				}
			}
		}
		return taken;
	}

	/** Index of an empty slot to receive the item: the 27 storage slots first, then the hotbar; -1 if none. */
	public static int findEmptySlot(PlayerInventory inv) {
		for (int i = 9; i < inv.main.size(); i++) {
			if (inv.main.get(i).isEmpty()) {
				return i;
			}
		}
		for (int i = 0; i < 9; i++) {
			if (inv.main.get(i).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	private ShulkerInventory() {
	}
}
