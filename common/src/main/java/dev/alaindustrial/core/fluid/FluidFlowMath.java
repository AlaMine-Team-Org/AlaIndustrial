package dev.alaindustrial.core.fluid;

/**
 * Pure long-math extraction from {@link FluidNetwork} (MOD-333), mirroring the rationale for
 * {@link TankMath} and {@link FluidMoverMath}: {@link FluidNetwork} is typed on {@code ServerLevel},
 * {@code BlockPos} and {@code Direction} throughout (it looks segments up through
 * {@code level.getBlockEntity}), so not a single line of it is reachable from {@code :common}'s L1
 * test runtime — which has no Minecraft jar by design. It is therefore in pitest's
 * {@code excludedClasses} (MOD-324) and its 81 mutations are permanent NO_COVERAGE. The two rules
 * below are the part of it that never needed Minecraft at all: deterministic functions of their
 * {@code long} inputs. Extracting them puts the anti-slosh threshold and the intake-room test under
 * the L1 suite and into the mutation run, where the rest of the class can never go.
 *
 * <p><b>Behavioural identity.</b> {@link FluidNetwork#propagateOneHop} and
 * {@link FluidNetwork#fillFromSources} call these helpers verbatim. The inline expressions they
 * replace were:
 * <ul>
 *   <li>{@code from.fluidBuffer.amount - to.fluidBuffer.amount} (L193) — see {@link #imbalance};</li>
 *   <li>{@code difference <= 1} hop-skip test (L194) — see {@link #tooSmallToHop};</li>
 *   <li>{@code difference / 2} hop size (L197) — see {@link #hopAmount};</li>
 *   <li>{@code pipe.fluidBuffer.getCapacity() - pipe.fluidBuffer.amount} (L212) — see {@link #room};</li>
 *   <li>{@code room <= 0} intake-skip test (L213) — see {@link #noRoomLeft}.</li>
 * </ul>
 * This is a pure extract: same results, same order of evaluation, no new branching, no MC types.
 * Five small helpers rather than two fused ones, exactly as {@link FluidMoverMath} splits its three:
 * fusing the guard into the amount would collapse it into a sentinel return and would put two
 * independent mutation targets (CONDITIONALS_BOUNDARY on the threshold, MATH on the halving) inside
 * one method, where a single test could no longer say which of them it killed.
 *
 * <p><b>Why {@link #room} does not reuse {@link TankMath#toInsert}.</b> That helper is
 * {@code Math.min(maxAmount, capacity - amount)} — it exists because a tank insert carries a per-op
 * cap. The network's intake has no such cap: the room IS the amount it asks the donor for. Passing
 * {@link Long#MAX_VALUE} as {@code maxAmount} to reuse the helper would read as an accident at the
 * call site and would fuse two unrelated call sites' mutants into one method.
 *
 * <p><b>What these tests can and cannot prove — stated rather than glossed.</b> Two of the five
 * mutants are equivalent <em>in game</em>: flipping {@link #tooSmallToHop}'s {@code <=} to {@code <},
 * or {@link #noRoomLeft}'s, only lets a zero-sized move reach {@code FluidNetwork.moveFluid}, which
 * returns on {@code amount <= 0} <em>before</em> opening a transaction — so no transaction is opened
 * at all, and the player sees nothing. What they do cost differs, and only one of them is negligible:
 * the {@link #tooSmallToHop} mutant adds a call that returns immediately, while the
 * {@link #noRoomLeft} one walks the rest of {@code fillFromSources} for every FULL source on every
 * tick — {@code level.isLoaded} plus a {@code FluidLookup.get().find(...)} block-entity lookup — before
 * arriving at that same early return. So for those two the L1 suite pins the <em>intent</em> and the
 * wasted work, and no L2 or L3 test could ever kill them — which is the argument for extracting the
 * rule rather than leaving it inline. The other three are real:
 * {@link #imbalance}'s MATH mutant ({@code -}→{@code +})
 * would hand over half the segments' SUM, and {@link #hopAmount}'s ({@code /}→{@code *}) twice the
 * gap — both drop the giver below the receiver, which is the sloshing the rule exists to prevent.
 */
