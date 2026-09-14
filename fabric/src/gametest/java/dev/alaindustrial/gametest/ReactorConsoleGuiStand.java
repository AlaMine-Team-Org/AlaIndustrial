package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;

import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorIdleReason;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.client.screen.reactor.ConsoleTabPage;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.registry.ModContent;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The reactor controller's «Console» tab in the seven states a player meets (MOD-618, MOD-623).
 *
 * <p><b>Built on the client's own menu</b>, like the water mill's stand: a real controller rescans and
 * re-sends its channels on its own timer, so a value injected into a menu with a server behind it
 * survives a tick at most. A client menu has nothing behind it, so the state photographed is the state
 * under test. What this does not cover is the real open path — {@code ScreensClientGameTest} opens a
 * placed controller by right-click.
 *
 * <p><b>Why a gate on the advice box.</b> Every state has its own advice, and the box is the one band of
 * the tab that must change with each of them. If the page stopped switching on the readout — one state
 * hardcoded, a verdict ranked wrongly — the box would draw the same picture twice and the comparison
 * fails. The chip is kept out of the band on purpose: an alarm chip blinks, and a band that moves by
 * itself could satisfy a "must differ" gate while the advice was broken.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ReactorConsoleGuiStand {

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

	/** {@code {leftPos, topPos, imageWidth, guiScaledWidth}} of the last frame, in GUI-scaled units. */
	private static int[] windowBox;

	/** Two different pieces of advice are hundreds of pixels apart; a broken switch is zero. */
	private static final int MIN_BAND_DELTA = 100;

	/**
	 * Ticks between opening the screen and taking the frame. The page shows or hides its throttle and
	 * catches the slider up with the menu in its tick, so a frame taken on the first tick after opening
	 * shows the controls of the state before the injection.
	 */
	private static final int SETTLE_TICKS = 3;

	private ReactorConsoleGuiStand() {
	}

	/** Photographs the seven states and proves the advice box follows the state. */
	public static void shootConsole(ClientGameTestContext context) {
		Path running = shoot(context, "console_running",
				"Sealed room on water: the heat bar nearly empty with the 70 / 85 marks and their numbers clear of "
						+ "each other, 384 EU/t, coolant and steam rows with bars, the slider reading 75 % beside the "
						+ "STOP button, advice \"All normal\" with a green tick badge and the RUNNING chip in green",
				Reading.running());
		Path runningAgain = shoot(context, "console_running_again",
				"The same frame shot a second time: the noise floor the gates below are measured against",
				Reading.running());
		Path steam = shoot(context, "console_steam_blocked",
				"Exhaust blocked: the steam row turns amber and says so, heat 79 % in amber, WARNING chip, and "
						+ "the advice names the Steam Nozzle",
				Reading.running().heat(79).output(512).depth(100).water(100).rate(14).steam(96));
		Path idle = shoot(context, "console_no_signal",
				"Sealed but silent: the output row reads the idle reason in red, IDLE chip in grey, advice tells "
						+ "the player to give the controller a redstone signal",
				Reading.running().output(0).idle(ReactorIdleReason.NO_SIGNAL).heat(9).water(81).rate(0).steam(6));
		Path dry = shoot(context, "console_no_water",
				"Running with no water (MOD-623): 384 EU/t still delivered, heat 40 % with an empty coolant row, "
						+ "the WARNING chip in amber, and the advice says nothing cools the reactor and names the "
						+ "Reactor Inlet",
				Reading.running().heat(40).water(0).rate(0).steam(0));
		Path building = shoot(context, "console_building_breach",
				"Shell with a breach: the verdict in red on top, the room row reads a dash, the problem row names "
						+ "the direction, no throttle, BUILDING chip, advice tells what is missing and where",
				Reading.building());
		Path bare = shoot(context, "console_bare",
				"Racks with no room: instability 59 % instead of heat, no coolant rows, no throttle — a note that "
						+ "only the redstone signal stops it — and the BARE MODE chip in amber",
				Reading.bare());
		Path blast = shoot(context, "console_blast",
				"Accident: the red countdown bar under the header, heat pinned at 100 %, the chip reads the "
						+ "accident, and the advice lists three numbered ways out without clipping",
				Reading.running().heat(100).output(512).depth(100).water(0).rate(0).steam(22).meltdown(true)
						.blast(63).stored(100));

		int noise = adviceBandDelta(running, runningAgain);
		LOG.info("[GUITEST][MOD-618] advice band noise floor: {} px", noise);
		assertAdviceDiffers("running vs accident", running, blast, noise);
		assertAdviceDiffers("running vs blocked exhaust", running, steam, noise);
		assertAdviceDiffers("running vs no signal", running, idle, noise);
		assertAdviceDiffers("no signal vs no water", idle, dry, noise);
		assertAdviceDiffers("building vs bare", building, bare, noise);
	}

	private static Path shoot(ClientGameTestContext context, String state, String checks, Reading reading) {
		LOG.info("[GUITEST][MOD-618] opening reactor_controller/{}", state);
		context.runOnClient(mc -> {
			MenuScreens.create(ModContent.REACTOR_CONTROLLER_MENU.get(), mc, 0,
					Component.translatable("block.alaindustrial.reactor_controller"));
			// No silent fallback: a frame with nothing injected differs from the others just as readily as a
			// correct one, so a quiet no-op here would keep every gate green while measuring nothing.
			if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> acs)
					|| !(acs.getMenu() instanceof ReactorControllerMenu menu)) {
				throw new AssertionError("[GUITEST][MOD-618] MenuScreens.create did not open the reactor "
						+ "controller screen for " + state);
			}
			reading.applyTo(menu);
			var pos = (dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor) acs;
			windowBox = new int[] {
					pos.alaindustrial$getLeftPos(),
					pos.alaindustrial$getTopPos(),
					pos.alaindustrial$getImageWidth(),
					mc.getWindow().getGuiScaledWidth(),
			};
		});
		awaitMenuScreen(context);
		context.waitTicks(SETTLE_TICKS);
		return ShotRecorder.captureScreen("reactor_controller", state,
				ShotRecorder.rules("R-GUI-01", "R-GUI-03"), checks);
	}

	private static int adviceBandDelta(Path first, Path second) {
		return VisualStandSupport.differingPixelsInBand(first, second, windowBox,
				ConsoleTabPage.CONTENT_LEFT, ConsoleTabPage.CONTENT_RIGHT,
				ConsoleTabPage.ADVICE_Y, ConsoleTabPage.ADVICE_BOTTOM - ConsoleTabPage.ADVICE_Y);
	}

	private static void assertAdviceDiffers(String what, Path first, Path second, int noise) {
		int delta = adviceBandDelta(first, second);
		int required = Math.max(4 * noise, MIN_BAND_DELTA);
		LOG.info("[GUITEST][MOD-618] {}: delta={} px, noise={} px, required>{}", what, delta, noise, required);
		if (delta < required) {
			throw new AssertionError("[GUITEST][MOD-618] " + what + " changed only " + delta
					+ " px in the advice box (noise " + noise + " px, required > " + required + ") — the tab drew "
					+ "the same advice for two different states, or none at all. Band: "
					+ VisualStandSupport.describeBand(ConsoleTabPage.CONTENT_LEFT, ConsoleTabPage.CONTENT_RIGHT,
							ConsoleTabPage.ADVICE_Y, ConsoleTabPage.ADVICE_BOTTOM - ConsoleTabPage.ADVICE_Y)
					+ ". Compare " + first.getFileName() + " with " + second.getFileName() + ".");
		}
	}

	/** One set of controller channels, edited copy-on-write so each frame names only what it changes. */
	private record Reading(ReactorRoomStatus status, int rods, int depth, int output, int heat, int water,
			int rate, int steam, ReactorIdleReason idle, int stored, boolean meltdown, int blast,
			int instability, int breachDx, int breachDy, int breachDz) {

		/** 200 000 EU buffer, as Config.reactorBuffer ships. */
		private static final int CAPACITY = 200_000;

		static Reading running() {
			// Heat 2: a room whose water carries the reaction sits near the bottom of the scale (MOD-623).
			return new Reading(ReactorRoomStatus.FORMED, 32, 75, 384, 2, 72, 240, 38, ReactorIdleReason.RUNNING,
					45, false, 0, 0, 0, 0, 0);
		}

		static Reading building() {
			return new Reading(ReactorRoomStatus.BREACH, 0, 75, 0, 0, 0, 0, 0, ReactorIdleReason.NOT_SEALED,
					0, false, 0, 0, 4, 2, -3);
		}

		static Reading bare() {
			return new Reading(ReactorRoomStatus.CONTROLLER_NOT_IN_WALL, 8, 100, 29, 0, 0, 0, 0,
					ReactorIdleReason.RUNNING, 20, false, 0, 59, 0, 0, 0);
		}

		Reading heat(int v) {
			return new Reading(status, rods, depth, output, v, water, rate, steam, idle, stored, meltdown, blast,
					instability, breachDx, breachDy, breachDz);
		}

		Reading output(int v) {
			return new Reading(status, rods, depth, v, heat, water, rate, steam, idle, stored, meltdown, blast,
					instability, breachDx, breachDy, breachDz);
		}

		Reading depth(int v) {
			return new Reading(status, rods, v, output, heat, water, rate, steam, idle, stored, meltdown, blast,
					instability, breachDx, breachDy, breachDz);
		}

		Reading water(int v) {
			return new Reading(status, rods, depth, output, heat, v, rate, steam, idle, stored, meltdown, blast,
					instability, breachDx, breachDy, breachDz);
		}

		Reading rate(int v) {
			return new Reading(status, rods, depth, output, heat, water, v, steam, idle, stored, meltdown, blast,
					instability, breachDx, breachDy, breachDz);
		}

		Reading steam(int v) {
			return new Reading(status, rods, depth, output, heat, water, rate, v, idle, stored, meltdown, blast,
					instability, breachDx, breachDy, breachDz);
		}

		Reading idle(ReactorIdleReason v) {
			return new Reading(status, rods, depth, output, heat, water, rate, steam, v, stored, meltdown, blast,
					instability, breachDx, breachDy, breachDz);
		}

		Reading stored(int v) {
			return new Reading(status, rods, depth, output, heat, water, rate, steam, idle, v, meltdown, blast,
					instability, breachDx, breachDy, breachDz);
		}

		Reading meltdown(boolean v) {
			return new Reading(status, rods, depth, output, heat, water, rate, steam, idle, stored, v, blast,
					instability, breachDx, breachDy, breachDz);
		}

		Reading blast(int v) {
			return new Reading(status, rods, depth, output, heat, water, rate, steam, idle, stored, meltdown, v,
					instability, breachDx, breachDy, breachDz);
		}

		void applyTo(ReactorControllerMenu menu) {
			menu.injectTestData(CAPACITY * stored / 100, CAPACITY, 0, 0);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_STATUS, status.ordinal());
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_BREACH_DX, breachDx);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_BREACH_DY, breachDy);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_BREACH_DZ, breachDz);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_SIZE_X, 0);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_SIZE_Y, 0);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_SIZE_Z, 0);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_HEAT_PERCENT, heat);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_RODS, rods);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_DEPTH_PERCENT, depth);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_OUTPUT, output);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_WATER_PERCENT, water);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_WATER_RATE, rate);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_STEAM_PERCENT, steam);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_IDLE_REASON, idle.ordinal());
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_ENERGY_PERCENT, stored);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_ENERGY_HUNDREDS, CAPACITY * stored / 100 / 100);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_MELTDOWN, meltdown ? 1 : 0);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_BLAST_PERCENT, blast);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_INSTABILITY, instability);
			// In these frames a working reactor with water carries all its heat, a working one with none carries
			// nothing, and a silent one has nothing to carry.
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_COOLANT_SHARE, water > 0 || output == 0 ? 100 : 0);
			// The shipped defaults, written out: the stand photographs a layout, not this client's Config.
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_HEAT_WARN, 70);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_HEAT_MELTDOWN, 85);
		}
	}
}
