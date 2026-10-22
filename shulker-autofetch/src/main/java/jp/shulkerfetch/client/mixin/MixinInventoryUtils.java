package jp.shulkerfetch.client.mixin;

import fi.dy.masa.litematica.util.InventoryUtils;
import jp.shulkerfetch.client.FetchController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Both Litematica entry points (wheel click / pick block hotkeys and Easy Place) select their item through
 * this one method. The original routine always runs; we only put the item into the held slot first when
 * it is missing from the inventory but present in a shulker box.
 */
@Mixin(value = InventoryUtils.class, remap = false)
public abstract class MixinInventoryUtils {
	@Inject(method = "schematicWorldPickBlock", at = @At("HEAD"), remap = false)
	private static void shulkerfetch$fetchFromShulker(ItemStack stack, BlockPos pos, World schematicWorld,
			MinecraftClient mc, CallbackInfo ci) {
		FetchController.beforePick(stack, mc);
	}
}
