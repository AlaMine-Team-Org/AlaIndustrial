package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.AbstractGeneratorBlockEntity;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.RadiantSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyPortHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.biome.Biomes;

/**
 * The solar panel scenarios' rig (MOD-717): the panel's cell, the day, night and weather a scenario stages,
 * and the typed lookup of each panel tier — kept beside {@link SolarPanelScenarios} so that file holds the
 * scenarios and their roster. The panels are ticked by {@link AlaGameTestHelper#drive}.
 */
final class SolarPanelRig {

	private SolarPanelRig() {}

	static final BlockPos POS = new BlockPos(1, 2, 1);

	/** Clear daytime, brightness recomputed NOW (no tick wait). Weather reset for isolation. */
	static void setClearDay(GameTestHelper helper) {
		var level = helper.getLevel();
		var server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set day");
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f); // isRaining() reads the interpolated level, not WeatherData
		level.updateSkyBrightness(); // skyDarken now reflects day → isBrightOutside() true synchronously
	}

	/** Clear midnight, brightness recomputed NOW. Mirror of {@link #setClearDay}. */
	static void setNight(GameTestHelper helper) {
		var level = helper.getLevel();
		var server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f);
		level.updateSkyBrightness();
	}

	/**
	 * Turn on rain in the current (already-settled) time, synchronously: WeatherData + interpolated level.
	 *
	 * <p>The 26.3 gametest world grew a desert overworld (GameTestServer switched to the
	 * {@code flat_all_dimensions} preset; 26.2 staged on {@code flat}/plains), and precipitation is
	 * biome-based by design (MOD-602) — a desert staging area classifies every storm as NONE and the
	 * panel keeps working. Give this structure a precipitation biome before the rain, so what the
	 * scenarios stage is what their names claim.
	 */
	static void setRaining(GameTestHelper helper, boolean thunder) {
		helper.setBiome(Biomes.PLAINS);
		var level = helper.getLevel();
		level.setRainLevel(1.0f); // isRaining() reads the interpolated rain level, not WeatherData
		level.getWeatherData().setRaining(true);
		if (thunder) {
			level.getWeatherData().setThundering(true);
			level.setThunderLevel(1.0f); // isThundering() multiplies by the rain level and reads the ramp,
			// which starts at zero — set it so the staged storm is a storm, not a drizzle claiming to be one
		}
	}

	static SolarPanelBlockEntity panelAt(GameTestHelper helper) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(POS)) instanceof SolarPanelBlockEntity p ? p : null;
	}

	static MoonlitSolarPanelBlockEntity moonlitAt(GameTestHelper helper) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(POS)) instanceof MoonlitSolarPanelBlockEntity p
				? p : null;
	}

	static AbstractGeneratorBlockEntity genAt(GameTestHelper helper) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(POS)) instanceof AbstractGeneratorBlockEntity g
				? g : null;
	}

	/**
	 * Shared assertion: top face emits no EU (working surface), the other five faces are OUT-only.
	 * Loader-neutral equivalent of the Fabric lane's {@code EnergyStorage.SIDED} probe: the per-face
	 * {@link EnergyPortHost#energyPort} is exactly what both loaders' energy capability is derived
	 * from (MOD-433), so a null port with a non-null extracting port elsewhere proves the same thing.
	 */
	static void assertTopFaceWorkingSurface(GameTestHelper helper, String label) {
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(POS)) instanceof EnergyPortHost host)) {
			helper.fail(label + ": no EnergyPortHost at " + POS);
			return;
		}
		EnergyPort top = host.energyPort(Direction.UP);
		if (top != null && top.supportsExtraction()) {
			helper.fail(label + ": top face (working surface) must not emit EU");
		}
		for (Direction d : new Direction[]{
				Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
			EnergyPort p = host.energyPort(d);
			if (p == null || !p.supportsExtraction()) {
				helper.fail(label + " face " + d + " must emit EU");
			}
		}
	}

	// --- Mirror Concentrator, the day branch's third rung (MOD-602) ---

	static RadiantSolarPanelBlockEntity concentratorAt(GameTestHelper helper) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(POS))
				instanceof RadiantSolarPanelBlockEntity p ? p : null;
	}

	static DaylightSolarPanelBlockEntity daylightAt(GameTestHelper helper) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(POS))
				instanceof DaylightSolarPanelBlockEntity d ? d : null;
	}
}
