package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;

import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorIdleReason;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.client.screen.ReactorControllerScreen;
import dev.alaindustrial.client.screen.reactor.ConsoleTabPage;
import dev.alaindustrial.client.screen.reactor.RoomTabPage;
import dev.alaindustrial.client.screen.reactor.CoolantTabPage;
import dev.alaindustrial.client.screen.reactor.ZoneTabPage;
import dev.alaindustrial.core.structure.ReactorZone;
import dev.alaindustrial.network.ReactorZonePayload;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.registry.ModContent;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The reactor controller's screen in the states a player meets: the «Console» tab in seven (MOD-618, MOD-623)
 * the «Room» tab in four (MOD-619) and the «Core» tab in four (MOD-620).
 *
 * <p><b>Built on the client's own menu</b>, like the water mill's stand: a real controller rescans and
 * re-sends its channels on its own timer, so a value injected into a menu with a server behind it
 * survives a tick at most. A client menu has nothing behind it, so the state photographed is the state
 * under test. What this does not cover is the real open path — {@code ScreensClientGameTest} opens a
 * placed controller by right-click.
 *
 * <p><b>Why a gate on one band per tab.</b> Every console state has its own advice, and every room state its
 * own checklist; each is the one band of its tab that must change with the state. If a page stopped switching
 * on the readout — one state hardcoded, a verdict ranked wrongly — the band would draw the same picture twice
 * and the comparison fails. The chip, the countdown and the room map's blinking problem cell are kept out of
 * the bands on purpose: a band that moves by itself could satisfy a "must differ" gate while the page was
 * broken.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ReactorConsoleGuiStand {

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

	/** {@code {leftPos, topPos, imageWidth, guiScaledWidth}} of the last frame, in GUI-scaled units. */
	private static int[] windowBox;

	/** The tab the last frame's screen had open once it settled. */
	private static int openedPage;

	/** Two different pieces of advice are hundreds of pixels apart; a broken switch is zero. */
	private static final int MIN_BAND_DELTA = 100;

	/** Passed instead of a tab: let the screen choose, as it does when a player opens it. */
	private static final int SCREEN_CHOOSES = -1;

	/**
	 * Ticks between opening the screen and taking the frame. The page shows or hides its throttle and
	 * catches the slider up with the menu in its tick, and the screen picks its opening tab on the first tick
	 * the channels are in, so a frame taken on the first tick after opening shows the state before the
	 * injection.
	 */
	private static final int SETTLE_TICKS = 3;

	private ReactorConsoleGuiStand() {
	}

	/** Photographs the seven console states and proves the advice box follows the state. */
	public static void shootConsole(ClientGameTestContext context) {
		int console = ReactorControllerScreen.PAGE_CONSOLE;
		Path running = shoot(context, "console_running",
				"Sealed room on water: the heat bar nearly empty with the 70 / 85 marks and their numbers clear of "
						+ "each other, 384 EU/t, coolant and steam rows with bars, the slider reading 75 % beside the "
						+ "STOP button, advice \"All normal\" with a green tick badge and the RUNNING chip in green",
				Reading.running(), RoomBox.NONE, console);
		Path runningAgain = shoot(context, "console_running_again",
				"The same frame shot a second time: the noise floor the gates below are measured against",
				Reading.running(), RoomBox.NONE, console);
		Path steam = shoot(context, "console_steam_blocked",
				"Exhaust blocked: the steam row turns amber and says so, heat 79 % in amber, WARNING chip, and "
						+ "the advice names the Steam Nozzle",
				Reading.running().heat(79).output(512).depth(100).water(100).rate(14).steam(96), RoomBox.NONE, console);
		Path idle = shoot(context, "console_no_signal",
				"Sealed but silent: the output row reads the idle reason in red, IDLE chip in grey, advice tells "
						+ "the player to give the controller a redstone signal",
				Reading.running().output(0).idle(ReactorIdleReason.NO_SIGNAL).heat(9).water(81).rate(0).steam(6),
				RoomBox.NONE, console);
		Path dry = shoot(context, "console_no_water",
				"Running with no water (MOD-623): 384 EU/t still delivered, heat 40 % with an empty coolant row, "
						+ "the WARNING chip in amber, and the advice says nothing cools the reactor and names the "
						+ "Reactor Inlet",
				Reading.running().heat(40).water(0).rate(0).steam(0), RoomBox.NONE, console);
		Path building = shoot(context, "console_building_breach",
				"Shell with a breach, Console tab picked by hand: the verdict in red on top, a full-width button "
						+ "that opens the Room tab under it, the buffer row, no throttle, BUILDING chip, advice tells "
						+ "what is missing",
				Reading.building(), RoomBox.ROOM_7X5X7, console);
		Path bare = shoot(context, "console_bare",
				"Racks with no room: instability 59 % instead of heat, no coolant rows, no throttle — a note that "
						+ "only the redstone signal stops it — and the BARE MODE chip in amber",
				Reading.bare(), RoomBox.NONE, console);
		Path blast = shoot(context, "console_blast",
				"Accident: the red countdown bar under the header, heat pinned at 100 %, the chip reads the "
						+ "accident, and the advice lists three numbered ways out without clipping",
				Reading.running().heat(100).output(512).depth(100).water(0).rate(0).steam(22).meltdown(true)
						.blast(63).stored(100),
				RoomBox.NONE, console);

		int noise = adviceBandDelta(running, runningAgain);
		LOG.info("[GUITEST][MOD-618] advice band noise floor: {} px", noise);
		assertAdviceDiffers("running vs accident", running, blast, noise);
		assertAdviceDiffers("running vs blocked exhaust", running, steam, noise);
		assertAdviceDiffers("running vs no signal", running, idle, noise);
		assertAdviceDiffers("no signal vs no water", idle, dry, noise);
		assertAdviceDiffers("building vs bare", building, bare, noise);
	}

	/**
	 * Photographs the «Room» tab in four states, proves the checklist follows the scan, and that a shell still
	 * being built opens the screen on this tab by itself.
	 *
	 * <p>The three unbuilt rooms go first and are left to the screen, so each is opened while the remembered tab
	 * is still the console's; a frame that picked the room tab by hand would make it the remembered one and turn
	 * the check on the next frame into a check of that memory.
	 */
	public static void shootRoom(ClientGameTestContext context) {
		Path breach = shoot(context, "room_breach",
				"Shell with five holes, opened by the screen on the Room tab: a 9×9 shell ring with the orange "
						+ "controller on the west wall; blinking red cells on the west wall either side of it, one on "
						+ "the east wall marked ×2 for two stacked holes, one in the middle marked +4 for the ceiling; "
						+ "the checklist with three ticks, the hole row crossed on a pink band and the rest dotted, and "
						+ "the box under the map saying five holes",
				Reading.building().breach(0, 1, -2), RoomBox.ROOM_7X5X7_HOLES, SCREEN_CHOOSES);
		assertOpenedOnRoom("a shell with a breach");
		Path small = shoot(context, "room_too_small",
				"Room too small: a 4×5 shell ring on the map, two ticks then the size row crossed and reading "
						+ "3–12, the rest dotted, and the box says to make the room bigger",
				Reading.building().status(ReactorRoomStatus.TOO_SMALL).breach(1, 0, 0), RoomBox.ROOM_2X3X3,
				SCREEN_CHOOSES);
		assertOpenedOnRoom("a room that is too small");
		Path doorway = shoot(context, "room_no_doorway",
				"A door not standing on the floor: a 7×7 shell ring, six ticks, the door row crossed, the glass "
						+ "row dotted, no problem cell on the map (the door has no single block to point at)",
				Reading.building().status(ReactorRoomStatus.NO_DOORWAY).breach(0, 0, 0), RoomBox.ROOM_5X4X5,
				SCREEN_CHOOSES);
		assertOpenedOnRoom("a room without a doorway");
		Path doorwayAgain = shoot(context, "room_no_doorway_again",
				"The same frame shot a second time, the Room tab picked by hand: the noise floor for the gates below",
				Reading.building().status(ReactorRoomStatus.NO_DOORWAY).breach(0, 0, 0), RoomBox.ROOM_5X4X5,
				ReactorControllerScreen.PAGE_ROOM);
		Path formed = shoot(context, "room_formed",
				"Sealed room, Room tab picked by hand: an 8×8 shell ring with the controller, every row ticked, "
						+ "no tab marker, and the green box saying the shell is sealed and pointing at the Console tab",
				Reading.running(), RoomBox.ROOM_6X4X6, ReactorControllerScreen.PAGE_ROOM);

		int noise = checklistBandDelta(doorway, doorwayAgain);
		LOG.info("[GUITEST][MOD-619] checklist band noise floor: {} px", noise);
		assertChecklistDiffers("breach vs too small", breach, small, noise);
		assertChecklistDiffers("too small vs no doorway", small, doorway, noise);
		assertChecklistDiffers("no doorway vs sealed", doorway, formed, noise);

		// The last two frames picked the Room tab by hand, which the screen remembers.
		rememberConsoleTab(context);
	}

	/**
	 * Photographs the «Core» tab in four states and proves its detail panel follows both the zone and the stack a
	 * player picks (MOD-620). The zone arrives the way the server sends it — a {@link ReactorZonePayload} handed to
	 * the client menu — so the page draws exactly what a real snapshot would make it draw.
	 */
	public static void shootZone(ClientGameTestContext context) {
		int zoneTab = ReactorControllerScreen.PAGE_ZONE;
		Path formed = shoot(context, "zone_formed",
				"Sealed room core, 6x6: nine stacks in the middle as inventory slots with green rod pips and a durability "
						+ "bar each, the centre stack two columns tall with a small count and the red densest mark, the "
						+ "north-east stack holding a spent casing shown in detail on the right with its amber row, the "
						+ "summary, the amber replace-first line naming that stack, the legend and the hint",
				Reading.running(), RoomBox.ROOM_6X4X6, zoneTab, screen -> screen.getMenu().acceptZone(ZONE_FORMED));
		Path formedAgain = shoot(context, "zone_formed_again",
				"The same frame shot a second time: the noise floor for the gates below",
				Reading.running(), RoomBox.ROOM_6X4X6, zoneTab, screen -> screen.getMenu().acceptZone(ZONE_FORMED));
		Path picked = shoot(context, "zone_selected",
				"The same core with the centre stack picked: a gold outline on it and the detail panel showing two "
						+ "columns, eight rods, eight fuelled neighbours",
				Reading.running(), RoomBox.ROOM_6X4X6, zoneTab, screen -> {
					screen.getMenu().acceptZone(ZONE_FORMED);
					((ZoneTabPage) screen.page(zoneTab)).pick(2, 2);
				});
		shoot(context, "zone_bare",
				"Racks burning with no room: a 3x2 grid over their own footprint, three stacks, the BARE MODE chip",
				Reading.bare(), RoomBox.NONE, zoneTab, screen -> screen.getMenu().acceptZone(ZONE_BARE));
		Path hall = shoot(context, "zone_hall_12",
				"The largest default hall, 12x12 with a stack on every cell: the whole grid inside its frame, seven-pixel "
						+ "cells each filled green with a one-pixel wear line, a dark cell for the rack of spent casings, "
						+ "a small red densest mark on the stack in the middle, and the gold outline on the stack to "
						+ "replace first",
				Reading.running(), RoomBox.NONE, zoneTab, screen -> screen.getMenu().acceptZone(hall(false)));
		Path hallEmpty = shoot(context, "zone_hall_12_empty",
				"The same 12x12 hall with every column empty: light slots with no fill and no wear lines - the picture the "
						+ "gate below requires the full hall to differ from",
				Reading.running(), RoomBox.NONE, zoneTab, screen -> screen.getMenu().acceptZone(hall(true)));
		Path empty = shoot(context, "zone_empty",
				"A sealed room with no columns: a 6x6 grid of dark empty slots and the detail panel saying the room has "
						+ "no columns",
				Reading.running(), RoomBox.ROOM_6X4X6, zoneTab,
				screen -> screen.getMenu().acceptZone(new ReactorZonePayload(0, 1, -3, 6, 6, List.of())));

		int noise = detailBandDelta(formed, formedAgain);
		LOG.info("[GUITEST][MOD-620] detail band noise floor: {} px", noise);
		assertDetailDiffers("default stack vs picked stack", formed, picked, noise);
		assertDetailDiffers("a core vs an empty room", formed, empty, noise);
		// The grid itself (review, MOD-620): a grid that drew nothing kept both detail gates green.
		int gridNoise = gridBandDelta(formed, formedAgain);
		assertGridDiffers("a core vs an empty room", formed, empty, gridNoise);
		assertGridDiffers("a full 12x12 hall vs one of empty columns", hall, hallEmpty, gridNoise);
		rememberConsoleTab(context);
	}

	/**
	 * Photographs the «Coolant» tab in the states a player meets and proves its advice and detail panel follow the loop
	 * (MOD-621): in order, a blocked exhaust, a dry stack beside wet ones while the heat is left behind, a bare pile with
	 * no loop, and a 12x12 hall at the smallest cells. The zone arrives as the server's snapshot would bring it.
	 */
	public static void shootCoolant(ClientGameTestContext context) {
		int coolantTab = ReactorControllerScreen.PAGE_COOLANT;
		Path normal = shoot(context, "coolant_normal",
				"Sealed room loop, 6x6: nine stacks as slots each with a teal water bar on the left and a light steam bar "
						+ "on the right, no marks, the first stack in detail with its water and steam in mB and In order, "
						+ "the summary with no dry or blocked stacks and the water rate, the green In order advice",
				Reading.running(), RoomBox.ROOM_6X4X6, coolantTab, screen -> screen.getMenu().acceptZone(ZONE_FORMED));
		Path normalAgain = shoot(context, "coolant_normal_again",
				"The same frame shot a second time: the noise floor for the gates below",
				Reading.running(), RoomBox.ROOM_6X4X6, coolantTab, screen -> screen.getMenu().acceptZone(ZONE_FORMED));
		Path blocked = shoot(context, "coolant_blocked",
				"The same loop with the north-east stack's steam at 95 %: its steam bar amber with an amber corner mark, "
						+ "the gold outline and the detail panel on it reading Exhaust blocked, one blocked in the summary, "
						+ "and the amber advice naming stack 4, 2 and the Steam Nozzle above it",
				Reading.running(), RoomBox.ROOM_6X4X6, coolantTab,
				screen -> screen.getMenu().acceptZone(with(ZONE_FORMED, stack(3, 1, 1, 3, 1, 480, 1000, 2, 70, 95))));
		Path dry = shoot(context, "coolant_dry",
				"The same loop with the south-west stack holding no water while the room's water carries none of the heat: "
						+ "that cell's water bar empty over a red floor with a red corner mark, the detail panel reading Dry, "
						+ "and the red advice naming stack 2, 4 and saying columns side by side share no water",
				Reading.running().water(0), RoomBox.ROOM_6X4X6, coolantTab,
				screen -> screen.getMenu().acceptZone(with(ZONE_FORMED, stack(1, 3, 1, 4, 0, 200, 210, 2, 0, 19))));
		shoot(context, "coolant_bare",
				"Racks burning with no room: an empty grid saying there is no loop without a room, an empty detail panel, "
						+ "and the grey advice that a bare pile heats no water and needs none",
				Reading.bare(), RoomBox.NONE, coolantTab, screen -> screen.getMenu().acceptZone(ZONE_BARE));
		Path hall = shoot(context, "coolant_hall_12",
				"The largest default hall, 12x12 with a stack on every cell: seven-pixel cells each split into a water "
						+ "and a steam bar, the whole grid inside its frame",
				Reading.running(), RoomBox.NONE, coolantTab, screen -> screen.getMenu().acceptZone(hall(false)));
		Path hallDry = shoot(context, "coolant_hall_12_empty",
				"The same 12x12 hall with every column empty: no water or steam bars, a red floor under every water half "
						+ "- the picture the grid gate below requires the full hall to differ from",
				Reading.running(), RoomBox.NONE, coolantTab, screen -> screen.getMenu().acceptZone(hall(true)));

		int adviceNoise = coolantAdviceBandDelta(normal, normalAgain);
		LOG.info("[GUITEST][MOD-621] advice band noise floor: {} px", adviceNoise);
		assertCoolantBandDiffers("advice", "a loop in order vs a blocked exhaust", normal, blocked, adviceNoise,
				ReactorConsoleGuiStand::coolantAdviceBandDelta);
		assertCoolantBandDiffers("advice", "a blocked exhaust vs a dry stack", blocked, dry, adviceNoise,
				ReactorConsoleGuiStand::coolantAdviceBandDelta);
		assertCoolantBandDiffers("detail", "the first stack in order vs the dry stack", normal, dry,
				detailBandDelta(normal, normalAgain), ReactorConsoleGuiStand::detailBandDelta);
		// The cells themselves (review, MOD-621): the advice and detail gates stay green over a grid that draws nothing.
		// Both halls select their first stack, so only the bars and the red floors differ.
		assertCoolantBandDiffers("grid", "a full 12x12 hall vs one of empty columns", hall, hallDry,
				gridBandDelta(normal, normalAgain), ReactorConsoleGuiStand::gridBandDelta);
		rememberConsoleTab(context);
	}

	/** A snapshot with one stack swapped for another at the same place. */
	private static ReactorZonePayload with(ReactorZonePayload zone, ReactorZone.Stack replacement) {
		List<ReactorZone.Stack> stacks = new java.util.ArrayList<>(zone.stacks());
		stacks.replaceAll(stack -> stack.x() == replacement.x() && stack.z() == replacement.z() ? replacement : stack);
		return new ReactorZonePayload(zone.containerId(), zone.originDx(), zone.originDz(), zone.width(), zone.depth(),
				stacks);
	}

	/** The Coolant tab's advice box. */
	private static int coolantAdviceBandDelta(Path first, Path second) {
		return VisualStandSupport.differingPixelsInBand(first, second, windowBox,
				ConsoleTabPage.CONTENT_LEFT, ConsoleTabPage.CONTENT_RIGHT, CoolantTabPage.ADVICE_Y,
				CoolantTabPage.ADVICE_BOTTOM - CoolantTabPage.ADVICE_Y);
	}

	private static void assertCoolantBandDiffers(String band, String what, Path first, Path second, int noise,
			java.util.function.ToIntBiFunction<Path, Path> delta) {
		int changed = delta.applyAsInt(first, second);
		int required = Math.max(4 * noise, MIN_BAND_DELTA);
		LOG.info("[GUITEST][MOD-621] {}: {}: delta={} px, noise={} px, required>{}", band, what, changed, noise, required);
		if (changed < required) {
			throw new AssertionError("[GUITEST][MOD-621] " + band + ": " + what + " changed only " + changed
					+ " px (noise " + noise + " px, required > " + required + ") - the Coolant tab drew the same " + band
					+ " for two different loops, or nothing at all. Compare " + first.getFileName() + " with "
					+ second.getFileName() + ".");
		}
	}

	/** Hands the console tab back, or the next stand to open a controller would photograph whatever was picked last. */
	private static void rememberConsoleTab(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			MenuScreens.create(ModContent.REACTOR_CONTROLLER_MENU.get(), mc, 0,
					Component.translatable("block.alaindustrial.reactor_controller"));
			if (mc.gui.screen() instanceof ReactorControllerScreen screen) {
				screen.selectPage(ReactorControllerScreen.PAGE_ROOM);
				screen.selectPage(ReactorControllerScreen.PAGE_CONSOLE);
			}
		});
	}

	private static ReactorZone.Stack stack(int x, int z, int columns, int fuelled, int spent, int averageWear,
			int worstWear, int neighbours, int water, int steam) {
		long remaining = (long) fuelled * (1000 - Math.min(1000, averageWear)) * 144;
		return new ReactorZone.Stack(x, z, columns, fuelled, spent, averageWear, worstWear, remaining, neighbours,
				coolant(columns, water, steam));
	}

	/** A stack's tanks from percentages, 4000 mB a column each, with the faults the server would judge from them. */
	private static ReactorZone.Coolant coolant(int columns, int waterPercent, int steamPercent) {
		long capacity = 4000L * columns;
		return new ReactorZone.Coolant(capacity * waterPercent / 100, capacity, capacity * steamPercent / 100, capacity,
				waterPercent == 0, steamPercent >= ReactorZone.STEAM_BLOCKED_PERCENT);
	}

	/** A 6x6 core: a 3x3 block of stacks, the centre two columns tall and the densest, one rack holding a casing. */
	private static final ReactorZonePayload ZONE_FORMED = new ReactorZonePayload(0, 1, -3, 6, 6, List.of(
			stack(1, 1, 1, 4, 0, 300, 320, 2, 80, 20),
			stack(2, 1, 1, 4, 0, 310, 330, 3, 78, 22),
			stack(3, 1, 1, 3, 1, 480, 1000, 2, 70, 30),
			stack(1, 2, 2, 8, 0, 250, 270, 5, 82, 18),
			stack(2, 2, 2, 8, 0, 260, 280, 8, 84, 16),
			stack(3, 2, 1, 4, 0, 300, 300, 3, 79, 21),
			stack(1, 3, 1, 4, 0, 200, 210, 2, 81, 19),
			stack(2, 3, 1, 4, 0, 220, 230, 3, 80, 20),
			stack(3, 3, 1, 2, 0, 500, 510, 1, 76, 24)));

	/**
	 * A 12x12 hall with a stack on every cell (MOD-620): the size at which cells shrink below the pips, which a 6x6
	 * frame never reaches. Wear grows towards the south-east, the middle stack is the densest, one rack holds only
	 * spent casings.
	 */
	private static ReactorZonePayload hall(boolean emptyColumns) {
		List<ReactorZone.Stack> stacks = new java.util.ArrayList<>();
		for (int z = 0; z < 12; z++) {
			for (int x = 0; x < 12; x++) {
				boolean spentOnly = x == 10 && z == 2;
				boolean middle = x == 6 && z == 6;
				int wear = (x + z) * 40;
				stacks.add(emptyColumns ? stack(x, z, 1, 0, 0, 0, 0, 0, 0, 0)
						: spentOnly
						? stack(x, z, 1, 0, 4, 1000, 1000, 0, 60, 30)
						: stack(x, z, middle ? 3 : 1, middle ? 12 : 4, 0, wear, wear + 20, middle ? 12 : 4, 70, 25));
			}
		}
		return new ReactorZonePayload(0, 1, -6, 12, 12, stacks);
	}

	/** Three bare racks over a 3x2 footprint. */
	private static final ReactorZonePayload ZONE_BARE = new ReactorZonePayload(0, -1, 1, 3, 2, List.of(
			stack(0, 0, 1, 4, 0, 100, 110, 1, 0, 0),
			stack(1, 0, 1, 4, 0, 120, 130, 2, 0, 0),
			stack(2, 1, 1, 4, 0, 90, 100, 0, 0, 0)));

	private static Path shoot(ClientGameTestContext context, String state, String checks, Reading reading,
			RoomBox box, int page) {
		return shoot(context, state, checks, reading, box, page, screen -> {
		});
	}

	private static Path shoot(ClientGameTestContext context, String state, String checks, Reading reading,
			RoomBox box, int page, Consumer<ReactorControllerScreen> before) {
		LOG.info("[GUITEST][MOD-618] opening reactor_controller/{}", state);
		context.runOnClient(mc -> {
			MenuScreens.create(ModContent.REACTOR_CONTROLLER_MENU.get(), mc, 0,
					Component.translatable("block.alaindustrial.reactor_controller"));
			// No silent fallback: a frame with nothing injected differs from the others just as readily as a
			// correct one, so a quiet no-op here would keep every gate green while measuring nothing.
			if (!(mc.gui.screen() instanceof ReactorControllerScreen screen)
					|| !(screen.getMenu() instanceof ReactorControllerMenu menu)) {
				throw new AssertionError("[GUITEST][MOD-618] MenuScreens.create did not open the reactor "
						+ "controller screen for " + state);
			}
			if (page == SCREEN_CHOOSES && screen.selectedPage() != ReactorControllerScreen.PAGE_CONSOLE) {
				throw new AssertionError("[GUITEST][MOD-619] " + state + " opened on tab " + screen.selectedPage()
						+ " before the channels arrived: the remembered tab is not the console's, so a frame on the "
						+ "Room tab would not show that the screen chose it");
			}
			reading.applyTo(menu, box);
			if (page != SCREEN_CHOOSES) {
				screen.selectPage(page);
			}
			before.accept(screen);
			var pos = (dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor) (AbstractContainerScreen<?>) screen;
			windowBox = new int[] {
					pos.alaindustrial$getLeftPos(),
					pos.alaindustrial$getTopPos(),
					pos.alaindustrial$getImageWidth(),
					mc.getWindow().getGuiScaledWidth(),
			};
		});
		awaitMenuScreen(context);
		context.waitTicks(SETTLE_TICKS);
		context.runOnClient(mc -> openedPage = mc.gui.screen() instanceof ReactorControllerScreen screen
				? screen.selectedPage() : -1);
		return ShotRecorder.captureScreen("reactor_controller", state,
				ShotRecorder.rules("R-GUI-01", "R-GUI-03"), checks);
	}

	private static void assertOpenedOnRoom(String what) {
		if (openedPage != ReactorControllerScreen.PAGE_ROOM) {
			throw new AssertionError("[GUITEST][MOD-619] " + what + " opened on tab " + openedPage
					+ " instead of the Room tab — a shell being built must open where it says what to fix");
		}
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

	/** The checklist column: from its first mark to the right edge, from the size line to the last row. */
	private static int checklistBandDelta(Path first, Path second) {
		return VisualStandSupport.differingPixelsInBand(first, second, windowBox,
				RoomTabPage.LIST_X - 2, ConsoleTabPage.CONTENT_RIGHT,
				RoomTabPage.INSIDE_Y, checklistBandHeight());
	}

	private static int checklistBandHeight() {
		return RoomTabPage.LIST_Y + 8 * RoomTabPage.LIST_ROW - RoomTabPage.INSIDE_Y;
	}

	private static void assertChecklistDiffers(String what, Path first, Path second, int noise) {
		int delta = checklistBandDelta(first, second);
		int required = Math.max(4 * noise, MIN_BAND_DELTA);
		LOG.info("[GUITEST][MOD-619] {}: delta={} px, noise={} px, required>{}", what, delta, noise, required);
		if (delta < required) {
			throw new AssertionError("[GUITEST][MOD-619] " + what + " changed only " + delta
					+ " px in the checklist (noise " + noise + " px, required > " + required + ") — the tab marked "
					+ "the same checks for two different scans, or none at all. Band: "
					+ VisualStandSupport.describeBand(RoomTabPage.LIST_X - 2, ConsoleTabPage.CONTENT_RIGHT,
							RoomTabPage.INSIDE_Y, checklistBandHeight())
					+ ". Compare " + first.getFileName() + " with " + second.getFileName() + ".");
		}
	}

	/** The Core tab's grid, inside its frame. */
	private static int gridBandDelta(Path first, Path second) {
		return VisualStandSupport.differingPixelsInBand(first, second, windowBox,
				ZoneTabPage.GRID_X, ZoneTabPage.GRID_X + ZoneTabPage.GRID_SIZE, ZoneTabPage.GRID_Y, ZoneTabPage.GRID_SIZE);
	}

	private static void assertGridDiffers(String what, Path first, Path second, int noise) {
		int delta = gridBandDelta(first, second);
		int required = Math.max(4 * noise, MIN_BAND_DELTA);
		LOG.info("[GUITEST][MOD-620] grid: {}: delta={} px, noise={} px, required>{}", what, delta, noise, required);
		if (delta < required) {
			throw new AssertionError("[GUITEST][MOD-620] grid: " + what + " changed only " + delta
					+ " px in the grid (noise " + noise + " px, required > " + required + ") - the cells drew the same "
					+ "picture for two different zones, or nothing at all. Band: "
					+ VisualStandSupport.describeBand(ZoneTabPage.GRID_X, ZoneTabPage.GRID_X + ZoneTabPage.GRID_SIZE,
							ZoneTabPage.GRID_Y, ZoneTabPage.GRID_SIZE)
					+ ". Compare " + first.getFileName() + " with " + second.getFileName() + ".");
		}
	}

	/** The Core tab's detail panel: what the selected stack holds. */
	private static int detailBandDelta(Path first, Path second) {
		return VisualStandSupport.differingPixelsInBand(first, second, windowBox,
				ZoneTabPage.DETAIL_X, ConsoleTabPage.CONTENT_RIGHT, ZoneTabPage.DETAIL_Y,
				ZoneTabPage.DETAIL_BOTTOM - ZoneTabPage.DETAIL_Y);
	}

	private static void assertDetailDiffers(String what, Path first, Path second, int noise) {
		int delta = detailBandDelta(first, second);
		int required = Math.max(4 * noise, MIN_BAND_DELTA);
		LOG.info("[GUITEST][MOD-620] {}: delta={} px, noise={} px, required>{}", what, delta, noise, required);
		if (delta < required) {
			throw new AssertionError("[GUITEST][MOD-620] " + what + " changed only " + delta
					+ " px in the detail panel (noise " + noise + " px, required > " + required + ") - the tab showed "
					+ "the same stack for two different picks, or nothing at all. Band: "
					+ VisualStandSupport.describeBand(ZoneTabPage.DETAIL_X, ConsoleTabPage.CONTENT_RIGHT,
							ZoneTabPage.DETAIL_Y, ZoneTabPage.DETAIL_BOTTOM - ZoneTabPage.DETAIL_Y)
					+ ". Compare " + first.getFileName() + " with " + second.getFileName() + ".");
		}
	}

	/**
	 * The measured interior: its size and the offset of its west and north edges from the controller.
	 * {@link #NONE} is a scan that measured nothing.
	 */
	private record RoomBox(int sizeX, int sizeY, int sizeZ, int west, int north, int... holes) {

		static final RoomBox NONE = new RoomBox(0, 0, 0, 0, 0);
		/** The 7×5×7 room with five holes as offsets from the controller, in the order the scan walks them. */
		static final RoomBox ROOM_7X5X7_HOLES = new RoomBox(7, 5, 7, 1, -3,
				0, 1, -2, 8, 1, -1, 8, 2, -1, 0, 2, 2, 4, 4, 0);
		/** Controller in the middle of the west wall of a 7×5×7 interior. */
		static final RoomBox ROOM_7X5X7 = new RoomBox(7, 5, 7, 1, -3);
		static final RoomBox ROOM_2X3X3 = new RoomBox(2, 3, 3, 1, -1);
		static final RoomBox ROOM_5X4X5 = new RoomBox(5, 4, 5, 1, -2);
		static final RoomBox ROOM_6X4X6 = new RoomBox(6, 4, 6, 1, -3);
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

		Reading status(ReactorRoomStatus v) {
			return new Reading(v, rods, depth, output, heat, water, rate, steam, idle, stored, meltdown, blast,
					instability, breachDx, breachDy, breachDz);
		}

		Reading breach(int dx, int dy, int dz) {
			return new Reading(status, rods, depth, output, heat, water, rate, steam, idle, stored, meltdown, blast,
					instability, dx, dy, dz);
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

		void applyTo(ReactorControllerMenu menu, RoomBox box) {
			menu.injectTestData(CAPACITY * stored / 100, CAPACITY, 0, 0);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_STATUS, status.ordinal());
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_BREACH_DX, breachDx);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_BREACH_DY, breachDy);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_BREACH_DZ, breachDz);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_SIZE_X, box.sizeX());
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_SIZE_Y, box.sizeY());
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_SIZE_Z, box.sizeZ());
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_BOX_WEST, box.west());
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_BOX_NORTH, box.north());
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_HOLE_COUNT, box.holes().length / 3);
			for (int i = 0; i < box.holes().length; i++) {
				menu.injectTestChannel(ReactorControllerBlockEntity.DATA_HOLE_FIRST + i, box.holes()[i]);
			}
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
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_ROOM_MIN_INNER, 3);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_ROOM_MAX_INNER, 12);
			menu.injectTestChannel(ReactorControllerBlockEntity.DATA_ROOM_MAX_GLASS, 30);
		}
	}
}
