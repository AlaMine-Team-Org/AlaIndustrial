package dev.alaindustrial.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * MOD-772: the Industrialist's energy order wants a battery whose {@code alaindustrial:pouch_energy} equals a
 * FULL charge. That number is written in the trade files, while the battery's capacity is the knob
 * {@code batteryBuffer}; the two can drift apart silently, and then no battery would ever be "full" for the
 * villager. This test pins every battery trade's charge to the knob's compiled default, read from the knob
 * export ({@code config-knobs.json}, itself pinned to the code by {@code ConfigKnobsExportTest}) rather than
 * from the static field, which other tests may change while they run.
 *
 * <p>Gson is not on the L1 compile classpath, so the two numbers are read with anchored patterns; a file the
 * pattern cannot read fails the test instead of being skipped.
 */
class EnergyOrderChargeTest {

	private static final Path TRADES = Path.of("src", "main", "resources", "data", "alaindustrial", "villager_trade");
	private static final Path KNOBS = Path.of("src", "test", "resources", "dev", "alaindustrial", "config-knobs.json");

	private static final Pattern WANTS_BATTERY = Pattern.compile(
			"\"wants\"\\s*:\\s*\\{[^{}]*\"id\"\\s*:\\s*\"alaindustrial:battery\"");
	private static final Pattern CHARGE = Pattern.compile("\"alaindustrial:pouch_energy\"\\s*:\\s*(\\d+)");
	private static final Pattern KNOB = Pattern.compile(
			"\\{\"key\": \"batteryBuffer\",[^}]*\"default\": (\\d+)[,}]");

	/** The trade files that buy a battery: today the three energy orders (levels 1, 3, 5). */
	private static List<Path> batteryTrades() throws IOException {
		try (Stream<Path> files = Files.walk(TRADES)) {
			return files.filter(f -> f.toString().endsWith(".json")).filter(f -> {
				try {
					return WANTS_BATTERY.matcher(Files.readString(f, StandardCharsets.UTF_8)).find();
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			}).sorted().toList();
		}
	}

	private static long batteryBufferDefault() throws IOException {
		Matcher m = KNOB.matcher(Files.readString(KNOBS, StandardCharsets.UTF_8));
		assertTrue(m.find(), "batteryBuffer not found in " + KNOBS);
		return Long.parseLong(m.group(1));
	}

	@Test
	void everyBatteryTradeWantsExactlyAFullCharge() throws IOException {
		long full = batteryBufferDefault();
		List<Path> trades = batteryTrades();
		assertEquals(3, trades.size(), "expected the three energy orders (levels 1, 3, 5), found " + trades);
		for (Path trade : trades) {
			Matcher m = CHARGE.matcher(Files.readString(trade, StandardCharsets.UTF_8));
			assertTrue(m.find(), trade + " buys a battery without a pouch_energy condition: a half-charged"
					+ " or empty battery would be accepted");
			assertEquals(full, Long.parseLong(m.group(1)), trade + ": the charge must equal the batteryBuffer"
					+ " default, or no battery is ever full for the villager");
		}
	}
}
