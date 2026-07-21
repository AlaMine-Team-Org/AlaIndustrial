package dev.alaindustrial.core.fluid;
import dev.alaindustrial.core.energy.EnergyPort;

/**
 * Platform-neutral view of a block's single-fluid tank (MOD-028), the fluid counterpart of
 * {@link EnergyPort}. The mod's two fluid machines (pump, geothermal generator) are written entirely
 * against this interface so the same tank logic drives either loader's fluid API:
 * <ul>
 *   <li><b>Fabric</b> — {@code net.fabricmc.fabric.api.transfer.v1.storage.Storage<FluidVariant>}
 *       (see {@code dev.alaindustrial.core.fabric.FabricFluidPort}).</li>
 *   <li><b>NeoForge</b> — {@code net.neoforged.neoforge.transfer.ResourceHandler<FluidResource>}
 *       (see {@code dev.alaindustrial.core.neoforge.NeoForgeFluidPort}).</li>
 * </ul>
 *
 * <p><b>Units.</b> Amounts are millibuckets (mB) in this mod's own scale, 1 bucket = {@link FluidAmounts#BUCKET};
 * the loader adapters convert to/from the loader-native unit (droplets on Fabric, mB on NeoForge — see
 * {@link FluidAmounts}) so balance numbers match across loaders (MOD-028 decision).
 *
 * <p><b>Transactions.</b> Fluid transfer reuses the exact {@link EnergyPort.Txn}/{@link EnergyPort.Participant}
 * transaction seam the energy core already established (MOD-022 Phase 2): both loaders' native transaction
 * systems (Fabric {@code TransactionContext}, NeoForge {@code TransactionContext}) are the SAME
 * transaction machinery under a fluid move as under an energy move (verified: NeoForge's fluid
 * {@code ResourceHandler} threads {@code net.neoforged.neoforge.transfer.transaction.TransactionContext},
 * identical to {@code EnergyHandler}'s). {@link EnergyPort.Txn} is a snapshot-over-{@code long} handle with
 * no energy-specific semantics, so reusing it here avoids duplicating the per-loader bridging code
 * ({@code FabricTxn}/{@code NeoForgeTxn}) for a second, parallel type (MOD-028 task decision — see
 * task.md "Open questions" #3).
 *
 * <p><b>Single fluid only.</b> Unlike a general-purpose tank, a {@link FluidPort} holds at most one kind
 * of fluid at a time — the mod's fluid machines only ever move pure lava, so no multi-slot/variant
 * indexing is needed (mirrors {@code SingleVariantStorage} on Fabric).
 */
public interface FluidPort {

	/**
	 * Insert up to {@code maxAmount} mB of {@code fluid} under transaction {@code txn}. Returns the amount
	 * actually inserted (0 if this port cannot receive, is full, or rejects {@code fluid}). Not committed
	 * until the owning transaction commits.
	 */
	long insert(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn);

	/**
	 * Extract up to {@code maxAmount} mB of {@code fluid} under transaction {@code txn}. Returns the amount
	 * actually extracted (0 if this port cannot emit, is empty, or holds a different fluid). Not committed
	 * until the owning transaction commits.
	 */
	long extract(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn);

	/** The fluid currently held, or {@link FluidHolder#EMPTY} if the tank is empty. */
	FluidHolder fluid();

	/** Current stored amount, in mB. */
	long getAmount();

	/** Maximum amount this tank can hold, in mB. */
	long getCapacity();

	/** Whether this port can currently accept fluid. */
	boolean supportsInsertion();

	/** Whether this port can currently emit fluid. */
	boolean supportsExtraction();
}
