package dev.alaindustrial.arch.fixture.clientstandin;

import dev.alaindustrial.arch.fixture.AbstractKnobBase;
import dev.alaindustrial.arch.fixture.DisplayHookItem;
import dev.alaindustrial.arch.fixture.KnobHelper;
import dev.alaindustrial.arch.fixture.KnobShape;
import dev.alaindustrial.arch.fixture.PendingTooltipBuilder;
import dev.alaindustrial.arch.fixture.SuppliedGrade;

/**
 * Deliberate violator for {@code ClientBalanceReachRules} (MOD-761): client code that never reads a knob
 * itself, and reaches one through a helper, a supplier enum and an interface — one method per edge.
 */
public final class IndirectKnobScreen {

	int helperRead() {
		return KnobHelper.twice();
	}

	int suppliedRead() {
		return SuppliedGrade.LOW.amount();
	}

	int virtualRead(KnobShape shape) {
		return shape.amount();
	}

	int acceptedRead() {
		return KnobHelper.accepted();
	}

	int abstractRead(AbstractKnobBase base) {
		return base.amount();
	}

	/** Reaches a knob only through another root, which reports it itself: nothing is reported here. */
	int viaAnotherRoot(DisplayHookItem item) {
		return item.getBarWidth();
	}

	int pendingTooltip() {
		return PendingTooltipBuilder.of();
	}
}
