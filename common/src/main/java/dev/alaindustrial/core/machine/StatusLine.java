package dev.alaindustrial.core.machine;

/**
 * A machine's one-line "why am I idle" state, the shape every status enum of the mod gives its screen
 * (MOD-716, CLI-3): the translation key of the caption, and whether the state holds the machine up.
 *
 * <p>Thirteen enums carried the same two facts under three different shapes — {@code translationKey()} +
 * {@code isBlocking()}, {@code key()} + {@code isSilent()}, a {@code code()} the screen mapped to a key by
 * hand — so a screen could not draw any of them through one helper. Each now implements this interface on
 * top of its own API; no translation key was renamed.
 *
 * <p><b>Blocking</b> means: the machine is not running in this state, for a reason the player can act on.
 * The working states and the quiet ones ("nothing loaded yet") are not blocking. Where an enum named this
 * itself ({@code isBlocking}, {@code needsAttention}, {@code isSilent}, {@code canWork}) that answer is kept;
 * where it did not, the state the screen treats as "running" is the one non-blocking state.
 */
public interface StatusLine {

	/** Translation key of the caption. */
	String translationKey();

	/** Whether this state holds the machine up for a reason the player can fix. */
	boolean isBlocking();
}
