package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

/**
 * Loader-neutral gametest bodies for the Polymerizer (MOD-019, suite TC-POLY-001). Wrapped by the
 * Fabric {@code PolymerizerGameTest} suite and registered on the NeoForge {@code gameTestServer} lane
 * ({@code NeoForgeGameTests}, {@code polymerizer_*}), so both loaders run the SAME bodies.
 *
 * <p>The per-loader seam is real here: the machine's recipes ride a {@code RecipeType} registered eagerly
 * on Fabric and deferred on NeoForge, and its tank is published through a different capability on each
 * ({@code FluidStorage.SIDED} vs {@code Capabilities.Fluid.BLOCK}). A family that fails to bind, or a
 * capability line left out, produces a machine that silently never works on that loader alone — which no
 * compile catches.
 *
 * <p><b>What these bodies do and do not prove about that seam.</b> The recipe family is exercised
 * end-to-end by every production scenario (no binding, no recipe, no output). The item side is exercised
 * through the real {@code ItemFluidBridge} by {@link #fun02BucketFillsTank}. The block fluid capability is
 * exercised by {@link #con03NeighbourFillsThroughFluidPort} via {@code FluidLookup} — but only on Fabric
 * is that a real proof: {@code NeoForgeFluidLookup} resolves a {@code FluidPortHost} directly and only
 * falls back to the capability for foreign blocks, so on NeoForge the registration in
 * {@code IndustrializationNeoForge} stays unproven here. Deleting it would keep this suite green while
 * breaking other mods' pipes. Closing that needs a NeoForge-local test asserting
 * {@code level.getCapability(Capabilities.Fluid.BLOCK, …)}; it is written down in the task's tails.
 */
public final class PolymerizerScenarios {

	private PolymerizerScenarios() {
	}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	/** Far above one operation's cost (400 EU), set directly so the tier packet cap is bypassed. */
	private static final long AMPLE_EU = 8000L;

	/** Ticks to drive: one full operation plus slack for the scaled-duration knob. */
	private static int driveTicks() {
		return Config.scaledDuration(Config.polymerizerDuration) + 20;
	}

