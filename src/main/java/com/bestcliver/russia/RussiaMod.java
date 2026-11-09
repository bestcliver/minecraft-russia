package com.bestcliver.russia;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RussiaMod implements ModInitializer {
	public static final String MOD_ID = "russiamc";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Item MURDER_COUPON = new Item(new Item.Settings());
	public static final Item VPN = new VpnItem(new Item.Settings().maxCount(1));

	@Override
	public void onInitialize() {
		Registry.register(Registries.ITEM, new Identifier(MOD_ID, "murder_coupon"), MURDER_COUPON);
		Registry.register(Registries.ITEM, new Identifier(MOD_ID, "vpn"), VPN);

		ServerTickEvents.END_SERVER_TICK.register(Game::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Game.sync(handler.player, GameState.get(server)));
		ServerLivingEntityEvents.ALLOW_DEATH.register(Game::allowDeath);
		registerRestrictions();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(buildCommand()));
	}

	/** A blocked player can do nothing but change the VPN; a locked item cannot be used to hit or dig. */
	private static void registerRestrictions() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (!stack.isOf(VPN) && Game.isBlocked(player)) {
				Game.notifyBlocked(player);
				return TypedActionResult.fail(stack);
			}
			return TypedActionResult.pass(stack);
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> blockedResult(player));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> blockedResult(player));
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> blockedOrLockedResult(player));
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> blockedOrLockedResult(player));
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> blockedOrLockedResult(player) == ActionResult.PASS);
	}

	private static ActionResult blockedResult(PlayerEntity player) {
		if (Game.isBlocked(player)) {
			Game.notifyBlocked(player);
			return ActionResult.FAIL;
		}
		return ActionResult.PASS;
	}

	private static ActionResult blockedOrLockedResult(PlayerEntity player) {
		if (player.isSpectator()) {
			return ActionResult.PASS;
		}
		if (Game.isLocked(player.getStackInHand(Hand.MAIN_HAND))) {
			Game.notifyLocked(player);
			return ActionResult.FAIL;
		}
		return blockedResult(player);
	}

	// ---------------------------------------------------------------- /russia

	private static LiteralArgumentBuilder<ServerCommandSource> buildCommand() {
		return literal("russia")
				.then(literal("start")
						.then(literal("singleplayer").executes(ctx -> start(ctx.getSource(), false)))
						.then(literal("multiplayer").executes(ctx -> start(ctx.getSource(), true)))
						// The README spells this one with a Cyrillic "м"; accept it as written too.
						.then(literal("мultiplayer").executes(ctx -> start(ctx.getSource(), true))))
				.then(literal("help").executes(ctx -> reply(ctx.getSource(), "cmd.help")))
				.then(literal("restart").executes(ctx -> restart(ctx.getSource())))
				.then(literal("lang")
						.then(literal("en").executes(ctx -> setLang(ctx.getSource(), Lang.EN)))
						.then(literal("ru").executes(ctx -> setLang(ctx.getSource(), Lang.RU))))
				.then(literal("random").executes(ctx -> random(ctx.getSource())))
				.then(literal("interval")
						.then(argument("minutes", IntegerArgumentType.integer(1, 10))
								.executes(ctx -> interval(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "minutes")))))
				.then(literal("coupon").executes(ctx -> coupon(ctx.getSource())));
	}

	private static Lang lang(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		return player == null ? Lang.RU : Game.lang(player);
	}

	private static int reply(ServerCommandSource source, String key, Object... args) {
		String text = lang(source).get(key, args);
		source.sendFeedback(() -> Text.literal(text), false);
		return 1;
	}

	private static int fail(ServerCommandSource source, String key) {
		source.sendError(Text.literal(lang(source).get(key)));
		return 0;
	}

	/** Game control is for operators and for the host of a singleplayer / LAN world. */
	private static boolean mayControl(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		return source.hasPermissionLevel(2) || (player != null && source.getServer().isHost(player.getGameProfile()));
	}

	private static int start(ServerCommandSource source, boolean multiplayer) {
		if (!mayControl(source)) {
			return fail(source, "cmd.no_permission");
		}
		ServerPlayerEntity player = source.getPlayer();
		if (!multiplayer && player == null) {
			return fail(source, "cmd.player_only");
		}
		MinecraftServer server = source.getServer();
		if (GameState.get(server).running) {
			return fail(source, "cmd.already_running");
		}
		Game.start(server, multiplayer, player == null ? null : player.getUuid(), false);
		return player == null ? reply(source, "chat.started", GameState.get(server).intervalMinutes) : 1;
	}

	private static int restart(ServerCommandSource source) {
		if (!mayControl(source)) {
			return fail(source, "cmd.no_permission");
		}
		GameState state = GameState.get(source.getServer());
		if (!state.running) {
			return fail(source, "cmd.not_running");
		}
		Game.start(source.getServer(), state.multiplayer, state.owner, true);
		return isParticipant(source, state) ? 1 : reply(source, "chat.restarted", state.intervalMinutes);
	}

	private static int setLang(ServerCommandSource source, Lang lang) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			return fail(source, "cmd.player_only");
		}
		GameState state = GameState.get(source.getServer());
		state.data(player.getUuid()).lang = lang;
		state.markDirty();
		Game.sync(player, state);
		return reply(source, "cmd.lang_set");
	}

	private static int random(ServerCommandSource source) {
		if (!mayControl(source)) {
			return fail(source, "cmd.no_permission");
		}
		if (!GameState.get(source.getServer()).running) {
			return fail(source, "cmd.not_running");
		}
		GameState state = GameState.get(source.getServer());
		if (!Game.issueRandomLaw(source.getServer())) {
			return fail(source, "cmd.all_issued");
		}
		if (!isParticipant(source, state)) {
			Law law = state.laws.get(state.laws.size() - 1);
			Lang lang = lang(source);
			return reply(source, "chat.new_law", law.name(lang), law.desc(lang));
		}
		return 1;
	}

	/** Participants are told about game events in chat already; everyone else (the console) gets a reply. */
	private static boolean isParticipant(ServerCommandSource source, GameState state) {
		ServerPlayerEntity player = source.getPlayer();
		return player != null && Game.isParticipant(state, player);
	}

	private static int interval(ServerCommandSource source, int minutes) {
		if (!mayControl(source)) {
			return fail(source, "cmd.no_permission");
		}
		Game.setInterval(source.getServer(), minutes);
		return reply(source, "cmd.interval_set", minutes);
	}

	private static int coupon(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			return fail(source, "cmd.player_only");
		}
		player.getInventory().offerOrDrop(new ItemStack(MURDER_COUPON));
		return reply(source, "cmd.coupon_given");
	}
}
