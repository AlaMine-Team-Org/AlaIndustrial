package dev.alaindustrial.block;

import java.util.Optional;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The glass a teleporter capsule cell was built from (MOD-112) — the eighteen vanilla glass blocks.
 *
 * <p><b>Why an enum and not a tag.</b> A cell keeps its glass in its block state, and hands exactly that
 * block back through its loot table. A tag would let modded glass in, and a state has nowhere to write
 * down a block it has no value for: the capsule would quietly swap a player's modded glass for a vanilla
 * one on the way out. Accepting only what can be returned is the honest shape.
 *
 * <p>The serialized names are the dye's own ({@code light_blue}), and {@code tools/gen_teleporter_capsule_assets.py}
 * writes one blockstate variant and one loot entry per value under those names.
 */
public enum CapsuleGlass implements StringRepresentable {
	CLEAR(null),
	WHITE(DyeColor.WHITE),
	ORANGE(DyeColor.ORANGE),
	MAGENTA(DyeColor.MAGENTA),
	LIGHT_BLUE(DyeColor.LIGHT_BLUE),
	YELLOW(DyeColor.YELLOW),
	LIME(DyeColor.LIME),
	PINK(DyeColor.PINK),
	GRAY(DyeColor.GRAY),
	LIGHT_GRAY(DyeColor.LIGHT_GRAY),
	CYAN(DyeColor.CYAN),
	PURPLE(DyeColor.PURPLE),
	BLUE(DyeColor.BLUE),
	BROWN(DyeColor.BROWN),
	GREEN(DyeColor.GREEN),
	RED(DyeColor.RED),
	BLACK(DyeColor.BLACK),
	TINTED(null);

	@Nullable
	private final DyeColor dye;

	CapsuleGlass(@Nullable DyeColor dye) {
		this.dye = dye;
	}

	/** The block this value stands for — resolved on call, so the enum can load before {@link Blocks}. */
	public Block block() {
		if (this == CLEAR) {
			return Blocks.GLASS;
		}
		if (this == TINTED) {
			return Blocks.TINTED_GLASS;
		}
		return Blocks.STAINED_GLASS.pick(dye);
	}

	/**
	 * The value for a glass block, or empty for anything else.
	 *
	 * <p>Compared by identity with the vanilla block, not by {@code instanceof StainedGlassBlock}: a modded
	 * subclass carrying the same dye would otherwise come back out as vanilla glass.
	 */
	public static Optional<CapsuleGlass> of(BlockState state) {
		Block block = state.getBlock();
		for (CapsuleGlass glass : values()) {
			if (glass.block() == block) {
				return Optional.of(glass);
			}
		}
		return Optional.empty();
	}

	@Override
	public String getSerializedName() {
		return this == CLEAR ? "clear" : this == TINTED ? "tinted" : dye.getSerializedName();
	}
}
