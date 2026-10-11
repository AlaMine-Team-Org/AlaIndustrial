package dev.alaindustrial.command.demo.energy;

import java.util.Locale;
import java.util.function.Function;

/**
 * One rule a bay of the energy stand must keep over the check window (MOD-799) — the player rule of ADR-049 put
 * as a number: machines first (nearest first when short), stores only what machines cannot take, nobody holds
 * output a reachable store has room for, nothing made from nothing. Minecraft-free: the L1 test judges invented
 * readings with the same rules the stand judges the world with.
 *
 * <p>Thresholds leave room for what a window boundary costs (a machine that finishes an operation one tick
 * after the window), not for a rule half kept: "fed" is 90 % of the declared need.
 */
public record Expectation(String label, Function<BayReading, Verdict> rule) {

	/** The outcome of one rule: pass or fail, and the numbers that decided it. */
	public record Verdict(boolean pass, String detail) {
		static Verdict of(boolean pass, String detail) {
			return new Verdict(pass, detail);
		}
	}

	/** Share of the declared need a machine must spend to count as running at full speed. */
	public static final double FED = 0.9;

	public Verdict judge(BayReading reading) {
		try {
			return rule.apply(reading);
		} catch (IllegalArgumentException e) {
			return Verdict.of(false, e.getMessage());
		}
	}