	private static PolymerizerBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.POLYMERIZER.get());
		PolymerizerBlockEntity be = helper.getBlockEntity(POS, PolymerizerBlockEntity.class);
		if (be == null) {
			helper.fail("polymerizer block entity missing after placement");
		}
		return be;
	}

	/**
	 * Put {@code mb} of oil straight into the tank. Direct field assignment rather than
	 * {@code insert(...)}: the test is about what the machine does with a stocked tank, and the insert
	 * path has its own coverage in {@link #reg01TankRefusesNonOil}.
	 */
	private static void fill(PolymerizerBlockEntity be, long mb) {
		be.fluidTank.fluid = FluidHolder.of(ModContent.OIL.get());
		be.fluidTank.amount = mb;
	}

	// ── FUN01: a stocked, powered machine makes raw rubber and drinks exactly one recipe volume ──────

	/** One bucket of oil + EU → one raw rubber, and the tank ends empty (not merely lower). */
	public static void fun01OilBecomesRawRubber(GameTestHelper helper) {
		PolymerizerBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		fill(be, FluidAmounts.BUCKET);

		drive(be, helper, driveTicks());

		ItemStack out = be.getItem(PolymerizerBlockEntity.OUTPUT_SLOT);
		if (!out.is(ModContent.RAW_RUBBER.get()) || out.getCount() != 1) {
			helper.fail("expected 1x raw rubber, got " + (out.isEmpty() ? "empty" : out.getCount() + "x " + out.getItem()));
			return;
		}
		if (be.fluidTank.amount != 0) {
			helper.fail("expected the tank drained to 0 mB, got " + be.fluidTank.amount);
			return;
		}
		// The tank invariant: no amount means no fluid, or the next insert of a different oil is refused
		// by a tank that looks occupied.
		if (!be.fluidTank.fluid.isEmpty()) {
			helper.fail("an emptied tank must clear its fluid, still holds " + be.fluidTank.fluid);
			return;
		}
		helper.succeed();
	}

	// ── FUN02: a filled container in the fill slot is emptied into the tank ──────────────────────────

	/** An oil bucket in the fill slot moves 1000 mB into the tank and drops an empty bucket below it. */
	public static void fun02BucketFillsTank(GameTestHelper helper) {
		PolymerizerBlockEntity be = place(helper);
		be.setItem(PolymerizerBlockEntity.FILL_INPUT_SLOT, new ItemStack(ModContent.OIL_BUCKET.get()));

		drive(be, helper, 2);

		if (be.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("bucket did not fill the tank: " + be.fluidTank.amount + " mB");
			return;
		}
		if (!be.fluidTank.fluid.is(ModContent.OIL.get())) {
			helper.fail("tank holds " + be.fluidTank.fluid + ", expected oil");
			return;
		}
		if (!be.getItem(PolymerizerBlockEntity.FILL_INPUT_SLOT).isEmpty()) {
			helper.fail("the filled bucket must leave the input slot");
			return;
		}
		if (!be.getItem(PolymerizerBlockEntity.FILL_OUTPUT_SLOT).is(net.minecraft.world.item.Items.BUCKET)) {
			helper.fail("expected an empty bucket in the fill-output slot, got "
					+ be.getItem(PolymerizerBlockEntity.FILL_OUTPUT_SLOT));
			return;
		}
		helper.succeed();
	}

	// ── NEG01: no EU, no product ────────────────────────────────────────────────────────────────────

	/** A full tank with an empty energy buffer produces nothing and consumes no oil. */
	public static void neg01NoPowerNoOutput(GameTestHelper helper) {
		PolymerizerBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(0L);
		fill(be, FluidAmounts.BUCKET);

		drive(be, helper, driveTicks());

		if (!be.getItem(PolymerizerBlockEntity.OUTPUT_SLOT).isEmpty()) {
			helper.fail("an unpowered polymerizer must not produce");
			return;
		}
		if (be.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("an unpowered polymerizer must not consume oil, tank is " + be.fluidTank.amount + " mB");
			return;
		}
		if (be.getDataAccess().get(2) != 0) {
			helper.fail("progress must stay at 0 without power, got " + be.getDataAccess().get(2));
			return;
		}
		helper.succeed();
	}

	// ── NEG02: below one recipe volume nothing happens — and nothing is eaten ────────────────────────

	/**
	 * 999 mB is one millibucket short of the recipe: no recipe matches, so the machine neither produces
	 * nor nibbles at the tank. Guards the "partial consumption" failure mode, where a machine drains what
	 * it has and hands back nothing.
	 */
	public static void neg02BelowRecipeVolumeIsInert(GameTestHelper helper) {
		PolymerizerBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		fill(be, FluidAmounts.BUCKET - 1);

		drive(be, helper, driveTicks());

		if (!be.getItem(PolymerizerBlockEntity.OUTPUT_SLOT).isEmpty()) {
			helper.fail("999 mB must not be enough for a 1000 mB recipe");
			return;
		}
		if (be.fluidTank.amount != FluidAmounts.BUCKET - 1) {
			helper.fail("an unmatched tank must be left untouched, got " + be.fluidTank.amount + " mB");
			return;
		}
		helper.succeed();
	}

	// ── CON01: a full output slot freezes the operation without voiding the oil ─────────────────────

	/**
	 * With the result slot full the machine may not complete: the oil stays in the tank and the output
	 * stack keeps its count. This is the item/fluid dupe-and-void guard (R-CON-*) — a machine that
	 * consumed its input and then failed to place the result would silently destroy a bucket of oil.
	 */
	public static void con01FullOutputKeepsOil(GameTestHelper helper) {
		PolymerizerBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		fill(be, FluidAmounts.BUCKET);
		be.setItem(PolymerizerBlockEntity.OUTPUT_SLOT, new ItemStack(ModContent.RAW_RUBBER.get(), 64));

		drive(be, helper, driveTicks());

		if (be.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("a blocked output must not consume oil, tank is " + be.fluidTank.amount + " mB");
			return;
		}
		ItemStack out = be.getItem(PolymerizerBlockEntity.OUTPUT_SLOT);
		if (out.getCount() != 64) {
			helper.fail("a full output slot must stay at 64, got " + out.getCount());
			return;
		}
		helper.succeed();
	}

	// ── REG01: the tank takes oil and refuses everything else ───────────────────────────────────────

	/** Water is refused by the tank's insert filter; oil is accepted. */
	public static void reg01TankRefusesNonOil(GameTestHelper helper) {
		PolymerizerBlockEntity be = place(helper);

		long[] water = {0};
		EnergyTransactions.get().runCommitting(txn ->
				water[0] = be.fluidTank.insert(FluidHolder.of(Fluids.WATER), FluidAmounts.BUCKET, txn));
		if (water[0] != 0 || be.fluidTank.amount != 0) {
			helper.fail("the tank must refuse water, inserted " + water[0] + " mB");
			return;
		}

		long[] oil = {0};
		EnergyTransactions.get().runCommitting(txn ->
				oil[0] = be.fluidTank.insert(FluidHolder.of(ModContent.OIL.get()), FluidAmounts.BUCKET, txn));
		if (oil[0] != FluidAmounts.BUCKET || be.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("the tank must accept oil, inserted " + oil[0] + " mB");
			return;
		}
		helper.succeed();
	}

	// ── REG02: the tank takes source oil and refuses the flowing variant ────────────────────────────

	/**
	 * The {@code c:oil} tag names the flowing variant as well (the pump's world search needs it), but a
	 * tank must not hold it: it is single-variant, so a partial amount of flowing oil could never be
	 * topped up by ordinary oil and would strand the machine below the recipe volume for good.
	 */
	public static void reg02TankRefusesFlowingOil(GameTestHelper helper) {
		PolymerizerBlockEntity be = place(helper);

		long[] flowing = {0};
		EnergyTransactions.get().runCommitting(txn ->
				flowing[0] = be.fluidTank.insert(FluidHolder.of(ModContent.FLOWING_OIL.get()),
						FluidAmounts.BUCKET, txn));
		if (flowing[0] != 0 || be.fluidTank.amount != 0) {
			helper.fail("the tank must refuse flowing oil, inserted " + flowing[0] + " mB");
			return;
		}
		// Sanity: the tag itself does accept it, so the guard above is the filter's doing, not a typo.
		if (!ModContent.FLOWING_OIL.get().is(ModTags.Fluids.C_OIL)) {
			helper.fail("flowing oil is expected to carry the c:oil tag — this test guards the source-only "
					+ "filter, and without the tag it would pass for the wrong reason");
			return;
		}
		helper.succeed();
	}

	// ── CON03: a neighbour fills the tank through the published fluid port ──────────────────────────

	/**
	 * Insert through {@code FluidLookup} — the same path a pump or a foreign pipe takes — rather than
	 * touching {@code fluidTank} directly, so the loader's block fluid capability is part of the test.
	 * See the class doc for what this does and does not prove per loader.
	 */
	public static void con03NeighbourFillsThroughFluidPort(GameTestHelper helper) {
		PolymerizerBlockEntity be = place(helper);
		BlockPos abs = helper.absolutePos(POS);

		FluidPort port = FluidLookup.get().find(helper.getLevel(), abs, Direction.UP);
		if (port == null) {
			helper.fail("no fluid port published for the polymerizer — the loader's capability/registration "
					+ "for this block entity is missing");
			return;
		}
		long[] moved = {0};
		EnergyTransactions.get().runCommitting(txn ->
				moved[0] = port.insert(FluidHolder.of(ModContent.OIL.get()), FluidAmounts.BUCKET, txn));
		if (moved[0] != FluidAmounts.BUCKET || be.fluidTank.amount != FluidAmounts.BUCKET) {
			helper.fail("insert through the published port moved " + moved[0] + " mB, tank holds "
					+ be.fluidTank.amount + " mB");
			return;
		}
		helper.succeed();
	}

	// ── STA01: the tank and the progress survive a save/load round-trip ─────────────────────────────

	/** NBT round-trip preserves the tank's volume and fluid identity plus energy and progress. */
	public static void sta01NbtRoundTripPreservesTank(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(POS);
		PolymerizerBlockEntity src = place(helper);

		// Below the machine buffer: since MOD-400 a buffer refuses a charge it cannot hold, so a
		// round-trip fixture has to start from a state the machine can actually be in.
		long energy0 = 734L;
		src.getEnergyStorage().setAmountUntracked(energy0);
		fill(src, 3 * FluidAmounts.BUCKET);
		src.getDataAccess().set(2, 11); // index 2 == progress

		CompoundTag tag = src.saveCustomOnly(registries);
		PolymerizerBlockEntity restored = new PolymerizerBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.fluidTank.amount != 3 * FluidAmounts.BUCKET
				|| !restored.fluidTank.fluid.is(ModContent.OIL.get())) {
			helper.fail("tank round-trip mismatch: " + restored.fluidTank.amount + " mB of "
					+ restored.fluidTank.fluid);
			return;
		}
		if (restored.getEnergyStorage().getAmount() != energy0 || restored.getDataAccess().get(2) != 11) {
			helper.fail("energy/progress round-trip mismatch: energy "
					+ restored.getEnergyStorage().getAmount() + " progress " + restored.getDataAccess().get(2));
			return;
		}
		helper.succeed();
	}
}
