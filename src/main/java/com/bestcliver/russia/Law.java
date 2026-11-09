package com.bestcliver.russia;

/** The laws, in README order. Texts live in {@link Lang} under "law.<id>.name" / "law.<id>.desc". */
public enum Law {
	MURDER_COUPON("coupon"),
	EXTRACTION_TAX("tax"),
	CONSCRIPTION("conscription"),
	MINECRAFT_BLOCK("vpn"),
	ROSKOMNADZOR("rkn"),
	LITTERING("litter");

	public static final Law[] VALUES = values();

	public final String id;

	Law(String id) {
		this.id = id;
	}

	public String name(Lang lang) {
		return lang.get("law." + id + ".name");
	}

	public String desc(Lang lang) {
		return lang.get("law." + id + ".desc");
	}
}
