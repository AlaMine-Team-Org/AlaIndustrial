package dev.alaindustrial.gametest.neoforge;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.CoreEnergyScenarios;
import dev.alaindustrial.gametest.CoreFluidScenarios;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * MOD-022 — NeoForge world gametest lane (GATE). Registers world-based energy scenarios that run on a
 * real chunk-loaded {@code gameTestServer} (see {@code neoforge/build.gradle} {@code runs.gameTests}).
 * This is the coverage the JUnit {@code EphemeralTestServerProvider} could not provide (no ticking world).
 *
 * <p><b>Registration mechanism (verified against 26.2.0.8-beta sources):</b>
 * <ul>
 *   <li>{@code RegistryDataLoader.load(..., fromResources=true)} posts a
 *       {@code net.neoforged.neoforge.event.RegisterGameTestsEvent} on the mod bus when
 *       {@code GameTestHooks.isGametestEnabled()} is true. The event exposes the writable
 *       {@code TEST_ENVIRONMENT} and {@code TEST_INSTANCE} registries.</li>
 *   <li>{@code event.registerEnvironment(id, new TestEnvironmentDefinition.AllOf())} adds an empty
 *       environment and returns its {@code Holder}, which fills {@code TestData.environment}.</li>
 *   <li>{@code event.registerTest(id, GameTestInstance)} inserts a live instance into
 *       {@code Registries.TEST_INSTANCE}. {@code GameTestServer} runs it by calling {@code instance.run}
 *       directly (no codec round-trip) — see {@link CodeGameTestInstance} for why a custom instance is
 *       used instead of {@code FunctionGameTestInstance} (which needs an unreachable
 *       {@code TEST_FUNCTION} entry).</li>
 *   <li>Structure {@code minecraft:empty} (1x1x1) ships in vanilla data; blocks are placed in the same
 *       chunk a few relative blocks from the structure origin, so a small empty structure suffices.</li>
 * </ul>
 */
public final class NeoForgeGameTests {

	/** Shared empty test environment holder, filled on {@link #register}. */
	private static Holder<TestEnvironmentDefinition<?>> emptyEnv;

