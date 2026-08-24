package dev.alaindustrial.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * L1.5 regression coverage for the dashboard's generator breakdown being a function of the DATA
 * (MOD-313).
 *
 * <p>The player-visible defect was rows swapping places between sessions with nothing in the world
 * changed. Two independent causes, and fixing either alone leaves the other:
 *
 * <ul>
 *   <li>the record's canonical constructor copied through {@code Map.copyOf}, whose iteration order is
 *       salted once per JVM start — so the underlying map came out in a different order every launch;</li>
 *   <li>the screen ranked by EU alone, so any two generators on the same total were left in whatever
 *       order that map iterated.</li>
 * </ul>
 *
 * <p>Every case here therefore feeds the SAME content in two different insertion orders and demands one
 * answer. "Call it twice and compare" would pass on the broken code — a salted map is stable within one
 * JVM run — which is exactly why the defect survived to be filed from a code audit rather than a test.
 *
 * <p>Lives in {@code :neoforge:test} because {@link Identifier} and the packet codec need the Minecraft
 * jar, which {@code :common}'s L1 classpath does not have.
 *
 * <p>The traceability tags for MOD-313 sit on the METHODS below, per AUTOMATION-STANDARDS §3. Not here:
 * a tag in this class javadoc stands 50 lines above the first {@code @Test}, and
 * {@code gen_traceability.py} scans only 40 lines forward from a tag, so it produced a matrix row with
 * an empty method column instead.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class PlayerStatsOrderTest {

	private static Identifier gen(String path) {
		return Identifier.fromNamespaceAndPath("alaindustrial", path);
	}

	/**
	 * Twelve distinct generator ids with twelve DISTINCT totals. Twelve because a salted map reproducing
	 * a chosen order by luck has to hit one arrangement out of 12!, which is not a coincidence anyone
	 * will meet; distinct totals so {@link #ranksByOutputFirst} cannot be satisfied by the tie-break.
	 */
	private static Map<Identifier, Long> descendingFixture() {
		Map<Identifier, Long> map = new LinkedHashMap<>();
		String[] paths = {"solar_panel", "generator", "geothermal_generator", "water_mill", "wind_mill",
				"nuclear_reactor", "semifluid_generator", "rtg", "biogas_generator", "steam_turbine",
				"kinetic_generator", "fuel_generator"};
		long eu = 12_000L;
		for (String path : paths) {
			map.put(gen(path), eu);
			eu -= 1_000L;
		}
		return map;
	}

	private static Map<Identifier, Long> reinsertedInReverse(Map<Identifier, Long> source) {
		List<Map.Entry<Identifier, Long>> entries = new ArrayList<>(source.entrySet());
		Collections.reverse(entries);
		Map<Identifier, Long> map = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Long> entry : entries) {
			map.put(entry.getKey(), entry.getValue());
		}
		return map;
	}

	private static PlayerModStats statsWith(Map<Identifier, Long> byGenerator) {
		return new PlayerModStats(1L, 2L, 3, byGenerator, 4L, 5L);
	}

	private static List<Identifier> keysOf(Map<Identifier, Long> map) {
		return new ArrayList<>(map.keySet());
	}

	private static List<Identifier> rankedKeys(PlayerModStats stats) {
		List<Identifier> out = new ArrayList<>();
		for (Map.Entry<Identifier, Long> entry : stats.rankedGenerators()) {
			out.add(entry.getKey());
		}
		return out;
	}

	/**
	 * @implements MOD-313 — the record keeps the generator map in the order it was handed
	 */
	@Test
	void constructorKeepsTheOrderItWasGiven() {
		Map<Identifier, Long> source = descendingFixture();
		PlayerModStats stats = statsWith(source);
		assertEquals(keysOf(source), keysOf(stats.producedByGenerator()),
				"the record reshuffled the generator map on the way in — a per-JVM-salted copy is back");
	}

	/**
	 * @implements MOD-313 — the dashboard breakdown is ranked by EU produced, not by map order
	 */
	@Test
	void ranksByOutputFirst() {
		PlayerModStats stats = statsWith(reinsertedInReverse(descendingFixture()));
		assertEquals(keysOf(descendingFixture()), rankedKeys(stats),
				"the breakdown is not ordered by EU produced");
	}

	/**
	 * @implements MOD-313 — two generators on the same total are ranked by registry id, not by map order
	 */
	@Test
	void tiesAreBrokenByIdNotByMapOrder() {
		// Two generators on exactly the same total — the case a fresh world and any two idle generators
		// both produce, and the one that used to make rows swap between sessions.
		Identifier first = gen("aaa_generator");
		Identifier second = gen("zzz_generator");

		Map<Identifier, Long> oneWay = new LinkedHashMap<>();
		oneWay.put(second, 500L);
		oneWay.put(first, 500L);
		Map<Identifier, Long> otherWay = new LinkedHashMap<>();
		otherWay.put(first, 500L);
		otherWay.put(second, 500L);

		assertNotEquals(keysOf(oneWay), keysOf(otherWay),
				"fixture is broken: the two maps must differ in insertion order for this to prove anything");
		assertEquals(List.of(first, second), rankedKeys(statsWith(oneWay)),
				"two generators with equal EU were ranked by map order, not by registry id");
		assertEquals(rankedKeys(statsWith(otherWay)), rankedKeys(statsWith(oneWay)),
				"the same two totals ranked differently depending on which entry was inserted first");
	}

	/**
	 * @implements MOD-313 — the stats packet codec preserves the order the server sent
	 */
	@Test
	void packetRoundTripKeepsTheOrder(MinecraftServer server) {
		PlayerModStats sent = statsWith(descendingFixture());
		// MOD-498 — NeoForge deprecated the two-argument constructor ("use overload with ConnectionType
		// context"); this lane is NeoForge-only and can take the replacement. `OTHER` is the value the
		// deprecated constructor supplied, so the order assertion below tests exactly what it did before.
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
				server.registryAccess(), ConnectionType.OTHER);
		PlayerModStats.STREAM_CODEC.encode(buffer, sent);
		PlayerModStats received = PlayerModStats.STREAM_CODEC.decode(buffer);

		assertEquals(keysOf(sent.producedByGenerator()), keysOf(received.producedByGenerator()),
				"the packet codec's map factory threw away the order the server sent — the client would "
						+ "hold a differently-ordered map than the one that was saved");
		assertEquals(sent, received, "round trip lost data");
	}
}
