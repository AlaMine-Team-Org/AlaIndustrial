package dev.alaindustrial.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Cold-start contract for the whole sound catalogue (MOD-397), generalising the single-event
 * regression of MOD-262: a static sound's first {@code play()} kicks off an asynchronous Ogg decode,
 * so a loop that starts in the first ticks after world entry races the world load and plays broken.
 * The second entry reuses Minecraft's completed static-buffer cache and sounds fine, which is exactly
 * why the defect is invisible to everything except a player's ears on a cold client.
 *
 * <p>The invariant is therefore per-sound rather than a list of event names: every non-streamed sound
 * must preload, so a newly added event cannot ship with the latent bug. Streaming stays the escape
 * hatch for a future long sample, where a preloaded static buffer would be the wrong trade.
 */
class SoundPreloadContractTest {

	private static final String SOUNDS_JSON = "assets/alaindustrial/sounds.json";

	/**
	 * Lower bound on the shipped event count. Without it the scan below would pass vacuously on an
	 * empty or restructured file — zero sounds trivially satisfy "every sound preloads".
	 */
	private static final int MIN_SHIPPED_SOUNDS = 11;

	/** Every {@code "name"} key, i.e. one per sound entry — the oracle for how many objects to expect. */
	private static final Pattern SOUND_NAME = Pattern.compile("\"name\"\\s*:\\s*\"alaindustrial:([a-z0-9_]+)\"");

	/** Sound entries are the only brace-free objects in the file, so this matches exactly one per entry. */
	private static final Pattern SOUND_ENTRY = Pattern.compile("\\{[^{}]*}");

	private static final Pattern STREAMED = Pattern.compile("\"stream\"\\s*:\\s*true");
	private static final Pattern PRELOADED = Pattern.compile("\"preload\"\\s*:\\s*true");

	@Test
	void shippedSoundsPreloadEveryStaticSample() throws Exception {
		String json = readShippedSoundsJson();

		int declaredSounds = countMatches(SOUND_NAME, json);
		assertTrue(declaredSounds >= MIN_SHIPPED_SOUNDS,
				"expected at least " + MIN_SHIPPED_SOUNDS + " sound entries in " + SOUNDS_JSON
						+ ", found " + declaredSounds + " — the scan below would pass vacuously");
		assertEquals(declaredSounds, countMatches(SOUND_ENTRY, json),
				"innermost objects no longer map 1:1 onto sound entries in " + SOUNDS_JSON
						+ " — the contract scan reads the wrong thing and must be repaired, not skipped");

		assertEquals(List.of(), violations(json),
				"every static sound must preload, or its first playback races the world load (MOD-262)");
	}

	@Test
	void detectorRejectsAStaticSoundWithoutPreload() {
		String broken = """
				{
				  "macerator_grind": {
				    "sounds": [{ "name": "alaindustrial:macerator_grind", "stream": false }]
				  }
				}""";

		assertEquals(List.of("alaindustrial:macerator_grind"), violations(broken),
				"the scan must fail on the exact shape it exists to forbid, or it proves nothing");
	}

	@Test
	void detectorExemptsAStreamedSound() {
		String streamed = """
				{
				  "long_ambience": {
				    "sounds": [{ "name": "alaindustrial:long_ambience", "stream": true }]
				  }
				}""";

		assertEquals(List.of(), violations(streamed),
				"a streamed sample is decoded incrementally and must stay free to skip preload");
	}

	/** Names of the sounds that are static ({@code stream: false}) yet do not preload. */
	private static List<String> violations(String json) {
		List<String> broken = new ArrayList<>();
		Matcher entries = SOUND_ENTRY.matcher(json);
		while (entries.find()) {
			String entry = entries.group();
			Matcher name = SOUND_NAME.matcher(entry);
			if (!name.find() || STREAMED.matcher(entry).find() || PRELOADED.matcher(entry).find()) {
				continue;
			}
			broken.add("alaindustrial:" + name.group(1));
		}
		return broken;
	}

	private static int countMatches(Pattern pattern, String json) {
		int count = 0;
		Matcher matcher = pattern.matcher(json);
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	private static String readShippedSoundsJson() throws Exception {
		try (InputStream stream = SoundPreloadContractTest.class.getClassLoader().getResourceAsStream(SOUNDS_JSON)) {
			assertNotNull(stream, SOUNDS_JSON + " must be present on the test runtime classpath");
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
