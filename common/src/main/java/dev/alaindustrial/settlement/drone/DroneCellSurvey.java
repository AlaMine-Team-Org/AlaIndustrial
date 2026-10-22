package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.registry.ModTags;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildingPlan;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The world-reading half of {@link DroneCellRules} (MOD-858; epic decisions R85, R93): what a builder drone sees in one
 * cell of a building's plan, turned into the rules' {@link DroneCellRules.Cell}.
 *
 * <ul>
 *   <li><b>Protected</b> — the drone never touches it: a block with a block entity (a chest, a machine, the core, the
 *       capsule, the dispatcher, a nest), a block on {@code alaindustrial:town_drone_protected}, and anything on the
 *       shaft of the lab under the building ({@link BuildingPlan#onLabShaft}).</li>
 *   <li><b>Removable</b> — rubble it may take away: a block on {@code alaindustrial:town_debris}; the ruin's own block
 *       where the intact building has another (the damage the ruin was made with); and anything placing replaces
 *       anyway — grass, snow, a flower.</li>
 *   <li><b>Skipped in the plan</b> — a block the drone does not put: one on the protected list (the core, the capsule,
 *       the dispatcher, a nest), or one no item places.</li>
 * </ul>
 */
public final class DroneCellSurvey {

	private DroneCellSurvey() {
	}

	/**
	 * Whether a clearing takes {@code world} out of the ring around a building (owner's round 4 of stage 6): terrain on
	 * {@code alaindustrial:town_ring_clear}, rubble on {@code alaindustrial:town_debris}, or anything placing replaces
	 * anyway — never air, a liquid, a block with a block entity or a protected one.
	 */
	public static boolean ringClearable(BlockState world) {
		if (world.isAir() || world.getBlock() instanceof LiquidBlock || world.hasBlockEntity()
				|| world.is(ModTags.Blocks.TOWN_DRONE_PROTECTED)) {
			return false;
		}
		return world.is(ModTags.Blocks.TOWN_RING_CLEAR) || world.is(ModTags.Blocks.TOWN_DEBRIS)
				|| world.canBeReplaced();
	}

	/** Cell {@code index} of {@code plan}, the world holding {@code world} there. */
	public static DroneCellRules.Cell survey(BuildingPlan plan, int index, BlockState world) {
		BlockState want = plan.expected(index);
		boolean wantAir = want.isAir() || plan.debris(index);
		boolean worldAir = world.isAir();
		boolean worldLiquid = world.getBlock() instanceof LiquidBlock;
		boolean worldProtected = !worldAir && (world.hasBlockEntity() || world.is(ModTags.Blocks.TOWN_DRONE_PROTECTED)
				|| plan.onLabShaft(index));
		BlockState ruin = plan.ruin(index);
		boolean ruinsOwn = !ruin.isAir() && world.is(ruin.getBlock()) && (wantAir || !want.is(ruin.getBlock()));
		boolean worldRemovable = !worldAir && !worldLiquid && !worldProtected
				&& (world.is(ModTags.Blocks.TOWN_DEBRIS) || ruinsOwn || world.canBeReplaced());
		boolean wantSkipped = !wantAir && (want.is(ModTags.Blocks.TOWN_DRONE_PROTECTED)
				|| want.is(ModTags.Blocks.TOWN_BUILD_NOT_BUILT)
				|| BuildMaterials.itemOf(want).isEmpty() || plan.onLabShaft(index));
		return new DroneCellRules.Cell(plan.matches(index, world), worldAir, worldLiquid, worldProtected,
				worldRemovable, wantAir, wantSkipped);
	}
}
