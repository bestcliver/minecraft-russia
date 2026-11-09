package com.bestcliver.russia;

import java.util.HashMap;
import java.util.Map;

/** The two supported languages. Chosen per player with /russia lang. */
public enum Lang {
	RU, EN;

	public static final Lang[] VALUES = values();

	private final Map<String, String> strings = new HashMap<>();

	public String get(String key, Object... args) {
		String s = strings.getOrDefault(key, key);
		return args.length == 0 ? s : String.format(s, args);
	}

	private static void add(String key, String ru, String en) {
		RU.strings.put(key, ru);
		EN.strings.put(key, en);
	}

	static {
		// Laws (texts from the README)
		add("law.coupon.name", "Талон на убийство", "Murder coupon");
		add("law.coupon.desc", "Чтобы убить любое существо надо получить талон на убийство",
				"To kill any creature, you must obtain a murder coupon");
		add("law.tax.name", "Налог на добычу", "Extraction tax");
		add("law.tax.desc", "Налог на любую добычу 25%", "A 25% tax on any extraction");
		add("law.conscription.name", "Призыв", "Conscription");
		add("law.conscription.desc", "Необходимо сменить биом нахождения до конца игрового дня",
				"You must move to a different biome before the end of the in-game day");
		add("law.vpn.name", "Блокировка Майнкрафта в России", "Blocking of Minecraft in the Russian");
		add("law.vpn.desc", "Необходимо каждые 3 минуты менять ВПН с помощью выданного предмета",
				"The VPN service must be changed every 3 minutes using the provided item");
		add("law.rkn.name", "Роскомнадзор", "Roskomnadzor");
		add("law.rkn.desc", "Каждые 3 минут случайный предмет в инвентаре блокируется навсегда",
				"Every 3 minutes, a random item in the inventory becomes permanently locked");
		add("law.litter.name", "Штраф за мусор", "Littering fine");
		add("law.litter.desc", "Предметы выброшенные в не положенном месте облагаются штрафом",
				"Items discarded in an unauthorized place are subject to a fine");

		// Chat
		add("chat.new_law", "§6Новый закон — §e%s§f: %s", "§6New law — §e%s§f: %s");
		add("chat.started", "§aИгра началась. Новый закон каждые %s мин.", "§aThe game has started. A new law every %s min.");
		add("chat.restarted", "§aИгра перезапущена. Новый закон каждые %s мин.", "§aThe game has been restarted. A new law every %s min.");
		add("chat.rkn_locked", "§cРоскомнадзор заблокировал предмет: §f", "§cRoskomnadzor has locked an item: §f");
		add("chat.conscripted", "§cВы не сменили биом до конца игрового дня.", "§cYou did not change biome before the end of the in-game day.");
		add("chat.vpn_blocked", "§cМайнкрафт заблокирован. Смените ВПН выданным предметом.",
				"§cMinecraft is blocked. Change the VPN using the provided item.");
		add("chat.fine", "§cШтраф за мусор: изъято предметов на %s ед. ценности.",
				"§cLittering fine: items worth %s value units were confiscated.");

		// Action bar
		add("bar.need_coupon", "§cНужен талон на убийство", "§cA murder coupon is required");
		add("bar.coupon_used", "§eТалон на убийство использован", "§eMurder coupon used");
		add("bar.tax", "§eНалог 25%%: изъято %s шт.", "§e25%% tax: %s item(s) withheld");
		add("bar.biome_changed", "§aБиом сменён", "§aBiome changed");
		add("bar.vpn_changed", "§aВПН сменён", "§aVPN changed");
		add("bar.blocked", "§cЗаблокировано. Смените ВПН", "§cBlocked. Change the VPN");
		add("bar.locked", "§cПредмет заблокирован Роскомнадзором", "§cThis item is locked by Roskomnadzor");

		// Commands
		add("cmd.already_running", "§cИгра уже идёт. Используйте /russia restart", "§cThe game is already running. Use /russia restart");
		add("cmd.not_running", "§cИгра не начата. Используйте /russia start singleplayer или /russia start multiplayer",
				"§cThe game has not been started. Use /russia start singleplayer or /russia start multiplayer");
		add("cmd.player_only", "§cЭту команду может выполнить только игрок", "§cOnly a player can run this command");
		add("cmd.no_permission", "§cНедостаточно прав", "§cYou do not have permission");
		add("cmd.lang_set", "§aЯзык: Русский", "§aLanguage: English");
		add("cmd.all_issued", "§cВсе законы уже приняты", "§cAll laws have already been issued");
		add("cmd.interval_set", "§aИнтервал появления законов: %s мин.", "§aLaw interval: %s min.");
		add("cmd.coupon_given", "§aВыдан талон на убийство", "§aMurder coupon issued");
		add("cmd.help",
				"§6Россия MC — команды:\n"
						+ "§e/russia start singleplayer§f - Начать одиночную игру\n"
						+ "§e/russia start multiplayer§f - Начать многопользовательскую игру\n"
						+ "§e/russia help§f - Получить помощь по командам\n"
						+ "§e/russia restart§f - Перезапустить игру\n"
						+ "§e/russia lang en§f - Установить Английский\n"
						+ "§e/russia lang ru§f - Установить Русский\n"
						+ "§e/russia random§f - Появление случайного закона\n"
						+ "§e/russia interval <1-10>§f - Интервал появления законов\n"
						+ "§e/russia coupon§f - Получить талон на убийство (см. Закон 1)",
				"§6Russia MC — commands:\n"
						+ "§e/russia start singleplayer§f - Start a singleplayer game\n"
						+ "§e/russia start multiplayer§f - Start a multiplayer game\n"
						+ "§e/russia help§f - Get help with commands\n"
						+ "§e/russia restart§f - Restart game\n"
						+ "§e/russia lang en§f - Set English\n"
						+ "§e/russia lang ru§f - Set Russian\n"
						+ "§e/russia random§f - The emergence of a random law\n"
						+ "§e/russia interval <1-10>§f - The arrival interval of laws\n"
						+ "§e/russia coupon§f - To obtain a killing voucher (see Law 1)");

		// HUD / tooltip
		add("hud.next", "Следующий закон: %s", "Next law: %s");
		add("hud.all", "Все законы приняты", "All laws issued");
		add("hud.laws", "Законы:", "Laws:");
		add("tooltip.locked", "Заблокировано Роскомнадзором", "Locked by Roskomnadzor");
	}
}
