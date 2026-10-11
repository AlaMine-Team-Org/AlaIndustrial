package dev.alaindustrial.client.blueprint;

import java.util.List;
import java.util.Optional;

/**
 * Every blueprint the mod ships (MOD-797) — one list, read by the tooltip line, the guide-book button and
 * the blueprint screen on both loaders. The blueprints themselves live in their group classes
 * ({@link EnergyBlueprints}, {@link MachineBlueprints}, {@link LogisticsBlueprints}); a new one is an entry
 * there, a line here and its lang keys in every {@code lang/} file. {@code BlueprintCatalogTest} fails on a
 * block id with no blockstate file and on a key missing from {@code en_us.json}.
 */
public final class BlueprintCatalog {

	/** The catalogue, in the order the archive lists it. */
	public static final List<BlueprintDef> ALL = List.of(
			EnergyBlueprints.ENERGY_BASICS,
			EnergyBlueprints.SOLAR_STORAGE,
			MachineBlueprints.ORE_PROCESSING,
			EnergyBlueprints.CABLE_GRADES,
			EnergyBlueprints.MV_STORAGE,
			MachineBlueprints.SMELTING,
			EnergyBlueprints.LAVA_POWER,
			EnergyBlueprints.INSULATED_CABLES,
			EnergyBlueprints.SOLAR_TIERS,
			LogisticsBlueprints.ITEM_PIPES,
			LogisticsBlueprints.FLUIDS,
			EnergyBlueprints.WIND_AND_WATER,
			EnergyBlueprints.EVENT_POWER,
			MachineBlueprints.WATER_BATH,
			MachineBlueprints.HEAT,
			MachineBlueprints.AUTOMATION,
			MachineBlueprints.RUBBER_LINE,
			MachineBlueprints.BIO_FARM,
			MachineBlueprints.WOOD_AND_WASTE,
			MachineBlueprints.REDSTONE_CONTROL,
			EnergyBlueprints.WIND_UPKEEP,
			LogisticsBlueprints.PIPE_GRADES,
			LogisticsBlueprints.CHEST_TIERS,
			MachineBlueprints.RADIATION,
			EnergyBlueprints.REPELLER_TIERS,
			MachineBlueprints.GARDEN,
			MachineBlueprints.MUTATION,
			LogisticsBlueprints.TOOL_KIT,
			GearBlueprints.POWER_TOOLS,
			GearBlueprints.LIGHTS,
			GearBlueprints.KOK_SAGYZ_FARM,
			ReactorBlueprints.REACTOR_ROOM,
			ReactorBlueprints.STEAM_EXHAUST,
			StructureBlueprints.TELEPORTER_LINK,
			StructureBlueprints.MOB_WHEEL,
			StructureBlueprints.OIL_REFINING,
			StructureBlueprints.CRYSTAL_FARM,
			StructureBlueprints.MIRROR_CONCENTRATOR,
			WorkshopBlueprints.MONITORING,
			WorkshopBlueprints.UPGRADE_TABLE,
			WorkshopBlueprints.WORKSTATION,
			WorkshopBlueprints.INDUSTRIAL_WORKBENCH,
			WorkshopBlueprints.REACTOR_CORE,
			UpgradeBlueprints.MOD_ORES,
			UpgradeBlueprints.BUILDING_BLOCKS,
			UpgradeBlueprints.ROTORS_AND_WHEELS,
			UpgradeBlueprints.LIGHTNING_TIPS,
			UpgradeBlueprints.MACHINE_CHIPS,
			PowerGearBlueprints.ENERGY_CRYSTALS,
			PowerGearBlueprints.BATTERIES,
			PowerGearBlueprints.JETPACK_FLIGHT,
			PowerGearBlueprints.ELECTRIC_WEAPONS,
			PowerGearBlueprints.DRILL_TIPS,
			ToolGearBlueprints.SCYTHES,
			ToolGearBlueprints.TEMPERED_IRON_GEAR,
			ToolGearBlueprints.PROTECTIVE_SUITS,
			ToolGearBlueprints.ELECTROMAGNETS,
			ToolGearBlueprints.FORGE_HAMMER_PLATES,
			MaterialBlueprints.ALLOYS,
			MaterialBlueprints.METAL_DECOR,
			MaterialBlueprints.DUSTS,
			MaterialBlueprints.REINFORCED_PLATES,
			MaterialBlueprints.GEARS_AND_BEARINGS,
			ComponentBlueprints.CIRCUITS,
			ComponentBlueprints.COILS_AND_CHOKES,
			ComponentBlueprints.FLUXWEAVE_LINE,
			ComponentBlueprints.CARBON_LINE,
			ComponentBlueprints.BYPRODUCTS,
			NuclearBioBlueprints.URANIUM_FUEL,
			NuclearBioBlueprints.RADIATION_PRODUCTS,
			NuclearBioBlueprints.MUTATION_CHIPS,
			NuclearBioBlueprints.CANNED_GOODS,
			NuclearBioBlueprints.BIO_FLUIDS,
			MiscBlueprints.LIQUID_FUELS,
			MiscBlueprints.EVOLUTION_CHIPS,
			MiscBlueprints.ENGRAVED_PLATES,
			MiscBlueprints.RANDOM_JUMP,
			MiscBlueprints.TEST_SOURCE,
			StorageBlueprints.CHEST_BOATS,
			StorageBlueprints.STORAGE_BLOCKS,
			StorageBlueprints.DECOR_FORMS,
			StorageBlueprints.PLATE_CLADDING,
			OreLineBlueprints.TIN_LINE,
			OreLineBlueprints.NOBLE_METALS,
			OreLineBlueprints.DEEPSLATE_ORES,
			OreLineBlueprints.WELDED_PLATES,
			OreLineBlueprints.GEM_DUSTS,
			ComponentLineBlueprints.RUBBER_MATERIALS,
			ComponentLineBlueprints.MONITOR_PARTS,
			ComponentLineBlueprints.ENERGY_CLOTS,
			AutomationBlueprints.ASSEMBLY_SHEETS,
			AutomationBlueprints.STATS_CHIP,
			AutomationBlueprints.COLUMN_DRILL,
			ReactorBuildBlueprints.REACTOR_SHELL,
			ReactorBuildBlueprints.DOOR_DRIVE,
			FieldToolsBlueprints.WIND_GAUGE,
			FieldToolsBlueprints.GARDEN_DRONE,
			FieldToolsBlueprints.SOUL_VESSEL);

	private BlueprintCatalog() {
	}

	/** The blueprint a tooltip or guide page of {@code itemId} leads to, if any. */
	public static Optional<BlueprintDef> forSubject(String itemId) {
		for (BlueprintDef def : ALL) {
			if (def.subjects().contains(itemId)) {
				return Optional.of(def);
			}
		}
		return Optional.empty();
	}

	/** The blueprint with this id, if any. */
	public static Optional<BlueprintDef> byId(String id) {
		return ALL.stream().filter(def -> def.id().equals(id)).findFirst();
	}
}
