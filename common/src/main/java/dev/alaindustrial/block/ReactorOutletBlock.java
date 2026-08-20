package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.ReactorOutletBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The reactor outlet (MOD-468, stage 3) — the socket a cable plugs into.
 *
 * <p><b>Why the reactor needed one at all.</b> A controller has to stand IN a wall, which leaves it
 * with four faces buried in shell blocks, one face opening into a sealed room a cable cannot leave,
 * and one face outside that the machine family's convention reserves for the player. The result was a
 * reactor that ran perfectly, showed its output on the panel, filled its buffer — and could not be
 * plugged into anything. This block is the answer, and it is the same answer the water loop already
 * uses: if something has to cross a sealed shell, it crosses through a purpose-built part of the
 * shell. Water goes through the inlet, power comes out of the outlet.
 *
 * <p>It is a shell block first, exactly like {@link ReactorPortBlock}: the room scan sees casing, so
 * fitting one into a wall is not a breach, and it wears the same seamless art when the room forms.
 */
public class ReactorOutletBlock extends ReactorShellBlock implements EntityBlock {

	public static final MapCodec<ReactorOutletBlock> CODEC = simpleCodec(ReactorOutletBlock::new);

	public ReactorOutletBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends ReactorShellBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ReactorOutletBlockEntity(pos, state);
	}
}
