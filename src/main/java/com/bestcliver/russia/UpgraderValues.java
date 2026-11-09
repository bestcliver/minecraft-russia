package com.bestcliver.russia;

import java.lang.reflect.Method;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Item values from the Upgrader mod (net.execheinz.upgrader.value.ItemValues#stackValue). */
public final class UpgraderValues {
	private static Method stackValue;
	private static boolean failed;

	private UpgraderValues() {
	}

	/** Value of the whole stack in Upgrader units, 0 if it has none. */
	public static double stackValue(World world, ItemStack stack) {
		if (failed || stack.isEmpty()) {
			return 0;
		}
		try {
			if (stackValue == null) {
				stackValue = Class.forName("net.execheinz.upgrader.value.ItemValues").getMethod("stackValue", World.class, ItemStack.class);
			}
			double v = (double) stackValue.invoke(null, world, stack);
			return Double.isFinite(v) && v > 0 ? v : 0;
		} catch (ReflectiveOperationException | RuntimeException e) {
			failed = true;
			RussiaMod.LOGGER.error("Could not read item values from the Upgrader mod", e);
			return 0;
		}
	}
}
