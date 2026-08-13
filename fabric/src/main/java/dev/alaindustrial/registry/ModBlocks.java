package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Fabric block registration: a replay of the shared {@link ContentManifest#BLOCKS} list, plus the typed
 * handles the rest of the Fabric source set reads.
 *
 * <p><b>MOD-190 → MOD-403.</b> MOD-190 moved each block's {@code BlockBehaviour.Properties} chain into
 * {@link ContentManifest#BLOCK_PROPS}. MOD-403 moved the <b>list</b> too: which blocks exist, in what
 * order, built by which factory, bound to which {@link ModContent} handle. Before that, this file and
 * {@code ModBlocksNeoForge} each carried their own copy of all of it, and the only thing keeping the two
 * in step was a Python set-comparison run after the fact — a block added on one loader and forgotten on
 * the other compiled and shipped.
 *
 * <p><b>What is left here is the Fabric registration MECHANISM, and only that:</b> eager construction,
 * the {@code setId(key)} the loader has to stamp itself, and {@code Registry.register}. The id, the
 * factory, the properties lookup and the {@code ModContent} binding all come from the manifest entry — so
 * a block can no longer receive another block's properties, and the 72 hand-written
 * {@code ModContent.X = () -> X;} lines that used to close {@code init()} are gone with the failure mode
 * they carried (a missing one surfaced only as a {@code verifyAllBound()} crash at startup).
 *
 * <p><b>The typed fields below are handles, not registrations.</b> They exist because ~100 Fabric call
 * sites (block items, gametests, REI compat, the entrypoint) read {@code ModBlocks.X}; adding a block does
 * NOT require adding one. Each is looked up from its manifest entry rather than from a string, so it
 * cannot point at an id that no longer exists.
 */
public final class ModBlocks {
	private ModBlocks() {
	}

	/**
	 * Every block, registered the moment this class loads. Declared FIRST on purpose: static fields
	 * initialise in textual order, so this runs before the handles below read it.
	 */
	private static final Map<String, Block> REGISTERED = registerAll();

	public static final Block GENERATOR = handle(ContentManifest.GENERATOR);
	public static final Block SOLAR_PANEL = handle(ContentManifest.SOLAR_PANEL);
	public static final Block MOONLIT_SOLAR_PANEL = handle(ContentManifest.MOONLIT_SOLAR_PANEL);
	public static final Block DAYLIGHT_SOLAR_PANEL = handle(ContentManifest.DAYLIGHT_SOLAR_PANEL);
	public static final Block GEOTHERMAL_GENERATOR = handle(ContentManifest.GEOTHERMAL_GENERATOR);
	public static final Block WATER_MILL = handle(ContentManifest.WATER_MILL);
	public static final Block WIND_MILL = handle(ContentManifest.WIND_MILL);
	public static final Block HIGH_ALTITUDE_WIND_MILL = handle(ContentManifest.HIGH_ALTITUDE_WIND_MILL);
	public static final Block STORM_WIND_MILL = handle(ContentManifest.STORM_WIND_MILL);
	public static final Block PUMP = handle(ContentManifest.PUMP);
	public static final Block GARDEN_DRONE_STATION = handle(ContentManifest.GARDEN_DRONE_STATION);
	public static final Block FLUID_TANK = handle(ContentManifest.FLUID_TANK);
	public static final Block COPPER_CABLE = handle(ContentManifest.COPPER_CABLE);
	public static final Block TIN_CABLE = handle(ContentManifest.TIN_CABLE);
	public static final Block GOLD_CABLE = handle(ContentManifest.GOLD_CABLE);
	public static final Block ELECTRUM_CABLE = handle(ContentManifest.ELECTRUM_CABLE);
	public static final Block INSULATED_COPPER_CABLE = handle(ContentManifest.INSULATED_COPPER_CABLE);
	public static final Block INSULATED_TIN_CABLE = handle(ContentManifest.INSULATED_TIN_CABLE);
	public static final Block INSULATED_GOLD_CABLE = handle(ContentManifest.INSULATED_GOLD_CABLE);
	public static final Block INSULATED_ELECTRUM_CABLE = handle(ContentManifest.INSULATED_ELECTRUM_CABLE);
	public static final Block ITEM_PIPE = handle(ContentManifest.ITEM_PIPE);
	public static final Block FLUID_PIPE = handle(ContentManifest.FLUID_PIPE);
	public static final Block MACERATOR = handle(ContentManifest.MACERATOR);
	public static final Block BATTERY_BOX = handle(ContentManifest.BATTERY_BOX);
	public static final Block CESU = handle(ContentManifest.CESU);
	public static final Block TELEPORTER = handle(ContentManifest.TELEPORTER);
	public static final Block ELECTRIC_FURNACE = handle(ContentManifest.ELECTRIC_FURNACE);
	public static final Block IRON_FURNACE = handle(ContentManifest.IRON_FURNACE);
	public static final Block EXTRACTOR = handle(ContentManifest.EXTRACTOR);
	public static final Block COMPRESSOR = handle(ContentManifest.COMPRESSOR);
	public static final Block CANNING_MACHINE = handle(ContentManifest.CANNING_MACHINE);
	public static final Block SAWMILL = handle(ContentManifest.SAWMILL);
	public static final Block ASSEMBLER = handle(ContentManifest.ASSEMBLER);
	public static final Block POLYMERIZER = handle(ContentManifest.POLYMERIZER);
	public static final Block DISTILLATION_COLUMN = handle(ContentManifest.DISTILLATION_COLUMN);
	public static final Block DISTILLATION_COLUMN_MIDDLE = handle(ContentManifest.DISTILLATION_COLUMN_MIDDLE);
	public static final Block DISTILLATION_COLUMN_TOP = handle(ContentManifest.DISTILLATION_COLUMN_TOP);
	public static final Block RECTIFICATION_SECTION = handle(ContentManifest.RECTIFICATION_SECTION);
	public static final Block ALLOY_SMELTER = handle(ContentManifest.ALLOY_SMELTER);
	public static final Block VULCANIZER = handle(ContentManifest.VULCANIZER);
	public static final Block GALVANIC_BATH = handle(ContentManifest.GALVANIC_BATH);
	public static final Block ELECTRIC_HEATER = handle(ContentManifest.ELECTRIC_HEATER);
	public static final Block CHARGE_PAD = handle(ContentManifest.CHARGE_PAD);
	public static final Block ENERGY_CONDENSER = handle(ContentManifest.ENERGY_CONDENSER);
	public static final Block MOB_REPELLER = handle(ContentManifest.MOB_REPELLER);
	public static final Block MOB_REPELLER_MV = handle(ContentManifest.MOB_REPELLER_MV);
	public static final Block MOB_REPELLER_HV = handle(ContentManifest.MOB_REPELLER_HV);
	public static final Block INCUBATOR = handle(ContentManifest.INCUBATOR);
	public static final Block INCUBATOR_DOME = handle(ContentManifest.INCUBATOR_DOME);
	public static final Block TRELLIS = handle(ContentManifest.TRELLIS);
	public static final Block TIN_ORE = handle(ContentManifest.TIN_ORE);
	public static final Block DEEPSLATE_TIN_ORE = handle(ContentManifest.DEEPSLATE_TIN_ORE);
	public static final Block SILVER_ORE = handle(ContentManifest.SILVER_ORE);
	public static final Block DEEPSLATE_SILVER_ORE = handle(ContentManifest.DEEPSLATE_SILVER_ORE);
	public static final Block NICKEL_ORE = handle(ContentManifest.NICKEL_ORE);
	public static final Block DEEPSLATE_NICKEL_ORE = handle(ContentManifest.DEEPSLATE_NICKEL_ORE);
	public static final Block SULFUR_ORE = handle(ContentManifest.SULFUR_ORE);
	public static final Block DEEPSLATE_SULFUR_ORE = handle(ContentManifest.DEEPSLATE_SULFUR_ORE);
	public static final Block URANIUM_ORE = handle(ContentManifest.URANIUM_ORE);
	public static final Block DEEPSLATE_URANIUM_ORE = handle(ContentManifest.DEEPSLATE_URANIUM_ORE);
	public static final Block IRON_CHEST = handle(ContentManifest.IRON_CHEST);
	public static final Block STORAGE_MODULE = handle(ContentManifest.STORAGE_MODULE);
	public static final Block SILVER_CHEST = handle(ContentManifest.SILVER_CHEST);
	public static final Block GOLD_CHEST = handle(ContentManifest.GOLD_CHEST);
	public static final Block ELECTRUM_CHEST = handle(ContentManifest.ELECTRUM_CHEST);
	public static final Block TEMPERED_IRON_BLOCK = handle(ContentManifest.TEMPERED_IRON_BLOCK);
	public static final Block MACHINE_CASING = handle(ContentManifest.MACHINE_CASING);
	public static final Block ADVANCED_MACHINE_CASING = handle(ContentManifest.ADVANCED_MACHINE_CASING);
	public static final Block SILVER_PLATE_BLOCK = handle(ContentManifest.SILVER_PLATE_BLOCK);
	public static final Block TEMPERED_IRON_PLATE_BLOCK = handle(ContentManifest.TEMPERED_IRON_PLATE_BLOCK);
	public static final Block INDUSTRIAL_WORKBENCH = handle(ContentManifest.INDUSTRIAL_WORKBENCH);
	public static final Block ENRICHED_URANIUM_TORCH = handle(ContentManifest.ENRICHED_URANIUM_TORCH);
	public static final Block ENRICHED_URANIUM_WALL_TORCH = handle(ContentManifest.ENRICHED_URANIUM_WALL_TORCH);
	public static final Block OIL = handle(ContentManifest.OIL);
	public static final Block DIESEL = handle(ContentManifest.DIESEL);
	public static final Block FUEL_OIL = handle(ContentManifest.FUEL_OIL);

	/**
	 * Registers every {@link ContentManifest#BLOCKS} entry, in list order, and binds each one into
	 * {@link ModContent}.
	 *
	 * <p><b>Fluids first (MOD-250).</b> A {@code Block} constructor builds its state cache, and that cache
	 * calls {@code getFluidState} on every state — so an oil-loggable block (the enriched uranium torches)
	 * reads {@code ModContent.OIL} while it is being constructed, and the three liquid blocks read their
	 * fluid outright. {@code ModFluids.init()} is idempotent and binds those handles, so calling it before
	 * the first factory runs is what makes the manifest's loader-neutral liquid factories legal here.
	 */
	private static Map<String, Block> registerAll() {
		ModFluids.init();
		Map<String, Block> registered = new LinkedHashMap<>();
		for (ContentManifest.BlockDef<?> def : ContentManifest.BLOCKS) {
			if (registered.put(def.id(), register(def)) != null) {
				throw new IllegalStateException(
						"ContentManifest.BLOCKS declares block id '" + def.id() + "' twice");
			}
		}
		return Map.copyOf(registered);
	}

	/**
	 * One manifest entry, the Fabric way: build the {@code Properties} from the shared chain on a base
	 * carrying the Fabric-only {@code setId}, construct eagerly, register, and publish the result into the
	 * entry's {@link ModContent} slot as a constant supplier ({@code () -> value}) — NeoForge instead binds
	 * its lazy {@code DeferredHolder} into the same slot.
	 *
	 * <p>Binding here rather than in a later {@code init()} is what lets an entry read an EARLIER entry:
	 * {@code enriched_uranium_wall_torch} takes its loot table and description from the standing torch,
	 * which the previous iteration has already bound (see {@code ModBlockProperties#applyWallTorch}).
	 */
	private static <T extends Block> T register(ContentManifest.BlockDef<T> def) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Industrialization.id(def.id()));
		T block = def.factory().apply(
				ContentManifest.blockProps(def.id()).apply(BlockBehaviour.Properties.of().setId(key)));
		Registry.register(BuiltInRegistries.BLOCK, key, block);
		def.bind().accept(() -> block);
		return block;
	}

	/** The registered block for a manifest entry; throws rather than returning {@code null}. */
	private static Block handle(ContentManifest.BlockDef<?> def) {
		Block block = REGISTERED.get(def.id());
		if (block == null) {
			throw new IllegalStateException("ModBlocks handle for '" + def.id()
					+ "' has no registered block — is the entry missing from ContentManifest.BLOCKS?");
		}
		return block;
	}

	/**
	 * Class-load trigger for the entrypoint. Registration and {@link ModContent} binding both happen in
	 * the static initializer above, so this only has to touch the class — and it checks, cheaply, that the
	 * replay covered the whole manifest rather than silently stopping short.
	 */
	public static void init() {
		if (REGISTERED.size() != ContentManifest.BLOCKS.size()) {
			throw new IllegalStateException("ModBlocks registered " + REGISTERED.size() + " of "
					+ ContentManifest.BLOCKS.size() + " manifest blocks");
		}
	}
}
