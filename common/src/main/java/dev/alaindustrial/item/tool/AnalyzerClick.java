package dev.alaindustrial.item.tool;

/**
 * What one right-click with the Network Analyzer does (MOD-665), as a pure decision so the L1 suite can
 * pin the whole table. Minecraft-free on purpose.
 *
 * <table>
 *   <tr><th>aimed at</th><th>no Shift</th><th>Shift</th></tr>
 *   <tr><td>a cable of a network</td><td>scan</td><td>scan</td></tr>
 *   <tr><td>a block without a network</td><td>clear</td><td>switch mode</td></tr>
 *   <tr><td>air</td><td>clear</td><td>switch mode</td></tr>
 * </table>
 *
 * <p>D9: the spec always promised that a click into the air clears the highlight; the item did nothing
 * there, so the only ways to get rid of a trace were to find a block without a network or to switch the
 * overlay off in the settings.
 */
public final class AnalyzerClick {
	/** The three things a click can mean. */
	public enum Action {
		SCAN,
		SWITCH_MODE,
		CLEAR
	}

	private AnalyzerClick() {
	}

	/**
	 * @param onNetwork the click landed on a block that belongs to an energy network (always false in
	 *                  the air)
	 * @param shift     the player is sneaking (secondary use)
	 */
	public static Action decide(boolean onNetwork, boolean shift) {
		if (onNetwork) {
			return Action.SCAN;
		}
		return shift ? Action.SWITCH_MODE : Action.CLEAR;
	}
}
