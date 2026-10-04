package dev.alaindustrial.gametest.neoforge;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.CableEnergyScenarios;
import dev.alaindustrial.gametest.ItemPipeScenarios;
import dev.alaindustrial.gametest.BlockCapabilityParityScenarios;
import dev.alaindustrial.gametest.PayloadRegistrationScenarios;
import dev.alaindustrial.gametest.RosterEntry;
import dev.alaindustrial.gametest.RosterReplayScenarios;
import dev.alaindustrial.registry.ModContent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * MOD-022 — NeoForge world gametest lane (GATE). Registers world-based energy scenarios that run on a
 * real chunk-loaded {@code gameTestServer} (see {@code neoforge/build.gradle} {@code runs.gameTests}).
 * This is the coverage the JUnit {@code EphemeralTestServerProvider} could not provide (no ticking world).
 *
 * <p><b>Registration mechanism (verified against 26.2.0.67 sources):</b>
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
	 * The lane's test environments by id, filled on {@link #register}: each is registered once, empty, on
	 * first use — by a hand-wired test or by a {@link ScenarioRosterNeoForge} entry — so tests asking for
	 * the same id share one batch.
	 */
	private static final Map<Identifier, Holder<TestEnvironmentDefinition<?>>> ENVIRONMENTS = new LinkedHashMap<>();

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
		ENVIRONMENTS.clear();
		emptyEnv = environment(event, ScenarioRosterNeoForge.DEFAULT_ENVIRONMENT);
		// MOD-717 (ADR-038): every common scenario wired exactly once on each lane is declared in the common
		// roster (XScenarios.Roster, ScenarioRoster.PARTS) and registered by ScenarioRosterNeoForge at the end of
		// this method. What is still wired here by hand, and why (gametest_roster_codemod.py --all --stays):
		//   * the NeoForge half of a Fabric wrapper with a body of its own (Industrialist POI, the cable's vanilla
		//     neighbour, the ItemEnergyCapability bridge) or a lambda with extra arguments (item pipe, block
		//     capabilities) — not a plain delegate on one lane, so not one roster entry;
		//   * networkSplitRejoinResumesFlow — two Fabric wrappers call this one body;
		//   * LineOnlyRegistrations — scenarios of one Minecraft line (MOD-705).

		// MOD-062 Industrialist: the per-loader seams (POI state map via registry callback, the
		// profession's data-driven trade sets, the server-start village pool injection).
		registerTest(event, "industrialist_workbench_poi_mapping", 40, true,
				dev.alaindustrial.gametest.IndustrialistScenarios::workbenchStateMapsToPoi);

		// A vanilla furnace neighbour must not NPE during endpoint discovery (loader-capability null-safety).
		registerTest(event, "cable_vanilla_neighbor_no_npe", 100, true,
				CableEnergyScenarios::cableVanillaNeighborNoNpe);

		// Network split + rejoin (R-CON-04, R-CON-09): break stops delivery, replace resumes flow.
		registerTest(event, "network_split_rejoin_resumes_flow", 200, true,
				CableEnergyScenarios::networkSplitRejoinResumesFlow);

		// MOD-084: the NeoForge half of the cross-mod bridge. Loader-specific bodies (they call
		// Capabilities.Energy.ITEM), mirroring the Fabric lane's ItemEnergyCapabilityGameTest.
		registerTest(event, "xmod_foreign_charger_fills_pouch", 40, true,
				ItemEnergyCapabilityScenarios::fun01ForeignChargerFillsPouch);
		registerTest(event, "xmod_foreign_machine_cannot_drain", 40, true,
				ItemEnergyCapabilityScenarios::neg01ForeignMachineCannotDrain);
		registerTest(event, "xmod_pack_charges_foreign_item", 40, true,
				ItemEnergyCapabilityScenarios::fun02PackChargesForeignItem);
		// MOD-372: the roster guard — every powered item of the mod must be reachable through
		// Capabilities.Energy.ITEM, not just the ones someone remembered to add to the literal list.
		registerTest(event, "xmod_every_powered_item_exposes_capability", 40, true,
				ItemEnergyCapabilityScenarios::reg01EveryPoweredItemExposesCapability);

		// MOD-193: the MOD-115 furnace family, which ran on Fabric only. The sided WorldlyContainer path
		// (top=input, side=fuel, bottom=result) is exactly where this loader differed: Fabric's
		// ItemStorage.SIDED falls back to any Container, NeoForge publishes the capability per block
		// entity — so a mod machine missing from that list is invisible to the pipe here and nowhere
		// else. The iron-furnace cases below are the guards for that; the vanilla-furnace ones pin the
		// round-robin itself (vanilla publishes its own capability, so they pass either way).
		registerTest(event, "item_pipe_vanilla_furnace_bottom_extract", 60, true,
				h -> ItemPipeScenarios.extractsFurnaceResultFromBottom(h, Blocks.FURNACE, "vanilla furnace bottom extract"));
		registerTest(event, "item_pipe_iron_furnace_bottom_extract", 60, true,
				h -> ItemPipeScenarios.extractsFurnaceResultFromBottom(h, ModContent.IRON_FURNACE.get(),
						"iron furnace bottom extract"));

		// MOD-433: the capability this loader hands out on a block-entity face is exactly the port the
		// block entity exposes there — asked through Capabilities.*.BLOCK, the way a foreign mod would.
		// The mod's own network never goes through the capability here (NeoForgeEnergyLookup reads the
		// port straight off the block entity), which is how the CESU stayed invisible to FE mods on
		// this loader alone until this sweep existed.
		registerTest(event, "block_capabilities_match_ports", 40, true,
				helper -> BlockCapabilityParityScenarios.capabilitiesMatchPorts(helper, CAPABILITY_PROBES));

		// MOD-706: every payload of ModPayloads.PAYLOADS registered in its direction, asked of NeoForge's
		// NetworkRegistry through the loader probe — the same body as the Fabric PayloadRegistrationGameTest.
		// Hand-wired because the probe is loader-specific; the END-tick hook scenarios run from the roster.
		registerTest(event, "payloads_registered_in_their_direction", 40, true,
				helper -> PayloadRegistrationScenarios.everyPayloadRegisteredInItsDirection(
						helper, NeoForgePayloadProbe.INSTANCE));

		// MOD-717: the roster floor (RosterReplayScenarios) — hand-wired, it must outlive the roster it watches.
		registerTest(event, "roster_is_replayed_on_this_lane", 20, true,
				helper -> RosterReplayScenarios.rosterIsReplayedOnThisLane(helper, RosterEntry.Lane.NEOFORGE));

		// MOD-705: scenarios of this Minecraft line only; the call is the same on both lines.
		LineOnlyRegistrations.register(event);

		// MOD-717: every scenario declared in the common roster (ScenarioRoster) — no hand line per test.
		ScenarioRosterNeoForge.registerInstances(event, id -> environment(event, id));
	}

	/** The lane's environment {@code id}, registered empty on first use (see {@link #ENVIRONMENTS}). */
	private static Holder<TestEnvironmentDefinition<?>> environment(RegisterGameTestsEvent event, Identifier id) {
		return ENVIRONMENTS.computeIfAbsent(id,
				key -> event.registerEnvironment(key, new TestEnvironmentDefinition.AllOf()));
	}

	/** The three NeoForge probes for {@link BlockCapabilityParityScenarios} (MOD-433). */
	private static final BlockCapabilityParityScenarios.Probes CAPABILITY_PROBES =
			new BlockCapabilityParityScenarios.Probes(
					(level, pos, side) -> level.getCapability(Capabilities.Energy.BLOCK, pos, side) != null,
					(level, pos, side) -> level.getCapability(Capabilities.Fluid.BLOCK, pos, side) != null,
					(level, pos, side) -> level.getCapability(Capabilities.Item.BLOCK, pos, side) != null);

	/**
	 * Structure template every scenario is placed on: 8x8x8 of air, shipped in this source set as
	 * {@code data/alaindustrial/structure/gametest_rig.nbt}.
	 *
	 * <p><b>Why not {@code minecraft:empty} (MOD-335).</b> The vanilla template is 1x1x1, and the
	 * engine sizes three things off the STRUCTURE box, not off what a scenario body actually writes:
	 * <ul>
	 *   <li>{@code TestInstanceBlockEntity.forceLoadChunks()} force-loads only the chunks intersecting
	 *       {@code getStructureBoundingBox()};</li>
	 *   <li>{@code GameTestInfo} waits for only those chunks to be entity-ticking before running;</li>
	 *   <li>{@code StructureGridSpawner} spaces the grid by {@code getTestBounds().getXsize() + 5}
	 *       (rows: {@code + 6}), so a 1x1x1 template packed tests 6 blocks apart.</li>
	 * </ul>
	 * Our rigs are up to 5x5 (the scythe/trellis platforms), so a rig whose origin landed late in a
	 * chunk straddled the border and dropped its items into a chunk this lane never loaded —
	 * {@code getEntitiesOfClass} then counted zero and the test failed with "no drop". It reproduced
	 * on exactly the tests whose origin had {@code z % 16 == 14} (rig z spans 14..18, crossing 16),
	 * which is why it looked random: {@code GameTestServer} picks a RANDOM world origin per run.
	 *
	 * <p>8x8x8 + {@code padding = 1} mirrors Fabric's {@code fabric-gametest-api-v1:empty} (same size,
	 * same default padding) — the reason the Fabric lane never saw this failure on identical bodies.
	 */
	private static final Identifier RIG_STRUCTURE = Industrialization.id("gametest_rig");

	/**
	 * Padding around the structure. Widens grid spacing and, importantly, extends
	 * {@code clearSpaceForStructure} + {@code removeEntities} so a neighbour's leftover drops are
	 * cleared before this test runs. Matches the Fabric annotation default.
	 */
	private static final int RIG_PADDING = 1;

	/**
	 * Register one code-body scenario under the alaindustrial namespace with a sane maxTicks. Package-private
	 * for {@link LineOnlyRegistrations}, which registers the scenarios of this Minecraft line only (MOD-705).
	 */
	static void registerTest(RegisterGameTestsEvent event, String name, int maxTicks, boolean required,
			Consumer<GameTestHelper> body) {
		TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
				emptyEnv,
				Level.OVERWORLD,
				RIG_STRUCTURE,
				maxTicks,
				0,          // setupTicks
				required,
				Rotation.NONE,
				false,      // manualOnly
				1,          // maxAttempts — a retry would only mask a real defect
				1,          // requiredSuccesses
				false,      // skyAccess
				RIG_PADDING);
		event.registerTest(Industrialization.id(name), new CodeGameTestInstance(body, data));
	}
}
