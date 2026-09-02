package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.tool.ElectricHoeDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricHoeItem;
import dev.alaindustrial.item.tool.ElectricShovelDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricShovelItem;
import dev.alaindustrial.item.tool.HammerItem;
import dev.alaindustrial.item.tool.HammerItemFabric;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Fabric item registration: a replay of the shared {@link ContentManifest#ITEMS} list, plus the mod's
 * creative tab and its insertions into the vanilla ones.
 *
 * <p><b>MOD-306 → MOD-554.</b> MOD-306 moved each item's CONSTRUCTION into the manifest. MOD-554 moved
 * the <b>list</b>: which items exist, in what order, built by which factory, bound to which
 * {@link ModContent} handle. Before that, this file and {@code ModItemsNeoForge} each carried their own
 * copy of all of it — 273 registrations and 273 {@code ModContent.X = …} lines apiece — and the only
 * thing keeping the two in step was a Python set-comparison run after the fact.
 *
 * <p><b>What is left here is the Fabric registration MECHANISM, and only that:</b> eager construction,
 * the {@code setId(key)} the loader has to stamp itself, {@code Registry.register}, and
 * {@link #LOADER_ITEMS} — the five items whose CLASS is Fabric's rather than shared.
 *
 * <p><b>The typed fields below are handles, not registrations.</b> They exist because a handful of
 * Fabric call sites (the item-energy and capsule capability registrations, two gametests, the tab icon)
 * read {@code ModItems.X}; adding an item does NOT require adding one. Everything else reads
 * {@code ModContent}, which the replay binds.
 */
public final class ModItems {
	private ModItems() {
	}

	/**
	 * The mod's own creative tab. Lists the release-visible blocks + items; pre-release blocks
	 * (pump, non-copper cables) stay registered but are intentionally omitted from the tab — see
	 * {@link #init()} and task MOD-010.
	 */
	public static final ResourceKey<CreativeModeTab> TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Industrialization.id("main"));

	/**
	 * The five items whose CLASS is Fabric-specific — the manifest declares them with
	 * {@code loaderItem(...)} and no shared factory, so this map is the only thing that can build them
	 * here (and its NeoForge twin the only thing that can build them there). A missing entry throws at
	 * startup rather than falling back to a shared class: see {@code ContentManifest#itemFactory}.
	 *
	 * <p>All five are loader-API seams, not content differences. The forge hammer's craft-remainder hook
	 * has a different signature on each loader ({@code FabricItem#getCraftingRemainder(ItemStack)} here),
	 * so the class differs while the properties stay shared in {@code HammerItem#hammerProperties}. The
	 * four hoe/shovel tiers use the COMMON classes here precisely because Fabric has no
	 * {@code ItemAbility}: NeoForge needs subclasses that declare {@code HOE_TILL} / {@code SHOVEL_FLATTEN}
	 * (MOD-378/MOD-379), Fabric needs nothing of the sort.
	 */
	private static final Map<String, Function<Item.Properties, ? extends Item>> LOADER_ITEMS = Map.of(
			"forge_hammer", p -> new HammerItemFabric(HammerItem.hammerProperties(p)),
			"electric_shovel", p -> new ElectricShovelItem(ElectricShovelItem.electricShovelProperties(p)),
			"electric_shovel_diamond_tip", p -> new ElectricShovelDiamondTipItem(
					ElectricShovelDiamondTipItem.electricShovelDiamondTipProperties(p)),
			"electric_hoe", p -> new ElectricHoeItem(ElectricHoeItem.electricHoeProperties(p)),
			"electric_hoe_diamond_tip", p -> new ElectricHoeDiamondTipItem(
					ElectricHoeDiamondTipItem.electricHoeDiamondTipProperties(p)));

	/**
	 * Every item, registered the moment this class loads. Declared FIRST on purpose: static fields
	 * initialise in textual order, so this runs before the handles below read it.
	 */
	private static final Map<String, Item> REGISTERED = registerAll();

	// Powered items: read by the Fabric item-energy capability registration (StackAsEnergyStorage).
	public static final Item BATTERY = handle("battery");
	public static final Item BATTERY_POUCH = handle("battery_pouch");
	public static final Item ENERGY_PACK = handle("energy_pack");
	public static final Item ELECTRIC_DRILL = handle("electric_drill");
	public static final Item ELECTRIC_DRILL_DIAMOND_TIP = handle("electric_drill_diamond_tip");
	public static final Item ELECTRIC_DRILL_NETHERITE_TIP = handle("electric_drill_netherite_tip");
	public static final Item ELECTRIC_CHAINSAW = handle("electric_chainsaw");
	public static final Item ELECTRIC_CHAINSAW_DIAMOND_TIP = handle("electric_chainsaw_diamond_tip");
	public static final Item ELECTRIC_SHOVEL = handle("electric_shovel");
	public static final Item ELECTRIC_SHOVEL_DIAMOND_TIP = handle("electric_shovel_diamond_tip");
	public static final Item ELECTRIC_HOE = handle("electric_hoe");
	public static final Item ELECTRIC_HOE_DIAMOND_TIP = handle("electric_hoe_diamond_tip");
	public static final Item ELECTRIC_SABER = handle("electric_saber");
	public static final Item ELECTROMAGNET = handle("electromagnet");
	public static final Item JETPACK = handle("jetpack");
	public static final Item FLUXWEAVE_HELMET = handle("fluxweave_helmet");
	public static final Item FLUXWEAVE_CHESTPLATE = handle("fluxweave_chestplate");
	public static final Item FLUXWEAVE_LEGGINGS = handle("fluxweave_leggings");
	public static final Item FLUXWEAVE_BOOTS = handle("fluxweave_boots");
	public static final Item ENERGY_CRYSTAL_BLANK = handle("energy_crystal_blank");
	public static final Item LAPOTRON_CRYSTAL_BLANK = handle("lapotron_crystal_blank");
	public static final Item RESONANT_CRYSTAL_BLANK = handle("resonant_crystal_blank");
	// Capsules: read by the Fabric item-fluid capability registration (CapsuleItemFluidStorage).
	public static final Item VACUUM_CAPSULE = handle("vacuum_capsule");
	public static final Item FILLED_VACUUM_CAPSULE = handle("filled_vacuum_capsule");
	// Read by the solar-panel gametest stands.
	public static final Item ALIGNMENT_CHIP_DAY = handle("alignment_chip_day");
	public static final Item ALIGNMENT_CHIP_NIGHT = handle("alignment_chip_night");
	// The creative tab's icon, below.
	public static final Item MACERATOR_ITEM = handle("macerator");

	/**
	 * Registers every {@link ContentManifest#ITEMS} entry, in list order, and binds each one into
	 * {@link ModContent}.
	 *
	 * <p><b>Blocks, fluids and entity types first.</b> A block item's factory resolves its block by id, a
	 * bucket its fluid, the display frame its entity type — all from the vanilla registries, so those
	 * registries have to be populated before the first factory runs. Both calls are idempotent class-load
	 * triggers ({@code ModBlocks} registers the fluids on the way, see its own javadoc); the entrypoint
	 * already calls them in this order, and doing it here as well means an early touch of this class
	 * cannot silently produce items over AIR.
	 */
	private static Map<String, Item> registerAll() {
		ModBlocks.init();
		ModEntities.init();
		Map<String, Item> registered = new LinkedHashMap<>();
		for (ContentManifest.ItemDef def : ContentManifest.ITEMS) {
			if (registered.put(def.id(), register(def)) != null) {
				throw new IllegalStateException(
						"ContentManifest.ITEMS declares item id '" + def.id() + "' twice");
			}
		}
		return Map.copyOf(registered);
	}

	/**
	 * One manifest entry, the Fabric way: build the item eagerly from a {@code Properties} carrying the
	 * Fabric-only {@code setId}, register it, and publish the result into the entry's {@link ModContent}
	 * slot as a constant supplier ({@code () -> value}) — NeoForge instead binds its lazy
	 * {@code DeferredItem} into the same slot.
	 *
	 * <p>Binding here rather than in a later {@code init()} is what lets an entry read an EARLIER one:
	 * {@code filled_vacuum_capsule} takes the empty capsule as its craft-remainder.
	 */
	private static Item register(ContentManifest.ItemDef def) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Industrialization.id(def.id()));
		Item item = ContentManifest.itemFactory(def, LOADER_ITEMS.get(def.id()))
				.apply(new Item.Properties().setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		def.bind().accept(() -> item);
		return item;
	}

	/** The registered item for a manifest id; throws rather than returning {@code null}. */
	private static Item handle(String id) {
		Item item = REGISTERED.get(id);
		if (item == null) {
			throw new IllegalStateException("ModItems handle for '" + id
					+ "' has no registered item — is the entry missing from ContentManifest.ITEMS?");
		}
		return item;
	}

	/**
	 * Registers the creative tabs. Registration and {@link ModContent} binding both happen in the static
	 * initializer above, so this only has to touch the class — and it checks, cheaply, that the replay
	 * covered the whole manifest rather than silently stopping short.
	 */
	public static void init() {
		if (REGISTERED.size() != ContentManifest.ITEMS.size()) {
			throw new IllegalStateException("ModItems registered " + REGISTERED.size() + " of "
					+ ContentManifest.ITEMS.size() + " manifest items");
		}
		CreativeModeTab tab = FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.alaindustrial"))
				.icon(() -> new ItemStack(MACERATOR_ITEM))
				.displayItems((params, output) ->
						// Single source of truth shared with NeoForge (CreativeTabContent) so the mod tab
						// is identical on both loaders. Hidden pre-release content (non-copper cables,
						// water/high-altitude mills) is omitted there, so it stays hidden here too — MOD-102.
						CreativeTabContent.main(output::accept))
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, tab);

		registerVanillaCreativeTabs();
	}

	private static void registerVanillaCreativeTabs() {
		CreativeModeTabEvents.modifyOutputEvent(VanillaCreativeTabs.COMBAT)
				.register(output -> CreativeTabContent.combat(anchored(output)));
		CreativeModeTabEvents.modifyOutputEvent(VanillaCreativeTabs.TOOLS_AND_UTILITIES)
				.register(output -> CreativeTabContent.toolsAndUtilities(anchored(output)));
		CreativeModeTabEvents.modifyOutputEvent(VanillaCreativeTabs.INGREDIENTS)
				.register(output -> CreativeTabContent.ingredients(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(VanillaCreativeTabs.BUILDING_BLOCKS)
				.register(output -> CreativeTabContent.buildingBlocks(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(VanillaCreativeTabs.NATURAL_BLOCKS)
				.register(output -> CreativeTabContent.naturalBlocks(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(VanillaCreativeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> CreativeTabContent.functionalBlocks(output::accept));
	}

	/**
	 * Fabric's tab output, seen as the loader-neutral {@link CreativeTabContent.AnchoredSink} the two
	 * vanilla-tab lists are written against (MOD-555).
	 *
	 * <p>Fabric places a whole group in one call and, when the anchor is absent, appends instead of
	 * throwing — so this adapter is the varargs call and nothing else. NeoForge needs a chain and a guard;
	 * that asymmetry is exactly why the shared list speaks in {@code insertAfter(anchor, items)} and lets
	 * each loader say it its own way.
	 */
	private static CreativeTabContent.AnchoredSink anchored(FabricCreativeModeTabOutput output) {
		return new CreativeTabContent.AnchoredSink() {
			@Override
			public void accept(ItemLike item) {
				output.accept(item);
			}

			@Override
			public void insertAfter(ItemLike anchor, List<ItemLike> items) {
				output.insertAfter(anchor, items.toArray(new ItemLike[0]));
			}
		};
	}
}
