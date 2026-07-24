package dev.alaindustrial.core.energy;

/**
 * Per-face energy role (R-NRG-03): what a single block face does in the Energy API.
 *
 * <p>No machine exposes a role on every face. Generators and consumers keep their {@code FACING}
 * front inert and expose {@link #OUT} / {@link #IN} on the other five ({@code facingAwareRole});
 * solar panels keep the working top inert; the water mill and the wind mills expose {@link #OUT}
 * on the back face alone; the BatteryBox splits input and output across two opposite faces with
 * four inert sides. Only cables are {@link #BOTH} everywhere.
 *
 * <p>Whatever a block declares here, its block class must refuse a cable arm on every {@link #NONE}
 * face — a joint the player reads as working while no EU can pass is the MOD-038 / MOD-194 defect.
 * {@code CableFaceParityScenarios} (MOD-199) sweeps every block for exactly that.
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
