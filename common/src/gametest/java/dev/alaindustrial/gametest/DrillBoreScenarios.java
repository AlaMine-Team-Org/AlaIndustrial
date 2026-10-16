package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;
import static dev.alaindustrial.gametest.ElectricDrillScenarios.assertCharge;
import static dev.alaindustrial.gametest.ElectricDrillScenarios.mineColumnAim;

import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.tool.DrillBore;
import dev.alaindustrial.item.tool.DrillBoreMode;
import dev.alaindustrial.item.tool.DrillUpgrades;
import dev.alaindustrial.item.tool.ElectricDrillItem;
import dev.alaindustrial.network.DrillColumnTogglePayload;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Loader-neutral gametest bodies for the drill's bore modules (MOD-482 column, MOD-821 row): the row, the
 * cross, the key's mode cycle and the plan the cracks and the client prediction share. Split from
 * {@link ElectricDrillScenarios}, whose rig and helpers it reuses.
 */
public final class DrillBoreScenarios {

	/** The roster of this class's scenarios, declared next to their bodies (ADR-038). */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(DrillBoreScenarios::fun19RowBoresLeftAndRightOfFacing,
								"drill_row_bores_left_and_right_of_facing")
						.fabricId("ElectricDrillGameTest", "tcDrill001Fun19_rowBoresLeftAndRightOfFacing")
						.ticks(20, 40),
				RosterEntry.of(DrillBoreScenarios::fun20BothModulesBoreACrossAndPayForAllOfIt,
								"drill_both_modules_bore_a_cross")
						.fabricId("ElectricDrillGameTest", "tcDrill001Fun20_bothModulesBoreACrossAndPayForAllOfIt")
						.ticks(20, 40),
				RosterEntry.of(DrillBoreScenarios::fun21KeyCyclesTheBoreMode,
								"drill_key_cycles_the_bore_mode")
						.fabricId("ElectricDrillGameTest", "tcDrill001Fun21_keyCyclesTheBoreMode")
						.ticks(20, 40),
				RosterEntry.of(DrillBoreScenarios::fun22PlannedExtrasAreExactlyTheBlocksThatBreak,
								"drill_planned_extras_are_what_breaks")
						.fabricId("ElectricDrillGameTest", "tcDrill001Fun22_plannedExtrasAreExactlyTheBlocksThatBreak")
						.ticks(20, 40));

		private Roster() {}
	}

	private DrillBoreScenarios() {}

	// ── MOD-821 — row bore module ────────────────────────────────────────────────────────────────

	/** A drill of {@code item} carrying the named bore modules, both switched on. */
	private static ItemStack boreDrill(Item item, long eu, boolean column, boolean row) {
		ItemStack stack = new ItemStack(item);
		ItemEnergy.set(stack, eu);
		if (column && !DrillUpgrades.install(stack, DrillUpgrades.COLUMN_BORE)
				|| row && !DrillUpgrades.install(stack, DrillUpgrades.ROW_BORE)) {
			throw new IllegalStateException("fixture error: a bore module did not install on " + item);
		}
		return stack;
	}

	/** Stone on the aimed block, the eight blocks around it in its 3x3 plane, and one behind and in front. */
	private static void setStonePlane(GameTestHelper helper) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				helper.setBlock(ElectricDrillScenarios.COLUMN_AIM.offset(dx, dy, 0), Blocks.STONE);
			}
		}
		helper.setBlock(ElectricDrillScenarios.COLUMN_AIM.offset(0, 0, 1), Blocks.STONE);
		helper.setBlock(ElectricDrillScenarios.COLUMN_AIM.offset(0, 0, -1), Blocks.STONE);
	}

	private static void assertAt(GameTestHelper helper, Block expected, int dx, int dy, int dz, String what) {
		BlockPos at = ElectricDrillScenarios.COLUMN_AIM.offset(dx, dy, dz);
		if (!helper.getBlockState(at).is(expected)) {
			helper.fail(what + ": expected " + expected + " at offset (" + dx + ", " + dy + ", " + dz
					+ ") but found " + helper.getBlockState(at).getBlock());
		}
	}

	/**
	 * TC-DRILL-001-FUN19 (MOD-821): a drill carrying only the row bore takes the block to the left and to the
	 * right of the one it hits, measured on the player's horizontal facing — and nothing above or below.
	 *
	 * <p>Two facings, because "left and right" is the whole feature: facing north the row lies along X, facing
	 * east it lies along Z. A rig that only checked one would pass an implementation that hard-coded an axis.
	 *
	 * @implements TC-DRILL-001-FUN19 — the row bore breaks the two side blocks across the facing and bills each
	 *     at the column price.
	 */
	public static void fun19RowBoresLeftAndRightOfFacing(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		Item drillItem = ModContent.ELECTRIC_DRILL.get();
		long full = ToolConfig.electricDrillBuffer;

		// 1. Facing north: the row lies along X.
		player.setYRot(180.0f);
		setStonePlane(helper);
		ItemStack drill = boreDrill(drillItem, full, false, true);
		mineColumnAim(helper, player, drill);
		assertAt(helper, Blocks.AIR, 0, 0, 0, "the aimed block");
		assertAt(helper, Blocks.AIR, -1, 0, 0, "facing north, the block to the left");
		assertAt(helper, Blocks.AIR, 1, 0, 0, "facing north, the block to the right");
		assertAt(helper, Blocks.STONE, 0, 1, 0, "a row bore must leave the block above");
		assertAt(helper, Blocks.STONE, 0, -1, 0, "a row bore must leave the block below");
		assertAt(helper, Blocks.STONE, 0, 0, 1, "a row bore must leave the block behind");
		assertCharge(helper, drill, full - ToolConfig.electricDrillEuPerBlock
				- 2L * ToolConfig.electricDrillColumnEuPerBlock,
				"a row stroke bills the aimed block plus two extras");

		// 2. Facing east: the row lies along Z.
		player.setYRot(-90.0f);
		setStonePlane(helper);
		ItemStack east = boreDrill(drillItem, full, false, true);
		mineColumnAim(helper, player, east);
		assertAt(helper, Blocks.AIR, 0, 0, 1, "facing east, one side block");
		assertAt(helper, Blocks.AIR, 0, 0, -1, "facing east, the other side block");
		assertAt(helper, Blocks.STONE, 1, 0, 0, "facing east, the X neighbours are no longer the row");
		assertAt(helper, Blocks.STONE, -1, 0, 0, "facing east, the X neighbours are no longer the row");
		helper.succeed();
	}

	/**
	 * TC-DRILL-001-FUN20 (MOD-821): with both modules the drill bores a "+" — centre, above, below, left, right —
	 * and leaves the four corners; the whole set is paid for up front, so a drill one EU short of it mines only
	 * the aimed block.
	 *
	 * @implements TC-DRILL-001-FUN20 — both modules bore five blocks at plain + 4 x column price; a drill short of
	 *     the whole set bores only the aimed block.
	 */
	public static void fun20BothModulesBoreACrossAndPayForAllOfIt(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		Item drillItem = ModContent.ELECTRIC_DRILL.get();
		long cross = ToolConfig.electricDrillEuPerBlock + 4L * ToolConfig.electricDrillColumnEuPerBlock;
		player.setYRot(180.0f);

		setStonePlane(helper);
		ItemStack drill = boreDrill(drillItem, cross, true, true);
		mineColumnAim(helper, player, drill);
		assertAt(helper, Blocks.AIR, 0, 0, 0, "the aimed block");
		assertAt(helper, Blocks.AIR, 0, 1, 0, "above");
		assertAt(helper, Blocks.AIR, 0, -1, 0, "below");
		assertAt(helper, Blocks.AIR, -1, 0, 0, "left");
		assertAt(helper, Blocks.AIR, 1, 0, 0, "right");
		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dy = -1; dy <= 1; dy += 2) {
				assertAt(helper, Blocks.STONE, dx, dy, 0, "a corner of the cross must stay");
			}
		}
		assertCharge(helper, drill, 0, "a full cross stroke bills the aimed block plus four extras");

		setStonePlane(helper);
		ItemStack almost = boreDrill(drillItem, cross - 1, true, true);
		mineColumnAim(helper, player, almost);
		assertAt(helper, Blocks.AIR, 0, 0, 0, "the aimed block always breaks");
		assertAt(helper, Blocks.STONE, 0, 1, 0, "short of the whole cross: nothing above");
		assertAt(helper, Blocks.STONE, 1, 0, 0, "short of the whole cross: nothing to the side");
		assertCharge(helper, almost, cross - 1 - ToolConfig.electricDrillEuPerBlock,
				"short of the whole cross only the aimed block is billed");
		helper.succeed();
	}

	/**
	 * TC-DRILL-001-FUN21 (MOD-821): the key cycles the bore mode — cross, column, row, single, cross — on a drill
	 * with both modules, flips the one module on a drill with one, and a drill with none stays untouched.
	 *
	 * <p>It drives the real server handler of the key's packet, so what is asserted is the state the player's
	 * next stroke would see: the effective {@code boreMode} and the two switches. A switched-off module must also
	 * really stop boring (checked for the row, the new half).
	 *
	 * @implements TC-DRILL-001-FUN21 — the toggle key walks the documented mode cycle and a switched-off row bore
	 *     breaks nothing beside the aimed block.
	 */
	public static void fun21KeyCyclesTheBoreMode(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		Item drillItem = ModContent.ELECTRIC_DRILL.get();
		DrillColumnTogglePayload press = new DrillColumnTogglePayload();
		DrillBoreMode[] expected = {DrillBoreMode.COLUMN, DrillBoreMode.ROW, DrillBoreMode.SINGLE, DrillBoreMode.CROSS};

		ItemStack both = boreDrill(drillItem, ToolConfig.electricDrillBuffer, true, true);
		player.setItemInHand(InteractionHand.MAIN_HAND, both);
		if (DrillBore.boreMode(both) != DrillBoreMode.CROSS) {
			helper.fail("a fresh drill with both modules must start on the cross");
		}
		for (DrillBoreMode want : expected) {
			DrillColumnTogglePayload.handle(press, player);
			DrillBoreMode got = DrillBore.boreMode(both);
			if (got != want) {
				helper.fail("the key must cycle cross, column, row, single, cross — expected " + want + " got " + got);
			}
		}

		// One module: the key flips that module only.
		ItemStack rowOnly = boreDrill(drillItem, ToolConfig.electricDrillBuffer, false, true);
		player.setItemInHand(InteractionHand.MAIN_HAND, rowOnly);
		DrillColumnTogglePayload.handle(press, player);
		if (DrillBore.isRowActive(rowOnly) || DrillBore.boreMode(rowOnly) != DrillBoreMode.SINGLE) {
			helper.fail("with only the row bore installed the key must switch it off");
		}
		// Switched off, the row bore must break nothing beside the aimed block.
		player.setYRot(180.0f);
		setStonePlane(helper);
		mineColumnAim(helper, player, rowOnly);
		assertAt(helper, Blocks.STONE, -1, 0, 0, "a switched-off row bore must leave the left block");
		assertAt(helper, Blocks.STONE, 1, 0, 0, "a switched-off row bore must leave the right block");
		DrillColumnTogglePayload.handle(press, player);
		if (!DrillBore.isRowActive(rowOnly) || rowOnly.has(ModDataComponents.DRILL_ROW_ENABLED.get())) {
			helper.fail("switching back on must remove the component, leaving a pristine stack");
		}

		// No module: nothing changes.
		ItemStack bare = boreDrill(drillItem, ToolConfig.electricDrillBuffer, false, false);
		player.setItemInHand(InteractionHand.MAIN_HAND, bare);
		DrillColumnTogglePayload.handle(press, player);
		if (bare.has(ModDataComponents.DRILL_ROW_ENABLED.get())
				|| bare.has(ModDataComponents.DRILL_COLUMN_ENABLED.get())) {
			helper.fail("a drill with no module must not be written to by the key");
		}
		helper.succeed();
	}

	/**
	 * TC-DRILL-001-FUN22 (MOD-821): the plan the cracks and the client prediction draw is exactly what the server
	 * breaks, and it counts only blocks that really qualify.
	 *
	 * <p>Four situations on a row-only drill facing north (left is X-1, right is X+1): a drill that cannot pay for
	 * the aimed block plans nothing and breaks the centre alone; stone on one side and AIR on the other is priced
	 * for ONE extra (aim + 1 x extra is enough, one EU less is not); dirt beside the hole is no pickaxe block, so
	 * it is neither planned nor priced; and in creative the plan is empty.
	 *
	 * @implements TC-DRILL-001-FUN22 — plannedExtras lists only the affordable, qualifying extras, prices only
	 *     those, and is empty in creative or when the aimed block itself cannot be paid for.
	 */
	public static void fun22PlannedExtrasAreExactlyTheBlocksThatBreak(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		Item drillItem = ModContent.ELECTRIC_DRILL.get();
		long aim = ToolConfig.electricDrillEuPerBlock;
		long extra = ToolConfig.electricDrillColumnEuPerBlock;
		player.setYRot(180.0f);

		// 1. Cannot pay for the aimed block: no plan, the centre alone breaks, nothing is billed.
		setStonePlane(helper);
		ItemStack flat = boreDrill(drillItem, aim - 1, true, true);
		player.setItemInHand(InteractionHand.MAIN_HAND, flat);
		assertPlan(helper, flat, player, 0, "a drill short of the aimed block must plan nothing");
		mineColumnAim(helper, player, flat);
		assertAt(helper, Blocks.AIR, 0, 0, 0, "the aimed block");
		assertAt(helper, Blocks.STONE, 1, 0, 0, "an unaffordable drill must leave the side");
		assertAt(helper, Blocks.STONE, 0, 1, 0, "an unaffordable drill must leave the block above");

		// 2. Stone on one side, air on the other: priced for one extra.
		setStonePlane(helper);
		helper.setBlock(ElectricDrillScenarios.COLUMN_AIM.offset(1, 0, 0), Blocks.AIR);
		ItemStack justEnough = boreDrill(drillItem, aim + extra, false, true);
		player.setItemInHand(InteractionHand.MAIN_HAND, justEnough);
		assertPlan(helper, justEnough, player, 1, "the air side must not be planned");
		mineColumnAim(helper, player, justEnough);
		assertAt(helper, Blocks.AIR, -1, 0, 0, "the stone side");
		assertCharge(helper, justEnough, 0, "aim + one extra, no more: the air beside the hole is not billed");
		setStonePlane(helper);
		helper.setBlock(ElectricDrillScenarios.COLUMN_AIM.offset(1, 0, 0), Blocks.AIR);
		ItemStack oneShort = boreDrill(drillItem, aim + extra - 1, false, true);
		player.setItemInHand(InteractionHand.MAIN_HAND, oneShort);
		assertPlan(helper, oneShort, player, 0, "one EU short of aim + extra must plan nothing");
		mineColumnAim(helper, player, oneShort);
		assertAt(helper, Blocks.STONE, -1, 0, 0, "one EU short: the side stays");

		// 3. Dirt beside the hole is no pickaxe block: not planned, not priced.
		setStonePlane(helper);
		helper.setBlock(ElectricDrillScenarios.COLUMN_AIM.offset(-1, 0, 0), Blocks.DIRT);
		ItemStack dirtSide = boreDrill(drillItem, aim + extra, false, true);
		player.setItemInHand(InteractionHand.MAIN_HAND, dirtSide);
		assertPlan(helper, dirtSide, player, 1, "dirt must not be planned");
		mineColumnAim(helper, player, dirtSide);
		assertAt(helper, Blocks.DIRT, -1, 0, 0, "the dirt side must stay");
		assertAt(helper, Blocks.AIR, 1, 0, 0, "the stone side");
		assertCharge(helper, dirtSide, 0, "dirt beside the hole is not billed");

		// 4. Creative breaks no extras: the server never calls mineBlock there, so nothing may be drawn either.
		setStonePlane(helper);
		ItemStack creative = boreDrill(drillItem, ToolConfig.electricDrillBuffer, true, true);
		player.setItemInHand(InteractionHand.MAIN_HAND, creative);
		player.getAbilities().instabuild = true;
		try {
			assertPlan(helper, creative, player, 0, "creative must plan nothing");
		} finally {
			player.getAbilities().instabuild = false;
		}
		helper.succeed();
	}

	private static void assertPlan(GameTestHelper helper, ItemStack drill, ServerPlayer player, int expected,
			String what) {
		BlockPos aimPos = helper.absolutePos(ElectricDrillScenarios.COLUMN_AIM);
		int actual = ElectricDrillItem.plannedExtras(drill, helper.getLevel(), aimPos,
				helper.getLevel().getBlockState(aimPos), player).size();
		if (actual != expected) {
			helper.fail(what + ": expected " + expected + " planned extras, got " + actual);
		}
	}
}
