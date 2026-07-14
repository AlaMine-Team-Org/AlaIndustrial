package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for MOD-077 (geothermal lava-input parity + lava-capsule furnace fuel). Thin Fabric
 * wrappers over the loader-neutral bodies in {@code common/.../gametest/GeothermalLavaInputScenarios}; the
 * SAME bodies run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}), so both loaders
 * exercise the geothermal block entity, the {@code VanillaBucketDeposit} interception helper, and the
 * furnace-fuel mixins identically.
 */
public class GeothermalLavaInputGameTest {

	/** @implements TC-GEO-001-FUN06 — lava capsule in the input slot drains and returns an empty capsule. */
	@GameTest
	public void tcGeo001Fun06_capsuleInSlotDrains(GameTestHelper helper) {
		GeothermalLavaInputScenarios.fun05CapsuleInSlotDrains(helper);
	}

	/** @implements TC-GEO-001-FUN07 — shift+use a lava bucket on the block loads the tank, returns an empty bucket. */
	@GameTest
	public void tcGeo001Fun07_bucketDepositViaShift(GameTestHelper helper) {
		GeothermalLavaInputScenarios.fun06BucketDepositViaShift(helper);
	}

	/** @implements TC-GEO-001-NEG07 — a full tank consumes the shift-click but keeps the bucket (silent no-op). */
	@GameTest
	public void tcGeo001Neg07_bucketFullTankNoOp(GameTestHelper helper) {
		GeothermalLavaInputScenarios.neg06BucketFullTankNoOp(helper);
	}

	/** @implements TC-CAPS-001-FUN04 — lava capsule is furnace fuel (lava-bucket burn time); water capsule is not. */
	@GameTest
	public void tcCaps001Fun04_lavaCapsuleIsFurnaceFuel(GameTestHelper helper) {
		GeothermalLavaInputScenarios.fun04LavaCapsuleIsFurnaceFuel(helper);
	}

	/** @implements TC-CAPS-001-FUN05 — a lava capsule caps to one in a furnace fuel slot (no tare loss). */
	@GameTest
	public void tcCaps001Fun05_furnaceFuelSlotCapsOne(GameTestHelper helper) {
		GeothermalLavaInputScenarios.fun05FurnaceFuelSlotCapsOne(helper);
	}
}
