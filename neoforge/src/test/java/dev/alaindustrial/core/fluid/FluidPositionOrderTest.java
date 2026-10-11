package dev.alaindustrial.core.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * L1.5 regression for the order a fluid network visits segments of equal standing in (MOD-733): it must
 * not change when a build is moved, including across {@code y = 0} and {@code z = 0} — exactly where the
 * old {@code BlockPos.asLong} tie-break flipped. The world test {@code FluidTieBreakTranslationScenarios}
 * can move a rig across {@code y = 0} only (the framework picks x/z); this covers both planes.
 *
 * <p>Lives in {@code :neoforge:test} because the comparator takes {@link BlockPos}; no world is started,
 * the provider is here for the classpath (as {@code NetworkTopologyDeterminismTest}).
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class FluidPositionOrderTest {

	/** A small cloud of offsets that, placed at the origin, straddles both {@code y = 0} and {@code z = 0}. */
	private static final List<BlockPos> SHAPE = List.of(
			new BlockPos(0, -1, 0), new BlockPos(0, 0, 0), new BlockPos(0, 1, 0),
			new BlockPos(0, 0, -1), new BlockPos(0, 0, 1), new BlockPos(1, -1, -1),
			new BlockPos(-1, 1, 1), new BlockPos(0, -2, 2), new BlockPos(0, 2, -2));

	/** The translations tried, each from the origin build: across zero on y, on z, and on all three. */
	private static final List<BlockPos> SHIFTS = List.of(
			new BlockPos(0, 10, 0), new BlockPos(0, -10, 0), new BlockPos(0, 0, 10), new BlockPos(0, 0, -10),
			new BlockPos(7, 7, 7), new BlockPos(-30_000, 64, 30_000));

	/** Indices of {@link #SHAPE} in the order the comparator puts the shape moved by {@code shift}. */
	private static List<Integer> order(BlockPos shift) {
		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < SHAPE.size(); i++) {
			indices.add(i);
		}
		indices.sort((i, j) -> FluidLineTopology.POSITION_ORDER.compare(SHAPE.get(i).offset(shift),
				SHAPE.get(j).offset(shift)));
		return indices;
	}

	/**
	 * @implements MOD-733-AC2 — the fluid network's equal-standing order is translation-invariant across
	 *     {@code y = 0} and {@code z = 0}
	 */
	@Test
	void orderSurvivesTranslationAcrossZero() {
		List<Integer> atOrigin = order(BlockPos.ZERO);
		for (BlockPos shift : SHIFTS) {
			assertEquals(atOrigin, order(shift), "order changed when the build moved by " + shift);
		}
	}

	/** The two pairs the old {@code asLong} tie-break put the wrong way round: lower coordinate first. */
	@Test
	void lowerCoordinateComesFirstAcrossZero() {
		assertTrue(FluidLineTopology.POSITION_ORDER.compare(new BlockPos(0, -1, 0), new BlockPos(0, 0, 0)) < 0);
		assertTrue(FluidLineTopology.POSITION_ORDER.compare(new BlockPos(0, 64, -1), new BlockPos(0, 64, 0)) < 0);
	}
}
