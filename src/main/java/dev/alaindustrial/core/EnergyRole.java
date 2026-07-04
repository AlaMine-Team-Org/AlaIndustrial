package dev.alaindustrial.core;

/**
 * Per-face energy role (R-NRG-03): what a single block face does in the Energy API.
 * Generators expose {@link #OUT} on every face, consumers {@link #IN}, cables {@link #BOTH};
 * the BatteryBox is the one block with a mixed layout (output face vs. input faces).
 */
public enum EnergyRole {
	NONE(false, false),
	IN(true, false),
	OUT(false, true),
	BOTH(true, true);

	private final boolean in;
	private final boolean out;

	EnergyRole(boolean in, boolean out) {
		this.in = in;
		this.out = out;
	}

	public boolean canInsert() {
		return in;
	}

	public boolean canExtract() {
		return out;
	}
}
