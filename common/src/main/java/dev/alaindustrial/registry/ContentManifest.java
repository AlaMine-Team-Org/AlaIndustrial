package dev.alaindustrial.registry;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CesuBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.ChargePadBlockEntity;
import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import dev.alaindustrial.block.entity.CompressorBlockEntity;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnSegmentBlockEntity;
import dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.block.entity.ExtractorBlockEntity;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.GalvanicBathBlockEntity;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.IncubatorMode;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.IronFurnaceBlockEntity;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.Overclockable;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.block.entity.SawmillBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.item.energy.BatteryItem;
import dev.alaindustrial.item.misc.HintItem;
import dev.alaindustrial.item.misc.MutationChipItem;
import dev.alaindustrial.item.misc.OverclockerChipItem;
import dev.alaindustrial.menu.AssemblerMenu;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.menu.EnergyCondenserMenu;
import dev.alaindustrial.menu.CesuMenu;
import dev.alaindustrial.menu.CompressorMenu;
import dev.alaindustrial.menu.DaylightSolarPanelMenu;
import dev.alaindustrial.menu.DistillationColumnMenu;
import dev.alaindustrial.menu.DoubleChestMenu;
import dev.alaindustrial.menu.ElectricFurnaceMenu;
import dev.alaindustrial.menu.ExtractorMenu;
import dev.alaindustrial.menu.GeneratorMenu;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.menu.GoldChestMenu;
import dev.alaindustrial.menu.HighAltitudeWindMillMenu;
import dev.alaindustrial.menu.IronChestMenu;
import dev.alaindustrial.menu.StorageMenu3;
import dev.alaindustrial.menu.StorageMenu6;
import dev.alaindustrial.menu.MaceratorMenu;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import dev.alaindustrial.menu.PolymerizerMenu;
import dev.alaindustrial.menu.GardenDroneStationMenu;
import dev.alaindustrial.menu.PumpMenu;
import dev.alaindustrial.menu.IncubatorMenu;
import dev.alaindustrial.menu.SawmillMenu;
import dev.alaindustrial.menu.SilverChestMenu;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.menu.StormWindMillMenu;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.menu.TeleporterStationMenu;
import dev.alaindustrial.menu.WaterMillMenu;
import dev.alaindustrial.menu.WindMillMenu;
import dev.alaindustrial.menu.AlloySmelterMenu;
import dev.alaindustrial.menu.VulcanizerMenu;
import dev.alaindustrial.menu.GalvanicBathMenu;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

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
			// MOD-275 — the assembler: blueprint queue, ghost pattern grid, six-slot output.
			menu("assembler", AssemblerMenu::new, s -> ModContent.ASSEMBLER_MENU = s),
			menu("incubator", IncubatorMenu::new, s -> ModContent.INCUBATOR_MENU = s),
			menu("polymerizer", PolymerizerMenu::new, s -> ModContent.POLYMERIZER_MENU = s),
			// MOD-251 — the distillation column: three tank gauges, warm-up bar, status line.
			menu("distillation_column", DistillationColumnMenu::new,
					s -> ModContent.DISTILLATION_COLUMN_MENU = s),
			menu("vulcanizer", VulcanizerMenu::new, s -> ModContent.VULCANIZER_MENU = s),
			// MOD-064 — the alloy smelter: three interchangeable component slots, one result slot.
			menu("alloy_smelter", AlloySmelterMenu::new, s -> ModContent.ALLOY_SMELTER_MENU = s),
			menu("galvanic_bath", GalvanicBathMenu::new, s -> ModContent.GALVANIC_BATH_MENU = s),
			menu("battery_box", BatteryBoxMenu::new, s -> ModContent.BATTERY_BOX_MENU = s),
			menu("energy_condenser", EnergyCondenserMenu::new, s -> ModContent.ENERGY_CONDENSER_MENU = s),
			menu("cesu", CesuMenu::new, s -> ModContent.CESU_MENU = s),
			menu("teleporter_station", TeleporterStationMenu::new, s -> ModContent.TELEPORTER_STATION_MENU = s),
			menu("teleporter_remote", TeleporterRemoteMenu::new, s -> ModContent.TELEPORTER_REMOTE_MENU = s),
			menu("daylight_solar_panel", DaylightSolarPanelMenu::new, s -> ModContent.DAYLIGHT_SOLAR_PANEL_MENU = s),
			menu("geothermal_generator", GeothermalGeneratorMenu::new, s -> ModContent.GEOTHERMAL_GENERATOR_MENU = s),
			menu("pump", PumpMenu::new, s -> ModContent.PUMP_MENU = s),
			menu("garden_drone_station", GardenDroneStationMenu::new,
					s -> ModContent.GARDEN_DRONE_STATION_MENU = s),
			menu("water_mill", WaterMillMenu::new, s -> ModContent.WATER_MILL_MENU = s),
			menu("wind_mill", WindMillMenu::new, s -> ModContent.WIND_MILL_MENU = s),
			menu("high_altitude_wind_mill", HighAltitudeWindMillMenu::new,
					s -> ModContent.HIGH_ALTITUDE_WIND_MILL_MENU = s),
			menu("storm_wind_mill", StormWindMillMenu::new, s -> ModContent.STORM_WIND_MILL_MENU = s),
			menu("iron_chest", IronChestMenu::new, s -> ModContent.IRON_CHEST_MENU = s),
			// MOD-287 — the warehouse window, one registration per height (3/6/9/12 rows).
			menu("storage_module_3", StorageMenu3::new, s -> ModContent.STORAGE_MODULE_MENU_3 = s),
			menu("storage_module_6", StorageMenu6::new, s -> ModContent.STORAGE_MODULE_MENU_6 = s),
			menu("silver_chest", SilverChestMenu::new, s -> ModContent.SILVER_CHEST_MENU = s),
			menu("gold_chest", GoldChestMenu::new, s -> ModContent.GOLD_CHEST_MENU = s),
			// MOD-391 — the double chest's 6-row scrolling window, one type for all three tiers.
			menu("double_chest", DoubleChestMenu::new, s -> ModContent.DOUBLE_CHEST_MENU = s));

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
			// noOcclusion: the dock is a 4px plate, not a full cube — without it the faces below/around
			// it would be culled as if a solid block sat there.
			Map.entry("garden_drone_station",
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("fluid_tank", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("copper_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("tin_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("gold_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("electrum_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("insulated_copper_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion())),
			Map.entry("insulated_tin_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion())),
			Map.entry("insulated_gold_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion())),
			Map.entry("insulated_electrum_cable", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion())),
			Map.entry("item_pipe", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("fluid_pipe", machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion())),
			Map.entry("macerator", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("battery_box", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.WOOD))),
			// Metal, and tougher than the LV box it is built from — this tier is a steel shell, not a crate.
			Map.entry("cesu", machine(p -> p.strength(4.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("teleporter", machine(p -> p.strength(5.0f, 12.0f).sound(SoundType.METAL))),
			Map.entry("electric_furnace", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("iron_furnace", machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			Map.entry("extractor", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("compressor", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("sawmill", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			// MOD-064: the smelter glows through its crucible windows while melting, so it lights like
			// the other machines with a lit front texture.
			Map.entry("alloy_smelter", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			// MOD-275: the assembler has no lit model, so no lightLevel — a plain metal machine cube.
			Map.entry("assembler", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("polymerizer", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			// MOD-251 — the distillation tower: all three segments share the machine chain, glowing
			// windows while working (litLight). The base drops the item; the segments have no loot.
			// noOcclusion: the tower is a chamfered ~12px column (round-2 voxel models), not a full
			// cube — a non-full collision shape MUST not occlude (R-PHY-05).
			Map.entry("distillation_column", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::litLight))),
			Map.entry("distillation_column_middle", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::litLight))),
			Map.entry("distillation_column_top", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::litLight))),
			Map.entry("rectification_section", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::litLight))),
			Map.entry("galvanic_bath", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("vulcanizer", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			Map.entry("electric_heater", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			// noOcclusion for the same reason as the drone dock: a 4px plate would otherwise cull the
			// faces around it as if a solid cube sat there. The light is four-valued rather than lit/unlit
			// — see ChargePadState.
			Map.entry("charge_pad", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::chargePadLight))),
			// Energy condenser (MOD-393): an open frame, so noOcclusion — otherwise it culls its
			// neighbours' faces as if it were solid, and you would see through the world past the orb.
			// It glows while the bank holds anything, which is also the "it is working" signal.
			Map.entry("energy_condenser", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.noOcclusion().lightLevel(ModBlockProperties::litLight))),
			// The emitter ring lights the chamber while an operation runs, so the block emits too.
			Map.entry("incubator", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight))),
			// The dome is see-through: noOcclusion keeps the chamber (and the item inside) visible.
			// A piston must not take it: the dome is half of a multiblock and its glass is remembered
			// by the base below, so moving it away from its base would strand both.
			Map.entry("incubator_dome", machine(p -> p.strength(1.0f, 2.0f).sound(SoundType.GLASS)
					.noOcclusion().pushReaction(PushReaction.BLOCK))),
			// Cotton trellis (MOD-280) — a plant, not a machine: no requiresCorrectToolForDrops (it comes
			// apart by hand), and randomTicks() is load-bearing rather than decoration — without it the
			// block never receives randomTick and the crop would simply never grow. Deliberately NOT
			// noCollision: the trellis is a structure the player builds, so it blocks movement like a fence
			// post rather than being walked through like wheat. A piston must not drag half a two-block
			// plant away from its other half.
			Map.entry("trellis", p -> p.strength(0.2f).sound(SoundType.GRASS)
					.noOcclusion().randomTicks().pushReaction(PushReaction.DESTROY)),
			Map.entry("tin_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_tin_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("silver_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_silver_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("nickel_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_nickel_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("sulfur_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_sulfur_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("uranium_ore", machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE))),
			Map.entry("deepslate_uranium_ore", machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE))),
			Map.entry("iron_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			// MOD-287 — plain full cube, no noOcclusion(): unlike the chests it has no 3D renderer.
			Map.entry("storage_module", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("silver_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("gold_chest", machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion())),
			Map.entry("tempered_iron_block", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL))),
			// MOD-225: machine casing (crafting base for machines) + two decorative plate blocks.
			Map.entry("machine_casing", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL))),
			// MOD-292: MV casing — tougher than the LV one, it is the tier-up part.
			Map.entry("advanced_machine_casing", machine(p -> p.strength(6.0f, 8.0f).sound(SoundType.METAL))),
			Map.entry("silver_plate_block", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("tempered_iron_plate_block", machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL))),
			Map.entry("industrial_workbench", machine(p -> p.strength(2.5f, 6.0f).sound(SoundType.METAL))),
			Map.entry("enriched_uranium_torch", ModBlockProperties::applyTorch),
			Map.entry("enriched_uranium_wall_torch", ModBlockProperties::applyTorch),
			// Distillation fractions (MOD-251): same vanilla liquid-block chain as oil, their own
			// map colours (diesel golden-yellow, fuel oil dark brown).
			Map.entry("diesel", p -> p.mapColor(MapColor.COLOR_YELLOW).replaceable().noCollision()
					.strength(100.0F).pushReaction(PushReaction.DESTROY).noLootTable().liquid()
					.sound(SoundType.EMPTY)),
			Map.entry("fuel_oil", p -> p.mapColor(MapColor.TERRACOTTA_BROWN).replaceable().noCollision()
					.strength(100.0F).pushReaction(PushReaction.DESTROY).noLootTable().liquid()
					.sound(SoundType.EMPTY)),
			// Oil (MOD-238): the vanilla liquid-block chain (see Blocks.WATER in 26.2), dark map colour.
			// Not machine(...) - a liquid needs no tool and has no drops.
			Map.entry("oil", p -> p.mapColor(MapColor.COLOR_BLACK).replaceable().noCollision()
					.strength(100.0F).pushReaction(PushReaction.DESTROY).noLootTable().liquid()
					.sound(SoundType.EMPTY)));

	/** The shared {@code Properties} chain for {@code id} (see {@link #BLOCK_PROPS}); throws if unknown. */
	public static UnaryOperator<BlockBehaviour.Properties> blockProps(String id) {
		UnaryOperator<BlockBehaviour.Properties> op = BLOCK_PROPS.get(id);
		if (op == null) {
			throw new IllegalArgumentException("No BLOCK_PROPS entry for block id '" + id + "'");
		}
		return op;
	}

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Items (MOD-305 / MOD-306)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * How an item is CONSTRUCTED, declared once for both loaders (MOD-306). Same shape as
	 * {@link #BLOCK_PROPS}: keyed by registry path, looked up by the per-loader registry class.
	 *
	 * <p><b>What this fixes.</b> {@code ModItems} (Fabric) and {@code ModItemsNeoForge} were
	 * line-for-line twins — same ids, same comments, two different registration syntaxes around an
	 * identical construction. Adding an item meant two edits in two files, and nothing but a Python
	 * validator running after the fact stopped the two from drifting apart.
	 *
	 * <p><b>Why a factory over {@code Item.Properties} and not a finished {@code Item}.</b> That is the
	 * one shape both loaders can consume, because they disagree on <i>when</i> and <i>with what</i> the
	 * properties appear:
	 * <ul>
	 *   <li>Fabric registers eagerly and must stamp the id itself — it calls the factory with
	 *       {@code new Item.Properties().setId(key)};</li>
	 *   <li>NeoForge registers lazily through {@code DeferredRegister.Items#registerItem}, which hands
	 *       the factory a {@code Properties} whose id it has already derived from the deferred key.</li>
	 * </ul>
	 * Neither loader can hold a constructed {@code Item} at this point, but both can hold the function
	 * that makes one. Everything else about the item — extra {@code Properties} steps such as
	 * {@code durability(...)}, and the concrete {@code Item} subclass — lives inside the factory, so the
	 * whole definition is one expression here.
	 *
	 * <p><b>Scope, deliberately.</b> Entries here are the items whose construction is genuinely
	 * loader-neutral. Block items are NOT here: their factory needs the loader's own block handle
	 * ({@code ModBlocks.X} is an eager {@code Block}, {@code ModBlocksNeoForge.X} a lazy holder), so a
	 * shared factory would have to close over a loader type — exactly what {@code common} must not do.
	 * The typed field per loader also stays: it is the handle 100+ call sites already use, and MOD-190
	 * settled that trade-off (menus collapse fully, blocks/items keep their field, definition is shared).
	 */
	public static final Map<String, Function<Item.Properties, ? extends Item>> ITEM_FACTORIES =
			buildItemFactories();

	/** The construction function for item {@code id} (see {@link #ITEM_FACTORIES}); throws if unknown. */
	public static Function<Item.Properties, ? extends Item> itemFactory(String id) {
		Function<Item.Properties, ? extends Item> factory = ITEM_FACTORIES.get(id);
		if (factory == null) {
			throw new IllegalArgumentException("No ITEM_FACTORIES entry for item id '" + id + "'");
		}
		return factory;
	}

	/**
	 * Two gray hint lines under the name, keyed {@code item.alaindustrial.<id>.hint} / {@code .hint2}.
	 * The key strings are derived from the id here rather than typed twice per loader.
	 */
	private static Function<Item.Properties, ? extends Item> hintItem(String id) {
		return p -> new HintItem(p, "item.alaindustrial." + id + ".hint",
				"item.alaindustrial." + id + ".hint2");
	}

	/** An overclocker chip of a fixed tier (MOD-393) — a hint item that also carries its step count. */
	private static Function<Item.Properties, ? extends Item> overclockerChip(String id, int tier) {
		return p -> new OverclockerChipItem(p, tier, "item.alaindustrial." + id + ".hint",
				"item.alaindustrial." + id + ".hint2");
	}

	/**
	 * A wearing machine component (MOD-189): {@code durability(max)} sets the vanilla {@code max_damage}
	 * component, so wear renders as the standard durability bar and the item becomes non-stackable.
	 * {@code max} is read from {@link Config} when the item is constructed — i.e. at registration, so a
	 * config change still needs a restart; the wear RATE is read live each tick in the block entity.
	 */
	private static Function<Item.Properties, ? extends Item> durableComponent(IntSupplier maxDamage) {
		return p -> new Item(p.durability(maxDamage.getAsInt()));
	}

	private static Map<String, Function<Item.Properties, ? extends Item>> buildItemFactories() {
		Map<String, Function<Item.Properties, ? extends Item>> defs = new LinkedHashMap<>();
		// Plain items: crafting components, dusts, plates, ingots, raw ores, by-products. Nothing but
		// `new Item(properties)` — the largest and most duplicated group.
		for (String id : List.of(
				"advanced_circuit", "alignment_chip_day", "alignment_chip_night",
				// MOD-064 alloys: the four products of the alloy smelter.
				"bronze_ingot", "cupronickel_ingot", "electrum_ingot", "invar_ingot",
				"coal_dust", "copper_coil", "copper_dust", "copper_plate", "cotton_fiber",
				"cotton_seeds", "depleted_uranium", "diamond_dust", "electronic_circuit",
				"emerald_dust", "flux_thread", "fluxweave_cloth", "garden_drone", "gold_dust",
				"gold_gear", "gold_plate", "iron_dust", "iron_gear", "iron_plate",
				"irradiated_diamond", "irradiated_slag", "lapis_dust", "mutagen_dust",
				"nickel_dust", "nickel_ingot", "nickel_plate", "raw_nickel", "raw_rubber",
				"raw_silver", "raw_sulfur", "raw_tin", "raw_uranium", "resonant_shard", "rubber",
				"silver_dust", "silver_gear", "silver_ingot", "silver_plate", "stone_gear",
				"sulfur_dust", "tempered_iron", "tempered_iron_plate", "tin_dust", "tin_ingot",
				"tin_plate", "unstable_isotope", "uranium_dust", "uranium_ingot", "uranium_plate",
				"wooden_gear")) {
			defs.put(id, Item::new);
		}
		// Battery (MOD-083): the stackable EU carrier. Charge is per item, so the stack size is what
		// keeps stack transfers exact — see BatteryItem for why 16 and not 64.
		defs.put("battery", p -> new BatteryItem(p.stacksTo(BatteryItem.MAX_STACK)));
		// Upgrade chips (MOD-080): the blank and the mute upgrade, each with its hint lines.
		defs.put("empty_chip", hintItem("empty_chip"));
		defs.put("mute_chip", hintItem("mute_chip"));
		defs.put("overclocker_chip_i", overclockerChip("overclocker_chip_i", 1));
		defs.put("overclocker_chip_ii", overclockerChip("overclocker_chip_ii", 2));
		defs.put("overclocker_chip_iii", overclockerChip("overclocker_chip_iii", 3));
		// Energy clots (MOD-393): what the condenser packs surplus grid power into. Three tiers, told
		// apart by how much was banked when the player pulled it out.
		defs.put("energy_clot_i", hintItem("energy_clot_i"));
		defs.put("energy_clot_ii", hintItem("energy_clot_ii"));
		defs.put("energy_clot_iii", hintItem("energy_clot_iii"));
		// Cable breaker (MOD-276): clamps onto a laid cable and cuts the line for maintenance. A hint
		// item because the whole control scheme (install / throw / pry off) is gestures on the wire,
		// with no GUI anywhere to explain itself.
		defs.put("cable_breaker", hintItem("cable_breaker"));
		// Incubator mode chips (MOD-118) — the mode binding lives in the item.
		defs.put("mutation_chip_transform", p -> new MutationChipItem(p, IncubatorMode.TRANSFORM));
		defs.put("mutation_chip_duplicate", p -> new MutationChipItem(p, IncubatorMode.DUPLICATE));
		defs.put("mutation_chip_create", p -> new MutationChipItem(p, IncubatorMode.CREATE));
		// Wearing components (MOD-189).
		defs.put("windmill_rotor", durableComponent(() -> Config.windMillRotorMaxDamage));
		defs.put("water_mill_wheel", durableComponent(() -> Config.waterMillWheelMaxDamage));
		return Map.copyOf(defs);
	}

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// BlockEntity types (MOD-307)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * One {@code BlockEntityType} to register: its id, the {@code BlockEntity} class it produces, the
	 * factory, and the set of blocks it is valid for — <b>by registry id</b>.
	 *
	 * <p><b>Why the blocks are ids and not {@code Block} handles.</b> A handle would have to come from a
	 * loader registry ({@code ModBlocks.X} is an eager {@code Block}; {@code ModBlocksNeoForge.X} a lazy
	 * holder), so the valid-block set had to be written out twice — and the two copies were held together
	 * by nothing but a Python parity script. That is exactly the defect class MOD-191 filed: a block
	 * missing from one loader's set is not a type error, it is a silent "this block entity does not exist
	 * on that loader". With ids, the set is written once and each loader resolves it against the vanilla
	 * registry at its own registration moment.
	 *
	 * @param <T>     the block entity class
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param type    the block entity class, so a lookup can verify the caller's expected type
	 * @param factory the {@code BlockEntity} constructor, shared by both loaders
	 * @param blocks  registry ids of the blocks this type is valid for
	 */
	public record BlockEntityDef<T extends BlockEntity>(String id, Class<T> type,
			BlockEntityType.BlockEntitySupplier<T> factory, List<String> blocks) {

		/**
		 * Resolves {@link #blocks} against the vanilla block registry. Called by each loader when it
		 * builds the {@code BlockEntityType}: on Fabric that is {@code ModBlockEntities.init()} (after
		 * {@code ModBlocks.init()}), on NeoForge it is inside the deferred type supplier — in both cases
		 * the blocks are already registered.
		 *
		 * <p>An unknown id throws instead of quietly resolving to {@code AIR}: a typo here would otherwise
		 * produce a block entity that never attaches to anything, which is precisely the silent failure
		 * this manifest exists to remove.
		 */
		public Set<Block> blockSet() {
			// The strictness below is not belt-and-braces: it replaces guarantees the vanilla/NeoForge
			// varargs constructors used to give and that the Set-taking one does not. An empty block set
			// used to throw; a duplicate block used to throw (Set.of). Losing both silently would leave a
			// BlockEntityType that is valid for nothing — the exact quiet failure this manifest exists to
			// remove.
			if (blocks.isEmpty()) {
				throw new IllegalStateException("BlockEntityDef '" + id
						+ "' lists no blocks — the type would be valid for nothing");
			}
			Set<Block> resolved = new LinkedHashSet<>();
			for (String blockId : blocks) {
				Identifier key = Industrialization.id(blockId);
				// getValue on a DefaultedRegistry substitutes AIR for an unknown key rather than
				// returning null, so AIR — not null — is what an unregistered/misspelt id looks like.
				Block block = BuiltInRegistries.BLOCK.getValue(key);
				if (block == Blocks.AIR) {
					throw new IllegalStateException("BlockEntityDef '" + id + "': block '" + key
							+ "' is not registered (yet) — cannot build its BlockEntityType");
				}
				if (!resolved.add(block)) {
					throw new IllegalStateException("BlockEntityDef '" + id + "': block '" + key
							+ "' listed twice");
				}
			}
			return resolved;
		}
	}

	private static <T extends BlockEntity> BlockEntityDef<T> blockEntity(String id, Class<T> type,
			BlockEntityType.BlockEntitySupplier<T> factory, String... blocks) {
		return new BlockEntityDef<>(id, type, factory, List.of(blocks));
	}

	/** Every {@code BlockEntityType}, declared once for both loaders. See {@link BlockEntityDef}. */
	public static final List<BlockEntityDef<?>> BLOCK_ENTITIES = List.of(
			blockEntity("generator", GeneratorBlockEntity.class, GeneratorBlockEntity::new, "generator"),
			blockEntity("geothermal_generator", GeothermalGeneratorBlockEntity.class, GeothermalGeneratorBlockEntity::new, "geothermal_generator"),
			blockEntity("solar_panel", SolarPanelBlockEntity.class, SolarPanelBlockEntity::new, "solar_panel"),
			blockEntity("moonlit_solar_panel", MoonlitSolarPanelBlockEntity.class, MoonlitSolarPanelBlockEntity::new, "moonlit_solar_panel"),
			blockEntity("daylight_solar_panel", DaylightSolarPanelBlockEntity.class, DaylightSolarPanelBlockEntity::new, "daylight_solar_panel"),
			blockEntity("copper_cable", CableBlockEntity.class, CableBlockEntity::new, "copper_cable", "tin_cable", "gold_cable", "electrum_cable", "insulated_copper_cable", "insulated_tin_cable", "insulated_gold_cable", "insulated_electrum_cable"),
			blockEntity("item_pipe", ItemPipeBlockEntity.class, ItemPipeBlockEntity::new, "item_pipe"),
			blockEntity("fluid_pipe", FluidPipeBlockEntity.class, FluidPipeBlockEntity::new, "fluid_pipe"),
			blockEntity("macerator", MaceratorBlockEntity.class, MaceratorBlockEntity::new, "macerator"),
			blockEntity("battery_box", BatteryBoxBlockEntity.class, BatteryBoxBlockEntity::new, "battery_box"),
			blockEntity("cesu", CesuBlockEntity.class, CesuBlockEntity::new, "cesu"),
			blockEntity("teleporter", TeleporterBlockEntity.class, TeleporterBlockEntity::new, "teleporter"),
			blockEntity("electric_furnace", ElectricFurnaceBlockEntity.class, ElectricFurnaceBlockEntity::new, "electric_furnace"),
			blockEntity("iron_furnace", IronFurnaceBlockEntity.class, IronFurnaceBlockEntity::new, "iron_furnace"),
			blockEntity("extractor", ExtractorBlockEntity.class, ExtractorBlockEntity::new, "extractor"),
			blockEntity("compressor", CompressorBlockEntity.class, CompressorBlockEntity::new, "compressor"),
			blockEntity("sawmill", SawmillBlockEntity.class, SawmillBlockEntity::new, "sawmill"),
			blockEntity("assembler", AssemblerBlockEntity.class, AssemblerBlockEntity::new, "assembler"),
			blockEntity("polymerizer", PolymerizerBlockEntity.class, PolymerizerBlockEntity::new, "polymerizer"),
			// MOD-251 — the tower: master BE on the base, one shared proxy type on both segments.
			blockEntity("distillation_column", DistillationColumnBlockEntity.class,
					DistillationColumnBlockEntity::new, "distillation_column"),
			blockEntity("distillation_column_segment", DistillationColumnSegmentBlockEntity.class,
					DistillationColumnSegmentBlockEntity::new,
					"distillation_column_middle", "distillation_column_top"),
			blockEntity("vulcanizer", VulcanizerBlockEntity.class, VulcanizerBlockEntity::new, "vulcanizer"),
			blockEntity("alloy_smelter", AlloySmelterBlockEntity.class, AlloySmelterBlockEntity::new, "alloy_smelter"),
			blockEntity("galvanic_bath", GalvanicBathBlockEntity.class, GalvanicBathBlockEntity::new, "galvanic_bath"),
			blockEntity("electric_heater", ElectricHeaterBlockEntity.class, ElectricHeaterBlockEntity::new, "electric_heater"),
			blockEntity("charge_pad", ChargePadBlockEntity.class, ChargePadBlockEntity::new, "charge_pad"),
			blockEntity("energy_condenser", EnergyCondenserBlockEntity.class,
					EnergyCondenserBlockEntity::new, "energy_condenser"),
			blockEntity("incubator", IncubatorBlockEntity.class, IncubatorBlockEntity::new, "incubator"),
			blockEntity("pump", PumpBlockEntity.class, PumpBlockEntity::new, "pump"),
			blockEntity("garden_drone_station", GardenDroneStationBlockEntity.class, GardenDroneStationBlockEntity::new, "garden_drone_station"),
			blockEntity("fluid_tank", FluidTankBlockEntity.class, FluidTankBlockEntity::new, "fluid_tank"),
			blockEntity("water_mill", WaterMillBlockEntity.class, WaterMillBlockEntity::new, "water_mill"),
			blockEntity("wind_mill", WindMillBlockEntity.class, WindMillBlockEntity::new, "wind_mill"),
			blockEntity("high_altitude_wind_mill", HighAltitudeWindMillBlockEntity.class, HighAltitudeWindMillBlockEntity::new, "high_altitude_wind_mill"),
			blockEntity("storm_wind_mill", StormWindMillBlockEntity.class, StormWindMillBlockEntity::new, "storm_wind_mill"),
			blockEntity("iron_chest", IronChestBlockEntity.class, IronChestBlockEntity::new, "iron_chest"),
			blockEntity("storage_module", StorageModuleBlockEntity.class, StorageModuleBlockEntity::new, "storage_module"),
			blockEntity("silver_chest", SilverChestBlockEntity.class, SilverChestBlockEntity::new, "silver_chest"),
			blockEntity("gold_chest", GoldChestBlockEntity.class, GoldChestBlockEntity::new, "gold_chest"));

	/**
	 * The definition for block-entity {@code id}, checked against the type the caller expects.
	 *
	 * <p>The {@code Class} argument is what keeps the loader's typed field honest: asking for
	 * {@code blockEntity("macerator", SawmillBlockEntity.class)} fails loudly here instead of producing a
	 * {@code BlockEntityType} whose generic parameter lies about what it creates.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends BlockEntity> BlockEntityDef<T> blockEntity(String id, Class<T> type) {
		for (BlockEntityDef<?> def : BLOCK_ENTITIES) {
			if (def.id().equals(id)) {
				if (def.type() != type) {
					throw new IllegalArgumentException("BlockEntityDef '" + id + "' produces "
							+ def.type().getSimpleName() + ", not " + type.getSimpleName());
				}
				return (BlockEntityDef<T>) def;
			}
		}
		throw new IllegalArgumentException("No BLOCK_ENTITIES entry for block-entity id '" + id + "'");
	}

	/**
	 * Does an overclocker chip do anything in {@code block}? Answered from the block alone, so the
	 * upgrade panel can refuse the chip on the CLIENT, where the menu is backed by a dummy container
	 * with no block entity to ask (MOD-392: a generator used to accept the chip and then ignore it).
	 *
	 * <p>Derived from {@link Overclockable} on the block entity class rather than from a second,
	 * hand-written list of blocks: the manifest already maps blocks to their BE class, so there is
	 * exactly one place to declare that a machine overclocks, and the slot cannot drift from the effect.
	 * Blocks with no block entity at all — every plain building block — answer {@code false}.
	 */
	public static boolean isOverclockable(Block block) {
		Identifier key = BuiltInRegistries.BLOCK.getKey(block);
		if (!Industrialization.MOD_ID.equals(key.getNamespace())) {
			return false;
		}
		String path = key.getPath();
		for (BlockEntityDef<?> def : BLOCK_ENTITIES) {
			if (def.blocks().contains(path)) {
				return Overclockable.class.isAssignableFrom(def.type());
			}
		}
		return false;
	}
}
