package jp.shulkerfetch.selftest;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.mixin.IMixinHandledScreen;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.EasyPlaceProtocol;
import fi.dy.masa.litematica.util.WorldUtils;
import fi.dy.masa.malilib.gui.GuiBase;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import jp.shulkerfetch.client.config.FetchConfigGui;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Dev-only: scripted tour of the mod for the explanation video. Drives the real Litematica routines. */
public class DemoClient implements ClientModInitializer {
	private record Step(int delay, Runnable action, java.util.function.BooleanSupplier gate) {
	}

	private final List<Step> steps = new ArrayList<>();
	private int wait;
	private Step current;
	private int index;
	private boolean started;
	private int worldTicks;

	private String caption = "";
	private String[] card;
	private BlockPos origin = new BlockPos(-2, -60, 4);

	private void then(int delay, Runnable r) {
		steps.add(new Step(delay, r, null));
	}

	private final double[] narrSec = new double[32];
	private long narrFreeAt;

	/** Starts narration clip i as soon as the previous clip has finished; logs the wall-clock start for the audio mix. */
	private void narrate(int i) {
		steps.add(new Step(0, () -> {
			long now = System.currentTimeMillis();
			System.out.println("DEMOMARK say " + i + " " + now);
			narrFreeAt = now + (long) (narrSec[i] * 1000) + 600;
		}, () -> System.currentTimeMillis() >= narrFreeAt));
	}

	private void say(String text) {
		then(0, () -> caption = text);
	}

	private void card(String... lines) {
		then(0, () -> card = lines);
	}

	private void noCard() {
		then(0, () -> card = null);
	}

