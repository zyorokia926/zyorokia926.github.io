package jp.shulkerfetch;

import static org.junit.jupiter.api.Assertions.*;

import jp.shulkerfetch.common.FetchTransaction;
import jp.shulkerfetch.common.FetchTransaction.Result;
import jp.shulkerfetch.common.Protocol.Status;
import jp.shulkerfetch.common.ShulkerInventory;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FetchTransactionTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
	}

	private static PlayerInventory inv() {
		return new PlayerInventory(null);
	}

	/** Builds a shulker box item holding the given (boxSlot, stack) pairs. */
	private static ItemStack box(ItemStack boxItem, Object... slotAndStack) {
		NbtList list = new NbtList();
		for (int i = 0; i < slotAndStack.length; i += 2) {
			NbtCompound e = new NbtCompound();
			((ItemStack) slotAndStack[i + 1]).writeNbt(e);
			e.putByte("Slot", ((Integer) slotAndStack[i]).byteValue());
			list.add(e);
		}
		boxItem.getOrCreateSubNbt("BlockEntityTag").put("Items", list);
		return boxItem;
	}

	private static ItemStack stack(net.minecraft.item.Item item, int n) {
		return new ItemStack(item, n);
	}

	private static int total(PlayerInventory inv, net.minecraft.item.Item item) {
		int n = 0;
		for (ItemStack s : inv.main) {
			if (s.isOf(item)) {
				n += s.getCount();
			}
			if (ShulkerInventory.isShulkerBox(s) && s.getSubNbt("BlockEntityTag") != null) {
				NbtList l = s.getSubNbt("BlockEntityTag").getList("Items", 10);
				for (int i = 0; i < l.size(); i++) {
					ItemStack c = ItemStack.fromNbt(l.getCompound(i));
					if (c.isOf(item)) {
						n += c.getCount();
					}
				}
			}
		}
		return n;
	}

	@Test
	void takesFullStackAndLeavesRemainder() {
		PlayerInventory inv = inv();
		inv.main.set(0, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.STONE, 64), 1, stack(Items.STONE, 64), 2, stack(Items.DIRT, 5)));
		int before = total(inv, Items.STONE);
		Result r = FetchTransaction.run(inv, stack(Items.STONE, 1), 0);
		assertEquals(Status.OK, r.status());
		assertEquals(64, r.amount());
		assertTrue(inv.main.get(r.slot()).isOf(Items.STONE));
		assertEquals(64, inv.main.get(r.slot()).getCount());
		assertEquals(before, total(inv, Items.STONE), "no item may be created or lost");
		assertEquals(5, total(inv, Items.DIRT), "other contents untouched");
	}

	@Test
	void partialStackIsTakenAndEmptyBoxLosesItsTag() {
		PlayerInventory inv = inv();
		inv.main.set(3, box(stack(Items.RED_SHULKER_BOX, 1), 4, stack(Items.OAK_LOG, 7)));
		Result r = FetchTransaction.run(inv, stack(Items.OAK_LOG, 1), 0);
		assertEquals(Status.OK, r.status());
		assertEquals(7, r.amount());
		assertNull(inv.main.get(3).getNbt(), "an emptied box must be a plain stackable-looking box again");
		assertEquals(7, total(inv, Items.OAK_LOG));
	}

	@Test
	void picksTheRightBoxAmongSeveralColors() {
		PlayerInventory inv = inv();
		inv.main.set(0, box(stack(Items.BLUE_SHULKER_BOX, 1), 0, stack(Items.GLASS, 10)));
		inv.main.set(1, box(stack(Items.LIME_SHULKER_BOX, 1), 0, stack(Items.SAND, 20)));
		inv.main.set(2, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.SAND, 3)));
		Result r = FetchTransaction.run(inv, stack(Items.SAND, 1), 0);
		assertEquals(Status.OK, r.status());
		assertEquals(20, r.amount(), "the largest matching entry is used");
		assertEquals(23, total(inv, Items.SAND));
		assertEquals(10, total(inv, Items.GLASS));
	}

	@Test
	void takesFromTheLargestEntryEvenWithinOneBox() {
		PlayerInventory inv = inv();
		inv.main.set(9, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.STONE, 10), 1, stack(Items.STONE, 40), 2, stack(Items.STONE, 25)));
		Result r = FetchTransaction.run(inv, stack(Items.STONE, 1), 0);
		assertEquals(40, r.amount());
		assertEquals(35, total(inv, Items.STONE) - 40);
	}

	@Test
	void largestEntryAcrossBoxesTiesGoToTheLowerSlot() {
		PlayerInventory inv = inv();
		inv.main.set(12, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.STONE, 64)));
		inv.main.set(11, box(stack(Items.RED_SHULKER_BOX, 1), 0, stack(Items.STONE, 64), 1, stack(Items.DIRT, 1)));
		inv.main.set(30, box(stack(Items.BLUE_SHULKER_BOX, 1), 0, stack(Items.STONE, 5)));
		Result r = FetchTransaction.run(inv, stack(Items.STONE, 1), 0);
		assertEquals(64, r.amount());
		assertEquals(1, total(inv, Items.DIRT), "the lower-slot box (11) was used and keeps its dirt");
		assertEquals(0, inv.main.get(12).getSubNbt("BlockEntityTag") == null ? 1 : 0, "the higher-slot box was untouched");
	}

	@Test
	void doesNothingWhenItemAlreadyInInventory() {
		PlayerInventory inv = inv();
		inv.main.set(5, stack(Items.STONE, 1));
		inv.main.set(0, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.STONE, 64)));
		ItemStack boxBefore = inv.main.get(0).copy();
		assertEquals(Status.ALREADY_AVAILABLE, FetchTransaction.run(inv, stack(Items.STONE, 1), 0).status());
		assertTrue(ItemStack.areEqual(boxBefore, inv.main.get(0)));
	}

	@Test
	void notFoundChangesNothing() {
		PlayerInventory inv = inv();
		inv.main.set(0, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.DIRT, 64)));
		ItemStack boxBefore = inv.main.get(0).copy();
		assertEquals(Status.NOT_FOUND, FetchTransaction.run(inv, stack(Items.STONE, 1), 0).status());
		assertTrue(ItemStack.areEqual(boxBefore, inv.main.get(0)));
	}

	@Test
	void fullInventoryLosesNothing() {
		PlayerInventory inv = inv();
		for (int i = 1; i < 36; i++) {
			inv.main.set(i, stack(Items.COBBLESTONE, 64));
		}
		inv.main.set(0, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.STONE, 64)));
		ItemStack boxBefore = inv.main.get(0).copy();
		assertEquals(Status.INVENTORY_FULL, FetchTransaction.run(inv, stack(Items.STONE, 1), 0).status());
		assertTrue(ItemStack.areEqual(boxBefore, inv.main.get(0)));
		assertEquals(64, total(inv, Items.STONE));
		assertEquals(35 * 64, total(inv, Items.COBBLESTONE));
	}

	@Test
	void itemAlwaysLandsInTheHeldSlotAndDisplacedStackMovesToAFreeSlot() {
		PlayerInventory inv = inv();
		for (int i = 0; i < 36; i++) {
			inv.main.set(i, stack(Items.COBBLESTONE, 1));
		}
		inv.main.set(4, ItemStack.EMPTY);
		inv.main.set(9, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.STONE, 2)));
		inv.main.get(2).setCount(7); // distinguishable stack in the held slot
		Result r = FetchTransaction.run(inv, stack(Items.STONE, 1), 2);
		assertEquals(Status.OK, r.status());
		assertEquals(2, r.slot());
		assertTrue(inv.main.get(2).isOf(Items.STONE));
		assertEquals(2, inv.main.get(2).getCount());
		assertEquals(7, inv.main.get(4).getCount(), "the displaced stack is kept in the only free slot");
		assertEquals(40, total(inv, Items.COBBLESTONE), "nothing lost: 33 single stacks + the displaced 7");
	}

	@Test
	void emptyHeldSlotNeedsNoDisplacement() {
		PlayerInventory inv = inv();
		inv.main.set(9, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.STONE, 2)));
		inv.main.set(5, stack(Items.DIRT, 3));
		Result r = FetchTransaction.run(inv, stack(Items.STONE, 1), 3);
		assertEquals(Status.OK, r.status());
		assertEquals(3, r.slot());
		assertEquals(3, inv.main.get(5).getCount());
		assertTrue(inv.main.get(5).isOf(Items.DIRT));
	}

	@Test
	void heldSlotHoldingTheSourceBoxMovesTheUpdatedBoxAside() {
		PlayerInventory inv = inv();
		inv.main.set(0, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.STONE, 64), 1, stack(Items.DIRT, 9)));
		Result r = FetchTransaction.run(inv, stack(Items.STONE, 1), 0);
		assertEquals(Status.OK, r.status());
		assertTrue(inv.main.get(0).isOf(Items.STONE));
		assertEquals(64, inv.main.get(0).getCount());
		assertEquals(9, total(inv, Items.DIRT), "the box with its remaining contents survives");
		assertEquals(0, total(inv, Items.STONE) - 64);
	}

	@Test
	void fullInventoryWithOccupiedHeldSlotChangesNothing() {
		PlayerInventory inv = inv();
		for (int i = 0; i < 36; i++) {
			inv.main.set(i, stack(Items.COBBLESTONE, 1));
		}
		inv.main.set(20, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.STONE, 2)));
		String before = inv.main.toString();
		assertEquals(Status.INVENTORY_FULL, FetchTransaction.run(inv, stack(Items.STONE, 1), 5).status());
		assertEquals(before, inv.main.toString());
		assertEquals(2, total(inv, Items.STONE));
	}

	@Test
	void invalidHeldSlotIsRejected() {
		PlayerInventory inv = inv();
		inv.main.set(9, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.STONE, 2)));
		assertEquals(Status.BAD_REQUEST, FetchTransaction.run(inv, stack(Items.STONE, 1), 9).status());
		assertEquals(Status.BAD_REQUEST, FetchTransaction.run(inv, stack(Items.STONE, 1), -1).status());
	}

	@Test
	void boxWithoutDataOrWithLootTableIsNeverGuessed() {
		PlayerInventory inv = inv();
		inv.main.set(0, stack(Items.SHULKER_BOX, 1));
		ItemStack loot = stack(Items.SHULKER_BOX, 1);
		loot.getOrCreateSubNbt("BlockEntityTag").putString("LootTable", "minecraft:chests/simple_dungeon");
		inv.main.set(1, loot);
		assertEquals(Status.NOT_FOUND, FetchTransaction.run(inv, stack(Items.STONE, 1), 0).status());
	}

	@Test
	void malformedItemsTagIsIgnoredSafely() {
		PlayerInventory inv = inv();
		ItemStack b = stack(Items.SHULKER_BOX, 1);
		NbtList bad = new NbtList();
		bad.add(NbtString.of("garbage"));
		b.getOrCreateSubNbt("BlockEntityTag").put("Items", bad);
		inv.main.set(0, b);
		assertEquals(Status.NOT_FOUND, FetchTransaction.run(inv, stack(Items.STONE, 1), 0).status());
		inv.main.set(1, stack(Items.SHULKER_BOX, 1));
		inv.main.get(1).getOrCreateSubNbt("BlockEntityTag").putString("Items", "not a list");
		assertEquals(Status.NOT_FOUND, FetchTransaction.run(inv, stack(Items.STONE, 1), 0).status());
	}

	@Test
	void respectsItemNbtLikeNamedItems() {
		PlayerInventory inv = inv();
		ItemStack named = stack(Items.STONE, 5);
		named.setCustomName(net.minecraft.text.Text.literal("special"));
		inv.main.set(0, box(stack(Items.SHULKER_BOX, 1), 0, named));
		assertEquals(Status.NOT_FOUND, FetchTransaction.run(inv, stack(Items.STONE, 1), 0).status(), "plain stone must not match a renamed stone");
	}

	@Test
	void repeatedRequestsNeverDuplicate() {
		PlayerInventory inv = inv();
		inv.main.set(0, box(stack(Items.SHULKER_BOX, 1), 0, stack(Items.STONE, 64), 1, stack(Items.STONE, 64)));
		assertEquals(Status.OK, FetchTransaction.run(inv, stack(Items.STONE, 1), 0).status());
		assertEquals(Status.ALREADY_AVAILABLE, FetchTransaction.run(inv, stack(Items.STONE, 1), 0).status());
		assertEquals(Status.ALREADY_AVAILABLE, FetchTransaction.run(inv, stack(Items.STONE, 1), 0).status());
		assertEquals(128, total(inv, Items.STONE));
	}
}
