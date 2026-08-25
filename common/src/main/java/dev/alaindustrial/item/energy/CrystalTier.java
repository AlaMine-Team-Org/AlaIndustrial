package dev.alaindustrial.item.energy;

import dev.alaindustrial.Config;
import java.util.function.LongSupplier;

/**
 * The three EU crystals (MOD-504), loader-neutral, in one place.
 *
 * <p>Each tier is <b>two</b> items, not one with a flag: a <i>blank</i>, which is the only half that
 * has an EU buffer, and the finished <i>crystal</i>, which is an ordinary item with no energy at all —
 * no bar, no charge tooltip, nothing to charge or drain. The blank turns into the crystal the moment
 * its buffer fills up, and that is the last time energy is ever involved.
 *
 * <p>Two items rather than one primed flag is what makes the ladder honest in a recipe: the next tier
 * simply asks for {@code alaindustrial:energy_crystal}, and a half-charged blank cannot be mistaken for
 * it. A vanilla {@code Ingredient} compares items and ignores components, so a single-item design would
 * have accepted an empty blank in place of a finished crystal and no gate would have caught it.
 *
 * <p>The suppliers read {@link Config} <b>live</b> rather than copying its value into a field, so a
 * config reload moves the ladder with it instead of leaving the old balance until the next restart.
 */
public enum CrystalTier {
	/** MV entry rung. */
	ENERGY("energy_crystal", () -> Config.energyCrystalBuffer, () -> Config.energyCrystalInputRate),
	/** HV rung, built around a finished Energy Crystal. */
	LAPOTRON("lapotron_crystal", () -> Config.lapotronCrystalBuffer, () -> Config.lapotronCrystalInputRate),
	/** End of the ladder. */
	RESONANT("resonant_crystal", () -> Config.resonantCrystalBuffer, () -> Config.resonantCrystalInputRate);

	private final String id;
	private final LongSupplier buffer;
	private final LongSupplier inputRate;

	CrystalTier(String id, LongSupplier buffer, LongSupplier inputRate) {
		this.id = id;
		this.buffer = buffer;
		this.inputRate = inputRate;
	}

	/** Registry path of the finished crystal — an ordinary item, no energy. */
	public String id() {
		return id;
	}

	/** Registry path of the blank — the chargeable half. */
	public String blankId() {
		return id + "_blank";
	}

	/** EU the blank must take before it becomes the finished crystal. */
	public long capacity() {
		return buffer.getAsLong();
	}

	/**
	 * Max EU/tick the blank accepts in a charge slot, read live from {@link Config}.
	 *
	 * <p>A charge slot moves {@code min(EnergyTier.MV.maxVoltage(), inputRate)}, so today every tier
	 * effectively tops out at 128 whatever this returns. It stays a per-tier knob so an HV item charger
	 * could raise the ladder later without a code change.
	 */
	public long inputRate() {
		return inputRate.getAsLong();
	}
}
