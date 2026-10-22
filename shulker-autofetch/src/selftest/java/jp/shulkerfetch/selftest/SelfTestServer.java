package jp.shulkerfetch.selftest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;

/** Dev-only: gives the joining player test shulker boxes and dumps the authoritative inventory. */
public class SelfTestServer implements ModInitializer {
	private int joinTick = -1;
	private ServerPlayerEntity player;

	static ItemStack box(Item boxItem, Object... slotAndStack) {
		ItemStack box = new ItemStack(boxItem);
		NbtList list = new NbtList();
		for (int i = 0; i < slotAndStack.length; i += 2) {
			NbtCompound e = new NbtCompound();
			((ItemStack) slotAndStack[i + 1]).writeNbt(e);
			e.putByte("Slot", ((Integer) slotAndStack[i]).byteValue());
			list.add(e);
		}
		box.getOrCreateSubNbt("BlockEntityTag").put("Items", list);
		return box;
	}

	@Override
	public void onInitialize() {
		if (Boolean.getBoolean("shulkerfetch.demo")) {
			return;
		}
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity p = handler.player;
			p.getInventory().clear();
			p.getInventory().main.set(0, box(Items.BLUE_SHULKER_BOX, 0, new ItemStack(Items.STONE, 64), 1, new ItemStack(Items.STONE, 17), 2, new ItemStack(Items.GLASS, 10)));
			p.getInventory().main.set(1, box(Items.RED_SHULKER_BOX, 0, new ItemStack(Items.OAK_PLANKS, 5)));
			p.getInventory().main.set(2, box(Items.LIME_SHULKER_BOX, 0, new ItemStack(Items.SAND, 20), 1, new ItemStack(Items.BRICKS, 30)));
			p.playerScreenHandler.sendContentUpdates();
			player = p;
			joinTick = server.getTicks();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (player != null && server.getTicks() - joinTick == 420) {
				System.out.println("SELFTEST-SERVER: " + SelfTestClient.dump(player.getInventory()));
			}
		});
	}
}
