package dev.alaindustrial.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * Which piece of a Workstation (MOD-483) a block is: a loose casing, or one half of an assembled
 * machine.
 *
 * <p><b>Three values rather than vanilla's {@code DoubleBlockHalf}.</b> A door or a tall flower is
 * only ever a bottom or a top — the item raises both halves at once, and "not yet a door" is not a
 * state the world can hold. The workstation is the opposite: the player crafts and places single
 * casings, and two of them stacked become the machine. That third state is real, it is what the
 * player holds in hand, and giving it a name here is what makes "an upper half with nothing under
 * it" unrepresentable instead of merely unlikely.
 *
 * <p>The two helpers below replace the ones the vanilla enum would have donated
 * ({@code getDirectionToOther} / {@code getOtherHalf}); both return {@code null} for a loose casing,
 * because a casing has no partner and callers must say what they do about that.
 */
public enum WorkstationPart implements StringRepresentable {
	/** A loose casing: a plain full cube, craftable, placeable and pickable on its own. */
	SINGLE("single"),
	/** The lower half of an assembled machine — the half that owns the energy buffer. */
	LOWER("lower"),
	/** The upper half of an assembled machine: the monitor arm, energy-inert. */
	UPPER("upper");

	private final String serializedName;

	WorkstationPart(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	/** Whether this piece belongs to an assembled machine (i.e. it has a partner to lose). */
	public boolean assembled() {
		return this != SINGLE;
	}

	/** Direction from this piece toward its partner; {@code null} for a loose casing. */
	@Nullable
	public Direction towardPartner() {
		return switch (this) {
			case SINGLE -> null;
			case LOWER -> Direction.UP;
			case UPPER -> Direction.DOWN;
		};
	}

	/** The part its partner must be; {@code null} for a loose casing. */
	@Nullable
	public WorkstationPart partner() {
		return switch (this) {
			case SINGLE -> null;
			case LOWER -> UPPER;
			case UPPER -> LOWER;
		};
	}
}
