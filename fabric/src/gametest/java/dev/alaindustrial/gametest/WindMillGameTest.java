package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.environment.WindMillOutput;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import team.reborn.energy.api.EnergyStorage;

/**
 * L2 functional suite for the wind mill — the passive height/sky/weather-driven LV generator. Mirrors the
 * structure of {@link WaterMillWheelGameTest} / {@link SolarPanelGameTest}: each method is one case, traced via
 * {@code @implements}, driving {@code serverTick} directly (deterministic, no waiting).
 *
 * <p><b>Height note.</b> A Fabric gametest structure sits near world Y = 0, well below sea level
 * ({@code level.getSeaLevel()} = 63), so the wind mill's height base is 0 in-world. The FUN/weather cases
 * therefore assert the mill's per-tick output equals {@link WindMillOutput#euFor} evaluated against the
 * <em>real</em> world state (absolute Y, sea level, sky, weather) — the same pure function {@code produce()}
 * calls — so the wiring (sky gate, weather read, sampling, buffering) is verified end-to-end regardless of
 * the region's altitude. The full height→base scaling and weather-multiplier arithmetic (0 at sea level,
 * +1/16 blocks, cap 4, rain ×1.5, thunder ×2, cap 8) is covered numerically at L1 in {@code WindMillOutputTest}.
 *
 * <p>Weather is set synchronously the way the solar suite does it — {@code WeatherData} plus the interpolated
 * rain level ({@code isRaining()} reads the latter). Numbers come from {@link Config} (canon), never hard-coded.
 *
 * <p><b>MOD-445/446.</b> All bodies live in {@link WindMillScenarios} (common); these wrappers keep only the
 * {@code @GameTest} wiring and the {@code @implements}/{@code @covers} tracing. The one exception is
 * {@link #tcWindmill001Phy01_backFaceOnlyOutput}, whose body checks the port via the Fabric-only
 * {@code EnergyStorage.SIDED} capability seam and therefore stays here.
 */
public class WindMillGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/**
	 * @implements TC-WINDMILL-001-PHY01 — the mill emits EU only from its BACK face (opposite of FACING);
	 *     the front and the four sides are inert (single-output contract, R-NRG-03). FACING = NORTH by
	 *     default, so SOUTH is the sole OUT face; NORTH/EAST/WEST/UP/DOWN must not extract.
	 * @covers R-NRG-03
	 */
	@GameTest
	public void tcWindmill001Phy01_backFaceOnlyOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.WIND_MILL.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.NORTH));
		// Only the back face (SOUTH) should support extraction.
		EnergyStorage back = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), Direction.SOUTH);
		if (back == null || !back.supportsExtraction()) {
			helper.fail("wind mill BACK (south) face must emit EU");
		}
		// The front and all four sides must be inert.
		for (Direction d : new Direction[]{
				Direction.NORTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}) {
			EnergyStorage p = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), d);
			if (p != null && p.supportsExtraction()) {
				helper.fail("wind mill face " + d + " must NOT emit EU (only the back face does)");
			}
		}
		helper.succeed();
	}

	// ── MOD-189: rotor wear — the rotor is a durability component that wears out and breaks ───────────

	// ── MOD-445: loader-neutral bodies the NeoForge lane already ran; wired here so both lanes run the same set ──

}
