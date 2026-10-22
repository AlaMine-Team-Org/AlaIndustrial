package dev.alaindustrial.settlement.drone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.drone.StartReadiness.Blocker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 for when the dispatcher's «Start» may be pressed (MOD-858, owner's round 2): the plan of this hall and a drone
 * are hard requirements, materials only a warning; the hall's own state comes alone; the screen and the server ask
 * the same method.
 *
 * @implements MOD-858-START
 */
class StartReadinessTest {

	private static final String HALL = "alaindustrial:ala_town/plains/intact/town_hall";
	private static final String OTHER = "alaindustrial:ala_town/desert/intact/town_hall";

	private static DispatcherView.Checklist ready() {
		return new DispatcherView.Checklist(true, false, true, false, true, HALL);
	}

	@Test
	void theHallsPlanAndADroneLetTheOwnerStart() {
		assertTrue(StartReadiness.blockers(true, ready(), HALL, 1).isEmpty());
	}

	@Test
	void everyMissingRequirementIsListedInOrder() {
		assertEquals(List.of(Blocker.NOT_OWNER, Blocker.NO_PLAN, Blocker.NO_DRONE),
				StartReadiness.blockers(false, ready(), null, 0));
		assertEquals(List.of(Blocker.NO_PLAN), StartReadiness.blockers(true, ready(), null, 2));
		assertEquals(List.of(Blocker.NO_DRONE), StartReadiness.blockers(true, ready(), HALL, 0));
	}

	@Test
	void aPlanOfAnotherHallDoesNotCount() {
		assertEquals(List.of(Blocker.NO_PLAN), StartReadiness.blockers(true, ready(), OTHER, 1));
		DispatcherView.Checklist unknownHall = new DispatcherView.Checklist(true, false, true, false, true, "");
		assertEquals(List.of(Blocker.NO_PLAN), StartReadiness.blockers(true, unknownHall, HALL, 1));
	}

	@Test
	void theHallsOwnStateComesAlone() {
		assertEquals(List.of(Blocker.NO_TOWN), StartReadiness.blockers(true, DispatcherView.Checklist.NONE, null, 0));
		assertEquals(List.of(Blocker.RESTORED), StartReadiness.blockers(false,
				new DispatcherView.Checklist(true, true, false, false, false, HALL), null, 0));
		assertEquals(List.of(Blocker.STARTED), StartReadiness.blockers(true,
				new DispatcherView.Checklist(true, false, true, true, false, HALL), HALL, 1));
	}

	@Test
	void aHallWithNoIntactTemplateCannotStart() {
		assertEquals(List.of(Blocker.NO_HALL), StartReadiness.blockers(true,
				new DispatcherView.Checklist(true, false, false, false, false, HALL), HALL, 1));
	}

	@Test
	void shortMaterialsWarnButDoNotBlock() {
		assertTrue(StartReadiness.materialsShort(ready(), false));
		assertTrue(StartReadiness.blockers(true, ready(), HALL, 1).isEmpty());
		assertFalse(StartReadiness.materialsShort(ready(), true));
		assertFalse(StartReadiness.materialsShort(
				new DispatcherView.Checklist(true, true, true, false, false, HALL), false));
	}

	@Test
	void everyLineHasItsKeyInEnglishAndRussian() throws IOException {
		for (String locale : List.of("en_us", "ru_ru")) {
			Path file = Path.of("src/main/resources/assets/alaindustrial/lang/" + locale + ".json");
			String lang = Files.readString(file, StandardCharsets.UTF_8);
			for (String key : StartReadiness.keys()) {
				assertTrue(lang.contains("\"" + key + "\":"), locale + " has no " + key);
			}
		}
	}
}
