package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for kok sagyz (MOD-537). Thin Fabric wrappers: the bodies are loader-neutral
 * in {@code common/.../gametest/KokSagyzScenarios} and the SAME bodies run on the NeoForge
 * {@code gameTestServer} lane ({@code dev.alaindustrial.gametest.neoforge.NeoForgeGameTests}), so
 * the rubber dandelion behaves identically on both loaders.
 *
 * <p>Growth is driven by bone meal ({@code performBonemeal}), which is not gated by light — the
 * rig is a closed box, so the random-tick light floor would make every growth scenario
 * non-deterministic here. Covers the age stages, the two-deep root column, the harvest/regrow
 * cycle of the tip, the plant-death paths, the two-deep planting rule, the maceration recipe and
 * the scythe/drone tip harvests.
 */
public class KokSagyzGameTest {

	/** @implements MOD-537 — bone meal walks AGE 0→3 one stage at a time; maturity alone roots not. */
	@GameTest
	public void mod537_stagesAdvance(GameTestHelper helper) {
		KokSagyzScenarios.mod537StagesAdvance(helper);
	}

	/** @implements MOD-537 — the root grows exactly two deep (root[tip=false] then tip=true). */
	@GameTest
	public void mod537_rootGrowsTwoDeep(GameTestHelper helper) {
		KokSagyzScenarios.mod537RootGrowsTwoDeep(helper);
	}

	/** @implements MOD-537 — digging the tip pays 1 root, refills with dirt, keeps the plant. */
	@GameTest
	public void mod537_tipBreakDropsRootAndKeepsFlower(GameTestHelper helper) {
		KokSagyzScenarios.mod537TipBreakDropsRootAndKeepsFlower(helper);
	}

	/** @implements MOD-537 — the tip regrows after a harvest and pays out a second time. */
	@GameTest
	public void mod537_tipRegrows(GameTestHelper helper) {
		KokSagyzScenarios.mod537TipRegrows(helper);
	}

	/** @implements MOD-537 — digging the UPPER root keeps the flower, pays no root item, regrows as an upper root. */
	@GameTest
	public void mod537_upperRootBreakKeepsFlower(GameTestHelper helper) {
		KokSagyzScenarios.mod537UpperRootBreakKeepsFlower(helper);
	}

	/** @implements MOD-537 — breaking the flower never drops the root item and leaves both roots in the ground. */
	@GameTest
	public void mod537_flowerBreakGivesNoRoot(GameTestHelper helper) {
		KokSagyzScenarios.mod537FlowerBreakGivesNoRoot(helper);
	}

	/** @implements MOD-537 — one block of any soil is enough: dirt, grass, podzol, mycelium, mud, sand, farmland. */
	@GameTest
	public void mod537PlacementAcceptsAnySingleSoil(GameTestHelper helper) {
		KokSagyzScenarios.mod537PlacementAcceptsAnySingleSoil(helper);
	}

	/** @implements MOD-537 — the worldgen feature plants a mature flower on grass, the real surface. */
	@GameTest
	public void mod537_worldgenFeaturePlantsOnGrass(GameTestHelper helper) {
		KokSagyzScenarios.mod537WorldgenFeaturePlantsOnGrass(helper);
	}

	/** @implements MOD-537 — 2× root macerates into 1× raw rubber + 1× inulin secondary, 300 EU. */
	@GameTest
	public void mod537_macerationYieldsRubberAndInulin(GameTestHelper helper) {
		KokSagyzScenarios.mod537MacerationYieldsRubberAndInulin(helper);
	}

	/** @implements MOD-537 — scythe crop mode digs the tip and leaves the plant standing. */
	@GameTest
	public void mod537_scytheHarvestsTip(GameTestHelper helper) {
		KokSagyzScenarios.mod537ScytheHarvestsTip(helper);
	}

	/** @implements MOD-537 — the garden drone cuts the flower for seeds and never touches the roots. */
	@GameTest(maxTicks = 200)
	public void mod537_droneCutsFlowerNotRoot(GameTestHelper helper) {
		KokSagyzScenarios.mod537DroneCutsFlowerNotRoot(helper);
	}
}
