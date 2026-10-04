package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wrappers of the scenarios that exist on ONE Minecraft line only (MOD-705) — bodies in
 * {@link LineOnlyScenarios}, NeoForge registrations in {@code LineOnlyRegistrations}. The class name is
 * the same on both lines, so its line in {@code fabric.mod.json} is too; the content differs per line.
 */
public class LineOnlyGameTest {

	/**
	 * Hand-built 26.2 {@code {Name[, Properties]}} tags load into the incubator's dome and the
	 * kok-sagyz root's soil, and a re-save writes the 26.3 shape. Body:
	 * {@link LineOnlyScenarios#mod645_mc262BlockStateTagsStillLoad}.
	 */
	@GameTest
	public void mod645Per01_mc262BlockStateTagsStillLoad(GameTestHelper helper) {
		LineOnlyScenarios.mod645_mc262BlockStateTagsStillLoad(helper);
	}

	/**
	 * @implements TC-CAPS-001-FUN14 — MOD-226: a capsule saved by 26.2 (fluid, no cooking_fuel) heals its
	 * fuel component the moment it loads; a water capsule stays inert.
	 */
	@GameTest
	public void tcCaps001Fun14_capsuleSavedBy262HealsFuelOnLoad(GameTestHelper helper) {
		LineOnlyScenarios.fun14CapsuleSavedBy262HealsFuelOnLoad(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN14 — a hoe with the block transformer stripped from the stack PASSES the
	 *     click on and spends nothing: applicability on 26.3 is a property of the stack (MOD-226).
	 */
	@GameTest
	public void tcHoe001Fun14_hoeWithoutTransformerPassesFree(GameTestHelper helper) {
		LineOnlyScenarios.fun14HoeWithoutTransformerPassesFree(helper);
	}

	/**
	 * @implements TC-HOE-001-FUN15 — rooted dirt is a hoe conversion on 26.3's second transformer rule:
	 *     becomes plain dirt, drops its hanging root, costs the till (MOD-226).
	 */
	@GameTest
	public void tcHoe001Fun15_tillsRootedDirtToDirt(GameTestHelper helper) {
		LineOnlyScenarios.fun15TillsRootedDirtToDirt(helper);
	}

	/**
	 * @implements TC-SHOVEL-001-FUN15 — the diamond-tipped upgrade douses a lit campfire: on 26.3 the
	 *     douses_campfires tag (#minecraft:shovels, which we append to) answers, not a shovel class
	 *     (MOD-226).
	 */
	@GameTest
	public void tcShovel001Fun15_diamondTipDousesLitCampfire(GameTestHelper helper) {
		LineOnlyScenarios.fun15DiamondTipDousesLitCampfire(helper);
	}

	/**
	 * @implements TC-ETOOL-001-FUN06 — every right-click tool of the line carries the block transformer
	 *     its domain names in its BAKED default components; a delayed component that never resolved at
	 *     registry bake is invisible to the source-level gate (MOD-226).
	 */
	@GameTest
	public void tcEtool001Fun06_rightClickRosterDeclaresTransformer(GameTestHelper helper) {
		LineOnlyScenarios.fun06RightClickRosterDeclaresTransformer(helper);
	}
}
