package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The reactor room's light (MOD-468, stage 1) — a shell block that only lights up once the room is
 * sealed.
 *
 * <p>A sealed room is a windowless box, and at night it is pitch dark inside: the player needs a lamp
 * that is part of the wall rather than a torch stuck on it (torches are not shell, so one placed in
 * the wall would read as a breach). This block is shell, so it can replace any casing cell.
 *
 * <p><b>The light shines inwards — but geometry does NOT arrange that for free.</b> This class first
 * tried to glow by itself, reasoning that a sealed shell is surrounded by solid blocks so the light
 * could only fall inside. That was wrong, and a playtest showed it: a lamp sits IN the wall, its outer
 * face is open to the sky, and block light radiates in every direction — a lit room glowed across the
 * whole landscape. The lamp is therefore dark in itself, and
 * {@link dev.alaindustrial.core.structure.RoomValidator#applyFormed} places a vanilla
 * {@code minecraft:light} in the interior cell it faces. All the light is born inside the room.
 *
 * <p>Two consequences worth knowing: a lamp built into an edge or a corner of the box lights nothing
 * (it has no neighbour inside the room), and the light is not placed if the player has built something
 * in that cell.
 *
 * <p>Gating all of this on {@code formed} is the honest signal: a lamp that lit up in a half-built
 * wall would say the structure works when it does not. Here, light means the room is done.
 */
public class ReactorLampBlock extends ReactorShellBlock {

	public static final MapCodec<ReactorLampBlock> CODEC = simpleCodec(ReactorLampBlock::new);

	/** Bright enough to keep a sealed room mob-free at any size the cap allows. */
	public static final int LIT_LEVEL = 15;

	public ReactorLampBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends ReactorLampBlock> codec() {
		return CODEC;
	}

	/**
	 * The lamp block itself emits <b>nothing</b>, and that is the fix for the first playtest's
	 * complaint that a lit room glowed across the countryside. Block light radiates in every direction,
	 * so a glowing wall block lights the outside exactly as much as the inside. Instead
	 * {@link dev.alaindustrial.core.structure.RoomValidator#applyFormed} drops a vanilla
	 * {@code minecraft:light} into the interior cell this lamp faces, so all the light is born inside
	 * the room. What the player sees on the lamp's own face is its texture, not emission.
	 *
	 * <p>Kept as a named method because the light level is still the lamp's property in every sense
	 * that matters — the interior light source is created at exactly this brightness.
	 */
	public static int lightLevel(BlockState state) {
		return 0;
	}
}
