package dev.alaindustrial.command.demo;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ConcreteRailBeamBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The quarry stand (MOD-950): its own arena west of the showcase, holding one complete quarry frame, so the frame can
 * be looked at, walked through and taken apart without hunting for the blocks. Built by {@code /ala demo quarry
 * build}; the showcase stand keeps one loose block of each part, because its coverage test reads that stand alone.
 *
 * <p>The frame is the largest a tier-1 (tempered iron) foundation allows, 9 × 9 between the towers, 15 × 15 outside:
 * four towers on the designer's 3 × 3 footprint — two layers of foundations, four of scaffolding, one of heads — the
 * controller in the north-west corner of the north-west head; two paths as wide as the heads along X, the near one of
 * concrete rail beams and the far one of plate rail beams; the bridge across them at the middle; the cart on the
 * bridge. A charged Energy Storage feeds the socket — the foundation under the controller — through one copper cable,
 * so the socket is seen to work the moment the frame forms. The controller scans on its first tick, so the frame
 * reports itself at once.
 */
public final class QuarryStand {

	/** West of the showcase stand (which starts at x 0) and clear of the energy stand (z -70 to -20). */
	public static final int ARENA_X = -28;
	public static final int ARENA_Z = 0;
	public static final int WIDTH = 24;
	public static final int DEPTH = 24;
	/**
	 * Cleared above the floor: the head layer is seven blocks up, the bridge and the cart above it; the rest is room
	 * for whatever an earlier build or the terrain left over the arena.
	 */
	public static final int CLEAR_HEIGHT = 24;

	private static final int T = QuarryFrameScan.TOWER;
	/** The frame's north-west tower corner, from the arena origin; the floor is at y 0, the bottom foundations at 1. */
	private static final int FX = 4;
	private static final int FZ = 4;
	private static final int SPAN = 9;
	private static final int FAR = T + SPAN;
	private static final int BASE = 1;
	private static final int HEAD_Y = BASE + QuarryFrameScan.HEAD_HEIGHT;

	private static final Block FLOOR = Blocks.SMOOTH_STONE;

	private QuarryStand() {
	}

