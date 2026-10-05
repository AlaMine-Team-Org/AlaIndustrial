package dev.alaindustrial.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every tunable of the knob holders, collected once by reading the {@link Knob} annotation each field
 * carries (MOD-553). The loader and the canonical writer walk this list instead of repeating each field
 * five times (declaration, staged read, clamp, commit, serialize).
 *
 * <p><b>Order is deterministic and does NOT come from reflection.</b> {@code getDeclaredFields()}
 * returns fields in no specified order — the JLS gives no guarantee, HotSpot merely happens to return
 * class-file order — so relying on it would make the canonical file's key order a property of the JVM
 * it was written on, and a JVM that reordered them would rewrite every operator's file with the same
 * values in a different sequence. The registry is therefore sorted by section (enum order) and then by
 * key name: a total order derived only from data the holders declare, identical everywhere. The
 * section objects and each key's own {@code _comment_} carry the feature grouping instead.
 *
 * <p><b>Built after the holders are initialised.</b> Each entry captures its field's compiled default
 * when the registry is scanned, and Java runs static initializers in source order, so a holder that
 * scans itself must do it textually BELOW its knob declarations, or it captures zeroes.
 */
public final class KnobRegistry {

	private final List<Class<?>> holders;
	private final List<ConfigField> fields;

	private KnobRegistry(List<Class<?>> holders, List<ConfigField> fields) {
		this.holders = holders;
		this.fields = fields;
	}

	/**
	 * Read every {@link Knob}-annotated field of {@code holders} into the registry. Runs before any file
	 * can be loaded — which is what makes each entry's captured fallback the value COMPILED into this
	 * build rather than whatever a file last applied.
	 *
	 * <p>A field annotated in a way the loader cannot honour (non-public, final, or of an unsupported
	 * type) fails here instead of being skipped. A silently ignored knob — neither loaded nor saved — is
	 * exactly the failure this registry exists to prevent.
	 *
	 * <p><b>A knob name is unique across ALL holders</b> (ADR-034, MOD-710): the json key is the field name
	 * and one key lives in one section of one file, so two holders declaring {@code foo} would have the
	 * second silently shadow the first in the file. A duplicate fails here, naming both holders — which is
	 * also what a mistaken second listing of the same holder looks like. The order of {@code holders} does
	 * not change the registry: entries are sorted by section and key, a total order once names are unique.
	 */
	public static KnobRegistry scan(List<Class<?>> holders) {
		List<ConfigField> out = new ArrayList<>();
		Map<String, Class<?>> ownerOfKey = new HashMap<>();
		for (Class<?> holder : holders) {
			for (Field field : holder.getDeclaredFields()) {
				Knob knob = field.getAnnotation(Knob.class);
				if (knob == null) {
					continue;
				}
				int mods = field.getModifiers();
				if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
					throw new IllegalStateException("@Knob field " + field.getName()
							+ " must be public, static and non-final");
				}
				Class<?> first = ownerOfKey.putIfAbsent(field.getName(), holder);
				if (first != null) {
					throw new IllegalStateException("knob '" + field.getName() + "' is declared by both "
							+ first.getName() + " and " + holder.getName()
							+ ": a key is global to the config file, so a name may live in one holder only");
				}
				out.add(ConfigField.of(field, knob));
			}
		}
		out.sort(Comparator.comparingInt((ConfigField f) -> f.section.ordinal())
				.thenComparing((ConfigField f) -> f.key));
		return new KnobRegistry(List.copyOf(holders), List.copyOf(out));
	}

	/** The classes this registry scanned, in the order given to {@link #scan}. */
	public List<Class<?>> holders() {
		return holders;
	}

	/** Every knob in load order: section, then key. */
	List<ConfigField> fields() {
		return fields;
	}

	/** Every knob in load order, as its public view. */
	public List<KnobEntry> entries() {
		return fields.stream().map(f -> new KnobEntry(f.key, f.section, f.min, f.field)).toList();
	}

	/**
	 * The live value of every {@link Knob#clientVisible()} knob, keyed by knob name in registry order
	 * (MOD-695). What the server sends its clients; boxed as the knob's own type
	 * ({@code Integer}/{@code Float}/{@code Double}/{@code Boolean}). A fresh copy — the caller owns it.
	 */
	public Map<String, Object> clientVisibleValues() {
		Map<String, Object> out = new LinkedHashMap<>();
		for (ConfigField field : fields) {
			if (field.clientVisible) {
				out.put(field.key, field.live());
			}
		}
		return out;
	}

	/** The live value of every knob, in load order — what {@link #assignAll} puts back. */
	List<Object> liveValues() {
		return fields.stream().map(ConfigField::live).toList();
	}

	/** Write {@code values}, as returned by {@link #liveValues()}, back into the live fields. */
	void assignAll(List<Object> values) {
		for (int i = 0; i < fields.size(); i++) {
			fields.get(i).assign(values.get(i));
		}
	}

	/**
	 * Restore every tunable to the value compiled into this build — the state a fresh install starts
	 * from. Used when the file is absent (MOD-694) and when its schema is from the future: leaving
	 * whatever the previous load happened to apply would mean the server keeps running on a file nobody
	 * can read any more.
	 */
	void resetToDefaults() {
		for (ConfigField field : fields) {
			field.resetToDefault();
		}
	}
}
