package com.bestcliver.russia.client;

import java.util.ArrayList;
import java.util.List;

import com.bestcliver.russia.Game;
import com.bestcliver.russia.Lang;
import com.bestcliver.russia.Law;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Client side: the window in the top right corner with the countdown and the laws in force. */
public class RussiaClient implements ClientModInitializer {
	private static Lang lang = Lang.RU;
	private static boolean visible;
	private static int nextSeconds;
	private static int[] laws = new int[0];
	private static int[] lawSeconds = new int[0];

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(Game.SYNC_PACKET, (client, handler, buf, responseSender) -> {
			Lang newLang = Lang.VALUES[buf.readByte()];
			boolean participant = buf.readBoolean();
			int next = 0;
			boolean blocked = false;
			int[] ids = new int[0];
			int[] secs = new int[0];
			if (participant) {
				next = buf.readVarInt();
				blocked = buf.readBoolean();
				int n = buf.readVarInt();
				ids = new int[n];
				secs = new int[n];
				for (int i = 0; i < n; i++) {
					ids[i] = buf.readByte();
					secs[i] = buf.readVarInt();
				}
			}
			int fNext = next;
			boolean fBlocked = blocked;
			int[] fIds = ids;
			int[] fSecs = secs;
			client.execute(() -> {
				lang = newLang;
				visible = participant;
				nextSeconds = fNext;
				laws = fIds;
				lawSeconds = fSecs;
				Game.clientBlocked = fBlocked;
			});
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			visible = false;
			Game.clientBlocked = false;
		});
		HudRenderCallback.EVENT.register(RussiaClient::render);
		ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
			if (Game.isLocked(stack)) {
				lines.add(Text.literal(lang.get("tooltip.locked")).formatted(Formatting.RED));
			}
		});
	}

	private static String time(int seconds) {
		return String.format("%02d:%02d", seconds / 60, seconds % 60);
	}

	private static void render(DrawContext ctx, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!visible || client.player == null || client.options.hudHidden || client.options.debugEnabled) {
			return;
		}
		List<String> lines = new ArrayList<>();
		lines.add(nextSeconds < 0 ? lang.get("hud.all") : lang.get("hud.next", time(nextSeconds)));
		if (laws.length > 0) {
			lines.add(lang.get("hud.laws"));
			for (int i = 0; i < laws.length; i++) {
				String line = "- " + Law.VALUES[laws[i]].name(lang);
				if (lawSeconds[i] >= 0) {
					line += " (" + time(lawSeconds[i]) + ")";
				}
				lines.add(line);
			}
		}

		TextRenderer font = client.textRenderer;
		int width = 0;
		for (String line : lines) {
			width = Math.max(width, font.getWidth(line));
		}
		int pad = 4;
		int boxW = width + pad * 2;
		int boxH = lines.size() * 10 + pad * 2 - 1;
		int x = ctx.getScaledWindowWidth() - boxW - 2;
		int y = 2 + effectsOffset(client);

		ctx.fill(x, y, x + boxW, y + boxH, 0x90000000);
		for (int i = 0; i < lines.size(); i++) {
			int color = i == 0 ? 0xFFFF55 : i == 1 ? 0xAAAAAA : 0xFFFFFF;
			ctx.drawTextWithShadow(font, lines.get(i), x + pad, y + pad + i * 10, color);
		}
	}

	/** Vanilla draws status effect icons in the same corner: move below them. */
	private static int effectsOffset(MinecraftClient client) {
		boolean beneficial = false;
		boolean harmful = false;
		for (StatusEffectInstance effect : client.player.getStatusEffects()) {
			if (effect.shouldShowIcon()) {
				if (effect.getEffectType().isBeneficial()) {
					beneficial = true;
				} else {
					harmful = true;
				}
			}
		}
		return harmful ? 52 : beneficial ? 26 : 0;
	}
}
