package dev.alaindustrial.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.alaindustrial.KnobSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * MOD-695: the knobs flagged {@code @Knob(clientVisible = true)} are exactly the knobs client code
 * reads. Read from the compiled bytecode, in the direction the other checks cannot see.
 *
 * <p>Three checks hold the set in place together. {@code ServerBalanceTest} pins flag ⇔ accessor (one
 * {@code ServerBalance} method per flagged knob). {@code ArchitectureRules.clientReadsBalanceThroughServerBalance}
 * forbids a client read from {@code Config}, so every knob the client reads has an accessor and hence a
 * flag. This class closes the loop: every accessor is actually CALLED from client code, so a flag cannot
 * outlive the last reader and send a knob nobody shows.
 *
 * <p><b>Naming.</b> No {@code Test} suffix, like {@link ArchitectureRules}: nothing here to mutate.
 */
class ClientVisibleKnobsRules {

	private static final String SERVER_BALANCE = "dev.alaindustrial.client.ServerBalance";

	private static JavaClasses production;

	@BeforeAll
	static void importProduction() {
		production = new ClassFileImporter()
				.withImportOption(new ImportOption.DoNotIncludeTests())
				.importPackages("dev.alaindustrial");
		assertTrue(production.contain(SERVER_BALANCE), "ServerBalance must be on the test classpath");
		assertTrue(production.contain("dev.alaindustrial.client.tooltip.MachineTooltips"),
				"client classes must be on the test classpath, or every check below is vacuous");
	}

	/**
	 * Whether {@code member} is reached from outside {@code ServerBalance}: directly, or through one of
	 * ServerBalance's own derived methods (machineEuPerTickEffective, …) that is itself reached from
	 * outside. A caller owned by ServerBalance alone does not count — otherwise every derived input would
	 * pass whether or not anything shows it (MOD-695 review). Calls and method references alike:
	 * RecipeViewerInfo hands suppliers around. Item tooltips (item..) count as readers too.
	 */
	private static boolean readOutside(JavaMethod member, JavaClass balance, int depth) {
		for (JavaAccess<?> access : member.getAccessesToSelf()) {
			if (!access.getOriginOwner().equals(balance)) {
				return true;
			}
			if (depth > 1 && access.getOrigin() instanceof JavaMethod derived && !derived.equals(member)
					&& readOutside(derived, balance, depth - 1)) {
				return true;
			}
		}
		return false;
	}

	/** @implements MOD-695-FLAG — every flagged knob is read outside ServerBalance (through its accessor) */
	@Test
	void everyFlaggedKnobIsReadByClientCode() {
		JavaClass balance = production.get(SERVER_BALANCE);
		Set<String> flagged = new TreeSet<>(KnobSnapshot.capture().keys());
		List<String> unread = new ArrayList<>();
		for (String key : flagged) {
			JavaMethod accessor = balance.getMethod(key);
			if (!readOutside(accessor, balance, 2)) {
				unread.add(key);
			}
		}
		assertTrue(flagged.size() > 50, "too few flagged knobs to trust: " + flagged);
		assertTrue(unread.isEmpty(), "flagged clientVisible but no client code reads them — drop the flag and "
				+ "the ServerBalance accessor, or they are sent to every client for nothing: " + unread);
	}
}
