package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.ElectricDrillItem;
import dev.alaindustrial.item.EnergyPackItem;
import dev.alaindustrial.item.HintItem;
import dev.alaindustrial.item.FluidTankBlockItem;
import dev.alaindustrial.item.ModArmorMaterials;
import dev.alaindustrial.item.ModToolMaterials;
import dev.alaindustrial.item.NetworkAnalyzerItem;
import dev.alaindustrial.item.TeleporterRemoteItem;
import dev.alaindustrial.item.PouchItem;
import dev.alaindustrial.item.ScytheItem;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge item registration (MOD-022 registration-facade). Mirrors the Fabric
 * {@code dev.alaindustrial.registry.ModItems} set 1:1 (same ids): the crafting components (plain
 * {@link Item}s), the Network Analyzer, and a {@link BlockItem} for every registered block — the real
 * content, not stubs.
 *
 * <p><b>Geothermal generator and pump block items (MOD-028).</b> Their blocks now live in {@code common}
 * (see {@code ModBlocksNeoForge}), so both get a {@code BlockItem} here like every other machine.
 *
 * <p><b>Split constraint (verified 26.2 API):</b> the {@code DeferredRegister} object and its
 * {@code register(modBus)} call must live on the {@code neoforge} side. NeoForge 26.2 applies
 * {@code Item.Properties.setId} automatically: {@code registerItem(name, Function&lt;Properties, I&gt;,
 * Supplier&lt;Properties&gt;)} and {@code registerSimpleBlockItem(name, Supplier&lt;Block&gt;)} both
 * derive the id from the deferred key (verified neoforge-26.2.0.8-beta). {@code registerSimpleBlockItem}
 * also applies {@code useBlockDescriptionPrefix()}, matching the Fabric {@code blockItem(...)} helper.
 */
public final class ModItemsNeoForge {
	public static final DeferredRegister.Items ITEMS =
			DeferredRegister.createItems(Industrialization.MOD_ID);

