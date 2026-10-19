package dev.alaindustrial.settlement;

/**
 * The rules of the reception capsule (MOD-833; epic MOD-824, decisions R6, R36, R39): what one shift worker
 * costs, whether the capsule may restore one now, and how much is still missing. The block entity feeds it what
 * it sees on a tick and acts on the answer; nothing here touches the world.
 *
 * <p><b>Nothing is taken until a worker stands in the world.</b> The capsule only checks the price while it
 * works; it takes the EU and the materials in one go on the tick the worker steps out. A refusal — no core, the
 * core dark or away, the capsule outside the territory, the town unclaimed or full, too little EU or material, the
 * archive silent — therefore never costs anything, whichever tick it comes on.
 *
 * <p><b>The order of the refusals is the order a player fixes them in.</b> First where the capsule stands (a town,
 * its core in place, inside the territory), then the town (lit, claimed, room for one more), then what the player
 * puts in (materials before energy: energy arrives by itself, materials do not). The archive's answer comes last:
 * only a capsule that has everything else learns that no worker can be issued.
 *
 * <p>Minecraft-free on purpose: the L1 lane checks the verdict, the price and the shortfall directly
 * ({@code ReceptionCapsuleRulesTest}).
 */
public final class ReceptionCapsuleRules {

	/** A stack of the price's materials never holds more than this; a slot holds one stack. */
	public static final int STACK_LIMIT = 64;
	/** Bringing a dead worker back costs this many new workers' price (epic decision R39). */
	public static final int RETURN_PRICE_FACTOR = 2;

	private ReceptionCapsuleRules() {
	}

	/** The materials of the price, one capsule slot each, in slot order. */
	public enum Material {
		/** The suit's electronics. */
		CIRCUIT,
		/** The suit itself. */
		RUBBER,
		/** The helmet's copper and the battery cylinder. */
		COPPER_PLATE;

		/** How many one new worker costs, from {@link SettlementConfig}, at most {@link #STACK_LIMIT}. */
		public int perWorker() {
			int knob = switch (this) {
				case CIRCUIT -> SettlementConfig.capsuleCircuitsPerWorker;
				case RUBBER -> SettlementConfig.capsuleRubberPerWorker;
				case COPPER_PLATE -> SettlementConfig.capsuleCopperPlatesPerWorker;
			};
			return clampCount(knob);
		}

		/** How many restoring a worker costs: a new one's price, or {@link #RETURN_PRICE_FACTOR} times it. */
		public int price(boolean returning) {
			return clampCount((long) perWorker() * (returning ? RETURN_PRICE_FACTOR : 1));
		}
	}

	/** Why the capsule restores nobody right now — or {@link #READY} when it may. */
	public enum Verdict {
		/** No town's territory holds the capsule: there is no core around it at all. */
		NO_CORE,
		/** The capsule's town has its core taken away (R36): the town hibernates. */
		CORE_AWAY,
		/** The capsule stands outside its town's territory — the core was moved away from it. */
		OUTSIDE,
		/** The town's core is dark: no territory, no capsule (R5, R10). */
		DARK,
		/** Nobody owns the town yet: a worker belongs to the town's owner (R34, R37). */
		UNCLAIMED,
		/** The town holds as many workers as its tier allows. */
		AT_LIMIT,
		/** A material of the price is missing from its slot. */
		NEED_MATERIALS,
		/** The capsule's buffer does not hold the price yet. */
		NEED_ENERGY,
		/** Everything is paid, but the archive cannot issue a worker: no number left, or no worker to restore. */
		ARCHIVE_SILENT,
		/** Everything is in place: the capsule restores. */
		READY;

		/** Whether the capsule stands still because of this. */
		public boolean blocking() {
			return this != READY;
		}

		/** The verdict with this ordinal, or {@link #NO_CORE} for one out of range — what a GUI channel sends. */
		public static Verdict byOrdinal(int ordinal) {
			Verdict[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NO_CORE;
		}
	}

	/**
	 * What the capsule sees on one tick.
	 *
	 * @param hasTown        a town is bound to the capsule
	 * @param corePlaced     that town's core stands in the world
	 * @param inside         the capsule lies inside that town's territory
	 * @param lit            that town's core is lit
	 * @param claimed        that town has an owner
	 * @param living         the town's living workers
	 * @param limit          how many workers the town's tier allows
	 * @param euStored       EU in the capsule's buffer
	 * @param euPrice        EU the next worker costs
	 * @param have           the count of each {@link Material} in its slot, in material order
	 * @param need           the count of each {@link Material} the next worker costs, in material order
	 * @param archiveAnswers the archive can issue a worker at all
	 */
	public record Situation(boolean hasTown, boolean corePlaced, boolean inside, boolean lit, boolean claimed,
			int living, int limit, long euStored, long euPrice, int[] have, int[] need, boolean archiveAnswers) {

		public Situation {
			if (have.length != Material.values().length || need.length != Material.values().length) {
				throw new IllegalArgumentException("one count per material: have " + have.length + ", need "
						+ need.length + ", materials " + Material.values().length);
			}
			have = have.clone();
			need = need.clone();
		}
	}

