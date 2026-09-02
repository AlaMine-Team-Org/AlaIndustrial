package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge {@code BlockEntityType} registration: a replay of the shared
 * {@link ContentManifest#BLOCK_ENTITIES} list over {@link Registries#BLOCK_ENTITY_TYPE}, lazily through
 * a {@link DeferredRegister}. The per-face energy/fluid capability for each type is bound separately in
 * {@code IndustrializationNeoForge#registerCapabilities}, from the same manifest (MOD-433).
 *
 * <p><b>MOD-307 → MOD-554.</b> MOD-307 moved each type's id, factory and valid-block set into the
 * manifest; MOD-554 moved the <b>list</b> as well. Until then this file mirrored the Fabric
 * {@code ModBlockEntities} roster "1:1 by convention" — 58 hand-written lookups on each side, held
 * together by a Python comparison run after the fact. A type added on one loader and forgotten on the
 * other compiled and shipped as "this block has no block entity there": it does not tick, and its
 * screen does not open.
 *
 * <p><b>Split constraint (verified 26.2 API):</b> the {@code DeferredRegister} object and its
 * {@code register(modBus)} call must live on the {@code neoforge} side — which is the whole of what is
 * left here. There are deliberately NO typed handles: nothing outside this file ever read one (code
 * that needs a concrete type asks the manifest), so a per-type field would be one more name to keep in
 * step for no reader.
 *
 * <p><b>Verified 26.2 API (neoforge/minecraft 26.2.0.67):</b> the blocks a {@code BlockEntityType} is
 * valid for are stored in a {@code Set} and only read at runtime ({@code isValid}), never validated for
 * registry membership at construction — so {@link #register} can safely resolve the manifest's block ids
 * inside the type supplier (see its javadoc).
 */
public final class ModBlockEntitiesNeoForge {
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
			DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Industrialization.MOD_ID);

	/**
	 * Every manifest entry, queued on {@link #BLOCK_ENTITIES} the moment this class loads. Declared
	 * right after the register on purpose: static fields initialise in textual order, so the register
	 * exists by the time the replay runs.
	 */
	private static final int REGISTERED = registerAll();

	private ModBlockEntitiesNeoForge() {
	}

	/** Queues every {@link ContentManifest#BLOCK_ENTITIES} entry, in list order; returns how many. */
	private static int registerAll() {
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			register(def);
		}
		return ContentManifest.BLOCK_ENTITIES.size();
	}

	/**
	 * Class-load trigger for the {@code @Mod} constructor. Queueing and {@link ModContent} binding both
	 * happen in the static initializer above, so this only has to touch the class — and it checks,
	 * cheaply, that the replay covered the whole manifest rather than silently stopping short.
	 */
	public static void init() {
		if (REGISTERED != ContentManifest.BLOCK_ENTITIES.size()) {
			throw new IllegalStateException("ModBlockEntitiesNeoForge registered " + REGISTERED + " of "
					+ ContentManifest.BLOCK_ENTITIES.size() + " manifest block entities");
		}
	}

	/**
	 * Registers the {@code BlockEntityType} described by one shared manifest entry (MOD-307): id,
	 * factory and valid-block set all come from {@link ContentManifest#BLOCK_ENTITIES}; MOD-554 made the
	 * CALL come from there too, so this loader can no longer register a different subset than Fabric.
	 *
	 * <p><b>Timing (the chicken-and-egg guard, unchanged).</b> On NeoForge a block only resolves after
	 * its {@code RegisterEvent} — later than this method is <i>called</i> (static init of this class). So
	 * the manifest ids are resolved <b>inside</b> the deferred type supplier, which the register invokes
	 * only when the block-entity {@code RegisterEvent} fires, by which point every block is registered.
	 *
	 * <p><b>MOD-403.</b> The entry's {@link ModContent} slot is bound here too, via {@code holder::get}: a
	 * {@code DeferredHolder<_, BlockEntityType<X>>} is a {@code Supplier<BlockEntityType<X>>} while the
	 * slot is {@code Supplier<BlockEntityType<?>>} — generics are invariant, so the method reference
	 * bridges the wildcard while staying lazy. Binding at class load is legal for the same reason it was
	 * in {@code init()}: the holder is a handle, not the value.
	 */
	private static <T extends BlockEntity> void register(ContentManifest.BlockEntityDef<T> def) {
		DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> holder =
				BLOCK_ENTITIES.register(def.id(), () -> new BlockEntityType<>(def.factory(), def.blockSet()));
		def.bind().accept(holder::get);
	}
}
