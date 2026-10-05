package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.alaindustrial.core.machine.AssemblyRefusal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 contract for the assembler's status line (MOD-275), pinned when the enum left
 * {@code AssemblerBlockEntity} for its own file (MOD-714): the ordinals travel over GUI channel 5 and
 * the keys name lang entries, so neither may move. The expected values are written out, not derived.
 */
class AssemblerStatusTest {

	@Test
	void ordinalsAreTheWireFormat() {
		assertEquals(List.of(AssemblerStatus.READY, AssemblerStatus.NO_BLUEPRINT, AssemblerStatus.NO_MATERIALS,
				AssemblerStatus.OUTPUT_FULL, AssemblerStatus.NO_ENERGY, AssemblerStatus.NO_RECIPE),
				List.of(AssemblerStatus.values()));
	}

	@Test
	void translationKeysAreStable() {
		assertEquals("gui.alaindustrial.assembler.status.ready", AssemblerStatus.READY.translationKey());
		assertEquals("gui.alaindustrial.assembler.status.no_blueprint",
				AssemblerStatus.NO_BLUEPRINT.translationKey());
		assertEquals("gui.alaindustrial.assembler.status.no_materials",
				AssemblerStatus.NO_MATERIALS.translationKey());
		assertEquals("gui.alaindustrial.assembler.status.output_full",
				AssemblerStatus.OUTPUT_FULL.translationKey());
		assertEquals("gui.alaindustrial.assembler.status.no_energy", AssemblerStatus.NO_ENERGY.translationKey());
		assertEquals("gui.alaindustrial.assembler.status.no_recipe", AssemblerStatus.NO_RECIPE.translationKey());
	}

	@Test
	void ordinalRoundTrips() {
		for (AssemblerStatus status : AssemblerStatus.values()) {
			assertEquals(status, AssemblerStatus.byOrdinal(status.ordinal()));
		}
	}

	@Test
	void eachRefusalShowsTheStatusOfItsName() {
		assertEquals(AssemblerStatus.NO_BLUEPRINT, AssemblerStatus.of(AssemblyRefusal.NO_BLUEPRINT));
		assertEquals(AssemblerStatus.NO_MATERIALS, AssemblerStatus.of(AssemblyRefusal.NO_MATERIALS));
		assertEquals(AssemblerStatus.NO_RECIPE, AssemblerStatus.of(AssemblyRefusal.NO_RECIPE));
		assertEquals(AssemblerStatus.OUTPUT_FULL, AssemblerStatus.of(AssemblyRefusal.OUTPUT_FULL));
	}

	/** A jam beats a broken blueprint beats the ordinary wait; everything else ranks last (MOD-275, MOD-714). */
	@Test
	void theMoreActionableReasonWins() {
		assertEquals(AssemblerStatus.OUTPUT_FULL,
				AssemblerStatus.moreActionable(AssemblerStatus.NO_RECIPE, AssemblerStatus.OUTPUT_FULL));
		assertEquals(AssemblerStatus.OUTPUT_FULL,
				AssemblerStatus.moreActionable(AssemblerStatus.OUTPUT_FULL, AssemblerStatus.NO_RECIPE));
		assertEquals(AssemblerStatus.NO_RECIPE,
				AssemblerStatus.moreActionable(AssemblerStatus.NO_MATERIALS, AssemblerStatus.NO_RECIPE));
		assertEquals(AssemblerStatus.NO_MATERIALS,
				AssemblerStatus.moreActionable(AssemblerStatus.NO_BLUEPRINT, AssemblerStatus.NO_MATERIALS));
		assertEquals(AssemblerStatus.NO_MATERIALS,
				AssemblerStatus.moreActionable(AssemblerStatus.NO_MATERIALS, AssemblerStatus.NO_ENERGY));
	}

	/** Two reasons of the same rank keep the first: the queue's starting reason stands until a better one. */
	@Test
	void aTieKeepsTheFirstReason() {
		assertEquals(AssemblerStatus.NO_BLUEPRINT,
				AssemblerStatus.moreActionable(AssemblerStatus.NO_BLUEPRINT, AssemblerStatus.READY));
		assertEquals(AssemblerStatus.NO_ENERGY,
				AssemblerStatus.moreActionable(AssemblerStatus.NO_ENERGY, AssemblerStatus.NO_BLUEPRINT));
	}

	@Test
	void outOfRangeOrdinalsFallBackToReady() {
		int last = AssemblerStatus.values().length - 1;
		assertNotEquals(AssemblerStatus.READY, AssemblerStatus.byOrdinal(last));
		assertEquals(AssemblerStatus.READY, AssemblerStatus.byOrdinal(last + 1));
		assertEquals(AssemblerStatus.READY, AssemblerStatus.byOrdinal(-1));
		assertEquals(AssemblerStatus.READY, AssemblerStatus.byOrdinal(Integer.MIN_VALUE));
		// `ordinal >= 0` narrowed to `> 0` is an equivalent mutant: index 0 is READY, the fallback too.
	}
}