	// --- Crafting components (plain items) ---
	public static final DeferredItem<Item> ELECTRONIC_CIRCUIT = ITEMS.registerItem("electronic_circuit", Item::new);
	// Copper Coil — crafting component (copper cable + tin), gates the Electric Drill.
	public static final DeferredItem<Item> COPPER_COIL = ITEMS.registerItem("copper_coil", Item::new);
	public static final DeferredItem<Item> ALIGNMENT_CHIP_DAY = ITEMS.registerItem("alignment_chip_day", Item::new);
	public static final DeferredItem<Item> ALIGNMENT_CHIP_NIGHT = ITEMS.registerItem("alignment_chip_night", Item::new);
	// Upgrade chips (MOD-080): empty blank + the mute upgrade. Each shows a gray hint line.
	public static final DeferredItem<Item> EMPTY_CHIP = ITEMS.registerItem("empty_chip",
			p -> new HintItem(p, "item.alaindustrial.empty_chip.hint", "item.alaindustrial.empty_chip.hint2"));
	public static final DeferredItem<Item> MUTE_CHIP = ITEMS.registerItem("mute_chip",
			p -> new HintItem(p, "item.alaindustrial.mute_chip.hint", "item.alaindustrial.mute_chip.hint2"));
	public static final DeferredItem<Item> WINDMILL_ROTOR = ITEMS.registerItem("windmill_rotor", Item::new);
	public static final DeferredItem<Item> WATER_MILL_WHEEL = ITEMS.registerItem("water_mill_wheel", Item::new);
	public static final DeferredItem<Item> WOODEN_GEAR = ITEMS.registerItem("wooden_gear", Item::new);
	// Metal gears (MOD-105): crafting components for machinery still to come.
	public static final DeferredItem<Item> STONE_GEAR = ITEMS.registerItem("stone_gear", Item::new);
	public static final DeferredItem<Item> IRON_GEAR = ITEMS.registerItem("iron_gear", Item::new);
	public static final DeferredItem<Item> GOLD_GEAR = ITEMS.registerItem("gold_gear", Item::new);
	public static final DeferredItem<Item> SILVER_GEAR = ITEMS.registerItem("silver_gear", Item::new);
	public static final DeferredItem<Item> TEMPERED_IRON = ITEMS.registerItem("tempered_iron", Item::new);
	// Tempered-iron pickaxe — first mod tool (MOD-054). MC 26.2 has no PickaxeItem class: a pickaxe is
	// a plain Item whose `minecraft:tool` component is attached via Item.Properties.pickaxe(...). The
	// third arg is a Properties-unary-op that applies the tempered-iron material (durability/speed/
	// damage/enchant). setId is applied automatically by NeoForge, matching the Fabric helper.
	public static final DeferredItem<Item> TEMPERED_IRON_PICKAXE =
			ITEMS.registerItem("tempered_iron_pickaxe", Item::new,
					p -> p.pickaxe(ModToolMaterials.TEMPERED_IRON, 1.0f, -2.8f));
	// Tempered-iron tool line (MOD-054): axe/hoe/shovel/sword. Pickaxe/sword are plain Item (their
	// 26.2 subclasses were removed). Axe/Hoe/Shovel extend their vanilla subclasses so useOn works
	// (log stripping / dirt tilling / grass path) — those ctors call props.{axe,hoe,shovel}() and
	// super() themselves, so the Properties supplier stays default (NeoForge applies setId). Args
	// mirror vanilla iron equivalents (javap-verified).
	public static final DeferredItem<Item> TEMPERED_IRON_AXE =
			ITEMS.registerItem("tempered_iron_axe",
					p -> new net.minecraft.world.item.AxeItem(ModToolMaterials.TEMPERED_IRON, 6.0f, -3.1f, p),
					Item.Properties::new);
	public static final DeferredItem<Item> TEMPERED_IRON_HOE =
			ITEMS.registerItem("tempered_iron_hoe",
					p -> new net.minecraft.world.item.HoeItem(ModToolMaterials.TEMPERED_IRON, -2.0f, -1.0f, p),
					Item.Properties::new);
	public static final DeferredItem<Item> TEMPERED_IRON_SHOVEL =
			ITEMS.registerItem("tempered_iron_shovel",
					p -> new net.minecraft.world.item.ShovelItem(ModToolMaterials.TEMPERED_IRON, 1.5f, -3.0f, p),
					Item.Properties::new);
	public static final DeferredItem<Item> TEMPERED_IRON_SWORD =
			ITEMS.registerItem("tempered_iron_sword", Item::new,
					p -> p.sword(ModToolMaterials.TEMPERED_IRON, 3.0f, -2.4f));
	// Tempered-iron armor line (MOD-056): helmet/chestplate/leggings/boots. MC 26.2 has no ArmorItem
	// class — each piece is a plain Item whose equipment properties are attached via the single
	// Item.Properties.humanoidArmor(ArmorMaterial, ArmorType) helper (javap-verified against the
	// 26.2 jar; it is how vanilla Items.IRON_HELMET is built). setId is applied automatically by
	// NeoForge, matching the Fabric helper.
	public static final DeferredItem<Item> TEMPERED_IRON_HELMET =
			ITEMS.registerItem("tempered_iron_helmet", Item::new,
					p -> p.humanoidArmor(ModArmorMaterials.TEMPERED_IRON, ArmorType.HELMET));
	public static final DeferredItem<Item> TEMPERED_IRON_CHESTPLATE =
			ITEMS.registerItem("tempered_iron_chestplate", Item::new,
					p -> p.humanoidArmor(ModArmorMaterials.TEMPERED_IRON, ArmorType.CHESTPLATE));
	public static final DeferredItem<Item> TEMPERED_IRON_LEGGINGS =
			ITEMS.registerItem("tempered_iron_leggings", Item::new,
					p -> p.humanoidArmor(ModArmorMaterials.TEMPERED_IRON, ArmorType.LEGGINGS));
	public static final DeferredItem<Item> TEMPERED_IRON_BOOTS =
			ITEMS.registerItem("tempered_iron_boots", Item::new,
					p -> p.humanoidArmor(ModArmorMaterials.TEMPERED_IRON, ArmorType.BOOTS));
	public static final DeferredItem<Item> IRON_DUST = ITEMS.registerItem("iron_dust", Item::new);
	public static final DeferredItem<Item> COPPER_DUST = ITEMS.registerItem("copper_dust", Item::new);
	public static final DeferredItem<Item> GOLD_DUST = ITEMS.registerItem("gold_dust", Item::new);
	public static final DeferredItem<Item> COAL_DUST = ITEMS.registerItem("coal_dust", Item::new);
	public static final DeferredItem<Item> DIAMOND_DUST = ITEMS.registerItem("diamond_dust", Item::new);
	public static final DeferredItem<Item> EMERALD_DUST = ITEMS.registerItem("emerald_dust", Item::new);
	public static final DeferredItem<Item> LAPIS_DUST = ITEMS.registerItem("lapis_dust", Item::new);
	public static final DeferredItem<Item> TIN_DUST = ITEMS.registerItem("tin_dust", Item::new);
	public static final DeferredItem<Item> RAW_TIN = ITEMS.registerItem("raw_tin", Item::new);
	public static final DeferredItem<Item> TIN_INGOT = ITEMS.registerItem("tin_ingot", Item::new);
	public static final DeferredItem<Item> SILVER_DUST = ITEMS.registerItem("silver_dust", Item::new);
	public static final DeferredItem<Item> RAW_SILVER = ITEMS.registerItem("raw_silver", Item::new);
	public static final DeferredItem<Item> SILVER_INGOT = ITEMS.registerItem("silver_ingot", Item::new);
	public static final DeferredItem<Item> NICKEL_DUST = ITEMS.registerItem("nickel_dust", Item::new);
	public static final DeferredItem<Item> RAW_NICKEL = ITEMS.registerItem("raw_nickel", Item::new);
	public static final DeferredItem<Item> NICKEL_INGOT = ITEMS.registerItem("nickel_ingot", Item::new);
	public static final DeferredItem<Item> URANIUM_DUST = ITEMS.registerItem("uranium_dust", Item::new);
	public static final DeferredItem<Item> RAW_URANIUM = ITEMS.registerItem("raw_uranium", Item::new);
	public static final DeferredItem<Item> URANIUM_INGOT = ITEMS.registerItem("uranium_ingot", Item::new);
	public static final DeferredItem<NetworkAnalyzerItem> NETWORK_ANALYZER =
			ITEMS.registerItem("network_analyzer", NetworkAnalyzerItem::new, p -> p.stacksTo(1));
	public static final DeferredItem<dev.alaindustrial.item.WrenchItem> WRENCH =
			ITEMS.registerItem("wrench", dev.alaindustrial.item.WrenchItem::new, p -> p.stacksTo(1));
	public static final DeferredItem<dev.alaindustrial.item.GuideBookItem> GUIDE_BOOK =
			ITEMS.registerItem("guide_book", dev.alaindustrial.item.GuideBookItem::new, p -> p.stacksTo(1));

