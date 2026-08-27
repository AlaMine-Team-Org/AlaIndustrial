package dev.alaindustrial.core.structure;

import dev.alaindustrial.block.CrystalFarmControllerBlock;
import dev.alaindustrial.block.CrystalFarmShellBlock;
import dev.alaindustrial.block.CrystalSeedbedBlock;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.Nullable;

/**
 * The world-facing half of the crystal-farm room check (MOD-505) — the greenhouse's answer to
 * "what kind of shell block is this?", handed to {@link RoomScan}, which knows nothing about
 * Minecraft.
 *
 * <p><b>Why this is not {@link RoomValidator}.</b> Two reasons, and the second is the bigger one.
 * The world-facing half cannot be shared because {@code RoomValidator} names the reactor's blocks
 * outright ({@code REACTOR_CASING}, {@code instanceof ReactorShellBlock}). And the geometry is not
 * shared either: the reactor uses {@link RoomScan}, which finds a BOX, while a greenhouse uses
 * {@link RoomFill}, which floods whatever volume the player enclosed. That difference is the
 * feature — playtest four built a stepped pyramid and expected it to count, and under a rectangular
 * scanner every dome, lean-to and L-shaped wing reads as a breach.
 *
 * <p><b>Glass is the point here, not a concession.</b> The reactor caps its windows at 30 % because
 * a containment wall of glass is a contradiction. A greenhouse is the opposite: the player builds it
 * to look at what is growing inside, so there is no glass cap at all. Any glass counts — vanilla,
 * stained, or a modded pane joining through {@code #c:glass_blocks} — so a coloured greenhouse is
 * simply a greenhouse built from coloured glass, with no code of its own.
 */
public final class CrystalFarmRoom {

	private CrystalFarmRoom() {
	}

	/**
	 * Classifies a block for the scan. Three things may stand in a greenhouse shell — the deck, any
	 * glass, and any door — plus the controller itself. Everything else is
	 * {@link RoomScan.ShellKind#OTHER}: legal inside the room (that is where the seedbeds and the
	 * water go), never part of its shell.
	 */
	public static RoomScan.ShellKind kindOf(BlockState state) {
		if (state.is(ModContent.CRYSTAL_FARM_FLOOR.get())) {
			return RoomScan.ShellKind.CASING;
		}
		if (state.is(ModContent.CRYSTAL_FARM_CONTROLLER.get())) {
			return RoomScan.ShellKind.CONTROLLER;
		}
		// Tags rather than block identities: the player walls the greenhouse with glass they already
		// have and walks in through an ordinary door. Checked after the farm's own blocks so nothing
		// falls through to a tag by accident.
		if (state.is(ModTags.Blocks.CRYSTAL_FARM_GLASS)) {
			return RoomScan.ShellKind.GLASS;
		}
		if (state.is(ModTags.Blocks.CRYSTAL_FARM_DOOR)) {
			return RoomScan.ShellKind.DOOR;
		}
		return RoomScan.ShellKind.OTHER;
	}

	/**
	 * Floods the room belonging to a controller at {@code pos} whose panel faces {@code facing}.
	 *
	 * @param facing the controller's {@code FACING} — the face carrying the panel, which looks
	 *               <em>outwards</em>, so the interior lies the other way
	 */
	public static RoomFill.Result scan(BlockGetter level, BlockPos pos, Direction facing,
			int minCells, int maxCells, int maxSpan) {
		Direction inward = facing.getOpposite();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		RoomScan.ShellProbe probe = (x, y, z) -> kindOf(level.getBlockState(cursor.set(x, y, z)));
		return RoomFill.fill(probe, pos.getX(), pos.getY(), pos.getZ(),
				inward.getStepX(), inward.getStepY(), inward.getStepZ(), minCells, maxCells, maxSpan);
	}

