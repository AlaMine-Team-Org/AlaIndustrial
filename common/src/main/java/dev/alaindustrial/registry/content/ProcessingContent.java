package dev.alaindustrial.registry.content;

import static dev.alaindustrial.registry.content.ContentDeclarations.beginBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.block;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockEntity;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockItem;
import static dev.alaindustrial.registry.content.ContentDeclarations.endBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.hiddenFromPlayers;
import static dev.alaindustrial.registry.content.ContentDeclarations.machine;
import static dev.alaindustrial.registry.content.ContentDeclarations.menu;
import static dev.alaindustrial.registry.content.TabEntries.show;

import dev.alaindustrial.block.AlloySmelterBlock;
import dev.alaindustrial.block.AssemblerBlock;
import dev.alaindustrial.block.CanningMachineBlock;
import dev.alaindustrial.block.ComponentRepairBenchBlock;
import dev.alaindustrial.block.CompressorBlock;
import dev.alaindustrial.block.ElectricFurnaceBlock;
import dev.alaindustrial.block.ElectricHeaterBlock;
import dev.alaindustrial.block.ExtractorBlock;
import dev.alaindustrial.block.GalvanicBathBlock;
import dev.alaindustrial.block.IndustrialWorkbenchBlock;
import dev.alaindustrial.block.IronFurnaceBlock;
import dev.alaindustrial.block.MaceratorBlock;
import dev.alaindustrial.block.PolymerizerBlock;
import dev.alaindustrial.block.RecyclerBlock;
import dev.alaindustrial.block.SawmillBlock;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.ThermalCentrifugeBlock;
import dev.alaindustrial.block.UpgradeTableBlock;
import dev.alaindustrial.block.VulcanizerBlock;
import dev.alaindustrial.block.WorkstationBlock;
import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.CanningMachineBlockEntity;
import dev.alaindustrial.block.entity.ComponentRepairBenchBlockEntity;
import dev.alaindustrial.block.entity.CompressorBlockEntity;
import dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.block.entity.ExtractorBlockEntity;
import dev.alaindustrial.block.entity.GalvanicBathBlockEntity;
import dev.alaindustrial.block.entity.IndustrialWorkbenchBlockEntity;
import dev.alaindustrial.block.entity.IronFurnaceBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import dev.alaindustrial.block.entity.RecyclerBlockEntity;
import dev.alaindustrial.block.entity.SawmillBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import dev.alaindustrial.block.entity.UpgradeTableBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.block.entity.WorkstationBlockEntity;
import dev.alaindustrial.compat.LineBlockProps;
import dev.alaindustrial.menu.AlloySmelterMenu;
import dev.alaindustrial.menu.AssemblerMenu;
import dev.alaindustrial.menu.CanningMachineMenu;
import dev.alaindustrial.menu.ComponentRepairBenchMenu;
import dev.alaindustrial.menu.CompressorMenu;
import dev.alaindustrial.menu.ElectricFurnaceMenu;
import dev.alaindustrial.menu.ElectricHeaterMenu;
import dev.alaindustrial.menu.ExtractorMenu;
import dev.alaindustrial.menu.GalvanicBathMenu;
import dev.alaindustrial.menu.MaceratorMenu;
import dev.alaindustrial.menu.PolymerizerMenu;
import dev.alaindustrial.menu.RecyclerMenu;
import dev.alaindustrial.menu.SawmillMenu;
import dev.alaindustrial.menu.TeleporterStationMenu;
import dev.alaindustrial.menu.ThermalCentrifugeMenu;
import dev.alaindustrial.menu.UpgradeTableMenu;
import dev.alaindustrial.menu.VulcanizerMenu;
import dev.alaindustrial.registry.ContentManifest.BlockDef;
import dev.alaindustrial.registry.ContentManifest.BlockEntityDef;
import dev.alaindustrial.registry.ContentManifest.Domain;
import dev.alaindustrial.registry.ContentManifest.ItemDef;
import dev.alaindustrial.registry.ContentManifest.MenuDef;
import dev.alaindustrial.registry.CreativeTabContent.Sink;
import dev.alaindustrial.registry.ModBlockProperties;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.world.level.block.SoundType;

