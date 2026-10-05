package dev.alaindustrial.core.tooltip;

/**
 * A block that declares its own hover tooltip (MOD-716, ADR-040). Its block item's tooltip is
 * {@link #machineTooltip()} rendered by the client's {@code MachineTooltips}; a new machine implements this
 * interface on its block class instead of joining lists kept in a shared file.
 *
 * <p>Looked up with {@code instanceof} on the placed block's class, so a subclass inherits its parent's
 * tooltip unless it overrides the method — the same reach the per-class checks it replaces had.
 *
 * <p>Runs on the client: every number in the description must be read through {@code ServerBalance}, never
 * from {@code Config} directly, or a dedicated server's player reads his own file's numbers
 * ({@code ArchitectureRules.itemTooltipsReadBalanceThroughServerBalance} covers this method too).
 */
public interface HasMachineTooltip {

	/** This block's tooltip; built per call — the lines read their numbers live anyway. */
	MachineTooltipSpec machineTooltip();
}
