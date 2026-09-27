package dev.alaindustrial.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * L1.5 — the removed "through blocks" switch of the Network Analyzer overlay (MOD-665, D8): a settings
 * file written by an older version still loads, keeps every other choice, and the obsolete key is gone
 * from the file the next time it is saved. On {@code :neoforge:test} because the loader uses
 * {@code GsonHelper}; no world is started.
 */
class AlaClientConfigOverlayTest {

	@TempDir
	Path dir;

	@AfterEach
	void restoreDefaults() {
		AlaClientConfig.init(dir.resolve("defaults"));
		AlaClientConfig.apply(AlaClientConfig.Snapshot.defaults());
	}

	private void write(String json) throws Exception {
		Files.writeString(dir.resolve("alaindustrial-client.json"), json);
	}

	/**
	 * @implements MOD-665-D8 — an old settings file loads, keeps the other choices, and loses the obsolete
	 *     key on the next save; its untouched opacity 255 moves to the new translucent default
	 * @covers MOD-665
	 */
	@Test
	void oldFileLoadsAndDropsTheSwitch() throws Exception {
		write("""
				{
				  "networkOverlayEnabled": true,
				  "networkOverlayThroughBlocks": false,
				  "networkOverlayFlowDots": false,
				  "networkOverlayColor": "#22C55E",
				  "networkOverlayAlpha": 255
				}
				""");
		AlaClientConfig.init(dir);
		assertFalse(AlaClientConfig.networkOverlayFlowDots, "the player's other choices must survive");
		assertEquals(0x22C55E, AlaClientConfig.networkOverlayColor & 0xFFFFFF);
		assertEquals(AlaClientConfig.DEFAULT_NETWORK_ALPHA, AlaClientConfig.networkOverlayAlpha,
				"an old file's 255 was the old default: the trace is meant to be translucent now");
		assertEquals(AlaClientConfig.DEFAULT_NETWORK_ALPHA, AlaClientConfig.networkOverlayColor >>> 24);

		AlaClientConfig.apply(AlaClientConfig.snapshot());
		String saved = Files.readString(dir.resolve("alaindustrial-client.json"));
		assertFalse(saved.contains(AlaClientConfig.OBSOLETE_THROUGH_BLOCKS_KEY),
				"the removed switch must not be written back:\n" + saved);
	}

	/**
	 * @implements MOD-665 — an old file's untouched dark-teal default moves to the new vivid blue, the start
	 *     of the blue-to-yellow ramp; the same colour in a file written after the change is the player's own
	 * @covers MOD-665
	 */
	@Test
	void oldDefaultColourMovesToTheNewOne() throws Exception {
		write("""
				{
				  "networkOverlayThroughBlocks": true,
				  "networkOverlayColor": "#11577A"
				}
				""");
		AlaClientConfig.init(dir);
		assertEquals(0x3B82F6, AlaClientConfig.networkOverlayColor & 0xFFFFFF, "old default moves");

		write("""
				{
				  "networkOverlayColor": "#11577A"
				}
				""");
		AlaClientConfig.init(dir);
		assertEquals(0x11577A, AlaClientConfig.networkOverlayColor & 0xFFFFFF, "a chosen colour stays");
	}

	/** @implements MOD-665-D8 — an opacity the player picked on purpose is kept */
	@Test
	void chosenOpacityIsKept() throws Exception {
		write("""
				{ "networkOverlayThroughBlocks": true, "networkOverlayAlpha": 127 }
				""");
		AlaClientConfig.init(dir);
		assertEquals(127, AlaClientConfig.networkOverlayAlpha);
	}
}
