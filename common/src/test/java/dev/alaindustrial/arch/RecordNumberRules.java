package dev.alaindustrial.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static dev.alaindustrial.arch.VersionedApiRules.callAFacadeOnlyMember;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import dev.alaindustrial.arch.VersionedApiRules.FacadeOnlyMember;
import java.util.List;
import java.util.Set;

/**
 * A record number is created only by the issuing service (MOD-827; R3a of MOD-824: no number twice in a
 * world) — against the compiled bytecode, like {@link ArchitectureRules}, of which it is the record-number
 * chapter (kept in its own class: {@code ArchitectureRules} is at the size ceiling of coding.md §10). Same
 * import as there: every {@code dev.alaindustrial} class of {@code common/src/main} and
 * {@code common/src/gametest}.
 *
 * <p>The condition is {@link VersionedApiRules#callAFacadeOnlyMember}, "a call or a method reference of a
 * listed member outside the one class allowed to make it", renamed for this chapter; its {@code facade} is
 * that class, which the failure line names. The condition's
 * ability to fail is proven on a fixture by {@code ArchitectureRulesNegativeControl}, and
 * {@code everyRecordNumberSourceIsCalledByItsService} there proves on the production classes that each listed
 * member is really called by its service, so a misspelt name cannot leave a rule that matches nothing.
 *
 * <p><b>Naming.</b> No {@code Test} suffix, like {@link ArchitectureRules}: nothing here to mutate.
 */
@AnalyzeClasses(packages = "dev.alaindustrial", importOptions = ImportOption.DoNotIncludeTests.class)
public class RecordNumberRules {

	private static final String RECORD = "dev.alaindustrial.settlement.record.";

	/** The only class that walks the sequence and mints numbers: the state behind the world's ledger. */
	static final String ISSUING_SERVICE = RECORD + "RecordNumberBook";

	/** The members that make a new number: the seed-keyed walk and the minting factory. */
	static final List<FacadeOnlyMember> ISSUING_MEMBERS = List.of(
			new FacadeOnlyMember(Set.of(RECORD + "RecordNumberSequence"), "indexAt", null, ISSUING_SERVICE),
			new FacadeOnlyMember(Set.of(RECORD + "RecordNumber"), "ofIndex", null, ISSUING_SERVICE));

	/** The player's own record — the one number not issued by the ledger, because it is derived from the profile. */
	static final String PLAYER_RECORD_BRIDGE = "dev.alaindustrial.core.guide.ArchiveRecordSync";

	static final List<FacadeOnlyMember> PLAYER_RECORD_MEMBERS = List.of(
			new FacadeOnlyMember(Set.of("dev.alaindustrial.core.guide.ArchiveRecord"), "of", null,
					PLAYER_RECORD_BRIDGE));

	/** Gametests check the player's record against its definition, so they may call it directly. */
	private static final String GAMETESTS = "dev.alaindustrial.gametest..";

	/**
	 * A new record number comes only from {@code RecordNumberLedger.issue()}: the walk of the world's sequence
	 * and the minting factory {@code RecordNumber.ofIndex} are called only by {@code RecordNumberBook}, which
	 * checks a candidate against every number already live, closed, held by a player or shared with a lab.
	 * A second caller would hand out numbers the ledger does not know about — the duplicates R3a forbids.
	 * A {@code noClasses().should(...)} rule: the condition reports {@code satisfied}.
	 */
	@ArchTest
	static final ArchRule recordNumbersAreMintedOnlyByTheIssuingService = noClasses()
			.that().doNotHaveFullyQualifiedName(ISSUING_SERVICE)
			.should(callAFacadeOnlyMember(ISSUING_MEMBERS).as("mint a record number"))
			.because("a record number is unique in its world only if the ledger hands it out (MOD-827, R3a): "
					+ "call RecordNumberLedger.issue(), never the sequence or RecordNumber.ofIndex");

	/**
	 * A record derived from the seed and a profile id is the player's, and only the player's: a shift worker's
	 * number derived the same way — from the entity's UUID, say — would look right and collide with the
	 * ledger's numbers. {@code ArchiveRecord.of} is called only by {@code ArchiveRecordSync} (and by the
	 * gametests that pin it); everything else asks the bridge or the ledger.
	 */
	@ArchTest
	static final ArchRule playerRecordIsDerivedOnlyForThePlayer = noClasses()
			.that().doNotHaveFullyQualifiedName(PLAYER_RECORD_BRIDGE)
			.and().resideOutsideOfPackage(GAMETESTS)
			.should(callAFacadeOnlyMember(PLAYER_RECORD_MEMBERS).as("derive a record from a profile id"))
			.because("a worker's number derived like a player's record bypasses the ledger (MOD-827): the player's "
					+ "record comes from ArchiveRecordSync.recordFor, a worker's number from "
					+ "RecordNumberLedger.issue()");
}
