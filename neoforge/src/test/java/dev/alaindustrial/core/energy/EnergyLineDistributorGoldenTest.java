package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.fail;

import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Golden master of the line distribution kernel (MOD-715, batch 0): the exact buffer of every cable and
 * every endpoint, tick by tick, on the reference circuits the kernel's history was fixed on — a straight
 * line, a fork to a machine and a store (MOD-254), a dead-end spur (MOD-318), a ring, the starving fork of
 * MOD-413, a multiblock host lending one buffer to two cells (MOD-608), and the three storage channels
 * (backup, cascade, feed; ADR-004).
 *
 * <p><b>What is under test and what is not.</b> The subject is the REAL {@link EnergyLineDistributor}.
 * Around it runs {@link GoldenRig}, a replica of {@code EnergyNetwork.tick()} and of the
 * {@code EnergyTopologyCache} fields: the cache needs a live {@code ServerLevel}, and this lane's ephemeral
 * server has none (MOD-485). The fields are inputs here; the real cache is pinned end to end by the world
 * golden {@code EnergyFlowFieldGoldenScenarios}. This test exists so the kernel can be reshaped (its
 * constructor folded into a record, its overloads into one, its position generalised) with the numbers
 * held still — the reason {@code EnergyLineDistributorTest} alone is not enough is that its 25 cases each
 * pin one rule, not the composition of all of them over many ticks.
 *
 * <p><b>Updated only by an explicit command</b> (ADR-032), never by the build, a hook or {@code regen.py}:
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dalaindustrial.lineDistributorGolden.writeTo=&lt;file&gt;"
 *   ./gradlew :neoforge:test --tests dev.alaindustrial.core.energy.EnergyLineDistributorGoldenTest
 * </pre>
 * with {@code <file>} the absolute path of {@link EnergyLineDistributorGolden}'s source. The run writes
 * the file and then fails on purpose, so it can never pass for a check. A changed number
 * in a behaviour-preserving change is a defect, not a reason to rewrite the golden.
 *
 * @implements MOD-715-CH01 — the kernel's per-tick buffer layout on the reference circuits is unchanged
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class EnergyLineDistributorGoldenTest {

	/** System property that turns the comparison into a rewrite of the golden file (ADR-032). */
	static final String WRITE_TO_PROPERTY = "alaindustrial.lineDistributorGolden.writeTo";

	/** Ticks recorded one by one for every circuit except the long MOD-413 run. */
	private static final int TICKS = 40;

	/** A fake EnergyTransactions that runs body callbacks synchronously against a shared fake txn. */
	private static final class FakeTransactions implements EnergyTransactions {
		final EnergyPort.Txn txn = participant -> { };

		@Override
		public void runCommitting(Consumer<EnergyPort.Txn> body) {
			body.accept(txn);
		}

		@Override
		public <T> T simulate(Function<EnergyPort.Txn, T> body) {
			return body.apply(txn);
		}
	}

	private FakeTransactions txns;

	@BeforeEach
	void installFakeTransactions() {
		txns = new FakeTransactions();
		EnergyTransactions.install(txns);
	}

	@AfterEach
	void clearFakeTransactions() {
		EnergyTransactions.install(null);
	}

	private static BlockPos at(int x, int z) {
		return new BlockPos(x, 0, z);
	}

	/** A run of cables from {@code (x0, z0)} to {@code (x1, z1)} along one axis, both ends included. */
	private static void run(GoldenRig rig, int x0, int z0, int x1, int z1) {
		int dx = Integer.signum(x1 - x0);
		int dz = Integer.signum(z1 - z0);
		for (int x = x0, z = z0; ; x += dx, z += dz) {
			rig.cable(at(x, z));
			if (x == x1 && z == z1) {
				return;
			}
		}
	}

	/** Every reference circuit, built fresh. */
	private static List<GoldenRig> circuits() {
		List<GoldenRig> out = new ArrayList<>();

		GoldenRig line = new GoldenRig("line");
		line.generator("G", at(0, 0), 4000, 32, 20);
		run(line, 1, 0, 5, 0);
		line.machine("M", at(6, 0), 800, 32, 2);
		out.add(line);

		GoldenRig fork = new GoldenRig("fork254");
		fork.generator("G", at(0, 0), 4000, 32, 24);
		run(fork, 1, 0, 3, 0);
		run(fork, 3, -1, 3, -2);
		run(fork, 3, 1, 3, 2);
		fork.machine("M", at(3, -3), 800, 32, 4);
		fork.store("S", at(3, 3), 4000, 32, 32, 0);
		out.add(fork);

		GoldenRig spur = new GoldenRig("spur318");
		spur.generator("G", at(0, 0), 4000, 32, 20);
		run(spur, 1, 0, 4, 0);
		run(spur, 2, -1, 2, -3);
		spur.machine("M", at(5, 0), 800, 32, 2);
		out.add(spur);

		GoldenRig ring = new GoldenRig("ring");
		ring.generator("G", at(-1, 0), 4000, 32, 16);
		run(ring, 0, 0, 2, 0);
		run(ring, 2, 1, 2, 2);
		run(ring, 1, 2, 0, 2);
		ring.cable(at(0, 1));
		ring.machine("M", at(3, 2), 800, 32, 2);
		out.add(ring);

		GoldenRig host = new GoldenRig("host608");
		EnergyBuffer shared = host.generator("H", at(0, 0), 4000, 64, 40);
		host.cell("C", at(0, 1), shared, at(0, 0));
		host.cable(at(1, 0));
		host.cable(at(1, 1));
		run(host, 2, 0, 3, 0);
		host.machine("M", at(4, 0), 800, 64, 0);
		out.add(host);

		GoldenRig backup = new GoldenRig("backup");
		backup.generator("G", at(0, 0), 4000, 32, 4);
		run(backup, 1, 0, 4, 0);
		backup.machine("M", at(5, 0), 800, 32, 16);
		backup.store("S", at(2, 1), 4000, 32, 32, 4000);
		out.add(backup);

		GoldenRig cascade = new GoldenRig("cascade");
		cascade.store("A", at(0, 0), 4000, 32, 32, 4000);
		run(cascade, 1, 0, 4, 0);
		cascade.store("B", at(5, 0), 4000, 32, 32, 0);
		out.add(cascade);

		GoldenRig feed = new GoldenRig("feed");
		feed.store("A", at(0, 0), 4000, 32, 32, 4000);
		run(feed, 1, 0, 3, 0);
		feed.fund("T", at(4, 0), 100000, 32, 24);
		out.add(feed);
		return out;
	}

	/** The MOD-413 player rig: a 1 EU/t panel feeding two identical machines through a symmetric fork. */
	private static GoldenRig fork413() {
		GoldenRig rig = new GoldenRig("fork413");
		rig.generator("P", at(18, 12), 8000, 20, 1);
		run(rig, 17, 12, 13, 12);
		rig.cable(at(13, 11));
		rig.cable(at(13, 13));
		rig.machine("A", at(12, 11), 800, 32, 2);
		rig.machine("B", at(12, 13), 800, 32, 2);
		return rig;
	}

	private List<String> capture() {
		List<String> lines = new ArrayList<>();
		for (GoldenRig rig : circuits()) {
			for (int t = 1; t <= TICKS; t++) {
				rig.tick(txns.txn);
				lines.add(rig.snapshot(t));
			}
		}
		GoldenRig slow = fork413();
		for (int t = 1; t <= 4000; t++) {
			slow.tick(txns.txn);
			if (t % 250 == 0) {
				lines.add(slow.snapshot(t));
			}
		}
		return lines;
	}

	@Test
	void distributionMatchesTheGolden() throws IOException {
		List<String> actual = capture();
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			Files.writeString(Path.of(writeTo), GoldenSource.render("EnergyLineDistributorGolden",
					"dev.alaindustrial.core.energy", "EnergyLineDistributorGoldenTest", WRITE_TO_PROPERTY,
					":neoforge:test --tests dev.alaindustrial.core.energy.EnergyLineDistributorGoldenTest", actual),
					StandardCharsets.UTF_8);
			fail("line-distributor golden rewritten to " + writeTo + " (" + actual.size()
					+ " lines) - review the diff, then run again without -D" + WRITE_TO_PROPERTY);
		}
		String difference = GoldenSource.firstDifference(EnergyLineDistributorGolden.LINES, actual);
		if (difference != null) {
			fail("the line distribution kernel no longer reproduces its golden master.\n" + difference
					+ "\nA behaviour-preserving change must not move a single EU. If the change is a deliberate"
					+ " behaviour change, rewrite the golden with -D" + WRITE_TO_PROPERTY + " (ADR-032).");
		}
	}
}
