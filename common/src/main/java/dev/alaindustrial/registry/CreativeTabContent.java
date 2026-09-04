package dev.alaindustrial.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

/**
 * Loader-neutral source of truth for public creative-inventory visibility.
 *
 * <p>This deliberately lists only player-visible MVP content. Registered-hidden entries
 * such as non-copper cables stay out of both the mod tab and the vanilla tabs until their
 * progression path is restored. (The T2 wind mills were restored to the player in MOD-172.)
 *
 * <p><b>Vanilla Combat and Tools &amp; Utilities are back here</b> (MOD-477 → MOD-555). They were groups
 * once, then the mod's gear had to sit NEXT TO the matching vanilla gear rather than at the end of the
 * tab: a {@link Sink} only appends, and positioning needs an anchor. So the two loaders took those tabs
 * over and kept a copy each — and the copies drifted, which is MOD-478 (three powered tools reached the
 * vanilla tab on Fabric and not on NeoForge). {@link AnchoredSink} is the missing verb; with it the two
 * lists collapse back into {@link #combat} and {@link #toolsAndUtilities}, and a loader supplies only its
 * own way of placing an entry after an anchor. The lists were reunified from the Fabric copy, which
 * MOD-478 had already made item-for-item equal to the NeoForge one.
 */
public final class CreativeTabContent {
	private CreativeTabContent() {
	}

	@FunctionalInterface
	public interface Sink {
		void accept(ItemLike item);
	}

	/**
	 * A {@link Sink} that can also POSITION an entry, for the vanilla tabs where the mod's gear has to
	 * stand next to the matching vanilla gear rather than at the end (MOD-555).
	 *
	 * <p>One verb, because that is all the two loaders have in common. Fabric's tab output takes an anchor
	 * and a whole group at once ({@code insertAfter(anchor, a, b, c)}); NeoForge's event takes one stack at
	 * a time and asserts the anchor is present, so its adapter chains the group — anchor → a, a → b, b → c —
	 * and falls back to an append when a third-party mod has removed the anchor (MOD-349). Both produce the
	 * same order; neither shape belongs in this file.
	 */
	public interface AnchoredSink extends Sink {
		/** Places {@code items} directly after {@code anchor}, in the order given. */
		void insertAfter(ItemLike anchor, List<ItemLike> items);
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
	 * <p>This is not a way to tolerate missing content: {@code registry_check.check_creative_coverage}
	 * fails the BUILD when a registered item stops being listed, so a real gap is caught long before a
	 * player sees it. What this guard removes is the crash as a failure mode.
	 */
	private static void show(Sink out, Supplier<? extends ItemLike> handle) {
		ItemLike item = resolve(handle);
		if (item != null) {
			out.accept(item);
		}
	}

	/** The item behind a handle, or {@code null} if it cannot be resolved. See {@link #show}. */
	private static @Nullable ItemLike resolve(Supplier<? extends ItemLike> handle) {
		try {
			return handle.get();
		} catch (RuntimeException e) {
			if (!warnedAboutMissingEntry) {
				warnedAboutMissingEntry = true;
				dev.alaindustrial.Industrialization.LOGGER.error(
						"[creative] an entry could not be resolved and was skipped; the rest of the tab is"
								+ " unaffected. Further occurrences this run are not logged.", e);
			}
			return null;
		}
	}

	/**
	 * Place {@code entries} after a VANILLA anchor. Unresolvable entries are skipped one by one, the same
	 * way {@link #show} skips them — a tab is a display, and it must degrade to one icon fewer.
	 */
	@SafeVarargs
	private static void after(AnchoredSink out, ItemLike anchor, Supplier<? extends ItemLike>... entries) {
		List<ItemLike> items = resolveAll(entries);
		if (!items.isEmpty()) {
			out.insertAfter(anchor, items);
		}
	}

	/**
	 * Place {@code entries} after one of the mod's OWN items, already placed by an earlier call. An
	 * anchor that cannot be resolved takes its whole group with it: there is nowhere to put them.
	 */
	@SafeVarargs
	private static void after(AnchoredSink out, Supplier<? extends ItemLike> anchor,
			Supplier<? extends ItemLike>... entries) {
		ItemLike resolvedAnchor = resolve(anchor);
		if (resolvedAnchor == null) {
			return;
		}
		after(out, resolvedAnchor, entries);
	}

