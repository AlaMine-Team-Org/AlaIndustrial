package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for the {@link EnergyTransactions} service-locator — the loader-neutral transaction
 * entrypoint. The install/get contract is tiny but load-bearing: {@code get()} throws if nothing was
 * installed (so a misconfigured loader entrypoint fails loud at the first transport tick, not silently),
 * and {@code install} replaces the previous binding (idempotent re-init).
 *
 * @implements energy-transactions service locator (install/get + not-installed guard)
 */
class EnergyTransactionsTest {

	private static final class StubTransactions implements EnergyTransactions {
		@Override
		public void runCommitting(Consumer<EnergyPort.Txn> body) {
		}

		@Override
		public <T> T simulate(Function<EnergyPort.Txn, T> body) {
			return null;
		}
	}

	@BeforeEach
	void clearBefore() {
		// Start every test from an uninstalled state so order doesn't matter.
		EnergyTransactions.install(null);
	}

	@AfterEach
	void clearAfter() {
		EnergyTransactions.install(null);
	}

	@Test
	void get_throwsWhenNotInstalled() {
		IllegalStateException ex = assertThrows(IllegalStateException.class, EnergyTransactions::get,
				"get() must throw if no loader entrypoint has installed an implementation");
		assertTrue(ex.getMessage().contains("not installed"),
				"the error message must explain the missing install() call");
	}

	@Test
	void get_returnsTheInstalledImplementation() {
		StubTransactions stub = new StubTransactions();
		EnergyTransactions.install(stub);
		assertTrue(EnergyTransactions.get() == stub, "get() returns the exact instance installed");
	}

	@Test
	void install_replacesThePreviousImplementation() {
		StubTransactions first = new StubTransactions();
		StubTransactions second = new StubTransactions();
		EnergyTransactions.install(first);
		EnergyTransactions.install(second);
		assertTrue(EnergyTransactions.get() == second,
				"a second install() replaces the first — re-init is idempotent, not append");
	}

	@Test
	void install_nullThenGetThrows() {
		StubTransactions stub = new StubTransactions();
		EnergyTransactions.install(stub);
		assertEquals(stub, EnergyTransactions.get(), "sanity: installed and resolvable");
		EnergyTransactions.install(null);
		assertThrows(IllegalStateException.class, EnergyTransactions::get,
				"install(null) clears the binding — get() throws again");
	}
}
