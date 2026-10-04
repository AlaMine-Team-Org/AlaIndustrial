package dev.alaindustrial.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import dev.alaindustrial.KnobSnapshot;
import dev.alaindustrial.config.KnobEntry;
import dev.alaindustrial.core.machine.MachineRates;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * L1 for the client's copy of the server balance (MOD-695): it mirrors exactly the knobs flagged
 * {@code clientVisible}, carries every one of them through the wire format, never writes to
 * {@link Config}, and falls back to the local numbers when the server says nothing it can read.
 */
class ServerBalanceTest {

	@AfterEach
	void forgetTheServer() {
		ServerBalance.reset();
	}

	/** The flagged knobs, straight from the registry. */
	private static Set<String> flagged() {
		return KnobSnapshot.capture().keys();
	}

	/** {@code ServerBalance} methods that mirror a Config knob: public, static, no argument, a knob's name. */
	private static Map<String, Method> accessors() {
		Map<String, Method> out = new LinkedHashMap<>();
		for (Method method : ServerBalance.class.getDeclaredMethods()) {
			if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers())
					&& method.getParameterCount() == 0 && knobField(method.getName()) != null) {
				out.put(method.getName(), method);
			}
		}
		return out;
	}

	/** The knob field named {@code name} in whichever holder declares it (MOD-710), or null. */
	private static Field knobField(String name) {
		Field field = Config.REGISTRY.entries().stream().filter(entry -> entry.key().equals(name)).findFirst()
				.map(KnobEntry::field).orElse(null);
		return field != null && Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
				? field : null;
	}

	/** A value unlike {@code live}, of the same boxed type. */
	private static Object other(Object live) {
		return switch (live) {
			case Integer i -> i * 3 + 7;
			case Float f -> f * 3 + 0.5f;
			case Double d -> d * 3 + 0.25;
			case Boolean b -> !b;
			default -> throw new IllegalArgumentException("unexpected knob type " + live);
		};
	}

	/**
	 * @implements MOD-695-FLAG — the accessors and the {@code clientVisible} flags name the same knobs,
	 *     with the knob's own type
	 * @covers MOD-695
	 */
	@Test
	void everyFlaggedKnobHasAnAccessorAndNothingElseDoes() {
		Set<String> flagged = new TreeSet<>(flagged());
		Set<String> mirrored = new TreeSet<>(accessors().keySet());
		assertEquals(flagged, mirrored, "a clientVisible knob needs a ServerBalance accessor, and an accessor needs the flag");
		for (Map.Entry<String, Method> entry : accessors().entrySet()) {
			assertEquals(knobField(entry.getKey()).getType(), entry.getValue().getReturnType(),
					"ServerBalance." + entry.getKey() + "() must return the knob's own type");
		}
		assertTrue(flagged.size() >= 60, "the client reads ~70 knobs; " + flagged.size() + " flagged is too few to trust");
	}

	/**
	 * @implements MOD-695-ROUNDTRIP — every flagged knob crosses the wire format and comes out of its
	 *     accessor as the server's value, while {@code Config} keeps the local one (single-player JVM)
	 */
	@Test
	void everyKnobRoundTripsAndConfigIsNeverWritten() throws ReflectiveOperationException {
		Map<String, Object> local = new LinkedHashMap<>();
		Map<String, Object> server = new LinkedHashMap<>();
		for (String key : flagged()) {
			Object live = knobField(key).get(null);
			local.put(key, live);
			server.put(key, other(live));
		}

		KnobSnapshot sent = KnobSnapshot.of(server);
		assertEquals(sent, KnobSnapshot.decode(sent.encode()).orElseThrow(), "the wire format must be lossless");
		assertTrue(ServerBalance.receive(sent.encode()));
		assertTrue(ServerBalance.fromServer());

		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, Method> entry : accessors().entrySet()) {
			Object shown = entry.getValue().invoke(null);
			if (!server.get(entry.getKey()).equals(shown)) {
				wrong.add(entry.getKey() + " shows " + shown + ", server sent " + server.get(entry.getKey()));
			}
		}
		assertTrue(wrong.isEmpty(), "accessors reading the wrong key or type: " + wrong);

		List<String> written = new ArrayList<>();
		for (Map.Entry<String, Object> entry : local.entrySet()) {
			Object now = knobField(entry.getKey()).get(null);
			if (!entry.getValue().equals(now)) {
				written.add(entry.getKey() + ": " + entry.getValue() + " -> " + now);
			}
		}
		assertTrue(written.isEmpty(), "applying the client snapshot rewrote the server's Config: " + written);
		assertEquals(local.get("euPerXp"), Config.euPerXp);
		assertEquals(local.get("teleporterMaxPoints"), Config.teleporterMaxPoints);
	}

	/** @implements MOD-695-ROUNDTRIP — derived figures apply Config's formula to the server's numbers */
	@Test
	void derivedFiguresUseTheServersInputs() {
		int eu = Config.machineEuPerTick + 5;
		int heater = Config.electricHeaterEuPerTick + 3;
		int furnace = Config.electricFurnaceDuration + 40;
		float speed = 2.0f;
		assertTrue(ServerBalance.receive(KnobSnapshot.of(Map.of("machineEuPerTick", eu,
				"electricHeaterEuPerTick", heater, "electricFurnaceDuration", furnace,
				"globalMachineSpeedMultiplier", speed)).encode()));

		assertEquals(MachineRates.euPerTick(eu, speed), ServerBalance.machineEuPerTickEffective());
		assertEquals(MachineRates.euPerTick(heater, speed), ServerBalance.electricHeaterEuPerTickEffective());
		assertEquals(MachineRates.duration(200, speed), ServerBalance.scaledDuration(200));
		assertEquals(MachineRates.vanillaSmeltEu(furnace, eu, speed), ServerBalance.electricFurnaceVanillaSmeltEu());
	}

	/**
	 * @implements MOD-695-FORMAT — a snapshot of another layout version is refused without an
	 *     exception, and the client keeps the numbers it had
	 */
	@Test
	void aForeignFormatIsRefusedAndTheNumbersStay() {
		int localRate = Config.euPerXp;
		byte[] foreign = KnobSnapshot.of(Map.of("euPerXp", localRate + 1)).encode();
		ByteBuffer.wrap(foreign).putInt(0, KnobSnapshot.FORMAT + 1);

		assertFalse(ServerBalance.receive(foreign));
		assertFalse(ServerBalance.fromServer(), "nothing was ever accepted");
		assertEquals(localRate, ServerBalance.euPerXp(), "no snapshot: the local value is shown");

		assertTrue(ServerBalance.receive(KnobSnapshot.of(Map.of("euPerXp", localRate + 2)).encode()));
		assertFalse(ServerBalance.receive(foreign));
		assertEquals(localRate + 2, ServerBalance.euPerXp(), "a refused snapshot keeps the last accepted one");
	}

	/** @implements MOD-695-FORMAT — truncated, trailing and unknown-type bytes are refused, never thrown */
	@Test
	void malformedBytesAreRefused() {
		byte[] good = KnobSnapshot.of(Map.of("euPerXp", 5, "levelXpMultiplier", 1.5f)).encode();
		byte[] truncated = java.util.Arrays.copyOf(good, good.length - 3);
		byte[] trailing = java.util.Arrays.copyOf(good, good.length + 1);
		assertFalse(ServerBalance.receive(truncated));
		assertFalse(ServerBalance.receive(trailing));
		assertFalse(ServerBalance.receive(new byte[0]));
		assertFalse(ServerBalance.receive(null));
		assertFalse(ServerBalance.fromServer());
	}

	/**
	 * @implements MOD-695-FALLBACK — no snapshot, or a snapshot without a knob (an older server), shows
	 *     the local number for that knob
	 */
	@Test
	void aMissingKnobFallsBackToTheLocalValue() {
		assertEquals(Config.teleporterMaxPoints, ServerBalance.teleporterMaxPoints());
		assertTrue(ServerBalance.receive(KnobSnapshot.of(Map.of("euPerXp", Config.euPerXp + 9)).encode()));
		assertEquals(Config.teleporterMaxPoints, ServerBalance.teleporterMaxPoints(), "not in the snapshot: local");
		assertEquals(Config.euPerXp + 9, ServerBalance.euPerXp());

		// A knob of the wrong type is ignored the same way, instead of a ClassCastException in a tooltip.
		assertTrue(ServerBalance.receive(KnobSnapshot.of(Map.of("teleporterMaxPoints", 3.0)).encode()));
		assertEquals(Config.teleporterMaxPoints, ServerBalance.teleporterMaxPoints());

		ServerBalance.reset();
		assertEquals(Config.euPerXp, ServerBalance.euPerXp(), "leaving the world forgets the server's numbers");
	}
}
