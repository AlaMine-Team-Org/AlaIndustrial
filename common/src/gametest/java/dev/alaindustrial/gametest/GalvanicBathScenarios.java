package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.GalvanicBathBlockEntity;
import dev.alaindustrial.block.entity.GalvanicBathStatus;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * Loader-neutral gametest bodies for the Galvanic Bath (MOD-127, suite TC-BATH-001). Wrapped by the
 * Fabric {@code GalvanicBathGameTest} suite and registered on the NeoForge {@code gameTestServer}
 * lane ({@code NeoForgeGameTests}, {@code galvanic_bath_*}), so both loaders run the SAME bodies.
 *
 * <p>Two things here are unique to this machine and are what these bodies mostly exist to pin down:
 *
 * <ul>
 * <li><b>The fibre tag.</b> The recipe accepts {@code #alaindustrial:fiber}, so vanilla string and
 * our own cotton fibre must both work. A tag that silently loses a member — or a recipe rewritten to
 * name one item — would still let the machine "work", and only the other playstyle would break.
 * {@link #fun01StringBecomesFluxThread} and {@link #fun02CottonBecomesFluxThread} run the same
 * assertion from both entry points on purpose.</li>
 * <li><b>The water gate.</b> Water is NOT part of the recipe (see the block entity), so nothing in
 * the recipe system enforces it. If the hand-written check were dropped, the machine would happily
 * plate fibre out of an empty tank and no recipe test would notice.
 * {@link #con01DryTankBlocksWork} is the only thing standing between that and a shipped bug.</li>
 * </ul>
 */
public final class GalvanicBathScenarios {

	private GalvanicBathScenarios() {
	}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	/** Far above one operation's cost (600 EU), set directly so the tier packet cap is bypassed. */
	private static final long AMPLE_EU = 8000L;

	/** Ticks to drive: one full operation plus slack for the scaled-duration knob. */
	private static int driveTicks() {
		return Config.scaledDuration(Config.galvanicBathDuration) + 20;
	}

	private static GalvanicBathBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.GALVANIC_BATH.get());
		GalvanicBathBlockEntity be = helper.getBlockEntity(POS, GalvanicBathBlockEntity.class);
		if (be == null) {
			helper.fail("galvanic bath block entity missing after placement");
		}
		return be;
	}

	/** Put {@code mb} of water straight into the tank — the tests are about what the machine does with it. */
	private static void fill(GalvanicBathBlockEntity be, long mb) {
		be.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
		be.fluidTank.amount = mb;
	}

	/** Stock the machine for exactly one operation, with {@code fibre} as the fibre input. */
	private static GalvanicBathBlockEntity stocked(GameTestHelper helper, ItemStack fibre) {
		GalvanicBathBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(GalvanicBathBlockEntity.FIBER_SLOT, fibre);
		be.setItem(GalvanicBathBlockEntity.SILVER_SLOT, new ItemStack(ModContent.SILVER_DUST.get()));
		// Fill the tank right up: one operation now drinks four buckets, so a single bucket would
		// leave the machine water-starved and every production case would read as a failure.
		fill(be, GalvanicBathBlockEntity.TANK_CAPACITY);
		return be;
	}

	private static void drive(GalvanicBathBlockEntity be, GameTestHelper helper, int ticks) {
		AlaGameTestHelper.drive(be, helper, ticks);
	}

	/**
	 * Drive the machine with its buffer topped up every tick, the way a connected cable keeps it fed.
	 *
	 * <p>Necessary because one operation costs 1000 EU while {@code machineBuffer} holds 800: the bath
	 * draws 2 EU/t over 500 ticks and relies on the network refilling it as it goes, so a buffer set
	 * once at the start runs dry two thirds of the way through. Not a defect in the machine — the
	 * energy network does exactly this in play — but a test that skipped it would report a working
	 * machine as broken.
	 */
	private static void drivePowered(GalvanicBathBlockEntity be, GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
			AlaGameTestHelper.drive(be, helper, 1);
		}
	}

	/** Assert exactly one flux thread landed in the output slot. */
	private static boolean producedOneThread(GalvanicBathBlockEntity be, GameTestHelper helper, String what) {
		ItemStack out = be.getItem(GalvanicBathBlockEntity.OUTPUT_SLOT);
		if (!out.is(ModContent.FLUX_THREAD.get()) || out.getCount() != 1) {
			helper.fail("expected 1x flux thread from " + what + ", got "
					+ (out.isEmpty() ? "empty" : out.getCount() + "x " + out.getItem()));
			return false;
		}
		return true;
	}

	// ── FUN01/FUN02: both fibres in the tag reach the same result ───────────────────────────────────

	/** Vanilla string + silver dust + water + EU → one flux thread. */
	public static void fun01StringBecomesFluxThread(GameTestHelper helper) {
		GalvanicBathBlockEntity be = stocked(helper, new ItemStack(Items.STRING));
		drivePowered(be, helper, driveTicks());
		if (!producedOneThread(be, helper, "vanilla string")) {
			return;
		}
		helper.succeed();
	}

	/**
	 * Cotton fibre (MOD-280) must work exactly like string. This is the farming half of the
	 * {@code #alaindustrial:fiber} tag; without it the trellis line would dead-end and only a spider
	 * farm would open the Fluxweave chain.
	 */
	public static void fun02CottonBecomesFluxThread(GameTestHelper helper) {
		GalvanicBathBlockEntity be = stocked(helper, new ItemStack(ModContent.COTTON_FIBER.get()));
		drivePowered(be, helper, driveTicks());
		if (!producedOneThread(be, helper, "cotton fibre")) {
			return;
		}
		helper.succeed();
	}

	// ── FUN03: a completed operation debits exactly one recipe's worth of water ─────────────────────

	/** One operation consumes {@code galvanicBathWaterPerOp} mB — no more, no less. */
	public static void fun03OperationDebitsWater(GameTestHelper helper) {
		GalvanicBathBlockEntity be = stocked(helper, new ItemStack(Items.STRING));
		long before = be.fluidTank.amount;
		drivePowered(be, helper, driveTicks());
		if (!producedOneThread(be, helper, "string")) {
			return;
		}
		long spent = before - be.fluidTank.amount;
		long expected = Math.max(1L, Config.galvanicBathWaterPerOp);
		if (spent != expected) {
			helper.fail("expected " + expected + " mB of water spent, got " + spent);
			return;
		}
		helper.succeed();
	}

	// ── CON01: the water gate — the one rule no recipe test can cover ───────────────────────────────

	/**
	 * With everything else present but a dry tank, the machine must NOT produce, and must say so.
	 * Water is not a recipe ingredient, so this hand-written gate is the only thing enforcing it.
	 */
	public static void con01DryTankBlocksWork(GameTestHelper helper) {
		GalvanicBathBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(GalvanicBathBlockEntity.FIBER_SLOT, new ItemStack(Items.STRING));
		be.setItem(GalvanicBathBlockEntity.SILVER_SLOT, new ItemStack(ModContent.SILVER_DUST.get()));
		// tank deliberately left empty

		drive(be, helper, driveTicks());

		if (!be.getItem(GalvanicBathBlockEntity.OUTPUT_SLOT).isEmpty()) {
			helper.fail("the bath produced flux thread with an empty tank");
			return;
		}
		if (be.getItem(GalvanicBathBlockEntity.FIBER_SLOT).isEmpty()) {
			helper.fail("a blocked operation must not eat its fibre");
			return;
		}
		// The player has to be told WHICH thing is missing: "no recipe" would be a lie here, since the
		// two items do form one.
		GalvanicBathStatus status = GalvanicBathStatus.byOrdinal(be.getDataAccess().get(6));
		if (status != GalvanicBathStatus.NO_WATER) {
			helper.fail("expected status NO_WATER on a dry tank, got " + status);
			return;
		}
		helper.succeed();
	}

	// ── CON02: partial water is not enough ──────────────────────────────────────────────────────────

	/**
	 * Less than one operation's worth of water blocks the run rather than half-paying for it. Without
	 * this the debit could underflow the tank and hand out a free thread at the bottom of the bath.
	 */
	public static void con02PartialWaterBlocksWork(GameTestHelper helper) {
		long need = Math.max(1L, Config.galvanicBathWaterPerOp);
		if (need < 2) {
			helper.succeed(); // nothing meaningful to under-fill
			return;
		}
		GalvanicBathBlockEntity be = stocked(helper, new ItemStack(Items.STRING));
		fill(be, need - 1);

		drive(be, helper, driveTicks());

		if (!be.getItem(GalvanicBathBlockEntity.OUTPUT_SLOT).isEmpty()) {
			helper.fail("the bath produced flux thread on a partially filled tank");
			return;
		}
		if (be.fluidTank.amount != need - 1) {
			helper.fail("a blocked operation must not touch the tank, now " + be.fluidTank.amount + " mB");
			return;
		}
		helper.succeed();
	}

	// ── CON03: the tank is feedstock, not storage ───────────────────────────────────────────────────

	/**
	 * Neighbours must not be able to pull water back out (R-CON-08).
	 *
	 * <p>Asserted by attempting a real extraction rather than by reading
	 * {@code supportsExtraction()}: that method reports what the <em>port type</em> can do in principle
	 * (it answers from the capacity alone) and is true for every tank, filter or no filter. The refusal
	 * lives in {@code extract(...)}, where the {@code canExtract} predicate is consulted. An earlier
	 * version of this test checked the flag and failed against a perfectly correct machine.
	 */
	public static void con03TankRefusesExtraction(GameTestHelper helper) {
		GalvanicBathBlockEntity be = place(helper);
		fill(be, FluidAmounts.BUCKET);
		long[] pulled = {0};
		EnergyTransactions.get().runCommitting(txn ->
				pulled[0] = be.fluidTank.extract(FluidHolder.of(Fluids.WATER), FluidAmounts.BUCKET, txn));
		if (pulled[0] != 0) {
			helper.fail("a neighbour drained " + pulled[0] + " mB out of the bath");
			return;
		}
		if (be.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("a refused extraction must leave the tank untouched, now " + be.fluidTank.amount + " mB");
			return;
		}
		helper.succeed();
	}

	// ── REG01: the tank takes water and nothing else ────────────────────────────────────────────────

	/** Lava (or any non-water) must be refused: a single-variant tank stuck on lava would be bricked. */
	public static void reg01TankRefusesNonWater(GameTestHelper helper) {
		GalvanicBathBlockEntity be = place(helper);
		long[] lava = {0};
		EnergyTransactions.get().runCommitting(txn ->
				lava[0] = be.fluidTank.insert(FluidHolder.of(Fluids.LAVA), FluidAmounts.BUCKET, txn));
		if (lava[0] != 0 || be.fluidTank.amount != 0) {
			helper.fail("the tank accepted " + lava[0] + " mB of lava");
			return;
		}
		long[] water = {0};
		EnergyTransactions.get().runCommitting(txn ->
				water[0] = be.fluidTank.insert(FluidHolder.of(Fluids.WATER), FluidAmounts.BUCKET, txn));
		if (water[0] != FluidAmounts.BUCKET) {
			helper.fail("the tank refused water: " + water[0] + " mB accepted");
			return;
		}
		helper.succeed();
	}

	// ── FUN04: a bucket in the fill slot loads the tank and drops an empty bucket ───────────────────

	/** Manual refill through the item fluid capability, the path a player uses before pipes exist. */
	public static void fun04BucketFillsTank(GameTestHelper helper) {
		GalvanicBathBlockEntity be = place(helper);
		be.setItem(GalvanicBathBlockEntity.FILL_INPUT_SLOT, new ItemStack(Items.WATER_BUCKET));

		drive(be, helper, 2);

		if (be.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("bucket did not fill the tank: " + be.fluidTank.amount + " mB");
			return;
		}
		if (!be.getItem(GalvanicBathBlockEntity.FILL_INPUT_SLOT).isEmpty()) {
			helper.fail("the filled bucket must leave the input slot");
			return;
		}
		if (!be.getItem(GalvanicBathBlockEntity.FILL_OUTPUT_SLOT).is(Items.BUCKET)) {
			helper.fail("the emptied bucket must land in the fill-output slot");
			return;
		}
		helper.succeed();
	}
}
