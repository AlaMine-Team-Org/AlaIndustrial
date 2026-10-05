package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.gametest.compat.UseResults;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

/**
 * Loader-neutral gametest bodies for the Electric Hoe (suite TC-HOE-001) — the diamond-tipped upgrade
 * (MOD-378), the flat-hoe click paths (MOD-389) and, since MOD-364, the base tool's own EU contract
 * including <b>paid tilling</b>. Same pattern as {@link ElectricChainsawScenarios}: plain
 * {@code GameTestHelper} bodies wrapped by the Fabric {@code ElectricHoeGameTest} suite and registered on
 * the NeoForge {@code gameTestServer} lane via {@code NeoForgeGameTests} — both loaders run the SAME
 * logic.
 *
 * <p>Numbers come from {@link Config} (electricHoeBuffer, electricHoeEuPerBlock, electricHoeTillEuCost),
 * and moisture is read back off the real {@code FarmlandBlock} state rather than from a re-implementation
 * of what vanilla is believed to store.
 *
 * <p><b>Why FUN03 and FUN04 exist.</b> FUN02 on its own is a test that cannot fail for the right reason:
 * if the perk were accidentally moved into the base hoe, or if the watering fired on any consumed click
 * instead of on a real till, FUN02 would stay green. FUN03 pins the perk to the upgrade (the base hoe must
 * leave the plot dry) and FUN04 pins it to an actually-paid till — see its javadoc for the specific
 * 26.2 trap it guards.
 */
