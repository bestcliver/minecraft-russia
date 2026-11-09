package com.bestcliver.russia;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Server-side game logic: the law timer and the six laws. */
public final class Game {
	public static final Identifier SYNC_PACKET = new Identifier(RussiaMod.MOD_ID, "sync");
	public static final String LOCKED_KEY = "RussiaLocked";

	private static final int GAME_DAY_TICKS = 24000;
	private static final int THREE_MINUTES = 3 * GameState.TICKS_PER_MINUTE;
	private static final double FINE_VALUE = 300;
	/** Ticks after a drop when the place is checked (the pickup delay of a dropped item is 40). */
	private static final int LITTER_CHECK_TICKS = 38;

	/** Client mirror of the "blocked" flag, set from the sync packet. */
	public static volatile boolean clientBlocked;

	private static final List<Litter> LITTER = new ArrayList<>();
	private static int tickCounter;

	private static final class Litter {
		final ItemEntity item;
		final UUID player;
		int ticks = LITTER_CHECK_TICKS;

		Litter(ItemEntity item, UUID player) {
			this.item = item;
			this.player = player;
		}
	}

	private Game() {
	}

	// ---------------------------------------------------------------- helpers

	public static Lang lang(ServerPlayerEntity player) {
		return GameState.get(player.server).data(player.getUuid()).lang;
	}

	public static boolean isParticipant(GameState state, ServerPlayerEntity player) {
		return state.running && (state.multiplayer || player.getUuid().equals(state.owner));
	}

	public static boolean isActive(ServerPlayerEntity player, Law law) {
		GameState state = GameState.get(player.server);
		return isParticipant(state, player) && state.laws.contains(law);
	}

