package dev.alaindustrial.command.demo;

/**
 * One zone of the demo stand: a group of exhibits that is built as a unit (MOD-709).
 *
 * <p>A zone writes only through the {@link StandWriter} it is handed, so every cell it touches lands in
 * the ledger (ADR-028). Nothing else can run a zone: the writer is made by {@link DemoStand#buildAll}
 * alone, and the zones are listed, in build order, in {@link DemoStand#ZONES}.
 */
@FunctionalInterface
interface DemoZone {
	/** Build the zone's exhibits and stock their inventories, tanks and buffers. */
	void build(StandWriter w);
}
