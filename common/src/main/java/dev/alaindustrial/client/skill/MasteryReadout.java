package dev.alaindustrial.client.skill;

import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.stats.LevelMath;

/**
 * The mastery level and bar the client shows — the dashboard's «Mastery» card and the skill tree's
 * header read the same three numbers from here, so the two screens cannot disagree about a player
 * (MOD-695).
 *
 * <p>Takes the career totals as plain numbers rather than a {@code PlayerModStats}, which keeps it
 * Minecraft-free: the L1 suite pins what a player is SHOWN, not only what {@link LevelMath} computes.
 */
public final class MasteryReadout {

	private MasteryReadout() {
	}

	/** Career XP from the two running totals, at the server's XP rates ({@link ServerBalance}). */
	public static long xp(long euUsefulConsumedTotal, long euProducedTotal) {
		return LevelMath.xpOf(euUsefulConsumedTotal, euProducedTotal,
				ServerBalance.euPerXp(), ServerBalance.euPerXpGenerated());
	}

	/**
	 * The level shown: the rank floor OR the level the current points already earn, whichever is higher
	 * — the formula of {@code /ala profile show} and of {@code SkillPoints.level}. The floor matters: a
	 * balance change must never demote a rank a player already reached.
	 */
	public static int level(long euUsefulConsumedTotal, long euProducedTotal, int highestLevelReached) {
		int fromXp = LevelMath.levelForXp(xp(euUsefulConsumedTotal, euProducedTotal),
				ServerBalance.xpLevelOneCost(), ServerBalance.levelXpMultiplier());
		return Math.max(Math.max(1, highestLevelReached), fromXp);
	}

	/** How far the bar toward the next level is filled, in {@code [0, 1]}. */
	public static double progress(long xp, int level) {
		return LevelMath.progressToNext(xp, level, ServerBalance.xpLevelOneCost(), ServerBalance.levelXpMultiplier());
	}
}
