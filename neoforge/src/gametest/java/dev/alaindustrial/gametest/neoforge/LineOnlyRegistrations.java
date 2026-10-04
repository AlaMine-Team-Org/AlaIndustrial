package dev.alaindustrial.gametest.neoforge;

import dev.alaindustrial.gametest.LineOnlyScenarios;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * NeoForge registrations of the scenarios that exist on ONE Minecraft line only (MOD-705) — bodies in
 * {@link LineOnlyScenarios}, Fabric wrappers in {@code LineOnlyGameTest}. {@link NeoForgeGameTests#register}
 * calls {@link #register} on both lines, so that call stays byte-equal; the content differs per line.
 */
final class LineOnlyRegistrations {

	private LineOnlyRegistrations() {}

	static void register(RegisterGameTestsEvent event) {
		NeoForgeGameTests.registerTest(event, "persistence_mod645_mc262_blockstate_tags_still_load", 40, true,
				LineOnlyScenarios::mod645_mc262BlockStateTagsStillLoad);
		NeoForgeGameTests.registerTest(event, "capsule_saved_by_262_heals_fuel_on_load", 40, true,
				LineOnlyScenarios::fun14CapsuleSavedBy262HealsFuelOnLoad);
		NeoForgeGameTests.registerTest(event, "hoe_without_transformer_passes_free", 40, true,
				LineOnlyScenarios::fun14HoeWithoutTransformerPassesFree);
		NeoForgeGameTests.registerTest(event, "hoe_tills_rooted_dirt_to_dirt", 40, true,
				LineOnlyScenarios::fun15TillsRootedDirtToDirt);
		NeoForgeGameTests.registerTest(event, "right_click_roster_declares_transformer", 40, true,
				LineOnlyScenarios::fun06RightClickRosterDeclaresTransformer);
		NeoForgeGameTests.registerTest(event, "shovel_diamond_tip_douses_lit_campfire", 40, true,
				LineOnlyScenarios::fun15DiamondTipDousesLitCampfire);
	}
}
