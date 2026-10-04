package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.core.structure.ReactorLog;
import dev.alaindustrial.registry.ModContent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Golden traces of the reactor controller (MOD-713, batch 0): what the controller reports, tick by tick, on three
 * reference rigs — a dry sealed room, a sealed room with water and a plain pipe, and a bare pile of sixteen rods.
 *
 * <p><b>Why a trace and not more assertions.</b> The controller is about to be cut into components, and the order
 * of the steps inside one reactor tick is what such a cut breaks without any single scenario noticing: each
 * scenario asks one question at the end of its run, while a step moved by one place shifts the arming tick, the
 * meltdown tick or the first log line by a tick and leaves every end state as it was. The trace records the readings
 * after every tick, so a moved step is a changed line.
 *
 * <p><b>What a line holds.</b> {@code t=} the tick, {@code h=} heat, {@code i=} instability, {@code o=} the last
 * output, the idle reason, {@code armed}/{@code safe} for the accident countdown, {@code m=} the blocks marked for
 * melting, {@code l=} the number of log lines, {@code p=} the steam puffs sent, the room status and {@code bare}
 * while the controller runs bare. A line is written for the first tick and for every tick on which any of these
 * changed, then one closing line with how far the countdown has run and the log itself, without its game-time
 * stamps.
 *
 * <p><b>Determinism.</b> All six hundred ticks run inside one call, so the world does not tick in between and the
 * game time stands still. Nothing traced may depend on the level's random source, which other tests share: the
 * countdown's rolled length is traced as the ticks it has run, never as what is left; the rooms have nothing a
 * contents meltdown could pick at random (the wet room's pipe is found by a fixed walk); the bare pile melts within
 * a radius of zero, so its scenery hazard never draws a victim. Knobs are changed only through
 * {@link ConfigOverrides#sync()}, inside the one call.
 *
 * <p>The reference is three files, {@link ReactorGoldenTraceDryRoom}, {@link ReactorGoldenTraceWetRoom} and
 * {@link ReactorGoldenTraceBarePile}, rewritten only by the explicit command in their javadoc — never by
 * {@code regen.py}, a hook or a merge driver (ADR-032). A behaviour-preserving change leaves them byte-identical.
 */
public final class ReactorGoldenTraceScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ReactorGoldenTraceScenarios::dryRoomFollowsTheGoldenTrace,
								"reactor_golden_trace_dry_room")
						.ticks(100),
				RosterEntry.of(ReactorGoldenTraceScenarios::wetRoomFollowsTheGoldenTrace,
								"reactor_golden_trace_wet_room")
						.ticks(100),
				RosterEntry.of(ReactorGoldenTraceScenarios::barePileFollowsTheGoldenTrace,
								"reactor_golden_trace_bare_pile")
						.ticks(100));

		private Roster() {}
	}

	private ReactorGoldenTraceScenarios() {}

	/** System property naming the DIRECTORY the explicit update command writes the three reference files into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.reactorGoldenTrace.writeTo";

	/** Ticks every rig is driven for. */
	static final int TRACE_TICKS = 600;

	/**
	 * A dry sealed room: one rack of four rods, a redstone block, no water — the room heats to the top, melts down
	 * with nothing in it to melt, and arms the countdown.
	 *
	 * @implements MOD-713 — the dry room's per-tick trace matches the reviewed reference.
	 */
	public static void dryRoomFollowsTheGoldenTrace(GameTestHelper helper) {
		ReactorRig.buildRoom(helper);
		ReactorControllerBlockEntity brain = ReactorRig.controller(helper);
		ReactorRig.fuel(ReactorRig.placeColumnAt(helper, new BlockPos(2, 1, 2)));
		helper.setBlock(ReactorRig.CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		List<String> trace = traceRoom(helper, brain);
		compare(helper, "dry_room", "ReactorGoldenTraceDryRoom", trace, ReactorGoldenTraceDryRoom.LINES);
	}

	/**
	 * A sealed room with water: a two-high stack of eight rods with its lower column filled once, and a plain
	 * fluid pipe in the far corner — the loop boils, settles the stack and puffs, and the working room melts the
	 * pipe.
	 *
	 * @implements MOD-713 — the wet room's per-tick trace matches the reviewed reference.
	 */
	public static void wetRoomFollowsTheGoldenTrace(GameTestHelper helper) {
		ReactorRig.buildRoom(helper);
		ReactorControllerBlockEntity brain = ReactorRig.controller(helper);
		FuelRodAssemblyBlockEntity bottom = ReactorRig.placeColumnAt(helper, new BlockPos(2, 1, 2));
		FuelRodAssemblyBlockEntity top = ReactorRig.placeColumnAt(helper, new BlockPos(2, 2, 2));
		ReactorRig.fuel(bottom);
		ReactorRig.fuel(top);
		bottom.setTank(true, bottom.waterTank.capacity);
		helper.setBlock(new BlockPos(3, 3, 3), ModContent.FLUID_PIPE.get().defaultBlockState());
		helper.setBlock(ReactorRig.CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		List<String> trace = traceRoom(helper, brain);
		compare(helper, "wet_room", "ReactorGoldenTraceWetRoom", trace, ReactorGoldenTraceWetRoom.LINES);
	}

	/**
	 * A bare pile of sixteen rods: no room, the pile's instability climbs past the top and arms the countdown.
	 *
	 * @implements MOD-713 — the bare pile's per-tick trace matches the reviewed reference.
	 */
	public static void barePileFollowsTheGoldenTrace(GameTestHelper helper) {
		try (ConfigOverrides o = ReactorRig.barePileOverrides()) {
			ReactorControllerBlockEntity brain = ReactorRig.buildBarePile(helper);
			List<String> trace = new ArrayList<>();
			String last = null;
			for (int tick = 1; tick <= TRACE_TICKS; tick++) {
				ReactorRig.driveBareUnderLoad(helper, brain, 1);
				last = record(trace, tick, brain, last);
			}
			close(trace, brain);
			compare(helper, "bare_pile", "ReactorGoldenTraceBarePile", trace, ReactorGoldenTraceBarePile.LINES);
		}
	}

	private static List<String> traceRoom(GameTestHelper helper, ReactorControllerBlockEntity brain) {
		List<String> trace = new ArrayList<>();
		String last = null;
		for (int tick = 1; tick <= TRACE_TICKS; tick++) {
			ReactorRig.driveUnderLoad(helper, brain, 1);
			last = record(trace, tick, brain, last);
		}
		close(trace, brain);
		return trace;
	}

	/** Appends this tick's line if anything changed since {@code last}; returns this tick's reading. */
	private static String record(List<String> trace, int tick, ReactorControllerBlockEntity brain, String last) {
		String now = reading(brain);
		if (!now.equals(last)) {
			trace.add("t=" + tick + " " + now);
		}
		return now;
	}

	/** One tick's reading; the legend is in the class javadoc. Short keys keep a line inside the width limit. */
	private static String reading(ReactorControllerBlockEntity brain) {
		return "h=" + brain.getHeat()
				+ " i=" + brain.getInstability()
				+ " o=" + brain.getLastOutput()
				+ " " + brain.getIdleReason()
				+ " " + (brain.getBlastCountdown() > 0 ? "armed" : "safe")
				+ " m=" + brain.getMeltsScheduled()
				+ " l=" + brain.logEntries().size()
				+ " p=" + brain.getSteamPuffsSent()
				+ " " + brain.getStatus()
				+ (brain.isBare() ? " bare" : "");
	}

	/** The closing lines: how far the countdown has run (never what is left of it), then the log without stamps. */
	private static void close(List<String> trace, ReactorControllerBlockEntity brain) {
		int run = brain.getBlastCountdown() > 0 ? brain.getBlastCountdownTotal() - brain.getBlastCountdown() : 0;
		trace.add("end countdown-run=" + run);
		for (ReactorLog.Entry entry : brain.logEntries()) {
			trace.add("log #" + entry.seq() + " " + entry.kind() + " " + entry.a() + " " + entry.b() + " " + entry.c()
					+ (entry.actor().isEmpty() ? "" : " by " + entry.actor()));
		}
	}

	private static void compare(GameTestHelper helper, String rig, String referenceClass, List<String> actual,
			List<String> expected) {
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			Path target = Path.of(writeTo).resolve(referenceClass + ".java");
			try {
				Files.writeString(target, render(rig, referenceClass, actual), StandardCharsets.UTF_8);
			} catch (IOException e) {
				helper.fail("could not write the " + rig + " golden trace to " + target + ": " + e);
				return;
			}
			helper.fail("the " + rig + " golden trace was rewritten to " + target + " (" + actual.size()
					+ " lines) — review the diff, then run again without -D" + WRITE_TO_PROPERTY);
			return;
		}
		if (actual.equals(expected)) {
			helper.succeed();
			return;
		}
		int at = 0;
		while (at < actual.size() && at < expected.size() && actual.get(at).equals(expected.get(at))) {
			at++;
		}
		helper.fail("the reactor's " + rig + " trace left " + referenceClass + " at line " + (at + 1) + ".\n"
				+ "  reference: " + (at < expected.size() ? expected.get(at) : "<end>") + "\n"
				+ "  now:       " + (at < actual.size() ? actual.get(at) : "<end>") + "\n"
				+ "A behaviour-preserving change must leave the trace as it was. A deliberate behaviour change"
				+ " rewrites the reference with the explicit command in " + referenceClass + "'s javadoc.");
	}

	/** The whole source of one reference file, LF line endings. */
	private static String render(String rig, String referenceClass, List<String> lines) {
		StringBuilder out = new StringBuilder("""
				package dev.alaindustrial.gametest;

				import java.util.List;

				/**
				 * Reviewed golden trace of the reactor controller on the %s rig (MOD-713) — the
				 * reference {@link ReactorGoldenTraceScenarios} compares against. Written by that scenario,
				 * and only on the explicit command below; never by {@code regen.py}, a hook or a merge driver
				 * (ADR-032). The same file on both Minecraft lines and both loaders: a difference between
				 * them is a finding.
				 *
				 * <p>Update command (absolute directory; Fabric lane):
				 * <pre>
				 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:reactor_golden_trace*
				 *   -D%s=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest"
				 *   ./gradlew :fabric:runGameTest
				 * </pre>
				 * The run fails on purpose after writing; review the diff, then commit it together with
				 * the change that altered the reactor's behaviour.
				 */
				final class %s {

					private %s() {}

					static final List<String> LINES = List.of(
				""".formatted(rig.replace('_', ' '), WRITE_TO_PROPERTY, referenceClass, referenceClass));
		for (int i = 0; i < lines.size(); i++) {
			out.append(INDENT).append('"').append(lines.get(i)).append('"').append(i + 1 < lines.size() ? "," : ");")
					.append(NL);
		}
		if (lines.isEmpty()) {
			out.append(INDENT).append(");").append(NL);
		}
		return out.append('}').append(NL).toString();
	}

	/** Three tabs: where a list element of the rendered reference starts. */
	private static final String INDENT = "\t\t\t";

	/** The line ending a rendered reference uses on every platform (ADR-009). */
	private static final String NL = "\n";
}
