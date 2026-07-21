package dev.alaindustrial.item;

import java.util.List;

/**
 * The eight canonical {@link ScytheTier} instances (MOD-068), in a single loader-neutral place.
 * Both loaders register their scythe items by reading these named entries (or iterating
 * {@link #ALL}), so a balance tweak is one edit and the Fabric and NeoForge builds cannot drift —
 * the comment-as-contract this used to rely on ("Keep these values in sync with the Fabric
 * ModItems#scythe helper") is gone.
 *
 * <p>Order is the canonical tier order (wood → stone → copper → iron → gold → tempered → diamond →
 * netherite); it does not affect runtime, but keeping it stable makes diffs reviewable.
 */
public final class ScytheTiers {
	private ScytheTiers() {
	}

	/** Wood tier — small AOE (3×2), low cap (12), no material attack bonus so the bias is lifted to 0. */
	public static final ScytheTier WOOD =
			new ScytheTier("scythe_wood", net.minecraft.world.item.ToolMaterial.WOOD, 0.0f, new ScytheItem.Profile(3, 2, 12), false);
	/** Stone tier — same width, deeper reach (3), higher cap (18); standard -1 bias. */
	public static final ScytheTier STONE =
			new ScytheTier("scythe_stone", net.minecraft.world.item.ToolMaterial.STONE, -1.0f, new ScytheItem.Profile(3, 3, 18), false);
	/** Copper tier — same shape as stone, higher block cap (24). */
	public static final ScytheTier COPPER =
			new ScytheTier("scythe_copper", net.minecraft.world.item.ToolMaterial.COPPER, -1.0f, new ScytheItem.Profile(3, 3, 24), false);
	/** Iron tier — wider AOE (5×3), cap 30. The baseline mid-tier scythe. */
	public static final ScytheTier IRON =
			new ScytheTier("scythe_iron", net.minecraft.world.item.ToolMaterial.IRON, -2.0f, new ScytheItem.Profile(5, 3, 30), false);
	/** Gold tier — iron-sized area but fragile (gold durability) and highly enchantable; a side-grade. */
	public static final ScytheTier GOLD =
			new ScytheTier("scythe_gold", net.minecraft.world.item.ToolMaterial.GOLD, 0.0f, new ScytheItem.Profile(5, 3, 30), false);
	/** Tempered-iron tier — the mod's tier between iron and gold: wider depth (4), cap 40. */
	public static final ScytheTier TEMPERED_IRON = new ScytheTier("scythe_tempered_iron",
			dev.alaindustrial.item.ModToolMaterials.TEMPERED_IRON, -2.0f, new ScytheItem.Profile(5, 4, 40), false);
	/** Diamond tier — depth 5, cap 50; the strong-but-not-fire-immune tier. */
	public static final ScytheTier DIAMOND =
			new ScytheTier("scythe_diamond", net.minecraft.world.item.ToolMaterial.DIAMOND, -2.0f, new ScytheItem.Profile(5, 5, 50), false);
	/** Netherite tier — the widest AOE (7×5, cap 70) and the only fire-resistant scythe. */
	public static final ScytheTier NETHERITE =
			new ScytheTier("scythe_netherite", net.minecraft.world.item.ToolMaterial.NETHERITE, -2.0f, new ScytheItem.Profile(7, 5, 70), true);

	/**
	 * All eight scythe tiers in canonical tier order. Adding/reordering a tier is one edit here and
	 * both builds pick it up.
	 */
	public static final List<ScytheTier> ALL = List.of(
			WOOD, STONE, COPPER, IRON, GOLD, TEMPERED_IRON, DIAMOND, NETHERITE);
}
