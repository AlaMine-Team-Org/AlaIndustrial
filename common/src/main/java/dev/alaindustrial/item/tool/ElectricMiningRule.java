package dev.alaindustrial.item.tool;

/**
 * The EU rule of the electric mining tools, stated once (MOD-707): when a tool runs at its own speed, and
 * which broken blocks it pays for. {@link ElectricMiningToolItem} is its only reader on the item side, so
 * the drill, the chainsaw, the shovel, the hoe and every tier of them follow the same three lines.
 *
 * <p>Minecraft-free on purpose, so L1 pins it ({@code ElectricMiningRuleTest}) — including the client-side
 * clause, which a world gametest cannot reach because an L2 test has no client level.
 */
public final class ElectricMiningRule {

	/**
	 * What a tool that cannot afford a block reports as its speed — exactly a bare hand. Not an
	 * approximation: {@code Player.getDestroySpeed} adds the Efficiency bonus only when the tool reports
	 * more than {@code 1.0F}, so any value even slightly above it would hand a flat tool its enchantment back.
	 */
	public static final float HAND_SPEED = 1.0f;

	private ElectricMiningRule() {
	}

	/**
	 * Does a tool holding {@code charge} EU afford an action costing {@code cost}? The one gate for both the
	 * speed (tool speed only when affordable, else {@link #HAND_SPEED}) and the spend (a block broken at hand
	 * speed is free): at exactly the cost the tool still runs and pays.
	 */
	public static boolean affords(long charge, long cost) {
		return charge >= cost;
	}

	/**
	 * Is a broken block billed at all? Only on the server — {@code mineBlock} runs on both sides and the
	 * charge moves on the server alone, the client reads it back from the synced component — and only for a
	 * block of non-zero hardness, mirroring vanilla's durability gate: instant-break blocks (a sapling, a
	 * torch, short grass) are free, a merely soft one (leaves 0.2, a snow layer 0.1) is not.
	 */
	public static boolean billsBlock(boolean clientSide, float hardness) {
		return !clientSide && hardness != 0.0f;
	}
}
