package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.BatteryBoxBlock;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.CompressorBlock;
import dev.alaindustrial.block.DaylightSolarPanelBlock;
import dev.alaindustrial.block.ElectricFurnaceBlock;
import dev.alaindustrial.block.EnrichedUraniumTorchBlock;
import dev.alaindustrial.block.EnrichedUraniumWallTorchBlock;
import dev.alaindustrial.block.ExtractorBlock;
import dev.alaindustrial.block.GeneratorBlock;
import dev.alaindustrial.block.GeothermalGeneratorBlock;
import dev.alaindustrial.block.IronChestBlock;
import dev.alaindustrial.block.IronFurnaceBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import dev.alaindustrial.block.MaceratorBlock;
import dev.alaindustrial.block.SilverChestBlock;
import dev.alaindustrial.block.GoldChestBlock;
import dev.alaindustrial.block.MoonlitSolarPanelBlock;
import dev.alaindustrial.block.PumpBlock;
import dev.alaindustrial.block.FluidTankBlock;
import dev.alaindustrial.block.SolarPanelBlock;
import dev.alaindustrial.block.WaterMillBlock;
import dev.alaindustrial.block.WindMillBlock;
import dev.alaindustrial.block.HighAltitudeWindMillBlock;
import dev.alaindustrial.block.StormWindMillBlock;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModParticles;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge block registration (MOD-022 registration-facade). Mirrors the Fabric
 * {@code dev.alaindustrial.registry.ModBlocks} set 1:1 (same ids, same block subclasses, same
 * {@code BlockBehaviour.Properties}) using NeoForge's {@link DeferredRegister.Blocks} — the real
 * content classes from {@code common}, not stubs.
 *
 * <p><b>Geothermal generator and pump (MOD-028).</b> Both now live in {@code common} — their block
 * entities moved off the Fabric fluid Transfer API onto the neutral {@code FluidPort}/{@code FluidTank}
 * abstraction, so they are registered here like every other machine.
 *
 * <p><b>Split constraint (verified 26.2 API):</b> the {@code DeferredRegister} object and its
 * {@code register(modBus)} call must live on the {@code neoforge} side.
 *
 * <p><b>Verified against neoforge-26.2.0.8-beta.</b> {@code DeferredRegister.Blocks#registerBlock(
 * String, Function&lt;Properties, ? extends B&gt;, Supplier&lt;Properties&gt;)} applies
 * {@code Properties.setId(...)} automatically from the deferred key, so the factory takes bare
 * {@code Properties} — exactly the {@code (Properties)} constructor each block subclass exposes. The
 * Fabric side calls {@code Properties.of().setId(key)...} explicitly; here {@code setId} is applied by
 * the register, so the property {@link Supplier} below only carries the behaviour (strength/sound/etc.),
 * matching the Fabric {@code props()} helper (which adds {@code requiresCorrectToolForDrops()} to every
 * block).
 */
public final class ModBlocksNeoForge {
	public static final DeferredRegister.Blocks BLOCKS =
			DeferredRegister.createBlocks(Industrialization.MOD_ID);

