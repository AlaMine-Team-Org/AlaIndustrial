package dev.alaindustrial.core.waste;

/**
 * Which briquette a finished batch casts (MOD-145) — decided by how VARIED the batch was, never by how
 * big it was.
 *
 * <p>This is the machine's whole idea. A stack of cobblestone and a stack of dirt are the same batch to
 * a mass counter, so a mass-only recycler would reward shovelling one thing forever. Grading by mix
 * turns "throw junk in" into "sort junk into streams and feed them together", which the mod's item pipes
 * and chests already make possible.
 *
 * <p>{@link WasteFraction#OTHER} is excluded from the arithmetic on purpose — see its javadoc.
 */
public enum SlagGrade {
	/** One fraction dominates (or the batch was all unknown junk). Ballast: blocks, cheap fuel. */
	POOR,
	/** Two fractions present in workable proportion. The everyday output. */
	COMMON,
	/** All three fractions, none of them more than half the batch. The feedstock for matter, later. */
	RICH;

	/** A batch is POOR when one fraction takes at least this share of the graded mass. */
	private static final float POOR_SHARE = 0.80f;
	/** A batch is RICH when all three are present and none exceeds this share. */
	private static final float RICH_MAX_SHARE = 0.50f;

	/**
	 * Grade a batch from the mass collected in each graded fraction.
	 *
	 * @param mineral mass of the mineral fraction
	 * @param metal mass of the metal fraction
	 * @param combustible mass of the combustible fraction
	 */
	public static SlagGrade of(int mineral, int metal, int combustible) {
		int total = mineral + metal + combustible;
		if (total <= 0) {
			return POOR; // nothing but OTHER went in — no variety to reward
		}
		int max = Math.max(mineral, Math.max(metal, combustible));
		int present = (mineral > 0 ? 1 : 0) + (metal > 0 ? 1 : 0) + (combustible > 0 ? 1 : 0);
		float topShare = (float) max / total;
		if (present == WasteFraction.GRADED_COUNT && topShare <= RICH_MAX_SHARE) {
			return RICH;
		}
		if (topShare >= POOR_SHARE) {
			return POOR;
		}
		return COMMON;
	}
}
