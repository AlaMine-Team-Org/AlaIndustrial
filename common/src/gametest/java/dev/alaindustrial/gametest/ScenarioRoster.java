package dev.alaindustrial.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every world gametest declared as a {@link RosterEntry} — the one list both loaders replay
 * (MOD-717, ADR-038). Fabric: {@code ScenarioRosterFabric}; NeoForge: {@code ScenarioRosterNeoForge}.
 *
 * <p>A scenario class joins by declaring {@code XScenarios.Roster.ENTRIES} and adding that list here.
 * A holder that is declared but not listed here would silently never run;
 * {@code docs/tools/testing/gametest_lane_parity_check.py} fails on it, and on a scenario that is both declared
 * here and still wired by hand on a lane.
 *
 * <p>Scenarios not yet moved here keep their hand wiring (a Fabric {@code @GameTest} wrapper plus a
 * {@code registerTest} line in {@code NeoForgeGameTests}) until their class migrates.
 */
public final class ScenarioRoster {

	/** One list per scenario class, in the order the classes migrated. */
	public static final List<List<RosterEntry>> PARTS = List.of(
			AbandonedLabScenarios.Roster.ENTRIES,
			AdvancedCircuitScenarios.Roster.ENTRIES,
			AdvancementCriteriaScenarios.Roster.ENTRIES,
			AlaCommonScenarios.Roster.ENTRIES,
			AlloySmelterScenarios.Roster.ENTRIES,
			ArchiveRecordScenarios.Roster.ENTRIES,
			AssemblerPerfScenarios.Roster.ENTRIES,
			AssemblerPlanningScenarios.Roster.ENTRIES,
			AssemblerScenarios.Roster.ENTRIES,
			BatteryDrawerScenarios.Roster.ENTRIES,
			BatteryScenarios.Roster.ENTRIES,
			BlockCodecScenarios.Roster.ENTRIES,
			BlockEntityDisplayNameSnapshotScenarios.Roster.ENTRIES,
			BlockEntityPersistenceSweepScenarios.Roster.ENTRIES,
			BlockEntitySlotLayoutSnapshotScenarios.Roster.ENTRIES,
			BlockPropsCharacterizationScenarios.Roster.ENTRIES,
			CableArmEndpointParityScenarios.Roster.ENTRIES,
			CableBreakerScenarios.Roster.ENTRIES,
			CableDyeScenarios.Roster.ENTRIES,
			CableEnergyScenarios.Roster.ENTRIES,
			CableFaceParityScenarios.Roster.ENTRIES,
			CableInsulationScenarios.Roster.ENTRIES,
			CablePlacementScenarios.Roster.ENTRIES,
			CableShockScenarios.Roster.ENTRIES,
			CanningMachineScenarios.Roster.ENTRIES,
			CapsuleScenarios.Roster.ENTRIES,
			CascadeAddressScenarios.Roster.ENTRIES,
			CeramicScenarios.Roster.ENTRIES,
			CesuScenarios.Roster.ENTRIES,
			ChargePadScenarios.Roster.ENTRIES,
			ComponentRepairBenchScenarios.Roster.ENTRIES,
			ComponentTierScenarios.Roster.ENTRIES,
			ConcentratorStructureScenarios.Roster.ENTRIES,
			ConfigOverridesScenarios.Roster.ENTRIES,
			ConfigSyncScenarios.Roster.ENTRIES,
			CookingTimeScenarios.Roster.ENTRIES,
			CoreFluidScenarios.Roster.ENTRIES,
			CreativeEnergySourceScenarios.Roster.ENTRIES,
			CreativeTabCoverageScenarios.Roster.ENTRIES,
			CreativeTabSnapshotScenarios.Roster.ENTRIES,
			CrystalFarmScenarios.Roster.ENTRIES,
			CrystalPrimingScenarios.Roster.ENTRIES,
			DataComponentRoundTripScenarios.Roster.ENTRIES,
			DemoStandScenarios.Roster.ENTRIES,
			DischargeChannelBoundaryScenarios.Roster.ENTRIES,
			DistillationColumnScenarios.Roster.ENTRIES,
			DoubleChestScenarios.Roster.ENTRIES,
			ElectricBowScenarios.Roster.ENTRIES,
			ElectricChainsawScenarios.Roster.ENTRIES,
			ElectricDrillScenarios.Roster.ENTRIES,
			ElectricHoeScenarios.Roster.ENTRIES,
			ElectricMiningToolContractScenarios.Roster.ENTRIES,
			ElectricSaberScenarios.Roster.ENTRIES,
			ElectricShovelScenarios.Roster.ENTRIES,
			ElectricToolTagScenarios.Roster.ENTRIES,
			ElectrumChestScenarios.Roster.ENTRIES,
			EnchantableRosterScenarios.Roster.ENTRIES,
			EnergyCondenserScenarios.Roster.ENTRIES,
			EnergyFlowFieldGoldenScenarios.Roster.ENTRIES,
			EnergyNetworkPerfScenarios.Roster.ENTRIES,
			EnergyPackScenarios.Roster.ENTRIES,
			EnergyRoleForFaceSnapshotScenarios.Roster.ENTRIES,
			EnergySpurScenarios.Roster.ENTRIES,
			EnrichedUraniumTorchScenarios.Roster.ENTRIES,
			FermenterScenarios.Roster.ENTRIES,
			FluidDefinitionScenarios.Roster.ENTRIES,
			FluidLineGoldenScenarios.Roster.ENTRIES,
			FluidLineThroughputScenarios.Roster.ENTRIES,
			FluidMachineScenarios.Roster.ENTRIES,
			FluidNetworkPerfScenarios.Roster.ENTRIES,
			FluidPipeScenarios.Roster.ENTRIES,
			FluidTankScenarios.Roster.ENTRIES,
			FluidWashScenarios.Roster.ENTRIES,
			FluxweaveArmorScenarios.Roster.ENTRIES,
			ForeignMaterialScenarios.Roster.ENTRIES,
			GalvanicBathScenarios.Roster.ENTRIES,
			GardenDroneScenarios.Roster.ENTRIES,
			GeneratorEnergyScenarios.Roster.ENTRIES,
			GeneratorScenarios.Roster.ENTRIES,
			GeothermalLavaInputScenarios.Roster.ENTRIES,
			GuideBookGiverScenarios.Roster.ENTRIES,
			HammerScenarios.Roster.ENTRIES,
			IncubatorScenarios.Roster.ENTRIES,
			IndustrialistScenarios.Roster.ENTRIES,
			IronFurnaceFuelScenarios.Roster.ENTRIES,
			ItemPipeScenarios.Roster.ENTRIES,
			JetpackScenarios.Roster.ENTRIES,
			KokSagyzScenarios.Roster.ENTRIES,
			LegacySaveCorpusScenarios.Roster.ENTRIES,
			LightningRodScenarios.Roster.ENTRIES,
			MachineCapabilityRoundTripScenarios.Roster.ENTRIES,
			MachineCycleTraceScenarios.Roster.ENTRIES,
			MachineEnergyScenarios.Roster.ENTRIES,
			MachineScenarios.Roster.ENTRIES,
			MagnetModuleScenarios.Roster.ENTRIES,
			MagnetScenarios.Roster.ENTRIES,
			MenuChannelValuesScenarios.Roster.ENTRIES,
			MenuDataWidthScenarios.Roster.ENTRIES,
			MobRepellerScenarios.Roster.ENTRIES,
			MobSpawnEquipmentScenarios.Roster.ENTRIES,
			MockPlayerScenarios.Roster.ENTRIES,
			Mod353DiagnosticScenarios.Roster.ENTRIES,
			MonitorNetworkScenarios.Roster.ENTRIES,
			MuteChipScenarios.Roster.ENTRIES,
			NetworkAnalyzerScenarios.Roster.ENTRIES,
			OilScenarios.Roster.ENTRIES,
			OperationEnergyScenarios.Roster.ENTRIES,
			OreScenarios.Roster.ENTRIES,
			OverclockerEffectScenarios.Roster.ENTRIES,
			OverclockerPanelScenarios.Roster.ENTRIES,
			PersistenceScenarios.Roster.ENTRIES,
			PipeEnergyRoleScenarios.Roster.ENTRIES,
			PipeLowArmScenarios.Roster.ENTRIES,
			PistonPushReactionScenarios.Roster.ENTRIES,
			PlayerAttachmentRoundTripScenarios.Roster.ENTRIES,
			PlayerStatsScenarios.Roster.ENTRIES,
			PolymerizerScenarios.Roster.ENTRIES,
			PouchScenarios.Roster.ENTRIES,
			PoweredItemBarScenarios.Roster.ENTRIES,
			PoweredItemEnergySnapshotScenarios.Roster.ENTRIES,
			RadiationEmitterScenarios.Roster.ENTRIES,
			RadiationScenarios.Roster.ENTRIES,
			ReactorBreachOrderScenarios.Roster.ENTRIES,
			ReactorControllerPersistenceScenarios.Roster.ENTRIES,
			ReactorGoldenTraceScenarios.Roster.ENTRIES,
			ReactorScenarios.Roster.ENTRIES,
			ReactorSteamScenarios.Roster.ENTRIES,
			RecipeCoverageScenarios.Roster.ENTRIES,
			RecipeFamilyRegistrationScenarios.Roster.ENTRIES,
			RecipeTagScenarios.Roster.ENTRIES,
			RegistrySnapshotScenarios.Roster.ENTRIES,
			RosterSelfTestScenarios.Roster.ENTRIES,
			RtpScenarios.Roster.ENTRIES,
			SavedDataRoundTripScenarios.Roster.ENTRIES,
			ScytheScenarios.Roster.ENTRIES,
			ServerHookWiringScenarios.Roster.ENTRIES,
			SkillEffectScenarios.Roster.ENTRIES,
			SkillPurchaseScenarios.Roster.ENTRIES,
			SolarPanelScenarios.Roster.ENTRIES,
			SprinklerScenarios.Roster.ENTRIES,
			StatsChipScenarios.Roster.ENTRIES,
			SteamPipeScenarios.Roster.ENTRIES,
			StockDisplayFrameScenarios.Roster.ENTRIES,
			StorageClusterScenarios.Roster.ENTRIES,
			StorageEnergyScenarios.Roster.ENTRIES,
			TeleporterCapsuleScenarios.Roster.ENTRIES,
			TeleporterGuiScenarios.Roster.ENTRIES,
			TeleporterJumpScenarios.Roster.ENTRIES,
			TeleporterLogScenarios.Roster.ENTRIES,
			TeleporterRegistryScenarios.Roster.ENTRIES,
			TeleporterStationScenarios.Roster.ENTRIES,
			TemperedIronToolScenarios.Roster.ENTRIES,
			ThermalCentrifugeScenarios.Roster.ENTRIES,
			TrellisScenarios.Roster.ENTRIES,
			UpgradeTableAssemblyScenarios.Roster.ENTRIES,
			VulcanizerScenarios.Roster.ENTRIES,
			WaterMillWheelScenarios.Roster.ENTRIES,
			WindMillScenarios.Roster.ENTRIES,
			WorkstationScenarios.Roster.ENTRIES,
			WorldContentScenarios.Roster.ENTRIES,
			WorldgenFeatureSnapshotScenarios.Roster.ENTRIES,
			WorldgenInjectionScenarios.Roster.ENTRIES);

	/** Every entry, in declaration order. */
	public static final List<RosterEntry> ALL = flatten(PARTS);

	private ScenarioRoster() {}

	/** Flattens the parts and refuses a duplicate id on either lane, naming it. */
	static List<RosterEntry> flatten(List<List<RosterEntry>> parts) {
		List<RosterEntry> all = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (List<RosterEntry> part : parts) {
			for (RosterEntry entry : part) {
				for (RosterEntry.Lane lane : RosterEntry.Lane.values()) {
					String key = lane + " " + entry.testId(lane);
					if (!seen.add(key)) {
						throw new IllegalStateException("Duplicate gametest id in the roster: " + key);
					}
				}
				all.add(entry);
			}
		}
		return List.copyOf(all);
	}
}