	private static void send(int step) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(step);
		ClientPlayNetworking.send(DemoServer.STEP, buf);
	}

	private void lookAt(MinecraftClient mc, int x, int y) {
		BlockPos p = origin.add(x, y, 0);
		Vec3d eye = mc.player.getEyePos();
		Vec3d t = Vec3d.ofCenter(p).subtract(eye);
		mc.player.setYaw((float) (MathHelper.atan2(t.z, t.x) * 57.29577951308232 - 90.0));
		mc.player.setPitch((float) -(MathHelper.atan2(t.y, Math.sqrt(t.x * t.x + t.z * t.z)) * 57.29577951308232));
	}

	/** Moves the OS cursor over an inventory slot so the vanilla tooltip (shulker contents) shows up. */
	private static void hoverSlot(MinecraftClient mc, int invIndex) {
		if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
			return;
		}
		int idx = invIndex < 9 ? 36 + invIndex : invIndex;
		Slot slot = screen.getScreenHandler().slots.get(idx);
		IMixinHandledScreen acc = (IMixinHandledScreen) screen;
		double scale = mc.getWindow().getScaleFactor();
		double mx = (acc.litematica_getX() + slot.x + 8) * scale;
		double my = (acc.litematica_getY() + slot.y + 8) * scale;
		try {
			Field fx = net.minecraft.client.Mouse.class.getDeclaredField("x");
			Field fy = net.minecraft.client.Mouse.class.getDeclaredField("y");
			fx.setAccessible(true);
			fy.setAccessible(true);
			fx.setDouble(mc.mouse, mx);
			fy.setDouble(mc.mouse, my);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private void buildSchematic(MinecraftClient mc) {
		AreaSelection area = new AreaSelection();
		area.setName("demo wall");
		area.addSubRegionBox(new Box(BlockPos.ORIGIN, new BlockPos(4, 3, 0), "wall"), true);
		area.setExplicitOrigin(BlockPos.ORIGIN);
		LitematicaSchematic schematic = LitematicaSchematic.createEmptySchematic(area, "demo");
		var c = schematic.getSubRegionContainer("wall");
		for (int x = 0; x < 5; x++) {
			c.set(x, 0, 0, Blocks.STONE.getDefaultState());
			c.set(x, 1, 0, (x == 0 || x == 4) ? Blocks.STONE.getDefaultState() : Blocks.GLASS.getDefaultState());
			c.set(x, 2, 0, Blocks.OAK_PLANKS.getDefaultState());
		}
		var slabTop = Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
		c.set(0, 3, 0, Blocks.BRICKS.getDefaultState());
		c.set(1, 3, 0, slabTop);
		c.set(2, 3, 0, Blocks.DIAMOND_BLOCK.getDefaultState());
		c.set(3, 3, 0, slabTop);
		c.set(4, 3, 0, Blocks.BRICKS.getDefaultState());
		SchematicPlacement placement = SchematicPlacement.createFor(schematic, origin, "demo wall", true, true);
		DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, false);
		DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(placement);
	}

	private static void wheel(MinecraftClient mc) {
		boolean r = WorldUtils.doSchematicWorldPickBlock(true, mc);
		System.out.println("DEMOLOG wheel result=" + r + " creative=" + mc.player.isCreative() + " selected=" + mc.player.getInventory().selectedSlot
				+ " hand=" + mc.player.getMainHandStack() + " pos=" + mc.player.getPos() + " yawpitch=" + mc.player.getYaw() + "/" + mc.player.getPitch());
	}

	/** Holds the use key on the given ghost block for a number of ticks (calls Litematica's Easy Place handler). */
	private void easyPlace(MinecraftClient mc, int x, int y, int ticks) {
		then(0, () -> lookAt(mc, x, y));
		for (int i = 0; i < ticks / 4; i++) {
			then(4, () -> {
				lookAt(mc, x, y);
				boolean r = WorldUtils.handleEasyPlace(mc);
				System.out.println("DEMOLOG easyplace(" + x + "," + y + ") result=" + r + " hand=" + mc.player.getMainHandStack()
						+ " target=" + mc.world.getBlockState(origin.add(x, y, 0)).getBlock());
			});
		}
	}

	private void script(MinecraftClient mc) {
		try {
			for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(System.getProperty("shulkerfetch.narr", "/tmp/demo/narr/durations.txt")))) {
				String[] kv = line.trim().split(" ");
				if (kv.length == 2) {
					narrSec[Integer.parseInt(kv[0])] = Double.parseDouble(kv[1]);
				}
			}
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		card("Shulker AutoFetch", "Litematica 用:シュルカーボックスから自動でアイテム取得", "Minecraft 1.19.4 / Fabric");
		narrate(0);
		steps.add(new Step(0, () -> { }, () -> System.currentTimeMillis() >= narrFreeAt));
		card("対応する操作", "・設計図ブロックへのホイールクリック (Pick Block)", "・Easy Place", "・Easy Place の精密設置プロトコル (ProPlace)");
		narrate(1);
		steps.add(new Step(0, () -> { }, () -> System.currentTimeMillis() >= narrFreeAt));
		noCard();
		then(0, () -> send(0));
		then(60, () -> buildSchematic(mc));
		then(100, () -> { });
		narrate(2);
		narrate(3);
		then(30, () -> mc.setScreen(new InventoryScreen(mc.player)));
		then(40, () -> hoverSlot(mc, 9));
		narrate(4);
		then(5, () -> hoverSlot(mc, 10));
		narrate(5);
		then(0, () -> mc.setScreen(null));
		then(40, () -> lookAt(mc, 0, 0));
		then(60, () -> { });
		then(0, () -> wheel(mc));
		narrate(6);
		then(40, () -> mc.setScreen(new InventoryScreen(mc.player)));
		then(20, () -> hoverSlot(mc, 4));
		narrate(7);
		then(0, () -> mc.setScreen(null));
		then(0, () -> {
			Configs.Generic.EASY_PLACE_MODE.setBooleanValue(true);
			Configs.Generic.PLACEMENT_RESTRICTION_WARN.setOptionListValue(fi.dy.masa.malilib.util.MessageOutputType.NONE);
		});
		then(20, () -> { });
		easyPlace(mc, 0, 0, 30);
		easyPlace(mc, 1, 0, 30);
		narrate(8);
		easyPlace(mc, 1, 1, 50);
		easyPlace(mc, 2, 1, 40);
		narrate(9);
		easyPlace(mc, 0, 2, 50);
		easyPlace(mc, 1, 2, 40);
		narrate(10);
		then(0, () -> Configs.Generic.EASY_PLACE_PROTOCOL.setOptionListValue(EasyPlaceProtocol.SLAB_ONLY));
		easyPlace(mc, 1, 3, 70);
		narrate(11);
		then(40, () -> lookAt(mc, 2, 3));
		then(40, () -> wheel(mc));
		then(60, () -> { });
		narrate(12);
		then(0, () -> send(1));
		then(40, () -> lookAt(mc, 0, 3));
		then(20, () -> wheel(mc));
		then(120, () -> send(2));
		then(60, () -> wheel(mc));
		narrate(13);
		then(20, () -> {
			jp.shulkerfetch.client.config.FetchConfigs.DEBUG_LOG.setBooleanValue(false);
			GuiBase.openGui(new FetchConfigGui());
		});
		narrate(14);
		then(0, () -> mc.setScreen(null));
		card("導入", "クライアント: Litematica 0.14.x + MaLiLib 0.15.x + Fabric API + 本 Mod", "サーバー: Fabric API + 本 Mod(シングルプレイはクライアントのみ)", "サーバーに未導入だと取得は動きません");
		narrate(15);
		card("仕様", "取り出しはサーバーが検証して実行(複製・消失なし)", "探索対象: インベントリ36枠内のシュルカーボックス", "手持ちの枠に必ず入る / 元のアイテムは空きスロットへ退避", "空きスロットが無いときは何もせず終了");
		steps.add(new Step(0, () -> { }, () -> System.currentTimeMillis() >= narrFreeAt));
		then(40, () -> {
			System.out.println("DEMOMARK end " + System.currentTimeMillis());
			mc.scheduleStop();
		});
	}

	private void drawCaption(MatrixStack m) {
		MinecraftClient mc = MinecraftClient.getInstance();
		int w = mc.getWindow().getScaledWidth();
		int h = mc.getWindow().getScaledHeight();
		if (card != null) {
			DrawableHelper.fill(m, 0, 0, w, h, 0xFF101018);
			int y = h / 2 - card.length * 8;
			for (int i = 0; i < card.length; i++) {
				float scale = i == 0 ? 2.0f : 1.0f;
				m.push();
				m.scale(scale, scale, 1f);
				int tw = mc.textRenderer.getWidth(card[i]);
				mc.textRenderer.drawWithShadow(m, card[i], (w / scale - tw) / 2f, y / scale, i == 0 ? 0xFFD060 : 0xFFFFFF);
				m.pop();
				y += i == 0 ? 24 : 14;
			}
			return;
		}
		if (caption.isEmpty() || Boolean.getBoolean("shulkerfetch.nocaption")) {
			return;
		}
		float k = 1.5f;
		int cw = (int) (w / k);
		List<OrderedText> lines = mc.textRenderer.wrapLines(Text.literal(caption), cw - 24);
		int boxH = lines.size() * 11 + 10;
		m.push();
		m.scale(k, k, 1f);
		DrawableHelper.fill(m, 4, 4, cw - 4, 4 + boxH, 0xC8000000);
		int y = 9;
		for (OrderedText line : lines) {
			mc.textRenderer.drawWithShadow(m, line, 12, y, 0xFFFFFF);
			y += 11;
		}
		m.pop();
	}

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("shulkerfetch.demo")) {
			return;
		}
		HudRenderCallback.EVENT.register((m, delta) -> drawCaption(m));
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
				ScreenEvents.afterRender(screen).register((s, m, mx, my, d) -> drawCaption(m)));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null || mc.world == null) {
				return;
			}
			mc.getToastManager().clear();
			if (!started) {
				if (++worldTicks < 100) {
					return;
				}
				started = true;
				mc.options.getGuiScale().setValue(2);
				mc.options.getViewDistance().setValue(6);
				mc.options.hudHidden = false;
				jp.shulkerfetch.client.config.FetchConfigs.DEBUG_LOG.setBooleanValue(true);
				System.out.println("DEMOMARK start " + System.currentTimeMillis());
				script(mc);
			}
			// A step's delay is the time to wait BEFORE its action runs; gated steps wait for their gate.
			while (true) {
				if (current != null) {
					if (current.gate() != null) {
						if (!current.gate().getAsBoolean()) {
							return;
						}
					} else if (--wait > 0) {
						return;
					}
					current.action().run();
					current = null;
				}
				if (index >= steps.size()) {
					return;
				}
				Step st = steps.get(index++);
				if (st.gate() == null && st.delay() == 0) {
					st.action().run();
					continue;
				}
				current = st;
				wait = st.delay();
				if (st.gate() == null) {
					return;
				}
			}
		});
	}
}
