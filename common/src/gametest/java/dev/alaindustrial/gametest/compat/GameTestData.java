package dev.alaindustrial.gametest.compat;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/**
 * Version facade (ADR-036) for building a gametest's {@link TestData}: its full constructor takes 12
 * arguments on Minecraft 26.3 (with a {@code ResourceKey<Level> dimension} after the environment) and 11
 * on 26.2 (no dimension) — verified by javap of both lines' {@code minecraft-merged.jar}. The signature
 * of {@link #of} is the same on both lines; the body is this line's.
 *
 * <p>It lives in {@code dev.alaindustrial.gametest.compat}, not {@code dev.alaindustrial.compat}: on
 * NeoForge the gametest source set is its own JPMS module, and a package cannot be exported by two modules
 * (ADR-036, "Boundaries", 2026-10-02).
 *
 * <p>Every roster test runs in the overworld, once, with no setup ticks and no manual-only flag — the
 * values both lanes used for hand-wired tests ({@code @GameTest} defaults on Fabric, the
 * {@code NeoForgeGameTests.registerTest} literals on NeoForge).
 */
public final class GameTestData {

	private GameTestData() {}

	/** The test data of one roster test on one lane. */
	public static TestData<Holder<TestEnvironmentDefinition<?>>> of(Holder<TestEnvironmentDefinition<?>> environment,
			Identifier structure, int maxTicks, boolean required, Rotation rotation, boolean skyAccess, int padding) {
		return new TestData<>(
				environment,
				structure,
				maxTicks,
				0,          // setupTicks
				required,
				rotation,
				false,      // manualOnly
				1,          // maxAttempts — a retry would only mask a real defect
				1,          // requiredSuccesses
				skyAccess,
				padding);
	}
}
