package dev.alaindustrial.registry.content;

import static dev.alaindustrial.registry.content.ContentDeclarations.CREATIVE_ONLY_ITEM;
import static dev.alaindustrial.registry.content.ContentDeclarations.beginBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.block;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockEntity;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockItem;
import static dev.alaindustrial.registry.content.ContentDeclarations.endBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.hiddenFromPlayers;
import static dev.alaindustrial.registry.content.ContentDeclarations.machine;
import static dev.alaindustrial.registry.content.ContentDeclarations.menu;
import static dev.alaindustrial.registry.content.TabEntries.show;

import dev.alaindustrial.block.ConcentratorSectionBlock;
import dev.alaindustrial.block.CreativeEnergySourceBlock;
import dev.alaindustrial.block.DaylightSolarPanelBlock;
import dev.alaindustrial.block.GeneratorBlock;
import dev.alaindustrial.block.GeothermalGeneratorBlock;
import dev.alaindustrial.block.HighAltitudeWindMillBlock;
import dev.alaindustrial.block.LightningRodGeneratorBlock;
import dev.alaindustrial.block.MobWheelCellBlock;
import dev.alaindustrial.block.MobWheelControllerBlock;
import dev.alaindustrial.block.MobWheelFrameBlock;
import dev.alaindustrial.block.MobWheelGateBlock;
import dev.alaindustrial.block.MobWheelRotorBlock;
import dev.alaindustrial.block.MoonlitSolarPanelBlock;
import dev.alaindustrial.block.RadiantSolarPanelBlock;
import dev.alaindustrial.block.SolarPanelBlock;
import dev.alaindustrial.block.StormWindMillBlock;
import dev.alaindustrial.block.WaterMillBlock;
import dev.alaindustrial.block.WindMillBlock;
import dev.alaindustrial.block.entity.CreativeEnergySourceBlockEntity;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity;
import dev.alaindustrial.block.entity.LightningRodGeneratorBlockEntity;
import dev.alaindustrial.block.entity.MobWheelBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.RadiantSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.compat.LineBlockProps;
import dev.alaindustrial.menu.CreativeEnergySourceMenu;
import dev.alaindustrial.menu.DaylightSolarPanelMenu;
import dev.alaindustrial.menu.GeneratorMenu;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.menu.HighAltitudeWindMillMenu;
import dev.alaindustrial.menu.LightningRodGeneratorMenu;
import dev.alaindustrial.menu.MobWheelMenu;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import dev.alaindustrial.menu.RadiantSolarPanelMenu;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.menu.StormWindMillMenu;
import dev.alaindustrial.menu.WaterMillMenu;
import dev.alaindustrial.menu.WindMillMenu;
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
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The EnergyGeneration domain of the content manifest (MOD-711; coding.md §1, owner decision D5). EU generators
 * of every kind — fuel, sun, wind, water, geothermal heat, lightning — and the creative source, with their block
 * entities, menus and block items.
 *
 * <p>Declared here and registered through {@link dev.alaindustrial.registry.ContentManifest}, which joins the
 * ten domains in the fixed order of {@code ContentManifest.DOMAINS}; both loaders replay the joined lists. A new
 * entry of this domain is written here and nowhere else: a block between {@code beginBlocks()} and
 * {@link #BLOCKS}, an item, block entity or menu at the end of its list. This file declares no other domain's
 * entries and reads nothing of {@code ContentManifest} but its records
 * ({@code ArchitectureRules.contentDomainsInitialiseOnTheirOwn}): its static initialiser runs on its own.
 */
public final class EnergyGenerationContent {
	private EnergyGenerationContent() {
	}

	static {
		beginBlocks();
	}

	public static final BlockDef<GeneratorBlock> GENERATOR = block("generator", GeneratorBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight)), s -> ModContent.GENERATOR = s);
	public static final BlockDef<SolarPanelBlock> SOLAR_PANEL = block("solar_panel", SolarPanelBlock::new,
			machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion()), s -> ModContent.SOLAR_PANEL = s);
	public static final BlockDef<MoonlitSolarPanelBlock> MOONLIT_SOLAR_PANEL =
			block("moonlit_solar_panel", MoonlitSolarPanelBlock::new,
					machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion()),
					s -> ModContent.MOONLIT_SOLAR_PANEL = s);
	public static final BlockDef<DaylightSolarPanelBlock> DAYLIGHT_SOLAR_PANEL =
			block("daylight_solar_panel", DaylightSolarPanelBlock::new,
					machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion()),
					s -> ModContent.DAYLIGHT_SOLAR_PANEL = s);
	/** MOD-602 — the day branch's third rung, grown from the daylight panel. */
	public static final BlockDef<RadiantSolarPanelBlock> RADIANT_SOLAR_PANEL =
			block("radiant_solar_panel", RadiantSolarPanelBlock::new,
					// MOD-602 — a raised collector with folding wings, nowhere near a full cube, so noOcclusion is
					// mandatory (R-PHY-05). Metal rather than glass: most of what you touch is the chassis.
					machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).noOcclusion()),
					s -> ModContent.RADIANT_SOLAR_PANEL = s);

	/** Filler cell of the assembled Mirror Concentrator (MOD-603) — inert on its own. */
	public static final BlockDef<ConcentratorSectionBlock> CONCENTRATOR_SECTION =
			block("concentrator_section", ConcentratorSectionBlock::new,
					// Seven of its eight states are thin slices of the structure, so it must not occlude; the
					// loose state is inset by a pixel for the same reason (R-PHY-05 wants the two to agree).
					machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).noOcclusion()),
					s -> ModContent.CONCENTRATOR_SECTION = s);
	public static final BlockDef<GeothermalGeneratorBlock> GEOTHERMAL_GENERATOR =
			block("geothermal_generator", GeothermalGeneratorBlock::new,
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
							.lightLevel(ModBlockProperties::litLight)), s -> ModContent.GEOTHERMAL_GENERATOR = s);
	public static final BlockDef<WaterMillBlock> WATER_MILL = block("water_mill", WaterMillBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.WATER_MILL = s);
	public static final BlockDef<WindMillBlock> WIND_MILL = block("wind_mill", WindMillBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.WIND_MILL = s);
	public static final BlockDef<HighAltitudeWindMillBlock> HIGH_ALTITUDE_WIND_MILL =
			block("high_altitude_wind_mill", HighAltitudeWindMillBlock::new,
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)),
					s -> ModContent.HIGH_ALTITUDE_WIND_MILL = s);
	public static final BlockDef<StormWindMillBlock> STORM_WIND_MILL = block("storm_wind_mill", StormWindMillBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.STORM_WIND_MILL = s);
	public static final BlockDef<LightningRodGeneratorBlock> LIGHTNING_ROD_GENERATOR =
			block("lightning_rod_generator", LightningRodGeneratorBlock::new,
					// MOD-386: not a full cube (casing plate + mast), hence noOcclusion — R-PHY-05.
					machine(p -> p.strength(3.0f, 6.0f)
							.sound(SoundType.METAL).noOcclusion()), s -> ModContent.LIGHTNING_ROD_GENERATOR = s);
	/**
	 * MOD-479 — a technical block, not survival content: an inexhaustible EU source for test
	 * stands. Filed with the generators because that is what it is to the energy network.
	 */
	public static final BlockDef<CreativeEnergySourceBlock> CREATIVE_ENERGY_SOURCE =
			block("creative_energy_source", CreativeEnergySourceBlock::new,
					// MOD-479: an ordinary machine block — breakable, explodable, drops itself.
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)),
					s -> ModContent.CREATIVE_ENERGY_SOURCE = s);

	/** MOD-763 — the mob wheel's drive: block entity, feeder slot and LV port of the 3×3×3 structure. */
	public static final BlockDef<MobWheelControllerBlock> MOB_WHEEL_CONTROLLER =
			block("mob_wheel_controller", MobWheelControllerBlock::new, machine(EnergyGenerationContent::mobWheelPart),
					s -> ModContent.MOB_WHEEL_CONTROLLER = s);
	public static final BlockDef<MobWheelFrameBlock> MOB_WHEEL_FRAME = block("mob_wheel_frame",
			MobWheelFrameBlock::new, machine(EnergyGenerationContent::mobWheelPart),
			s -> ModContent.MOB_WHEEL_FRAME = s);
	public static final BlockDef<MobWheelRotorBlock> MOB_WHEEL_ROTOR = block("mob_wheel_rotor",
			MobWheelRotorBlock::new, machine(EnergyGenerationContent::mobWheelPart),
			s -> ModContent.MOB_WHEEL_ROTOR = s);
	public static final BlockDef<MobWheelGateBlock> MOB_WHEEL_GATE = block("mob_wheel_gate",
			MobWheelGateBlock::new, machine(EnergyGenerationContent::mobWheelPart), s -> ModContent.MOB_WHEEL_GATE = s);
	public static final BlockDef<MobWheelCellBlock> MOB_WHEEL_CELL = block("mob_wheel_cell",
			MobWheelCellBlock::new, machine(EnergyGenerationContent::mobWheelPart), s -> ModContent.MOB_WHEEL_CELL = s,
			hiddenFromPlayers("cell of an assembled mob wheel: the structure places it"));

	/** This domain's blocks, in declaration order — collected since {@code beginBlocks()} above. */
	private static final List<BlockDef<?>> BLOCKS = endBlocks();

	private static final List<ItemDef> ITEMS = List.of(
			// Block items.
			blockItem("generator", s -> ModContent.GENERATOR_ITEM = s),
			blockItem("geothermal_generator", s -> ModContent.GEOTHERMAL_GENERATOR_ITEM = s),
			blockItem("solar_panel", s -> ModContent.SOLAR_PANEL_ITEM = s),
			blockItem("moonlit_solar_panel", s -> ModContent.MOONLIT_SOLAR_PANEL_ITEM = s),
			blockItem("daylight_solar_panel", s -> ModContent.DAYLIGHT_SOLAR_PANEL_ITEM = s),
			blockItem("radiant_solar_panel", s -> ModContent.RADIANT_SOLAR_PANEL_ITEM = s),
			blockItem("concentrator_section", s -> ModContent.CONCENTRATOR_SECTION_ITEM = s),
			blockItem("water_mill", s -> ModContent.WATER_MILL_ITEM = s),
			blockItem("wind_mill", s -> ModContent.WIND_MILL_ITEM = s),
			blockItem("high_altitude_wind_mill", s -> ModContent.HIGH_ALTITUDE_WIND_MILL_ITEM = s),
			blockItem("storm_wind_mill", s -> ModContent.STORM_WIND_MILL_ITEM = s),
			blockItem("lightning_rod_generator", s -> ModContent.LIGHTNING_ROD_GENERATOR_ITEM = s),
			blockItem("creative_energy_source", "creative_energy_source", CREATIVE_ONLY_ITEM,
					s -> ModContent.CREATIVE_ENERGY_SOURCE_ITEM = s),
			blockItem("mob_wheel_controller", s -> ModContent.MOB_WHEEL_CONTROLLER_ITEM = s),
			blockItem("mob_wheel_frame", s -> ModContent.MOB_WHEEL_FRAME_ITEM = s),
			blockItem("mob_wheel_rotor", s -> ModContent.MOB_WHEEL_ROTOR_ITEM = s),
			blockItem("mob_wheel_gate", s -> ModContent.MOB_WHEEL_GATE_ITEM = s));

	private static final List<BlockEntityDef<?>> BLOCK_ENTITIES = List.of(
			blockEntity("generator", GeneratorBlockEntity.class, GeneratorBlockEntity::new,
					s -> ModContent.GENERATOR_BE = s, GENERATOR),
			blockEntity("geothermal_generator", GeothermalGeneratorBlockEntity.class,
					GeothermalGeneratorBlockEntity::new, s -> ModContent.GEOTHERMAL_GENERATOR_BE = s,
					GEOTHERMAL_GENERATOR),
			blockEntity("solar_panel", SolarPanelBlockEntity.class, SolarPanelBlockEntity::new,
					s -> ModContent.SOLAR_PANEL_BE = s, SOLAR_PANEL),
			blockEntity("moonlit_solar_panel", MoonlitSolarPanelBlockEntity.class, MoonlitSolarPanelBlockEntity::new,
					s -> ModContent.MOONLIT_SOLAR_PANEL_BE = s, MOONLIT_SOLAR_PANEL),
			blockEntity("daylight_solar_panel", DaylightSolarPanelBlockEntity.class, DaylightSolarPanelBlockEntity::new,
					s -> ModContent.DAYLIGHT_SOLAR_PANEL_BE = s, DAYLIGHT_SOLAR_PANEL),
			blockEntity("radiant_solar_panel", RadiantSolarPanelBlockEntity.class, RadiantSolarPanelBlockEntity::new,
					s -> ModContent.RADIANT_SOLAR_PANEL_BE = s, RADIANT_SOLAR_PANEL),
			blockEntity("water_mill", WaterMillBlockEntity.class, WaterMillBlockEntity::new,
					s -> ModContent.WATER_MILL_BE = s, WATER_MILL),
			blockEntity("wind_mill", WindMillBlockEntity.class, WindMillBlockEntity::new,
					s -> ModContent.WIND_MILL_BE = s, WIND_MILL),
			blockEntity("high_altitude_wind_mill", HighAltitudeWindMillBlockEntity.class,
					HighAltitudeWindMillBlockEntity::new, s -> ModContent.HIGH_ALTITUDE_WIND_MILL_BE = s,
					HIGH_ALTITUDE_WIND_MILL),
			blockEntity("storm_wind_mill", StormWindMillBlockEntity.class, StormWindMillBlockEntity::new,
					s -> ModContent.STORM_WIND_MILL_BE = s, STORM_WIND_MILL),
			blockEntity("lightning_rod_generator", LightningRodGeneratorBlockEntity.class,
					LightningRodGeneratorBlockEntity::new, s -> ModContent.LIGHTNING_ROD_GENERATOR_BE = s,
					LIGHTNING_ROD_GENERATOR),
			blockEntity("creative_energy_source", CreativeEnergySourceBlockEntity.class,
					CreativeEnergySourceBlockEntity::new, s -> ModContent.CREATIVE_ENERGY_SOURCE_BE = s,
					CREATIVE_ENERGY_SOURCE),
			blockEntity("mob_wheel_controller", MobWheelBlockEntity.class, MobWheelBlockEntity::new,
					s -> ModContent.MOB_WHEEL_CONTROLLER_BE = s, MOB_WHEEL_CONTROLLER));

	private static final List<MenuDef<?>> MENUS = List.of(
			menu("generator", GeneratorMenu::new, s -> ModContent.GENERATOR_MENU = s),
			menu("solar_panel", SolarPanelMenu::new, s -> ModContent.SOLAR_PANEL_MENU = s),
			menu("moonlit_solar_panel", MoonlitSolarPanelMenu::new, s -> ModContent.MOONLIT_SOLAR_PANEL_MENU = s),
			menu("daylight_solar_panel", DaylightSolarPanelMenu::new, s -> ModContent.DAYLIGHT_SOLAR_PANEL_MENU = s),
			menu("radiant_solar_panel", RadiantSolarPanelMenu::new, s -> ModContent.RADIANT_SOLAR_PANEL_MENU = s),
			menu("geothermal_generator", GeothermalGeneratorMenu::new, s -> ModContent.GEOTHERMAL_GENERATOR_MENU = s),
			menu("water_mill", WaterMillMenu::new, s -> ModContent.WATER_MILL_MENU = s),
			menu("wind_mill", WindMillMenu::new, s -> ModContent.WIND_MILL_MENU = s),
			menu("high_altitude_wind_mill", HighAltitudeWindMillMenu::new,
					s -> ModContent.HIGH_ALTITUDE_WIND_MILL_MENU = s),
			menu("storm_wind_mill", StormWindMillMenu::new, s -> ModContent.STORM_WIND_MILL_MENU = s),
			// MOD-386 — the lightning rod: conductor-tip slot, capacitor gauge, status line.
			menu("lightning_rod_generator", LightningRodGeneratorMenu::new,
					s -> ModContent.LIGHTNING_ROD_GENERATOR_MENU = s),
			// MOD-479 — the creative energy source: switch, output presets, fine slider, charge slot.
			menu("creative_energy_source", CreativeEnergySourceMenu::new,
					s -> ModContent.CREATIVE_ENERGY_SOURCE_MENU = s),
			// MOD-763 — the mob wheel's drive: feeder slot, stamina, status, species.
			menu("mob_wheel_controller", MobWheelMenu::new, s -> ModContent.MOB_WHEEL_CONTROLLER_MENU = s));

	/** Everything this domain declares; {@link dev.alaindustrial.registry.ContentManifest#DOMAINS} joins it in. */
	public static final Domain DOMAIN = new Domain("EnergyGeneration", BLOCKS, ITEMS, BLOCK_ENTITIES, MENUS);

	// ---- Creative tab sections (MOD-711, batch 4; ADR-041). CreativeTabContent calls them in the tab's
	// reading order. A section holds the run of the tab that coding.md §1 files under this domain, so it may
	// show a neighbour's handle where the tab has always grouped it here.

	public static void generators(Sink out) {
		// Cubes first, shaped models after them — inside the group only (MOD-574).
		show(out, ModContent.GENERATOR_ITEM);
		show(out, ModContent.GEOTHERMAL_GENERATOR_ITEM);
		show(out, ModContent.WATER_MILL_ITEM);
		show(out, ModContent.WIND_MILL_ITEM);
		// T2 wind mills (MOD-172): the height-focused Sky Mill and the weather-focused Tempest Mill.
		// Obtained only by evolving the T1 wind mill with a day/night alignment chip (no direct recipe),
		// so they are listed right after the T1 mill as the visible tail of the wind progression.
		show(out, ModContent.HIGH_ALTITUDE_WIND_MILL_ITEM);
		show(out, ModContent.STORM_WIND_MILL_ITEM);
		// MOD-479 — a QA instrument with no recipe: the creative tab is the only way to it.
		show(out, ModContent.CREATIVE_ENERGY_SOURCE_ITEM);
		// --- shaped: the panels draw a flat GUI sprite of their own, the rod is a mast.
		show(out, ModContent.SOLAR_PANEL_ITEM);
		show(out, ModContent.DAYLIGHT_SOLAR_PANEL_ITEM);
		show(out, ModContent.RADIANT_SOLAR_PANEL_ITEM);
		show(out, ModContent.CONCENTRATOR_SECTION_ITEM);
		show(out, ModContent.MOONLIT_SOLAR_PANEL_ITEM);
		show(out, ModContent.LIGHTNING_ROD_GENERATOR_ITEM);
		// MOD-763 — the mob wheel's four parts, the drive first.
		show(out, ModContent.MOB_WHEEL_CONTROLLER_ITEM);
		show(out, ModContent.MOB_WHEEL_FRAME_ITEM);
		show(out, ModContent.MOB_WHEEL_ROTOR_ITEM);
		show(out, ModContent.MOB_WHEEL_GATE_ITEM);
	}

	/**
	 * Properties shared by every block of the mob wheel (MOD-763): wood, light to break, never occluding (the
	 * formed parts are drawn by the drive's renderer, the loose ones are posts and panels), never suffocating
	 * the mob whose head is inside the structure, and pinned against pistons — a piston that moved one part
	 * would leave the drive describing a structure that is no longer there.
	 */
	private static BlockBehaviour.Properties mobWheelPart(BlockBehaviour.Properties p) {
		return LineBlockProps.pinnedAgainstPistons(p).strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion()
				.isSuffocating((state, level, pos) -> false);
	}
}
