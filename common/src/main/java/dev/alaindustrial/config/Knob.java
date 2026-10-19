package dev.alaindustrial.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a {@code public static} field of a knob holder as a tunable: which section of the file it
 * lives in, the inline documentation written above it, and the range the loader accepts (MOD-553).
 *
 * <p><b>This annotation IS the registration.</b> There is no second list to keep in step — that
 * mirror list is what MOD-553 deleted. A field carrying this annotation in a class that
 * {@link KnobRegistry} scans is loaded, validated, written and reset; a field without one is an
 * ordinary static that the config file never sees.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Knob {
	/** Json object this key lives in, and the heading an operator reads it under. */
	Section section();

	/** The {@code _comment_<key>} line written above the value, in the operator's own file. */
	String doc();

	/**
	 * Lowest value the loader accepts. The default means "no lower bound at all", which is what a
	 * boolean knob wants; every numeric knob declares one.
	 */
	double min() default Double.NEGATIVE_INFINITY;

	/** When true the bound is exclusive: the value must be strictly GREATER than {@link #min()}. */
	boolean exclusive() default false;

	/**
	 * Value substituted for an out-of-range one. {@link Double#NaN} — the default — means "restore
	 * this build's compiled default", which is what a knob wants when its default is also its
	 * recovery value. A knob that deliberately clamps to a range BOUNDARY instead says so here, so
	 * the exception is visible rather than implied: {@code euPerXp}, {@code euPerXpGenerated} and
	 * {@code xpLevelOneCost} floor at 1 because they guard a division, and every double knob names
	 * its floor because "no loss / no reserve" is a legal answer their default is not.
	 */
	double floorTo() default Double.NaN;

	/**
	 * True when client code shows this knob to the player (MOD-695): a tooltip, a recipe-viewer page
	 * or a screen reads it. Such a knob travels to every client in {@code ConfigSyncPayload}, and the
	 * client reads it only through {@code dev.alaindustrial.client.ServerBalance} — never from the
	 * holder, whose fields hold the CLIENT's own file on a dedicated server and the integrated server's
	 * live balance in single player. {@code ServerBalanceTest} holds the flag and the readers in step.
	 */
	boolean clientVisible() default false;

	/**
	 * The default this knob compiles to in a DEVELOPMENT environment (a dev client, a dev server, a gametest run),
	 * or {@link Double#NaN} — the default — for none (MOD-847). A loader that knows it runs from a development
	 * environment calls {@link KnobRegistry#useDevelopmentDefaults()} before the first load, and from then on this
	 * value IS the build's default for every rule of ADR-033: an absent file is written with it, a missing key takes
	 * it, an untouched knob follows it and a value the operator edited stays theirs. Use it for a wait a tester
	 * cannot sit through in game — a whole day's restoration shortened to seconds — never for balance. Int knobs only.
	 */
	double devDefault() default Double.NaN;
}