/**
 * The Processing domain of the content manifest (MOD-711; coding.md §1, owner decision D5). Machines that spend
 * EU to turn one item into another, and their workbenches: the furnaces, the macerator up to the assembler, the
 * teleporter station, the workstation, the upgrade table and the industrial workbench.
 *
 * <p>Declared here and registered through {@link dev.alaindustrial.registry.ContentManifest}, which joins the
 * ten domains in the fixed order of {@code ContentManifest.DOMAINS}; both loaders replay the joined lists. A new
 * entry of this domain is written here and nowhere else: a block between {@code beginBlocks()} and
 * {@link #BLOCKS}, an item, block entity or menu at the end of its list. This file declares no other domain's
 * entries and reads nothing of {@code ContentManifest} but its records
 * ({@code ArchitectureRules.contentDomainsInitialiseOnTheirOwn}): its static initialiser runs on its own.
 */
public final class ProcessingContent {
	private ProcessingContent() {
	}

	static {
		beginBlocks();
	}

	public static final BlockDef<MaceratorBlock> MACERATOR = block("macerator", MaceratorBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.MACERATOR = s);
	// Teleporter station (MOD-091); visible since MOD-093 completed the feature.
	public static final BlockDef<TeleporterBlock> TELEPORTER = block("teleporter", TeleporterBlock::new,
			machine(p -> p.strength(5.0f, 12.0f).sound(SoundType.METAL)), s -> ModContent.TELEPORTER = s);
	// MOD-112 — the two glass cells of an assembled teleporter capsule; placed by the station, never held.
	public static final BlockDef<dev.alaindustrial.block.TeleporterCapsuleBlock> TELEPORTER_CAPSULE =
			block("teleporter_capsule", dev.alaindustrial.block.TeleporterCapsuleBlock::new,
					// MOD-112 — the capsule's glass cells, on the dome's terms: see-through, and not for a piston,
					// which would carry a cell away from the station it belongs to.
					machine(p -> LineBlockProps.pinnedAgainstPistons(p).strength(1.0f, 2.0f)
							.sound(SoundType.GLASS).noOcclusion()), s -> ModContent.TELEPORTER_CAPSULE = s,
					hiddenFromPlayers("a cell of an assembled capsule: the station places it, nobody holds one"));
	public static final BlockDef<ElectricFurnaceBlock> ELECTRIC_FURNACE =
			block("electric_furnace", ElectricFurnaceBlock::new,
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.ELECTRIC_FURNACE = s);
	// Iron Furnace (MOD-115) — fuel-burning smelter between the stone and electric furnaces.
	public static final BlockDef<IronFurnaceBlock> IRON_FURNACE = block("iron_furnace", IronFurnaceBlock::new,
			machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight)), s -> ModContent.IRON_FURNACE = s);
	public static final BlockDef<ExtractorBlock> EXTRACTOR = block("extractor", ExtractorBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.EXTRACTOR = s);
	public static final BlockDef<CompressorBlock> COMPRESSOR = block("compressor", CompressorBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.COMPRESSOR = s);
	// Component Repair Bench (MOD-384) — restores worn rotors/wheels instead of recrafting them.
	public static final BlockDef<ComponentRepairBenchBlock> COMPONENT_REPAIR_BENCH =
			block("component_repair_bench", ComponentRepairBenchBlock::new,
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)),
					s -> ModContent.COMPONENT_REPAIR_BENCH = s);
	public static final BlockDef<CanningMachineBlock> CANNING_MACHINE =
			block("canning_machine", CanningMachineBlock::new,
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.CANNING_MACHINE = s);
	public static final BlockDef<SawmillBlock> SAWMILL = block("sawmill", SawmillBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.SAWMILL = s);
	// MOD-275 — the first MV machine; a blueprint-driven auto-crafter.
	public static final BlockDef<AssemblerBlock> ASSEMBLER = block("assembler", AssemblerBlock::new,
			// MOD-275: the assembler has no lit model, so no lightLevel — a plain metal machine cube.
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.ASSEMBLER = s);
	public static final BlockDef<PolymerizerBlock> POLYMERIZER = block("polymerizer", PolymerizerBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.POLYMERIZER = s);
	public static final BlockDef<AlloySmelterBlock> ALLOY_SMELTER = block("alloy_smelter", AlloySmelterBlock::new,
			// MOD-064: the smelter glows through its crucible windows while melting, so it lights like
			// the other machines with a lit front texture.
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight)), s -> ModContent.ALLOY_SMELTER = s);
	public static final BlockDef<VulcanizerBlock> VULCANIZER = block("vulcanizer", VulcanizerBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight)), s -> ModContent.VULCANIZER = s);
	public static final BlockDef<GalvanicBathBlock> GALVANIC_BATH = block("galvanic_bath", GalvanicBathBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.GALVANIC_BATH = s);
	public static final BlockDef<ElectricHeaterBlock> ELECTRIC_HEATER =
			block("electric_heater", ElectricHeaterBlock::new,
					// MOD-418: the heater's glow is a four-rung thermometer, not the boolean litLight the rest of
					// the machine family uses — see HeaterGlow.
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
							.lightLevel(ModBlockProperties::heaterLight)), s -> ModContent.ELECTRIC_HEATER = s);
	// Industrial Workbench (MOD-062) — the Industrialist villager's job-site block.
	public static final BlockDef<IndustrialWorkbenchBlock> INDUSTRIAL_WORKBENCH =
			block("industrial_workbench", IndustrialWorkbenchBlock::new,
					machine(p -> p.strength(2.5f, 6.0f).sound(SoundType.METAL)),
					s -> ModContent.INDUSTRIAL_WORKBENCH = s);
	// Thermal Centrifuge (MOD-424): redstone-started, heated from below; doubles a dust a second time.
	public static final BlockDef<ThermalCentrifugeBlock> THERMAL_CENTRIFUGE =
			block("thermal_centrifuge", ThermalCentrifugeBlock::new,
					// MOD-424 — the centrifuge housing is an open frame around the rotor, so its getShape is inset
					// (1..15) and noOcclusion is mandatory: a non-full shape must not cull its neighbours (R-PHY-05).
					// It glows through those openings while the rotor is doing work, hence litLight.
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
							.noOcclusion().lightLevel(ModBlockProperties::litLight)),
					s -> ModContent.THERMAL_CENTRIFUGE = s);
	// MOD-145 — the Recycler and the building block its poor slag compacts into.
	public static final BlockDef<RecyclerBlock> RECYCLER = block("recycler", RecyclerBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.RECYCLER = s);
	/** Two of these stacked become the player's workstation; alone it is just a casing (MOD-483). */
	public static final BlockDef<WorkstationBlock> WORKSTATION = block("workstation", WorkstationBlock::new,
			// MOD-483. No noOcclusion: the default state is a loose casing, a full cube, and R-PHY-05
			// reads exactly that state. Pinned against pistons because one that shoved one half clear
			// would leave the other standing as a casing — legal, but not what the player asked for.
			machine(p -> LineBlockProps.pinnedAgainstPistons(p).strength(3.0f, 6.0f)
					.sound(SoundType.METAL)), s -> ModContent.WORKSTATION = s);

	/** Upgrade Table (MOD-482) — fits permanent upgrades onto powered tools. Two casings stacked. */
	public static final BlockDef<UpgradeTableBlock> UPGRADE_TABLE = block("upgrade_table", UpgradeTableBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.UPGRADE_TABLE = s);

	/** This domain's blocks, in declaration order — collected since {@code beginBlocks()} above. */
	private static final List<BlockDef<?>> BLOCKS = endBlocks();

	private static final List<ItemDef> ITEMS = List.of(
			blockItem("macerator", s -> ModContent.MACERATOR_ITEM = s),
			blockItem("teleporter", s -> ModContent.TELEPORTER_ITEM = s),
			blockItem("electric_furnace", s -> ModContent.ELECTRIC_FURNACE_ITEM = s),
			blockItem("extractor", s -> ModContent.EXTRACTOR_ITEM = s),
			blockItem("compressor", s -> ModContent.COMPRESSOR_ITEM = s),
			blockItem("component_repair_bench", s -> ModContent.COMPONENT_REPAIR_BENCH_ITEM = s),
			blockItem("canning_machine", s -> ModContent.CANNING_MACHINE_ITEM = s),
			blockItem("sawmill", s -> ModContent.SAWMILL_ITEM = s),
			blockItem("assembler", s -> ModContent.ASSEMBLER_ITEM = s),
			blockItem("polymerizer", s -> ModContent.POLYMERIZER_ITEM = s),
			blockItem("vulcanizer", s -> ModContent.VULCANIZER_ITEM = s),
			blockItem("alloy_smelter", s -> ModContent.ALLOY_SMELTER_ITEM = s),
			blockItem("galvanic_bath", s -> ModContent.GALVANIC_BATH_ITEM = s),
			blockItem("thermal_centrifuge", s -> ModContent.THERMAL_CENTRIFUGE_ITEM = s),
			blockItem("electric_heater", s -> ModContent.ELECTRIC_HEATER_ITEM = s),
			blockItem("iron_furnace", s -> ModContent.IRON_FURNACE_ITEM = s),
			blockItem("industrial_workbench", s -> ModContent.INDUSTRIAL_WORKBENCH_ITEM = s),
			// MOD-483 — appended at the very tail; the order of this list is the registration order.
			blockItem("workstation", s -> ModContent.WORKSTATION_ITEM = s),
			// MOD-145 — the Recycler, its three grades of slag, its ash and the three blade grades.
			blockItem("recycler", s -> ModContent.RECYCLER_ITEM = s),
			// MOD-482 — appended at the very tail; the order of this list is the registration order.
			blockItem("upgrade_table", s -> ModContent.UPGRADE_TABLE_ITEM = s));

	private static final List<BlockEntityDef<?>> BLOCK_ENTITIES = List.of(
			blockEntity("macerator", MaceratorBlockEntity.class, MaceratorBlockEntity::new,
					s -> ModContent.MACERATOR_BE = s, MACERATOR),
			blockEntity("component_repair_bench", ComponentRepairBenchBlockEntity.class,
					ComponentRepairBenchBlockEntity::new, s -> ModContent.COMPONENT_REPAIR_BENCH_BE = s,
					COMPONENT_REPAIR_BENCH),
			blockEntity("upgrade_table", UpgradeTableBlockEntity.class, UpgradeTableBlockEntity::new,
					s -> ModContent.UPGRADE_TABLE_BE = s, UPGRADE_TABLE),
			blockEntity("teleporter", TeleporterBlockEntity.class, TeleporterBlockEntity::new,
					s -> ModContent.TELEPORTER_BE = s, TELEPORTER),
			blockEntity("electric_furnace", ElectricFurnaceBlockEntity.class, ElectricFurnaceBlockEntity::new,
					s -> ModContent.ELECTRIC_FURNACE_BE = s, ELECTRIC_FURNACE),
			blockEntity("iron_furnace", IronFurnaceBlockEntity.class, IronFurnaceBlockEntity::new,
					s -> ModContent.IRON_FURNACE_BE = s, IRON_FURNACE),
			blockEntity("extractor", ExtractorBlockEntity.class, ExtractorBlockEntity::new,
					s -> ModContent.EXTRACTOR_BE = s, EXTRACTOR),
			blockEntity("compressor", CompressorBlockEntity.class, CompressorBlockEntity::new,
					s -> ModContent.COMPRESSOR_BE = s, COMPRESSOR),
			blockEntity("recycler", RecyclerBlockEntity.class, RecyclerBlockEntity::new,
					s -> ModContent.RECYCLER_BE = s, RECYCLER),
			blockEntity("canning_machine", CanningMachineBlockEntity.class, CanningMachineBlockEntity::new,
					s -> ModContent.CANNING_MACHINE_BE = s, CANNING_MACHINE),
			blockEntity("sawmill", SawmillBlockEntity.class, SawmillBlockEntity::new, s -> ModContent.SAWMILL_BE = s,
					SAWMILL),
			blockEntity("assembler", AssemblerBlockEntity.class, AssemblerBlockEntity::new,
					s -> ModContent.ASSEMBLER_BE = s, ASSEMBLER),
			blockEntity("polymerizer", PolymerizerBlockEntity.class, PolymerizerBlockEntity::new,
					s -> ModContent.POLYMERIZER_BE = s, POLYMERIZER),
			blockEntity("vulcanizer", VulcanizerBlockEntity.class, VulcanizerBlockEntity::new,
					s -> ModContent.VULCANIZER_BE = s, VULCANIZER),
			blockEntity("alloy_smelter", AlloySmelterBlockEntity.class, AlloySmelterBlockEntity::new,
					s -> ModContent.ALLOY_SMELTER_BE = s, ALLOY_SMELTER),
			blockEntity("galvanic_bath", GalvanicBathBlockEntity.class, GalvanicBathBlockEntity::new,
					s -> ModContent.GALVANIC_BATH_BE = s, GALVANIC_BATH),
			blockEntity("electric_heater", ElectricHeaterBlockEntity.class, ElectricHeaterBlockEntity::new,
					s -> ModContent.ELECTRIC_HEATER_BE = s, ELECTRIC_HEATER),
			blockEntity("thermal_centrifuge", ThermalCentrifugeBlockEntity.class,
					ThermalCentrifugeBlockEntity::new, s -> ModContent.THERMAL_CENTRIFUGE_BE = s,
					THERMAL_CENTRIFUGE),
			// MOD-483: one type for all three parts — the casing and the upper half carry an inert one,
			// because a state whose block is an EntityBlock has to produce a block entity.
			blockEntity("workstation", WorkstationBlockEntity.class, WorkstationBlockEntity::new,
					s -> ModContent.WORKSTATION_BE = s, WORKSTATION),
			// MOD-656: the workbench's memory of the last recipe crafted on it.
			blockEntity("industrial_workbench", IndustrialWorkbenchBlockEntity.class,
					IndustrialWorkbenchBlockEntity::new,
					s -> ModContent.INDUSTRIAL_WORKBENCH_BE = s, INDUSTRIAL_WORKBENCH));

	private static final List<MenuDef<?>> MENUS = List.of(
			menu("macerator", MaceratorMenu::new, s -> ModContent.MACERATOR_MENU = s),
			menu("electric_furnace", ElectricFurnaceMenu::new, s -> ModContent.ELECTRIC_FURNACE_MENU = s),
			menu("extractor", ExtractorMenu::new, s -> ModContent.EXTRACTOR_MENU = s),
			menu("compressor", CompressorMenu::new, s -> ModContent.COMPRESSOR_MENU = s),
			menu("recycler", RecyclerMenu::new, s -> ModContent.RECYCLER_MENU = s),
			menu("component_repair_bench", ComponentRepairBenchMenu::new,
					s -> ModContent.COMPONENT_REPAIR_BENCH_MENU = s),
			menu("upgrade_table", UpgradeTableMenu::new, s -> ModContent.UPGRADE_TABLE_MENU = s),
			menu("canning_machine", CanningMachineMenu::new, s -> ModContent.CANNING_MACHINE_MENU = s),
			menu("sawmill", SawmillMenu::new, s -> ModContent.SAWMILL_MENU = s),
			// MOD-275 — the assembler: blueprint queue, ghost pattern grid, six-slot output.
			menu("assembler", AssemblerMenu::new, s -> ModContent.ASSEMBLER_MENU = s),
			menu("polymerizer", PolymerizerMenu::new, s -> ModContent.POLYMERIZER_MENU = s),
			menu("vulcanizer", VulcanizerMenu::new, s -> ModContent.VULCANIZER_MENU = s),
			// MOD-418 — the heater under it: a slotless readout for the warm-up ramp it now has.
			menu("electric_heater", ElectricHeaterMenu::new, s -> ModContent.ELECTRIC_HEATER_MENU = s),
			// MOD-064 — the alloy smelter: three interchangeable component slots, one result slot.
			menu("alloy_smelter", AlloySmelterMenu::new, s -> ModContent.ALLOY_SMELTER_MENU = s),
			menu("galvanic_bath", GalvanicBathMenu::new, s -> ModContent.GALVANIC_BATH_MENU = s),
			menu("teleporter_station", TeleporterStationMenu::new, s -> ModContent.TELEPORTER_STATION_MENU = s),
			// MOD-424 — the thermal centrifuge: rotor gauge + status line over the usual two slots.
			menu("thermal_centrifuge", ThermalCentrifugeMenu::new, s -> ModContent.THERMAL_CENTRIFUGE_MENU = s));

	/** Everything this domain declares; {@link dev.alaindustrial.registry.ContentManifest#DOMAINS} joins it in. */
	public static final Domain DOMAIN = new Domain("Processing", BLOCKS, ITEMS, BLOCK_ENTITIES, MENUS);

	// ---- Creative tab sections (MOD-711, batch 4; ADR-041). CreativeTabContent calls them in the tab's
	// reading order. A section holds the run of the tab that coding.md §1 files under this domain, so it may
	// show a neighbour's handle where the tab has always grouped it here.

	/**
	 * 4 - the processing line: what a machine turns one item into another. Fluid machinery lives in
	 * {@code FluidContent.fluids} instead, next to the pump and the tank it cannot work without (MOD-407).
	 */
	public static void machines(Sink out) {
		// The smelting line before the crusher: the Iron Furnace is the first machine a player builds
		// (no power needed), the Electric Furnace is its powered successor, and only then the Macerator.
		show(out, ModContent.IRON_FURNACE_ITEM);
		show(out, ModContent.ELECTRIC_FURNACE_ITEM);
		show(out, ModContent.MACERATOR_ITEM);
		show(out, ModContent.EXTRACTOR_ITEM);
		show(out, ModContent.COMPRESSOR_ITEM);
		// MOD-145 - the Recycler next to the crusher family: same idea, opposite end of the value chain.
		show(out, ModContent.RECYCLER_ITEM);
		show(out, ModContent.COMPONENT_REPAIR_BENCH_ITEM);
		show(out, ModContent.SAWMILL_ITEM);
		show(out, ModContent.ALLOY_SMELTER_ITEM);
		show(out, ModContent.CANNING_MACHINE_ITEM);
		show(out, ModContent.ELECTRIC_HEATER_ITEM);
		// MOD-275 - the first MV machine, last of the cubes because it sits a tier above the rest.
		show(out, ModContent.ASSEMBLER_ITEM);
		// --- shaped models close the group (MOD-574); the family itself stays here, one row down.
		// MOD-424 - the centrifuge stands ON the heater above and does nothing without one.
		show(out, ModContent.THERMAL_CENTRIFUGE_ITEM);
	}

	/**
	 * The two stacked work stations. MOD-483 — the workstation's casing: one item for the whole machine, two
	 * of them stacked assemble it, so there is nothing else to list. MOD-482 — the upgrade table's casing,
	 * beside the workstation it is built like. Shown in the mod's own tab and in vanilla Functional Blocks.
	 */
	public static void stations(Sink out) {
		show(out, ModContent.WORKSTATION_ITEM);
		show(out, ModContent.UPGRADE_TABLE_ITEM);
	}
}
