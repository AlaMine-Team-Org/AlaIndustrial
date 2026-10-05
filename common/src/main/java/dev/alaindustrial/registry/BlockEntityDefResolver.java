package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * How a {@link ContentManifest.BlockEntityDef} is resolved against the vanilla registries at a loader's
 * registration moment: its valid blocks by id ({@link ContentManifest.BlockEntityDef#blockSet()}) and the type it
 * produced ({@link ContentManifest.BlockEntityDef#registeredType()}). The record keeps those two public methods;
 * the work and its reasons live here, so the aggregator holds declarations and joins (MOD-711).
 */
final class BlockEntityDefResolver {
	private BlockEntityDefResolver() {
	}

	/**
	 * Resolves {@code blocks} against the vanilla block registry. Called by each loader when it builds the
	 * {@code BlockEntityType}: on Fabric that is {@code ModBlockEntities.init()} (after {@code ModBlocks.init()}),
	 * on NeoForge it is inside the deferred type supplier — in both cases the blocks are already registered.
	 *
	 * <p>An unknown id throws instead of quietly resolving to {@code AIR}: a typo here would otherwise
	 * produce a block entity that never attaches to anything, which is precisely the silent failure
	 * this manifest exists to remove.
	 *
	 * <p><b>The returned set is UNMODIFIABLE (MOD-417), and that is load-bearing.</b> Both loaders
	 * hand this exact instance to {@code new BlockEntityType<>(factory, blockSet())}, and the vanilla
	 * constructor stores the reference as-is — it does not copy. {@code isValid(BlockState)} then reads
	 * that very set on every block-entity attach. Before the manifest, each loader passed
	 * {@code Set.of(...)}, so the field was unmodifiable by construction; building it here turned it
	 * into a live mutable collection aliased by a registered {@code BlockEntityType}, where a stray
	 * {@code add}/{@code remove} would silently change which blocks the type attaches to. That
	 * regression was accidental, undocumented and uncovered, so it is closed rather than kept.
	 *
	 * <p><b>Why {@code Collections.unmodifiableSet} and not {@code Set.copyOf}.</b> {@code Set.copyOf}
	 * is salted — its iteration order varies between JVM runs — and the resolution below depends on a
	 * stable order for its duplicate diagnostics and for reproducible error messages. The wrapper
	 * keeps insertion order (i.e. the order of {@code blocks}) and adds immutability on top.
	 *
	 * <p><b>What this does NOT do.</b> It does not stop a NeoForge mod extending our types through
	 * {@code BlockEntityTypeAddBlocksEvent}: that event copies {@code getValidBlocks()} into a fresh
	 * {@code HashSet} and REPLACES the field through a mixin accessor, so it never touches the set we
	 * pass. Extensibility there is unchanged by this method, in either direction.
	 *
	 * @param id     the block-entity id, for the error messages
	 * @param blocks registry ids of the blocks the type is valid for
	 */
	static Set<Block> blockSet(String id, List<String> blocks) {
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
		return Collections.unmodifiableSet(resolved);
	}

	/**
	 * The registered {@code BlockEntityType} of block-entity {@code id}, from the vanilla registry rather than
	 * from a loader handle, so client code shared by both loaders can name it (MOD-403: the
	 * {@code BlockEntityRenderer} manifest).
	 *
	 * <p>Callable only after the loader registered its block-entity types (Fabric:
	 * {@code ModBlockEntities.init()}; NeoForge: its {@code RegisterEvent}). Both renderer-registration
	 * hooks run far later than that.
	 */
	static BlockEntityType<?> registeredType(String id) {
		Identifier key = Industrialization.id(id);
		BlockEntityType<?> registered = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(key);
		if (registered == null) {
			throw new IllegalStateException("BlockEntityDef '" + id + "': '" + key
					+ "' is not registered (yet) — asked for its BlockEntityType too early");
		}
		return registered;
	}
}