public final class ElectricHoeScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ElectricHoeScenarios::fun01DiamondTipSpeedAndTier, "hoe_diamond_tip_speed_and_tier")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun01_diamondTipSpeedAndTier").ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::fun02DiamondTipTillLeavesPlotWatered,
								"hoe_diamond_tip_till_waters_plot")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun02_diamondTipTillLeavesPlotWatered")
						.ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::fun03BaseHoeLeavesPlotDry, "hoe_base_leaves_plot_dry")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun03_baseHoeLeavesPlotDry").ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::fun04FlatUpgradeCannotWaterExistingFarmland,
								"hoe_flat_upgrade_cannot_water_farmland")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun04_flatUpgradeCannotWaterExistingFarmland")
						.ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::neg01FlatHoeOnNonTillableDoesNotSwallowClick,
								"hoe_flat_on_non_tillable_passes")
						.fabricId("ElectricHoeGameTest", "tcHoe001Neg01_flatHoeOnNonTillableDoesNotSwallowClick")
						.ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::fun05FlatHoeOnTillableStillRefuses,
								"hoe_flat_on_tillable_still_refuses")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun05_flatHoeOnTillableStillRefuses").ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::fun06ChargeInBatteryBox, "hoe_charge_in_battery_box")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun06_chargeInBatteryBox").ticks(20, 80),
				RosterEntry.of(ElectricHoeScenarios::fun07DrainOnMineBlock, "hoe_drain_on_mine_block")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun07_drainOnMineBlock").ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::fun08NoDrainBelowCost, "hoe_no_drain_below_cost")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun08_noDrainBelowCost").ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::fun09ZeroHardnessFreeMossCosts,
								"hoe_zero_hardness_free_moss_costs")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun09_zeroHardnessFreeMossCosts").ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::fun10SpeedAndDrops, "hoe_speed_and_drops")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun10_speedAndDrops").ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::per01ChargeRoundTrip, "hoe_charge_round_trip")
						.fabricId("ElectricHoeGameTest", "tcHoe001Per01_chargeRoundTrip").ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::fun11TillDrainsExactlyTillCost, "hoe_till_drains_till_cost")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun11_tillDrainsExactlyTillCost").ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::fun12TillRefusedJustBelowCost, "hoe_till_refused_just_below_cost")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun12_tillRefusedJustBelowCost").ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::fun13ChargedHoeOnNonTillableKeepsBuffer,
								"hoe_charged_on_non_tillable_keeps_buffer")
						.fabricId("ElectricHoeGameTest", "tcHoe001Fun13_chargedHoeOnNonTillableKeepsBuffer")
						.ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::faceMatrixAgreesWithVanillaHoe,
								"hoe_face_matrix_agrees_with_vanilla")
						.ticks(20, 40),
				RosterEntry.of(ElectricHoeScenarios::dryProbeMatchesTheTill, "hoe_dry_probe_matches_the_till")
						.ticks(20, 40));

		private Roster() {}
	}

	private ElectricHoeScenarios() {}

	/**
	 * The hoe's slice of the shared EU contract (MOD-364), declared here and nowhere else so that naming
	 * the wrong tool would mean writing {@code ModContent.ELECTRIC_HOE} inside another tool's suite.
	 * {@link ElectricToolEnergyScenarios#energyCaseRosterIsHonest} checks every field of it against the
	 * real item.
	 *
	 * <p>Fixtures: a hay block is the hoe's own {@code #mineable/hoe} domain; stone is the negative; a
	 * moss block (hardness 0.1 in the 26.2 sources, and in {@code #mineable/hoe}) is the soft block that
	 * must still cost full price.
	 */
	public static final ElectricToolEnergyScenarios.ToolCase ENERGY =
			new ElectricToolEnergyScenarios.ToolCase(
					"electric_hoe",
					ModContent.ELECTRIC_HOE,
					() -> ToolConfig.electricHoeEuPerBlock,
					() -> ToolConfig.electricHoeBuffer,
					() -> ToolConfig.electricHoeInputRate,
					() -> Blocks.HAY_BLOCK,
					() -> Blocks.STONE,
					() -> Blocks.MOSS_BLOCK,
					9.0f);

	/** The plot that gets tilled. Air is forced above it: vanilla's grass/dirt tillables are gated on
	 * {@code HoeItem.onlyIfAirAbove}. */
	static final BlockPos SOIL = new BlockPos(1, 2, 1);

	static ItemStack hoe(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTRIC_HOE.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	private static ItemStack diamondTipHoe(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTRIC_HOE_DIAMOND_TIP.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/**
	 * Simulates a right-click of the main-hand stack on the top face of {@link #SOIL}.
	 *
	 * <p><b>Driven through {@code player.gameMode.useItemOn}, not {@code stack.useOn(new UseOnContext(…))}
	 * — the MOD-310 lesson, re-learned here.</b> The direct-{@code useOn} shortcut passes on Fabric and
	 * fails on NeoForge: NeoForge replaces {@code ItemStack#useOn} with its own hook, so the vanilla
	 * tilling chain never ran and the plot stayed dirt. This body runs on both lanes, so it has to take
	 * the full vanilla path — {@code BlockState.useItemOn} → {@code useWithoutItem} → PASS →
	 * {@code ItemStack.useOn} → our {@code useOn} — which behaves the same on both loaders.
	 */
	static InteractionResult useOnSoil(GameTestHelper helper, ServerPlayer player) {
		BlockPos abs = helper.absolutePos(SOIL);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		return player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
				InteractionHand.MAIN_HAND, hit);
	}

	/** Clears the plot and puts {@code soil} there with guaranteed air above it. */
	static void prepareSoil(GameTestHelper helper, Block soil) {
		helper.setBlock(SOIL, soil);
		helper.setBlock(SOIL.above(), Blocks.AIR);
	}

	private static int moistureAtSoil(GameTestHelper helper) {
		BlockState state = helper.getLevel().getBlockState(helper.absolutePos(SOIL));
		if (!state.hasProperty(FarmlandBlock.MOISTURE)) {
			helper.fail("expected farmland at the tilled plot, found " + state.getBlock());
		}
		return state.getValue(FarmlandBlock.MOISTURE);
	}

	private static void assertCorrect(GameTestHelper helper, ItemStack stack, BlockState state, String name,
			boolean expected) {
		if (stack.isCorrectToolForDrops(state) != expected) {
			helper.fail("isCorrectToolForDrops(" + name + ") must be " + expected);
		}
	}

	// ── MOD-378 — diamond-tipped upgrade ────────────────────────────────────────────────────────────

	/**
	 * TC-HOE-001-FUN01 — the upgrade breaks {@code #mineable/hoe} at 10.5 against the base hoe's 9.0, is
	 * strictly faster than the base (so the test reddens if the base is ever bumped to match), still drops
	 * to exactly hand speed when flat, and does NOT change mining tier.
	 *
	 * @implements TC-HOE-001-FUN01 — the diamond-tipped upgrade (MOD-378) breaks hoe-mineable blocks at
	 *     10.5, strictly faster than the base hoe, keeps the hoe tier and still drops to hand speed when
	 *     flat.
	 */
	public static void fun01DiamondTipSpeedAndTier(GameTestHelper helper) {
		Item tip = ModContent.ELECTRIC_HOE_DIAMOND_TIP.get();
		Item base = ModContent.ELECTRIC_HOE.get();
		BlockState hay = Blocks.HAY_BLOCK.defaultBlockState();

		float charged = tip.getDestroySpeed(diamondTipHoe(ToolConfig.electricHoeBuffer), hay);
		if (charged != 10.5f) {
			helper.fail("a charged diamond-tipped hoe must break hay at 10.5, got " + charged);
		}
		float baseSpeed = base.getDestroySpeed(hoe(ToolConfig.electricHoeBuffer), hay);
		if (!(charged > baseSpeed)) {
			helper.fail("the upgrade must out-cut the base hoe, got " + charged + " vs base " + baseSpeed);
		}
		float flat = tip.getDestroySpeed(diamondTipHoe(0), hay);
		if (flat != 1.0f) {
			helper.fail("a flat diamond-tipped hoe must break at exactly hand speed 1.0, got " + flat);
		}

		// The tier is unchanged: it is still a hoe, and a hoe is not a pickaxe.
		ItemStack chargedStack = diamondTipHoe(ToolConfig.electricHoeBuffer);
		assertCorrect(helper, chargedStack, hay, "hay_block", true);
		assertCorrect(helper, chargedStack, Blocks.STONE.defaultBlockState(), "stone", false);
		helper.succeed();
	}

	/**
	 * TC-HOE-001-FUN02 — tilling with the upgrade leaves the fresh plot at full moisture. Asserted against
	 * {@link FarmlandBlock#MAX_MOISTURE} rather than the literal 7, so the test tracks vanilla instead of
	 * freezing a number vanilla owns.
	 *
	 * @implements TC-HOE-001-FUN02 — a plot tilled with the upgrade comes out at full farmland moisture,
	 *     with no water anywhere near it.
	 */
	public static void fun02DiamondTipTillLeavesPlotWatered(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, diamondTipHoe(ToolConfig.electricHoeBuffer));
		prepareSoil(helper, Blocks.DIRT);

		useOnSoil(helper, player);

		int moisture = moistureAtSoil(helper);
		if (moisture != FarmlandBlock.MAX_MOISTURE) {
			helper.fail("a plot tilled by the upgrade must start at moisture "
					+ FarmlandBlock.MAX_MOISTURE + ", got " + moisture);
		}
		helper.succeed();
	}

	/**
	 * TC-HOE-001-FUN03 — negative control for FUN02: the BASE hoe waters nothing. Vanilla farmland is
	 * created dry ({@code MOISTURE = 0} is {@code FarmlandBlock}'s registered default), so if this ever
	 * goes green with a non-zero reading, the perk has leaked into the base class and FUN02 alone would
	 * never have noticed.
	 *
	 * @implements TC-HOE-001-FUN03 — the BASE hoe has no irrigation: the plot it tills stays dry, which is
	 *     what makes FUN02 a real assertion rather than a restatement of vanilla.
	 */
	public static void fun03BaseHoeLeavesPlotDry(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, hoe(ToolConfig.electricHoeBuffer));
		prepareSoil(helper, Blocks.DIRT);

		useOnSoil(helper, player);

		int moisture = moistureAtSoil(helper);
		if (moisture != 0) {
			helper.fail("a plot tilled by the BASE hoe must stay dry (moisture 0), got " + moisture);
		}
		helper.succeed();
	}

	/**
	 * TC-HOE-001-FUN04 — a flat upgrade cannot water existing farmland.
	 *
	 * <p>This is the guard for a real 26.2 trap, not a hypothetical one: {@code InteractionResult.CONSUME}
	 * is an {@code InteractionResult.Success}, so {@code consumesAction()} returns {@code true} for it —
	 * and {@code ElectricHoeItem.useOn} returns exactly {@code CONSUME} on its "not enough charge" path.
	 * An implementation that watered on {@code consumesAction()} alone would therefore hand a player with
	 * a dead battery unlimited free irrigation of any plot they click. The upgrade instead only waters a
	 * block that was not farmland before the click, which is what this asserts.
	 *
	 * <p>Since MOD-389 the click in this scenario also stops one step earlier — farmland is not tillable,
	 * so {@code useOn} answers {@code PASS} before the charge gate is even reached. The assertion is kept
	 * exactly as it was: it must hold whichever layer stops the exploit, and it is the only one of the two
	 * that survives a future reordering of the gate.
	 *
	 * @implements TC-HOE-001-FUN04 — a flat upgrade cannot water already-existing farmland, closing the
	 *     free-irrigation hole that gating on {@code consumesAction()} alone would have opened.
	 */
	public static void fun04FlatUpgradeCannotWaterExistingFarmland(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, diamondTipHoe(0));
		prepareSoil(helper, Blocks.FARMLAND);

		if (moistureAtSoil(helper) != 0) {
			helper.fail("fixture error: freshly placed farmland must start dry");
		}

		useOnSoil(helper, player);

		int moisture = moistureAtSoil(helper);
		if (moisture != 0) {
			helper.fail("a flat upgrade must not water existing farmland, moisture became " + moisture);
		}
		helper.succeed();
	}

	// ── MOD-389 — a flat hoe must not swallow every right-click ─────────────────────────────────────

	/**
	 * TC-HOE-001-NEG01 — a flat hoe right-clicking a block no hoe can till answers {@code PASS}.
	 *
	 * <p>The shipped defect this pins: the charge gate used to run <b>before</b> anything looked at the
	 * block, so an empty hoe returned {@code CONSUME} for a click on stone — which in 26.2 is an
	 * {@code InteractionResult.Success}, i.e. "handled". The player got a red "not enough charge" line
	 * they never asked for, and the click never reached the off-hand: no block placed, no food eaten.
	 *
	 * <p>Asserting on the {@code InteractionResult} <b>is</b> the off-hand assertion — that result is
	 * exactly what {@code ServerPlayerGameMode.useItemOn} returns to the interaction chain, and
	 * {@code PASS} is the only value that lets the chain continue. Run against the pre-MOD-389 code this
	 * body fails on the first check.
	 *
	 * @implements TC-HOE-001-NEG01 — a flat hoe right-clicking a block no hoe can till returns PASS, so it
	 *     neither shouts "not enough charge" at a player who was not tilling nor swallows the click that
	 *     the off-hand item was meant to get (MOD-389).
	 */
	public static void neg01FlatHoeOnNonTillableDoesNotSwallowClick(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, hoe(0));
		prepareSoil(helper, Blocks.STONE);

		InteractionResult result = useOnSoil(helper, player);

		if (result != InteractionResult.PASS) {
			helper.fail("a flat hoe clicking stone must return PASS so the off-hand still runs, got " + result);
		}
		helper.assertBlockPresent(Blocks.STONE, SOIL);
		if (ItemEnergy.get(player.getMainHandItem()) != 0) {
			helper.fail("fixture error: the hoe under test must be flat");
		}
		helper.succeed();
	}

	/**
	 * TC-HOE-001-FUN05 — the other half of NEG01: on a block a hoe <i>can</i> till, a flat hoe still
	 * refuses loudly. {@code CONSUME} is the "handled, and I told you why" answer, and the plot must stay
	 * dirt.
	 *
	 * <p>Without this, MOD-389 could have been "fixed" by returning {@code PASS} everywhere when flat —
	 * green NEG01, and the player silently loses the only feedback that says the hoe needs charging.
	 *
	 * @implements TC-HOE-001-FUN05 — on a block that IS tillable, a flat hoe still refuses with CONSUME and
	 *     leaves the plot untilled, so the MOD-389 fix cannot degenerate into "always PASS".
	 */
	public static void fun05FlatHoeOnTillableStillRefuses(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, hoe(0));
		prepareSoil(helper, Blocks.DIRT);

		InteractionResult result = useOnSoil(helper, player);

		if (!UseResults.isNoSwingConsume(result)) {
			helper.fail("a flat hoe clicking tillable dirt must return CONSUME (refusal + message), got " + result);
		}
		helper.assertBlockPresent(Blocks.DIRT, SOIL);
		helper.succeed();
	}

	// ── MOD-364 — paid tilling, the one path where the hoe actually spends EU ────────────────────────

	/**
	 * TC-HOE-001-FUN11 — a successful till drains exactly {@code electricHoeTillEuCost}.
	 *
	 * <p>This is the single most important gap MOD-364 closed. Tilling is the only thing the hoe does in
	 * normal play that costs energy at all — {@code mineBlock} charges for breaking hay and leaves, but
	 * nobody buys an electric hoe to break hay — and until now <b>no test on either loader read the buffer
	 * after a till that worked</b>. TC-HOE-001-FUN02 tills, but with a full buffer it never looks at the
	 * charge; TC-HOE-001-FUN05 and NEG01 only cover the two refusal paths. An
	 * {@code ItemEnergy.spend(...)} deleted from
	 * {@link dev.alaindustrial.item.tool.ElectricHoeItem#useOn} passed every gate in the repo, which is
	 * exactly the defect the task description calls "the hoe never spends a single EU in normal play".
	 *
	 * <p>Both halves are asserted: the plot really became farmland (otherwise "the charge did not move"
	 * would be true of a hoe that simply did nothing), and the charge moved by exactly the till cost.
	 *
	 * @implements TC-HOE-001-FUN11 — a successful till drains exactly electricHoeTillEuCost; the only path
	 *     where the hoe spends EU in normal play, and it was covered by nothing (MOD-364).
	 */
	public static void fun11TillDrainsExactlyTillCost(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		long buffer = ToolConfig.electricHoeBuffer;
		player.setItemInHand(InteractionHand.MAIN_HAND, hoe(buffer));
		prepareSoil(helper, Blocks.DIRT);
		helper.assertBlockPresent(Blocks.DIRT, SOIL);

		InteractionResult result = useOnSoil(helper, player);

		if (!result.consumesAction()) {
			helper.fail("a charged hoe tilling dirt must report a consumed action, got " + result);
		}
		helper.assertBlockPresent(Blocks.FARMLAND, SOIL);
		long left = ItemEnergy.get(player.getMainHandItem());
		long expected = buffer - ToolConfig.electricHoeTillEuCost;
		if (left != expected) {
			helper.fail("tilling must drain exactly electricHoeTillEuCost (" + ToolConfig.electricHoeTillEuCost
					+ "), charge went " + buffer + " → " + left + ", expected " + expected);
		}
		helper.succeed();
	}

	/**
	 * TC-HOE-001-FUN12 — one EU below the till cost the hoe refuses, and the refusal is free.
	 *
	 * <p>The boundary partner of FUN11. TC-HOE-001-FUN05 already covers a hoe at zero, but zero is not a
	 * boundary: a gate written {@code > cost} instead of {@code >= cost}, or one comparing against the
	 * wrong config key, is still red at zero and green at {@code cost - 1}. This case is the only one that
	 * moves when the comparison is off by one. It also asserts what FUN05 does not — that the buffer is
	 * untouched by a refusal, so a hoe cannot be charged for work it declined to do.
	 *
	 * @implements TC-HOE-001-FUN12 — one EU below the till cost the hoe refuses with CONSUME, leaves the
	 *     plot dirt and does not touch the buffer; the boundary FUN05's zero-charge case cannot see
	 *     (MOD-364).
	 */
	public static void fun12TillRefusedJustBelowCost(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		long below = ToolConfig.electricHoeTillEuCost - 1;
		player.setItemInHand(InteractionHand.MAIN_HAND, hoe(below));
		prepareSoil(helper, Blocks.DIRT);
		helper.assertBlockPresent(Blocks.DIRT, SOIL);

		InteractionResult result = useOnSoil(helper, player);

		if (!UseResults.isNoSwingConsume(result)) {
			helper.fail("a hoe one EU below the till cost (" + below + ") must refuse with CONSUME, got " + result);
		}
		helper.assertBlockPresent(Blocks.DIRT, SOIL);
		long left = ItemEnergy.get(player.getMainHandItem());
		if (left != below) {
			helper.fail("a refused till must not touch the buffer, charge went " + below + " → " + left);
		}
		helper.succeed();
	}

	/**
	 * TC-HOE-001-FUN13 — a <b>charged</b> hoe clicking a block no hoe can till passes the click on and
	 * keeps every EU.
	 *
	 * <p>The charged twin of NEG01, and the reason NEG01 alone is not enough: NEG01 runs a flat hoe, so
	 * its {@code charge == 0} check is a fixture assertion, not a statement about spending. Only a full
	 * buffer can show that the tool did not pay for a click it correctly declined — which is what a
	 * spend moved above the {@code wouldTill} gate, or one keyed on the wrong result, would break.
	 *
	 * @implements TC-HOE-001-FUN13 — a CHARGED hoe clicking a block no hoe can till returns PASS and keeps
	 *     every EU; the charged twin of NEG01, whose flat hoe makes its charge check a fixture assertion
	 *     rather than a statement about spending (MOD-364).
	 */
	public static void fun13ChargedHoeOnNonTillableKeepsBuffer(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		long buffer = ToolConfig.electricHoeBuffer;
		player.setItemInHand(InteractionHand.MAIN_HAND, hoe(buffer));
		prepareSoil(helper, Blocks.STONE);
		helper.assertBlockPresent(Blocks.STONE, SOIL);

		InteractionResult result = useOnSoil(helper, player);

		if (result != InteractionResult.PASS) {
			helper.fail("a charged hoe clicking stone must return PASS so the off-hand still runs, got " + result);
		}
		helper.assertBlockPresent(Blocks.STONE, SOIL);
		long left = ItemEnergy.get(player.getMainHandItem());
		if (left != buffer) {
			helper.fail("a click the hoe declined must cost nothing, charge went " + buffer + " → " + left);
		}
		helper.succeed();
	}

	// ── MOD-704 — characterization before the right-click seam: the face matrix and the dry probe ────

	/** The matrix soils: the five a hoe converts, farmland (already tilled) and stone (never). */
	private static final List<Block> MATRIX_SOILS = List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH,
			Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.FARMLAND, Blocks.STONE);

	/** The top, one side and the bottom: the three faces the tilling rules can treat differently. */
	static final List<Direction> MATRIX_FACES = List.of(Direction.UP, Direction.NORTH, Direction.DOWN);

	/** Same as {@link #useOnSoil}, from any face of {@link #SOIL}. */
	static InteractionResult useOnSoilFace(GameTestHelper helper, ServerPlayer player, Direction face) {
		BlockPos abs = helper.absolutePos(SOIL);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
		return player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
				InteractionHand.MAIN_HAND, hit);
	}

	private static Block soilBlock(GameTestHelper helper) {
		return helper.getLevel().getBlockState(helper.absolutePos(SOIL)).getBlock();
	}

	/**
	 * The block a hoe leaves on every lane, for the faces where all four lanes agree: UP and a side face
	 * convert the five soils, DOWN converts only rooted dirt (its rule has no face condition on any
	 * line or loader). {@code null} = no literal here: the answer depends on the lane (the DOWN face of
	 * the four air-above soils — refused by vanilla's rules on 26.2 Fabric and on 26.3, converted by the
	 * NeoForge 26.2 {@code HOE_TILL} copy, which has no face check), and only the vanilla oracle pins it.
	 */
	private static Block literalTill(Block soil, Direction face) {
		if (soil == Blocks.FARMLAND || soil == Blocks.STONE) {
			return soil;
		}
		if (soil == Blocks.ROOTED_DIRT) {
			return Blocks.DIRT;
		}
		if (face == Direction.DOWN) {
			return null;
		}
		return soil == Blocks.COARSE_DIRT ? Blocks.DIRT : Blocks.FARMLAND;
	}

	/** Puts {@code soil} on the plot (air above) and clicks it from {@code face} with {@code stack}. */
	private static InteractionResult clickFresh(GameTestHelper helper, ServerPlayer player, ItemStack stack,
			Block soil, Direction face) {
		prepareSoil(helper, soil);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return useOnSoilFace(helper, player, face);
	}

	/**
	 * MOD-704 batch 0 — the face matrix of the charged hoe, base and diamond tip, against a vanilla diamond
	 * hoe on the identical fixture, on this lane.
	 *
	 * @implements MOD-704-RC01 — for every soil of the matrix (dirt, grass, dirt path, coarse dirt, rooted
	 *     dirt, farmland, stone) and every face (UP, a side, DOWN), a charged electric hoe — base and
	 *     diamond tip — leaves the same block a vanilla diamond hoe leaves on the same fixture, matches the
	 *     lane-independent literal where all lanes agree, and spends exactly the till cost when (and only
	 *     when) the block changed. The DOWN answer for the four air-above soils is pinned relative to
	 *     vanilla only: it differs between lanes today (see research.md of MOD-704).
	 */
	public static void faceMatrixAgreesWithVanillaHoe(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		long buffer = ToolConfig.electricHoeBuffer;
		List<ItemStack> tools = List.of(hoe(buffer), diamondTipHoe(buffer));
		for (Block soil : MATRIX_SOILS) {
			for (Direction face : MATRIX_FACES) {
				clickFresh(helper, player, new ItemStack(Items.DIAMOND_HOE), soil, face);
				Block vanilla = soilBlock(helper);
				Block literal = literalTill(soil, face);
				if (literal != null && vanilla != literal) {
					helper.fail("fixture error: a vanilla diamond hoe on " + soil + " from " + face + " left "
							+ vanilla + ", expected " + literal);
					return;
				}
				for (ItemStack template : tools) {
					ItemStack tool = template.copy();
					InteractionResult result = clickFresh(helper, player, tool, soil, face);
					Block after = soilBlock(helper);
					String cell = tool.getItem() + " on " + soil + " from " + face;
					if (after != vanilla) {
						helper.fail(cell + " left " + after + ", a vanilla diamond hoe left " + vanilla);
						return;
					}
					boolean changed = after != soil;
					long spent = buffer - ItemEnergy.get(player.getMainHandItem());
					long expectedSpend = changed ? ToolConfig.electricHoeTillEuCost : 0;
					if (spent != expectedSpend) {
						helper.fail(cell + " spent " + spent + " EU, expected " + expectedSpend);
						return;
					}
					if (changed != result.consumesAction()) {
						helper.fail(cell + " answered " + result + " although the block "
								+ (changed ? "changed" : "did not change"));
						return;
					}
				}
			}
		}
		helper.succeed();
	}

	/**
	 * MOD-704 batch 0 — the dry probe, observed through the outcome of {@code useOn}: a FLAT hoe asks
	 * "would a hoe convert this?" before its charge gate, so its answer — refusal (a consumed click that
	 * changes nothing) or pass — must be exactly the answer the till itself gives.
	 *
	 * @implements MOD-704-RC02 — over the whole face matrix, a flat electric hoe (base and diamond tip)
	 *     passes the click on exactly where a charged one changes nothing, refuses (consumed, block
	 *     untouched) exactly where a charged one converts the block, and never writes the world or the
	 *     buffer either way.
	 */
	public static void dryProbeMatchesTheTill(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		long buffer = ToolConfig.electricHoeBuffer;
		List<ItemStack> charged = List.of(hoe(buffer), diamondTipHoe(buffer));
		List<ItemStack> flat = List.of(hoe(0), diamondTipHoe(0));
		for (Block soil : MATRIX_SOILS) {
			for (Direction face : MATRIX_FACES) {
				for (int i = 0; i < charged.size(); i++) {
					clickFresh(helper, player, charged.get(i).copy(), soil, face);
					boolean tills = soilBlock(helper) != soil;
					ItemStack flatTool = flat.get(i).copy();
					InteractionResult probe = clickFresh(helper, player, flatTool, soil, face);
					String cell = "flat " + flatTool.getItem() + " on " + soil + " from " + face;
					if (soilBlock(helper) != soil) {
						helper.fail(cell + " changed the block to " + soilBlock(helper));
						return;
					}
					if (ItemEnergy.get(player.getMainHandItem()) != 0) {
						helper.fail(cell + " gained charge");
						return;
					}
					boolean refused = probe != InteractionResult.PASS;
					if (refused != tills) {
						helper.fail(cell + " answered " + probe + " but a charged one "
								+ (tills ? "tills" : "does not till") + " this cell");
						return;
					}
					if (refused && !probe.consumesAction()) {
						helper.fail(cell + " refused with a non-consuming " + probe);
						return;
					}
				}
			}
		}
		helper.succeed();
	}

	// ── MOD-364 — the base tool's EU contract (shared forms, hoe parameters) ─────────────────────────

	/**
	 * TC-HOE-001-FUN06 — accepted by the Battery Box charge slot and charged at its intake rate.
	 *
	 * @implements TC-HOE-001-FUN06 — the hoe is accepted by both Battery Box charge-slot filters and
	 *     charges there at min(LV ceiling, its own intake rate) (MOD-364).
	 */
	public static void fun06ChargeInBatteryBox(GameTestHelper helper) {
		ElectricToolEnergyScenarios.chargeInBatteryBox(helper, ENERGY);
	}

	/**
	 * TC-HOE-001-FUN07 — breaking one hay block drains exactly {@code electricHoeEuPerBlock}.
	 *
	 * @implements TC-HOE-001-FUN07 — breaking one hay block with a charged hoe drains exactly
	 *     electricHoeEuPerBlock (MOD-364).
	 */
	public static void fun07DrainOnMineBlock(GameTestHelper helper) {
		ElectricToolEnergyScenarios.drainOnMineBlock(helper, ENERGY);
	}

	/**
	 * TC-HOE-001-FUN08 — one EU below the cost it breaks for free, at exactly hand speed.
	 *
	 * @implements TC-HOE-001-FUN08 — one EU below the per-block cost the hoe breaks for free and at
	 *     exactly hand speed 1.0f on its own domain block (MOD-364).
	 */
	public static void fun08NoDrainBelowCost(GameTestHelper helper) {
		ElectricToolEnergyScenarios.noDrainBelowCost(helper, ENERGY);
	}

	/**
	 * TC-HOE-001-FUN09 — a torch (hardness 0.0) is free, a moss block (0.1) is not.
	 *
	 * @implements TC-HOE-001-FUN09 — a zero-hardness block costs nothing, while a moss block (0.1) costs
	 *     the full per-block drain (MOD-364).
	 */
	public static void fun09ZeroHardnessFreeMossCosts(GameTestHelper helper) {
		ElectricToolEnergyScenarios.zeroHardnessFreeSoftBlockCosts(helper, ENERGY);
	}

	/**
	 * TC-HOE-001-FUN10 — 9.0 on hoe blocks while charged, 1.0f flat, drops kept either way.
	 *
	 * @implements TC-HOE-001-FUN10 — 9.0 on hoe blocks while charged, exactly 1.0f one EU below the cost,
	 *     drops kept either way and refused on a foreign block (MOD-364).
	 */
	public static void fun10SpeedAndDrops(GameTestHelper helper) {
		ElectricToolEnergyScenarios.speedAndDrops(helper, ENERGY);
	}

	/**
	 * TC-HOE-001-PER01 — charge survives a copy, 0 EU drops the component, writes clamp.
	 *
	 * @implements TC-HOE-001-PER01 — charge survives a stack copy, 0 EU removes the component, and writes
	 *     clamp at capacity (MOD-364).
	 */
	public static void per01ChargeRoundTrip(GameTestHelper helper) {
		ElectricToolEnergyScenarios.chargeRoundTrip(helper, ENERGY);
	}
}
