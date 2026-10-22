package jp.shulkerfetch.client.mixin;

import fi.dy.masa.litematica.util.WorldUtils;
import jp.shulkerfetch.client.FetchController;
import jp.shulkerfetch.client.FetchController.Source;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only tags which Litematica feature is calling the pick routine; behaviour is not changed here. */
@Mixin(value = WorldUtils.class, remap = false)
public abstract class MixinWorldUtils {
	@Inject(method = "doEasyPlaceAction", at = @At("HEAD"), remap = false)
	private static void shulkerfetch$easyPlaceStart(MinecraftClient mc, CallbackInfoReturnable<ActionResult> cir) {
		FetchController.source = Source.EASY_PLACE;
	}

	@Inject(method = "doEasyPlaceAction", at = @At("RETURN"), remap = false)
	private static void shulkerfetch$easyPlaceEnd(MinecraftClient mc, CallbackInfoReturnable<ActionResult> cir) {
		FetchController.source = Source.NONE;
	}

	@Inject(method = "doSchematicWorldPickBlock", at = @At("HEAD"), remap = false)
	private static void shulkerfetch$pickStart(boolean closest, MinecraftClient mc, CallbackInfoReturnable<Boolean> cir) {
		FetchController.source = Source.WHEEL_CLICK;
	}

	@Inject(method = "doSchematicWorldPickBlock", at = @At("RETURN"), remap = false)
	private static void shulkerfetch$pickEnd(boolean closest, MinecraftClient mc, CallbackInfoReturnable<Boolean> cir) {
		FetchController.source = Source.NONE;
	}
}
