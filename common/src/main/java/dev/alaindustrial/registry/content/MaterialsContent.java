package dev.alaindustrial.registry.content;

import static dev.alaindustrial.registry.content.ContentDeclarations.beginBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.block;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockItem;
import static dev.alaindustrial.registry.content.ContentDeclarations.durableComponent;
import static dev.alaindustrial.registry.content.ContentDeclarations.endBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.hint;
import static dev.alaindustrial.registry.content.ContentDeclarations.item;
import static dev.alaindustrial.registry.content.ContentDeclarations.machine;
import static dev.alaindustrial.registry.content.ContentDeclarations.plain;
import static dev.alaindustrial.registry.content.TabEntries.show;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.item.CarbonBriquetteItem;
import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import dev.alaindustrial.item.energy.BatteryItem;
import dev.alaindustrial.item.energy.CrystalBlankItem;
import dev.alaindustrial.item.energy.CrystalTier;
import dev.alaindustrial.item.misc.CannedRation;
import dev.alaindustrial.item.tool.NetheriteDrillUpgradeTemplate;
import dev.alaindustrial.registry.ContentManifest.BlockDef;
import dev.alaindustrial.registry.ContentManifest.BlockEntityDef;
import dev.alaindustrial.registry.ContentManifest.Domain;
import dev.alaindustrial.registry.ContentManifest.ItemDef;
import dev.alaindustrial.registry.ContentManifest.MenuDef;
import dev.alaindustrial.registry.CreativeTabContent.Sink;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

// size-justified: a declarative table under the §10 norm of 600 lines per domain — the manifest entries
// (134 items) and the four tab runs coding.md §1 files here (crafting components, materials, vanilla
// Ingredients, ores). Item construction longer than one line lives in the item's own class (MOD-711).
/**
 * The Materials domain of the content manifest (MOD-711; coding.md §1, owner decision D5). Ores, dusts, ingots,
 * plates and alloys, and the crafting components machines are built from: circuits, coils, chokes, casings,
 * gears, bearings and the parts that wear out.
 *
 * <p>Declared here and registered through {@link dev.alaindustrial.registry.ContentManifest}, which joins the
 * ten domains in the fixed order of {@code ContentManifest.DOMAINS}; both loaders replay the joined lists. A new
 * entry of this domain is written here and nowhere else: a block between {@code beginBlocks()} and
 * {@link #BLOCKS}, an item, block entity or menu at the end of its list. This file declares no other domain's
 * entries and reads nothing of {@code ContentManifest} but its records
 * ({@code ArchitectureRules.contentDomainsInitialiseOnTheirOwn}): its static initialiser runs on its own.
 */
public final class MaterialsContent {
	private MaterialsContent() {
	}

	static {
		beginBlocks();
	}

