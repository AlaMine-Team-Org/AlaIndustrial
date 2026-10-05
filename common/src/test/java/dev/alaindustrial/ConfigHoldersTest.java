package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.config.Knob;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The knob holders against the golden file (MOD-710, batch 0): every {@code @Knob} field of every
 * holder is exactly one key of the operator's file, and no knob name is declared twice.
 *
 * <p>Today {@link Config} is the only holder. When knobs move to per-subsystem holders (ADR-034) the
 * list below grows and this test is what proves the move lost nothing and duplicated nothing — while
 * {@link ConfigGoldenFileTest} proves the file itself did not change.
 */
class ConfigHoldersTest {

	/** Every class the registry scans; the golden file, not this list, is the independent side. */
	private static final List<Class<?>> HOLDERS = Config.REGISTRY.holders();

	/** The eleven sections of the file, in the order the golden file renders them. */
	private static final List<String> SECTIONS = List.of("global", "generators", "machines", "storage",
			"cables", "safety", "network", "tools", "logistics", "player", "world");

	@Test
	void everyKnobOfEveryHolderIsOneKeyOfTheGoldenFile() {
		String golden = ConfigText.golden();
		Set<String> recorded = new TreeSet<>(ConfigText.keysOf(golden, "builtinDefaults"));
		List<String> declared = new ArrayList<>();
		for (Class<?> holder : HOLDERS) {
			declared.addAll(knobNames(holder));
		}
		assertEquals(recorded.size(), ConfigText.keysOf(golden, "builtinDefaults").size(),
				"a key recorded twice in builtinDefaults");
		assertEquals(recorded, new TreeSet<>(declared),
				"the @Knob fields of all holders must be exactly the builtinDefaults keys of the golden file");
		assertEquals(recorded.size(), declared.size(), "a knob declared by two holders");
		assertTrue(declared.size() > 400, "sanity: every tunable should be counted, got " + declared.size());
	}

	/** The section members of the golden file are the same keys as its builtinDefaults block. */
	@Test
	void theSectionsHoldEveryRecordedKeyOnce() {
		String golden = ConfigText.golden();
		List<String> inSections = new ArrayList<>();
		for (String section : SECTIONS) {
			List<String> keys = ConfigText.keysOf(golden, section);
			assertTrue(!keys.isEmpty(), "section " + section + " is empty or missing in the golden file");
			inSections.addAll(keys);
		}
		assertEquals(inSections.size(), new TreeSet<>(inSections).size(), "a key written in two sections");
		assertEquals(new TreeSet<>(ConfigText.keysOf(golden, "builtinDefaults")), new TreeSet<>(inSections),
				"sections and builtinDefaults must name the same knobs");
	}

	/** No knob name is declared by two holders (trivially true with one holder; the guard for the split). */
	@Test
	void noKnobNameIsDeclaredTwice() {
		Map<String, String> owner = new HashMap<>();
		for (Class<?> holder : HOLDERS) {
			for (String name : knobNames(holder)) {
				String first = owner.putIfAbsent(name, holder.getSimpleName());
				assertNull(first, "knob " + name + " is declared by " + first + " and "
						+ holder.getSimpleName());
			}
		}
	}

	private static List<String> knobNames(Class<?> holder) {
		List<String> out = new ArrayList<>();
		for (Field field : holder.getDeclaredFields()) {
			if (field.getAnnotation(Knob.class) != null) {
				out.add(field.getName());
			}
		}
		return out;
	}
}