	private static List<ServerPlayerEntity> participants(MinecraftServer server, GameState state) {
		List<ServerPlayerEntity> list = new ArrayList<>();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (isParticipant(state, p)) {
				list.add(p);
			}
		}
		return list;
	}

	private static void chat(ServerPlayerEntity player, String key, Object... args) {
		player.sendMessage(Text.literal(lang(player).get(key, args)), false);
	}

	private static void bar(ServerPlayerEntity player, String key, Object... args) {
		player.sendMessage(Text.literal(lang(player).get(key, args)), true);
	}

	public static boolean isLocked(ItemStack stack) {
		return !stack.isEmpty() && stack.hasNbt() && stack.getNbt().getBoolean(LOCKED_KEY);
	}

	/** True while the player is blocked by the "Blocking of Minecraft" law. Works on both sides. */
	public static boolean isBlocked(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity sp) {
			return GameState.get(sp.server).data(sp.getUuid()).blocked && isActive(sp, Law.MINECRAFT_BLOCK);
		}
		return player.getWorld().isClient && clientBlocked;
	}

	public static void notifyLocked(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity sp) {
			bar(sp, "bar.locked");
		}
	}

	public static void notifyBlocked(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity sp) {
			bar(sp, "bar.blocked");
		}
	}

	// ---------------------------------------------------------------- game control

	public static void start(MinecraftServer server, boolean multiplayer, UUID owner, boolean restart) {
		GameState state = GameState.get(server);
		state.running = true;
		state.multiplayer = multiplayer;
		state.owner = owner;
		state.reset();
		LITTER.clear();
		for (ServerPlayerEntity p : participants(server, state)) {
			p.removeStatusEffect(StatusEffects.BLINDNESS);
			chat(p, restart ? "chat.restarted" : "chat.started", state.intervalMinutes);
		}
		syncAll(server, state);
	}

	/** Issues a random law that has not been issued yet. Returns false if all laws are already in force. */
	public static boolean issueRandomLaw(MinecraftServer server) {
		GameState state = GameState.get(server);
		List<Law> remaining = new ArrayList<>(List.of(Law.VALUES));
		remaining.removeAll(state.laws);
		if (remaining.isEmpty()) {
			return false;
		}
		Law law = remaining.get(server.getOverworld().getRandom().nextInt(remaining.size()));
		state.laws.add(law);
		state.markDirty();
		for (ServerPlayerEntity p : participants(server, state)) {
			Lang lang = lang(p);
			p.sendMessage(Text.literal(lang.get("chat.new_law", law.name(lang), law.desc(lang))), false);
		}
		syncAll(server, state);
		return true;
	}

	public static void setInterval(MinecraftServer server, int minutes) {
		GameState state = GameState.get(server);
		state.intervalMinutes = minutes;
		state.ticksUntilNext = minutes * GameState.TICKS_PER_MINUTE;
		state.markDirty();
		syncAll(server, state);
	}

	// ---------------------------------------------------------------- tick

	public static void tick(MinecraftServer server) {
		GameState state = GameState.get(server);
		tickCounter++;
		boolean second = tickCounter % 20 == 0;
		if (state.running) {
			if (state.laws.size() < Law.VALUES.length && --state.ticksUntilNext <= 0) {
				state.ticksUntilNext = state.intervalMinutes * GameState.TICKS_PER_MINUTE;
				issueRandomLaw(server);
			}
			for (ServerPlayerEntity p : participants(server, state)) {
				tickPlayer(state, p, second);
			}
			tickLitter(server, state);
			if (second) {
				state.markDirty();
			}
		}
		if (second) {
			syncAll(server, state);
		}
	}

	private static void tickPlayer(GameState state, ServerPlayerEntity p, boolean second) {
		if (!p.isAlive() || p.isSpectator()) {
			return;
		}
		GameState.PlayerData d = state.data(p.getUuid());

		if (state.laws.contains(Law.CONSCRIPTION)) {
			if (d.biome == null) {
				d.biome = biomeOf(p);
				d.conscriptionTicks = GAME_DAY_TICKS;
			} else {
				d.conscriptionTicks--;
				if (second && !biomeOf(p).equals(d.biome)) {
					d.biome = biomeOf(p);
					d.conscriptionTicks = GAME_DAY_TICKS;
					bar(p, "bar.biome_changed");
				} else if (d.conscriptionTicks <= 0) {
					d.biome = null;
					chat(p, "chat.conscripted");
					p.kill();
					return;
				}
			}
		}

		if (state.laws.contains(Law.MINECRAFT_BLOCK)) {
			if (d.vpnTicks < 0) {
				d.vpnTicks = THREE_MINUTES;
			}
			if (second && !hasVpn(p)) {
				p.getInventory().offerOrDrop(new ItemStack(RussiaMod.VPN));
			}
			if (!d.blocked) {
				if (--d.vpnTicks <= 0) {
					d.vpnTicks = 0;
					d.blocked = true;
					d.freezePos = null;
					chat(p, "chat.vpn_blocked");
				}
			}
			if (d.blocked) {
				holdInPlace(p, d);
				if (second || !p.hasStatusEffect(StatusEffects.BLINDNESS)) {
					p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, false, true));
				}
			}
		}

		if (state.laws.contains(Law.ROSKOMNADZOR)) {
			if (d.rknTicks < 0) {
				d.rknTicks = THREE_MINUTES;
			}
			if (--d.rknTicks <= 0) {
				d.rknTicks = THREE_MINUTES;
				lockRandomItem(p);
			}
		}
	}

	private static String biomeOf(ServerPlayerEntity p) {
		return p.getWorld().getBiome(p.getBlockPos()).getKey().map(k -> k.getValue().toString()).orElse("");
	}

	// ---------------------------------------------------------------- law: blocking of Minecraft (VPN)

	private static boolean hasVpn(ServerPlayerEntity p) {
		if (p.currentScreenHandler.getCursorStack().isOf(RussiaMod.VPN)) {
			return true;
		}
		PlayerInventory inv = p.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			if (inv.getStack(i).isOf(RussiaMod.VPN)) {
				return true;
			}
		}
		return false;
	}

	private static void holdInPlace(ServerPlayerEntity p, GameState.PlayerData d) {
		Vec3d pos = p.getPos();
		// A far jump means a respawn or a teleport by something else: hold the player at the new place.
		if (d.freezePos == null || pos.squaredDistanceTo(d.freezePos) > 64) {
			d.freezePos = pos;
		} else if (pos.squaredDistanceTo(d.freezePos) > 0.0025) {
			p.setVelocity(Vec3d.ZERO);
			p.networkHandler.requestTeleport(d.freezePos.x, d.freezePos.y, d.freezePos.z, p.getYaw(), p.getPitch());
		}
	}

	public static void onVpnUsed(ServerPlayerEntity p) {
		if (!isActive(p, Law.MINECRAFT_BLOCK)) {
			return;
		}
		GameState.PlayerData d = GameState.get(p.server).data(p.getUuid());
		d.vpnTicks = THREE_MINUTES;
		if (d.blocked) {
			d.blocked = false;
			d.freezePos = null;
			p.removeStatusEffect(StatusEffects.BLINDNESS);
		}
		bar(p, "bar.vpn_changed");
		sync(p, GameState.get(p.server));
	}

	// ---------------------------------------------------------------- law: Roskomnadzor

	private static void lockRandomItem(ServerPlayerEntity p) {
		PlayerInventory inv = p.getInventory();
		List<ItemStack> candidates = new ArrayList<>();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			// The VPN item is never locked, otherwise the VPN law could not be obeyed any more.
			if (!stack.isEmpty() && !isLocked(stack) && !stack.isOf(RussiaMod.VPN)) {
				candidates.add(stack);
			}
		}
		if (candidates.isEmpty()) {
			return;
		}
		ItemStack stack = candidates.get(p.getRandom().nextInt(candidates.size()));
		stack.getOrCreateNbt().putBoolean(LOCKED_KEY, true);
		p.sendMessage(Text.literal(lang(p).get("chat.rkn_locked")).append(stack.getName()), false);
	}

	// ---------------------------------------------------------------- law: murder coupon

	/** Fabric ALLOW_DEATH handler: a player may only kill with a coupon, which is spent on the kill. */
	public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		Entity attacker = source.getAttacker();
		if (!(attacker instanceof ServerPlayerEntity p) || p == entity || !isActive(p, Law.MURDER_COUPON)) {
			return true;
		}
		PlayerInventory inv = p.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isOf(RussiaMod.MURDER_COUPON) && !isLocked(stack)) {
				stack.decrement(1);
				bar(p, "bar.coupon_used");
				return true;
			}
		}
		entity.setHealth(1.0F);
		bar(p, "bar.need_coupon");
		return false;
	}

	// ---------------------------------------------------------------- law: extraction tax

	/** Takes 25% of a loot stack. Returns the number of items withheld. */
	public static int tax(ServerPlayerEntity p, ItemStack stack) {
		if (stack.isEmpty() || !isActive(p, Law.EXTRACTION_TAX)) {
			return 0;
		}
		double share = stack.getCount() * 0.25;
		int taken = (int) share;
		if (p.getRandom().nextDouble() < share - taken) {
			taken++;
		}
		if (taken > 0) {
			stack.decrement(taken);
			bar(p, "bar.tax", taken);
		}
		return taken;
	}

	// ---------------------------------------------------------------- law: littering fine

	/** Called when a player throws an item out (Q key or out of an inventory screen). */
	public static void onItemDropped(ServerPlayerEntity p, ItemEntity item) {
		if (p.isAlive() && isActive(p, Law.LITTERING)) {
			LITTER.add(new Litter(item, p.getUuid()));
		}
	}

	private static void tickLitter(MinecraftServer server, GameState state) {
		Iterator<Litter> it = LITTER.iterator();
		while (it.hasNext()) {
			Litter l = it.next();
			// Burned in lava/fire, destroyed by a cactus, fell into the void, sucked in by a hopper...
			if (l.item.isRemoved()) {
				it.remove();
				continue;
			}
			if (--l.ticks > 0) {
				continue;
			}
			it.remove();
			if (isDisposalPlace(l.item)) {
				l.item.discard();
				continue;
			}
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(l.player);
			if (p != null && isActive(p, Law.LITTERING)) {
				fine(p);
			}
		}
	}

	/** Lava, fire, cactus, composter, cauldron, hopper: places where items may be thrown away. */
	private static boolean isDisposalPlace(ItemEntity item) {
		BlockPos pos = item.getBlockPos();
		return item.isInLava() || isDisposalBlock(item, pos) || isDisposalBlock(item, pos.down());
	}

	private static boolean isDisposalBlock(ItemEntity item, BlockPos pos) {
		BlockState state = item.getWorld().getBlockState(pos);
		return state.isOf(Blocks.COMPOSTER) || state.isIn(BlockTags.CAULDRONS) || state.isOf(Blocks.HOPPER)
				|| state.isIn(BlockTags.FIRE) || state.isIn(BlockTags.CAMPFIRES) || state.isOf(Blocks.CACTUS)
				|| state.getFluidState().isIn(FluidTags.LAVA);
	}

	/**
	 * The fine: items worth 300 Upgrader value units are confiscated,
	 * the most expensive first, then the most expensive of what is left, and so on.
	 */
	private static void fine(ServerPlayerEntity p) {
		PlayerInventory inv = p.getInventory();
		double remaining = FINE_VALUE;
		double takenValue = 0;
		while (remaining > 0) {
			ItemStack best = ItemStack.EMPTY;
			double bestUnit = 0;
			for (int i = 0; i < inv.size(); i++) {
				ItemStack stack = inv.getStack(i);
				if (stack.isEmpty() || stack.isOf(RussiaMod.VPN)) {
					continue;
				}
				double unit = UpgraderValues.stackValue(p.getWorld(), stack) / stack.getCount();
				if (unit > bestUnit) {
					bestUnit = unit;
					best = stack;
				}
			}
			if (best.isEmpty()) {
				break;
			}
			int count = (int) Math.min(best.getCount(), Math.ceil(remaining / bestUnit));
			best.decrement(count);
			remaining -= count * bestUnit;
			takenValue += count * bestUnit;
		}
		chat(p, "chat.fine", Math.round(takenValue));
	}

	// ---------------------------------------------------------------- HUD sync

	public static void syncAll(MinecraftServer server, GameState state) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			sync(p, state);
		}
	}

	public static void sync(ServerPlayerEntity p, GameState state) {
		if (!ServerPlayNetworking.canSend(p, SYNC_PACKET)) {
			return;
		}
		GameState.PlayerData d = state.data(p.getUuid());
		boolean participant = isParticipant(state, p);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeByte(d.lang.ordinal());
		buf.writeBoolean(participant);
		if (participant) {
			boolean allIssued = state.laws.size() >= Law.VALUES.length;
			buf.writeVarInt(allIssued ? -1 : (state.ticksUntilNext + 19) / 20);
			buf.writeBoolean(d.blocked && state.laws.contains(Law.MINECRAFT_BLOCK));
			buf.writeVarInt(state.laws.size());
			for (Law law : state.laws) {
				buf.writeByte(law.ordinal());
				int ticks = switch (law) {
					case CONSCRIPTION -> d.biome == null ? -1 : d.conscriptionTicks;
					case MINECRAFT_BLOCK -> d.vpnTicks;
					case ROSKOMNADZOR -> d.rknTicks;
					default -> -1;
				};
				buf.writeVarInt(ticks < 0 ? -1 : (ticks + 19) / 20);
			}
		}
		ServerPlayNetworking.send(p, SYNC_PACKET, buf);
	}
}