	// Teleporter Remote (MOD-092) — hidden from the creative tab until MOD-093 (see CreativeTabContent).
	public static final DeferredItem<TeleporterRemoteItem> TELEPORTER_REMOTE =
			ITEMS.registerItem("teleporter_remote", TeleporterRemoteItem::new, p -> p.stacksTo(1));
	public static final DeferredItem<PouchItem> BATTERY_POUCH =
			ITEMS.registerItem("battery_pouch", PouchItem::new, p -> p.stacksTo(1));
	// Energy Pack (MOD-065): worn LV buffer + the inert battery cell it is crafted from. Equipment
	// properties (EQUIPPABLE + token armor attribute, no ArmorMaterial) come from the common helper,
	// so both loaders build the same item; NeoForge supplies the id from the deferred key itself.
	public static final DeferredItem<Item> BATTERY = ITEMS.registerItem("battery", Item::new);
	public static final DeferredItem<EnergyPackItem> ENERGY_PACK =
			ITEMS.registerItem("energy_pack", EnergyPackItem::new, EnergyPackItem::equipmentProperties);
	// Electric Drill (MOD-079): first powered hand tool — a diamond-tier pickaxe that runs on EU. The
	// properties (hand-built TOOL component + EU-item bar, no MAX_DAMAGE) come from the common factory,
	// so both loaders build the same item; NeoForge supplies the id from the deferred key itself.
	public static final DeferredItem<ElectricDrillItem> ELECTRIC_DRILL =
			ITEMS.registerItem("electric_drill", ElectricDrillItem::new, ElectricDrillItem::electricDrillProperties);
	// Electromagnet (MOD-132): EU item in any inventory slot that pulls loose drops toward the carrier.
	public static final DeferredItem<dev.alaindustrial.item.MagnetItem> ELECTROMAGNET =
			ITEMS.registerItem("electromagnet", dev.alaindustrial.item.MagnetItem::new, p -> p.stacksTo(1));
	// Vacuum Capsule (MOD-063): empty stacks to the vanilla default (64), filled to STACK_SIZE (16).
	public static final DeferredItem<dev.alaindustrial.item.VacuumCapsuleItem> VACUUM_CAPSULE =
			ITEMS.registerItem("vacuum_capsule", dev.alaindustrial.item.VacuumCapsuleItem::new);
	public static final DeferredItem<dev.alaindustrial.item.FilledCapsuleItem> FILLED_VACUUM_CAPSULE =
			ITEMS.registerItem("filled_vacuum_capsule", dev.alaindustrial.item.FilledCapsuleItem::new,
					// MOD-077: craftRemainder = empty capsule, so a lava capsule burnt in a vanilla furnace
					// returns an empty capsule (fuel remainder), like a lava bucket returns an empty bucket.
					// VACUUM_CAPSULE is an earlier entry in this DeferredRegister, so it is resolved by the
					// time this properties lambda runs during the item RegisterEvent.
					p -> p.stacksTo(dev.alaindustrial.item.FilledCapsuleItem.STACK_SIZE)
							.craftRemainder(VACUUM_CAPSULE.get()));
	// Stock Display Frame (MOD-066). The factory lambda runs during the ITEM RegisterEvent, by which
	// point the ENTITY_TYPE register has already fired (vanilla registry order) — so resolving the
	// entity-type holder here is safe, and never at static-init time.
	public static final DeferredItem<dev.alaindustrial.item.StockDisplayFrameItem> STOCK_DISPLAY_FRAME_ITEM =
			ITEMS.registerItem("stock_display_frame",
					p -> new dev.alaindustrial.item.StockDisplayFrameItem(
							ModEntitiesNeoForge.STOCK_DISPLAY_FRAME.get(), p),
					Item.Properties::new);

