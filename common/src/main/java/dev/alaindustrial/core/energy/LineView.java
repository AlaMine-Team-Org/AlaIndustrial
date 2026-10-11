package dev.alaindustrial.core.energy;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Everything the distribution kernel reads about one line for one tick (MOD-715, batch 6): which positions are
 * cables and what they hold, the distances the plan ranks by, which endpoint faces may move energy, and the
 * planner that keeps its arrays from tick to tick (MOD-789, ADR-049).
 *
 * <p>Generic over the position type {@code P} (MOD-715, batch 8): the kernel never names a block position or a
 * direction. A position's neighbours come from {@link #faces()} as numbered steps — the network passes the six
 * block faces, an L1 test a toy grid.
 *
 * @param faces the steps to a position's neighbours, numbered; face {@code i} is the same side everywhere
 * @param isCable is this position a cable of the line
 * @param cableBufferAt the buffer of a cable
 * @param consumerDistance the cables a consumer's energy travelled, for the MOD-021 loss
 * @param producerDistance a cable's distance to the nearest supplying generator, or {@code null} off that field:
 *     the plan serves the nearer of two consumers first (ADR-049), and a line with nothing planned fills outward
 * @param cables every cable of the line, in a stable order; the same list object until the line changes, so the
 *     planner keeps its index
 * @param canDrawFace may the endpoint at a position draw energy through that face (MOD-255)
 * @param canFeedFace may the source at a position feed energy through that face (MOD-255)
 * @param planner the line's planner, kept by the network between ticks
 */
record LineView<P>(
		List<UnaryOperator<P>> faces,
		Predicate<P> isCable,
		Function<P, EnergyBuffer> cableBufferAt,
		Function<P, Integer> consumerDistance,
		Function<P, Integer> producerDistance,
		List<P> cables,
		FaceGate<P> canDrawFace,
		FaceGate<P> canFeedFace,
		LinePlanner<P> planner) {

	/**
	 * May the endpoint at a position move energy through one of its numbered {@link #faces()} (MOD-255)? The
	 * kernel also rotates its source sweep over these numbers (MOD-254).
	 *
	 * @param <P> the position type
	 */
	@FunctionalInterface
	interface FaceGate<P> {
		boolean test(P pos, int face);
	}
}
