package com.bestcliver.russia.mixin;

import com.bestcliver.russia.Game;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Littering fine: tracks items a player throws out. */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
	@Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("RETURN"))
	private void russiamc$onDropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
		ItemEntity item = cir.getReturnValue();
		// retainOwnership is only set for items the player throws out, not for death drops and the like.
		if (retainOwnership && item != null) {
			Game.onItemDropped((ServerPlayerEntity) (Object) this, item);
		}
	}
}
