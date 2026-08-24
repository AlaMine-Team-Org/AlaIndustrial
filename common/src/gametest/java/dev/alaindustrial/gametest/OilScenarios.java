package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.OilLoggedBlock;
import dev.alaindustrial.fluid.FluidImmersion;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.item.fluid.ItemFluid;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

/**
 * Loader-neutral gametest bodies for the oil fluid (MOD-238, suite TC-OIL-001). Same pattern as
 * {@link CoreFluidScenarios}/{@link CapsuleScenarios}: plain {@code Consumer<GameTestHelper>} bodies
 * over vanilla {@code GameTestHelper} + neutral content ({@link ModContent}), wrapped by the Fabric
 * {@code OilGameTest} suite and registered on the NeoForge {@code gameTestServer} lane via
 * {@code NeoForgeGameTests} — both loaders exercise the SAME oil fluid/block registered through
 * their own registration paths (eager {@code ModFluids} vs {@code ModFluidsNeoForge} with its
 * {@code FluidType} subclasses).
 *
 * <p>Covers: bucket place/pick-up round trip, the vacuum-capsule world exchange through the REAL
 * {@code ServerPlayerGameMode.useItem} routing (the {@code FlowingFluid} gate in
 * {@code VacuumCapsuleItem#use}), the pump draining a small oil lake into a portable tank, the
 * finite-deposit guarantee ({@code canConvertToSource=false} — a gap between two sources never
 * becomes a third source, self-calibrated against a water control rig that DOES convert), the
 * {@code Config.oilBurns} gate driven by a real flint-and-steel click through each loader's
 * block-use seam (ON: the cell becomes fire and the lighter loses durability; OFF: the same click
 * falls through to vanilla and changes nothing), the burn walking a pool, the guarantee that a LAVA
 * neighbour never ignites oil (worldgen puts deposits against lava lakes), the dispenser emptying an
 * oil bucket as well as filling one, and the viscous spread profile
 * (drop-off 2 → flowing amounts 6/4/2 and a hard stop at distance 3, where water reaches 7 with
 * amount 7/6/5/...).
 *
 * <p>Fluid-flow scenarios rely on REAL world ticking (oil's tick delay is 40), so they assert via
 * {@code runAtTickTime} rather than driving block entities synchronously. Every rig is a closed
 * stone basin so the analysis is deterministic: no slope-find hole hunting, no fluid escaping the
 * plot.
 */
public final class OilScenarios {

	private OilScenarios() {
	}

	private static BlockState oilSource() {
		return ModContent.OIL_BLOCK.get().defaultBlockState();
	}

	private static boolean isOil(FluidState state) {
		return !state.isEmpty() && state.getType().isSame(ModContent.OIL.get());
	}

	/**
	 * Build a closed stone basin: a floor at rel y=1 under the interior AND the surrounding ring, plus
	 * a one-block-high wall ring at rel y=2 around the interior rectangle. Fluids placed at y=2 inside
	 * can only flow within the interior — no slope-find holes, nothing leaks into the plot.
	 */
	private static void basin(GameTestHelper helper, int x1, int z1, int x2, int z2) {
		for (int x = x1 - 1; x <= x2 + 1; x++) {
			for (int z = z1 - 1; z <= z2 + 1; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				boolean interior = x >= x1 && x <= x2 && z >= z1 && z <= z2;
				if (!interior) {
					helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
				}
			}
		}
	}

	// ── FUN01: bucket round trip — place a source from the oil bucket, scoop it back ─────────────

	/**
	 * The oil bucket places a genuine oil SOURCE ({@code createLegacyBlock} → {@code OilLiquidBlock},
	 * LEVEL=0), and scooping it back through the vanilla {@link BucketPickup} path returns a full oil
	 * bucket and leaves the cell fluid-free. Exercises the two oil-specific wirings the vanilla bucket
	 * dispatch rides on: {@code OilFluid#createLegacyBlock} and {@code Fluid#getBucket}.
	 */
	public static void fun01BucketPlaceAndPickup(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos cell = new BlockPos(2, 2, 2);
		basin(helper, 2, 2, 2, 2);
		BlockPos abs = helper.absolutePos(cell);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);

		BucketItem bucket = (BucketItem) ModContent.OIL_BUCKET.get();
		// MOD-498 — the 4-argument BucketItem#emptyContents is NOT deprecated by vanilla; NeoForge's patch
		// deprecates it in favour of "the ItemStack sensitive version", a 5-argument overload taking the
		// container stack that vanilla does not declare at all. This scenario lives in shared gametest code
		// compiled for Fabric too, so only the 4-argument form exists on both loaders — and the test places
		// straight from the item with no container stack to pass, which is what the extra argument is for.
		@SuppressWarnings("deprecation")
		boolean poured = bucket.emptyContents(player, level, abs, null);
		if (!poured) {
			helper.fail("oil bucket emptyContents refused to place into an empty basin cell");
			return;
		}
		FluidState placed = level.getFluidState(abs);
		if (!placed.isSourceOfType(ModContent.OIL.get())) {
			helper.fail("bucket must place an oil SOURCE, got " + placed.getType());
			return;
		}
		if (level.getBlockState(abs).getBlock() != ModContent.OIL_BLOCK.get()) {
			helper.fail("placed block must be the oil liquid block, got " + level.getBlockState(abs));
			return;
		}

