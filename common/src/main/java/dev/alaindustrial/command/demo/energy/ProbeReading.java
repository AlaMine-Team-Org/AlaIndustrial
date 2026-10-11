package dev.alaindustrial.command.demo.energy;

/**
 * What one measured block of an energy-stand bay did over the check window (MOD-799). Minecraft-free, so the
 * rules that judge it ({@link Expectation}) are tested on plain numbers.
 *
 * @param name     the probe's name inside its bay ({@code "M1"}, {@code "B1"}, {@code "S1"} …)
 * @param kind     what the block is to the network
 * @param ticks    length of the window in ticks
 * @param need     EU per tick the block declares while working ({@code PerTickDemand}); 0 when unknown
 * @param used     EU the block spent on its own work in the window — read from its lifetime counters
 * @param received EU the wires delivered into the block in the window (counters)
 * @param delta    change of the block's charge over the window
 * @param out      EU that left the block into the wires in the window; for a creative source the sum of
 *                 what was taken from its topped-up buffer each tick
 * @param counters whether {@link #used} and {@link #received} are real numbers (the block's counters ran)
 */
public record ProbeReading(String name, ProbeKind kind, int ticks, long need, long used, long received, long delta,
		long out, boolean counters) {

	/** {@link #used} per tick of the window. */
	public double usedPerTick() {
		return ticks == 0 ? 0 : (double) used / ticks;
	}

	/** {@link #delta} per tick of the window. */
	public double deltaPerTick() {
		return ticks == 0 ? 0 : (double) delta / ticks;
	}

	/** {@link #out} per tick of the window. */
	public double outPerTick() {
		return ticks == 0 ? 0 : (double) out / ticks;
	}

	/** One line of the report. */
	public String describe() {
		StringBuilder line = new StringBuilder(name).append(' ').append(kind.name().toLowerCase(java.util.Locale.ROOT));
		if (need > 0) {
			line.append(" need=").append(need).append("/t");
		}
		if (kind == ProbeKind.MACHINE || kind == ProbeKind.UNKNOWN || kind == ProbeKind.STORE) {
			line.append(counters ? " used=" + used + " in=" + received : " (no counters)");
		}
		line.append(" d=").append(delta);
		if (kind == ProbeKind.SOURCE || kind == ProbeKind.GENERATOR || kind == ProbeKind.STORE) {
			line.append(" out=").append(out);
		}
		return line.toString();
	}
}
