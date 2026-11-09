package com.bestcliver.russia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

/** World-wide game state, saved with the world so issued laws stay forever. */
public class GameState extends PersistentState {
	public static final int TICKS_PER_MINUTE = 20 * 60;

	public boolean running;
	public boolean multiplayer;
	/** The player who started a singleplayer game. */
	public UUID owner;
	public int intervalMinutes = 5;
	public int ticksUntilNext;
	/** Issued laws, the same for every player, in order of appearance. */
	public final List<Law> laws = new ArrayList<>();
	public final Map<UUID, PlayerData> players = new HashMap<>();

	public static class PlayerData {
		public Lang lang = Lang.RU;
		/** Conscription: biome the player has to leave before the game day ends, null = not started yet. */
		public String biome;
		/** Conscription: the game day being checked, and whether the biome was already changed during it. */
		public long conscriptionDay;
		public boolean biomeChanged;
		/** VPN: ticks until the block, -1 = not started yet. */
		public int vpnTicks = -1;
		public boolean blocked;
		/** Roskomnadzor: ticks until the next item lock, -1 = not started yet. */
		public int rknTicks = -1;

		/** Where a blocked player is held; not saved. */
		public transient Vec3d freezePos;

		public void resetLaws() {
			biome = null;
			conscriptionDay = 0;
			biomeChanged = false;
			vpnTicks = -1;
			blocked = false;
			rknTicks = -1;
			freezePos = null;
		}
	}

	public static GameState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(GameState::fromNbt, GameState::new, "russiamc");
	}

	public PlayerData data(UUID uuid) {
		return players.computeIfAbsent(uuid, u -> new PlayerData());
	}

	public void reset() {
		laws.clear();
		ticksUntilNext = intervalMinutes * TICKS_PER_MINUTE;
		players.values().forEach(PlayerData::resetLaws);
		markDirty();
	}

	public static GameState fromNbt(NbtCompound nbt) {
		GameState s = new GameState();
		s.running = nbt.getBoolean("running");
		s.multiplayer = nbt.getBoolean("multiplayer");
		if (nbt.containsUuid("owner")) {
			s.owner = nbt.getUuid("owner");
		}
		s.intervalMinutes = Math.max(1, Math.min(10, nbt.getInt("interval")));
		s.ticksUntilNext = nbt.getInt("ticksUntilNext");
		for (int ordinal : nbt.getIntArray("laws")) {
			if (ordinal >= 0 && ordinal < Law.VALUES.length && !s.laws.contains(Law.VALUES[ordinal])) {
				s.laws.add(Law.VALUES[ordinal]);
			}
		}
		NbtList list = nbt.getList("players", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound p = list.getCompound(i);
			PlayerData d = new PlayerData();
			d.lang = p.getBoolean("en") ? Lang.EN : Lang.RU;
			d.biome = p.contains("biome") ? p.getString("biome") : null;
			d.conscriptionDay = p.getLong("conscriptionDay");
			d.biomeChanged = p.getBoolean("biomeChanged");
			d.vpnTicks = p.getInt("vpnTicks");
			d.blocked = p.getBoolean("blocked");
			d.rknTicks = p.getInt("rknTicks");
			s.players.put(p.getUuid("uuid"), d);
		}
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putBoolean("running", running);
		nbt.putBoolean("multiplayer", multiplayer);
		if (owner != null) {
			nbt.putUuid("owner", owner);
		}
		nbt.putInt("interval", intervalMinutes);
		nbt.putInt("ticksUntilNext", ticksUntilNext);
		nbt.putIntArray("laws", laws.stream().mapToInt(Enum::ordinal).toArray());
		NbtList list = new NbtList();
		players.forEach((uuid, d) -> {
			NbtCompound p = new NbtCompound();
			p.putUuid("uuid", uuid);
			p.putBoolean("en", d.lang == Lang.EN);
			if (d.biome != null) {
				p.putString("biome", d.biome);
			}
			p.putLong("conscriptionDay", d.conscriptionDay);
			p.putBoolean("biomeChanged", d.biomeChanged);
			p.putInt("vpnTicks", d.vpnTicks);
			p.putBoolean("blocked", d.blocked);
			p.putInt("rknTicks", d.rknTicks);
			list.add(p);
		});
		nbt.put("players", list);
		return nbt;
	}
}
