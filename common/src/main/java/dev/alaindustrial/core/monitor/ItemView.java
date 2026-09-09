package dev.alaindustrial.core.monitor;

/**
 * A read-only look at somebody else's storage (MOD-480).
 *
 * <p>Deliberately NOT {@code ItemPort}: that interface only knows how to {@code moveTo}, and moving
 * is exactly what this system never does. The monitor network counts what is in the player's chests
 * and leaves every item where it lies.
 */
@FunctionalInterface
public interface ItemView {

	/** Add whatever of {@code sink}'s watched types is held here. */
	void tally(StockTally sink);
}
