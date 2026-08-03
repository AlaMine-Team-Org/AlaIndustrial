package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.registry.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;

/**
 * MOD-111 L2 coverage for the portable fluid tank: persistence, portable item state, stack safety,
 * and the real Fabric fluid capability exposed to other mods.
 *
 * <p><b>MOD-310 — the loader-neutral scenario bodies live in {@link FluidTankScenarios}
 * ({@code common/src/gametest}).</b> What stays here is the Fabric wiring only: the
 * {@code @GameTest} annotation and a delegation, so the SAME scenario runs on BOTH loaders
 * (NeoForge registers it in {@code NeoForgeGameTests}). The one exception is
 * {@link #tcFluidTank001Int01_allFacesExposeTransactionalFabricStorage}, whose body asserts the
 * Fabric-only transfer API ({@code FluidStorage.SIDED}, {@code Storage<FluidVariant>},
 * {@code Transaction}) and therefore cannot move to common.
 */
public final class FluidTankGameTest {

	/** Mirrors {@code FluidTankScenarios.POS} — the rig slot the Fabric-only capability test uses. */
	private static final BlockPos TANK_POS = new BlockPos(1, 2, 1);

	@GameTest
	public void tcFluidTank001Fun02_glassWallStopsAClick(GameTestHelper helper) {
		FluidTankScenarios.tcFluidTank001Fun02_glassWallStopsAClick(helper);
	}

	@GameTest
	public void tcFluidTank001Dat01_contentsCodecRoundTrips(GameTestHelper helper) {
		FluidTankScenarios.tcFluidTank001Dat01_contentsCodecRoundTrips(helper);
	}

	@GameTest
	public void tcFluidTank001Per01_nbtAndComponentRoundTrip(GameTestHelper helper) {
		FluidTankScenarios.tcFluidTank001Per01_nbtAndComponentRoundTrip(helper);
	}

	@GameTest
	public void tcFluidTank001Safe01_filledItemIsAtomicAndUnstackable(GameTestHelper helper) {
		FluidTankScenarios.tcFluidTank001Safe01_filledItemIsAtomicAndUnstackable(helper);
	}

	@GameTest
	public void tcFluidTank001Bva01_componentAmountClampsToCapacity(GameTestHelper helper) {
		FluidTankScenarios.tcFluidTank001Bva01_componentAmountClampsToCapacity(helper);
	}

	/**
	 * Placed locally rather than through {@code FluidTankScenarios.place} on purpose: the
	 * {@code gametest-bodies-live-in-common} rule in {@code docs/tools/arch_check.py} counts
	 * {@code …Scenarios.method(…)} calls against the number of {@code @GameTest} methods, so borrowing
	 * a helper from common would make this Fabric-only body look like a twelfth delegation and quietly
	 * drop the file off the frozen debt list.
	 */
	private static FluidTankBlockEntity placeTank(GameTestHelper helper) {
		helper.setBlock(TANK_POS, ModContent.FLUID_TANK.get());
		return helper.getBlockEntity(TANK_POS, FluidTankBlockEntity.class);
	}

	@GameTest
	public void tcFluidTank001Int01_allFacesExposeTransactionalFabricStorage(GameTestHelper helper) {
		FluidTankBlockEntity tank = placeTank(helper);
		for (Direction side : Direction.values()) {
			Storage<FluidVariant> storage = FluidStorage.SIDED.find(
					helper.getLevel(), tank.getBlockPos(), side);
			if (storage == null) {
				helper.fail("missing Fabric fluid capability on face " + side);
				return;
			}
		}

		Storage<FluidVariant> storage = FluidStorage.SIDED.find(
				helper.getLevel(), tank.getBlockPos(), Direction.UP);
		try (Transaction tx = Transaction.openOuter()) {
			long inserted = storage.insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, tx);
			if (inserted != FluidConstants.BUCKET) {
				helper.fail("capability did not accept exactly one bucket");
			}
			tx.commit();
		}
		if (tank.fluidTank.amount != FluidAmounts.BUCKET || !tank.fluidTank.fluid.is(Fluids.WATER)) {
			helper.fail("Fabric droplet conversion did not store exactly 1000 mB of water");
		}

		try (Transaction tx = Transaction.openOuter()) {
			long rejected = storage.insert(FluidVariant.of(Fluids.LAVA), FluidConstants.BUCKET, tx);
			if (rejected != 0) {
				helper.fail("single-fluid tank accepted lava while it contained water");
			}
			// Deliberately no commit: the failed operation must leave the tank unchanged.
		}
		if (tank.fluidTank.amount != FluidAmounts.BUCKET || !tank.fluidTank.fluid.is(Fluids.WATER)) {
			helper.fail("rejected/rolled-back transaction changed tank contents");
		}
		helper.succeed();
	}

	@GameTest
	public void tcFluidTank001Per02_placingAFilledTankKeepsItsContents(GameTestHelper helper) {
		FluidTankScenarios.tcFluidTank001Per02_placingAFilledTankKeepsItsContents(helper);
	}

	@GameTest
	public void tcFluidTank001Fun01_bucketAndCapsuleUseRealClickRouting(GameTestHelper helper) {
		FluidTankScenarios.tcFluidTank001Fun01_bucketAndCapsuleUseRealClickRouting(helper);
	}
}
