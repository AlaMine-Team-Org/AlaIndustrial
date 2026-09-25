package dev.alaindustrial.command.demo;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.ConcentratorPart;
import dev.alaindustrial.block.ConcentratorStructure;
import dev.alaindustrial.block.CrystalSeedbedBlock;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.IrradiatedSoilBlock;
import dev.alaindustrial.block.ReactorDoorBlock;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.block.TrellisBlock;
import dev.alaindustrial.block.UpgradeTableBlock;
import dev.alaindustrial.block.WorkstationBlock;
import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
import dev.alaindustrial.block.entity.CanningMachineBlockEntity;
import dev.alaindustrial.block.entity.ComponentRepairBenchBlockEntity;
import dev.alaindustrial.block.entity.FermenterBlockEntity;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.GalvanicBathBlockEntity;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.LightningRodGeneratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import dev.alaindustrial.block.entity.RecyclerBlockEntity;
import dev.alaindustrial.block.entity.SprinklerBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import dev.alaindustrial.block.entity.UpgradeTableBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.block.entity.WorkstationBlockEntity;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * The MOD-058 demo stand: a generated showcase of every mod block, "alive" where possible
 * (fuelled generators, charged machines with inputs, powered cable runs). Built by
 * {@code /ala demo build}, removed by {@code /ala demo clear}, inspected via the fixed
 * {@link #TP_POINTS} camera positions of {@code /ala demo tp <zone>}.
 *
 * <p>The stand is <b>generated, not saved</b>: it is rebuilt from the live registry state on
 * demand, so it survives block renames and never rots in a binary save file. Completeness is
 * enforced by the {@code DemoStandGameTest} smoke test, which builds this same stand and asserts
 * every {@code alaindustrial} block appears inside {@link #WIDTH}×{@link #HEIGHT}×{@link #DEPTH}.
 *
 * <p>All coordinates are relative to a caller-supplied origin — the north-west corner of the
 * floor layer. The command anchors the origin at world (0, ?, 0) via {@link #findOrigin}; the
 * gametest anchors it inside its own structure envelope.
 */
public final class DemoStand {
	private DemoStand() {
	}

	/**
	 * Stand footprint (x). MOD-659 doubled it (42 → 88) together with every gap between the zones and
	 * between the independent blocks inside them: the old stand put machines two cells apart and let a
	 * tester's hand, camera and ladder of chests all fight for the same few blocks.
	 */
	public static final int WIDTH = 88;
	/**
	 * Stand footprint (z). The LAST row, {@code DEPTH - 1}, is the back of the showcase wall, so
	 * nothing may be written at {@code z >= DEPTH - 1} except that wall: the old item-pipe row sat one
	 * row behind the wall (z=26 of 27), out of the camera's sight and shut in by smooth stone.
	 * The {@code demo_stand_area} GameTest structure must be at least this deep plus its 1-block origin
	 * margin — a row past it is built but falls outside the coverage scan, which reads as "block missing
	 * from the stand" rather than as an out-of-bounds error.
	 */
	public static final int DEPTH = 51;
	/** Blocks above the floor layer that belong to the stand (wind-mill pillars are tallest). */
	public static final int HEIGHT = 9;
	/**
	 * How far above the floor {@code build} and {@code clear} sweep the sky clear (MOD-659). The stand
	 * itself is {@link #HEIGHT} tall, but the world it is built into is not empty: on ordinary terrain
	 * a tree's crown reaches thirty blocks up, and clearing only the stand's own height sliced every
	 * crown that overhung it in half, leaving branches hanging in mid-air over the exhibits. The stand
	 * takes the whole column, forty blocks of it — the tallest thing a vanilla tree grows is well
	 * under that.
	 */
	public static final int CLEAR_HEIGHT = 40;

	/**
	 * Cells the stand owns BELOW its floor (MOD-597). The stand digs: the water mill's wheel plane is
	 * a walled channel two levels deep, and every sunken fluid basin has a pan under it.
	 *
	 * <p>It was not declared anywhere until now, and that is exactly what made it a hole. Both the
	 * builder and its gametests stopped at {@code y = 0}: {@code clear} restored grass and left the
	 * channel and the pans buried under it forever, and the scans that would have noticed started at
	 * {@code y = -1} or {@code y = 0}, so the code and the test agreed on the same wrong bound. It is
	 * the same defect as MOD-584 (a column written past {@code WIDTH}), turned ninety degrees.
	 */
	public static final int DEPTH_BELOW = 2;

	/** Floor material — also the datum marker {@link #findOrigin} recognises for idempotent rebuilds. */
	private static final Block FLOOR = Blocks.SMOOTH_STONE;

	/**
	 * What fills the {@link #DEPTH_BELOW} layers under the floor — ordinary subsoil, the thing that
	 * sits under grass. Written blanket on every build and every clear, exactly like the floor above
	 * it, so what the stand digs is always dug out of a known slab rather than out of whatever the
	 * previous version of the layout happened to leave there.
	 */
	public static final Block SUBSOIL = Blocks.DIRT;

	/**
	 * Showcase wall (MOD-294): the LAST row of the stand, columns x=1..{@link #SHOWCASE_COLUMNS}. The
	 * frames hang on its front (north) face, one row in front of it — so the wall closes the stand and
	 * there is no cell behind it (MOD-659: the old item-pipe row stood there, invisible from the front).
	 */
	private static final int SHOWCASE_WALL_Z = DEPTH - 1;
	private static final int SHOWCASE_COLUMNS = WIDTH - 2;
	/** The wall's bottom row. y=1 is the first cell above the floor. */
	private static final int SHOWCASE_BASE_Y = 1;
	/**
	 * Six rows: every frame within arm's reach without craning, and the wall spans the whole stand
	 * instead of towering over a corner of it (MOD-659; it used to be nine rows of forty).
	 *
	 * <p>The wall shows EVERY item of the mod since MOD-586, block items included. Capacity is a
	 * deadline, not a preference: the wall fills from the registry, and the day the registry outgrows
	 * it the coverage gametest reddens in whatever task happened to add the item. The current item
	 * count is read from the registry, never restated here; six rows of {@code WIDTH - 2} are 492 slots.
	 */
	private static final int SHOWCASE_ROWS = 6;

	/** A named camera position for {@code /ala demo tp}, relative to the stand origin. */
	public record TpPoint(String name, double dx, double dy, double dz, float yaw, float pitch, boolean night) {
	}

	/**
	 * Camera points, one per zone plus an overview. Yaw 0 looks south (+z) — every zone is laid
	 * out with its blocks south of the camera and machine fronts (FACING north) toward it.
	 * {@code night} points additionally switch the world clock to midnight (moonlit panel).
	 *
	 * <p>The stand is 88 wide (MOD-659), wider than one screen can hold at a legible distance, so the long
	 * rows have a point per stretch of them ({@code machines}/{@code machines2}, the three showcase
	 * points) instead of one far view. Every point stands in open air above the blocks near it: none
	 * of them is inside a build, and the feet are at least three cells above anything taller than two.
	 */
	public static final List<TpPoint> TP_POINTS = List.of(
			new TpPoint("overview", 44.0, 42.0, -10.0, 0.0f, 55.0f, false),
			new TpPoint("tiers", 25.0, 9.0, -4.0, 0.0f, 28.0f, false),
			new TpPoint("bench", 68.0, 6.0, -3.0, 0.0f, 28.0f, false),
			new TpPoint("generators", 20.0, 6.0, 2.0, 0.0f, 24.0f, false),
			new TpPoint("watermill", 46.0, 6.0, 2.0, 0.0f, 30.0f, false),
			new TpPoint("concentrator", 54.0, 5.0, 3.0, 0.0f, 25.0f, false),
			new TpPoint("windmills", 71.0, 9.0, 0.0, 0.0f, 12.0f, false),
			new TpPoint("loss", 22.0, 8.0, 10.0, 0.0f, 40.0f, false),
			new TpPoint("ores", 52.0, 5.0, 10.0, 0.0f, 30.0f, false),
			new TpPoint("transport", 7.0, 6.0, 12.0, 0.0f, 40.0f, false),
			new TpPoint("fluids", 34.0, 6.0, 12.0, 0.0f, 40.0f, false),
			new TpPoint("machines", 16.0, 6.0, 14.0, 0.0f, 30.0f, false),
			new TpPoint("machines2", 44.0, 6.0, 14.0, 0.0f, 30.0f, false),
			new TpPoint("misc", 70.0, 7.0, 14.0, 0.0f, 32.0f, false),
			new TpPoint("cables", 45.0, 14.0, 21.0, 0.0f, 45.0f, false),
			new TpPoint("reactor", 14.0, 9.0, 24.0, 0.0f, 40.0f, false),
			new TpPoint("reactorrow", 17.0, 6.0, 37.0, 0.0f, 45.0f, false),
			new TpPoint("farms", 38.0, 20.0, 32.0, 0.0f, 55.0f, false),
			new TpPoint("monitor", 68.0, 7.0, 32.0, 0.0f, 40.0f, false),
			new TpPoint("itemless", 30.0, 10.0, 36.0, 0.0f, 42.0f, false),
			new TpPoint("showcase", 43.0, 5.0, 41.0, 0.0f, 8.0f, false),
			new TpPoint("showcase_west", 15.0, 5.0, 41.0, 0.0f, 8.0f, false),
			new TpPoint("showcase_east", 71.0, 5.0, 41.0, 0.0f, 8.0f, false),
			new TpPoint("night", 20.0, 6.0, 2.0, 0.0f, 24.0f, true));

	/**
	 * The stand origin used by the command: world column (0, ?, 0). The y is the topmost non-air
	 * block of that column — plain ground on a fresh world, and the stand's own floor after a
	 * build (the datum column carries nothing above the floor), so rebuilds land on the same
	 * level instead of stacking.
	 */
	public static BlockPos findOrigin(ServerLevel level) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(0, 0, 0);
		for (int y = level.getMaxY(); y > level.getMinY(); y--) {
			p.setY(y);
			if (!level.getBlockState(p).isAir()) {
				return new BlockPos(0, y, 0);
			}
		}
		return new BlockPos(0, level.getMinY(), 0);
	}

	/** Clear the stand envelope above the floor, sweep the entities that spilled, then build everything. */
	public static void buildAll(ServerLevel level, BlockPos origin) {
		clearAbove(level, origin);
		buildFloor(level, origin);
		// The sweep runs after the floor pass, not after clearAbove: the water mill sits AT floor
		// level with a wheel in its slot, and replacing its block is what spills that wheel — the
		// y>=1 machines spill during clearAbove, so this one sweep catches both waves.
		killLooseEntities(level, origin);
		// From here on every write is recorded: the floor pass above legitimately covers the whole
		// footprint, the zones below own one cell each (MOD-597, see #lastBuildProblems).
		openLedger();
		try {
			buildTierZone(level, origin);
			buildTestBench(level, origin);
			buildGeneratorRow(level, origin);
			buildWindMills(level, origin);
			buildMachines(level, origin);
			buildCableRuns(level, origin);
			buildTransportLines(level, origin);
			buildOreWall(level, origin);
			buildMisc(level, origin);
			buildLossLane(level, origin);
			buildFarms(level, origin);
			buildReactorZone(level, origin);
			buildCrystalGreenhouse(level, origin);
			buildReactorRoom(level, origin);
			buildLabPlaque(level, origin);
			buildShowcase(level, origin);
			buildItemlessRow(level, origin);
			buildMonitorWall(level, origin);
		} finally {
			closeLedger();
		}
		// Leaves whose trunks were just cleared decay over the next minutes and rain drops onto the
		// floor; the sweep above has already run, so arm the ones that follow (MOD-674).
		DemoStandDropSweeper.arm(level, origin);
	}

	/**
	 * Remove the stand: air above, entities gone, the floor layer reverts to grass — and the
	 * {@link #DEPTH_BELOW} layers under it are filled back in (MOD-597).
	 *
	 * <p>Filling them blanket, rather than only where the stand dug, for the same reason the floor is
	 * laid blanket: nothing remembers which cells a previous build carved, and a clear that restores
	 * grass over an open water channel leaves a trap under the lawn.
	 */
	public static void clear(ServerLevel level, BlockPos origin) {
		clearAbove(level, origin);
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				for (int y = -DEPTH_BELOW; y < 0; y++) {
					set(level, origin, x, y, z, SUBSOIL);
				}
				set(level, origin, x, 0, z, Blocks.GRASS_BLOCK);
			}
		}
		// After the grass pass for the same reason buildAll sweeps after its floor pass: the
		// floor-level water mill spills its wheel when its block is replaced.
		killLooseEntities(level, origin);
		DemoStandDropSweeper.arm(level, origin);
	}

	/**
	 * Air out everything above the floor layer, up to {@link #CLEAR_HEIGHT} (also removes sunken
	 * water/lava cells' contents).
	 */
	private static void clearAbove(ServerLevel level, BlockPos origin) {
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				for (int y = 1; y <= CLEAR_HEIGHT; y++) {
					if (!level.getBlockState(origin.offset(x, y, z)).isAir()) {
						set(level, origin, x, y, z, Blocks.AIR);
					}
				}
			}
		}
	}

	/**
	 * Discard every entity in the stand envelope except players. Runs after the floor/grass pass
	 * of a build or clear: by then the block changes have already unseated the showcase frames,
	 * spilled the y≥1 machine inventories and replaced the floor-level water mill (whose slot
	 * holds a wheel) — this sweep is what makes build-after-build leave nothing behind.
	 * {@code discard()} removes without drops, and a popped frame must not litter the floor with
	 * itself and its item. The box comes from the generator constants, never from "whatever this
	 * run built" — rebuilds of any size clean the same fixed volume.
	 */
	private static void killLooseEntities(ServerLevel level, BlockPos origin) {
		AABB box = AABB.encapsulatingFullBlocks(origin.offset(-1, 0, -1),
				origin.offset(WIDTH + 1, HEIGHT + 2, DEPTH + 1));
		for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) {
			entity.discard();
		}
	}

	/**
	 * The floor slab, and the subsoil under it the zones dig into (MOD-597). Both are blanket writes
	 * of the whole footprint, which is why they run BEFORE the ledger opens: the zones carve this
	 * slab, and carving is not a collision.
	 */
	private static void buildFloor(ServerLevel level, BlockPos origin) {
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				for (int y = -DEPTH_BELOW; y < 0; y++) {
					set(level, origin, x, y, z, SUBSOIL);
				}
				set(level, origin, x, 0, z, FLOOR);
			}
		}
	}

	/** Row of the generator line; the battery boxes sit one row behind it, the water mill's channel in front. */
	private static final int GEN_Z = 8;

	/**
	 * Zone <b>generators</b> (row z=8, battery boxes behind at z=9): every generator runs live —
	 * coal in the fuel generator, a lava bucket in the geothermal, open sky for the solars, and a
	 * water mill sunk into the floor between two contained water cells. Each generator delivers
	 * into its battery box by the cable-less direct push (the box sits on an OUT face).
	 *
	 * <p>The row is laid out at a pitch of six cells (MOD-659), so every generator has its own bay and a
	 * free aisle on each side: coal x=4, geothermal x=10, the four solar panels x=16..34, then the water
	 * mill's channel (x=44..48), the concentrator (x=54..55), and the wind row of the next method.
	 */
	private static void buildGeneratorRow(ServerLevel level, BlockPos origin) {
		// MOD-479 — the creative source, at the head of the generator row: the instrument you reach for
		// when the generator behind it is the thing under test. One row in front of the line and two
		// cells clear of the first generator, so it touches nothing of the row it is meant to drive.
		set(level, origin, 2, 1, GEN_Z - 1, ModContent.CREATIVE_ENERGY_SOURCE.get());

		set(level, origin, 4, 1, GEN_Z, ModContent.GENERATOR.get());
		fillSlot(level, origin, 4, 1, GEN_Z, 0, new ItemStack(Items.COAL, 64));
		set(level, origin, 4, 1, GEN_Z + 1, ModContent.BATTERY_BOX.get());

		set(level, origin, 10, 1, GEN_Z, ModContent.GEOTHERMAL_GENERATOR.get());
		fillSlot(level, origin, 10, 1, GEN_Z, 0, new ItemStack(Items.LAVA_BUCKET));
		set(level, origin, 10, 1, GEN_Z + 1, ModContent.BATTERY_BOX.get());

		// Four solar panels, one bay each. Under open sky by construction: nothing on this row is taller
		// than the wind pillars at the far end, and the channel's dam (MOD-597 — it once roofed the fourth
		// panel, a panel that produces nothing and says nothing) starts east of x=44.
		int x = 16;
		for (Block solar : new Block[] {ModContent.SOLAR_PANEL.get(),
				ModContent.DAYLIGHT_SOLAR_PANEL.get(), ModContent.MOONLIT_SOLAR_PANEL.get(),
				ModContent.RADIANT_SOLAR_PANEL.get()}) {
			set(level, origin, x, 1, GEN_Z, solar);
			set(level, origin, x, 1, GEN_Z + 1, ModContent.BATTERY_BOX.get());
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
			set(level, origin, dx, -2, plane, FLOOR); // bed, one level BELOW the plane so the plane stays clear
			for (int dy = -1; dy <= 1; dy++) {
				set(level, origin, dx, dy, plane, Blocks.AIR);
			}
		}
		set(level, origin, mx, -1, GEN_Z, FLOOR); // support under the mill itself (outside the wheel plane)
		// Walls that hold the water in, all outside the wheel plane: beside it (mx-2 / mx+2) and in front.
		for (int dy = -2; dy <= 2; dy++) {
			set(level, origin, mx - 2, dy, plane, FLOOR);
			set(level, origin, mx + 2, dy, plane, FLOOR);
			for (int dx = mx - 2; dx <= mx + 2; dx++) {
				set(level, origin, dx, dy, plane - 1, FLOOR);
			}
		}
		// Cap the feed at y=2 (above the plane) so the sources only ever fall downward.
		for (int dx = mx - 1; dx <= mx + 1; dx++) {
			set(level, origin, dx, 2, GEN_Z, FLOOR);
			set(level, origin, dx, 2, plane, Blocks.WATER);
		}
		// The mill and its battery box behind it (south = the back/OUT face).
		set(level, origin, mx, 0, GEN_Z, ModContent.WATER_MILL.get());
		fillSlot(level, origin, mx, 0, GEN_Z, 0, new ItemStack(ModContent.WATER_MILL_WHEEL.get()));
		set(level, origin, mx, -1, GEN_Z + 1, FLOOR);
		set(level, origin, mx, 0, GEN_Z + 1, ModContent.BATTERY_BOX.get());

		// MOD-603: the concentrator grown out into its two-by-two-by-two form — the next bay of the
		// generator row after the water mill, its 2x2x2 at x=54..55 / z=8..9.
		// Built the way a player builds it — a grown panel plus seven loose sections — and then handed
		// to the real assembler, so the stand cannot show a structure the game could not produce.
		BlockPos structureCore = origin.offset(54, 1, GEN_Z);
		place(level, origin, structureCore,
				ModContent.RADIANT_SOLAR_PANEL.get().defaultBlockState());
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part == ConcentratorPart.CORE) {
				continue;
			}
			place(level, origin, structureCore.offset(part.worldOffset(Direction.NORTH)),
					ModContent.CONCENTRATOR_SECTION.get().defaultBlockState());
		}
		ConcentratorStructure.tryAssemble(level, structureCore);
	}

	/**
	 * Zone <b>windmills</b>: the three wind mills on pillars, a battery box sitting directly beneath
	 * each head as a decorative plinth. The box does <b>not</b> receive the mill's EU: a wind mill
	 * emits only from its back <i>horizontal</i> face (opposite FACING, see
	 * {@code WindMillBlockEntity#energyRoleForFace}), never downward, and the box's top face is inert
	 * anyway (single-axis IO, MOD-006). A pitch of six keeps the mills well out of each other's
	 * interference radius. Their EU/t depends on build height vs sea level, so on a low superflat they
	 * are intentionally decorative (see MOD-058 task log) — the plinth box is purely scenic (MOD-103).
	 *
	 * <p>The row continues the generator row's pitch (x=62, 68, 74, then the lightning rod at 80), so the
	 * whole power band reads as one line from coal to lightning.
	 */
	private static void buildWindMills(ServerLevel level, BlockPos origin) {
		Block[] mills = {ModContent.WIND_MILL.get(),
				ModContent.HIGH_ALTITUDE_WIND_MILL.get(), ModContent.STORM_WIND_MILL.get()};
		// Each head carries a rotor of its own grade: a wind mill with an empty rotor slot produces nothing
		// at any height, so without one the three stood there as scenery whatever the world's altitude.
		Item[] rotors = {ModContent.WINDMILL_ROTOR.get(), ModContent.WINDMILL_ROTOR_REINFORCED.get(),
				ModContent.WINDMILL_ROTOR_ADVANCED.get()};
		int x = 62;
		for (int i = 0; i < mills.length; i++) {
			for (int y = 1; y <= 4; y++) {
				set(level, origin, x, y, GEN_Z, FLOOR);
			}
			set(level, origin, x, 5, GEN_Z, ModContent.BATTERY_BOX.get());
			set(level, origin, x, 6, GEN_Z, mills[i]);
			fillSlot(level, origin, x, 6, GEN_Z, WindMillBlockEntity.ROTOR_SLOT, new ItemStack(rotors[i]));
			x += 6;
		}
		// MOD-386: the lightning rod shares this weather row — same mast-on-a-pillar shape, and a
		// conductor tip pre-installed so the stand shows the configured block rather than an inert one.
		x = 80;
		for (int y = 1; y <= 4; y++) {
			set(level, origin, x, y, GEN_Z, FLOOR);
		}
		set(level, origin, x, 5, GEN_Z, ModContent.BATTERY_BOX.get());
		set(level, origin, x, 6, GEN_Z, ModContent.LIGHTNING_ROD_GENERATOR.get());
		fillSlot(level, origin, x, 6, GEN_Z, LightningRodGeneratorBlockEntity.TIP_SLOT,
				new ItemStack(ModContent.LIGHTNING_ROD_CONDUCTOR_TIP.get()));
	}

	/**
	 * Zone <b>machines</b> (rows z=20 and z=24): processing machines with full buffers and guaranteed
	 * inputs, so they are visibly working (lit + progress) the moment the stand is built.
	 *
	 * <p>Machines stand on a pitch of six cells (x=4, 10, 16, …) — a free aisle of five between any two,
	 * enough to walk round each, break it, and rebuild it without touching a neighbour (MOD-659). Blocks
	 * that only make sense as a story stay a cell apart instead: the recycler's slag and ceramic, and
	 * the workstation next to the upgrade table. Rows are the first machines row (z=20) and a second
	 * row (z=24) behind it; the misc zone shares z=20 to the east of the galvanic bath.
	 */
	private static void buildMachines(ServerLevel level, BlockPos origin) {
		placeWorkingMachine(level, origin, 4, 20, ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 64));
		placeWorkingMachine(level, origin, 10, 20, ModContent.ELECTRIC_FURNACE.get(), new ItemStack(Items.RAW_COPPER, 64));
		placeWorkingMachine(level, origin, 16, 20, ModContent.COMPRESSOR.get(),
				new ItemStack(ModContent.IRON_DUST.get(), 64));
		placeWorkingMachine(level, origin, 22, 20, ModContent.EXTRACTOR.get(), new ItemStack(Items.GRAVEL, 64));
		// Canning Machine (MOD-383): placeWorkingMachine does not fit — it fills slot 0 only, and this
		// machine needs both a food stack and a stack of empty cans before it will run at all.
		set(level, origin, 22, 1, 24, ModContent.CANNING_MACHINE.get());
		chargeBuffer(level, origin, 22, 1, 24);
		fillSlot(level, origin, 22, 1, 24, CanningMachineBlockEntity.FOOD_SLOT,
				new ItemStack(Items.COOKED_BEEF, 64));
		fillSlot(level, origin, 22, 1, 24, CanningMachineBlockEntity.CAN_SLOT,
				new ItemStack(ModContent.EMPTY_CAN.get(), 64));
		// Component Repair Bench (MOD-384): placeWorkingMachine does not fit either — its target slot
		// needs a component that is actually WORN, and a pristine rotor would leave the bench idle on the
		// stand. So the rotor is damaged by hand first, then paired with its T1 material (an iron plate).
		set(level, origin, 28, 1, 24, ModContent.COMPONENT_REPAIR_BENCH.get());
		chargeBuffer(level, origin, 28, 1, 24);
		ItemStack wornRotor = new ItemStack(ModContent.WINDMILL_ROTOR.get());
		wornRotor.setDamageValue(wornRotor.getMaxDamage() / 2);
		fillSlot(level, origin, 28, 1, 24, ComponentRepairBenchBlockEntity.TARGET_SLOT, wornRotor);
		fillSlot(level, origin, 28, 1, 24, ComponentRepairBenchBlockEntity.MATERIAL_SLOT,
				new ItemStack(ModContent.IRON_PLATE.get(), 64));
		// Sawmill (MOD-150): pre-charged + a stack of logs → visibly sawing (default PLANKS mode).
		placeWorkingMachine(level, origin, 34, 20, ModContent.SAWMILL.get(), new ItemStack(Items.OAK_LOG, 64));
		// Incubator (MOD-118): the 1x2 multiblock. Glass goes on top so the base assembles it into the
		// dome; the slots are filled by hand rather than via placeWorkingMachine because the chip picks
		// the mode and the uranium is a separate fuel slot.
		set(level, origin, 40, 1, 20, ModContent.INCUBATOR.get());
		set(level, origin, 40, 2, 20, ModContent.INCUBATOR_DOME.get());
		chargeBuffer(level, origin, 40, 1, 20);
		fillSlot(level, origin, 40, 1, 20, IncubatorBlockEntity.CHIP_SLOT,
				new ItemStack(ModContent.MUTATION_CHIP_DUPLICATE.get()));
		fillSlot(level, origin, 40, 1, 20, IncubatorBlockEntity.FUEL_SLOT,
				new ItemStack(ModContent.URANIUM_INGOT.get(), 16));
		fillSlot(level, origin, 40, 1, 20, IncubatorBlockEntity.INPUT_SLOT,
				new ItemStack(Items.DIAMOND, 64));
		// Polymerizer (MOD-019): the fluid-fed machine. placeWorkingMachine does not fit it — its slot 0
		// takes a CONTAINER, not the feedstock, and one bucket would give the stand a single run before
		// the machine went idle. The tank is stocked directly instead, so it runs for ten operations.
		set(level, origin, 46, 1, 20, ModContent.POLYMERIZER.get());
		chargeBuffer(level, origin, 46, 1, 20);
		if (level.getBlockEntity(origin.offset(46, 1, 20)) instanceof PolymerizerBlockEntity polymerizer) {
			polymerizer.fluidTank.fluid = FluidHolder.of(ModContent.OIL.get());
			polymerizer.fluidTank.amount = PolymerizerBlockEntity.TANK_CAPACITY;
			polymerizer.setChangedQuietly();
			polymerizer.wake();
		}
		// Vulcanizer (MOD-258): the electric heater occupies the block directly below the machine.
		// Both buffers are charged and both positional inputs are stocked, so the stand demonstrates a
		// running pair rather than an idle shell. The heater is placed COLD on purpose (MOD-418): the
		// pair opens at x2 with the thermometer climbing and settles at x3 once the first batch has paid
		// for the warm-up, which is the mechanic worth showing — a stand pre-heated behind the player's
		// back would show the destination and hide the ramp.
		set(level, origin, 52, 1, 20, ModContent.ELECTRIC_HEATER.get());
		chargeBuffer(level, origin, 52, 1, 20);
		set(level, origin, 52, 2, 20, ModContent.VULCANIZER.get());
		chargeBuffer(level, origin, 52, 2, 20);
		fillSlot(level, origin, 52, 2, 20, VulcanizerBlockEntity.RAW_RUBBER_SLOT,
				new ItemStack(ModContent.RAW_RUBBER.get(), 64));
		fillSlot(level, origin, 52, 2, 20, VulcanizerBlockEntity.SULFUR_SLOT,
				new ItemStack(ModContent.SULFUR_DUST.get(), 64));
		// Thermal Centrifuge (MOD-424): the same heater-underneath pair as the vulcanizer, on the second
		// machines row. Three things must be true before this machine
		// turns at all, so all three are set up rather than only the two the other stands need: the heater
		// below, a stack of uranium dust, and — the one no other machine on the stand wants — a held
		// redstone signal. A redstone BLOCK rather than a lever: the lever's default state is unpowered and
		// wall-mounted, so `set` (which places defaultBlockState and never calls setPlacedBy) would leave a
		// dead switch and an idle centrifuge. It is placed LAST of the three so its neighbour update reaches
		// an already-built machine. Like the vulcanizer's, the heater starts cold on purpose: the stand shows
		// the rotor spinning up while the thermometer climbs, which is the mechanic worth watching.
		set(level, origin, 34, 1, 24, ModContent.ELECTRIC_HEATER.get());
		chargeBuffer(level, origin, 34, 1, 24);
		set(level, origin, 34, 2, 24, ModContent.THERMAL_CENTRIFUGE.get());
		chargeBuffer(level, origin, 34, 2, 24);
		fillSlot(level, origin, 34, 2, 24, ThermalCentrifugeBlockEntity.INPUT_SLOT,
				new ItemStack(ModContent.URANIUM_DUST.get(), 64));
		set(level, origin, 35, 2, 24, Blocks.REDSTONE_BLOCK);
		// Galvanic Bath (MOD-127): like the polymerizer its feedstock is a fluid, so the tank is stocked
		// directly rather than through a bucket — one bucket would buy four operations and then the stand
		// would show an idle machine. Both item inputs are filled so it plates continuously.
		set(level, origin, 58, 1, 20, ModContent.GALVANIC_BATH.get());
		chargeBuffer(level, origin, 58, 1, 20);
		fillSlot(level, origin, 58, 1, 20, GalvanicBathBlockEntity.FIBER_SLOT,
				new ItemStack(Items.STRING, 64));
		fillSlot(level, origin, 58, 1, 20, GalvanicBathBlockEntity.SILVER_SLOT,
				new ItemStack(ModContent.SILVER_DUST.get(), 64));
		if (level.getBlockEntity(origin.offset(58, 1, 20)) instanceof GalvanicBathBlockEntity bath) {
			bath.fluidTank.fluid = FluidHolder.of(net.minecraft.world.level.material.Fluids.WATER);
			bath.fluidTank.amount = GalvanicBathBlockEntity.TANK_CAPACITY;
			bath.setChangedQuietly();
			bath.wake();
		}
		// Fermenter (MOD-146): the head of the organic chain. Stocked like the bath — the tank filled
		// directly, the input slot loaded — so the stand shows it brewing rather than waiting.
		// A second `set` on one cell silently drops the first machine's inventory (ADR-028).
		set(level, origin, 40, 1, 24, ModContent.FERMENTER.get());
		chargeBuffer(level, origin, 40, 1, 24);
		fillSlot(level, origin, 40, 1, 24, FermenterBlockEntity.ORGANIC_SLOT,
				new ItemStack(Items.POISONOUS_POTATO, 64));
		if (level.getBlockEntity(origin.offset(40, 1, 24)) instanceof FermenterBlockEntity fermenter) {
			fermenter.waterTank.fluid = FluidHolder.of(net.minecraft.world.level.material.Fluids.WATER);
			fermenter.waterTank.amount = FermenterBlockEntity.TANK_CAPACITY;
			fermenter.setChangedQuietly();
			fermenter.wake();
		}
		// Sprinkler (MOD-525): the tail of the same chain, and the one block here that takes no cable —
		// so it is shown charged with solution instead, beside the farm plot rather than in the machine
		// row. Its head only turns when the tank can pay, which is the whole readout it has.
		set(level, origin, 78, 1, 24, ModContent.SPRINKLER.get());
		if (level.getBlockEntity(origin.offset(78, 1, 24)) instanceof SprinklerBlockEntity sprinkler) {
			sprinkler.tank.fluid = FluidHolder.of(ModContent.NUTRIENT_SOLUTION.get());
			sprinkler.tank.amount = sprinkler.tank.capacity;
			sprinkler.setChangedQuietly();
			sprinkler.wake();
		}
		// Workstation (MOD-483): the 1x2 multiblock, shown assembled and powered so the stand carries a
		// lit one rather than two loose casings. Both cells are written by hand and the assembly hook is
		// then called explicitly — a programmatic setBlock never runs setPlacedBy, the same reason the
		// airlock's halves are placed cell by cell.
		//
		// A cell written twice loses the first block in silence and leaves a loose casing floating over
		// whatever won (MOD-597) — the ledger in #lastBuildProblems is what says this out loud.
		BlockState workstationCasing = ModContent.WORKSTATION.get().defaultBlockState();
		place(level, origin, origin.offset(54, 1, 24), workstationCasing);
		place(level, origin, origin.offset(54, 2, 24), workstationCasing);
		WorkstationBlock.tryAssemble(level, origin.offset(54, 2, 24));
		if (level.getBlockEntity(origin.offset(54, 1, 24)) instanceof WorkstationBlockEntity station) {
			station.getEnergyStorage().setAmountUntracked(station.getEnergyStorage().getCapacity());
			station.setChangedQuietly();
		}
		// Upgrade Table (MOD-482): the same 1x2 pattern, shown assembled and powered directly beside
		// the workstation it is built like. Both are 1x2 and assemble VERTICALLY, so standing them
		// shoulder to shoulder (one free cell between) costs nothing — neither scan looks sideways.
		BlockState upgradeTableCasing = ModContent.UPGRADE_TABLE.get().defaultBlockState();
		place(level, origin, origin.offset(56, 1, 24), upgradeTableCasing);
		place(level, origin, origin.offset(56, 2, 24), upgradeTableCasing);
		UpgradeTableBlock.tryAssemble(level, origin.offset(56, 2, 24));
		if (level.getBlockEntity(origin.offset(56, 1, 24)) instanceof UpgradeTableBlockEntity table) {
			table.getEnergyStorage().setAmountUntracked(table.getEnergyStorage().getCapacity());
			// A charged drill and the column module it takes: the table shows an upgrade in progress
			// rather than the "no tool" status of an empty bench.
			table.setItem(UpgradeTableBlockEntity.TOOL_SLOT, charged(ModContent.ELECTRIC_DRILL.get()));
			table.setItem(UpgradeTableBlockEntity.MODULE_SLOT, new ItemStack(ModContent.DRILL_COLUMN_MODULE.get()));
			table.setChangedQuietly();
		}
		// Assembler (MOD-275): the first MV machine, first of the second machines row, behind the
		// macerator. Charged but idle by design — this slice registers the block and its inventory; the
		// crafting cycle (and with it a blueprint to stock it with) lands in a later slice.
		set(level, origin, 4, 1, 24, ModContent.ASSEMBLER.get());
		chargeBuffer(level, origin, 4, 1, 24);
		// Recycler (MOD-145): stocked so the stand shows the thing that makes it different — a batch in
		// progress. It gets blades (without them the machine is inert), cobblestone to chew, and a slag
		// block set beside it so the building use of the output is visible in the same glance.
		set(level, origin, 46, 1, 24, ModContent.RECYCLER.get());
		chargeBuffer(level, origin, 46, 1, 24);
		fillSlot(level, origin, 46, 1, 24, RecyclerBlockEntity.BLADE_SLOT,
				new ItemStack(ModContent.RECYCLER_BLADES_TEMPERED.get()));
		fillSlot(level, origin, 46, 1, 24, RecyclerBlockEntity.INPUT_SLOT,
				new ItemStack(Items.COBBLESTONE, 64));
		set(level, origin, 48, 1, 24, ModContent.SLAG_BLOCK.get());
		// MOD-590 — carbon ceramic beside the slag block it is fired with: the two blocks the Recycler
		// feeds, standing next to each other. Machine, slag, ceramic is that story, a cell apart each.
		set(level, origin, 50, 1, 24, ModContent.CARBON_CERAMIC.get());
		// Iron furnace (MOD-115): fuel-burning, not EU — so it is loaded with input + coal instead of a
		// pre-charged buffer, and lights itself on the first tick like a vanilla furnace.
		set(level, origin, 28, 1, 20, ModContent.IRON_FURNACE.get());
		fillSlot(level, origin, 28, 1, 20, 0, new ItemStack(Items.RAW_IRON, 64));
		fillSlot(level, origin, 28, 1, 20, 1, new ItemStack(Items.COAL, 64));
		// Alloy smelter (MOD-064): the second machines row, next to the assembler. Stocked for bronze —
		// and deliberately with the tin in the LAST input rather than the second, so the stand shows the
		// thing that makes this machine different: the components may sit in any slot in any order.
		// The third slot is left empty on purpose; filling it would block the two-component recipe.
		set(level, origin, 10, 1, 24, ModContent.ALLOY_SMELTER.get());
		chargeBuffer(level, origin, 10, 1, 24);
		fillSlot(level, origin, 10, 1, 24, AlloySmelterBlockEntity.INPUT_SLOT_0,
				new ItemStack(Items.COPPER_INGOT, 64));
		fillSlot(level, origin, 10, 1, 24, AlloySmelterBlockEntity.INPUT_SLOT_2,
				new ItemStack(ModContent.TIN_INGOT.get(), 64));
		// Distillation Column (MOD-251): the 1×1×3 tower on the second machines row. The three
		// segments are placed explicitly — DemoStand.set uses setBlockAndUpdate, which never calls
		// setPlacedBy, so relying on the base's own placement hook would leave an orphan bottom
		// segment (the MOD-015 gametest lesson). HEIGHT=9 leaves ample headroom on this row.
		dev.alaindustrial.block.DistillationColumnBlock.placeTower(level,
				origin.offset(16, 1, 24));
		chargeBuffer(level, origin, 16, 1, 24);
		// Round 2: the Rectification Section on top — the stand shows the full 4-storey refinery.
		set(level, origin, 16, 4, 24, ModContent.RECTIFICATION_SECTION.get());
	}

	/**
	 * Zone <b>cables</b> (rows z=28, 32, 36, 40, in <b>two columns</b>: bare grades at x=32..39, their
	 * insulated counterparts at x=50..57): a fully charged battery box feeds a 6-cable run into an electric
	 * furnace with input — a live network per run, so the energy visibly flows (and the resistive loss of
	 * each material is observable in the GUI).
	 *
	 * <p>Rows must stay well apart or adjacent runs would connect into a single network; they are four
	 * cells apart (MOD-659), with the lab plaque of {@link #buildLabPlaque} in the aisle between two of
	 * them. Pairing each conductor with its insulated version side by side reads better than a list: the
	 * loss difference is one glance away instead of four rows.
	 */
	private static void buildCableRuns(ServerLevel level, BlockPos origin) {
		Block[][] cables = {
			{ModContent.COPPER_CABLE.get(), ModContent.INSULATED_COPPER_CABLE.get()},
			{ModContent.TIN_CABLE.get(), ModContent.INSULATED_TIN_CABLE.get()},
			{ModContent.GOLD_CABLE.get(), ModContent.INSULATED_GOLD_CABLE.get()},
			{ModContent.ELECTRUM_CABLE.get(), ModContent.INSULATED_ELECTRUM_CABLE.get()},
		};
		int z = 28;
		for (Block[] row : cables) {
			for (int column = 0; column < row.length; column++) {
				int x0 = 32 + column * 18;
				// The box's rotation is load-bearing: single-axis IO (MOD-006) emits ONLY from the face
				// opposite FACING. The cable run sits to the box's east, so the box must face WEST for its
				// output face to meet the cables. Placed with the default state (FACING=NORTH) it would emit
				// southward into thin air, the cables would not connect, and the whole row would sit dead
				// (MOD-103) — the same fix pattern as the misc zone's teleporter box.
				place(level, origin, origin.offset(x0, 1, z), ModContent.BATTERY_BOX.get().defaultBlockState()
						.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
				chargeBuffer(level, origin, x0, 1, z);
				for (int x = x0 + 1; x <= x0 + 6; x++) {
					set(level, origin, x, 1, z, row[column]);
				}
				set(level, origin, x0 + 7, 1, z, ModContent.ELECTRIC_FURNACE.get());
				fillSlot(level, origin, x0 + 7, 1, z, 0, new ItemStack(Items.RAW_COPPER, 64));
			}
			z += 4;
		}
	}

	/**
	 * Zone <b>transport</b> (rows z=16 and z=18, MOD-659): the two item-pipe grades and the fluid line.
	 * They used to stand at the very back of the stand, one row behind the showcase wall — invisible from
	 * the front, with the two item grades stacked on top of each other and so, very likely, one network
	 * (an advanced pipe IS an item pipe to its neighbour). Now each grade has its own row, one free row
	 * between, so the two lines are separate networks that can be compared by eye and by the wrench.
	 *
	 * <p>The dead monitor kit that used to ride on top of the pipes (an unpowered core without its
	 * capacity card, a panel with no filter) is gone: the working monitoring wall at
	 * {@link #buildMonitorWall} shows every one of those blocks running.
	 */
	private static void buildTransportLines(ServerLevel level, BlockPos origin) {
		// MOD-104: a short item-pipe run between two chests. The two end faces remain neutral
		// in the stand; the wrench is used by the player to demonstrate extract/insert arrows.
		set(level, origin, 4, 1, 16, ModContent.IRON_CHEST.get());
		fillSlot(level, origin, 4, 1, 16, 0, new ItemStack(Items.IRON_INGOT, 32));
		for (int x = 5; x <= 9; x++) {
			set(level, origin, x, 1, 16, ModContent.ITEM_PIPE.get());
		}
		set(level, origin, 10, 1, 16, ModContent.IRON_CHEST.get());
		// MOD-581: the advanced grade next to the basic one — the two are meant to be told apart by eye,
		// and a stand showing only one proves nothing about that. Its own row (see the class note).
		set(level, origin, 4, 1, 18, ModContent.IRON_CHEST.get());
		fillSlot(level, origin, 4, 1, 18, 0, new ItemStack(Items.IRON_INGOT, 32));
		for (int x = 5; x <= 9; x++) {
			set(level, origin, x, 1, 18, ModContent.ITEM_PIPE_ADVANCED.get());
		}
		set(level, origin, 10, 1, 18, ModContent.IRON_CHEST.get());

		// The fluid line: tank → pipes → tank, the same read-left-to-right shape, so the two transport
		// systems can be compared side by side. The source tank is seeded so the pipes carry something and
		// show their fluid colour instead of sitting empty.
		set(level, origin, 30, 1, 16, ModContent.FLUID_TANK.get());
		if (level.getBlockEntity(origin.offset(30, 1, 16)) instanceof FluidTankBlockEntity tank) {
			tank.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
			tank.fluidTank.amount = tank.fluidTank.capacity;
		}
		for (int x = 31; x <= 35; x++) {
			set(level, origin, x, 1, 16, ModContent.FLUID_PIPE.get());
		}
		set(level, origin, 36, 1, 16, ModContent.FLUID_TANK.get());
		// MOD-612: the advanced grade stands next to the basic one, both filled to their OWN capacity —
		// side by side the taller fluid column and the belt around the frame are the whole point of the
		// tier, and a stand that filled both to 8000 would hide it.
		set(level, origin, 38, 1, 16, ModContent.FLUID_TANK_ADVANCED.get());
		if (level.getBlockEntity(origin.offset(38, 1, 16)) instanceof FluidTankBlockEntity advanced) {
			advanced.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
			advanced.fluidTank.amount = advanced.fluidTank.capacity;
		}
	}

	/**
	 * Zone <b>ores</b>: a 6×2 wall at x=50..55, z=14 — stone variants on top, deepslate variants below —
	 * with the Nether ore as its last column.
	 *
	 * <p>Palladium (MOD-423) breaks the pairing the wall was built around: it is the only ore of the
	 * mod without a deepslate twin, because its host rock is netherrack/basalt/blackstone. Rather
	 * than pad the grid with a filler block, it gets its own column with the ore on both rows, so the
	 * wall stays rectangular and {@code DemoStandGameTest} still sees every registered block.
	 */
	private static void buildOreWall(ServerLevel level, BlockPos origin) {
		Block[][] wall = {
				{ModContent.TIN_ORE.get(), ModContent.SILVER_ORE.get(),
						ModContent.NICKEL_ORE.get(), ModContent.URANIUM_ORE.get(),
						ModContent.SULFUR_ORE.get(), ModContent.PALLADIUM_ORE.get()},
				{ModContent.DEEPSLATE_TIN_ORE.get(), ModContent.DEEPSLATE_SILVER_ORE.get(),
						ModContent.DEEPSLATE_NICKEL_ORE.get(), ModContent.DEEPSLATE_URANIUM_ORE.get(),
						ModContent.DEEPSLATE_SULFUR_ORE.get(), ModContent.PALLADIUM_ORE.get()}};
		for (int i = 0; i < wall[0].length; i++) {
			set(level, origin, 50 + i, 2, 14, wall[0][i]);
			set(level, origin, 50 + i, 1, 14, wall[1][i]);
		}
	}

	/**
	 * Zone <b>misc</b> (rows z=20 and z=24, east of the machines): the storage cabinet, the pump chain,
	 * the torches, the charging station, the garden and the pools of every fluid the mod adds.
	 *
	 * <p>Each exhibit is a compact group that has to stay together — a cabinet whose two storage modules
	 * must touch to merge, a pump that must touch the generator it feeds, a torch that hangs on its post
	 * — and MOD-659 spread the GROUPS apart instead: two to three free cells between one exhibit and the
	 * next, where they used to touch.
	 */
	private static void buildMisc(ServerLevel level, BlockPos origin) {
		final int z = 20;
		// The storage cabinet, x=62..65: the four chest tiers on the floor (iron, silver, gold, electrum —
		// a ladder that reads left to right), plate blocks on the shelf above and a third shelf on top.
		set(level, origin, 62, 1, z, ModContent.IRON_CHEST.get());
		set(level, origin, 63, 1, z, ModContent.SILVER_CHEST.get());
		set(level, origin, 64, 1, z, ModContent.GOLD_CHEST.get());
		set(level, origin, 65, 1, z, ModContent.ELECTRUM_CHEST.get());
		// Plate blocks (MOD-225): machine casing + two decorative plate panels, on the shelf above the chests.
		// MOD-292 puts the MV casing directly on top of the LV one so the tier step is visible side by side.
		set(level, origin, 62, 3, z, ModContent.ADVANCED_MACHINE_CASING.get());
		set(level, origin, 62, 2, z, ModContent.MACHINE_CASING.get());
		// Reinforced Energy Storage (MOD-351): next to the MV casing it is built from, so the shelf reads
		// as the MV column — casing, and the first block assembled on top of it.
		set(level, origin, 63, 3, z, ModContent.CESU.get());
		set(level, origin, 63, 2, z, ModContent.SILVER_PLATE_BLOCK.get());
		set(level, origin, 64, 2, z, ModContent.TEMPERED_IRON_PLATE_BLOCK.get());
		// Industrial Workbench (MOD-062): the Industrialist villager's job-site block on display.
		set(level, origin, 65, 2, z, ModContent.INDUSTRIAL_WORKBENCH.get());
		// MOD-287: two storage modules side by side on the top shelf — adjacent on purpose, so the stand
		// shows them merged into one warehouse rather than two separate ones (the gametest walks the cluster
		// from the first one and expects both).
		set(level, origin, 64, 3, z, ModContent.STORAGE_MODULE.get());
		set(level, origin, 65, 3, z, ModContent.STORAGE_MODULE.get());
		// The tempered iron block stands alone beside the galvanic bath, west of the cabinet.
		set(level, origin, 56, 1, z, ModContent.TEMPERED_IRON_BLOCK.get());

		// The pump chain, x=69..71: pump → geothermal generator → tank, touching so the pump's tank pushes
		// straight into the generator's. The diamond chest (MOD-599) and the mob repeller tier ladder
		// (MOD-278) stand on top of it. The repellers are placed unpowered: a live field would shove this
		// stand's own test mobs; their trim (iron / silver / electrum) IS the tier readout in the world.
		//
		// The pump draws from the block IN FRONT of it (FACING is its only intake face, the way
		// PumpBlockEntity#acquireFluid starts its search), so it faces NORTH — the default — into a small
		// lava cistern raised on the floor: one source, three walls. A pump set over a pool BELOW it, as the
		// stand did until MOD-659, stands over nothing it can drink and never moved a drop.
		set(level, origin, 69, 1, z, ModContent.PUMP.get());
		chargeBuffer(level, origin, 69, 1, z);
		set(level, origin, 69, 1, z - 1, Blocks.LAVA);
		set(level, origin, 68, 1, z - 1, FLOOR);
		set(level, origin, 70, 1, z - 1, FLOOR);
		set(level, origin, 69, 1, z - 2, FLOOR);
		set(level, origin, 70, 1, z, ModContent.GEOTHERMAL_GENERATOR.get());
		set(level, origin, 71, 1, z, ModContent.FLUID_TANK.get());
		if (level.getBlockEntity(origin.offset(71, 1, z)) instanceof FluidTankBlockEntity tank) {
			tank.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
			tank.fluidTank.amount = tank.fluidTank.capacity / 2;
			tank.setChanged();
		}
		set(level, origin, 69, 2, z, ModContent.DIAMOND_CHEST.get());
		set(level, origin, 69, 3, z, ModContent.MOB_REPELLER.get());
		set(level, origin, 70, 3, z, ModContent.MOB_REPELLER_MV.get());
		set(level, origin, 71, 3, z, ModContent.MOB_REPELLER_HV.get());

		// Enriched Uranium Torch (MOD-085): the standing torch on the floor, and the wall variant mounted
		// on a small stone post (facing WEST → supported by the post block to its east) so both survive.
		set(level, origin, 75, 1, z, ModContent.ENRICHED_URANIUM_TORCH.get());
		// Charging Station (MOD-274): banked full, so a visitor can step straight onto the stand's copy
		// and watch their gear fill — an empty one would only ever show the red "no power" indicator.
		// Sits at floor level under the wall torch; it is a 4px plate, so nothing above it moves.
		set(level, origin, 78, 1, z, ModContent.CHARGE_PAD.get());
		chargeBuffer(level, origin, 78, 1, z);
		// Energy condenser (MOD-546): banked full, so the stand's copy shows the top-tier crystal
		// and a tier-III clot already sitting in its slot — an empty one would just be a dark frame.
		set(level, origin, 79, 1, z, ModContent.ENERGY_CONDENSER.get());
		chargeBuffer(level, origin, 79, 1, z);
		set(level, origin, 79, 2, z, FLOOR);
		place(level, origin, origin.offset(78, 2, z),
				ModContent.ENRICHED_URANIUM_WALL_TORCH.get().defaultBlockState()
						.setValue(WallTorchBlock.FACING, Direction.WEST));

		// The garden, x=83..84: the drone dock beside a ripe cotton trellis, with the kok-sagyz cross-sections
		// on the row behind them.
		//
		// Cotton trellis (MOD-280): a ripe plant on moist farmland — the stand has to show the crop at
		// its most recognisable stage, with the soil it actually needs. Placed via the vanilla two-block
		// helper so both halves appear; the age is written to BOTH halves, since the upper one carries it
		// only to keep its model in step with the lower.
		place(level, origin, origin.offset(84, 0, z),
				Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
		DoublePlantBlock.placeAt(level, ModContent.TRELLIS.get().defaultBlockState(),
				origin.offset(84, 1, z), 3);
		for (int dy = 1; dy <= 2; dy++) {
			BlockPos half = origin.offset(84, dy, z);
			BlockState state = level.getBlockState(half);
			if (state.is(ModContent.TRELLIS.get())) {
				level.setBlock(half, state.setValue(TrellisBlock.AGE, TrellisBlock.MAX_AGE), 3);
			}
		}
		// Kok-sagyz column (MOD-537): shown as an exposed soil cross-section beside the trellis —
		// tip at the bottom, upper root above it, mature puff on top — so the stand answers "where
		// does the rubber come from" the way the plant itself does: dig the bottom block.
		place(level, origin, origin.offset(84, 0, z - 1), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.KokSagyzRootBlock.TIP, true));
		place(level, origin, origin.offset(84, 1, z - 1), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState());
		place(level, origin, origin.offset(84, 2, z - 1), ModContent.KOK_SAGYZ.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.KokSagyzBlock.AGE, dev.alaindustrial.block.KokSagyzBlock.AGE_MATURE));
		// MOD-584: a short harvestable root beside the full column, both in their original soil. Every
		// column of this exhibit lies inside WIDTH: a column past it would survive `clear`.
		set(level, origin, 83, 0, z - 1, FLOOR);
		place(level, origin, origin.offset(83, 1, z - 1), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.KokSagyzRootBlock.TIP, true));
		if (level.getBlockEntity(origin.offset(83, 1, z - 1)) instanceof dev.alaindustrial.block.entity.KokSagyzRootBlockEntity root) {
			root.setSoil(Blocks.SAND.defaultBlockState());
		}
		place(level, origin, origin.offset(83, 2, z - 1), ModContent.KOK_SAGYZ.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.KokSagyzBlock.AGE, dev.alaindustrial.block.KokSagyzBlock.AGE_MATURE));
		// Garden Drone Station (MOD-277): the dock beside the trellis plot, charged so its status light
		// reads "powered" rather than "no EU". Placed next to farmland on purpose — the stand should show
		// the block in the context it works in.
		set(level, origin, 83, 1, z, ModContent.GARDEN_DRONE_STATION.get());
		chargeBuffer(level, origin, 83, 1, z);
		// Deliberately NOT stocked with a drone and a hoe. It was, in MOD-659's first cut, and the station
		// did exactly what it is for: it harvested every ripe crop in its zone — the two kok-sagyz plants
		// and the ripe trellis this exhibit exists to SHOW — within a minute of the build. The drone, the
		// seeds, the bone meal and a hoe are in the bench's chests for whoever wants to watch it work.

		// Second row (z=24): the teleporter station and the pools, each one-block basin a cell of its own.
		//
		// Oil (MOD-238): a sunken one-block oil pool. Kept non-adjacent to the lava and torches on purpose
		// — a directly neighbouring igniter would set it on fire (OilLiquidBlock ignition mechanic) and the
		// stand would showcase a fire block instead.
		set(level, origin, 68, -1, 24, FLOOR);
		set(level, origin, 68, 0, 24, ModContent.OIL_BLOCK.get());
		// Distillation fractions (MOD-251): the same sunken-pool pattern for diesel and fuel oil —
		// water-like fluids, so no ignition spacing worries.
		set(level, origin, 72, -1, 24, FLOOR);
		set(level, origin, 72, 0, 24, ModContent.DIESEL_BLOCK.get());
		set(level, origin, 76, -1, 24, FLOOR);
		set(level, origin, 76, 0, 24, ModContent.FUEL_OIL_BLOCK.get());
		// The organic chain's two fluids (MOD-146/MOD-525), same sunken-basin pattern. The solution stands
		// by the fermenter it comes from, and the sprinkler (built with the machines) sits between the
		// fuel-oil pool and the biofuel.
		set(level, origin, 80, -1, 24, FLOOR);
		set(level, origin, 80, 0, 24, ModContent.BIOFUEL_BLOCK.get());
		set(level, origin, 44, -1, 24, FLOOR);
		set(level, origin, 44, 0, 24, ModContent.NUTRIENT_SOLUTION_BLOCK.get());
		// Burning oil and its soot (MOD-638), on the east edge of the floor, away from the oil pool and
		// from anything flammable. The fire is live: it burns out within seconds of the build and may
		// itself leave soot — the coverage scan runs on the build tick, while it still stands.
		set(level, origin, 86, 1, 28, ModContent.SOOT_LAYER.get());
		set(level, origin, 86, 1, 32, ModContent.OIL_FIRE.get());
		// Teleporter station (MOD-091): a charged battery box feeds it through a cable, so the stand
		// shows it actually taking EU rather than standing there inert. It has no GUI and cannot jump
		// yet — the remote is MOD-092. Hidden from the creative tab until MOD-093, so the demo stand
		// and /give are the only ways to see it right now.
		//
		// The box's rotation is explicit and load-bearing: it emits ONLY from the face opposite its
		// FACING and is inert on the other four (single-axis IO, MOD-006 — see
		// BatteryBoxBlockEntity#energyRoleForFace). The cable sits to its east, so the box must face
		// WEST for its output face to meet it. Placed with the default state (FACING=NORTH) it would
		// emit southward into thin air, the cable would not even connect, and the station would sit
		// there dead next to a full battery.
		place(level, origin, origin.offset(62, 1, 24), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 62, 1, 24);
		set(level, origin, 63, 1, 24, ModContent.COPPER_CABLE.get());
		set(level, origin, 64, 1, 24, ModContent.TELEPORTER.get());
		// MOD-112: a station is a jump destination only with its capsule, so the stand builds one from
		// two glass blocks. The glass goes through the ledger; assembling swaps it in place.
		set(level, origin, 64, 2, 24, net.minecraft.world.level.block.Blocks.GLASS);
		set(level, origin, 64, 3, 24, net.minecraft.world.level.block.Blocks.GLASS);
		dev.alaindustrial.block.TeleporterBlock.tryAssemble(level, origin.offset(64, 1, 24));
	}

	/** Row z of the test bench: the front row, beside the tiers, where a tester arrives. */
	private static final int BENCH_Z = 2;

	/**
	 * Zone <b>bench</b> (MOD-659, row z=2, x=58..78): what a tester reaches for first — a recharge point
	 * and four chests of gear they would otherwise have to conjure one item at a time.
	 *
	 * <p>The recharge point is a creative energy source behind a copper cable into a charging pad: stand
	 * on the pad and the gear in the hands and on the body fills up, indefinitely, unlike every other
	 * charged block on the stand (their buffers run dry within minutes).
	 *
	 * <p>The chests hold the things that are awkward to get in the creative inventory because they are
	 * only interesting FULL: the electric tools, devices and armour arrive charged; the chips, parts and
	 * rotors, and the buckets, seeds and fuel needed to try a machine, come stacked. Nothing here is a
	 * demonstration; it is a supply point, and the four chests are the four things it supplies.
	 */
	private static void buildTestBench(ServerLevel level, BlockPos origin) {
		set(level, origin, 58, 1, BENCH_Z, ModContent.CREATIVE_ENERGY_SOURCE.get());
		set(level, origin, 59, 1, BENCH_Z, ModContent.COPPER_CABLE.get());
		set(level, origin, 60, 1, BENCH_Z, ModContent.CHARGE_PAD.get());

		set(level, origin, 66, 1, BENCH_Z, ModContent.DIAMOND_CHEST.get());
		stock(level, origin, 66, 1, BENCH_Z, List.of(
				charged(ModContent.ELECTRIC_DRILL.get()), charged(ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get()),
				charged(ModContent.ELECTRIC_DRILL_NETHERITE_TIP.get()), charged(ModContent.ELECTRIC_CHAINSAW.get()),
				charged(ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP.get()), charged(ModContent.ELECTRIC_SHOVEL.get()),
				charged(ModContent.ELECTRIC_SHOVEL_DIAMOND_TIP.get()), charged(ModContent.ELECTRIC_HOE.get()),
				charged(ModContent.ELECTRIC_HOE_DIAMOND_TIP.get()), charged(ModContent.ELECTRIC_SABER.get()),
				charged(ModContent.ELECTRIC_BOW.get()), charged(ModContent.ELECTROMAGNET.get()),
				charged(ModContent.ELECTROMAGNET_ADVANCED.get()), charged(ModContent.JETPACK.get()),
				charged(ModContent.ENERGY_PACK.get()), charged(ModContent.BATTERY.get()),
				charged(ModContent.BATTERY_POUCH.get()), charged(ModContent.SHIELDING_POUCH.get()),
				charged(ModContent.VACUUM_CAPSULE.get()), charged(ModContent.NETWORK_ANALYZER.get()),
				charged(ModContent.WIND_GAUGE.get()), charged(ModContent.GEIGER_COUNTER.get()),
				charged(ModContent.TELEPORTER_REMOTE.get()), charged(ModContent.ENERGY_CRYSTAL.get()),
				charged(ModContent.LAPOTRON_CRYSTAL.get()), charged(ModContent.RESONANT_CRYSTAL.get()),
				charged(ModContent.WRENCH.get()), charged(ModContent.CABLE_BREAKER.get()),
				charged(ModContent.FORGE_HAMMER.get()), charged(ModContent.GARDEN_DRONE.get()),
				charged(ModContent.SCYTHE_WOOD.get()), charged(ModContent.SCYTHE_STONE.get()),
				charged(ModContent.SCYTHE_COPPER.get()), charged(ModContent.SCYTHE_IRON.get()),
				charged(ModContent.SCYTHE_GOLD.get()), charged(ModContent.SCYTHE_TEMPERED_IRON.get()),
				charged(ModContent.SCYTHE_DIAMOND.get()), charged(ModContent.SCYTHE_NETHERITE.get()),
				new ItemStack(ModContent.GUIDE_BOOK.get()), new ItemStack(ModContent.STOCK_DISPLAY_FRAME_ITEM.get(), 16)));

		set(level, origin, 70, 1, BENCH_Z, ModContent.DIAMOND_CHEST.get());
		stock(level, origin, 70, 1, BENCH_Z, List.of(
				charged(ModContent.FLUXWEAVE_HELMET.get()), charged(ModContent.FLUXWEAVE_CHESTPLATE.get()),
				charged(ModContent.FLUXWEAVE_LEGGINGS.get()), charged(ModContent.FLUXWEAVE_BOOTS.get()),
				charged(ModContent.SHIELDING_HELMET.get()), charged(ModContent.SHIELDING_CHESTPLATE.get()),
				charged(ModContent.SHIELDING_LEGGINGS.get()), charged(ModContent.SHIELDING_BOOTS.get()),
				charged(ModContent.INSULATED_HELMET.get()), charged(ModContent.INSULATED_CHESTPLATE.get()),
				charged(ModContent.INSULATED_LEGGINGS.get()), charged(ModContent.INSULATED_BOOTS.get()),
				charged(ModContent.TEMPERED_IRON_HELMET.get()), charged(ModContent.TEMPERED_IRON_CHESTPLATE.get()),
				charged(ModContent.TEMPERED_IRON_LEGGINGS.get()), charged(ModContent.TEMPERED_IRON_BOOTS.get()),
				charged(ModContent.TEMPERED_IRON_PICKAXE.get()), charged(ModContent.TEMPERED_IRON_AXE.get()),
				charged(ModContent.TEMPERED_IRON_SHOVEL.get()), charged(ModContent.TEMPERED_IRON_HOE.get()),
				charged(ModContent.TEMPERED_IRON_SWORD.get())));

		set(level, origin, 74, 1, BENCH_Z, ModContent.DIAMOND_CHEST.get());
		stock(level, origin, 74, 1, BENCH_Z, List.of(
				new ItemStack(ModContent.OVERCLOCKER_CHIP_I.get()), new ItemStack(ModContent.OVERCLOCKER_CHIP_II.get()),
				new ItemStack(ModContent.OVERCLOCKER_CHIP_III.get()), new ItemStack(ModContent.MUTE_CHIP.get()),
				new ItemStack(ModContent.STATS_CHIP.get()), new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get()),
				new ItemStack(ModContent.ALIGNMENT_CHIP_NIGHT.get()), new ItemStack(ModContent.RESONANCE_CHIP.get()),
				new ItemStack(ModContent.RTP_CHIP.get()), new ItemStack(ModContent.EMPTY_CHIP.get()),
				new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get()),
				new ItemStack(ModContent.MUTATION_CHIP_DUPLICATE.get()),
				new ItemStack(ModContent.MUTATION_CHIP_CREATE.get()), new ItemStack(ModContent.CAPACITY_CARD.get()),
				new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()), new ItemStack(ModContent.ENERGY_CLOT_I.get()),
				new ItemStack(ModContent.ENERGY_CLOT_II.get()), new ItemStack(ModContent.ENERGY_CLOT_III.get()),
				new ItemStack(ModContent.WINDMILL_ROTOR.get()), new ItemStack(ModContent.WINDMILL_ROTOR_REINFORCED.get()),
				new ItemStack(ModContent.WINDMILL_ROTOR_ADVANCED.get()), new ItemStack(ModContent.WATER_MILL_WHEEL.get()),
				new ItemStack(ModContent.WATER_MILL_WHEEL_REINFORCED.get()),
				new ItemStack(ModContent.WATER_MILL_WHEEL_ADVANCED.get()),
				new ItemStack(ModContent.LIGHTNING_ROD_CONDUCTOR_TIP.get()),
				new ItemStack(ModContent.LIGHTNING_ROD_CONDUCTOR_TIP_REINFORCED.get()),
				new ItemStack(ModContent.LIGHTNING_ROD_CONDUCTOR_TIP_ADVANCED.get()),
				new ItemStack(ModContent.RECYCLER_BLADES_IRON.get()), new ItemStack(ModContent.RECYCLER_BLADES_TEMPERED.get()),
				new ItemStack(ModContent.RECYCLER_BLADES_DIAMOND.get()), new ItemStack(ModContent.DRILL_COLUMN_MODULE.get()),
				new ItemStack(ModContent.CORE_BARREL.get())));

		set(level, origin, 78, 1, BENCH_Z, ModContent.DIAMOND_CHEST.get());
		stock(level, origin, 78, 1, BENCH_Z, List.of(
				new ItemStack(ModContent.OIL_BUCKET.get()), new ItemStack(ModContent.DIESEL_BUCKET.get()),
				new ItemStack(ModContent.FUEL_OIL_BUCKET.get()), new ItemStack(ModContent.BIOFUEL_BUCKET.get()),
				new ItemStack(ModContent.NUTRIENT_SOLUTION_BUCKET.get()), new ItemStack(Items.WATER_BUCKET),
				new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.COAL, 64), new ItemStack(Items.REDSTONE, 64),
				new ItemStack(Items.LEVER, 16), new ItemStack(ModContent.URANIUM_FUEL_ROD.get(), 16),
				new ItemStack(ModContent.EMPTY_CAN.get(), 64), new ItemStack(Items.COOKED_BEEF, 64),
				new ItemStack(ModContent.COTTON_SEEDS.get(), 64), new ItemStack(ModContent.KOK_SAGYZ_SEEDS.get(), 64),
				new ItemStack(Items.BONE_MEAL, 64), new ItemStack(Items.IRON_HOE)));
	}

	/** A stack of {@code item}, filled to the brim if it holds energy at all (a plain item is returned as it is). */
	private static ItemStack charged(Item item) {
		ItemStack stack = new ItemStack(item);
		long capacity = ItemEnergy.capacity(stack);
		if (capacity > 0) {
			ItemEnergy.set(stack, capacity);
		}
		return stack;
	}

	/** Put {@code stacks} into the first slots of the container at a cell. */
	private static void stock(ServerLevel level, BlockPos origin, int x, int y, int z, List<ItemStack> stacks) {
		for (int slot = 0; slot < stacks.size(); slot++) {
			fillSlot(level, origin, x, y, z, slot, stacks.get(slot));
		}
	}

	// --- helpers ---

	/**
	 * Zone <b>tiers</b> (MOD-294, row z=2, rows z=0..1 kept clear): one live network per voltage tier so
	 * the ladder reads left to right along the stand's north edge, twenty cells between the three. Each storage block faces WEST — the
	 * single-axis IO rule (front IN, back OUT, MOD-006) then puts its output face east, straight
	 * into the tier's own cable grade, exactly like the cable-run boxes below.
	 *
	 * <p>HV is a stub by design: the electrum cable and its consumer exist, but real HV content is
	 * roadmap; the row shows what is built rather than pretending otherwise.
	 */
	private static void buildTierZone(ServerLevel level, BlockPos origin) {
		// LV: battery box, buffer charged full → tin cable → a macerator actually grinding.
		place(level, origin, origin.offset(4, 1, 2), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 4, 1, 2);
		set(level, origin, 5, 1, 2, ModContent.TIN_CABLE.get());
		placeWorkingMachine(level, origin, 6, 2, ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 64));
		// MV: CESU, buffer charged full → gold cable → the assembler, charged and idle (first MV machine).
		place(level, origin, origin.offset(24, 1, 2), ModContent.CESU.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 24, 1, 2);
		set(level, origin, 25, 1, 2, ModContent.GOLD_CABLE.get());
		set(level, origin, 26, 1, 2, ModContent.ASSEMBLER.get());
		chargeBuffer(level, origin, 26, 1, 2);
		// HV stub: an LV battery feeding a teleporter over HV wiring is legal (packet ceiling, not
		// floor) and keeps the row honest — no fake HV source stands in for content that is not built.
		place(level, origin, origin.offset(44, 1, 2), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 44, 1, 2);
		set(level, origin, 45, 1, 2, ModContent.ELECTRUM_CABLE.get());
		set(level, origin, 46, 1, 2, ModContent.TELEPORTER.get());
		// MOD-112: the same capsule as the misc zone's station, from two glass blocks.
		set(level, origin, 46, 2, 2, net.minecraft.world.level.block.Blocks.GLASS);
		set(level, origin, 46, 3, 2, net.minecraft.world.level.block.Blocks.GLASS);
		dev.alaindustrial.block.TeleporterBlock.tryAssemble(level, origin.offset(46, 1, 2));
	}

	/**
	 * Zone <b>loss lane</b> (MOD-294, row z=14): a single 36-block copper run from a
	 * charged battery box into an electric furnace. The bare-vs-insulated comparison at 6 blocks
	 * already lives in the cable zone; this lane answers the other question — what a LONG haul
	 * costs. Read it by walking the line and comparing the furnace GUI's received-EU against the
	 * distance from the box.
	 */
	private static void buildLossLane(ServerLevel level, BlockPos origin) {
		place(level, origin, origin.offset(4, 1, 14), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 4, 1, 14);
		for (int x = 5; x <= 40; x++) {
			set(level, origin, x, 1, 14, ModContent.COPPER_CABLE.get());
		}
		set(level, origin, 41, 1, 14, ModContent.ELECTRIC_FURNACE.get());
		fillSlot(level, origin, 41, 1, 14, 0, new ItemStack(Items.RAW_COPPER, 64));
	}

	/**
	 * Zone <b>farms</b> (MOD-294, chains along z=46): four working mini-chains, one per key
	 * progression, each stocked so it runs the moment the stand is built. The rows on either side are
	 * walkways; the north neighbour of every chain cell is air, never an energy block, so nothing from
	 * the item-less row (z=44) or the cable rows above can tap a farm.
	 */
	private static void buildFarms(ServerLevel level, BlockPos origin) {
		// Farm A — LV cycle: solar → copper cable → battery box → cable → macerator, ore chest beside.
		// The panels are FACING-inert (every horizontal face is OUT), so the east-running chain needs
		// no state juggling — the cable simply meets the panel's OUT face.
		set(level, origin, 4, 1, 46, ModContent.SOLAR_PANEL.get());
		set(level, origin, 5, 1, 46, ModContent.COPPER_CABLE.get());
		place(level, origin, origin.offset(6, 1, 46), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 6, 1, 46);
		set(level, origin, 7, 1, 46, ModContent.COPPER_CABLE.get());
		placeWorkingMachine(level, origin, 8, 46, ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 64));
		set(level, origin, 10, 1, 46, ModContent.IRON_CHEST.get());
		fillSlot(level, origin, 10, 1, 46, 0, new ItemStack(Items.RAW_IRON, 64));
		fillSlot(level, origin, 10, 1, 46, 1, new ItemStack(Items.COAL, 64));
		// Farm B — fluid line: battery box → pump facing a water cistern → fluid pipes → empty tank
		// that visibly fills. The pump's IN faces are everything but its intake (PumpBlock), so the
		// box, standing on its south face, meets one directly.
		//
		// The pump drinks from the block IN FRONT of it (its FACING is its only intake face), so it
		// faces EAST into a raised cistern: a walled 2x2 pool of sources. The pump removes the source it
		// drinks and the two beside the emptied cell refill it — the cistern is inexhaustible, like a
		// vanilla pond. The stand used to sink one water cell UNDER the pump, which stands over nothing it
		// can drink (MOD-659). Walled on every side, because flowing fluid washes the mod's pipes away
		// (MOD-661): the three-in-a-row cistern this replaced had its east source right under the first
		// pipe of the line and swept it off the stand.
		//
		// MOD-674: the whole line stands on rows z=45..48, one row clear of the showcase frames (z=49).
		// The cistern's back wall used to be laid AT z=49, on top of the two lowest frames' cells: the
		// frames could not hang there and left two holes in the wall, with water lying against it. Nothing
		// of this farm stands on z=49 now, and the water is two cells from it. The pump is the pool's west
		// wall beside its north row and the battery box is the west wall beside its south row.
		place(level, origin, origin.offset(35, 1, 46), ModContent.PUMP.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		chargeBuffer(level, origin, 35, 1, 46);
		place(level, origin, origin.offset(35, 1, 47), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH));
		chargeBuffer(level, origin, 35, 1, 47);
		for (int cx = 36; cx <= 37; cx++) {
			set(level, origin, cx, 1, 45, FLOOR);
			set(level, origin, cx, 1, 46, Blocks.WATER);
			set(level, origin, cx, 1, 47, Blocks.WATER);
			set(level, origin, cx, 1, 48, FLOOR);
		}
		set(level, origin, 38, 1, 46, FLOOR);
		set(level, origin, 38, 1, 47, FLOOR);
		for (int x = 32; x <= 34; x++) {
			set(level, origin, x, 1, 46, ModContent.FLUID_PIPE.get());
		}
		set(level, origin, 31, 1, 46, ModContent.FLUID_TANK.get());
		// Farm C — oil → rubber → cable: an open oil cell beside an oil-fed polymerizer, the
		// heater+vulcanizer pair, and a chest with the chain's inputs and both cable grades to
		// compare in hand.
		set(level, origin, 44, -1, 46, FLOOR);
		set(level, origin, 44, 0, 46, ModContent.OIL_BLOCK.get());
		set(level, origin, 45, 1, 46, ModContent.POLYMERIZER.get());
		chargeBuffer(level, origin, 45, 1, 46);
		if (level.getBlockEntity(origin.offset(45, 1, 46)) instanceof PolymerizerBlockEntity polymerizer) {
			polymerizer.fluidTank.fluid = FluidHolder.of(ModContent.OIL.get());
			polymerizer.fluidTank.amount = PolymerizerBlockEntity.TANK_CAPACITY;
			polymerizer.setChangedQuietly();
			polymerizer.wake();
		}
		set(level, origin, 47, 1, 46, ModContent.ELECTRIC_HEATER.get());
		chargeBuffer(level, origin, 47, 1, 46);
		set(level, origin, 47, 2, 46, ModContent.VULCANIZER.get());
		chargeBuffer(level, origin, 47, 2, 46);
		fillSlot(level, origin, 47, 2, 46, VulcanizerBlockEntity.RAW_RUBBER_SLOT,
				new ItemStack(ModContent.RAW_RUBBER.get(), 64));
		fillSlot(level, origin, 47, 2, 46, VulcanizerBlockEntity.SULFUR_SLOT,
				new ItemStack(ModContent.SULFUR_DUST.get(), 64));
		set(level, origin, 49, 1, 46, ModContent.IRON_CHEST.get());
		fillSlot(level, origin, 49, 1, 46, 0, new ItemStack(ModContent.RAW_RUBBER.get(), 64));
		fillSlot(level, origin, 49, 1, 46, 1, new ItemStack(ModContent.RUBBER.get(), 64));
		fillSlot(level, origin, 49, 1, 46, 2, new ItemStack(ModContent.INSULATED_COPPER_CABLE_ITEM.get(), 64));
		// Farm D — mutation: incubator + dome in transform mode, a ripe trellis on moist farmland,
		// and a chest with all three chips. The misc zone's incubator shows duplicate mode; this one
		// shows the mode that consumes the plant and returns the mutated result.
		set(level, origin, 66, 1, 46, ModContent.INCUBATOR.get());
		set(level, origin, 66, 2, 46, ModContent.INCUBATOR_DOME.get());
		chargeBuffer(level, origin, 66, 1, 46);
		fillSlot(level, origin, 66, 1, 46, IncubatorBlockEntity.CHIP_SLOT,
				new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get()));
		fillSlot(level, origin, 66, 1, 46, IncubatorBlockEntity.FUEL_SLOT,
				new ItemStack(ModContent.URANIUM_INGOT.get(), 16));
		fillSlot(level, origin, 66, 1, 46, IncubatorBlockEntity.INPUT_SLOT,
				new ItemStack(Items.SWEET_BERRIES, 64));
		place(level, origin, origin.offset(68, 0, 46),
				Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
		DoublePlantBlock.placeAt(level, ModContent.TRELLIS.get().defaultBlockState(),
				origin.offset(68, 1, 46), 3);
		for (int dy = 1; dy <= 2; dy++) {
			BlockPos half = origin.offset(68, dy, 46);
			BlockState state = level.getBlockState(half);
			if (state.is(ModContent.TRELLIS.get())) {
				level.setBlock(half, state.setValue(TrellisBlock.AGE, TrellisBlock.MAX_AGE), 3);
			}
		}
		set(level, origin, 70, 1, 46, ModContent.IRON_CHEST.get());
		fillSlot(level, origin, 70, 1, 46, 0, new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get(), 16));
		fillSlot(level, origin, 70, 1, 46, 1, new ItemStack(ModContent.MUTATION_CHIP_DUPLICATE.get(), 16));
		fillSlot(level, origin, 70, 1, 46, 2, new ItemStack(ModContent.MUTATION_CHIP_CREATE.get(), 16));
	}

	/**
	 * Zone <b>reactor</b> (MOD-468, row z=42): every part of the reactor room laid out side by side, a
	 * cell apart (x=4, 6, 8, … in MOD-659's doubled layout).
	 *
	 * <p>A row rather than an actual room, and deliberately so: a sealed 5x5x5 shell would hide its own
	 * contents, and the stand exists to SHOW blocks. The pieces are spaced so each reads on its own —
	 * casing, glass, feedthrough and lamp in a run, the airlock standing free, the controller facing the
	 * camera, and a fuel assembly loaded to the brim so the rods are visible through its casing.
	 *
	 * <p>Nothing here is wired: the reactor only runs inside a sealed room, and a controller reporting
	 * "not formed" on the stand is the honest state for a block sitting in the open.
	 */
	/**
	 * Zone <b>crystal greenhouse</b> (MOD-505): a real, sealed greenhouse standing beside the reactor
	 * room, not a row of loose blocks.
	 *
	 * <p>A row is how this started, and it was the wrong shape for this feature. The reactor zone can
	 * be a row because its parts read on their own; a greenhouse's whole point is the moment it closes
	 * — the shell drops its seams, the panel lights, the beds start budding — and none of that happens
	 * to a block lying on a shelf. So the stand builds one that actually seals, and the controller's
	 * own scan turns it on a second or two after {@code /ala demo build} finishes.
	 *
	 * <p>Placed at x 16..22, z 30..36, east of the reactor room's 7×7×7 at x 4..10 with five blocks of
	 * air between them (room for the reactor room's water feed and for a visitor):
	 * two multiblocks side by side, which is the comparison worth showing. Like the room it is a
	 * 7×7×7 shell with a 5×5×5 interior since MOD-659 (the scan asks for at least 27 cells and takes up
	 * to 4096); the interior holds a row of five beds that show the whole life of the block at once.
	 * The write ledger ({@link #lastBuildProblems}) reports any block a later zone overwrites.
	 */
	private static void buildCrystalGreenhouse(ServerLevel level, BlockPos origin) {
		final int bx = 16;
		final int by = 1;
		final int bz = 30;
		final int edge = 7;

		// Deck underfoot and overhead, glass everywhere else: the interior is 5×5×5, 125 cells against the
		// 27 the scan asks for at least.
		for (int lx = 0; lx < edge; lx++) {
			for (int ly = 0; ly < edge; ly++) {
				for (int lz = 0; lz < edge; lz++) {
					boolean perimeter = lx == 0 || lx == edge - 1 || ly == 0 || ly == edge - 1
							|| lz == 0 || lz == edge - 1;
					if (!perimeter) {
						continue;
					}
					boolean deck = ly == 0 || ly == edge - 1;
					set(level, origin, bx + lx, by + ly, bz + lz,
							deck ? ModContent.CRYSTAL_FARM_FLOOR.get()
									: ModContent.CRYSTAL_FARM_GLASS.get());
				}
			}
		}

		// Controller in the north wall, panel outward — FACING names the way it looks OUT, and the
		// scan walks inward along the opposite.
		refit(level, origin, origin.offset(bx + 1, by + 1, bz),
				ModContent.CRYSTAL_FARM_CONTROLLER.get().defaultBlockState()
						.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));

		// Door in the same wall, both halves by hand: setPlacedBy, which normally raises the upper
		// leaf, does not run for a programmatic setBlock.
		BlockState door = ModContent.CRYSTAL_FARM_DOOR.get().defaultBlockState()
				.setValue(DoorBlock.FACING, Direction.SOUTH);
		refit(level, origin, origin.offset(bx + 3, by + 1, bz), door);
		refit(level, origin, origin.offset(bx + 3, by + 2, bz),
				door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

		// Water in the far corner: the free half of the growth bonus, and the panel reports it.
		place(level, origin, origin.offset(bx + edge - 2, by + 1, bz + edge - 2),
				Blocks.WATER.defaultBlockState());

		// A row of five beds along the back, a row from the door so the walk in stays free: one dead as
		// crafted, then four awake ones carrying vanilla amethyst at every stage of its growth, so the
		// stand shows the whole life of the block at once.
		final int bedRow = bz + edge - 3;
		set(level, origin, bx + 1, by + 1, bedRow, ModContent.CRYSTAL_SEEDBED.get());
		Block[] stages = { Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD,
				Blocks.AMETHYST_CLUSTER };
		int lx = 2;
		for (Block stage : stages) {
			BlockPos bed = origin.offset(bx + lx, by + 1, bedRow);
			place(level, origin, bed, ModContent.CRYSTAL_SEEDBED.get().defaultBlockState()
					.setValue(CrystalSeedbedBlock.CHARGES, CrystalSeedbedBlock.MAX_CHARGES));
			place(level, origin, bed.above(), stage.defaultBlockState()
					.setValue(AmethystClusterBlock.FACING, Direction.UP));
			lx++;
		}
	}

	private static void buildReactorZone(ServerLevel level, BlockPos origin) {
		int z = 42;
		set(level, origin, 4, 1, z, ModContent.REACTOR_CASING.get());
		set(level, origin, 6, 1, z, ModContent.REACTOR_GLASS.get());
		set(level, origin, 8, 1, z, ModContent.REACTOR_PORT.get());
		set(level, origin, 10, 1, z, ModContent.REACTOR_LAMP.get());
		set(level, origin, 12, 1, z, ModContent.REACTOR_OUTLET.get());

		// The button needs something to hang on, so it gets its own casing block to sit against.
		set(level, origin, 14, 1, z, ModContent.REACTOR_CASING.get());
		place(level, origin, origin.offset(14, 2, z),
				ModContent.REACTOR_BUTTON.get().defaultBlockState()
						.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
						.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));

		// The lever (MOD-514) stands beside the button it twins, on its own casing block, so the two
		// control blocks can be told apart at a glance: one pulses, one latches.
		set(level, origin, 16, 1, z, ModContent.REACTOR_CASING.get());
		place(level, origin, origin.offset(16, 2, z),
				ModContent.REACTOR_LEVER.get().defaultBlockState()
						.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
						.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));

		// The airlock is two blocks: the stand places both halves by hand, because setPlacedBy (which
		// normally raises the upper half) does not run for a programmatic setBlock.
		BlockState door = ModContent.REACTOR_DOOR.get().defaultBlockState()
				.setValue(ReactorDoorBlock.FACING, Direction.SOUTH);
		place(level, origin, origin.offset(18, 1, z), door);
		place(level, origin, origin.offset(18, 2, z),
				door.setValue(ReactorDoorBlock.HALF, DoubleBlockHalf.UPPER));

		place(level, origin, origin.offset(22, 1, z),
				ModContent.REACTOR_CONTROLLER.get().defaultBlockState()
						.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));

		// The exhaust, facing south into open air — a nozzle pointing at a block vents nothing, and a
		// stand that showed one buried in a wall would be showing a broken installation.
		place(level, origin, origin.offset(30, 1, z),
				ModContent.STEAM_NOZZLE.get().defaultBlockState()
						.setValue(SteamNozzleBlock.FACING, Direction.SOUTH));

		// Loaded to four rods, so the stand shows the state the fill level exists to communicate.
		place(level, origin, origin.offset(26, 1, z),
				ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState()
						.setValue(FuelRodAssemblyBlock.RODS, FuelRodAssemblyBlock.MAX_RODS));
		if (level.getBlockEntity(origin.offset(26, 1, z))
				instanceof FuelRodAssemblyBlockEntity assembly) {
			for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
				assembly.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
			}
			// Half a tank, so the stand shows the coolant level doing what it is for: a column that is
			// full and a column that is empty look the same from the front, and neither is the state
			// the property exists to communicate.
			assembly.setTank(true, assembly.waterTank.capacity / 2);
		}
	}

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
	 */
	private static void buildReactorRoom(ServerLevel level, BlockPos origin) {
		// The room stands at x 4..10, z 30..36 (its north-west floor corner): west of the cable rows,
		// which own x>=32 from z=28 to z=40, and clear of the reactor row of loose parts at z=42. Outside
		// the 7x7 footprint stand only its exhaust (pipe and nozzle at x=3, z=33), its water feed (x 11..14,
		// z 33..36, in the walkway to the greenhouse) and its control post (z=29).
		final int bx = 4;
		final int by = 1;
		final int bz = 30;
		final int edge = 7;
		// The middle of a wall, floor or roof: where the crossings and the core sit.
		final int mid = edge / 2;

		// Shell: every perimeter cell of the 7x7x7 box is casing; the interior is left as air.
		for (int lx = 0; lx < edge; lx++) {
			for (int ly = 0; ly < edge; ly++) {
				for (int lz = 0; lz < edge; lz++) {
					boolean perimeter = lx == 0 || lx == edge - 1 || ly == 0 || ly == edge - 1
							|| lz == 0 || lz == edge - 1;
					if (perimeter) {
						set(level, origin, bx + lx, by + ly, bz + lz, ModContent.REACTOR_CASING.get());
					}
				}
			}
		}

		// Windows in the north wall — seven cells, far under the 30 % glass cap the scan enforces.
		// From here to the door, every write is a `refit`: it is a fitting punched into the casing
		// shell laid above, which is a replacement the author means (MOD-597).
		refit(level, origin, bx + 1, by + 2, bz, ModContent.REACTOR_GLASS.get());
		refit(level, origin, bx + 2, by + 2, bz, ModContent.REACTOR_GLASS.get());
		for (int wx = 1; wx <= 5; wx++) {
			refit(level, origin, bx + wx, by + 3, bz, ModContent.REACTOR_GLASS.get());
		}

		// Crossings, all on the room's middle line: water comes in through the east inlet, steam leaves
		// through the west one a level up (an inlet holds one fluid at a time, so the two lines need two),
		// the power outlet sits under the steam inlet, and the lamp is in the roof.
		refit(level, origin, bx + edge - 1, by + 1, bz + mid, ModContent.REACTOR_PORT.get());
		refit(level, origin, bx, by + 2, bz + mid, ModContent.REACTOR_PORT.get());
		refit(level, origin, bx, by + 1, bz + mid, ModContent.REACTOR_OUTLET.get());
		refit(level, origin, bx + mid, by + edge - 1, bz + mid, ModContent.REACTOR_LAMP.get());

		// Controller in the north wall, front outward, left of the middle; the airlock stands as far right.
		refit(level, origin, origin.offset(bx + 2, by + 1, bz),
				ModContent.REACTOR_CONTROLLER.get().defaultBlockState()
						.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));

		// Airlock, both halves by hand: setPlacedBy does not run for a programmatic setBlock.
		BlockState door = ModContent.REACTOR_DOOR.get().defaultBlockState()
				.setValue(ReactorDoorBlock.FACING, Direction.SOUTH);
		refit(level, origin, origin.offset(bx + 4, by + 1, bz), door);
		refit(level, origin, origin.offset(bx + 4, by + 2, bz),
				door.setValue(ReactorDoorBlock.HALF, DoubleBlockHalf.UPPER));

		// Control post outside the door: one casing block, and the button on its west face.
		set(level, origin, bx + 5, by + 1, bz - 1, ModContent.REACTOR_CASING.get());
		place(level, origin, origin.offset(bx + 4, by + 1, bz - 1),
				ModContent.REACTOR_BUTTON.get().defaultBlockState()
						.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
						.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));

		// Interior: the core, left without a signal — see the method note on why the stand does not run it.
		place(level, origin, origin.offset(bx + mid, by + 1, bz + mid),
				ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState()
						.setValue(FuelRodAssemblyBlock.RODS, FuelRodAssemblyBlock.MAX_RODS));
		if (level.getBlockEntity(origin.offset(bx + mid, by + 1, bz + mid))
				instanceof FuelRodAssemblyBlockEntity assembly) {
			for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
				assembly.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
			}
			assembly.setTank(true, assembly.waterTank.capacity / 2);
		}
		// The scram switch, inside on the controller's back — the one held signal that survives a meltdown
		// (MOD-514). Placed OFF: flicking it is the whole of starting the reactor.
		place(level, origin, origin.offset(bx + 2, by + 1, bz + 1),
				ModContent.REACTOR_LEVER.get().defaultBlockState()
						.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
						.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));

		// ── Coolant loop (MOD-660): water in low on the east, steam out high on the west. ──
		// Every fitting that meets a vessel is wrenched on purpose, so the collars show which way each
		// line runs. The inlets need it — an inlet is a two-way crossing and a plain face against one moves
		// nothing — while the column and the nozzle are one-way and would be read right without it.
		final int east = bx + edge;
		final int row = bz + mid;

		// Water feed, outside: a pump drinking from a raised cistern, fed by a creative source beside it,
		// and ordinary pipe up to the east inlet — outside the shell an ordinary pipe is safe. The pump
		// drinks from the block IN FRONT of it (FACING is its only intake), so it faces SOUTH into a 2x2
		// pool of sources: it removes the one it drinks and the two beside it refill it. It pushes into
		// the pipe at its west face by itself.
		// The pool is walled: flowing fluid washes the mod's pipes away (MOD-661, both lines), and a
		// source beside the pipe at the pump's west face would sweep it off the stand within a tick.
		place(level, origin, origin.offset(east + 1, by, row), ModContent.PUMP.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH));
		chargeBuffer(level, origin, east + 1, by, row);
		set(level, origin, east + 2, by, row, ModContent.CREATIVE_ENERGY_SOURCE.get());
		for (int cz = row + 1; cz <= row + 2; cz++) {
			set(level, origin, east, by, cz, FLOOR);
			set(level, origin, east + 1, by, cz, Blocks.WATER);
			set(level, origin, east + 2, by, cz, Blocks.WATER);
			set(level, origin, east + 3, by, cz, FLOOR);
		}
		set(level, origin, east + 1, by, row + 3, FLOOR);
		set(level, origin, east + 2, by, row + 3, FLOOR);
		set(level, origin, east, by, row, ModContent.FLUID_PIPE.get());
		set(level, origin, east, by + 1, row, ModContent.FLUID_PIPE.get());
		pipeFace(level, origin, east, by + 1, row, Direction.WEST, PipeFaceMode.INSERT);

		// Water feed, inside: reinforced pipe from the inlet to the column's east side. Any face of a
		// column but the top takes water.
		set(level, origin, east - 2, by + 1, row, ModContent.REINFORCED_FLUID_PIPE.get());
		pipeFace(level, origin, east - 2, by + 1, row, Direction.EAST, PipeFaceMode.EXTRACT);
		set(level, origin, east - 3, by + 1, row, ModContent.REINFORCED_FLUID_PIPE.get());
		pipeFace(level, origin, east - 3, by + 1, row, Direction.WEST, PipeFaceMode.INSERT);

		// Steam exhaust, inside: steam leaves a column through its TOP face only, so the line starts
		// directly above the rack and runs west, a level above the water line, to the west inlet. It is
		// the steam family (MOD-662): a fluid pipe refuses steam and does not even reach for a column's top.
		set(level, origin, bx + mid, by + 2, row, ModContent.REINFORCED_STEAM_PIPE.get());
		pipeFace(level, origin, bx + mid, by + 2, row, Direction.DOWN, PipeFaceMode.EXTRACT);
		set(level, origin, bx + 2, by + 2, row, ModContent.REINFORCED_STEAM_PIPE.get());
		set(level, origin, bx + 1, by + 2, row, ModContent.REINFORCED_STEAM_PIPE.get());
		pipeFace(level, origin, bx + 1, by + 2, row, Direction.WEST, PipeFaceMode.INSERT);

		// Steam exhaust, outside: one ordinary steam pipe out of the inlet and the nozzle on top of it, mouth
		// up into open air. A nozzle facing a block vents nothing, the columns stop boiling and the reactor
		// overheats with its water still in it — the "exhaust blocked" state this loop exists to avoid.
		set(level, origin, bx - 1, by + 2, row, ModContent.STEAM_PIPE.get());
		pipeFace(level, origin, bx - 1, by + 2, row, Direction.EAST, PipeFaceMode.EXTRACT);
		place(level, origin, origin.offset(bx - 1, by + 3, row),
				ModContent.STEAM_NOZZLE.get().defaultBlockState()
						.setValue(SteamNozzleBlock.FACING, Direction.UP));
		pipeFace(level, origin, bx - 1, by + 2, row, Direction.UP, PipeFaceMode.INSERT);

		// MOD-474 — the shielding chest, stocked with the fuel it is there to make safe, parked outside
		// the shell. Its place in the story is exactly here: the only spot on the stand where refined
		// uranium can sit in the open without dosing whoever walks past it.
		//
		// x=26: past the crystal greenhouse (x 16..22), whose shell must stay sealed (MOD-597 — a chest
		// set into its perimeter once punched a hole in it), and west of the cable rows that start at 32.
		set(level, origin, 26, by, bz + 2, ModContent.SHIELDING_CHEST.get());
		fillSlot(level, origin, 26, by, bz + 2, 0, new ItemStack(ModContent.REFINED_URANIUM.get(), 64));
		fillSlot(level, origin, 26, by, bz + 2, 1, new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		// MOD-471 — the scar an accident leaves. Shown as the four decay stages side by side, because
		// the whole point of the intensity is that a player can read how clean a patch is at a glance,
		// and a single sample would show them one colour with nothing to compare it against. Runs south
		// from the chest down the same free column, a cell apart.
		for (int stage = 0; stage <= IrradiatedSoilBlock.MAX_INTENSITY; stage++) {
			place(level, origin, origin.offset(26, by, bz + 4 + 2 * stage),
					ModContent.IRRADIATED_SOIL.get().defaultBlockState()
							.setValue(IrradiatedSoilBlock.INTENSITY, stage));
		}
	}

	/**
	 * Zone <b>lab plaque</b> (MOD-513, row z=34, x 74..83): the seventeen engraved plates the plaque of
	 * an abandoned lab is built from — the ten digits in one row on the floor, the seven broken prefix
	 * letters one level up. Purely decorative. They keep their default FACING=NORTH, so the engraving
	 * faces the cameras like every machine front on the stand.
	 *
	 * <p>Row z=34 sits east of the cable rows, and east of the monitoring wall too: the wall's front (x 66..70)
	 * is left free, because the plaque used to stand right across it and hide the panels a visitor is
	 * meant to read. A plate conducts nothing, so it cannot join two runs into one network the way an
	 * energy block would.
	 */
	private static void buildLabPlaque(ServerLevel level, BlockPos origin) {
		List<Supplier<Block>> digits = List.of(
				ModContent.ENGRAVED_PLATE_0, ModContent.ENGRAVED_PLATE_1, ModContent.ENGRAVED_PLATE_2, ModContent.ENGRAVED_PLATE_3,
				ModContent.ENGRAVED_PLATE_4, ModContent.ENGRAVED_PLATE_5, ModContent.ENGRAVED_PLATE_6,
				ModContent.ENGRAVED_PLATE_7, ModContent.ENGRAVED_PLATE_8, ModContent.ENGRAVED_PLATE_9);
		for (int i = 0; i < digits.size(); i++) {
			set(level, origin, 74 + i, 1, 34, digits.get(i).get());
		}
		List<Supplier<Block>> letters = List.of(
				ModContent.BROKEN_ENGRAVED_PLATE_W, ModContent.BROKEN_ENGRAVED_PLATE_K, ModContent.BROKEN_ENGRAVED_PLATE_P,
				ModContent.BROKEN_ENGRAVED_PLATE_B, ModContent.BROKEN_ENGRAVED_PLATE_D,
				ModContent.BROKEN_ENGRAVED_PLATE_R, ModContent.BROKEN_ENGRAVED_PLATE_M);
		for (int i = 0; i < letters.size(); i++) {
			set(level, origin, 76 + i, 2, 34, letters.get(i).get());
		}
	}

	/**
	 * Zone <b>showcase</b> (MOD-294, wall at the back edge z=50, frames at z=49): EVERY item of the registry in a
	 * glow item frame — block items included since MOD-586, so the wall is the mod's index rather
	 * than a leftovers shelf. The frames are generated by looping {@code BuiltInRegistries.ITEM}, so
	 * the wall refills itself whenever the registry grows — and the item-coverage gametest reddens
	 * the day the registry outgrows the wall. The wall is the very last row of the stand: there is nothing
	 * behind it (MOD-659 — the item-pipe row that used to stand there was invisible from the front).
	 *
	 * <p>Frames are entities: {@link #killLooseEntities} discards them on every rebuild and this
	 * method places them anew after the wall, which keeps rebuild×2 idempotent. Glow frames render
	 * their item readable in the dark without emitting light. The wall itself is floor blocks — a
	 * support that is part of the stand's own block pass, so a frame never hangs on terrain the
	 * stand does not own.
	 */
	private static void buildShowcase(ServerLevel level, BlockPos origin) {
		for (int row = 0; row < SHOWCASE_ROWS; row++) {
			for (int x = 1; x <= SHOWCASE_COLUMNS; x++) {
				set(level, origin, x, SHOWCASE_BASE_Y + row, SHOWCASE_WALL_Z, FLOOR);
			}
		}
		List<Item> items = showcaseItems();
		int placed = 0;
		for (int row = 0; row < SHOWCASE_ROWS && placed < items.size(); row++) {
			for (int x = 1; x <= SHOWCASE_COLUMNS && placed < items.size(); x++) {
				GlowItemFrame frame = new GlowItemFrame(level,
						origin.offset(x, SHOWCASE_BASE_Y + row, SHOWCASE_WALL_Z - 1),
						Direction.NORTH);
				frame.setItem(new ItemStack(items.get(placed++)));
				level.addFreshEntity(frame);
			}
		}
	}

	/** Row z of the item-less blocks: on the floor in front of the farm row, clear of every zone. */
	private static final int ITEMLESS_Z = 44;
	/** First column of the item-less row, and the pitch between two of its blocks (two free cells between). */
	private static final int ITEMLESS_X0 = 2;
	private static final int ITEMLESS_STEP = 3;

	/**
	 * Zone <b>itemless</b>: every block that has no item — fluids, fire, soot, the upper column
	 * sections, the dome, the kok-sagyz plant and root, the capsule cells. They cannot hang in a
	 * frame, so the item wall cannot show them; here each stands on its own, two free cells apart.
	 * Fluids sit in sunken one-block basins (the pattern the oil and diesel pools use), the rest on
	 * the floor. Derived from the registry, so a block added later joins the row by itself.
	 */
	private static void buildItemlessRow(ServerLevel level, BlockPos origin) {
		List<Block> blocks = showcaseBlocks();
		for (int i = 0; i < blocks.size() && itemlessX(i) < WIDTH; i++) {
			int x = itemlessX(i);
			Block block = blocks.get(i);
			if (block instanceof net.minecraft.world.level.block.LiquidBlock) {
				set(level, origin, x, -1, ITEMLESS_Z, FLOOR);
				set(level, origin, x, 0, ITEMLESS_Z, block);
			} else if (block == ModContent.KOK_SAGYZ.get()) {
				// A plant needs its root under it.
				set(level, origin, x, 0, ITEMLESS_Z, ModContent.KOK_SAGYZ_ROOT.get());
				set(level, origin, x, 1, ITEMLESS_Z, block);
			} else {
				set(level, origin, x, 1, ITEMLESS_Z, block);
			}
		}
	}

	/** Column of the {@code index}-th block of the item-less row; the gametest asks the same question. */
	public static int itemlessX(int index) {
		return ITEMLESS_X0 + ITEMLESS_STEP * index;
	}

	/** Row z of the item-less blocks, for the gametest. */
	public static int itemlessZ() {
		return ITEMLESS_Z;
	}

	/** Blocks of the mod with no item; shown by {@link #buildItemlessRow}. */
	public static List<Block> showcaseBlocks() {
		List<Block> blocks = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			if (Industrialization.MOD_ID.equals(id.getNamespace()) && block.asItem() == Items.AIR) {
				blocks.add(block);
			}
		}
		blocks.sort(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
		return blocks;
	}

	/** Origin of the monitoring wall (MOD-480): x 66..70, z 38..39, on the east side. */
	private static final int MONITOR_Z = 38;
	private static final int MONITOR_X = 66;

	/**
	 * Zone <b>monitor</b> (MOD-480): a working monitoring wall you can read from the camera — two
	 * stocked chests, smart wires into a powered core, six panels on it, each watching one item.
	 * Layout along x from {@code MONITOR_X} (all facing north): chest, wire, core, wire, chest; six
	 * panels on the two levels above the wires and the core; a creative energy source behind the core.
	 */
	private static void buildMonitorWall(ServerLevel level, BlockPos origin) {
		set(level, origin, MONITOR_X, 1, MONITOR_Z, ModContent.IRON_CHEST.get());
		fillSlot(level, origin, MONITOR_X, 1, MONITOR_Z, 0, new ItemStack(Items.DIAMOND, 37));
		fillSlot(level, origin, MONITOR_X, 1, MONITOR_Z, 1, new ItemStack(Items.IRON_INGOT, 64));
		fillSlot(level, origin, MONITOR_X, 1, MONITOR_Z, 2, new ItemStack(Items.IRON_INGOT, 64));
		fillSlot(level, origin, MONITOR_X, 1, MONITOR_Z, 3, new ItemStack(Items.EMERALD, 5));
		set(level, origin, MONITOR_X + 4, 1, MONITOR_Z, ModContent.IRON_CHEST.get());
		fillSlot(level, origin, MONITOR_X + 4, 1, MONITOR_Z, 0, new ItemStack(Items.GOLD_INGOT, 48));
		fillSlot(level, origin, MONITOR_X + 4, 1, MONITOR_Z, 1, new ItemStack(Items.REDSTONE, 64));
		fillSlot(level, origin, MONITOR_X + 4, 1, MONITOR_Z, 2, new ItemStack(Items.REDSTONE, 64));
		fillSlot(level, origin, MONITOR_X + 4, 1, MONITOR_Z, 3, new ItemStack(Items.REDSTONE, 64));
		set(level, origin, MONITOR_X + 1, 1, MONITOR_Z, ModContent.SMART_WIRE.get());
		set(level, origin, MONITOR_X + 3, 1, MONITOR_Z, ModContent.SMART_WIRE.get());
		set(level, origin, MONITOR_X + 2, 1, MONITOR_Z, ModContent.MONITOR_CORE.get());
		if (level.getBlockEntity(origin.offset(MONITOR_X + 2, 1, MONITOR_Z))
				instanceof dev.alaindustrial.block.entity.MonitorCoreBlockEntity core) {
			core.getEnergyStorage().setAmountUntracked(core.getEnergyStorage().getCapacity());
			// A bare rack tracks nothing at all, so the stand fits the card that pays for the six
			// panels below — without it every panel would show the yellow cross and the zone would
			// demonstrate the failure mode instead of the feature.
			core.insertCard(new ItemStack(ModContent.CAPACITY_CARD.get()));
			core.setChangedQuietly();
			core.wake();
		} else {
			missed(level, origin, MONITOR_X + 2, 1, MONITOR_Z, "has no monitor core to charge");
		}
		// Behind the core: a creative energy source, not a solar panel — the core keeps its charge for as
		// long as the stand stands, at night and in the rain too, instead of for as long as the sun.
		set(level, origin, MONITOR_X + 2, 1, MONITOR_Z + 1, ModContent.CREATIVE_ENERGY_SOURCE.get());
		Item[][] filters = {
				{Items.DIAMOND, Items.IRON_INGOT, Items.EMERALD},
				{Items.GOLD_INGOT, Items.REDSTONE, Items.COAL},
		};
		for (int row = 0; row < filters.length; row++) {
			for (int col = 0; col < filters[row].length; col++) {
				int x = MONITOR_X + 1 + col;
				int y = 2 + row;
				set(level, origin, x, y, MONITOR_Z, ModContent.MONITOR_PANEL.get());
				if (level.getBlockEntity(origin.offset(x, y, MONITOR_Z))
						instanceof dev.alaindustrial.block.entity.MonitorPanelBlockEntity panel) {
					panel.setFilter(new ItemStack(filters[row][col]));
				} else {
					missed(level, origin, x, y, MONITOR_Z, "has no monitor panel to set a filter on");
				}
			}
		}
	}

	/**
	 * EVERY item registered in the {@code alaindustrial} namespace, sorted by id — the exact
	 * population the showcase wall displays and its gametest asserts, in the exact order the wall
	 * fills. Public for the gametest: one enumeration, no drift between builder and check.
	 *
	 * <p><b>Block items are included since MOD-586.</b> The wall used to skip them on the argument
	 * that a block is already SOMEWHERE on the stand as a placed block — true, and useless to a
	 * player looking for one: the greenhouse glass, the kok sagyz and ninety-seven others were
	 * scattered across nine zones with no index, so "is this in the mod at all" had no answer short
	 * of walking the whole stand. The wall is that index, and an index with a hundred holes in it is
	 * the complaint the owner raised the first time they read it in game.
	 *
	 * <p>Blocks with no item at all cannot appear here (they stand in the item-less row instead, see {@link #buildItemlessRow}): the five fluids
	 * ({@code oil}, {@code diesel}, {@code fuel_oil}, {@code biofuel}, {@code nutrient_solution} —
	 * their buckets do appear), the two upper distillation-column sections, the incubator dome and
	 * the kok sagyz root block. None is a block a player ever holds, and each is placed on the stand
	 * where its own machine stands.
	 */
	public static List<Item> showcaseItems() {
		List<Item> items = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (Industrialization.MOD_ID.equals(id.getNamespace())) {
				items.add(BuiltInRegistries.ITEM.getValue(id));
			}
		}
		items.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
		return items;
	}

	// --- the write ledger: one cell, one owner (MOD-597) ---

	/**
	 * Every cell the zone pass has written, and what wrote it. Keyed by the cell's coordinates
	 * RELATIVE to the origin, so the ledger reads the same in the command's world and in the
	 * gametest rig.
	 *
	 * <p>Static rather than passed down, and that is safe for exactly one reason: {@link #buildAll}
	 * is synchronous and runs on the server thread — a whole build completes inside one tick, so two
	 * gametests ticking the same server can never interleave their builds. It is NOT safe to call the
	 * zone builders directly from anywhere else.
	 */
	private static final Map<BlockPos, String> WRITTEN = new LinkedHashMap<>();

	/** What went wrong in the last build, in the order it happened — empty when the stand is healthy. */
	private static final List<String> PROBLEMS = new ArrayList<>();

	/** Whether {@link #place} is currently recording: the zone pass yes, the base passes no. */
	private static boolean recording;

	/**
	 * What the last {@link #buildAll} overwrote — one line per cell written twice by the zone pass.
	 *
	 * <p>Two kinds of problem land here: a cell written twice, and a stocking call that found no
	 * block entity to stock. They are one list because they are one incident seen from both ends — the
	 * block that lost its cell, and the inventory that was then filled into nothing.
	 *
	 * <p>This exists because the failure it reports is <b>silent</b>: a second write on a cell drops
	 * the first block without a word, the block-coverage scan still ticks its box (the lost block is
	 * almost always somewhere else on the stand too), and the stocking helpers below fall through
	 * their {@code instanceof} in silence. It has happened at least five times — MOD-292, MOD-275,
	 * MOD-145, MOD-599, MOD-597 — and every time the answer was another comment telling the next
	 * author to check the neighbouring cells by hand. This is that comment turned into a check.
	 *
	 * <p><b>Only what goes through {@link #place}.</b> Blocks written by a vanilla or mod helper the
	 * stand calls ({@code DoublePlantBlock.placeAt}, {@code DistillationColumnBlock.placeTower},
	 * {@code ConcentratorStructure.tryAssemble}) place their own cells and are invisible here; so is
	 * the floor pass, which legitimately writes every cell of the stand before the zones carve it.
	 */
	public static List<String> lastBuildProblems() {
		return List.copyOf(PROBLEMS);
	}

	/** Start recording: the base passes are done, the zones are about to write. */
	private static void openLedger() {
		WRITTEN.clear();
		PROBLEMS.clear();
		recording = true;
	}

	/** Stop recording and shout about anything the zones overwrote. */
	private static void closeLedger() {
		recording = false;
		WRITTEN.clear();
		for (String problem : PROBLEMS) {
			Industrialization.LOGGER.warn("demo stand: {}", problem);
		}
	}

	/**
	 * The one way the stand writes a block. Records the cell first, so a second write on it is
	 * reported rather than swallowed, then places it exactly as before.
	 */
	private static void place(ServerLevel level, BlockPos origin, BlockPos pos, BlockState state) {
		write(level, origin, pos, state, false);
	}

	/**
	 * Replace a cell this same zone has just filled — a fitting punched into a shell it built itself:
	 * the reactor room lays five walls of casing and then sets the glass, the controller and the door
	 * into them, and the greenhouse does the same with its glass.
	 *
	 * <p>It is a separate call rather than an entry in an allow-list somewhere because the intent
	 * belongs at the site that has it, and because the claim is <b>checked</b>: a refit of a cell
	 * nobody built is reported exactly like a collision. An author who reaches for this to silence the
	 * ledger on a cell another zone owns gets the same red they were trying to avoid.
	 */
	private static void refit(ServerLevel level, BlockPos origin, BlockPos pos, BlockState state) {
		write(level, origin, pos, state, true);
	}

	/** {@code refit}'s int-coordinate twin, for the zones that write in local coordinates. */
	private static void refit(ServerLevel level, BlockPos origin, int x, int y, int z, Block block) {
		refit(level, origin, origin.offset(x, y, z), block.defaultBlockState());
	}

	private static void write(ServerLevel level, BlockPos origin, BlockPos pos, BlockState state,
			boolean expectedToReplace) {
		if (recording) {
			BlockPos local = pos.subtract(origin);
			String owner = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
			String previous = WRITTEN.put(local, owner);
			String cell = "cell (" + local.getX() + ", " + local.getY() + ", " + local.getZ() + ")";
			if (previous != null && !expectedToReplace) {
				PROBLEMS.add(cell + " written twice — " + previous + " overwritten by " + owner);
			} else if (previous == null && expectedToReplace) {
				PROBLEMS.add(cell + " is a refit of a cell no zone built — " + owner
						+ " expected to replace something and replaced nothing");
			}
		}
		level.setBlockAndUpdate(pos, state);
	}

	private static void set(ServerLevel level, BlockPos origin, int x, int y, int z, Block block) {
		place(level, origin, origin.offset(x, y, z), block.defaultBlockState());
	}

	/** Place a processing machine with a full EU buffer and an input stack — it starts working immediately. */
	private static void placeWorkingMachine(ServerLevel level, BlockPos origin, int x, int z,
			Block machine, ItemStack input) {
		set(level, origin, x, 1, z, machine);
		chargeBuffer(level, origin, x, 1, z);
		fillSlot(level, origin, x, 1, z, 0, input);
	}

	/**
	 * Stock one slot of the block at a cell — and say so when there is nothing there to stock.
	 *
	 * <p>The silent {@code if (instanceof)} this used to be is the other half of every overwrite
	 * incident on this stand: the block that owned the cell is gone, its inventory is filled into
	 * nothing, and the build finishes green. Now the miss is recorded next to the overwrite that
	 * caused it (MOD-597).
	 */
	private static void fillSlot(ServerLevel level, BlockPos origin, int x, int y, int z, int slot, ItemStack stack) {
		if (level.getBlockEntity(origin.offset(x, y, z)) instanceof Container container) {
			container.setItem(slot, stack);
			return;
		}
		missed(level, origin, x, y, z, "has no container to stock");
	}

	/** Fill a machine's EU buffer — and say so when the cell holds no machine (see {@link #fillSlot}). */
	private static void chargeBuffer(ServerLevel level, BlockPos origin, int x, int y, int z) {
		if (level.getBlockEntity(origin.offset(x, y, z)) instanceof MachineBlockEntity machine) {
			machine.getEnergyStorage().setAmountUntracked(machine.getEnergyStorage().getCapacity());
			machine.setChangedQuietly();
			machine.wake();
			return;
		}
		missed(level, origin, x, y, z, "has no machine to charge");
	}

	/** Wrench one face of a fluid pipe — and say so when the cell holds no pipe (see {@link #fillSlot}). */
	private static void pipeFace(ServerLevel level, BlockPos origin, int x, int y, int z, Direction face,
			PipeFaceMode mode) {
		if (level.getBlockEntity(origin.offset(x, y, z)) instanceof FluidPipeBlockEntity pipe) {
			pipe.setFaceMode(face, mode);
			return;
		}
		missed(level, origin, x, y, z, "has no fluid pipe to wrench");
	}

	/** Record a stocking call that found nothing, naming the block that actually stands there. */
	private static void missed(ServerLevel level, BlockPos origin, int x, int y, int z, String what) {
		if (!recording) {
			return;
		}
		String actual = BuiltInRegistries.BLOCK.getKey(
				level.getBlockState(origin.offset(x, y, z)).getBlock()).toString();
		PROBLEMS.add("cell (" + x + ", " + y + ", " + z + ") " + what + " — it holds " + actual);
	}
}
