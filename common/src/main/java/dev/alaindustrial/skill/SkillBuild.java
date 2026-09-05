package dev.alaindustrial.skill;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * What one player has bought in the Workstation's upgrade tree, and every rule about what they may buy
 * next (MOD-483).
 *
 * <p>Deliberately free of Minecraft types: the tree is arithmetic plus a fork rule, and both are worth
 * testing without a game. Persistence and the wire form live next door in {@code PlayerSkills}; the
 * screen, the purchase packet and the buff lookups all ask this class the same questions, so
 * "can I take this?" has exactly one answer in the codebase rather than one per caller.
 *
 * <p>Immutable: {@link #with} returns a new build. That is what makes a purchase a single
 * read-modify-write against the attachment with no half-applied state if anything throws in between.
 */
public final class SkillBuild {

	/** Nothing bought — the build every player starts with, and the fallback for an unbound store. */
	public static final SkillBuild EMPTY = new SkillBuild(Map.of());

	private final Map<SkillBranch, Set<SkillSlot>> taken;

	private SkillBuild(Map<SkillBranch, Set<SkillSlot>> taken) {
		EnumMap<SkillBranch, Set<SkillSlot>> copy = new EnumMap<>(SkillBranch.class);
		taken.forEach((branch, slots) -> {
			if (!slots.isEmpty()) {
				copy.put(branch, Collections.unmodifiableSet(EnumSet.copyOf(slots)));
			}
		});
		this.taken = Collections.unmodifiableMap(copy);
	}

	/** Build from a raw map — the entry point for both the codec and the wire decoder. */
	public static SkillBuild of(Map<SkillBranch, Set<SkillSlot>> taken) {
		return taken.isEmpty() ? EMPTY : new SkillBuild(taken);
	}

	/** Everything bought, by branch. Unmodifiable, and branches with nothing taken are absent. */
	public Map<SkillBranch, Set<SkillSlot>> taken() {
		return taken;
	}

	/** Whether this exact skill is bought — the question every buff site asks. */
	public boolean has(SkillBranch branch, SkillSlot slot) {
		Set<SkillSlot> slots = taken.get(branch);
		return slots != null && slots.contains(slot);
	}

	/** Points spent so far — what the free count is measured against. */
	public int spent() {
		int total = 0;
		for (Set<SkillSlot> slots : taken.values()) {
			for (SkillSlot slot : slots) {
				total += slot.cost();
			}
		}
		return total;
	}

	/** Points still free at this level. Never negative, even if a build outlives a level rollback. */
	public int free(int earnedPoints) {
		return Math.max(0, earnedPoints - spent());
	}

	/**
	 * Whether a parent of {@code slot} is already bought — the "is it reachable" half of the rule.
	 * The entry has no parents and is therefore always reachable.
	 */
	public boolean reachable(SkillBranch branch, SkillSlot slot) {
		SkillSlot[] parents = slot.parents();
		if (parents.length == 0) {
			return true;
		}
		for (SkillSlot parent : parents) {
			if (has(branch, parent)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether this slot is closed forever because the other side of its fork was taken — the hard-fork
	 * rule, asked here and nowhere else.
	 */
	public boolean blocked(SkillBranch branch, SkillSlot slot) {
		SkillSlot sibling = slot.sibling();
		return sibling != null && has(branch, sibling);
	}

	/**
	 * Why this slot cannot be bought right now, or {@link Refusal#NONE} when it can.
	 *
	 * <p>Returns a reason rather than a boolean because the screen has to explain the refusal and the
	 * server has to reject it — and those two must never disagree about why.
	 */
	public Refusal refuse(SkillBranch branch, SkillSlot slot, int earnedPoints) {
		if (has(branch, slot)) {
			return Refusal.ALREADY_TAKEN;
		}
		if (blocked(branch, slot)) {
			return Refusal.FORK_CLOSED;
		}
		if (!reachable(branch, slot)) {
			return Refusal.LOCKED;
		}
		if (free(earnedPoints) < slot.cost()) {
			return Refusal.NOT_ENOUGH_POINTS;
		}
		return Refusal.NONE;
	}

	/** Shorthand for {@code refuse(...) == Refusal.NONE}. */
	public boolean canBuy(SkillBranch branch, SkillSlot slot, int earnedPoints) {
		return refuse(branch, slot, earnedPoints) == Refusal.NONE;
	}

	/**
	 * This build plus one skill. Callers must have checked {@link #canBuy} first — this method trusts
	 * them, because the check produces a reason the caller has to report either way.
	 */
	public SkillBuild with(SkillBranch branch, SkillSlot slot) {
		EnumMap<SkillBranch, Set<SkillSlot>> next = new EnumMap<>(SkillBranch.class);
		taken.forEach((b, slots) -> next.put(b, EnumSet.copyOf(slots)));
		next.computeIfAbsent(branch, b -> EnumSet.noneOf(SkillSlot.class)).add(slot);
		return new SkillBuild(next);
	}

	/** Slots bought in one branch — what the screen counts as "taken N of 5 possible". */
	public int takenIn(SkillBranch branch) {
		Set<SkillSlot> slots = taken.get(branch);
		return slots == null ? 0 : slots.size();
	}

	/** Points sunk into one branch. */
	public int spentIn(SkillBranch branch) {
		Set<SkillSlot> slots = taken.get(branch);
		if (slots == null) {
			return 0;
		}
		int total = 0;
		for (SkillSlot slot : slots) {
			total += slot.cost();
		}
		return total;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof SkillBuild build && taken.equals(build.taken);
	}

	@Override
	public int hashCode() {
		return taken.hashCode();
	}

	@Override
	public String toString() {
		return "SkillBuild" + taken;
	}

	/** Why a purchase was refused. {@link #NONE} means it was not. */
	public enum Refusal {
		/** It can be bought. */
		NONE,
		/** Already owned. */
		ALREADY_TAKEN,
		/** The other side of this fork was taken, and that is permanent. */
		FORK_CLOSED,
		/** No parent bought yet. */
		LOCKED,
		/** Not enough free points. */
		NOT_ENOUGH_POINTS
	}
}
