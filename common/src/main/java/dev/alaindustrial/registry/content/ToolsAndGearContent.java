package dev.alaindustrial.registry.content;

import static dev.alaindustrial.registry.content.ContentDeclarations.armor;
import static dev.alaindustrial.registry.content.ContentDeclarations.beginBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.endBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.hint;
import static dev.alaindustrial.registry.content.ContentDeclarations.item;
import static dev.alaindustrial.registry.content.ContentDeclarations.loaderItem;
import static dev.alaindustrial.registry.content.ContentDeclarations.menu;
import static dev.alaindustrial.registry.content.ContentDeclarations.overclockerChip;
import static dev.alaindustrial.registry.content.ContentDeclarations.plain;
import static dev.alaindustrial.registry.content.ContentDeclarations.scythe;
import static dev.alaindustrial.registry.content.TabEntries.after;
import static dev.alaindustrial.registry.content.TabEntries.show;

import dev.alaindustrial.block.entity.IncubatorMode;
import dev.alaindustrial.item.energy.PouchItem;
import dev.alaindustrial.item.material.ModArmorMaterials;
import dev.alaindustrial.item.material.ModToolMaterials;
import dev.alaindustrial.item.material.TemperedIronToolStats;
import dev.alaindustrial.item.misc.GuideBookItem;
import dev.alaindustrial.item.misc.MutationChipItem;
import dev.alaindustrial.item.misc.ShieldingPouchItem;
import dev.alaindustrial.item.misc.SoulVesselItem;
import dev.alaindustrial.item.teleport.RtpChipItem;
import dev.alaindustrial.item.teleport.TeleporterRemoteItem;
import dev.alaindustrial.item.tool.ElectricBowItem;
import dev.alaindustrial.item.tool.ElectricChainsawDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricChainsawItem;
import dev.alaindustrial.item.tool.ElectricDrillDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricDrillItem;
import dev.alaindustrial.item.tool.ElectricDrillNetheriteTipItem;
import dev.alaindustrial.item.tool.ElectricHoeDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricHoeItem;
import dev.alaindustrial.item.tool.ElectricSaberItem;
import dev.alaindustrial.item.tool.ElectricShovelDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricShovelItem;
import dev.alaindustrial.item.tool.GeigerCounterItem;
import dev.alaindustrial.item.tool.MagnetItem;
import dev.alaindustrial.item.tool.MagnetTier;
import dev.alaindustrial.item.tool.NetworkAnalyzerItem;
import dev.alaindustrial.item.tool.ScytheTiers;
import dev.alaindustrial.item.tool.WindGaugeItem;
import dev.alaindustrial.item.tool.WrenchItem;
import dev.alaindustrial.item.wearable.EnergyPackItem;
import dev.alaindustrial.item.wearable.FluxweaveArmorItem;
import dev.alaindustrial.item.wearable.JetpackItem;
import dev.alaindustrial.menu.MagnetMenu;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.registry.ContentManifest.BlockDef;
import dev.alaindustrial.registry.ContentManifest.BlockEntityDef;
import dev.alaindustrial.registry.ContentManifest.Domain;
import dev.alaindustrial.registry.ContentManifest.ItemDef;
import dev.alaindustrial.registry.ContentManifest.MenuDef;
import dev.alaindustrial.registry.CreativeTabContent.AnchoredSink;
import dev.alaindustrial.registry.CreativeTabContent.Sink;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * The ToolsAndGear domain of the content manifest (MOD-711; coding.md §1, owner decision D5). What the player
 * holds or wears: hand and powered tools, armour and weapons, worn energy stores, upgrade and evolution chips.
 *
 * <p>Declared here and registered through {@link dev.alaindustrial.registry.ContentManifest}, which joins the
 * ten domains in the fixed order of {@code ContentManifest.DOMAINS}; both loaders replay the joined lists. A new
 * entry of this domain is written here and nowhere else: a block between {@code beginBlocks()} and
 * {@link #BLOCKS}, an item, block entity or menu at the end of its list. This file declares no other domain's
 * entries and reads nothing of {@code ContentManifest} but its records
 * ({@code ArchitectureRules.contentDomainsInitialiseOnTheirOwn}): its static initialiser runs on its own.
 */
public final class ToolsAndGearContent {
	private ToolsAndGearContent() {
	}

	static {
		beginBlocks();
	}

	/** This domain's blocks, in declaration order — collected since {@code beginBlocks()} above. */
	private static final List<BlockDef<?>> BLOCKS = endBlocks();

	private static final List<ItemDef> ITEMS = List.of(
			// Random Jump Chip (MOD-116): the teleporter station's one permanent upgrade. Its own class
			// rather than a hintItem because fitting it is an interaction, not just a tooltip.
			item("rtp_chip", p -> new RtpChipItem(p, "item.alaindustrial.rtp_chip.hint",
				"item.alaindustrial.rtp_chip.hint2"), s -> ModContent.RTP_CHIP = s),
			plain("alignment_chip_day", s -> ModContent.ALIGNMENT_CHIP_DAY = s),
			plain("alignment_chip_night", s -> ModContent.ALIGNMENT_CHIP_NIGHT = s),
			// MOD-602 — one chip for BOTH solar branches: the panel already knows whether it is a day
			// or a night one, so a second line of crafting would only cost the player bench space.
			plain("resonance_chip", s -> ModContent.RESONANCE_CHIP = s),
			// Upgrade chips (MOD-080): empty blank + the mute upgrade. Each shows a gray hint line.
			hint("empty_chip", s -> ModContent.EMPTY_CHIP = s),
			// Incubator (MOD-118): mode chips, by-products and the tier-1 evolution materials.
			item("mutation_chip_transform", p -> new MutationChipItem(p, IncubatorMode.TRANSFORM),
					s -> ModContent.MUTATION_CHIP_TRANSFORM = s),
			item("mutation_chip_duplicate", p -> new MutationChipItem(p, IncubatorMode.DUPLICATE),
					s -> ModContent.MUTATION_CHIP_DUPLICATE = s),
			item("mutation_chip_create", p -> new MutationChipItem(p, IncubatorMode.CREATE),
					s -> ModContent.MUTATION_CHIP_CREATE = s),
			hint("mute_chip", s -> ModContent.MUTE_CHIP = s),
			hint("stats_chip", s -> ModContent.STATS_CHIP = s),
			// Soul Vessel (MOD-278): the repeller's upgrade currency. stacksTo(1) is not a balance knob —
			// the kill counter is a stack component, and stacks with different components never merge, so a
			// stackable vessel would only ever look broken.
			// Soul Vessel (MOD-278): the Mob Repeller upgrade currency.
			item("soul_vessel", p -> new SoulVesselItem(p.stacksTo(1)), s -> ModContent.SOUL_VESSEL = s),
			// Overclocker chips (MOD-392/393): three tiers trading energy for machine speed.
			overclockerChip("overclocker_chip_i", 1, s -> ModContent.OVERCLOCKER_CHIP_I = s),
			overclockerChip("overclocker_chip_ii", 2, s -> ModContent.OVERCLOCKER_CHIP_II = s),
			overclockerChip("overclocker_chip_iii", 3, s -> ModContent.OVERCLOCKER_CHIP_III = s),
			item("tempered_iron_pickaxe", p -> new Item(p.pickaxe(ModToolMaterials.TEMPERED_IRON,
					TemperedIronToolStats.PICKAXE.attackDamage(), TemperedIronToolStats.PICKAXE.attackSpeed())),
					s -> ModContent.TEMPERED_IRON_PICKAXE = s),
			// 26.3 removed AxeItem/HoeItem/ShovelItem as well, so the whole line is a plain Item built
			// from Item.Properties. Stripping, tilling and path-making are NOT lost with the subclasses:
			// .axe()/.hoe()/.shovel() attach the BLOCK_TRANSFORMER data component (vanilla's own
			// Items.IRON_AXE and friends are declared exactly this way), which is what performs the
			// right-click conversion now.
			item("tempered_iron_axe", p -> new Item(p.axe(ModToolMaterials.TEMPERED_IRON,
					TemperedIronToolStats.AXE.attackDamage(), TemperedIronToolStats.AXE.attackSpeed())),
					s -> ModContent.TEMPERED_IRON_AXE = s),
			item("tempered_iron_hoe", p -> new Item(p.hoe(ModToolMaterials.TEMPERED_IRON,
					TemperedIronToolStats.HOE.attackDamage(), TemperedIronToolStats.HOE.attackSpeed())),
					s -> ModContent.TEMPERED_IRON_HOE = s),
			item("tempered_iron_shovel", p -> new Item(p.shovel(ModToolMaterials.TEMPERED_IRON,
					TemperedIronToolStats.SHOVEL.attackDamage(), TemperedIronToolStats.SHOVEL.attackSpeed())),
					s -> ModContent.TEMPERED_IRON_SHOVEL = s),
			item("tempered_iron_sword", p -> new Item(p.sword(ModToolMaterials.TEMPERED_IRON,
					TemperedIronToolStats.SWORD.attackDamage(), TemperedIronToolStats.SWORD.attackSpeed())),
					s -> ModContent.TEMPERED_IRON_SWORD = s),
			// Tempered-iron armor (MOD-056). MC 26.2 has no ArmorItem: each piece is a plain Item whose
			// equipment properties are attached via Item.Properties.humanoidArmor(ArmorMaterial, ArmorType).
			// That helper chains durability, attributes, enchantability, the EQUIPPABLE component (with the
			// material's asset id + equip sound) and the repair tag in one go (javap-verified).
			armor("tempered_iron_helmet", ModArmorMaterials.TEMPERED_IRON, ArmorType.HELMET,
					s -> ModContent.TEMPERED_IRON_HELMET = s),
			armor("tempered_iron_chestplate", ModArmorMaterials.TEMPERED_IRON, ArmorType.CHESTPLATE,
					s -> ModContent.TEMPERED_IRON_CHESTPLATE = s),
			armor("tempered_iron_leggings", ModArmorMaterials.TEMPERED_IRON, ArmorType.LEGGINGS,
					s -> ModContent.TEMPERED_IRON_LEGGINGS = s),
			armor("tempered_iron_boots", ModArmorMaterials.TEMPERED_IRON, ArmorType.BOOTS,
					s -> ModContent.TEMPERED_IRON_BOOTS = s),
			// Fluxweave armour (MOD-127): humanoidArmor gives it ordinary armour stats; FluxweaveArmorItem
			// layers the charge-driven worn asset and bonus attributes on top of that.
			item("fluxweave_helmet", p -> new FluxweaveArmorItem(
					FluxweaveArmorItem.equipmentProperties(p, ArmorType.HELMET), ArmorType.HELMET),
					s -> ModContent.FLUXWEAVE_HELMET = s),
			item("fluxweave_chestplate", p -> new FluxweaveArmorItem(
					FluxweaveArmorItem.equipmentProperties(p, ArmorType.CHESTPLATE), ArmorType.CHESTPLATE),
					s -> ModContent.FLUXWEAVE_CHESTPLATE = s),
			item("fluxweave_leggings", p -> new FluxweaveArmorItem(
					FluxweaveArmorItem.equipmentProperties(p, ArmorType.LEGGINGS), ArmorType.LEGGINGS),
					s -> ModContent.FLUXWEAVE_LEGGINGS = s),
			item("fluxweave_boots", p -> new FluxweaveArmorItem(
					FluxweaveArmorItem.equipmentProperties(p, ArmorType.BOOTS), ArmorType.BOOTS),
					s -> ModContent.FLUXWEAVE_BOOTS = s),
			// Shielding suit (MOD-470): ordinary armour items; the shielding lives in the item tag.
			armor("shielding_helmet", ModArmorMaterials.SHIELDING, ArmorType.HELMET,
					s -> ModContent.SHIELDING_HELMET = s),
			armor("shielding_chestplate", ModArmorMaterials.SHIELDING, ArmorType.CHESTPLATE,
					s -> ModContent.SHIELDING_CHESTPLATE = s),
			armor("shielding_leggings", ModArmorMaterials.SHIELDING, ArmorType.LEGGINGS,
					s -> ModContent.SHIELDING_LEGGINGS = s),
			armor("shielding_boots", ModArmorMaterials.SHIELDING, ArmorType.BOOTS, s -> ModContent.SHIELDING_BOOTS = s),
			// Insulated set (MOD-466): ordinary armour items; the insulation lives in the item tag.
			armor("insulated_helmet", ModArmorMaterials.INSULATED, ArmorType.HELMET,
					s -> ModContent.INSULATED_HELMET = s),
			armor("insulated_chestplate", ModArmorMaterials.INSULATED, ArmorType.CHESTPLATE,
					s -> ModContent.INSULATED_CHESTPLATE = s),
			armor("insulated_leggings", ModArmorMaterials.INSULATED, ArmorType.LEGGINGS,
					s -> ModContent.INSULATED_LEGGINGS = s),
			armor("insulated_boots", ModArmorMaterials.INSULATED, ArmorType.BOOTS, s -> ModContent.INSULATED_BOOTS = s),
			item("network_analyzer", p -> new NetworkAnalyzerItem(p.stacksTo(1)), s -> ModContent.NETWORK_ANALYZER = s),
			item("wind_gauge", p -> new WindGaugeItem(p.stacksTo(1)), s -> ModContent.WIND_GAUGE = s),
			item("geiger_counter", p -> new GeigerCounterItem(p.stacksTo(1)),
					s -> ModContent.GEIGER_COUNTER = s),
			item("wrench", p -> new WrenchItem(p.stacksTo(1)), s -> ModContent.WRENCH = s),
			item("guide_book", p -> new GuideBookItem(p.stacksTo(1)), s -> ModContent.GUIDE_BOOK = s),
			// Teleporter Remote (MOD-092): registered but kept out of the creative tab + no recipe until
			// MOD-093 finishes the feature (same treatment as the station — see CreativeTabContent).
			item("teleporter_remote", p -> new TeleporterRemoteItem(p.stacksTo(1)),
					s -> ModContent.TELEPORTER_REMOTE = s),
			item("battery_pouch", p -> new PouchItem(p.stacksTo(1)), s -> ModContent.BATTERY_POUCH = s),
			// Shielding Pouch (MOD-545): the same pouch handling with no electricity, and the one
			// carried container the radiation sweep skips — see RadiationSources.countTagged.
			item("shielding_pouch", p -> new ShieldingPouchItem(p.stacksTo(1)),
					s -> ModContent.SHIELDING_POUCH = s),
			item("energy_pack", p -> new EnergyPackItem(EnergyPackItem.equipmentProperties(p)),
					s -> ModContent.ENERGY_PACK = s),
			// Electric Drill (MOD-079): first powered hand tool — a diamond-tier pickaxe that runs on EU.
			item("electric_drill", p -> new ElectricDrillItem(ElectricDrillItem.electricDrillProperties(p)),
					s -> ModContent.ELECTRIC_DRILL = s),
			// Diamond-Tipped Electric Drill (MOD-321): the drill's upgrade tier — faster, switchable Silk Touch.
			item("electric_drill_diamond_tip", p -> new ElectricDrillDiamondTipItem(
					ElectricDrillDiamondTipItem.electricDrillDiamondTipProperties(p)),
					s -> ModContent.ELECTRIC_DRILL_DIAMOND_TIP = s),
			// Netherite-Tipped Electric Drill (MOD-534): the drill's third tier — faster still, harder hitting,
			// and the one tier with a bigger EU buffer of its own.
			item("electric_drill_netherite_tip", p -> new ElectricDrillNetheriteTipItem(
					ElectricDrillNetheriteTipItem.electricDrillNetheriteTipProperties(p)),
					s -> ModContent.ELECTRIC_DRILL_NETHERITE_TIP = s),
			// Electric Chainsaw (MOD-337): the drill's wood-side counterpart — an EU axe for logs and leaves.
			item("electric_chainsaw", p -> new ElectricChainsawItem(ElectricChainsawItem.electricChainsawProperties(p)),
					s -> ModContent.ELECTRIC_CHAINSAW = s),
			// Diamond-Tipped Electric Chainsaw (MOD-374): the chainsaw's upgrade tier — faster, with a
			// switchable Silk Touch mode that drops leaves as blocks.
			item("electric_chainsaw_diamond_tip", p -> new ElectricChainsawDiamondTipItem(
					ElectricChainsawDiamondTipItem.electricChainsawDiamondTipProperties(p)),
					s -> ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP = s),
			// Electric Shovel (MOD-338): the earth-side member of the same line — an EU shovel for loose ground.
			item("electric_shovel", p -> new ElectricShovelItem(ElectricShovelItem.electricShovelProperties(p)),
					s -> ModContent.ELECTRIC_SHOVEL = s),
			// Diamond-Tipped Electric Shovel (MOD-481): the shovel's upgrade tier — faster, and its drops switch
			// between normal and Silk Touch on the fly.
			item("electric_shovel_diamond_tip", p -> new ElectricShovelDiamondTipItem(
					ElectricShovelDiamondTipItem.electricShovelDiamondTipProperties(p)),
					s -> ModContent.ELECTRIC_SHOVEL_DIAMOND_TIP = s),
			// Electric Hoe (MOD-342): the farming member of the same line — an EU hoe that tills for free.
			item("electric_hoe", p -> new ElectricHoeItem(ElectricHoeItem.electricHoeProperties(p)),
					s -> ModContent.ELECTRIC_HOE = s),
			// Diamond-Tipped Electric Hoe (MOD-378): the hoe's upgrade tier — faster, and the plots it tills
			// come out already watered.
			item("electric_hoe_diamond_tip", p -> new ElectricHoeDiamondTipItem(
					ElectricHoeDiamondTipItem.electricHoeDiamondTipProperties(p)),
					s -> ModContent.ELECTRIC_HOE_DIAMOND_TIP = s),
			// Electric Saber (MOD-149): the line's first weapon — EU per hit, plain sword when flat or off.
			item("electric_saber", p -> new ElectricSaberItem(ElectricSaberItem.electricSaberProperties(p)),
					s -> ModContent.ELECTRIC_SABER = s),
			// Electric Bow (MOD-363): the line's ranged weapon — EU per shot buys a faster draw and a faster,
			// steadier arrow; arrows are still ammunition, and a flat bow is a plain bow.
			item("electric_bow", p -> new ElectricBowItem(ElectricBowItem.electricBowProperties(p)),
					s -> ModContent.ELECTRIC_BOW = s),
			// Electromagnet (MOD-132): EU item in any inventory slot that pulls loose drops toward the carrier.
			item("electromagnet", p -> new MagnetItem(p.stacksTo(1)), s -> ModContent.ELECTROMAGNET = s),
			item("electromagnet_advanced", p -> new MagnetItem(p.stacksTo(1), MagnetTier.ADVANCED),
					s -> ModContent.ELECTROMAGNET_ADVANCED = s),
			// MOD-592: the blank every item module starts from, and the first module made from it.
			plain("module_blank", s -> ModContent.MODULE_BLANK = s),
			item("magnet_filter_module",
					p -> new dev.alaindustrial.item.tool.MagnetFilterModuleItem(p.stacksTo(1)),
					s -> ModContent.MAGNET_FILTER_MODULE = s),
			// Jetpack (MOD-148): worn EU flight — thrust on held jump, powerless glide when drained.
			item("jetpack", p -> new JetpackItem(JetpackItem.equipmentProperties(p)), s -> ModContent.JETPACK = s),
			// Scythe (MOD-068): six material tiers, each an AOE foliage clearer. Registered like a hoe
			// (.hoe(material, attackDamage, -1.0f) attaches the tool component + enchantability) but as
			// ScytheItem, not HoeItem — the scythe must not till dirt on right-click, it clears its area
			// instead. The eight tiers (material + AOE profile + attack bias) are declared once in the
			// loader-neutral dev.alaindustrial.item.tool.ScytheTiers — both loaders register from the same list,
			// so a balance tweak cannot drift between Fabric and NeoForge.
			scythe(ScytheTiers.WOOD, s -> ModContent.SCYTHE_WOOD = s),
			scythe(ScytheTiers.STONE, s -> ModContent.SCYTHE_STONE = s),
			scythe(ScytheTiers.COPPER, s -> ModContent.SCYTHE_COPPER = s),
			scythe(ScytheTiers.IRON, s -> ModContent.SCYTHE_IRON = s),
			scythe(ScytheTiers.GOLD, s -> ModContent.SCYTHE_GOLD = s),
			scythe(ScytheTiers.TEMPERED_IRON, s -> ModContent.SCYTHE_TEMPERED_IRON = s),
			scythe(ScytheTiers.DIAMOND, s -> ModContent.SCYTHE_DIAMOND = s),
			scythe(ScytheTiers.NETHERITE, s -> ModContent.SCYTHE_NETHERITE = s),
			// Forge Hammer (MOD-078): pre-machine hand tool — ingot + hammer on the grid → plate; the hammer
			// stays and loses 1 durability per plate. The craft-remainder hook has a different signature on
			// each loader, so the CLASS is loader-supplied (HammerItemFabric / HammerItemNeoForge) while the
			// durability and the anvil repair stay shared, in HammerItem#hammerProperties.
			loaderItem("forge_hammer", s -> ModContent.FORGE_HAMMER = s));

	private static final List<BlockEntityDef<?>> BLOCK_ENTITIES = List.of();

	private static final List<MenuDef<?>> MENUS = List.of(
			menu("teleporter_remote", TeleporterRemoteMenu::new, s -> ModContent.TELEPORTER_REMOTE_MENU = s),
			// MOD-592 — the electromagnet's own screen, opened from the hand: module slots + filter cells.
			menu("magnet", MagnetMenu::new,
					s -> ModContent.MAGNET_MENU = s));

	/** Everything this domain declares; {@link dev.alaindustrial.registry.ContentManifest#DOMAINS} joins it in. */
	public static final Domain DOMAIN = new Domain("ToolsAndGear", BLOCKS, ITEMS, BLOCK_ENTITIES, MENUS);

	// ---- Creative tab sections (MOD-711, batch 4; ADR-041). CreativeTabContent calls them in the tab's
	// reading order. A section holds the run of the tab that coding.md §1 files under this domain, so it may
	// show a neighbour's handle where the tab has always grouped it here.

	/** 7a - hand tools and instruments: everything useful with no charge in it. */
	public static void handTools(Sink out) {
		show(out, ModContent.WRENCH);
		show(out, ModContent.FORGE_HAMMER);
		show(out, ModContent.NETWORK_ANALYZER);
		show(out, ModContent.WIND_GAUGE);
		show(out, ModContent.GEIGER_COUNTER);
		show(out, ModContent.TELEPORTER_REMOTE);
		show(out, ModContent.GUIDE_BOOK);
	}

	/** 7b - powered gear: the electric tools, then what carries charge for them. */
	public static void poweredGear(Sink out) {
		show(out, ModContent.ELECTRIC_DRILL);
		show(out, ModContent.ELECTRIC_DRILL_DIAMOND_TIP);
		show(out, ModContent.ELECTRIC_DRILL_NETHERITE_TIP);
		show(out, ModContent.ELECTRIC_CHAINSAW);
		show(out, ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP);
		show(out, ModContent.ELECTRIC_SHOVEL);
		show(out, ModContent.ELECTRIC_SHOVEL_DIAMOND_TIP);
		show(out, ModContent.ELECTRIC_HOE);
		show(out, ModContent.ELECTRIC_HOE_DIAMOND_TIP);
		show(out, ModContent.ELECTRIC_SABER);
		show(out, ModContent.ELECTRIC_BOW);
		show(out, ModContent.ELECTROMAGNET);
		show(out, ModContent.ELECTROMAGNET_ADVANCED);
		show(out, ModContent.MODULE_BLANK);
		show(out, ModContent.MAGNET_FILTER_MODULE);
		show(out, ModContent.JETPACK);
		// Charge carriers last: they exist to feed everything above.
		show(out, ModContent.BATTERY);
		show(out, ModContent.BATTERY_POUCH);
		// MOD-545 — the lead-lined tier of the pouch above, kept beside it.
		show(out, ModContent.SHIELDING_POUCH);
		show(out, ModContent.ENERGY_PACK);
		// Crystals after the pack, each blank next to what it becomes.
		show(out, ModContent.ENERGY_CRYSTAL_BLANK);
		show(out, ModContent.ENERGY_CRYSTAL);
		show(out, ModContent.LAPOTRON_CRYSTAL_BLANK);
		show(out, ModContent.LAPOTRON_CRYSTAL);
		show(out, ModContent.RESONANT_CRYSTAL_BLANK);
		show(out, ModContent.RESONANT_CRYSTAL);
		// MOD-480: the crystal the monitoring wall is built from — beside the crystals it sits with.
		show(out, ModContent.REINFORCED_AMETHYST);
	}

	/** 8 - what goes into a machine's upgrade panel, and the chips that evolve a generator. */
	public static void upgrades(Sink out) {
		show(out, ModContent.OVERCLOCKER_CHIP_I);
		show(out, ModContent.OVERCLOCKER_CHIP_II);
		show(out, ModContent.OVERCLOCKER_CHIP_III);
		show(out, ModContent.ENERGY_CLOT_I);
		show(out, ModContent.ENERGY_CLOT_II);
		show(out, ModContent.ENERGY_CLOT_III);
		show(out, ModContent.MUTE_CHIP);
		show(out, ModContent.STATS_CHIP);
		// Evolution chips: not upgrade-panel parts, but the same "a chip you apply to a block" idea.
		show(out, ModContent.EMPTY_CHIP);
		show(out, ModContent.ALIGNMENT_CHIP_DAY);
		show(out, ModContent.ALIGNMENT_CHIP_NIGHT);
		// MOD-602: the second-tier evolution chip belongs beside the two that precede it — it was in
		// the flat list from the start, but not in this group, which is where a player looking for a
		// chip actually looks.
		show(out, ModContent.RESONANCE_CHIP);
		show(out, ModContent.MUTATION_CHIP_TRANSFORM);
		show(out, ModContent.MUTATION_CHIP_DUPLICATE);
		show(out, ModContent.MUTATION_CHIP_CREATE);
		// Random Jump Chip (MOD-116): fitted to a block by hand like the alignment chips above, not
		// dropped into an upgrade panel — the teleporter station has none.
		show(out, ModContent.RTP_CHIP);
	}

	/** 13 - the armour and weapon lines, plain tempered iron first, then the EU set. */
	public static void wearablesAndWeapons(Sink out) {
		show(out, ModContent.TEMPERED_IRON_PICKAXE);
		show(out, ModContent.TEMPERED_IRON_AXE);
		show(out, ModContent.TEMPERED_IRON_SHOVEL);
		show(out, ModContent.TEMPERED_IRON_HOE);
		show(out, ModContent.TEMPERED_IRON_SWORD);
		// Rubber set (MOD-466) opens the armour line: it is the cheapest and the earliest of the four,
		// so the row reads in progression order rather than in the order the sets were written.
		show(out, ModContent.INSULATED_HELMET);
		show(out, ModContent.INSULATED_CHESTPLATE);
		show(out, ModContent.INSULATED_LEGGINGS);
		show(out, ModContent.INSULATED_BOOTS);
		show(out, ModContent.TEMPERED_IRON_HELMET);
		show(out, ModContent.TEMPERED_IRON_CHESTPLATE);
		show(out, ModContent.TEMPERED_IRON_LEGGINGS);
		show(out, ModContent.TEMPERED_IRON_BOOTS);
		// Fluxweave set (MOD-127) - the EU armour line, right after the plain tempered iron set.
		show(out, ModContent.FLUXWEAVE_HELMET);
		show(out, ModContent.FLUXWEAVE_CHESTPLATE);
		show(out, ModContent.FLUXWEAVE_LEGGINGS);
		show(out, ModContent.FLUXWEAVE_BOOTS);
		// Shielding suit (MOD-470) - the sealed anti-radiation set, end of the armour line.
		show(out, ModContent.SHIELDING_HELMET);
		show(out, ModContent.SHIELDING_CHESTPLATE);
		show(out, ModContent.SHIELDING_LEGGINGS);
		show(out, ModContent.SHIELDING_BOOTS);
		// The scythes are a weapon line of their own, six tiers as one continuous row.
		scythes(out);
	}

	/** The six scythe tiers (MOD-068), wood → netherite, as one continuous row. */
	public static void scythes(Sink out) {
		show(out, ModContent.SCYTHE_WOOD);
		show(out, ModContent.SCYTHE_STONE);
		show(out, ModContent.SCYTHE_COPPER);
		show(out, ModContent.SCYTHE_IRON);
		show(out, ModContent.SCYTHE_GOLD);
		show(out, ModContent.SCYTHE_TEMPERED_IRON);
		show(out, ModContent.SCYTHE_DIAMOND);
		show(out, ModContent.SCYTHE_NETHERITE);
	}

	/**
	 * The mod's contribution to the VANILLA Combat tab (MOD-478 → MOD-555): the tempered-iron weapon and
	 * armour line, each piece beside the vanilla iron piece it upgrades.
	 *
	 * <p>Order is load-bearing between statements, not only within them: an entry anchored on one of the
	 * mod's own items can only be placed once that item is in the tab. Anchors on vanilla items are
	 * independent of each other, so their statements may be in any order — the loaders used to write these
	 * two lists in different orders for that reason, and produced the same tab.
	 */
	public static void vanillaCombat(AnchoredSink out) {
		after(out, Items.IRON_SWORD, ModContent.TEMPERED_IRON_SWORD);
		after(out, Items.IRON_BOOTS, ModContent.TEMPERED_IRON_HELMET, ModContent.TEMPERED_IRON_CHESTPLATE,
				ModContent.TEMPERED_IRON_LEGGINGS, ModContent.TEMPERED_IRON_BOOTS);
		// The Energy Pack is worn in the chest slot, so a player looking for chest gear finds it here too —
		// it also sits with the other powered items under Tools & Utilities below.
		after(out, ModContent.TEMPERED_IRON_BOOTS, ModContent.ENERGY_PACK);
		// The Jetpack is chest gear too (MOD-148) — it sits right after the pack here.
		after(out, ModContent.ENERGY_PACK, ModContent.JETPACK);
	}

	/**
	 * The mod's contribution to the VANILLA Tools &amp; Utilities tab (MOD-478 → MOD-555): each scythe
	 * beside the vanilla hoe tier it matches, the tempered-iron tool set between iron and gold, and the
	 * powered gear appended at the end.
	 *
	 * <p>The powered tools are appended rather than anchored on purpose — there is no vanilla tool they
	 * upgrade, so there is nothing to stand beside. This list is exactly what MOD-478 restored on NeoForge
	 * after two releases in which the base chainsaw, shovel and hoe reached this tab on Fabric only.
	 */
	public static void vanillaToolsAndUtilities(AnchoredSink out) {
		// Each scythe sits right after the matching vanilla hoe tier. The iron scythe follows the vanilla
		// iron hoe; the tempered-iron scythe follows the mod's own tempered-iron hoe, placed just below.
		after(out, Items.IRON_HOE, ModContent.SCYTHE_IRON);
		// The tempered-iron tool set is the mod's tier between iron and gold, so it follows the iron scythe
		// right after the vanilla iron hoe.
		after(out, ModContent.SCYTHE_IRON, ModContent.TEMPERED_IRON_PICKAXE, ModContent.TEMPERED_IRON_AXE,
				ModContent.TEMPERED_IRON_SHOVEL, ModContent.TEMPERED_IRON_HOE);
		after(out, Items.WOODEN_HOE, ModContent.SCYTHE_WOOD);
		after(out, Items.STONE_HOE, ModContent.SCYTHE_STONE);
		after(out, Items.COPPER_HOE, ModContent.SCYTHE_COPPER);
		after(out, ModContent.TEMPERED_IRON_HOE, ModContent.SCYTHE_TEMPERED_IRON);
		after(out, Items.GOLDEN_HOE, ModContent.SCYTHE_GOLD);
		after(out, Items.DIAMOND_HOE, ModContent.SCYTHE_DIAMOND);
		after(out, Items.NETHERITE_HOE, ModContent.SCYTHE_NETHERITE);
		after(out, Items.COMPASS, ModContent.NETWORK_ANALYZER);
		show(out, ModContent.WRENCH);
		show(out, ModContent.BATTERY_POUCH);
		// MOD-545 — the lead-lined tier of the pouch above, kept beside it.
		show(out, ModContent.SHIELDING_POUCH);
		show(out, ModContent.ENERGY_PACK);
		show(out, ModContent.ELECTRIC_DRILL);
		show(out, ModContent.ELECTRIC_DRILL_DIAMOND_TIP);
		show(out, ModContent.ELECTRIC_DRILL_NETHERITE_TIP);
		show(out, ModContent.ELECTRIC_CHAINSAW);
		show(out, ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP);
		show(out, ModContent.ELECTRIC_SHOVEL);
		show(out, ModContent.ELECTRIC_SHOVEL_DIAMOND_TIP);
		show(out, ModContent.ELECTRIC_HOE);
		show(out, ModContent.ELECTRIC_HOE_DIAMOND_TIP);
		show(out, ModContent.ELECTROMAGNET);
		show(out, ModContent.ELECTROMAGNET_ADVANCED);
		show(out, ModContent.JETPACK);
	}
}
