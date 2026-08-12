package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.alaindustrial.core.neoforge.NeoForgeEnergyPort;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-285 — leak checks for the per-thread {@code NeoForgeTxn.ACTIVE} cache that backs
 * {@link NeoForgeEnergyPort#wrapForeign}.
 *
 * <p><b>Why this exists.</b> NeoForge gives no close callback on a {@code TransactionContext}, so a
 * foreign mod's transaction — one it opened and closes out of our sight — is tracked by enlisting a
 * housekeeping {@code EvictionJournal} that drops the cache entry when the transaction resolves
 * (MOD-185). That journal only evicts at the ROOT: {@code onRootCommit} fires at root commit, and its
 * abort path is gated on {@code depth() == 0}. The open question these tests answer is what happens
 * when the foreign mod calls our capability from inside a NESTED transaction and then aborts it —
 * a routine "simulate, then commit for real" pattern. If the entry survives that, it is orphaned:
 * NeoForge reuses {@code TransactionContext} identities between successive root transactions, so a
 * later transaction could pick up a stale handle carrying journals from a dead one.
 *
 * <p>{@code activeCacheSize()} exists on {@code NeoForgeTxn} precisely for this assert and had no
 * caller before this suite.
 *
 * @implements MOD-285 foreign-transaction cache eviction
 */
@ExtendWith(EphemeralTestServerProvider.class)
class NeoForgeTxnCacheTest {

	/** Baseline: a foreign transaction that commits at the root must leave the cache as it found it. */
	@Test
	void foreignRootCommit_evictsTheCacheEntry(MinecraftServer server) {
		assertNotNull(server);
		int before = NeoForgeEnergyPort.NeoForgeTxn.activeCacheSize();
		try (Transaction root = Transaction.openRoot()) {
			NeoForgeEnergyPort.wrapForeign(root);
			root.commit();
		}
		assertEquals(before, NeoForgeEnergyPort.NeoForgeTxn.activeCacheSize(),
				"a committed foreign root transaction must not leave its handle behind");
	}

	/** Baseline: the same for a root that aborts (closed without commit). */
	@Test
	void foreignRootAbort_evictsTheCacheEntry(MinecraftServer server) {
		assertNotNull(server);
		int before = NeoForgeEnergyPort.NeoForgeTxn.activeCacheSize();
		try (Transaction root = Transaction.openRoot()) {
			NeoForgeEnergyPort.wrapForeign(root);
			// no commit — closing aborts
		}
		assertEquals(before, NeoForgeEnergyPort.NeoForgeTxn.activeCacheSize(),
				"an aborted foreign root transaction must not leave its handle behind");
	}

	/**
	 * The case under investigation: the foreign mod probes inside a nested transaction, aborts it, and
	 * then commits the root for real. The nested handle must not outlive the root.
	 */
	@Test
	void foreignNestedAbortThenRootCommit_leavesNoOrphan(MinecraftServer server) {
		assertNotNull(server);
		int before = NeoForgeEnergyPort.NeoForgeTxn.activeCacheSize();
		try (Transaction root = Transaction.openRoot()) {
			try (Transaction nested = Transaction.open(root)) {
				NeoForgeEnergyPort.wrapForeign(nested);
				// no commit — the probe is discarded
			}
			root.commit();
		}
		assertEquals(before, NeoForgeEnergyPort.NeoForgeTxn.activeCacheSize(),
				"a nested foreign probe that was aborted must not orphan its handle past the root");
	}

	/** The same shape, but the whole thing unwinds: nested aborts and so does the root. */
	@Test
	void foreignNestedAbortThenRootAbort_leavesNoOrphan(MinecraftServer server) {
		assertNotNull(server);
		int before = NeoForgeEnergyPort.NeoForgeTxn.activeCacheSize();
		try (Transaction root = Transaction.openRoot()) {
			try (Transaction nested = Transaction.open(root)) {
				NeoForgeEnergyPort.wrapForeign(nested);
			}
			// root aborts too
		}
		assertEquals(before, NeoForgeEnergyPort.NeoForgeTxn.activeCacheSize(),
				"neither the nested nor the root handle may survive a full unwind");
	}

	/**
	 * The scenario the {@code depth()==0} gate was written to protect: a buffer is enlisted inside a
	 * nested transaction that aborts, and then enlisted AGAIN in the same root. If evicting on the
	 * nested close dropped a still-live entry, the second enlist would build a SECOND
	 * {@code ParticipantJournal} for the same buffer, and the eventual abort would revert them in
	 * registration order — leaving the buffer at the intermediate value instead of the original.
	 *
	 * <p>It does not, because the cache is keyed by the exact {@code TransactionContext}: the nested
	 * context and the root are different keys, so dropping the nested entry cannot disturb the root's.
	 * This test pins that reasoning rather than leaving it as an argument in a comment.
	 */
	@Test
	void bufferReEnlistedAfterNestedAbort_stillRollsBackFully(MinecraftServer server) {
		assertNotNull(server);
		dev.alaindustrial.core.energy.EnergyBuffer buffer =
				new dev.alaindustrial.core.energy.EnergyBuffer(10_000, 32, 32, () -> { });
		buffer.setAmountUntracked(5_000);
		net.neoforged.neoforge.transfer.energy.EnergyHandler handler =
				dev.alaindustrial.core.neoforge.BufferAsEnergyHandler.of(buffer);
		long original = handler.getAmountAsLong();

		try (Transaction root = Transaction.openRoot()) {
			try (Transaction nested = Transaction.open(root)) {
				handler.insert(32, nested);
				// aborted: this probe must leave nothing behind
			}
			handler.insert(32, root);
			// root aborts too
		}

		assertEquals(original, handler.getAmountAsLong(),
				"after a nested abort and a re-enlist in the same root, the buffer must roll fully back — "
						+ "a second journal would strand it at the intermediate value");
	}

	/** A nested probe that COMMITS into a committing root — the ordinary success path. */
	@Test
	void foreignNestedCommitThenRootCommit_leavesNoOrphan(MinecraftServer server) {
		assertNotNull(server);
		int before = NeoForgeEnergyPort.NeoForgeTxn.activeCacheSize();
		try (Transaction root = Transaction.openRoot()) {
			try (Transaction nested = Transaction.open(root)) {
				NeoForgeEnergyPort.wrapForeign(nested);
				nested.commit();
			}
			root.commit();
		}
		assertEquals(before, NeoForgeEnergyPort.NeoForgeTxn.activeCacheSize(),
				"a committed nested probe must not orphan its handle either");
	}
}
