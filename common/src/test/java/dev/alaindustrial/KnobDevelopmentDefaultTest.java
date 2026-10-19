package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.config.ConfigFile;
import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.KnobRegistry;
import dev.alaindustrial.config.Section;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link Knob#devDefault()} (MOD-847): a development run takes the knob's development default as the build's
 * default, and every ADR-033 rule follows from that — an absent file is written with it, an untouched knob
 * follows it (both ways between a production and a development file), a value the operator edited stays.
 *
 * <p>The holder is a fixture; each test scans fresh registries over it after putting the field back on its
 * compiled value, because a registry captures the field's value at scan time.
 */
class KnobDevelopmentDefaultTest {

	/** One int knob: a day in production, ten seconds in development. */
	public static final class Holder {
		@Knob(section = Section.SETTLEMENTS, min = 1, devDefault = 200, doc = "fixture")
		public static int wait = 24000;
	}

	/** A float knob may not carry a development default. */
	public static final class FloatHolder {
		@Knob(section = Section.SETTLEMENTS, devDefault = 1, doc = "fixture")
		public static float notInt = 2.0f;
	}

	@TempDir
	Path dir;

	@BeforeEach
	void compiledValue() {
		Holder.wait = 24000;
	}

	private static KnobRegistry production() {
		return KnobRegistry.scan(List.of(Holder.class));
	}

	private static KnobRegistry development() {
		KnobRegistry registry = KnobRegistry.scan(List.of(Holder.class));
		registry.useDevelopmentDefaults();
		return registry;
	}

	/** The file a fresh install of {@code registry}'s build writes, at {@code name}. */
	private Path freshFile(KnobRegistry registry, String name) {
		Path file = dir.resolve(name);
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, new ConfigFile(registry).loadFrom(file));
		return file;
	}

	/** The file with its section's value of the knob (the first occurrence; the defaults block comes last) set. */
	private static void setValue(Path file, int value) throws IOException {
		String text = Files.readString(file, StandardCharsets.UTF_8);
		int at = text.indexOf("\"wait\": ");
		int comma = text.indexOf(',', at);
		int newline = text.indexOf('\n', at);
		int end = comma >= 0 && comma < newline ? comma : newline;
		Files.writeString(file, text.substring(0, at) + "\"wait\": " + value + text.substring(end),
				StandardCharsets.UTF_8);
	}

	@Test
	void aDevelopmentRunStartsOnTheDevelopmentDefaultAndWritesIt() throws IOException {
		KnobRegistry registry = development();
		assertEquals(200, Holder.wait, "useDevelopmentDefaults puts the live field on the development default");

		Holder.wait = 7;
		Path file = freshFile(registry, "dev.json");
		assertEquals(200, Holder.wait, "an absent file loads the development default");
		String text = Files.readString(file, StandardCharsets.UTF_8);
		assertEquals(2, text.split("\"wait\": 200", -1).length - 1,
				"the value and the recorded default are both the development one:\n" + text);
	}

	@Test
	void aProductionRunIgnoresTheDevelopmentDefault() {
		freshFile(production(), "prod.json");
		assertEquals(24000, Holder.wait);
	}

	@Test
	void anUntouchedKnobFollowsTheDefaultOfTheRunThatReadsIt() {
		Path prodFile = freshFile(production(), "prod.json");
		Holder.wait = 24000;
		Path devFile = freshFile(development(), "dev.json");

		Holder.wait = 24000;
		assertEquals(Config.LoadResult.LOADED, new ConfigFile(development()).loadFrom(prodFile));
		assertEquals(200, Holder.wait, "a production file read by a development run: the untouched day becomes 200");

		Holder.wait = 24000;
		assertEquals(Config.LoadResult.LOADED, new ConfigFile(production()).loadFrom(devFile));
		assertEquals(24000, Holder.wait,
				"a development file read by a production run: the untouched 200 becomes a day");
	}

	@Test
	void aValueTheOperatorEditedStaysTheirsInADevelopmentRun() throws IOException {
		Path file = freshFile(production(), "prod.json");
		setValue(file, 500);

		Holder.wait = 24000;
		assertEquals(Config.LoadResult.LOADED, new ConfigFile(development()).loadFrom(file));
		assertEquals(500, Holder.wait, "an edited value is never replaced by the development default");
	}

	@Test
	void onlyAnIntKnobMayDeclareADevelopmentDefault() {
		IllegalStateException refused = assertThrows(IllegalStateException.class,
				() -> KnobRegistry.scan(List.of(FloatHolder.class)));
		assertTrue(refused.getMessage().contains("notInt"), refused.getMessage());
	}
}
