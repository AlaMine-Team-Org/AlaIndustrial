package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.IrradiatedSoilBlock;
import dev.alaindustrial.block.ReactorDoorBlock;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A whole reactor room that actually forms and runs (MOD-470), next to the loose sample row above.
 *
 * <p>The row shows the parts; this shows the machine. Before it existed the only way to see a
 * working room was to build a 7x7x7 shell by hand every time the stand was rebuilt — which is
 * exactly the kind of chore the demo command exists to remove, and it made the reactor the one
 * shipped multiblock the stand could not demonstrate.
 *
 * <p><b>Shell 7x7x7, interior 5x5x5</b> (MOD-659; it was the 5x5x5 / 3x3x3 minimum {@code RoomScan}
 * accepts). The stand has the room now, and a room a tester can walk into and put more fuel
 * assemblies in is worth more than the smallest one that forms. Local coordinates below are (lx, ly, lz) from the room's north-west floor
 * corner; the camera looks south, so the north wall is the face the player sees: controller, glass
 * and the airlock all live in it.
 *
 * <p>Three placements are less obvious than they look:
 * <ul>
 * <li>the <b>controller faces north</b> (outward). {@code RoomScan} demands that the cell behind its
 * face be interior, not shell — a controller facing into the wall reports CONTROLLER_NOT_IN_WALL;</li>
 * <li>the room has <b>no redstone signal</b>, on purpose. It used to have a redstone block inside,
 * against the controller's back, and ran on half a column of water — harmless while the shell shed a
 * lone rack's heat by itself. Since MOD-623 the shell sheds nothing while the rods work, so that stand
 * would boil its water dry, climb to the top and blow itself up some minutes after every
 * {@code /ala demo}. It stands sealed, fuelled and scrammed, and a reactor lever on the controller's
 * back, inside, starts it. Started, it holds: the room is plumbed (MOD-660) — a pump outside feeds
 * the east inlet through ordinary pipe, reinforced fluid pipe inside carries the water to the column,
 * reinforced STEAM pipe carries the steam from the column's top to the west inlet (MOD-662 — fluid
 * pipes no longer take steam), and an ordinary steam pipe outside feeds a nozzle that vents it upward.
 * Only the reinforced grades stand inside, because a working room melts every ordinary pipe within
 * its shell. The stand still does not start itself: a reactor running unattended in every world the
 * command is used in is a hazard to a tester who did not ask for one;</li>
 * <li>the <b>button needs its own post</b>. A button must hang on a solid block, and no shell cell
 * next to the doorway is available without punching a hole in the room, so a single casing block
 * outside carries it. Its cell is adjacent to the door's lower half, which is what
 * {@code ReactorDoorBlock.neighborChanged} reads.</li>
 * </ul>
 *
 * <p>Domain (coding standard, section 1): Reactor.
 */
final class ReactorRoomZone implements DemoZone {
	private static final Block FLOOR = StandLayout.FLOOR;

	/** Camera of {@code /ala demo tp reactor}. */
	static final DemoStand.TpPoint REACTOR_CAMERA =
			new DemoStand.TpPoint("reactor", 14.0, 9.0, 24.0, 0.0f, 40.0f, false);

	// The room stands at x 4..10, z 30..36 (its north-west floor corner): west of the cable rows,
	// which own x>=32 from z=28 to z=40, and clear of the reactor row of loose parts at z=42. Outside
	// the 7x7 footprint stand only its exhaust (pipe and nozzle at x=3, z=33), its water feed (x 11..14,
	// z 33..36, in the walkway to the greenhouse) and its control post (z=29).
	private static final int BX = 4;
	private static final int BY = 1;
	private static final int BZ = 30;
	private static final int EDGE = 7;
	// The middle of a wall, floor or roof: where the crossings and the core sit.
	private static final int MID = EDGE / 2;
	// Coolant loop (MOD-660): the east face of the shell, and the middle row the whole loop runs along.
	private static final int EAST = BX + EDGE;
	private static final int ROW = BZ + MID;

	@Override
	public void build(StandWriter w) {
		shellAndFittings(w);
		controlPostAndCore(w);
		waterFeed(w);
		steamExhaust(w);
		shieldingChestAndScars(w);
	}

