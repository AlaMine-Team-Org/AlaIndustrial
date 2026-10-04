package dev.alaindustrial.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Typed reads out of a section body or the {@code builtinDefaults} block.
 *
 * <p>The {@code net.minecraft.util.GsonHelper}-equivalent contract, but on plain Gson so the config
 * mechanism carries no {@code net.minecraft} dependency and the L1 suite can exercise it: an
 * absent/null key yields {@code def}, a present key of the wrong type <b>throws</b> — which is what makes
 * a typo abort the whole load instead of silently applying a partial file. {@code _comment_*} keys are
 * never requested.
 */
final class KnobJson {

	private KnobJson() {
	}

	/** True when {@code key} carries an actual value in {@code o} (a json null counts as absent). */
	static boolean present(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && !e.isJsonNull();
	}

	static int getInt(JsonObject o, String key, int def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsInt();
		}
		throw new IllegalArgumentException("config key '" + key + "' must be a number, got " + e);
	}

	/** Float counterpart of {@link #getInt} — same absent-default / wrong-type-throws contract. */
	static float getFloat(JsonObject o, String key, float def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsFloat();
		}
		throw new IllegalArgumentException("config key '" + key + "' must be a number, got " + e);
	}

	/**
	 * Double counterpart of {@link #getInt} — same absent-default / wrong-type-throws contract, read at
	 * double precision so {@code 0.02} is not rewritten as float noise.
	 */
	static double getDouble(JsonObject o, String key, double def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsDouble();
		}
		throw new IllegalArgumentException("config key '" + key + "' must be a number, got " + e);
	}

	/** Boolean counterpart of {@link #getInt} — same absent-default / wrong-type-throws contract. */
	static boolean getBool(JsonObject o, String key, boolean def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
			return e.getAsBoolean();
		}
		throw new IllegalArgumentException("config key '" + key + "' must be a boolean, got " + e);
	}

	/**
	 * The recorded built-in default for {@code key} as a raw primitive, or {@code null} when it is
	 * absent or not a number. Read at the knob's OWN precision by the caller: widening a recorded
	 * {@code 0.45} to double and comparing it against a {@code float} knob's {@code 0.45f} would never
	 * match, and every float knob would look hand-edited forever.
	 */
	static JsonPrimitive recordedNumber(JsonObject recorded, String key) {
		JsonElement e = recorded.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()
				? e.getAsJsonPrimitive() : null;
	}

	/** Boolean counterpart of {@link #recordedNumber}. */
	static JsonPrimitive recordedBoolean(JsonObject recorded, String key) {
		JsonElement e = recorded.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()
				? e.getAsJsonPrimitive() : null;
	}
}
