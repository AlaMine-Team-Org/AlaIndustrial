package dev.alaindustrial.block;

import net.minecraft.util.StringRepresentable;

/**
 * What the Charging Station's indicator is showing (MOD-274) — the block's whole user interface.
 *
 * <p>The station has no screen on purpose: its entire promise is that the player never opens one. That
 * makes the indicator the only channel it has to answer the two questions someone standing on it will
 * actually ask — "is it doing anything?" and, when it is not, "is that because I am full or because it
 * is?". Those are different problems with different fixes (walk away vs. build more generation), so
 * they get different colours rather than sharing one "not charging" look.
 *
 * <p>Deliberately four states and not the boolean {@code lit} every other machine uses: {@code lit}
 * answers only the first question. {@link dev.alaindustrial.block.entity.MachineBlockEntity#updateLit}
 * is typed against {@code BlockStateProperties.LIT}, so it cannot drive this — the station keeps its own
 * property and its own update method.
 */
public enum ChargePadState implements StringRepresentable {
	/** Nobody is standing on it. Dark indicator, no light — a station at rest is not a light source. */
	IDLE("idle"),
	/** Energy is moving into the player's gear right now. */
	CHARGING("charging"),
	/** Someone is standing on it and everything they carry is already full — the "you are done" signal. */
	READY("ready"),
	/** Someone is standing on it and its buffer is empty — the station is the problem, not the player. */
	EMPTY("empty");

	private final String serializedName;

	ChargePadState(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	/**
	 * Emitted light level for this state.
	 *
	 * <p>Charging and ready sit at 10 — bright enough to read across a room, below the 13 the working
	 * machines use so a bank of stations does not out-glow the furnaces. Empty gets a dim 3: visible in
	 * the dark, but a station that cannot do its job should not look like one that can. Idle emits
	 * nothing, which also means a station nobody uses never lights a base.
	 */
	public int lightLevel() {
		return switch (this) {
			case IDLE -> 0;
			case CHARGING, READY -> 10;
			case EMPTY -> 3;
		};
	}
}
