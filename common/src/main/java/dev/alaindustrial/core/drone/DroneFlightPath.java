package dev.alaindustrial.core.drone;

/**
 * A drone's flight as a client draws and hears it (MOD-858, owner's round 2): the route's corners as a polyline, a
 * point {@code travel} (0 dock .. 1 goal) of the way along it by length, and the corners rounded off so a turn reads as
 * a curve, not as a snap. Minecraft-free: the renderer, the flight sound and the L1 lane ({@code DroneFlightPathTest})
 * share one copy.
 *
 * <p><b>Rounding stays in the air.</b> A rounded point is the mean of points of the polyline no farther than
 * {@link #ROUNDING} along it, and a route's corners are cell centres whose legs run through open cells — so the mean of
 * a corner's neighbourhood lies within that corner's cell and its two neighbours on the route. The ends are not
 * rounded: the drone leaves the dock and meets its goal exactly.
 */
public final class DroneFlightPath {

	/** How far along the route a corner is rounded over, either side, in blocks. */
	public static final float ROUNDING = 0.45F;
	private static final int ROUNDING_SAMPLES = 5;

	private final float[] xyz;
	private final float[] at;
	private final float length;

	/** A path through {@code xyz} (x, y, z triplets, at least one point). */
	public DroneFlightPath(float[] xyz) {
		if (xyz.length < 3 || xyz.length % 3 != 0) {
			throw new IllegalArgumentException("a flight path needs whole x, y, z triplets, got " + xyz.length);
		}
		this.xyz = xyz.clone();
		int count = xyz.length / 3;
		this.at = new float[count];
		float total = 0.0F;
		for (int i = 1; i < count; i++) {
			float dx = xyz[i * 3] - xyz[i * 3 - 3];
			float dy = xyz[i * 3 + 1] - xyz[i * 3 - 2];
			float dz = xyz[i * 3 + 2] - xyz[i * 3 - 1];
			total += (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			at[i] = total;
		}
		this.length = total;
	}

	/** The path's length in blocks. */
	public float length() {
		return length;
	}

	/** The point {@code travel} (clamped to 0..1) of the way along the polyline, by length, into {@code out}. */
	public void point(float travel, float[] out) {
		pointAtDistance(Math.clamp(travel, 0.0F, 1.0F) * length, out);
	}

	/**
	 * The point {@code travel} of the way along the path with its corners rounded over {@link #ROUNDING}, into
	 * {@code out}. Equals {@link #point} at both ends.
	 */
	public void rounded(float travel, float[] out) {
		float s = Math.clamp(travel, 0.0F, 1.0F) * length;
		float window = Math.min(ROUNDING, Math.min(s, length - s));
		if (window <= 1.0E-4F) {
			pointAtDistance(s, out);
			return;
		}
		float sx = 0.0F;
		float sy = 0.0F;
		float sz = 0.0F;
		float[] p = new float[3];
		for (int k = 0; k < ROUNDING_SAMPLES; k++) {
			float d = s - window + 2.0F * window * k / (ROUNDING_SAMPLES - 1);
			pointAtDistance(d, p);
			sx += p[0];
			sy += p[1];
			sz += p[2];
		}
		out[0] = sx / ROUNDING_SAMPLES;
		out[1] = sy / ROUNDING_SAMPLES;
		out[2] = sz / ROUNDING_SAMPLES;
	}

	private void pointAtDistance(float s, float[] out) {
		int count = at.length;
		if (count == 1 || s <= 0.0F) {
			copy(0, out);
			return;
		}
		if (s >= length) {
			copy(count - 1, out);
			return;
		}
		int i = 1;
		while (i < count - 1 && at[i] < s) {
			i++;
		}
		float span = at[i] - at[i - 1];
		float f = span <= 0.0F ? 1.0F : (s - at[i - 1]) / span;
		out[0] = xyz[i * 3 - 3] + (xyz[i * 3] - xyz[i * 3 - 3]) * f;
		out[1] = xyz[i * 3 - 2] + (xyz[i * 3 + 1] - xyz[i * 3 - 2]) * f;
		out[2] = xyz[i * 3 - 1] + (xyz[i * 3 + 2] - xyz[i * 3 - 1]) * f;
	}

	private void copy(int i, float[] out) {
		out[0] = xyz[i * 3];
		out[1] = xyz[i * 3 + 1];
		out[2] = xyz[i * 3 + 2];
	}

	/** The box {@code {minX, minY, minZ, maxX, maxY, maxZ}} of every corner of an x, y, z polyline. */
	public static float[] bounds(float[] xyz) {
		float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE,
				-Float.MAX_VALUE};
		for (int i = 0; i + 2 < xyz.length; i += 3) {
			for (int a = 0; a < 3; a++) {
				box[a] = Math.min(box[a], xyz[i + a]);
				box[a + 3] = Math.max(box[a + 3], xyz[i + a]);
			}
		}
		return box;
	}
}
