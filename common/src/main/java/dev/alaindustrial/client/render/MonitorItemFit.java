package dev.alaindustrial.client.render;

/**
 * Where and how large a monitoring panel draws its watched item (MOD-863), computed from the item
 * model's own extents rather than from one constant for every shape.
 *
 * <p>The panel's screen is an opaque plane recessed {@code 0.6/16} behind the block face. The item is
 * flattened to a thin slab in front of it; before MOD-863 the slab was CENTRED on the screen plane, so
 * the back half of every 3D model (a block's isometric cube, a volumetric item) sat behind the opaque
 * screen and was cut off — only flat items, which have no depth, looked right. Here the slab's BACK is
 * rested on the screen, its depth is squeezed into the recess so nothing pokes out of the block, and a
 * model wider than an inventory slot is shrunk to fit its cell instead of spilling onto the neighbour.
 *
 * <p>Minecraft-free on purpose: the renderer measures the extents, this class only does the arithmetic,
 * so the rule is checked by a plain unit test.
 */
public final class MonitorItemFit {

	/** The screen plane, measured from block centre along the panel's outward axis (model Z 0.6/16). */
	public static final float SCREEN_PLANE = 0.5F - 0.6F / 16.0F;

	/** How far in front of the screen the back of the item rests, so the two never fight for depth. */
	public static final float SCREEN_CLEARANCE = 0.002F;

	/** The front of the item stays this close to the block face at most: inside the block's render box. */
	public static final float FRONT_LIMIT = 0.499F;

	/** The flattening a model of ordinary depth gets: enough to keep its shading, thin enough to read. */
	public static final float MAX_DEPTH_SCALE = 0.03F;

	/** World size of one inventory-slot unit on the screen; a flat item is drawn this wide. */
	public static final float BASE_SCALE = 0.38F;

	/** Half a slot: an item model whose extents stay within this fits the cell at {@link #BASE_SCALE}. */
	public static final float SLOT_HALF = 0.5F;

	private MonitorItemFit() {
	}

	/**
	 * @param scale the uniform X/Y scale of the item, one slot unit to world
	 * @param depthScale the Z scale that flattens the model into the recess
	 * @param z where the model's origin goes, measured outward from block centre
	 */
	public record Fit(float scale, float depthScale, float z) {
	}

	/** The fit of an item that reports no extents: a flat item centred on its slot. */
	public static final Fit DEFAULT = new Fit(BASE_SCALE, MAX_DEPTH_SCALE, SCREEN_PLANE + SCREEN_CLEARANCE);

	/** Fits a model whose drawn geometry spans the given box, in item-model units around its origin. */
	public static Fit fit(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
		float reach = Math.max(Math.max(Math.abs(minX), Math.abs(maxX)),
				Math.max(Math.abs(minY), Math.abs(maxY)));
		float scale = reach > SLOT_HALF ? BASE_SCALE * SLOT_HALF / reach : BASE_SCALE;
		float depth = maxZ - minZ;
		float room = FRONT_LIMIT - (SCREEN_PLANE + SCREEN_CLEARANCE);
		float depthScale = depth > 0.0F ? Math.min(MAX_DEPTH_SCALE, room / depth) : MAX_DEPTH_SCALE;
		float z = SCREEN_PLANE + SCREEN_CLEARANCE - depthScale * minZ;
		return new Fit(scale, depthScale, z);
	}
}