	// Scythe (MOD-068): six AOE foliage tiers. The factory builds a ScytheItem (its own AOE useOn);
	// the properties operator applies .hoe(material, ...) for the tool component + enchantability,
	// exactly like the Fabric ModItems#scythe helper. NeoForge applies setId from the deferred key.
	// The first .hoe float is per-tier attackDamage: displayed damage = 1 + attackDamage +
	// material.attackDamageBonus, so wood/stone/copper/gold (whose bonus alone renders 0) are lifted
	// to 1; iron and up keep -2.0f. Keep these values in sync with the Fabric ModItems#scythe helper.
	public static final DeferredItem<ScytheItem> SCYTHE_WOOD =
			ITEMS.registerItem("scythe_wood",
					p -> new ScytheItem(new ScytheItem.Profile(3, 2, 12), p),
					p -> p.hoe(ToolMaterial.WOOD, 0.0f, -1.0f));
	public static final DeferredItem<ScytheItem> SCYTHE_STONE =
			ITEMS.registerItem("scythe_stone",
					p -> new ScytheItem(new ScytheItem.Profile(3, 3, 18), p),
					p -> p.hoe(ToolMaterial.STONE, -1.0f, -1.0f));
	public static final DeferredItem<ScytheItem> SCYTHE_COPPER =
			ITEMS.registerItem("scythe_copper",
					p -> new ScytheItem(new ScytheItem.Profile(3, 3, 24), p),
					p -> p.hoe(ToolMaterial.COPPER, -1.0f, -1.0f));
	public static final DeferredItem<ScytheItem> SCYTHE_IRON =
			ITEMS.registerItem("scythe_iron",
					p -> new ScytheItem(new ScytheItem.Profile(5, 3, 30), p),
					p -> p.hoe(ToolMaterial.IRON, -2.0f, -1.0f));
	// Gold: iron-sized area, fragile + highly enchantable (vanilla gold philosophy). See Fabric ModItems.
	public static final DeferredItem<ScytheItem> SCYTHE_GOLD =
			ITEMS.registerItem("scythe_gold",
					p -> new ScytheItem(new ScytheItem.Profile(5, 3, 30), p),
					p -> p.hoe(ToolMaterial.GOLD, 0.0f, -1.0f));
	public static final DeferredItem<ScytheItem> SCYTHE_TEMPERED_IRON =
			ITEMS.registerItem("scythe_tempered_iron",
					p -> new ScytheItem(new ScytheItem.Profile(5, 4, 40), p),
					p -> p.hoe(ModToolMaterials.TEMPERED_IRON, -2.0f, -1.0f));
	public static final DeferredItem<ScytheItem> SCYTHE_DIAMOND =
			ITEMS.registerItem("scythe_diamond",
					p -> new ScytheItem(new ScytheItem.Profile(5, 5, 50), p),
					p -> p.hoe(ToolMaterial.DIAMOND, -2.0f, -1.0f));
	// Netherite tier is fire-resistant like all vanilla netherite gear (survives lava/fire).
	public static final DeferredItem<ScytheItem> SCYTHE_NETHERITE =
			ITEMS.registerItem("scythe_netherite",
					p -> new ScytheItem(new ScytheItem.Profile(7, 5, 70), p),
					p -> p.hoe(ToolMaterial.NETHERITE, -2.0f, -1.0f).fireResistant());

