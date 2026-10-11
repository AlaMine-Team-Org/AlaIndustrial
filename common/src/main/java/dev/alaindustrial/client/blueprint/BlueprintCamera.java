package dev.alaindustrial.client.blueprint;

/**
 * The blueprint camera (MOD-797) as plain arithmetic: a yaw around the vertical axis, a pitch around the
 * horizontal one, then a half turn around the view axis — the same turn vanilla's inventory doll takes,
 * because the picture-in-picture texture is drawn with its Y axis pointing down.
 *
 * <p>The scene renderer builds its quaternion from the same three angles in the same order, and the
 * screen projects callout anchors through {@link #project}; keeping both on one formula is what makes a
 * callout land on its block. No Minecraft type here, so the formula is tested on the L1 lane.
 *
 * @param yawDegrees   turn around the vertical axis
 * @param pitchDegrees tilt toward the viewer, clamped by {@link #withPitch}
 */
public record BlueprintCamera(float yawDegrees, float pitchDegrees) {

	/** The angle the sheet opens with: three quarters, looking down on the blocks. */
	public static final BlueprintCamera DEFAULT = new BlueprintCamera(215f, 28f);

	public static final float MIN_PITCH = 5f;
	public static final float MAX_PITCH = 80f;

	/** The camera turned by {@code deltaYaw} degrees. */
	public BlueprintCamera turned(float deltaYaw) {
		return new BlueprintCamera(yawDegrees + deltaYaw, pitchDegrees);
	}

	/** The camera tilted to {@code pitch}, kept between {@link #MIN_PITCH} and {@link #MAX_PITCH}. */
	public BlueprintCamera withPitch(float pitch) {
		return new BlueprintCamera(yawDegrees, Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch)));
	}

	/** Yaw in radians, for the renderer's quaternion. */
	public float yawRadians() {
		return (float) Math.toRadians(yawDegrees);
	}

	/** The X turn for the renderer's quaternion, in radians: minus the pitch, so the camera looks down. */
	public float pitchRadians() {
		return (float) -Math.toRadians(pitchDegrees);
	}

	/**
	 * Where scene point ({@code x}, {@code y}, {@code z}) — already relative to the scene centre — lands on
	 * screen, as an offset in blocks from the picture's centre (multiply by the scale in pixels per block).
	 * Screen X grows right, screen Y grows down.
	 */
	public double[] project(double x, double y, double z) {
		double yaw = Math.toRadians(yawDegrees);
		double pitch = Math.toRadians(pitchDegrees);
		double x1 = x * Math.cos(yaw) + z * Math.sin(yaw);
		double z1 = -x * Math.sin(yaw) + z * Math.cos(yaw);
		// The renderer turns by -pitch: a positive pitch looks down on the blocks.
		double y2 = y * Math.cos(pitch) + z1 * Math.sin(pitch);
		return new double[] {-x1, -y2};
	}
}
