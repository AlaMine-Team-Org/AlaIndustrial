package dev.alaindustrial.core;

/**
 * Platform-neutral view of a block's energy buffer (MOD-022 Phase 2).
 *
 * <p>The mod's energy transport is written entirely against this interface so the same network math
 * drives either loader's energy API:
 * <ul>
 *   <li><b>Fabric</b> — Team Reborn {@code team.reborn.energy.api.EnergyStorage}
 *       (see {@code dev.alaindustrial.core.fabric.FabricEnergyPort}).</li>
 *   <li><b>NeoForge</b> — {@code net.neoforged.neoforge.transfer.energy.EnergyHandler}
 *       (see {@code dev.alaindustrial.core.neoforge.NeoForgeEnergyPort}).</li>
 * </ul>
 *
 * <p><b>Units.</b> Amounts are EU in this mod's own scale; the loader adapters pin a 1:1 ratio to the
 * loader-native unit (FE / TRE-E) so balance numbers match across loaders (MOD-022 decision).
 *
 * <p><b>Transactions.</b> The two loaders model transactions differently — Fabric's
 * {@code SnapshotParticipant} + {@code TransactionContext} versus NeoForge's {@code SnapshotJournal} +
 * {@code Transaction} ({@code openRoot()}/{@code commit()}). Common code never constructs or commits a
 * transaction; it only threads an opaque {@link Txn} handle that the owning loader created, so this
 * interface stays free of loader types. Each adapter unwraps {@link Txn} back to its native
 * transaction. The common {@link EnergyBuffer} additionally enlists itself with the {@link Txn} via
 * {@link Txn#enlist(Participant)} so its rollback / final-commit hooks run on the loader's native
 * journal.
 */
public interface EnergyPort {

	/**
	 * Opaque, loader-neutral handle for an in-flight energy transaction. The concrete implementation
	 * wraps the loader's native transaction object. Common code treats it as a token to pass through to
	 * {@link #insert} / {@link #extract}, and (for the common {@link EnergyBuffer}) as the anchor a
	 * mutation enlists against so it participates in the loader's snapshot/commit lifecycle.
	 */
	interface Txn {
		/**
		 * Enlist {@code participant} with this transaction so that, before its state is mutated, a
		 * snapshot is taken, and on rollback / final commit the participant's hooks fire on the
		 * loader's native journal. Must be called by the participant <em>before</em> it mutates its
		 * own state, and is idempotent within a single (nesting-depth) transaction — mirroring
		 * {@code SnapshotParticipant.updateSnapshots} (Fabric) / {@code SnapshotJournal.updateSnapshots}
		 * (NeoForge).
		 */
		void enlist(Participant participant);
	}

	/**
	 * A transaction participant whose mutable {@code long} state the loader's snapshot journal can save
	 * and restore. The common {@link EnergyBuffer} is the only implementation; the loader adapter bridges
	 * these callbacks onto its native {@code SnapshotParticipant} / {@code SnapshotJournal}.
	 */
	interface Participant {
		/** Capture the current state as a {@code long} (the buffer's stored amount). */
		long createSnapshot();

		/** Restore a previously captured state (transaction rolled back). */
		void readSnapshot(long snapshot);

		/** Fired once, after the outermost transaction that modified this participant commits. */
		void onFinalCommit();
	}

	/**
	 * Insert up to {@code maxAmount} EU under transaction {@code txn}. Returns the amount actually
	 * inserted (0 if this port cannot receive). Not committed until the owning transaction commits.
	 */
	long insert(long maxAmount, Txn txn);

	/**
	 * Extract up to {@code maxAmount} EU under transaction {@code txn}. Returns the amount actually
	 * extracted (0 if this port cannot emit). Not committed until the owning transaction commits.
	 */
	long extract(long maxAmount, Txn txn);

	/** Current stored EU. */
	long getAmount();

	/** Maximum EU this buffer can hold. */
	long getCapacity();

	/** Whether this port can currently accept energy (respects per-face {@link EnergyRole}). */
	boolean supportsInsertion();

	/** Whether this port can currently emit energy (respects per-face {@link EnergyRole}). */
	boolean supportsExtraction();
}
