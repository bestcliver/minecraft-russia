package com.bestcliver.russia.mixin;

import com.bestcliver.russia.Game;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Roskomnadzor: a locked item cannot be used. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void russiamc$use(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		ItemStack self = (ItemStack) (Object) this;
		if (Game.isLocked(self)) {
			Game.notifyLocked(user);
			cir.setReturnValue(TypedActionResult.fail(self));
		}
	}

	@Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
	private void russiamc$useOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
		if (Game.isLocked((ItemStack) (Object) this)) {
			if (context.getPlayer() != null) {
				Game.notifyLocked(context.getPlayer());
			}
			cir.setReturnValue(ActionResult.FAIL);
		}
	}

	@Inject(method = "useOnEntity", at = @At("HEAD"), cancellable = true)
	private void russiamc$useOnEntity(PlayerEntity user, LivingEntity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (Game.isLocked((ItemStack) (Object) this)) {
			Game.notifyLocked(user);
			cir.setReturnValue(ActionResult.FAIL);
		}
	}
}
