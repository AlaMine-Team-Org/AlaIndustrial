package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.LeverBlock;

/**
 * The reactor control lever (MOD-514) — the button's twin, for a signal you can leave on.
 *
 * <p><b>Why the room needs one beside the button.</b> The
 * {@link ReactorButtonBlock button} sends a pulse, which is exactly what the airlock wants and what
 * nothing else does. The controller reads a <em>held</em> signal — no signal means the reaction stops —
 * so a scram switch inside the room cannot be a button, and neither can the throttle on a thermal
 * centrifuge or any redstone the player wires up in there. Before this block the only held source
 * available inside a sealed room was a vanilla lever, which melts in a meltdown: the emergency stop
 * would disappear at the exact moment it is reached for.
 *
 * <p>Like the button it is a real {@link LeverBlock} rather than a look-alike — the flick, the latch,
 * the wall/floor/ceiling faces, the redstone it emits and the sound it makes are vanilla's. Only the
 * material is ours, which is the whole point: it is in {@code alaindustrial:meltproof}, so a room that
 * melts its own contents leaves the switch that stops it alone.
 *
 * <p><b>No {@code BlockSetType}, unlike the button.</b> Verified against 26.2: {@code LeverBlock}'s
 * only constructor is {@code (BlockBehaviour.Properties)} and its click sound is hard-coded to
 * {@code SoundEvents.LEVER_CLICK} inside a static helper called from a method whose neighbour-update
 * pass is private. So the click is vanilla's and cannot be re-skinned without reimplementing the block —
 * which would trade a cosmetic gain for the very "looks like a lever, behaves like something else"
 * problem this class exists to avoid. The placement and break sounds are metal, from the properties.
 */
public class ReactorLeverBlock extends LeverBlock {

	/**
	 * Typed as {@code MapCodec<LeverBlock>} rather than {@code MapCodec<ReactorLeverBlock>} because
	 * {@link LeverBlock#codec()} declares that exact return type — the same narrowing the button has to
	 * work around, and for the same reason.
	 */
	public static final MapCodec<LeverBlock> CODEC =
			simpleCodec(ReactorLeverBlock::new).xmap(b -> (LeverBlock) b, b -> (ReactorLeverBlock) b);

	public ReactorLeverBlock(Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<LeverBlock> codec() {
		return CODEC;
	}
}
