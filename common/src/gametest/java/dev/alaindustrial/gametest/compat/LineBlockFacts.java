package dev.alaindustrial.gametest.compat;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/**
 * Version facade (ADR-036) for reading block facts whose spelling differs between the Minecraft lines, so a
 * gametest can compare them with ONE reference on both lines. The signatures are the same on both lines; the
 * bodies are this line's.
 *
 * <p><b>This twin: Minecraft 26.3</b>, whose push reactions are {@code PUSH_PULL}, {@code PUSH}, {@code POPPED},
 * {@code IMMOVEABLE} and {@code IGNORE_ENTITY} (26.2: {@code NORMAL}, {@code PUSH_ONLY}, {@code DESTROY},
 * {@code BLOCK}, {@code IGNORE}; verified by javap of both {@code minecraft-merged.jar}), and whose flowing fluid
 * washes away a block when it is in the {@code #minecraft:washed_away_by_fluids} tag
 * ({@code FlowingFluid.canHoldAnyFluid} reads {@code BlockTags.WASHED_AWAY_BY_FLUIDS}; javap).
 */
public final class LineBlockFacts {

	private LineBlockFacts() {}

	/**
	 * What a piston does to {@code state}, as a word both lines share: {@code normal}, {@code push_only},
	 * {@code pops}, {@code pinned} or {@code ignore}. The switch names every constant of the line's enum, so a
	 * constant the line adds stops the compilation instead of falling into a default.
	 */
	public static String pushIntent(BlockState state) {
		PushReaction reaction = state.getPistonPushReaction();
		return switch (reaction) {
			case PUSH_PULL -> "normal";
			case PUSH -> "push_only";
			case POPPED -> "pops";
			case IMMOVEABLE -> "pinned";
			case IGNORE_ENTITY -> "ignore";
		};
	}

	/**
	 * Whether this line's own mechanism marks {@code block} to be washed away by flowing fluid — the mark that
	 * {@code compat.LineBlockProps.washedAwayByFluids} stands for, read where the line keeps it. 26.3: membership
	 * of the {@code #minecraft:washed_away_by_fluids} block tag, filled by the mod's
	 * {@code data/minecraft/tags/block/washed_away_by_fluids.json}.
	 */
	public static boolean washedAwayByLine(Block block) {
		return block.defaultBlockState().is(BlockTags.WASHED_AWAY_BY_FLUIDS);
	}
}
