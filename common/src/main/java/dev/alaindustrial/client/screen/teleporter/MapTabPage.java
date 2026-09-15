package dev.alaindustrial.client.screen.teleporter;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.ReadoutFormat;
import dev.alaindustrial.client.hud.TeleportNotice;
import dev.alaindustrial.client.screen.TeleporterRemoteScreen;
import dev.alaindustrial.client.screen.tabs.PageText;
import dev.alaindustrial.client.screen.tabs.TabPage;
import dev.alaindustrial.client.screen.teleporter.StationsTabPage.Readiness;
import dev.alaindustrial.core.structure.ReactorLog;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.item.teleport.TeleportPoints;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.network.TeleportStationsPayload;
import dev.alaindustrial.teleporter.TeleportEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The remote's «Map» tab (MOD-629): a radar of the bound stations around the player, a card for the selected one, and a
 * status line that says whether the jump will work and why not.
 *
 * <p>Every coordinate, colour and pixel pattern is the approved mockup's (variant 6, frames A, B, C and I), read from
 * its generator. The geometry itself — scale, clusters, rim arrows, clicks, the wheel's order, the price rounded up —
 * lives in {@link RemoteMap}, where L1 tests it; this class only draws it and passes clicks on.
 *
 * <p>Nothing here decides anything: readiness comes from the server's {@link TeleportStationsPayload}, a click sends an
 * index, and the server checks the real station on the jump.
 */
public final class MapTabPage implements TabPage {

	private static final int RADAR_X = 8, RADAR_Y = 20, SIZE = RemoteMap.SIZE;
	private static final int CARD_X = 134, CARD_Y = 20, CARD_W = 94, CARD_H = 122;
	private static final int CARD_TEXT_X = 138, CARD_RIGHT = 224, CARD_TEXT_W = 86;
	private static final int STATUS_X = 8, STATUS_Y = 145, STATUS_W = 220, STATUS_H = 18;
	private static final int BTN_Y = 166, BTN_H = 20;
	private static final int TELEPORT_X = 8, TELEPORT_W = 108;
	private static final int RTP_X = 120, RTP_W = 108;

	private static final int PLATE_EDGE = 0xFF111316;
	private static final int BOX_BACK = 0xFF2A2D33;
	private static final int GRID = 0xFF30343B;
	private static final int RING = 0xFF566070;
	private static final int ZONE_DOT = 0xFF6A52A0;
	private static final int ZONE_LEGEND = 0xFF7A5FB0;
	private static final int ON_DARK = 0xFFD7DBE0;
	private static final int ON_DARK_DIM = 0xFF9AA3AB;
	private static final int SELECTED = 0xFFF2C94C;
	private static final int CLUSTER_CORE = 0xFF1C1F24;
	private static final int UNKNOWN_RIM = 0xFF8B939B;
	private static final int LOCK_ON_PLATE = 0xFFE6E9EC;
	private static final int LOCK_ON_CARD = 0xFF8B8B8B;
	private static final int PLATE_RIM = 0xFF566070;
	private static final int PLATE_BACK = 0xFF3A3F47;
	private static final int CARD_EDGE = 0xFF5A5A5A;
	private static final int CARD_BACK = 0xFFC6C6C6;

	private static final int TEXT = 0xFF3F3F3F;
	private static final int TEXT_DIM = 0xFF6B6B6B;
	private static final int INK_RED = 0xFFAA2A1A;
	private static final int INK_GREEN = 0xFF2F6B33;
	private static final int INK_AMBER = 0xFF8A5A00;

	private static final int FILL_GREEN = 0xFF4E9E52;
	private static final int FILL_AMBER = 0xFFD9A33A;
	private static final int FILL_RED = 0xFFD63A2A;
	private static final int FILL_IDLE = 0xFF6B7178;

	/** The charge bar's fill is the station screen's own sprite, cut to the bar's four middle rows. */
	private static final Identifier STATION_TEXTURE = Industrialization.id("textures/gui/container/teleporter_station.png");
	private static final int FILL_U = 0, FILL_V = 200, FILL_SRC_W = 158, BAR_H = 4;

	private static final int[] RING_BLOCKS = {100, 1000, 5000};
	private static final String[] RING_KEYS = {"100", "1k", "5k"};
	private static final List<List<int[]>> RING_PIXELS = List.of(RemoteMap.ringPixels(RemoteMap.radius(100)),
			RemoteMap.ringPixels(RemoteMap.radius(1000)), RemoteMap.ringPixels(RemoteMap.radius(5000)));

	private static final String[] LOCK = {".###.", ".#.#.", "#####", "##.##", "#####"};
	private static final String[] GLYPH_TICK = {
			"........", "......#.", ".....##.", "#...##..", "##.##...", ".###....", "..#.....", "........"};
	private static final String[] GLYPH_CROSS = {
			"........", ".##..##.", "..####..", "...##...", "..####..", ".##..##.", "........", "........"};
	private static final String[] GLYPH_PAUSE = {
			"........", "..#..#..", "..#..#..", "..#..#..", "..#..#..", "..#..#..", "........", "........"};

