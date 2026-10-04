package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.IndustrialWorkbenchBlock;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.content.ProcessingContent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge block registration: a replay of the shared {@link ContentManifest#BLOCKS} list, plus the one
 * typed {@link DeferredBlock} handle the rest of the NeoForge source set reads.
 *
 * <p><b>MOD-190 → MOD-403.</b> MOD-190 moved each block's {@code BlockBehaviour.Properties} chain into the
 * shared manifest (since MOD-711 it is part of the block's own {@link ContentManifest.BlockDef}). MOD-403
 * moved the <b>list</b>: which blocks exist, in what order,
 * built by which factory, bound to which {@link ModContent} handle. This file used to mirror the Fabric
 * {@code ModBlocks} "1:1 by convention", held together by a Python parity script — a block added on one
 * loader and forgotten here compiled fine and simply did not exist on NeoForge.
 *
 * <p><b>What is left here is the NeoForge registration MECHANISM, and only that:</b> the
 * {@link DeferredRegister} (which must live on this side) and its lazy {@code registerBlock}, whose
 * properties {@link Supplier} is invoked during the block {@code RegisterEvent} — that lateness is what
 * lets manifest entries read earlier ones ({@code enriched_uranium_wall_torch} → the standing torch, the
 * liquid blocks → their fluid, whose {@code RegisterEvent} fires first).
 *
 * <p><b>The typed field below is a handle, not a registration.</b> {@code ModProfessionsNeoForge} reads
 * the workbench by name; everything else reads {@link ModContent}, which the replay binds. Adding a block
 * does NOT add a handle (MOD-708 removed the ones nobody read). A handle is derived from its manifest
 * entry, so the entry's block class — not a string — is what fixes the field's type parameter.
 */
public final class ModBlocksNeoForge {
	public static final DeferredRegister.Blocks BLOCKS =
			DeferredRegister.createBlocks(Industrialization.MOD_ID);

	/**
	 * Every manifest entry, queued on {@link #BLOCKS} the moment this class loads. Declared right after
	 * the register on purpose: static fields initialise in textual order, so the register exists here and
	 * the handle below can read the result.
	 */
	private static final Map<String, DeferredBlock<?>> REGISTERED = registerAll();


	// MOD-468, stage 1 — the reactor room's shell.
	// MOD-471 — fallout. Bound for the same reason as on Fabric: no recipe, no creative tab, no
	// block item; only an accident ever puts it in the world.
	/** Energy condenser (MOD-393): banks grid surplus into energy clots. */
	// MOD-537 — kok sagyz: the flower, and the root column it grows downward.
	// MOD-505 — the crystal greenhouse.
	public static final DeferredBlock<IndustrialWorkbenchBlock> INDUSTRIAL_WORKBENCH = handle(ProcessingContent.INDUSTRIAL_WORKBENCH);
	// The organic chain (MOD-146/MOD-525).

	private ModBlocksNeoForge() {
	}

	/** Queues every {@link ContentManifest#BLOCKS} entry, in list order, and binds each into ModContent. */
	private static Map<String, DeferredBlock<?>> registerAll() {
		Map<String, DeferredBlock<?>> registered = new LinkedHashMap<>();
		for (ContentManifest.BlockDef<?> def : ContentManifest.BLOCKS) {
			if (registered.put(def.id(), register(def)) != null) {
				throw new IllegalStateException(
						"ContentManifest.BLOCKS declares block id '" + def.id() + "' twice");
			}
		}
		return Map.copyOf(registered);
	}

	/**
	 * One manifest entry, the NeoForge way: {@code registerBlock} applies {@code setId} from the deferred
	 * key and calls the factory with the properties this supplier builds, when the block
	 * {@code RegisterEvent} fires.
	 *
	 * <p>The {@link ModContent} slot is bound HERE, at class load, with {@code holder::get} — a lazy
	 * handle, so binding before the event is legal and is exactly what {@code ModBlocksNeoForge.init()}
	 * used to do line by line for all 72 blocks.
	 */
	private static <T extends Block> DeferredBlock<T> register(ContentManifest.BlockDef<T> def) {
		DeferredBlock<T> holder = BLOCKS.registerBlock(def.id(), def.factory(), props(def));
		def.bind().accept(holder::get);
		return holder;
	}

	/**
	 * The queued holder for a manifest entry, typed on the entry's block class.
	 *
	 * <p>The cast is unchecked but cannot lie: {@code def} is the very entry {@link #register} passed to
	 * {@code registerBlock}, so the holder it produced is a {@code DeferredBlock<T>} for that same
	 * {@code T}. What the compiler DOES check is the field: a {@code DeferredBlock<SolarPanelBlock>
	 * INDUSTRIAL_WORKBENCH = handle(ProcessingContent.INDUSTRIAL_WORKBENCH)} does not compile, because
	 * {@code T} comes from the entry rather than from a string key.
	 */
	@SuppressWarnings("unchecked")
	private static <T extends Block> DeferredBlock<T> handle(ContentManifest.BlockDef<T> def) {
		DeferredBlock<?> holder = REGISTERED.get(def.id());
		if (holder == null) {
			throw new IllegalStateException("ModBlocksNeoForge handle for '" + def.id()
					+ "' has no queued block — is the entry missing from ContentManifest.BLOCKS?");
		}
		return (DeferredBlock<T>) holder;
	}

	/**
	 * The property {@link Supplier} {@code registerBlock} wants: the entry's own chain
	 * ({@link ContentManifest.BlockDef#props()}) applied to a bare {@code Properties.of()}. {@code setId} is
	 * applied by {@code registerBlock} from the deferred key; machine chains already carry
	 * {@code requiresCorrectToolForDrops}, and torch chains deliberately do not.
	 */
	private static Supplier<BlockBehaviour.Properties> props(ContentManifest.BlockDef<?> def) {
		return () -> def.props().apply(BlockBehaviour.Properties.of());
	}

	/**
	 * Class-load trigger for the {@code @Mod} constructor. Queueing and {@link ModContent} binding both
	 * happen in the static initializer above, so this only has to touch the class — and it checks, cheaply,
	 * that the replay covered the whole manifest rather than silently stopping short.
	 */
	public static void init() {
		if (REGISTERED.size() != ContentManifest.BLOCKS.size()) {
			throw new IllegalStateException("ModBlocksNeoForge registered " + REGISTERED.size() + " of "
					+ ContentManifest.BLOCKS.size() + " manifest blocks");
		}
	}
}
