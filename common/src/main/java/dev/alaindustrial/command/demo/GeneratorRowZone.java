package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.ConcentratorPart;
import dev.alaindustrial.block.ConcentratorStructure;
import dev.alaindustrial.block.MobWheelStructure;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Zone <b>generators</b> (row z=8, battery boxes behind at z=9): every generator runs live —
 * coal in the fuel generator, a lava bucket in the geothermal, open sky for the solars, and a
 * water mill sunk into the floor between two contained water cells. Each generator delivers
 * into its battery box by the cable-less direct push (the box sits on an OUT face).
 *
 * <p>The row is laid out at a pitch of six cells (MOD-659), so every generator has its own bay and a
 * free aisle on each side: coal x=4, geothermal x=10, the four solar panels x=16..34, then the water
 * mill's channel (x=44..48), the concentrator (x=54..55), the mob wheel (x=57..59, z=8..10, MOD-763), and the
 * wind row of the next method.
 *
 * <p>Domain (coding standard, section 1): EnergyGeneration.
 */
final class GeneratorRowZone implements DemoZone {
	private static final Block FLOOR = StandLayout.FLOOR;
	private static final int GEN_Z = StandLayout.GEN_Z;

	/** Camera of {@code /ala demo tp generators}. */
	static final DemoStand.TpPoint GENERATORS_CAMERA =
			new DemoStand.TpPoint("generators", 20.0, 6.0, 2.0, 0.0f, 24.0f, false);

	/** Camera of {@code /ala demo tp watermill}. */
	static final DemoStand.TpPoint WATERMILL_CAMERA =
			new DemoStand.TpPoint("watermill", 46.0, 6.0, 2.0, 0.0f, 30.0f, false);

	/** The mob wheel's drive (MOD-763): the back-west cell of the 3x3x3 at x=57..59 / z=8..10, gate to the north. */
	static final int MOB_WHEEL_X = 57;
	static final int MOB_WHEEL_Z = GEN_Z + 2;

	/** Camera of {@code /ala demo tp concentrator}. */
	static final DemoStand.TpPoint CONCENTRATOR_CAMERA =
			new DemoStand.TpPoint("concentrator", 54.0, 5.0, 3.0, 0.0f, 25.0f, false);

	@Override
	public void build(StandWriter w) {
		// MOD-479 — the creative source, at the head of the generator row: the instrument you reach for
		// when the generator behind it is the thing under test. One row in front of the line and two
		// cells clear of the first generator, so it touches nothing of the row it is meant to drive.
		w.set(2, 1, GEN_Z - 1, ModContent.CREATIVE_ENERGY_SOURCE.get());

		w.set(4, 1, GEN_Z, ModContent.GENERATOR.get());
		w.fillSlot(4, 1, GEN_Z, 0, new ItemStack(Items.COAL, 64));
		w.set(4, 1, GEN_Z + 1, ModContent.BATTERY_BOX.get());

		w.set(10, 1, GEN_Z, ModContent.GEOTHERMAL_GENERATOR.get());
		w.fillSlot(10, 1, GEN_Z, 0, new ItemStack(Items.LAVA_BUCKET));
		w.set(10, 1, GEN_Z + 1, ModContent.BATTERY_BOX.get());

		// Four solar panels, one bay each. Under open sky by construction: nothing on this row is taller
		// than the wind pillars at the far end, and the channel's dam (MOD-597 — it once roofed the fourth
		// panel, a panel that produces nothing and says nothing) starts east of x=44.
		int x = 16;
		for (Block solar : new Block[] {ModContent.SOLAR_PANEL.get(),
				ModContent.DAYLIGHT_SOLAR_PANEL.get(), ModContent.MOONLIT_SOLAR_PANEL.get(),
				ModContent.RADIANT_SOLAR_PANEL.get()}) {
			w.set(x, 1, GEN_Z, solar);
			w.set(x, 1, GEN_Z + 1, ModContent.BATTERY_BOX.get());
			x += 6;
		}

		// Water mill driven by a real CURRENT (MOD-188): only FLOWING water turns the wheel — a still
		// source powers nothing. The mill faces NORTH, so its wheel hangs in the whole plane one row in
		// front of it: mx-1..mx+1 by y -1..1. Two rules shape this build:
		//   MOD-355 — every one of those nine cells must be non-solid or the wheel clips through it and
		//             stalls, so the channel is dug a level deeper and walled OUTSIDE the plane;
		//   MOD-352 — the mill is driven by the four cells around the WHEEL (above, below, both sides),
		//             so three sources at y=2 fall through the plane and wet all four → a full 4 EU/t.
		// Water is canBeReplaced(), so a wet wheel plane is a clear wheel plane.
		final int mx = 46;
		final int plane = GEN_Z - 1;
		for (int dx = mx - 1; dx <= mx + 1; dx++) {
			w.set(dx, -2, plane, FLOOR); // bed, one level BELOW the plane so the plane stays clear
			for (int dy = -1; dy <= 1; dy++) {
				w.set(dx, dy, plane, Blocks.AIR);
			}
		}
		w.set(mx, -1, GEN_Z, FLOOR); // support under the mill itself (outside the wheel plane)
		// Walls that hold the water in, all outside the wheel plane: beside it (mx-2 / mx+2) and in front.
		for (int dy = -2; dy <= 2; dy++) {
			w.set(mx - 2, dy, plane, FLOOR);
			w.set(mx + 2, dy, plane, FLOOR);
			for (int dx = mx - 2; dx <= mx + 2; dx++) {
				w.set(dx, dy, plane - 1, FLOOR);
			}
		}
		// Cap the feed at y=2 (above the plane) so the sources only ever fall downward.
		for (int dx = mx - 1; dx <= mx + 1; dx++) {
			w.set(dx, 2, GEN_Z, FLOOR);
			w.set(dx, 2, plane, Blocks.WATER);
		}
		// The mill and its battery box behind it (south = the back/OUT face).
		w.set(mx, 0, GEN_Z, ModContent.WATER_MILL.get());
		w.fillSlot(mx, 0, GEN_Z, 0, new ItemStack(ModContent.WATER_MILL_WHEEL.get()));
		w.set(mx, -1, GEN_Z + 1, FLOOR);
		w.set(mx, 0, GEN_Z + 1, ModContent.BATTERY_BOX.get());

		buildConcentrator(w);
		buildMobWheel(w);
	}

