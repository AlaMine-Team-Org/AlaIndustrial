package dev.alaindustrial.core.item;

import java.util.List;
import net.minecraft.util.StringRepresentable;

/**
 * What one pipe face DRAWS: the routing mode the player configured, plus whether the arm reaching
 * that face is dropped toward a half-block neighbour (MOD-540).
 *
 * <p><b>Why this is a second enum and not a flag beside {@link PipeFaceMode}.</b> Cables carry their
 * low-arm answer in four extra boolean properties (MOD-042), which works because every cable property
 * is already boolean: it took that block from 128 states to 2048. A pipe face is not a boolean — it is
 * a four-value enum, six times over — so the same four booleans would multiply the item pipe from
 * 4 096 states to 65 536 and the fluid pipe from 8 192 to 131 072. States are not free: for a block
 * without a dynamic shape, {@code BlockBehaviour.BlockStateBase.initCache()} builds and keeps a
 * {@code VoxelShape} per state.
 *
 * <p>What that accounting missed, and MOD-562 paid for: {@code initCache()} asks the block for its
 * shape TWENTY times per state, so a pipe that assembled its shape in {@code getShape} paid the
 * {@code Shapes.or} twenty times over — 440 s of a 530 s client startup. The shape now comes from
 * {@link dev.alaindustrial.block.FaceShapeTable}, which holds one entry per geometry; the mode a face
 * shows is texture, not geometry, so these seven values still cost only the states, never the
 * assembly. See ADR-023.
 *
 * <p>Folding "low" into the face value instead costs 7 values on the four horizontal faces (the
 * vertical two keep the plain four — a pipe above or below a slab already meets it correctly), which
 * is 38 416 / 76 832 states: the same feature for 40 % of the state count, and no combination that
 * cannot happen — there is no {@code disabled_low}, because a face drawing nothing cannot drop it.
 *
 * <p>{@link PipeFaceMode} stays exactly as it is: its ordinals are the on-disk format, packed two bits
 * per face into the block entity, and the transport code (item/fluid networks, the wrench) reads the
 * mode from there. This enum never leaves the blockstate.
 */
public enum PipeFaceRender implements StringRepresentable {
	DISABLED("disabled", PipeFaceMode.DISABLED, false),
	NEUTRAL("neutral", PipeFaceMode.NEUTRAL, false),
	EXTRACT("extract", PipeFaceMode.EXTRACT, false),
	INSERT("insert", PipeFaceMode.INSERT, false),
	NEUTRAL_LOW("neutral_low", PipeFaceMode.NEUTRAL, true),
	EXTRACT_LOW("extract_low", PipeFaceMode.EXTRACT, true),
	INSERT_LOW("insert_low", PipeFaceMode.INSERT, true);

	/**
	 * The values a vertical face may take. Handed to {@code EnumProperty.create(name, class, list)}
	 * so up/down faces stay four-valued: their arms are already correct against a half-block, and
	 * paying for three unreachable values on two more faces would undo most of what this enum saves.
	 *
	 * <p>These four are also exactly the value names the property carried before MOD-540, which is why
	 * worlds saved earlier load unchanged: a chunk palette resolves a property by its name and its
	 * value by name, and every old name is still in the set.
	 */
	public static final List<PipeFaceRender> VERTICAL = List.of(DISABLED, NEUTRAL, EXTRACT, INSERT);

	private final String serializedName;
	private final PipeFaceMode mode;
	private final boolean low;

	PipeFaceRender(String serializedName, PipeFaceMode mode, boolean low) {
		this.serializedName = serializedName;
		this.mode = mode;
		this.low = low;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	/** The routing mode this face shows. */
	public PipeFaceMode mode() {
		return mode;
	}

	/** Whether the arm on this face drops to hug a half-block neighbour. */
	public boolean low() {
		return low;
	}

	/**
	 * The value a face with this mode takes. A {@link PipeFaceMode#DISABLED} face draws nothing, so it
	 * is never low — asking for one returns {@link #DISABLED} rather than throwing, because the caller
	 * derives {@code low} from the neighbour's shape and the mode from the block entity, and those two
	 * are answered independently.
	 */
	public static PipeFaceRender of(PipeFaceMode mode, boolean low) {
		return switch (mode) {
			case DISABLED -> DISABLED;
			case NEUTRAL -> low ? NEUTRAL_LOW : NEUTRAL;
			case EXTRACT -> low ? EXTRACT_LOW : EXTRACT;
			case INSERT -> low ? INSERT_LOW : INSERT;
		};
	}
}
