package dev.alaindustrial.block.entity.quarry;

import dev.alaindustrial.block.entity.quarry.QuarryFramePlacements.Part;
import dev.alaindustrial.block.entity.quarry.QuarryFramePlacements.Placement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The collision and outline of a formed quarry frame (MOD-950): the designer's model cut into the cells it covers,
 * so the player bumps into the drawn lattice and stands on the drawn path, and the hover outline follows the model.
 *
 * <p><b>Why it is not in the block state.</b> A cell's shape depends on where it sits in its tower or path, which no
 * block state says. Each formed frame registers its cells here per level when it forms — on the server in the
 * controller's scan, on the client when the controller's update tag arrives — and the frame's blocks look their
 * shape up by position. A shape is built once per cell at registration (ADR-023), never in {@code getShape}.
 *
 * <p>The model rises above the blocks it hides (the heads reach 8 3/4 blocks, the paths 8), so some of its cells hold
 * no frame block at all; the controller fills those with quarry shell blocks ({@link #cells}) that take their shape
 * from here too. Only the towers and the paths count: the bridge, the cart and the drill move as the quarry digs
 * (MOD-952), so they are drawn and never bumped into.
 */
public final class QuarryFrameShapes {

	private static final float PIXEL = 1.0F / 16.0F;

	/** Per level: every registered cell's shape, by packed position. */
	private static final Map<Level, Map<Long, VoxelShape>> CELLS = Collections.synchronizedMap(new WeakHashMap<>());
	/** Per level: the cells each controller registered, by its packed position — what to take back. */
	private static final Map<Level, Map<Long, Map<Long, VoxelShape>>> FRAMES =
			Collections.synchronizedMap(new WeakHashMap<>());

	private QuarryFrameShapes() {
	}

	/**
	 * The shape of the cell at {@code pos} if a formed frame registered it, or {@code null} — then the block falls back
	 * to its loose shape, which covers the moment between a client seeing the blocks formed and the frame's update
	 * tag arriving. Only a {@link Level} knows; any other getter answers {@code null}.
	 */
	@Nullable
	public static VoxelShape shapeAt(BlockGetter getter, BlockPos pos) {
		if (!(getter instanceof Level level)) {
			return null;
		}
		Map<Long, VoxelShape> cells = CELLS.get(level);
		return cells == null ? null : cells.get(pos.asLong());
	}

	/** Registers the frame of the controller at {@code controller} in {@code level}, replacing any it had. */
	public static void register(Level level, BlockPos controller, QuarryFrameLayout layout) {
		unregister(level, controller);
		Map<Long, VoxelShape> frame = cells(layout);
		FRAMES.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>()).put(controller.asLong(), frame);
		CELLS.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>()).putAll(frame);
	}

	/** The cells the controller at {@code controller} registered, or {@code null}. */
	@Nullable
	public static Map<Long, VoxelShape> registered(Level level, BlockPos controller) {
		Map<Long, Map<Long, VoxelShape>> frames = FRAMES.get(level);
		return frames == null ? null : frames.get(controller.asLong());
	}

	/**
	 * The controller whose formed frame holds the cell at {@code pos}, or {@code null}: what a right click on any part
	 * of the frame opens. A level holds few frames, so they are simply looked through.
	 */
	@Nullable
	public static BlockPos controllerOf(Level level, BlockPos pos) {
		Map<Long, Map<Long, VoxelShape>> frames = FRAMES.get(level);
		if (frames == null) {
			return null;
		}
		long cell = pos.asLong();
		for (Map.Entry<Long, Map<Long, VoxelShape>> frame : frames.entrySet()) {
			if (frame.getValue().containsKey(cell)) {
				return BlockPos.of(frame.getKey());
			}
		}
		return null;
	}

	/** Takes back the cells the controller at {@code controller} registered, if any. */
	public static void unregister(Level level, BlockPos controller) {
		Map<Long, Map<Long, VoxelShape>> frames = FRAMES.get(level);
		Map<Long, VoxelShape> frame = frames == null ? null : frames.remove(controller.asLong());
		Map<Long, VoxelShape> cells = CELLS.get(level);
		if (frame == null || cells == null) {
			return;
		}
		frame.forEach(cells::remove);
	}

	/**
	 * Every cell the frame's solid parts and blocks cover, with its shape: the model cut by the cell, empty where the
	 * model leaves a frame cell open (the lattice is thinner than its scaffolding).
	 */
	public static Map<Long, VoxelShape> cells(QuarryFrameLayout layout) {
		Map<Long, VoxelShape> shapes = new HashMap<>();
		layout.forEachCell((x, y, z) -> shapes.put(BlockPos.asLong(x, y, z), Shapes.empty()));
		for (Placement placement : QuarryFramePlacements.of(layout)) {
			float[][] boxes = boxes(placement.part());
			if (boxes == null) {
				continue;
			}
			for (float[] box : boxes) {
				cut(shapes, world(placement, box, layout.baseY()));
			}
		}
		shapes.replaceAll((pos, shape) -> shape.optimize());
		return shapes;
	}

	@Nullable
	private static float[][] boxes(Part part) {
		return switch (part) {
			case TOWER -> QuarryFrameBoxes.TOWER;
			case PATH -> QuarryFrameBoxes.PATH;
			case SADDLE, BRIDGE, CART, DRILL -> null;
		};
	}

	/**
	 * A part's box (pixels, part frame) in world blocks: {@code centre + R_Y(yaw) · (p − local)}. The yaw is a quarter
	 * turn, so the box stays axis-aligned and two corners are enough.
	 */
	private static double[] world(Placement placement, float[] box, int baseY) {
		double rad = Math.toRadians(placement.yaw());
		double cos = Math.round(Math.cos(rad));
		double sin = Math.round(Math.sin(rad));
		double[] a = turn(box[0], box[2], placement, cos, sin);
		double[] b = turn(box[3], box[5], placement, cos, sin);
		return new double[] {Math.min(a[0], b[0]), baseY + box[1] * PIXEL, Math.min(a[1], b[1]),
				Math.max(a[0], b[0]), baseY + box[4] * PIXEL, Math.max(a[1], b[1])};
	}

	private static double[] turn(float px, float pz, Placement placement, double cos, double sin) {
		double x = (px - placement.part().localX()) * PIXEL;
		double z = (pz - placement.part().localZ()) * PIXEL;
		return new double[] {placement.x() + x * cos + z * sin, placement.z() - x * sin + z * cos};
	}

	/** Adds the world box to every cell it crosses, cut to the cell. */
	private static void cut(Map<Long, VoxelShape> shapes, double[] box) {
		for (int x = (int) Math.floor(box[0]); x < Math.ceil(box[3]); x++) {
			for (int y = (int) Math.floor(box[1]); y < Math.ceil(box[4]); y++) {
				for (int z = (int) Math.floor(box[2]); z < Math.ceil(box[5]); z++) {
					double x0 = Math.max(box[0] - x, 0.0);
					double y0 = Math.max(box[1] - y, 0.0);
					double z0 = Math.max(box[2] - z, 0.0);
					double x1 = Math.min(box[3] - x, 1.0);
					double y1 = Math.min(box[4] - y, 1.0);
					double z1 = Math.min(box[5] - z, 1.0);
					if (x1 - x0 < 1.0E-4 || y1 - y0 < 1.0E-4 || z1 - z0 < 1.0E-4) {
						continue;
					}
					shapes.merge(BlockPos.asLong(x, y, z), Shapes.box(x0, y0, z0, x1, y1, z1), Shapes::or);
				}
			}
		}
	}

	/** The cells of {@link #cells} that hold some of the model, for placing shell blocks; read-only view. */
	public static List<Long> solidCells(Map<Long, VoxelShape> cells) {
		return cells.entrySet().stream().filter(e -> !e.getValue().isEmpty()).map(Map.Entry::getKey).toList();
	}
}