	private static final String P = "gui.alaindustrial.teleporter_remote.";
	private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	/**
	 * A marker's tooltip goes up and to the left of the cursor, as the mockup draws it: the radar's empty side, not the
	 * card beside it — vanilla's place, down and to the right, covered the very card the player was about to read.
	 */
	private static final ClientTooltipPositioner LEFT_OF_CURSOR = (screenWidth, screenHeight, mouseX, mouseY, width,
			height) -> new Vector2i(Math.max(4, Math.min(mouseX - 8 - width, screenWidth - width - 4)),
					Math.max(4, Math.min(mouseY - height - 4, screenHeight - height - 4)));

	/** How the status line and a marker read: the reactor screen's four tones. */
	private enum Tone {
		GOOD(0xFF7FD08A, FILL_GREEN), WARN(0xFFE8B04A, FILL_AMBER), ALARM(0xFFFF6B5A, FILL_RED), IDLE(0xFFB9C0C7, FILL_IDLE);

		final int ink;
		final int fill;

		Tone(int ink, int fill) {
			this.ink = ink;
			this.fill = fill;
		}
	}

	/** What the radar shows this frame: markers, the stations in other dimensions, and the wheel's order. */
	private record Layout(List<RemoteMap.Marker> markers, List<Integer> elsewhere, List<Integer> order) {
	}

	private final TeleporterRemoteScreen screen;
	private final ItemStack icon = new ItemStack(Items.MAP);

	private @Nullable Button teleportButton;
	private @Nullable Button rtpButton;
	private boolean shown = true;
	/** The dotted zone depends only on its two radii, so its pixels are worked out once per pair. */
	private int zoneMin = -1, zoneMax = -1;
	private List<int[]> zoneDots = List.of();

	public MapTabPage(TeleporterRemoteScreen screen) {
		this.screen = screen;
	}

	@Override
	public Component title() {
		return Component.translatable(P + "tab.map");
	}

	@Override
	public ItemStack icon() {
		return icon;
	}

	@Override
	public int badgeColour() {
		return 0;
	}

	@Override
	public void init() {
		int x = screen.left();
		int y = screen.top();
		teleportButton = screen.addPageWidget(Button.builder(
				Component.translatable("gui.alaindustrial.teleporter.teleport"),
				b -> press(TeleporterRemoteMenu.Action.TELEPORT))
				.bounds(x + TELEPORT_X, y + BTN_Y, TELEPORT_W, BTN_H).build());
		// Until the «Random» tab ships (MOD-630) this still jumps at once; its price is flat, so the tooltip can tell it.
		rtpButton = screen.addPageWidget(Button.builder(
				Component.translatable("gui.alaindustrial.teleporter.rtp"),
				b -> press(TeleporterRemoteMenu.Action.RTP))
				.tooltip(Tooltip.create(Component.translatable("gui.alaindustrial.teleporter.rtp.tooltip",
						Config.teleporterRtpCost, Config.teleporterRtpRadius)))
				.bounds(x + RTP_X, y + BTN_Y, RTP_W, BTN_H).build());
		setShown(shown);
	}

	@Override
	public void setShown(boolean shown) {
		this.shown = shown;
		if (teleportButton != null) {
			teleportButton.visible = shown;
		}
		if (rtpButton != null) {
			rtpButton.visible = shown;
		}
	}

	@Override
	public void tick() {
		TeleporterRemoteMenu menu = screen.getMenu();
		int selected = menu.getSelected();
		Readiness readiness = selected >= 0 ? StationsTabPage.readiness(StationsTabPage.stationAt(menu.stations(), selected))
				: Readiness.UNKNOWN;
		if (teleportButton != null) {
			// Ready, or unknown — the server checks an unknown station on the spot. Someone else's private one: never.
			teleportButton.active = selected >= 0 && menu.stations() != null
					&& (readiness == Readiness.READY || readiness == Readiness.UNKNOWN);
		}
		if (rtpButton != null) {
			rtpButton.active = selected >= 0;
		}
	}

	// ── Drawing ─────────────────────────────────────────────────────────────────────────────────────

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		TeleporterRemoteMenu menu = screen.getMenu();
		TeleportPoints points = menu.points();
		TeleportStationsPayload snapshot = menu.stations();
		Font font = screen.font();
		LocalPlayer player = screen.minecraftClient().player;
		ClientLevel level = screen.minecraftClient().level;
		int x = screen.left();
		int y = screen.top();
		int selected = menu.getSelected();

