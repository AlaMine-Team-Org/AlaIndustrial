package dev.alaindustrial.core.teleport;

import java.util.function.DoubleSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Where a random jump lands (MOD-116) — the search, kept free of Minecraft types so it can be
 * unit-tested and mutation-tested directly.
 *
 * <p><b>Why the world arrives through {@link Terrain} instead of a {@code ServerLevel}.</b> The
 * feature's whole point is a jump of up to {@code teleporterRtpRadius} (5000) blocks, and a jump
 * that long cannot be driven from an L2 gametest at all: the lane's rigs are 8×8×8 with one chunk
 * of force-loading around them, {@code GameTestServer} picks a RANDOM world origin per run, and the
 * per-test tick budget is a fraction of the warmup alone. Anything decided inside
 * {@code TeleportEngine.execute} would therefore be covered by nothing. Behind this interface the
 * search is a pure function of the numbers a fake can hand it, so the retry rule, the ring geometry
 * and the give-up path are all L1 — and, living under {@code dev.alaindustrial.core.*}, they are in
 * the pitest run as well.
 *
 * <p><b>The two-stage probe is not an optimisation, it is what makes the feature affordable.</b> A
 * candidate 5000 blocks out is almost never in a loaded chunk, and the precise probe has to force
 * one — which on fresh terrain means running the full worldgen pipeline synchronously on the server
 * thread. Doing that once per attempt would freeze the server for as many chunk generations as the
 * retry budget allows. So every candidate is first tested against {@link Terrain#noiseGroundY},
 * which samples the generator's noise for a single column and generates nothing; only a candidate
 * whose terrain already stands above sea level is worth paying a chunk for. Ocean — the single
 * commonest reason a random Overworld column is unusable — is thereby rejected for free.
 */
public final class RtpSiteFinder {

	private RtpSiteFinder() {
	}

	/** {@link Terrain#noiseGroundY} and {@link Terrain#safeFeetY} return this when there is no answer. */
	public static final int NO_SITE = Integer.MIN_VALUE;

	/** Random draws each attempt consumes: one for the angle, one for the radius. */
	public static final int ROLLS_PER_ATTEMPT = 2;

	/** A landing spot: the block the player's feet end up in. */
	public record Site(int x, int y, int z) {
	}

	/** A horizontal displacement from the jump's origin. */
	public record Offset(int dx, int dz) {
	}

	/**
	 * The world, reduced to the three questions the search asks of it.
	 *
	 * <p>The split between the two height methods is the cheap/expensive divide described in the
	 * class javadoc: {@link #noiseGroundY} must never generate a chunk, {@link #safeFeetY} is allowed
	 * to and is therefore asked at most once per surviving candidate.
	 */
	public interface Terrain {

		/**
		 * The generator's idea of where solid ground sits at this column, WITHOUT generating the
		 * chunk — feet level, i.e. the first free block above the terrain.
		 *
		 * @return {@link #NO_SITE} when the column cannot be sampled at all
		 */
		int noiseGroundY(int x, int z);

		/**
		 * The real, block-accurate landing height at this column, loading the chunk if it must.
		 *
		 * @return feet level, or {@link #NO_SITE} when nothing here is safe to stand in
		 */
		int safeFeetY(int x, int z);

		/** Whether this column is inside the world border — a jump outside it would be rejected anyway. */
		boolean isInsideBorder(int x, int z);
	}

	/**
	 * Pick a landing spot, or give up.
	 *
	 * <p>Gives up rather than widening the search or falling back to somewhere safe-but-boring: a
	 * refusal is free (the caller has not charged the station yet) and the player can simply press
	 * again, whereas a fallback would quietly turn "random jump" into "jump to the same place".
	 *
	 * @param rolls uniform draws in {@code [0, 1)}; exactly {@link #ROLLS_PER_ATTEMPT} are consumed
	 *     per attempt, so a fixed sequence makes the whole search reproducible in a test
	 * @return the spot, or {@code null} when {@code attempts} candidates were all unusable
	 */
	@Nullable
	public static Site find(Terrain terrain, int originX, int originZ, int minRadius, int maxRadius,
			int seaLevel, int attempts, DoubleSupplier rolls) {
		for (int attempt = 0; attempt < attempts; attempt++) {
			// Both draws are taken before any early `continue`, so one attempt always costs the same
			// number of rolls. A rejection path that skipped a draw would shift every later candidate
			// and make a fixed roll sequence describe a different search than the test intended.
			Offset offset = offsetInRing(rolls.getAsDouble(), rolls.getAsDouble(), minRadius, maxRadius);
			int x = originX + offset.dx();
			int z = originZ + offset.dz();

			if (!terrain.isInsideBorder(x, z)) {
				continue;
			}
			// The free half of the probe: ocean and anything else whose terrain never rises above the
			// waterline is dropped here, before a chunk is paid for.
			int noiseGround = terrain.noiseGroundY(x, z);
			if (noiseGround == NO_SITE || noiseGround <= seaLevel) {
				continue;
			}
			// The paid half: at most once per surviving candidate.
			int feetY = terrain.safeFeetY(x, z);
			if (feetY == NO_SITE) {
				continue;
			}
			return new Site(x, feetY, z);
		}
		return null;
	}

	/**
	 * One point of the ring {@code [minRadius, maxRadius]} around the origin, from two uniform draws.
	 *
	 * <p>Takes the draws as plain numbers rather than a random source so it stays a pure function:
	 * the geometry is then testable without faking an RNG, and a mutated {@code +}/{@code -} inside
	 * it fails an assertion instead of merely shifting a distribution nobody checks.
	 *
	 * <p><b>Uniform by area, not by radius.</b> {@code r = min + u × (max − min)} looks right and is
	 * wrong: it puts as many landings in the innermost ring as in the outermost one, which are wildly
	 * different amounts of ground, so jumps would pile up near the origin. Sampling
	 * {@code r = sqrt(min² + u × (max² − min²))} spreads them evenly over the actual area.
	 *
	 * <p><b>Why a minimum radius exists at all.</b> Without it a random draw can legitimately land the
	 * player a few blocks from where they stood — a full jump's worth of EU spent to go nowhere, which
	 * reads as a broken feature rather than as bad luck.
	 */
	public static Offset offsetInRing(double angleFraction, double radiusFraction,
			int minRadius, int maxRadius) {
		double min = Math.max(0.0, Math.min(minRadius, maxRadius));
		double max = Math.max(0.0, Math.max(minRadius, maxRadius));
		double u = clampUnit(radiusFraction);
		double radius = Math.sqrt(min * min + u * (max * max - min * min));
		double angle = clampUnit(angleFraction) * 2.0 * Math.PI;
		return new Offset((int) Math.round(Math.cos(angle) * radius),
				(int) Math.round(Math.sin(angle) * radius));
	}

	/** Fold a draw into {@code [0, 1]}; a supplier handing out rubbish must not throw the geometry off. */
	private static double clampUnit(double value) {
		if (Double.isNaN(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}
}