	/** The 7x7x7 casing shell, then the windows, crossings, controller and airlock punched into it. */
	private static void shellAndFittings(StandWriter w) {
		BlockPos origin = w.origin();
		// Shell: every perimeter cell of the 7x7x7 box is casing; the interior is left as air.
		for (int lx = 0; lx < EDGE; lx++) {
			for (int ly = 0; ly < EDGE; ly++) {
				for (int lz = 0; lz < EDGE; lz++) {
					boolean perimeter = lx == 0 || lx == EDGE - 1 || ly == 0 || ly == EDGE - 1
							|| lz == 0 || lz == EDGE - 1;
					if (perimeter) {
						w.set(BX + lx, BY + ly, BZ + lz, ModContent.REACTOR_CASING.get());
					}
				}
			}
		}

		// Windows in the north wall — seven cells, far under the 30 % glass cap the scan enforces.
		// From here to the door, every write is a `refit`: it is a fitting punched into the casing
		// shell laid above, which is a replacement the author means (MOD-597).
		w.refit(BX + 1, BY + 2, BZ, ModContent.REACTOR_GLASS.get());
		w.refit(BX + 2, BY + 2, BZ, ModContent.REACTOR_GLASS.get());
		for (int wx = 1; wx <= 5; wx++) {
			w.refit(BX + wx, BY + 3, BZ, ModContent.REACTOR_GLASS.get());
		}

		// Crossings, all on the room's middle line: water comes in through the east inlet, steam leaves
		// through the west one a level up (an inlet holds one fluid at a time, so the two lines need two),
		// the power outlet sits under the steam inlet, and the lamp is in the roof.
		w.refit(BX + EDGE - 1, BY + 1, BZ + MID, ModContent.REACTOR_PORT.get());
		w.refit(BX, BY + 2, BZ + MID, ModContent.REACTOR_PORT.get());
		w.refit(BX, BY + 1, BZ + MID, ModContent.REACTOR_OUTLET.get());
		w.refit(BX + MID, BY + EDGE - 1, BZ + MID, ModContent.REACTOR_LAMP.get());

		// Controller in the north wall, front outward, left of the middle; the airlock stands as far right.
		w.refit(origin.offset(BX + 2, BY + 1, BZ),
				ModContent.REACTOR_CONTROLLER.get().defaultBlockState()
						.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));

