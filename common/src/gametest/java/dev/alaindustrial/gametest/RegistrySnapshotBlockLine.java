package dev.alaindustrial.gametest;

/**
 * The text of one {@code block} line of the registry snapshot (MOD-741), built from values the scenario has
 * already read off the live block. Kept free of Minecraft types so the L1 lane can load it and pin the format
 * ({@code RegistrySnapshotBlockLineTest}); {@link RegistrySnapshotScenarios} only does the reading.
 *
 * <p><b>The line.</b>
 * {@code block <id> destroy= tool= light= push= occludes= sound= blast= map=[ conditional tokens] loot=}.
 * The tokens before {@code loot=} that every line carries are the properties the mod actually sets on most of
 * its blocks. The conditional ones — {@code lightmax= friction= speed= jump= bounce= lava_ignites
 * replaceable liquid}, always in that order — are printed only when the block differs from the vanilla
 * default of {@code BlockBehaviour.Properties.of()} (or, for {@code lightmax=}, from its own default state):
 * a column that reads the same on all 142 lines is one nobody reviews. A new conditional token goes in
 * {@link #toLine()} before {@code loot=}, with its default spelled out here.
 *
 * <p>Numbers are written by {@link Float#toString(float)} and {@link Integer#toString(int)}, the way the
 * reference has always written {@code destroy=}.
 */
record RegistrySnapshotBlockLine(
		String id,
		float destroy,
		boolean tool,
		int light,
		int lightMax,
		String push,
		boolean occludes,
		String sound,
		float blast,
		String map,
		float friction,
		float speed,
		float jump,
		float bounce,
		boolean lavaIgnites,
		boolean replaceable,
		boolean liquid,
		String loot) {

	/** Vanilla {@code Properties.of()} friction. */
	static final float DEFAULT_FRICTION = 0.6f;

	/** Vanilla {@code Properties.of()} speed factor. */
	static final float DEFAULT_SPEED = 1.0f;

	/** Vanilla {@code Properties.of()} jump factor. */
	static final float DEFAULT_JUMP = 1.0f;

	/** Vanilla {@code Properties.of()} bounce restitution. */
	static final float DEFAULT_BOUNCE = 0.0f;

	/** The reference line for this block. */
	String toLine() {
		StringBuilder out = new StringBuilder("block ").append(id)
				.append(" destroy=").append(Float.toString(destroy))
				.append(" tool=").append(yesNo(tool))
				.append(" light=").append(Integer.toString(light))
				.append(" push=").append(push)
				.append(" occludes=").append(yesNo(occludes))
				.append(" sound=").append(sound)
				.append(" blast=").append(Float.toString(blast))
				.append(" map=").append(map);
		if (lightMax > light) {
			out.append(" lightmax=").append(Integer.toString(lightMax));
		}
		appendIfNotDefault(out, "friction", friction, DEFAULT_FRICTION);
		appendIfNotDefault(out, "speed", speed, DEFAULT_SPEED);
		appendIfNotDefault(out, "jump", jump, DEFAULT_JUMP);
		appendIfNotDefault(out, "bounce", bounce, DEFAULT_BOUNCE);
		appendFlag(out, "lava_ignites", lavaIgnites);
		appendFlag(out, "replaceable", replaceable);
		appendFlag(out, "liquid", liquid);
		return out.append(" loot=").append(loot).toString();
	}

	/** {@link Float#compare} and not {@code !=}: a {@code -0.0} is a different value and gets printed. */
	private static void appendIfNotDefault(StringBuilder out, String token, float value, float vanillaDefault) {
		if (Float.compare(value, vanillaDefault) != 0) {
			out.append(' ').append(token).append('=').append(Float.toString(value));
		}
	}

	private static void appendFlag(StringBuilder out, String token, boolean set) {
		if (set) {
			out.append(' ').append(token);
		}
	}

	private static String yesNo(boolean value) {
		return value ? "yes" : "no";
	}
}
