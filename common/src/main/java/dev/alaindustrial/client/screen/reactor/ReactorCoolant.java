package dev.alaindustrial.client.screen.reactor;

import dev.alaindustrial.core.structure.ReactorZone;
import java.util.List;

/**
 * What the «Coolant» tab says about a reactor's water loop (MOD-621): how many stacks are dry and how many cannot vent
 * their steam, which one to go and fix, and how loudly the tab's badge should call for it.
 *
 * <p><b>Minecraft-free on purpose</b>, like {@link ReactorConsole}: the order of the faults and the wording of the fix
 * are judgements, and a judgement belongs under an L1 test. The server has already decided which vessels are dry and
 * which are blocked ({@link ReactorZone.Coolant}); nothing here re-measures a tank.
 */
public final class ReactorCoolant {

	private static final String KEY = "gui.alaindustrial.reactor_controller.coolant.";

	private ReactorCoolant() {
	}

	/**
	 * The loop's faults across its stacks.
	 *
	 * @param firstDry     index of the first dry stack in the list's order, or -1
	 * @param firstBlocked index of the first stack with a blocked exhaust, or -1
	 * @param allDry       every stack is dry — the loop has no water at all, which is not one stack's plumbing
	 */
	public record Survey(int dry, int blocked, int firstDry, int firstBlocked, boolean allDry) {

		public boolean faulty() {
			return dry + blocked > 0;
		}
	}

	/**
	 * What to tell the player.
	 *
	 * @param args the numbers the body quotes: a stack's two coordinates, or the share of heat the water carries
	 */
	public record Advice(ReactorConsole.Tone tone, String titleKey, String bodyKey, List<Integer> args) {

		public Advice {
			args = List.copyOf(args);
		}
	}

	public static Survey survey(List<ReactorZone.Stack> stacks) {
		int dry = 0;
		int blocked = 0;
		int firstDry = -1;
		int firstBlocked = -1;
		for (int i = 0; i < stacks.size(); i++) {
			ReactorZone.Coolant coolant = stacks.get(i).coolant();
			if (coolant.dry()) {
				dry++;
				firstDry = firstDry < 0 ? i : firstDry;
			}
			if (coolant.blocked()) {
				blocked++;
				firstBlocked = firstBlocked < 0 ? i : firstBlocked;
			}
		}
		return new Survey(dry, blocked, firstDry, firstBlocked, !stacks.isEmpty() && dry == stacks.size());
	}

	/**
	 * The advice, worst first.
	 *
	 * <p>A blocked exhaust outranks a dry stack: steam with nowhere to go stops the boiling even in a stack the pipe
	 * keeps full, so fixing the water first would change nothing. A loop with no water anywhere is one message about
	 * the supply, not a list of stacks. A single dry stack next to wet ones is almost always the plumbing mistake the
	 * spec warns about — columns side by side share no water — so the advice names that stack.
	 *
	 * @param coolantShare share of the reaction's heat the water carried last tick; 100 while nothing reacts
	 */
	public static Advice advice(boolean formed, boolean bare, List<ReactorZone.Stack> stacks, int coolantShare) {
		if (bare) {
			return plain(ReactorConsole.Tone.IDLE, "bare");
		}
		if (!formed) {
			return plain(ReactorConsole.Tone.IDLE, "not_built");
		}
		if (stacks.isEmpty()) {
			return plain(ReactorConsole.Tone.IDLE, "empty");
		}
		Survey survey = survey(stacks);
		ReactorConsole.Tone fault = faultTone(coolantShare);
		if (survey.firstBlocked() >= 0) {
			return named(fault, "blocked", stacks.get(survey.firstBlocked()));
		}
		if (survey.allDry()) {
			return plain(fault, "dry_all");
		}
		if (survey.firstDry() >= 0) {
			return named(fault, "dry", stacks.get(survey.firstDry()));
		}
		if (coolantShare < ReactorConsole.SHORT_SHARE_PERCENT) {
			return new Advice(ReactorConsole.Tone.WARN, KEY + "advice.short.title", KEY + "advice.short.body",
					List.of(coolantShare));
		}
		return plain(ReactorConsole.Tone.GOOD, "ok");
	}

	/**
	 * The tab's badge: an alarm while a dry or blocked stack is already leaving heat behind, a warning while the loop
	 * has such a stack but still carries the heat, and nothing otherwise. A loop that is merely short is the «Console»
	 * tab's to flag — it has no stack to point at.
	 */
	public static ReactorConsole.Tone badge(List<ReactorZone.Stack> stacks, int coolantShare) {
		return survey(stacks).faulty() ? faultTone(coolantShare) : ReactorConsole.Tone.GOOD;
	}

	private static ReactorConsole.Tone faultTone(int coolantShare) {
		return coolantShare < ReactorConsole.SHORT_SHARE_PERCENT ? ReactorConsole.Tone.ALARM : ReactorConsole.Tone.WARN;
	}

	private static Advice named(ReactorConsole.Tone tone, String name, ReactorZone.Stack stack) {
		return new Advice(tone, KEY + "advice." + name + ".title", KEY + "advice." + name + ".body",
				List.of(stack.x() + 1, stack.z() + 1));
	}

	private static Advice plain(ReactorConsole.Tone tone, String name) {
		return new Advice(tone, KEY + "advice." + name + ".title", KEY + "advice." + name + ".body", List.of());
	}
}
