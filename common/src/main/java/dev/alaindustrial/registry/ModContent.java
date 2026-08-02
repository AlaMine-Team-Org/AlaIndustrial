package dev.alaindustrial.registry;

import dev.alaindustrial.menu.AssemblerMenu;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.menu.CompressorMenu;
import dev.alaindustrial.menu.DaylightSolarPanelMenu;
import dev.alaindustrial.menu.ElectricFurnaceMenu;
import dev.alaindustrial.menu.ExtractorMenu;
import dev.alaindustrial.menu.GeneratorMenu;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.menu.GoldChestMenu;
import dev.alaindustrial.menu.HighAltitudeWindMillMenu;
import dev.alaindustrial.menu.IronChestMenu;
import dev.alaindustrial.menu.StorageMenu3;
import dev.alaindustrial.menu.StorageMenu6;
import dev.alaindustrial.menu.MaceratorMenu;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import dev.alaindustrial.menu.PolymerizerMenu;
import dev.alaindustrial.menu.GardenDroneStationMenu;
import dev.alaindustrial.menu.PumpMenu;
import dev.alaindustrial.menu.IncubatorMenu;
import dev.alaindustrial.menu.SawmillMenu;
import dev.alaindustrial.menu.SilverChestMenu;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.menu.StormWindMillMenu;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.menu.TeleporterStationMenu;
import dev.alaindustrial.menu.WaterMillMenu;
import dev.alaindustrial.menu.WindMillMenu;
import dev.alaindustrial.menu.VulcanizerMenu;
import dev.alaindustrial.menu.GalvanicBathMenu;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Loader-neutral registration facade (MOD-022 registration-facade step). Content classes
 * (Block/BlockEntity/Menu/Screen) read the registered game objects they need from here instead of
 * hard-referencing a per-loader registry singleton ({@code ModBlocks}/{@code ModBlockEntities}/
 * {@code ModMenus}/{@code ModItems}). That is the seam that lets the same content class compile and
 * run on both loaders once the classes move to {@code common}.
 *
 * <p><b>Why {@link Supplier} handles.</b> Fabric registers eagerly (the object exists the moment the
 * {@code Registry.register} call returns) while NeoForge registers lazily through
 * {@code DeferredRegister}, whose {@code DeferredHolder} is itself a {@link Supplier} that resolves
 * only after the registration event fires. A {@code Supplier} is the single contract both fit.
 *
 * <p><b>Binding mechanism — the fields below are plain {@code public static} slots, not JavaBean
 * setters.</b> Each loader's {@code Mod*} registries assign a {@link Supplier} into each field once,
 * at mod init. The two loaders differ in <i>what</i> they assign and <i>when the value inside is
 * resolvable</i>:
 * <ul>
 *   <li><b>Fabric</b> — registers eagerly, then in {@code ModBlocks.init()} / {@code ModItems.init()}
 *       / {@code ModBlockEntities.init()} / {@code ModMenus.init()} wraps the already-resolved value in
 *       a constant supplier: {@code ModContent.GENERATOR = () -> GENERATOR;}. The value is live the
 *       instant the field is assigned.</li>
 *   <li><b>NeoForge</b> — assigns the {@code DeferredHolder} <i>directly</i> (it already implements
 *       {@code Supplier} and lazily resolves): {@code ModContent.GENERATOR = ModBlocksNeoForge.GENERATOR;}.
 *       This runs in each {@code Mod*NeoForge.init()}, called from the {@code @Mod} constructor. The
 *       assignment order that matters:
 *       <ol>
 *         <li>{@code DeferredRegister.register(modBus)} in the constructor (queues the entries);</li>
 *         <li>{@code ModContent.X = deferredHolder} in the loader's {@code init()} — legal <i>before</i>
 *             the {@code RegisterEvent} fires, because the {@code DeferredHolder} is a lazy handle, not
 *             the resolved value;</li>
 *         <li>content classes read {@code ModContent.X.get()} at <b>runtime</b> — after both loaders'
 *             registration events have fired.</li>
 *       </ol></li>
 * </ul>
 *
 * <p><b>Read at runtime only.</b> Content must call {@code .get()} at runtime (constructors, tick,
 * interaction) — never at class-static-init — so a lazy NeoForge holder is guaranteed resolved by the
 * time it is read. In practice binding must complete before any content-class constructor runs (before
 * the first {@code ItemStack} creation or server start). Each handle starts as a throwing placeholder
 * so a premature {@code .get()} (before the loader populated it) fails loudly instead of returning
 * {@code null}.
 *
 * <p><b>Verification.</b> A field left on its throwing placeholder — a handle added here but forgotten
 * in a loader's {@code init()} — would only surface at the first {@code .get()}, mid-gameplay. Call
 * {@link #verifyAllBound()} at the end of each loader's init sequence (after every {@code Mod*.init()})
 * to catch incomplete bindings loudly at startup instead. This mirrors the set-once service-locator used
 * for {@link dev.alaindustrial.core.energy.EnergyTransactions} /
 * {@link dev.alaindustrial.network.NetworkDispatcher}.
 */
public final class ModContent {
	private ModContent() {
	}

	// --- Blocks ---
	public static Supplier<Block> GENERATOR = unbound("GENERATOR");
	public static Supplier<Block> SOLAR_PANEL = unbound("SOLAR_PANEL");
	public static Supplier<Block> MOONLIT_SOLAR_PANEL = unbound("MOONLIT_SOLAR_PANEL");
	public static Supplier<Block> DAYLIGHT_SOLAR_PANEL = unbound("DAYLIGHT_SOLAR_PANEL");
	public static Supplier<Block> GEOTHERMAL_GENERATOR = unbound("GEOTHERMAL_GENERATOR");
	public static Supplier<Block> WATER_MILL = unbound("WATER_MILL");
	public static Supplier<Block> WIND_MILL = unbound("WIND_MILL");
	public static Supplier<Block> HIGH_ALTITUDE_WIND_MILL = unbound("HIGH_ALTITUDE_WIND_MILL");
	public static Supplier<Block> STORM_WIND_MILL = unbound("STORM_WIND_MILL");
	public static Supplier<Block> PUMP = unbound("PUMP");
	public static Supplier<Block> GARDEN_DRONE_STATION = unbound("GARDEN_DRONE_STATION");
	public static Supplier<Block> FLUID_TANK = unbound("FLUID_TANK");
	public static Supplier<Block> COPPER_CABLE = unbound("COPPER_CABLE");
	public static Supplier<Block> TIN_CABLE = unbound("TIN_CABLE");
	public static Supplier<Block> GOLD_CABLE = unbound("GOLD_CABLE");
	public static Supplier<Block> INSULATED_COPPER_CABLE = unbound("INSULATED_COPPER_CABLE");
	public static Supplier<Block> INSULATED_TIN_CABLE = unbound("INSULATED_TIN_CABLE");
	public static Supplier<Block> INSULATED_GOLD_CABLE = unbound("INSULATED_GOLD_CABLE");
	public static Supplier<Block> ITEM_PIPE = unbound("ITEM_PIPE");
	public static Supplier<Block> FLUID_PIPE = unbound("FLUID_PIPE");
	public static Supplier<Block> MACERATOR = unbound("MACERATOR");
	public static Supplier<Block> BATTERY_BOX = unbound("BATTERY_BOX");
	/** Teleporter station (MOD-091) — registered but hidden from the creative tab until MOD-093. */
	public static Supplier<Block> TELEPORTER = unbound("TELEPORTER");
	public static Supplier<Block> ELECTRIC_FURNACE = unbound("ELECTRIC_FURNACE");
	public static Supplier<Block> EXTRACTOR = unbound("EXTRACTOR");
	public static Supplier<Block> COMPRESSOR = unbound("COMPRESSOR");
	public static Supplier<Block> SAWMILL = unbound("SAWMILL");
	/** MOD-275 — the first MV machine: stamps crafting-table recipes from blueprints. */
	public static Supplier<Block> ASSEMBLER = unbound("ASSEMBLER");
	/** Polymerizer (MOD-019) — the first machine fed by a fluid: oil in the tank, raw rubber out. */
	public static Supplier<Block> POLYMERIZER = unbound("POLYMERIZER");
	public static Supplier<Block> VULCANIZER = unbound("VULCANIZER");
	public static Supplier<Block> GALVANIC_BATH = unbound("GALVANIC_BATH");
	public static Supplier<Block> ELECTRIC_HEATER = unbound("ELECTRIC_HEATER");
	public static Supplier<Block> INCUBATOR = unbound("INCUBATOR");
	public static Supplier<Block> INCUBATOR_DOME = unbound("INCUBATOR_DOME");
	/** Cotton trellis (MOD-280) — the mod's first crop: a two-block support carrying a perennial plant. */
	public static Supplier<Block> TRELLIS = unbound("TRELLIS");
	public static Supplier<Block> TIN_ORE = unbound("TIN_ORE");
	public static Supplier<Block> DEEPSLATE_TIN_ORE = unbound("DEEPSLATE_TIN_ORE");
	public static Supplier<Block> SILVER_ORE = unbound("SILVER_ORE");
	public static Supplier<Block> DEEPSLATE_SILVER_ORE = unbound("DEEPSLATE_SILVER_ORE");
	public static Supplier<Block> NICKEL_ORE = unbound("NICKEL_ORE");
	public static Supplier<Block> DEEPSLATE_NICKEL_ORE = unbound("DEEPSLATE_NICKEL_ORE");
	public static Supplier<Block> SULFUR_ORE = unbound("SULFUR_ORE");
	public static Supplier<Block> DEEPSLATE_SULFUR_ORE = unbound("DEEPSLATE_SULFUR_ORE");
	public static Supplier<Block> URANIUM_ORE = unbound("URANIUM_ORE");
	public static Supplier<Block> DEEPSLATE_URANIUM_ORE = unbound("DEEPSLATE_URANIUM_ORE");
	// Iron Chest — a pure-storage block (no energy), so its BE extends vanilla
	// BaseContainerBlockEntity, not the mod's MachineBlockEntity. See docs/blocks/iron_chest.md.
	public static Supplier<Block> IRON_CHEST = unbound("IRON_CHEST");
	/** MOD-287 — one block of the modular warehouse; several form one shared inventory. */
	public static Supplier<Block> STORAGE_MODULE = unbound("STORAGE_MODULE");
	public static Supplier<Block> IRON_FURNACE = unbound("IRON_FURNACE");
	// Silver Chest (MOD-087) — the tier above the iron chest: 45 slots (5×9, +1 row). Same vanilla
	// BaseContainerBlockEntity spine, no energy. Crafted from an iron chest ringed with silver ingots.
	public static Supplier<Block> SILVER_CHEST = unbound("SILVER_CHEST");
	// Gold Chest (MOD-088) — the tier above the silver chest: 54 slots (6×9, +1 row). Same vanilla
	// BaseContainerBlockEntity spine, no energy. Crafted from a silver chest ringed with gold ingots.
	public static Supplier<Block> GOLD_CHEST = unbound("GOLD_CHEST");
	// Tempered Iron Block — a "block of X" material block (9 ingots ↔ 1 block), like
	// vanilla iron block. Pure material/decorative block, no BE, single texture on all 6 faces.
	public static Supplier<Block> TEMPERED_IRON_BLOCK = unbound("TEMPERED_IRON_BLOCK");
	// MOD-225: machine casing (crafting base for machines) + two decorative plate blocks.
	public static Supplier<Block> MACHINE_CASING = unbound("MACHINE_CASING");
	public static Supplier<Block> ADVANCED_MACHINE_CASING = unbound("ADVANCED_MACHINE_CASING");
	public static Supplier<Block> SILVER_PLATE_BLOCK = unbound("SILVER_PLATE_BLOCK");
	public static Supplier<Block> TEMPERED_IRON_PLATE_BLOCK = unbound("TEMPERED_IRON_PLATE_BLOCK");
	public static Supplier<Block> INDUSTRIAL_WORKBENCH = unbound("INDUSTRIAL_WORKBENCH");
	// Enriched Uranium Torch (MOD-085) — a vanilla-behaviour torch (light 15, green flame) in two
	// blocks: standing + wall. The wall variant has NO block item; it drops/names from the standing
	// torch via overrideLootTable/overrideDescription, exactly as vanilla WALL_TORCH mirrors TORCH.
	public static Supplier<Block> ENRICHED_URANIUM_TORCH = unbound("ENRICHED_URANIUM_TORCH");
	public static Supplier<Block> ENRICHED_URANIUM_WALL_TORCH = unbound("ENRICHED_URANIUM_WALL_TORCH");
	// Oil (MOD-238): the in-world liquid block. No block item — a liquid block is never held.
	public static Supplier<Block> OIL_BLOCK = unbound("OIL_BLOCK");

	// --- Fluids ---
	// Oil (MOD-238): still + flowing. OilFluid (common) reads these at runtime for its
	// getSource()/getFlowing() cross-references; NeoForge binds FluidType-carrying subclasses.
	public static Supplier<FlowingFluid> OIL = unbound("OIL");
	public static Supplier<FlowingFluid> FLOWING_OIL = unbound("FLOWING_OIL");

	// --- Items (crafting components + tools) ---
	public static Supplier<Item> ELECTRONIC_CIRCUIT = unbound("ELECTRONIC_CIRCUIT");
	/** MOD-299 — the MV tier of the circuit: the LV board rewired with gold and sealed in rubber. */
	public static Supplier<Item> ADVANCED_CIRCUIT = unbound("ADVANCED_CIRCUIT");
	/** MOD-275 — blank out of the grid, a recorded blueprint once the Assembler writes a layout. */
	public static Supplier<Item> ASSEMBLY_BLUEPRINT = unbound("ASSEMBLY_BLUEPRINT");
	// Copper Coil — a crafting component (copper cable wound on a tin core); gates the Electric Drill.
	public static Supplier<Item> COPPER_COIL = unbound("COPPER_COIL");
	public static Supplier<Item> ALIGNMENT_CHIP_DAY = unbound("ALIGNMENT_CHIP_DAY");
	public static Supplier<Item> ALIGNMENT_CHIP_NIGHT = unbound("ALIGNMENT_CHIP_NIGHT");
	// Upgrade chips (MOD-080): EMPTY_CHIP is the inert blank/base; MUTE_CHIP is the first functional
	// upgrade — dropped into a machine's active upgrade slot it silences the block (see isMuted()).
	public static Supplier<Item> EMPTY_CHIP = unbound("EMPTY_CHIP");

	// Incubator (MOD-118): three mode chips, the mutation by-products and the tier-1 materials.
	public static Supplier<Item> MUTATION_CHIP_TRANSFORM = unbound("MUTATION_CHIP_TRANSFORM");
	public static Supplier<Item> MUTATION_CHIP_DUPLICATE = unbound("MUTATION_CHIP_DUPLICATE");
	public static Supplier<Item> MUTATION_CHIP_CREATE = unbound("MUTATION_CHIP_CREATE");
	public static Supplier<Item> DEPLETED_URANIUM = unbound("DEPLETED_URANIUM");
	public static Supplier<Item> IRRADIATED_SLAG = unbound("IRRADIATED_SLAG");
	public static Supplier<Item> IRRADIATED_DIAMOND = unbound("IRRADIATED_DIAMOND");
	public static Supplier<Item> RESONANT_SHARD = unbound("RESONANT_SHARD");
	public static Supplier<Item> MUTAGEN_DUST = unbound("MUTAGEN_DUST");
	public static Supplier<Item> UNSTABLE_ISOTOPE = unbound("UNSTABLE_ISOTOPE");
	public static Supplier<Item> MUTE_CHIP = unbound("MUTE_CHIP");
	public static Supplier<Item> WINDMILL_ROTOR = unbound("WINDMILL_ROTOR");
	public static Supplier<Item> WATER_MILL_WHEEL = unbound("WATER_MILL_WHEEL");
	public static Supplier<Item> WOODEN_GEAR = unbound("WOODEN_GEAR");
	// Metal gears (MOD-105): crafting components for machinery still to come. Each is a cross of the
	// tier material around a wooden gear; no consumer recipe uses them yet.
	public static Supplier<Item> STONE_GEAR = unbound("STONE_GEAR");
	public static Supplier<Item> IRON_GEAR = unbound("IRON_GEAR");
	public static Supplier<Item> GOLD_GEAR = unbound("GOLD_GEAR");
	public static Supplier<Item> SILVER_GEAR = unbound("SILVER_GEAR");
	public static Supplier<Item> TEMPERED_IRON = unbound("TEMPERED_IRON");
	public static Supplier<Item> TEMPERED_IRON_PICKAXE = unbound("TEMPERED_IRON_PICKAXE");
	public static Supplier<Item> TEMPERED_IRON_AXE = unbound("TEMPERED_IRON_AXE");
	public static Supplier<Item> TEMPERED_IRON_HOE = unbound("TEMPERED_IRON_HOE");
	public static Supplier<Item> TEMPERED_IRON_SHOVEL = unbound("TEMPERED_IRON_SHOVEL");
	public static Supplier<Item> TEMPERED_IRON_SWORD = unbound("TEMPERED_IRON_SWORD");
	// Tempered-iron armor (MOD-056): helmet/chestplate/leggings/boots, built via the MC 26.2
	// Item.Properties.humanoidArmor(ArmorMaterial, ArmorType) helper. Same line as MOD-054 tools.
	public static Supplier<Item> TEMPERED_IRON_HELMET = unbound("TEMPERED_IRON_HELMET");
	public static Supplier<Item> TEMPERED_IRON_CHESTPLATE = unbound("TEMPERED_IRON_CHESTPLATE");
	public static Supplier<Item> TEMPERED_IRON_LEGGINGS = unbound("TEMPERED_IRON_LEGGINGS");
	public static Supplier<Item> TEMPERED_IRON_BOOTS = unbound("TEMPERED_IRON_BOOTS");
	public static Supplier<Item> IRON_DUST = unbound("IRON_DUST");
	public static Supplier<Item> COPPER_DUST = unbound("COPPER_DUST");
	public static Supplier<Item> GOLD_DUST = unbound("GOLD_DUST");
	public static Supplier<Item> COAL_DUST = unbound("COAL_DUST");
	public static Supplier<Item> DIAMOND_DUST = unbound("DIAMOND_DUST");
	public static Supplier<Item> EMERALD_DUST = unbound("EMERALD_DUST");
	public static Supplier<Item> LAPIS_DUST = unbound("LAPIS_DUST");
	public static Supplier<Item> TIN_DUST = unbound("TIN_DUST");
	public static Supplier<Item> RAW_TIN = unbound("RAW_TIN");
	public static Supplier<Item> TIN_INGOT = unbound("TIN_INGOT");
	public static Supplier<Item> SILVER_DUST = unbound("SILVER_DUST");
	public static Supplier<Item> RAW_SILVER = unbound("RAW_SILVER");
	public static Supplier<Item> SILVER_INGOT = unbound("SILVER_INGOT");
	public static Supplier<Item> NICKEL_DUST = unbound("NICKEL_DUST");
	public static Supplier<Item> RAW_NICKEL = unbound("RAW_NICKEL");
	public static Supplier<Item> NICKEL_INGOT = unbound("NICKEL_INGOT");
	public static Supplier<Item> SULFUR_DUST = unbound("SULFUR_DUST");
	public static Supplier<Item> RAW_SULFUR = unbound("RAW_SULFUR");
	public static Supplier<Item> URANIUM_DUST = unbound("URANIUM_DUST");
	public static Supplier<Item> RAW_URANIUM = unbound("RAW_URANIUM");
	public static Supplier<Item> URANIUM_INGOT = unbound("URANIUM_INGOT");
	public static Supplier<Item> NETWORK_ANALYZER = unbound("NETWORK_ANALYZER");
	public static Supplier<Item> WRENCH = unbound("WRENCH");
	public static Supplier<Item> GUIDE_BOOK = unbound("GUIDE_BOOK");
	/** Teleporter Remote (MOD-092) — hidden from the creative tab until MOD-093 completes the feature. */
	public static Supplier<Item> TELEPORTER_REMOTE = unbound("TELEPORTER_REMOTE");
	public static Supplier<Item> BATTERY_POUCH = unbound("BATTERY_POUCH");
	// Energy Pack (MOD-065) — worn LV energy buffer (chest slot) that tops up the powered items the
	// player carries; BATTERY is its crafting component (an inert cell, no charge of its own).
	public static Supplier<Item> BATTERY = unbound("BATTERY");
	public static Supplier<Item> ENERGY_PACK = unbound("ENERGY_PACK");
	// Electric Drill (MOD-079) — the first powered hand tool: a diamond-tier pickaxe that runs on EU.
	public static Supplier<Item> ELECTRIC_DRILL = unbound("ELECTRIC_DRILL");
	// Electromagnet (MOD-132) — EU item in any inventory slot that draws loose drops toward the carrier.
	public static Supplier<Item> ELECTROMAGNET = unbound("ELECTROMAGNET");
	// Jetpack (MOD-148) — worn EU flight device (chest slot): thrust on held jump, glide when drained.
	public static Supplier<Item> JETPACK = unbound("JETPACK");
	// Vacuum Capsule (MOD-063) — a stackable fluid container: empty (×64) exchanges with the
	// fluid-carrying filled form (×16). See docs/blocks/items/vacuum_capsule.md.
	public static Supplier<Item> VACUUM_CAPSULE = unbound("VACUUM_CAPSULE");
	public static Supplier<Item> FILLED_VACUUM_CAPSULE = unbound("FILLED_VACUUM_CAPSULE");
	// Stock Display Frame (MOD-066) — placement item for the frame entity below.
	public static Supplier<Item> STOCK_DISPLAY_FRAME_ITEM = unbound("STOCK_DISPLAY_FRAME_ITEM");
	// Scythe (MOD-068) — AOE foliage-clearing tool, six material tiers. Behaviour lives in
	// dev.alaindustrial.item.tool.ScytheItem (common); each loader registers the six items.
	public static Supplier<Item> SCYTHE_WOOD = unbound("SCYTHE_WOOD");
	public static Supplier<Item> SCYTHE_STONE = unbound("SCYTHE_STONE");
	public static Supplier<Item> SCYTHE_COPPER = unbound("SCYTHE_COPPER");
	public static Supplier<Item> SCYTHE_IRON = unbound("SCYTHE_IRON");
	public static Supplier<Item> SCYTHE_GOLD = unbound("SCYTHE_GOLD");
	public static Supplier<Item> SCYTHE_TEMPERED_IRON = unbound("SCYTHE_TEMPERED_IRON");
	public static Supplier<Item> SCYTHE_DIAMOND = unbound("SCYTHE_DIAMOND");
	public static Supplier<Item> SCYTHE_NETHERITE = unbound("SCYTHE_NETHERITE");
	// Metal plates (MOD-078) — ingot form pressed/hammered from an ingot; feeds LV machine and
	// circuit recipes. Eight metals; no tempered_iron dust exists, so tempered_iron_plate has no
	// maceration recycle. See docs/blocks/components/material_forms.md.
	public static Supplier<Item> COPPER_PLATE = unbound("COPPER_PLATE");
	public static Supplier<Item> GOLD_PLATE = unbound("GOLD_PLATE");
	public static Supplier<Item> IRON_PLATE = unbound("IRON_PLATE");
	public static Supplier<Item> TIN_PLATE = unbound("TIN_PLATE");
	public static Supplier<Item> SILVER_PLATE = unbound("SILVER_PLATE");
	public static Supplier<Item> NICKEL_PLATE = unbound("NICKEL_PLATE");
	public static Supplier<Item> URANIUM_PLATE = unbound("URANIUM_PLATE");
	public static Supplier<Item> TEMPERED_IRON_PLATE = unbound("TEMPERED_IRON_PLATE");
	// Forge Hammer (MOD-078) — pre-machine hand tool: ingot + hammer on the grid → plate; the hammer
	// stays and loses 1 durability per plate. Behaviour lives in dev.alaindustrial.item.tool.HammerItem
	// (common) + a loader subclass for the craft-remainder hook.
	public static Supplier<Item> FORGE_HAMMER = unbound("FORGE_HAMMER");
	// Oil Bucket (MOD-238): a vanilla-pattern BucketItem carrying the still oil fluid.
	public static Supplier<Item> OIL_BUCKET = unbound("OIL_BUCKET");
	// Oil → rubber chain: the polymerizer makes raw rubber; the vulcanizer cures it with sulfur and heat.
	public static Supplier<Item> RAW_RUBBER = unbound("RAW_RUBBER");
	public static Supplier<Item> RUBBER = unbound("RUBBER");
	/** Cotton seed (MOD-280) — right-clicked onto a bare trellis to plant it. */
	public static Supplier<Item> COTTON_SEEDS = unbound("COTTON_SEEDS");
	/** Cotton fibre (MOD-280) — the harvest; the farming alternative to spider string for MOD-127. */
	public static Supplier<Item> COTTON_FIBER = unbound("COTTON_FIBER");
	// Fluxweave chain (MOD-127): the Galvanic Bath plates fibre with silver into conductive thread, and
	// four threads weave into one sheet of cloth — the material the LV armor set is built from.
	public static Supplier<Item> FLUX_THREAD = unbound("FLUX_THREAD");
	public static Supplier<Item> FLUXWEAVE_CLOTH = unbound("FLUXWEAVE_CLOTH");
	// Fluxweave armour (MOD-127): the mod's first full EU set. One class, four registrations.
	public static Supplier<Item> FLUXWEAVE_HELMET = unbound("FLUXWEAVE_HELMET");
	public static Supplier<Item> FLUXWEAVE_CHESTPLATE = unbound("FLUXWEAVE_CHESTPLATE");
	public static Supplier<Item> FLUXWEAVE_LEGGINGS = unbound("FLUXWEAVE_LEGGINGS");
	public static Supplier<Item> FLUXWEAVE_BOOTS = unbound("FLUXWEAVE_BOOTS");

	// --- Entity types ---
	// Stock Display Frame (MOD-066) — the mod's first entity: an ItemFrame subclass that counts the
	// container behind it. See docs/blocks/utility/stock_display_frame.md.
	public static Supplier<EntityType<?>> STOCK_DISPLAY_FRAME = unbound("STOCK_DISPLAY_FRAME");

	// --- Block items ---
	public static Supplier<BlockItem> GENERATOR_ITEM = unbound("GENERATOR_ITEM");
	public static Supplier<BlockItem> GEOTHERMAL_GENERATOR_ITEM = unbound("GEOTHERMAL_GENERATOR_ITEM");
	public static Supplier<BlockItem> WATER_MILL_ITEM = unbound("WATER_MILL_ITEM");
	public static Supplier<BlockItem> WIND_MILL_ITEM = unbound("WIND_MILL_ITEM");
	public static Supplier<BlockItem> HIGH_ALTITUDE_WIND_MILL_ITEM = unbound("HIGH_ALTITUDE_WIND_MILL_ITEM");
	public static Supplier<BlockItem> STORM_WIND_MILL_ITEM = unbound("STORM_WIND_MILL_ITEM");
	public static Supplier<BlockItem> SOLAR_PANEL_ITEM = unbound("SOLAR_PANEL_ITEM");
	public static Supplier<BlockItem> MOONLIT_SOLAR_PANEL_ITEM = unbound("MOONLIT_SOLAR_PANEL_ITEM");
	public static Supplier<BlockItem> DAYLIGHT_SOLAR_PANEL_ITEM = unbound("DAYLIGHT_SOLAR_PANEL_ITEM");
	public static Supplier<BlockItem> COPPER_CABLE_ITEM = unbound("COPPER_CABLE_ITEM");
	public static Supplier<BlockItem> TIN_CABLE_ITEM = unbound("TIN_CABLE_ITEM");
	public static Supplier<BlockItem> GOLD_CABLE_ITEM = unbound("GOLD_CABLE_ITEM");
	public static Supplier<BlockItem> INSULATED_COPPER_CABLE_ITEM = unbound("INSULATED_COPPER_CABLE_ITEM");
	public static Supplier<BlockItem> INSULATED_TIN_CABLE_ITEM = unbound("INSULATED_TIN_CABLE_ITEM");
	public static Supplier<BlockItem> INSULATED_GOLD_CABLE_ITEM = unbound("INSULATED_GOLD_CABLE_ITEM");
	public static Supplier<BlockItem> ITEM_PIPE_ITEM = unbound("ITEM_PIPE_ITEM");
	public static Supplier<BlockItem> FLUID_PIPE_ITEM = unbound("FLUID_PIPE_ITEM");
	public static Supplier<BlockItem> MACERATOR_ITEM = unbound("MACERATOR_ITEM");
	public static Supplier<BlockItem> BATTERY_BOX_ITEM = unbound("BATTERY_BOX_ITEM");
	public static Supplier<BlockItem> TELEPORTER_ITEM = unbound("TELEPORTER_ITEM");
	public static Supplier<BlockItem> ELECTRIC_FURNACE_ITEM = unbound("ELECTRIC_FURNACE_ITEM");
	public static Supplier<BlockItem> EXTRACTOR_ITEM = unbound("EXTRACTOR_ITEM");
	public static Supplier<BlockItem> COMPRESSOR_ITEM = unbound("COMPRESSOR_ITEM");
	public static Supplier<BlockItem> SAWMILL_ITEM = unbound("SAWMILL_ITEM");
	public static Supplier<BlockItem> ASSEMBLER_ITEM = unbound("ASSEMBLER_ITEM");
	public static Supplier<BlockItem> POLYMERIZER_ITEM = unbound("POLYMERIZER_ITEM");
	public static Supplier<BlockItem> VULCANIZER_ITEM = unbound("VULCANIZER_ITEM");
	public static Supplier<BlockItem> GALVANIC_BATH_ITEM = unbound("GALVANIC_BATH_ITEM");
	public static Supplier<BlockItem> ELECTRIC_HEATER_ITEM = unbound("ELECTRIC_HEATER_ITEM");
	public static Supplier<BlockItem> INCUBATOR_ITEM = unbound("INCUBATOR_ITEM");
	public static Supplier<BlockItem> TRELLIS_ITEM = unbound("TRELLIS_ITEM");
	public static Supplier<BlockItem> PUMP_ITEM = unbound("PUMP_ITEM");
	public static Supplier<BlockItem> GARDEN_DRONE_STATION_ITEM = unbound("GARDEN_DRONE_STATION_ITEM");
	/** The drone itself — an item that lives in the station's dock slot (MOD-277). */
	public static Supplier<Item> GARDEN_DRONE = unbound("GARDEN_DRONE");
	public static Supplier<BlockItem> FLUID_TANK_ITEM = unbound("FLUID_TANK_ITEM");
	public static Supplier<BlockItem> TIN_ORE_ITEM = unbound("TIN_ORE_ITEM");
	public static Supplier<BlockItem> DEEPSLATE_TIN_ORE_ITEM = unbound("DEEPSLATE_TIN_ORE_ITEM");
	public static Supplier<BlockItem> SILVER_ORE_ITEM = unbound("SILVER_ORE_ITEM");
	public static Supplier<BlockItem> DEEPSLATE_SILVER_ORE_ITEM = unbound("DEEPSLATE_SILVER_ORE_ITEM");
	public static Supplier<BlockItem> NICKEL_ORE_ITEM = unbound("NICKEL_ORE_ITEM");
	public static Supplier<BlockItem> DEEPSLATE_NICKEL_ORE_ITEM = unbound("DEEPSLATE_NICKEL_ORE_ITEM");
	public static Supplier<BlockItem> SULFUR_ORE_ITEM = unbound("SULFUR_ORE_ITEM");
	public static Supplier<BlockItem> DEEPSLATE_SULFUR_ORE_ITEM = unbound("DEEPSLATE_SULFUR_ORE_ITEM");
	public static Supplier<BlockItem> URANIUM_ORE_ITEM = unbound("URANIUM_ORE_ITEM");
	public static Supplier<BlockItem> DEEPSLATE_URANIUM_ORE_ITEM = unbound("DEEPSLATE_URANIUM_ORE_ITEM");
	public static Supplier<BlockItem> IRON_CHEST_ITEM = unbound("IRON_CHEST_ITEM");
	public static Supplier<BlockItem> STORAGE_MODULE_ITEM = unbound("STORAGE_MODULE_ITEM");
	public static Supplier<BlockItem> IRON_FURNACE_ITEM = unbound("IRON_FURNACE_ITEM");
	public static Supplier<BlockItem> SILVER_CHEST_ITEM = unbound("SILVER_CHEST_ITEM");
	public static Supplier<BlockItem> GOLD_CHEST_ITEM = unbound("GOLD_CHEST_ITEM");
	public static Supplier<BlockItem> TEMPERED_IRON_BLOCK_ITEM = unbound("TEMPERED_IRON_BLOCK_ITEM");
	// MOD-225 block-items.
	public static Supplier<BlockItem> MACHINE_CASING_ITEM = unbound("MACHINE_CASING_ITEM");
	public static Supplier<BlockItem> ADVANCED_MACHINE_CASING_ITEM = unbound("ADVANCED_MACHINE_CASING_ITEM");
	public static Supplier<BlockItem> SILVER_PLATE_BLOCK_ITEM = unbound("SILVER_PLATE_BLOCK_ITEM");
	public static Supplier<BlockItem> TEMPERED_IRON_PLATE_BLOCK_ITEM = unbound("TEMPERED_IRON_PLATE_BLOCK_ITEM");
	public static Supplier<BlockItem> INDUSTRIAL_WORKBENCH_ITEM = unbound("INDUSTRIAL_WORKBENCH_ITEM");
	// Enriched Uranium Torch (MOD-085) — only the STANDING torch has a block item; the wall variant
	// drops this item via its overrideLootTable, so it needs no item of its own.
	public static Supplier<BlockItem> ENRICHED_URANIUM_TORCH_ITEM = unbound("ENRICHED_URANIUM_TORCH_ITEM");

	// --- Block entity types ---
	public static Supplier<BlockEntityType<?>> GENERATOR_BE = unbound("GENERATOR_BE");
	public static Supplier<BlockEntityType<?>> GEOTHERMAL_GENERATOR_BE = unbound("GEOTHERMAL_GENERATOR_BE");
	public static Supplier<BlockEntityType<?>> WATER_MILL_BE = unbound("WATER_MILL_BE");
	public static Supplier<BlockEntityType<?>> WIND_MILL_BE = unbound("WIND_MILL_BE");
	public static Supplier<BlockEntityType<?>> HIGH_ALTITUDE_WIND_MILL_BE = unbound("HIGH_ALTITUDE_WIND_MILL_BE");
	public static Supplier<BlockEntityType<?>> STORM_WIND_MILL_BE = unbound("STORM_WIND_MILL_BE");
	public static Supplier<BlockEntityType<?>> SOLAR_PANEL_BE = unbound("SOLAR_PANEL_BE");
	public static Supplier<BlockEntityType<?>> MOONLIT_SOLAR_PANEL_BE = unbound("MOONLIT_SOLAR_PANEL_BE");
	public static Supplier<BlockEntityType<?>> DAYLIGHT_SOLAR_PANEL_BE = unbound("DAYLIGHT_SOLAR_PANEL_BE");
	public static Supplier<BlockEntityType<?>> COPPER_CABLE_BE = unbound("COPPER_CABLE_BE");
	public static Supplier<BlockEntityType<?>> ITEM_PIPE_BE = unbound("ITEM_PIPE_BE");
	public static Supplier<BlockEntityType<?>> FLUID_PIPE_BE = unbound("FLUID_PIPE_BE");
	public static Supplier<BlockEntityType<?>> MACERATOR_BE = unbound("MACERATOR_BE");
	public static Supplier<BlockEntityType<?>> BATTERY_BOX_BE = unbound("BATTERY_BOX_BE");
	public static Supplier<BlockEntityType<?>> TELEPORTER_BE = unbound("TELEPORTER_BE");
	public static Supplier<BlockEntityType<?>> ELECTRIC_FURNACE_BE = unbound("ELECTRIC_FURNACE_BE");
	public static Supplier<BlockEntityType<?>> EXTRACTOR_BE = unbound("EXTRACTOR_BE");
	public static Supplier<BlockEntityType<?>> COMPRESSOR_BE = unbound("COMPRESSOR_BE");
	public static Supplier<BlockEntityType<?>> SAWMILL_BE = unbound("SAWMILL_BE");
	public static Supplier<BlockEntityType<?>> ASSEMBLER_BE = unbound("ASSEMBLER_BE");
	public static Supplier<BlockEntityType<?>> POLYMERIZER_BE = unbound("POLYMERIZER_BE");
	public static Supplier<BlockEntityType<?>> VULCANIZER_BE = unbound("VULCANIZER_BE");
	public static Supplier<BlockEntityType<?>> GALVANIC_BATH_BE = unbound("GALVANIC_BATH_BE");
	public static Supplier<BlockEntityType<?>> ELECTRIC_HEATER_BE = unbound("ELECTRIC_HEATER_BE");
	public static Supplier<BlockEntityType<?>> INCUBATOR_BE = unbound("INCUBATOR_BE");
	public static Supplier<BlockEntityType<?>> PUMP_BE = unbound("PUMP_BE");
	public static Supplier<BlockEntityType<?>> GARDEN_DRONE_STATION_BE = unbound("GARDEN_DRONE_STATION_BE");
	public static Supplier<BlockEntityType<?>> FLUID_TANK_BE = unbound("FLUID_TANK_BE");
	public static Supplier<BlockEntityType<?>> IRON_CHEST_BE = unbound("IRON_CHEST_BE");
	public static Supplier<BlockEntityType<?>> STORAGE_MODULE_BE = unbound("STORAGE_MODULE_BE");
	public static Supplier<BlockEntityType<?>> IRON_FURNACE_BE = unbound("IRON_FURNACE_BE");
	public static Supplier<BlockEntityType<?>> SILVER_CHEST_BE = unbound("SILVER_CHEST_BE");
	public static Supplier<BlockEntityType<?>> GOLD_CHEST_BE = unbound("GOLD_CHEST_BE");

	// --- Menu types ---
	// Each slot carries its concrete menu class (MOD-198), not MenuType<?>. That generic is what ties a
	// menu to its screen: ContentManifest.MenuDef binds into these slots with T inferred from the menu
	// factory, and MenuScreenManifest.screen(...) reads them with M inferred from the screen — so a
	// mismatched pair in EITHER manifest is a compile error instead of a ClassCastException at GUI-open.
	// A wildcard here defeats both: with Supplier<MenuType<?>> the lambda `() -> X_MENU.get()` yields a
	// fresh capture that cannot satisfy the invariant Supplier<MenuType<M>>, so the typed form does not
	// compile at all — not even for correct pairs.
	public static Supplier<MenuType<GeneratorMenu>> GENERATOR_MENU = unbound("GENERATOR_MENU");
	public static Supplier<MenuType<MaceratorMenu>> MACERATOR_MENU = unbound("MACERATOR_MENU");
	public static Supplier<MenuType<SolarPanelMenu>> SOLAR_PANEL_MENU = unbound("SOLAR_PANEL_MENU");
	public static Supplier<MenuType<MoonlitSolarPanelMenu>> MOONLIT_SOLAR_PANEL_MENU =
			unbound("MOONLIT_SOLAR_PANEL_MENU");
	public static Supplier<MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE_MENU = unbound("ELECTRIC_FURNACE_MENU");
	public static Supplier<MenuType<ExtractorMenu>> EXTRACTOR_MENU = unbound("EXTRACTOR_MENU");
	public static Supplier<MenuType<CompressorMenu>> COMPRESSOR_MENU = unbound("COMPRESSOR_MENU");
	public static Supplier<MenuType<SawmillMenu>> SAWMILL_MENU = unbound("SAWMILL_MENU");
	public static Supplier<MenuType<AssemblerMenu>> ASSEMBLER_MENU = unbound("ASSEMBLER_MENU");
	public static Supplier<MenuType<PolymerizerMenu>> POLYMERIZER_MENU = unbound("POLYMERIZER_MENU");
	public static Supplier<MenuType<VulcanizerMenu>> VULCANIZER_MENU = unbound("VULCANIZER_MENU");
	public static Supplier<MenuType<GalvanicBathMenu>> GALVANIC_BATH_MENU = unbound("GALVANIC_BATH_MENU");
	public static Supplier<MenuType<IncubatorMenu>> INCUBATOR_MENU = unbound("INCUBATOR_MENU");
	public static Supplier<MenuType<BatteryBoxMenu>> BATTERY_BOX_MENU = unbound("BATTERY_BOX_MENU");
	/** Teleporter station screen (MOD-093): EU bar, owner, private/public toggle. */
	public static Supplier<MenuType<TeleporterStationMenu>> TELEPORTER_STATION_MENU =
			unbound("TELEPORTER_STATION_MENU");
	/** Teleporter remote screen (MOD-093): the named point list. */
	public static Supplier<MenuType<TeleporterRemoteMenu>> TELEPORTER_REMOTE_MENU =
			unbound("TELEPORTER_REMOTE_MENU");
	public static Supplier<MenuType<DaylightSolarPanelMenu>> DAYLIGHT_SOLAR_PANEL_MENU =
			unbound("DAYLIGHT_SOLAR_PANEL_MENU");
	public static Supplier<MenuType<GeothermalGeneratorMenu>> GEOTHERMAL_GENERATOR_MENU =
			unbound("GEOTHERMAL_GENERATOR_MENU");
	public static Supplier<MenuType<PumpMenu>> PUMP_MENU = unbound("PUMP_MENU");
	// Typed (not wildcard): MenuScreenManifest.screen(...) cannot bind a wildcard MenuType.
	public static Supplier<MenuType<GardenDroneStationMenu>> GARDEN_DRONE_STATION_MENU =
			unbound("GARDEN_DRONE_STATION_MENU");
	public static Supplier<MenuType<WaterMillMenu>> WATER_MILL_MENU = unbound("WATER_MILL_MENU");
	public static Supplier<MenuType<WindMillMenu>> WIND_MILL_MENU = unbound("WIND_MILL_MENU");
	public static Supplier<MenuType<HighAltitudeWindMillMenu>> HIGH_ALTITUDE_WIND_MILL_MENU =
			unbound("HIGH_ALTITUDE_WIND_MILL_MENU");
	public static Supplier<MenuType<StormWindMillMenu>> STORM_WIND_MILL_MENU = unbound("STORM_WIND_MILL_MENU");
	public static Supplier<MenuType<IronChestMenu>> IRON_CHEST_MENU = unbound("IRON_CHEST_MENU");
	// MOD-287 — one menu class, four registered sizes: the client builds its menu from
	// (syncId, Inventory) alone, so the row count has to travel in the menu type itself.
	public static Supplier<MenuType<StorageMenu3>> STORAGE_MODULE_MENU_3 = unbound("STORAGE_MODULE_MENU_3");
	public static Supplier<MenuType<StorageMenu6>> STORAGE_MODULE_MENU_6 = unbound("STORAGE_MODULE_MENU_6");
	public static Supplier<MenuType<SilverChestMenu>> SILVER_CHEST_MENU = unbound("SILVER_CHEST_MENU");
	public static Supplier<MenuType<GoldChestMenu>> GOLD_CHEST_MENU = unbound("GOLD_CHEST_MENU");

	/** A placeholder handle that throws if read before the loader populated it. */
	private static <T> Supplier<T> unbound(String name) {
		return new Unbound<>(name);
	}

	/**
	 * The initial value of every handle: a {@link Supplier} that throws on {@code .get()} and is
	 * identifiable by {@link #verifyAllBound()} <i>without</i> reading it. Identity matters — a
	 * legitimately-bound NeoForge {@code DeferredHolder} also throws on {@code .get()} until its
	 * {@code RegisterEvent} fires, so {@code verifyAllBound()} must detect "unbound" by type, never by
	 * probing {@code .get()}.
	 */
	private static final class Unbound<T> implements Supplier<T> {
		private final String name;

		private Unbound(String name) {
			this.name = name;
		}

		@Override
		public T get() {
			throw new IllegalStateException(
					"ModContent." + name + " read before it was bound — the loader registry must "
							+ "populate ModContent at init, and content must only read handles at runtime");
		}
	}

	/**
	 * Fail loudly if any handle was never bound by a loader's {@code init()}. Reflects over every
	 * {@code public static Supplier} field on this class and flags each one still holding its
	 * {@link Unbound} placeholder. Call this once at the end of each loader's init sequence — after all
	 * {@code Mod*.init()} calls — so a handle added here but forgotten in {@code ModBlocks}/{@code ModItems}/
	 * {@code ModBlockEntities}/{@code ModMenus}{@code .init()} (or their NeoForge counterparts) crashes at
	 * startup with the field name instead of silently mid-gameplay at first {@code .get()}.
	 *
	 * <p>Detection is by <b>identity</b>, not by calling {@code .get()}: a correctly-bound NeoForge
	 * {@code DeferredHolder} also throws from {@code .get()} until its {@code RegisterEvent} fires, so
	 * this method must not probe the value — it only checks whether the slot is still the placeholder.
	 *
	 * @throws IllegalStateException listing every unbound handle, if any remain.
	 */
	public static void verifyAllBound() {
		List<String> unbound = unboundHandles();
		if (!unbound.isEmpty()) {
			throw new IllegalStateException(
					"ModContent handles never bound by any loader init(): " + unbound
							+ " — each must be assigned in the loader's Mod*.init() (Fabric wraps the "
							+ "registered value, NeoForge assigns its DeferredHolder)");
		}
	}

	/**
	 * The names of every handle still holding its {@link Unbound} placeholder — i.e. never bound by a
	 * loader's {@code init()}. Empty when the facade is fully populated. Detection is by identity (see
	 * {@link #verifyAllBound()}), so it is safe to call before NeoForge {@code RegisterEvent}s fire.
	 *
	 * <p>Exposed (rather than only the throwing {@link #verifyAllBound()}) so a loader still mid-migration
	 * — the NeoForge side until Phase 4 populates every registry — can <i>report</i> the gap without
	 * aborting load.
	 */
	public static List<String> unboundHandles() {
		List<String> unbound = new ArrayList<>();
		for (Field field : ModContent.class.getDeclaredFields()) {
			int mods = field.getModifiers();
			if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || Supplier.class != field.getType()) {
				continue;
			}
			try {
				if (field.get(null) instanceof Unbound<?>) {
					unbound.add(field.getName());
				}
			} catch (IllegalAccessException e) {
				// public static field on a public class — cannot happen; surface it if it ever does.
				throw new IllegalStateException("Cannot read ModContent." + field.getName(), e);
			}
		}
		return unbound;
	}
}
