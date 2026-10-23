package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.block.entity.machine.DroneFlight;
import dev.alaindustrial.core.drone.DroneFlightPath;
import dev.alaindustrial.mixin.FenceGateBlockAccessor;
import dev.alaindustrial.mixin.TrapDoorBlockAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The doors, trapdoors and fence gates a builder drone passes on its route (MOD-872; epic MOD-824, decision R116): it
 * opens a shut one in front of it as a player would — the vanilla sound and game event — and shuts it behind it, if
 * it was the one to open it. A doorway that stood open stays open.
 *
 * <p><b>Which ones.</b> A doorway a hand opens: a door or a trapdoor whose block set opens by hand, and every fence
 * gate. An iron door or trapdoor (redstone only) is never touched: shut, it is a closed cell to the route
 * ({@link #passable}), so the drone goes round it or its cell waits «no way there».
 *
 * <p><b>When.</b> Every tick in the air the drone's place along its route ({@link DroneFlight#travel}) is matched
 * against the stretch of the route each doorway cell covers: within {@link #LEAD} blocks before it to {@link #TRAIL}
 * blocks after it the doorway is held open — so a door a player shuts while the drone is inside is opened again from
 * inside on the way out — and once the drone is past it is shut. A drone that docks shuts whatever it still holds
 * open. The doorways it opened are saved, so a reload in flight still shuts them.
 */
public final class DroneDoorways {

	/** Blocks of route before a doorway at which the drone opens it. */
	public static final float LEAD = 1.5F;
	/** Blocks of route after a doorway at which the drone shuts it behind it. */
	public static final float TRAIL = 1.0F;
	/** Step along the route at which its cells are read, in blocks. */
	private static final float STEP = 0.25F;
	private static final int DOOR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE;

	/** Doorways this drone opened and has not shut yet. Saved. */
	private final List<BlockPos> opened = new ArrayList<>();
	/** The route the stretches below are for; recomputed when the leg's route changes. Not saved. */
	private int[] forPath = new int[0];
	/** Every cell the route goes through, with the stretch of route {from, to} in blocks it covers. Not saved. */
	private final Map<BlockPos, float[]> stretches = new LinkedHashMap<>();
	private float length;

	/** Whether a cell holding {@code state} is a doorway, and the drone may fly through it: open, or opened by hand. */
	public static boolean passable(BlockState state) {
		return doorway(state) && (open(state) || byHand(state));
	}

	/** Whether {@code state} is a door, a trapdoor or a fence gate. */
	public static boolean doorway(BlockState state) {
		Block block = state.getBlock();
		return (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock)
				&& state.hasProperty(BlockStateProperties.OPEN);
	}

	/** Whether a hand opens the doorway {@code state}: not an iron door or trapdoor. */
	public static boolean byHand(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof DoorBlock door) {
			return door.type().canOpenByHand();
		}
		if (block instanceof TrapDoorBlock trapdoor) {
			return ((TrapDoorBlockAccessor) trapdoor).alaindustrial$getType().canOpenByHand();
		}
		return block instanceof FenceGateBlock;
	}

	private static boolean open(BlockState state) {
		return state.getValue(BlockStateProperties.OPEN);
	}

	/** One tick of a drone in the air: opens the doorways ahead of it, shuts the ones behind it. */
	public void tick(ServerLevel level, DroneFlight flight) {
		int[] path = flight.path();
		if (!Arrays.equals(path, forPath)) {
			measure(path);
		}
		float at = flight.travel() * length;
		for (Map.Entry<BlockPos, float[]> cell : stretches.entrySet()) {
			BlockPos pos = cell.getKey();
			float[] stretch = cell.getValue();
			boolean near = at >= stretch[0] - LEAD && at <= stretch[1] + TRAIL;
			if (near) {
				BlockState state = level.getBlockState(pos);
				if (doorway(state) && !open(state) && byHand(state) && level.isLoaded(pos)) {
					swing(level, pos, state, true);
					if (!opened.contains(pos)) {
						opened.add(pos);
					}
				}
			} else if (opened.contains(pos)) {
				shut(level, pos);
				opened.remove(pos);
			}
		}
	}

	/** The drone is docked, or its nest is gone: every doorway it still holds open is shut. */
	public void shutAll(ServerLevel level) {
		for (BlockPos pos : opened) {
			shut(level, pos);
		}
		opened.clear();
		forPath = new int[0];
		stretches.clear();
	}

	/** Shuts the doorway at {@code pos} if it is still one, still open and its chunk loaded. */
	private static void shut(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (doorway(state) && open(state)) {
			swing(level, pos, state, false);
		}
	}

	/** Opens or shuts a doorway as a player's hand does: the block, the sound, the game event. */
	private static void swing(ServerLevel level, BlockPos pos, BlockState state, boolean open) {
		Block block = state.getBlock();
		if (block instanceof DoorBlock door) {
			door.setOpen(null, level, state, pos, open);
			return;
		}
		BlockState moved = state.setValue(BlockStateProperties.OPEN, open);
		SoundEvent sound;
		if (block instanceof TrapDoorBlock trapdoor) {
			level.setBlock(pos, moved, Block.UPDATE_CLIENTS);
			if (moved.hasProperty(BlockStateProperties.WATERLOGGED)
					&& moved.getValue(BlockStateProperties.WATERLOGGED)) {
				level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
			}
			var type = ((TrapDoorBlockAccessor) trapdoor).alaindustrial$getType();
			sound = open ? type.trapdoorOpen() : type.trapdoorClose();
		} else {
			level.setBlock(pos, moved, DOOR_FLAGS);
			var type = ((FenceGateBlockAccessor) block).alaindustrial$getType();
			sound = open ? type.fenceGateOpen() : type.fenceGateClose();
		}
		level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
		level.gameEvent(null, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
	}

	/** Reads the cells the route goes through — through the centres of its corner cells — and their stretches. */
	private void measure(int[] path) {
		forPath = path;
		stretches.clear();
		length = 0.0F;
		if (path.length < 6) {
			return;
		}
		float[] xyz = new float[path.length];
		for (int i = 0; i < path.length; i++) {
			xyz[i] = path[i] + 0.5F;
		}
		DroneFlightPath line = new DroneFlightPath(xyz);
		length = line.length();
		int samples = Math.max(1, (int) Math.ceil(length / STEP));
		float[] p = new float[3];
		for (int s = 0; s <= samples; s++) {
			float travel = (float) s / samples;
			line.point(travel, p);
			BlockPos pos = BlockPos.containing(p[0], p[1], p[2]);
			float d = travel * length;
			float[] stretch = stretches.computeIfAbsent(pos, k -> new float[] {d, d});
			stretch[0] = Math.min(stretch[0], d);
			stretch[1] = Math.max(stretch[1], d);
		}
	}

	public void save(ValueOutput output) {
		if (!opened.isEmpty()) {
			output.store("OpenedDoorways", BlockPos.CODEC.listOf(), List.copyOf(opened));
		}
	}

	public void load(ValueInput input) {
		opened.clear();
		opened.addAll(input.read("OpenedDoorways", BlockPos.CODEC.listOf()).orElse(List.of()));
		forPath = new int[0];
		stretches.clear();
	}
}