	// Ores: plain Block, harvest tier is tag-driven.
	public static final BlockDef<Block> TIN_ORE = block("tin_ore", Block::new,
			machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE)), s -> ModContent.TIN_ORE = s);
	public static final BlockDef<Block> DEEPSLATE_TIN_ORE = block("deepslate_tin_ore", Block::new,
			machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)), s -> ModContent.DEEPSLATE_TIN_ORE = s);
	public static final BlockDef<Block> SILVER_ORE = block("silver_ore", Block::new,
			machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE)), s -> ModContent.SILVER_ORE = s);
	public static final BlockDef<Block> DEEPSLATE_SILVER_ORE = block("deepslate_silver_ore", Block::new,
			machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)), s -> ModContent.DEEPSLATE_SILVER_ORE = s);
	public static final BlockDef<Block> NICKEL_ORE = block("nickel_ore", Block::new,
			machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE)), s -> ModContent.NICKEL_ORE = s);
	public static final BlockDef<Block> DEEPSLATE_NICKEL_ORE = block("deepslate_nickel_ore", Block::new,
			machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)), s -> ModContent.DEEPSLATE_NICKEL_ORE = s);
	public static final BlockDef<Block> SULFUR_ORE = block("sulfur_ore", Block::new,
			machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE)), s -> ModContent.SULFUR_ORE = s);
	public static final BlockDef<Block> DEEPSLATE_SULFUR_ORE = block("deepslate_sulfur_ore", Block::new,
			machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)), s -> ModContent.DEEPSLATE_SULFUR_ORE = s);
	public static final BlockDef<Block> URANIUM_ORE = block("uranium_ore", Block::new,
			machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE)), s -> ModContent.URANIUM_ORE = s);
	public static final BlockDef<Block> DEEPSLATE_URANIUM_ORE = block("deepslate_uranium_ore", Block::new,
			machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)), s -> ModContent.DEEPSLATE_URANIUM_ORE = s);
	// MOD-423 — the only Nether ore, hence the only one WITHOUT a deepslate twin: the host rock
	// there is netherrack/basalt/blackstone, and no deepslate strata exist to carry a second variant.
	public static final BlockDef<Block> PALLADIUM_ORE = block("palladium_ore", Block::new,
			// MOD-423/MOD-511 — Nether ore. The two halves of strength() are set from different
			// arguments and must not be read as one "toughness" number.
			// destroyTime 4.5 — same as the deepslate variants: a diamond pickaxe clears a vein in
			// seconds. Ancient debris' 30.0 is deliberately NOT copied; digging speed is not the point.
			// explosionResistance 1200.0 — copied from ancient debris exactly (MOD-511). Palladium is
			// the mod's only Nether ore and sits in the layer where creepers, ghasts, beds and respawn
			// anchors go off by accident; at the old 3.0 a stray blast erased the vein along with the
			// netherrack around it. ServerExplosion drains (resistance + 0.3) * 0.3 per 0.3-block step,
			// so 1200.0 burns ~360 power per step and stops every vanilla blast (TNT 4, creeper 3/6,
			// ghast 1, bed/anchor 5, wither skull 1, wither spawn 7) at the first block it touches.
			// This buys immunity to EXPLOSIONS only: a wither's body-charge destruction is gated by
			// #minecraft:wither_immune, not by resistance, so it still breaks palladium — exactly as
			// it breaks ancient debris. Behavioural parity with debris is the goal, not invulnerability.
			machine(p -> p.strength(4.5f, 1200.0f).sound(SoundType.NETHER_ORE)), s -> ModContent.PALLADIUM_ORE = s);
	// MOD-225 machine casing (crafting base) + MOD-292 MV casing + two decorative plate blocks.
	public static final BlockDef<Block> MACHINE_CASING = block("machine_casing", Block::new,
			// MOD-225: machine casing (crafting base for machines) + two decorative plate blocks.
			machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.MACHINE_CASING = s);
	public static final BlockDef<Block> ADVANCED_MACHINE_CASING = block("advanced_machine_casing", Block::new,
			// MOD-292: MV casing — tougher than the LV one, it is the tier-up part.
			machine(p -> p.strength(6.0f, 8.0f).sound(SoundType.METAL)), s -> ModContent.ADVANCED_MACHINE_CASING = s);
	public static final BlockDef<Block> SLAG_BLOCK = block("slag_block", Block::new,
			machine(p -> p.strength(2.5f, 6.0f).sound(SoundType.STONE)), s -> ModContent.SLAG_BLOCK = s);
	// MOD-590 — carbon ceramic: the coal sink, and the refractory the heated machines stand on.
	public static final BlockDef<Block> CARBON_CERAMIC = block("carbon_ceramic", Block::new,
			// MOD-590 — blast resistance 30, the same number the reactor shell carries, because this is
			// the wall a player builds that room out of. Five times stone (6) and nowhere near obsidian
			// (1200): the block is bulk-craftable, so it must not be the end of blast-proofing.
			machine(p -> p.strength(4.0f, 30.0f).sound(SoundType.CALCITE)), s -> ModContent.CARBON_CERAMIC = s);

	/** This domain's blocks, in declaration order — collected since {@code beginBlocks()} above. */
	private static final List<BlockDef<?>> BLOCKS = endBlocks();

	private static final List<ItemDef> ITEMS = List.of(
			// Crafting components (referenced by MaceratorBlockEntity recipes and crafting recipes).
			plain("electronic_circuit", s -> ModContent.ELECTRONIC_CIRCUIT = s),
			// MOD-299 — the MV circuit: electronic circuit + gold plates + rubber. Gates the advanced casing.
			plain("advanced_circuit", s -> ModContent.ADVANCED_CIRCUIT = s),
			item("assembly_blueprint", p -> new AssemblyBlueprintItem(
					p.stacksTo(AssemblyBlueprintItem.BLANK_STACK_SIZE)), s -> ModContent.ASSEMBLY_BLUEPRINT = s),
			// Copper Coil — crafting component (copper cable + tin), gates the Electric Drill.
			plain("copper_coil", s -> ModContent.COPPER_COIL = s),
			// Resonance chain (MOD-116): spatial stock -> the coil above the copper one -> the station's chip.
			plain("spatial_crystal", s -> ModContent.SPATIAL_CRYSTAL = s),
			plain("resonance_coil", s -> ModContent.RESONANCE_COIL = s),
			// Choke (MOD-589): three tiers of the same part, plain -> reinforced -> advanced, the naming
			// ladder the windmill rotor and the water-mill wheel already use.
			plain("choke", s -> ModContent.CHOKE = s),
			plain("choke_reinforced", s -> ModContent.CHOKE_REINFORCED = s),
			plain("choke_advanced", s -> ModContent.CHOKE_ADVANCED = s),
			plain("irradiated_slag", s -> ModContent.IRRADIATED_SLAG = s),
			plain("irradiated_diamond", s -> ModContent.IRRADIATED_DIAMOND = s),
			plain("resonant_shard", s -> ModContent.RESONANT_SHARD = s),
			// MOD-480: four amethyst shards alloyed around a silver core — the crystal the monitoring
			// wall is built from. Alloyed rather than crafted: the price of the wall is a running
			// machine, not a pattern on a bench.
			plain("reinforced_amethyst", s -> ModContent.REINFORCED_AMETHYST = s),
			plain("mutagen_dust", s -> ModContent.MUTAGEN_DUST = s),
			// Oil → rubber chain: the polymerizer's product and the vulcanizer's cured output.
			plain("raw_rubber", s -> ModContent.RAW_RUBBER = s),
			// Organic chain (MOD-146): the fermenter's solid leftover, stock for a later task.
			plain("biomass", s -> ModContent.BIOMASS = s),
			plain("rubber", s -> ModContent.RUBBER = s),
			// Fluxweave chain (MOD-127): silver-plated fibre, then the woven sheet. Both are plain crafting
			// components — the EU buffer lives on the armor, not on the material.
			plain("flux_thread", s -> ModContent.FLUX_THREAD = s),
			plain("fluxweave_cloth", s -> ModContent.FLUXWEAVE_CLOTH = s),
			plain("unstable_isotope", s -> ModContent.UNSTABLE_ISOTOPE = s),
			// Energy clots (MOD-393): surplus grid power packed into an item by the energy condenser.
			hint("energy_clot_i", s -> ModContent.ENERGY_CLOT_I = s),
			hint("energy_clot_ii", s -> ModContent.ENERGY_CLOT_II = s),
			hint("energy_clot_iii", s -> ModContent.ENERGY_CLOT_III = s),
			// Rotor / wheel (MOD-189): durability components — wear shows as a vanilla durability bar and, being
			// damageable, they are automatically non-stackable. maxDamage from Config (registration-time).
			durableComponent("windmill_rotor", () -> GeneratorConfig.windMillRotorMaxDamage,
					s -> ModContent.WINDMILL_ROTOR = s),
			durableComponent("water_mill_wheel", () -> GeneratorConfig.waterMillWheelMaxDamage,
					s -> ModContent.WATER_MILL_WHEEL = s),
			// MOD-385: upper grades — richer craft, higher output, longer life. See core.machine.ComponentTier.
			durableComponent("windmill_rotor_reinforced", () -> GeneratorConfig.windMillRotorReinforcedMaxDamage,
					s -> ModContent.WINDMILL_ROTOR_REINFORCED = s),
			durableComponent("windmill_rotor_advanced", () -> GeneratorConfig.windMillRotorAdvancedMaxDamage,
					s -> ModContent.WINDMILL_ROTOR_ADVANCED = s),
			// MOD-386: the lightning rod's conductor tips.
			durableComponent("lightning_rod_conductor_tip", () -> GeneratorConfig.lightningRodTipMaxDamage,
					s -> ModContent.LIGHTNING_ROD_CONDUCTOR_TIP = s),
			durableComponent("lightning_rod_conductor_tip_reinforced",
					() -> GeneratorConfig.lightningRodTipReinforcedMaxDamage,
					s -> ModContent.LIGHTNING_ROD_CONDUCTOR_TIP_REINFORCED = s),
			durableComponent("lightning_rod_conductor_tip_advanced",
					() -> GeneratorConfig.lightningRodTipAdvancedMaxDamage,
					s -> ModContent.LIGHTNING_ROD_CONDUCTOR_TIP_ADVANCED = s),
			durableComponent("water_mill_wheel_reinforced", () -> GeneratorConfig.waterMillWheelReinforcedMaxDamage,
					s -> ModContent.WATER_MILL_WHEEL_REINFORCED = s),
			durableComponent("water_mill_wheel_advanced", () -> GeneratorConfig.waterMillWheelAdvancedMaxDamage,
					s -> ModContent.WATER_MILL_WHEEL_ADVANCED = s),
			plain("wooden_gear", s -> ModContent.WOODEN_GEAR = s),
			// Metal gears (MOD-105): crafting components for machinery still to come.
			plain("stone_gear", s -> ModContent.STONE_GEAR = s),
			plain("iron_gear", s -> ModContent.IRON_GEAR = s),
			plain("gold_gear", s -> ModContent.GOLD_GEAR = s),
			plain("silver_gear", s -> ModContent.SILVER_GEAR = s),
			// MOD-534: electrum's first use as a gear — the netherite tip's head drive.
			plain("electrum_gear", s -> ModContent.ELECTRUM_GEAR = s),
			// MOD-467: bearings. Deliberately built out of parts the mod barely used — the stone gear had
			// no consumer recipe at all, and neither did the nickel and reinforced-bronze plates.
			plain("basic_bearing", s -> ModContent.BASIC_BEARING = s),
			plain("reinforced_bearing", s -> ModContent.REINFORCED_BEARING = s),
			// MOD-534: the assembled drill bit and the smithing template that gates it.
			plain("netherite_drill_head", s -> ModContent.NETHERITE_DRILL_HEAD = s),
			// MOD-482: the column bore. Same reasoning as the bearings above — the barrel is built from
			// reinforced invar plate and palladium, neither of which had a consumer recipe at all.
			plain("core_barrel", s -> ModContent.CORE_BARREL = s),
			plain("drill_column_module", s -> ModContent.DRILL_COLUMN_MODULE = s),
			// Netherite Drill Upgrade smithing template (MOD-534): the gate on the top drill tier.
			item("netherite_drill_upgrade_smithing_template", NetheriteDrillUpgradeTemplate::create,
					s -> ModContent.NETHERITE_DRILL_UPGRADE_SMITHING_TEMPLATE = s),
			plain("tempered_iron", s -> ModContent.TEMPERED_IRON = s),
			plain("iron_dust", s -> ModContent.IRON_DUST = s),
			plain("copper_dust", s -> ModContent.COPPER_DUST = s),
			plain("gold_dust", s -> ModContent.GOLD_DUST = s),
			plain("coal_dust", s -> ModContent.COAL_DUST = s),
			plain("diamond_dust", s -> ModContent.DIAMOND_DUST = s),
			plain("emerald_dust", s -> ModContent.EMERALD_DUST = s),
			plain("empty_can", s -> ModContent.EMPTY_CAN = s),
			// Canned Ration (MOD-383): the mod's first edible item; its food numbers are declared in CannedRation.
			item("canned_ration", CannedRation::create, s -> ModContent.CANNED_RATION = s),
			plain("lapis_dust", s -> ModContent.LAPIS_DUST = s),
			plain("tin_dust", s -> ModContent.TIN_DUST = s),
			plain("raw_tin", s -> ModContent.RAW_TIN = s),
			plain("tin_ingot", s -> ModContent.TIN_INGOT = s),
			plain("silver_dust", s -> ModContent.SILVER_DUST = s),
			plain("raw_silver", s -> ModContent.RAW_SILVER = s),
			plain("silver_ingot", s -> ModContent.SILVER_INGOT = s),
			plain("nickel_dust", s -> ModContent.NICKEL_DUST = s),
			plain("raw_nickel", s -> ModContent.RAW_NICKEL = s),
			plain("nickel_ingot", s -> ModContent.NICKEL_INGOT = s),
			// MOD-064 alloys.
			plain("bronze_ingot", s -> ModContent.BRONZE_INGOT = s),
			plain("invar_ingot", s -> ModContent.INVAR_INGOT = s),
			plain("cupronickel_ingot", s -> ModContent.CUPRONICKEL_INGOT = s),
			plain("electrum_ingot", s -> ModContent.ELECTRUM_INGOT = s),
			// MOD-534: the alloy smelter's endgame product, built on a vanilla netherite ingot.
			plain("netherite_alloy_ingot", s -> ModContent.NETHERITE_ALLOY_INGOT = s),
			plain("sulfur_dust", s -> ModContent.SULFUR_DUST = s),
			plain("raw_sulfur", s -> ModContent.RAW_SULFUR = s),
			plain("uranium_dust", s -> ModContent.URANIUM_DUST = s),
			plain("raw_uranium", s -> ModContent.RAW_URANIUM = s),
			plain("uranium_ingot", s -> ModContent.URANIUM_INGOT = s),
			// MOD-424: the centrifuge's product, and what smelting it yields.
			plain("uranium_shavings", s -> ModContent.URANIUM_SHAVINGS = s),
			// MOD-424: the centrifuge's product and what smelting it yields.
			plain("refined_uranium", s -> ModContent.REFINED_URANIUM = s),
			// MOD-468, stage 1 — the shielding chain and the controller's parts.
			plain("shielding_alloy_ingot", s -> ModContent.SHIELDING_ALLOY_INGOT = s),
			plain("shielding_alloy_plate", s -> ModContent.SHIELDING_ALLOY_PLATE = s),
			plain("shielding_alloy_reinforced_plate", s -> ModContent.SHIELDING_ALLOY_REINFORCED_PLATE = s),
			plain("reactor_circuit", s -> ModContent.REACTOR_CIRCUIT = s),
			plain("control_rod_drive", s -> ModContent.CONTROL_ROD_DRIVE = s),
			plain("palladium_dust", s -> ModContent.PALLADIUM_DUST = s),
			plain("raw_palladium", s -> ModContent.RAW_PALLADIUM = s),
			plain("palladium_ingot", s -> ModContent.PALLADIUM_INGOT = s),
			// Energy Pack (MOD-065): worn LV buffer + the inert battery cell it is crafted from.
			item("battery", p -> new BatteryItem(p.stacksTo(BatteryItem.MAX_STACK)), s -> ModContent.BATTERY = s),
			// EU crystals (MOD-504). Two items per tier: the blank carries the buffer and is stacksTo(1)
			// (energy moved into a stack must divide by count, and at these buffer sizes any stack would
			// start rounding EU away); the finished crystal is an ordinary crafting material that stacks
			// normally, because it holds no energy at all.
			// Written out id by id rather than looped over CrystalTier.values(): every gate that knows
			// which items exist (arch_check, loader_parity_check, graph_data) reads the id LITERALS of
			// this list. A loop registers correctly at runtime and is invisible to all three, which
			// would leave the looped ids unguarded — and the six crystals out of the item catalogue.
			// EU crystals (MOD-504): a chargeable blank per tier, and the finished crystal it becomes at 100 %.
			// Only the blanks have an EU buffer; the finished three are ordinary crafting materials.
			item("energy_crystal_blank", p -> new CrystalBlankItem(p.stacksTo(1), CrystalTier.ENERGY),
					s -> ModContent.ENERGY_CRYSTAL_BLANK = s),
			item("energy_crystal", Item::new, s -> ModContent.ENERGY_CRYSTAL = s),
			item("lapotron_crystal_blank", p -> new CrystalBlankItem(p.stacksTo(1), CrystalTier.LAPOTRON),
					s -> ModContent.LAPOTRON_CRYSTAL_BLANK = s),
			item("lapotron_crystal", Item::new, s -> ModContent.LAPOTRON_CRYSTAL = s),
			item("resonant_crystal_blank", p -> new CrystalBlankItem(p.stacksTo(1), CrystalTier.RESONANT),
					s -> ModContent.RESONANT_CRYSTAL_BLANK = s),
			item("resonant_crystal", Item::new, s -> ModContent.RESONANT_CRYSTAL = s),
			// Metal plates (MOD-078): plain ingredient items, ingot form. Made by the Forge Hammer (by hand)
			// or the Compressor; recycled back to dust by the Macerator (except tempered_iron — no dust).
			plain("copper_plate", s -> ModContent.COPPER_PLATE = s),
			plain("gold_plate", s -> ModContent.GOLD_PLATE = s),
			plain("iron_plate", s -> ModContent.IRON_PLATE = s),
			plain("tin_plate", s -> ModContent.TIN_PLATE = s),
			plain("silver_plate", s -> ModContent.SILVER_PLATE = s),
			plain("nickel_plate", s -> ModContent.NICKEL_PLATE = s),
			plain("uranium_plate", s -> ModContent.URANIUM_PLATE = s),
			plain("palladium_plate", s -> ModContent.PALLADIUM_PLATE = s),
			plain("tempered_iron_plate", s -> ModContent.TEMPERED_IRON_PLATE = s),
			// Alloy plates + reinforced tier (MOD-460): same hammer/compressor path, no dust to recycle to.
			plain("bronze_plate", s -> ModContent.BRONZE_PLATE = s),
			plain("invar_plate", s -> ModContent.INVAR_PLATE = s),
			plain("cupronickel_plate", s -> ModContent.CUPRONICKEL_PLATE = s),
			plain("electrum_plate", s -> ModContent.ELECTRUM_PLATE = s),
			plain("bronze_reinforced_plate", s -> ModContent.BRONZE_REINFORCED_PLATE = s),
			plain("invar_reinforced_plate", s -> ModContent.INVAR_REINFORCED_PLATE = s),
			plain("cupronickel_reinforced_plate", s -> ModContent.CUPRONICKEL_REINFORCED_PLATE = s),
			plain("electrum_reinforced_plate", s -> ModContent.ELECTRUM_REINFORCED_PLATE = s),
			blockItem("tin_ore", s -> ModContent.TIN_ORE_ITEM = s),
			blockItem("deepslate_tin_ore", s -> ModContent.DEEPSLATE_TIN_ORE_ITEM = s),
			blockItem("silver_ore", s -> ModContent.SILVER_ORE_ITEM = s),
			blockItem("deepslate_silver_ore", s -> ModContent.DEEPSLATE_SILVER_ORE_ITEM = s),
			blockItem("nickel_ore", s -> ModContent.NICKEL_ORE_ITEM = s),
			blockItem("deepslate_nickel_ore", s -> ModContent.DEEPSLATE_NICKEL_ORE_ITEM = s),
			blockItem("sulfur_ore", s -> ModContent.SULFUR_ORE_ITEM = s),
			blockItem("deepslate_sulfur_ore", s -> ModContent.DEEPSLATE_SULFUR_ORE_ITEM = s),
			blockItem("uranium_ore", s -> ModContent.URANIUM_ORE_ITEM = s),
			blockItem("deepslate_uranium_ore", s -> ModContent.DEEPSLATE_URANIUM_ORE_ITEM = s),
			blockItem("palladium_ore", s -> ModContent.PALLADIUM_ORE_ITEM = s),
			// MOD-225 block-items.
			blockItem("machine_casing", s -> ModContent.MACHINE_CASING_ITEM = s),
			blockItem("advanced_machine_casing", s -> ModContent.ADVANCED_MACHINE_CASING_ITEM = s),
			blockItem("slag_block", s -> ModContent.SLAG_BLOCK_ITEM = s),
			plain("slag_poor", s -> ModContent.SLAG_POOR = s),
			plain("slag", s -> ModContent.SLAG = s),
			plain("slag_rich", s -> ModContent.SLAG_RICH = s),
			plain("ash", s -> ModContent.ASH = s),
			durableComponent("recycler_blades_iron", () -> Config.recyclerBladesIronMaxDamage,
					s -> ModContent.RECYCLER_BLADES_IRON = s),
			durableComponent("recycler_blades_tempered", () -> Config.recyclerBladesTemperedMaxDamage,
					s -> ModContent.RECYCLER_BLADES_TEMPERED = s),
			durableComponent("recycler_blades_diamond", () -> Config.recyclerBladesDiamondMaxDamage,
					s -> ModContent.RECYCLER_BLADES_DIAMOND = s),
			// MOD-590 — the ceramic chain, appended at the tail like everything since MOD-403: the
			// order of this list is the registration order. Pressed briquette -> fired plate -> block.
			blockItem("carbon_ceramic", s -> ModContent.CARBON_CERAMIC_ITEM = s),
			plain("carbon_rod", s -> ModContent.CARBON_ROD = s),
			plain("carbon_rod_double", s -> ModContent.CARBON_ROD_DOUBLE = s),
			item("carbon_briquette", CarbonBriquetteItem::new, s -> ModContent.CARBON_BRIQUETTE = s),
			plain("ceramic_plate", s -> ModContent.CERAMIC_PLATE = s),
			// MOD-638 — what a shovel takes off a soot layer; the compressor presses 16 into a briquette.
			plain("soot", s -> ModContent.SOOT = s));

	private static final List<BlockEntityDef<?>> BLOCK_ENTITIES = List.of();

	private static final List<MenuDef<?>> MENUS = List.of();

	/** Everything this domain declares; {@link dev.alaindustrial.registry.ContentManifest#DOMAINS} joins it in. */
	public static final Domain DOMAIN = new Domain("Materials", BLOCKS, ITEMS, BLOCK_ENTITIES, MENUS);

	// ---- Creative tab sections (MOD-711, batch 4; ADR-041). CreativeTabContent calls them in the tab's
	// reading order. A section holds the run of the tab that coding.md §1 files under this domain, so it may
	// show a neighbour's handle where the tab has always grouped it here.

	/** 9 - the parts machines and blocks are assembled from. */
	public static void craftingComponents(Sink out) {
		show(out, ModContent.ELECTRONIC_CIRCUIT);
		// MOD-299 - the MV tier of the circuit, listed right after its LV predecessor.
		show(out, ModContent.ADVANCED_CIRCUIT);
		show(out, ModContent.ASSEMBLY_BLUEPRINT);
		show(out, ModContent.COPPER_COIL);
		// Resonance chain (MOD-116): the raw crystal, then the coil built on it. The chip they lead to
		// sits in upgrades() instead — it is applied to a block, not consumed by a machine recipe.
		show(out, ModContent.SPATIAL_CRYSTAL);
		show(out, ModContent.RESONANCE_COIL);
		// Choke (MOD-589), plain -> reinforced -> advanced.
		show(out, ModContent.CHOKE);
		show(out, ModContent.CHOKE_REINFORCED);
		show(out, ModContent.CHOKE_ADVANCED);
		// Soul Vessel (MOD-278): the Mob Repeller's upgrade currency, listed with the other parts a
		// machine is fed. Also in ingredients() — that group feeds the vanilla Ingredients tab.
		show(out, ModContent.SOUL_VESSEL);
		// Wearing parts (MOD-189/MOD-385): each line runs plain -> reinforced -> advanced.
		show(out, ModContent.WINDMILL_ROTOR);
		show(out, ModContent.WINDMILL_ROTOR_REINFORCED);
		show(out, ModContent.WINDMILL_ROTOR_ADVANCED);
		show(out, ModContent.WATER_MILL_WHEEL);
		show(out, ModContent.WATER_MILL_WHEEL_REINFORCED);
		show(out, ModContent.WATER_MILL_WHEEL_ADVANCED);
		// Gears, in tier order.
		show(out, ModContent.WOODEN_GEAR);
		show(out, ModContent.STONE_GEAR);
		show(out, ModContent.IRON_GEAR);
		show(out, ModContent.GOLD_GEAR);
		show(out, ModContent.SILVER_GEAR);
		show(out, ModContent.ELECTRUM_GEAR);
		show(out, ModContent.BASIC_BEARING);
		show(out, ModContent.REINFORCED_BEARING);
		show(out, ModContent.NETHERITE_DRILL_HEAD);
		show(out, ModContent.NETHERITE_DRILL_UPGRADE_SMITHING_TEMPLATE);
		show(out, ModContent.CORE_BARREL);
		show(out, ModContent.DRILL_COLUMN_MODULE);
		// The rubber and cloth chains, each from raw to finished.
		show(out, ModContent.RAW_RUBBER);
		show(out, ModContent.BIOMASS);
		show(out, ModContent.RUBBER);
		show(out, ModContent.COTTON_FIBER);
		show(out, ModContent.FLUX_THREAD);
		show(out, ModContent.FLUXWEAVE_CLOTH);
	}

	/**
	 * 10 - raw materials, one metal at a time: ore -> dust -> ingot -> plate (-> reinforced plate
	 * for the alloys). MOD-460: previously the dusts, the {@link #plates} block and the alloys were
	 * three separate walls with no visible link to each other — a player saw nine plates in a row
	 * with no ore/dust/ingot next to any of them. Every metal's full chain now sits together.
	 * size-justified: one tab run listed entry by entry; its order IS the content (MOD-711, batch 4).
	 */
	public static void materials(Sink out) {
		show(out, ModContent.RAW_TIN);
		show(out, ModContent.TIN_DUST);
		show(out, ModContent.TIN_INGOT);
		show(out, ModContent.TIN_PLATE);
		show(out, ModContent.RAW_SILVER);
		show(out, ModContent.SILVER_DUST);
		show(out, ModContent.SILVER_INGOT);
		show(out, ModContent.SILVER_PLATE);
		show(out, ModContent.RAW_NICKEL);
		show(out, ModContent.NICKEL_DUST);
		show(out, ModContent.NICKEL_INGOT);
		show(out, ModContent.NICKEL_PLATE);
		// Sulfur has no ingot or plate form — the chain stops at dust.
		show(out, ModContent.RAW_SULFUR);
		show(out, ModContent.SULFUR_DUST);
		// MOD-423 — the Nether metal; sits after the overworld chain it is a tier above.
		show(out, ModContent.RAW_PALLADIUM);
		show(out, ModContent.PALLADIUM_DUST);
		show(out, ModContent.PALLADIUM_INGOT);
		show(out, ModContent.PALLADIUM_PLATE);
		// MOD-468 - the shielding alloy, listed straight after the palladium half of its recipe: it is
		// the first thing palladium is actually FOR, so the chain should read as one run.
		show(out, ModContent.SHIELDING_ALLOY_INGOT);
		show(out, ModContent.SHIELDING_ALLOY_PLATE);
		show(out, ModContent.SHIELDING_ALLOY_REINFORCED_PLATE);
		// Tempered Iron is a crafted upgrade material with no ore/dust of its own.
		show(out, ModContent.TEMPERED_IRON);
		show(out, ModContent.TEMPERED_IRON_PLATE);
		// Dusts of vanilla-ingot metals: no mod ingot to pair with, so dust -> plate directly.
		show(out, ModContent.IRON_DUST);
		show(out, ModContent.IRON_PLATE);
		show(out, ModContent.COPPER_DUST);
		show(out, ModContent.COPPER_PLATE);
		show(out, ModContent.GOLD_DUST);
		show(out, ModContent.GOLD_PLATE);
		// Dust-only byproducts with no metallic form at all.
		show(out, ModContent.COAL_DUST);
		show(out, ModContent.DIAMOND_DUST);
		show(out, ModContent.EMERALD_DUST);
		show(out, ModContent.LAPIS_DUST);
		// Uranium's raw ore/dust/ingot live in the nuclear line below (11); only its plate is a
		// plain material here.
		show(out, ModContent.URANIUM_PLATE);
		// MOD-064 alloys: smelted from the metals above rather than mined, so they close the metal
		// list. MOD-460: each alloy's plate + reinforced plate sit right after its ingot.
		show(out, ModContent.BRONZE_INGOT);
		show(out, ModContent.BRONZE_PLATE);
		show(out, ModContent.BRONZE_REINFORCED_PLATE);
		show(out, ModContent.INVAR_INGOT);
		show(out, ModContent.INVAR_PLATE);
		show(out, ModContent.INVAR_REINFORCED_PLATE);
		show(out, ModContent.CUPRONICKEL_INGOT);
		show(out, ModContent.CUPRONICKEL_PLATE);
		show(out, ModContent.CUPRONICKEL_REINFORCED_PLATE);
		show(out, ModContent.ELECTRUM_INGOT);
		show(out, ModContent.ELECTRUM_PLATE);
		show(out, ModContent.ELECTRUM_REINFORCED_PLATE);
		// Canning line (MOD-383): the tin can and what the machine fills it with.
		show(out, ModContent.EMPTY_CAN);
		show(out, ModContent.CANNED_RATION);
	}

	/** The eight metal plates (MOD-078), in the mod's canonical metal order. */
	public static void plates(Sink out) {
		show(out, ModContent.COPPER_PLATE);
		show(out, ModContent.GOLD_PLATE);
		show(out, ModContent.IRON_PLATE);
		show(out, ModContent.TIN_PLATE);
		show(out, ModContent.SILVER_PLATE);
		show(out, ModContent.NICKEL_PLATE);
		show(out, ModContent.URANIUM_PLATE);
		show(out, ModContent.PALLADIUM_PLATE);
		show(out, ModContent.TEMPERED_IRON_PLATE);
	}

	/** Alloy plates + their reinforced tier (MOD-460), alloy by alloy. */
	public static void alloyPlates(Sink out) {
		show(out, ModContent.BRONZE_PLATE);
		show(out, ModContent.BRONZE_REINFORCED_PLATE);
		show(out, ModContent.INVAR_PLATE);
		show(out, ModContent.INVAR_REINFORCED_PLATE);
		show(out, ModContent.CUPRONICKEL_PLATE);
		show(out, ModContent.CUPRONICKEL_REINFORCED_PLATE);
		show(out, ModContent.ELECTRUM_PLATE);
		show(out, ModContent.ELECTRUM_REINFORCED_PLATE);
	}

	/**
	 * The mod's contribution to the VANILLA Ingredients tab: every ingot, raw metal, dust, plate and crafting
	 * part, in the order the tab has always shown them.
	 * size-justified: one tab run listed entry by entry; its order IS the content (MOD-711, batch 4).
	 */
	public static void vanillaIngredients(Sink out) {
		show(out, ModContent.TEMPERED_IRON);
		show(out, ModContent.TIN_INGOT);
		show(out, ModContent.SILVER_INGOT);
		show(out, ModContent.NICKEL_INGOT);
		show(out, ModContent.URANIUM_INGOT);
		show(out, ModContent.PALLADIUM_INGOT);
		show(out, ModContent.BRONZE_INGOT);
		show(out, ModContent.INVAR_INGOT);
		show(out, ModContent.CUPRONICKEL_INGOT);
		show(out, ModContent.ELECTRUM_INGOT);
		show(out, ModContent.NETHERITE_ALLOY_INGOT);
		show(out, ModContent.RAW_TIN);
		show(out, ModContent.RAW_SILVER);
		show(out, ModContent.RAW_NICKEL);
		show(out, ModContent.RAW_SULFUR);
		show(out, ModContent.RAW_URANIUM);
		show(out, ModContent.RAW_PALLADIUM);
		show(out, ModContent.IRON_DUST);
		show(out, ModContent.COPPER_DUST);
		show(out, ModContent.GOLD_DUST);
		show(out, ModContent.COAL_DUST);
		show(out, ModContent.DIAMOND_DUST);
		show(out, ModContent.EMERALD_DUST);
		show(out, ModContent.LAPIS_DUST);
		show(out, ModContent.TIN_DUST);
		show(out, ModContent.SILVER_DUST);
		show(out, ModContent.NICKEL_DUST);
		show(out, ModContent.SULFUR_DUST);
		show(out, ModContent.URANIUM_DUST);
		// MOD-424 - the centrifuge's chain, in the order it happens: dust -> shavings -> refined.
		show(out, ModContent.URANIUM_SHAVINGS);
		show(out, ModContent.REFINED_URANIUM);
		show(out, ModContent.PALLADIUM_DUST);
		show(out, ModContent.EMPTY_CAN);
		plates(out);
		alloyPlates(out);
		show(out, ModContent.ELECTRONIC_CIRCUIT);
		// MOD-299 — the MV tier of the circuit, listed right after its LV predecessor.
		show(out, ModContent.ADVANCED_CIRCUIT);
		// MOD-468 - the reactor's own control circuit, one rung above the advanced one it is built on,
		// and the rod drive that goes into the controller and the airlock.
		show(out, ModContent.REACTOR_CIRCUIT);
		show(out, ModContent.CONTROL_ROD_DRIVE);
		show(out, ModContent.ASSEMBLY_BLUEPRINT);
		show(out, ModContent.COPPER_COIL);
		show(out, ModContent.SPATIAL_CRYSTAL);
		show(out, ModContent.RESONANCE_COIL);
		// Choke (MOD-589), plain -> reinforced -> advanced.
		show(out, ModContent.CHOKE);
		show(out, ModContent.CHOKE_REINFORCED);
		show(out, ModContent.CHOKE_ADVANCED);
		show(out, ModContent.RTP_CHIP);
		show(out, ModContent.ALIGNMENT_CHIP_DAY);
		show(out, ModContent.ALIGNMENT_CHIP_NIGHT);
		show(out, ModContent.RESONANCE_CHIP);
		show(out, ModContent.EMPTY_CHIP);
		show(out, ModContent.MUTE_CHIP);
		show(out, ModContent.STATS_CHIP);
		show(out, ModContent.OVERCLOCKER_CHIP_I);
		show(out, ModContent.OVERCLOCKER_CHIP_II);
		show(out, ModContent.OVERCLOCKER_CHIP_III);
		show(out, ModContent.ENERGY_CLOT_I);
		show(out, ModContent.ENERGY_CLOT_II);
		show(out, ModContent.ENERGY_CLOT_III);
		// Soul Vessel (MOD-278): the Mob Repeller's upgrade currency — a component, not a tool.
		show(out, ModContent.SOUL_VESSEL);
		// Cable breaker (MOD-276): a cable accessory, listed with the components it is crafted from.
		show(out, ModContent.CABLE_BREAKER);
		show(out, ModContent.WINDMILL_ROTOR);
		show(out, ModContent.WINDMILL_ROTOR_REINFORCED);
		show(out, ModContent.WINDMILL_ROTOR_ADVANCED);
		show(out, ModContent.WATER_MILL_WHEEL);
		show(out, ModContent.WATER_MILL_WHEEL_REINFORCED);
		show(out, ModContent.WATER_MILL_WHEEL_ADVANCED);
		show(out, ModContent.LIGHTNING_ROD_CONDUCTOR_TIP);
		show(out, ModContent.LIGHTNING_ROD_CONDUCTOR_TIP_REINFORCED);
		show(out, ModContent.LIGHTNING_ROD_CONDUCTOR_TIP_ADVANCED);
		show(out, ModContent.WOODEN_GEAR);
		show(out, ModContent.STONE_GEAR);
		show(out, ModContent.IRON_GEAR);
		show(out, ModContent.GOLD_GEAR);
		show(out, ModContent.SILVER_GEAR);
		show(out, ModContent.ELECTRUM_GEAR);
		show(out, ModContent.BASIC_BEARING);
		show(out, ModContent.REINFORCED_BEARING);
		show(out, ModContent.NETHERITE_DRILL_HEAD);
		show(out, ModContent.NETHERITE_DRILL_UPGRADE_SMITHING_TEMPLATE);
		show(out, ModContent.CORE_BARREL);
		show(out, ModContent.DRILL_COLUMN_MODULE);
	}

	/** The mod's ores as placed blocks, overworld then deepslate per metal (vanilla Natural Blocks). */
	public static void ores(Sink out) {
		show(out, ModContent.TIN_ORE_ITEM);
		show(out, ModContent.DEEPSLATE_TIN_ORE_ITEM);
		show(out, ModContent.SILVER_ORE_ITEM);
		show(out, ModContent.DEEPSLATE_SILVER_ORE_ITEM);
		show(out, ModContent.NICKEL_ORE_ITEM);
		show(out, ModContent.DEEPSLATE_NICKEL_ORE_ITEM);
		show(out, ModContent.SULFUR_ORE_ITEM);
		show(out, ModContent.DEEPSLATE_SULFUR_ORE_ITEM);
		show(out, ModContent.URANIUM_ORE_ITEM);
		show(out, ModContent.DEEPSLATE_URANIUM_ORE_ITEM);
		show(out, ModContent.PALLADIUM_ORE_ITEM);
	}
}