	/** The capsule's answer for {@code situation}; see the class comment for the order. */
	public static Verdict verdict(Situation situation) {
		if (!situation.hasTown()) {
			return Verdict.NO_CORE;
		}
		if (!situation.corePlaced()) {
			return Verdict.CORE_AWAY;
		}
		if (!situation.inside()) {
			return Verdict.OUTSIDE;
		}
		if (!situation.lit()) {
			return Verdict.DARK;
		}
		if (!situation.claimed()) {
			return Verdict.UNCLAIMED;
		}
		if (situation.living() >= situation.limit()) {
			return Verdict.AT_LIMIT;
		}
		if (!hasMaterials(situation.have(), situation.need())) {
			return Verdict.NEED_MATERIALS;
		}
		if (euMissing(situation.euStored(), situation.euPrice()) > 0) {
			return Verdict.NEED_ENERGY;
		}
		return situation.archiveAnswers() ? Verdict.READY : Verdict.ARCHIVE_SILENT;
	}

	/** EU of the price the buffer does not hold yet, never negative. */
	public static long euMissing(long stored, long price) {
		return Math.max(0L, price - Math.max(0L, stored));
	}

	/** How many of a material are still missing from its slot, never negative. */
	public static int missing(int have, int need) {
		return Math.max(0, need - Math.max(0, have));
	}

	/** Whether every material of the price is in its slot. */
	public static boolean hasMaterials(int[] have, int[] need) {
		for (int i = 0; i < need.length; i++) {
			if (missing(have[i], need[i]) > 0) {
				return false;
			}
		}
		return true;
	}

	/** EU restoring a worker costs: a new one's price, or {@link #RETURN_PRICE_FACTOR} times it (R39). */
	public static long euPrice(boolean returning) {
		long perWorker = Math.max(1, SettlementConfig.capsuleEuPerWorker);
		return perWorker * (returning ? RETURN_PRICE_FACTOR : 1);
	}

	/** The capsule's buffer: room for the dearest restoration, a returning worker's (R39). */
	public static long bufferCapacity() {
		return euPrice(true);
	}

	/** The counts of every material a restoration costs, in material order. */
	public static int[] materialPrice(boolean returning) {
		Material[] all = Material.values();
		int[] counts = new int[all.length];
		for (int i = 0; i < all.length; i++) {
			counts[i] = all[i].price(returning);
		}
		return counts;
	}

	/** Ticks one restoration takes once everything is in, at least 1. */
	public static int restoreTicks() {
		return Math.max(1, SettlementConfig.capsuleRestoreTicks);
	}

	/** The scale the screen is sent the restoration's progress on: per mille, whatever the duration (MOD-847). */
	public static final int SHOWN_SCALE = 1000;

	/** The least a started restoration shows: one percent of {@link #SHOWN_SCALE} (owner decision, MOD-847). */
	public static final int SHOWN_FLOOR = SHOWN_SCALE / 100;

	/**
	 * The restoration's progress as the screen shows it, in parts of {@link #SHOWN_SCALE}: 0 before it starts, at
	 * least one percent from its first tick on — a day-long restoration would otherwise sit on 0 % for over three
	 * minutes and look as if nothing happened — and the whole scale once done. A fixed scale also keeps the value
	 * inside the short a menu channel carries, whatever duration an operator sets.
	 */
	public static int shownProgress(int progress, int duration) {
		if (progress <= 0 || duration <= 0) {
			return 0;
		}
		if (progress >= duration) {
			return SHOWN_SCALE;
		}
		return (int) Math.max(SHOWN_FLOOR, Math.min(SHOWN_SCALE - 1, (long) SHOWN_SCALE * progress / duration));
	}

	/**
	 * The restoration's progress after one tick of {@code verdict}: one tick further while the capsule is
	 * {@link Verdict#READY}, unchanged otherwise — a restoration halted by a dark core, a full town or a slot
	 * emptied mid-way keeps what it had and goes on from there (the capsule spec: nothing is lost).
	 */
	public static int advance(int progress, int duration, Verdict verdict) {
		int clamped = Math.max(0, Math.min(progress, duration));
		return verdict == Verdict.READY ? Math.min(duration, clamped + 1) : clamped;
	}

	/** A material count clamped into one stack: never below 0, never above {@link #STACK_LIMIT}. */
	static int clampCount(long count) {
		return (int) Math.max(0L, Math.min(STACK_LIMIT, count));
	}
}
