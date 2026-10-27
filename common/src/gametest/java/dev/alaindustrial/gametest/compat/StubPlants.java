package dev.alaindustrial.gametest.compat;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Version facade (ADR-036) for a stand-in "foreign mod plant" (MOD-906): a {@link BonemealableBlock} that is
 * not a block, so a gametest can hand it to {@code ReinforcedBonemeal.grow} without registering anything. Its
 * overrides must spell the line's signature of the bone-meal trio, which is why it lives here.
 *
 * <p><b>This twin: Minecraft 26.3</b>, where the trio takes a {@link BonemealSource}.
 */
public final class StubPlants {

	private StubPlants() {}

	/**
	 * A plant that is always a valid target, passes its success roll with probability {@code chance}, and
	 * counts every growth step it is given in {@code performed}.
	 */
	public static BonemealableBlock rolling(double chance, AtomicInteger performed) {
		return new BonemealableBlock() {
			@Override
			public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state,
					BonemealSource source) {
				return true;
			}

			@Override
			public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state,
					BonemealSource source) {
				return random.nextDouble() < chance;
			}

			@Override
			public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state,
					BonemealSource source) {
				performed.incrementAndGet();
			}
		};
	}
}
