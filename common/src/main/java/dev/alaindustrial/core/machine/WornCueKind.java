package dev.alaindustrial.core.machine;

/**
 * What a generator shows when its consumable component is nearly spent (MOD-654). Declared by the generator
 * itself ({@code AbstractGeneratorBlockEntity#wornCueKind}); the base default is {@link #NONE}, so a
 * generator that wears a component without a visible turning part (the lightning rod's tip) stays silent.
 */
public enum WornCueKind {
	/** No warning. */
	NONE,
	/** A wind mill rotor: smoke from the hub of the blade disc. */
	ROTOR,
	/** A water mill wheel: splinters from the rim of the wheel. */
	WHEEL
}
