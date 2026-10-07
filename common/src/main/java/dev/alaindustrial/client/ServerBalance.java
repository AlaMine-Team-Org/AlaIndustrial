package dev.alaindustrial.client;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.KnobSnapshot;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.item.ToolConfig;
import java.util.Map;
import java.util.Optional;

// size-justified: one three-line accessor per client-visible knob, named exactly as the knob — the shape
// ClientVisibleKnobsRules checks; splitting it would only scatter that one-to-one table.
/**
 * The balance numbers the client SHOWS (MOD-695): the server's, once it has sent them, and the local
 * {@link Config} until then.
 *
 * <p><b>Why this class exists.</b> On a dedicated server the client's {@code Config} holds the PLAYER's
 * own {@code config/alaindustrial.json}, which usually is the untouched default. Every tooltip,
 * recipe-viewer page and screen that read {@code Config.*} directly showed those local numbers — the
 * player's mastery level, the teleporter's free points, the recycler's batch, every tooltip figure —
 * while the server played by its own. The server now sends its client-visible knobs
 * ({@code ConfigSyncPayload}) and client code reads them here.
 *
 * <p><b>Why a separate copy and not {@code Config}'s fields.</b> In single player the client and the
 * integrated server share one JVM and one {@code Config}; writing the received values into it would
 * overwrite the server's balance from the client thread, through fields that carry no
 * {@code volatile}. The received numbers live in one immutable {@link KnobSnapshot} behind a volatile
 * reference: {@link #receive} swaps the reference, readers see either the old snapshot or the new one,
 * never a half-applied mix, and nothing here ever writes to {@code Config}.
 *
 * <p><b>The accessors ARE the list of client-visible knobs.</b> One per knob flagged
 * {@code @Knob(clientVisible = true)}, named exactly like the field; {@code ServerBalanceTest} fails when
 * a flagged knob has no accessor, an accessor has no flag, or an accessor reads the wrong key, and
 * {@code ArchitectureRules.clientReadsBalanceThroughServerBalance} fails when client code reads a knob
 * from {@code Config} instead of from here. A knob missing from the snapshot (an older server) falls back
 * to the local value, knob by knob.
 *
 * <p>Cleared when the player leaves a world ({@code ClientDisconnectReset}), so a single-player world
 * opened after a server shows its own balance, not the server's. Minecraft-free, so L1 covers it
 * directly; both loaders' receivers call {@link #receive} on the client thread.
 */
public final class ServerBalance {

	/** What "nothing received" reads as: an empty snapshot, so every accessor falls back to {@code Config}. */
	private static final KnobSnapshot LOCAL = KnobSnapshot.of(Map.of());

	private static volatile KnobSnapshot current = LOCAL;

	private ServerBalance() {
	}

	/**
	 * Takes the server's encoded snapshot. Returns whether it was kept: bytes of another
	 * {@link KnobSnapshot#FORMAT}, or malformed ones, are refused and the numbers already shown stay —
	 * the server's previous snapshot, or the local ones if none has arrived (MOD-695, open question 3).
	 */
	public static boolean receive(byte[] data) {
		Optional<KnobSnapshot> decoded = KnobSnapshot.decode(data);
		if (decoded.isEmpty()) {
			Industrialization.LOGGER.warn("[config-sync] ignoring a server balance snapshot this build cannot"
					+ " read (expected format {}); showing the numbers already in use", KnobSnapshot.FORMAT);
			return false;
		}
		current = decoded.get();
		return true;
	}

	/** Forgets the server's numbers: the player left the world they belonged to. */
	public static void reset() {
		current = LOCAL;
	}

	/** Whether the numbers shown come from a server snapshot rather than the local file. */
	public static boolean fromServer() {
		return current != LOCAL;
	}

	// --- Derived figures: the MachineRates formulas, fed the server's numbers ---

	/** {@link MachineRates#euPerTick} on the server's machine EU/t and speed multiplier. */
	public static int machineEuPerTickEffective() {
		return MachineRates.euPerTick(machineEuPerTick(), globalMachineSpeedMultiplier());
	}

	/** {@link MachineRates#euPerTick} on the server's heater EU/t and speed multiplier. */
	public static int electricHeaterEuPerTickEffective() {
		return MachineRates.euPerTick(electricHeaterEuPerTick(), globalMachineSpeedMultiplier());
	}

	/** {@link MachineRates#duration} on the server's speed multiplier. */
	public static int scaledDuration(int baseTicks) {
		return MachineRates.duration(baseTicks, globalMachineSpeedMultiplier());
	}

	/** {@link MachineRates#vanillaSmeltEu} on the server's furnace duration, machine EU/t and multiplier. */
	public static int electricFurnaceVanillaSmeltEu() {
		return MachineRates.vanillaSmeltEu(electricFurnaceDuration(), machineEuPerTick(),
				globalMachineSpeedMultiplier());
	}