		BlockState state = level.getBlockState(abs);
		ItemStack taken = ((BucketPickup) state.getBlock()).pickupBlock(player, level, abs, state);
		if (!taken.is(ModContent.OIL_BUCKET.get())) {
			helper.fail("pickupBlock must return a full oil bucket, got " + taken);
			return;
		}
		if (!level.getFluidState(abs).isEmpty()) {
			helper.fail("cell must be fluid-free after pickup, got " + level.getFluidState(abs).getType());
			return;
		}
		helper.succeed();
	}

	// ── FUN02: vacuum capsule world exchange through the REAL useItem routing ─────────────────────

	/**
	 * An empty vacuum capsule right-clicked at an oil source (through the real
	 * {@code ServerPlayerGameMode.useItem} routing, i.e. the raytrace + hand-swap path a player
	 * actually uses) picks the source up — proving oil passes the {@code fluid instanceof
	 * FlowingFluid} gate in {@code VacuumCapsuleItem#use} — and the filled capsule then places the
	 * source back and swaps to empty. Survival on purpose: {@code ItemUtils.createFilledResult} keeps
	 * the original stack in creative (see the creative-filled-result gotcha).
	 */
	public static void fun02CapsulePickupAndPlace(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos cell = new BlockPos(2, 2, 2);
		basin(helper, 2, 2, 2, 2);
		BlockPos abs = helper.absolutePos(cell);
		level.setBlockAndUpdate(abs, oilSource());

		// Stand two blocks above the oil column looking straight down (pitch 90): the SOURCE_ONLY
		// raytrace in VacuumCapsuleItem#use hits the oil source well within block interaction range.
		ServerPlayer player = survivalPlayer(helper);
		Vec3 stand = helper.absoluteVec(new Vec3(2.5, 4.0, 2.5));
		player.snapTo(stand.x, stand.y, stand.z, 0.0F, 90.0F);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.VACUUM_CAPSULE.get()));

		InteractionResult pickup = player.gameMode.useItem(player, level,
				player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND);
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean handFilledOil = hand.is(ModContent.FILLED_VACUUM_CAPSULE.get())
				&& ItemFluid.get(hand) == ModContent.OIL.get();
		if (!pickup.consumesAction() || !handFilledOil || !level.getFluidState(abs).isEmpty()) {
			helper.fail("capsule world pickup: result=" + pickup + " hand=" + hand
					+ " worldFluid=" + level.getFluidState(abs).getType());
			return;
		}

		// Same stance, filled capsule: the Fluid.NONE raytrace hits the basin floor, the offset cell is
		// the one we just emptied — the capsule must place the oil source back and swap to empty.
		InteractionResult place = player.gameMode.useItem(player, level,
				player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND);
		hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean sourceBack = level.getFluidState(abs).isSourceOfType(ModContent.OIL.get());
		if (!place.consumesAction() || !hand.is(ModContent.VACUUM_CAPSULE.get()) || !sourceBack) {
			helper.fail("capsule world place: result=" + place + " hand=" + hand
					+ " worldFluid=" + level.getFluidState(abs).getType());
			return;
		}
		helper.succeed();
	}

	// ── FUN03: the pump drains a small oil lake into a portable fluid tank ────────────────────────

	/**
	 * A three-source oil "lake" in front of the pump is drained bucket by bucket (one per
	 * {@code pumpEuPerBucket} charge, one per scan cooldown) and pushed into an adjacent portable
	 * fluid tank. The pump ticks NATURALLY here — no synchronous driving on purpose: the pump always
	 * drains the CLOSEST source first (the block right in front of it), and only real world ticking
	 * lets the remaining lake FLOW back into that gap (viscous, 40 ticks per step) so the BFS can
	 * traverse the flowing oil to the farther sources. That is the exact in-game draining loop; a
	 * synchronous drive leaves the front block permanently empty and stalls after one bucket (the
	 * first run of this test proved it).
	 */
	public static void fun03PumpDrainsOilLakeIntoTank(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pumpRel = new BlockPos(2, 2, 2);
		BlockPos[] sources = { new BlockPos(3, 2, 2), new BlockPos(4, 2, 2), new BlockPos(5, 2, 2) };
		BlockPos tankRel = new BlockPos(1, 2, 2);

		basin(helper, 2, 2, 5, 2);
		// The west ring wall becomes the sink: the pump (facing EAST at the lake) pushes its tank out
		// of every non-FACING face, including WEST into this portable tank.
		helper.setBlock(tankRel, ModContent.FLUID_TANK.get());
		helper.setBlock(pumpRel, ModContent.PUMP.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		for (BlockPos src : sources) {
			level.setBlockAndUpdate(helper.absolutePos(src), oilSource());
		}

		PumpBlockEntity pump = helper.getBlockEntity(pumpRel, PumpBlockEntity.class);
		FluidTankBlockEntity tank = helper.getBlockEntity(tankRel, FluidTankBlockEntity.class);
		if (pump == null || tank == null) {
			helper.fail("pump or fluid tank block entity missing after placement");
			return;
		}
		// EU for exactly three acquisitions; failed scans do not spend EU, so no top-up is needed.
		pump.getEnergyStorage().setAmountUntracked((long) sources.length * Config.pumpEuPerBucket);

		// Mid-state at tick 30: the first bucket (scan at ~tick 1) is already through the pump and in
		// the tank, and the lake is only PARTIALLY drained — at least one source must remain.
		helper.runAtTickTime(30, () -> {
			boolean tankReceivingOil = tank.fluidTank.amount >= FluidAmounts.BUCKET
					&& tank.fluidTank.fluid.is(ModContent.OIL.get());
			int sourcesLeft = countOilSources(helper, level, sources);
			if (!tankReceivingOil || sourcesLeft < 1) {
				helper.fail("mid-state at tick 30: tank=" + tank.fluidTank.amount + " mB of "
						+ tank.fluidTank.fluid + ", sourcesLeft=" + sourcesLeft
						+ " (expected >= 1 bucket of oil already pushed and a partially drained lake)");
			}
		});
		// Scans run every pumpScanCooldownTicks (20); with the 40-tick reflow cadence (MOD-248) the
		// third bucket lands around tick 100 worst-case. Tick 280 leaves ample margin.
		helper.runAtTickTime(280, () -> {
			boolean lakeDrained = countOilSources(helper, level, sources) == 0;
			boolean tankHoldsLake = tank.fluidTank.amount == FluidAmounts.BUCKET * sources.length
					&& tank.fluidTank.fluid.is(ModContent.OIL.get());
			boolean pumpEmptied = pump.fluidTank.amount == 0;
			if (!(lakeDrained && tankHoldsLake && pumpEmptied)) {
				helper.fail("oil lake drain: sourcesLeft=" + countOilSources(helper, level, sources)
						+ " tank=" + tank.fluidTank.amount + " mB of " + tank.fluidTank.fluid
						+ " pumpTank=" + pump.fluidTank.amount);
				return;
			}
			helper.succeed();
		});
	}

	private static int countOilSources(GameTestHelper helper, ServerLevel level, BlockPos[] cells) {
		int n = 0;
		for (BlockPos cell : cells) {
			if (level.getFluidState(helper.absolutePos(cell)).isSourceOfType(ModContent.OIL.get())) {
				n++;
			}
		}
		return n;
	}

	// ── NEG01: finite deposit — a gap between two sources never becomes a source ──────────────────

	/**
	 * Two oil sources with a one-block gap on a closed trench: the gap fills with FLOWING oil but
	 * never converts to a source ({@code canConvertToSource=false} — the finite-deposit guarantee of
	 * the visible-lakes worldgen model). Self-calibrating: an identical water trench in the same rig
	 * MUST convert its gap to a source by the same deadline, proving the geometry does trigger
	 * vanilla {@code getNewLiquid} source conversion — so flipping oil's {@code canConvertToSource}
	 * to {@code true} makes the oil assertion fail rather than the whole test being vacuous.
	 */
	public static void neg01GapNeverBecomesSource(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// Oil trench at z=2, water control trench at z=5; sources at the ends, gap in the middle.
		basin(helper, 2, 2, 4, 2);
		basin(helper, 2, 5, 4, 5);
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, 2, 2)), oilSource());
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(4, 2, 2)), oilSource());
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, 2, 5)), Blocks.WATER.defaultBlockState());
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(4, 2, 5)), Blocks.WATER.defaultBlockState());

		BlockPos oilGapAbs = helper.absolutePos(new BlockPos(3, 2, 2));
		BlockPos waterGapAbs = helper.absolutePos(new BlockPos(3, 2, 5));
		// Oil tick delay is 40 (MOD-248); by tick 140 both trenches have flowed several times over.
		helper.runAtTickTime(140, () -> {
			if (!level.getFluidState(waterGapAbs).isSourceOfType(Fluids.WATER)) {
				helper.fail("control rig broken: the water gap must have converted to a source"
						+ " — the geometry no longer triggers source conversion at all");
				return;
			}
			FluidState oilGap = level.getFluidState(oilGapAbs);
			if (!isOil(oilGap)) {
				helper.fail("oil must flow into the gap between the two sources, got " + oilGap.getType());
				return;
			}
			if (oilGap.isSource()) {
				helper.fail("the oil gap became a SOURCE — canConvertToSource must stay false"
						+ " (finite deposit guarantee)");
				return;
			}
			helper.succeed();
		});
	}

	// ── FUN04: the oilBurns config gate, driven by a real flint-and-steel click ───────────────────

	/**
	 * Force {@link Config#oilBurns} for the duration of one test and make the restore survive EVERY
	 * exit path, including a timeout.
	 *
	 * <p>{@code Config} is global mutable state shared by a concurrently running gametest batch, so a
	 * test that leaves the flag flipped poisons every later oil test. A {@code finally} in the
	 * asserting lambda is not enough: if the test times out, that lambda never runs at all.
	 * {@code runBeforeTestEnd} schedules the restore at {@code timeout - 1}, which is precisely the
	 * tick a hung test still reaches — so the flag is put back either by the explicit restore on the
	 * success/fail path or by this backstop. Restoring twice is harmless (it writes the same value).
	 */
	private static boolean forceOilBurns(GameTestHelper helper, boolean value) {
		boolean saved = Config.oilBurns;
		helper.runBeforeTestEnd(() -> Config.oilBurns = saved);
		Config.oilBurns = value;
		return saved;
	}

	/** Right-click the top face of {@code floorRel} with {@code stack} through the real interaction path. */
	private static InteractionResult useOnTopFace(GameTestHelper helper, ServerPlayer player,
			ItemStack stack, BlockPos floorRel) {
		BlockPos floorAbs = helper.absolutePos(floorRel);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(
				Vec3.atCenterOf(floorAbs).add(0.0, 0.5, 0.0), Direction.UP, floorAbs, false);
		return player.gameMode.useItemOn(player, helper.getLevel(),
				player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
	}

	/**
	 * The {@code oilBurns} gate, exercised through the interaction a player actually performs: a
	 * flint and steel right-clicked at an oil cell.
	 *
	 * <p><b>Why the click targets the FLOOR.</b> {@code LiquidBlock#getShape} is empty, so the
	 * crosshair raytrace goes straight through oil and always lands on the solid block behind it —
	 * here the basin floor, face UP, whose offset cell is the oil. That is the exact hit vanilla
	 * {@code FlintAndSteelItem#useOn} would receive (and fail on, because the offset cell is not
	 * air), and it is what the mod's early block-use seam intercepts. Routing through
	 * {@code ServerPlayerGameMode#useItemOn} rather than calling the helper directly is the point:
	 * it proves the seam is actually wired on THIS loader (Fabric {@code UseBlockCallback} /
	 * NeoForge {@code PlayerInteractEvent.RightClickBlock}).
	 *
	 * <p>Phase 1 ({@code oilBurns=true}, forced so a stale run-dir config cannot shadow the test):
	 * the click consumes the interaction, the oil cell is fire on the same tick, and the flint and
	 * steel loses one durability. Phase 2 ({@code oilBurns=false}): the identical click on an
	 * identical rig is NOT ours — vanilla handles it, fails to place fire into the non-air oil block,
	 * and the oil source is untouched. Both phases live in ONE test because {@code Config} is global
	 * and a batch runs concurrently.
	 */
	public static void fun04BurnGateOnThenOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean saved = forceOilBurns(helper, true);
		try {
			basin(helper, 2, 2, 2, 2);
			basin(helper, 2, 5, 2, 5);
			ServerPlayer player = survivalPlayer(helper);

			// Phase 1 — gate ON: the oil must catch immediately.
			BlockPos oilOnAbs = helper.absolutePos(new BlockPos(2, 2, 2));
			level.setBlockAndUpdate(oilOnAbs, oilSource());
			ItemStack lighter = new ItemStack(Items.FLINT_AND_STEEL);
			InteractionResult lit = useOnTopFace(helper, player, lighter, new BlockPos(2, 1, 2));
			ItemStack afterLit = player.getItemInHand(InteractionHand.MAIN_HAND);
			if (!lit.consumesAction() || !level.getBlockState(oilOnAbs).is(BlockTags.FIRE)) {
				helper.fail("oilBurns=true: flint and steel must light the oil cell, result=" + lit
						+ " block=" + level.getBlockState(oilOnAbs));
				return;
			}
			if (afterLit.getDamageValue() != 1) {
				helper.fail("lighting oil must cost the flint and steel one durability, damage="
						+ afterLit.getDamageValue());
				return;
			}

			// Phase 2 — gate OFF: the same click must fall through to vanilla and change nothing.
			Config.oilBurns = false;
			BlockPos oilOffAbs = helper.absolutePos(new BlockPos(2, 2, 5));
			level.setBlockAndUpdate(oilOffAbs, oilSource());
			InteractionResult refused = useOnTopFace(helper, player,
					new ItemStack(Items.FLINT_AND_STEEL), new BlockPos(2, 1, 5));
			FluidState untouched = level.getFluidState(oilOffAbs);
			if (refused.consumesAction() || !untouched.isSourceOfType(ModContent.OIL.get())) {
				helper.fail("oilBurns=false: oil must not be ignitable, result=" + refused
						+ " fluid=" + untouched.getType() + " source=" + untouched.isSource());
				return;
			}
			helper.succeed();
		} finally {
			Config.oilBurns = saved;
		}
	}

	// ── FUN05: the burn walks the pool, and obeys the fire-spread game rule ───────────────────────

	/**
	 * Force the vanilla anti-griefing game rule {@code fire_spread_radius_around_player} for the
	 * duration of one test, restoring it on every exit path (same reasoning and same
	 * {@code runBeforeTestEnd} backstop as {@link #forceOilBurns}: the rule is server-global state,
	 * and a timed-out test would otherwise leave it flipped for the rest of the run).
	 *
	 * @param radius {@code -1} unlimited spread, {@code 0} no spread at all.
	 */
	private static int forceFireSpreadRadius(GameTestHelper helper, int radius) {
		ServerLevel level = helper.getLevel();
		int saved = level.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
		helper.runBeforeTestEnd(() -> setFireSpreadRadius(helper, saved));
		setFireSpreadRadius(helper, radius);
		return saved;
	}

	private static void setFireSpreadRadius(GameTestHelper helper, int radius) {
		helper.getLevel().getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, radius,
				helper.getLevel().getServer());
	}

	/**
	 * Chain reaction: light the west end of a three-source oil trench and the whole trench is gone —
	 * unless an admin has turned fire spread off, in which case only the cell the player actually lit
	 * burns. Both directions in one test because the game rule, like {@code Config}, is server-global
	 * state and a gametest batch runs concurrently.
	 *
	 * <p>Phase A — {@code fire_spread_radius_around_player = 0}: the flint and steel still works (a
	 * deliberate player action, exactly as in vanilla), but the two cells beyond it must still hold
	 * oil. Removing the {@code canSpreadFireAround} gate from {@code OilLiquidBlock} burns the trench
	 * anyway and fails here.
	 *
	 * <p>Phase B — the rule set to {@code -1} (unlimited, the value that makes the assertion
	 * independent of where the mock player happens to stand): the same rig burns out completely. This
	 * is the {@link BlockTags#FIRE} neighbour trigger — the lit cell becomes fire, its oil neighbour
	 * schedules its own ignition tick, and so on down the line.
	 *
	 * <p>The cadence in phase B is a race the ignition delay must win: fire is a replaceable block, so
	 * the neighbouring oil re-floods a burnt cell on its own fluid tick (40 since MOD-248). With the
	 * ignition delay at 10 the next cell catches first; at the original 30 the re-flood doused the
	 * fire and the chain died after one block.
	 *
	 * <p><b>The diagonal cell (MOD-250).</b> Phase B's rig also holds one source walled off on all four
	 * horizontal faces, reachable from the trench only along an edge. Faces-only ignition left it
	 * untouched, which in the world showed up as a fire burning for ever beside a source it could not
	 * see: only the flowing oil running past the flame burnt, and the source refilled it every time.
	 * It rides in this test rather than its own because the fire-spread game rule is server-global and
	 * a gametest batch runs concurrently — a second test flipping that rule races this one.
	 *
	 * <p>Both trenches are built at tick 0 but lit much later on purpose: placing a source schedules a
	 * fluid tick one delay out, and that stale pending tick would re-flood the first burnt cell before
	 * the next one caught — an artefact of building the rig in a single tick, not of the mechanic (an
	 * in-world lake has no pending fluid ticks until something disturbs it).
	 */
	public static void fun05BurnSpreadsAcrossPool(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		final BlockPos[] blocked = { new BlockPos(2, 2, 2), new BlockPos(3, 2, 2), new BlockPos(4, 2, 2) };
		final BlockPos[] pool = { new BlockPos(2, 2, 5), new BlockPos(3, 2, 5), new BlockPos(4, 2, 5) };
		// Diagonal-only cell: its four horizontal faces are stone or wall, so the burn can reach it
		// along an edge or not at all (MOD-250).
		final BlockPos diagonal = new BlockPos(5, 2, 6);
		boolean savedBurns = forceOilBurns(helper, true);
		forceFireSpreadRadius(helper, 0);
		basin(helper, 2, 2, 4, 2);
		basin(helper, 2, 5, 5, 6);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 2, 6), Blocks.STONE);
		level.setBlockAndUpdate(helper.absolutePos(diagonal), oilSource());
		for (BlockPos cell : blocked) {
			level.setBlockAndUpdate(helper.absolutePos(cell), oilSource());
		}
		for (BlockPos cell : pool) {
			level.setBlockAndUpdate(helper.absolutePos(cell), oilSource());
		}

		// Phase A — spread disabled. Lighting still works; the burn must not walk.
		helper.runAtTickTime(20, () -> {
			InteractionResult lit = useOnTopFace(helper, survivalPlayer(helper),
					new ItemStack(Items.FLINT_AND_STEEL), new BlockPos(2, 1, 2));
			if (!lit.consumesAction()) {
				Config.oilBurns = savedBurns;
				helper.fail("flint and steel refused to light the west end of the blocked trench: " + lit);
			}
		});
		// Three ignition delays (10) plus margin: if the chain were running the trench would be gone.
		helper.runAtTickTime(50, () -> {
			for (int i = 1; i < blocked.length; i++) {
				FluidState fs = level.getFluidState(helper.absolutePos(blocked[i]));
				if (!isOil(fs)) {
					Config.oilBurns = savedBurns;
					helper.fail("fire_spread_radius_around_player=0 must stop the burn from spreading,"
							+ " but cell " + blocked[i] + " now holds "
							+ (fs.isEmpty() ? "nothing" : fs.getType().toString())
							+ " — OilLiquidBlock ignores the vanilla anti-griefing game rule");
					return;
				}
			}
			// Phase B — spread unlimited: the same rig must burn out completely.
			setFireSpreadRadius(helper, -1);
			InteractionResult lit = useOnTopFace(helper, survivalPlayer(helper),
					new ItemStack(Items.FLINT_AND_STEEL), new BlockPos(2, 1, 5));
			if (!lit.consumesAction()) {
				Config.oilBurns = savedBurns;
				helper.fail("flint and steel refused to light the west end of the pool: " + lit);
			}
		});
		// Three cells at one ignition delay (10) each after the tick-50 light: done by ~tick 80.
		helper.runAtTickTime(120, () -> {
			try {
				for (BlockPos cell : pool) {
					FluidState fs = level.getFluidState(helper.absolutePos(cell));
					if (isOil(fs)) {
						helper.fail("the burn must walk the whole pool: cell " + cell + " still holds "
								+ fs.getType() + " (amount=" + fs.getAmount() + ")");
						return;
					}
				}
				FluidState edge = level.getFluidState(helper.absolutePos(diagonal));
				if (isOil(edge)) {
					helper.fail("the burn must also cross an edge diagonal: " + diagonal + " still holds oil"
							+ " (amount=" + edge.getAmount() + ") — either the ignition search is back to"
							+ " faces only, or a lit cell no longer wakes its diagonal neighbours");
					return;
				}
				helper.succeed();
			} finally {
				Config.oilBurns = savedBurns;
			}
		});
	}

	// ── NEG02: lava is NOT an igniter — worldgen may place a deposit flush against it ─────────────

	/**
	 * Regression guard for the worldgen blocker: an oil source sitting directly against a lava source
	 * must stay oil forever, even with {@code oilBurns=true}.
	 *
	 * <p>Oil lakes generate in the same Y band as vanilla lava lakes, and {@code ProtoChunk
	 * .setBlockState} never calls {@code onPlace} — so a deposit generated against lava was inert
	 * until the player's first neighbour update nearby and then burned away in one chain reaction:
	 * the deposit vanished the moment it was found. The fix scoped the trigger to actual FIRE blocks
	 * ({@link BlockTags#FIRE}); this test fails the instant lava is wired back in as a trigger.
	 *
	 * <p>Not vacuous by construction: FUN04/FUN05 above prove the very same rig DOES ignite from a
	 * real igniter, so "nothing ever burns" cannot make this pass.
	 */
	public static void neg02LavaNeighbourNeverIgnites(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean saved = forceOilBurns(helper, true);
		// Lava at x=2, oil at x=3 — oil placed LAST so its onPlace sees the lava, exactly the
		// ordering that used to schedule the ignition tick.
		basin(helper, 2, 2, 3, 2);
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, 2, 2)), Blocks.LAVA.defaultBlockState());
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(3, 2, 2)), oilSource());
		BlockPos oilAbs = helper.absolutePos(new BlockPos(3, 2, 2));
		// The gate stays forced ON for the whole observation window — restoring it early would let a
		// stale oilBurns=false, not the fix, be the reason nothing burned.
		helper.runAtTickTime(90, () -> {
			try {
				FluidState fs = level.getFluidState(oilAbs);
				if (!fs.isSourceOfType(ModContent.OIL.get())) {
					helper.fail("a lava NEIGHBOUR must never ignite oil (worldgen puts deposits next to"
							+ " lava lakes), but the cell now holds " + fs.getType()
							+ " / block " + level.getBlockState(oilAbs));
					return;
				}
				helper.succeed();
			} finally {
				Config.oilBurns = saved;
			}
		});
	}

	// ── FUN06: the dispenser empties an oil bucket as well as filling one ─────────────────────────

	/**
	 * A dispenser loaded with an oil bucket places an oil SOURCE in front of it and keeps the empty
	 * bucket. Vanilla registers the emptying behaviour per item by name
	 * ({@code DispenseItemBehavior.bootStrap}), so a modded filled bucket gets none — while the
	 * EMPTY-bucket behaviour is generic ({@code instanceof BucketPickup}) and always scooped oil up.
	 * That asymmetry (oil goes in, never comes out) is what this covers, on both loaders: Fabric
	 * registers the behaviour during mod init, NeoForge inside {@code FMLCommonSetupEvent}.
	 */
	public static void fun06DispenserEmptiesOilBucket(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos dispenserRel = new BlockPos(2, 2, 2);
		BlockPos targetAbs = helper.absolutePos(new BlockPos(3, 2, 2));
		basin(helper, 2, 2, 3, 2);
		helper.setBlock(dispenserRel, Blocks.DISPENSER.defaultBlockState()
				.setValue(DispenserBlock.FACING, Direction.EAST));
		DispenserBlockEntity dispenser = helper.getBlockEntity(dispenserRel, DispenserBlockEntity.class);
		if (dispenser == null) {
			helper.fail("dispenser block entity missing after placement");
			return;
		}
		dispenser.setItem(0, new ItemStack(ModContent.OIL_BUCKET.get()));
		// Rising redstone edge -> DispenserBlock schedules its dispense 4 ticks later.
		helper.setBlock(new BlockPos(2, 3, 2), Blocks.REDSTONE_BLOCK);

		helper.runAtTickTime(20, () -> {
			FluidState placed = level.getFluidState(targetAbs);
			if (!placed.isSourceOfType(ModContent.OIL.get())) {
				helper.fail("the dispenser must empty the oil bucket into the cell it faces, got "
						+ (placed.isEmpty() ? "nothing" : placed.getType().toString())
						+ " — is OilBucketDispenseBehavior registered on this loader?");
				return;
			}
			if (!dispenser.getItem(0).is(Items.BUCKET)) {
				helper.fail("the dispenser must keep the emptied bucket, slot holds "
						+ dispenser.getItem(0));
				return;
			}
			helper.succeed();
		});
	}

	// ── PRF01: viscous spread profile — drop-off 2 pins amounts 6/4/2 and a stop at distance 3 ────

	/**
	 * A single oil source at the closed end of a flat 5-cell trench: with drop-off 2 the flowing
	 * amounts along the line are exactly 8→6→4→2 and the flow STOPS there — the fifth cell stays dry
	 * (2 − 2 = 0 spreads nothing). Water in the same trench would wet every cell with amounts
	 * 7/6/5/4, so this deterministically pins the viscous profile without any timing-sensitive
	 * tick-delay measurement (tick delay 15 and slope-find 2 are not observable on a closed flat
	 * trench and stay covered by the dev-client gameplay check; see the suite doc). Tick delay 40 is
	 * still not asserted here — only the settle deadline scales with it.
	 */
	public static void prf01ViscousSpreadProfile(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		basin(helper, 2, 2, 6, 2);
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, 2, 2)), oilSource());

		// Three spread steps at 40 ticks each (MOD-248); tick 220 leaves ample settle margin.
		helper.runAtTickTime(220, () -> {
			int[] expected = { 6, 4, 2 };
			for (int i = 0; i < expected.length; i++) {
				BlockPos cell = new BlockPos(3 + i, 2, 2);
				FluidState fs = level.getFluidState(helper.absolutePos(cell));
				if (!isOil(fs) || fs.isSource() || fs.getAmount() != expected[i]) {
					helper.fail("viscous spread at distance " + (i + 1) + ": expected FLOWING oil amount "
							+ expected[i] + ", got " + (fs.isEmpty() ? "empty" : fs.getType() + " amount="
							+ fs.getAmount() + " source=" + fs.isSource()));
					return;
				}
			}
			FluidState beyond = level.getFluidState(helper.absolutePos(new BlockPos(6, 2, 2)));
			if (!beyond.isEmpty()) {
				helper.fail("oil must stop at distance 3 (amount 2 spreads nothing), but distance 4 holds "
						+ beyond.getType() + " amount=" + beyond.getAmount());
				return;
			}
			helper.succeed();
		});
	}

	// ── FUN09: the first oil bucket awards "Black Gold" ───────────────────────────────────────────

	/**
	 * The oil bucket landing in a player's inventory awards {@code alaindustrial:first_oil}
	 * ("Black Gold", MOD-244).
	 *
	 * <p>Worth a test rather than a manual tick-off, because the advancement tree is <b>not
	 * retroactive</b>: if this node ever stops firing, the only players who notice are the ones who
	 * already missed it, and there is no way to hand it back. It also drives the shipped JSON end to
	 * end — a typo in the trigger id, the item id or {@code requirements} fails right here.
	 *
	 * <p>The bucket is put into the inventory directly rather than scooped out of a lake: the trigger
	 * is {@code minecraft:inventory_changed}, and vanilla fires it from the {@code ContainerListener}
	 * {@code ServerPlayer} registers on {@code inventoryMenu} — {@code slotChanged} calls
	 * {@code CriteriaTriggers.INVENTORY_CHANGED} for any slot backed by the player's own inventory.
	 * {@code broadcastChanges()} is what walks the slots and fires it. How the bucket got there is
	 * irrelevant to the criterion, and the scooping path itself is already covered by FUN01/FUN02.
	 */
	public static void fun09FirstOilAwardsTheAdvancement(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);

		AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements()
				.get(Industrialization.id("first_oil"));
		if (advancement == null) {
			helper.fail("advancement alaindustrial:first_oil is not loaded — the JSON is missing or "
					+ "failed to parse");
			return;
		}
		if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
			helper.fail("first_oil was already awarded before the player got any oil — the criterion "
					+ "is not gated on the bucket");
			return;
		}

		player.getInventory().add(new ItemStack(ModContent.OIL_BUCKET.get()));
		player.inventoryMenu.broadcastChanges();

		if (!player.getAdvancements().getOrStartProgress(advancement).isDone()) {
			helper.fail("an oil bucket reached the inventory but alaindustrial:first_oil was not "
					+ "awarded — check the trigger id and the item predicate in the advancement JSON");
			return;
		}
		helper.succeed();
	}

	// ── FUN08: a torch placed into oil is logged with it, not left in an air pocket ───────────────

	/**
	 * Placing the enriched uranium torch into an oil source logs the cell with oil (MOD-250).
	 *
	 * <p><b>The bug this pins.</b> The torch is a waterloggable block, and the vanilla default for that
	 * answers "water only" to {@code canPlaceLiquid}. Oil therefore could not enter the cell at all: it
	 * stopped at the boundary, the cell stayed air, and the surrounding pool rendered its side faces
	 * into that pocket — a black frame standing in the oil with the torch invisible behind it.
	 *
	 * <p>Asserting the fluid state rather than only the property is deliberate: the property is what
	 * the placement writes, the fluid state is what the chunk renderer reads, and it is the renderer's
	 * view that the player complained about.
	 */
	public static void fun08TorchLogsWithOil(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos cell = new BlockPos(2, 2, 2);
		basin(helper, 2, 2, 2, 2);
		level.setBlockAndUpdate(helper.absolutePos(cell), oilSource());

		helper.runAtTickTime(5, () -> {
			InteractionResult placed = useOnTopFace(helper, survivalPlayer(helper),
					new ItemStack(ModContent.ENRICHED_URANIUM_TORCH_ITEM.get()), new BlockPos(2, 1, 2));
			if (!placed.consumesAction()) {
				helper.fail("the torch refused to be placed into an oil source: " + placed);
				return;
			}
			BlockState state = level.getBlockState(helper.absolutePos(cell));
			if (!state.is(ModContent.ENRICHED_URANIUM_TORCH.get())) {
				helper.fail("expected the torch at " + cell + ", found " + state);
				return;
			}
			if (!state.getValue(OilLoggedBlock.OILLOGGED)) {
				helper.fail("the torch placed into oil must come out oil-logged, but OILLOGGED is false"
						+ " — the oil is left standing around an air pocket");
				return;
			}
			FluidState fs = state.getFluidState();
			if (!isOil(fs) || !fs.isSource()) {
				helper.fail("an oil-logged torch must report a full oil source to the renderer, got "
						+ (fs.isEmpty() ? "nothing" : fs.getType() + " source=" + fs.isSource()));
				return;
			}
			helper.succeed();
		});
	}

	// ── NEG03: an entity must SINK through oil, never hang in it ─────────────────────────────────

	/**
	 * Build a sealed 1x1 shaft from rel y=2 up to {@code topY}, walls all round and a stone floor,
	 * so a fluid poured in cannot escape and an entity inside can only move vertically.
	 */
	private static void shaft(GameTestHelper helper, int x, int z, int topY) {
		helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
		for (int y = 1; y <= topY + 1; y++) {
			helper.setBlock(new BlockPos(x - 1, y, z), Blocks.STONE);
			helper.setBlock(new BlockPos(x + 1, y, z), Blocks.STONE);
			helper.setBlock(new BlockPos(x, y, z - 1), Blocks.STONE);
			helper.setBlock(new BlockPos(x, y, z + 1), Blocks.STONE);
		}
	}

	/**
	 * @implements TC-OIL-001-NEG03 — an entity inside oil keeps vanilla AIR movement and sinks;
	 * it must never hang motionless as if oil were a solid block. A water shaft is the control: the
	 * fix must not strip vanilla fluid physics from vanilla fluids.
	 *
	 * <p><b>The regression this exists for (MOD-495).</b> {@code LivingEntity#travel} routes through
	 * {@code travelInFluid} whenever {@code shouldTravelInFluid} is true, and on NeoForge that
	 * question is also asked of every registered {@code FluidType} — ours included. Inside
	 * {@code travelInFluid}, a fluid that is neither water-like nor carrying a custom
	 * {@code FluidType#move} matches NO branch, so neither input nor gravity is applied: the entity
	 * freezes in mid-fluid and the deposit reads as an invisible wall. This was dormant while the
	 * upstream fluid patches were commented out (26.2.0.8-beta) and went live the moment the platform
	 * was raised to 26.2.0.67 — a player-visible break that every existing oil test passed straight
	 * through, because they all assert on blocks and fluids, never on an entity moving.
	 */
	public static void neg03EntitySinksInsteadOfHanging(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		final int topY = 7;
		shaft(helper, 2, 2, topY);
		shaft(helper, 5, 2, topY);
		for (int y = 2; y <= topY; y++) {
			level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, y, 2)), oilSource());
			level.setBlockAndUpdate(helper.absolutePos(new BlockPos(5, y, 2)),
					Blocks.WATER.defaultBlockState());
		}

		Cow inOil = helper.spawn(EntityTypes.COW, new BlockPos(2, topY, 2));
		Cow inWater = helper.spawn(EntityTypes.COW, new BlockPos(5, topY, 2));
		double oilStartY = inOil.getY();
		double waterStartY = inWater.getY();

		// 40 ticks: oil's own damping (OilFluid#entityInside, vertical x0.72 on top of air drag)
		// gives a terminal speed near 0.28 blocks/tick, so a working sink covers the shaft easily,
		// while a hung entity has moved essentially nothing.
		helper.runAtTickTime(40, () -> {
			double oilDrop = oilStartY - inOil.getY();
			if (oilDrop < 1.5D) {
				helper.fail("an entity in oil must sink, but it dropped only "
						+ String.format(java.util.Locale.ROOT, "%.3f", oilDrop) + " blocks in 40 ticks"
						+ " — oil is behaving like a solid block (shouldTravelInFluid routed it into"
						+ " travelInFluid, where a non-water-like FluidType applies no movement at all)");
				return;
			}
			// Control: vanilla water must still be handled by vanilla. A cow swims, so it stays near
			// the surface — it must NOT plummet the way it does through oil.
			double waterDrop = waterStartY - inWater.getY();
			if (waterDrop >= oilDrop) {
				helper.fail("water physics were stripped along with the fix: the cow fell "
						+ String.format(java.util.Locale.ROOT, "%.3f", waterDrop)
						+ " blocks through water vs " + String.format(java.util.Locale.ROOT, "%.3f", oilDrop)
						+ " through oil — vanilla fluids must keep vanilla movement");
				return;
			}
			helper.succeed();
		});
	}

	// ── FUN10: all three fluids damp the fall, in viscosity order ────────────────────────────────

	/**
	 * @implements TC-OIL-001-FUN10 — every fluid the mod places in the world damps a falling entity
	 * and clears its fall distance, and the damping is ordered by viscosity: air &gt; diesel &gt;
	 * fuel oil &gt; crude oil.
	 *
	 * <p><b>The gap this closes (MOD-496).</b> The immersion mechanic was written inside
	 * {@code OilFluid#entityInside} and reachable only from crude oil, so diesel and fuel oil shipped
	 * with no physics at all. That was not the "behaves like water" their spec promised — a modded
	 * fluid is in neither vanilla tag, so it inherits neither swimming nor buoyancy, and an entity
	 * that dropped into a pool could not climb out. The mechanic now lives in {@link FluidImmersion},
	 * one profile per fluid, and this test is what keeps the roster honest: a fluid that loses its
	 * profile stops damping and its column collapses into the air column's result.
	 *
	 * <p>Asserted as an ORDER rather than against absolute distances: the ordering is the design
	 * claim (thinner fluid, freer movement), it follows the viscosities already fixed in the fluid
	 * types, and it cannot be satisfied by accident the way a single threshold can.
	 */
	public static void fun10ImmersionDampsFallInViscosityOrder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		final int topY = 16;
		final int[] columns = {2, 4, 6, 8};
		for (int x : columns) {
			shaft(helper, x, 2, topY);
		}
		for (int y = 2; y <= topY; y++) {
			level.setBlockAndUpdate(helper.absolutePos(new BlockPos(4, y, 2)), oilSource());
			level.setBlockAndUpdate(helper.absolutePos(new BlockPos(6, y, 2)),
					ModContent.FUEL_OIL_BLOCK.get().defaultBlockState());
			level.setBlockAndUpdate(helper.absolutePos(new BlockPos(8, y, 2)),
					ModContent.DIESEL_BLOCK.get().defaultBlockState());
		}
		// x=2 stays empty: the air column is the control that proves the fluids damp anything at all.

		Cow inAir = helper.spawn(EntityTypes.COW, new BlockPos(2, topY, 2));
		Cow inOil = helper.spawn(EntityTypes.COW, new BlockPos(4, topY, 2));
		Cow inFuelOil = helper.spawn(EntityTypes.COW, new BlockPos(6, topY, 2));
		Cow inDiesel = helper.spawn(EntityTypes.COW, new BlockPos(8, topY, 2));
		double startY = inAir.getY();

		// Tick 12: nothing has reached the floor of a 16-deep shaft yet, so every column is still
		// measuring free travel rather than a landing.
		helper.runAtTickTime(12, () -> {
			double air = startY - inAir.getY();
			double diesel = startY - inDiesel.getY();
			double fuelOil = startY - inFuelOil.getY();
			double oil = startY - inOil.getY();
			String measured = String.format(java.util.Locale.ROOT,
					"air=%.3f diesel=%.3f fuelOil=%.3f oil=%.3f", air, diesel, fuelOil, oil);

			// A margin, not a bare >: two columns differing by a hair would "pass" on noise.
			final double margin = 0.2D;
			if (air - diesel < margin) {
				helper.fail("diesel must damp a fall relative to air, but " + measured
						+ " — the fluid has no immersion profile and the entity is falling as if"
						+ " through air");
				return;
			}
			if (diesel - fuelOil < margin) {
				helper.fail("fuel oil must damp more than diesel (viscosity 2400 vs 1200): " + measured);
				return;
			}
			if (fuelOil - oil < margin) {
				helper.fail("crude must damp more than fuel oil (viscosity 3000 vs 2400): " + measured);
				return;
			}
			// Fall damage: every mod fluid clears fall distance every tick, air does not.
			for (Cow submerged : new Cow[] {inOil, inFuelOil, inDiesel}) {
				if (submerged.fallDistance > 0.0F) {
					helper.fail("an entity inside a mod fluid must not accumulate fall distance, got "
							+ submerged.fallDistance + " — a deep pool would deal fall damage on landing");
					return;
				}
			}
			if (inAir.fallDistance <= 0.0F) {
				helper.fail("the air control must accumulate fall distance, got " + inAir.fallDistance
						+ " — the rig is not measuring what it claims");
				return;
			}
			helper.succeed();
		});
	}
}
