package dev.alaindustrial.core.tooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A block's hover tooltip, declared by the block itself (MOD-716, ADR-040): its voltage tier, the lines shown
 * without [SHIFT] and the lines [SHIFT] adds. The client's {@code MachineTooltips} only looks the description
 * up ({@link HasMachineTooltip}) and turns {@link Line}s into chat components — it no longer keeps a list of
 * machines of its own.
 *
 * <p><b>The four modes come from the description, not from four lists.</b> {@link #lines} decides what a
 * player sees for [SHIFT] up or down and for the client's {@code showEuNumbers} on or off, from each line's
 * {@link Shown} flag:
 * <ul>
 *   <li>{@link Shown#NUMBERS} — an EU figure or another number the player switched off: shown only with
 *       numbers on;</li>
 *   <li>{@link Shown#ALWAYS} — a size or a range that is not an energy figure (a tank's millibuckets, a
 *       sprinkler's reach): shown in every mode;</li>
 *   <li>{@link Shown#LABEL} — a word, not a number (a tier, a safety warning, a hint): shown with numbers
 *       on, and with numbers off only under [SHIFT], so the plain tooltip stays the bare hint.</li>
 * </ul>
 * Without [SHIFT] the visible {@code basic} lines are followed by the hold-[SHIFT] hint; under it they are
 * followed by the tier line (when {@link #tier} is set) and the visible {@code detailed} lines.
 *
 * <p>Minecraft-free on purpose: the arguments are live suppliers (the block passes {@code ServerBalance}
 * reads, so a dedicated server's balance is what the player reads), the colours are {@link Tone}s the client
 * maps to chat formatting. That keeps the mode rules above on the L1 lane ({@code MachineTooltipSpecTest}).
 *
 * @param tier     the tier line shown after the basic lines under [SHIFT], or null for a block that names its
 *                 tier elsewhere (in its basic lines) or takes no EU at all
 * @param basic    lines shown without [SHIFT]
 * @param detailed lines [SHIFT] adds after the tier line
 */
public record MachineTooltipSpec(@Nullable Tier tier, List<Line> basic, List<Line> detailed) {

	/** Translation key of the hint a non-detailed tooltip ends with. */
	public static final String HOLD_SHIFT_KEY = "tooltip.alaindustrial.hold_shift";

	/** Prefix of every stat key: {@code stat("buffer", …)} is {@code tooltip.alaindustrial.buffer}. */
	private static final String STAT_PREFIX = "tooltip.alaindustrial.";

	public MachineTooltipSpec {
		basic = List.copyOf(basic);
		detailed = List.copyOf(detailed);
	}

	/** Colour of a line; the client maps each to the chat formatting of the same name. */
	public enum Tone {
		GRAY, DARK_GRAY, GREEN, RED, LIGHT_PURPLE, AQUA
	}

	/** When a line is visible — see the class javadoc. */
	public enum Shown {
		NUMBERS, ALWAYS, LABEL
	}

	/** Voltage class of the tier line; key and colour are those every tooltip of the mod has always used. */
	public enum Tier {
		LV("tooltip.alaindustrial.tier_lv", Tone.GREEN),
		MV("tooltip.alaindustrial.tier_mv", Tone.GREEN),
		HV("tooltip.alaindustrial.tier_hv", Tone.LIGHT_PURPLE);

		private final String key;
		private final Tone tone;

		Tier(String key, Tone tone) {
			this.key = key;
			this.tone = tone;
		}

		/** The tier line itself, as {@link #lines} places it. */
		public Line line() {
			return new Line(key, List.of(), tone, Shown.LABEL, null);
		}
	}

	/**
	 * One tooltip line: a full translation key with its live arguments, its colour and when it shows. With a
	 * {@code label} the line reads {@code <label> — <key(args)>} (the incubator's per-mode durations): the
	 * label is the coloured root and the stat its uncoloured sibling.
	 */
	public record Line(String key, List<Supplier<?>> args, Tone tone, Shown shown, @Nullable String label) {
		public Line {
			args = List.copyOf(args);
		}

		/** The arguments as the line shows them now. */
		public Object[] argValues() {
			Object[] values = new Object[args.size()];
			for (int i = 0; i < values.length; i++) {
				values[i] = args.get(i).get();
			}
			return values;
		}
	}

	/** A grey numeric line {@code tooltip.alaindustrial.<key>(value)}, hidden with EU numbers off. */
	public static Line stat(String key, IntSupplier value) {
		return new Line(STAT_PREFIX + key, List.of(value::getAsInt), Tone.GRAY, Shown.NUMBERS, null);
	}

	/**
	 * A grey numeric line whose argument is not an {@code int}: a {@code long} buffer, or a figure already
	 * formatted as text (a percentage). The value keeps its own type, as the chat component receives it.
	 */
	public static Line statValue(String key, Supplier<?> value) {
		return new Line(STAT_PREFIX + key, List.of(value), Tone.GRAY, Shown.NUMBERS, null);
	}

	/** A grey line with two arguments, hidden with EU numbers off. */
	public static Line stat(String key, Supplier<?> first, Supplier<?> second) {
		return new Line(STAT_PREFIX + key, List.of(first, second), Tone.GRAY, Shown.NUMBERS, null);
	}

	/** A grey line that is not an energy figure (a tank size, a range): shown in every mode. */
	public static Line plain(String key, IntSupplier value) {
		return new Line(STAT_PREFIX + key, List.of(value::getAsInt), Tone.GRAY, Shown.ALWAYS, null);
	}

	/** A line of words with no argument, shown like a tier line ({@link Shown#LABEL}). */
	public static Line text(String fullKey, Tone tone) {
		return new Line(fullKey, List.of(), tone, Shown.LABEL, null);
	}

	/** A line of words with no argument shown in every mode ({@link Shown#ALWAYS}). */
	public static Line note(String fullKey, Tone tone) {
		return new Line(fullKey, List.of(), tone, Shown.ALWAYS, null);
	}

	/** {@code <labelKey> — tooltip.alaindustrial.<key>(value)} in grey, hidden with EU numbers off. */
	public static Line labelled(String labelKey, String key, IntSupplier value) {
		return new Line(STAT_PREFIX + key, List.of(value::getAsInt), Tone.GRAY, Shown.NUMBERS, labelKey);
	}

	/**
	 * The standard processing machine (LV): draw and duration without [SHIFT]; buffer and the energy of one
	 * operation under it. The per-operation figure is {@code euPerTick * duration}, computed live.
	 */
	public static MachineTooltipSpec processing(IntSupplier euPerTick, IntSupplier duration, IntSupplier buffer) {
		return processing(Tier.LV, euPerTick, duration, buffer);
	}

	/** {@link #processing(IntSupplier, IntSupplier, IntSupplier)} for a machine of another tier. */
	public static MachineTooltipSpec processing(Tier tier, IntSupplier euPerTick, IntSupplier duration,
			IntSupplier buffer) {
		return new MachineTooltipSpec(tier,
				List.of(stat("energy_input", euPerTick), stat("duration_ticks", duration)),
				List.of(stat("buffer", buffer),
						stat("energy_per_op", () -> euPerTick.getAsInt() * duration.getAsInt())));
	}

	/**
	 * The lines a player sees: {@code detailed} is [SHIFT] held (or the client's always-detailed option),
	 * {@code numbers} is the client's {@code showEuNumbers}. A non-detailed list always ends with the
	 * hold-[SHIFT] hint.
	 */
	public List<Line> lines(boolean detailed, boolean numbers) {
		List<Line> out = new ArrayList<>();
		addVisible(basic, detailed, numbers, out);
		if (!detailed) {
			out.add(new Line(HOLD_SHIFT_KEY, List.of(), Tone.DARK_GRAY, Shown.ALWAYS, null));
			return out;
		}
		if (tier != null) {
			out.add(tier.line());
		}
		addVisible(this.detailed, true, numbers, out);
		return out;
	}

	private static void addVisible(List<Line> lines, boolean detailed, boolean numbers, List<Line> out) {
		for (Line line : lines) {
			boolean visible = switch (line.shown()) {
				case NUMBERS -> numbers;
				case ALWAYS -> true;
				case LABEL -> numbers || detailed;
			};
			if (visible) {
				out.add(line);
			}
		}
	}
}
