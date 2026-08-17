package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: one machine's career statistics for the panel behind the GUI's statistics tab (MOD-125).
 * Sent to exactly ONE player — the one whose screen is open on that machine.
 *
 * <p><b>Why a payload and not {@code ContainerData}.</b> The vanilla container channel serialises each
 * value as a signed 16-bit short ({@code ClientboundContainerSetDataPacket}, verified against the 26.2
 * bytecode), so anything past 32767 arrives negative. Lifetime EU passes that within minutes of play —
 * the counters here are exactly the numbers that cannot travel that way.
 *
 * <p><b>Why it still costs almost nothing.</b> It rides {@code MachineMenu.broadcastChanges()}, which
 * vanilla already calls once per tick for an OPEN menu and never for a closed one, throttled there to
 * one packet every {@code STATS_SYNC_INTERVAL_TICKS}. No block scans the player list, and a machine
 * nobody is watching produces no traffic at all.
 *
 * <p>{@code containerId} guards against the packet landing on the wrong screen: a player who closes one
 * machine and opens another within the flight time would otherwise see the previous block's numbers for
 * one frame. The client drops any packet whose id is not its current menu's.
 *
 * @param containerId the menu this snapshot belongs to ({@code AbstractContainerMenu.containerId})
 * @param activeTicks ticks the block spent actually working — NOT its age. A machine that has never run
 *     reports zero, which is the honest answer; an age counter under a "working time" label climbs on a
 *     block that has never done anything
 * @param energyIn EU delivered into the block from outside, lifetime
 * @param energyOut EU drawn out of the block by the network, lifetime
 * @param energyGenerated EU the block produced itself, lifetime (exceeds {@code energyOut} by whatever a
 *     full buffer discarded — the use-it-or-lose-it loss worth showing the player)
 * @param energyConsumed EU the block spent on its own work, lifetime
 * @param euRate average EU/t over the window just elapsed, not the instantaneous tick — a generator that
 *     delivers in bursts is unreadable otherwise
 * @param peakEuRate highest per-tick rate since the block entity loaded (session, never persisted)
 * @param connections direct-face connections, sources in the low 16 bits and sinks in the next 16
 * @param itemsProcessed completed operations, lifetime (carried now, drawn when the machines task lands)
 */
public record MachineStatsPayload(
		int containerId,
		long activeTicks,
		long energyIn,
		long energyOut,
		long energyGenerated,
		long energyConsumed,
		int euRate,
		int peakEuRate,
		int connections,
		long itemsProcessed) implements CustomPacketPayload {

	public static final Type<MachineStatsPayload> TYPE = new Type<>(Industrialization.id("machine_stats"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MachineStatsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MachineStatsPayload::containerId,
			ByteBufCodecs.VAR_LONG, MachineStatsPayload::activeTicks,
			ByteBufCodecs.VAR_LONG, MachineStatsPayload::energyIn,
			ByteBufCodecs.VAR_LONG, MachineStatsPayload::energyOut,
			ByteBufCodecs.VAR_LONG, MachineStatsPayload::energyGenerated,
			ByteBufCodecs.VAR_LONG, MachineStatsPayload::energyConsumed,
			ByteBufCodecs.VAR_INT, MachineStatsPayload::euRate,
			ByteBufCodecs.VAR_INT, MachineStatsPayload::peakEuRate,
			ByteBufCodecs.VAR_INT, MachineStatsPayload::connections,
			ByteBufCodecs.VAR_LONG, MachineStatsPayload::itemsProcessed,
			MachineStatsPayload::new);

	/** Sources among the six direct faces (unpacked from {@link #connections}). */
	public int sources() {
		return connections & 0xFFFF;
	}

	/** Sinks among the six direct faces (unpacked from {@link #connections}). */
	public int sinks() {
		return (connections >>> 16) & 0xFFFF;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
