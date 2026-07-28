package dev.alaindustrial.client.sound;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Resource regression coverage for MOD-262. The wind-mill loop is a short static sound, but its first
 * on-demand decode used to happen concurrently with the first world entry. A subsequent entry reused
 * Minecraft's completed static-buffer cache and therefore did not reproduce the broken first start.
 */
class WindMillSoundResourceTest {

	private static final Pattern PRELOADED_STATIC_WIND_LOOP = Pattern.compile(
			"\"name\"\\s*:\\s*\"alaindustrial:wind_mill_hum\"\\s*,"
					+ "\\s*\"stream\"\\s*:\\s*false\\s*,"
					+ "\\s*\"preload\"\\s*:\\s*true");

	@Test
	void windMillLoopIsPreloadedAsStaticBuffer() throws Exception {
		String resource = "assets/alaindustrial/sounds.json";
		try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(stream, resource + " must be present on the test runtime classpath");
			String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

			assertTrue(PRELOADED_STATIC_WIND_LOOP.matcher(json).find(),
					"wind-mill loop must remain static and preload before first world-entry playback");
		}
	}
}
