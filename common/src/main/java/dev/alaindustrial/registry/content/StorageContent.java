package dev.alaindustrial.registry.content;

import static dev.alaindustrial.registry.content.ContentDeclarations.beginBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.block;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockEntity;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockItem;
import static dev.alaindustrial.registry.content.ContentDeclarations.chestBoat;
import static dev.alaindustrial.registry.content.ContentDeclarations.endBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.item;
import static dev.alaindustrial.registry.content.ContentDeclarations.machine;
import static dev.alaindustrial.registry.content.ContentDeclarations.menu;
import static dev.alaindustrial.registry.content.ContentDeclarations.registeredBlock;
import static dev.alaindustrial.registry.content.ContentDeclarations.stockDisplayFrameType;
import static dev.alaindustrial.registry.content.TabEntries.show;

import dev.alaindustrial.block.AdvancedItemPipeBlock;
import dev.alaindustrial.block.BatteryBoxBlock;
import dev.alaindustrial.block.CesuBlock;
import dev.alaindustrial.block.ChargePadBlock;
import dev.alaindustrial.block.DiamondChestBlock;
import dev.alaindustrial.block.ElectrumChestBlock;
import dev.alaindustrial.block.EnergyCondenserBlock;
import dev.alaindustrial.block.GoldChestBlock;
import dev.alaindustrial.block.IronChestBlock;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.MonitorCoreBlock;
import dev.alaindustrial.block.MonitorPanelBlock;
import dev.alaindustrial.block.ShieldingChestBlock;
import dev.alaindustrial.block.SilverChestBlock;
import dev.alaindustrial.block.SmartWireBlock;
import dev.alaindustrial.block.StorageModuleBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CesuBlockEntity;
import dev.alaindustrial.block.entity.ChargePadBlockEntity;
import dev.alaindustrial.block.entity.DiamondChestBlockEntity;
import dev.alaindustrial.block.entity.ElectrumChestBlockEntity;
import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.block.entity.MonitorCoreBlockEntity;
import dev.alaindustrial.block.entity.MonitorPanelBlockEntity;
import dev.alaindustrial.block.entity.ShieldingChestBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import dev.alaindustrial.block.entity.SmartWireBlockEntity;
import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.item.misc.ItemPipeBlockItem;
import dev.alaindustrial.item.misc.StockDisplayFrameItem;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.menu.CesuMenu;
import dev.alaindustrial.menu.ChargePadMenu;
import dev.alaindustrial.menu.DiamondChestMenu;
import dev.alaindustrial.menu.DoubleChestMenu;
import dev.alaindustrial.menu.ElectrumChestMenu;
import dev.alaindustrial.menu.EnergyCondenserMenu;
import dev.alaindustrial.menu.GoldChestMenu;
import dev.alaindustrial.menu.IronChestMenu;
import dev.alaindustrial.menu.MonitorCoreMenu;
import dev.alaindustrial.menu.ShieldingChestMenu;
import dev.alaindustrial.menu.SilverChestMenu;
import dev.alaindustrial.menu.StorageMenu3;
import dev.alaindustrial.menu.StorageMenu6;
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
import net.minecraft.world.level.material.MapColor;

/**
 * The Storage domain of the content manifest (MOD-711; coding.md §1, owner decision D5). Storage: energy stores
 * from the battery box up, the chests and the storage module, item pipes, and the monitoring wall that watches a
 * warehouse.
 *
 * <p>Declared here and registered through {@link dev.alaindustrial.registry.ContentManifest}, which joins the
 * ten domains in the fixed order of {@code ContentManifest.DOMAINS}; both loaders replay the joined lists. A new
 * entry of this domain is written here and nowhere else: a block between {@code beginBlocks()} and
 * {@link #BLOCKS}, an item, block entity or menu at the end of its list. This file declares no other domain's
 * entries and reads nothing of {@code ContentManifest} but its records
 * ({@code ArchitectureRules.contentDomainsInitialiseOnTheirOwn}): its static initialiser runs on its own.
 */
public final class StorageContent {
	private StorageContent() {
	}

	static {
		beginBlocks();
	}

