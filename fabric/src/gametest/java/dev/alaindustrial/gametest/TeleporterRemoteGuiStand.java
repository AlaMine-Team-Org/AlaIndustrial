package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;

import dev.alaindustrial.Config;
import dev.alaindustrial.client.hud.TeleportNotice;
import dev.alaindustrial.client.screen.TeleporterRemoteScreen;
import dev.alaindustrial.client.screen.tabs.TabPage;
import dev.alaindustrial.client.screen.teleporter.RemoteMap;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.item.teleport.TeleportPoints;
import dev.alaindustrial.network.TeleportStationsPayload;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.teleporter.TeleportEngine;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The teleporter remote's tabs: «Stations» (MOD-628) — a list with every lamp a player can meet, three selections and a
 * refusal — and «Map» (MOD-629) — the approved mockup's frames A, B, C and I.
 *
 * <p><b>Built on the client's own menu</b>, like the reactor controller's stand. The menu reads the remote from the
 * player's hand, so a remote with bound points is put there, and the station snapshot a server would send is handed to
 * the menu directly — the snapshot, not a world of real stations, is what the tabs draw from, and its own building is
 * covered by the {@code TC-TELE-006} game tests. What this does not cover is the real open path:
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
	private static final TeleportStationsPayload SNAPSHOT = new TeleportStationsPayload(0, 0, 1_400, 500_000, 500, 5_000, List.of(
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

	// ── The «Map» frames: the mockup's stations, around wherever the stand's player stands ─────────────────────────────

	/** Where the mockup's player stood; a map station keeps its X/Z offset from here and its own Y. */
	private static final int MOCKUP_X = -90, MOCKUP_Z = 40;
	/** The mockup's pack: ×1.40. */
	private static final double LOAD = 1.40;
	/** Yaw that faces north-east, as the mockup's arrow does: forward is (+X, −Z). */
	private static final float FACING_NORTH_EAST = -135f;

	/** One station of the mockup. {@code energy} is {@code -1} for someone else's private one, {@code -2} for unknown. */
	private record MapStation(String name, int x, int y, int z, ResourceKey<Level> dim, long energy, boolean isPrivate,
			boolean chip, boolean formed, int agoSeconds) {
	}

	/** Frames A, B and I: seven stations, one of each kind. */
	private static final List<MapStation> MAP_MAIN = List.of(
			new MapStation("Teleporter 1", -104, 70, 58, Level.OVERWORLD, 410_000, true, false, true, 0),
			new MapStation("Mine shaft", 312, -41, -890, Level.OVERWORLD, 190_000, true, false, true, 720),
			new MapStation("Desert outpost", 1180, 72, -1320, Level.OVERWORLD, 15_000, false, true, true, 180),
			new MapStation("Reactor room", -60, 12, 210, Level.OVERWORLD, 480_000, true, true, false, 0),
			new MapStation("Alex's farm", 2400, 72, -3100, Level.OVERWORLD, -1, true, false, true, 3_600),
			new MapStation("Sky island", -2600, 190, 1800, Level.OVERWORLD, 500_000, false, true, true, 172_800),
			new MapStation("Old Nether portal base", 150, 40, -95, Level.NETHER, 220_000, true, false, true, 18_000));

	/** Frame C: a cluster of three, a station past the last ring, one the server has no record of, and the Nether. */
	private static final List<MapStation> MAP_EDGE = List.of(
			new MapStation("Teleporter 1", -104, 70, 58, Level.OVERWORLD, 410_000, true, false, true, 0),
			new MapStation("Well", 610, 70, -80, Level.OVERWORLD, 90_000, false, false, true, 1_200),
			new MapStation("Farm", 618, 68, -88, Level.OVERWORLD, 60_000, false, true, true, 1_200),
			new MapStation("Smithy", 604, 71, -95, Level.OVERWORLD, 8_000, false, false, true, 1_200),
			new MapStation("Far camp", 5900, 80, -4960, Level.OVERWORLD, 300_000, true, false, true, 259_200),
			new MapStation("Old outpost", -900, 64, 1900, Level.OVERWORLD, -2, false, false, false, 0),
			new MapStation("Sky island", -2600, 190, 1800, Level.OVERWORLD, 500_000, false, true, true, 172_800),
			new MapStation("Old Nether portal base", 150, 40, -95, Level.NETHER, 220_000, true, false, true, 18_000));

	private TeleporterRemoteGuiStand() {
	}

	/** Photographs the «Stations» tab: the full list, two chips a player needs to read, and a refusal. */
	public static void shootStations(ClientGameTestContext context) {
		context.getInput().moveCursor(-CURSOR_SHIFT, 0);
		try {
			shoot(context, "stations_ready",
					"Stations tab with seven bound stations: two tabs below the panel's rounded top corner, Stations open, "
							+ "READY chip in green, 'Bound stations 7 / 16', lamps green, green, red, amber, grey, hollow and "
							+ "grey, the first row filled lilac with a lighter 1 px frame and no stripe down its left edge, "
							+ "coordinates right-aligned, 'Nether' on the last row, 'Free slots: 9' and the shift-click hint "
							+ "below, the name field reading 'Home base', Delete greyed beside the shut padlock and the hint "
							+ "about the padlock beside it — no Teleport or Random on this tab",
					POINTS, SNAPSHOT, TeleporterRemoteScreen::stationsPage, HOME, false);
			shoot(context, "stations_no_power",
					"The same list with Desert outpost picked: the NO POWER chip in red and the name field reading "
							+ "'Desert outpost'",
					POINTS, SNAPSHOT, TeleporterRemoteScreen::stationsPage, DESERT, false);
			shoot(context, "stations_private",
					"Alex's farm picked: the PRIVATE chip in red and a grey lamp — nothing more of someone else's private "
							+ "station is shown",
					POINTS, SNAPSHOT, TeleporterRemoteScreen::stationsPage, ALEX, false);
			shoot(context, "stations_refused",
					"Reactor room picked and its refusal just received: the NO CAPSULE chip in amber and a dark red band "
							+ "across the bottom of the list carrying the whole reason, wrapped rather than cut",
					POINTS, SNAPSHOT, TeleporterRemoteScreen::stationsPage, REACTOR_ROOM, true);
		} finally {
			context.getInput().moveCursor(CURSOR_SHIFT, 0);
			cleanUp(context);
		}
	}

	/**
	 * Photographs the «Map» tab as the approved mockup's frames A, B, C and I. The frames turn the player and move the
	 * cursor; both are put back afterwards, or every stand shot after this one would inherit them.
	 */
	public static void shootMap(ClientGameTestContext context) {
		double[] saved = new double[3];
		context.runOnClient(mc -> {
			saved[0] = mc.mouseHandler.xpos();
			saved[1] = mc.mouseHandler.ypos();
			saved[2] = mc.player.getYRot();
		});
		try {
			shootMapFrame(context, "map_ready",
					"Frame A. Map tab open: dark radar with a grid, rings labelled 100, 1k and 5k, N on top, the white "
							+ "player arrow facing north-east; lamps for the stations, Alex's farm a grey plate with a "
							+ "padlock, a '+1 in the Nether' plate bottom-left; Mine shaft in a yellow frame. The card reads "
							+ "Mine shaft, its distance, NE and Y -41, Cost rounded up to a hundred, Charge with a purple bar "
							+ "and an amber tail, Access Private, Chip None, Capsule OK in green, Load ×1.40 and 'updated 12 "
							+ "min ago'. A green tick and 'Ready. The jump takes up to … EU from Mine shaft.' in the status "
							+ "line; Teleport and Random live",
					MAP_MAIN, 1, false);
			shootMapFrame(context, "map_no_power",
					"Frame B. Desert outpost picked: NO POWER chip; the radar dotted purple in a ring between 500 and 5 000 "
							+ "blocks because this station has a chip; Cost, Charge and its EU in red, a red stub on the "
							+ "bar, Chip Fitted, 'Random zone ring' legend; a red cross and the whole out-of-power reason in "
							+ "the status line; Teleport greyed",
					MAP_MAIN, 2, false);
			shootMapFrame(context, "map_edge_cases",
					"Frame C. Old outpost picked, a hollow '?' plate in a yellow frame: UNKNOWN chip, the card with an "
							+ "estimated cost '≈… EU', question marks for charge, access, chip and capsule, 'Status unknown' "
							+ "and its explanation, a pause sign and the unknown-status line, Teleport live. A plate with a "
							+ "3 for the village of three, its tooltip listing Well, Farm and Smithy with distances and "
							+ "'Click: next'; a triangle on the rim for Far camp; '+1 in the Nether'",
					MAP_EDGE, 5, true);
			shootMapFrame(context, "map_private",
					"Frame I. Alex's farm picked, a grey padlock plate in a yellow frame: PRIVATE chip; the card shows "
							+ "only the name, coordinates, 'Access Private' in red, 'Private station · details hidden', the "
							+ "explanation and a large padlock; no zone ring; a red cross and 'Alex's farm is private — only "
							+ "its owner can jump there.'; Teleport greyed",
					MAP_MAIN, 4, false);
		} finally {
			context.getInput().setCursorPos(saved[0], saved[1]);
			context.runOnClient(mc -> {
				mc.player.setYRot((float) saved[2]);
				mc.player.yRotO = (float) saved[2];
			});
			cleanUp(context);
		}
	}

	private static void cleanUp(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			TeleportNotice.clear();
			mc.player.getInventory().setItem(0, ItemStack.EMPTY);
		});
	}

	private static Path shootMapFrame(ClientGameTestContext context, String state, String checks, List<MapStation> map,
			int row, boolean hoverCluster) {
		List<TeleportPoint> points = new ArrayList<>();
		List<TeleportStationsPayload.Station> stations = new ArrayList<>();
		context.runOnClient(mc -> {
			mc.player.setYRot(FACING_NORTH_EAST);
			mc.player.yRotO = FACING_NORTH_EAST;
			BlockPos at = mc.player.blockPosition();
			for (MapStation s : map) {
				BlockPos pos = new BlockPos(at.getX() + s.x() - MOCKUP_X, s.y(), at.getZ() + s.z() - MOCKUP_Z);
				points.add(new TeleportPoint(s.dim(), pos, s.name()));
				stations.add(station(s, at, pos));
			}
		});
		// Away from the panel unless the frame is about a hover.
		context.getInput().setCursorPos(10, 10);
		TeleportStationsPayload snapshot = new TeleportStationsPayload(0, 0, (int) Math.round(LOAD * 1000),
				Config.teleporterBuffer, Config.teleporterRtpMinRadius, Config.teleporterRtpRadius, stations);
		return shoot(context, state, checks, points, snapshot, TeleporterRemoteScreen::mapPage, row, false, hoverCluster);
	}

	/** What the server would send for a mockup station, priced the way {@code TeleportEngine#computeCost} prices it. */
	private static TeleportStationsPayload.Station station(MapStation s, BlockPos player, BlockPos pos) {
		boolean here = s.dim() == Level.OVERWORLD;
		long cost = here ? Math.round((Config.teleporterBaseCost + Math.sqrt(player.distSqr(pos))
				* Config.teleporterCostPerBlock) * LOAD) : 0;
		int flags = here ? TeleportStationsPayload.SAME_DIMENSION : 0;
		if (s.energy() == -2) {
			return new TeleportStationsPayload.Station(flags, TeleportEngine.Denial.OK, 0, cost, 0);
		}
		flags |= KNOWN | (s.isPrivate() ? TeleportStationsPayload.PRIVATE : 0) | (s.agoSeconds() == 0 ? TeleportStationsPayload.LIVE : 0);
		if (s.energy() == -1) {
			return new TeleportStationsPayload.Station(flags | TeleportStationsPayload.HIDDEN,
					here ? TeleportEngine.Denial.NO_ACCESS : TeleportEngine.Denial.CROSS_DIM, 0, 0, s.agoSeconds());
		}
		flags |= (s.formed() ? TeleportStationsPayload.FORMED : 0) | (s.chip() ? TeleportStationsPayload.CHIP : 0);
		TeleportEngine.Denial denial = !here ? TeleportEngine.Denial.CROSS_DIM
				: !s.formed() ? TeleportEngine.Denial.NOT_FORMED
				: s.energy() < cost ? TeleportEngine.Denial.NOT_ENOUGH_EU : TeleportEngine.Denial.OK;
		return new TeleportStationsPayload.Station(flags, denial, s.energy(), cost, s.agoSeconds());
	}

	private static Path shoot(ClientGameTestContext context, String state, String checks, List<TeleportPoint> points,
			TeleportStationsPayload snapshot, Function<TeleporterRemoteScreen, TabPage> page, int row, boolean refused) {
		return shoot(context, state, checks, points, snapshot, page, row, refused, false);
	}

	private static Path shoot(ClientGameTestContext context, String state, String checks, List<TeleportPoint> points,
			TeleportStationsPayload snapshot, Function<TeleporterRemoteScreen, TabPage> page, int row, boolean refused,
			boolean hoverCluster) {
		LOG.info("[GUITEST][MOD-629] opening teleporter_remote/{}", state);
		context.runOnClient(mc -> {
			ItemStack remote = new ItemStack(ModContent.TELEPORTER_REMOTE.get());
			remote.set(ModDataComponents.TELEPORTER_POINTS.get(), new TeleportPoints(points));
			mc.player.getInventory().setSelectedSlot(0);
			mc.player.getInventory().setItem(0, remote);
			MenuScreens.create(ModContent.TELEPORTER_REMOTE_MENU.get(), mc, 0,
					Component.translatable("item.alaindustrial.teleporter_remote"));
			// No silent fallback: a frame of an empty remote differs from the others just as readily as a right one.
			if (!(mc.gui.screen() instanceof TeleporterRemoteScreen screen)) {
				throw new AssertionError("[GUITEST][MOD-629] MenuScreens.create did not open the remote screen for "
						+ state);
			}
			screen.getMenu().acceptStations(snapshot);
		});
		awaitMenuScreen(context);
		context.waitTicks(SETTLE_TICKS);
		double[] hover = new double[2];
		context.runOnClient(mc -> {
			if (!(mc.gui.screen() instanceof TeleporterRemoteScreen screen)) {
				throw new AssertionError("[GUITEST][MOD-629] the remote screen closed before " + state);
			}
			if (screen.getMenu().points().size() != points.size()) {
				throw new AssertionError("[GUITEST][MOD-629] the menu reads " + screen.getMenu().points().size()
						+ " points, the remote in hand holds " + points.size());
			}
			screen.showPage(page.apply(screen));
			screen.selectStation(row);
			if (refused) {
				TeleportNotice.receive(TeleportEngine.Denial.NOT_FORMED.message());
			}
			if (hoverCluster) {
				hover[0] = Double.NaN;
				List<RemoteMap.Placed> placed = new ArrayList<>();
				for (int i = 0; i < points.size(); i++) {
					TeleportPoint point = points.get(i);
					if (point.dim() == mc.level.dimension()) {
						placed.add(new RemoteMap.Placed(i, point.pos().getX() + 0.5 - mc.player.getX(),
								point.pos().getZ() + 0.5 - mc.player.getZ()));
					}
				}
				for (RemoteMap.Marker marker : RemoteMap.markers(placed)) {
					if (marker.isCluster()) {
						double scale = mc.getWindow().getGuiScale();
						hover[0] = (screen.left() + 8 + marker.pixelX() + 0.5) * scale;
						hover[1] = (screen.top() + 20 + marker.pixelY() + 0.5) * scale;
					}
				}
				if (Double.isNaN(hover[0])) {
					throw new AssertionError("[GUITEST][MOD-629] frame " + state + " has no cluster to hover");
				}
			}
		});
		if (hoverCluster) {
			context.getInput().setCursorPos(hover[0], hover[1]);
		}
		context.waitTicks(1);
		return ShotRecorder.captureScreen("teleporter_remote", state, ShotRecorder.rules("R-GUI-01", "R-GUI-03"), checks);
	}
}
