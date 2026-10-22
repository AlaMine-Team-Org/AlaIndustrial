package dev.alaindustrial.settlement.works;

import java.util.Locale;
import java.util.Optional;

/**
 * What a job of a town's work queue asks for (MOD-857, epic decision R79). The list grows with the stages of the
 * epic — roads (MOD-861), repairs, deliveries — by a new constant here; a save keeps the job by
 * {@link #serializedName()}, so a constant is never renamed, and a job of a type this build does not know is dropped
 * on load with a warning instead of failing the file.
 *
 * <p>Minecraft-free on purpose: the L1 lane reads it.
 */
public enum WorkJobType {
	/** Take the debris out of a building (R85, R93): only blocks on the debris list, liquids where air belongs. */
	CLEAR,
	/** Put the building's intact template back, block by block. */
	BUILD;

	/** The name in saves and in translation keys: {@code clear}, {@code build}. */
	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The type saved as {@code name}, if this build knows it. */
	public static Optional<WorkJobType> byName(String name) {
		for (WorkJobType type : values()) {
			if (type.serializedName().equals(name)) {
				return Optional.of(type);
			}
		}
		return Optional.empty();
	}
}
