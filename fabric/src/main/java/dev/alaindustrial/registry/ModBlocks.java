package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.content.EnergyGenerationContent;
import dev.alaindustrial.registry.content.EnergyGridContent;
import dev.alaindustrial.registry.content.FluidContent;
import dev.alaindustrial.registry.content.ProcessingContent;
import dev.alaindustrial.registry.content.StorageContent;
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
 * <p><b>MOD-190 → MOD-403.</b> MOD-190 moved each block's {@code BlockBehaviour.Properties} chain into the
 * shared manifest (since MOD-711 it is part of the block's own {@link ContentManifest.BlockDef}). MOD-403
 * moved the <b>list</b> too: which blocks exist, in what
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
 * <p><b>The typed fields below are handles, not registrations.</b> Only blocks that Fabric code reads by
 * name have one — the REI plugin, the villager profession in the entrypoint and a few gametests; everything
 * else reads {@link ModContent}, which the replay binds. Adding a block does NOT add a handle (MOD-708
 * removed the ones nobody read). Each is looked up from its manifest entry rather than from a string, so
 * it cannot point at an id that no longer exists.
 */
public final class ModBlocks {
	private ModBlocks() {
	}

	/**
	 * Every block, registered the moment this class loads. Declared FIRST on purpose: static fields
	 * initialise in textual order, so this runs before the handles below read it.
	 */
	private static final Map<String, Block> REGISTERED = registerAll();

	public static final Block GENERATOR = handle(EnergyGenerationContent.GENERATOR);
	public static final Block MOONLIT_SOLAR_PANEL = handle(EnergyGenerationContent.MOONLIT_SOLAR_PANEL);
	public static final Block DAYLIGHT_SOLAR_PANEL = handle(EnergyGenerationContent.DAYLIGHT_SOLAR_PANEL);
	public static final Block GEOTHERMAL_GENERATOR = handle(EnergyGenerationContent.GEOTHERMAL_GENERATOR);
	public static final Block WIND_MILL = handle(EnergyGenerationContent.WIND_MILL);
	public static final Block PUMP = handle(FluidContent.PUMP);
	public static final Block COPPER_CABLE = handle(EnergyGridContent.COPPER_CABLE);
	public static final Block MACERATOR = handle(ProcessingContent.MACERATOR);
	public static final Block BATTERY_BOX = handle(StorageContent.BATTERY_BOX);
	public static final Block ELECTRIC_FURNACE = handle(ProcessingContent.ELECTRIC_FURNACE);
	public static final Block IRON_FURNACE = handle(ProcessingContent.IRON_FURNACE);
	public static final Block EXTRACTOR = handle(ProcessingContent.EXTRACTOR);
	public static final Block COMPRESSOR = handle(ProcessingContent.COMPRESSOR);
	public static final Block CANNING_MACHINE = handle(ProcessingContent.CANNING_MACHINE);

	public static final Block ENERGY_CONDENSER = handle(StorageContent.ENERGY_CONDENSER);
	public static final Block INDUSTRIAL_WORKBENCH = handle(ProcessingContent.INDUSTRIAL_WORKBENCH);

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
	 * One manifest entry, the Fabric way: build the {@code Properties} from the entry's own chain
	 * ({@code def.props()}) on a base carrying the Fabric-only {@code setId}, construct eagerly, register,
	 * and publish the result into the entry's {@link ModContent} slot as a constant supplier
	 * ({@code () -> value}) — NeoForge instead binds its lazy {@code DeferredHolder} into the same slot.
	 *
	 * <p>Binding here rather than in a later {@code init()} is what lets an entry read an EARLIER entry:
	 * {@code enriched_uranium_wall_torch} takes its loot table and description from the standing torch,
	 * which the previous iteration has already bound (see {@code ModBlockProperties#applyWallTorch}).
	 */
	private static <T extends Block> T register(ContentManifest.BlockDef<T> def) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Industrialization.id(def.id()));
		T block = def.factory().apply(def.props().apply(BlockBehaviour.Properties.of().setId(key)));
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
