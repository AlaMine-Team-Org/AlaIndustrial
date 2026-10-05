package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.KnobEntry;
import dev.alaindustrial.network.PayloadBudget;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * L1 for the config-sync wire format (MOD-695): what the server captures, that the bytes are stable and
 * lossless, and that even a snapshot of EVERY knob fits the payload budget.
 */
class KnobSnapshotTest {

	/**
	 * Every tunable field of every knob holder ({@code Config} and the per-subsystem holders, MOD-710),
	 * flagged or not — the upper bound of what a snapshot could hold.
	 */
	private static Map<String, Object> everyKnob() throws IllegalAccessException {
		Map<String, Object> out = new LinkedHashMap<>();
		for (Class<?> holder : Config.REGISTRY.holders()) {
			for (Field field : holder.getDeclaredFields()) {
				int mods = field.getModifiers();
				Class<?> type = field.getType();
				boolean tunable = type == int.class || type == float.class || type == double.class
						|| type == boolean.class;
				if (Modifier.isPublic(mods) && Modifier.isStatic(mods) && !Modifier.isFinal(mods) && tunable
						&& field.isAnnotationPresent(Knob.class)) {
					out.put(field.getName(), field.get(null));
				}
			}
		}
		return out;
	}

	/** The declared field behind a knob key, in whichever holder declares it (the registry knows). */
	private static Field knobField(String key) throws NoSuchFieldException {
		return Config.REGISTRY.entries().stream().filter(entry -> entry.key().equals(key)).findFirst()
				.map(KnobEntry::field).orElseThrow(() -> new NoSuchFieldException(key));
	}

	/**
	 * @implements MOD-695-SEND — the server captures exactly the flagged knobs, at their live values
	 * @covers MOD-695
	 */
	@Test
	void captureHoldsTheFlaggedKnobsAtTheirLiveValues() throws ReflectiveOperationException {
		KnobSnapshot captured = KnobSnapshot.capture();
		assertEquals(Config.REGISTRY.clientVisibleValues().keySet(), captured.keys());
		assertEquals(Config.euPerXp, captured.get("euPerXp"));
		assertEquals(Config.teleporterMaxPoints, captured.get("teleporterMaxPoints"));
		assertEquals(Config.levelXpMultiplier, captured.get("levelXpMultiplier"));

		for (String key : captured.keys()) {
			Field field = knobField(key);
			assertTrue(field.getAnnotation(Knob.class).clientVisible(), key + " is captured but not flagged");
		}
		long flagged = everyKnob().keySet().stream()
				.filter(key -> {
					try {
						return knobField(key).getAnnotation(Knob.class).clientVisible();
					} catch (NoSuchFieldException e) {
						throw new IllegalStateException(e);
					}
				}).count();
		assertEquals(flagged, captured.keys().size(), "every flagged knob is captured");
	}

	/** @implements MOD-695-SEND — the capture is a copy: changing a knob afterwards does not change it */
	@Test
	void captureIsACopyNotAView() {
		int before = Config.euPerXp;
		KnobSnapshot captured = KnobSnapshot.capture();
		try {
			Config.euPerXp = before + 11;
			assertEquals(before, captured.get("euPerXp"));
			assertEquals(before + 11, KnobSnapshot.capture().get("euPerXp"));
		} finally {
			Config.euPerXp = before;
		}
	}

	/** @implements MOD-695-ROUNDTRIP — every type survives the bytes, and the bytes are deterministic */
	@Test
	void encodingIsLosslessAndDeterministic() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("anInt", Integer.MIN_VALUE);
		values.put("aFloat", 0.45f);
		values.put("aDouble", 0.02);
		values.put("aBoolean", true);
		values.put("non\u00e9Ascii\u2014name", -1);
		KnobSnapshot snapshot = KnobSnapshot.of(values);
		KnobSnapshot back = KnobSnapshot.decode(snapshot.encode()).orElseThrow();
		assertEquals(snapshot, back);
		assertEquals(List.copyOf(values.keySet()), List.copyOf(back.keys()), "order is kept");
		assertArrayEquals(snapshot.encode(), KnobSnapshot.of(values).encode());
		assertEquals(0.45f, back.floatValue("aFloat", 0f), "a float is not widened on the way");
		assertEquals(0.02, back.doubleValue("aDouble", 0.0));
	}

	/** @implements MOD-695-ROUNDTRIP — a value of an unsupported type is a programming error */
	@Test
	void anUnsupportedValueTypeIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> KnobSnapshot.of(Map.of("aLong", 5L)));
	}

	/**
	 * @implements MOD-695-BUDGET — even a snapshot of all 459 knobs fits the payload's byte ceiling, so the
	 *     decoder's bound can never refuse a real server's snapshot
	 */
	@Test
	void everyKnobFitsTheBudget() throws IllegalAccessException {
		Map<String, Object> all = everyKnob();
		assertTrue(all.size() > 400, "found only " + all.size() + " knobs — the reflection floor is broken");
		int bytes = KnobSnapshot.of(all).encode().length;
		assertTrue(bytes <= PayloadBudget.MAX_CONFIG_SNAPSHOT_BYTES,
				"all knobs encode to " + bytes + " bytes, over the " + PayloadBudget.MAX_CONFIG_SNAPSHOT_BYTES + " budget");
		assertTrue(KnobSnapshot.capture().encode().length < bytes);
	}
}
