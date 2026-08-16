package dev.alaindustrial.registry;

import net.minecraft.world.level.ItemLike;

/**
 * Loader-neutral source of truth for public creative-inventory visibility.
 *
 * <p>This deliberately lists only player-visible MVP content. Registered-hidden entries
 * such as non-copper cables stay out of both the mod tab and the vanilla tabs until their
 * progression path is restored. (The T2 wind mills were restored to the player in MOD-172.)
 */
public final class CreativeTabContent {
	private CreativeTabContent() {
	}

	@FunctionalInterface
	public interface Sink {
		void accept(ItemLike item);
	}

	/** Logged once per run: a broken handle would otherwise print the same line for every tab rebuild. */
	private static boolean warnedAboutMissingEntry;

	/**
	 * Show one entry, and survive it being unavailable (MOD-407).
	 *
	 * <p>Every line below reads a {@code ModContent} handle, and a handle that was never bound throws
	 * on {@code get()}. That throw happens INSIDE the creative-tab fill callback, so one bad entry took
	 * the whole tab with it — the player opened creative and found the mod's tab empty or the screen
	 * gone, with no clue which item caused it. A tab is a display; it must degrade to "one icon fewer",
	 * never to "no tab".
	 *
	 * <p>This is not a way to tolerate missing content: the completeness test in
	 * {@code CreativeTabContentTest} fails the BUILD when an item stops being listed, so a real gap is
	 * caught long before a player sees it. What this guard removes is the crash as a failure mode.
	 */
	private static void show(Sink out, java.util.function.Supplier<? extends ItemLike> handle) {
		ItemLike item;
		try {
			item = handle.get();
		} catch (RuntimeException e) {
			if (!warnedAboutMissingEntry) {
				warnedAboutMissingEntry = true;
				dev.alaindustrial.Industrialization.LOGGER.error(
						"[creative] an entry could not be resolved and was skipped; the rest of the tab is"
								+ " unaffected. Further occurrences this run are not logged.", e);
			}
			return;
		}
		if (item != null) {
			out.accept(item);
		}
	}

	/**
	 * The mod's own tab, in the order a player actually meets the mod (MOD-407).
	 *
	 * <p>Energy first and in the order it flows — where it comes FROM, where it is KEPT, how it is
	 * CARRIED, what SPENDS it — then the two logistics networks, then what the player holds, and only
	 * then the raw materials and crafting parts everything above is built from. Blocks and the armour
	 * lines close the list.
	 *
	 * <p>Before this the tab had a {@code components()} section holding seventy entries: circuits,
	 * gears, dusts, nuclear leftovers, the wrench, the chainsaw, a jetpack and three buckets of oil, in
	 * one run. "Components" had come to mean "everything else", and nothing about the order told the
	 * player where to look. Each group below answers exactly one question instead.
	 */
	public static void main(Sink out) {
		// 1 - where energy comes from.
		generators(out);
		// 2 - where it is kept (and, for the teleporter, banked to be spent in one go).
		energyStorage(out);
		// 3 - how it is carried: the conductor ladder, each grade next to its insulated form, then the
		// accessory installed on it. The breaker is an ITEM, so it is added here and not inside
		// energyTransfer() - that group also feeds vanilla Functional Blocks, which takes blocks only.
		energyTransfer(out);
		show(out, ModContent.CABLE_BREAKER);
		// 4 - what spends it: the processing line, LV first and the MV assembler last.
		machines(out);
		// 5 - the fluid chain, from the pump that fills a tank to the column that splits oil, then what
		// carries those fluids by hand.
		fluids(out);
		fluidCarriers(out);
		// 6 - item logistics: the pipe and the containers it serves.
		itemLogistics(out);
		// 7 - what the player holds: hand tools first (no charge needed), then the powered gear.
		handTools(out);
		poweredGear(out);
		// 8 - what goes INTO a machine to change how it runs.
		upgrades(out);
		// 9 - what machines and blocks are built from.
		craftingComponents(out);
		// 10 - raw materials: ore -> dust -> ingot per metal, then plates and the alloys.
		materials(out);
		// 11 - the nuclear line, kept together and away from ordinary materials.
		nuclear(out);
		// 12 - blocks: ores as placed, metal and plate blocks, the workbench, the torch.
		blocks(out);
		// 13 - the armour and weapon lines close the tab, tempered iron then Fluxweave.
		wearablesAndWeapons(out);
	}

