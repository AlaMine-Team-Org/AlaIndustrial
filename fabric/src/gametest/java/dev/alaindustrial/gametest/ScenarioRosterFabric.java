package dev.alaindustrial.gametest;

import dev.alaindustrial.gametest.compat.GameTestData;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.fabricmc.fabric.api.event.registry.DynamicRegistryView;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Fabric lane's replay of {@link ScenarioRoster#ALL} (MOD-717, ADR-038) — no {@code @GameTest}
 * wrapper per scenario. It writes to the same two registries Fabric's own annotation scanner writes to
 * ({@code FabricGameTestModInitializer}, fabric-gametest-api-v1 4.0.32 / 4.0.21, verified by javap):
 * <ol>
 *   <li>each body goes into {@link BuiltInRegistries#TEST_FUNCTION} from {@code onInitialize}, exactly
 *       where Fabric registers the annotated methods;</li>
 *   <li>a vanilla {@link FunctionGameTestInstance} pointing at that key goes into {@code TEST_INSTANCE}
 *       from the public {@link DynamicRegistrySetupCallback}, which fabric-registry-sync fires on the
 *       server-side resource load only, after the fresh registries exist and before data is read into
 *       them.</li>
 * </ol>
 * Because data has not been read yet, an environment is taken as a forward reference
 * ({@code createRegistrationLookup().getOrThrow}): the data load binds it — {@code minecraft:default}
 * from vanilla data, {@code alaindustrial:*} from {@code data/alaindustrial/test_environment/} of this
 * source set — and an id nobody defines fails the load as "Unbound values in registry".
 *
 * <p>Instances are added only in a {@code :fabric:runGameTest} JVM (system property
 * {@value #GAMETEST_PROPERTY}, the switch {@code FabricGameTestRunner.ENABLED} reads), so the client
 * lanes never see them. Wired as a {@code main} entrypoint of the gametest mod ({@code fabric.mod.json}).
 * The D2 spike proved the mechanism on both lanes (research.md of MOD-717).
 */
public final class ScenarioRosterFabric implements ModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("alaindustrial/scenario-roster");

	/** Set by loom for the server gametest run; read by {@code FabricGameTestRunner.ENABLED}. */
	static final String GAMETEST_PROPERTY = "fabric-api.gametest";

	/** Lane default structure: the {@code @GameTest} annotation default {@code structure()} (javap). */
	private static final Identifier DEFAULT_STRUCTURE = Identifier.parse("fabric-gametest-api-v1:empty");

	/** Lane default environment: the {@code @GameTest} annotation default {@code environment()} (javap). */
	private static final Identifier DEFAULT_ENVIRONMENT = Identifier.parse("minecraft:default");

	/** Lane default padding: the {@code @GameTest} annotation default {@code padding()} (javap). */
	private static final int PADDING = 1;

	@Override
	public void onInitialize() {
		for (RosterEntry entry : ScenarioRoster.ALL) {
			Registry.<Consumer<GameTestHelper>, Consumer<GameTestHelper>>register(
					BuiltInRegistries.TEST_FUNCTION, entry.testId(RosterEntry.Lane.FABRIC), entry.body());
		}
		LOGGER.info("[scenario roster] fabric: {} test functions registered", ScenarioRoster.ALL.size());
		if (System.getProperty(GAMETEST_PROPERTY) == null) {
			return;
		}
		DynamicRegistrySetupCallback.EVENT.register(ScenarioRosterFabric::registerInstances);
	}

	private static void registerInstances(DynamicRegistryView view) {
		Optional<Registry<GameTestInstance>> tests = view.getOptional(Registries.TEST_INSTANCE);
		Optional<Registry<TestEnvironmentDefinition<?>>> environments =
				view.<TestEnvironmentDefinition<?>>getOptional(Registries.TEST_ENVIRONMENT);
		if (tests.isEmpty() || environments.isEmpty()) {
			return; // a load without the test registries (dimensions, ...)
		}
		HolderGetter<TestEnvironmentDefinition<?>> environmentLookup =
				((WritableRegistry<TestEnvironmentDefinition<?>>) environments.get()).createRegistrationLookup();
		for (RosterEntry entry : ScenarioRoster.ALL) {
			RosterEntry.LaneParams params = entry.params(RosterEntry.Lane.FABRIC);
			Identifier id = entry.testId(RosterEntry.Lane.FABRIC);
			Identifier environment = entry.environment() != null ? entry.environment() : DEFAULT_ENVIRONMENT;
			Holder<TestEnvironmentDefinition<?>> environmentHolder =
					environmentLookup.getOrThrow(ResourceKey.create(Registries.TEST_ENVIRONMENT, environment));
			Identifier structure = params.structure() != null ? params.structure() : DEFAULT_STRUCTURE;
			GameTestInstance instance = new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
					GameTestData.of(environmentHolder, structure, params.maxTicks(), entry.required(),
							entry.rotation(), params.skyAccess(), PADDING));
			Registry.<GameTestInstance, GameTestInstance>register(tests.get(), id, instance);
		}
		LOGGER.info("[scenario roster] fabric: {} test instances registered", ScenarioRoster.ALL.size());
	}
}