	/**
	 * The arena's origin: its north-west corner on the topmost ground block, the way the other stands find theirs —
	 * except that leaves and logs are looked through. Taking a treetop for the floor once put a whole frame in the
	 * air, out of reach of the next build's clearing.
	 */
	public static BlockPos findOrigin(ServerLevel level) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(ARENA_X, 0, ARENA_Z);
		for (int y = level.getMaxY(); y > level.getMinY(); y--) {
			p.setY(y);
			BlockState state = level.getBlockState(p);
			if (!state.isAir() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) {
				return new BlockPos(ARENA_X, y, ARENA_Z);
			}
		}
		return new BlockPos(ARENA_X, level.getMinY(), ARENA_Z);
	}

	/** Clears the arena, lays its floor and builds the frame with its power line; no owner, so it does not dig. */
	public static void build(ServerLevel level, BlockPos origin) {
		build(level, origin, null);
	}

	/**
	 * The same stand, and with an {@code owner} it digs at once (MOD-951): the controller is theirs and holds a fresh
	 * drill bit, as if they had placed it — a quarry breaks blocks only on behalf of a player.
	 */
	public static void build(ServerLevel level, BlockPos origin, @Nullable ServerPlayer owner) {
		clear(level, origin);
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				level.setBlock(origin.offset(x, 0, z), FLOOR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		BlockPos frame = origin.offset(FX, 0, FZ);
		tower(level, frame, 0, 0);
		tower(level, frame, FAR, 0);
		tower(level, frame, 0, FAR);
		tower(level, frame, FAR, FAR);
		set(level, frame.offset(0, HEAD_Y, 0), ModContent.QUARRY_CONTROLLER.get().defaultBlockState());
		int top = HEAD_Y + QuarryFrameScan.PATH_LAYERS - 1;
		for (int y = HEAD_Y; y <= top; y++) {
			for (int x = T; x < FAR; x++) {
				for (int row = 0; row < T; row++) {
					set(level, frame.offset(x, y, row), beam(ModContent.CONCRETE_RAIL_BEAM.get(), Direction.Axis.X));
					set(level, frame.offset(x, y, FAR + row), beam(ModContent.PLATE_RAIL_BEAM.get(), Direction.Axis.X));
				}
			}
		}
		int bridgeX = T + SPAN / 2;
		for (int z = T; z < FAR; z++) {
			set(level, frame.offset(bridgeX, top, z), beam(ModContent.QUARRY_BRIDGE_BEAM.get(), Direction.Axis.Z));
		}
		for (int z = 0; z < FAR + T; z++) {
			if (z > T && z < FAR - 1) {
				continue; // the saddles: over each path and one cell into the pit
			}
			set(level, frame.offset(bridgeX, top + 1, z), beam(ModContent.QUARRY_BRIDGE_BEAM.get(), Direction.Axis.Z));
		}
		set(level, frame.offset(bridgeX, top + 1, (FAR + T - 1) / 2), ModContent.QUARRY_CART.get().defaultBlockState());

		// The power line: an Energy Storage whose OUT face (opposite its facing) points east, a cable, the socket.
		BlockPos box = frame.offset(-2, BASE, 0);
		set(level, box, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		set(level, frame.offset(-1, BASE, 0), ModContent.COPPER_CABLE.get().defaultBlockState());
		if (level.getBlockEntity(box) instanceof BatteryBoxBlockEntity storage) {
			storage.getEnergyStorage().setAmountUntracked(Math.max(0, Config.batteryBoxBuffer));
		}
		if (level.getBlockEntity(frame.offset(0, HEAD_Y, 0)) instanceof QuarryControllerBlockEntity controller) {
			if (owner != null) {
				controller.setOwner(owner.getUUID(), owner.getGameProfile().name());
				ItemStack bit = new ItemStack(ModContent.QUARRY_DRILL_BIT.get());
				controller.setItem(QuarryControllerBlockEntity.BIT_SLOT, bit);
			}
			controller.requestScan();
		}
	}

	/** Empties the arena above its floor. */
	public static void clear(ServerLevel level, BlockPos origin) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				for (int y = 1; y <= CLEAR_HEIGHT; y++) {
					BlockPos pos = origin.offset(x, y, z);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, air, Block.UPDATE_ALL);
					}
				}
			}
		}
	}

	/** Where the camera stands: above the power line's corner, looking across the frame to the south-east. */
	public static double[] camera(BlockPos origin) {
		return new double[] {origin.getX() + 1.5, origin.getY() + HEAD_Y + 8.0, origin.getZ() + 1.5};
	}

	/** Where the controller head is, for the command's reply. */
	public static BlockPos controller(BlockPos origin) {
		return origin.offset(FX, HEAD_Y, FZ);
	}

	/** The north-west cell of the top head layer of the tower opposite the controller's, for the L3 stand. */
	public static BlockPos farTowerTop(BlockPos origin) {
		return origin.offset(FX + FAR, HEAD_Y + QuarryFrameScan.HEAD_LAYERS - 1, FZ + FAR);
	}

	private static void tower(ServerLevel level, BlockPos frame, int x0, int z0) {
		for (int dx = 0; dx < T; dx++) {
			for (int dz = 0; dz < T; dz++) {
				for (int layer = 0; layer < QuarryFrameScan.FOUNDATION_LAYERS; layer++) {
					set(level, frame.offset(x0 + dx, BASE + layer, z0 + dz),
							ModContent.QUARRY_FOUNDATION.get().defaultBlockState());
				}
				for (int layer = 0; layer < QuarryFrameScan.HEAD_LAYERS; layer++) {
					set(level, frame.offset(x0 + dx, HEAD_Y + layer, z0 + dz),
							ModContent.QUARRY_HEAD.get().defaultBlockState());
				}
			}
		}
		for (int y = BASE + QuarryFrameScan.FOUNDATION_LAYERS; y < HEAD_Y; y++) {
			for (int dx = 0; dx < T; dx++) {
				for (int dz = 0; dz < T; dz++) {
					set(level, frame.offset(x0 + dx, y, z0 + dz),
							ModContent.TEMPERED_IRON_SCAFFOLD.get().defaultBlockState());
				}
			}
		}
	}

	private static BlockState beam(Block block, Direction.Axis axis) {
		return block.defaultBlockState().setValue(ConcreteRailBeamBlock.AXIS, axis);
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}
}
