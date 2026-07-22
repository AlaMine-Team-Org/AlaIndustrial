package dev.alaindustrial.worldgen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.mixin.StructureTemplatePoolAccessor;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Runtime injection of the Industrialist's house into the vanilla village house pools (MOD-062).
 *
 * <p>Why runtime and not a datapack: a datapack file with a vanilla pool id REPLACES the whole pool
 * (RegistryDataLoader semantics — no merging), which breaks coexistence with any other mod touching
 * villages; neither Fabric nor NeoForge ships a pool-injection API in 26.2 (verified). So both
 * loaders call {@link #inject(MinecraftServer)} on server start — before any worldgen, which
 * matters twice: worldgen threads only ever read the list we mutate, and
 * {@code StructureTemplatePool.getMaxSize()} memoizes on first use, so late additions taller than
 * the vanilla set would be clipped.
 *
 * <p>Weight: {@code WEIGHT} copies of the element in the shuffled list (that is how vanilla encodes
 * weight at runtime). Kept low on purpose — the house should be an occasional find, not a staple
 * (tune here; json-range equivalent is 1..150).
 *
 * <p>Idempotency: datapack registries are rebuilt on every server start, so nothing accumulates
 * across restarts; the guard below protects against a double call within one start (and asserts
 * exactly-once in gametests) via {@link SinglePoolElement#getTemplateLocation()}.
 */
public final class VillagePoolInjector {
	private VillagePoolInjector() {
	}

	/** All five vanilla village biome variants get the same house (decision 2026-07-21). */
	private static final List<String> VILLAGE_BIOMES = List.of("plains", "desert", "savanna", "snowy", "taiga");

	public static final Identifier HOUSE_TEMPLATE = Industrialization.id("village/industrialist_house");

	public static final int WEIGHT = 2;

	public static void inject(MinecraftServer server) {
		Registry<StructureTemplatePool> pools =
				server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		for (String biome : VILLAGE_BIOMES) {
			Identifier poolId = Identifier.withDefaultNamespace("village/" + biome + "/houses");
			pools.getOptional(poolId).ifPresentOrElse(
					pool -> injectInto(pool, poolId),
					() -> Industrialization.LOGGER.warn(
							"[MOD-062] village pool {} not found; Industrialist house not injected there", poolId));
		}
	}

	private static void injectInto(StructureTemplatePool pool, Identifier poolId) {
		var templates = ((StructureTemplatePoolAccessor) (Object) pool).alaindustrial$getTemplates();
		boolean present = templates.stream().anyMatch(VillagePoolInjector::isHouse);
		if (present) {
			return;
		}
		StructurePoolElement element =
				StructurePoolElement.legacy(HOUSE_TEMPLATE.toString())
						.apply(StructureTemplatePool.Projection.RIGID);
		for (int i = 0; i < WEIGHT; i++) {
			templates.add(element);
		}
		Industrialization.LOGGER.info("[MOD-062] injected Industrialist house into {} (weight {})", poolId, WEIGHT);
	}

	/** True when the element is our house (idempotency guard + gametest assert). */
	public static boolean isHouse(StructurePoolElement element) {
		return element instanceof SinglePoolElement single
				&& HOUSE_TEMPLATE.equals(single.getTemplateLocation());
	}

	/**
	 * Enforces "at most one Industrialist house per village" (MOD-062). 26.2 has no native per-element
	 * occurrence cap and our compact house wins many tight house slots (a big vanilla house that
	 * doesn't fit is skipped, so the first that DOES fit — ours — is picked), so left alone it appears
	 * several times per village. {@code JigsawPlacementPlacerMixin} calls this on every candidate list
	 * the placer builds: once our house is already among the placed pieces, it is filtered out of all
	 * further house slots. When it is not yet placed, the candidate list is returned unchanged (so the
	 * house can still appear once — subject to its pool weight). Pure/deterministic → unit-testable.
	 */
	public static List<StructurePoolElement> withoutHouseIfPresent(List<StructurePoolElement> candidates, boolean housePlaced) {
		if (!housePlaced) {
			return candidates;
		}
		List<StructurePoolElement> filtered = new java.util.ArrayList<>(candidates.size());
		for (StructurePoolElement element : candidates) {
			if (!isHouse(element)) {
				filtered.add(element);
			}
		}
		return filtered;
	}

	/** Gametest seam: how many copies of the house an element list carries. */
	public static long houseCopies(List<StructurePoolElement> elements) {
		return elements.stream().filter(VillagePoolInjector::isHouse).count();
	}
}
