package dev.alaindustrial.core.energy;

/**
 * The balance knobs the energy core reads INSIDE its algorithms, delivered as a value (MOD-710, CORE-10).
 * {@link NetworkManager#balance()} reads them from {@code Config} — the one place in the energy core that
 * does — once per network tick pass, and hands the record to {@link EnergyNetwork#tick(NetworkBalance)};
 * the direct cable-less push gets the same record at the moment it pushes. The kernels below take numbers,
 * not a global, so a test feeds them any balance without touching {@code Config}.
 *
 * <p>Only what an algorithm reads goes here. The grade numbers a cable carries itself ({@link CableType}
 * buffers, caps and loss) and the tier voltages ({@link EnergyTier#maxVoltage()}) stay where they are: the
 * line's deadband is the strongest grade's segment buffer, not {@link #cableBuffer()}.
 *
 * @param cableBuffer the deadband of the direct store → store push in {@link DirectAdjacencyDistributor}
 *     ({@code Config.cableBuffer}, kept as it was: the cabled cascade uses the strongest grade's segment
 *     buffer instead — a known difference, not changed here)
 * @param storageFeedReserveFraction the share of a store's capacity the feed channel never drains
 *     ({@code Config.storageFeedReserveFraction}, read by {@link DischargePlan})
 */
public record NetworkBalance(int cableBuffer, double storageFeedReserveFraction) {
}
