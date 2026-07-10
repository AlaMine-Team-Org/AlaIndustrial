package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral world-based fluid gametest bodies (MOD-028), the fluid counterpart of
 * {@link CoreEnergyScenarios}. Each scenario is a plain {@code Consumer<GameTestHelper>} using only the
 * vanilla {@code GameTestHelper} + loader-neutral content ({@link ModContent}, the common
 * {@code BlockEntity} classes) — no loader-specific gametest infrastructure. Both the Fabric
 * {@code @GameTest} suite ({@code FluidGameTest}, which has broader case coverage) and the NeoForge
 * {@code gameTestServer} lane (via {@code dev.alaindustrial.gametest.neoforge.NeoForgeGameTests}) exercise
 * the SAME fluid core — this is the coverage that proves the {@code FluidPort}/{@code FluidTank}
 * abstraction actually works end to end on NeoForge (capability resolves, transactions commit, lava
 * becomes EU), not just that it compiles.
 */
public final class CoreFluidScenarios {

	private CoreFluidScenarios() {
	}

	private static BlockEntity be(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	private static void tick(GameTestHelper helper, BlockEntity be) {
		if (be == null) {
			return;
		}
		BlockPos p = be.getBlockPos();
		BlockState st = helper.getLevel().getBlockState(p);
		if (be instanceof PumpBlockEntity pump) {
			pump.serverTick(helper.getLevel(), p, st);
		} else if (be instanceof GeothermalGeneratorBlockEntity geo) {
			geo.serverTick(helper.getLevel(), p, st);
		}
	}

	// ── scenario: lava source -> pump -> geothermal tank -> EU ────────────────────────────────────

	private static final BlockPos LAVA = new BlockPos(1, 2, 1);
	private static final BlockPos PUMP = new BlockPos(2, 2, 1);
	private static final BlockPos GEO = new BlockPos(3, 2, 1);

	/**
	 * End-to-end: a lava source feeds an EU-powered pump, which moves lava into an adjacent geothermal
	 * generator's tank; the generator then produces EU from that fluid — proving the NeoForge
	 * {@code Capabilities.Fluid.BLOCK} capability resolves between our own blocks and the transaction
	 * bridge commits real tank state. Mirrors the Fabric {@code FluidGameTest.tcFluidPump_lavaSourceToGeothermal}
	 * and pins the SAME exact numbers (derived from {@link Config}) that scenario asserts, so a
	 * conversion-factor divergence between the two loaders (e.g. a fraction-of-a-bucket slip) cannot pass
	 * both suites — this is the cross-loader numeric parity oracle for the fluid feature.
	 *
	 * <p>Structured in two phases, exactly like the Fabric counterpart: transport (pump -> geo tank) is
	 * observed via a peak sample taken right after the pump's push but BEFORE the geo's own {@code produce}
	 * runs in that same tick — the geo drains a full bucket from its tank into {@code lavaTicks} the instant
	 * it has one, so the tank's End-of-loop amount is 0 and cannot be used as the transport oracle. Then the
	 * pump is removed and the geo ticks alone so the EU it accumulates is an exact, uncontended multiple of
	 * {@code Config.geothermalEuPerTick}.
	 */
	public static void sourceToPumpToGeoToEu(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos lavaAbs = helper.absolutePos(LAVA);
		level.setBlockAndUpdate(lavaAbs, Blocks.LAVA.defaultBlockState());
		helper.setBlock(PUMP, ModContent.PUMP.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.WEST));

		if (!(be(helper, PUMP) instanceof PumpBlockEntity pump)) {
			helper.fail("pump block entity missing after placement");
			return;
		}

		// Phase 1a: tick the pump alone (geo not yet placed) so the single lava source bucket is
		// acquired into the pump's own tank, not immediately pushed onward (mirrors the Fabric rig).
		pump.getEnergyStorage().amount = Config.pumpEuPerBucket;
		tick(helper, pump);
		boolean sourceGone = !level.getFluidState(lavaAbs)
				.isSourceOfType(net.minecraft.world.level.material.Fluids.LAVA);
		long pumpTankPeak = pump.fluidTank.amount;

		// Phase 1b: place the geo sink and let the pump push its held bucket across. Sample the geo tank
		// right after the push, BEFORE geo's own tick drains it into lavaTicks.
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get().defaultBlockState());
		if (!(be(helper, GEO) instanceof GeothermalGeneratorBlockEntity geo)) {
			helper.fail("geothermal generator block entity missing after placement");
			return;
		}
		pump.getEnergyStorage().amount = Config.pumpEuPerBucket;
		tick(helper, pump);
		pumpTankPeak = Math.max(pumpTankPeak, pump.fluidTank.amount);
		long geoTankPeak = geo.fluidTank.amount;

		// Phase 2: remove the pump so its EU buffer can't siphon the generator's own output, then tick
		// ONLY the generator so the EU it makes from the delivered bucket accumulates exactly.
		level.setBlockAndUpdate(helper.absolutePos(PUMP), Blocks.AIR.defaultBlockState());
		long geoEnergyBefore = geo.getEnergyStorage().getAmount();
		int burnTicks = 40;
		for (int i = 0; i < burnTicks; i++) {
			tick(helper, geo);
		}
		long geoEnergyAfter = geo.getEnergyStorage().getAmount();

		long expectedGeoTankPeak = dev.alaindustrial.core.FluidAmounts.BUCKET;
		long expectedEuGain = (long) burnTicks * Config.geothermalEuPerTick;
		long actualEuGain = geoEnergyAfter - geoEnergyBefore;

		boolean pumpAcquiredExactlyOneBucket = pumpTankPeak == dev.alaindustrial.core.FluidAmounts.BUCKET;
		boolean geoReceivedExactlyOneBucket = geoTankPeak == expectedGeoTankPeak;
		boolean producedExactEu = actualEuGain == expectedEuGain;

		if (!(sourceGone && pumpAcquiredExactlyOneBucket && geoReceivedExactlyOneBucket && producedExactEu)) {
			helper.fail("fluid pump (NeoForge): sourceGone=" + sourceGone
					+ " pumpTankPeak=" + pumpTankPeak + " (expected " + dev.alaindustrial.core.FluidAmounts.BUCKET + ")"
					+ " geoTankPeak=" + geoTankPeak + " (expected " + expectedGeoTankPeak + ")"
					+ " euGain=" + actualEuGain + " (expected " + expectedEuGain + ")");
			return;
		}
		helper.succeed();
	}
}
