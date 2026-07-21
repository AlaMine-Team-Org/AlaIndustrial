package dev.alaindustrial.core.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.neoforge.NeoForgeEnergyPort;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import dev.alaindustrial.core.energy.EnergyPort;

/**
 * MOD-113 — direct L1.5 coverage of {@link FluidTank}, the platform-neutral tank. Closes the
 * 37-mutant NO_COVERAGE hole that {@code :common:pitest} reports against this class: {@link FluidTank}'s
 * public API takes a {@link FluidHolder} (wrapping {@code net.minecraft.Fluid}, absent from
 * {@code :common}'s L1 test classpath), so the pure-JUnit {@code TankMath} suite can only cover the
 * extracted math helpers — not the {@link FluidTank} methods that thread the {@link FluidHolder}
 * identity, the {@link EnergyPort.Txn} enlistment, and the deferred fluid-clear invariant.
 *
 * <p>This class runs in {@code :neoforge:test} where ModDevGradle's {@code unitTest} lane boots a
 * headless {@link MinecraftServer} (via {@link EphemeralTestServerProvider}), so {@link Fluids} is on
 * the classpath and {@link FluidHolder#of} produces real holders. The tank is driven directly through
 * {@link FluidTank#insert}/{@link FluidTank#extract} inside a real {@link Transaction} wrapped via
 * {@link NeoForgeEnergyPort#wrap} — exactly the snapshot-journal bridge a live pump/geothermal move
 * uses, so the transaction terminal hooks ({@link FluidTank#readSnapshot} rollback,
 * {@link FluidTank#onFinalCommit} commit) exercise the deferred {@code fluid}-clear invariant for real.
 *
 * <p><b>Why pitest still shows these as NO_COVERAGE.</b> The {@code :common:pitest} lane has no
 * Minecraft jar and cannot see this test class at all. Closing the mutants <em>at pitest level</em>
 * requires a separate {@code :neoforge:pitest} lane (an L1.5 lane that mutates against the MC-bearing
 * classpath); that lane is FML-classloading-blocked per the MOD-110 audit and tracked separately. This
 * test nonetheless raises the <em>functional</em> coverage: every {@link FluidTank} method is now
 * exercised by a real assertion, so a future regression in any of them fails here even though pitest
 * does not (yet) count it.
 *
 * <p>Every NeoForge/MC symbol here is verified against the decompiled 26.2 sources and the existing
 * {@code NeoForgeFluidRuntimeTest} precedent: {@code Transaction.openRoot()/commit()/close()},
 * {@code NeoForgeEnergyPort.wrap(TransactionContext)}, {@code Fluids.LAVA/WATER/EMPTY},
 * {@link FluidHolder#of(net.minecraft.world.level.material.Fluid)}.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class FluidTankTest {

	private static final FluidHolder LAVA = FluidHolder.of(Fluids.LAVA);
	private static final FluidHolder WATER = FluidHolder.of(Fluids.WATER);

	/** A tank whose {@code canInsert}/{@code canExtract} predicates are pinned by the test. */
	private static FluidTank tank(long capacity, boolean canInsert, boolean canExtract) {
		return new FluidTank(capacity, f -> canInsert, f -> canExtract, () -> {
		});
	}

	// --- insert math (L98-L108): capacity clamp, single-variant, predicate gate ---

	@Test
	void insert_cappedByRemainingCapacity(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(100, true, true);
		t.amount = 95;
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(5L, t.insert(LAVA, 1000, NeoForgeEnergyPort.wrap(tx)),
					"insert must not overfill past capacity");
			assertEquals(100L, t.amount, "amount must reach exactly capacity");
			tx.commit();
		}
		assertEquals(100L, t.amount, "committed insert must persist");
	}

	@Test
	void insert_cappedByRequestedAmount(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, true, true);
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(7L, t.insert(LAVA, 7, NeoForgeEnergyPort.wrap(tx)),
					"insert must not exceed the requested amount");
			tx.commit();
		}
		assertEquals(7L, t.amount);
	}

	@Test
	void insert_rejectsDifferentFluid(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, true, true);
		t.fluid = LAVA;
		t.amount = 50;
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(0L, t.insert(WATER, 100, NeoForgeEnergyPort.wrap(tx)),
					"single-variant tank must refuse a different fluid");
			tx.commit();
		}
		assertEquals(50L, t.amount, "amount must be unchanged on a refused insert");
		assertTrue(t.fluid.is(Fluids.LAVA), "fluid identity must be unchanged on a refused insert");
	}

	@Test
	void insert_rejectsNullAndEmptyAndZero(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, true, true);
		try (Transaction tx = Transaction.openRoot()) {
			EnergyPort.Txn txn = NeoForgeEnergyPort.wrap(tx);
			assertEquals(0L, t.insert(null, 100, txn), "null fluid must be refused");
			assertEquals(0L, t.insert(FluidHolder.EMPTY, 100, txn), "EMPTY fluid must be refused");
			assertEquals(0L, t.insert(LAVA, 0, txn), "maxAmount=0 is a no-op probe");
			tx.commit();
		}
		assertEquals(0L, t.amount);
		assertTrue(t.fluid.isEmpty());
	}

	@Test
	void insert_rejectedByPredicate(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, false, true); // canInsert == false
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(0L, t.insert(LAVA, 100, NeoForgeEnergyPort.wrap(tx)),
					"a canInsert=false tank must refuse everything");
			tx.commit();
		}
		assertEquals(0L, t.amount);
	}

	@Test
	void insert_negativeMaxAmountThrows(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, true, true);
		try (Transaction tx = Transaction.openRoot()) {
			assertThrows(IllegalArgumentException.class,
					() -> t.insert(LAVA, -1, NeoForgeEnergyPort.wrap(tx)));
		}
	}

	@Test
	void insert_fullTankAcceptsNothing(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(100, true, true);
		t.fluid = LAVA;
		t.amount = 100;
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(0L, t.insert(LAVA, 32, NeoForgeEnergyPort.wrap(tx)),
					"a full tank must accept 0 more");
			tx.commit();
		}
		assertEquals(100L, t.amount);
	}

	// --- extract math (L111-L141): stored-amount clamp, predicate gate, deferred fluid clear ---

	@Test
	void extract_cappedByStoredAmount(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, true, true);
		t.fluid = LAVA;
		t.amount = 9;
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(9L, t.extract(LAVA, 1000, NeoForgeEnergyPort.wrap(tx)),
					"cannot extract more than is stored");
			tx.commit();
		}
		// Commit drained the tank to 0 → onFinalCommit clears the fluid identity.
		assertEquals(0L, t.amount);
		assertTrue(t.fluid.isEmpty(), "committed full drain must clear the fluid identity");
	}

	@Test
	void extract_rejectedByPredicate(MinecraftServer server) {
		assertNotNull(server);
		// Mirrors the geothermal generator: canExtract == false (R-CON-08) — internal-only tank.
		FluidTank t = tank(1000, true, false);
		t.fluid = LAVA;
		t.amount = 100;
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(0L, t.extract(LAVA, 100, NeoForgeEnergyPort.wrap(tx)),
					"a canExtract=false tank must emit nothing");
			tx.commit();
		}
		assertEquals(100L, t.amount, "amount must be unchanged on a refused extract");
	}

	@Test
	void extract_rejectsMismatchedAndEmptyAndZero(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, true, true);
		t.fluid = LAVA;
		t.amount = 100;
		try (Transaction tx = Transaction.openRoot()) {
			EnergyPort.Txn txn = NeoForgeEnergyPort.wrap(tx);
			assertEquals(0L, t.extract(WATER, 100, txn), "mismatched fluid must extract 0");
			assertEquals(0L, t.extract(null, 100, txn), "null fluid must extract 0");
			assertEquals(0L, t.extract(FluidHolder.EMPTY, 100, txn), "EMPTY fluid must extract 0");
			assertEquals(0L, t.extract(LAVA, 0, txn), "maxAmount=0 is a no-op probe");
			tx.commit();
		}
		assertEquals(100L, t.amount, "no extract must have changed the amount");
	}

	@Test
	void extract_fromEmptyTankReturnsZero(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, true, true); // starts empty
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(0L, t.extract(LAVA, 100, NeoForgeEnergyPort.wrap(tx)));
			tx.commit();
		}
		assertEquals(0L, t.amount);
		assertTrue(t.fluid.isEmpty());
	}

	@Test
	void extract_negativeMaxAmountThrows(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, true, true);
		t.fluid = LAVA;
		t.amount = 50;
		try (Transaction tx = Transaction.openRoot()) {
			assertThrows(IllegalArgumentException.class,
					() -> t.extract(LAVA, -1, NeoForgeEnergyPort.wrap(tx)));
		}
	}

	// --- deferred fluid-clear invariant: the two transaction terminals ---

	/**
	 * The commit terminal: a committed extract that drives the tank to 0 must clear {@code fluid} via
	 * {@link FluidTank#onFinalCommit} (extract itself does NOT clear it — see FluidTank.extract javadoc).
	 * A mutant that drops the {@code onFinalCommit} clear, or the {@code shouldClearFluid} test inside it,
	 * leaves the tank reporting {@code amount==0, fluid==LAVA} — invisible to capability readers.
	 */
	@Test
	void extract_commitToZero_clearsFluidIdentity(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, true, true);
		t.fluid = LAVA;
		t.amount = 50;
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(50L, t.extract(LAVA, 50, NeoForgeEnergyPort.wrap(tx)));
			// Mid-transaction: extract already mutated amount but MUST NOT have cleared fluid yet.
			assertEquals(0L, t.amount, "mid-transaction amount reflects the extract");
			assertTrue(t.fluid.is(Fluids.LAVA),
					"mid-transaction fluid must NOT be cleared (deferred-clear invariant)");
			tx.commit();
		}
		assertTrue(t.fluid.isEmpty(), "onFinalCommit must clear fluid after a draining commit");
	}

	/**
	 * The rollback terminal: an uncommitted full-drain must restore BOTH {@code amount} AND {@code fluid}.
	 * {@link FluidTank#readSnapshot} restores {@code amount} from the journal and clears {@code fluid}
	 * only when the snapshot is 0; here the snapshot is the pre-drain positive amount, so {@code fluid}
	 * is left in place (extract never cleared it). A mutant that drops the {@code shouldClearFluid}
	 * guard or restores the wrong snapshot breaks this.
	 */
	@Test
	void extract_uncommittedDrain_rollsBackAmountAndFluid(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, true, true);
		t.fluid = LAVA;
		t.amount = 100;
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(100L, t.extract(LAVA, 100, NeoForgeEnergyPort.wrap(tx)));
			assertEquals(0L, t.amount, "mid-transaction amount reflects the full drain");
			// no commit — close() rolls back.
		}
		assertEquals(100L, t.amount, "uncommitted drain must roll amount back");
		assertTrue(t.fluid.is(Fluids.LAVA),
				"rolled-back full drain must restore the fluid identity (deferred-clear invariant)");
	}

	/**
	 * A partial, committed extract leaves a positive amount: the {@code shouldClearFluid} test on both
	 * terminals must NOT clear {@code fluid} (the tank still holds something). A boundary mutant
	 * ({@code ==} → {@code <=}) on {@link TankMath#shouldClearFluid} would wipe a non-empty tank's
	 * identity. This kills that mutant at the integration level.
	 */
	@Test
	void extract_partialCommit_keepsFluidIdentity(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, true, true);
		t.fluid = LAVA;
		t.amount = 100;
		try (Transaction tx = Transaction.openRoot()) {
			t.extract(LAVA, 30, NeoForgeEnergyPort.wrap(tx));
			tx.commit();
		}
		assertEquals(70L, t.amount);
		assertTrue(t.fluid.is(Fluids.LAVA), "partial drain must NOT clear the fluid identity");
	}

	// --- EnergyPort.Participant contract: snapshot/restore + onCommit ---

	@Test
	void snapshot_roundTripsAmount(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(1000, true, true);
		t.amount = 321;
		long snap = t.createSnapshot();
		t.amount = 0;
		t.readSnapshot(snap);
		assertEquals(321L, t.amount, "readSnapshot must restore the captured amount");
	}

	@Test
	void onFinalCommit_firesTheConfiguredCallback(MinecraftServer server) {
		assertNotNull(server);
		int[] fired = {0};
		FluidTank t = new FluidTank(1000, f -> true, f -> true, () -> fired[0]++);
		t.onFinalCommit();
		assertEquals(1, fired[0], "onFinalCommit must invoke the configured onCommit callback");
		t.onFinalCommit();
		assertEquals(2, fired[0], "the callback fires once per onFinalCommit call");
	}

	// --- capability flags + getters (L159-L166): follow capacity, not a hardcoded value ---

	@Test
	void capabilityFlags_followCapacity(MinecraftServer server) {
		assertNotNull(server);
		assertTrue(tank(100, true, true).supportsInsertion(), "positive capacity → supports insertion");
		assertTrue(tank(100, true, true).supportsExtraction(), "positive capacity → supports extraction");
		assertFalse(tank(0, true, true).supportsInsertion(), "zero-capacity tank is sealed (no insert)");
		assertFalse(tank(0, true, true).supportsExtraction(), "zero-capacity tank is sealed (no extract)");
	}

	@Test
	void getters_returnLiveFields(MinecraftServer server) {
		assertNotNull(server);
		FluidTank t = tank(5000, true, true);
		t.fluid = LAVA;
		t.amount = 4321;
		assertEquals(4321L, t.getAmount(), "getAmount returns the live stored amount");
		assertEquals(5000L, t.getCapacity(), "getCapacity returns the configured capacity");
		assertTrue(t.fluid().is(Fluids.LAVA), "fluid() returns the live fluid identity");
		// Empty tank reports the real zero, not a hardcoded-0 return mutant.
		FluidTank empty = tank(5000, true, true);
		assertEquals(0L, empty.getAmount());
		assertTrue(empty.fluid().isEmpty());
		assertNotEquals(FluidHolder.EMPTY, t.fluid(), "non-empty tank fluid() must not be EMPTY");
	}

	// --- constructor guard (L73-L82): negative capacity is invalid ---

	@Test
	void constructor_rejectsNegativeCapacity(MinecraftServer server) {
		assertNotNull(server);
		assertThrows(IllegalArgumentException.class, () -> tank(-1, true, true),
				"negative capacity must be rejected");
	}

	@Test
	void constructor_acceptsZeroCapacity(MinecraftServer server) {
		assertNotNull(server);
		FluidTank zero = tank(0, true, true);
		assertEquals(0L, zero.getCapacity(), "zero capacity is legal (sealed/decorative tank)");
	}

	private static void assertNotNull(Object o) {
		org.junit.jupiter.api.Assertions.assertNotNull(o, "ephemeral MinecraftServer was not injected");
	}
}
