package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;

import dev.alaindustrial.client.hud.TeleportNotice;
import dev.alaindustrial.client.screen.TeleporterRemoteScreen;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.item.teleport.TeleportPoints;
import dev.alaindustrial.network.TeleportStationsPayload;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.teleporter.TeleportEngine;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The teleporter remote's «Stations» tab (MOD-628): a list with every lamp a player can meet, three selections and a
 * refusal.
 *
 * <p><b>Built on the client's own menu</b>, like the reactor controller's stand. The menu reads the remote from the
 * player's hand, so a remote with seven bound points is put there, and the station snapshot a server would send is
 * handed to the menu directly — the snapshot, not a world of real stations, is what the tab draws from, and its own
 * building is covered by the {@code TC-TELE-006} game tests. What this does not cover is the real open path:
 * {@code ScreensClientGameTest} opens the remote by right-click.
 */
@SuppressWarnings("UnstableApiUsage")
public final class TeleporterRemoteGuiStand {

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

	/** The page auto-selects its first station on the first tick after opening; a frame must come after that. */
	private static final int SETTLE_TICKS = 3;

	/** The cursor rests mid-window, over the list; moved left of the panel so no row is tinted by hover. */
	private static final double CURSOR_SHIFT = 500;

	private static final int HOME = 0;
	private static final int DESERT = 2;
	private static final int REACTOR_ROOM = 3;
	private static final int ALEX = 4;

	private static final List<TeleportPoint> POINTS = List.of(
			new TeleportPoint(Level.OVERWORLD, new BlockPos(-104, 70, 58), "Home base"),
			new TeleportPoint(Level.OVERWORLD, new BlockPos(312, -41, -890), "Mine shaft"),
			new TeleportPoint(Level.OVERWORLD, new BlockPos(1180, 72, -1320), "Desert outpost"),
			new TeleportPoint(Level.OVERWORLD, new BlockPos(-60, 12, 210), "Reactor room"),
			new TeleportPoint(Level.OVERWORLD, new BlockPos(2400, 72, -3100), "Alex's farm"),
			new TeleportPoint(Level.OVERWORLD, new BlockPos(-2600, 190, 1800), "Sky island"),
			new TeleportPoint(Level.NETHER, new BlockPos(40, 64, -90), "Old portal base"));

	private static final int OW = TeleportStationsPayload.SAME_DIMENSION;
	private static final int KNOWN = TeleportStationsPayload.KNOWN;

	/** What a server would say about those seven, in the same order: one of every lamp the tab draws. */
	private static final TeleportStationsPayload SNAPSHOT = new TeleportStationsPayload(0, 0, List.of(
			new TeleportStationsPayload.Station(KNOWN | OW | TeleportStationsPayload.LIVE | TeleportStationsPayload.FORMED,
					TeleportEngine.Denial.OK, 412_000, 7_160, 0),
			new TeleportStationsPayload.Station(KNOWN | OW | TeleportStationsPayload.FORMED | TeleportStationsPayload.CHIP,
					TeleportEngine.Denial.OK, 190_000, 14_135, 720),
			new TeleportStationsPayload.Station(KNOWN | OW | TeleportStationsPayload.FORMED,
					TeleportEngine.Denial.NOT_ENOUGH_EU, 15_000, 20_100, 180),
			new TeleportStationsPayload.Station(KNOWN | OW, TeleportEngine.Denial.NOT_FORMED, 88_000, 3_900, 60),
			new TeleportStationsPayload.Station(KNOWN | OW | TeleportStationsPayload.HIDDEN | TeleportStationsPayload.PRIVATE,
					TeleportEngine.Denial.NO_ACCESS, 0, 0, 3_600),
			new TeleportStationsPayload.Station(OW, TeleportEngine.Denial.OK, 0, 0, 0),
			new TeleportStationsPayload.Station(KNOWN | TeleportStationsPayload.FORMED,
					TeleportEngine.Denial.CROSS_DIM, 240_000, 0, 5_400)));

	private TeleporterRemoteGuiStand() {
	}

	/** Photographs the «Stations» tab: the full list, two chips a player needs to read, and a refusal. */
	public static void shootStations(ClientGameTestContext context) {
		context.getInput().moveCursor(-CURSOR_SHIFT, 0);
		try {
			shoot(context, "stations_ready",
					"Stations tab with seven bound stations: one tab below the panel's rounded top corner, READY chip in "
							+ "green, 'Bound stations 7 / 16', lamps green, green, red, amber, grey, hollow and grey, the first "
							+ "row filled lilac with a lighter 1 px frame and no stripe down its left edge, coordinates "
							+ "right-aligned, 'Nether' on the last row, 'Free slots: 9' and the shift-click hint below, the "
							+ "name field reading 'Home base', Delete greyed beside the shut padlock, Teleport and Random live",
					HOME, false);
			shoot(context, "stations_no_power",
					"The same list with Desert outpost picked: the NO POWER chip in red and the name field reading "
							+ "'Desert outpost'",
					DESERT, false);
			shoot(context, "stations_private",
					"Alex's farm picked: the PRIVATE chip in red and a grey lamp — nothing more of someone else's private "
							+ "station is shown",
					ALEX, false);
			shoot(context, "stations_refused",
					"Reactor room picked and its refusal just received: the NO CAPSULE chip in amber and a dark red band "
							+ "across the bottom of the list carrying the whole reason, wrapped rather than cut",
					REACTOR_ROOM, true);
		} finally {
			context.getInput().moveCursor(CURSOR_SHIFT, 0);
			context.runOnClient(mc -> {
				TeleportNotice.clear();
				mc.player.getInventory().setItem(0, ItemStack.EMPTY);
			});
		}
	}

	private static Path shoot(ClientGameTestContext context, String state, String checks, int row, boolean refused) {
		LOG.info("[GUITEST][MOD-628] opening teleporter_remote/{}", state);
		context.runOnClient(mc -> {
			ItemStack remote = new ItemStack(ModContent.TELEPORTER_REMOTE.get());
			remote.set(ModDataComponents.TELEPORTER_POINTS.get(), new TeleportPoints(POINTS));
			mc.player.getInventory().setSelectedSlot(0);
			mc.player.getInventory().setItem(0, remote);
			MenuScreens.create(ModContent.TELEPORTER_REMOTE_MENU.get(), mc, 0,
					Component.translatable("item.alaindustrial.teleporter_remote"));
			// No silent fallback: a frame of an empty remote differs from the others just as readily as a right one.
			if (!(mc.gui.screen() instanceof TeleporterRemoteScreen screen)) {
				throw new AssertionError("[GUITEST][MOD-628] MenuScreens.create did not open the remote screen for "
						+ state);
			}
			screen.getMenu().acceptStations(SNAPSHOT);
		});
		awaitMenuScreen(context);
		context.waitTicks(SETTLE_TICKS);
		context.runOnClient(mc -> {
			if (!(mc.gui.screen() instanceof TeleporterRemoteScreen screen)) {
				throw new AssertionError("[GUITEST][MOD-628] the remote screen closed before " + state);
			}
			if (screen.getMenu().points().size() != POINTS.size()) {
				throw new AssertionError("[GUITEST][MOD-628] the menu reads " + screen.getMenu().points().size()
						+ " points, the remote in hand holds " + POINTS.size());
			}
			screen.stationsPage().select(row);
			if (refused) {
				TeleportNotice.receive(TeleportEngine.Denial.NOT_FORMED.message());
			}
		});
		context.waitTicks(1);
		return ShotRecorder.captureScreen("teleporter_remote", state, ShotRecorder.rules("R-GUI-01", "R-GUI-03"), checks);
	}
}