	private static List<ItemLike> resolveAll(Supplier<? extends ItemLike>[] handles) {
		List<ItemLike> items = new ArrayList<>(handles.length);
		for (Supplier<? extends ItemLike> handle : handles) {
			ItemLike item = resolve(handle);
			if (item != null) {
				items.add(item);
			}
		}
		return items;
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

	/**
	 * The mod's contribution to the VANILLA Combat tab (MOD-478 → MOD-555): the tempered-iron weapon and
	 * armour line, each piece beside the vanilla iron piece it upgrades.
	 *
	 * <p>Order is load-bearing between statements, not only within them: an entry anchored on one of the
	 * mod's own items can only be placed once that item is in the tab. Anchors on vanilla items are
	 * independent of each other, so their statements may be in any order — the loaders used to write these
	 * two lists in different orders for that reason, and produced the same tab.
	 */
	public static void combat(AnchoredSink out) {
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
	public static void toolsAndUtilities(AnchoredSink out) {
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
		show(out, ModContent.JETPACK);
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
		show(out, ModContent.RTP_CHIP);
		show(out, ModContent.ALIGNMENT_CHIP_DAY);
		show(out, ModContent.ALIGNMENT_CHIP_NIGHT);
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
		// MOD-505 — the greenhouse, next to the mod's other agriculture blocks.
		show(out, ModContent.CRYSTAL_FARM_FLOOR_ITEM);
		show(out, ModContent.CRYSTAL_FARM_GLASS_ITEM);
		show(out, ModContent.CRYSTAL_FARM_DOOR_ITEM);
		show(out, ModContent.CRYSTAL_FARM_CONTROLLER_ITEM);
		show(out, ModContent.CRYSTAL_SEEDBED_ITEM);
		show(out, ModContent.COTTON_SEEDS);
		// MOD-537 — the second crop, next to the first: seeds to plant, the dug root, and its by-product.
		show(out, ModContent.KOK_SAGYZ_SEEDS);
		show(out, ModContent.KOK_SAGYZ_ROOT_ITEM);
		show(out, ModContent.INULIN);
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
		// MOD-146: the head of the organic chain, beside the other fluid-fed machines.
		show(out, ModContent.FERMENTER_ITEM);
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
		show(out, ModContent.BIOFUEL_BUCKET);
		show(out, ModContent.NUTRIENT_SOLUTION_BUCKET);
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
		// MOD-474 — not a rung of the ladder above, so it sits after it: same 36 slots as the iron
		// chest, bought for the shielding.
		show(out, ModContent.SHIELDING_CHEST_ITEM);
		show(out, ModContent.STORAGE_MODULE_ITEM);
		show(out, ModContent.STOCK_DISPLAY_FRAME_ITEM);
	}

	/** 7a - hand tools and instruments: everything useful with no charge in it. */
	private static void handTools(Sink out) {
		show(out, ModContent.WRENCH);
		show(out, ModContent.FORGE_HAMMER);
		show(out, ModContent.NETWORK_ANALYZER);
		show(out, ModContent.WIND_GAUGE);
		show(out, ModContent.GEIGER_COUNTER);
		show(out, ModContent.TELEPORTER_REMOTE);
		show(out, ModContent.GUIDE_BOOK);
	}

	/** 7b - powered gear: the electric tools, then what carries charge for them. */
	private static void poweredGear(Sink out) {
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
		show(out, ModContent.ELECTROMAGNET);
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
		show(out, ModContent.STATS_CHIP);
		// Evolution chips: not upgrade-panel parts, but the same "a chip you apply to a block" idea.
		show(out, ModContent.EMPTY_CHIP);
		show(out, ModContent.ALIGNMENT_CHIP_DAY);
		show(out, ModContent.ALIGNMENT_CHIP_NIGHT);
		show(out, ModContent.MUTATION_CHIP_TRANSFORM);
		show(out, ModContent.MUTATION_CHIP_DUPLICATE);
		show(out, ModContent.MUTATION_CHIP_CREATE);
		// Random Jump Chip (MOD-116): fitted to a block by hand like the alignment chips above, not
		// dropped into an upgrade panel — the teleporter station has none.
		show(out, ModContent.RTP_CHIP);
	}

	/** 9 - the parts machines and blocks are assembled from. */
	private static void craftingComponents(Sink out) {
		show(out, ModContent.ELECTRONIC_CIRCUIT);
		// MOD-299 - the MV tier of the circuit, listed right after its LV predecessor.
		show(out, ModContent.ADVANCED_CIRCUIT);
		show(out, ModContent.ASSEMBLY_BLUEPRINT);
		show(out, ModContent.COPPER_COIL);
		// Resonance chain (MOD-116): the raw crystal, then the coil built on it. The chip they lead to
		// sits in upgrades() instead — it is applied to a block, not consumed by a machine recipe.
		show(out, ModContent.SPATIAL_CRYSTAL);
		show(out, ModContent.RESONANCE_COIL);
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
	 */
	private static void materials(Sink out) {
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

	/** 11 - the nuclear line, kept together so it reads as one chain rather than stray oddities. */
	private static void nuclear(Sink out) {
		show(out, ModContent.RAW_URANIUM);
		show(out, ModContent.URANIUM_DUST);
		show(out, ModContent.URANIUM_INGOT);
		// The centrifuge branch, right after the plain smelting one it forks from: the same dust either
		// becomes an ingot in a furnace or twice as many shavings here, which press into refined uranium.
		show(out, ModContent.URANIUM_SHAVINGS);
		show(out, ModContent.REFINED_URANIUM);
		show(out, ModContent.UNSTABLE_ISOTOPE);
		show(out, ModContent.IRRADIATED_SLAG);
		show(out, ModContent.IRRADIATED_DIAMOND);
		show(out, ModContent.RESONANT_SHARD);
		show(out, ModContent.MUTAGEN_DUST);
		// MOD-468, stage 1 - the reactor room's shell. Kept in the nuclear group rather than with the
		// building blocks: these are reactor parts that happen to be cubes, and a player hunting for
		// them will look here.
		show(out, ModContent.REACTOR_CASING_ITEM);
		show(out, ModContent.IRRADIATED_SOIL_ITEM);
		show(out, ModContent.REACTOR_GLASS_ITEM);
		show(out, ModContent.REACTOR_PORT_ITEM);
		show(out, ModContent.REACTOR_DOOR_ITEM);
		show(out, ModContent.REACTOR_CONTROLLER_ITEM);
		show(out, ModContent.REACTOR_LAMP_ITEM);
		show(out, ModContent.REACTOR_OUTLET_ITEM);
		show(out, ModContent.STEAM_NOZZLE_ITEM);
		show(out, ModContent.REACTOR_BUTTON_ITEM);
		show(out, ModContent.REACTOR_LEVER_ITEM);
		show(out, ModContent.FUEL_ROD_ASSEMBLY_ITEM);
		show(out, ModContent.DEPLETED_URANIUM);
		show(out, ModContent.EMPTY_FUEL_ROD);
		show(out, ModContent.URANIUM_FUEL_ROD);
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
		show(out, ModContent.LIGHTNING_ROD_GENERATOR_ITEM);
		// MOD-479 — a QA instrument with no recipe: the creative tab is the only way to it.
		show(out, ModContent.CREATIVE_ENERGY_SOURCE_ITEM);
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
		// MOD-424 - directly after the heater it stands on, because it does nothing without one.
		show(out, ModContent.THERMAL_CENTRIFUGE_ITEM);
		// Agriculture: the station and the drone it flies.
		show(out, ModContent.INCUBATOR_ITEM);
		show(out, ModContent.GARDEN_DRONE_STATION_ITEM);
		show(out, ModContent.GARDEN_DRONE);
		// MOD-525: the sprinkler belongs with the farm blocks, not with the machines — it takes
		// no cable and its whole job is the plot around it.
		show(out, ModContent.SPRINKLER_ITEM);
		// MOD-275 - the first MV machine, last in the list because it sits a tier above the rest.
		show(out, ModContent.ASSEMBLER_ITEM);
	}





}
