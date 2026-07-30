package dev.alaindustrial.loot;

import dev.alaindustrial.Industrialization;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Loot keys for the trellis (MOD-280).
 *
 * <p>The trellis is the mod's first block that is <em>harvested without being destroyed</em>, so it
 * needs two separate tables and this is the one place their ids live:
 *
 * <ul>
 *   <li>the ordinary block table ({@code blocks/trellis}, resolved by vanilla from the block's
 *       registry id) pays out when the structure is actually broken — the trellis item back, plus the
 *       seed if something was growing on it;</li>
 *   <li>{@link #HARVEST_TABLE} pays out the cotton on a right-click, while the plant stays in the
 *       world. It is read by {@code Block.dropFromBlockInteractLootTable}, whose param set is
 *       {@code LootContextParamSets.BLOCK_INTERACT} — hence {@code "type": "minecraft:block_interact"}
 *       in the json.</li>
 * </ul>
 *
 * <p>Unlike {@link BonusChest#INJECT_TABLE} this key needs no loader-side wiring at all: nothing is
 * injected into a vanilla table, and a {@code ResourceKey} into {@link Registries#LOOT_TABLE} is
 * resolved straight from {@code data/alaindustrial/loot_table/harvest/trellis.json}. Vanilla's
 * own {@code BuiltInLootTables.HARVEST_*} constants are built exactly this way, so registering the key
 * anywhere is unnecessary.
 */
public final class Trellis {

	/** Cotton handed out by picking a ripe trellis; the plant survives the pick. */
	public static final ResourceKey<LootTable> HARVEST_TABLE =
			ResourceKey.create(Registries.LOOT_TABLE, Industrialization.id("harvest/trellis"));

	/**
	 * The seed drop injected into vanilla grass, mirroring how wheat seeds are found: break grass, get an
	 * occasional seed. This is the entry point to the whole cotton chain — without it a fresh world has no
	 * way to obtain a first seed at all.
	 *
	 * <p>The sub-table itself refuses to pay out when the grass was cut with shears, the same rule vanilla
	 * applies to wheat seeds. That has to live inside the sub-table rather than being assumed: an injected
	 * pool rolls <em>independently</em> of the vanilla pool, so nothing about vanilla's own
	 * shears-versus-seeds choice carries over to ours.
	 */
	public static final ResourceKey<LootTable> SEEDS_FROM_GRASS =
			ResourceKey.create(Registries.LOOT_TABLE, Industrialization.id("inject/cotton_seeds_from_grass"));

	/**
	 * The tall-grass variant of {@link #SEEDS_FROM_GRASS}, and it exists because tall grass is a
	 * <b>two-block</b> plant: breaking it evaluates its loot table <em>twice</em> (once for the half the
	 * player hit, once for the partner that {@code updateShape} then destroys). An unguarded pool would
	 * therefore roll the seed chance twice — 5 % would silently become 9.75 %.
	 *
	 * <p>This table mirrors what vanilla does in {@code blocks/tall_grass.json}: two mutually exclusive
	 * pools, each demanding its own half <em>and</em> that the partner is still standing, so exactly one
	 * of the two evaluations can ever pay out — whichever half the player broke. It is the same
	 * double-payout mechanism that made the trellis itself drop two items before the {@code half=lower}
	 * fix, seen from the other side.
	 */
	public static final ResourceKey<LootTable> SEEDS_FROM_TALL_GRASS =
			ResourceKey.create(Registries.LOOT_TABLE,
					Industrialization.id("inject/cotton_seeds_from_tall_grass"));

	/**
	 * Which sub-table each vanilla grass table gets. Both variants are covered, so a player finds seeds
	 * wherever they clear plains; ferns and dry grass are deliberately left out — cotton growing out of a
	 * fern reads as a bug, not as a feature.
	 */
	public static final Map<ResourceKey<LootTable>, ResourceKey<LootTable>> GRASS_TABLES = Map.of(
			vanillaTable("blocks/short_grass"), SEEDS_FROM_GRASS,
			vanillaTable("blocks/tall_grass"), SEEDS_FROM_TALL_GRASS);

	private static ResourceKey<LootTable> vanillaTable(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace(path));
	}

	private Trellis() {
	}
}
