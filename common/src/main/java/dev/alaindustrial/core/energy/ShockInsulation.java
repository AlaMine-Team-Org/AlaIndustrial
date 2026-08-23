package dev.alaindustrial.core.energy;

/**
 * The arithmetic of a worn insulating set against a bare cable's shock (MOD-466) — how much of the
 * hit the rubber stops, and what that costs the suit in durability.
 *
 * <p><b>Three protections, three different things.</b> {@link CableType#isInsulated()} is a property
 * of the cable block and removes the hazard entirely; {@link ShockGuardMaterial} is a plate under the
 * wire that lowers the <em>probability</em> of a hit; this is the third and only <em>worn</em> one,
 * and it is the only one that follows the player rather than the wire. They do not combine into one
 * model on purpose — a player who has re-laid the line in rubber needs no suit, and a suit is what
 * you wear precisely because you have not.
 *
 * <p><b>Protection is per piece, not all-or-nothing.</b> Each worn piece cuts the same share, so
 * three pieces are worth three quarters of a set rather than nothing at all. This is the same shape
 * the shielding suit's {@code RadiationCore#shielded} already uses, deliberately: the mod ends up
 * with one rule for both worn defences instead of two that a player has to learn separately. Unlike
 * radiation, there is no cap below 100 here — a full set is meant to make a bare LV line safe to work
 * on, which is the whole point of a cheap early-game craft.
 *
 * <p><b>Wear is charged for what the rubber stopped, and only that.</b> Not for the raw hit: a
 * partial set that let most of the damage through has not insulated most of it, and billing it for
 * the difference would wear the suit out on protection it never gave. (The shielding suit learned
 * this the hard way — it once charged itself for a dose ceiling that had nothing to do with
 * shielding.) The remainder still reaches {@code hurtServer}, where vanilla applies its own armour
 * absorption and its own armour wear, so a partial set is not charged twice for the same point.
 *
 * <p><b>Deliberately free of Minecraft types</b>, exactly like {@link CableType} and
 * {@link ShockGuardMaterial}: the L1 unit suite runs without the game on the classpath, and the
 * pitest target glob {@code dev.alaindustrial.core.*} covers this package. Reading which pieces a
 * player is actually wearing needs MC types, so that lives next to its only consumer, in
 * {@code CableBlock}.
 */
public final class ShockInsulation {

	/** Pieces in a full set — head, chest, legs, feet. */
	public static final int SET_PIECES = 4;

	private ShockInsulation() {
	}

	/**
	 * Share of the shock this many worn pieces cut, in percent, clamped to a whole set and to 100.
	 *
	 * <p>Both clamps are load-bearing rather than defensive: a config edit can set the per-piece share
	 * above 25, and without the ceiling a generous operator would end up with a set that reduces
	 * damage past zero and starts healing the player.
	 */
	public static int cutPercent(int wornPieces, int perPiecePercent) {
		int pieces = Math.clamp(wornPieces, 0, SET_PIECES);
		return Math.clamp((long) pieces * Math.max(0, perPiecePercent), 0, 100);
	}

	/** Damage the worn set stops — the part the rubber pays for in durability. */
	public static float prevented(float raw, int wornPieces, int perPiecePercent) {
		if (raw <= 0.0f) {
			return 0.0f;
		}
		return raw * cutPercent(wornPieces, perPiecePercent) / 100.0f;
	}

	/**
	 * Damage that still reaches the player — what {@code hurtServer} is called with, or nothing at all
	 * when a full set stops the hit outright.
	 *
	 * <p>Computed as the difference rather than by a second multiplication, so
	 * {@code prevented + remaining} is exactly {@code raw} for every input and a full set can never
	 * leave a floating-point sliver of damage behind.
	 */
	public static float remaining(float raw, int wornPieces, int perPiecePercent) {
		if (raw <= 0.0f) {
			return 0.0f;
		}
		return raw - prevented(raw, wornPieces, perPiecePercent);
	}

	/**
	 * Durability one worn piece spends for a shock the set stopped, given how much absorbed damage buys
	 * one point.
	 *
	 * <p><b>Contact is continuous, and that is what the divisor is for.</b> Both hazard paths re-enter
	 * every tick and the absorbed hit opens only a one-second window, so a player merely standing next
	 * to a live wire is shocked once a second — not once per visit. Charging the full absorbed damage
	 * per hit therefore cost 2 durability a second on the weakest cable in the game and destroyed a
	 * helmet in under half a minute of standing still. The first version shipped that, because the
	 * per-hit number looked reasonable in isolation and was never converted into seconds.
	 *
	 * <p><b>Never zero for a real contact.</b> A hit that was actually insulated always costs at least
	 * one point, however small the share: a set that absorbs for free is not a consumable, and the whole
	 * reason this set is cheap to craft is that it is meant to be re-crafted. At the other end the cost
	 * still follows the tier — an LV line nibbles at the suit, an HV line eats it — which is what makes
	 * voltage something the player feels through the suit rather than only through health.
	 */
	public static int wearFor(float prevented, float damagePerPoint) {
		if (prevented <= 0.0f) {
			return 0;
		}
		if (damagePerPoint <= 0.0f) {
			return Math.max(1, Math.round(prevented));
		}
		return Math.max(1, Math.round(prevented / damagePerPoint));
	}
}
