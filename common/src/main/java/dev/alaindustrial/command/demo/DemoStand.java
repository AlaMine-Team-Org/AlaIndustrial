package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;

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
	/** Stand footprint (z). */
	public static final int DEPTH = 26;
	/** Blocks above the floor layer that belong to the stand (wind-mill pillars are tallest). */
	public static final int HEIGHT = 9;

	/** Floor material — also the datum marker {@link #findOrigin} recognises for idempotent rebuilds. */
	private static final Block FLOOR = Blocks.SMOOTH_STONE;

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
			new TpPoint("generators", 10.0, 4.0, 0.0, 0.0f, 20.0f, false),
			new TpPoint("windmills", 25.0, 9.0, 0.0, 0.0f, 10.0f, false),
			new TpPoint("machines", 7.0, 3.0, 6.5, 0.0f, 15.0f, false),
			new TpPoint("cables", 20.0, 6.0, 11.0, 0.0f, 30.0f, false),
			new TpPoint("ores", 36.0, 2.0, 0.5, 0.0f, 5.0f, false),
			new TpPoint("misc", 33.0, 3.0, 6.5, 0.0f, 15.0f, false),
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

	/** Clear the stand envelope above the floor, then build the floor and every zone. */
	public static void buildAll(ServerLevel level, BlockPos origin) {
		clearAbove(level, origin);
		buildFloor(level, origin);
		buildGeneratorRow(level, origin);
		buildWindMills(level, origin);
		buildMachines(level, origin);
		buildCableRuns(level, origin);
		buildOreWall(level, origin);
		buildMisc(level, origin);
	}

	/** Remove the stand: air above, and the floor layer reverts to grass. */
	public static void clear(ServerLevel level, BlockPos origin) {
		clearAbove(level, origin);
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				set(level, origin, x, 0, z, Blocks.GRASS_BLOCK);
			}
		}
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

	private static void buildFloor(ServerLevel level, BlockPos origin) {
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
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
		set(level, origin, 2, 1, 4, ModContent.GENERATOR.get());
		fillSlot(level, origin, 2, 1, 4, 0, new ItemStack(Items.COAL, 64));
		set(level, origin, 2, 1, 5, ModContent.BATTERY_BOX.get());

		set(level, origin, 5, 1, 4, ModContent.GEOTHERMAL_GENERATOR.get());
		fillSlot(level, origin, 5, 1, 4, 0, new ItemStack(Items.LAVA_BUCKET));
		set(level, origin, 5, 1, 5, ModContent.BATTERY_BOX.get());

		int x = 8;
		for (Block solar : new Block[] {ModContent.SOLAR_PANEL.get(),
				ModContent.DAYLIGHT_SOLAR_PANEL.get(), ModContent.MOONLIT_SOLAR_PANEL.get()}) {
			set(level, origin, x, 1, 4, solar);
			set(level, origin, x, 1, 5, ModContent.BATTERY_BOX.get());
			x += 3;
		}

		// Water mill sunk to floor level so its horizontal faces touch water; the water cells
		// replace floor blocks and are contained laterally by the floor and below by stone.
		set(level, origin, 17, -1, 4, FLOOR);
		set(level, origin, 16, -1, 4, FLOOR);
		set(level, origin, 18, -1, 4, FLOOR);
		set(level, origin, 17, 0, 4, ModContent.WATER_MILL.get());
		set(level, origin, 16, 0, 4, Blocks.WATER);
		set(level, origin, 18, 0, 4, Blocks.WATER);
		set(level, origin, 17, -1, 5, FLOOR);
		set(level, origin, 17, 0, 5, ModContent.BATTERY_BOX.get());
	}

	/**
	 * Zone <b>windmills</b>: the three wind mills on pillars, battery box directly beneath each
	 * head (an OUT face — direct push). Spacing 5 keeps them out of each other's interference
	 * radius. Their EU/t depends on build height vs sea level, so on a low superflat they are
	 * intentionally decorative (see MOD-058 task log).
	 */
	private static void buildWindMills(ServerLevel level, BlockPos origin) {
		Block[] mills = {ModContent.WIND_MILL.get(),
				ModContent.HIGH_ALTITUDE_WIND_MILL.get(), ModContent.STORM_WIND_MILL.get()};
		int x = 20;
		for (Block mill : mills) {
			for (int y = 1; y <= 4; y++) {
				set(level, origin, x, y, 4, FLOOR);
			}
			set(level, origin, x, 5, 4, ModContent.BATTERY_BOX.get());
			set(level, origin, x, 6, 4, mill);
			x += 5;
		}
	}

	/**
	 * Zone <b>machines</b> (row z=10): the four processing machines, buffer pre-charged to full
	 * and a stack of a guaranteed-recipe input in slot 0, so they are visibly working (lit +
	 * progress) the moment the stand is built.
	 */
	private static void buildMachines(ServerLevel level, BlockPos origin) {
		placeWorkingMachine(level, origin, 2, 10, ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 64));
		placeWorkingMachine(level, origin, 5, 10, ModContent.ELECTRIC_FURNACE.get(), new ItemStack(Items.RAW_COPPER, 64));
		placeWorkingMachine(level, origin, 8, 10, ModContent.COMPRESSOR.get(),
				new ItemStack(ModContent.IRON_DUST.get(), 64));
		placeWorkingMachine(level, origin, 11, 10, ModContent.EXTRACTOR.get(), new ItemStack(Items.GRAVEL, 64));
	}

	/**
	 * Zone <b>cables</b> (rows z=14/16/18/20, one per cable type): a fully charged battery box
	 * feeds a 6-cable run into an electric furnace with input — a live network per row, so the
	 * energy visibly flows (and the resistive loss of each material is observable in the GUI).
	 */
	private static void buildCableRuns(ServerLevel level, BlockPos origin) {
		Block[] cables = {ModContent.COPPER_CABLE.get(), ModContent.TIN_CABLE.get(),
				ModContent.INSULATED_COPPER_CABLE.get(), ModContent.INSULATED_TIN_CABLE.get()};
		int z = 14;
		for (Block cable : cables) {
			set(level, origin, 16, 1, z, ModContent.BATTERY_BOX.get());
			chargeBuffer(level, origin, 16, 1, z);
			for (int x = 17; x <= 22; x++) {
				set(level, origin, x, 1, z, cable);
			}
			set(level, origin, 23, 1, z, ModContent.ELECTRIC_FURNACE.get());
			fillSlot(level, origin, 23, 1, z, 0, new ItemStack(Items.RAW_COPPER, 64));
			z += 2;
		}
	}

	/** Zone <b>ores</b>: a 4×2 wall at z=4 — stone variants on top, deepslate variants below. */
	private static void buildOreWall(ServerLevel level, BlockPos origin) {
		Block[][] wall = {
				{ModContent.TIN_ORE.get(), ModContent.SILVER_ORE.get(),
						ModContent.NICKEL_ORE.get(), ModContent.URANIUM_ORE.get()},
				{ModContent.DEEPSLATE_TIN_ORE.get(), ModContent.DEEPSLATE_SILVER_ORE.get(),
						ModContent.DEEPSLATE_NICKEL_ORE.get(), ModContent.DEEPSLATE_URANIUM_ORE.get()}};
		for (int i = 0; i < 4; i++) {
			set(level, origin, 34 + i, 2, 4, wall[0][i]);
			set(level, origin, 34 + i, 1, 4, wall[1][i]);
		}
	}

	/**
	 * Zone <b>misc</b> (row z=10): iron chest, tempered iron block, and a powered pump over a
	 * sunken lava cell feeding the adjacent geothermal generator's tank.
	 */
	private static void buildMisc(ServerLevel level, BlockPos origin) {
		set(level, origin, 30, 1, 10, ModContent.IRON_CHEST.get());
		set(level, origin, 32, 1, 10, ModContent.TEMPERED_IRON_BLOCK.get());
		set(level, origin, 34, -1, 10, FLOOR);
		set(level, origin, 34, 0, 10, Blocks.LAVA);
		set(level, origin, 34, 1, 10, ModContent.PUMP.get());
		chargeBuffer(level, origin, 34, 1, 10);
		set(level, origin, 35, 1, 10, ModContent.GEOTHERMAL_GENERATOR.get());
		// Enriched Uranium Torch (MOD-085): the standing torch on the floor, and the wall variant mounted
		// on a small stone post (facing WEST → supported by the post block to its east) so both survive.
		set(level, origin, 37, 1, 10, ModContent.ENRICHED_URANIUM_TORCH.get());
		set(level, origin, 39, 1, 10, FLOOR);
		set(level, origin, 39, 2, 10, FLOOR);
		level.setBlockAndUpdate(origin.offset(38, 2, 10),
				ModContent.ENRICHED_URANIUM_WALL_TORCH.get().defaultBlockState()
						.setValue(WallTorchBlock.FACING, Direction.WEST));
	}

	// --- helpers ---

	private static void set(ServerLevel level, BlockPos origin, int x, int y, int z, Block block) {
		level.setBlockAndUpdate(origin.offset(x, y, z), block.defaultBlockState());
	}

	/** Place a processing machine with a full EU buffer and an input stack — it starts working immediately. */
	private static void placeWorkingMachine(ServerLevel level, BlockPos origin, int x, int z,
			Block machine, ItemStack input) {
		set(level, origin, x, 1, z, machine);
		chargeBuffer(level, origin, x, 1, z);
		fillSlot(level, origin, x, 1, z, 0, input);
	}

	private static void fillSlot(ServerLevel level, BlockPos origin, int x, int y, int z, int slot, ItemStack stack) {
		if (level.getBlockEntity(origin.offset(x, y, z)) instanceof Container container) {
			container.setItem(slot, stack);
		}
	}

	private static void chargeBuffer(ServerLevel level, BlockPos origin, int x, int y, int z) {
		if (level.getBlockEntity(origin.offset(x, y, z)) instanceof MachineBlockEntity machine) {
			machine.getEnergyStorage().amount = machine.getEnergyStorage().getCapacity();
			machine.markDirtyAndSync();
			machine.wake();
		}
	}
}
