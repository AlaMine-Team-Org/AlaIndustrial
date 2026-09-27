package dev.alaindustrial.block;

import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * The sleeve colour of an insulated cable (MOD-666), shared by the block tint, the item tint and the
 * accessory renderer so the three can never disagree.
 *
 * <p>The sleeve texture is light grey and takes all its colour from this multiply. {@link #UNDYED}
 * reproduces the black rubber every cable had before dyeing existed pixel for pixel: the texture's two
 * greys (255 and 195) times this colour give back the original {@code #221E1B} and {@code #1A1715}.
 */
public final class CableColors {

	/** Plain rubber: the colour of an undyed sleeve. */
	public static final int UNDYED = 0xFF221E1B;

	/**
	 * Black dye as a slate grey rather than vanilla's {@code #1D1D21}: that is within a few units of
	 * {@link #UNDYED}, so a cable dyed black would be indistinguishable from an undyed one.
	 */
	public static final int BLACK = 0xFF34373F;

	private CableColors() {
	}

	/** The opaque sleeve colour for {@code dye}, or {@link #UNDYED} for {@code null}. */
	public static int sleeve(@Nullable DyeColor dye) {
		if (dye == null) {
			return UNDYED;
		}
		if (dye == DyeColor.BLACK) {
			return BLACK;
		}
		return 0xFF000000 | (dye.getTextureDiffuseColor() & 0x00FFFFFF);
	}
}
