package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;
import java.util.function.IntSupplier;

/**
 * Deliberate violator for {@code ArchitectureRules.itemTooltipsReadBalanceThroughServerBalance}
 * (MOD-695): an item-shaped class whose tooltip body reads a knob from {@code Config} — directly and
 * inside a lambda javac lifts out of the method. The stand-in has no Minecraft parameters because this
 * lane has no Minecraft jar; the rule matches the method by name.
 */
public final class TooltipKnobReader {

	int appendHoverText() {
		IntSupplier later = () -> Config.teleporterMaxPoints;
		return Config.euPerXp + later.getAsInt();
	}

	/** MOD-716: a block's owner-declared tooltip is a tooltip body too; the read sits in its lambda. */
	IntSupplier machineTooltip() {
		return () -> Config.euPerXp;
	}

	/** MOD-716: a powered item's owner-declared tooltip, read directly. */
	int toolTooltip() {
		return Config.teleporterMaxPoints;
	}
}
