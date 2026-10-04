package dev.alaindustrial.gametest.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.RosterEntry;
import dev.alaindustrial.gametest.ScenarioRoster;
import dev.alaindustrial.gametest.compat.GameTestData;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The NeoForge lane's replay of {@link ScenarioRoster#ALL} (MOD-717, ADR-038) through the same two
 * vanilla registries the Fabric replay uses ({@code ScenarioRosterFabric}):
 * <ol>
 *   <li>each body goes into {@code minecraft:test_function} from {@link RegisterEvent} — NeoForge posts it
 *       for every key of {@code BuiltInRegistries.REGISTRY} between {@code GameData.unfreezeData()} and
 *       {@code freezeData()} ({@code CommonModLoader}); the D2 spike run showed it reaches
 *       {@code TEST_FUNCTION};</li>
 *   <li>a vanilla {@link FunctionGameTestInstance} pointing at that key goes into {@code TEST_INSTANCE}
 *       from {@link RegisterGameTestsEvent}, called by {@link NeoForgeGameTests#register} so that the
 *       roster and the hand-wired tests share the lane's environments.</li>
 * </ol>
 */
final class ScenarioRosterNeoForge {

	private static final Logger LOGGER = LoggerFactory.getLogger("alaindustrial/scenario-roster");

	/** Lane default structure: the 8³ rig ({@code NeoForgeGameTests.RIG_STRUCTURE}). */
	static final Identifier DEFAULT_STRUCTURE = Industrialization.id("gametest_rig");

	/** Lane default environment: the one every hand-wired test uses. */
	static final Identifier DEFAULT_ENVIRONMENT = Industrialization.id("empty_env");

	/** Lane padding ({@code NeoForgeGameTests.RIG_PADDING}). */
	private static final int PADDING = 1;

	private ScenarioRosterNeoForge() {}

	/** Wired from {@link NeoForgeGameTestBootstrap#init}. */
	static void init(IEventBus modBus) {
		modBus.addListener(ScenarioRosterNeoForge::registerFunctions);
	}

	private static void registerFunctions(RegisterEvent event) {
		event.<Consumer<GameTestHelper>>register(Registries.TEST_FUNCTION, helper -> {
			for (RosterEntry entry : ScenarioRoster.ALL) {
				helper.register(entry.testId(RosterEntry.Lane.NEOFORGE), entry.body());
			}
			LOGGER.info("[scenario roster] neoforge: {} test functions registered", ScenarioRoster.ALL.size());
		});
	}

	/**
	 * Registers one test instance per roster entry. {@code environments} hands out the lane's environment
	 * holder for an id, registering it on first use, so a roster test and a hand-wired one asking for the
	 * same environment share one batch.
	 */
	static void registerInstances(RegisterGameTestsEvent event,
			Function<Identifier, Holder<TestEnvironmentDefinition<?>>> environments) {
		for (RosterEntry entry : ScenarioRoster.ALL) {
			RosterEntry.LaneParams params = entry.params(RosterEntry.Lane.NEOFORGE);
			Identifier id = entry.testId(RosterEntry.Lane.NEOFORGE);
			Identifier environment = entry.environment() != null ? entry.environment() : DEFAULT_ENVIRONMENT;
			Identifier structure = params.structure() != null ? params.structure() : DEFAULT_STRUCTURE;
			event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
					GameTestData.of(environments.apply(environment), structure, params.maxTicks(), entry.required(),
							entry.rotation(), params.skyAccess(), PADDING)));
		}
		LOGGER.info("[scenario roster] neoforge: {} test instances registered", ScenarioRoster.ALL.size());
	}
}
