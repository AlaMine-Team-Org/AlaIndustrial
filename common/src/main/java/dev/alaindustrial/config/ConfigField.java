package dev.alaindustrial.config;

import com.google.gson.JsonObject;
import dev.alaindustrial.Industrialization;
import java.lang.reflect.Field;
import java.util.List;

/**
 * One tunable's read/validate/write behaviour, bound to its declared {@link Field} by reflection.
 *
 * <p>Subclasses exist per primitive type for two reasons that outlive the lambdas they replaced: the
 * serialized json must keep the exact numeric form the knob has (an int must not start rendering as
 * a double), and each type has to compare the recorded built-in default at its OWN precision.
 *
 * <p>The range lives on the base class as plain {@code double}s. An absent bound is
 * {@link Double#NEGATIVE_INFINITY}, which fails every comparison on its own — so "no bound" needs no
 * null check and no separate code path.
 */
abstract class ConfigField {
	/** Json key of the knob — always the java field's own name, so the two cannot drift. */
	final String key;
	/** Which JSON object this key lives in (MOD-402); declared on the field, never in a side table. */
	final Section section;
	final String doc;
	/** The declared field itself; read and written reflectively. See {@link KnobRegistry#scan}. */
	final Field field;
	/** Lowest accepted value, or {@code -inf} when the knob declares no bound. */
	final double min;
	/** True when {@link #min} is exclusive (the value must be strictly greater). */
	final boolean exclusive;
	/** Replacement for a rejected value, or {@link Double#NaN} for "restore the compiled default". */
	final double floorTo;
	/** Sent to clients and read there through {@code ServerBalance} (MOD-695). */
	final boolean clientVisible;

	ConfigField(Field field, Knob knob) {
		this.key = field.getName();
		this.section = knob.section();
		this.doc = knob.doc();
		this.field = field;
		this.min = knob.min();
		this.exclusive = knob.exclusive();
		this.floorTo = knob.floorTo();
		this.clientVisible = knob.clientVisible();
	}

	/**
	 * The entry for {@code field}'s primitive type. A type with no reader fails here rather than being
	 * skipped: a knob the loader cannot handle must not become a knob the loader silently ignores.
	 */
	static ConfigField of(Field field, Knob knob) {
		Class<?> type = field.getType();
		if (type == int.class) {
			return new IntField(field, knob);
		}
		if (type == float.class) {
			return new FloatField(field, knob);
		}
		if (type == double.class) {
			return new DoubleField(field, knob);
		}
		if (type == boolean.class) {
			return new BoolField(field, knob);
		}
		throw new IllegalStateException("@Knob field " + field.getName()
				+ " has type " + type.getName() + ", which the config loader cannot read");
	}

	/**
	 * The live value, boxed. The field is public, static and non-final (checked in
	 * {@link KnobRegistry#scan}), so an {@link IllegalAccessException} here cannot be a runtime state —
	 * only a programming error, which is why it is rethrown rather than handled.
	 */
	Object live() {
		try {
			return field.get(null);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("config field " + key + " is not readable", e);
		}
	}

	/** Write {@code value} into the live field. Same accessibility contract as {@link #live()}. */
	void assign(Object value) {
		try {
			field.set(null, value);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("config field " + key + " is not writable", e);
		}
	}

	/** True when {@code v} is below this knob's declared bound and must be replaced. */
	boolean rejects(double v) {
		return exclusive ? v <= min : v < min;
	}

	/**
	 * Parse and validate this field out of {@code body} — its own section's object — returning the
	 * action that commits it. An absent key stages the compiled default, never the live value, so a
	 * load is the builtin baseline with the file laid over it.
	 * Throws if the key is present with a wrong type: that is what aborts
	 * the whole load before any field is applied, keeping {@link ConfigFile#loadFrom} all-or-nothing.
	 *
	 * <p>{@code recordedDefaults} is the file's {@link ConfigSchema#BUILTIN_DEFAULTS_KEY} block
	 * (MOD-553). When the file's value for this key is exactly the default recorded beside it AND this
	 * build ships a different default, the build's default wins and the swap is appended to
	 * {@code adopted} for the load log (ADR-033).
	 */
	abstract Runnable stage(JsonObject body, JsonObject recordedDefaults, List<String> adopted);

	/**
	 * Append this knob's COMPILED default (the value captured when the registry was built, never the
	 * live one) to the machine-owned defaults block. See {@link ConfigFile#snapshot()}.
	 */
	abstract void writeDefault(JsonObject out);

	/** Append the current live value (and its doc comment) to its section in the canonical snapshot. */
	abstract void write(JsonObject out);

	/**
	 * Put the field back to the value compiled into this build (captured when the registry was
	 * built, before any file could touch it). Used by {@link KnobRegistry#resetToDefaults()}.
	 */
	abstract void resetToDefault();

	/** Add the {@code _comment_<field>} doc right before its field (Gson keeps insertion order); never read. */
	void writeComment(JsonObject out) {
		out.addProperty("_comment_" + key, doc);
	}

	/** Record an adopted default for the one-line load report: {@code key: old -> new}. */
	static void recordAdoption(List<String> adopted, String key, Object from, Object to) {
		adopted.add(key + ": " + from + " -> " + to);
	}

	/**
	 * Report an out-of-range value being replaced by a safe one. Shared by all three numeric
	 * field types so the three call sites cannot drift apart in wording, and so the player sees
	 * the same line whichever key they got wrong. Says what was rejected AND what replaced it —
	 * "value ignored" alone would leave the player guessing what the mod is actually running on.
	 */
	static void warnOutOfRange(String key, Number rejected, Number replacement) {
		Industrialization.LOGGER.warn(
				"[config] key '{}' has out-of-range value {} — using {} instead. The file will be "
						+ "rewritten with the replacement, so your edit will not survive this run.",
				key, rejected, replacement);
	}
}
