package dev.alaindustrial.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Field;
import java.util.List;

/** A {@code float} knob. */
final class FloatField extends ConfigField {
	private final float fallback;

	FloatField(Field field, Knob knob) {
		super(field, knob);
		this.fallback = liveValue();
	}

	private float liveValue() {
		return ((Float) live()).floatValue();
	}

	@Override
	Runnable stage(JsonObject body, JsonObject recordedDefaults, List<String> adopted) {
		float v = KnobJson.getFloat(body, key, fallback);
		if (KnobJson.present(body, key) && Float.compare(v, fallback) != 0) {
			JsonPrimitive was = KnobJson.recordedNumber(recordedDefaults, key);
			// Compared as a FLOAT: the file's 0.45 read as a double is 0.4500000000000000111,
			// while the knob's 0.45f widens to 0.4499999880790710 — every float knob would look
			// hand-edited forever if the two were compared at double precision.
			if (was != null && Float.compare(was.getAsFloat(), v) == 0) {
				recordAdoption(adopted, key, v, fallback);
				v = fallback;
			}
		}
		if (rejects(v)) {
			float replacement = Double.isNaN(floorTo) ? fallback : (float) floorTo;
			warnOutOfRange(key, v, replacement);
			v = replacement;
		}
		float applied = v;
		return () -> assign(applied);
	}

	@Override
	void write(JsonObject out) {
		writeComment(out);
		out.addProperty(key, liveValue());
	}

	@Override
	void writeDefault(JsonObject out) {
		out.addProperty(key, fallback);
	}

	@Override
	void resetToDefault() {
		assign(fallback);
	}
}
