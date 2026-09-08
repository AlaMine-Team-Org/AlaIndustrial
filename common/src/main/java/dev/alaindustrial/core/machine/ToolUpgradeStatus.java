package dev.alaindustrial.core.machine;

/**
 * What the Upgrade Table (MOD-482) is waiting for, in the order the player has to fix it.
 *
 * <p>Minecraft-free on purpose, like {@link RepairStatus}: the whole content of the type is
 * {@link #resolve}, and a plain unit test can assert every branch of it without a running machine.
 *
 * <p>The enum also answers the two questions the tick loop asks — {@link #canWork()} and
 * {@link #jobIntact()} — so the line printed on the screen and the arrow drawn next to it cannot
 * contradict each other: both come from this one value.
 */
public enum ToolUpgradeStatus {
	/** Nothing in the tool slot. */
	NO_TOOL,
	/** A tool is loaded, but no module to fit. */
	NO_MODULE,
	/** The loaded tool already carries the module's upgrade — fitting a second one buys nothing. */
	ALREADY_INSTALLED,
	/** Tool, module and room: the table can run. */
	READY;

	/**
	 * Classify the table's contents.
	 *
	 * <p>Order is the point: a player with an empty table is told to put a drill in, not to find a
	 * module. Each answer names the FIRST thing standing in the way.
	 */
	public static ToolUpgradeStatus resolve(boolean hasTool, boolean hasModule, boolean alreadyInstalled) {
		if (!hasTool) {
			return NO_TOOL;
		}
		if (!hasModule) {
			return NO_MODULE;
		}
		if (alreadyInstalled) {
			return ALREADY_INSTALLED;
		}
		return READY;
	}

	/** Everything except energy is in place. */
	public boolean canWork() {
		return this == READY;
	}

	/** The job the accumulated progress was bought for still exists (R-NRG-10). */
	public boolean jobIntact() {
		return this == READY;
	}

	public int code() {
		return ordinal();
	}

	/**
	 * The status for a wire value. Out-of-range falls back to {@link #NO_TOOL} rather than throwing: a
	 * client that read a stale or truncated channel should draw "put a tool in", not crash the render
	 * thread.
	 */
	public static ToolUpgradeStatus byCode(int code) {
		ToolUpgradeStatus[] all = values();
		return code >= 0 && code < all.length ? all[code] : NO_TOOL;
	}

	/** Lang key for the line the screen prints. */
	public String translationKey() {
		return "gui.alaindustrial.upgrade_table." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
