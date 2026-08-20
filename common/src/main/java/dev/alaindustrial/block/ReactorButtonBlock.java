package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Config;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * The reactor control button (MOD-468, stage 1) — the way out of a sealed room.
 *
 * <p><b>Why the room needs its own button.</b> The airlock only opens on a redstone pulse, so someone
 * standing inside has to be able to send one. A vanilla button would do the job today and melt in
 * stage 4, when everything inside an unshielded room starts turning to lava — and a player who built
 * their exit out of oak would find the exit gone at the worst possible moment. This one is made of the
 * same shielding alloy as the walls, so it survives what the room is for.
 *
 * <p>It is deliberately a real {@link ButtonBlock} rather than a look-alike: pressing, the pressed
 * shape, the redstone pulse and the auto-release are exactly vanilla's, which is what makes it behave
 * the way every player already expects. Only the material, the sound family and the press duration
 * are ours — {@link BlockSetType#IRON} because it is metal, and it takes a hand rather than an arrow.
 *
 * <p>The press lasts {@link Config#reactorButtonPressTicks}, long enough that the pulse comfortably
 * covers the airlock's own opening logic even if the button is wired through a length of dust.
 */
public class ReactorButtonBlock extends ButtonBlock {

	/**
	 * Typed as {@code MapCodec<ButtonBlock>} rather than {@code MapCodec<ReactorButtonBlock>} because
	 * {@link ButtonBlock#codec()} declares that exact return type — unlike the rest of the block
	 * hierarchy, which uses a wildcard and lets subclasses narrow it.
	 */
	public static final MapCodec<ButtonBlock> CODEC =
			simpleCodec(ReactorButtonBlock::new).xmap(b -> (ButtonBlock) b, b -> (ReactorButtonBlock) b);

	public ReactorButtonBlock(Properties properties) {
		super(BlockSetType.IRON, Config.reactorButtonPressTicks, properties);
	}

	@Override
	public MapCodec<ButtonBlock> codec() {
		return CODEC;
	}
}
