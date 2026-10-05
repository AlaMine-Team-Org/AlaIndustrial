package dev.alaindustrial.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Field;
import java.util.List;

/** A {@code double} knob. */
final class DoubleField extends ConfigField {
	private final double fallback;

	DoubleField(Field field, Knob knob) {
		super(field, knob);
		this.fallback = liveValue();
	}

	private double liveValue() {
		return ((Double) live()).doubleValue();
	}

	@Override
	Runnable stage(JsonObject body, JsonObject recordedDefaults, List<String> adopted) {
		double v = KnobJson.getDouble(body, key, fallback);
		if (KnobJson.present(body, key) && Double.compare(v, fallback) != 0) {
			JsonPrimitive was = KnobJson.recordedNumber(recordedDefaults, key);
			if (was != null && Double.compare(was.getAsDouble(), v) == 0) {
				recordAdoption(adopted, key, v, fallback);
				v = fallback;
			}
		}
		if (rejects(v)) {
			double replacement = Double.isNaN(floorTo) ? fallback : floorTo;
			warnOutOfRange(key, v, replacement);
			v = replacement;
		}
		double applied = v;
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
