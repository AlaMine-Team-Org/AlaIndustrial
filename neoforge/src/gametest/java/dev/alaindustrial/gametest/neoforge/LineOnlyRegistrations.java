package dev.alaindustrial.gametest.neoforge;

import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * NeoForge registrations of the scenarios that exist on ONE Minecraft line only (MOD-705) — bodies in
 * {@code LineOnlyScenarios}, Fabric wrappers in {@code LineOnlyGameTest}. {@link NeoForgeGameTests#register}
 * calls {@link #register} on both lines, so that call stays byte-equal; the content differs per line.
 * On 26.2 the only line-only scenarios are NeoForge-only ones: the {@code ItemAbility} answers of the
 * right-click tools (MOD-704), whose bodies are in {@link ElectricToolAbilityNeoForgeScenarios}.
 */
final class LineOnlyRegistrations {

	private LineOnlyRegistrations() {}

	static void register(RegisterGameTestsEvent event) {
		// MOD-704: the ItemAbility half of the four right-click tools — a NeoForge 26.2 mechanism only.
		NeoForgeGameTests.registerTest(event, "right_click_tools_ability_answers", 40, true,
				ElectricToolAbilityNeoForgeScenarios::abilitiesAnswerAsTheVanillaTools);
		NeoForgeGameTests.registerTest(event, "right_click_tools_simulated_probe_writes_nothing", 40, true,
				ElectricToolAbilityNeoForgeScenarios::simulatedProbeAnswersAndWritesNothing);
	}
}