	// --- One accessor per client-visible knob, named like the Config field it mirrors ---

	public static int alloySmelterDuration() {
		return current.intValue("alloySmelterDuration", Config.alloySmelterDuration);
	}

	public static int alloySmelterEuPerTick() {
		return current.intValue("alloySmelterEuPerTick", Config.alloySmelterEuPerTick);
	}

	public static int assemblerBuffer() {
		return current.intValue("assemblerBuffer", Config.assemblerBuffer);
	}

	public static int assemblerDuration() {
		return current.intValue("assemblerDuration", Config.assemblerDuration);
	}

	public static int assemblerEuPerTick() {
		return current.intValue("assemblerEuPerTick", Config.assemblerEuPerTick);
	}

	public static int batteryBoxBuffer() {
		return current.intValue("batteryBoxBuffer", Config.batteryBoxBuffer);
	}

	public static int canningFoodValuePerCan() {
		return current.intValue("canningFoodValuePerCan", Config.canningFoodValuePerCan);
	}

	public static int canningMachineDuration() {
		return current.intValue("canningMachineDuration", Config.canningMachineDuration);
	}

	public static int ceramicPressRedstoneCost() {
		return current.intValue("ceramicPressRedstoneCost", Config.ceramicPressRedstoneCost);
	}

	public static int cesuBuffer() {
		return current.intValue("cesuBuffer", Config.cesuBuffer);
	}

	public static int chargePadBuffer() {
		return current.intValue("chargePadBuffer", Config.chargePadBuffer);
	}

	public static int chargePadInputRate() {
		return current.intValue("chargePadInputRate", Config.chargePadInputRate);
	}

	public static int chargePadOutputRate() {
		return current.intValue("chargePadOutputRate", Config.chargePadOutputRate);
	}

	public static int clotThresholdI() {
		return current.intValue("clotThresholdI", Config.clotThresholdI);
	}

	public static int compressorDuration() {
		return current.intValue("compressorDuration", Config.compressorDuration);
	}

	public static int condenserCapacity() {
		return current.intValue("condenserCapacity", Config.condenserCapacity);
	}

	public static int condenserInputRate() {
		return current.intValue("condenserInputRate", Config.condenserInputRate);
	}

	public static int daylightEuPerTick() {
		return current.intValue("daylightEuPerTick", GeneratorConfig.daylightEuPerTick);
	}

	public static int distillationColumnDuration() {
		return current.intValue("distillationColumnDuration", Config.distillationColumnDuration);
	}

	public static int electricBowEuPerShot() {
		return current.intValue("electricBowEuPerShot", ToolConfig.electricBowEuPerShot);
	}

	public static int electricChainsawEuPerBlock() {
		return current.intValue("electricChainsawEuPerBlock", ToolConfig.electricChainsawEuPerBlock);
	}

	public static int electricDrillEuPerBlock() {
		return current.intValue("electricDrillEuPerBlock", ToolConfig.electricDrillEuPerBlock);
	}

	public static int electricFurnaceDuration() {
		return current.intValue("electricFurnaceDuration", Config.electricFurnaceDuration);
	}

	public static int electricHeaterBuffer() {
		return current.intValue("electricHeaterBuffer", Config.electricHeaterBuffer);
	}

	public static int electricHeaterEuPerTick() {
		return current.intValue("electricHeaterEuPerTick", Config.electricHeaterEuPerTick);
	}

	public static int electricHoeEuPerBlock() {
		return current.intValue("electricHoeEuPerBlock", ToolConfig.electricHoeEuPerBlock);
	}

	public static int electricSaberEuPerHit() {
		return current.intValue("electricSaberEuPerHit", ToolConfig.electricSaberEuPerHit);
	}

	public static int electricShovelEuPerBlock() {
		return current.intValue("electricShovelEuPerBlock", ToolConfig.electricShovelEuPerBlock);
	}

	public static int euPerXp() {
		return current.intValue("euPerXp", Config.euPerXp);
	}

	public static int euPerXpGenerated() {
		return current.intValue("euPerXpGenerated", Config.euPerXpGenerated);
	}

	public static int extractorDuration() {
		return current.intValue("extractorDuration", Config.extractorDuration);
	}

	public static int fermenterDuration() {
		return current.intValue("fermenterDuration", Config.fermenterDuration);
	}

	public static int fluidPipeSegmentBuffer() {
		return current.intValue("fluidPipeSegmentBuffer", Config.fluidPipeSegmentBuffer);
	}

	public static int fuelEuPerTick() {
		return current.intValue("fuelEuPerTick", GeneratorConfig.fuelEuPerTick);
	}

	public static int galvanicBathDuration() {
		return current.intValue("galvanicBathDuration", Config.galvanicBathDuration);
	}

