package dev.alaindustrial.command.demo.energy;

import dev.alaindustrial.core.energy.NetworkManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;

/**
 * The outcome of one {@code /ala demo energy check} (MOD-799): a verdict per bay with every number behind it, the
 * cost of the level's energy-network pass during the window, and the text and JSON files written next to the
 * server's run directory ({@code energy-stand-report-<time>.txt} / {@code .json}).
 */
public record EnergyStandReport(BlockPos origin, int window, List<BayResult> bays, long networkNanos,
		long networkMaxNanos, int networkTicks, NetworkManager.Stats stats, List<String> buildProblems,
		LocalDateTime at) {

	/** One bay: its reading, its rules and how each rule came out. */
	public record BayResult(String id, String title, BayReading reading, List<Expectation> rules,
			List<Expectation.Verdict> verdicts) {
		public boolean pass() {
			return verdicts.stream().allMatch(Expectation.Verdict::pass);
		}

		/** The first failed rule with its numbers, or the empty string. */
		public String firstFailure() {
			for (int i = 0; i < verdicts.size(); i++) {
				if (!verdicts.get(i).pass()) {
					return rules.get(i).label() + ": " + verdicts.get(i).detail();
				}
			}
			return "";
		}
	}

	static EnergyStandReport of(BlockPos origin, int window, List<BayResult> bays, long nanos, long maxNanos,
			int ticks, NetworkManager.Stats stats, List<String> problems) {
		return new EnergyStandReport(origin, window, List.copyOf(bays), nanos, maxNanos, ticks, stats,
				List.copyOf(problems), LocalDateTime.now());
	}

	/** Bays that kept every rule. */
	public long passed() {
		return bays.stream().filter(BayResult::pass).count();
	}

	/** The result of bay {@code id}, or {@code null}. */
	public BayResult bay(String id) {
		return bays.stream().filter(b -> b.id().equalsIgnoreCase(id)).findFirst().orElse(null);
	}

	/** Mean wall time of the level's energy-network pass per measured tick, in milliseconds. */
	public double meanNetworkMillis() {
		return networkTicks == 0 ? 0 : networkNanos / 1e6 / networkTicks;
	}

	/** The one-line summary chat shows first. */
	public String summary() {
		return String.format(Locale.ROOT, "Energy stand: %d/%d bays PASS over %d ticks; energy networks %.3f ms/tick"
				+ " (max %.3f)", passed(), bays.size(), window, meanNetworkMillis(), networkMaxNanos / 1e6);
	}

	/** The full text report. */
	public String text() {
		StringBuilder out = new StringBuilder("Ala Industrial energy stand report (MOD-799)\n")
				.append("time ").append(at).append(", origin ").append(origin.toShortString())
				.append(", window ").append(window).append(" ticks after a ").append(EnergyStandCheck.WARMUP)
				.append("-tick warm-up\n").append(summary()).append('\n')
				.append(String.format(Locale.ROOT, "level energy networks: %d (%d awake), %d cables; network pass"
						+ " total %.1f ms over %d ticks\n", stats.networks(), stats.awake(), stats.cables(),
						networkNanos / 1e6, networkTicks));
		for (String problem : buildProblems) {
			out.append("BUILD PROBLEM: ").append(problem).append('\n');
		}
		for (BayResult bay : bays) {
			BayReading r = bay.reading();
			Long loss = r.loss();
			out.append('\n').append(bay.pass() ? "PASS " : "FAIL ").append(bay.id()).append(" — ")
					.append(bay.title()).append(known(bay.id()))
					.append(String.format(Locale.ROOT, "  [cables %d, cable delta %d, awake %d/%d, loss %s]\n",
							r.cables(), r.cableDelta(), r.awakeTicks(), r.ticks(),
							loss == null ? "n/a" : loss.toString()));
			for (int i = 0; i < bay.rules().size(); i++) {
				Expectation.Verdict v = bay.verdicts().get(i);
				out.append("  ").append(v.pass() ? "ok   " : "FAIL ").append(bay.rules().get(i).label())
						.append(" — ").append(v.detail()).append('\n');
			}
			for (ProbeReading p : r.probes().values()) {
				out.append("    ").append(p.describe()).append('\n');
			}
			r.flags().forEach((k, v) -> out.append("    flag ").append(k).append('=').append(v).append('\n'));
		}
		return out.toString();
	}

	private static String known(String id) {
		String finding = EnergyStand.knownFinding(id);
		return finding == null ? "" : " (known finding: " + finding + ")";
	}

	/** The same numbers as JSON, one object per bay. */
	public String json() {
		StringBuilder out = new StringBuilder("{\"window\":").append(window)
				.append(",\"passed\":").append(passed()).append(",\"bays\":").append(bays.size())
				.append(String.format(Locale.ROOT, ",\"networkMsMean\":%.4f,\"networkMsMax\":%.4f",
						meanNetworkMillis(), networkMaxNanos / 1e6))
				.append(",\"results\":[");
		for (int b = 0; b < bays.size(); b++) {
			BayResult bay = bays.get(b);
			BayReading r = bay.reading();
			out.append(b == 0 ? "" : ",").append("{\"id\":\"").append(bay.id()).append("\",\"pass\":")
					.append(bay.pass()).append(",\"cableDelta\":").append(r.cableDelta())
					.append(",\"awake\":").append(r.awakeTicks()).append(",\"loss\":").append(r.loss())
					.append(",\"probes\":[");
			int i = 0;
			for (ProbeReading p : r.probes().values()) {
				out.append(i++ == 0 ? "" : ",").append(String.format(Locale.ROOT,
						"{\"name\":\"%s\",\"kind\":\"%s\",\"need\":%d,\"used\":%d,\"in\":%d,\"delta\":%d,\"out\":%d,"
								+ "\"counters\":%b}", p.name(), p.kind(), p.need(), p.used(), p.received(), p.delta(),
						p.out(), p.counters()));
			}
			out.append("],\"failure\":\"").append(bay.firstFailure().replace("\"", "'")).append("\"}");
		}
		return out.append("]}\n").toString();
	}

	/** Write the text and JSON reports into {@code dir}; returns the text file. */
	public Path write(Path dir) throws IOException {
		String stamp = at.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
		Path text = dir.resolve("energy-stand-report-" + stamp + ".txt");
		Files.writeString(text, text(), StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("energy-stand-report-" + stamp + ".json"), json(), StandardCharsets.UTF_8);
		return text;
	}
}
