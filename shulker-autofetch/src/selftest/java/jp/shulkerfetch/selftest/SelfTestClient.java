package jp.shulkerfetch.selftest;

import fi.dy.masa.litematica.util.InventoryUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import jp.shulkerfetch.client.FetchController;
import jp.shulkerfetch.client.FetchController.Source;
import jp.shulkerfetch.client.config.FetchConfigs;
import jp.shulkerfetch.common.ShulkerInventory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;

/** Dev-only scenario driver. It calls Litematica's real pick routine, so the real Mixin and network path run. */
public class SelfTestClient implements ClientModInitializer {
	private int ticksInWorld;

	static String dump(PlayerInventory inv) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < inv.main.size(); i++) {
			ItemStack s = inv.main.get(i);
			if (s.isEmpty()) {
				continue;
			}
			sb.append(i).append('=').append(Registries.ITEM.getId(s.getItem()).getPath()).append('x').append(s.getCount());
			if (ShulkerInventory.isShulkerBox(s) && s.getSubNbt("BlockEntityTag") != null) {
				NbtList l = s.getSubNbt("BlockEntityTag").getList("Items", 10);
				sb.append('[');
				for (int j = 0; j < l.size(); j++) {
					ItemStack c = ItemStack.fromNbt(l.getCompound(j));
					sb.append(Registries.ITEM.getId(c.getItem()).getPath()).append('x').append(c.getCount()).append(' ');
				}
				sb.append(']');
			}
			sb.append("; ");
		}
		return sb.toString();
	}

	private static void pick(MinecraftClient mc, Item item, Source source) {
		FetchController.source = source;
		try {
			InventoryUtils.schematicWorldPickBlock(new ItemStack(item), BlockPos.ORIGIN, SchematicWorldHandler.getSchematicWorld(), mc);
		} finally {
			FetchController.source = Source.NONE;
		}
	}

	private static void report(MinecraftClient mc, String label) {
		PlayerInventory inv = mc.player.getInventory();
		System.out.println("SELFTEST: " + label + " | selected=" + inv.selectedSlot + " hand=" + inv.getMainHandStack()
				+ " | " + dump(inv));
	}

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null || mc.world == null) {
				ticksInWorld = 0;
				return;
			}
			ticksInWorld++;
			FetchConfigs.DEBUG_LOG.setBooleanValue(true);
			switch (ticksInWorld) {
				case 100 -> {
					System.out.println("SELFTEST: schematicWorld=" + (SchematicWorldHandler.getSchematicWorld() != null)
							+ " survival=" + !mc.player.isCreative());
					report(mc, "initial");
				}
				case 110 -> pick(mc, Items.STONE, Source.WHEEL_CLICK);
				case 130 -> report(mc, "after A (stone via wheel click)");
				case 140 -> pick(mc, Items.STONE, Source.WHEEL_CLICK);
				case 150 -> report(mc, "after B (repeat, must not fetch)");
				case 160 -> pick(mc, Items.GLASS, Source.EASY_PLACE);
				case 180 -> report(mc, "after C (glass via easy place)");
				case 190 -> pick(mc, Items.DIAMOND, Source.WHEEL_CLICK);
				case 200 -> pick(mc, Items.DIAMOND, Source.WHEEL_CLICK);
				case 210 -> report(mc, "after D (diamond, nonexistent)");
				case 220 -> pick(mc, Items.OAK_PLANKS, Source.NONE);
				case 230 -> report(mc, "after E (source NONE, must not fetch)");
				case 240 -> pick(mc, Items.OAK_PLANKS, Source.WHEEL_CLICK);
				case 260 -> report(mc, "after F (planks)");
				case 320 -> {
					System.out.println("SELFTEST: done");
					mc.scheduleStop();
				}
				default -> { }
			}
		});
	}
}
