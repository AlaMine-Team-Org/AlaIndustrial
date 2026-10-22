package dev.alaindustrial.settlement.drone;

import java.util.Locale;

/**
 * What a town's dispatcher is doing, or why it stands still (MOD-858, epic decision R91) — the line in the head of its
 * screen. Minecraft-free: the block entity picks one, the screen reads its key.
 */
public enum DispatcherStatus {
	/** No town's territory holds the dispatcher. */
	NO_TOWN,
	/** It stands outside its town's town hall. */
	OUTSIDE_HALL,
	/** The town already has a working dispatcher. */
	SECOND,
	/** It works, and no drone of the town is busy. */
	IDLE,
	/** It works, and the town's drones are busy. */
	WORKING;

	/** Whether the dispatcher stands still because of this. */
	public boolean blocking() {
		return this == NO_TOWN || this == OUTSIDE_HALL || this == SECOND;
	}

	/** Whether the dispatcher serves its town: its plan and buffer are the ones the drones use. */
	public boolean serves() {
		return this == IDLE || this == WORKING;
	}

	/** The translation key of the status line. */
	public String key() {
		return "settlement.alaindustrial.dispatcher.status." + name().toLowerCase(Locale.ROOT);
	}

	/** The status with this ordinal, or {@link #NO_TOWN} for one out of range. */
	public static DispatcherStatus byOrdinal(int ordinal) {
		DispatcherStatus[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NO_TOWN;
	}
}
