package dev.alaindustrial.item;

/**
 * Per-tool attack stats for the tempered-iron tool line (MOD-190). The tool <b>material</b>
 * (durability, mining speed, damage bonus, enchantability) already lives once in
 * {@link ModToolMaterials#TEMPERED_IRON}; these are the per-tool-type {@code (attackDamage, attackSpeed)}
 * pairs that both loaders feed into {@code Item.Properties.pickaxe/sword(...)} and the vanilla
 * {@code AxeItem/HoeItem/ShovelItem} constructors.
 *
 * <p>They used to be inlined as raw {@code float} literals in both {@code ModItems} (Fabric) and
 * {@code ModItemsNeoForge} (NeoForge). Centralising them here means a balance tweak can no longer
 * silently diverge between the loaders (the MOD-157 drift class) — exactly what {@link ScytheTiers}
 * already does for the scythe line. Plain data only (no registry/component access), so it is safe to
 * read at either loader's registration time.
 */
public final class TemperedIronToolStats {
	private TemperedIronToolStats() {
	}

	/** A tool's melee attack tuning: bonus damage and the attack-speed modifier. */
	public record ToolStats(float attackDamage, float attackSpeed) {
	}

	public static final ToolStats PICKAXE = new ToolStats(1.0f, -2.8f);
	public static final ToolStats AXE = new ToolStats(6.0f, -3.1f);
	public static final ToolStats HOE = new ToolStats(-2.0f, -1.0f);
	public static final ToolStats SHOVEL = new ToolStats(1.5f, -3.0f);
	public static final ToolStats SWORD = new ToolStats(3.0f, -2.4f);
}
