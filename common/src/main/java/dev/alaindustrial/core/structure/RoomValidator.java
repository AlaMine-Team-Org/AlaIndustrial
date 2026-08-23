package dev.alaindustrial.core.structure;

import dev.alaindustrial.block.ReactorControllerBlock;
import dev.alaindustrial.block.ReactorDoorBlock;
import dev.alaindustrial.block.ReactorLampBlock;
import dev.alaindustrial.block.ReactorShellBlock;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.Nullable;

/**
 * The world-facing half of the reactor-room check (MOD-468, stage 1): it answers "what kind of shell
 * block is this?" and hands the geometry to {@link RoomScan}, which knows nothing about Minecraft.
 *
 * <p>The split exists so the shape logic stays L1-testable. Everything that needs a {@code Level} —
 * block identity, the controller's facing — lives here; everything that is arithmetic lives there.
 *
 * <p><b>Reads are chunk-safe by omission.</b> The scan touches at most a 14×14×14 box around the
 * controller, which is at most one chunk away in each direction, and every read goes through
 * {@link BlockGetter#getBlockState}. It is called from the controller's server tick, never from a
 * chunk-load callback, so it cannot force-load a neighbour the way a naive world scan can.
 */
public final class RoomValidator {

	private RoomValidator() {
	}

	/**
	 * Classifies a block for the scan. Seven blocks may stand in a shell — casing, glass, door, port,
	 * controller, plus the lamp and the outlet (both counted as casing) — and anything else is
	 * {@link RoomScan.ShellKind#OTHER}: legal inside the room, never part of its shell.
	 */
	public static RoomScan.ShellKind kindOf(BlockState state) {
		if (state.is(ModContent.REACTOR_CASING.get())) {
			return RoomScan.ShellKind.CASING;
		}
		// The lamp is a wall that happens to glow: solid, opaque, and counted as casing rather than as
		// its own kind — it must NOT count towards the glass share, or lighting a room would eat the
		// window budget. Without this line a lamp set into a wall reads as a hole and breaks the shell.
		if (state.is(ModContent.REACTOR_LAMP.get())) {
			return RoomScan.ShellKind.CASING;
		}
		if (state.is(ModContent.REACTOR_GLASS.get())) {
			return RoomScan.ShellKind.GLASS;
		}
		if (state.is(ModContent.REACTOR_DOOR.get())) {
			return RoomScan.ShellKind.DOOR;
		}
		if (state.is(ModContent.REACTOR_OUTLET.get())) {
			return RoomScan.ShellKind.CASING;
		}
		if (state.is(ModContent.REACTOR_PORT.get())) {
			return RoomScan.ShellKind.PORT;
		}
		if (state.is(ModContent.REACTOR_CONTROLLER.get())) {
			return RoomScan.ShellKind.CONTROLLER;
		}
		return RoomScan.ShellKind.OTHER;
	}

	/**
	 * Scans the room belonging to a controller at {@code pos} whose screen faces {@code facing}.
	 *
	 * @param facing the controller's {@code FACING} — the face carrying the screen, which looks
	 *               <em>outwards</em>, so the interior lies the other way
	 */
	public static RoomScan.Result scan(BlockGetter level, BlockPos pos, Direction facing,
			int minInner, int maxInner, int maxGlassPercent) {
		Direction inward = facing.getOpposite();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		RoomScan.ShellProbe probe = (x, y, z) -> kindOf(level.getBlockState(cursor.set(x, y, z)));
		return RoomScan.scan(probe, pos.getX(), pos.getY(), pos.getZ(),
				inward.getStepX(), inward.getStepY(), inward.getStepZ(), minInner, maxInner,
				maxGlassPercent);
	}