	/**
	 * Registers {@link CodeGameTestInstance#CODEC} as the {@code alaindustrial:code} instance type so the
	 * {@code TEST_INSTANCE} registry can be encoded during the client known-packs handshake without the
	 * {@code ClassCastException} the borrowed function-codec caused. {@code register(modBus)} is called from
	 * {@code IndustrializationNeoForge}. Registering the type is harmless outside the gametest run (the
	 * instances themselves are only added when {@code RegisterGameTestsEvent} fires).
	 */
	public static final DeferredRegister<MapCodec<? extends GameTestInstance>> INSTANCE_TYPES =
			DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Industrialization.MOD_ID);

	public static final DeferredHolder<MapCodec<? extends GameTestInstance>, MapCodec<CodeGameTestInstance>> CODE_TYPE =
			INSTANCE_TYPES.register("code", () -> CodeGameTestInstance.CODEC);

	private NeoForgeGameTests() {}

	/** Mod-bus listener, wired from {@code IndustrializationNeoForge}. */
	public static void register(RegisterGameTestsEvent event) {
		emptyEnv = event.registerEnvironment(Industrialization.id("empty_env"),
				new TestEnvironmentDefinition.AllOf());

		// Core world energy scenarios — shared, loader-neutral bodies in common
		// (dev.alaindustrial.gametest.CoreEnergyScenarios), so Fabric and NeoForge exercise the same core.
		// MOD-022 recipe seam: proves the machine RecipeType/RecipeSerializer register + resolve on NeoForge
		// (the frozen-registry fix — ModRecipesNeoForge). Fabric covers this via MachineGameTest; this is the
		// NeoForge world lane's first recipe-processing case.
		registerTest(event, "macerator_processes_recipe", 420, true,
				CoreEnergyScenarios::maceratorProcessesRecipe);
		// MOD-022 data-component seam: a charged battery box carries STORED_ENERGY on drop (frozen-registry
		// fix — ModDataComponentsNeoForge). Fabric covers this via BatteryBoxGameTest; NeoForge world lane's first.
		registerTest(event, "battery_box_drop_carries_energy", 40, true,
				CoreEnergyScenarios::batteryBoxDropCarriesEnergy);
		registerTest(event, "generator_charges_adjacent_box", 40, true,
				CoreEnergyScenarios::generatorChargesAdjacentBox);
		registerTest(event, "full_neighbour_no_leak", 40, true,
				CoreEnergyScenarios::fullNeighbourNoLeak);
		registerTest(event, "generator_delivers_down_cable", 200, true,
				CoreEnergyScenarios::generatorDeliversDownCable);
		registerTest(event, "return_round_robin_no_leak", 40, true,
				CoreEnergyScenarios::returnRoundRobinNoLeak);
		registerTest(event, "mod009_battery_box_charges_to_full", 80, true,
				CoreEnergyScenarios::mod009BatteryBoxChargesToFull);
		// MOD-021 cable-loss on NeoForge: REQUIRED (guards a now-FIXED NeoForge-only defect). — surfaces a REAL NeoForge energy-core defect this
		// world lane found (the JUnit adapter tests could not). NeoForgeEnergyLookup.find() hardcodes
		// EnergyRole.BOTH, and NeoForgeEnergyPort.supportsInsertion/Extraction derive from that role, so
		// EVERY face reports supporting BOTH insertion and extraction regardless of its real per-face role.
		// EnergyNetwork.refreshEndpoints therefore classifies every storage/consumer as a producer too, so
		// computeConsumerDistances seeds cable-distance 1 at every consumer and the MOD-021 loss floors to 0
		// (the box gains a full lossless 32 EU/tick). insert()/extract() still gate correctly on role via the
		// block-side FaceEnergyPort, so delivery works — only the classification-only supports* predicates are
		// wrong. Root cause: the per-face role is lost across the Capabilities.Energy.BLOCK round-trip
		// (EnergyHandler has no supports* predicate). Fix belongs in the NeoForge energy adapter, not here.
		// Fabric's tcCable001Nrg02 passes because EnergyStorage.supports* reflect the real per-face capability.
		registerTest(event, "mod021_loss_over_ten_cables", 100, true,
				CoreEnergyScenarios::mod021LossOverTenCables);
		registerTest(event, "nbt_round_trip_preserves_state", 40, true,
				CoreEnergyScenarios::nbtRoundTripPreservesState);
		// MOD-025: ring network union-find merge on cycle, proven on both loaders (loader-neutral body
		// in common/.../CoreEnergyScenarios — NetworkManager/EnergyNetwork are shared, not per-loader).
		registerTest(event, "ring_network_merges_on_close", 200, true,
				CoreEnergyScenarios::ringNetworkMergesOnClose);
		// Phase-03 LV expansion: water mill + wind mill world-tested on NeoForge (passive LV generators,
		// cable-less push path). Loader-neutral bodies in common/.../CoreEnergyScenarios.
		registerTest(event, "water_mill_charges_adjacent_box", 40, true,
				CoreEnergyScenarios::waterMillChargesAdjacentBox);
		registerTest(event, "wind_mill_charges_adjacent_box", 40, true,
				CoreEnergyScenarios::windMillChargesAdjacentBox);

		// MOD-028: fluid multiloader — proves the FluidPort/FluidTank abstraction works end to end on
		// NeoForge (Capabilities.Fluid.BLOCK resolves, transactions commit, lava becomes EU).
		registerTest(event, "fluid_source_to_pump_to_geo_to_eu", 40, true,
				CoreFluidScenarios::sourceToPumpToGeoToEu);
	}

	/** Register one code-body scenario under the alaindustrial namespace with a sane maxTicks. */
	private static void registerTest(RegisterGameTestsEvent event, String name, int maxTicks, boolean required,
			Consumer<GameTestHelper> body) {
		TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
				emptyEnv,
				Identifier.withDefaultNamespace("empty"), // minecraft:empty structure (1x1x1), ships in vanilla data
				maxTicks,
				0,          // setupTicks
				required);
		event.registerTest(Industrialization.id(name), new CodeGameTestInstance(body, data));
	}
}
