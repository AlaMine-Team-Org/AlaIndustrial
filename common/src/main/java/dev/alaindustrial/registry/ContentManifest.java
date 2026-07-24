package dev.alaindustrial.registry;

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
import dev.alaindustrial.menu.MaceratorMenu;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import dev.alaindustrial.menu.PumpMenu;
import dev.alaindustrial.menu.SawmillMenu;
import dev.alaindustrial.menu.SilverChestMenu;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.menu.StormWindMillMenu;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.menu.TeleporterStationMenu;
import dev.alaindustrial.menu.WaterMillMenu;
import dev.alaindustrial.menu.WindMillMenu;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Loader-neutral content manifest (MOD-190). The single ordered list of the mod's registrable content,
 * declared once here in {@code common} with only vanilla types + common content classes — no Fabric or
 * NeoForge imports. Each loader replays this list through a thin adapter:
 * {@code ModMenus} (Fabric, eager {@code Registry.register}) and
 * {@code ModMenusNeoForge} (NeoForge, lazy {@code DeferredRegister}).
 *
 * <p>Adding a menu = one {@link #menu} entry here, plus its screen in
 * {@code dev.alaindustrial.client.screen.MenuScreenManifest}; both loaders pick it up automatically.
 *
 * <p><b>Ordered on purpose.</b> {@link #MENUS} is a {@link List} (insertion order), never a map, so the
 * registration order is identical on both loaders (MOD-190 gotcha #7). Order does not affect
 * correctness in 26.2 — registries sync by string id — but one shared order keeps the loaders consistent.
 */
public final class ContentManifest {
	private ContentManifest() {
	}

	/**
	 * The client menu constructor {@code (int syncId, Inventory) -> Menu}. Our own public functional
	 * interface — the vanilla {@code MenuType.MenuSupplier} is private, so it cannot be named here; each
	 * loader adapts this to its own factory type ({@code MenuType.MenuSupplier} on Fabric,
	 * {@code IContainerFactory} on NeoForge) by passing {@code factory::create}.
	 *
	 * @param <T> the menu class
	 */
	@FunctionalInterface
	public interface MenuFactory<T extends AbstractContainerMenu> {
		T create(int syncId, Inventory playerInventory);
	}

	/**
	 * One {@code MenuType} to register.
	 *
	 * @param <T>     the menu class
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param factory the client menu constructor, shared by both loaders
	 * @param bind    publishes the registered {@code MenuType} into its {@link ModContent} slot
	 */
	public record MenuDef<T extends AbstractContainerMenu>(String id, MenuFactory<T> factory,
			Consumer<Supplier<MenuType<T>>> bind) {
	}

	/**
	 * Builds a {@link MenuDef}, capturing the menu-class generic {@code T} from {@code factory} at the
	 * call site so the {@link #MENUS} list below stays free of explicit type witnesses.
	 *
	 * <p>{@code T} is fixed by {@code factory} (an exact method reference), and the {@code bind} target —
	 * a typed {@code ModContent} slot — is then <i>checked</i> against it (MOD-198). Pointing an entry at
	 * the wrong slot ({@code menu("sawmill", SawmillMenu::new, s -> ModContent.MACERATOR_MENU = s)}) is
	 * therefore a compile error, not a silent swap: {@code Supplier<MenuType<SawmillMenu>> cannot be
	 * converted to Supplier<MenuType<MaceratorMenu>>}. (In {@link
	 * dev.alaindustrial.client.screen.MenuScreenManifest#screen} the same guard shows up one step
	 * earlier, as a type-inference conflict rather than an assignment error.)
	 */
	private static <T extends AbstractContainerMenu> MenuDef<T> menu(String id, MenuFactory<T> factory,
			Consumer<Supplier<MenuType<T>>> bind) {
		return new MenuDef<>(id, factory, bind);
	}

	/** Every machine/chest menu, in one shared order. See {@link MenuDef}. */
	public static final List<MenuDef<?>> MENUS = List.of(
			menu("generator", GeneratorMenu::new, s -> ModContent.GENERATOR_MENU = s),
			menu("macerator", MaceratorMenu::new, s -> ModContent.MACERATOR_MENU = s),
			menu("solar_panel", SolarPanelMenu::new, s -> ModContent.SOLAR_PANEL_MENU = s),
			menu("moonlit_solar_panel", MoonlitSolarPanelMenu::new, s -> ModContent.MOONLIT_SOLAR_PANEL_MENU = s),
			menu("electric_furnace", ElectricFurnaceMenu::new, s -> ModContent.ELECTRIC_FURNACE_MENU = s),
			menu("extractor", ExtractorMenu::new, s -> ModContent.EXTRACTOR_MENU = s),
			menu("compressor", CompressorMenu::new, s -> ModContent.COMPRESSOR_MENU = s),
			menu("sawmill", SawmillMenu::new, s -> ModContent.SAWMILL_MENU = s),
			menu("battery_box", BatteryBoxMenu::new, s -> ModContent.BATTERY_BOX_MENU = s),
			menu("teleporter_station", TeleporterStationMenu::new, s -> ModContent.TELEPORTER_STATION_MENU = s),
			menu("teleporter_remote", TeleporterRemoteMenu::new, s -> ModContent.TELEPORTER_REMOTE_MENU = s),
			menu("daylight_solar_panel", DaylightSolarPanelMenu::new, s -> ModContent.DAYLIGHT_SOLAR_PANEL_MENU = s),
			menu("geothermal_generator", GeothermalGeneratorMenu::new, s -> ModContent.GEOTHERMAL_GENERATOR_MENU = s),
			menu("pump", PumpMenu::new, s -> ModContent.PUMP_MENU = s),
			menu("water_mill", WaterMillMenu::new, s -> ModContent.WATER_MILL_MENU = s),
			menu("wind_mill", WindMillMenu::new, s -> ModContent.WIND_MILL_MENU = s),
			menu("high_altitude_wind_mill", HighAltitudeWindMillMenu::new,
					s -> ModContent.HIGH_ALTITUDE_WIND_MILL_MENU = s),
			menu("storm_wind_mill", StormWindMillMenu::new, s -> ModContent.STORM_WIND_MILL_MENU = s),
			menu("iron_chest", IronChestMenu::new, s -> ModContent.IRON_CHEST_MENU = s),
			menu("silver_chest", SilverChestMenu::new, s -> ModContent.SILVER_CHEST_MENU = s),
			menu("gold_chest", GoldChestMenu::new, s -> ModContent.GOLD_CHEST_MENU = s));

	/**
	 * Wraps a machine/ore/material block's {@code strength/sound/…} chain with the shared base every such
	 * block carries — {@code requiresCorrectToolForDrops()} (a pickaxe is needed to drop; harvest tier is
	 * tag-driven). Torch blocks skip this (they break by hand) and use {@link ModBlockProperties#applyTorch}
	 * directly. {@code setId} is layered by each loader (Fabric from its key; NeoForge from the deferred key).
	 */
	private static UnaryOperator<BlockBehaviour.Properties> machine(UnaryOperator<BlockBehaviour.Properties> chain) {
		return p -> chain.apply(p.requiresCorrectToolForDrops());
	}

	/**
	 * The single source of every block's {@code BlockBehaviour.Properties} chain (MOD-190), keyed by
	 * registry id. Both {@code ModBlocks} (Fabric) and {@code ModBlocksNeoForge} apply
	 * {@code BLOCK_PROPS.get(id)} to their loader base instead of inlining the chain twice — so a
	 * {@code strength}/{@code sound}/{@code lightLevel} value can no longer drift between loaders
	 * (the MOD-157 bug class). Contains only loader-neutral behaviour; {@code setId} and the wall-torch
	 * loot/description overrides stay loader-side.
	 */
	public static final Map<String, UnaryOperator<BlockBehaviour.Properties>> BLOCK_PROPS = Map.ofEntries(
			Map.entry("generator", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			Map.entry("solar_panel", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion())),
			Map.entry("moonlit_solar_panel", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion())),
			Map.entry("daylight_solar_panel", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion())),
			Map.entry("geothermal_generator", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			Map.entry("water_mill", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("wind_mill", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("high_altitude_wind_mill", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("storm_wind_mill", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("pump", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("fluid_tank", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("copper_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("tin_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("insulated_copper_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion())),
			Map.entry("insulated_tin_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion())),
			Map.entry("item_pipe", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("macerator", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("battery_box", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.WOOD))),
			Map.entry("teleporter", machine(p -> p.strength(5.0f, 12.0f).sound(SoundType.METAL))),
			Map.entry("electric_furnace", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("iron_furnace", machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			Map.entry("extractor", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("compressor", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("sawmill", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("tin_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_tin_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("silver_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_silver_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("nickel_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_nickel_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("uranium_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_uranium_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("iron_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("silver_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("gold_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("tempered_iron_block", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("industrial_workbench", machine(p -> p.strength(2.5f, 6.0f).sound(SoundType.METAL))),
			Map.entry("enriched_uranium_torch", ModBlockProperties::applyTorch),
			Map.entry("enriched_uranium_wall_torch", ModBlockProperties::applyTorch));

	/** The shared {@code Properties} chain for {@code id} (see {@link #BLOCK_PROPS}); throws if unknown. */
	public static UnaryOperator<BlockBehaviour.Properties> blockProps(String id) {
		UnaryOperator<BlockBehaviour.Properties> op = BLOCK_PROPS.get(id);
		if (op == null) {
			throw new IllegalArgumentException("No BLOCK_PROPS entry for block id '" + id + "'");
		}
		return op;
	}
}
