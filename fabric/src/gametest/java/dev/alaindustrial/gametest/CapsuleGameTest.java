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

	/**
	 * @implements TC-CAPS-001-FUN05 — MOD-099: empty capsule right-click on the pump block via the REAL
	 * ServerPlayerGameMode routing pulls one bucket (regression guard for GUI-eats-click ordering).
	 */
	@GameTest
	public void tcCaps001Fun05_useRoutingFill(GameTestHelper helper) {
		CapsuleScenarios.fun05UseRoutingFill(helper);
	}

	/**
	 * @implements TC-CAPS-001-FUN06 — MOD-099: filled capsule right-click on the pump block via the REAL
	 * ServerPlayerGameMode routing empties into the tank (regression guard, insertion direction).
	 */
	@GameTest
	public void tcCaps001Fun06_useRoutingEmpty(GameTestHelper helper) {
		CapsuleScenarios.fun06UseRoutingEmpty(helper);
	}

	/**
	 * @implements TC-CAPS-001-FUN07 — MOD-099: OFF-hand capsule must exchange exactly once (no
	 * double Item.useOn fall-through); tank goes 2 buckets → 1, not → 0.
	 */
	@GameTest
	public void tcCaps001Fun07_useRoutingOffHand(GameTestHelper helper) {
		CapsuleScenarios.fun07UseRoutingOffHand(helper);
	}

	/**
	 * @implements TC-CAPS-001-FUN08 — MOD-099: every pump sync channel fits a signed short, the encoding
	 * ClientboundContainerSetDataPacket uses (regression guard for the ARGB-colour channel that truncated).
	 */
	@GameTest
	public void tcCaps001Fun08_syncChannelsFitShort(GameTestHelper helper) {
		CapsuleScenarios.fun08SyncChannelsFitShort(helper);
	}

	/**
	 * @implements TC-CAPS-001-FUN09 — MOD-099: the client PumpMenu stub declares the same channel count the
	 * block entity projects.
	 */
	@GameTest
	public void tcCaps001Fun09_menuStubWidthMatches(GameTestHelper helper) {
		CapsuleScenarios.fun09MenuStubWidthMatches(helper);
	}

	/**
	 * @implements TC-CAPS-001-FUN10 — MOD-107 regression guard: a vanilla lava bucket in the pump's fill
	 * slot still empties into the tank (behaviour that predates the item-fluid bridge and had no coverage).
	 */
	@GameTest
	public void tcCaps001Fun10_bucketFillsTankFromSlot(GameTestHelper helper) {
		CapsuleScenarios.fun10BucketFillsTankFromSlot(helper);
	}

	/**
	 * @implements TC-CAPS-001-FUN11 — MOD-107 regression guard: an empty bucket in the drain slot still
	 * fills from the tank.
	 */
	@GameTest
	public void tcCaps001Fun11_bucketDrainsTankFromSlot(GameTestHelper helper) {
		CapsuleScenarios.fun11BucketDrainsTankFromSlot(helper);
	}

	/**
	 * @implements TC-CAPS-001-FUN12 — MOD-107: a filled capsule in the pump's fill slot empties into the
	 * tank and leaves an empty capsule (the player-reported "capsule just sits in the slot").
	 */
	@GameTest
	public void tcCaps001Fun12_capsuleFillsTankFromSlot(GameTestHelper helper) {
		CapsuleScenarios.fun12CapsuleFillsTankFromSlot(helper);
	}

	/**
	 * @implements TC-CAPS-001-FUN13 — MOD-107: an empty capsule in the drain slot fills from the tank,
	 * carrying the tank's fluid out.
	 */
	@GameTest
	public void tcCaps001Fun13_capsuleDrainsTankFromSlot(GameTestHelper helper) {
		CapsuleScenarios.fun13CapsuleDrainsTankFromSlot(helper);
	}
}