	private static String f(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	/** Machine {@code m} spent at least 90 % of its declared need on every tick of the window. */
	public static Expectation fed(String m) {
		return new Expectation(m + " fed", r -> {
			ProbeReading p = r.probe(m);
			if (!p.counters()) {
				return Verdict.of(false, m + ": counters off, nothing to judge");
			}
			double floor = FED * p.need();
			return Verdict.of(p.need() > 0 && p.usedPerTick() >= floor,
					m + " used " + f(p.usedPerTick()) + "/t of need " + p.need() + "/t");
		});
	}

	/**
	 * Machine {@code m} ran as fast as its path allows: 90 % of its need, or of {@code cap} EU per tick when its
	 * cables carry less than it needs (ADR-001: a cable's buffer is its throughput).
	 */
	public static Expectation fedUpTo(String m, long cap) {
		return new Expectation(m + " fed up to " + cap + "/t", r -> {
			ProbeReading p = r.probe(m);
			double floor = FED * Math.min(p.need(), cap);
			return Verdict.of(p.counters() && p.need() > 0 && p.usedPerTick() >= floor,
					m + " used " + f(p.usedPerTick()) + "/t, need " + p.need() + "/t, path " + cap + "/t");
		});
	}

	/** Every machine of the bay is {@link #fed}. */
	public static Expectation allFed() {
		return new Expectation("every machine fed", r -> {
			int machines = 0;
			for (ProbeReading p : r.probes().values()) {
				if (p.kind() != ProbeKind.MACHINE) {
					continue;
				}
				machines++;
				Verdict v = fed(p.name()).judge(r);
				if (!v.pass()) {
					return v;
				}
			}
			return Verdict.of(machines > 0, machines + " machines fed");
		});
	}

	/** Machine {@code m} spent at least {@code perTick} EU per tick — for a machine its cables cannot fill. */
	public static Expectation runsAtLeast(String m, double perTick) {
		return new Expectation(m + " runs >= " + f(perTick) + "/t", r -> {
			ProbeReading p = r.probe(m);
			return Verdict.of(p.counters() && p.usedPerTick() >= perTick,
					m + " used " + f(p.usedPerTick()) + "/t");
		});
	}

	/** Machine {@code m} is short: it spent less than 90 % of its need (the supply cannot cover everyone). */
	public static Expectation underFed(String m) {
		return new Expectation(m + " under-fed", r -> {
			ProbeReading p = r.probe(m);
			return Verdict.of(p.counters() && p.usedPerTick() < FED * p.need(),
					m + " used " + f(p.usedPerTick()) + "/t of need " + p.need() + "/t");
		});
	}

	/** Machine {@code m} got nothing at all — it stands on a network no source reaches. */
	public static Expectation dark(String m) {
		return new Expectation(m + " dark", r -> {
			ProbeReading p = r.probe(m);
			return Verdict.of(p.counters() && p.used() == 0 && p.received() == 0,
					m + " used " + p.used() + ", in " + p.received());
		});
	}

	/** Nearest first: machine {@code near} spent at least as much as {@code far}. */
	public static Expectation nearFirst(String near, String far) {
		return new Expectation(near + " >= " + far, r -> {
			ProbeReading a = r.probe(near);
			ProbeReading b = r.probe(far);
			return Verdict.of(a.counters() && b.counters() && a.used() >= b.used(),
					near + " " + a.used() + " vs " + far + " " + b.used());
		});
	}

	/** Store {@code s} gained at least {@code perTick} EU per tick. */
	public static Expectation charges(String s, double perTick) {
		return new Expectation(s + " charges >= " + f(perTick) + "/t", r -> {
			ProbeReading p = r.probe(s);
			return Verdict.of(p.deltaPerTick() >= perTick, s + " gained " + f(p.deltaPerTick()) + "/t");
		});
	}

	/** Store {@code s} gave energy away: its charge fell over the window. */
	public static Expectation discharges(String s) {
		return new Expectation(s + " discharges", r -> {
			ProbeReading p = r.probe(s);
			return Verdict.of(p.delta() < 0, s + " changed " + p.delta());
		});
	}

	/** Stores charge nearest first: {@code near} gained at least as much as {@code far}. */
	public static Expectation storeOrder(String near, String far) {
		return new Expectation(near + " charges before " + far, r -> {
			ProbeReading a = r.probe(near);
			ProbeReading b = r.probe(far);
			return Verdict.of(a.delta() >= b.delta(), near + " " + a.delta() + " vs " + far + " " + b.delta());
		});
	}

	/** Source {@code s} gave at least {@code perTick} EU per tick: it does not sit on output a sink could take. */
	public static Expectation delivers(String s, double perTick) {
		return new Expectation(s + " gives >= " + f(perTick) + "/t", r -> {
			ProbeReading p = r.probe(s);
			return Verdict.of(p.outPerTick() >= perTick, s + " gave " + f(p.outPerTick()) + "/t");
		});
	}

	/**
	 * Nothing from nothing, and the cables lose no more than {@code maxShare} of what the sources gave: the bay's
	 * balance (sources out minus everything kept) lies in {@code [-tolerance, maxShare * out + tolerance]}.
	 */
	public static Expectation conserves(double maxShare) {
		return new Expectation("balance (loss <= " + Math.round(maxShare * 100) + "%)", r -> {
			Long loss = r.loss();
			if (loss == null) {
				return Verdict.of(false, "balance not computable (a probe ran without counters)");
			}
			long out = r.sourceOut();
			long tolerance = 4 + out / 100;
			return Verdict.of(loss >= -tolerance && loss <= maxShare * out + tolerance,
					"sources " + out + ", loss " + loss);
		});
	}

	/** The world flag {@code flag} decides which rule applies: {@code ifSet} when it is set, else {@code ifClear}. */
	public static Expectation when(String flag, Expectation ifSet, Expectation ifClear) {
		return new Expectation("if " + flag + ": " + ifSet.label() + ", else " + ifClear.label(),
				r -> r.flag(flag) ? ifSet.judge(r) : ifClear.judge(r));
	}

	/** {@code a} gained no more than {@code factor} times what {@code b} gained, plus a small slack. */
	public static Expectation notMoreThan(String a, String b, double factor) {
		return new Expectation(a + " <= " + f(factor) + " x " + b, r -> {
			long ga = r.probe(a).delta();
			long gb = r.probe(b).delta();
			return Verdict.of(ga <= factor * Math.max(0, gb) + 4 + r.ticks() / 10,
					a + " " + ga + " vs " + b + " " + gb);
		});
	}
}