	public static int gardenDroneBuffer() {
		return current.intValue("gardenDroneBuffer", Config.gardenDroneBuffer);
	}

	public static int gardenDroneEuPerAction() {
		return current.intValue("gardenDroneEuPerAction", Config.gardenDroneEuPerAction);
	}

	public static int gardenDroneRange() {
		return current.intValue("gardenDroneRange", Config.gardenDroneRange);
	}

	public static int generatorBuffer() {
		return current.intValue("generatorBuffer", GeneratorConfig.generatorBuffer);
	}

	public static int geothermalBuffer() {
		return current.intValue("geothermalBuffer", GeneratorConfig.geothermalBuffer);
	}

	public static int geothermalBurnTicks() {
		return current.intValue("geothermalBurnTicks", GeneratorConfig.geothermalBurnTicks);
	}

	public static int geothermalEuPerTick() {
		return current.intValue("geothermalEuPerTick", GeneratorConfig.geothermalEuPerTick);
	}

	public static float globalMachineSpeedMultiplier() {
		return current.floatValue("globalMachineSpeedMultiplier", Config.globalMachineSpeedMultiplier);
	}

	public static int incubatorBuffer() {
		return current.intValue("incubatorBuffer", Config.incubatorBuffer);
	}

	public static int incubatorEuPerTick() {
		return current.intValue("incubatorEuPerTick", Config.incubatorEuPerTick);
	}

	public static int itemPipeTransferIntervalTicks() {
		return current.intValue("itemPipeTransferIntervalTicks", Config.itemPipeTransferIntervalTicks);
	}

	public static int kokSagyzSandGrowthPercent() {
		return current.intValue("kokSagyzSandGrowthPercent", Config.kokSagyzSandGrowthPercent);
	}

	public static float levelXpMultiplier() {
		return current.floatValue("levelXpMultiplier", Config.levelXpMultiplier);
	}

	public static int lightningRodBuffer() {
		return current.intValue("lightningRodBuffer", GeneratorConfig.lightningRodBuffer);
	}

	public static int maceratorBuffer() {
		return current.intValue("maceratorBuffer", Config.maceratorBuffer);
	}

	public static int maceratorDuration() {
		return current.intValue("maceratorDuration", Config.maceratorDuration);
	}

	public static int machineBuffer() {
		return current.intValue("machineBuffer", Config.machineBuffer);
	}

	public static int machineEuPerTick() {
		return current.intValue("machineEuPerTick", Config.machineEuPerTick);
	}

	public static int mobRepellerBuffer() {
		return current.intValue("mobRepellerBuffer", Config.mobRepellerBuffer);
	}

	public static int mobRepellerEuPerTick() {
		return current.intValue("mobRepellerEuPerTick", Config.mobRepellerEuPerTick);
	}

	public static int mobRepellerEvolveKillsHv() {
		return current.intValue("mobRepellerEvolveKillsHv", Config.mobRepellerEvolveKillsHv);
	}

	public static int mobRepellerEvolveKillsMv() {
		return current.intValue("mobRepellerEvolveKillsMv", Config.mobRepellerEvolveKillsMv);
	}

	public static int mobRepellerRange() {
		return current.intValue("mobRepellerRange", Config.mobRepellerRange);
	}

	public static int moonlitEuPerTick() {
		return current.intValue("moonlitEuPerTick", GeneratorConfig.moonlitEuPerTick);
	}

	public static int moonlitWeatherEuPerTick() {
		return current.intValue("moonlitWeatherEuPerTick", GeneratorConfig.moonlitWeatherEuPerTick);
	}

	public static int mutationAttemptsPerIngot() {
		return current.intValue("mutationAttemptsPerIngot", Config.mutationAttemptsPerIngot);
	}

	public static double mutationChanceCap() {
		return current.doubleValue("mutationChanceCap", Config.mutationChanceCap);
	}

	public static double mutationChanceCreate() {
		return current.doubleValue("mutationChanceCreate", Config.mutationChanceCreate);
	}

	public static double mutationChanceDuplicate() {
		return current.doubleValue("mutationChanceDuplicate", Config.mutationChanceDuplicate);
	}

	public static double mutationChanceTransform() {
		return current.doubleValue("mutationChanceTransform", Config.mutationChanceTransform);
	}

	public static int mutationDurationCreate() {
		return current.intValue("mutationDurationCreate", Config.mutationDurationCreate);
	}

	public static int mutationDurationDuplicate() {
		return current.intValue("mutationDurationDuplicate", Config.mutationDurationDuplicate);
	}

	public static int mutationDurationTransform() {
		return current.intValue("mutationDurationTransform", Config.mutationDurationTransform);
	}

	public static double mutationGradeEpic() {
		return current.doubleValue("mutationGradeEpic", Config.mutationGradeEpic);
	}

