package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.KnobEntry;
import dev.alaindustrial.config.KnobRegistry;
import dev.alaindustrial.config.Section;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code KnobRegistry} over SEVERAL holder classes (MOD-710, batch 2.0): the mechanism the per-subsystem
 * slices of ADR-034 stand on. {@link Config} is the only real holder today, so the holders here are
 * fixtures; what is proved is that a second holder is merged into the one file order and that a knob name
 * declared twice — across holders, or by listing one holder twice — is refused instead of shadowed.
 *
 * <p>The holders are {@code public} because the registry reads and writes their fields reflectively from
 * another package; a non-public holder would fail at the first {@code get}, not at {@code scan}.
 */
class KnobRegistryHoldersTest {

	/** Two knobs in two sections. */
	public static final class HolderA {
		@Knob(section = Section.MACHINES, doc = "fixture")
		public static int alpha = 1;
		@Knob(section = Section.GLOBAL, doc = "fixture")
		public static float zeta = 2.0f;
	}

	/** A GLOBAL knob that interleaves with {@code HolderA}'s by key once the two holders are merged. */
	public static final class HolderB {
		@Knob(section = Section.GLOBAL, doc = "fixture")
		public static double beta = 3.0;
	}

	/** Declares {@code beta} again: the same json key as {@link HolderB#beta}. */
	public static final class HolderBeta {
		@Knob(section = Section.TOOLS, doc = "fixture")
		public static boolean beta = true;
	}

	private static List<String> keys(KnobRegistry registry) {
		return registry.entries().stream().map(KnobEntry::key).toList();
	}

	@Test
	void twoHoldersAreMergedIntoOneOrderBySectionThenKey() {
		KnobRegistry registry = KnobRegistry.scan(List.of(HolderA.class, HolderB.class));

		assertEquals(List.of("beta", "zeta", "alpha"), keys(registry),
				"GLOBAL (beta, zeta — from both holders) before MACHINES (alpha)");
		assertEquals(List.of(HolderA.class, HolderB.class), registry.holders());
	}

	@Test
	void theOrderOfTheHoldersDoesNotChangeTheRegistry() {
		assertEquals(keys(KnobRegistry.scan(List.of(HolderA.class, HolderB.class))),
				keys(KnobRegistry.scan(List.of(HolderB.class, HolderA.class))));
	}

	@Test
	void aKnobNameDeclaredByTwoHoldersIsRefusedNamingBoth() {
		IllegalStateException refused = assertThrows(IllegalStateException.class,
				() -> KnobRegistry.scan(List.of(HolderB.class, HolderBeta.class)));

		String message = refused.getMessage();
		assertTrue(message.contains("'beta'"), message);
		assertTrue(message.contains(HolderB.class.getName()) && message.contains(HolderBeta.class.getName()),
				message);
	}

	@Test
	void listingOneHolderTwiceIsRefusedToo() {
		IllegalStateException refused = assertThrows(IllegalStateException.class,
				() -> KnobRegistry.scan(List.of(HolderA.class, HolderA.class)));

		assertTrue(refused.getMessage().contains(HolderA.class.getName()), refused.getMessage());
	}

	@Test
	void theRealRegistryDeclaresEveryKnobNameOnce() {
		List<String> keys = keys(Config.REGISTRY);

		assertEquals(keys.size(), keys.stream().distinct().count(), "a knob name appears twice in Config.REGISTRY");
	}
}
