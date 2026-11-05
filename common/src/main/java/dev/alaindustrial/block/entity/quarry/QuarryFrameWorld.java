package dev.alaindustrial.block.entity.quarry;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ConcreteRailBeamBlock;
import dev.alaindustrial.block.QuarryFoundationBlock;
import dev.alaindustrial.block.QuarryFramePart;
import dev.alaindustrial.core.structure.QuarryFrameScan.Kind;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import dev.alaindustrial.registry.ModContent;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The world side of {@link QuarryFrameScan} (MOD-950): which block is which part of the frame, and the size rules
 * from the config. Kept out of {@code core}, which names no block class (ADR-039).
 */
public final class QuarryFrameWorld {

	private QuarryFrameWorld() {
	}

	/** Scans the frame whose controller stands at {@code controller}. */
	public static QuarryFrameScan.Result scan(Level level, BlockPos controller) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		QuarryFrameScan.Probe probe = (x, y, z) -> {
			cursor.set(x, y, z);
			// An unloaded cell is no part of a frame: a frame straddling a chunk that is not loaded is not whole
			// as far as anyone can see, and asking would load the chunk.
			return level.isLoaded(cursor) ? kindOf(level.getBlockState(cursor)) : Kind.OTHER;
		};
		return QuarryFrameScan.scan(probe, controller.getX(), controller.getY(), controller.getZ(), limits());
	}

	/**
	 * Marks every cell of {@code layout} formed (hidden under the model) or loose again, and puts quarry shells into
	 * the air cells of {@code shapes} the model reaches into, or takes them out. Cells that are not loaded, or hold
	 * no frame part any more, are left alone; {@code skip} (the controller while it is being removed) too. Written
	 * to the clients only: how a formed cell looks and collides follows the frame's registration, not a neighbour.
	 */
	public static void paint(Level level, QuarryFrameLayout layout, Map<Long, VoxelShape> shapes, boolean formed,
			@Nullable BlockPos skip) {
		Set<Long> frame = new HashSet<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		layout.forEachCell((x, y, z) -> {
			frame.add(BlockPos.asLong(x, y, z));
			cursor.set(x, y, z);
			if (cursor.equals(skip) || !level.isLoaded(cursor)) {
				return;
			}
			BlockState state = level.getBlockState(cursor);
			if (state.hasProperty(QuarryFramePart.FORMED) && state.getValue(QuarryFramePart.FORMED) != formed) {
				BlockState painted = state.setValue(QuarryFramePart.FORMED, formed);
				level.setBlock(cursor.immutable(), painted, Block.UPDATE_CLIENTS);
			}
		});
		Block shell = ModContent.QUARRY_SHELL.get();
		for (long packed : QuarryFrameShapes.solidCells(shapes)) {
			if (frame.contains(packed)) {
				continue;
			}
			cursor.set(packed);
			if (!level.isLoaded(cursor)) {
				continue;
			}
			BlockState state = level.getBlockState(cursor);
			if (formed && state.isAir()) {
				level.setBlock(cursor.immutable(), shell.defaultBlockState(), Block.UPDATE_CLIENTS);
			} else if (!formed && state.is(shell)) {
				level.setBlock(cursor.immutable(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** The size rules as the config sets them now, read per scan so a reload applies at the next one. */
	public static QuarryFrameScan.Limits limits() {
		return new QuarryFrameScan.Limits(Math.max(1, Config.quarryMinSpan), new int[] {
				Math.max(1, Config.quarryMaxSpanTier1),
				Math.max(1, Config.quarryMaxSpanTier2),
				Math.max(1, Config.quarryMaxSpanTier3)});
	}

	/**
	 * What one block is to the frame. Either rail beam makes a path, and the two may be mixed: both shipped as the
	 * quarry's rails (MOD-927), and a rule that counted only one of them is one no player would guess.
	 */
	static Kind kindOf(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof QuarryFoundationBlock foundation) {
			return switch (foundation.tier()) {
				case 1 -> Kind.FOUNDATION_1;
				case 2 -> Kind.FOUNDATION_2;
				default -> Kind.FOUNDATION_3;
			};
		}
		if (block == ModContent.TEMPERED_IRON_SCAFFOLD.get()) {
			return Kind.SCAFFOLD;
		}
		if (block == ModContent.QUARRY_HEAD.get()) {
			return Kind.HEAD;
		}
		if (block == ModContent.QUARRY_CONTROLLER.get()) {
			return Kind.CONTROLLER;
		}
		if (block == ModContent.QUARRY_CART.get()) {
			return Kind.CART;
		}
		if (block == ModContent.CONCRETE_RAIL_BEAM.get() || block == ModContent.PLATE_RAIL_BEAM.get()) {
			return alongX(state) ? Kind.RAIL_X : Kind.RAIL_Z;
		}
		if (block == ModContent.QUARRY_BRIDGE_BEAM.get()) {
			return alongX(state) ? Kind.BRIDGE_X : Kind.BRIDGE_Z;
		}
		return Kind.OTHER;
	}

	private static boolean alongX(BlockState state) {
		return state.getValue(ConcreteRailBeamBlock.AXIS) == Direction.Axis.X;
	}
}
