package dev.alaindustrial.core.energy;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Everything the distribution kernel reads about one line for one tick (MOD-715, batch 6): which positions
 * are cables and their buffers, the fields that give the flow a direction, the face permissions of the
 * endpoints, and the stranded segments. {@link EnergyNetwork} builds it from its topology cache; a test
 * builds it from a synthetic graph — a hand-rolled {@code Set::contains} over fake positions drives the same
 * code path, with no {@code ServerLevel} behind it.
 *
 * <p>Generic over the position type {@code P} (MOD-715, batch 8): the kernel never names a block position
 * or a direction. A position's neighbours come from {@link #faces()} as numbered steps — the network
 * numbers a block's six in {@code Direction} order — and the face permissions take the same numbers. The
 * order the kernel visits cables in is not decided here: it arrives already ordered ({@link #propagationOrder},
 * {@link #strandedOrder}), so the kernel itself needs no position order of its own.
 *
 * <p>Read-only from the kernel's side: it never changes a field, only the buffers the lookups return.
 *
 * @param faces the steps from a position to its neighbours; face {@code i} is the same side everywhere
 * @param isCable whether a position is a cable of this line
 * @param cableBufferAt the live buffer of a cable, or {@code null} when the block is no longer a cable
 * @param consumerDistance cable distance from a consumer to its nearest producer — the MOD-021 loss
 * @param flowPotential the potential energy flows down, or {@code null} off the active field (MOD-252)
 * @param machinePotential distance to the nearest waiting machine, the fork tie-break only (MOD-254)
 * @param propagationOrder the cables in ascending flow potential, the sweep order (MOD-070)
 * @param canDrawFace may the endpoint at a position draw energy through that face (MOD-255)
 * @param canFeedFace may the source at a position feed energy through that face (MOD-255)
 * @param strandedOrder the cables the downhill rule cannot reach, farthest from the source first (MOD-318)
 * @param strandedProducerDistance distance from the supplying producers, the stranded fill's direction
 * @param <P> the position type of a cable or an endpoint
 */
record LineView<P>(
		List<UnaryOperator<P>> faces,
		Predicate<P> isCable,
		Function<P, EnergyBuffer> cableBufferAt,
		Function<P, Integer> consumerDistance,
		Function<P, Integer> flowPotential,
		Function<P, Integer> machinePotential,
		List<P> propagationOrder,
		FaceGate<P> canDrawFace,
		FaceGate<P> canFeedFace,
		List<P> strandedOrder,
		Function<P, Integer> strandedProducerDistance) {

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
