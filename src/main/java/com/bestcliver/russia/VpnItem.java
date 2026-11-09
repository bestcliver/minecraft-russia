package com.bestcliver.russia;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** The item handed out by the "Blocking of Minecraft" law: right-click to change VPN. */
public class VpnItem extends Item {
	public VpnItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user instanceof ServerPlayerEntity player) {
			Game.onVpnUsed(player);
		}
		user.getItemCooldownManager().set(this, 20);
		return TypedActionResult.success(stack, world.isClient());
	}
}
