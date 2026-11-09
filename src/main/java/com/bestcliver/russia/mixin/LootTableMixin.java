package com.bestcliver.russia.mixin;

import java.util.function.Consumer;

import com.bestcliver.russia.Game;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Extraction tax: 25% of any loot a player extracts (block drops, mob drops, fishing, chests...). */
@Mixin(LootTable.class)
public abstract class LootTableMixin {
	@ModifyVariable(method = "generateLoot(Lnet/minecraft/loot/context/LootContextParameterSet;JLjava/util/function/Consumer;)V",
			at = @At("HEAD"), argsOnly = true)
	private Consumer<ItemStack> russiamc$taxSeeded(Consumer<ItemStack> consumer, LootContextParameterSet parameters, long seed) {
		return russiamc$taxed(consumer, russiamc$extractor(parameters.getOptional(LootContextParameters.KILLER_ENTITY),
				parameters.getOptional(LootContextParameters.LAST_DAMAGE_PLAYER), parameters.getOptional(LootContextParameters.THIS_ENTITY)));
	}

	@ModifyVariable(method = "generateLoot(Lnet/minecraft/loot/context/LootContextParameterSet;Ljava/util/function/Consumer;)V",
			at = @At("HEAD"), argsOnly = true)
	private Consumer<ItemStack> russiamc$taxParameters(Consumer<ItemStack> consumer, LootContextParameterSet parameters) {
		return russiamc$taxed(consumer, russiamc$extractor(parameters.getOptional(LootContextParameters.KILLER_ENTITY),
				parameters.getOptional(LootContextParameters.LAST_DAMAGE_PLAYER), parameters.getOptional(LootContextParameters.THIS_ENTITY)));
	}

	@ModifyVariable(method = "generateLoot(Lnet/minecraft/loot/context/LootContext;Ljava/util/function/Consumer;)V",
			at = @At("HEAD"), argsOnly = true)
	private Consumer<ItemStack> russiamc$taxContext(Consumer<ItemStack> consumer, LootContext context) {
		return russiamc$taxed(consumer, russiamc$extractor(context.get(LootContextParameters.KILLER_ENTITY),
				context.get(LootContextParameters.LAST_DAMAGE_PLAYER), context.get(LootContextParameters.THIS_ENTITY)));
	}

	/** The player who gets the loot: the killer of a mob, the breaker of a block, the angler, the chest opener. */
	@Unique
	private static ServerPlayerEntity russiamc$extractor(Entity killer, Entity lastDamagePlayer, Entity thisEntity) {
		if (killer instanceof ServerPlayerEntity p) {
			return p;
		}
		if (lastDamagePlayer instanceof ServerPlayerEntity p) {
			return p;
		}
		if (thisEntity instanceof ServerPlayerEntity p) {
			return p;
		}
		if (thisEntity instanceof FishingBobberEntity bobber && bobber.getPlayerOwner() instanceof ServerPlayerEntity p) {
			return p;
		}
		return null;
	}

	@Unique
	private static Consumer<ItemStack> russiamc$taxed(Consumer<ItemStack> consumer, ServerPlayerEntity player) {
		if (player == null) {
			return consumer;
		}
		return stack -> {
			Game.tax(player, stack);
			if (!stack.isEmpty()) {
				consumer.accept(stack);
			}
		};
	}
}