	public static void combat(Sink out) {
		show(out, ModContent.TEMPERED_IRON_SWORD);
		show(out, ModContent.TEMPERED_IRON_HELMET);
		show(out, ModContent.TEMPERED_IRON_CHESTPLATE);
		show(out, ModContent.TEMPERED_IRON_LEGGINGS);
		show(out, ModContent.TEMPERED_IRON_BOOTS);
		// Fluxweave set (MOD-127) — the EU armour line, listed right after the plain tempered iron set.
		show(out, ModContent.FLUXWEAVE_HELMET);
		show(out, ModContent.FLUXWEAVE_CHESTPLATE);
		show(out, ModContent.FLUXWEAVE_LEGGINGS);
		show(out, ModContent.FLUXWEAVE_BOOTS);
	}

	public static void toolsAndUtilities(Sink out) {
		show(out, ModContent.TEMPERED_IRON_PICKAXE);
		show(out, ModContent.TEMPERED_IRON_AXE);
		show(out, ModContent.TEMPERED_IRON_SHOVEL);
		show(out, ModContent.TEMPERED_IRON_HOE);
		scythes(out);
		show(out, ModContent.NETWORK_ANALYZER);
		show(out, ModContent.WIND_GAUGE);
		show(out, ModContent.TELEPORTER_REMOTE);
		show(out, ModContent.WRENCH);
		show(out, ModContent.FORGE_HAMMER);
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

	/** Blocks made from plates (MOD-225): the machine casing and two decorative panels. */
	public static void plateBlocks(Sink out) {
		show(out, ModContent.MACHINE_CASING_ITEM);
		show(out, ModContent.ADVANCED_MACHINE_CASING_ITEM);
		show(out, ModContent.SILVER_PLATE_BLOCK_ITEM);
		show(out, ModContent.TEMPERED_IRON_PLATE_BLOCK_ITEM);
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

	public static void ingredients(Sink out) {
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
		show(out, ModContent.PALLADIUM_DUST);
		show(out, ModContent.EMPTY_CAN);
		plates(out);
		show(out, ModContent.ELECTRONIC_CIRCUIT);
		// MOD-299 — the MV tier of the circuit, listed right after its LV predecessor.
		show(out, ModContent.ADVANCED_CIRCUIT);
		show(out, ModContent.ASSEMBLY_BLUEPRINT);
		show(out, ModContent.COPPER_COIL);
		show(out, ModContent.ALIGNMENT_CHIP_DAY);
		show(out, ModContent.ALIGNMENT_CHIP_NIGHT);
		show(out, ModContent.EMPTY_CHIP);
		show(out, ModContent.MUTE_CHIP);
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
		show(out, ModContent.WOODEN_GEAR);
		show(out, ModContent.STONE_GEAR);
		show(out, ModContent.IRON_GEAR);
		show(out, ModContent.GOLD_GEAR);
		show(out, ModContent.SILVER_GEAR);
	}

	public static void buildingBlocks(Sink out) {
		show(out, ModContent.TEMPERED_IRON_BLOCK_ITEM);
		show(out, ModContent.MACHINE_CASING_ITEM);
		show(out, ModContent.ADVANCED_MACHINE_CASING_ITEM);
		show(out, ModContent.SILVER_PLATE_BLOCK_ITEM);
		show(out, ModContent.TEMPERED_IRON_PLATE_BLOCK_ITEM);
		show(out, ModContent.INDUSTRIAL_WORKBENCH_ITEM);
	}

	public static void naturalBlocks(Sink out) {
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
		show(out, ModContent.TRELLIS_ITEM);
		show(out, ModContent.COTTON_SEEDS);
	}

	public static void functionalBlocks(Sink out) {
		generators(out);
		energyStorage(out);
		energyTransfer(out);
		machines(out);
		fluids(out);
		itemLogistics(out);
		utility(out);
	}

	/** 2 - energy stores. The teleporter is here because it banks EU exactly like the box does. */
	private static void energyStorage(Sink out) {
		show(out, ModContent.BATTERY_BOX_ITEM);
		// Reinforced Energy Storage (MOD-351) - the MV step, directly after the LV box it is built from.
		show(out, ModContent.CESU_ITEM);
		// The Charging Station (MOD-274) banks EU exactly like the box above and exists to spend it on
		// the player, so it belongs next to storage rather than among the processing machines.
		show(out, ModContent.CHARGE_PAD_ITEM);
		show(out, ModContent.ENERGY_CONDENSER_ITEM);
		// Teleporter (MOD-091/092/093): a store with one very expensive way to spend itself.
		show(out, ModContent.TELEPORTER_ITEM);
	}

	/** 3 - the conductor ladder, each grade immediately followed by its insulated form. */
	private static void energyTransfer(Sink out) {
		show(out, ModContent.TIN_CABLE_ITEM);
		show(out, ModContent.INSULATED_TIN_CABLE_ITEM);
		show(out, ModContent.COPPER_CABLE_ITEM);
		show(out, ModContent.INSULATED_COPPER_CABLE_ITEM);
		show(out, ModContent.GOLD_CABLE_ITEM);
		show(out, ModContent.INSULATED_GOLD_CABLE_ITEM);
		show(out, ModContent.ELECTRUM_CABLE_ITEM);
		show(out, ModContent.INSULATED_ELECTRUM_CABLE_ITEM);
	}

	/** 5 - the fluid chain: source, storage, transport, then the machines that transform fluids. */
	private static void fluids(Sink out) {
		show(out, ModContent.PUMP_ITEM);
		show(out, ModContent.FLUID_TANK_ITEM);
		show(out, ModContent.FLUID_PIPE_ITEM);
		// The item pipe sits next to the fluid pipe: the two carriers are one idea, and a player looking
		// for "the pipe" should find both without scrolling to another group.
		show(out, ModContent.ITEM_PIPE_ITEM);
		// MOD-251: the distillation tower - one item, three blocks tall when placed, plus its optional
		// fourth storey (losses 10 % -> 5 %).
		show(out, ModContent.DISTILLATION_COLUMN_ITEM);
		show(out, ModContent.RECTIFICATION_SECTION_ITEM);
		show(out, ModContent.POLYMERIZER_ITEM);
		show(out, ModContent.VULCANIZER_ITEM);
		show(out, ModContent.GALVANIC_BATH_ITEM);
	}

	/**
	 * 5b - the hand-carried fluids. Split out of {@link #fluids} because that group also feeds vanilla
	 * Functional Blocks, and a bucket is not a functional block (MOD-407).
	 */
	private static void fluidCarriers(Sink out) {
		show(out, ModContent.VACUUM_CAPSULE);
		show(out, ModContent.OIL_BUCKET);
		show(out, ModContent.DIESEL_BUCKET);
		show(out, ModContent.FUEL_OIL_BUCKET);
	}

	/** 6 - item logistics: the pipe first, then what it moves things between. */
	private static void itemLogistics(Sink out) {
		// The item pipe itself now sits beside the fluid pipe in fluids(); this group is the containers
		// it serves. (Both groups feed the same tabs, so the pipe is still listed exactly once.)
		// The chest tiers, in upgrade order (36 -> 45 -> 54). Silver and Gold were missing here while
		// present in the Fabric list, so NeoForge players saw neither in any tab - MOD-102.
		show(out, ModContent.IRON_CHEST_ITEM);
		show(out, ModContent.SILVER_CHEST_ITEM);
		show(out, ModContent.GOLD_CHEST_ITEM);
		show(out, ModContent.ELECTRUM_CHEST_ITEM);
		show(out, ModContent.STORAGE_MODULE_ITEM);
		show(out, ModContent.STOCK_DISPLAY_FRAME_ITEM);
	}

	/** 7a - hand tools and instruments: everything useful with no charge in it. */
	private static void handTools(Sink out) {
		show(out, ModContent.WRENCH);
		show(out, ModContent.FORGE_HAMMER);
		show(out, ModContent.NETWORK_ANALYZER);
		show(out, ModContent.WIND_GAUGE);
		show(out, ModContent.TELEPORTER_REMOTE);
		show(out, ModContent.GUIDE_BOOK);
	}

	/** 7b - powered gear: the electric tools, then what carries charge for them. */
	private static void poweredGear(Sink out) {
		show(out, ModContent.ELECTRIC_DRILL);
		show(out, ModContent.ELECTRIC_DRILL_DIAMOND_TIP);
		show(out, ModContent.ELECTRIC_CHAINSAW);
		show(out, ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP);
		show(out, ModContent.ELECTRIC_SHOVEL);
		show(out, ModContent.ELECTRIC_HOE);
		show(out, ModContent.ELECTRIC_HOE_DIAMOND_TIP);
		show(out, ModContent.ELECTRIC_SABER);
		show(out, ModContent.ELECTROMAGNET);
		show(out, ModContent.JETPACK);
		// Charge carriers last: they exist to feed everything above.
		show(out, ModContent.BATTERY);
		show(out, ModContent.BATTERY_POUCH);
		show(out, ModContent.ENERGY_PACK);
	}

	/** 8 - what goes into a machine's upgrade panel, and the chips that evolve a generator. */
	private static void upgrades(Sink out) {
		show(out, ModContent.OVERCLOCKER_CHIP_I);
		show(out, ModContent.OVERCLOCKER_CHIP_II);
		show(out, ModContent.OVERCLOCKER_CHIP_III);
		show(out, ModContent.ENERGY_CLOT_I);
		show(out, ModContent.ENERGY_CLOT_II);
		show(out, ModContent.ENERGY_CLOT_III);
		show(out, ModContent.MUTE_CHIP);
		// Evolution chips: not upgrade-panel parts, but the same "a chip you apply to a block" idea.
		show(out, ModContent.EMPTY_CHIP);
		show(out, ModContent.ALIGNMENT_CHIP_DAY);
		show(out, ModContent.ALIGNMENT_CHIP_NIGHT);
		show(out, ModContent.MUTATION_CHIP_TRANSFORM);
		show(out, ModContent.MUTATION_CHIP_DUPLICATE);
		show(out, ModContent.MUTATION_CHIP_CREATE);
	}

	/** 9 - the parts machines and blocks are assembled from. */
	private static void craftingComponents(Sink out) {
		show(out, ModContent.ELECTRONIC_CIRCUIT);
		// MOD-299 - the MV tier of the circuit, listed right after its LV predecessor.
		show(out, ModContent.ADVANCED_CIRCUIT);
		show(out, ModContent.ASSEMBLY_BLUEPRINT);
		show(out, ModContent.COPPER_COIL);
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
		// The rubber and cloth chains, each from raw to finished.
		show(out, ModContent.RAW_RUBBER);
		show(out, ModContent.RUBBER);
		show(out, ModContent.COTTON_FIBER);
		show(out, ModContent.FLUX_THREAD);
		show(out, ModContent.FLUXWEAVE_CLOTH);
	}

	/** 10 - raw materials: ore -> dust -> ingot per metal, then the plates and the alloys. */
	private static void materials(Sink out) {
		show(out, ModContent.RAW_TIN);
		show(out, ModContent.TIN_DUST);
		show(out, ModContent.TIN_INGOT);
		show(out, ModContent.RAW_SILVER);
		show(out, ModContent.SILVER_DUST);
		show(out, ModContent.SILVER_INGOT);
		show(out, ModContent.RAW_NICKEL);
		show(out, ModContent.NICKEL_DUST);
		show(out, ModContent.NICKEL_INGOT);
		show(out, ModContent.RAW_SULFUR);
		show(out, ModContent.SULFUR_DUST);
		// MOD-423 — the Nether metal; sits after the overworld chain it is a tier above.
		show(out, ModContent.RAW_PALLADIUM);
		show(out, ModContent.PALLADIUM_DUST);
		show(out, ModContent.PALLADIUM_INGOT);
		show(out, ModContent.TEMPERED_IRON);
		// Dusts of vanilla materials: what the macerator gives back from ordinary ore.
		show(out, ModContent.IRON_DUST);
		show(out, ModContent.COPPER_DUST);
		show(out, ModContent.GOLD_DUST);
		show(out, ModContent.COAL_DUST);
		show(out, ModContent.DIAMOND_DUST);
		show(out, ModContent.EMERALD_DUST);
		show(out, ModContent.LAPIS_DUST);
		plates(out);
		// MOD-064 alloys: made from the metals above rather than mined, so they close the list.
		show(out, ModContent.BRONZE_INGOT);
		show(out, ModContent.INVAR_INGOT);
		show(out, ModContent.CUPRONICKEL_INGOT);
		show(out, ModContent.ELECTRUM_INGOT);
		// Canning line (MOD-383): the tin can and what the machine fills it with.
		show(out, ModContent.EMPTY_CAN);
		show(out, ModContent.CANNED_RATION);
	}

	/** 11 - the nuclear line, kept together so it reads as one chain rather than stray oddities. */
	private static void nuclear(Sink out) {
		show(out, ModContent.RAW_URANIUM);
		show(out, ModContent.URANIUM_DUST);
		show(out, ModContent.URANIUM_INGOT);
		show(out, ModContent.DEPLETED_URANIUM);
		show(out, ModContent.UNSTABLE_ISOTOPE);
		show(out, ModContent.IRRADIATED_SLAG);
		show(out, ModContent.IRRADIATED_DIAMOND);
		show(out, ModContent.RESONANT_SHARD);
		show(out, ModContent.MUTAGEN_DUST);
	}

	/** 12 - blocks: ores as they are found underground, then what is built out of metal. */
	private static void blocks(Sink out) {
		naturalBlocks(out);
		show(out, ModContent.TEMPERED_IRON_BLOCK_ITEM);
		plateBlocks(out);
		// The Industrial Workbench is a decorative building block (MOD-062 villager POI) and also shows
		// in vanilla Building Blocks; listed here too so players browsing the mod's tab find it.
		show(out, ModContent.INDUSTRIAL_WORKBENCH_ITEM);
		show(out, ModContent.ENRICHED_URANIUM_TORCH_ITEM);
		// Mob Repeller family (MOD-278): the crafted LV block then its two evolved tiers. Listed here
		// as well as in utility() because those are different tabs — utility() feeds vanilla's
		// Functional Blocks, and only what main() calls reaches the mod's OWN tab.
		show(out, ModContent.MOB_REPELLER_ITEM);
		show(out, ModContent.MOB_REPELLER_MV_ITEM);
		show(out, ModContent.MOB_REPELLER_HV_ITEM);
	}

	/** 13 - the armour and weapon lines, plain tempered iron first, then the EU set. */
	private static void wearablesAndWeapons(Sink out) {
		show(out, ModContent.TEMPERED_IRON_PICKAXE);
		show(out, ModContent.TEMPERED_IRON_AXE);
		show(out, ModContent.TEMPERED_IRON_SHOVEL);
		show(out, ModContent.TEMPERED_IRON_HOE);
		show(out, ModContent.TEMPERED_IRON_SWORD);
		show(out, ModContent.TEMPERED_IRON_HELMET);
		show(out, ModContent.TEMPERED_IRON_CHESTPLATE);
		show(out, ModContent.TEMPERED_IRON_LEGGINGS);
		show(out, ModContent.TEMPERED_IRON_BOOTS);
		// Fluxweave set (MOD-127) - the EU armour line, right after the plain tempered iron set.
		show(out, ModContent.FLUXWEAVE_HELMET);
		show(out, ModContent.FLUXWEAVE_CHESTPLATE);
		show(out, ModContent.FLUXWEAVE_LEGGINGS);
		show(out, ModContent.FLUXWEAVE_BOOTS);
		// The scythes are a weapon line of their own, six tiers as one continuous row.
		scythes(out);
	}

	/** Decorative/utility blocks that live in vanilla Functional Blocks — the Enriched Uranium Torch (MOD-085). */
	private static void utility(Sink out) {
		show(out, ModContent.ENRICHED_URANIUM_TORCH_ITEM);
		// Mob Repeller family (MOD-278): the crafted LV block followed by the two evolved tiers, which
		// have no recipe of their own — same listing shape as the evolved wind mills and solar panels.
		show(out, ModContent.MOB_REPELLER_ITEM);
		show(out, ModContent.MOB_REPELLER_MV_ITEM);
		show(out, ModContent.MOB_REPELLER_HV_ITEM);
	}

	private static void generators(Sink out) {
		show(out, ModContent.SOLAR_PANEL_ITEM);
		show(out, ModContent.DAYLIGHT_SOLAR_PANEL_ITEM);
		show(out, ModContent.MOONLIT_SOLAR_PANEL_ITEM);
		show(out, ModContent.GENERATOR_ITEM);
		show(out, ModContent.GEOTHERMAL_GENERATOR_ITEM);
		show(out, ModContent.WATER_MILL_ITEM);
		show(out, ModContent.WIND_MILL_ITEM);
		// T2 wind mills (MOD-172): the height-focused Sky Mill and the weather-focused Tempest Mill.
		// Obtained only by evolving the T1 wind mill with a day/night alignment chip (no direct recipe),
		// so they are listed right after the T1 mill as the visible tail of the wind progression.
		show(out, ModContent.HIGH_ALTITUDE_WIND_MILL_ITEM);
		show(out, ModContent.STORM_WIND_MILL_ITEM);
	}

	/**
	 * 4 - the processing line: what a machine turns one item into another. Fluid machinery lives in
	 * {@link #fluids} instead, next to the pump and the tank it cannot work without (MOD-407).
	 */
	private static void machines(Sink out) {
		// The smelting line before the crusher: the Iron Furnace is the first machine a player builds
		// (no power needed), the Electric Furnace is its powered successor, and only then the Macerator.
		show(out, ModContent.IRON_FURNACE_ITEM);
		show(out, ModContent.ELECTRIC_FURNACE_ITEM);
		show(out, ModContent.MACERATOR_ITEM);
		show(out, ModContent.EXTRACTOR_ITEM);
		show(out, ModContent.COMPRESSOR_ITEM);
		show(out, ModContent.COMPONENT_REPAIR_BENCH_ITEM);
		show(out, ModContent.SAWMILL_ITEM);
		show(out, ModContent.ALLOY_SMELTER_ITEM);
		show(out, ModContent.CANNING_MACHINE_ITEM);
		show(out, ModContent.ELECTRIC_HEATER_ITEM);
		// Agriculture: the station and the drone it flies.
		show(out, ModContent.INCUBATOR_ITEM);
		show(out, ModContent.GARDEN_DRONE_STATION_ITEM);
		show(out, ModContent.GARDEN_DRONE);
		// MOD-275 - the first MV machine, last in the list because it sits a tier above the rest.
		show(out, ModContent.ASSEMBLER_ITEM);
	}





}
