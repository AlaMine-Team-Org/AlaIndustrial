package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric lane for the MOD-290 common-tag guards. Bodies are loader-neutral and live in
 * {@link RecipeTagScenarios}; the NeoForge lane registers the same two in
 * {@code NeoForgeGameTests}.
 */
public class RecipeTagGameTest {

	/** @implements TC-TAGS-001 — consumed common tags are non-empty on this loader. */
	@GameTest
	public void tcTags01_consumedCommonTagsAreNonEmpty(GameTestHelper helper) {
		RecipeTagScenarios.consumedCommonTagsAreNonEmpty(helper);
	}

	/** @implements TC-TAGS-003 — optional foreign tag references in the mod's tag files resolve. */
	@GameTest
	public void tcTags03_referencedForeignTagsResolve(GameTestHelper helper) {
		RecipeTagScenarios.referencedForeignTagsResolve(helper);
	}

	/** @implements TC-TAGS-002 — no mod recipe carries an ingredient that matches nothing. */
	@GameTest
	public void tcTags02_everyModRecipeIngredientResolves(GameTestHelper helper) {
		RecipeTagScenarios.everyModRecipeIngredientResolves(helper);
	}

	/** @implements TC-TAGS-004 — a foreign item reaches the mod's tags through the c: conventions. */
	@GameTest
	public void tcTags04_foreignItemReachesOurTags(GameTestHelper helper) {
		ForeignMaterialScenarios.tags04ForeignItemReachesOurTags(helper);
	}

	/** @implements TC-TAGS-005 — the macerator processes a foreign mod's tin ore. */
	@GameTest
	public void tcTags05_maceratorGrindsForeignOre(GameTestHelper helper) {
		ForeignMaterialScenarios.tags05MaceratorGrindsForeignOre(helper);
	}

	/** @implements TC-TAGS-006 — a mod craft written on a c: tag accepts a foreign ingot. */
	@GameTest
	public void tcTags06_foreignIngotFeedsOurCraft(GameTestHelper helper) {
		ForeignMaterialScenarios.tags06ForeignIngotFeedsOurCraft(helper);
	}
}
