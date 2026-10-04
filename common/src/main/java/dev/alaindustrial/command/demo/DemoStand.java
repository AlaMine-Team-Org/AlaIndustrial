package dev.alaindustrial.command.demo;

import dev.alaindustrial.Industrialization;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * <p>This class is the facade: the stand's size and public constants, the camera list, the registry
 * queries the gametests share, and {@link #buildAll}. The exhibits live one zone per file in this
 * package, each a {@link DemoZone} listed in build order in {@link #ZONES}; every cell they write goes
 * through the {@link StandWriter} that only {@link #buildAll} can make (ADR-028).
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

	/**
	 * What fills the {@link #DEPTH_BELOW} layers under the floor — ordinary subsoil, the thing that
	 * sits under grass. Written blanket on every build and every clear, exactly like the floor above
	 * it, so what the stand digs is always dug out of a known slab rather than out of whatever the
	 * previous version of the layout happened to leave there.
	 */
	public static final Block SUBSOIL = Blocks.DIRT;

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
	 *
	 * <p>Each camera is declared in the file of the zone it looks at; the list stays one explicit line-up
	 * in this order because the order is what {@code /ala demo tp} suggests and what the clickable map in
	 * the answer to {@code /ala demo build} shows. It is NOT the order of {@link #ZONES}: the cameras and
	 * the zones are not one-to-one (the overview and the night view have no zone, some zones have several
	 * cameras and two have none), and the two orders were never the same.
	 */
	public static final List<TpPoint> TP_POINTS = List.of(
			new TpPoint("overview", 44.0, 42.0, -10.0, 0.0f, 55.0f, false),
			TierZone.TIERS_CAMERA,
			BenchZone.BENCH_CAMERA,
			GeneratorRowZone.GENERATORS_CAMERA,
			GeneratorRowZone.WATERMILL_CAMERA,
			GeneratorRowZone.CONCENTRATOR_CAMERA,
			WindMillsZone.WINDMILLS_CAMERA,
			LossLaneZone.LOSS_CAMERA,
			OreWallZone.ORES_CAMERA,
			TransportZone.TRANSPORT_CAMERA,
			TransportZone.FLUIDS_CAMERA,
			MachinesZone.MACHINES_CAMERA,
			MachinesZone.MACHINES2_CAMERA,
			MiscZone.MISC_CAMERA,
			CableRunsZone.CABLES_CAMERA,
			ReactorRoomZone.REACTOR_CAMERA,
			ReactorZone.REACTORROW_CAMERA,
			FarmsZone.FARMS_CAMERA,
			MonitorWallZone.MONITOR_CAMERA,
			ItemlessRowZone.ITEMLESS_CAMERA,
			ShowcaseZone.SHOWCASE_CAMERA,
			ShowcaseZone.SHOWCASE_WEST_CAMERA,
			ShowcaseZone.SHOWCASE_EAST_CAMERA,
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

	/**
	 * The zones of the stand, in the order {@link #buildAll} builds them: one class per zone, one file
	 * per class. A new zone is a new class plus one entry here; the order is part of the layout, because
	 * a later zone may carve a cell an earlier one filled (ADR-028).
	 */
	static final List<DemoZone> ZONES = List.of(
			new TierZone(),
			new BenchZone(),
			new GeneratorRowZone(),
			new WindMillsZone(),
			new MachinesZone(),
			new CableRunsZone(),
			new TransportZone(),
			new OreWallZone(),
			new MiscZone(),
			new LossLaneZone(),
			new FarmsZone(),
			new ReactorZone(),
			new CrystalGreenhouseZone(),
			new ReactorRoomZone(),
			new LabPlaqueZone(),
			new ShowcaseZone(),
			new ItemlessRowZone(),
			new MonitorWallZone());

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
		StandWriter w = new StandWriter(new BuildPass(), level, origin);
		w.open();
		try {
			for (DemoZone zone : ZONES) {
				zone.build(w);
			}
		} finally {
			lastProblems = w.close();
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
					fill(level, origin, x, y, z, SUBSOIL);
				}
				fill(level, origin, x, 0, z, Blocks.GRASS_BLOCK);
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
						fill(level, origin, x, y, z, Blocks.AIR);
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
					fill(level, origin, x, y, z, SUBSOIL);
				}
				fill(level, origin, x, 0, z, StandLayout.FLOOR);
			}
		}
	}

	/**
	 * A blanket write of the base passes (floor, subsoil, clearing, the grass of {@code clear}) — never
	 * recorded, because those passes legitimately cover the whole footprint before the zones carve it.
	 */
	private static void fill(ServerLevel level, BlockPos origin, int x, int y, int z, Block block) {
		level.setBlockAndUpdate(origin.offset(x, y, z), block.defaultBlockState());
	}

	/**
	 * The ticket a {@link StandWriter} is made with. Its constructor is private to this class, so the
	 * only writer that exists is the one {@link #buildAll} opens: a zone cannot be run past the ledger
	 * from anywhere else in the package (ADR-028, MOD-709).
	 */
	static final class BuildPass {
		private BuildPass() {
		}
	}

	/** Column of the {@code index}-th block of the item-less row; the gametest asks the same question. */
	public static int itemlessX(int index) {
		return StandLayout.ITEMLESS_X0 + StandLayout.ITEMLESS_STEP * index;
	}

	/** Row z of the item-less blocks, for the gametest. */
	public static int itemlessZ() {
		return StandLayout.ITEMLESS_Z;
	}

	/** Blocks of the mod with no item; shown by {@link ItemlessRowZone}. */
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
	 * <p>Blocks with no item at all cannot appear here (they stand in the item-less row instead, see
	 * {@link ItemlessRowZone}): the five fluids
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

	/** What the last {@link #buildAll} reported; replaced whole at the end of every build. */
	private static volatile List<String> lastProblems = List.of();

	/**
	 * What the last {@link #buildAll} overwrote — one line per cell written twice by the zone pass.
	 *
	 * <p>Two kinds of problem land here: a cell written twice, and a stocking call that found no
	 * block entity to stock. They are one list because they are one incident seen from both ends — the
	 * block that lost its cell, and the inventory that was then filled into nothing.
	 *
	 * <p>This exists because the failure it reports is <b>silent</b>: a second write on a cell drops
	 * the first block without a word, the block-coverage scan still ticks its box (the lost block is
	 * almost always somewhere else on the stand too), and the stocking helpers fall through
	 * their {@code instanceof} in silence. It has happened at least five times — MOD-292, MOD-275,
	 * MOD-145, MOD-599, MOD-597 — and every time the answer was another comment telling the next
	 * author to check the neighbouring cells by hand. This is that comment turned into a check.
	 *
	 * <p><b>Only what goes through {@link StandWriter#place}.</b> Blocks written by a vanilla or mod helper the
	 * stand calls ({@code DistillationColumnBlock.placeTower}, {@code ConcentratorStructure.tryAssemble},
	 * the workstation, upgrade-table and teleporter assemblers) place their own cells and are invisible
	 * here; so is the floor pass, which legitimately writes every cell of the stand before the zones
	 * carve it. The cotton trellis is the exception since MOD-709: {@link StandWriter#ripenTrellis}
	 * rewrites both halves through the ledger.
	 */
	public static List<String> lastBuildProblems() {
		return lastProblems;
	}
}
