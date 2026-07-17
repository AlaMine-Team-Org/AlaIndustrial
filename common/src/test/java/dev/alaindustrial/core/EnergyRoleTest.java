package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * L1 unit tests for the {@link EnergyRole} enum — the per-face capability matrix (R-NRG-03). Pure data;
 * no Minecraft runtime. Pitest baseline found 4 uncovered mutants (the {@code canInsert}/{@code canExtract}
 * returns), so this pins the exact boolean matrix and the enum's shape.
 *
 * @implements R-NRG-03 per-face energy role matrix (NONE/IN/OUT/BOTH × insert/extract)
 */
class EnergyRoleTest {

	@ParameterizedTest
	@EnumSource(EnergyRole.class)
	void canInsertAndCanExtractAreConsistentWithRole(EnergyRole role) {
		switch (role) {
			case NONE -> {
				assertFalse(role.canInsert(), "NONE cannot insert");
				assertFalse(role.canExtract(), "NONE cannot extract");
			}
			case IN -> {
				assertTrue(role.canInsert(), "IN can insert");
				assertFalse(role.canExtract(), "IN cannot extract");
			}
			case OUT -> {
				assertFalse(role.canInsert(), "OUT cannot insert");
				assertTrue(role.canExtract(), "OUT can extract");
			}
			case BOTH -> {
				assertTrue(role.canInsert(), "BOTH can insert");
				assertTrue(role.canExtract(), "BOTH can extract");
			}
		}
	}

	/** The four canonical roles exist and no others — catches an added/removed/renamed constant. */
	@Test
	void enumHasExactlyNoneInOutBoth() {
		Set<EnergyRole> all = EnumSet.allOf(EnergyRole.class);
		assertEquals(4, all.size(), "exactly four roles");
		assertTrue(all.contains(EnergyRole.NONE));
		assertTrue(all.contains(EnergyRole.IN));
		assertTrue(all.contains(EnergyRole.OUT));
		assertTrue(all.contains(EnergyRole.BOTH));
	}

	/**
	 * A role that can insert must NOT necessarily be able to extract (and vice versa) — the asymmetric
	 * roles {@code IN}/{@code OUT} are the whole point of R-NRG-03. A mutation swapping the two booleans
	 * inside a constructor or flipping a return would break this asymmetry.
	 */
	@Test
	void inAndOutAreAsymmetric_notMirrors() {
		assertTrue(EnergyRole.IN.canInsert() && !EnergyRole.IN.canExtract(),
				"IN is insert-only");
		assertTrue(EnergyRole.OUT.canExtract() && !EnergyRole.OUT.canInsert(),
				"OUT is extract-only");
	}
}
