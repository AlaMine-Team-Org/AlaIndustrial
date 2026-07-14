package dev.alaindustrial.registry;

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
 * for {@link dev.alaindustrial.core.EnergyTransactions} /
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
	public static Supplier<Block> COPPER_CABLE = unbound("COPPER_CABLE");
	public static Supplier<Block> TIN_CABLE = unbound("TIN_CABLE");
	public static Supplier<Block> INSULATED_COPPER_CABLE = unbound("INSULATED_COPPER_CABLE");
	public static Supplier<Block> INSULATED_TIN_CABLE = unbound("INSULATED_TIN_CABLE");
	public static Supplier<Block> MACERATOR = unbound("MACERATOR");
	public static Supplier<Block> BATTERY_BOX = unbound("BATTERY_BOX");
	public static Supplier<Block> ELECTRIC_FURNACE = unbound("ELECTRIC_FURNACE");
	public static Supplier<Block> EXTRACTOR = unbound("EXTRACTOR");
	public static Supplier<Block> COMPRESSOR = unbound("COMPRESSOR");
	public static Supplier<Block> TIN_ORE = unbound("TIN_ORE");
	public static Supplier<Block> DEEPSLATE_TIN_ORE = unbound("DEEPSLATE_TIN_ORE");
	public static Supplier<Block> SILVER_ORE = unbound("SILVER_ORE");
	public static Supplier<Block> DEEPSLATE_SILVER_ORE = unbound("DEEPSLATE_SILVER_ORE");
	public static Supplier<Block> NICKEL_ORE = unbound("NICKEL_ORE");
	public static Supplier<Block> DEEPSLATE_NICKEL_ORE = unbound("DEEPSLATE_NICKEL_ORE");
	public static Supplier<Block> URANIUM_ORE = unbound("URANIUM_ORE");
	public static Supplier<Block> DEEPSLATE_URANIUM_ORE = unbound("DEEPSLATE_URANIUM_ORE");
	// Iron Chest — a pure-storage block (no energy), so its BE extends vanilla
	// BaseContainerBlockEntity, not the mod's MachineBlockEntity. See docs/blocks/iron_chest.md.
	public static Supplier<Block> IRON_CHEST = unbound("IRON_CHEST");
	// Tempered Iron Block — a "block of X" material block (9 ingots ↔ 1 block), like
	// vanilla iron block. Pure material/decorative block, no BE, single texture on all 6 faces.
	public static Supplier<Block> TEMPERED_IRON_BLOCK = unbound("TEMPERED_IRON_BLOCK");

	// --- Items (crafting components + tools) ---
	public static Supplier<Item> ELECTRONIC_CIRCUIT = unbound("ELECTRONIC_CIRCUIT");
	public static Supplier<Item> ALIGNMENT_CHIP_DAY = unbound("ALIGNMENT_CHIP_DAY");
	public static Supplier<Item> ALIGNMENT_CHIP_NIGHT = unbound("ALIGNMENT_CHIP_NIGHT");
	public static Supplier<Item> WINDMILL_ROTOR = unbound("WINDMILL_ROTOR");
	public static Supplier<Item> WOODEN_GEAR = unbound("WOODEN_GEAR");
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
	public static Supplier<Item> URANIUM_DUST = unbound("URANIUM_DUST");
	public static Supplier<Item> RAW_URANIUM = unbound("RAW_URANIUM");
	public static Supplier<Item> URANIUM_INGOT = unbound("URANIUM_INGOT");
	public static Supplier<Item> NETWORK_ANALYZER = unbound("NETWORK_ANALYZER");
	public static Supplier<Item> BATTERY_POUCH = unbound("BATTERY_POUCH");
	// Energy Pack (MOD-065) — worn LV energy buffer (chest slot) that tops up the powered items the
	// player carries; BATTERY is its crafting component (an inert cell, no charge of its own).
	public static Supplier<Item> BATTERY = unbound("BATTERY");
	public static Supplier<Item> ENERGY_PACK = unbound("ENERGY_PACK");
	// Vacuum Capsule (MOD-063) — a stackable fluid container: empty (×64) exchanges with the
	// fluid-carrying filled form (×16). See docs/blocks/items/vacuum_capsule.md.
	public static Supplier<Item> VACUUM_CAPSULE = unbound("VACUUM_CAPSULE");
	public static Supplier<Item> FILLED_VACUUM_CAPSULE = unbound("FILLED_VACUUM_CAPSULE");
	// Stock Display Frame (MOD-066) — placement item for the frame entity below.
	public static Supplier<Item> STOCK_DISPLAY_FRAME_ITEM = unbound("STOCK_DISPLAY_FRAME_ITEM");
	// Scythe (MOD-068) — AOE foliage-clearing tool, six material tiers. Behaviour lives in
	// dev.alaindustrial.item.ScytheItem (common); each loader registers the six items.
	public static Supplier<Item> SCYTHE_WOOD = unbound("SCYTHE_WOOD");
	public static Supplier<Item> SCYTHE_STONE = unbound("SCYTHE_STONE");
	public static Supplier<Item> SCYTHE_COPPER = unbound("SCYTHE_COPPER");
	public static Supplier<Item> SCYTHE_IRON = unbound("SCYTHE_IRON");
	public static Supplier<Item> SCYTHE_GOLD = unbound("SCYTHE_GOLD");
	public static Supplier<Item> SCYTHE_TEMPERED_IRON = unbound("SCYTHE_TEMPERED_IRON");
	public static Supplier<Item> SCYTHE_DIAMOND = unbound("SCYTHE_DIAMOND");
	public static Supplier<Item> SCYTHE_NETHERITE = unbound("SCYTHE_NETHERITE");

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
	public static Supplier<BlockItem> INSULATED_COPPER_CABLE_ITEM = unbound("INSULATED_COPPER_CABLE_ITEM");
	public static Supplier<BlockItem> INSULATED_TIN_CABLE_ITEM = unbound("INSULATED_TIN_CABLE_ITEM");
	public static Supplier<BlockItem> MACERATOR_ITEM = unbound("MACERATOR_ITEM");
	public static Supplier<BlockItem> BATTERY_BOX_ITEM = unbound("BATTERY_BOX_ITEM");
	public static Supplier<BlockItem> ELECTRIC_FURNACE_ITEM = unbound("ELECTRIC_FURNACE_ITEM");
	public static Supplier<BlockItem> EXTRACTOR_ITEM = unbound("EXTRACTOR_ITEM");
	public static Supplier<BlockItem> COMPRESSOR_ITEM = unbound("COMPRESSOR_ITEM");
	public static Supplier<BlockItem> PUMP_ITEM = unbound("PUMP_ITEM");
	public static Supplier<BlockItem> TIN_ORE_ITEM = unbound("TIN_ORE_ITEM");
	public static Supplier<BlockItem> DEEPSLATE_TIN_ORE_ITEM = unbound("DEEPSLATE_TIN_ORE_ITEM");
	public static Supplier<BlockItem> SILVER_ORE_ITEM = unbound("SILVER_ORE_ITEM");
	public static Supplier<BlockItem> DEEPSLATE_SILVER_ORE_ITEM = unbound("DEEPSLATE_SILVER_ORE_ITEM");
	public static Supplier<BlockItem> NICKEL_ORE_ITEM = unbound("NICKEL_ORE_ITEM");
	public static Supplier<BlockItem> DEEPSLATE_NICKEL_ORE_ITEM = unbound("DEEPSLATE_NICKEL_ORE_ITEM");
	public static Supplier<BlockItem> URANIUM_ORE_ITEM = unbound("URANIUM_ORE_ITEM");
	public static Supplier<BlockItem> DEEPSLATE_URANIUM_ORE_ITEM = unbound("DEEPSLATE_URANIUM_ORE_ITEM");
	public static Supplier<BlockItem> IRON_CHEST_ITEM = unbound("IRON_CHEST_ITEM");
	public static Supplier<BlockItem> TEMPERED_IRON_BLOCK_ITEM = unbound("TEMPERED_IRON_BLOCK_ITEM");

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
	public static Supplier<BlockEntityType<?>> MACERATOR_BE = unbound("MACERATOR_BE");
	public static Supplier<BlockEntityType<?>> BATTERY_BOX_BE = unbound("BATTERY_BOX_BE");
	public static Supplier<BlockEntityType<?>> ELECTRIC_FURNACE_BE = unbound("ELECTRIC_FURNACE_BE");
	public static Supplier<BlockEntityType<?>> EXTRACTOR_BE = unbound("EXTRACTOR_BE");
	public static Supplier<BlockEntityType<?>> COMPRESSOR_BE = unbound("COMPRESSOR_BE");
	public static Supplier<BlockEntityType<?>> PUMP_BE = unbound("PUMP_BE");
	public static Supplier<BlockEntityType<?>> IRON_CHEST_BE = unbound("IRON_CHEST_BE");

	// --- Menu types ---
	public static Supplier<MenuType<?>> GENERATOR_MENU = unbound("GENERATOR_MENU");
	public static Supplier<MenuType<?>> MACERATOR_MENU = unbound("MACERATOR_MENU");
	public static Supplier<MenuType<?>> SOLAR_PANEL_MENU = unbound("SOLAR_PANEL_MENU");
	public static Supplier<MenuType<?>> MOONLIT_SOLAR_PANEL_MENU = unbound("MOONLIT_SOLAR_PANEL_MENU");
	public static Supplier<MenuType<?>> ELECTRIC_FURNACE_MENU = unbound("ELECTRIC_FURNACE_MENU");
	public static Supplier<MenuType<?>> EXTRACTOR_MENU = unbound("EXTRACTOR_MENU");
	public static Supplier<MenuType<?>> COMPRESSOR_MENU = unbound("COMPRESSOR_MENU");
	public static Supplier<MenuType<?>> BATTERY_BOX_MENU = unbound("BATTERY_BOX_MENU");
	public static Supplier<MenuType<?>> DAYLIGHT_SOLAR_PANEL_MENU = unbound("DAYLIGHT_SOLAR_PANEL_MENU");
	public static Supplier<MenuType<?>> GEOTHERMAL_GENERATOR_MENU = unbound("GEOTHERMAL_GENERATOR_MENU");
	public static Supplier<MenuType<?>> PUMP_MENU = unbound("PUMP_MENU");
	public static Supplier<MenuType<?>> WATER_MILL_MENU = unbound("WATER_MILL_MENU");
	public static Supplier<MenuType<?>> WIND_MILL_MENU = unbound("WIND_MILL_MENU");
	public static Supplier<MenuType<?>> HIGH_ALTITUDE_WIND_MILL_MENU = unbound("HIGH_ALTITUDE_WIND_MILL_MENU");
	public static Supplier<MenuType<?>> STORM_WIND_MILL_MENU = unbound("STORM_WIND_MILL_MENU");
	public static Supplier<MenuType<?>> IRON_CHEST_MENU = unbound("IRON_CHEST_MENU");

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
