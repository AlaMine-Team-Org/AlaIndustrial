package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.ReactorLog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * The reactor controller's save, characterised before the controller is cut into components (MOD-713, batch 0).
 *
 * <p><b>Round trip.</b> On three rigs — a running room, a bare pile, a room with its accident countdown running —
 * the controller is saved, a fresh block entity is loaded from that tag and saved again: the two tags must be equal,
 * and the saved keys with their tag types must match {@link ReactorControllerSaveKeys}. Equality alone would pass a
 * key renamed on both sides of the save; the key list is what a renamed key fails.
 *
 * <p><b>The bare countdown across a reload.</b> Batch 0 pinned it as it behaved then, which was wrong: the
 * countdown was saved but the instability that holds it armed and the ticks it had spent under the line were not,
 * so a reloaded pile restarted its scale from zero and called its own accident off. MOD-727 saves both, and the
 * scenario is its regression.
 *
 * <p>The key list is rewritten only by the explicit command in {@link ReactorControllerSaveKeys}' javadoc (ADR-032).
 */
public final class ReactorControllerPersistenceScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ReactorControllerPersistenceScenarios::aRunningRoomSurvivesSaveLoadSave,
								"reactor_persistence_running_room_round_trip")
						.ticks(100),
				RosterEntry.of(ReactorControllerPersistenceScenarios::aBarePileSurvivesSaveLoadSave,
								"reactor_persistence_bare_pile_round_trip")
						.ticks(100),
				RosterEntry.of(ReactorControllerPersistenceScenarios::aRunningCountdownSurvivesSaveLoadSave,
								"reactor_persistence_countdown_round_trip")
						.ticks(100),
				RosterEntry.of(ReactorControllerPersistenceScenarios::aReloadedBarePileKeepsItsCountdown,
								"reactor_persistence_bare_countdown_reload")
						.ticks(100));

		private Roster() {}
	}

	private ReactorControllerPersistenceScenarios() {}

	/** System property naming the FILE the explicit update command writes the key list into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.reactorSaveKeys.writeTo";

	/** Key lists captured in this run, so a capture of one rig keeps the others it has already seen. */
	private static final Map<String, List<String>> CAPTURED = new TreeMap<>();

	/** Five racks packed on the room floor: the same hot core the blast scenarios use. */
	private static final BlockPos[] HOT_CORE = {
		new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(3, 1, 1),
		new BlockPos(1, 1, 2), new BlockPos(2, 1, 2),
	};

	/**
	 * @implements MOD-713 — a running room's controller saves, loads and saves to the same tag, with the reviewed
	 *     keys.
	 */
	public static void aRunningRoomSurvivesSaveLoadSave(GameTestHelper helper) {
		ReactorRig.buildRoom(helper);
		ReactorControllerBlockEntity brain = ReactorRig.controller(helper);
		FuelRodAssemblyBlockEntity column = ReactorRig.placeColumnAt(helper, new BlockPos(2, 1, 2));
		ReactorRig.fuel(column);
		column.setTank(true, column.waterTank.capacity);
		helper.setBlock(ReactorRig.CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		ReactorRig.driveUnderLoad(helper, brain, 120);
		if (brain.getStatus() != ReactorRoomStatus.FORMED || brain.getLastOutput() <= 0) {
			helper.fail("the room did not seal and run: " + brain.getStatus() + ", output " + brain.getLastOutput());
			return;
		}
		roundTrip(helper, "running_room", brain, ReactorRig.CONTROLLER);
	}

	/**
	 * @implements MOD-713 — a bare pile's controller saves, loads and saves to the same tag, with the reviewed keys.
	 */
	public static void aBarePileSurvivesSaveLoadSave(GameTestHelper helper) {
		try (ConfigOverrides o = ReactorRig.barePileOverrides()) {
			ReactorControllerBlockEntity brain = ReactorRig.buildBarePile(helper);
			ReactorRig.driveBareUnderLoad(helper, brain, 100);
			if (!brain.isBare() || brain.getBlastCountdown() != 0) {
				helper.fail("the bare pile is not running bare and unarmed after 100 ticks: bare=" + brain.isBare()
						+ ", countdown " + brain.getBlastCountdown());
				return;
			}
			roundTrip(helper, "bare_pile", brain, ReactorRig.BARE_CONTROLLER);
		}
	}

	/**
	 * @implements MOD-713 — a room whose accident countdown is running saves, loads and saves to the same tag, with
	 *     the reviewed keys.
	 */
	public static void aRunningCountdownSurvivesSaveLoadSave(GameTestHelper helper) {
		ReactorRig.buildRoom(helper);
		ReactorControllerBlockEntity brain = ReactorRig.controller(helper);
		for (BlockPos at : HOT_CORE) {
			ReactorRig.fuel(ReactorRig.placeColumnAt(helper, at));
		}
		helper.setBlock(ReactorRig.CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		ReactorRig.driveUnderLoad(helper, brain, 200);
		if (brain.getBlastCountdown() <= 0) {
			helper.fail("the hot core never armed its countdown, so a running countdown is not under test");
			return;
		}
		roundTrip(helper, "countdown", brain, ReactorRig.CONTROLLER);
		// Left safe: an armed countdown in a shared world is how a test grows a blast radius nobody asked for.
		helper.setBlock(ReactorRig.CONTROLLER.west(), Blocks.AIR.defaultBlockState());
	}

	/**
	 * A bare pile reloaded while its countdown runs keeps the countdown: the reloaded controller comes back at the
	 * instability it was saved with, stays at the top of its scale and goes on counting down; and a scram that was
	 * part of the way through the release window when the chunk unloaded finishes that window after the reload
	 * instead of starting it again (MOD-727, the regression of the defect batch 0 of MOD-713 pinned).
	 *
	 * @implements MOD-727 — a reload keeps a bare pile's instability and the release counter of its countdown.
	 */
	public static void aReloadedBarePileKeepsItsCountdown(GameTestHelper helper) {
		try (ConfigOverrides o = ReactorRig.barePileOverrides()) {
			ReactorControllerBlockEntity brain = ReactorRig.buildBarePile(helper);
			int armedAt = 0;
			for (int tick = 1; tick <= ReactorGoldenTraceScenarios.TRACE_TICKS && armedAt == 0; tick++) {
				ReactorRig.driveBareUnderLoad(helper, brain, 1);
				if (brain.getBlastCountdown() > 0) {
					armedAt = tick;
				}
			}
			if (armedAt == 0) {
				helper.fail("the bare pile never armed its countdown in " + ReactorGoldenTraceScenarios.TRACE_TICKS
						+ " ticks, so its reload is not under test");
				return;
			}
			ReactorRig.driveBareUnderLoad(helper, brain, 20);
			if (brain.getInstability() <= 0 || brain.getBlastCountdown() <= 0) {
				helper.fail("the armed pile is not holding its scale: instability " + brain.getInstability()
						+ ", countdown " + brain.getBlastCountdown());
				return;
			}
			ReactorControllerBlockEntity reloaded = reload(helper, brain, ReactorRig.BARE_CONTROLLER);
			if (reloaded.getBlastCountdown() != brain.getBlastCountdown()
					|| reloaded.getInstability() != brain.getInstability()) {
				helper.fail("the reload changed the accident: countdown " + brain.getBlastCountdown() + " became "
						+ reloaded.getBlastCountdown() + ", instability " + brain.getInstability() + " became "
						+ reloaded.getInstability());
				return;
			}
			int logBefore = reloaded.logEntries().size();
			int window = dev.alaindustrial.core.reactor.ReactorConfig.reactorBlastReleaseTicks + 20;
			ReactorRig.driveBareUnderLoad(helper, reloaded, window);
			if (reloaded.getBlastCountdown() != brain.getBlastCountdown() - window
					|| countdownLines(reloaded, logBefore).contains(ReactorLog.Kind.COUNTDOWN_CANCELLED)) {
				helper.fail(window + " ticks after the reload the countdown reads " + reloaded.getBlastCountdown()
						+ " (expected " + (brain.getBlastCountdown() - window) + ") and the log gained "
						+ countdownLines(reloaded, logBefore) + " — a reload must not call the accident off");
				return;
			}
			// The scram, interrupted by a reload: sixty ticks under the line, then the chunk unloads, then the rest
			// of the release window. A reload that forgot those sixty ticks would need the whole window again.
			int release = dev.alaindustrial.core.reactor.ReactorConfig.reactorBlastReleaseTicks;
			int before = release * 3 / 5;
			helper.setBlock(ReactorRig.BARE_SIGNAL, Blocks.AIR.defaultBlockState());
			ReactorRig.driveBareUnderLoad(helper, reloaded, before);
			if (reloaded.getBlastCountdown() <= 0) {
				helper.fail("the countdown was called off after " + before + " of the " + release
						+ " release ticks, before the reload it is meant to survive");
				return;
			}
			ReactorControllerBlockEntity again = reload(helper, reloaded, ReactorRig.BARE_CONTROLLER);
			int logAgain = again.logEntries().size();
			ReactorRig.driveBareUnderLoad(helper, again, release - before);
			if (again.getBlastCountdown() != 0
					|| !countdownLines(again, logAgain).contains(ReactorLog.Kind.COUNTDOWN_CANCELLED)) {
				helper.fail("a scram held for the whole release window across a reload left the countdown at "
						+ again.getBlastCountdown() + " and logged " + countdownLines(again, logAgain)
						+ " — the reload forgot the ticks already spent under the line");
				return;
			}
			helper.succeed();
		}
	}

	/** The lines a controller's log gained since it held {@code from} entries. */
	private static List<ReactorLog.Kind> countdownLines(ReactorControllerBlockEntity brain, int from) {
		return brain.logEntries().subList(from, brain.logEntries().size()).stream().map(ReactorLog.Entry::kind)
				.toList();
	}

	/** Saves, loads a fresh block entity from the tag, saves again; compares the tags and the key list. */
	private static void roundTrip(GameTestHelper helper, String rig, ReactorControllerBlockEntity brain, BlockPos at) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		CompoundTag first = brain.saveCustomOnly(registries);
		ReactorControllerBlockEntity reloaded = load(helper, first, at);
		CompoundTag second = reloaded.saveCustomOnly(registries);
		if (!first.equals(second)) {
			helper.fail("the " + rig + " controller did not save the tag it loaded:\n  saved:   " + first
					+ "\n  re-saved: " + second);
			return;
		}
		compareKeys(helper, rig, keys(first));
	}

	private static ReactorControllerBlockEntity reload(GameTestHelper helper, ReactorControllerBlockEntity brain,
			BlockPos at) {
		return load(helper, brain.saveCustomOnly(helper.getLevel().registryAccess()), at);
	}

	/** A fresh controller at the rig's controller cell, loaded from {@code tag} and attached to the level. */
	private static ReactorControllerBlockEntity load(GameTestHelper helper, CompoundTag tag, BlockPos at) {
		BlockPos absolute = helper.absolutePos(at);
		ReactorControllerBlockEntity fresh =
				new ReactorControllerBlockEntity(absolute, helper.getLevel().getBlockState(absolute));
		fresh.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
				helper.getLevel().registryAccess(), tag));
		fresh.setLevel(helper.getLevel());
		return fresh;
	}

	/** Every top-level key with its tag type, sorted. */
	private static List<String> keys(CompoundTag tag) {
		List<String> keys = new ArrayList<>();
		for (String key : tag.keySet()) {
			keys.add(key + ":" + tag.get(key).getType().getName());
		}
		keys.sort(null);
		return keys;
	}

	private static void compareKeys(GameTestHelper helper, String rig, List<String> actual) {
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			CAPTURED.put(rig, actual);
			Map<String, List<String>> all = new TreeMap<>(ReactorControllerSaveKeys.BY_RIG);
			all.putAll(CAPTURED);
			try {
				Files.writeString(Path.of(writeTo), render(all), StandardCharsets.UTF_8);
			} catch (IOException e) {
				helper.fail("could not write the controller's save keys to " + writeTo + ": " + e);
				return;
			}
			helper.fail("the " + rig + " save keys were rewritten to " + writeTo + " — review the diff, then run"
					+ " again without -D" + WRITE_TO_PROPERTY);
			return;
		}
		List<String> expected = ReactorControllerSaveKeys.BY_RIG.getOrDefault(rig, List.of());
		if (actual.equals(expected)) {
			helper.succeed();
			return;
		}
		List<String> gone = new ArrayList<>(expected);
		gone.removeAll(actual);
		List<String> added = new ArrayList<>(actual);
		added.removeAll(expected);
		helper.fail("the " + rig + " controller's save keys no longer match ReactorControllerSaveKeys: gone " + gone
				+ ", new " + added + ". A key is part of every player's save: keep the old one loading, then update"
				+ " the list with the explicit command in ReactorControllerSaveKeys' javadoc.");
	}

	/** The whole source of {@link ReactorControllerSaveKeys}, LF line endings. */
	private static String render(Map<String, List<String>> byRig) {
		StringBuilder out = new StringBuilder("""
				package dev.alaindustrial.gametest;

				import java.util.List;
				import java.util.Map;

				/**
				 * Reviewed top-level keys of the reactor controller's save, with their tag types, per rig
				 * (MOD-713) — the reference {@link ReactorControllerPersistenceScenarios} compares against.
				 * Written by that scenario, and only on the explicit command below; never by
				 * {@code regen.py}, a hook or a merge driver (ADR-032). The same file on both Minecraft
				 * lines: a difference between them is a finding.
				 *
				 * <p>Update command (absolute path; Fabric lane):
				 * <pre>
				 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:reactor_persistence_*
				 *   -D%s=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest/ReactorControllerSaveKeys.java"
				 *   ./gradlew :fabric:runGameTest
				 * </pre>
				 * The run fails on purpose after writing; review the diff, then commit it together with
				 * the change that altered the save.
				 */
				final class ReactorControllerSaveKeys {

					private ReactorControllerSaveKeys() {}

					static final Map<String, List<String>> BY_RIG = Map.of(""".formatted(WRITE_TO_PROPERTY));
		String nl = "\n";
		int rig = 0;
		for (Map.Entry<String, List<String>> entry : byRig.entrySet()) {
			out.append(rig++ == 0 ? "" : ",").append(nl)
					.append("\t\t\t\"").append(entry.getKey()).append("\", List.of(");
			List<String> keys = entry.getValue();
			for (int i = 0; i < keys.size(); i++) {
				out.append(nl).append("\t\t\t\t\t\"").append(keys.get(i)).append('"')
						.append(i + 1 < keys.size() ? "," : "");
			}
			out.append(')');
		}
		return out.append(");").append(nl).append('}').append(nl).toString();
	}
}