	/**
	 * Paints the sealed look onto the exact cells the fill found.
	 *
	 * <p><b>This is the mechanic, not decoration.</b> The scan is invisible, so until the shell
	 * changes the player has no way to see that a greenhouse assembled — playtest one reported
	 * exactly that as "it did not become a multiblock", with the room in fact sealed and reporting
	 * its size correctly.
	 *
	 * <p>Writes only what differs, with flag 2 ({@code UPDATE_CLIENTS} alone): the properties are
	 * cosmetic, so the client must hear about them but no neighbour needs waking.
	 *
	 * @return how many cells actually changed
	 */
	public static int applyFormed(Level level, int[] shell, boolean[] isEdge) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int changed = 0;
		for (int i = 0; i < shell.length; i += 3) {
			cursor.set(shell[i], shell[i + 1], shell[i + 2]);
			BlockState state = level.getBlockState(cursor);
			BooleanProperty flag = formedProperty(state);
			if (flag == null) {
				continue;
			}
			if (state.getValue(flag)) {
				// Already claimed, and left alone on purpose. Two greenhouses can share one wall, and a
				// cell that faces one interior but only touches the other diagonally is a wall to one
				// room and framing to the other. Both would keep overwriting it — an audit found the
				// shared bezel flickering about once a second, forever, dirtying the chunk each time.
				// First room to seal it wins, until something clears it and the next scan re-decides.
				continue;
			}
			BlockState wanted = state.setValue(flag, true);
			// Framing keeps its bezel even when sealed: that is what draws the outline of the finished
			// structure instead of leaving one featureless surface.
			if (wanted.hasProperty(CrystalFarmShellBlock.EDGE)) {
				wanted = wanted.setValue(CrystalFarmShellBlock.EDGE, isEdge[i / 3]);
			}
			if (wanted != state) {
				level.setBlock(cursor.immutable(), wanted, 2);
				changed++;
			}
		}
		return changed;
	}

	/**
	 * Hunts for the hole in a greenhouse that used to be sealed.
	 *
	 * <p><b>Why this exists at all.</b> A flood fill cannot say where a room leaks — it walks out
	 * through the gap and keeps going until it hits its leash, two dozen blocks away over open
	 * ground. Playtest five reported exactly that: the fault particles appeared "about thirty blocks
	 * off, nowhere near the greenhouse", which is worse than showing nothing, because it sends the
	 * player to search where there is nothing to find.
	 *
	 * <p>The fix is to stop asking the fill. A room that sealed a moment ago left behind the exact
	 * list of cells its shell was made of, and the hole is simply the one that is no longer a shell
	 * block. That is a direct answer rather than an inference.
	 *
	 * <p><b>The cell list, not its bounding box.</b> An earlier attempt walked the box instead, and it
	 * would have been wrong for every shape the fill exists to allow: the corners of a dome's bounding
	 * box are open sky, so an intact dome would have reported a "hole" hanging in the air beside it.
	 *
	 * @param shell the shell cells of the last sealed scan, as flat {@code x, y, z} triples
	 * @return the first cell that is no longer a shell block, or {@code null} if all of them still are
	 */
	@Nullable
	public static BlockPos findBreach(BlockGetter level, int[] shell) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int i = 0; i < shell.length; i += 3) {
			cursor.set(shell[i], shell[i + 1], shell[i + 2]);
			if (!kindOf(level.getBlockState(cursor)).isShell()) {
				return cursor.immutable();
			}
		}
		return null;
	}

	/**
	 * Strips the sealed look from every farm block in a box.
	 *
	 * <p><b>A box here, deliberately, where forming uses the exact cells.</b> A room that just came
	 * apart has no cells to hand back — the fill measures nothing when it fails — so the controller
	 * remembers the bounding box of what it last sealed and clears that. Sweeping a superset is
	 * safe: a farm block inside the box that belongs to a NEIGHBOURING greenhouse gets its flag back
	 * on that room's next scan two seconds later, whereas a block missed by a narrower sweep would
	 * stay looking sealed forever with nothing left that owned it.
	 *
	 * @return how many cells actually changed
	 */
	public static int clearFormed(Level level, int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ) {
		if (maxX < minX || maxY < minY || maxZ < minZ) {
			return 0; // nothing was ever sealed
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int changed = 0;
		for (int y = minY - 1; y <= maxY + 1; y++) {
			for (int z = minZ - 1; z <= maxZ + 1; z++) {
				for (int x = minX - 1; x <= maxX + 1; x++) {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					// A seedbed inside the footprint stops being tended along with the room it stood in,
					// so it can warn the next player who feeds it.
					if (state.hasProperty(CrystalSeedbedBlock.TENDED)
							&& state.getValue(CrystalSeedbedBlock.TENDED)) {
						level.setBlock(cursor.immutable(),
								state.setValue(CrystalSeedbedBlock.TENDED, false), 2);
						changed++;
						continue;
					}
					BooleanProperty flag = formedProperty(state);
					if (flag == null || !state.getValue(flag)) {
						continue;
					}
					BlockState wanted = state.setValue(flag, false);
					if (wanted.hasProperty(CrystalFarmShellBlock.EDGE)) {
						wanted = wanted.setValue(CrystalFarmShellBlock.EDGE, false);
					}
					level.setBlock(cursor.immutable(), wanted, 2);
					changed++;
				}
			}
		}
		return changed;
	}

	/** The {@code formed} flag of a greenhouse shell block, or {@code null} for anything without one. */
	@Nullable
	private static BooleanProperty formedProperty(BlockState state) {
		if (state.getBlock() instanceof CrystalFarmShellBlock) {
			return CrystalFarmShellBlock.FORMED;
		}
		if (state.getBlock() instanceof CrystalFarmControllerBlock) {
			return CrystalFarmControllerBlock.FORMED;
		}
		return null;
	}
}
