package dev.alaindustrial.skill;

import dev.alaindustrial.Config;
import dev.alaindustrial.stats.LevelMath;
import dev.alaindustrial.stats.PlayerModStats;

/**
 * How many skill points a player has earned (MOD-483): <b>one per cabinet level they actually
 * earned</b>, which is one fewer than their level.
 *
 * <p>Level 1 is where everybody starts — it is not an achievement, and paying for it handed a brand-new
 * player the entry node of a branch before they had produced a single EU (found in play by the owner,
 * 2026-09-05, who called it a freebie). The first Fragment now arrives with level 2, the first level
 * anyone has to work for, and the ceiling is 39 rather than 40.
 *
 * <p>The budget is still the whole balance of the tree. A finished branch costs
 * {@link SkillSlot#BRANCH_PATH_COST} = 18 points under the hard-fork rule, four branches cost 72, and a
 * player has 39 — two capstones per run plus three points to look into a third branch, which is exactly
 * an entry node and one fork. Change this function and that arithmetic changes with it.
 *
 * <p>Lives in one place because the screen and the purchase handler must never disagree about how many
 * points a player has: a client that thinks it has more would offer a purchase the server refuses, and
 * one that thinks it has fewer would grey out a node the player has paid for.
 */
public final class SkillPoints {

	private SkillPoints() {
	}

	/**
	 * Points earned at this cabinet level — one per level ABOVE the first, floored at zero.
	 *
	 * <p>The {@code - 1} is the whole rule: a player sitting at level 1 has earned nothing yet, so they
	 * hold nothing. A player who already spent a Fragment before this changed does not lose the node
	 * they bought — {@link SkillBuild#free(int)} floors at zero rather than going negative, so their
	 * build stands and their next Fragment is simply the one they earn next.
	 */
	public static int forLevel(int level) {
		return Math.max(0, Math.min(LevelMath.MAX_LEVEL, level) - 1);
	}

	/**
	 * The player's cabinet level, read exactly as the dashboard reads it:
	 * {@code max(levelForXp(...), highestLevelReached)}.
	 *
	 * <p>The second half is not decoration. The mod stores the highest level ever reached so that a
	 * balance change can never demote a rank a player already earned — and since algorithms are one per
	 * level, dropping it here would take away algorithms they had already spent: their free count would
	 * clamp to zero and the tree would show a build they could no longer afford. Using only
	 * {@code levelForXp} also made this screen disagree with the dashboard about the same player.
	 */
	public static int level(PlayerModStats stats) {
		int fromXp = LevelMath.levelForXp(stats.xp(Config.euPerXp, Config.euPerXpGenerated),
				Config.xpLevelOneCost, Config.levelXpMultiplier);
		return Math.max(fromXp, stats.highestLevelReached());
	}

	/** Points earned, straight from career stats — the form both the screen and the server use. */
	public static int earned(PlayerModStats stats) {
		return forLevel(level(stats));
	}
}