final class FluidFlowMath {

	private FluidFlowMath() {
	}

	/**
	 * How much more the giving segment holds than the segment it is about to hand fluid to. Negative
	 * when the neighbour holds more — the uphill case, which {@link #tooSmallToHop} then rejects.
	 * The MATH mutant flips {@code from - to} to {@code from + to}: on two segments holding 100 and 0
	 * it agrees (both give 100), which is why the suite pins an input where they diverge.
	 */
	static long imbalance(long fromAmount, long toAmount) {
		return fromAmount - toAmount;
	}

	/**
	 * Whether the gap is too small to be worth a hop — the {@code continue} in
	 * {@link FluidNetwork#propagateOneHop}. The threshold is {@code <= 1}, not {@code <= 0}, and that
	 * is the deliberate off-by-one this class was extracted for: at a gap of exactly 1 the halving
	 * below yields 0, so the hop would move nothing — {@code moveFluid} bails on {@code amount <= 0}
	 * before it opens a transaction, so the waste is the call itself and not a transaction. The
	 * CONDITIONALS_BOUNDARY mutant ({@code <=}→{@code <}) is precisely the lowered threshold, and it
	 * is invisible in-world — see the class doc.
	 *
	 * <p>The same test doubles as the uphill guard: a negative gap (the neighbour holds more) is
	 * {@code <= 1} too, so a segment never pulls from a fuller neighbour on this path. That is part of
	 * the contract, not a side effect — the reverse pair is visited on its own turn of the loop.
	 */
	static boolean tooSmallToHop(long imbalance) {
		return imbalance <= 1;
	}

	/**
	 * How much to hand over: half the gap, rounded towards zero. Half rather than all of it is the
	 * anti-slosh rule itself ({@link FluidNetwork#propagateOneHop}'s javadoc: it "keeps a line from
	 * sloshing back and forth between two segments on consecutive ticks") — after the hop the giver is
	 * still at or above the receiver, so the next tick does not send it straight back. Rounding
	 * towards zero is load-bearing for the same reason: at a gap of 3 the hop is 1, not 2, because 2
	 * would drop the giver BELOW the receiver. The MATH mutant ({@code /}→{@code *}) hands over twice
	 * the gap, which is that overshoot at its worst.
	 */
	static long hopAmount(long imbalance) {
		return imbalance / 2;
	}

	/**
	 * Free space in a segment about to pull from a source — and, at this call site, also the amount it
	 * asks the donor for. The MATH mutant ({@code capacity - amount}→{@code capacity + amount}) would
	 * over-ask; the target tank's own clamp ({@link TankMath#toInsert}) and the mover's refund path
	 * hide the result from the player, so nothing is destroyed or duplicated — but the segment would
	 * pull far more out of the donor than it can hold and hand it straight back every tick.
	 */
	static long room(long capacity, long amount) {
		return capacity - amount;
	}

	/**
	 * Whether a segment is full — the {@code continue} in {@link FluidNetwork#fillFromSources}.
	 * Boundary: {@code room == 0} counts as full. The {@code <=} (rather than {@code <}) also absorbs
	 * negative room, which is out-of-contract for a well-formed tank but is treated as full
	 * defensively, exactly as {@link FluidMoverMath#nothingExtracted} treats a negative extract as
	 * nothing. The CONDITIONALS_BOUNDARY mutant here is in-game equivalent but NOT free: it sends every
	 * full source through the whole rest of {@link FluidNetwork#fillFromSources} — a chunk-load test and
	 * a {@code FluidLookup} block-entity lookup per source per tick — to reach a {@code moveFluid} that
	 * returns before opening anything. See the class doc.
	 */
	static boolean noRoomLeft(long room) {
		return room <= 0;
	}
}