		drawRadar(graphics, font, x + RADAR_X, y + RADAR_Y, points, snapshot, player, level, selected);
		drawCard(graphics, font, x, y, points, snapshot, player, level, selected);
		drawStatus(graphics, font, x, y, points, snapshot, selected);
	}

	private void drawRadar(GuiGraphicsExtractor graphics, Font font, int x0, int y0, TeleportPoints points,
			@Nullable TeleportStationsPayload snapshot, @Nullable LocalPlayer player, @Nullable ClientLevel level,
			int selected) {
		graphics.fill(x0, y0, x0 + SIZE, y0 + SIZE, PLATE_EDGE);
		graphics.fill(x0 + 1, y0 + 1, x0 + SIZE - 1, y0 + SIZE - 1, BOX_BACK);
		for (int k = 1; k < 8; k++) {
			int line = k * SIZE / 8;
			graphics.fill(x0 + line, y0 + 1, x0 + line + 1, y0 + SIZE - 1, GRID);
			graphics.fill(x0 + 1, y0 + line, x0 + SIZE - 1, y0 + line + 1, GRID);
		}
		if (player == null || level == null) {
			return;
		}

		TeleportStationsPayload.Station chosen = selected >= 0 ? StationsTabPage.stationAt(snapshot, selected) : null;
		// The random-jump zone, 500–5 000 blocks from the player: only for a station that can pay for one, and only
		// where random jumps work. A hidden station's chip is withheld, so it never shows a ring.
		if (snapshot != null && chosen != null && showsZone(chosen, level)) {
			for (int[] dot : zoneDots(x0 - screen.left(), y0 - screen.top(), snapshot)) {
				int px = screen.left() + dot[0];
				int py = screen.top() + dot[1];
				graphics.fill(px, py, px + 1, py + 1, ZONE_DOT);
			}
		}
		for (int i = 0; i < RING_BLOCKS.length; i++) {
			for (int[] p : RING_PIXELS.get(i)) {
				graphics.fill(x0 + p[0], y0 + p[1], x0 + p[0] + 1, y0 + p[1] + 1, RING);
			}
			float r = (float) RemoteMap.radius(RING_BLOCKS[i]);
			text(graphics, font, Component.translatable(P + "map.ring." + RING_KEYS[i]),
					x0 + (float) RemoteMap.CENTRE + r * 0.72f + 1, y0 + (float) RemoteMap.CENTRE + r * 0.72f - 1, 0.5f,
					ON_DARK_DIM);
		}
		Component north = Component.translatable(P + "map.north");
		text(graphics, font, north, x0 + (float) RemoteMap.CENTRE - 10 + (20 - font.width(north) * 0.75f) / 2, y0 + 2,
				0.75f, ON_DARK_DIM);

		Layout layout = layout(points, player, level);
		for (RemoteMap.Marker marker : layout.markers()) {
			drawMarker(graphics, font, x0, y0, marker, snapshot, selected);
		}
		double[] facing = RemoteMap.facing(player.getYRot());
		RemoteMap.ArrowPixels arrow = RemoteMap.playerArrow(facing[0], facing[1]);
		pixels(graphics, x0, y0, arrow.edge(), PLATE_EDGE);
		pixels(graphics, x0, y0, arrow.body(), 0xFFFFFFFF);

		if (!layout.elsewhere().isEmpty()) {
			Component label = elsewhereLabel(points, layout.elsewhere());
			int w = Math.round(font.width(label) * 0.75f) + 6;
			graphics.fill(x0 + 2, y0 + SIZE - 12, x0 + 2 + w, y0 + SIZE - 2, PLATE_RIM);
			graphics.fill(x0 + 3, y0 + SIZE - 11, x0 + 1 + w, y0 + SIZE - 3, PLATE_BACK);
			text(graphics, font, label, x0 + 5, y0 + SIZE - 10, 0.75f, ON_DARK);
		}
	}

	private void drawMarker(GuiGraphicsExtractor graphics, Font font, int x0, int y0, RemoteMap.Marker marker,
			@Nullable TeleportStationsPayload snapshot, int selected) {
		int mx = x0 + marker.pixelX();
		int my = y0 + marker.pixelY();
		boolean plate;
		if (marker.far()) {
			Readiness readiness = StationsTabPage.readiness(StationsTabPage.stationAt(snapshot, marker.members().get(0)));
			RemoteMap.ArrowPixels arrow = RemoteMap.rimArrow(marker);
			pixels(graphics, x0, y0, arrow.edge(), PLATE_EDGE);
			pixels(graphics, x0, y0, arrow.body(), markerTone(readiness).fill);
			plate = false;
		} else if (marker.isCluster()) {
			Tone best = Tone.IDLE;
			for (int index : marker.members()) {
				Tone tone = markerTone(StationsTabPage.readiness(StationsTabPage.stationAt(snapshot, index)));
				if (tone.ordinal() < best.ordinal()) {
					best = tone;
				}
			}
			graphics.fill(mx - 4, my - 4, mx + 5, my + 5, PLATE_EDGE);
			graphics.fill(mx - 3, my - 3, mx + 4, my + 4, best.fill);
			graphics.fill(mx - 2, my - 2, mx + 3, my + 3, CLUSTER_CORE);
			Component count = Component.literal(Integer.toString(marker.members().size()));
			text(graphics, font, count, mx - font.width(count) * 0.25f + 0.5f, my - 1.5f, 0.5f, 0xFFFFFFFF);
			plate = true;
		} else {
			Readiness readiness = StationsTabPage.readiness(StationsTabPage.stationAt(snapshot, marker.members().get(0)));
			if (readiness == Readiness.UNKNOWN) {
				graphics.fill(mx - 4, my - 4, mx + 5, my + 5, PLATE_EDGE);
				graphics.fill(mx - 3, my - 3, mx + 4, my + 4, UNKNOWN_RIM);
				graphics.fill(mx - 2, my - 2, mx + 3, my + 3, BOX_BACK);
				text(graphics, font, Component.literal("?"), mx - 1.25f, my - 2.25f, 0.75f, ON_DARK);
				plate = true;
			} else if (readiness == Readiness.HIDDEN) {
				// Always grey: the client does not know whether someone else's private station is ready.
				graphics.fill(mx - 4, my - 4, mx + 5, my + 5, PLATE_EDGE);
				graphics.fill(mx - 3, my - 3, mx + 4, my + 4, FILL_IDLE);
				glyph(graphics, mx - 2, my - 2, LOCK, LOCK_ON_PLATE, 1);
				plate = true;
			} else {
				graphics.fill(mx - 2, my - 2, mx + 3, my + 3, PLATE_EDGE);
				graphics.fill(mx - 1, my - 1, mx + 2, my + 2, verdictTone(readiness).fill);
				plate = false;
			}
		}
		if (marker.members().contains(selected)) {
			int half = plate ? 6 : 5;
			int box = 2 * half - 1;
			int left = mx - half + 1;
			int top = my - half + 1;
			graphics.fill(left, top, left + box, top + 1, SELECTED);
			graphics.fill(left, my + half - 1, left + box, my + half, SELECTED);
			graphics.fill(left, top, left + 1, top + box, SELECTED);
			graphics.fill(mx + half - 1, top, mx + half, top + box, SELECTED);
		}
	}

	private void drawCard(GuiGraphicsExtractor graphics, Font font, int x, int y, TeleportPoints points,
			@Nullable TeleportStationsPayload snapshot, @Nullable LocalPlayer player, @Nullable ClientLevel level,
			int selected) {
		graphics.fill(x + CARD_X, y + CARD_Y, x + CARD_X + CARD_W, y + CARD_Y + CARD_H, CARD_EDGE);
		graphics.fill(x + CARD_X + 1, y + CARD_Y + 1, x + CARD_X + CARD_W - 1, y + CARD_Y + CARD_H - 1, CARD_BACK);
		TeleportPoint point = selected >= 0 ? points.get(selected) : null;
		if (point == null || player == null || level == null) {
			return;
		}
		TeleportStationsPayload.Station station = StationsTabPage.stationAt(snapshot, selected);
		Readiness readiness = StationsTabPage.readiness(station);
		boolean sameDimension = point.dim() == level.dimension();
		float cx = x + CARD_TEXT_X;
		float right = x + CARD_RIGHT;

		fit(graphics, font, point.displayName(), cx, y + 23, CARD_TEXT_W, 1.0f, TEXT);
		Component second;
		if (!sameDimension) {
			second = Component.translatable(P + (point.dim() == Level.NETHER ? "card.in_nether"
					: point.dim() == Level.END ? "card.in_end" : "card.in_other"));
		} else if (readiness == Readiness.HIDDEN) {
			// Coordinates rather than a distance: that is what the player bound, and all they may still see.
			second = Component.translatable(P + "card.coords", point.pos().getX(), point.pos().getY(), point.pos().getZ());
		} else {
			double distance = Math.sqrt(player.blockPosition().distSqr(point.pos()));
			int direction = RemoteMap.compassPoint(point.pos().getX() - player.blockPosition().getX(),
					point.pos().getZ() - player.blockPosition().getZ());
			second = Component.translatable(P + "card.distance", ReadoutFormat.exact(Math.round(distance)),
					Component.translatable(P + "dir." + DIRECTIONS[direction]), point.pos().getY());
		}
		fit(graphics, font, second, cx, y + 33, CARD_TEXT_W, 0.75f, TEXT_DIM);

		if (readiness == Readiness.HIDDEN) {
			row(graphics, font, cx, y + 44, right, Component.translatable(P + "card.access"),
					Component.translatable(P + "card.access.private"), INK_RED);
			paragraph(graphics, font, Component.translatable(P + "card.hidden.title"), cx, y + 56, CARD_TEXT_W, 14, TEXT);
			paragraph(graphics, font, Component.translatable(P + "card.hidden.body"), cx, y + 74, CARD_TEXT_W, 41,
					TEXT_DIM);
			glyph(graphics, x + CARD_TEXT_X + 38, y + 122, LOCK, LOCK_ON_CARD, 2);
			return;
		}
		Component load = Component.translatable(P + "card.load.value",
				String.format(Locale.ROOT, "%.2f", weight(snapshot)));
		if (station == null || !station.has(TeleportStationsPayload.KNOWN)
				|| readiness == Readiness.MISSING) {
			Component question = Component.literal("?");
			Component cost = sameDimension && station != null && station.cost() > 0
					? Component.translatable(P + "card.cost_estimate", ReadoutFormat.exact(RemoteMap.shownPrice(station.cost())))
					: question;
			row(graphics, font, cx, y + 44, right, Component.translatable(P + "card.cost"), cost, TEXT);
			String[] labels = {"card.charge", "card.access", "card.chip", "card.capsule"};
			for (int k = 0; k < labels.length; k++) {
				row(graphics, font, cx, y + 53 + 9 * k, right, Component.translatable(P + labels[k]), question, TEXT_DIM);
			}
			row(graphics, font, cx, y + 89, right, Component.translatable(P + "card.load"), load, TEXT);
			if (readiness == Readiness.UNKNOWN) {
				fit(graphics, font, Component.translatable(P + "card.unknown"), cx, y + 101, CARD_TEXT_W, 0.75f, INK_AMBER);
				paragraph(graphics, font, Component.translatable(P + "card.unknown.body"), cx, y + 110, CARD_TEXT_W, 28,
						TEXT_DIM);
			}
			return;
		}

		long capacity = Math.max(1, snapshot == null ? 1 : snapshot.stationCapacity());
		boolean affordable = !sameDimension || station.energy() >= station.cost();
		int ink = affordable ? TEXT : INK_RED;
		Component cost = sameDimension
				? Component.translatable(P + "card.eu", ReadoutFormat.exact(RemoteMap.shownPrice(station.cost())))
				: Component.literal("—");
		row(graphics, font, cx, y + 44, right, Component.translatable(P + "card.cost"), cost,
				sameDimension ? ink : TEXT_DIM);
		row(graphics, font, cx, y + 53, right, Component.translatable(P + "card.charge"),
				Component.translatable(P + "card.percent", Math.round(100.0 * station.energy() / capacity)), ink);
		chargeBar(graphics, x + CARD_TEXT_X, y + 61, CARD_TEXT_W, station.energy() / (double) capacity,
				sameDimension ? station.cost() / (double) capacity : 0.0);
		rightText(graphics, font, Component.translatable(P + "card.eu", ReadoutFormat.exact(station.energy())), right,
				y + 67, 0.75f, affordable ? TEXT_DIM : INK_RED);
		row(graphics, font, cx, y + 78, right, Component.translatable(P + "card.access"),
				Component.translatable(P + (station.has(TeleportStationsPayload.PRIVATE) ? "card.access.private"
						: "card.access.public")), TEXT);
		boolean chip = station.has(TeleportStationsPayload.CHIP);
		row(graphics, font, cx, y + 87, right, Component.translatable(P + "card.chip"),
				Component.translatable(P + (chip ? "card.chip.fitted" : "card.chip.none")), chip ? INK_GREEN : TEXT_DIM);
		boolean formed = station.has(TeleportStationsPayload.FORMED);
		row(graphics, font, cx, y + 96, right, Component.translatable(P + "card.capsule"),
				Component.translatable(P + (formed ? "card.capsule.ok" : "card.capsule.missing")),
				formed ? INK_GREEN : INK_RED);
		row(graphics, font, cx, y + 105, right, Component.translatable(P + "card.load"), load, TEXT);
		if (!station.has(TeleportStationsPayload.LIVE) && station.updatedAgoSeconds() > 0) {
			fit(graphics, font, Component.translatable(P + "card.updated", age(station.updatedAgoSeconds())), cx, y + 117,
					CARD_TEXT_W, 0.75f, TEXT_DIM);
		}
		if (showsZone(station, level)) {
			graphics.fill(x + CARD_TEXT_X, y + 130, x + CARD_TEXT_X + 5, y + 135, ZONE_LEGEND);
			fit(graphics, font, Component.translatable(P + "card.rtp_ring"), cx + 7, y + 130, CARD_TEXT_W - 7, 0.75f,
					TEXT_DIM);
		}
	}

	private void drawStatus(GuiGraphicsExtractor graphics, Font font, int x, int y, TeleportPoints points,
			@Nullable TeleportStationsPayload snapshot, int selected) {
		Tone tone;
		Component text;
		Component notice = TeleportNotice.current();
		TeleportPoint point = selected >= 0 ? points.get(selected) : null;
		if (point == null && !points.isEmpty()) {
			graphics.fill(x + STATUS_X, y + STATUS_Y, x + STATUS_X + STATUS_W, y + STATUS_Y + STATUS_H, BOX_BACK);
			return;
		}
		if (points.isEmpty()) {
			tone = Tone.IDLE;
			text = Component.translatable("gui.alaindustrial.teleporter.no_points");
		} else if (notice != null) {
			// The server's own refusal of the last press outranks what the snapshot expects.
			tone = Tone.ALARM;
			text = notice;
		} else {
			TeleportStationsPayload.Station station = StationsTabPage.stationAt(snapshot, selected);
			Readiness readiness = StationsTabPage.readiness(station);
			tone = verdictTone(readiness);
			text = switch (readiness) {
				case READY -> snapshot != null && snapshot.cooldownSeconds() > 0
						? Component.translatable("alaindustrial.teleporter.cooldown", snapshot.cooldownSeconds())
						: Component.translatable(P + "status.ready",
								ReadoutFormat.exact(RemoteMap.shownPrice(station.cost())), point.displayName());
				case NO_POWER -> Component.translatable(P + "status.no_power", point.displayName(),
						ReadoutFormat.exact(station.energy()), ReadoutFormat.exact(RemoteMap.shownPrice(station.cost())));
				case HIDDEN -> Component.translatable(P + "status.private_hidden", point.displayName());
				case UNKNOWN -> Component.translatable(P + "status.unknown");
				case NO_CAPSULE -> TeleportEngine.Denial.NOT_FORMED.message();
				case MISSING -> TeleportEngine.Denial.NO_STATION.message();
				case OTHER_WORLD -> TeleportEngine.Denial.CROSS_DIM.message();
			};
			if (readiness == Readiness.READY && snapshot != null && snapshot.cooldownSeconds() > 0) {
				tone = Tone.WARN;
			}
		}
		int left = x + STATUS_X;
		int top = y + STATUS_Y;
		graphics.fill(left, top, left + STATUS_W, top + STATUS_H, BOX_BACK);
		int plate = tone == Tone.GOOD ? FILL_GREEN : tone == Tone.IDLE ? FILL_IDLE : FILL_RED;
		String[] glyph = tone == Tone.GOOD ? GLYPH_TICK : tone == Tone.IDLE ? GLYPH_PAUSE : GLYPH_CROSS;
		int badgeX = left + 4;
		int badgeY = top + (STATUS_H - 10) / 2;
		graphics.fill(badgeX, badgeY, badgeX + 10, badgeY + 10, PLATE_EDGE);
		graphics.fill(badgeX + 1, badgeY + 1, badgeX + 9, badgeY + 9, plate);
		glyph(graphics, badgeX + 1, badgeY + 1, glyph, 0xFFFFFFFF, 1);

		// Two lines at 0.75, else at 0.5 — wrapped, never cut.
		int width = PageText.wrapWidth(text, STATUS_W - 22);
		float scale = 0.75f;
		List<FormattedCharSequence> lines = font.split(text, Math.round(width / scale));
		if (lines.size() * 9 * scale > STATUS_H - 2) {
			scale = 0.5f;
			lines = font.split(text, Math.round(width / scale));
		}
		int shownLines = Math.min(lines.size(), (int) ((STATUS_H - 2) / (9 * scale)));
		float total = shownLines * 9 * scale - scale;
		float textTop = Math.round((top + (STATUS_H - total) / 2) * 4) / 4f;
		for (int i = 0; i < shownLines; i++) {
			sequence(graphics, font, lines.get(i), left + 18, textTop + i * 9 * scale, scale, tone.ink);
		}
	}

	// ── Tooltips and input ──────────────────────────────────────────────────────────────────────────

	@Override
	public boolean tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		LocalPlayer player = screen.minecraftClient().player;
		ClientLevel level = screen.minecraftClient().level;
		if (player == null || level == null || !overRadar(mouseX, mouseY)) {
			return false;
		}
		TeleportPoints points = screen.getMenu().points();
		Layout layout = layout(points, player, level);
		if (overElsewhere(mouseX, mouseY, points, layout)) {
			List<Component> lines = new ArrayList<>();
			for (int index : layout.elsewhere()) {
				lines.add(points.get(index).displayName());
			}
			graphics.setComponentTooltipForNextFrame(screen.font(), lines, mouseX, mouseY);
			return true;
		}
		int hit = RemoteMap.markerAt(layout.markers(), mouseX - screen.left() - RADAR_X, mouseY - screen.top() - RADAR_Y);
		if (hit < 0) {
			return false;
		}
		RemoteMap.Marker marker = layout.markers().get(hit);
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (int index : marker.members()) {
			TeleportPoint point = points.get(index);
			lines.add(Component.translatable(P + "map.tooltip.station", point.displayName(),
					ReadoutFormat.exact(Math.round(Math.sqrt(player.blockPosition().distSqr(point.pos())))))
					.getVisualOrderText());
		}
		if (marker.isCluster()) {
			lines.add(Component.translatable(P + "map.tooltip.next").withStyle(ChatFormatting.GRAY).getVisualOrderText());
		}
		graphics.setTooltipForNextFrame(screen.font(), lines, LEFT_OF_CURSOR, mouseX, mouseY, false);
		return true;
	}

	@Override
	public void mouseReleased(MouseButtonEvent event) {
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event) {
		LocalPlayer player = screen.minecraftClient().player;
		ClientLevel level = screen.minecraftClient().level;
		if (event.button() != 0 || player == null || level == null || !overRadar(event.x(), event.y())) {
			return false;
		}
		TeleportPoints points = screen.getMenu().points();
		Layout layout = layout(points, player, level);
		int selected = screen.getMenu().getSelected();
		if (overElsewhere(event.x(), event.y(), points, layout)) {
			screen.selectStation(RemoteMap.step(layout.elsewhere(), selected, 1));
			return true;
		}
		int hit = RemoteMap.markerAt(layout.markers(), event.x() - screen.left() - RADAR_X,
				event.y() - screen.top() - RADAR_Y);
		if (hit < 0) {
			return false;
		}
		screen.selectStation(RemoteMap.nextInCluster(layout.markers().get(hit).members(), selected));
		return true;
	}

	/** The wheel over the radar walks the stations nearest first. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (scrollY == 0 || !overRadar(mouseX, mouseY)) {
			return false;
		}
		return stepSelection(scrollY > 0 ? -1 : 1);
	}

	/** ← and → walk the stations as the wheel does. Enter is not bound: a name just typed must not start a jump. */
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_LEFT) {
			return stepSelection(-1);
		}
		if (event.key() == GLFW.GLFW_KEY_RIGHT) {
			return stepSelection(1);
		}
		return false;
	}

	private boolean stepSelection(int step) {
		LocalPlayer player = screen.minecraftClient().player;
		ClientLevel level = screen.minecraftClient().level;
		if (player == null || level == null) {
			return false;
		}
		Layout layout = layout(screen.getMenu().points(), player, level);
		if (layout.order().isEmpty()) {
			return false;
		}
		screen.selectStation(RemoteMap.step(layout.order(), screen.getMenu().getSelected(), step));
		return true;
	}

	// ── Helpers ─────────────────────────────────────────────────────────────────────────────────────

	private static Layout layout(TeleportPoints points, LocalPlayer player, ClientLevel level) {
		List<RemoteMap.Placed> placed = new ArrayList<>();
		List<Integer> elsewhere = new ArrayList<>();
		for (int i = 0; i < points.size(); i++) {
			TeleportPoint point = points.get(i);
			if (point.dim() == level.dimension()) {
				placed.add(new RemoteMap.Placed(i, point.pos().getX() + 0.5 - player.getX(),
						point.pos().getZ() + 0.5 - player.getZ()));
			} else {
				elsewhere.add(i);
			}
		}
		List<Integer> order = new ArrayList<>(RemoteMap.byDistance(placed));
		order.addAll(elsewhere);
		return new Layout(RemoteMap.markers(placed), List.copyOf(elsewhere), List.copyOf(order));
	}

	private static Component elsewhereLabel(TeleportPoints points, List<Integer> elsewhere) {
		boolean allNether = true;
		boolean allEnd = true;
		for (int index : elsewhere) {
			allNether &= points.get(index).dim() == Level.NETHER;
			allEnd &= points.get(index).dim() == Level.END;
		}
		String key = allNether ? "map.elsewhere.nether" : allEnd ? "map.elsewhere.end" : "map.elsewhere.other";
		return Component.translatable(P + key, elsewhere.size());
	}

	private boolean overRadar(double mouseX, double mouseY) {
		double rx = mouseX - screen.left() - RADAR_X;
		double ry = mouseY - screen.top() - RADAR_Y;
		return rx >= 0 && rx < SIZE && ry >= 0 && ry < SIZE;
	}

	private boolean overElsewhere(double mouseX, double mouseY, TeleportPoints points, Layout layout) {
		if (layout.elsewhere().isEmpty()) {
			return false;
		}
		int w = Math.round(screen.font().width(elsewhereLabel(points, layout.elsewhere())) * 0.75f) + 6;
		double rx = mouseX - screen.left() - RADAR_X;
		double ry = mouseY - screen.top() - RADAR_Y;
		return rx >= 2 && rx < 2 + w && ry >= SIZE - 12 && ry < SIZE - 2;
	}

	private List<int[]> zoneDots(int radarLeft, int radarTop, TeleportStationsPayload snapshot) {
		if (snapshot.rtpMinRadius() != zoneMin || snapshot.rtpMaxRadius() != zoneMax) {
			zoneMin = snapshot.rtpMinRadius();
			zoneMax = snapshot.rtpMaxRadius();
			double inner = RemoteMap.radius(zoneMin);
			double outer = RemoteMap.radius(zoneMax);
			List<int[]> dots = new ArrayList<>();
			for (int py = radarTop + 1; py < radarTop + SIZE - 1; py++) {
				for (int px = radarLeft + 1; px < radarLeft + SIZE - 1; px++) {
					if (RemoteMap.zoneDot(px, py, radarLeft, radarTop, inner, outer)) {
						dots.add(new int[] {px, py});
					}
				}
			}
			zoneDots = dots;
		}
		return zoneDots;
	}

	/**
	 * The dotted random-jump zone and its legend: for a station with a chip that a random jump can use from here — in the
	 * player's own dimension, which must be the Overworld. A hidden station's chip is withheld, so it never shows one.
	 */
	private static boolean showsZone(TeleportStationsPayload.Station station, ClientLevel level) {
		return station.has(TeleportStationsPayload.CHIP) && !station.has(TeleportStationsPayload.HIDDEN)
				&& station.has(TeleportStationsPayload.SAME_DIMENSION) && level.dimension() == Level.OVERWORLD;
	}

	/** A marker's colour: as the status line reads, except that a hidden or unknown station is always grey. */
	private static Tone markerTone(Readiness readiness) {
		return readiness == Readiness.HIDDEN || readiness == Readiness.UNKNOWN ? Tone.IDLE : verdictTone(readiness);
	}

	private static Tone verdictTone(Readiness readiness) {
		return switch (readiness) {
			case READY -> Tone.GOOD;
			case NO_CAPSULE -> Tone.WARN;
			case NO_POWER, MISSING, HIDDEN -> Tone.ALARM;
			case OTHER_WORLD, UNKNOWN -> Tone.IDLE;
		};
	}

	private static double weight(@Nullable TeleportStationsPayload snapshot) {
		return snapshot == null ? 1.0 : snapshot.weightPermille() / 1000.0;
	}

	private static Component age(int seconds) {
		ReactorLog.Age age = ReactorLog.Age.of(seconds * 20L);
		return switch (age.unit()) {
			case NOW -> Component.translatable("gui.alaindustrial.ago.now");
			case SECONDS -> Component.translatable("gui.alaindustrial.ago.seconds", age.value());
			case MINUTES -> Component.translatable("gui.alaindustrial.ago.minutes", age.value());
			case HOURS -> Component.translatable("gui.alaindustrial.ago.hours", age.value());
			case DAYS -> Component.translatable("gui.alaindustrial.ago.days", age.value());
		};
	}

	private void press(TeleporterRemoteMenu.Action action) {
		int index = screen.getMenu().getSelected();
		if (index >= 0) {
			screen.press(action, index);
		}
	}

	/** The fill is the station screen's sprite; an amber tail is what the jump spends, a red one what it lacks. */
	private static void chargeBar(GuiGraphicsExtractor graphics, int x, int y, int width, double share, double spend) {
		graphics.fill(x, y, x + width, y + BAR_H, BOX_BACK);
		int filled = (int) (width * Math.max(0.0, Math.min(1.0, share)));
		if (filled > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, STATION_TEXTURE, x, y, (float) FILL_U, (float) FILL_V, filled, BAR_H,
					Math.max(1, Math.round((float) FILL_SRC_W * filled / width)), BAR_H, 256, 256);
		}
		if (spend > 0) {
			int cost = Math.max(1, (int) Math.round(width * spend));
			if (cost > filled) {
				graphics.fill(x + filled, y, x + Math.min(width, cost), y + BAR_H, FILL_RED);
			} else {
				graphics.fill(x + filled - cost, y, x + filled, y + BAR_H, FILL_AMBER);
			}
		}
	}

	private static void pixels(GuiGraphicsExtractor graphics, int x0, int y0, List<int[]> pixels, int colour) {
		for (int[] p : pixels) {
			graphics.fill(x0 + p[0], y0 + p[1], x0 + p[0] + 1, y0 + p[1] + 1, colour);
		}
	}

	private static void glyph(GuiGraphicsExtractor graphics, int x, int y, String[] rows, int colour, int cell) {
		for (int row = 0; row < rows.length; row++) {
			for (int col = 0; col < rows[row].length(); col++) {
				if (rows[row].charAt(col) == '#') {
					graphics.fill(x + col * cell, y + row * cell, x + (col + 1) * cell, y + (row + 1) * cell, colour);
				}
			}
		}
	}

	private static void text(GuiGraphicsExtractor graphics, Font font, Component text, float x, float y, float scale,
			int colour) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, 0, 0, colour, false);
		graphics.pose().popMatrix();
	}

	private static void sequence(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, float x, float y,
			float scale, int colour) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, 0, 0, colour, false);
		graphics.pose().popMatrix();
	}

	/**
	 * One line at a scale, stepping down to 0.75 and 0.5 if it does not fit — centred on the same line, as the mockup
	 * draws it — and cut with an ellipsis only at 0.5.
	 */
	private static void fit(GuiGraphicsExtractor graphics, Font font, Component line, float x, float y, int width,
			float scale, int colour) {
		for (float step : new float[] {scale, 0.75f, 0.5f}) {
			if (step > scale) {
				continue;
			}
			if (font.width(line) * step <= width) {
				text(graphics, font, line, x, y + 7 * (scale - step) / 2, step, colour);
				return;
			}
		}
		String plain = line.getString();
		int room = Math.round(width / 0.5f) - font.width("…");
		text(graphics, font, Component.literal(font.plainSubstrByWidth(plain, Math.max(0, room)) + "…"), x,
				y + 7 * (scale - 0.5f) / 2, 0.5f, colour);
	}

	/** Returns where the text starts. */
	private static float rightText(GuiGraphicsExtractor graphics, Font font, Component line, float right, float y,
			float scale, int colour) {
		float left = right - font.width(line) * scale;
		text(graphics, font, line, left, y, scale, colour);
		return left;
	}

	/** A card row: the value right-aligned at 0.75, the label on the left at 0.75 — or 0.5 if the value crowds it. */
	private static void row(GuiGraphicsExtractor graphics, Font font, float x, float y, float right, Component label,
			Component value, int valueColour) {
		float valueLeft = rightText(graphics, font, value, right, y, 0.75f, valueColour);
		float room = valueLeft - 3 - x;
		if (font.width(label) * 0.75f <= room) {
			text(graphics, font, label, x, y, 0.75f, TEXT_DIM);
		} else if (font.width(label) * 0.5f <= room) {
			text(graphics, font, label, x, y + 1, 0.5f, TEXT_DIM);
		} else {
			int cut = Math.max(0, Math.round(room / 0.5f) - font.width("…"));
			text(graphics, font, Component.literal(font.plainSubstrByWidth(label.getString(), cut) + "…"), x, y + 1, 0.5f,
					TEXT_DIM);
		}
	}

	/** Wrapped text in a box: 0.75 if every line fits the height, else 0.5, and only then clipped. */
	private static void paragraph(GuiGraphicsExtractor graphics, Font font, Component body, float x, float y, int width,
			int height, int colour) {
		int wrap = PageText.wrapWidth(body, width);
		float scale = 0.75f;
		List<FormattedCharSequence> lines = font.split(body, Math.round(wrap / scale));
		if (lines.size() * 9 * scale > height + 0.01f) {
			scale = 0.5f;
			lines = font.split(body, Math.round(wrap / scale));
		}
		int fits = Math.min(lines.size(), (int) Math.floor((height + 0.01f) / (9 * scale)));
		for (int i = 0; i < fits; i++) {
			sequence(graphics, font, lines.get(i), x, y + i * 9 * scale, scale, colour);
		}
	}
}
