package dev.alaindustrial.core.energy;

import dev.alaindustrial.Config;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * The three shipped cable grades and the balance numbers that make them different (MOD-219).
 *
 * <p>Before this existed, all cable blocks shared one {@code CableBlockEntity} hardcoded to
 * {@link EnergyTier#LV}, {@link Config#cableBuffer} and {@link Config#copperCableLossPerBlock}, so
 * tin/insulated cables were literally recoloured copper. Each grade now carries its own three knobs:
 *
 * <ul>
 *   <li>{@link #segmentBuffer()} — the per-segment live buffer, and therefore the cable's <b>real
 *       throughput</b>: energy flows segment-to-segment through these buffers (MOD-070), so a cable
 *       carries {@code segmentBuffer} EU/tick. This — not {@link #packetCap()} — is what a player
 *       feels as "thicker wire".</li>
 *   <li>{@link #packetCap()} — the per-tick ceiling on what one source may be drawn for, applied by
 *       {@link EnergyNetwork#tick()}. A separate, higher ceiling than the buffer.</li>
 *   <li>{@link #lossPerBlock()} — the resistive fraction destroyed per cable block traversed
 *       (MOD-021 proportional model).</li>
 * </ul>
 *
 * <p><b>Ladder</b> (source of truth: {@code docs/research/cable-balance-model.md} §3, mirrored in
 * {@code docs/PERFORMANCE.md}). Every grade is strictly better than the one before on at least one
 * axis, so the choice is real rather than cosmetic:
 *
 * <table border="1">
 *   <caption>Cable grades</caption>
 *   <tr><th>Grade</th><th>Tier</th><th>Throughput (buffer)</th><th>Packet cap</th><th>Loss/block</th><th>Niche</th></tr>
 *   <tr><td>{@link #TIN}</td><td>LV</td><td>8 EU/t</td><td>8 EU/t</td><td>0.006</td>
 *       <td>Cheap solar-farm wire: a 1 EU/t panel trickle floors to <b>zero</b> loss at any distance</td></tr>
 *   <tr><td>{@link #COPPER}</td><td>LV</td><td>12 EU/t</td><td>32 EU/t</td><td>0.02</td>
 *       <td>The universal workhorse (unchanged — these are the shipped v0.1 numbers)</td></tr>
 *   <tr><td>{@link #GOLD}</td><td>MV</td><td>48 EU/t</td><td>128 EU/t</td><td>0.03</td>
 *       <td>Wide pipe: 4× copper's throughput, paid for with the highest loss per block</td></tr>
 * </table>
 *
 * <p>All numbers are read <b>live</b> from {@link Config} (same contract as
 * {@link EnergyTier#maxVoltage()}), so a server operator can retune them from
 * {@code config/alaindustrial.json} without a code change. The enum carries no compile-time copy of
 * the values on purpose — {@link Config}'s field defaults are the single source.
 *
 * <p><b>Tin is not its own tier.</b> There is no sub-LV entry in {@link EnergyTier}; tin is an LV
 * cable whose packet cap is simply lower than the LV tier voltage. That is why {@link #packetCap()}
 * is a property of the cable grade rather than being derived from {@link #tier()} — deriving it from
 * the tier is exactly what made all four cables identical before.
 *
 * <p><b>Deliberately free of Minecraft types</b> (no {@code StringRepresentable}), so the L1 unit
 * suite — which runs without the game on the classpath — can assert the ladder directly. The
 * serialization codec that needs an MC type lives next to its only consumer, in
 * {@code CableBlock.TYPE_CODEC}.
 */
public enum CableType {
	/** Cheapest grade: narrow but nearly lossless — the solar-farm wire. */
	TIN("tin", EnergyTier.LV, () -> Config.tinCablePacketCap, () -> Config.tinCableBuffer,
			() -> Config.tinCableLossPerBlock),
	/** The shipped v0.1 workhorse. Its knobs keep their original Config names for save/config compat. */
	COPPER("copper", EnergyTier.LV, () -> Config.tierLvVoltage, () -> Config.cableBuffer,
			() -> Config.copperCableLossPerBlock),
	/** MV grade: 4× copper throughput at the highest loss per block. */
	GOLD("gold", EnergyTier.MV, () -> Config.tierMvVoltage, () -> Config.goldCableBuffer,
			() -> Config.goldCableLossPerBlock);

	private final String name;
	private final EnergyTier tier;
	private final IntSupplier packetCap;
	private final IntSupplier segmentBuffer;
	private final DoubleSupplier lossPerBlock;

	CableType(String name, EnergyTier tier, IntSupplier packetCap, IntSupplier segmentBuffer,
			DoubleSupplier lossPerBlock) {
		this.name = name;
		this.tier = tier;
		this.packetCap = packetCap;
		this.segmentBuffer = segmentBuffer;
		this.lossPerBlock = lossPerBlock;
	}

	/** The voltage tier this cable belongs to — drives the tooltip and machine-facing semantics. */
	public EnergyTier tier() {
		return tier;
	}

	/**
	 * Per-tick ceiling on the EU drawn from a single source through this cable, applied network-wide by
	 * {@link EnergyNetwork#tick()}. NOT the throughput — see {@link #segmentBuffer()}.
	 */
	public long packetCap() {
		return packetCap.getAsInt();
	}

	/**
	 * The live per-segment buffer (EU) — the cable's actual throughput per tick, because energy moves
	 * through these buffers rather than teleporting (MOD-070). Fixed per block entity at construction
	 * ({@code EnergyBuffer.capacity} is final), so changing the config affects newly placed cables.
	 */
	public long segmentBuffer() {
		return segmentBuffer.getAsInt();
	}

	/** Fraction of throughput destroyed per cable block traversed ({@code floor(flow × k × distance)}). */
	public double lossPerBlock() {
		return lossPerBlock.getAsDouble();
	}

	/**
	 * Whether this grade governs a mixed network over {@code other} — i.e. carries the strictly higher
	 * {@link #packetCap()}. A network takes BOTH its packet cap and its loss from the single strongest
	 * cable present (the rule established for the tier cap in MOD-101 and extended to loss here), so one
	 * gold segment lifts the whole line to 128 EU/t <b>and</b> to gold's 0.03 loss. That coupling is
	 * deliberate: it keeps gold's "wide pipe, higher toll" trade honest instead of letting a player
	 * cherry-pick a high cap at copper's loss by splicing in one cheap segment.
	 */
	public boolean strongerThan(CableType other) {
		return packetCap() > other.packetCap();
	}

	/** Stable id used by {@code CableBlock}'s codec — never rename without a save migration. */
	public String serializedName() {
		return name;
	}
}