	// --- Block items ---
	public static final DeferredItem<BlockItem> GENERATOR_ITEM =
			ITEMS.registerSimpleBlockItem("generator", ModBlocksNeoForge.GENERATOR);
	public static final DeferredItem<BlockItem> SOLAR_PANEL_ITEM =
			ITEMS.registerSimpleBlockItem("solar_panel", ModBlocksNeoForge.SOLAR_PANEL);
	public static final DeferredItem<BlockItem> MOONLIT_SOLAR_PANEL_ITEM =
			ITEMS.registerSimpleBlockItem("moonlit_solar_panel", ModBlocksNeoForge.MOONLIT_SOLAR_PANEL);
	public static final DeferredItem<BlockItem> DAYLIGHT_SOLAR_PANEL_ITEM =
			ITEMS.registerSimpleBlockItem("daylight_solar_panel", ModBlocksNeoForge.DAYLIGHT_SOLAR_PANEL);
	public static final DeferredItem<BlockItem> MACERATOR_ITEM =
			ITEMS.registerSimpleBlockItem("macerator", ModBlocksNeoForge.MACERATOR);
	public static final DeferredItem<BlockItem> BATTERY_BOX_ITEM =
			ITEMS.registerSimpleBlockItem("battery_box", ModBlocksNeoForge.BATTERY_BOX);

