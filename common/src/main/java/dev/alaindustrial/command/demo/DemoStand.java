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
import dev.alaindustrial.block.entity.WorkstationBlockEntity;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

	/** Stand footprint (x). */
	public static final int WIDTH = 42;
	/**
	 * Stand footprint (z). Grows whenever a zone gains a row — the cable zone takes one row per grade,
	 * so insulated gold (MOD-268) pushed the item-pipe row to z=26 and this bound to 27. The
	 * {@code demo_stand_area} GameTest structure is 28 deep with a 1-block origin margin, so 27 is the
	 * ceiling: a row past it is built but falls outside the coverage scan, which reads as "block missing
	 * from the stand" rather than as an out-of-bounds error.
	 */
	public static final int DEPTH = 27;
	/** Blocks above the floor layer that belong to the stand (wind-mill pillars are tallest). */
	public static final int HEIGHT = 9;

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

	/** Showcase wall (MOD-294): the back-edge row z, columns x=1..40, rows y=1..9 (360 slots). */
	private static final int SHOWCASE_WALL_Z = 25;
	private static final int SHOWCASE_COLUMNS = 40;
	/** The wall's bottom row. y=1 is the first cell above the floor, so the wall uses the full height. */
	private static final int SHOWCASE_BASE_Y = 1;
	/**
	 * Nine rows — every cell the stand has above its floor ({@code y = 1..HEIGHT}).
	 *
	 * <p>The wall shows EVERY item of the mod since MOD-586, block items included, so it needs 289
	 * slots where the old non-block-only wall needed 190. Capacity is a deadline, not a preference:
	 * the wall fills from the registry, and the day the registry outgrows it the coverage gametest
	 * reddens in whatever task happened to add the item. Nine rows of forty give 360 for 289.
	 */
	private static final int SHOWCASE_ROWS = 9;

	/** A named camera position for {@code /ala demo tp}, relative to the stand origin. */
	public record TpPoint(String name, double dx, double dy, double dz, float yaw, float pitch, boolean night) {
	}

	/**
	 * Camera points, one per zone plus an overview. Yaw 0 looks south (+z) — every zone is laid
	 * out with its blocks south of the camera and machine fronts (FACING north) toward it.
	 * {@code night} points additionally switch the world clock to midnight (moonlit panel).
	 */
	public static final List<TpPoint> TP_POINTS = List.of(
			new TpPoint("overview", 21.0, 12.0, -6.0, 0.0f, 35.0f, false),
			new TpPoint("tiers", 12.0, 3.0, -3.0, 0.0f, 20.0f, false),
			new TpPoint("generators", 10.0, 4.0, 0.0, 0.0f, 20.0f, false),
			new TpPoint("windmills", 25.0, 9.0, 0.0, 0.0f, 10.0f, false),
			new TpPoint("machines", 7.0, 3.0, 6.5, 0.0f, 15.0f, false),
			new TpPoint("loss", 20.0, 4.0, 2.0, 0.0f, 25.0f, false),
			new TpPoint("cables", 20.0, 6.0, 11.0, 0.0f, 30.0f, false),
			new TpPoint("farms", 20.0, 4.0, 18.0, 0.0f, 20.0f, false),
			new TpPoint("ores", 36.0, 2.0, 0.5, 0.0f, 5.0f, false),
			new TpPoint("misc", 33.0, 3.0, 6.5, 0.0f, 15.0f, false),
			new TpPoint("reactor", 4.5, 4.0, 11.0, 0.0f, 10.0f, false),
			new TpPoint("showcase", 21.0, 5.5, 17.0, 0.0f, 15.0f, false),
			new TpPoint("night", 10.0, 4.0, 0.0, 0.0f, 20.0f, true));

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
			buildGeneratorRow(level, origin);
			buildWindMills(level, origin);
			buildMachines(level, origin);
			buildCableRuns(level, origin);
			buildOreWall(level, origin);
			buildMisc(level, origin);
			buildLossLane(level, origin);
			buildFarms(level, origin);
			buildReactorZone(level, origin);
			buildCrystalGreenhouse(level, origin);
			buildReactorRoom(level, origin);
			buildShowcase(level, origin);
		} finally {
			closeLedger();
		}
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
	}

	/** Air out everything above the floor layer (also removes sunken water/lava cells' contents). */
	private static void clearAbove(ServerLevel level, BlockPos origin) {
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				for (int y = 1; y <= HEIGHT; y++) {
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

	/**
	 * Zone <b>generators</b> (row z=4, battery boxes behind at z=5): every generator runs live —
	 * coal in the fuel generator, a lava bucket in the geothermal, open sky for the solars, and a
	 * water mill sunk into the floor between two contained water cells. Each generator delivers
	 * into its battery box by the cable-less direct push (the box sits on an OUT face).
	 */
	private static void buildGeneratorRow(ServerLevel level, BlockPos origin) {
		// MOD-479 — the creative source, at the head of the generator row: the instrument you reach for
		// when the generator behind it is the thing under test.
		//
		// x=0 rather than the obvious x=2, and this is worth stating because the obvious choice failed:
		// z=3 used to be the label row for this zone and is kept clear of machines: the row reads as
		// the front of the z=4 line, and a block dropped here would sit in front of what it labels.
		// x=0 sits before the row proper and outside the water channel (x=15..19).
		set(level, origin, 0, 1, 3, ModContent.CREATIVE_ENERGY_SOURCE.get());

		set(level, origin, 2, 1, 4, ModContent.GENERATOR.get());
		fillSlot(level, origin, 2, 1, 4, 0, new ItemStack(Items.COAL, 64));
		set(level, origin, 2, 1, 5, ModContent.BATTERY_BOX.get());

		set(level, origin, 5, 1, 4, ModContent.GEOTHERMAL_GENERATOR.get());
		fillSlot(level, origin, 5, 1, 4, 0, new ItemStack(Items.LAVA_BUCKET));
		set(level, origin, 5, 1, 5, ModContent.BATTERY_BOX.get());

		// x=6, not 8 (MOD-597): with the run starting at 8 the fourth panel landed at x=17, which is
		// the middle of the water channel below — and the channel's dam puts a smooth-stone block at
		// (17, 2, 4), directly ON TOP of it. A roofed solar panel produces nothing, and nothing was
		// ever going to say so: the cell is not a collision (the dam is one level up), the coverage
		// scan finds the panel exactly where it expects it, and the liveness sweep deliberately does
		// not assert solar output. Starting at 6 ends the run at 15, clear of the dam's 16..18.
		int x = 6;
		for (Block solar : new Block[] {ModContent.SOLAR_PANEL.get(),
				ModContent.DAYLIGHT_SOLAR_PANEL.get(), ModContent.MOONLIT_SOLAR_PANEL.get(),
				ModContent.RADIANT_SOLAR_PANEL.get()}) {
			set(level, origin, x, 1, 4, solar);
			set(level, origin, x, 1, 5, ModContent.BATTERY_BOX.get());
			x += 3;
		}

		// MOD-603: the concentrator grown out into its two-by-two-by-two form — the first clear pair of
		// columns east of the water channel, so it reads as the next item of the generator row.
		// Built the way a player builds it — a grown panel plus seven loose sections — and then handed
		// to the real assembler, so the stand cannot show a structure the game could not produce.
		//
		// x=20 is written out rather than taken from the loop counter above (MOD-597): riding the
		// counter put the structure wherever the last panel happened to leave it, and the wind-mill row
		// below — which starts from a literal — drove a smooth-stone pillar straight through its core
		// and its top cell. Two zones, two literals, one cell, and the survivor still ticked every box
		// the coverage scan has.
		BlockPos structureCore = origin.offset(20, 1, 4);
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

		// Water mill driven by a real CURRENT (MOD-188): only FLOWING water turns the wheel — a still
		// source powers nothing. The mill faces NORTH, so its wheel hangs in the whole z=3 plane:
		// x 16..18 by y -1..1. Two rules shape this build:
		//   MOD-355 — every one of those nine cells must be non-solid or the wheel clips through it and
		//             stalls, so the channel is dug a level deeper and walled OUTSIDE the plane;
		//   MOD-352 — the mill is driven by the four cells around the WHEEL (above, below, both sides),
		//             so three sources at y=2 fall through the plane and wet all four → a full 4 EU/t.
		// Water is canBeReplaced(), so a wet wheel plane is a clear wheel plane.
		for (int dx = 16; dx <= 18; dx++) {
			set(level, origin, dx, -2, 3, FLOOR); // bed, one level BELOW the plane so the plane stays clear
			for (int dy = -1; dy <= 1; dy++) {
				set(level, origin, dx, dy, 3, Blocks.AIR);
			}
		}
		set(level, origin, 17, -1, 4, FLOOR); // support under the mill itself (outside the wheel plane)
		// Walls that hold the water in, all outside the wheel plane: beside it (x=15/19) and in front (z=2).
		for (int dy = -2; dy <= 2; dy++) {
			set(level, origin, 15, dy, 3, FLOOR);
			set(level, origin, 19, dy, 3, FLOOR);
			for (int dx = 15; dx <= 19; dx++) {
				set(level, origin, dx, dy, 2, FLOOR);
			}
		}
		// Cap the feed at y=2 (above the plane) so the sources only ever fall downward.
		for (int dx = 16; dx <= 18; dx++) {
			set(level, origin, dx, 2, 4, FLOOR);
			set(level, origin, dx, 2, 3, Blocks.WATER);
		}
		// The mill and its battery box behind it (south = the back/OUT face).
		set(level, origin, 17, 0, 4, ModContent.WATER_MILL.get());
		fillSlot(level, origin, 17, 0, 4, 0, new ItemStack(ModContent.WATER_MILL_WHEEL.get()));
		set(level, origin, 17, -1, 5, FLOOR);
		set(level, origin, 17, 0, 5, ModContent.BATTERY_BOX.get());
	}

	/**
	 * Zone <b>windmills</b>: the three wind mills on pillars, a battery box sitting directly beneath
	 * each head as a decorative plinth. The box does <b>not</b> receive the mill's EU: a wind mill
	 * emits only from its back <i>horizontal</i> face (opposite FACING, see
	 * {@code WindMillBlockEntity#energyRoleForFace}), never downward, and the box's top face is inert
	 * anyway (single-axis IO, MOD-006). Spacing 5 keeps the mills out of each other's interference
	 * radius. Their EU/t depends on build height vs sea level, so on a low superflat they are
	 * intentionally decorative (see MOD-058 task log) — the plinth box is purely scenic (MOD-103).
	 */
	private static void buildWindMills(ServerLevel level, BlockPos origin) {
		Block[] mills = {ModContent.WIND_MILL.get(),
				ModContent.HIGH_ALTITUDE_WIND_MILL.get(), ModContent.STORM_WIND_MILL.get()};
		// x=23, not 20 (MOD-597): the concentrator's 2x2x2 owns x 20..21 on this row, and the first
		// pillar was being driven straight through it.
		int x = 23;
		for (Block mill : mills) {
			for (int y = 1; y <= 4; y++) {
				set(level, origin, x, y, 4, FLOOR);
			}
			set(level, origin, x, 5, 4, ModContent.BATTERY_BOX.get());
			set(level, origin, x, 6, 4, mill);
			x += 5;
		}
		// MOD-386: the lightning rod shares this weather row — same mast-on-a-pillar shape, and a
		// conductor tip pre-installed so the stand shows the configured block rather than an inert one.
		//
		// Its column is a literal instead of the loop counter (MOD-597): carried on, the counter put the
		// mast at x=35, which is the middle of the ore wall two zones later — and since the ore wall is
		// built after the mills, the pillar's bottom two blocks were quietly replaced by silver ore. The
		// wall owns x 34..39, so the weather row ends past it.
		x = 41;
		for (int y = 1; y <= 4; y++) {
			set(level, origin, x, y, 4, FLOOR);
		}
		set(level, origin, x, 5, 4, ModContent.BATTERY_BOX.get());
		set(level, origin, x, 6, 4, ModContent.LIGHTNING_ROD_GENERATOR.get());
		fillSlot(level, origin, x, 6, 4, LightningRodGeneratorBlockEntity.TIP_SLOT,
				new ItemStack(ModContent.LIGHTNING_ROD_CONDUCTOR_TIP.get()));
	}

	/**
	 * Zone <b>machines</b> (row z=10): processing machines with full buffers and guaranteed
	 * inputs, so they are visibly working (lit + progress) the moment the stand is built.
	 */
	private static void buildMachines(ServerLevel level, BlockPos origin) {
		placeWorkingMachine(level, origin, 2, 10, ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 64));
		placeWorkingMachine(level, origin, 5, 10, ModContent.ELECTRIC_FURNACE.get(), new ItemStack(Items.RAW_COPPER, 64));
		placeWorkingMachine(level, origin, 8, 10, ModContent.COMPRESSOR.get(),
				new ItemStack(ModContent.IRON_DUST.get(), 64));
		placeWorkingMachine(level, origin, 11, 10, ModContent.EXTRACTOR.get(), new ItemStack(Items.GRAVEL, 64));
		// Canning Machine (MOD-383): placeWorkingMachine does not fit — it fills slot 0 only, and this
		// machine needs both a food stack and a stack of empty cans before it will run at all. Set on
		// the second row because the first is full.
		set(level, origin, 11, 1, 12, ModContent.CANNING_MACHINE.get());
		chargeBuffer(level, origin, 11, 1, 12);
		fillSlot(level, origin, 11, 1, 12, CanningMachineBlockEntity.FOOD_SLOT,
				new ItemStack(Items.COOKED_BEEF, 64));
		fillSlot(level, origin, 11, 1, 12, CanningMachineBlockEntity.CAN_SLOT,
				new ItemStack(ModContent.EMPTY_CAN.get(), 64));
		// Component Repair Bench (MOD-384): placeWorkingMachine does not fit either — its target slot
		// needs a component that is actually WORN, and a pristine rotor would leave the bench idle on the
		// stand. So the rotor is damaged by hand first, then paired with its T1 material (an iron plate).
		set(level, origin, 14, 1, 12, ModContent.COMPONENT_REPAIR_BENCH.get());
		chargeBuffer(level, origin, 14, 1, 12);
		ItemStack wornRotor = new ItemStack(ModContent.WINDMILL_ROTOR.get());
		wornRotor.setDamageValue(wornRotor.getMaxDamage() / 2);
		fillSlot(level, origin, 14, 1, 12, ComponentRepairBenchBlockEntity.TARGET_SLOT, wornRotor);
		fillSlot(level, origin, 14, 1, 12, ComponentRepairBenchBlockEntity.MATERIAL_SLOT,
				new ItemStack(ModContent.IRON_PLATE.get(), 64));
		// Sawmill (MOD-150): pre-charged + a stack of logs → visibly sawing (default PLANKS mode).
		placeWorkingMachine(level, origin, 17, 10, ModContent.SAWMILL.get(), new ItemStack(Items.OAK_LOG, 64));
		// Incubator (MOD-118): the 1x2 multiblock. Glass goes on top so the base assembles it into the
		// dome; the slots are filled by hand rather than via placeWorkingMachine because the chip picks
		// the mode and the uranium is a separate fuel slot.
		set(level, origin, 20, 1, 10, ModContent.INCUBATOR.get());
		set(level, origin, 20, 2, 10, ModContent.INCUBATOR_DOME.get());
		chargeBuffer(level, origin, 20, 1, 10);
		fillSlot(level, origin, 20, 1, 10, IncubatorBlockEntity.CHIP_SLOT,
				new ItemStack(ModContent.MUTATION_CHIP_DUPLICATE.get()));
		fillSlot(level, origin, 20, 1, 10, IncubatorBlockEntity.FUEL_SLOT,
				new ItemStack(ModContent.URANIUM_INGOT.get(), 16));
		fillSlot(level, origin, 20, 1, 10, IncubatorBlockEntity.INPUT_SLOT,
				new ItemStack(Items.DIAMOND, 64));
		// Polymerizer (MOD-019): the fluid-fed machine. placeWorkingMachine does not fit it — its slot 0
		// takes a CONTAINER, not the feedstock, and one bucket would give the stand a single run before
		// the machine went idle. The tank is stocked directly instead, so it runs for ten operations.
		set(level, origin, 23, 1, 10, ModContent.POLYMERIZER.get());
		chargeBuffer(level, origin, 23, 1, 10);
		if (level.getBlockEntity(origin.offset(23, 1, 10)) instanceof PolymerizerBlockEntity polymerizer) {
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
		set(level, origin, 26, 1, 10, ModContent.ELECTRIC_HEATER.get());
		chargeBuffer(level, origin, 26, 1, 10);
		set(level, origin, 26, 2, 10, ModContent.VULCANIZER.get());
		chargeBuffer(level, origin, 26, 2, 10);
		fillSlot(level, origin, 26, 2, 10, VulcanizerBlockEntity.RAW_RUBBER_SLOT,
				new ItemStack(ModContent.RAW_RUBBER.get(), 64));
		fillSlot(level, origin, 26, 2, 10, VulcanizerBlockEntity.SULFUR_SLOT,
				new ItemStack(ModContent.SULFUR_DUST.get(), 64));
		// Thermal Centrifuge (MOD-424): the same heater-underneath pair as the vulcanizer, on the second
		// machines row because z=10 is full from x=2 to x=41. Three things must be true before this machine
		// turns at all, so all three are set up rather than only the two the other stands need: the heater
		// below, a stack of uranium dust, and — the one no other machine on the stand wants — a held
		// redstone signal. A redstone BLOCK rather than a lever: the lever's default state is unpowered and
		// wall-mounted, so `set` (which places defaultBlockState and never calls setPlacedBy) would leave a
		// dead switch and an idle centrifuge. It is placed LAST of the three so its neighbour update reaches
		// an already-built machine. Like the vulcanizer's, the heater starts cold on purpose: the stand shows
		// the rotor spinning up while the thermometer climbs, which is the mechanic worth watching.
		set(level, origin, 17, 1, 12, ModContent.ELECTRIC_HEATER.get());
		chargeBuffer(level, origin, 17, 1, 12);
		set(level, origin, 17, 2, 12, ModContent.THERMAL_CENTRIFUGE.get());
		chargeBuffer(level, origin, 17, 2, 12);
		fillSlot(level, origin, 17, 2, 12, ThermalCentrifugeBlockEntity.INPUT_SLOT,
				new ItemStack(ModContent.URANIUM_DUST.get(), 64));
		set(level, origin, 18, 2, 12, Blocks.REDSTONE_BLOCK);
		// Galvanic Bath (MOD-127): like the polymerizer its feedstock is a fluid, so the tank is stocked
		// directly rather than through a bucket — one bucket would buy four operations and then the stand
		// would show an idle machine. Both item inputs are filled so it plates continuously.
		set(level, origin, 29, 1, 10, ModContent.GALVANIC_BATH.get());
		chargeBuffer(level, origin, 29, 1, 10);
		fillSlot(level, origin, 29, 1, 10, GalvanicBathBlockEntity.FIBER_SLOT,
				new ItemStack(Items.STRING, 64));
		fillSlot(level, origin, 29, 1, 10, GalvanicBathBlockEntity.SILVER_SLOT,
				new ItemStack(ModContent.SILVER_DUST.get(), 64));
		if (level.getBlockEntity(origin.offset(29, 1, 10)) instanceof GalvanicBathBlockEntity bath) {
			bath.fluidTank.fluid = FluidHolder.of(net.minecraft.world.level.material.Fluids.WATER);
			bath.fluidTank.amount = GalvanicBathBlockEntity.TANK_CAPACITY;
			bath.setChangedQuietly();
			bath.wake();
		}
		// Fermenter (MOD-146): the head of the organic chain. Stocked like the bath — the tank filled
		// directly, the input slot loaded — so the stand shows it brewing rather than waiting.
		// Row z=10 is full from x=2 to x=41, so this sits in the second machines row like the
		// assembler does; a second `set` on one cell silently drops the first machine's inventory.
		set(level, origin, 20, 1, 12, ModContent.FERMENTER.get());
		chargeBuffer(level, origin, 20, 1, 12);
		fillSlot(level, origin, 20, 1, 12, FermenterBlockEntity.ORGANIC_SLOT,
				new ItemStack(Items.POISONOUS_POTATO, 64));
		if (level.getBlockEntity(origin.offset(20, 1, 12)) instanceof FermenterBlockEntity fermenter) {
			fermenter.waterTank.fluid = FluidHolder.of(net.minecraft.world.level.material.Fluids.WATER);
			fermenter.waterTank.amount = FermenterBlockEntity.TANK_CAPACITY;
			fermenter.setChangedQuietly();
			fermenter.wake();
		}
		// Sprinkler (MOD-525): the tail of the same chain, and the one block here that takes no cable —
		// so it is shown charged with solution instead, beside the farm plot rather than in the machine
		// row. Its head only turns when the tank can pay, which is the whole readout it has.
		set(level, origin, 39, 1, 12, ModContent.SPRINKLER.get());
		if (level.getBlockEntity(origin.offset(39, 1, 12)) instanceof SprinklerBlockEntity sprinkler) {
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
		// x=26, not 25 (MOD-597): x=25 belongs to the carbon ceramic block, which is written LATER in
		// this same method and therefore won its half of the argument in silence — the station's lower
		// casing was replaced, the upper half lost its partner and degraded back to a casing, and the
		// stand showed a loose casing floating over a ceramic block. Nothing went red: the coverage scan
		// still found a workstation casing, and the stocking block below simply fell through its
		// instanceof. The ledger in #lastBuildProblems is what now says this out loud.
		BlockState workstationCasing = ModContent.WORKSTATION.get().defaultBlockState();
		place(level, origin, origin.offset(26, 1, 12), workstationCasing);
		place(level, origin, origin.offset(26, 2, 12), workstationCasing);
		WorkstationBlock.tryAssemble(level, origin.offset(26, 2, 12));
		if (level.getBlockEntity(origin.offset(26, 1, 12)) instanceof WorkstationBlockEntity station) {
			station.getEnergyStorage().setAmountUntracked(station.getEnergyStorage().getCapacity());
			station.setChangedQuietly();
		}
		// Upgrade Table (MOD-482): the same 1x2 pattern, shown assembled and powered directly beside
		// the workstation it is built like. Both are 1x2 and assemble VERTICALLY, so standing them
		// shoulder to shoulder at 26 and 27 costs nothing — neither scan looks sideways.
		BlockState upgradeTableCasing = ModContent.UPGRADE_TABLE.get().defaultBlockState();
		place(level, origin, origin.offset(27, 1, 12), upgradeTableCasing);
		place(level, origin, origin.offset(27, 2, 12), upgradeTableCasing);
		UpgradeTableBlock.tryAssemble(level, origin.offset(27, 2, 12));
		if (level.getBlockEntity(origin.offset(27, 1, 12)) instanceof UpgradeTableBlockEntity table) {
			table.getEnergyStorage().setAmountUntracked(table.getEnergyStorage().getCapacity());
			table.setChangedQuietly();
		}
		// Assembler (MOD-275): the first MV machine. Row z=10 is full from x=2 to x=41 (machines then
		// the misc zone), so it opens a second machines row one block further south, in front of the
		// macerator. Charged but idle by design — this slice registers the block and its inventory; the
		// crafting cycle (and with it a blueprint to stock it with) lands in a later slice.
		set(level, origin, 2, 1, 12, ModContent.ASSEMBLER.get());
		chargeBuffer(level, origin, 2, 1, 12);
		// Recycler (MOD-145): stocked so the stand shows the thing that makes it different — a batch in
		// progress. It gets blades (without them the machine is inert), cobblestone to chew, and a slag
		// block set beside it so the building use of the output is visible in the same glance.
		// x=23 and 24, NOT x=8: the distillation tower is raised at x=8 later in this same method and
		// would overwrite the machine, spilling its stocked slots as item drops (which is exactly what
		// the stand's idempotency test caught).
		set(level, origin, 23, 1, 12, ModContent.RECYCLER.get());
		chargeBuffer(level, origin, 23, 1, 12);
		fillSlot(level, origin, 23, 1, 12, RecyclerBlockEntity.BLADE_SLOT,
				new ItemStack(ModContent.RECYCLER_BLADES_TEMPERED.get()));
		fillSlot(level, origin, 23, 1, 12, RecyclerBlockEntity.INPUT_SLOT,
				new ItemStack(Items.COBBLESTONE, 64));
		set(level, origin, 24, 1, 12, ModContent.SLAG_BLOCK.get());
		// MOD-590 — carbon ceramic beside the slag block it is fired with: the two blocks the Recycler
		// feeds, standing next to each other. 23-24-25 is that story (machine, slag, ceramic) and it is
		// why the workstation moved to 26 rather than this block moving away from the slag.
		set(level, origin, 25, 1, 12, ModContent.CARBON_CERAMIC.get());
		// Iron furnace (MOD-115): fuel-burning, not EU — so it is loaded with input + coal instead of a
		// pre-charged buffer, and lights itself on the first tick like a vanilla furnace.
		set(level, origin, 14, 1, 10, ModContent.IRON_FURNACE.get());
		fillSlot(level, origin, 14, 1, 10, 0, new ItemStack(Items.RAW_IRON, 64));
		fillSlot(level, origin, 14, 1, 10, 1, new ItemStack(Items.COAL, 64));
		// Alloy smelter (MOD-064): the second machines row, next to the assembler. Stocked for bronze —
		// and deliberately with the tin in the LAST input rather than the second, so the stand shows the
		// thing that makes this machine different: the components may sit in any slot in any order.
		// The third slot is left empty on purpose; filling it would block the two-component recipe.
		set(level, origin, 5, 1, 12, ModContent.ALLOY_SMELTER.get());
		chargeBuffer(level, origin, 5, 1, 12);
		fillSlot(level, origin, 5, 1, 12, AlloySmelterBlockEntity.INPUT_SLOT_0,
				new ItemStack(Items.COPPER_INGOT, 64));
		fillSlot(level, origin, 5, 1, 12, AlloySmelterBlockEntity.INPUT_SLOT_2,
				new ItemStack(ModContent.TIN_INGOT.get(), 64));
		// Distillation Column (MOD-251): the 1×1×3 tower on the second machines row. The three
		// segments are placed explicitly — DemoStand.set uses setBlockAndUpdate, which never calls
		// setPlacedBy, so relying on the base's own placement hook would leave an orphan bottom
		// segment (the MOD-015 gametest lesson). HEIGHT=9 leaves ample headroom on this row.
		dev.alaindustrial.block.DistillationColumnBlock.placeTower(level,
				origin.offset(8, 1, 12));
		chargeBuffer(level, origin, 8, 1, 12);
		// Round 2: the Rectification Section on top — the stand shows the full 4-storey refinery.
		set(level, origin, 8, 4, 12, ModContent.RECTIFICATION_SECTION.get());
	}

	/**
	 * Zone <b>cables</b> (rows z=14..20, in <b>two columns</b>: bare grades at x=16..23, their insulated
	 * counterparts at x=25..32): a fully charged battery box feeds a 6-cable run into an electric furnace
	 * with input — a live network per run, so the energy visibly flows (and the resistive loss of each
	 * material is observable in the GUI).
	 *
	 * <p>One column of eight runs does not fit. Rows must stay two apart or adjacent runs would connect
	 * into a single network, and at that spacing eight grades reach z=28 — past the z=26 item/fluid row
	 * (which is placed after this loop and would silently overwrite the seventh run) and past
	 * {@link #DEPTH} entirely for the eighth. Pairing each conductor with its insulated version side by
	 * side also reads better than a list: the loss difference is one glance away instead of four rows.
	 */
	private static void buildCableRuns(ServerLevel level, BlockPos origin) {
		Block[][] cables = {
			{ModContent.COPPER_CABLE.get(), ModContent.INSULATED_COPPER_CABLE.get()},
			{ModContent.TIN_CABLE.get(), ModContent.INSULATED_TIN_CABLE.get()},
			{ModContent.GOLD_CABLE.get(), ModContent.INSULATED_GOLD_CABLE.get()},
			{ModContent.ELECTRUM_CABLE.get(), ModContent.INSULATED_ELECTRUM_CABLE.get()},
		};
		int z = 14;
		for (Block[] row : cables) {
			for (int column = 0; column < row.length; column++) {
				int x0 = 16 + column * 9;
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
			z += 2;
		}
		// MOD-104: a short item-pipe run between two chests. The two end faces remain neutral
		// in the stand; the wrench is used by the player to demonstrate extract/insert arrows.
		// Sits well past the last cable row (z=20 since MOD-358 paired the grades into two columns;
		// before that the single column kept creeping toward this row as grades were added).
		set(level, origin, 16, 1, 26, ModContent.IRON_CHEST.get());
		fillSlot(level, origin, 16, 1, 26, 0, new ItemStack(Items.IRON_INGOT, 32));
		for (int x = 17; x <= 21; x++) set(level, origin, x, 1, 26, ModContent.ITEM_PIPE.get());
		// MOD-581: the advanced grade directly ABOVE the basic one — the two are meant to be told apart by
		// eye, and a stand showing only one proves nothing about that. One level up rather than one row
		// back: DEPTH is 27, so z=26 is the last row this zone has.
		for (int x = 17; x <= 21; x++) set(level, origin, x, 2, 26, ModContent.ITEM_PIPE_ADVANCED.get());
		// MOD-480: the monitoring wall, one level above the pipes — a wire run into a core with a panel
		// on it. Level 3 of this row was empty; the two rows below are the pipe grades.
		for (int x = 17; x <= 19; x++) set(level, origin, x, 3, 26, ModContent.SMART_WIRE.get());
		set(level, origin, 20, 3, 26, ModContent.MONITOR_CORE.get());
		set(level, origin, 21, 3, 26, ModContent.MONITOR_PANEL.get());
		set(level, origin, 22, 1, 26, ModContent.IRON_CHEST.get());

		// The fluid line continues the same row (DEPTH is 27, so z=26 is the last one available):
		// tank → pipes → tank, the same read-left-to-right shape, so the two transport systems can be
		// compared side by side. The source tank is seeded so the pipes carry something and show their
		// fluid colour instead of sitting empty.
		set(level, origin, 25, 1, 26, ModContent.FLUID_TANK.get());
		if (level.getBlockEntity(origin.offset(25, 1, 26))
				instanceof dev.alaindustrial.block.entity.FluidTankBlockEntity tank) {
			tank.fluidTank.fluid =
					dev.alaindustrial.core.fluid.FluidHolder.of(net.minecraft.world.level.material.Fluids.WATER);
			tank.fluidTank.amount = tank.fluidTank.capacity;
		}
		for (int x = 26; x <= 30; x++) set(level, origin, x, 1, 26, ModContent.FLUID_PIPE.get());
		set(level, origin, 31, 1, 26, ModContent.FLUID_TANK.get());
		// MOD-612: the advanced grade stands next to the basic one, both filled to their OWN capacity —
		// side by side the taller fluid column and the belt around the frame are the whole point of the
		// tier, and a stand that filled both to 8000 would hide it.
		set(level, origin, 33, 1, 26, ModContent.FLUID_TANK_ADVANCED.get());
		if (level.getBlockEntity(origin.offset(33, 1, 26))
				instanceof dev.alaindustrial.block.entity.FluidTankBlockEntity advanced) {
			advanced.fluidTank.fluid =
					dev.alaindustrial.core.fluid.FluidHolder.of(net.minecraft.world.level.material.Fluids.WATER);
			advanced.fluidTank.amount = advanced.fluidTank.capacity;
		}
	}

	/**
	 * Zone <b>ores</b>: a 5×2 wall at z=4 — stone variants on top, deepslate variants below — plus
	 * the Nether ore appended as a single column.
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
			set(level, origin, 34 + i, 2, 4, wall[0][i]);
			set(level, origin, 34 + i, 1, 4, wall[1][i]);
		}
	}

	/**
	 * Zone <b>misc</b> (row z=10): the four storage-chest tiers side by side (iron, silver, gold,
	 * electrum), tempered iron block, and a powered sunken lava cell feeding the adjacent geothermal
	 * generator's tank.
	 */
	private static void buildMisc(ServerLevel level, BlockPos origin) {
		set(level, origin, 30, 1, 10, ModContent.IRON_CHEST.get());
		set(level, origin, 31, 1, 10, ModContent.SILVER_CHEST.get());
		set(level, origin, 32, 1, 10, ModContent.GOLD_CHEST.get());
		// MOD-409: the electrum tier keeps the storage ladder contiguous (30→33), so the row reads as
		// a progression. The tempered iron block it displaced moved to the free cell at x=28 rather
		// than sharing a cell — two `set` calls on one cell silently drop one block (MOD-292).
		set(level, origin, 33, 1, 10, ModContent.ELECTRUM_CHEST.get());
		// MOD-599: the diamond tier, directly ABOVE the electrum one rather than beside it —
		// (34, 1, 10) is the pump's cell, and two `set` calls on one cell silently drop a block
		// (MOD-292/MOD-597). (33, 2, 10) is the workbench, so this sits at x=34 one level up.
		set(level, origin, 34, 2, 10, ModContent.DIAMOND_CHEST.get());
		set(level, origin, 28, 1, 10, ModContent.TEMPERED_IRON_BLOCK.get());
		// MOD-287: two storage modules side by side on the shelf above the plate blocks — adjacent on
		// purpose, so the stand shows them merged into one warehouse rather than two separate ones.
		// x=32/33 rather than 30/31: MOD-292 owns (30, 3, 10) for the MV casing that has to sit directly
		// on top of the LV one below it, and two `set` calls on one cell silently left a lone module.
		set(level, origin, 32, 3, 10, ModContent.STORAGE_MODULE.get());
		set(level, origin, 33, 3, 10, ModContent.STORAGE_MODULE.get());
		// Plate blocks (MOD-225): machine casing + two decorative plate panels, on the shelf above the chests.
		// MOD-292 puts the MV casing directly on top of the LV one so the tier step is visible side by side.
		set(level, origin, 30, 3, 10, ModContent.ADVANCED_MACHINE_CASING.get());
		set(level, origin, 30, 2, 10, ModContent.MACHINE_CASING.get());
		// Reinforced Energy Storage (MOD-351): next to the MV casing it is built from, so the shelf reads
		// as the MV column — casing, and the first block assembled on top of it.
		set(level, origin, 31, 3, 10, ModContent.CESU.get());
		set(level, origin, 31, 2, 10, ModContent.SILVER_PLATE_BLOCK.get());
		set(level, origin, 32, 2, 10, ModContent.TEMPERED_IRON_PLATE_BLOCK.get());
		// Industrial Workbench (MOD-062): the Industrialist villager's job-site block on display.
		set(level, origin, 33, 2, 10, ModContent.INDUSTRIAL_WORKBENCH.get());
		// Mob Repeller tier ladder (MOD-278): the three tiers side by side on the shelf, so the trim
		// difference (iron / silver / electrum) is visible in one glance — that trim IS the tier readout
		// in the world. They are placed unpowered: a live field would shove this stand's own test mobs.
		set(level, origin, 34, 3, 10, ModContent.MOB_REPELLER.get());
		set(level, origin, 35, 3, 10, ModContent.MOB_REPELLER_MV.get());
		set(level, origin, 36, 3, 10, ModContent.MOB_REPELLER_HV.get());
		set(level, origin, 34, -1, 10, FLOOR);
		set(level, origin, 34, 0, 10, Blocks.LAVA);
		// Oil (MOD-238): a sunken one-block oil pool, same pattern as the lava pool above. Kept
		// non-adjacent to the lava/torches on purpose — a directly neighbouring igniter would set it
		// on fire (OilLiquidBlock ignition mechanic) and the stand would showcase a fire block instead.
		set(level, origin, 34, -1, 12, FLOOR);
		set(level, origin, 34, 0, 12, ModContent.OIL_BLOCK.get());
		// Distillation fractions (MOD-251): the same sunken-pool pattern for diesel and fuel oil —
		// water-like fluids, so no ignition spacing worries; two more one-block basins along z=12.
		set(level, origin, 36, -1, 12, FLOOR);
		set(level, origin, 36, 0, 12, ModContent.DIESEL_BLOCK.get());
		set(level, origin, 38, -1, 12, FLOOR);
		set(level, origin, 38, 0, 12, ModContent.FUEL_OIL_BLOCK.get());
		// The organic chain's two fluids (MOD-146/MOD-525), same sunken-basin pattern. Biofuel takes
		// the next slot in this row; the solution goes further west so the two never share an edge.
		set(level, origin, 40, -1, 12, FLOOR);
		set(level, origin, 40, 0, 12, ModContent.BIOFUEL_BLOCK.get());
		set(level, origin, 22, -1, 12, FLOOR);
		set(level, origin, 22, 0, 12, ModContent.NUTRIENT_SOLUTION_BLOCK.get());
		set(level, origin, 34, 1, 10, ModContent.PUMP.get());
		chargeBuffer(level, origin, 34, 1, 10);
		set(level, origin, 35, 1, 10, ModContent.GEOTHERMAL_GENERATOR.get());
		set(level, origin, 36, 1, 10, ModContent.FLUID_TANK.get());
		if (level.getBlockEntity(origin.offset(36, 1, 10)) instanceof FluidTankBlockEntity tank) {
			tank.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
			tank.fluidTank.amount = tank.fluidTank.capacity / 2;
			tank.setChanged();
		}
		// Cotton trellis (MOD-280): a ripe plant on moist farmland — the stand has to show the crop at
		// its most recognisable stage, with the soil it actually needs. Placed via the vanilla two-block
		// helper so both halves appear; the age is written to BOTH halves, since the upper one carries it
		// only to keep its model in step with the lower.
		place(level, origin, origin.offset(41, 0, 10),
				Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
		DoublePlantBlock.placeAt(level, ModContent.TRELLIS.get().defaultBlockState(),
				origin.offset(41, 1, 10), 3);
		for (int dy = 1; dy <= 2; dy++) {
			BlockPos half = origin.offset(41, dy, 10);
			BlockState state = level.getBlockState(half);
			if (state.is(ModContent.TRELLIS.get())) {
				level.setBlock(half, state.setValue(TrellisBlock.AGE, TrellisBlock.MAX_AGE), 3);
			}
		}
		// Kok-sagyz column (MOD-537): shown as an exposed soil cross-section beside the trellis —
		// tip at the bottom, upper root above it, mature puff on top — so the stand answers "where
		// does the rubber come from" the way the plant itself does: dig the bottom block.
		place(level, origin, origin.offset(41, 0, 9), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.KokSagyzRootBlock.TIP, true));
		place(level, origin, origin.offset(41, 1, 9), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState());
		place(level, origin, origin.offset(41, 2, 9), ModContent.KOK_SAGYZ.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.KokSagyzBlock.AGE, dev.alaindustrial.block.KokSagyzBlock.AGE_MATURE));
		// MOD-584: a short harvestable root beside the full column, both in their original soil.
		// x=40, not 42: WIDTH is 42, so column 42 lies OUTSIDE the stand — `clearAbove` and `clear`
		// both stop at x < WIDTH, and this trio would have stood on the grass forever after a clear.
		// Row z=9 holds nothing else, so the pair sits together with the full column at x=41.
		set(level, origin, 40, 0, 9, FLOOR);
		place(level, origin, origin.offset(40, 1, 9), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.KokSagyzRootBlock.TIP, true));
		if (level.getBlockEntity(origin.offset(40, 1, 9)) instanceof dev.alaindustrial.block.entity.KokSagyzRootBlockEntity root) {
			root.setSoil(Blocks.SAND.defaultBlockState());
		}
		place(level, origin, origin.offset(40, 2, 9), ModContent.KOK_SAGYZ.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.KokSagyzBlock.AGE, dev.alaindustrial.block.KokSagyzBlock.AGE_MATURE));
		// Garden Drone Station (MOD-277): the dock beside the trellis plot, charged so its status light
		// reads "powered" rather than "no EU". Placed next to farmland on purpose — the stand should show
		// the block in the context it works in.
		set(level, origin, 40, 1, 10, ModContent.GARDEN_DRONE_STATION.get());
		chargeBuffer(level, origin, 40, 1, 10);
		// Enriched Uranium Torch (MOD-085): the standing torch on the floor, and the wall variant mounted
		// on a small stone post (facing WEST → supported by the post block to its east) so both survive.
		set(level, origin, 37, 1, 10, ModContent.ENRICHED_URANIUM_TORCH.get());
		// Charging Station (MOD-274): banked full, so a visitor can step straight onto the stand's copy
		// and watch their gear fill — an empty one would only ever show the red "no power" indicator.
		// Sits at floor level under the wall torch's post; it is a 4px plate, so nothing above it moves.
		set(level, origin, 38, 1, 10, ModContent.CHARGE_PAD.get());
		chargeBuffer(level, origin, 38, 1, 10);
		// Energy condenser (MOD-546): banked full, so the stand's copy shows the top-tier crystal
		// and a tier-III clot already sitting in its slot — an empty one would just be a dark frame.
		set(level, origin, 39, 1, 10, ModContent.ENERGY_CONDENSER.get());
		chargeBuffer(level, origin, 39, 1, 10);
		set(level, origin, 39, 2, 10, FLOOR);
		place(level, origin, origin.offset(38, 2, 10),
				ModContent.ENRICHED_URANIUM_WALL_TORCH.get().defaultBlockState()
						.setValue(WallTorchBlock.FACING, Direction.WEST));
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
		place(level, origin, origin.offset(30, 1, 12), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 30, 1, 12);
		set(level, origin, 31, 1, 12, ModContent.COPPER_CABLE.get());
		set(level, origin, 32, 1, 12, ModContent.TELEPORTER.get());
	}

	// --- helpers ---

	/**
	 * Zone <b>tiers</b> (MOD-294, row z=1, z=0 kept clear): one live network per voltage tier so the
	 * ladder reads left to right along the stand's north edge. Each storage block faces WEST — the
	 * single-axis IO rule (front IN, back OUT, MOD-006) then puts its output face east, straight
	 * into the tier's own cable grade, exactly like the cable-run boxes below.
	 *
	 * <p>HV is a stub by design: the electrum cable and its consumer exist, but real HV content is
	 * roadmap; the row shows what is built rather than pretending otherwise.
	 */
	private static void buildTierZone(ServerLevel level, BlockPos origin) {
		// LV: battery box, buffer charged full → tin cable → a macerator actually grinding.
		place(level, origin, origin.offset(2, 1, 1), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 2, 1, 1);
		set(level, origin, 3, 1, 1, ModContent.TIN_CABLE.get());
		placeWorkingMachine(level, origin, 4, 1, ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 64));
		// MV: CESU, buffer charged full → gold cable → the assembler, charged and idle (first MV machine).
		place(level, origin, origin.offset(12, 1, 1), ModContent.CESU.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 12, 1, 1);
		set(level, origin, 13, 1, 1, ModContent.GOLD_CABLE.get());
		set(level, origin, 14, 1, 1, ModContent.ASSEMBLER.get());
		chargeBuffer(level, origin, 14, 1, 1);
		// HV stub: an LV battery feeding a teleporter over HV wiring is legal (packet ceiling, not
		// floor) and keeps the row honest — no fake HV source stands in for content that is not built.
		place(level, origin, origin.offset(22, 1, 1), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 22, 1, 1);
		set(level, origin, 23, 1, 1, ModContent.ELECTRUM_CABLE.get());
		set(level, origin, 24, 1, 1, ModContent.TELEPORTER.get());
	}

	/**
	 * Zone <b>loss lane</b> (MOD-294, row z=7, z=8 kept clear): a single 36-block copper run from a
	 * charged battery box into an electric furnace. The bare-vs-insulated comparison at 6 blocks
	 * already lives in the cable zone; this lane answers the other question — what a LONG haul
	 * costs. Read it by walking the line and comparing the furnace GUI's received-EU against the
	 * distance from the box.
	 */
	private static void buildLossLane(ServerLevel level, BlockPos origin) {
		place(level, origin, origin.offset(2, 1, 7), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 2, 1, 7);
		for (int x = 3; x <= 38; x++) {
			set(level, origin, x, 1, 7, ModContent.COPPER_CABLE.get());
		}
		set(level, origin, 39, 1, 7, ModContent.ELECTRIC_FURNACE.get());
		fillSlot(level, origin, 39, 1, 7, 0, new ItemStack(Items.RAW_COPPER, 64));
	}

	/**
	 * Zone <b>farms</b> (MOD-294, chains along z=23, z=22 kept clear): four working mini-chains, one
	 * per key progression, each stocked so it runs the moment the stand is built. Rows z=21/24 stay
	 * walkways; the north neighbour of every chain cell is air, never an energy block — the z=20
	 * electrum runs above would otherwise tap the farms through any machine placed at x=16..32,
	 * which is why the chains hug z=23 where z=22 stays empty.
	 */
	private static void buildFarms(ServerLevel level, BlockPos origin) {
		// Farm A — LV cycle: solar → copper cable → battery box → cable → macerator, ore chest beside.
		// The panels are FACING-inert (every horizontal face is OUT), so the east-running chain needs
		// no state juggling — the cable simply meets the panel's OUT face.
		set(level, origin, 2, 1, 23, ModContent.SOLAR_PANEL.get());
		set(level, origin, 3, 1, 23, ModContent.COPPER_CABLE.get());
		place(level, origin, origin.offset(4, 1, 23), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 4, 1, 23);
		set(level, origin, 5, 1, 23, ModContent.COPPER_CABLE.get());
		placeWorkingMachine(level, origin, 6, 23, ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 64));
		set(level, origin, 8, 1, 23, ModContent.IRON_CHEST.get());
		fillSlot(level, origin, 8, 1, 23, 0, new ItemStack(Items.RAW_IRON, 64));
		fillSlot(level, origin, 8, 1, 23, 1, new ItemStack(Items.COAL, 64));
		// Farm B — fluid line: battery box → pump over a sunken water cell → fluid pipes → empty tank
		// that visibly fills. The pump's IN faces are everything but its intake (PumpBlock), so the
		// box's east output face meets one directly.
		place(level, origin, origin.offset(11, 1, 23), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		chargeBuffer(level, origin, 11, 1, 23);
		set(level, origin, 12, -1, 23, FLOOR);
		set(level, origin, 12, 0, 23, Blocks.WATER);
		set(level, origin, 12, 1, 23, ModContent.PUMP.get());
		chargeBuffer(level, origin, 12, 1, 23);
		for (int x = 13; x <= 15; x++) {
			set(level, origin, x, 1, 23, ModContent.FLUID_PIPE.get());
		}
		set(level, origin, 16, 1, 23, ModContent.FLUID_TANK.get());
		// Farm C — oil → rubber → cable: an open oil cell beside an oil-fed polymerizer, the
		// heater+vulcanizer pair, and a chest with the chain's inputs and both cable grades to
		// compare in hand.
		set(level, origin, 22, -1, 23, FLOOR);
		set(level, origin, 22, 0, 23, ModContent.OIL_BLOCK.get());
		set(level, origin, 23, 1, 23, ModContent.POLYMERIZER.get());
		chargeBuffer(level, origin, 23, 1, 23);
		if (level.getBlockEntity(origin.offset(23, 1, 23)) instanceof PolymerizerBlockEntity polymerizer) {
			polymerizer.fluidTank.fluid = FluidHolder.of(ModContent.OIL.get());
			polymerizer.fluidTank.amount = PolymerizerBlockEntity.TANK_CAPACITY;
			polymerizer.setChangedQuietly();
			polymerizer.wake();
		}
		set(level, origin, 25, 1, 23, ModContent.ELECTRIC_HEATER.get());
		chargeBuffer(level, origin, 25, 1, 23);
		set(level, origin, 25, 2, 23, ModContent.VULCANIZER.get());
		chargeBuffer(level, origin, 25, 2, 23);
		fillSlot(level, origin, 25, 2, 23, VulcanizerBlockEntity.RAW_RUBBER_SLOT,
				new ItemStack(ModContent.RAW_RUBBER.get(), 64));
		fillSlot(level, origin, 25, 2, 23, VulcanizerBlockEntity.SULFUR_SLOT,
				new ItemStack(ModContent.SULFUR_DUST.get(), 64));
		set(level, origin, 27, 1, 23, ModContent.IRON_CHEST.get());
		fillSlot(level, origin, 27, 1, 23, 0, new ItemStack(ModContent.RAW_RUBBER.get(), 64));
		fillSlot(level, origin, 27, 1, 23, 1, new ItemStack(ModContent.RUBBER.get(), 64));
		fillSlot(level, origin, 27, 1, 23, 2, new ItemStack(ModContent.INSULATED_COPPER_CABLE_ITEM.get(), 64));
		// Farm D — mutation: incubator + dome in transform mode, a ripe trellis on moist farmland,
		// and a chest with all three chips. The misc zone's incubator shows duplicate mode; this one
		// shows the mode that consumes the plant and returns the mutated result.
		set(level, origin, 33, 1, 23, ModContent.INCUBATOR.get());
		set(level, origin, 33, 2, 23, ModContent.INCUBATOR_DOME.get());
		chargeBuffer(level, origin, 33, 1, 23);
		fillSlot(level, origin, 33, 1, 23, IncubatorBlockEntity.CHIP_SLOT,
				new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get()));
		fillSlot(level, origin, 33, 1, 23, IncubatorBlockEntity.FUEL_SLOT,
				new ItemStack(ModContent.URANIUM_INGOT.get(), 16));
		fillSlot(level, origin, 33, 1, 23, IncubatorBlockEntity.INPUT_SLOT,
				new ItemStack(Items.SWEET_BERRIES, 64));
		place(level, origin, origin.offset(35, 0, 23),
				Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
		DoublePlantBlock.placeAt(level, ModContent.TRELLIS.get().defaultBlockState(),
				origin.offset(35, 1, 23), 3);
		for (int dy = 1; dy <= 2; dy++) {
			BlockPos half = origin.offset(35, dy, 23);
			BlockState state = level.getBlockState(half);
			if (state.is(ModContent.TRELLIS.get())) {
				level.setBlock(half, state.setValue(TrellisBlock.AGE, TrellisBlock.MAX_AGE), 3);
			}
		}
		set(level, origin, 37, 1, 23, ModContent.IRON_CHEST.get());
		fillSlot(level, origin, 37, 1, 23, 0, new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get(), 16));
		fillSlot(level, origin, 37, 1, 23, 1, new ItemStack(ModContent.MUTATION_CHIP_DUPLICATE.get(), 16));
		fillSlot(level, origin, 37, 1, 23, 2, new ItemStack(ModContent.MUTATION_CHIP_CREATE.get(), 16));
	}

	/**
	 * Zone <b>reactor</b> (MOD-468, row z=21): every part of the reactor room laid out side by side.
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
	 * <p>Placed at x 8..12, z 15..19, immediately east of the reactor room's 5×5×5 at x 2..6 with one
	 * block of air between them: two multiblocks side by side, which is the comparison worth showing.
	 * As the reactor room's own comment warns, moving this means re-reading the LOOPS of every other
	 * zone rather than the literal coordinates — the stand's coverage gametest is what catches a
	 * collision, by reporting whichever block got overwritten.
	 */
	private static void buildCrystalGreenhouse(ServerLevel level, BlockPos origin) {
		final int bx = 8;
		final int by = 1;
		final int bz = 15;
		final int edge = 5;

		// Deck underfoot and overhead, glass everywhere else: the interior 3×3×3 is exactly
		// crystalFarmRoomMinCells, so this is the smallest greenhouse the scan will accept.
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

		// Water in the corner: the free half of the growth bonus, and the panel reports it.
		place(level, origin, origin.offset(bx + 3, by + 1, bz + 3),
				Blocks.WATER.defaultBlockState());

		// Three beds on the floor: one dead as crafted, two awake and carrying vanilla amethyst at
		// different stages, so the stand shows the whole life of the block at once.
		set(level, origin, bx + 1, by + 1, bz + 1, ModContent.CRYSTAL_SEEDBED.get());
		Block[] stages = { Blocks.MEDIUM_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER };
		int lx = 2;
		for (Block stage : stages) {
			BlockPos bed = origin.offset(bx + lx, by + 1, bz + 2);
			place(level, origin, bed, ModContent.CRYSTAL_SEEDBED.get().defaultBlockState()
					.setValue(CrystalSeedbedBlock.CHARGES, CrystalSeedbedBlock.MAX_CHARGES));
			place(level, origin, bed.above(), stage.defaultBlockState()
					.setValue(AmethystClusterBlock.FACING, Direction.UP));
			lx++;
		}
	}

	private static void buildReactorZone(ServerLevel level, BlockPos origin) {
		int z = 21;
		set(level, origin, 2, 1, z, ModContent.REACTOR_CASING.get());
		set(level, origin, 3, 1, z, ModContent.REACTOR_GLASS.get());
		set(level, origin, 4, 1, z, ModContent.REACTOR_PORT.get());
		set(level, origin, 5, 1, z, ModContent.REACTOR_LAMP.get());
		set(level, origin, 6, 1, z, ModContent.REACTOR_OUTLET.get());

		// The button needs something to hang on, so it gets its own casing block to sit against.
		set(level, origin, 7, 1, z, ModContent.REACTOR_CASING.get());
		place(level, origin, origin.offset(7, 2, z),
				ModContent.REACTOR_BUTTON.get().defaultBlockState()
						.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
						.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));

		// The lever (MOD-514) stands beside the button it twins, on its own casing block, so the two
		// control blocks can be told apart at a glance: one pulses, one latches.
		set(level, origin, 8, 1, z, ModContent.REACTOR_CASING.get());
		place(level, origin, origin.offset(8, 2, z),
				ModContent.REACTOR_LEVER.get().defaultBlockState()
						.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
						.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));

		// The airlock is two blocks: the stand places both halves by hand, because setPlacedBy (which
		// normally raises the upper half) does not run for a programmatic setBlock.
		BlockState door = ModContent.REACTOR_DOOR.get().defaultBlockState()
				.setValue(ReactorDoorBlock.FACING, Direction.SOUTH);
		place(level, origin, origin.offset(9, 1, z), door);
		place(level, origin, origin.offset(9, 2, z),
				door.setValue(ReactorDoorBlock.HALF, DoubleBlockHalf.UPPER));

		place(level, origin, origin.offset(11, 1, z),
				ModContent.REACTOR_CONTROLLER.get().defaultBlockState()
						.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));

		// The exhaust, facing south into open air — a nozzle pointing at a block vents nothing, and a
		// stand that showed one buried in a wall would be showing a broken installation.
		place(level, origin, origin.offset(15, 1, z),
				ModContent.STEAM_NOZZLE.get().defaultBlockState()
						.setValue(SteamNozzleBlock.FACING, Direction.SOUTH));

		// Loaded to four rods, so the stand shows the state the fill level exists to communicate.
		place(level, origin, origin.offset(13, 1, z),
				ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState()
						.setValue(FuelRodAssemblyBlock.RODS, FuelRodAssemblyBlock.MAX_RODS));
		if (level.getBlockEntity(origin.offset(13, 1, z))
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
	 * working room was to build a 5x5x5 shell by hand every time the stand was rebuilt — which is
	 * exactly the kind of chore the demo command exists to remove, and it made the reactor the one
	 * shipped multiblock the stand could not demonstrate.
	 *
	 * <p><b>Shell 5x5x5, interior 3x3x3</b> — the smallest room {@code RoomScan} accepts, so it fits
	 * beside the other zones. Local coordinates below are (lx, ly, lz) from the room's north-west floor
	 * corner; the camera looks south, so the north wall is the face the player sees: controller, glass
	 * and the airlock all live in it.
	 *
	 * <p>Three placements are less obvious than they look:
	 * <ul>
	 * <li>the <b>controller faces north</b> (outward). {@code RoomScan} demands that the cell behind its
	 * face be interior, not shell — a controller facing into the wall reports CONTROLLER_NOT_IN_WALL;</li>
	 * <li>the <b>redstone block sits inside</b>, against the controller's back. The reactor runs only
	 * while {@code hasNeighborSignal} is true, and every outside cell adjacent to the controller is
	 * either shell (which would be a breach) or directly in front of its face (which would cover the
	 * screen the demo is there to show);</li>
	 * <li>the <b>button needs its own post</b>. A button must hang on a solid block, and no shell cell
	 * next to the doorway is available without punching a hole in the room, so a single casing block
	 * outside carries it. Its cell is adjacent to the door's lower half, which is what
	 * {@code ReactorDoorBlock.neighborChanged} reads.</li>
	 * </ul>
	 */
	private static void buildReactorRoom(ServerLevel level, BlockPos origin) {
		// x 2..6 / z 15..19 is the one 5x5 hole left in the stand: the cable rows own x>=16 from z=14
		// down to z=20, the farm row is z=23, and the item-pipe run is x 17..21 at z=26 — the first
		// attempt put the room straight on top of that run, and the stand's own coverage gametest
		// caught it ("missing: item_pipe"). Moving a zone means checking the LOOPS, not just the
		// literal coordinates.
		final int bx = 2;
		final int by = 1;
		final int bz = 15;
		final int edge = 5;

		// Shell: every perimeter cell of the 5x5x5 box is casing; the interior is left as air.
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

		// Windows in the north wall — five cells, far under the 30 % glass cap the scan enforces.
		// From here to the door, every write is a `refit`: it is a fitting punched into the casing
		// shell laid above, which is a replacement the author means (MOD-597).
		refit(level, origin, bx + 1, by + 2, bz, ModContent.REACTOR_GLASS.get());
		refit(level, origin, bx + 2, by + 2, bz, ModContent.REACTOR_GLASS.get());
		refit(level, origin, bx + 1, by + 3, bz, ModContent.REACTOR_GLASS.get());
		refit(level, origin, bx + 2, by + 3, bz, ModContent.REACTOR_GLASS.get());
		refit(level, origin, bx + 3, by + 3, bz, ModContent.REACTOR_GLASS.get());

		// Plumbing and power crossings, one per side wall.
		refit(level, origin, bx + edge - 1, by + 1, bz + 2, ModContent.REACTOR_PORT.get());
		refit(level, origin, bx, by + 1, bz + 2, ModContent.REACTOR_OUTLET.get());
		refit(level, origin, bx + 2, by + edge - 1, bz + 2, ModContent.REACTOR_LAMP.get());

		// Controller in the north wall, front outward.
		refit(level, origin, origin.offset(bx + 2, by + 1, bz),
				ModContent.REACTOR_CONTROLLER.get().defaultBlockState()
						.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));

		// Airlock, both halves by hand: setPlacedBy does not run for a programmatic setBlock.
		BlockState door = ModContent.REACTOR_DOOR.get().defaultBlockState()
				.setValue(ReactorDoorBlock.FACING, Direction.SOUTH);
		refit(level, origin, origin.offset(bx + 3, by + 1, bz), door);
		refit(level, origin, origin.offset(bx + 3, by + 2, bz),
				door.setValue(ReactorDoorBlock.HALF, DoubleBlockHalf.UPPER));

		// Control post outside the door: one casing block, and the button on its west face.
		set(level, origin, bx + 4, by + 1, bz - 1, ModContent.REACTOR_CASING.get());
		place(level, origin, origin.offset(bx + 3, by + 1, bz - 1),
				ModContent.REACTOR_BUTTON.get().defaultBlockState()
						.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
						.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));

		// Exhaust on the west side, venting into open air — a nozzle facing a block vents nothing.
		place(level, origin, origin.offset(bx - 1, by + 1, bz + 2),
				ModContent.STEAM_NOZZLE.get().defaultBlockState()
						.setValue(SteamNozzleBlock.FACING, Direction.WEST));

		// Interior: the core, and the signal that lets it run.
		set(level, origin, bx + 2, by + 1, bz + 1, Blocks.REDSTONE_BLOCK);
		place(level, origin, origin.offset(bx + 2, by + 1, bz + 2),
				ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState()
						.setValue(FuelRodAssemblyBlock.RODS, FuelRodAssemblyBlock.MAX_RODS));
		if (level.getBlockEntity(origin.offset(bx + 2, by + 1, bz + 2))
				instanceof FuelRodAssemblyBlockEntity assembly) {
			for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
				assembly.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
			}
			assembly.setTank(true, assembly.waterTank.capacity / 2);
		}

		// MOD-474 — the shielding chest, stocked with the fuel it is there to make safe, parked outside
		// the shell. Its place in the story is exactly here: the only spot on the stand where refined
		// uranium can sit in the open without dosing whoever walks past it.
		//
		// x=14, not 8 (MOD-597). x=8 was free when this was written and stopped being free when the
		// crystal greenhouse (MOD-505) took x 8..12 — so the chest was being set into the greenhouse's
		// own perimeter, punching a hole in a shell whose whole point is that it seals. Nothing went
		// red: no test asks the greenhouse to form. x=14 is the last free column before the cable rows
		// start at 16, with the greenhouse ending at 12.
		set(level, origin, 14, by, bz + 2, ModContent.SHIELDING_CHEST.get());
		fillSlot(level, origin, 14, by, bz + 2, 0, new ItemStack(ModContent.REFINED_URANIUM.get(), 64));
		fillSlot(level, origin, 14, by, bz + 2, 1, new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		// MOD-471 — the scar an accident leaves. Shown as the four decay stages side by side, because
		// the whole point of the intensity is that a player can read how clean a patch is at a glance,
		// and a single sample would show them one colour with nothing to compare it against. Runs south
		// from the chest down the same free column.
		//
		// x=14 for the same reason as the chest, and this strip had hit TWO zones from x=8 (MOD-597):
		// its first stage landed in the greenhouse's floor and its third replaced the casing post the
		// reactor row's lever stands on.
		for (int stage = 0; stage <= IrradiatedSoilBlock.MAX_INTENSITY; stage++) {
			place(level, origin, origin.offset(14, by, bz + 4 + stage),
					ModContent.IRRADIATED_SOIL.get().defaultBlockState()
							.setValue(IrradiatedSoilBlock.INTENSITY, stage));
		}
	}

	/**
	 * Zone <b>showcase</b> (MOD-294, wall at the back edge z=25): EVERY item of the registry in a
	 * glow item frame — block items included since MOD-586, so the wall is the mod's index rather
	 * than a leftovers shelf. The frames are generated by looping {@code BuiltInRegistries.ITEM}, so
	 * the wall refills itself whenever the registry grows — and the item-coverage gametest reddens
	 * the day the registry outgrows the wall. Rows z=25/26 hold nothing but the pipe run (x 16..31 at
	 * z=26, behind the wall), and no camera looks at anything through this wall, so the back edge was
	 * free space.
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
	 * <p>Nine blocks still cannot appear here, because they have no item at all: the five fluids
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
