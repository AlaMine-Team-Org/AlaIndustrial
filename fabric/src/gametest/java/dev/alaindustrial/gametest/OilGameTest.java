package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the oil fluid (MOD-238, suite TC-OIL-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/OilScenarios} and the SAME bodies run on
 * the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}, {@code oil_*}) — both loaders
 * exercise identical oil logic (bucket/capsule/pump exchange, the no-source-conversion guarantee,
 * the {@code oilBurns} gate, the viscous spread profile).
 */
public class OilGameTest {

	/**
	 * @implements TC-OIL-001-FUN01 — the oil bucket places an oil SOURCE and scooping it back returns
	 * a full oil bucket, leaving the cell fluid-free.
	 */
	@GameTest
	public void tcOil001Fun01_bucketPlaceAndPickup(GameTestHelper helper) {
		OilScenarios.fun01BucketPlaceAndPickup(helper);
	}

	/**
	 * @implements TC-OIL-001-FUN02 — the vacuum capsule picks an oil source up through the REAL
	 * useItem routing (the FlowingFluid gate passes for oil) and the filled capsule places it back.
	 */
	@GameTest
	public void tcOil001Fun02_capsulePickupAndPlace(GameTestHelper helper) {
		OilScenarios.fun02CapsulePickupAndPlace(helper);
	}

	/**
	 * @implements TC-OIL-001-FUN03 — the pump drains a three-source oil lake into an adjacent
	 * portable fluid tank, one bucket per EU charge, with the first bucket observed mid-run.
	 */
	@GameTest(maxTicks = 340)
	public void tcOil001Fun03_pumpDrainsOilLakeIntoTank(GameTestHelper helper) {
		OilScenarios.fun03PumpDrainsOilLakeIntoTank(helper);
	}

	/**
	 * @implements TC-OIL-001-NEG01 — a gap between two oil sources fills with FLOWING oil but never
	 * converts to a source (canConvertToSource=false); a water control rig proves the geometry does
	 * convert for a converting fluid.
	 */
	@GameTest(maxTicks = 220)
	public void tcOil001Neg01_gapNeverBecomesSource(GameTestHelper helper) {
		OilScenarios.neg01GapNeverBecomesSource(helper);
	}

	/**
	 * @implements TC-OIL-001-FUN04 — the oilBurns gate through a real flint-and-steel click: ON, the
	 * oil cell becomes fire and the lighter takes damage; OFF, the same click changes nothing.
	 */
	@GameTest(maxTicks = 200)
	public void tcOil001Fun04_burnGateOnThenOff(GameTestHelper helper) {
		OilScenarios.fun04BurnGateOnThenOff(helper);
	}

	/**
	 * @implements TC-OIL-001-FUN05 — chain reaction: lighting one end of a three-source oil trench
	 * consumes the whole trench (the BlockTags.FIRE neighbour trigger, one cell per ignition delay).
	 */
	@GameTest(maxTicks = 160)
	public void tcOil001Fun05_burnSpreadsAcrossPool(GameTestHelper helper) {
		OilScenarios.fun05BurnSpreadsAcrossPool(helper);
	}

	/**
	 * @implements TC-OIL-001-NEG02 — a LAVA neighbour never ignites oil, even with oilBurns=true
	 * (worldgen puts deposits flush against lava lakes; the old behaviour burned them away).
	 */
	@GameTest(maxTicks = 140)
	public void tcOil001Neg02_lavaNeighbourNeverIgnites(GameTestHelper helper) {
		OilScenarios.neg02LavaNeighbourNeverIgnites(helper);
	}

	/**
	 * @implements TC-OIL-001-FUN06 — a dispenser empties an oil bucket into the cell it faces and
	 * keeps the empty bucket (vanilla registers filled-bucket dispensing per item, by name).
	 */
	@GameTest(maxTicks = 100)
	public void tcOil001Fun06_dispenserEmptiesOilBucket(GameTestHelper helper) {
		OilScenarios.fun06DispenserEmptiesOilBucket(helper);
	}

	/**
	 * @implements TC-OIL-001-PRF01 — viscous spread profile: drop-off 2 pins flowing amounts 6/4/2
	 * along a flat trench and a hard stop at distance 3 (water would wet every cell).
	 */
	@GameTest(maxTicks = 300)
	public void tcOil001Prf01_viscousSpreadProfile(GameTestHelper helper) {
		OilScenarios.prf01ViscousSpreadProfile(helper);
	}

	/**
	 * @implements TC-OIL-001-FUN07 — a torch placed into an oil source comes out oil-logged and
	 * reports a full oil source to the renderer, instead of leaving an air pocket in the pool.
	 */
	@GameTest(maxTicks = 60)
	public void tcOil001Fun07_torchLogsWithOil(GameTestHelper helper) {
		OilScenarios.fun08TorchLogsWithOil(helper);
	}

	/**
	 * @implements TC-OIL-001-FUN08 — an oil bucket reaching the inventory awards "Black Gold"
	 * (alaindustrial:first_oil). The tree is not retroactive, so a node that silently stops firing
	 * cannot be handed back to the players who missed it.
	 */
	@GameTest
	public void tcOil001Fun08_firstOilAwardsTheAdvancement(GameTestHelper helper) {
		OilScenarios.fun09FirstOilAwardsTheAdvancement(helper);
	}

	/**
	 * @implements TC-OIL-001-NEG03 — an entity inside oil sinks with vanilla AIR physics and never
	 * hangs as if oil were a solid block; a water shaft controls that vanilla fluids keep vanilla
	 * movement. Guards the MOD-495 regression that NeoForge 26.2.0.49-beta made reachable.
	 */
	@GameTest(maxTicks = 80)
	public void tcOil001Neg03_entitySinksInsteadOfHanging(GameTestHelper helper) {
		OilScenarios.neg03EntitySinksInsteadOfHanging(helper);
	}

	/**
	 * @implements TC-OIL-001-FUN10 — all three world-placeable fluids damp a fall and clear fall
	 * distance, ordered by viscosity (air &gt; diesel &gt; fuel oil &gt; crude). Guards the MOD-496
	 * roster: a fluid that loses its immersion profile falls like air and the order collapses.
	 */
	@GameTest(maxTicks = 80)
	public void tcOil001Fun10_immersionDampsFallInViscosityOrder(GameTestHelper helper) {
		OilScenarios.fun10ImmersionDampsFallInViscosityOrder(helper);
	}
}