	public static final DeferredItem<BlockItem> TELEPORTER_ITEM =
			ITEMS.registerSimpleBlockItem("teleporter", ModBlocksNeoForge.TELEPORTER);
	public static final DeferredItem<BlockItem> ELECTRIC_FURNACE_ITEM =
			ITEMS.registerSimpleBlockItem("electric_furnace", ModBlocksNeoForge.ELECTRIC_FURNACE);
	public static final DeferredItem<BlockItem> IRON_FURNACE_ITEM =
			ITEMS.registerSimpleBlockItem("iron_furnace", ModBlocksNeoForge.IRON_FURNACE);
	public static final DeferredItem<BlockItem> EXTRACTOR_ITEM =
			ITEMS.registerSimpleBlockItem("extractor", ModBlocksNeoForge.EXTRACTOR);
	public static final DeferredItem<BlockItem> COMPRESSOR_ITEM =
			ITEMS.registerSimpleBlockItem("compressor", ModBlocksNeoForge.COMPRESSOR);
	public static final DeferredItem<BlockItem> GEOTHERMAL_GENERATOR_ITEM =
			ITEMS.registerSimpleBlockItem("geothermal_generator", ModBlocksNeoForge.GEOTHERMAL_GENERATOR);
	public static final DeferredItem<BlockItem> PUMP_ITEM =
			ITEMS.registerSimpleBlockItem("pump", ModBlocksNeoForge.PUMP);
	public static final DeferredItem<FluidTankBlockItem> FLUID_TANK_ITEM =
			ITEMS.registerItem("fluid_tank",
					p -> new FluidTankBlockItem(ModBlocksNeoForge.FLUID_TANK.get(),
							p.useBlockDescriptionPrefix()));
	public static final DeferredItem<BlockItem> WATER_MILL_ITEM =
			ITEMS.registerSimpleBlockItem("water_mill", ModBlocksNeoForge.WATER_MILL);
	public static final DeferredItem<BlockItem> WIND_MILL_ITEM =
			ITEMS.registerSimpleBlockItem("wind_mill", ModBlocksNeoForge.WIND_MILL);
	public static final DeferredItem<BlockItem> HIGH_ALTITUDE_WIND_MILL_ITEM =
			ITEMS.registerSimpleBlockItem("high_altitude_wind_mill", ModBlocksNeoForge.HIGH_ALTITUDE_WIND_MILL);
	public static final DeferredItem<BlockItem> STORM_WIND_MILL_ITEM =
			ITEMS.registerSimpleBlockItem("storm_wind_mill", ModBlocksNeoForge.STORM_WIND_MILL);
	public static final DeferredItem<BlockItem> COPPER_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("copper_cable", ModBlocksNeoForge.COPPER_CABLE);
	public static final DeferredItem<BlockItem> TIN_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("tin_cable", ModBlocksNeoForge.TIN_CABLE);
	public static final DeferredItem<BlockItem> INSULATED_COPPER_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("insulated_copper_cable", ModBlocksNeoForge.INSULATED_COPPER_CABLE);
	public static final DeferredItem<BlockItem> INSULATED_TIN_CABLE_ITEM =
			ITEMS.registerSimpleBlockItem("insulated_tin_cable", ModBlocksNeoForge.INSULATED_TIN_CABLE);
	// MOD-108: not registerSimpleBlockItem — the pipe needs its own BlockItem subclass to carry a
	// tooltip (plain hint + Shift for the throughput numbers).
	public static final DeferredItem<BlockItem> ITEM_PIPE_ITEM = ITEMS.registerItem("item_pipe",
			properties -> new dev.alaindustrial.item.ItemPipeBlockItem(
					ModBlocksNeoForge.ITEM_PIPE.get(), properties.useBlockDescriptionPrefix()));
	public static final DeferredItem<BlockItem> TIN_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("tin_ore", ModBlocksNeoForge.TIN_ORE);
	public static final DeferredItem<BlockItem> DEEPSLATE_TIN_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("deepslate_tin_ore", ModBlocksNeoForge.DEEPSLATE_TIN_ORE);
	public static final DeferredItem<BlockItem> SILVER_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("silver_ore", ModBlocksNeoForge.SILVER_ORE);
	public static final DeferredItem<BlockItem> DEEPSLATE_SILVER_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("deepslate_silver_ore", ModBlocksNeoForge.DEEPSLATE_SILVER_ORE);
	public static final DeferredItem<BlockItem> NICKEL_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("nickel_ore", ModBlocksNeoForge.NICKEL_ORE);
	public static final DeferredItem<BlockItem> DEEPSLATE_NICKEL_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("deepslate_nickel_ore", ModBlocksNeoForge.DEEPSLATE_NICKEL_ORE);
	public static final DeferredItem<BlockItem> URANIUM_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("uranium_ore", ModBlocksNeoForge.URANIUM_ORE);
	public static final DeferredItem<BlockItem> DEEPSLATE_URANIUM_ORE_ITEM =
			ITEMS.registerSimpleBlockItem("deepslate_uranium_ore", ModBlocksNeoForge.DEEPSLATE_URANIUM_ORE);
	public static final DeferredItem<BlockItem> IRON_CHEST_ITEM =
			ITEMS.registerSimpleBlockItem("iron_chest", ModBlocksNeoForge.IRON_CHEST);
	// Silver Chest (MOD-087) — the tier above the iron chest: 45 slots (5×9).
	public static final DeferredItem<BlockItem> SILVER_CHEST_ITEM =
			ITEMS.registerSimpleBlockItem("silver_chest", ModBlocksNeoForge.SILVER_CHEST);
	// Gold Chest (MOD-088) — the tier above the silver chest: 54 slots (6×9).
	public static final DeferredItem<BlockItem> GOLD_CHEST_ITEM =
			ITEMS.registerSimpleBlockItem("gold_chest", ModBlocksNeoForge.GOLD_CHEST);
	public static final DeferredItem<BlockItem> TEMPERED_IRON_BLOCK_ITEM =
			ITEMS.registerSimpleBlockItem("tempered_iron_block", ModBlocksNeoForge.TEMPERED_IRON_BLOCK);
	// Enriched Uranium Torch (MOD-085): a StandingAndWallBlockItem (like vanilla Items.TORCH) — floor use
	// places the standing block, wall use the wall block. Maps to both blocks; the wall block has no item
	// of its own. registerItem (not registerSimpleBlockItem) so we control the factory; useBlockDescriptionPrefix
	// is added explicitly (registerSimpleBlockItem would have added it). Block refs resolve during the item
	// RegisterEvent (blocks already registered).
	public static final DeferredItem<BlockItem> ENRICHED_URANIUM_TORCH_ITEM =
			ITEMS.registerItem("enriched_uranium_torch",
					p -> new net.minecraft.world.item.StandingAndWallBlockItem(
							ModBlocksNeoForge.ENRICHED_URANIUM_TORCH.get(),
							ModBlocksNeoForge.ENRICHED_URANIUM_WALL_TORCH.get(),
							net.minecraft.core.Direction.DOWN, p),
					() -> new Item.Properties().useBlockDescriptionPrefix());

	private ModItemsNeoForge() {
	}

