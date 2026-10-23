package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.registry.ModTags;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildingPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The world-reading half of {@link DroneCellRules} (MOD-858; epic decisions R85, R93): what a builder drone sees in one
 * cell of a building's plan, turned into the rules' {@link DroneCellRules.Cell}.
 *
 * <ul>
 *   <li><b>Protected</b> — the drone never touches it: a block with a block entity (a chest, a machine, the core, the
 *       capsule, the dispatcher, a nest), a block on {@code alaindustrial:town_drone_protected}, and anything on the
 *       shaft of the lab under the building ({@link BuildingPlan#onLabShaft}).</li>
 *   <li><b>Removable</b> — rubble it may take away: a block on {@code alaindustrial:town_debris}; the ruin's own block
 *       where the intact building has another (the damage the ruin was made with); anything placing replaces
 *       anyway; and litter on {@code alaindustrial:town_drone_litter} — grass, flowers, bushes, leaves, a snow layer
 *       (owner's round 4 of 0.1.219, R4-5). On the site of a house a player laid (MOD-877, R109) nature on
 *       {@code alaindustrial:town_site_clear} too: earth, stone, sand, leaves.</li>
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
		return ringClearable(world, false);
	}

	/**
	 * Whether a clearing takes {@code world} out of the ring around a building, as {@link #ringClearable(BlockState)};
	 * around a house a player laid ({@code laid}, MOD-877) nature on {@code alaindustrial:town_site_clear} goes too.
	 */
	public static boolean ringClearable(BlockState world, boolean laid) {
		if (world.isAir() || world.getBlock() instanceof LiquidBlock || world.hasBlockEntity()
				|| world.is(ModTags.Blocks.TOWN_DRONE_PROTECTED)) {
			return false;
		}
		return world.is(ModTags.Blocks.TOWN_RING_CLEAR) || world.is(ModTags.Blocks.TOWN_DEBRIS)
				|| world.canBeReplaced() || litter(world) || laid && world.is(ModTags.Blocks.TOWN_SITE_CLEAR);
	}

	/**
	 * Whether {@code world} is litter a drone takes out of its way by itself (owner's round 4 of 0.1.219, R4-5): on
	 * {@code alaindustrial:town_drone_litter} — grass, a flower, a bush, leaves, a snow layer — and not a liquid, not a
	 * block with a block entity, not a protected block. Never «in the way»: the drone clears it like rubble.
	 */
	public static boolean litter(BlockState world) {
		return world.is(ModTags.Blocks.TOWN_DRONE_LITTER) && !(world.getBlock() instanceof LiquidBlock)
				&& !world.hasBlockEntity() && !world.is(ModTags.Blocks.TOWN_DRONE_PROTECTED);
	}

	/**
	 * Whether a clearing takes {@code world} out of the ring around the building of {@code plan}: as
	 * {@link #ringClearable(BlockState, boolean)}, and the ruin's own rubble too — a block it left lying where the
	 * intact building is empty ({@link BuildingPlan#ruinRubble}, owner's round 1 of 0.1.219).
	 */
	public static boolean ringClearable(BlockState world, BuildingPlan plan) {
		if (ringClearable(world, plan.laid())) {
			return true;
		}
		return !world.isAir() && !(world.getBlock() instanceof LiquidBlock) && !world.hasBlockEntity()
				&& !world.is(ModTags.Blocks.TOWN_DRONE_PROTECTED) && plan.ruinRubble(world);
	}

	/**
	 * Whether a clearing fills the cell under a laid house's floor that holds {@code world} (MOD-877, R109): air, a
	 * liquid, or a plant the fill replaces anyway. A block with a block entity is never covered.
	 */
	public static boolean needsFill(BlockState world) {
		return !world.hasBlockEntity() && (world.isAir() || world.getBlock() instanceof LiquidBlock
				|| world.canBeReplaced() || litter(world));
	}

	/**
	 * Whether foundation cell {@code index} of {@code plan} wants earth (R3-5): it is open ({@link #needsFill}), it has
	 * ground under it, and every cell between it and the floor it holds up is open too — the pit right under a house,
	 * filled from its bottom up; not a cave under solid ground, nor a drop deeper than the foundation goes.
	 */
	public static boolean needsFoundation(BlockGetter level, BuildingPlan plan, int index) {
		BlockPos pos = plan.fillPos(index);
		if (!needsFill(level.getBlockState(pos)) || needsFill(level.getBlockState(pos.below()))) {
			return false;
		}
		for (int y = pos.getY() + 1; y < plan.fillFloor(index); y++) {
			if (!needsFill(level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * How many foundation cells of {@code plan} still want earth (R3-5); a cell in an unloaded chunk counts as wanting.
	 */
	public static int foundationWanted(Level level, BuildingPlan plan) {
		int wanted = 0;
		for (int f = 0; f < plan.fillSize(); f++) {
			if (!level.isLoaded(plan.fillPos(f)) || needsFoundation(level, plan, f)) {
				wanted++;
			}
		}
		return wanted;
	}

	/**
	 * Whether a drone may lift the block at {@code pos} out of its way for a while to reach a walled-up cell of
	 * {@code plan}, and put it back after (owner's round 5 of 0.1.219): a block of the building's own plan standing
	 * right, or ground, rubble and litter — never a block with data, a protected block, an unbreakable one, a liquid
	 * or a waterlogged block, a two-cell block, a block on the lab's shaft, one a falling block rests on or one
	 * beside a liquid. Anything else a player put outside the plan stays.
	 */
	public static boolean liftable(Level level, BuildingPlan plan, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()
				|| state.is(ModTags.Blocks.TOWN_DRONE_PROTECTED) || state.getDestroySpeed(level, pos) < 0.0F
				|| state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				|| state.hasProperty(BlockStateProperties.BED_PART) || plan.onLabShaft(pos)
				|| level.getBlockState(pos.above()).getBlock() instanceof FallingBlock) {
			return false;
		}
		for (Direction side : Direction.values()) {
			if (!level.getFluidState(pos.relative(side)).isEmpty()) {
				return false;
			}
		}
		int index = plan.indexOf(pos);
		if (index >= 0) {
			return !plan.debris(index) && plan.matches(index, state);
		}
		return state.is(ModTags.Blocks.TOWN_SITE_CLEAR) || state.is(ModTags.Blocks.TOWN_DEBRIS) || litter(state)
				|| state.canBeReplaced() || plan.ruinRubble(state);
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
				&& (world.is(ModTags.Blocks.TOWN_DEBRIS) || ruinsOwn || world.canBeReplaced() || litter(world)
						|| plan.laid() && world.is(ModTags.Blocks.TOWN_SITE_CLEAR));
		boolean wantSkipped = !wantAir && (want.is(ModTags.Blocks.TOWN_DRONE_PROTECTED)
				|| want.is(ModTags.Blocks.TOWN_BUILD_NOT_BUILT)
				|| BuildMaterials.itemOf(want).isEmpty() || plan.onLabShaft(index));
		return new DroneCellRules.Cell(plan.matches(index, world), worldAir, worldLiquid, worldProtected,
				worldRemovable, wantAir, wantSkipped);
	}
}