		// Airlock, both halves by hand: setPlacedBy does not run for a programmatic setBlock.
		BlockState door = ModContent.REACTOR_DOOR.get().defaultBlockState()
				.setValue(ReactorDoorBlock.FACING, Direction.SOUTH);
		w.refit(origin.offset(BX + 4, BY + 1, BZ), door);
		w.refit(origin.offset(BX + 4, BY + 2, BZ),
				door.setValue(ReactorDoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/** The control post and button outside the door, the fuel core and the scram lever inside. */
	private static void controlPostAndCore(StandWriter w) {
		BlockPos origin = w.origin();
		// Control post outside the door: one casing block, and the button on its west face.
		w.set(BX + 5, BY + 1, BZ - 1, ModContent.REACTOR_CASING.get());
		w.place(origin.offset(BX + 4, BY + 1, BZ - 1),
				ModContent.REACTOR_BUTTON.get().defaultBlockState()
						.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
						.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));

		// Interior: the core, left without a signal — see the method note on why the stand does not run it.
		w.place(origin.offset(BX + MID, BY + 1, BZ + MID),
				ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState()
						.setValue(FuelRodAssemblyBlock.RODS, FuelRodAssemblyBlock.MAX_RODS));
		w.loadFuelRods(BX + MID, BY + 1, BZ + MID);
		// The scram switch, inside on the controller's back — the one held signal that survives a meltdown
		// (MOD-514). Placed OFF: flicking it is the whole of starting the reactor.
		w.place(origin.offset(BX + 2, BY + 1, BZ + 1),
				ModContent.REACTOR_LEVER.get().defaultBlockState()
						.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
						.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
	}

	/** The coolant loop's water side: a pump and walled pool outside, reinforced pipe inside. */
	private static void waterFeed(StandWriter w) {
		// ── Coolant loop (MOD-660): water in low on the east, steam out high on the west. ──
		// Every fitting that meets a vessel is wrenched on purpose, so the collars show which way each
		// line runs. The inlets need it — an inlet is a two-way crossing and a plain face against one moves
		// nothing — while the column and the nozzle are one-way and would be read right without it.

		// Water feed, outside: a pump drinking from a raised cistern, fed by a creative source beside it,
		// and ordinary pipe up to the east inlet — outside the shell an ordinary pipe is safe. The pump
		// drinks from the block IN FRONT of it (FACING is its only intake), so it faces SOUTH into a 2x2
		// pool of sources: it removes the one it drinks and the two beside it refill it. It pushes into
		// the pipe at its west face by itself.
		// The pool is walled: flowing fluid washes the mod's pipes away (MOD-661, both lines), and a
		// source beside the pipe at the pump's west face would sweep it off the stand within a tick.
		w.placeFacing(EAST + 1, BY, ROW, ModContent.PUMP.get(), Direction.SOUTH);
		w.chargeBuffer(EAST + 1, BY, ROW);
		w.set(EAST + 2, BY, ROW, ModContent.CREATIVE_ENERGY_SOURCE.get());
		for (int cz = ROW + 1; cz <= ROW + 2; cz++) {
			w.set(EAST, BY, cz, FLOOR);
			w.set(EAST + 1, BY, cz, Blocks.WATER);
			w.set(EAST + 2, BY, cz, Blocks.WATER);
			w.set(EAST + 3, BY, cz, FLOOR);
		}
		w.set(EAST + 1, BY, ROW + 3, FLOOR);
		w.set(EAST + 2, BY, ROW + 3, FLOOR);
		w.set(EAST, BY, ROW, ModContent.FLUID_PIPE.get());
		w.set(EAST, BY + 1, ROW, ModContent.FLUID_PIPE.get());
		w.pipeFace(EAST, BY + 1, ROW, Direction.WEST, PipeFaceMode.INSERT);

		// Water feed, inside: reinforced pipe from the inlet to the column's east side. Any face of a
		// column but the top takes water.
		w.set(EAST - 2, BY + 1, ROW, ModContent.REINFORCED_FLUID_PIPE.get());
		w.pipeFace(EAST - 2, BY + 1, ROW, Direction.EAST, PipeFaceMode.EXTRACT);
		w.set(EAST - 3, BY + 1, ROW, ModContent.REINFORCED_FLUID_PIPE.get());
		w.pipeFace(EAST - 3, BY + 1, ROW, Direction.WEST, PipeFaceMode.INSERT);
	}

	/** The coolant loop's steam side: reinforced pipe inside, ordinary pipe and the nozzle outside. */
	private static void steamExhaust(StandWriter w) {
		BlockPos origin = w.origin();
		// Steam exhaust, inside: steam leaves a column through its TOP face only, so the line starts
		// directly above the rack and runs west, a level above the water line, to the west inlet. It is
		// the steam family (MOD-662): a fluid pipe refuses steam and does not even reach for a column's top.
		w.set(BX + MID, BY + 2, ROW, ModContent.REINFORCED_STEAM_PIPE.get());
		w.pipeFace(BX + MID, BY + 2, ROW, Direction.DOWN, PipeFaceMode.EXTRACT);
		w.set(BX + 2, BY + 2, ROW, ModContent.REINFORCED_STEAM_PIPE.get());
		w.set(BX + 1, BY + 2, ROW, ModContent.REINFORCED_STEAM_PIPE.get());
		w.pipeFace(BX + 1, BY + 2, ROW, Direction.WEST, PipeFaceMode.INSERT);

		// Steam exhaust, outside: one ordinary steam pipe out of the inlet and the nozzle on top of it, mouth
		// up into open air. A nozzle facing a block vents nothing, the columns stop boiling and the reactor
		// overheats with its water still in it — the "exhaust blocked" state this loop exists to avoid.
		w.set(BX - 1, BY + 2, ROW, ModContent.STEAM_PIPE.get());
		w.pipeFace(BX - 1, BY + 2, ROW, Direction.EAST, PipeFaceMode.EXTRACT);
		w.place(origin.offset(BX - 1, BY + 3, ROW),
				ModContent.STEAM_NOZZLE.get().defaultBlockState()
						.setValue(SteamNozzleBlock.FACING, Direction.UP));
		w.pipeFace(BX - 1, BY + 2, ROW, Direction.UP, PipeFaceMode.INSERT);
	}

	/** The shielding chest with its uranium and the four decay stages of irradiated soil. */
	private static void shieldingChestAndScars(StandWriter w) {
		BlockPos origin = w.origin();
		// MOD-474 — the shielding chest, stocked with the fuel it is there to make safe, parked outside
		// the shell. Its place in the story is exactly here: the only spot on the stand where refined
		// uranium can sit in the open without dosing whoever walks past it.
		//
		// x=26: past the crystal greenhouse (x 16..22), whose shell must stay sealed (MOD-597 — a chest
		// set into its perimeter once punched a hole in it), and west of the cable rows that start at 32.
		w.set(26, BY, BZ + 2, ModContent.SHIELDING_CHEST.get());
		w.fillSlot(26, BY, BZ + 2, 0, new ItemStack(ModContent.REFINED_URANIUM.get(), 64));
		w.fillSlot(26, BY, BZ + 2, 1, new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		// MOD-471 — the scar an accident leaves. Shown as the four decay stages side by side, because
		// the whole point of the intensity is that a player can read how clean a patch is at a glance,
		// and a single sample would show them one colour with nothing to compare it against. Runs south
		// from the chest down the same free column, a cell apart.
		for (int stage = 0; stage <= IrradiatedSoilBlock.MAX_INTENSITY; stage++) {
			w.place(origin.offset(26, BY, BZ + 4 + 2 * stage),
					ModContent.IRRADIATED_SOIL.get().defaultBlockState()
							.setValue(IrradiatedSoilBlock.INTENSITY, stage));
		}
	}
}
