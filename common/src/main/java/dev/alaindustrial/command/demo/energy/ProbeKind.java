package dev.alaindustrial.command.demo.energy;

/** What a measured block of an energy-stand bay is to the network (MOD-799). */
public enum ProbeKind {
	/** A creative energy source dialled to a fixed rate: a deterministic generator. */
	SOURCE,
	/** A real generator (solar, concentrator): its output depends on the world. */
	GENERATOR,
	/** A consumer that declares its per-tick need: planned first by the line (ADR-049, rule 1). */
	MACHINE,
	/** A storage block: takes what the machines cannot (rule 2), gives when generators fall short (rule 3). */
	STORE,
	/** A consumer with an unknown need (pump, charge pad, teleporter): planned as a store. */
	UNKNOWN
}
