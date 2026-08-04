package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * L1 unit tests for {@link Config}'s registry-driven snapshot/load round-trip (MOD-204).
 *
 * <p>The existing {@link ConfigFileTest} drives {@link Config#loadFrom(Path)} end-to-end, but each
 * test there reads the live {@code static} fields directly ({@code Config.solarEuPerTick}). That
 * bypasses the getter lambdas inside the private {@code FIELDS} list — the {@code () -> fieldName}
 * suppliers that {@code stage()} consults for an absent key's fallback and that {@code write()}
 * consults to serialize the canonical snapshot. PIT therefore saw ~110 of those getter lambdas as
 * freely mutable: replacing {@code return field;} with {@code return 0;} (or {@code false}) was
 * invisible to every existing assertion, because no assertion read a value <em>through</em> the
 * registry.
 *
 * <p>These tests close that hole. They set every tunable to a hand-picked non-default sentinel, then
 * exercise the registry paths that call the getters:
 * <ul>
 *   <li>{@code stage()} with an <b>absent</b> key must fall back to the field's <em>current</em>
 *       value via the getter — so loading a one-key file keeps the other ~110 fields unchanged.
 *       A mutated getter returns 0/false, the absent key loads 0/false, the test sees it.</li>
 *   <li>{@code write()} must emit the field's <em>current</em> value via the getter — so the
 *       canonical snapshot for a mutated field carries 0/false instead of the sentinel.</li>
 * </ul>
 *
 * <p>Every expected value below is a hand-written literal (the sentinel chosen for that field),
 * never derived from the production getter — the same anti-tautology rule MOD-203 set for
 * {@link ConfigBalanceTest}. The sentinels deliberately differ from the compiled defaults AND from
 * the mutation replacement value (0 / 0.0 / false), so a getter returning the wrong constant is
 * caught regardless of which constant PIT substitutes.
 *
 * <p>{@code loadFrom} mutates {@link Config}'s static fields, so the pristine defaults are captured
 * once into a baseline file and restored after every test (same idiom as {@link ConfigFileTest}).
 */
class ConfigSnapshotTest {

	@TempDir
	static Path sharedDir;
	/** Snapshot of the pristine compiled defaults, written before any test mutates the static fields. */
	static Path baseline;

	@BeforeAll
	static void captureDefaults() {
		baseline = sharedDir.resolve("baseline.json");
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(baseline));
	}

	@AfterEach
	void restoreDefaults() {
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(baseline));
	}

	/**
	 * Every public static non-final primitive tunable on {@link Config} (int / float / double), paired
	 * with a non-default sentinel that is also distinct from the PIT replacement constant (0 / 0.0)
	 * and from any clamping floor the field enforces on load. This map IS the assertion oracle: if a
	 * getter is mutated to return 0, the round-trip below observes 0 and fails against the sentinel.
	 *
	 * <p>Sentinels are hand-written, one per field, chosen to survive the field's own load-time
	 * validation (e.g. {@code machineEuPerTick} must stay {@code >= 1}; buffers must stay {@code >= 1};
	 * float multipliers must stay {@code > 0}; {@code levelXpMultiplier}/{@code windMillStormWearFactor}
	 * must stay {@code > 1.0}). They are otherwise arbitrary.
	 *
	 * <p>Boolean tunables are covered separately by {@link #absentBooleanKeys_keepEachValueViaGetter}
	 * because a single sentinel cannot distinguish both PIT boolean-return mutants at once.
	 */
	private static final Map<String, Number> SENTINELS = new LinkedHashMap<>();
	static {
		// Global multipliers (must stay > 0).
		SENTINELS.put("globalEuRateMultiplier", 2.5f);
		SENTINELS.put("globalMachineSpeedMultiplier", 1.25f);
		// Generators (ints; sentinel stays >= 1 so it clears the minimum guards).
		SENTINELS.put("solarEuPerTick", 7);
		SENTINELS.put("daylightEuPerTick", 19);
		SENTINELS.put("moonlitEuPerTick", 11);
		SENTINELS.put("moonlitWeatherEuPerTick", 2);
		SENTINELS.put("fuelEuPerTick", 23);
		SENTINELS.put("geothermalEuPerTick", 41);
		SENTINELS.put("geothermalBurnTicks", 2222);
		SENTINELS.put("waterMillEuPerTick", 3);
		SENTINELS.put("windMillMaxBaseEuPerTick", 6);
		SENTINELS.put("windMillMaxEuPerTick", 13);
		SENTINELS.put("windMillRainFactor", 1.75f);
		SENTINELS.put("windMillThunderFactor", 2.25f);
		SENTINELS.put("windMillSampleTicks", 55);
		SENTINELS.put("windMillEvolveTicks", 11_111);
		SENTINELS.put("highAltWindMillMaxBaseEuPerTick", 14);
		SENTINELS.put("highAltWindMillBlocksPerBase", 11);
		SENTINELS.put("highAltWindMillMaxEuPerTick", 21);
		SENTINELS.put("stormWindMillMaxBaseEuPerTick", 9);
		SENTINELS.put("stormWindMillRainFactor", 2.5f);
		SENTINELS.put("stormWindMillThunderFactor", 3.5f);
		SENTINELS.put("stormWindMillMaxEuPerTick", 22);
		SENTINELS.put("windMillRotorMaxDamage", 808);
		SENTINELS.put("windMillRotorEuPerDamage", 369);
		SENTINELS.put("windMillStormWearFactor", 1.8f);
		SENTINELS.put("waterMillWheelMaxDamage", 707);
		SENTINELS.put("waterMillWheelEuPerDamage", 251);
		SENTINELS.put("solarTransparentFactor", 0.625f);
		SENTINELS.put("solarSnowFactor", 0.375f);
		SENTINELS.put("solarEvolveTicks", 22_222);
		SENTINELS.put("solarSkySampleTicks", 33);
		// Pump / fluid.
		SENTINELS.put("pumpEuPerBucket", 1234);
		SENTINELS.put("pumpScanCooldownTicks", 44);
		SENTINELS.put("pumpScanMaxDistance", 64);
		SENTINELS.put("pumpScanMaxVisited", 1024);
		SENTINELS.put("fluidTankCapacity", 12_000);
		// Teleporter.
		SENTINELS.put("teleporterBuffer", 611_000);
		SENTINELS.put("teleporterBaseCost", 6789);
		SENTINELS.put("teleporterCostPerBlock", 7);
		SENTINELS.put("teleporterWarmupTicks", 150);
		SENTINELS.put("teleporterCooldownTicks", 1600);
		SENTINELS.put("teleporterWarmupCancelRadius", 3);
		SENTINELS.put("teleporterMaxPoints", 24);
		// Buffers (minimum=1 — sentinel stays >= 1).
		SENTINELS.put("batteryBoxBuffer", 22_222);
		SENTINELS.put("maceratorBuffer", 1600);
		SENTINELS.put("machineBuffer", 1601);
		SENTINELS.put("electricHeaterBuffer", 1602);
		SENTINELS.put("chargePadBuffer", 22_333);
		SENTINELS.put("chargePadInputRate", 137);
		SENTINELS.put("chargePadOutputRate", 138);
		SENTINELS.put("pumpBuffer", 5000);
		SENTINELS.put("generatorBuffer", 5001);
		SENTINELS.put("geothermalBuffer", 5002);
		SENTINELS.put("waterMillBuffer", 5003);
		SENTINELS.put("windMillBuffer", 5004);
		SENTINELS.put("t2WindMillBuffer", 9000);
		SENTINELS.put("solarBuffer", 9001);
		SENTINELS.put("cableBuffer", 17);
		// Item pipes.
		SENTINELS.put("itemPipeItemsPerTransfer", 5);
		SENTINELS.put("itemPipeTransferIntervalTicks", 40);
		// Battery pouch / energy pack / drill / magnet / jetpack / stock frame.
		SENTINELS.put("lvPouchCapacity", 200);
		SENTINELS.put("lvPouchBuffer", 3000);
		SENTINELS.put("lvPouchDrainPerSecond", 2);
		SENTINELS.put("energyPackBuffer", 22_000);
		SENTINELS.put("energyPackInputRate", 48);
		SENTINELS.put("energyPackOutputRate", 49);
		SENTINELS.put("electricDrillBuffer", 12_000);
		SENTINELS.put("electricDrillEuPerBlock", 77);
		SENTINELS.put("electricDrillInputRate", 50);
		SENTINELS.put("electricDrillTorchEuCost", 8);
		SENTINELS.put("jetpackBuffer", 33_000);
		SENTINELS.put("jetpackEuPerTick", 64);
		SENTINELS.put("jetpackInputRate", 51);
		SENTINELS.put("jetpackMaxY", 256);
		SENTINELS.put("jetpackFlightLightLevel", 12);
		SENTINELS.put("magnetBuffer", 6000);
		SENTINELS.put("magnetInputRate", 52);
		SENTINELS.put("magnetRange", 7);
		SENTINELS.put("magnetEuPerItem", 3);
		SENTINELS.put("magnetScanIntervalTicks", 2);
		SENTINELS.put("stockFrameScanIntervalTicks", 30);
		// Machine durations / EU.
		SENTINELS.put("machineEuPerTick", 4);
		SENTINELS.put("maceratorDuration", 250);
		SENTINELS.put("incubatorEuPerTick", 16);
		SENTINELS.put("incubatorBuffer", 9000);
		SENTINELS.put("mutationDurationTransform", 400);
		SENTINELS.put("mutationDurationDuplicate", 600);
		SENTINELS.put("mutationDurationCreate", 1100);
		SENTINELS.put("mutationAttemptsPerIngot", 5);
		// Mutation chances (doubles; sentinel stays >= 0, below the 0.95 chance cap where relevant).
		SENTINELS.put("mutationChanceTransform", 0.66);
		SENTINELS.put("mutationChanceDuplicate", 0.36);
		SENTINELS.put("mutationChanceCreate", 0.16);
		SENTINELS.put("mutationChanceCap", 0.91);
		SENTINELS.put("mutationSlagChance", 0.07);
		SENTINELS.put("mutationGradeRare", 0.22);
		SENTINELS.put("mutationGradeEpic", 0.09);
		SENTINELS.put("mutationGradeLegendary", 0.03);
		// Furnaces / sawmill / polymerizer / iron furnace.
		SENTINELS.put("electricFurnaceDuration", 175);
		SENTINELS.put("compressorDuration", 220);
		SENTINELS.put("extractorDuration", 180);
		SENTINELS.put("sawmillDuration", 130);
		SENTINELS.put("polymerizerDuration", 333);
		SENTINELS.put("vulcanizerDuration", 334);
		SENTINELS.put("galvanicBathDuration", 335);
		SENTINELS.put("galvanicBathWaterPerOp", 336);
		// Fluxweave armour (MOD-127).
		SENTINELS.put("fluxweaveBuffer", 15_101);
		SENTINELS.put("fluxweaveInputRate", 337);
		SENTINELS.put("fluxweaveUpkeepEuPerSecond", 338);
		SENTINELS.put("fluxweaveFallDamageReductionPercent", 41);
		SENTINELS.put("fluxweaveRunSpeedPercent", 42);
		SENTINELS.put("fluxweaveOxygenBonus", 43);
		SENTINELS.put("fluxweaveSwimEfficiency", 44);
		SENTINELS.put("fluxweaveChargedToughness", 45);
		SENTINELS.put("fluxweaveKnockbackResistance", 46);
		SENTINELS.put("fluxweaveStepHeightBonus", 47);
		SENTINELS.put("fluxweaveRegenEuPerHeal", 339);
		SENTINELS.put("electricHeaterEuPerTick", 5);
		SENTINELS.put("ironFurnaceCookTime", 160);
		// Player stats / XP.
		SENTINELS.put("euPerXp", 1500);
		SENTINELS.put("euPerXpGenerated", 22_000);
		SENTINELS.put("xpLevelOneCost", 99);
		SENTINELS.put("levelXpMultiplier", 1.22f);
		SENTINELS.put("statsFlushTicks", 150);
		// Energy tiers.
		SENTINELS.put("tierLvVoltage", 48);
		SENTINELS.put("tierMvVoltage", 256);
		SENTINELS.put("tierHvVoltage", 1024);
		SENTINELS.put("tierLvCapacity", 20_000);
		SENTINELS.put("tierMvCapacity", 80_000);
		SENTINELS.put("tierHvCapacity", 320_000);
		// Cables.
		SENTINELS.put("copperCableLossPerBlock", 0.035);
		SENTINELS.put("tinCableBuffer", 13);
		SENTINELS.put("tinCablePacketCap", 13);
		SENTINELS.put("tinCableLossPerBlock", 0.012);
		SENTINELS.put("goldCableBuffer", 64);
		SENTINELS.put("goldCableLossPerBlock", 0.045);
		// Network / worldgen.
		SENTINELS.put("networksPerTick", 1024);
		SENTINELS.put("networkAnalyzerMaxTraversedNetworks", 64);
		// MOD-324: the 21 tunables added after MOD-204 wrote this map and never appended to it. Their
		// getter lambdas were the entirety of Config's surviving mutants (21 PrimitiveReturns on
		// `() -> field`), which is how the class fell from MOD-204's documented 157/157 to 173/196.
		// The completeness guard below now fails the build when a new key lands without a sentinel,
		// so this block cannot silently fall behind a third time.
		// Fluid pipes / fluid network.
		SENTINELS.put("fluidPipeSegmentBuffer", 77);
		SENTINELS.put("fluidNetworksPerTick", 333);
		// Scythe (MOD-315).
		SENTINELS.put("scytheBonusSeedMultiplier", 0.44);
		// Garden drone station (MOD-277).
		SENTINELS.put("gardenDroneBuffer", 4444);
		SENTINELS.put("gardenDroneEuPerAction", 18);
		SENTINELS.put("gardenDroneRange", 9);
		SENTINELS.put("gardenDroneScanIntervalTicks", 35);
		SENTINELS.put("gardenDroneFlightTicksPerBlock", 23);
		// Cotton growth divisors.
		SENTINELS.put("cottonRootingChanceDivisor", 27);
		SENTINELS.put("cottonFruitingChanceDivisor", 14);
		// Alloy smelter (MOD-064).
		SENTINELS.put("alloySmelterEuPerTick", 19);
		SENTINELS.put("alloySmelterDuration", 233);
		// Assembler (MOD-288/289).
		SENTINELS.put("assemblerEuPerTick", 26);
		SENTINELS.put("assemblerDuration", 88);
		SENTINELS.put("assemblerBuffer", 13_500);
		// Bare-cable shock + insulation (MOD-279/318). Float sentinels are exact binary fractions so
		// Gson's rendering matches Float.toString in the snapshot assertion.
		SENTINELS.put("bareCableShockLvDamage", 3.25f);
		SENTINELS.put("bareCableShockMvDamage", 7.75f);
		SENTINELS.put("bareCableShockProximityRadius", 0.85);
		SENTINELS.put("insulationLossMultiplier", 0.35);
		SENTINELS.put("shockGuardWoodHitChance", 0.62);
		SENTINELS.put("shockGuardWoolHitChance", 0.81);
		SENTINELS.put("shockGuardGlassHitChance", 0.43);
		SENTINELS.put("shockGuardGraceTicks", 37);
	}

	/**
	 * Boolean tunables, covered by the polarity pair of tests rather than by a numeric sentinel. Held
	 * as a named set so the completeness guard can tell "covered elsewhere" from "forgotten".
	 */
	private static final Set<String> BOOLEAN_TUNABLES =
			Set.of("bonusChestEnabled", "oilBurns", "bareCableShockEnabled");

	/**
	 * Every key in {@link Config}'s own {@code FIELDS} registry is covered by this suite — either by a
	 * numeric sentinel or by the boolean polarity pair (MOD-324).
	 *
	 * <p>This is the guard that makes the rest of the suite self-maintaining, and it exists because the
	 * hand-written map above silently fell 21 keys behind. Every tunable's getter lambda is mutable
	 * bytecode ({@code () -> field} → {@code return 0}); a key absent from this suite is a mutant no
	 * test can kill, and the only visible symptom was a slowly sagging kill rate that nobody attributed
	 * to Config — MOD-204's "157/157" claim stayed in the build file long after it stopped being true.
	 *
	 * <p>Reading the registry reflectively rather than re-listing the keys is the whole point: the
	 * oracle has to come from production code, or it drifts in exactly the same way it just did.
	 */
	@Test
	void everyRegisteredTunableIsCoveredBySomeSentinel() throws ReflectiveOperationException {
		Field fieldsRegistry = Config.class.getDeclaredField("FIELDS");
		fieldsRegistry.setAccessible(true);
		List<?> registry = (List<?>) fieldsRegistry.get(null);
		assertTrue(registry.size() > 100, "sanity: the registry should hold every tunable, got " + registry.size());

		List<String> uncovered = new ArrayList<>();
		for (Object entry : registry) {
			// entry is an IntField/FloatField/DoubleField/BoolField; `key` lives on their shared
			// ConfigField superclass.
			Field keyField = entry.getClass().getSuperclass().getDeclaredField("key");
			keyField.setAccessible(true);
			String key = (String) keyField.get(entry);
			if (!SENTINELS.containsKey(key) && !BOOLEAN_TUNABLES.contains(key)) {
				uncovered.add(key);
			}
		}

		assertTrue(uncovered.isEmpty(),
				"Config tunables with no coverage in this suite — their getter lambdas are unkillable "
						+ "mutants. Add a numeric sentinel to SENTINELS (distinct from the default, from 0, "
						+ "and clearing the field's own minimum), or add a boolean to BOOLEAN_TUNABLES and "
						+ "assert both polarities: " + uncovered);
	}

	/**
	 * The mirror of the guard above: no sentinel may name a key the registry no longer has. A renamed
	 * or deleted tunable would otherwise leave a sentinel that reflection still resolves (the field may
	 * linger) while the registry path it was meant to exercise is gone.
	 */
	@Test
	void noSentinelNamesAnUnregisteredKey() throws ReflectiveOperationException {
		Field fieldsRegistry = Config.class.getDeclaredField("FIELDS");
		fieldsRegistry.setAccessible(true);
		List<?> registry = (List<?>) fieldsRegistry.get(null);

		Set<String> registered = new java.util.HashSet<>();
		for (Object entry : registry) {
			Field keyField = entry.getClass().getSuperclass().getDeclaredField("key");
			keyField.setAccessible(true);
			registered.add((String) keyField.get(entry));
		}

		List<String> orphans = new ArrayList<>();
		for (String key : SENTINELS.keySet()) {
			if (!registered.contains(key)) {
				orphans.add(key);
			}
		}
		for (String key : BOOLEAN_TUNABLES) {
			if (!registered.contains(key)) {
				orphans.add(key);
			}
		}
		assertTrue(orphans.isEmpty(), "sentinels naming keys absent from Config.FIELDS: " + orphans);
	}

	/**
	 * Apply every numeric sentinel to its live field, then load an <b>empty</b> file and assert each
	 * absent field kept its sentinel — i.e. the absent-key fallback in {@code stage()} really did call
	 * the getter and read the live value. A getter mutated to {@code return 0} loads 0 (or clamps to
	 * the compiled default for minimum-guarded fields) and fails here against the sentinel.
	 */
	@Test
	void absentKeys_keepLiveValueViaGetter_notZero(@TempDir Path dir)
			throws IOException, ReflectiveOperationException {
		applyNumericSentinels();

		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{}");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f), "empty file loads (every key absent)");
		for (Map.Entry<String, Number> e : SENTINELS.entrySet()) {
			assertFieldEquals(e.getKey(), e.getValue(),
					"absent key '" + e.getKey() + "' must keep its live value via the getter, not load 0");
		}
	}

	/**
	 * Apply every numeric sentinel, load an empty file (which canonicalizes via {@code write()}), then
	 * re-read the file and assert each field's serialized value is the sentinel. {@code write()}
	 * consults the getter to emit the value, so a getter mutated to {@code return 0} serializes 0 and
	 * the sentinel line is absent.
	 *
	 * <p>The canonical form is plain Gson pretty-print, so each tunable renders on its own line as
	 * {@code "key": <literal>}; the assertion matches that substring verbatim against the hand-written
	 * sentinel literal (no formula, no re-reading the live field — that would re-invoke the mutated
	 * getter and so pass even when the file holds 0).
	 */
	@Test
	void snapshot_emitsLiveSentinelsViaGetter_notZero(@TempDir Path dir)
			throws IOException, ReflectiveOperationException {
		applyNumericSentinels();

		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{}");
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));

		String canonical = Files.readString(f);
		for (Map.Entry<String, Number> e : SENTINELS.entrySet()) {
			String line = "\"" + e.getKey() + "\": " + literal(e.getValue());
			assertTrue(canonical.contains(line),
					"snapshot must emit the live sentinel for '" + e.getKey() + "' via the getter, not 0;"
							+ " missing line: " + line);
		}
	}

	/**
	 * Boolean tunables need both polarities asserted to kill both PIT boolean-return mutants:
	 * {@code BooleanFalseReturnValsMutator} (return false) is caught when the live value is TRUE,
	 * {@code BooleanTrueReturnValsMutator} (return true) is caught when the live value is FALSE. This
	 * pair of tests covers both polarities on both fields (defaults are TRUE), so all four getter
	 * mutants across the two boolean fields are pinned.
	 */
	@Test
	void absentBooleanKeys_keepTrueValueViaGetter(@TempDir Path dir)
			throws IOException, ReflectiveOperationException {
		// bonusChestEnabled=TRUE kills BooleanFalseReturnVals on its getter;
		// oilBurns=TRUE        kills BooleanFalseReturnVals on its getter too;
		// bareCableShockEnabled=TRUE likewise (MOD-324 — added with MOD-279, never covered here).
		Config.class.getDeclaredField("bonusChestEnabled").setBoolean(null, true);
		Config.class.getDeclaredField("oilBurns").setBoolean(null, true);
		Config.class.getDeclaredField("bareCableShockEnabled").setBoolean(null, true);

		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{}");
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(true, Config.bonusChestEnabled,
				"absent boolean key 'bonusChestEnabled' keeps its live TRUE via the getter, not false");
		assertEquals(true, Config.oilBurns,
				"absent boolean key 'oilBurns' keeps its live TRUE via the getter, not false");
		assertEquals(true, Config.bareCableShockEnabled,
				"absent boolean key 'bareCableShockEnabled' keeps its live TRUE via the getter, not false");
	}

	@Test
	void absentBooleanKeys_keepFalseValueViaGetter(@TempDir Path dir)
			throws IOException, ReflectiveOperationException {
		// bonusChestEnabled=FALSE kills BooleanTrueReturnVals on its getter;
		// oilBurns=FALSE          kills BooleanTrueReturnVals on its getter too;
		// bareCableShockEnabled=FALSE likewise (MOD-324).
		Config.class.getDeclaredField("bonusChestEnabled").setBoolean(null, false);
		Config.class.getDeclaredField("oilBurns").setBoolean(null, false);
		Config.class.getDeclaredField("bareCableShockEnabled").setBoolean(null, false);

		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{}");
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(false, Config.bonusChestEnabled,
				"absent boolean key 'bonusChestEnabled' keeps its live FALSE via the getter, not true");
		assertEquals(false, Config.oilBurns,
				"absent boolean key 'oilBurns' keeps its live FALSE via the getter, not true");
		assertEquals(false, Config.bareCableShockEnabled,
				"absent boolean key 'bareCableShockEnabled' keeps its live FALSE via the getter, not true");
	}

	/**
	 * Config.reload() delegates to loadFrom(configPath.get()); bind the supplier to the baseline and
	 * confirm reload returns LOADED (was NO_COVERAGE before — the method was never called by any test).
	 */
	@Test
	void reload_readsTheBoundConfigPath() {
		java.util.function.Supplier<Path> saved = Config.configPath;
		try {
			Config.configPath = () -> baseline;
			assertEquals(Config.LoadResult.LOADED, Config.reload(),
					"reload() reads the loader-bound configPath and reports its load result");
		} finally {
			Config.configPath = saved;
		}
	}

	/**
	 * The existing suite exercised {@code getInt} (and thus {@code getDouble} via copperCableLoss) with
	 * a present key, but never {@code getBool} or {@code getFloat} with a present value — so PIT's
	 * return-value mutators on those two readers (return false / return true / return 0.0f) and the
	 * NegateConditionals on their type-check guards survived. Loading a present boolean key in BOTH
	 * polarities and a present float key with a distinct literal kills all four mutants.
	 *
	 * <p>Literals are hand-written ({@code false}, {@code true}, {@code 2.25f}) — never the field's own
	 * default, so a mutant that returns the default value is also caught.
	 */
	@Test
	void presentBooleanKey_appliesTheFilesValue(@TempDir Path dir) throws IOException {
		// FALSE is the non-default polarity for both fields (defaults are true, true), so it kills
		// BooleanTrueReturnVals on getBool as well as the field-apply path.
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"bonusChestEnabled\": false, \"oilBurns\": false }");
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(false, Config.bonusChestEnabled, "present boolean key applies false, not the default true");
		assertEquals(false, Config.oilBurns, "present boolean key applies false, not the default true");
	}

	@Test
	void presentBooleanKey_appliesTrueOverridingFalseDefault(@TempDir Path dir) throws IOException {
		// Set the live fields to FALSE first (the non-default polarity), then load TRUE — kills
		// BooleanFalseReturnVals on getBool, which would otherwise leave the field at false.
		Config.bonusChestEnabled = false;
		Config.oilBurns = false;
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"bonusChestEnabled\": true, \"oilBurns\": true }");
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(true, Config.bonusChestEnabled, "present boolean key applies true over a false default");
		assertEquals(true, Config.oilBurns, "present boolean key applies true over a false default");
	}

	@Test
	void presentFloatKey_appliesTheFilesValue(@TempDir Path dir) throws IOException {
		// windMillRainFactor default is 1.5f; 2.25f is a hand-written literal distinct from both the
		// default and PIT's 0.0f replacement. Kills PrimitiveReturns (return 0.0f) and the
		// NegateConditionals on getFloat's type-check guard.
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"windMillRainFactor\": 2.25 }");
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(2.25f, Config.windMillRainFactor, 0.0f,
				"present float key applies 2.25, not 0.0f and not the 1.5f default");
	}

	// --- helpers ---

	/** Set every numeric tunable to its sentinel via reflection. */
	private static void applyNumericSentinels() throws ReflectiveOperationException {
		for (Map.Entry<String, Number> e : SENTINELS.entrySet()) {
			Field fld = Config.class.getDeclaredField(e.getKey());
			Number v = e.getValue();
			if (fld.getType() == int.class) {
				fld.setInt(null, v.intValue());
			} else if (fld.getType() == float.class) {
				fld.setFloat(null, v.floatValue());
			} else if (fld.getType() == double.class) {
				fld.setDouble(null, v.doubleValue());
			} else {
				throw new AssertionError("unexpected numeric tunable type for " + e.getKey() + ": " + fld.getType());
			}
		}
	}

	/** Read a static numeric field and compare against the sentinel with the right primitive equality. */
	private static void assertFieldEquals(String name, Number expected, String msg)
			throws ReflectiveOperationException {
		Field fld = Config.class.getDeclaredField(name);
		if (fld.getType() == int.class) {
			assertEquals(expected.intValue(), fld.getInt(null), msg);
		} else if (fld.getType() == float.class) {
			assertEquals(expected.floatValue(), fld.getFloat(null), 0.0f, msg);
		} else if (fld.getType() == double.class) {
			assertEquals(expected.doubleValue(), fld.getDouble(null), 0.0, msg);
		} else {
			throw new AssertionError("unexpected numeric tunable type for " + name + ": " + fld.getType());
		}
	}

	/**
	 * Render a sentinel the way Gson serializes it: ints bare, floats/doubles by {@code toString}
	 * (Gson prints {@code 2.5f} as {@code 2.5}, {@code 0.045} as {@code 0.045}). Used only to build the
	 * substring the canonical file must contain — never re-reads the live field.
	 */
	private static String literal(Number v) {
		if (v instanceof Float || v instanceof Double) {
			return v.toString();
		}
		return Integer.toString(v.intValue());
	}
}
