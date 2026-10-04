package dev.alaindustrial.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Field;
import java.util.List;

/** An {@code int} knob. */
final class IntField extends ConfigField {
	private final int fallback;

	IntField(Field field, Knob knob) {
		super(field, knob);
		this.fallback = liveValue();
	}

	private int liveValue() {
		return ((Integer) live()).intValue();
	}

	@Override
	Runnable stage(JsonObject body, JsonObject recordedDefaults, List<String> adopted) {
		int v = KnobJson.getInt(body, key, fallback);
		if (KnobJson.present(body, key) && v != fallback) {
			JsonPrimitive was = KnobJson.recordedNumber(recordedDefaults, key);
			if (was != null && was.getAsInt() == v) {
				recordAdoption(adopted, key, v, fallback);
				v = fallback;
			}
		}
		if (rejects(v)) {
			int replacement = Double.isNaN(floorTo) ? fallback : (int) floorTo;
			// Load-bearing: machineEuPerTick 0 would divide by zero (MOD-169, pinned by ConfigFileTest);
			// the self-heal rewrites the file, so the replacement is logged rather than silent.
			warnOutOfRange(key, v, replacement);
			v = replacement;
		}
		int applied = v;
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
