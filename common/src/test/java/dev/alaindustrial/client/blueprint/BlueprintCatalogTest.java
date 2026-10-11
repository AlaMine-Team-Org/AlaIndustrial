package dev.alaindustrial.client.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 checks of the blueprint catalogue (MOD-797): every block a sheet draws exists as a blockstate, every
 * subject item has a model, every text the sheet prints has an English key, and the camera formula the
 * callouts rely on keeps "up" up.
 */
class BlueprintCatalogTest {

	private static final String ASSETS = "assets/alaindustrial/";

	@Test
	void everyBlockOnASheetHasABlockstate() {
		List<String> missing = new ArrayList<>();
		for (BlueprintDef def : BlueprintCatalog.ALL) {
			for (Step step : def.steps()) {
				for (Placement p : step.blocks()) {
					// Vanilla blocks are resolved by the game; only the mod's own ids are checked against assets.
					if (p.blockId().startsWith("alaindustrial:")
							&& resource(ASSETS + "blockstates/" + path(p.blockId()) + ".json") == null) {
						missing.add(def.id() + ": " + p.blockId());
					}
				}
			}
		}
		assertTrue(missing.isEmpty(), "blocks with no blockstate file: " + missing);
	}

	@Test
	void everySubjectHasAnItemModel() {
		List<String> missing = new ArrayList<>();
		for (BlueprintDef def : BlueprintCatalog.ALL) {
			for (String subject : def.subjects()) {
				if (resource(ASSETS + "items/" + path(subject) + ".json") == null) {
					missing.add(def.id() + ": " + subject);
				}
			}
		}
		assertTrue(missing.isEmpty(), "subjects with no item model: " + missing);
	}

	@Test
	void everyTextOfASheetHasAnEnglishKey() throws IOException {
		String english = text(ASSETS + "lang/en_us.json");
		List<String> missing = new ArrayList<>();
		for (BlueprintDef def : BlueprintCatalog.ALL) {
			List<String> keys = new ArrayList<>(List.of(def.titleKey()));
			for (int i = 0; i < def.steps().size(); i++) {
				keys.add(def.stepTitleKey(i));
				keys.add(def.stepTextKey(i));
			}
			for (String key : keys) {
				if (!english.contains("\"" + key + "\"")) {
					missing.add(key);
				}
			}
		}
		assertTrue(missing.isEmpty(), "keys missing from en_us.json: " + missing);
	}

	/** House style for blueprint text (owner decision 2026-10-10): no exclamation marks, no long dashes. */
	@Test
	void blueprintTextHasNoExclamationOrLongDash() throws Exception {
		java.net.URL english = resource(ASSETS + "lang/en_us.json");
		java.nio.file.Path dir = java.nio.file.Path.of(english.toURI()).getParent();
		List<String> offending = new ArrayList<>();
		try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir)) {
			for (java.nio.file.Path file : files.toList()) {
				for (String line : java.nio.file.Files.readAllLines(file, StandardCharsets.UTF_8)) {
					if (line.contains("\"blueprint.alaindustrial.") && line.matches(".*[—–!！¡].*")) {
						offending.add(file.getFileName() + ": " + line.trim());
					}
				}
			}
		}
		assertTrue(offending.isEmpty(), "blueprint text with '!' or a long dash: " + offending);
	}

	@Test
	void noItemLeadsToTwoBlueprints() {
		Set<String> seen = new HashSet<>();
		for (BlueprintDef def : BlueprintCatalog.ALL) {
			for (String subject : def.subjects()) {
				assertTrue(seen.add(subject), subject + " is the subject of more than one blueprint");
			}
		}
	}

	@Test
	void aBlockIsNewOnlyWhenItFirstAppears() {
		BlueprintDef def = EnergyBlueprints.ENERGY_BASICS;
		assertTrue(def.isNewInStep(0, 0), "the first step's generator is new");
		assertFalse(def.isNewInStep(1, 0), "the generator lighting up is not a new block");
		assertTrue(def.isNewInStep(1, 1), "the cable appears in the second step");
		assertTrue(def.isNewInStep(2, 2), "the furnace appears in the third step");
		assertFalse(def.isNewInStep(3, 1), "the furnace stays in the fourth step");
	}

	@Test
	void progressRemembersOpenedSheets() {
		assertFalse(BlueprintProgress.isViewed("test_sheet_never_opened"));
		BlueprintProgress.markViewed("test_sheet_opened");
		assertTrue(BlueprintProgress.isViewed("test_sheet_opened"));
	}

	@Test
	void forSubjectFindsTheSheet() {
		assertEquals("energy_basics", BlueprintCatalog.forSubject("alaindustrial:copper_cable").orElseThrow().id());
		assertTrue(BlueprintCatalog.forSubject("minecraft:stone").isEmpty());
	}

	@Test
	void cameraKeepsUpUpAndTheCentreStill() {
		BlueprintCamera camera = BlueprintCamera.DEFAULT;
		double[] centre = camera.project(0, 0, 0);
		assertEquals(0, centre[0], 1e-9);
		assertEquals(0, centre[1], 1e-9);
		assertTrue(camera.project(0, 1, 0)[1] < 0, "a point above the centre is drawn higher on screen");
		assertEquals(BlueprintCamera.MAX_PITCH, camera.withPitch(500).pitchDegrees());
		assertEquals(BlueprintCamera.MIN_PITCH, camera.withPitch(-500).pitchDegrees());
	}

	@Test
	void boundsCoverEveryStep() {
		BlueprintBounds bounds = BlueprintBounds.of(EnergyBlueprints.ENERGY_BASICS);
		assertEquals(1.5, bounds.centerX(), 1e-9);
		assertEquals(0.5, bounds.centerY(), 1e-9);
		assertTrue(bounds.radius() > 1.5);
	}

	private static String path(String id) {
		return id.substring(id.indexOf(':') + 1);
	}

	private static java.net.URL resource(String name) {
		return BlueprintCatalogTest.class.getClassLoader().getResource(name);
	}

	private static String text(String name) throws IOException {
		try (InputStream in = BlueprintCatalogTest.class.getClassLoader().getResourceAsStream(name)) {
			assertNotNull(in, name);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
