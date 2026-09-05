package dev.alaindustrial.skill;

/**
 * The four skill branches of the Workstation's upgrade tree (MOD-483).
 *
 * <p>Order is the wire order and the screen order: on the radial layout each branch owns one diagonal
 * spoke, and {@link #ordinal()} picks it. Renaming a constant breaks saved data — the persisted form
 * is {@link #key()}, not the ordinal, so entries can be reordered but not renamed.
 */
public enum SkillBranch {
	/** EU carried on the player: tools, worn armour, the energy pack. */
	ENERGY("energy"),
	/** The hazards the mod already has: radiation dose, bare-cable shock. */
	HAZARD("hazard"),
	/** The player's own machines — and only while they are in the world. */
	MECH("mech"),
	/** The player's own garden machines — same online rule. */
	AGRO("agro");

	private final String key;

	SkillBranch(String key) {
		this.key = key;
	}

	/** Stable identifier used in NBT, on the wire and in lang keys. Never derived from the ordinal. */
	public String key() {
		return key;
	}

	/** The branch with this {@link #key()}, or {@code null} for anything unknown (old save, bad packet). */
	public static SkillBranch byKey(String key) {
		for (SkillBranch branch : values()) {
			if (branch.key.equals(key)) {
				return branch;
			}
		}
		return null;
	}
}
