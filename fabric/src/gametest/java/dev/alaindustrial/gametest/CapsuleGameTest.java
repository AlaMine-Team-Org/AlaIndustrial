package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Vacuum Capsule (MOD-063, suite TC-CAPS-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/CapsuleScenarios} and the SAME bodies run on
 * the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders exercise identical
 * capsule logic (the {@code capsule_fluid} component, per-fluid stacking, the tank exchange via FluidLookup).
 */
public class CapsuleGameTest {

	/** @implements TC-CAPS-001-PER01 — capsule_fluid component round-trip; writing empty removes it. */
	@GameTest
	public void tcCaps001Per01_componentRoundTrip(GameTestHelper helper) {
		CapsuleScenarios.per01ComponentRoundTrip(helper);
	}

	/** @implements TC-CAPS-001-FUN01 — same fluid stacks (to 16), different fluids do not merge. */
	@GameTest
	public void tcCaps001Fun01_stackingByFluid(GameTestHelper helper) {
		CapsuleScenarios.fun01StackingByFluid(helper);
	}

	/** @implements TC-CAPS-001-FUN02 — empty capsule pulls one bucket from a mod tank and becomes filled. */
	@GameTest
	public void tcCaps001Fun02_fillFromTank(GameTestHelper helper) {
		CapsuleScenarios.fun02FillFromTank(helper);
	}

	/** @implements TC-CAPS-001-FUN03 — filled capsule pushes its bucket into a mod tank and becomes empty. */
	@GameTest
	public void tcCaps001Fun03_emptyIntoTank(GameTestHelper helper) {
		CapsuleScenarios.fun03EmptyIntoTank(helper);
	}
}