	public static double mutationGradeLegendary() {
		return current.doubleValue("mutationGradeLegendary", Config.mutationGradeLegendary);
	}

	public static double mutationGradeRare() {
		return current.doubleValue("mutationGradeRare", Config.mutationGradeRare);
	}

	public static double oilSootChance() {
		return current.doubleValue("oilSootChance", Config.oilSootChance);
	}

	public static float overclockerEuFactor() {
		return current.floatValue("overclockerEuFactor", Config.overclockerEuFactor);
	}

	public static float overclockerSpeedFactor() {
		return current.floatValue("overclockerSpeedFactor", Config.overclockerSpeedFactor);
	}

	public static int piezoPlateLivingPressEu() {
		return current.intValue("piezoPlateLivingPressEu", GeneratorConfig.piezoPlateLivingPressEu);
	}

	public static int piezoPlateObjectPressEu() {
		return current.intValue("piezoPlateObjectPressEu", GeneratorConfig.piezoPlateObjectPressEu);
	}

	public static int polymerizerDuration() {
		return current.intValue("polymerizerDuration", Config.polymerizerDuration);
	}

	public static int pumpEuPerBucket() {
		return current.intValue("pumpEuPerBucket", Config.pumpEuPerBucket);
	}

	public static int radiantBuffer() {
		return current.intValue("radiantBuffer", GeneratorConfig.radiantBuffer);
	}

	public static int radiantEuPerTick() {
		return current.intValue("radiantEuPerTick", GeneratorConfig.radiantEuPerTick);
	}

	public static int recyclerBatchMass() {
		return current.intValue("recyclerBatchMass", Config.recyclerBatchMass);
	}

	public static int recyclerDuration() {
		return current.intValue("recyclerDuration", Config.recyclerDuration);
	}

	public static int recyclerEuPerTick() {
		return current.intValue("recyclerEuPerTick", Config.recyclerEuPerTick);
	}

	public static int repairBenchEuPerTick() {
		return current.intValue("repairBenchEuPerTick", Config.repairBenchEuPerTick);
	}

	public static int repairBenchMaxDamageDecayPercent() {
		return current.intValue("repairBenchMaxDamageDecayPercent", Config.repairBenchMaxDamageDecayPercent);
	}

	public static int repairBenchTier1EuCost() {
		return current.intValue("repairBenchTier1EuCost", Config.repairBenchTier1EuCost);
	}

	public static int sawmillDuration() {
		return current.intValue("sawmillDuration", Config.sawmillDuration);
	}

	public static double scytheBonusSeedMultiplier() {
		return current.doubleValue("scytheBonusSeedMultiplier", ToolConfig.scytheBonusSeedMultiplier);
	}

	public static int solarBuffer() {
		return current.intValue("solarBuffer", GeneratorConfig.solarBuffer);
	}

	public static int solarEuPerTick() {
		return current.intValue("solarEuPerTick", GeneratorConfig.solarEuPerTick);
	}

	public static int sprinklerRange() {
		return current.intValue("sprinklerRange", Config.sprinklerRange);
	}

	public static int sprinklerTankMb() {
		return current.intValue("sprinklerTankMb", Config.sprinklerTankMb);
	}

	public static int teleporterBuffer() {
		return current.intValue("teleporterBuffer", Config.teleporterBuffer);
	}

	public static int teleporterMaxPoints() {
		return current.intValue("teleporterMaxPoints", Config.teleporterMaxPoints);
	}

	public static int thermalCentrifugeDuration() {
		return current.intValue("thermalCentrifugeDuration", Config.thermalCentrifugeDuration);
	}

	public static int thermalCentrifugeEuPerTick() {
		return current.intValue("thermalCentrifugeEuPerTick", Config.thermalCentrifugeEuPerTick);
	}

	public static int upgradeTableDuration() {
		return current.intValue("upgradeTableDuration", Config.upgradeTableDuration);
	}

	public static int upgradeTableEuPerTick() {
		return current.intValue("upgradeTableEuPerTick", Config.upgradeTableEuPerTick);
	}

	public static int vulcanizerDuration() {
		return current.intValue("vulcanizerDuration", Config.vulcanizerDuration);
	}

	public static int waterMillBuffer() {
		return current.intValue("waterMillBuffer", GeneratorConfig.waterMillBuffer);
	}

	public static int waterMillEuPerTick() {
		return current.intValue("waterMillEuPerTick", GeneratorConfig.waterMillEuPerTick);
	}

	public static int windMillBuffer() {
		return current.intValue("windMillBuffer", GeneratorConfig.windMillBuffer);
	}

	public static int windMillMaxEuPerTick() {
		return current.intValue("windMillMaxEuPerTick", GeneratorConfig.windMillMaxEuPerTick);
	}

	public static int xpLevelOneCost() {
		return current.intValue("xpLevelOneCost", Config.xpLevelOneCost);
	}
}
