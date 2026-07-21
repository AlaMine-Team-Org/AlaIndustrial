package dev.alaindustrial.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyTransactions;

/**
 * L1 unit tests for {@link ItemMover#move} — the loader-neutral item-transfer helper that wraps a single
 * atomic {@link ItemPort#moveTo} in an {@link EnergyTransactions#runCommitting} transaction. Uses fake
 * {@link ItemPort} and a fake {@link EnergyTransactions} so no loader runtime is needed.
 *
 * @implements item-mover transactional wrapper (null/non-positive guards, delegates to port.moveTo)
 */
class ItemMoverTest {

	private static final class FakeTransactions implements EnergyTransactions {
		EnergyPort.Txn lastTxn;
		int runCount;

		@Override
		public void runCommitting(Consumer<EnergyPort.Txn> body) {
			runCount++;
			EnergyPort.Txn txn = new NoopTxn();
			lastTxn = txn;
			body.accept(txn);
		}

		@Override
		public <T> T simulate(Function<EnergyPort.Txn, T> body) {
			return body.apply(new NoopTxn());
		}
	}

	private static final class NoopTxn implements EnergyPort.Txn {
		@Override
		public void enlist(EnergyPort.Participant participant) {
		}
	}

	/** A fake ItemPort whose moveTo returns a fixed amount and records its arguments. */
	private static final class StubItemPort implements ItemPort {
		final int moveToReturn;
		ItemPort lastTarget;
		int lastMaxAmount;
		EnergyPort.Txn lastTxn;
		boolean moveToCalled;

		StubItemPort(int moveToReturn) {
			this.moveToReturn = moveToReturn;
		}

		@Override
		public int moveTo(ItemPort target, int maxAmount, EnergyPort.Txn txn) {
			moveToCalled = true;
			lastTarget = target;
			lastMaxAmount = maxAmount;
			lastTxn = txn;
			return moveToReturn;
		}
	}

	private FakeTransactions txns;

	@BeforeEach
	void installFakeTransactions() {
		txns = new FakeTransactions();
		EnergyTransactions.install(txns);
	}

	@AfterEach
	void clearTransactions() {
		EnergyTransactions.install(null);
	}

	@Test
	void move_returnsWhatThePortMoved_andOpensOneTransaction() {
		StubItemPort from = new StubItemPort(7);
		StubItemPort to = new StubItemPort(0);
		assertEquals(7, ItemMover.move(from, to, 16), "returns the amount the port actually moved");
		assertEquals(1, txns.runCount, "exactly one runCommitting transaction is opened");
		assertTrue(from.moveToCalled, "from.moveTo is invoked");
	}

	@Test
	void move_forwardsExactArgsToPortMoveTo() {
		StubItemPort from = new StubItemPort(3);
		StubItemPort to = new StubItemPort(0);
		ItemMover.move(from, to, 16);
		assertTrue(from.lastTarget == to, "the target port is forwarded");
		assertEquals(16, from.lastMaxAmount, "the maxAmount is forwarded");
		assertTrue(from.lastTxn == txns.lastTxn, "the transaction opened by runCommitting is forwarded");
	}

	@Test
	void move_zeroOrNullOrNonPositiveArgsReturnZeroWithoutOpeningATransaction() {
		StubItemPort from = new StubItemPort(7);
		StubItemPort to = new StubItemPort(0);
		assertEquals(0, ItemMover.move(null, to, 16), "null source → 0");
		assertEquals(0, ItemMover.move(from, null, 16), "null target → 0");
		assertEquals(0, ItemMover.move(from, to, 0), "maxAmount=0 → 0");
		assertEquals(0, ItemMover.move(from, to, -3), "negative maxAmount → 0");
		assertEquals(0, txns.runCount, "guards short-circuit before opening any transaction");
	}
}
