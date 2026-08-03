package dev.alaindustrial.item.tool;

import dev.alaindustrial.item.material.ModToolMaterials;

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

	/**
	 * Wood tier — small AOE (3×2), low cap (12), no material attack bonus so the bias is lifted to 0.
	 * Bonus seed chance 0: the starter scythe buys speed and area, never yield (MOD-315).
	 */
	public static final ScytheTier WOOD =
			new ScytheTier("scythe_wood", net.minecraft.world.item.ToolMaterial.WOOD, 0.0f, new ScytheItem.Profile(3, 2, 12, 0.0f), false);
	/** Stone tier — same width, deeper reach (3), higher cap (18); standard -1 bias. */
	public static final ScytheTier STONE =
			new ScytheTier("scythe_stone", net.minecraft.world.item.ToolMaterial.STONE, -1.0f, new ScytheItem.Profile(3, 3, 18, 0.05f), false);
	/** Copper tier — same shape as stone, higher block cap (24). */
	public static final ScytheTier COPPER =
			new ScytheTier("scythe_copper", net.minecraft.world.item.ToolMaterial.COPPER, -1.0f, new ScytheItem.Profile(3, 3, 24, 0.08f), false);
	/** Iron tier — wider AOE (5×3), cap 30. The baseline mid-tier scythe. */
	public static final ScytheTier IRON =
			new ScytheTier("scythe_iron", net.minecraft.world.item.ToolMaterial.IRON, -2.0f, new ScytheItem.Profile(5, 3, 30, 0.12f), false);
	/**
	 * Gold tier — iron-sized area but fragile (gold durability) and highly enchantable; a side-grade.
	 *
	 * <p><b>Its bonus chance (0.20) deliberately breaks the tier ladder</b> (MOD-315): it sits above
	 * tempered iron and just under diamond, on a tool with 32 durability — i.e. 32 blocks of life in
	 * total. Gold's whole identity here is the fragile-but-lucky side-grade (the vanilla gold
	 * semantics of enchantability and fortune), so giving it the yield edge is what stops it from
	 * being a strictly-worse iron. The tiny durability budget is its own balance limiter.
	 */
	public static final ScytheTier GOLD =
			new ScytheTier("scythe_gold", net.minecraft.world.item.ToolMaterial.GOLD, 0.0f, new ScytheItem.Profile(5, 3, 30, 0.20f), false);
	/** Tempered-iron tier — the mod's tier between iron and gold: wider depth (4), cap 40. */
	public static final ScytheTier TEMPERED_IRON = new ScytheTier("scythe_tempered_iron",
			dev.alaindustrial.item.material.ModToolMaterials.TEMPERED_IRON, -2.0f, new ScytheItem.Profile(5, 4, 40, 0.18f), false);
	/** Diamond tier — depth 5, cap 50; the strong-but-not-fire-immune tier. */
	public static final ScytheTier DIAMOND =
			new ScytheTier("scythe_diamond", net.minecraft.world.item.ToolMaterial.DIAMOND, -2.0f, new ScytheItem.Profile(5, 5, 50, 0.25f), false);
	/** Netherite tier — the widest AOE (7×5, cap 70) and the only fire-resistant scythe. */
	public static final ScytheTier NETHERITE =
			new ScytheTier("scythe_netherite", net.minecraft.world.item.ToolMaterial.NETHERITE, -2.0f, new ScytheItem.Profile(7, 5, 70, 0.35f), true);

	/**
	 * All eight scythe tiers in canonical tier order. Adding/reordering a tier is one edit here and
	 * both builds pick it up.
	 */
	public static final List<ScytheTier> ALL = List.of(
			WOOD, STONE, COPPER, IRON, GOLD, TEMPERED_IRON, DIAMOND, NETHERITE);
}
