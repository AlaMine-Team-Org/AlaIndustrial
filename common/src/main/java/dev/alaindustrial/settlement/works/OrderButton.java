package dev.alaindustrial.settlement.works;

/**
 * The one button a building's row in the core's «Construction» tab and its card show, by the building's state (round
 * 2 of 0.1.219, owner's answers Q1, Q2); {@link TownOrders#button} picks it.
 *
 * <p>Minecraft-free on purpose: the rows are data.
 */
public enum OrderButton {
	/** A ruin of the town's structure, never restored: «Restore». */
	RESTORE,
	/** A house a player laid, nothing of it built: «Build». */
	BUILD,
	/** An order that was called off and may go on from where it stopped: «Resume». */
	RESUME,
	/** A building restored once and damaged since: «Repair». */
	REPAIR,
	/** An order under way: «Cancel». */
	CANCEL
}
