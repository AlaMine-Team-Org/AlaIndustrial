package dev.alaindustrial.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Applying bone meal from a machine, whose API differs between the Minecraft lines (MOD-703, ADR-036).
 * Every line has a twin of this class with the same signatures; only the bodies differ. The garden drone
 * station and the sprinkler call these instead of the three {@link BonemealableBlock} methods of their
 * line. (Blocks that OVERRIDE those methods cannot go through a facade — an override must spell its
 * line's signature — and stay documented seams in {@code docs/BRANCHES.md}.)
 *
 * <p><b>This twin: Minecraft 26.3</b>, which added a {@link BonemealSource} argument to all three; a
 * machine applying bone meal is {@link BonemealSource#INTERACTION}, like a player's click.
 */
public final class Bonemeal {

	private Bonemeal() {
	}

	/** Whether bone meal can act on {@code state} at {@code pos} at all. */
	public static boolean isValidTarget(BonemealableBlock block, LevelReader level, BlockPos pos, BlockState state) {
		return block.isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION);
	}

	/** Whether this application succeeds (the per-use roll some plants make). */
	public static boolean isSuccess(BonemealableBlock block, Level level, RandomSource random, BlockPos pos,
			BlockState state) {
		return block.isBonemealSuccess(level, random, pos, state, BonemealSource.INTERACTION);
	}

	/** Grows {@code state} at {@code pos} by one bone-meal step. */
	public static void perform(BonemealableBlock block, ServerLevel level, RandomSource random, BlockPos pos,
			BlockState state) {
		block.performBonemeal(level, random, pos, state, BonemealSource.INTERACTION);
	}
}