	public static final BlockDef<ItemPipeBlock> ITEM_PIPE = block("item_pipe", ItemPipeBlock::new,
			machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()), s -> ModContent.ITEM_PIPE = s);
	public static final BlockDef<AdvancedItemPipeBlock> ITEM_PIPE_ADVANCED =
			block("item_pipe_advanced", AdvancedItemPipeBlock::new,
					machine(p -> p.strength(0.3f, 0.6f).sound(SoundType.COPPER).noOcclusion()),
					s -> ModContent.ITEM_PIPE_ADVANCED = s);
	public static final BlockDef<BatteryBoxBlock> BATTERY_BOX = block("battery_box", BatteryBoxBlock::new,
			machine(MapColor.WOOD, p -> p.strength(3.0f, 6.0f).sound(SoundType.WOOD)), s -> ModContent.BATTERY_BOX = s);
	public static final BlockDef<CesuBlock> CESU = block("cesu", CesuBlock::new,
			// Metal, and tougher than the LV box it is built from — this tier is a steel shell, not a crate.
			machine(p -> p.strength(4.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.CESU = s);
	public static final BlockDef<ChargePadBlock> CHARGE_PAD = block("charge_pad", ChargePadBlock::new,
			// noOcclusion for the same reason as the drone dock: a 4px plate would otherwise cull the
			// faces around it as if a solid cube sat there. The light is four-valued rather than lit/unlit
			// — see ChargePadState.
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::chargePadLight)), s -> ModContent.CHARGE_PAD = s);
	/** Energy condenser (MOD-393): banks grid surplus into energy clots. */
	public static final BlockDef<EnergyCondenserBlock> ENERGY_CONDENSER =
			block("energy_condenser", EnergyCondenserBlock::new,
					// Energy condenser (MOD-393): an open frame, so noOcclusion — otherwise it culls its
					// neighbours' faces as if it were solid, and you would see through the world past the orb.
					// It glows while the bank holds anything, which is also the "it is working" signal.
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
							.noOcclusion().lightLevel(ModBlockProperties::litLight)),
					s -> ModContent.ENERGY_CONDENSER = s);
	public static final BlockDef<IronChestBlock> IRON_CHEST = block("iron_chest", IronChestBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()), s -> ModContent.IRON_CHEST = s);
	// MOD-287 — modular warehouse block; several face-adjacent ones share one inventory.
	public static final BlockDef<StorageModuleBlock> STORAGE_MODULE = block("storage_module", StorageModuleBlock::new,
			// MOD-287 — plain full cube, no noOcclusion(): unlike the chests it has no 3D renderer.
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.STORAGE_MODULE = s);
	// Silver Chest (MOD-087) / Gold Chest (MOD-088) — the tiers above the iron chest. Same block stats.
	public static final BlockDef<SilverChestBlock> SILVER_CHEST = block("silver_chest", SilverChestBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()),
			s -> ModContent.SILVER_CHEST = s);
	public static final BlockDef<GoldChestBlock> GOLD_CHEST = block("gold_chest", GoldChestBlock::new,
			machine(MapColor.GOLD, p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()),
			s -> ModContent.GOLD_CHEST = s);
	// Electrum Chest (MOD-409) — the tier above gold. Same block stats; the difference is inside
	// (81 slots) and in the window (six rows + scrollbar instead of a taller panel).
	public static final BlockDef<ElectrumChestBlock> ELECTRUM_CHEST = block("electrum_chest", ElectrumChestBlock::new,
			machine(MapColor.RAW_IRON, p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()),
			s -> ModContent.ELECTRUM_CHEST = s);
	// Shielding Chest (MOD-474) — NOT a rung of the storage ladder above: it holds the same 36 slots
	// as the iron chest and is bought for what it stops, not for what it fits. It is the only place
	// radioactive material can sit without irradiating everything around it.
	public static final BlockDef<ShieldingChestBlock> SHIELDING_CHEST =
			block("shielding_chest", ShieldingChestBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()),
					s -> ModContent.SHIELDING_CHEST = s);
	public static final BlockDef<DiamondChestBlock> DIAMOND_CHEST = block("diamond_chest", DiamondChestBlock::new,
			// MOD-474 — same stats as the storage chests: the shielding is a radiation rule, not armour.
			// MOD-599: 1200 is the ancient-debris figure — an explosion ray spends its whole budget on
			// the first block, so TNT and creepers leave the chest and its contents alone. Hardness stays
			// at the chest family's 3.0 — this is a safe, not a slower block to mine.
			machine(MapColor.DIAMOND, p -> p.strength(3.0f, 1200.0f)
					.sound(SoundType.METAL).noOcclusion()), s -> ModContent.DIAMOND_CHEST = s);
	public static final BlockDef<SmartWireBlock> SMART_WIRE = block("smart_wire", SmartWireBlock::new,
			// MOD-480 — the monitoring wall. The wire is as fragile as the other conduits; the core and
			// the panels are machine casings.
			machine(MapColor.COLOR_GRAY, p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()),
			s -> ModContent.SMART_WIRE = s);
	public static final BlockDef<MonitorCoreBlock> MONITOR_CORE = block("monitor_core", MonitorCoreBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.MONITOR_CORE = s);
	public static final BlockDef<MonitorPanelBlock> MONITOR_PANEL = block("monitor_panel", MonitorPanelBlock::new,
			machine(p -> p.strength(2.0f, 4.0f).sound(SoundType.METAL).noOcclusion()),
			s -> ModContent.MONITOR_PANEL = s);

	/** This domain's blocks, in declaration order — collected since {@code beginBlocks()} above. */
	private static final List<BlockDef<?>> BLOCKS = endBlocks();

	private static final List<ItemDef> ITEMS = List.of(
			// Stock Display Frame (MOD-066). The entity type is resolved by id inside the factory, so it is
			// read when the item is built, never at class-init: Fabric registers entity types before items
			// in its entrypoint, and on NeoForge the ENTITY_TYPE RegisterEvent fires before ITEM.
			item("stock_display_frame", p -> new StockDisplayFrameItem(stockDisplayFrameType(), p),
					s -> ModContent.STOCK_DISPLAY_FRAME_ITEM = s),
			// MOD-108: its own BlockItem subclass so the pipe can carry a tooltip (plain hint + Shift for the
			// throughput numbers) — a plain blockItem() has none.
			blockItem("item_pipe", p -> new ItemPipeBlockItem(registeredBlock("item_pipe"),
					p.useBlockDescriptionPrefix()), s -> ModContent.ITEM_PIPE_ITEM = s),
			blockItem("item_pipe_advanced", p -> new ItemPipeBlockItem(registeredBlock("item_pipe_advanced"),
					p.useBlockDescriptionPrefix()), s -> ModContent.ITEM_PIPE_ADVANCED_ITEM = s),
			blockItem("battery_box", s -> ModContent.BATTERY_BOX_ITEM = s),
			blockItem("cesu", s -> ModContent.CESU_ITEM = s),
			blockItem("charge_pad", s -> ModContent.CHARGE_PAD_ITEM = s),
			blockItem("energy_condenser", s -> ModContent.ENERGY_CONDENSER_ITEM = s),
			blockItem("iron_chest", s -> ModContent.IRON_CHEST_ITEM = s),
			blockItem("storage_module", s -> ModContent.STORAGE_MODULE_ITEM = s),
			blockItem("silver_chest", s -> ModContent.SILVER_CHEST_ITEM = s),
			blockItem("gold_chest", s -> ModContent.GOLD_CHEST_ITEM = s),
			blockItem("electrum_chest", s -> ModContent.ELECTRUM_CHEST_ITEM = s),
			blockItem("shielding_chest", s -> ModContent.SHIELDING_CHEST_ITEM = s),
			blockItem("diamond_chest", s -> ModContent.DIAMOND_CHEST_ITEM = s),
			// MOD-785 — a boat carrying a mod chest, one per wood × chest pair of ChestBoatVariants.
			// BEGIN GENERATED chest boats (tools/gen_chest_boats.py)
			chestBoat("oak_iron_chest_boat", s -> ModContent.OAK_IRON_CHEST_BOAT = s),
			chestBoat("spruce_iron_chest_boat", s -> ModContent.SPRUCE_IRON_CHEST_BOAT = s),
			chestBoat("birch_iron_chest_boat", s -> ModContent.BIRCH_IRON_CHEST_BOAT = s),
			chestBoat("jungle_iron_chest_boat", s -> ModContent.JUNGLE_IRON_CHEST_BOAT = s),
			chestBoat("acacia_iron_chest_boat", s -> ModContent.ACACIA_IRON_CHEST_BOAT = s),
			chestBoat("cherry_iron_chest_boat", s -> ModContent.CHERRY_IRON_CHEST_BOAT = s),
			chestBoat("dark_oak_iron_chest_boat", s -> ModContent.DARK_OAK_IRON_CHEST_BOAT = s),
			chestBoat("pale_oak_iron_chest_boat", s -> ModContent.PALE_OAK_IRON_CHEST_BOAT = s),
			chestBoat("mangrove_iron_chest_boat", s -> ModContent.MANGROVE_IRON_CHEST_BOAT = s),
			chestBoat("poplar_iron_chest_boat", s -> ModContent.POPLAR_IRON_CHEST_BOAT = s),
			chestBoat("bamboo_iron_chest_raft", s -> ModContent.BAMBOO_IRON_CHEST_RAFT = s),
			chestBoat("oak_silver_chest_boat", s -> ModContent.OAK_SILVER_CHEST_BOAT = s),
			chestBoat("spruce_silver_chest_boat", s -> ModContent.SPRUCE_SILVER_CHEST_BOAT = s),
			chestBoat("birch_silver_chest_boat", s -> ModContent.BIRCH_SILVER_CHEST_BOAT = s),
			chestBoat("jungle_silver_chest_boat", s -> ModContent.JUNGLE_SILVER_CHEST_BOAT = s),
			chestBoat("acacia_silver_chest_boat", s -> ModContent.ACACIA_SILVER_CHEST_BOAT = s),
			chestBoat("cherry_silver_chest_boat", s -> ModContent.CHERRY_SILVER_CHEST_BOAT = s),
			chestBoat("dark_oak_silver_chest_boat", s -> ModContent.DARK_OAK_SILVER_CHEST_BOAT = s),
			chestBoat("pale_oak_silver_chest_boat", s -> ModContent.PALE_OAK_SILVER_CHEST_BOAT = s),
			chestBoat("mangrove_silver_chest_boat", s -> ModContent.MANGROVE_SILVER_CHEST_BOAT = s),
			chestBoat("poplar_silver_chest_boat", s -> ModContent.POPLAR_SILVER_CHEST_BOAT = s),
			chestBoat("bamboo_silver_chest_raft", s -> ModContent.BAMBOO_SILVER_CHEST_RAFT = s),
			chestBoat("oak_gold_chest_boat", s -> ModContent.OAK_GOLD_CHEST_BOAT = s),
			chestBoat("spruce_gold_chest_boat", s -> ModContent.SPRUCE_GOLD_CHEST_BOAT = s),
			chestBoat("birch_gold_chest_boat", s -> ModContent.BIRCH_GOLD_CHEST_BOAT = s),
			chestBoat("jungle_gold_chest_boat", s -> ModContent.JUNGLE_GOLD_CHEST_BOAT = s),
			chestBoat("acacia_gold_chest_boat", s -> ModContent.ACACIA_GOLD_CHEST_BOAT = s),
			chestBoat("cherry_gold_chest_boat", s -> ModContent.CHERRY_GOLD_CHEST_BOAT = s),
			chestBoat("dark_oak_gold_chest_boat", s -> ModContent.DARK_OAK_GOLD_CHEST_BOAT = s),
			chestBoat("pale_oak_gold_chest_boat", s -> ModContent.PALE_OAK_GOLD_CHEST_BOAT = s),
			chestBoat("mangrove_gold_chest_boat", s -> ModContent.MANGROVE_GOLD_CHEST_BOAT = s),
			chestBoat("poplar_gold_chest_boat", s -> ModContent.POPLAR_GOLD_CHEST_BOAT = s),
			chestBoat("bamboo_gold_chest_raft", s -> ModContent.BAMBOO_GOLD_CHEST_RAFT = s),
			chestBoat("oak_electrum_chest_boat", s -> ModContent.OAK_ELECTRUM_CHEST_BOAT = s),
			chestBoat("spruce_electrum_chest_boat", s -> ModContent.SPRUCE_ELECTRUM_CHEST_BOAT = s),
			chestBoat("birch_electrum_chest_boat", s -> ModContent.BIRCH_ELECTRUM_CHEST_BOAT = s),
			chestBoat("jungle_electrum_chest_boat", s -> ModContent.JUNGLE_ELECTRUM_CHEST_BOAT = s),
			chestBoat("acacia_electrum_chest_boat", s -> ModContent.ACACIA_ELECTRUM_CHEST_BOAT = s),
			chestBoat("cherry_electrum_chest_boat", s -> ModContent.CHERRY_ELECTRUM_CHEST_BOAT = s),
			chestBoat("dark_oak_electrum_chest_boat", s -> ModContent.DARK_OAK_ELECTRUM_CHEST_BOAT = s),
			chestBoat("pale_oak_electrum_chest_boat", s -> ModContent.PALE_OAK_ELECTRUM_CHEST_BOAT = s),
			chestBoat("mangrove_electrum_chest_boat", s -> ModContent.MANGROVE_ELECTRUM_CHEST_BOAT = s),
			chestBoat("poplar_electrum_chest_boat", s -> ModContent.POPLAR_ELECTRUM_CHEST_BOAT = s),
			chestBoat("bamboo_electrum_chest_raft", s -> ModContent.BAMBOO_ELECTRUM_CHEST_RAFT = s),
			chestBoat("oak_diamond_chest_boat", s -> ModContent.OAK_DIAMOND_CHEST_BOAT = s),
			chestBoat("spruce_diamond_chest_boat", s -> ModContent.SPRUCE_DIAMOND_CHEST_BOAT = s),
			chestBoat("birch_diamond_chest_boat", s -> ModContent.BIRCH_DIAMOND_CHEST_BOAT = s),
			chestBoat("jungle_diamond_chest_boat", s -> ModContent.JUNGLE_DIAMOND_CHEST_BOAT = s),
			chestBoat("acacia_diamond_chest_boat", s -> ModContent.ACACIA_DIAMOND_CHEST_BOAT = s),
			chestBoat("cherry_diamond_chest_boat", s -> ModContent.CHERRY_DIAMOND_CHEST_BOAT = s),
			chestBoat("dark_oak_diamond_chest_boat", s -> ModContent.DARK_OAK_DIAMOND_CHEST_BOAT = s),
			chestBoat("pale_oak_diamond_chest_boat", s -> ModContent.PALE_OAK_DIAMOND_CHEST_BOAT = s),
			chestBoat("mangrove_diamond_chest_boat", s -> ModContent.MANGROVE_DIAMOND_CHEST_BOAT = s),
			chestBoat("poplar_diamond_chest_boat", s -> ModContent.POPLAR_DIAMOND_CHEST_BOAT = s),
			chestBoat("bamboo_diamond_chest_raft", s -> ModContent.BAMBOO_DIAMOND_CHEST_RAFT = s),
			chestBoat("oak_shielding_chest_boat", s -> ModContent.OAK_SHIELDING_CHEST_BOAT = s),
			chestBoat("spruce_shielding_chest_boat", s -> ModContent.SPRUCE_SHIELDING_CHEST_BOAT = s),
			chestBoat("birch_shielding_chest_boat", s -> ModContent.BIRCH_SHIELDING_CHEST_BOAT = s),
			chestBoat("jungle_shielding_chest_boat", s -> ModContent.JUNGLE_SHIELDING_CHEST_BOAT = s),
			chestBoat("acacia_shielding_chest_boat", s -> ModContent.ACACIA_SHIELDING_CHEST_BOAT = s),
			chestBoat("cherry_shielding_chest_boat", s -> ModContent.CHERRY_SHIELDING_CHEST_BOAT = s),
			chestBoat("dark_oak_shielding_chest_boat", s -> ModContent.DARK_OAK_SHIELDING_CHEST_BOAT = s),
			chestBoat("pale_oak_shielding_chest_boat", s -> ModContent.PALE_OAK_SHIELDING_CHEST_BOAT = s),
			chestBoat("mangrove_shielding_chest_boat", s -> ModContent.MANGROVE_SHIELDING_CHEST_BOAT = s),
			chestBoat("poplar_shielding_chest_boat", s -> ModContent.POPLAR_SHIELDING_CHEST_BOAT = s),
			chestBoat("bamboo_shielding_chest_raft", s -> ModContent.BAMBOO_SHIELDING_CHEST_RAFT = s),
			// END GENERATED chest boats
			// MOD-480 — the monitoring wall, likewise appended at the tail.
			blockItem("smart_wire", s -> ModContent.SMART_WIRE_ITEM = s),
			blockItem("monitor_core", s -> ModContent.MONITOR_CORE_ITEM = s),
			blockItem("monitor_panel", s -> ModContent.MONITOR_PANEL_ITEM = s),
			item("capacity_card", dev.alaindustrial.item.misc.CapacityCardItem::new,
					s -> ModContent.CAPACITY_CARD = s));

	/*
	 * Block entities named by a constant because code outside the list refers to the entry itself
	 * (MOD-711): BlockCapabilityRoster.NO_ENERGY_CAPABILITY holds these definitions, so a block id can no
	 * longer be put there by mistake. Each still takes its own place in BLOCK_ENTITIES below.
	 */
	public static final BlockEntityDef<ItemPipeBlockEntity> ITEM_PIPE_BE =
			blockEntity("item_pipe", ItemPipeBlockEntity.class, ItemPipeBlockEntity::new,
					s -> ModContent.ITEM_PIPE_BE = s, ITEM_PIPE, ITEM_PIPE_ADVANCED);

	private static final List<BlockEntityDef<?>> BLOCK_ENTITIES = List.of(
			ITEM_PIPE_BE,
			blockEntity("smart_wire", SmartWireBlockEntity.class,
					SmartWireBlockEntity::new,
					s -> ModContent.SMART_WIRE_BE = s, SMART_WIRE),
			blockEntity("monitor_core", MonitorCoreBlockEntity.class,
					MonitorCoreBlockEntity::new,
					s -> ModContent.MONITOR_CORE_BE = s, MONITOR_CORE),
			blockEntity("monitor_panel", MonitorPanelBlockEntity.class,
					MonitorPanelBlockEntity::new,
					s -> ModContent.MONITOR_PANEL_BE = s, MONITOR_PANEL),
			blockEntity("battery_box", BatteryBoxBlockEntity.class, BatteryBoxBlockEntity::new,
					s -> ModContent.BATTERY_BOX_BE = s, BATTERY_BOX),
			blockEntity("cesu", CesuBlockEntity.class, CesuBlockEntity::new, s -> ModContent.CESU_BE = s, CESU),
			blockEntity("charge_pad", ChargePadBlockEntity.class, ChargePadBlockEntity::new,
					s -> ModContent.CHARGE_PAD_BE = s, CHARGE_PAD),
			blockEntity("energy_condenser", EnergyCondenserBlockEntity.class,
					EnergyCondenserBlockEntity::new, s -> ModContent.ENERGY_CONDENSER_BE = s, ENERGY_CONDENSER),
			blockEntity("iron_chest", IronChestBlockEntity.class, IronChestBlockEntity::new,
					s -> ModContent.IRON_CHEST_BE = s, IRON_CHEST),
			blockEntity("storage_module", StorageModuleBlockEntity.class, StorageModuleBlockEntity::new,
					s -> ModContent.STORAGE_MODULE_BE = s, STORAGE_MODULE),
			blockEntity("silver_chest", SilverChestBlockEntity.class, SilverChestBlockEntity::new,
					s -> ModContent.SILVER_CHEST_BE = s, SILVER_CHEST),
			blockEntity("gold_chest", GoldChestBlockEntity.class, GoldChestBlockEntity::new,
					s -> ModContent.GOLD_CHEST_BE = s, GOLD_CHEST),
			blockEntity("electrum_chest", ElectrumChestBlockEntity.class, ElectrumChestBlockEntity::new,
					s -> ModContent.ELECTRUM_CHEST_BE = s, ELECTRUM_CHEST),
			blockEntity("diamond_chest", DiamondChestBlockEntity.class, DiamondChestBlockEntity::new,
					s -> ModContent.DIAMOND_CHEST_BE = s, DIAMOND_CHEST),
			blockEntity("shielding_chest", ShieldingChestBlockEntity.class, ShieldingChestBlockEntity::new,
					s -> ModContent.SHIELDING_CHEST_BE = s, SHIELDING_CHEST));

	private static final List<MenuDef<?>> MENUS = List.of(
			menu("battery_box", BatteryBoxMenu::new, s -> ModContent.BATTERY_BOX_MENU = s),
			menu("energy_condenser", EnergyCondenserMenu::new, s -> ModContent.ENERGY_CONDENSER_MENU = s),
			menu("cesu", CesuMenu::new, s -> ModContent.CESU_MENU = s),
			// MOD-416 — the charging station's readout: the mod's only slotless machine menu.
			menu("charge_pad", ChargePadMenu::new, s -> ModContent.CHARGE_PAD_MENU = s),
			menu("iron_chest", IronChestMenu::new, s -> ModContent.IRON_CHEST_MENU = s),
			// MOD-287 — the warehouse window, one registration per height (3/6/9/12 rows).
			menu("storage_module_3", StorageMenu3::new, s -> ModContent.STORAGE_MODULE_MENU_3 = s),
			menu("storage_module_6", StorageMenu6::new, s -> ModContent.STORAGE_MODULE_MENU_6 = s),
			menu("silver_chest", SilverChestMenu::new, s -> ModContent.SILVER_CHEST_MENU = s),
			menu("gold_chest", GoldChestMenu::new, s -> ModContent.GOLD_CHEST_MENU = s),
			// MOD-409 — the electrum tier: 81 slots behind the same 6-row scrolling window, so this
			// is a single chest wearing the warehouse/double-chest machinery rather than a taller panel.
			menu("electrum_chest", ElectrumChestMenu::new, s -> ModContent.ELECTRUM_CHEST_MENU = s),
			// MOD-474 — the shielding chest's window: the iron chest's four rows, its own menu type.
			menu("diamond_chest", DiamondChestMenu::new, s -> ModContent.DIAMOND_CHEST_MENU = s),
			menu("shielding_chest", ShieldingChestMenu::new, s -> ModContent.SHIELDING_CHEST_MENU = s),
			// MOD-391 — the double chest's 6-row scrolling window, one type for every tier.
			menu("double_chest", DoubleChestMenu::new, s -> ModContent.DOUBLE_CHEST_MENU = s),
			// MOD-480: the monitoring wall's core — a rack of cards, not a machine, so its menu sits
			// on the vanilla base rather than on MachineMenu.
			menu("monitor_core", MonitorCoreMenu::new,
					s -> ModContent.MONITOR_CORE_MENU = s));

	/** Everything this domain declares; {@link dev.alaindustrial.registry.ContentManifest#DOMAINS} joins it in. */
	public static final Domain DOMAIN = new Domain("Storage", BLOCKS, ITEMS, BLOCK_ENTITIES, MENUS);

	// ---- Creative tab sections (MOD-711, batch 4; ADR-041). CreativeTabContent calls them in the tab's
	// reading order. A section holds the run of the tab that coding.md §1 files under this domain, so it may
	// show a neighbour's handle where the tab has always grouped it here.

	/** 2 - energy stores. The teleporter is here because it banks EU exactly like the box does. */
	public static void energyStorage(Sink out) {
		show(out, ModContent.BATTERY_BOX_ITEM);
		// Reinforced Energy Storage (MOD-351) - the MV step, directly after the LV box it is built from.
		show(out, ModContent.CESU_ITEM);
		// Teleporter (MOD-091/092/093): a store with one very expensive way to spend itself.
		show(out, ModContent.TELEPORTER_ITEM);
		// --- shaped: the pad is a floor plate, the condenser a lattice (MOD-574).
		// The Charging Station (MOD-274) banks EU exactly like the boxes above and exists to spend it on
		// the player, so it belongs with storage rather than among the processing machines.
		show(out, ModContent.CHARGE_PAD_ITEM);
		show(out, ModContent.ENERGY_CONDENSER_ITEM);
	}

	/**
	 * 6b - the chest boats (MOD-785): every wood with the iron chest, then the next chest up the ladder.
	 *
	 * <p>size-justified: one tab run listed entry by entry, written by tools/gen_chest_boats.py from
	 * ChestBoatVariants; its order IS the content.
	 */
	public static void chestBoats(Sink out) {
		// BEGIN GENERATED chest boats (tools/gen_chest_boats.py)
		show(out, ModContent.OAK_IRON_CHEST_BOAT);
		show(out, ModContent.SPRUCE_IRON_CHEST_BOAT);
		show(out, ModContent.BIRCH_IRON_CHEST_BOAT);
		show(out, ModContent.JUNGLE_IRON_CHEST_BOAT);
		show(out, ModContent.ACACIA_IRON_CHEST_BOAT);
		show(out, ModContent.CHERRY_IRON_CHEST_BOAT);
		show(out, ModContent.DARK_OAK_IRON_CHEST_BOAT);
		show(out, ModContent.PALE_OAK_IRON_CHEST_BOAT);
		show(out, ModContent.MANGROVE_IRON_CHEST_BOAT);
		show(out, ModContent.POPLAR_IRON_CHEST_BOAT);
		show(out, ModContent.BAMBOO_IRON_CHEST_RAFT);
		show(out, ModContent.OAK_SILVER_CHEST_BOAT);
		show(out, ModContent.SPRUCE_SILVER_CHEST_BOAT);
		show(out, ModContent.BIRCH_SILVER_CHEST_BOAT);
		show(out, ModContent.JUNGLE_SILVER_CHEST_BOAT);
		show(out, ModContent.ACACIA_SILVER_CHEST_BOAT);
		show(out, ModContent.CHERRY_SILVER_CHEST_BOAT);
		show(out, ModContent.DARK_OAK_SILVER_CHEST_BOAT);
		show(out, ModContent.PALE_OAK_SILVER_CHEST_BOAT);
		show(out, ModContent.MANGROVE_SILVER_CHEST_BOAT);
		show(out, ModContent.POPLAR_SILVER_CHEST_BOAT);
		show(out, ModContent.BAMBOO_SILVER_CHEST_RAFT);
		show(out, ModContent.OAK_GOLD_CHEST_BOAT);
		show(out, ModContent.SPRUCE_GOLD_CHEST_BOAT);
		show(out, ModContent.BIRCH_GOLD_CHEST_BOAT);
		show(out, ModContent.JUNGLE_GOLD_CHEST_BOAT);
		show(out, ModContent.ACACIA_GOLD_CHEST_BOAT);
		show(out, ModContent.CHERRY_GOLD_CHEST_BOAT);
		show(out, ModContent.DARK_OAK_GOLD_CHEST_BOAT);
		show(out, ModContent.PALE_OAK_GOLD_CHEST_BOAT);
		show(out, ModContent.MANGROVE_GOLD_CHEST_BOAT);
		show(out, ModContent.POPLAR_GOLD_CHEST_BOAT);
		show(out, ModContent.BAMBOO_GOLD_CHEST_RAFT);
		show(out, ModContent.OAK_ELECTRUM_CHEST_BOAT);
		show(out, ModContent.SPRUCE_ELECTRUM_CHEST_BOAT);
		show(out, ModContent.BIRCH_ELECTRUM_CHEST_BOAT);
		show(out, ModContent.JUNGLE_ELECTRUM_CHEST_BOAT);
		show(out, ModContent.ACACIA_ELECTRUM_CHEST_BOAT);
		show(out, ModContent.CHERRY_ELECTRUM_CHEST_BOAT);
		show(out, ModContent.DARK_OAK_ELECTRUM_CHEST_BOAT);
		show(out, ModContent.PALE_OAK_ELECTRUM_CHEST_BOAT);
		show(out, ModContent.MANGROVE_ELECTRUM_CHEST_BOAT);
		show(out, ModContent.POPLAR_ELECTRUM_CHEST_BOAT);
		show(out, ModContent.BAMBOO_ELECTRUM_CHEST_RAFT);
		show(out, ModContent.OAK_DIAMOND_CHEST_BOAT);
		show(out, ModContent.SPRUCE_DIAMOND_CHEST_BOAT);
		show(out, ModContent.BIRCH_DIAMOND_CHEST_BOAT);
		show(out, ModContent.JUNGLE_DIAMOND_CHEST_BOAT);
		show(out, ModContent.ACACIA_DIAMOND_CHEST_BOAT);
		show(out, ModContent.CHERRY_DIAMOND_CHEST_BOAT);
		show(out, ModContent.DARK_OAK_DIAMOND_CHEST_BOAT);
		show(out, ModContent.PALE_OAK_DIAMOND_CHEST_BOAT);
		show(out, ModContent.MANGROVE_DIAMOND_CHEST_BOAT);
		show(out, ModContent.POPLAR_DIAMOND_CHEST_BOAT);
		show(out, ModContent.BAMBOO_DIAMOND_CHEST_RAFT);
		show(out, ModContent.OAK_SHIELDING_CHEST_BOAT);
		show(out, ModContent.SPRUCE_SHIELDING_CHEST_BOAT);
		show(out, ModContent.BIRCH_SHIELDING_CHEST_BOAT);
		show(out, ModContent.JUNGLE_SHIELDING_CHEST_BOAT);
		show(out, ModContent.ACACIA_SHIELDING_CHEST_BOAT);
		show(out, ModContent.CHERRY_SHIELDING_CHEST_BOAT);
		show(out, ModContent.DARK_OAK_SHIELDING_CHEST_BOAT);
		show(out, ModContent.PALE_OAK_SHIELDING_CHEST_BOAT);
		show(out, ModContent.MANGROVE_SHIELDING_CHEST_BOAT);
		show(out, ModContent.POPLAR_SHIELDING_CHEST_BOAT);
		show(out, ModContent.BAMBOO_SHIELDING_CHEST_RAFT);
		// END GENERATED chest boats
	}

	/** 6 - item logistics: the pipe first, then what it moves things between. */
	public static void itemLogistics(Sink out) {
		// The item pipe itself now sits beside the fluid pipe in fluids(); this group is the containers
		// it serves. (Both groups feed the same tabs, so the pipe is still listed exactly once.)
		// The chest tiers, in upgrade order (36 -> 45 -> 54). Silver and Gold were missing here while
		// present in the Fabric list, so NeoForge players saw neither in any tab - MOD-102.
		show(out, ModContent.IRON_CHEST_ITEM);
		show(out, ModContent.SILVER_CHEST_ITEM);
		show(out, ModContent.GOLD_CHEST_ITEM);
		show(out, ModContent.ELECTRUM_CHEST_ITEM);
		// MOD-599: the diamond tier, directly after the electrum one it upgrades from.
		show(out, ModContent.DIAMOND_CHEST_ITEM);
		// MOD-474 — not a rung of the ladder above, so it sits after it: same 36 slots as the iron
		// chest, bought for the shielding.
		show(out, ModContent.SHIELDING_CHEST_ITEM);
		show(out, ModContent.STORAGE_MODULE_ITEM);
		show(out, ModContent.STOCK_DISPLAY_FRAME_ITEM);
		// MOD-480: the monitoring wall — wire reads the chests, core powers and permits, panels show.
		show(out, ModContent.MONITOR_CORE_ITEM);
		show(out, ModContent.MONITOR_PANEL_ITEM);
		show(out, ModContent.SMART_WIRE_ITEM);
		show(out, ModContent.CAPACITY_CARD);
	}
}