	// --- Machines (energy core + processing) ---
	public static final DeferredBlock<GeneratorBlock> GENERATOR =
			BLOCKS.registerBlock("generator", GeneratorBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)));
	public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL =
			BLOCKS.registerBlock("solar_panel", SolarPanelBlock::new, machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion()));
	public static final DeferredBlock<MoonlitSolarPanelBlock> MOONLIT_SOLAR_PANEL =
			BLOCKS.registerBlock("moonlit_solar_panel", MoonlitSolarPanelBlock::new, machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion()));
	public static final DeferredBlock<DaylightSolarPanelBlock> DAYLIGHT_SOLAR_PANEL =
			BLOCKS.registerBlock("daylight_solar_panel", DaylightSolarPanelBlock::new, machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.GLASS).noOcclusion()));
	public static final DeferredBlock<MaceratorBlock> MACERATOR =
			BLOCKS.registerBlock("macerator", MaceratorBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)));
	public static final DeferredBlock<BatteryBoxBlock> BATTERY_BOX =
			BLOCKS.registerBlock("battery_box", BatteryBoxBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.WOOD)));

	// Teleporter station (MOD-091). Registered but kept out of the creative tab and recipe set until
	// the feature is complete (MOD-093) — see CreativeTabContent.
	public static final DeferredBlock<TeleporterBlock> TELEPORTER =
			BLOCKS.registerBlock("teleporter", TeleporterBlock::new, machine(p -> p.strength(5.0f, 12.0f).sound(SoundType.METAL)));
	public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE =
			BLOCKS.registerBlock("electric_furnace", ElectricFurnaceBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)));
	// Iron Furnace (MOD-115) — fuel-burning smelter between stone and electric; lit glows (light 13).
	public static final DeferredBlock<IronFurnaceBlock> IRON_FURNACE =
			BLOCKS.registerBlock("iron_furnace", IronFurnaceBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.METAL)
							.lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 13 : 0)));
	public static final DeferredBlock<ExtractorBlock> EXTRACTOR =
			BLOCKS.registerBlock("extractor", ExtractorBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)));
	public static final DeferredBlock<CompressorBlock> COMPRESSOR =
			BLOCKS.registerBlock("compressor", CompressorBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)));
	public static final DeferredBlock<GeothermalGeneratorBlock> GEOTHERMAL_GENERATOR =
			BLOCKS.registerBlock("geothermal_generator", GeothermalGeneratorBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)));
	public static final DeferredBlock<PumpBlock> PUMP =
			BLOCKS.registerBlock("pump", PumpBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)));
	public static final DeferredBlock<FluidTankBlock> FLUID_TANK =
			BLOCKS.registerBlock("fluid_tank", FluidTankBlock::new,
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()));
	public static final DeferredBlock<WaterMillBlock> WATER_MILL =
			BLOCKS.registerBlock("water_mill", WaterMillBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)));
	public static final DeferredBlock<WindMillBlock> WIND_MILL =
			BLOCKS.registerBlock("wind_mill", WindMillBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)));
	public static final DeferredBlock<HighAltitudeWindMillBlock> HIGH_ALTITUDE_WIND_MILL =
			BLOCKS.registerBlock("high_altitude_wind_mill", HighAltitudeWindMillBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)));
	public static final DeferredBlock<StormWindMillBlock> STORM_WIND_MILL =
			BLOCKS.registerBlock("storm_wind_mill", StormWindMillBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)));

	// --- Cables ---
	public static final DeferredBlock<CableBlock> COPPER_CABLE =
			BLOCKS.registerBlock("copper_cable", CableBlock::new, machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()));
	public static final DeferredBlock<CableBlock> TIN_CABLE =
			BLOCKS.registerBlock("tin_cable", CableBlock::new, machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()));
	public static final DeferredBlock<CableBlock> INSULATED_COPPER_CABLE =
			BLOCKS.registerBlock("insulated_copper_cable", CableBlock::new, machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion()));
	public static final DeferredBlock<CableBlock> INSULATED_TIN_CABLE =
			BLOCKS.registerBlock("insulated_tin_cable", CableBlock::new, machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion()));
	public static final DeferredBlock<ItemPipeBlock> ITEM_PIPE =
			BLOCKS.registerBlock("item_pipe", ItemPipeBlock::new, machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()));

	// --- Ores (plain Block, harvest tier is tag-driven — see ModBlocks#props) ---
	public static final DeferredBlock<Block> TIN_ORE =
			BLOCKS.registerBlock("tin_ore", Block::new, machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE)));
	public static final DeferredBlock<Block> DEEPSLATE_TIN_ORE =
			BLOCKS.registerBlock("deepslate_tin_ore", Block::new, machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)));
	public static final DeferredBlock<Block> SILVER_ORE =
			BLOCKS.registerBlock("silver_ore", Block::new, machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE)));
	public static final DeferredBlock<Block> DEEPSLATE_SILVER_ORE =
			BLOCKS.registerBlock("deepslate_silver_ore", Block::new, machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)));
	public static final DeferredBlock<Block> NICKEL_ORE =
			BLOCKS.registerBlock("nickel_ore", Block::new, machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE)));
	public static final DeferredBlock<Block> DEEPSLATE_NICKEL_ORE =
			BLOCKS.registerBlock("deepslate_nickel_ore", Block::new, machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)));
	public static final DeferredBlock<Block> URANIUM_ORE =
			BLOCKS.registerBlock("uranium_ore", Block::new, machine(p -> p.strength(3.0f, 3.0f).sound(SoundType.STONE)));
	public static final DeferredBlock<Block> DEEPSLATE_URANIUM_ORE =
			BLOCKS.registerBlock("deepslate_uranium_ore", Block::new, machine(p -> p.strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE)));

	// --- Storage (pure container, no energy) ---
	public static final DeferredBlock<IronChestBlock> IRON_CHEST =
			BLOCKS.registerBlock("iron_chest", IronChestBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()));
	// Silver Chest (MOD-087) — the tier above the iron chest: 45 slots (5×9). Same block stats as the
	// iron chest (same chest shape: strength 3.0/6.0, METAL, noOcclusion).
	public static final DeferredBlock<SilverChestBlock> SILVER_CHEST =
			BLOCKS.registerBlock("silver_chest", SilverChestBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()));
	// Gold Chest (MOD-088) — the tier above the silver chest: 54 slots (6×9). Same block stats.
	public static final DeferredBlock<GoldChestBlock> GOLD_CHEST =
			BLOCKS.registerBlock("gold_chest", GoldChestBlock::new, machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()));

	// Tempered Iron Block — "block of X" material block (9 ingots ↔ 1 block), like
	// vanilla iron block. Plain Block, cube_all model, single texture on all 6 faces.
	// Strength/sound mirror vanilla iron_block (5.0 / 6.0, METAL).
	public static final DeferredBlock<Block> TEMPERED_IRON_BLOCK =
			BLOCKS.registerBlock("tempered_iron_block", Block::new, machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)));

	// Enriched Uranium Torch (MOD-085) — vanilla-behaviour torch, light 15, green flame. Uses torch()
	// (vanilla TORCH properties), NOT machine() — a torch breaks by hand, so no requiresCorrectToolForDrops
	// and no mineable/pickaxe tag entry. The particle comes from the eager ModParticles facade object.
	public static final DeferredBlock<EnrichedUraniumTorchBlock> ENRICHED_URANIUM_TORCH =
			BLOCKS.registerBlock("enriched_uranium_torch",
					p -> new EnrichedUraniumTorchBlock(ModParticles.ENRICHED_URANIUM_FLAME, p), torch());

	// Wall variant: drops/names from the standing torch (vanilla wallVariant). The property supplier runs
	// during the block RegisterEvent, by which point the earlier ENRICHED_URANIUM_TORCH entry is resolved
	// (same ordering the FILLED_VACUUM_CAPSULE craftRemainder relies on).
	public static final DeferredBlock<EnrichedUraniumWallTorchBlock> ENRICHED_URANIUM_WALL_TORCH =
			BLOCKS.registerBlock("enriched_uranium_wall_torch",
					p -> new EnrichedUraniumWallTorchBlock(ModParticles.ENRICHED_URANIUM_FLAME, p),
					() -> torchProps()
							.overrideLootTable(ENRICHED_URANIUM_TORCH.get().getLootTable())
							.overrideDescription(ENRICHED_URANIUM_TORCH.get().getDescriptionId()));

	private ModBlocksNeoForge() {
	}

	/**
	 * Property {@link Supplier} matching the Fabric {@code ModBlocks#props()} helper: every block
	 * {@code requiresCorrectToolForDrops()} (pickaxe needed to drop; tier gate is tag-driven), plus the
	 * per-block behaviour applied by {@code extra}. {@code setId} is NOT set here — the NeoForge
	 * {@code registerBlock(...)} applies it from the deferred key (verified 26.2.0.8-beta).
	 */
	private static Supplier<BlockBehaviour.Properties> machine(UnaryOperator<BlockBehaviour.Properties> extra) {
		return () -> extra.apply(BlockBehaviour.Properties.of().requiresCorrectToolForDrops());
	}

	/**
	 * Property {@link Supplier} for the Enriched Uranium Torch (MOD-085), mirroring the Fabric
	 * {@code ModBlocks#torchProps} helper and vanilla {@code Blocks.TORCH}: no collision, instant break
	 * (breaks by hand — so NO {@code requiresCorrectToolForDrops} and NO {@code mineable/pickaxe} entry,
	 * unlike {@link #machine}), light 14 (identical to the vanilla torch), WOOD sound, {@code DESTROY} push
	 * reaction, {@code noOcclusion()}. {@code setId} is applied by {@code registerBlock} from the deferred key.
	 */
	private static Supplier<BlockBehaviour.Properties> torch() {
		return ModBlocksNeoForge::torchProps;
	}

	private static BlockBehaviour.Properties torchProps() {
		return BlockBehaviour.Properties.of().noCollision().instabreak()
				.lightLevel(state -> 14).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY).noOcclusion();
	}

	/**
	 * Bind each block {@code DeferredBlock} into the loader-neutral {@link ModContent} facade, mirroring
	 * {@code dev.alaindustrial.registry.ModBlocks#init()} on the Fabric side. A {@code DeferredBlock}
	 * <b>is</b> a {@code Supplier<Block>} ({@code DeferredBlock<T> extends DeferredHolder<Block, T>
	 * implements Supplier<T>}, verified against neoforge-26.2.0.8-beta), so it is assigned directly — no
	 * {@code () -> value} wrapper, unlike Fabric — and resolves lazily after this register's
	 * {@code RegisterEvent} fires. Called from the {@code @Mod} constructor after
	 * {@code BLOCKS.register(modBus)}; assigning the lazy handle before the event fires is intentional.
	 *
	 * <p><b>Why {@code HOLDER::get}, not the holder directly.</b> A {@code DeferredBlock<GeneratorBlock>}
	 * is a {@code Supplier<GeneratorBlock>}, but the {@link ModContent} slot is {@code Supplier<Block>};
	 * Java generics are invariant, so the holder cannot be assigned straight in. The method reference
	 * {@code HOLDER::get} is a {@code Supplier<Block>} (its {@code get()} returns the subtype, widened by
	 * the target type) and is just as lazy — it still resolves only when invoked at runtime, after the
	 * {@code RegisterEvent} fires. It is not the throwing {@code Unbound} placeholder, so
	 * {@code verifyAllBound()} correctly counts the slot as bound.
	 */
	public static void init() {
		ModContent.GENERATOR = GENERATOR::get;
		ModContent.SOLAR_PANEL = SOLAR_PANEL::get;
		ModContent.MOONLIT_SOLAR_PANEL = MOONLIT_SOLAR_PANEL::get;
		ModContent.DAYLIGHT_SOLAR_PANEL = DAYLIGHT_SOLAR_PANEL::get;
		ModContent.MACERATOR = MACERATOR::get;
		ModContent.BATTERY_BOX = BATTERY_BOX::get;
		ModContent.TELEPORTER = TELEPORTER::get;
		ModContent.ELECTRIC_FURNACE = ELECTRIC_FURNACE::get;
		ModContent.IRON_FURNACE = IRON_FURNACE::get;
		ModContent.EXTRACTOR = EXTRACTOR::get;
		ModContent.COMPRESSOR = COMPRESSOR::get;
		ModContent.GEOTHERMAL_GENERATOR = GEOTHERMAL_GENERATOR::get;
		ModContent.PUMP = PUMP::get;
		ModContent.FLUID_TANK = FLUID_TANK::get;
		ModContent.WATER_MILL = WATER_MILL::get;
		ModContent.WIND_MILL = WIND_MILL::get;
		ModContent.HIGH_ALTITUDE_WIND_MILL = HIGH_ALTITUDE_WIND_MILL::get;
		ModContent.STORM_WIND_MILL = STORM_WIND_MILL::get;
		ModContent.COPPER_CABLE = COPPER_CABLE::get;
		ModContent.TIN_CABLE = TIN_CABLE::get;
		ModContent.INSULATED_COPPER_CABLE = INSULATED_COPPER_CABLE::get;
		ModContent.INSULATED_TIN_CABLE = INSULATED_TIN_CABLE::get;
		ModContent.ITEM_PIPE = ITEM_PIPE::get;
		ModContent.TIN_ORE = TIN_ORE::get;
		ModContent.DEEPSLATE_TIN_ORE = DEEPSLATE_TIN_ORE::get;
		ModContent.SILVER_ORE = SILVER_ORE::get;
		ModContent.DEEPSLATE_SILVER_ORE = DEEPSLATE_SILVER_ORE::get;
		ModContent.NICKEL_ORE = NICKEL_ORE::get;
		ModContent.DEEPSLATE_NICKEL_ORE = DEEPSLATE_NICKEL_ORE::get;
		ModContent.URANIUM_ORE = URANIUM_ORE::get;
		ModContent.DEEPSLATE_URANIUM_ORE = DEEPSLATE_URANIUM_ORE::get;
		ModContent.IRON_CHEST = IRON_CHEST::get;
		ModContent.SILVER_CHEST = SILVER_CHEST::get;
		ModContent.GOLD_CHEST = GOLD_CHEST::get;
		ModContent.TEMPERED_IRON_BLOCK = TEMPERED_IRON_BLOCK::get;
		ModContent.ENRICHED_URANIUM_TORCH = ENRICHED_URANIUM_TORCH::get;
		ModContent.ENRICHED_URANIUM_WALL_TORCH = ENRICHED_URANIUM_WALL_TORCH::get;
	}
}
