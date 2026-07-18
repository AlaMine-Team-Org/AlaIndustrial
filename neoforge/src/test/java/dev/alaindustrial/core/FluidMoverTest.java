package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.neoforge.NeoForgeEnergyPort;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-113 — direct L1.5 coverage of {@link FluidMover#move}, the platform-neutral "extract then insert
 * exactly what came out, refund any shortfall" fluid-move helper. Closes the 8-mutant NO_COVERAGE hole
 * that {@code :common:pitest} reports against this class: {@code move}'s signature takes
 * {@link FluidPort} + {@link FluidHolder} (both coupled to {@code net.minecraft.Fluid}, absent from
 * {@code :common}'s L1 classpath), so the pure-JUnit {@code FluidMoverMath} suite covers only the
 * extracted arithmetic — not the {@code move} wiring itself (the null/non-positive guard, the
 * nothing-extracted short-circuit, the refund path, the full-move return).
 *
 * <p>This class runs in {@code :neoforge:test} where ModDevGradle's {@code unitTest} lane boots a
 * headless {@link MinecraftServer} (via {@link EphemeralTestServerProvider}), so {@link Fluids} is on
 * the classpath. {@code move} is driven through two real {@link FluidTank}s (source + target) inside a
 * committed {@link Transaction} wrapped via {@link NeoForgeEnergyPort#wrap} — exactly the shape a live
 * pump→pipe→geothermal move uses.
 *
 * <p><b>Why pitest still shows these as NO_COVERAGE.</b> The {@code :common:pitest} lane has no
 * Minecraft jar and cannot see this test. A {@code :neoforge:pitest} lane that would count these
 * mutants is FML-classloading-blocked per the MOD-110 audit and tracked separately. This test
 * nonetheless raises the <em>functional</em> coverage: every branch of {@code move} is now exercised
 * by a real assertion, so a regression in any of them fails here.
 *
 * <p>Every NeoForge/MC symbol here is verified against {@link FluidTankTest} and the existing
 * {@code NeoForgeFluidRuntimeTest} precedent.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class FluidMoverTest {

	private static final FluidHolder LAVA = FluidHolder.of(Fluids.LAVA);

	/** A two-sided tank (insert + extract both allowed) holding {@code amount} mB of LAVA. */
	private static FluidTank tankOf(long capacity, long amount) {
		FluidTank t = new FluidTank(capacity, f -> true, f -> true, () -> {
		});
		if (amount > 0) {
			t.fluid = LAVA;
			t.amount = amount;
		}
		return t;
	}

	// --- L35 guard: null port or non-positive maxAmount short-circuits to 0 before touching either tank ---

	@Test
	void move_nullPortOrNonPositive_returnsZero(MinecraftServer server) {
		assertNotNull(server);
		FluidTank from = tankOf(1000, 500);
		FluidTank to = tankOf(1000, 0);
		try (Transaction tx = Transaction.openRoot()) {
			EnergyPort.Txn txn = NeoForgeEnergyPort.wrap(tx);
			assertEquals(0L, FluidMover.move(null, to, LAVA, 100, txn), "null source → 0");
			assertEquals(0L, FluidMover.move(from, null, LAVA, 100, txn), "null target → 0");
			assertEquals(0L, FluidMover.move(from, to, LAVA, 0, txn), "maxAmount=0 → 0");
			assertEquals(0L, FluidMover.move(from, to, LAVA, -5, txn), "negative maxAmount → 0");
			tx.commit();
		}
		assertEquals(500L, from.amount, "guard must short-circuit before any extract");
		assertEquals(0L, to.amount, "guard must short-circuit before any insert");
	}

	// --- L39 nothingExtracted: empty source → 0, no insert attempted on target ---

	@Test
	void move_emptySource_returnsZero(MinecraftServer server) {
		assertNotNull(server);
		FluidTank empty = tankOf(1000, 0); // nothing to give
		FluidTank to = tankOf(1000, 0);
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(0L, FluidMover.move(empty, to, LAVA, 100, NeoForgeEnergyPort.wrap(tx)),
					"empty source → 0 moved");
			tx.commit();
		}
		assertEquals(0L, to.amount, "an empty source must not cause any insert into the target");
		assertTrue(to.fluid.isEmpty(), "target fluid identity must stay EMPTY when nothing moved");
	}

	// --- L43 + L52 refund path: target accepts less than extracted → shortfall refunded to source ---

	/**
	 * The load-bearing conservation case. Source has 1000, target has 990/1000 (only 10 mB of room).
	 * {@code move(from, to, LAVA, 1000)} extracts 1000 from source, target accepts 10, the 990 shortfall
	 * is refunded to source in the same transaction. Net result: source drops by 10, target gains 10,
	 * nothing is destroyed. A mutant that mis-computes the refund (e.g. inserts the sum back, or skips
	 * the refund) breaks conservation — the source + target totals must be invariant.
	 *
	 * <p>The return value of {@code move} here is {@code movedWithRefund(inserted=10, refunded=990) = 1000}
	 * — by {@link FluidMover}'s documented contract this is "the amount actually moved" counting both what
	 * landed in the target and what was returned to the source (the gross outflow from source before
	 * refund). The two tank-state assertions below pin the actual end state; the return-value assertion
	 * pins the contract.
	 */
	@Test
	void move_partialAcceptance_refundsShortfallToSource(MinecraftServer server) {
		assertNotNull(server);
		FluidTank from = tankOf(2000, 1000);
		FluidTank to = tankOf(1000, 990); // only 10 mB of room
		long totalBefore = from.amount + to.amount;
		long moved;
		try (Transaction tx = Transaction.openRoot()) {
			moved = FluidMover.move(from, to, LAVA, 1000, NeoForgeEnergyPort.wrap(tx));
			tx.commit();
		}
		assertEquals(1000L, moved,
				"move returns movedWithRefund(inserted + refunded) = 10 + 990 = 1000 (gross source outflow)");
		assertEquals(1000L, to.amount, "target filled to capacity: 990 (start) + 10 (its free room) = 1000");
		assertEquals(990L, from.amount, "source net: 1000 - 10 transferred + 990 refunded = 990");
		assertEquals(totalBefore, from.amount + to.amount,
				"source + target total must be conserved (no fluid destroyed or created)");
		assertTrue(from.fluid.is(Fluids.LAVA), "source still holds LAVA after the refund");
		assertTrue(to.fluid.is(Fluids.LAVA), "target now holds LAVA");
	}

	/**
	 * The full-acceptance happy path: target has plenty of room, the refund branch is skipped (L43
	 * {@code shortfallNeeded} is false), and {@code move} returns {@code inserted} directly (L54). A
	 * mutant that always takes the refund branch, or returns the wrong value here, breaks this.
	 */
	@Test
	void move_fullAcceptance_returnsInsertedAndSkipsRefund(MinecraftServer server) {
		assertNotNull(server);
		FluidTank from = tankOf(2000, 1000);
		FluidTank to = tankOf(2000, 0); // plenty of room
		long moved;
		try (Transaction tx = Transaction.openRoot()) {
			moved = FluidMover.move(from, to, LAVA, 500, NeoForgeEnergyPort.wrap(tx));
			tx.commit();
		}
		assertEquals(500L, moved, "the full 500 mB must move when the target has room");
		assertEquals(500L, from.amount, "source dropped by exactly the moved amount");
		assertEquals(500L, to.amount, "target gained exactly the moved amount");
	}

	/** Exactly one bucket moves, end-to-end — the canonical pump scenario at L1.5 granularity. */
	@Test
	void move_movesExactlyOneBucket(MinecraftServer server) {
		assertNotNull(server);
		final long bucket = 1000L;
		FluidTank from = tankOf(bucket * 4, bucket * 2);
		FluidTank to = tankOf(bucket * 4, 0);
		long moved;
		try (Transaction tx = Transaction.openRoot()) {
			moved = FluidMover.move(from, to, LAVA, bucket, NeoForgeEnergyPort.wrap(tx));
			tx.commit();
		}
		assertEquals(bucket, moved, "exactly 1 bucket (1000 mB) must move");
		assertEquals(bucket, to.amount, "target must gain exactly 1 bucket");
		assertEquals(bucket, from.amount, "source must drop by exactly 1 bucket");
	}

	private static void assertNotNull(Object o) {
		org.junit.jupiter.api.Assertions.assertNotNull(o, "ephemeral MinecraftServer was not injected");
	}
}
