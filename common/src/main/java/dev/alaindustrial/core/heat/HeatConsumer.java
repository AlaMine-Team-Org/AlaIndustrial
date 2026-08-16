package dev.alaindustrial.core.heat;

/**
 * A machine that is heated from below, as seen by the Electric Heater underneath it.
 *
 * <p><b>Why this exists (MOD-424).</b> Until the Thermal Centrifuge there was exactly one heated
 * machine, and the heater simply asked {@code instanceof VulcanizerBlockEntity} and compared against
 * {@code VulcanizerStatus}. A second consumer could not be added without editing that check, and the
 * failure mode was silent in both directions: the heater would never warm (it saw no consumer), so the
 * machine above would wait on heat forever, and neither block would log anything. Naming the contract
 * turns that into a compile-time obligation.
 *
 * <p>Deliberately Minecraft-free so the heater's gate can be reasoned about — and unit-tested — without
 * a world, in the same spirit as {@link HeatSource}.
 */
public interface HeatConsumer {
	/**
	 * Whether this machine is blocked on heat <em>right now</em> — the single gate on every EU the heater
	 * spends outside a tick it was actually paid for.
	 *
	 * <p>Two states must answer {@code true}, and getting either wrong breaks the heater:
	 * <ul>
	 *   <li><b>"stocked, blocked only by heat"</b> — this is what should light the stove in the first
	 *       place;</li>
	 *   <li><b>"already being served"</b> — dropping this one makes the heater cool underneath a machine
	 *       it is actively heating, between two paid ticks.</li>
	 * </ul>
	 *
	 * <p>Everything else — no inputs, no recipe, a jammed output, and (new with the centrifuge) no
	 * redstone signal — must answer {@code false}, so a heater under an idle machine still costs exactly
	 * zero and stays cold. That is the heater's founding promise; it is pinned by
	 * {@code VulcanizerScenarios.fun06LoneHeaterNeverWarmsAndSpendsNothing}, and a consumer that answers
	 * {@code true} while switched off would burn a full warm-up ramp under a machine the player
	 * deliberately turned off.
	 */
	boolean isWaitingOnHeat();

	/**
	 * Overclocker chips installed in this machine, so the heater bills the same multiple (MOD-392).
	 * Without it a sped-up machine would finish a batch in fewer ticks while the heater kept charging one
	 * flat tick — making the faster setup pay LESS for heat overall.
	 */
	int overclockerCount();

	/**
	 * Called by the heater the moment it finishes warming, so a machine that went to sleep on "no heat"
	 * starts on the next tick instead of waiting out its idle-sleep poll.
	 */
	void onHeatNeighbourChanged();
}