	/**
	 * Paints the {@code formed}/{@code edge} flags across the shell of an explicit box, and runs the
	 * room's lighting.
	 *
	 * <p><b>The box is passed in, not read from the scan.</b> A failed scan measures no box, so a
	 * sweep driven by the scan result could only ever turn the flag ON — which is exactly the bug the
	 * first playtest found: punch a hole in a finished room and it kept looking finished. The
	 * controller therefore remembers the box it last sealed and hands it back here to be cleared.
	 *
	 * <p><b>Light lives inside the room, not in the lamp.</b> Block light in Minecraft radiates in
	 * every direction, so a glowing lamp set in a wall lights the countryside behind it just as much as
	 * the room — the second thing the playtest caught. Instead the lamp block itself is dark and this
	 * sweep puts a vanilla {@code minecraft:light} (an invisible, walk-through light source) in the
	 * interior cell facing each lamp, and takes it away again when the room opens. The glow the player
	 * sees on the lamp face is its texture; the light in the room is that block.
	 *
	 * <p>It writes only what differs — the controller re-scans on a timer, and a needless
	 * {@code setBlock} would re-trigger neighbour updates on every sweep. Flag 2 = {@code UPDATE_CLIENTS}
	 * alone: these properties are cosmetic, so the client must hear about them but no neighbour needs
	 * waking.
	 *
	 * @return how many cells actually changed
	 */
	public static int applyFormed(Level level, int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ, boolean formed) {
		if (maxX < minX || maxY < minY || maxZ < minZ) {
			return 0; // no box to paint
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int changed = 0;
		for (int y = minY - 1; y <= maxY + 1; y++) {
			for (int z = minZ - 1; z <= maxZ + 1; z++) {
				for (int x = minX - 1; x <= maxX + 1; x++) {
					int outside = 0;
					if (x < minX || x > maxX) {
						outside++;
					}
					if (y < minY || y > maxY) {
						outside++;
					}
					if (z < minZ || z > maxZ) {
						outside++;
					}
					if (outside == 0) {
						continue; // interior — the player's business
					}
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					BooleanProperty flag = formedProperty(state);
					if (flag == null) {
						continue;
					}
					BlockState wanted = state.setValue(flag, formed);
					// An edge or corner of the box keeps its bezel even when sealed: that is what draws
					// the outline of the finished structure instead of leaving one featureless slab.
					if (wanted.hasProperty(ReactorShellBlock.EDGE)) {
						wanted = wanted.setValue(ReactorShellBlock.EDGE, formed && outside > 1);
					}
					if (wanted != state) {
						level.setBlock(cursor.immutable(), wanted, 2);
						changed++;
					}
					if (state.getBlock() instanceof ReactorLampBlock) {
						updateLampLight(level, cursor.immutable(), minX, minY, minZ, maxX, maxY, maxZ, formed);
					}
				}
			}
		}
		return changed;
	}

	/**
	 * Puts an invisible light source in the interior cell a lamp faces, or takes it away.
	 *
	 * <p>Only ever replaces air with light, or light with air — anything the player built there is left
	 * alone, which is what keeps a lamp from eating a machine someone parked against the wall.
	 */
	private static void updateLampLight(Level level, BlockPos lamp, int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ, boolean formed) {
		BlockPos inner = interiorNeighbour(lamp, minX, minY, minZ, maxX, maxY, maxZ);
		if (inner == null) {
			return;
		}
		BlockState there = level.getBlockState(inner);
		if (formed) {
			if (there.isAir()) {
				level.setBlock(inner, Blocks.LIGHT.defaultBlockState()
						.setValue(LightBlock.LEVEL, ReactorLampBlock.LIT_LEVEL), 2);
			}
		} else if (there.is(Blocks.LIGHT)) {
			level.setBlock(inner, Blocks.AIR.defaultBlockState(), 2);
		}
	}

	/** The one neighbour of a wall cell that lies inside the box, or {@code null} for an edge cell. */
	@Nullable
	private static BlockPos interiorNeighbour(BlockPos pos, int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ) {
		for (Direction dir : Direction.values()) {
			int nx = pos.getX() + dir.getStepX();
			int ny = pos.getY() + dir.getStepY();
			int nz = pos.getZ() + dir.getStepZ();
			if (nx >= minX && nx <= maxX && ny >= minY && ny <= maxY && nz >= minZ && nz <= maxZ) {
				return new BlockPos(nx, ny, nz);
			}
		}
		return null;
	}

	/**
	 * Whether this block is part of a shell that currently reads as assembled.
	 *
	 * <p>The one place that answers the question, so that a new shell block cannot be added to the
	 * painter and forgotten by everything else that has to recognise one. The reactor's drone asks it to
	 * decide whether the containment stands between the listener and a fuel rack (MOD-472).
	 */
	public static boolean isFormedShell(BlockState state) {
		BooleanProperty flag = formedProperty(state);
		return flag != null && state.getValue(flag);
	}

	@Nullable
	private static BooleanProperty formedProperty(BlockState state) {
		if (state.getBlock() instanceof ReactorShellBlock) {
			return ReactorShellBlock.FORMED;
		}
		if (state.getBlock() instanceof ReactorDoorBlock) {
			return ReactorDoorBlock.FORMED;
		}
		if (state.getBlock() instanceof ReactorControllerBlock) {
			return ReactorControllerBlock.FORMED;
		}
		return null;
	}
}
