package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.FermenterBlockEntity;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * Loader-neutral gametest bodies for the Fermenter (MOD-146, suite TC-FERM-001). Wrapped by the
 * Fabric {@code FermenterGameTest} suite and registered on the NeoForge lane
 * ({@code NeoForgeGameTests}, {@code fermenter_*}), so both loaders run the SAME bodies.
 *
 * <p>What these exist to pin down is the part of the machine that lives OUTSIDE the recipe system,
 * and is therefore invisible to every recipe test:
 *
 * <ul>
 * <li><b>The price tiers.</b> The recipe decides the item and its odds; the fluid yield is read from
 * the input's TAG by the block entity. Nothing in the recipe system enforces that, so a tier read
 * that broke — or that ran after the input was consumed, when the slot is empty and no tag matches
 * — would quietly pay every batch at the cheap rate and still look like a working machine.
 * {@link #fun02RichTierBrewsMoreThanPoor} is the only thing standing between that and a shipped
 * bug.</li>
 * <li><b>The water gate.</b> Water is not part of the recipe either (see the block entity), so a
 * dropped check would let the machine brew out of an empty tank. {@link #con01DryTankBlocksWork}
 * covers it.</li>
 * <li><b>A full output tank stalls rather than voids.</b> {@link #con02FullBiofuelTankStalls}.</li>
 * </ul>
 *
 * <p><b>Biomass is deliberately never asserted.</b> It is rolled against the recipe's {@code chance}
 * (6–50 % by tier), so any assertion on it would be a coin flip that fails on some runs and passes
 * on others — the shape of flake that gets a suite muted. The fluid is deterministic, and it is what
 * these bodies measure.
 */
public final class FermenterScenarios {

	private FermenterScenarios() {
	}

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/** Far above one batch's cost (600 EU), set directly so the tier packet cap is bypassed. */
	private static final long AMPLE_EU = 8000L;

	/** Ticks to drive: one full batch plus slack for the scaled-duration knob. */
	private static int driveTicks() {
		return Config.scaledDuration(Config.fermenterDuration) + 20;
	}

	private static FermenterBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.FERMENTER.get());
		FermenterBlockEntity be = helper.getBlockEntity(POS, FermenterBlockEntity.class);
		if (be == null) {
			helper.fail("fermenter block entity missing after placement");
		}
		return be;
	}

	/** Put water straight into the tank — these tests are about what the machine does with it. */
	private static void fillWater(FermenterBlockEntity be, long mb) {
		be.waterTank.fluid = FluidHolder.of(Fluids.WATER);
		be.waterTank.amount = mb;
	}

	/** Stock the machine for one batch of {@code organic}, with water and power to spare. */
	private static FermenterBlockEntity stocked(GameTestHelper helper, ItemStack organic) {
		FermenterBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(FermenterBlockEntity.ORGANIC_SLOT, organic);
		fillWater(be, FermenterBlockEntity.TANK_CAPACITY);
		return be;
	}

	/**
	 * Drive with the buffer topped up every tick, the way a connected cable keeps it fed. One batch
	 * costs 600 EU while {@code machineBuffer} holds 800 — comfortable, but the same pattern the
	 * Galvanic Bath needs, and cheap insurance against a future duration change.
	 */
	private static void drivePowered(FermenterBlockEntity be, GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
			AlaGameTestHelper.drive(be, helper, 1);
		}
	}

	// ── FUN01: a batch brews biofuel and pays for it ────────────────────────────────────────────────

	/**
	 * Poor-tier organic + water + EU → biofuel in the output tank, water gone from the input tank,
	 * and the input stack shorter by the recipe's batch size.
	 */
	public static void fun01BrewsBiofuelAndSpendsWater(GameTestHelper helper) {
		// Four poisonous potatoes: the poor tier consumes four per batch.
		FermenterBlockEntity be = stocked(helper, new ItemStack(Items.POISONOUS_POTATO, 8));
		long waterBefore = be.waterTank.amount;

		drivePowered(be, helper, driveTicks());

		if (be.biofuelTank.amount <= 0) {
			helper.fail("a completed batch brewed no biofuel at all");
		}
		if (!be.biofuelTank.fluid.equals(FluidHolder.of(ModContent.BIOFUEL.get()))) {
			helper.fail("the output tank holds something other than biofuel: " + be.biofuelTank.fluid);
		}
		if (be.waterTank.amount >= waterBefore) {
			helper.fail("a batch completed without drinking any water");
		}
		// Deliberately NOT an exact count: the gametest world ticks the block entity alongside this
		// driver, so the run completes an unpredictable number of batches. What must hold is that it
		// ate its input in whole batches of four — the poor tier's price.
		int eaten = 8 - be.getItem(FermenterBlockEntity.ORGANIC_SLOT).getCount();
		if (eaten == 0) {
			helper.fail("a batch completed without eating any input");
		}
		if (eaten % 4 != 0) {
			helper.fail("the poor tier must consume four per batch, but " + eaten + " went missing");
		}
		helper.succeed();
	}

	// ── FUN02: the tier is read from the input, not assumed ─────────────────────────────────────────

	/**
	 * The whole economy of the machine in one assertion: the same batch, same time, same energy, but
	 * a rich input must return strictly more biofuel than a poor one.
	 *
	 * <p>Compares the two yields rather than pinning either number, so a balance pass that retunes
	 * the config keeps this test meaningful instead of turning it into a copy of {@code Config}.
	 */
	public static void fun02RichTierBrewsMoreThanPoor(GameTestHelper helper) {
		FermenterBlockEntity poor = stocked(helper, new ItemStack(Items.POISONOUS_POTATO, 16));
		drivePowered(poor, helper, driveTicks());
		int poorEaten = 16 - poor.getItem(FermenterBlockEntity.ORGANIC_SLOT).getCount();
		long poorYield = poor.biofuelTank.amount;

		// Same block position, rebuilt: one structure per test, so the second run reuses the cell.
		helper.setBlock(POS, net.minecraft.world.level.block.Blocks.AIR);
		FermenterBlockEntity rich = stocked(helper, new ItemStack(Items.GOLDEN_CARROT, 16));
		drivePowered(rich, helper, driveTicks());
		int richEaten = 16 - rich.getItem(FermenterBlockEntity.ORGANIC_SLOT).getCount();
		long richYield = rich.biofuelTank.amount;

		if (poorEaten == 0 || richEaten == 0) {
			helper.fail("a tier ran no batch at all: poor ate " + poorEaten + ", rich ate " + richEaten);
		}
		// Per BATCH, not per run: the driver and the world both tick the machine, so the two runs do
		// not necessarily complete the same number of batches. Batches are derived from the input
		// eaten at each tier's own price — four per batch for poor, one for rich.
		if (poorEaten % 4 != 0) {
			helper.fail("the poor tier must consume four per batch, but " + poorEaten + " went missing");
		}
		long poorPerBatch = poorYield / (poorEaten / 4);
		long richPerBatch = richYield / richEaten;

		if (richPerBatch <= poorPerBatch) {
			helper.fail("the rich tier must out-yield the poor one per batch, got rich=" + richPerBatch
					+ " vs poor=" + poorPerBatch + " mB — the tier is probably read AFTER the input is "
					+ "consumed, when the slot is empty and no tag matches");
		}
		helper.succeed();
	}

	// ── CON01/CON02: the two gates the recipe system cannot enforce ────────────────────────────────

	/** A dry tank stops the machine: water is a config cost, so nothing else checks it. */
	public static void con01DryTankBlocksWork(GameTestHelper helper) {
		FermenterBlockEntity be = place(helper);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(FermenterBlockEntity.ORGANIC_SLOT, new ItemStack(Items.POISONOUS_POTATO, 8));
		// No water at all.
		drivePowered(be, helper, driveTicks());

		if (be.biofuelTank.amount != 0) {
			helper.fail("the fermenter brewed " + be.biofuelTank.amount + " mB out of an empty tank");
		}
		if (be.getItem(FermenterBlockEntity.ORGANIC_SLOT).getCount() != 8) {
			helper.fail("a machine with a dry tank still ate its input");
		}
		helper.succeed();
	}

	/** A full output tank stalls the batch; nothing is voided and no input is eaten. */
	public static void con02FullBiofuelTankStalls(GameTestHelper helper) {
		FermenterBlockEntity be = stocked(helper, new ItemStack(Items.POISONOUS_POTATO, 8));
		be.biofuelTank.fluid = FluidHolder.of(ModContent.BIOFUEL.get());
		be.biofuelTank.amount = FermenterBlockEntity.TANK_CAPACITY;

		drivePowered(be, helper, driveTicks());

		if (be.biofuelTank.amount != FermenterBlockEntity.TANK_CAPACITY) {
			helper.fail("a full tank changed volume: " + be.biofuelTank.amount);
		}
		if (be.getItem(FermenterBlockEntity.ORGANIC_SLOT).getCount() != 8) {
			helper.fail("a stalled machine ate its input anyway — that is a silent dupe of nothing");
		}
		helper.succeed();
	}
}
