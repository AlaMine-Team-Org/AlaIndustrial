package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.tool.ElectricHoeDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricHoeItem;
import dev.alaindustrial.item.tool.ElectricShovelDiamondTipItem;
import dev.alaindustrial.item.tool.ElectricShovelItem;
import dev.alaindustrial.item.tool.HammerItem;
import dev.alaindustrial.item.tool.neoforge.ElectricHoeDiamondTipItemNeoForge;
import dev.alaindustrial.item.tool.neoforge.ElectricHoeItemNeoForge;
import dev.alaindustrial.item.tool.neoforge.ElectricShovelDiamondTipItemNeoForge;
import dev.alaindustrial.item.tool.neoforge.ElectricShovelItemNeoForge;
import dev.alaindustrial.item.tool.neoforge.HammerItemNeoForge;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge item registration: a replay of the shared {@link ContentManifest#ITEMS} list, lazily through
 * a {@link DeferredRegister}.
 *
 * <p><b>MOD-306 → MOD-554.</b> MOD-306 moved each item's CONSTRUCTION into the manifest. MOD-554 moved
 * the <b>list</b>: which items exist, in what order, built by which factory, bound to which
 * {@link ModContent} handle. This file used to mirror the Fabric {@code ModItems} "1:1 by convention" —
 * 273 registrations and 273 {@code ModContent.X = …} lines apiece — held together by a Python
 * set-comparison run after the fact.
 *
 * <p><b>What is left here is the NeoForge registration MECHANISM, and only that:</b> the
 * {@link DeferredRegister} (which must live on this side), its lazy {@code registerItem} — whose
 * {@code Properties} carry an id derived from the deferred key, so no {@code setId} here — and
 * {@link #LOADER_ITEMS}, the five items whose CLASS is NeoForge's rather than shared.
 *
 * <p><b>Timing.</b> Every factory runs when the item {@code RegisterEvent} fires, by which point the
 * block, fluid and entity-type registries this loader populates are already filled (vanilla registry
 * order), so the manifest's by-id resolution is safe here for the same reason it is on Fabric.
 *
 * <p><b>The typed fields below are handles, not registrations.</b> They exist because a handful of
 * NeoForge call sites (the item-capability registrations, one gametest, the tab icon) read
 * {@code ModItemsNeoForge.X}; adding an item does NOT require adding one.
 */
public final class ModItemsNeoForge {
	public static final DeferredRegister.Items ITEMS =
			DeferredRegister.createItems(Industrialization.MOD_ID);

	/**
	 * The five items whose CLASS is NeoForge-specific — the manifest declares them with
	 * {@code loaderItem(...)} and no shared factory, so this map is the only thing that can build them
	 * here. A missing entry throws at startup rather than falling back to a shared class, which is
	 * exactly the failure MOD-378 and MOD-379 each shipped once: the base hoe went out unable to till and
	 * the base shovel unable to make paths, because they used the common class and NeoForge's patched
	 * {@code HoeItem}/{@code ShovelItem} gate those actions behind an {@code ItemAbility} declaration.
	 *
	 * <p>The properties stay shared in every case — only the constructor differs.
	 */
	private static final Map<String, Function<Item.Properties, ? extends Item>> LOADER_ITEMS = Map.of(
			"forge_hammer", p -> new HammerItemNeoForge(HammerItem.hammerProperties(p)),
			"electric_shovel", p -> new ElectricShovelItemNeoForge(
					ElectricShovelItem.electricShovelProperties(p)),
			"electric_shovel_diamond_tip", p -> new ElectricShovelDiamondTipItemNeoForge(
					ElectricShovelDiamondTipItem.electricShovelDiamondTipProperties(p)),
			"electric_hoe", p -> new ElectricHoeItemNeoForge(ElectricHoeItem.electricHoeProperties(p)),
			"electric_hoe_diamond_tip", p -> new ElectricHoeDiamondTipItemNeoForge(
					ElectricHoeDiamondTipItem.electricHoeDiamondTipProperties(p)));

	/**
	 * Every manifest entry, queued on {@link #ITEMS} the moment this class loads. Declared right after
	 * the register on purpose: static fields initialise in textual order, so the register and the
	 * override map exist here and the handles below can read the result.
	 */
	private static final Map<String, DeferredItem<Item>> REGISTERED = registerAll();

	// Powered items: read by registerCapabilities (Capabilities.Energy.ITEM).
	public static final DeferredItem<Item> BATTERY = handle("battery");
	public static final DeferredItem<Item> BATTERY_POUCH = handle("battery_pouch");
	public static final DeferredItem<Item> ENERGY_PACK = handle("energy_pack");
	public static final DeferredItem<Item> ELECTRIC_DRILL = handle("electric_drill");
	public static final DeferredItem<Item> ELECTRIC_DRILL_DIAMOND_TIP = handle("electric_drill_diamond_tip");
	public static final DeferredItem<Item> ELECTRIC_DRILL_NETHERITE_TIP = handle("electric_drill_netherite_tip");
	public static final DeferredItem<Item> ELECTRIC_CHAINSAW = handle("electric_chainsaw");
	public static final DeferredItem<Item> ELECTRIC_CHAINSAW_DIAMOND_TIP = handle("electric_chainsaw_diamond_tip");
	public static final DeferredItem<Item> ELECTRIC_SHOVEL = handle("electric_shovel");
	public static final DeferredItem<Item> ELECTRIC_SHOVEL_DIAMOND_TIP = handle("electric_shovel_diamond_tip");
	public static final DeferredItem<Item> ELECTRIC_HOE = handle("electric_hoe");
	public static final DeferredItem<Item> ELECTRIC_HOE_DIAMOND_TIP = handle("electric_hoe_diamond_tip");
	public static final DeferredItem<Item> ELECTRIC_SABER = handle("electric_saber");
	public static final DeferredItem<Item> ELECTROMAGNET = handle("electromagnet");
	public static final DeferredItem<Item> JETPACK = handle("jetpack");
	public static final DeferredItem<Item> FLUXWEAVE_HELMET = handle("fluxweave_helmet");
	public static final DeferredItem<Item> FLUXWEAVE_CHESTPLATE = handle("fluxweave_chestplate");
	public static final DeferredItem<Item> FLUXWEAVE_LEGGINGS = handle("fluxweave_leggings");
	public static final DeferredItem<Item> FLUXWEAVE_BOOTS = handle("fluxweave_boots");
	public static final DeferredItem<Item> ENERGY_CRYSTAL_BLANK = handle("energy_crystal_blank");
	public static final DeferredItem<Item> LAPOTRON_CRYSTAL_BLANK = handle("lapotron_crystal_blank");
	public static final DeferredItem<Item> RESONANT_CRYSTAL_BLANK = handle("resonant_crystal_blank");
	// Capsules: read by registerCapabilities (Capabilities.Fluid.ITEM).
	public static final DeferredItem<Item> VACUUM_CAPSULE = handle("vacuum_capsule");
	public static final DeferredItem<Item> FILLED_VACUUM_CAPSULE = handle("filled_vacuum_capsule");
	// The creative tab's icon (ModCreativeTabNeoForge).
	public static final DeferredItem<Item> MACERATOR_ITEM = handle("macerator");

	private ModItemsNeoForge() {
	}

	/** Queues every {@link ContentManifest#ITEMS} entry, in list order, and binds each into ModContent. */
	private static Map<String, DeferredItem<Item>> registerAll() {
		Map<String, DeferredItem<Item>> registered = new LinkedHashMap<>();
		for (ContentManifest.ItemDef def : ContentManifest.ITEMS) {
			if (registered.put(def.id(), register(def)) != null) {
				throw new IllegalStateException(
						"ContentManifest.ITEMS declares item id '" + def.id() + "' twice");
			}
		}
		return Map.copyOf(registered);
	}

	/**
	 * One manifest entry, the NeoForge way: {@code registerItem} applies {@code setId} from the deferred
	 * key and calls the factory when the item {@code RegisterEvent} fires — that lateness is what lets a
	 * factory resolve a block, a fluid, an entity type, or an EARLIER item of this very list
	 * ({@code filled_vacuum_capsule} takes the empty capsule as its craft-remainder).
	 *
	 * <p>The {@link ModContent} slot is bound HERE, at class load: a {@code DeferredItem} <b>is</b> a
	 * {@code Supplier} and resolves lazily, so binding before the event is legal and is exactly what the
	 * 273 hand-written {@code ModContent.X = …} lines in {@code init()} used to do.
	 */
	private static DeferredItem<Item> register(ContentManifest.ItemDef def) {
		Function<Item.Properties, ? extends Item> factory =
				ContentManifest.itemFactory(def, LOADER_ITEMS.get(def.id()));
		DeferredItem<Item> holder = ITEMS.registerItem(def.id(), p -> factory.apply(p));
		def.bind().accept(holder);
		return holder;
	}

	/** The queued holder for a manifest id; throws rather than returning {@code null}. */
	private static DeferredItem<Item> handle(String id) {
		DeferredItem<Item> holder = REGISTERED.get(id);
		if (holder == null) {
			throw new IllegalStateException("ModItemsNeoForge handle for '" + id
					+ "' has no queued item — is the entry missing from ContentManifest.ITEMS?");
		}
		return holder;
	}

	/**
	 * Class-load trigger for the {@code @Mod} constructor. Queueing and {@link ModContent} binding both
	 * happen in the static initializer above, so this only has to touch the class — and it checks,
	 * cheaply, that the replay covered the whole manifest rather than silently stopping short.
	 */
	public static void init() {
		if (REGISTERED.size() != ContentManifest.ITEMS.size()) {
			throw new IllegalStateException("ModItemsNeoForge registered " + REGISTERED.size() + " of "
					+ ContentManifest.ITEMS.size() + " manifest items");
		}
	}
}
