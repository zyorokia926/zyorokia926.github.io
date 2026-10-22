package jp.shulkerfetch.selftest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

/** Dev-only (demo video): prepares the world and inventory on request of the demo client. */
public class DemoServer implements ModInitializer {
	public static final Identifier STEP = new Identifier("shulkerfetch_demo", "step");

	private static ItemStack box(net.minecraft.item.Item boxItem, ItemStack... contents) {
		ItemStack box = new ItemStack(boxItem);
		NbtList list = new NbtList();
		for (int i = 0; i < contents.length; i++) {
			NbtCompound e = new NbtCompound();
			contents[i].writeNbt(e);
			e.putByte("Slot", (byte) i);
			list.add(e);
		}
		box.getOrCreateSubNbt("BlockEntityTag").put("Items", list);
		return box;
	}

	@Override
	public void onInitialize() {
		if (!Boolean.getBoolean("shulkerfetch.demo")) {
			return;
		}
		ServerPlayNetworking.registerGlobalReceiver(STEP, (server, player, handler, buf, sender) -> {
			int step = buf.readVarInt();
			server.execute(() -> run(step, player));
		});
	}

	private static void run(int step, ServerPlayerEntity p) {
		var inv = p.getInventory();
		switch (step) {
			case 0 -> {
				var server = p.server;
				server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
				server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
				server.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, server);
				server.getOverworld().setTimeOfDay(1000);
				var w = server.getOverworld();
				for (int x = -4; x <= 4; x++) {
					for (int y = -60; y <= -54; y++) {
						for (int z = 2; z <= 6; z++) {
							w.setBlockState(new net.minecraft.util.math.BlockPos(x, y, z), net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
						}
					}
				}
				inv.clear();
				inv.main.set(9, box(Items.BLUE_SHULKER_BOX, new ItemStack(Items.STONE, 64), new ItemStack(Items.STONE, 30), new ItemStack(Items.GLASS, 16)));
				inv.main.set(10, box(Items.RED_SHULKER_BOX, new ItemStack(Items.OAK_PLANKS, 32), new ItemStack(Items.OAK_SLAB, 16), new ItemStack(Items.BRICKS, 20)));
				inv.main.set(4, new ItemStack(Items.STICK, 5));
				inv.selectedSlot = 4;
				p.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(4));
				p.networkHandler.requestTeleport(0.5, -60.0, -0.5, 0f, 0f);
				p.playerScreenHandler.sendContentUpdates();
			}
			case 1 -> {
				for (int i = 0; i < 36; i++) {
					if (inv.main.get(i).isEmpty()) {
						inv.main.set(i, new ItemStack(Items.COBBLESTONE, 64));
					}
				}
				p.playerScreenHandler.sendContentUpdates();
			}
			case 2 -> {
				for (int i = 0; i < 36; i++) {
					if (inv.main.get(i).isOf(Items.COBBLESTONE)) {
						inv.main.set(i, ItemStack.EMPTY);
					}
				}
				p.playerScreenHandler.sendContentUpdates();
			}
			default -> { }
		}
	}
}
