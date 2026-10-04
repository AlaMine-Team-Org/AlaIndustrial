package dev.alaindustrial.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;

/**
 * How a scenario counts what a rig dropped (MOD-717, TST-3): the one {@code countDrops} the harvest, scythe,
 * trellis and assembler scenarios used to copy as their own — {@code assertItemEntityPresent} can only answer
 * present/absent, and these scenarios need the exact number.
 *
 * <p>The radius is deliberate, and both extremes were tried and rejected on the NeoForge lane (MOD-335):
 * <ul>
 *   <li>{@code inflate(6.0)} was too wide — gametests sit only ~6 blocks apart, so the box reached
 *       into the neighbouring test and counted <em>its</em> drops ("a planted trellis returned 2
 *       seeds" was this rig plus the one next door, each seeing the other's seed). It stayed green
 *       on Fabric, whose layout is sparser — a loader-specific false pass.</li>
 *   <li>{@link GameTestHelper#getBounds()} was too tight — the NeoForge lane registers these bodies
 *       from code with a minimal structure, whose bounds do not cover the rig at all, so every count
 *       came back 0.</li>
 * </ul>
 * A radius of 2 spans well under the inter-test spacing while comfortably containing anything
 * {@code popResource} scatters around a plant or a broken block. It is a property of the lanes' layout, not of
 * one scenario, so it is not a parameter.
 *
 * <p>A new scenario that counts drops calls this; it does not declare a private copy.
 */
public final class GameTestDrops {

	/** Blocks around the counted position — see the class javadoc for why exactly 2. */
	private static final double RADIUS = 2.0;

	private GameTestDrops() {}

	/** Total count of {@code item} lying within {@link #RADIUS} blocks of {@code pos} (relative to the rig). */
	public static int countDrops(GameTestHelper helper, BlockPos pos, Item item) {
		AABB box = new AABB(helper.absolutePos(pos)).inflate(RADIUS);
		int total = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (entity.getItem().is(item)) {
				total += entity.getItem().getCount();
			}
		}
		return total;
	}
}