	/**
	 * MOD-603: the concentrator grown out into its two-by-two-by-two form — the next bay of the
	 * generator row after the water mill, its 2x2x2 at x=54..55 / z=8..9.
	 * Built the way a player builds it — a grown panel plus seven loose sections — and then handed
	 * to the real assembler, so the stand cannot show a structure the game could not produce.
	 */
	private static void buildConcentrator(StandWriter w) {
		BlockPos structureCore = w.origin().offset(54, 1, GEN_Z);
		w.place(structureCore,
				ModContent.RADIANT_SOLAR_PANEL.get().defaultBlockState());
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part == ConcentratorPart.CORE) {
				continue;
			}
			w.place(structureCore.offset(part.worldOffset(Direction.NORTH)),
					ModContent.CONCENTRATOR_SECTION.get().defaultBlockState());
		}
		ConcentratorStructure.tryAssemble(w.level(), structureCore);
	}

	/**
	 * MOD-763: the mob wheel, assembled, in the bay after the concentrator — its gate on the north side, toward
	 * the camera. Built the way a player builds it — the fourteen parts with the thirteen free cells left as air
	 * — and then handed to the real assembler, which fills those cells with its invisible members. The free
	 * cells are claimed through the ledger FIRST: the last part placed may already set the assembly off through
	 * a neighbour update, and an air write after that would take the wheel apart again. No runner is put in: a
	 * mob on the stand is not part of a rebuild, and the drive's screen then reads "No mob inside". The battery
	 * box sits on the drive's south port, the way each generator of the row sits on its box.
	 */
	private static void buildMobWheel(StandWriter w) {
		BlockPos drive = w.origin().offset(MOB_WHEEL_X, 1, MOB_WHEEL_Z);
		Direction facing = Direction.NORTH;
		for (boolean cells : new boolean[] {true, false}) {
			for (int x = 0; x < MobWheelStructure.SIZE; x++) {
				for (int y = 0; y < MobWheelStructure.SIZE; y++) {
					for (int z = 0; z < MobWheelStructure.SIZE; z++) {
						MobWheelStructure.Slot slot = MobWheelStructure.slotAt(x, y, z);
						if ((slot == MobWheelStructure.Slot.CELL) != cells) {
							continue;
						}
						Block block = switch (slot) {
							case CONTROLLER -> ModContent.MOB_WHEEL_CONTROLLER.get();
							case FRAME -> ModContent.MOB_WHEEL_FRAME.get();
							case ROTOR -> ModContent.MOB_WHEEL_ROTOR.get();
							case GATE -> ModContent.MOB_WHEEL_GATE.get();
							case CELL -> Blocks.AIR;
						};
						w.place(MobWheelStructure.at(drive, facing, x, y, z), block.defaultBlockState());
					}
				}
			}
		}
		MobWheelStructure.tryAssemble(w.level(), drive);
		w.set(MOB_WHEEL_X, 1, MOB_WHEEL_Z + 1, ModContent.BATTERY_BOX.get());
	}
}