	/**
	 * Bind each item / block-item {@code DeferredItem} into the loader-neutral {@link ModContent} facade,
	 * mirroring {@code dev.alaindustrial.registry.ModItems#init()} on the Fabric side. A {@code DeferredItem}
	 * <b>is</b> a {@code Supplier} ({@code DeferredItem<T> extends DeferredHolder<Item, T> implements
	 * Supplier<T>}, verified against neoforge-26.2.0.8-beta), so it is assigned directly and resolves lazily
	 * after this register's {@code RegisterEvent} fires. Called from the {@code @Mod} constructor after
	 * {@code ITEMS.register(modBus)}.
	 */
	public static void init() {
		ModContent.ELECTRONIC_CIRCUIT = ELECTRONIC_CIRCUIT;
		ModContent.COPPER_COIL = COPPER_COIL;
		ModContent.ALIGNMENT_CHIP_DAY = ALIGNMENT_CHIP_DAY;
		ModContent.ALIGNMENT_CHIP_NIGHT = ALIGNMENT_CHIP_NIGHT;
		ModContent.EMPTY_CHIP = EMPTY_CHIP;
		ModContent.MUTE_CHIP = MUTE_CHIP;
		ModContent.WINDMILL_ROTOR = WINDMILL_ROTOR;
		ModContent.WATER_MILL_WHEEL = WATER_MILL_WHEEL;
		ModContent.WOODEN_GEAR = WOODEN_GEAR;
		ModContent.STONE_GEAR = STONE_GEAR;
		ModContent.IRON_GEAR = IRON_GEAR;
		ModContent.GOLD_GEAR = GOLD_GEAR;
		ModContent.SILVER_GEAR = SILVER_GEAR;
		ModContent.TEMPERED_IRON = TEMPERED_IRON;
		ModContent.TEMPERED_IRON_PICKAXE = TEMPERED_IRON_PICKAXE;
		ModContent.TEMPERED_IRON_AXE = TEMPERED_IRON_AXE;
		ModContent.TEMPERED_IRON_HOE = TEMPERED_IRON_HOE;
		ModContent.TEMPERED_IRON_SHOVEL = TEMPERED_IRON_SHOVEL;
		ModContent.TEMPERED_IRON_SWORD = TEMPERED_IRON_SWORD;
		ModContent.TEMPERED_IRON_HELMET = TEMPERED_IRON_HELMET;
		ModContent.TEMPERED_IRON_CHESTPLATE = TEMPERED_IRON_CHESTPLATE;
		ModContent.TEMPERED_IRON_LEGGINGS = TEMPERED_IRON_LEGGINGS;
		ModContent.TEMPERED_IRON_BOOTS = TEMPERED_IRON_BOOTS;
		ModContent.IRON_DUST = IRON_DUST;
		ModContent.COPPER_DUST = COPPER_DUST;
		ModContent.GOLD_DUST = GOLD_DUST;
		ModContent.COAL_DUST = COAL_DUST;
		ModContent.DIAMOND_DUST = DIAMOND_DUST;
		ModContent.EMERALD_DUST = EMERALD_DUST;
		ModContent.LAPIS_DUST = LAPIS_DUST;
		ModContent.TIN_DUST = TIN_DUST;
		ModContent.RAW_TIN = RAW_TIN;
		ModContent.TIN_INGOT = TIN_INGOT;
		ModContent.SILVER_DUST = SILVER_DUST;
		ModContent.RAW_SILVER = RAW_SILVER;
		ModContent.SILVER_INGOT = SILVER_INGOT;
		ModContent.NICKEL_DUST = NICKEL_DUST;
		ModContent.RAW_NICKEL = RAW_NICKEL;
		ModContent.NICKEL_INGOT = NICKEL_INGOT;
		ModContent.URANIUM_DUST = URANIUM_DUST;
		ModContent.RAW_URANIUM = RAW_URANIUM;
		ModContent.URANIUM_INGOT = URANIUM_INGOT;
		// NETWORK_ANALYZER is a DeferredItem<NetworkAnalyzerItem>; the slot is Supplier<Item>. Generics are
		// invariant, so bind via the (still-lazy) method reference — see ModBlocksNeoForge#init javadoc.
		ModContent.NETWORK_ANALYZER = NETWORK_ANALYZER::get;
		ModContent.WRENCH = WRENCH::get;
		ModContent.GUIDE_BOOK = GUIDE_BOOK::get;
		ModContent.TELEPORTER_REMOTE = TELEPORTER_REMOTE::get;
		// Same invariant-generics story as NETWORK_ANALYZER above.
		ModContent.BATTERY_POUCH = BATTERY_POUCH::get;
		ModContent.BATTERY = BATTERY::get;
		ModContent.ENERGY_PACK = ENERGY_PACK::get;
		// DeferredItem<ElectricDrillItem> into a Supplier<Item> slot — bind via ::get (invariant generics).
		ModContent.ELECTRIC_DRILL = ELECTRIC_DRILL::get;
		ModContent.ELECTROMAGNET = ELECTROMAGNET::get;
		ModContent.VACUUM_CAPSULE = VACUUM_CAPSULE::get;
		ModContent.FILLED_VACUUM_CAPSULE = FILLED_VACUUM_CAPSULE::get;
		ModContent.STOCK_DISPLAY_FRAME_ITEM = STOCK_DISPLAY_FRAME_ITEM::get;
		// DeferredItem<ScytheItem> into Supplier<Item> slots — bind via ::get (invariant generics).
		ModContent.SCYTHE_WOOD = SCYTHE_WOOD::get;
		ModContent.SCYTHE_STONE = SCYTHE_STONE::get;
		ModContent.SCYTHE_COPPER = SCYTHE_COPPER::get;
		ModContent.SCYTHE_IRON = SCYTHE_IRON::get;
		ModContent.SCYTHE_GOLD = SCYTHE_GOLD::get;
		ModContent.SCYTHE_TEMPERED_IRON = SCYTHE_TEMPERED_IRON::get;
		ModContent.SCYTHE_DIAMOND = SCYTHE_DIAMOND::get;
		ModContent.SCYTHE_NETHERITE = SCYTHE_NETHERITE::get;

		ModContent.GENERATOR_ITEM = GENERATOR_ITEM;
		ModContent.SOLAR_PANEL_ITEM = SOLAR_PANEL_ITEM;
		ModContent.MOONLIT_SOLAR_PANEL_ITEM = MOONLIT_SOLAR_PANEL_ITEM;
		ModContent.DAYLIGHT_SOLAR_PANEL_ITEM = DAYLIGHT_SOLAR_PANEL_ITEM;
		ModContent.MACERATOR_ITEM = MACERATOR_ITEM;
		ModContent.BATTERY_BOX_ITEM = BATTERY_BOX_ITEM;
		ModContent.TELEPORTER_ITEM = TELEPORTER_ITEM;
		ModContent.ELECTRIC_FURNACE_ITEM = ELECTRIC_FURNACE_ITEM;
		ModContent.IRON_FURNACE_ITEM = IRON_FURNACE_ITEM;
		ModContent.EXTRACTOR_ITEM = EXTRACTOR_ITEM;
		ModContent.COMPRESSOR_ITEM = COMPRESSOR_ITEM;
		ModContent.GEOTHERMAL_GENERATOR_ITEM = GEOTHERMAL_GENERATOR_ITEM;
		ModContent.PUMP_ITEM = PUMP_ITEM;
		ModContent.FLUID_TANK_ITEM = FLUID_TANK_ITEM::get;
		ModContent.WATER_MILL_ITEM = WATER_MILL_ITEM;
		ModContent.WIND_MILL_ITEM = WIND_MILL_ITEM;
		ModContent.HIGH_ALTITUDE_WIND_MILL_ITEM = HIGH_ALTITUDE_WIND_MILL_ITEM;
		ModContent.STORM_WIND_MILL_ITEM = STORM_WIND_MILL_ITEM;
		ModContent.COPPER_CABLE_ITEM = COPPER_CABLE_ITEM;
		ModContent.TIN_CABLE_ITEM = TIN_CABLE_ITEM;
		ModContent.INSULATED_COPPER_CABLE_ITEM = INSULATED_COPPER_CABLE_ITEM;
		ModContent.INSULATED_TIN_CABLE_ITEM = INSULATED_TIN_CABLE_ITEM;
		ModContent.ITEM_PIPE_ITEM = ITEM_PIPE_ITEM;
		ModContent.TIN_ORE_ITEM = TIN_ORE_ITEM;
		ModContent.DEEPSLATE_TIN_ORE_ITEM = DEEPSLATE_TIN_ORE_ITEM;
		ModContent.SILVER_ORE_ITEM = SILVER_ORE_ITEM;
		ModContent.DEEPSLATE_SILVER_ORE_ITEM = DEEPSLATE_SILVER_ORE_ITEM;
		ModContent.NICKEL_ORE_ITEM = NICKEL_ORE_ITEM;
		ModContent.DEEPSLATE_NICKEL_ORE_ITEM = DEEPSLATE_NICKEL_ORE_ITEM;
		ModContent.URANIUM_ORE_ITEM = URANIUM_ORE_ITEM;
		ModContent.DEEPSLATE_URANIUM_ORE_ITEM = DEEPSLATE_URANIUM_ORE_ITEM;
		ModContent.IRON_CHEST_ITEM = IRON_CHEST_ITEM;
		ModContent.SILVER_CHEST_ITEM = SILVER_CHEST_ITEM;
		ModContent.GOLD_CHEST_ITEM = GOLD_CHEST_ITEM;
		ModContent.TEMPERED_IRON_BLOCK_ITEM = TEMPERED_IRON_BLOCK_ITEM;
		ModContent.ENRICHED_URANIUM_TORCH_ITEM = ENRICHED_URANIUM_TORCH_ITEM;
	}
}
