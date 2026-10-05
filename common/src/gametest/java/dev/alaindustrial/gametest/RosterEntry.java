package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * One world gametest, declared once next to its body and replayed by both loaders (MOD-717, ADR-038).
 *
 * <p>A scenario class declares its tests in a nested holder, {@code XScenarios.Roster.ENTRIES}, and
 * {@link ScenarioRoster#PARTS} lists that holder. The holder is a nested class on purpose: reading it
 * does not initialise {@code XScenarios} itself (a method reference to a static method initialises its
 * class only when invoked), so the loaders can read the roster while the mod's content is still being
 * registered.
 *
 * <p>Each loader turns an entry into a vanilla {@code FunctionGameTestInstance}: the body goes into
 * {@code TEST_FUNCTION} under {@link #testId(Lane)}, the instance into {@code TEST_INSTANCE} under the
 * same key ({@code ScenarioRosterFabric}, {@code ScenarioRosterNeoForge}).
 *
 * <p><b>Ids.</b> {@link #name()} is the NeoForge id path ({@code alaindustrial:<name>}). A scenario that
 * had a hand-written Fabric wrapper keeps that wrapper's id through {@link #fabricId(String, String)};
 * without it the Fabric lane runs the scenario under the same {@code alaindustrial:<name>} id.
 *
 * <p><b>Parameters per lane.</b> Where the two lanes ran the scenario with different parameters
 * before it moved here, the entry keeps both values ({@link #ticks(int, int)}, {@link #sky(boolean, boolean)},
 * {@link #structure(Identifier, Identifier)}); aligning them is a separate, deliberate change
 * (MOD-717 batch 6). A {@code null} structure or environment means "the lane's default", which only the
 * lane knows (Fabric: {@code fabric-gametest-api-v1:empty} and {@code minecraft:default}; NeoForge:
 * {@code alaindustrial:gametest_rig} and {@code alaindustrial:empty_env}).
 */
public record RosterEntry(
		String name,
		Consumer<GameTestHelper> body,
		@Nullable FabricId fabricId,
		LaneParams fabric,
		LaneParams neoForge,
		@Nullable Identifier environment,
		Rotation rotation,
		boolean required) {

	/** The two gametest lanes. */
	public enum Lane { FABRIC, NEOFORGE }

	/** Parameters that may differ between the lanes; {@code structure == null} is the lane default. */
	public record LaneParams(int maxTicks, boolean skyAccess, @Nullable Identifier structure) {}

	/** The class and method of the former Fabric {@code @GameTest} wrapper whose id the entry keeps. */
	public record FabricId(String wrapperClass, String wrapperMethod) {}

	/** Tick budget when an entry names none — the {@code @GameTest} default ({@code maxTicks() default 20}). */
	public static final int DEFAULT_MAX_TICKS = 20;

	/** Namespace of the former Fabric wrapper ids: the gametest mod id ({@code fabric.mod.json}). */
	public static final String FABRIC_WRAPPER_NAMESPACE = "alaindustrial-gametest";

	public RosterEntry {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(body, "body");
		Objects.requireNonNull(fabric, "fabric");
		Objects.requireNonNull(neoForge, "neoForge");
		Objects.requireNonNull(rotation, "rotation");
	}

	/** A required test named {@code name}, 20 ticks, no sky, lane-default structure and environment. */
	public static RosterEntry of(Consumer<GameTestHelper> body, String name) {
		LaneParams defaults = new LaneParams(DEFAULT_MAX_TICKS, false, null);
		return new RosterEntry(name, body, null, defaults, defaults, null, Rotation.NONE, true);
	}

	/** Keep the id of the former Fabric wrapper {@code wrapperClass.wrapperMethod}. */
	public RosterEntry fabricId(String wrapperClass, String wrapperMethod) {
		return new RosterEntry(name, body, new FabricId(wrapperClass, wrapperMethod), fabric, neoForge,
				environment, rotation, required);
	}

	/** The same tick budget on both lanes. */
	public RosterEntry ticks(int maxTicks) {
		return ticks(maxTicks, maxTicks);
	}

	/** Different tick budgets per lane, as wired by hand before the move (aligned in batch 6). */
	public RosterEntry ticks(int fabricTicks, int neoForgeTicks) {
		return new RosterEntry(name, body, fabricId,
				new LaneParams(fabricTicks, fabric.skyAccess(), fabric.structure()),
				new LaneParams(neoForgeTicks, neoForge.skyAccess(), neoForge.structure()),
				environment, rotation, required);
	}

	/** Sky access on both lanes (no barrier ceiling over the structure). */
	public RosterEntry sky() {
		return sky(true, true);
	}

	/** Sky access per lane, as wired by hand before the move (aligned in batch 6). */
	public RosterEntry sky(boolean fabricSky, boolean neoForgeSky) {
		return new RosterEntry(name, body, fabricId,
				new LaneParams(fabric.maxTicks(), fabricSky, fabric.structure()),
				new LaneParams(neoForge.maxTicks(), neoForgeSky, neoForge.structure()),
				environment, rotation, required);
	}

	/** One structure for both lanes; it must exist in both lanes' data. */
	public RosterEntry structure(Identifier structure) {
		return structure(structure, structure);
	}

	/** A structure per lane ({@code null} = that lane's default). */
	public RosterEntry structure(@Nullable Identifier fabricStructure, @Nullable Identifier neoForgeStructure) {
		return new RosterEntry(name, body, fabricId,
				new LaneParams(fabric.maxTicks(), fabric.skyAccess(), fabricStructure),
				new LaneParams(neoForge.maxTicks(), neoForge.skyAccess(), neoForgeStructure),
				environment, rotation, required);
	}

	/**
	 * Run in its own test environment. The game batches tests by environment, so the scenario never
	 * shares ticks with one outside it. Fabric resolves the id from data
	 * ({@code data/<ns>/test_environment/<path>.json}), NeoForge registers an empty one under the id.
	 */
	public RosterEntry environment(Identifier environment) {
		return new RosterEntry(name, body, fabricId, fabric, neoForge, environment, rotation, required);
	}

	/** Place the structure rotated. */
	public RosterEntry rotation(Rotation rotation) {
		return new RosterEntry(name, body, fabricId, fabric, neoForge, environment, rotation, required);
	}

	/** The lane's parameters. */
	public LaneParams params(Lane lane) {
		return lane == Lane.FABRIC ? fabric : neoForge;
	}

	/** The key of this test in {@code TEST_FUNCTION} and {@code TEST_INSTANCE} on {@code lane}. */
	public Identifier testId(Lane lane) {
		if (lane == Lane.FABRIC && fabricId != null) {
			return Identifier.fromNamespaceAndPath(FABRIC_WRAPPER_NAMESPACE,
					wrapperIdPath(fabricId.wrapperClass(), fabricId.wrapperMethod()));
		}
		return Industrialization.id(name);
	}

	/**
	 * The id path fabric-gametest-api gives an annotated method: {@code camelToSnake(SimpleClassName + "_"
	 * + method)} with {@code ([a-z])([A-Z]) -> $1_$2}, lower-cased in {@link Locale#ROOT}
	 * ({@code TestAnnotationLocator.TestMethod.identifier}, 4.0.32 and 4.0.21, verified by javap).
	 */
	static String wrapperIdPath(String wrapperClass, String wrapperMethod) {
		return (wrapperClass + "_" + wrapperMethod).replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
	}
}
