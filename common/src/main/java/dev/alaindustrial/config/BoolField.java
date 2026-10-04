package dev.alaindustrial.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Field;
import java.util.List;

/** A {@code boolean} knob. It has no range, so nothing is ever clamped. */
final class BoolField extends ConfigField {
	private final boolean fallback;

	BoolField(Field field, Knob knob) {
		super(field, knob);
		this.fallback = liveValue();
	}

	private boolean liveValue() {
		return ((Boolean) live()).booleanValue();
	}

	@Override
	Runnable stage(JsonObject body, JsonObject recordedDefaults, List<String> adopted) {
		boolean v = KnobJson.getBool(body, key, fallback);
		if (KnobJson.present(body, key) && v != fallback) {
			JsonPrimitive was = KnobJson.recordedBoolean(recordedDefaults, key);
			if (was != null && was.getAsBoolean() == v) {
				recordAdoption(adopted, key, v, fallback);
				v = fallback;
			}
		}
		boolean applied = v;
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
