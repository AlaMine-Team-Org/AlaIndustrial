package dev.alaindustrial.gametest.compat;

import java.lang.reflect.Field;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/**
 * Version facade (ADR-036) for reading block facts whose spelling differs between the Minecraft lines, so a
 * gametest can compare them with ONE reference on both lines. The signatures are the same on both lines; the
 * bodies are this line's.
 *
 * <p><b>This twin: Minecraft 26.2</b>, whose push reactions are {@code NORMAL}, {@code PUSH_ONLY}, {@code DESTROY},
 * {@code BLOCK} and {@code IGNORE} (26.3: {@code PUSH_PULL}, {@code PUSH}, {@code POPPED}, {@code IMMOVEABLE},
 * {@code IGNORE_ENTITY}; verified by javap of both {@code minecraft-merged.jar}), and whose flowing fluid washes
 * away a block that does not block motion ({@code FlowingFluid.canHoldAnyFluid} reads
 * {@code BlockState.blocksMotion()}, which a block forced non-solid answers {@code false} in every shape; javap).
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
			case NORMAL -> "normal";
			case PUSH_ONLY -> "push_only";
			case DESTROY -> "pops";
			case BLOCK -> "pinned";
			case IGNORE -> "ignore";
		};
	}

	/**
	 * Whether this line's own mechanism marks {@code block} to be washed away by flowing fluid — the mark that
	 * {@code compat.LineBlockProps.washedAwayByFluids} stands for, read where the line keeps it. 26.2: the
	 * {@code forceSolidOff} flag of the block's properties, which that facade sets. The flag has no reader, so it
	 * is read by reflection (the field name is the same in the Fabric and the NeoForge 26.2 jar; javap).
	 */
	public static boolean washedAwayByLine(Block block) {
		try {
			Field forceSolidOff = BlockBehaviour.Properties.class.getDeclaredField("forceSolidOff");
			forceSolidOff.setAccessible(true);
			return forceSolidOff.getBoolean(block.properties());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("BlockBehaviour.Properties.forceSolidOff is gone — update this probe", e);
		}
	}
}
