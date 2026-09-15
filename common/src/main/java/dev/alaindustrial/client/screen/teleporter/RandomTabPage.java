package dev.alaindustrial.client.screen.teleporter;

import dev.alaindustrial.client.ReadoutFormat;
import dev.alaindustrial.client.hud.TeleportNotice;
import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.TeleporterRemoteScreen;
import dev.alaindustrial.client.screen.tabs.PageText;
import dev.alaindustrial.client.screen.tabs.TabPage;
import dev.alaindustrial.core.teleport.RtpChecklist;
import dev.alaindustrial.core.teleport.RtpChecklist.Check;
import dev.alaindustrial.core.teleport.RtpChecklist.Mark;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.item.teleport.TeleportPoints;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.network.TeleportStationsPayload;
import dev.alaindustrial.teleporter.TeleportEngine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The remote's «Random» tab (MOD-630): which station pays for a random jump, the five things that jump needs — all of
 * them at once — what to do about the first one that is missing, and the jump itself.
 *
 * <p>Every coordinate and colour is the approved mockup's (variant 6, frames G and E), read from its generator.
 *
 * <p><b>The checklist is worked out here from the station snapshot</b>, which the server built without loading a chunk,
 * using {@link RtpChecklist} — the same rules {@code TeleportEngine#rtpProblems} checks on the server. The button only
 * asks; the server checks everything again on the press, and a refusal it sends back replaces the hint.
 */
public final class RandomTabPage implements TabPage {

	private static final String P = "gui.alaindustrial.teleporter_remote.";

	// ── Geometry, panel-relative ────────────────────────────────────────────────────────────────────
	private static final int PREV_X = 8, NEXT_X = 212, SWITCH_Y = 20, ARROW_W = 16, ARROW_H = 18;
	private static final int NAME_BOX_X = 26, NAME_BOX_W = 184;
	private static final int LAMP_X = 30, LAMP_Y = 26, NAME_X = 38, NAME_Y = 25, POSITION_RIGHT = 206, POSITION_Y = 26;
	private static final int PAYS_X = 8, PAYS_Y = 41, PAYS_W = 220;
	private static final int CHECK_X = 9, CHECK_Y = 51, CHECK_ROW = 12, CHECK_W = 218;
	private static final int BOX_X = 8, BOX_Y = 114, BOX_W = 220, BOX_H = 46;
	private static final int JUMP_X = 8, JUMP_Y = 166, JUMP_W = 220, JUMP_H = 20;
	private static final int BADGE = 10;
	private static final float SMALL = 0.75f;

	// ── Colours: the mockup's ───────────────────────────────────────────────────────────────────────
	private static final int PLATE_EDGE = 0xFF111316;
	private static final int BOX_BACK = 0xFF2A2D33;
	private static final int BOX_TEXT = 0xFFD7DBE0;
	private static final int ON_DARK = 0xFFD7DBE0;
	private static final int ON_DARK_DIM = 0xFF9AA3AB;
	private static final int CHECK_BAD_BACK = 0xFFE4C0BA;
	private static final int INK_RED = 0xFFAA2A1A;
	private static final int FILL_GREEN = 0xFF4E9E52;
	private static final int FILL_AMBER = 0xFFD9A33A;
	private static final int FILL_RED = 0xFFD63A2A;
	private static final int FILL_IDLE = 0xFF6B7178;
	private static final int FILL_UNKNOWN = 0xFFB4B4B4;
	private static final int GLYPH = 0xFFFFFFFF;

	private static final String[] GLYPH_TICK = {
			"........", "......#.", ".....##.", "#...##..", "##.##...", ".###....", "..#.....", "........"};
	private static final String[] GLYPH_CROSS = {
			"........", ".##..##.", "..####..", "...##...", "..####..", ".##..##.", "........", "........"};
	private static final String[] GLYPH_EXCLAMATION = {
			"...##...", "...##...", "...##...", "...##...", "...##...", "........", "...##...", "........"};
	private static final String[] GLYPH_PAUSE = {
			"........", "..#..#..", "..#..#..", "..#..#..", "..#..#..", "..#..#..", "........", "........"};

	/** A tone as the reactor screen's: what the chip, the lamp and the hint box's badge say. */
	private enum Tone {
		GOOD(0xFF7FD08A, FILL_GREEN, GLYPH_TICK),
		WARN(0xFFE8B04A, FILL_AMBER, GLYPH_EXCLAMATION),
		ALARM(0xFFFF6B5A, FILL_RED, GLYPH_EXCLAMATION),
		IDLE(0xFFB9C0C7, FILL_IDLE, GLYPH_PAUSE);

		final int ink;
		final int fill;
		final String[] glyph;

		Tone(int ink, int fill, String[] glyph) {
			this.ink = ink;
			this.fill = fill;
			this.glyph = glyph;
		}
	}

	private final TeleporterRemoteScreen screen;
	private final ItemStack icon = new ItemStack(Items.ENDER_EYE);

	private @Nullable Button prevButton;
	private @Nullable Button nextButton;
	private @Nullable Button jumpButton;
	private boolean shown;

	public RandomTabPage(TeleporterRemoteScreen screen) {
		this.screen = screen;
	}

	@Override
	public Component title() {
		return Component.translatable(P + "tab.random");
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
		prevButton = screen.addPageWidget(Button.builder(Component.literal("<"), b -> step(-1))
				.bounds(x + PREV_X, y + SWITCH_Y, ARROW_W, ARROW_H).build());
		nextButton = screen.addPageWidget(Button.builder(Component.literal(">"), b -> step(1))
				.bounds(x + NEXT_X, y + SWITCH_Y, ARROW_W, ARROW_H).build());
		jumpButton = screen.addPageWidget(Button.builder(Component.translatable(P + "button.jump_random"), b -> jump())
				.bounds(x + JUMP_X, y + JUMP_Y, JUMP_W, JUMP_H).build());
		setShown(shown);
	}

	@Override
	public void setShown(boolean shown) {
		this.shown = shown;
		for (Button button : new Button[] {prevButton, nextButton, jumpButton}) {
			if (button != null) {
				button.visible = shown;
			}
		}
	}

	@Override
	public void tick() {
		TeleporterRemoteMenu menu = screen.getMenu();
		int stations = order(menu).size();
		if (prevButton != null) {
			prevButton.active = stations > 1;
		}
		if (nextButton != null) {
			nextButton.active = stations > 1;
		}
		if (jumpButton != null) {
			int selected = menu.getSelected();
			// Only when nothing is broken. A row that is merely unknown does not grey it: the server checks the real station
			// on the press, as Teleport does for a station with no record.
			jumpButton.active = selected >= 0 && menu.stations() != null
					&& RtpChecklist.firstFailing(facts(menu, selected, screen.minecraftClient())) == null;
		}
	}

	private void step(int direction) {
		TeleporterRemoteMenu menu = screen.getMenu();
		int next = RtpChecklist.step(order(menu), menu.getSelected(), direction);
		if (next >= 0) {
			screen.selectStation(next);
		}
	}

	private void jump() {
		int selected = screen.getMenu().getSelected();
		if (selected >= 0) {
			screen.press(TeleporterRemoteMenu.Action.RTP, selected);
		}
	}

	// ── What the checklist knows ───────────────────────────────────────────────────────────────────

	/** The switcher's order: stations with a known chip first, each group in binding order. */
	private static List<Integer> order(TeleporterRemoteMenu menu) {
		TeleportPoints points = menu.points();
		boolean[] chip = new boolean[points.size()];
		for (int i = 0; i < chip.length; i++) {
			TeleportStationsPayload.Station station = StationsTabPage.stationAt(menu.stations(), i);
			chip[i] = station != null && station.has(TeleportStationsPayload.KNOWN)
					&& !station.has(TeleportStationsPayload.HIDDEN) && station.has(TeleportStationsPayload.CHIP);
		}
		return RtpChecklist.order(chip);
	}

	private static RtpChecklist.Facts facts(TeleporterRemoteMenu menu, int selected, Minecraft minecraft) {
		boolean inOverworld = minecraft.level != null && minecraft.level.dimension() == Level.OVERWORLD;
		return facts(menu.stations(), StationsTabPage.stationAt(menu.stations(), selected), inOverworld);
	}

	/**
	 * What the snapshot says about one paying station. Someone else's private station withholds its chip and charge, and
	 * a station with no record has neither; both read unknown rather than missing.
	 */
	static RtpChecklist.Facts facts(@Nullable TeleportStationsPayload snapshot,
			TeleportStationsPayload.@Nullable Station station, boolean inOverworld) {
		if (snapshot == null || station == null) {
			return new RtpChecklist.Facts(inOverworld, RtpChecklist.Access.UNKNOWN, false, false,
					RtpChecklist.ENERGY_UNKNOWN, snapshot != null ? snapshot.rtpCost() : 0,
					snapshot != null ? snapshot.cooldownSeconds() : 0);
		}
		long cost = snapshot.rtpCost();
		int cooldown = snapshot.cooldownSeconds();
		boolean known = station.has(TeleportStationsPayload.KNOWN);
		boolean hidden = station.has(TeleportStationsPayload.HIDDEN);
		boolean readable = known && !hidden;
		if (station.denial() == TeleportEngine.Denial.CROSS_DIM) {
			return new RtpChecklist.Facts(inOverworld, RtpChecklist.Access.OTHER_WORLD, readable,
					station.has(TeleportStationsPayload.CHIP), readable ? station.energy() : RtpChecklist.ENERGY_UNKNOWN, cost,
					cooldown);
		}
		if (!known) {
			return new RtpChecklist.Facts(inOverworld, RtpChecklist.Access.UNKNOWN, false, false,
					RtpChecklist.ENERGY_UNKNOWN, cost, cooldown);
		}
		if (hidden) {
			return new RtpChecklist.Facts(inOverworld, RtpChecklist.Access.PRIVATE, false, false,
					RtpChecklist.ENERGY_UNKNOWN, cost, cooldown);
		}
		if (station.denial() == TeleportEngine.Denial.NO_STATION) {
			return new RtpChecklist.Facts(inOverworld, RtpChecklist.Access.MISSING, false, false,
					RtpChecklist.ENERGY_UNKNOWN, cost, cooldown);
		}
		return new RtpChecklist.Facts(inOverworld, RtpChecklist.Access.OK, true, station.has(TeleportStationsPayload.CHIP),
				station.energy(), cost, cooldown);
	}

	/** The header chip on this tab: readiness for a random jump, named by the first thing that is missing. */
	public static StationsTabPage.@Nullable Chip chip(TeleporterRemoteMenu menu, Minecraft minecraft) {
		if (menu.points().isEmpty()) {
			return StationsTabPage.chip(menu);
		}
		int selected = menu.getSelected();
		if (selected < 0) {
			return null;
		}
		RtpChecklist.Facts facts = facts(menu, selected, minecraft);
		Check failing = RtpChecklist.firstFailing(facts);
		if (failing == null) {
			return RtpChecklist.allPassed(facts) ? chip("ready", Tone.GOOD) : chip("unknown", Tone.IDLE);
		}
		return switch (failing) {
			case OVERWORLD -> chip("other_world", Tone.IDLE);
			case ACCESS -> switch (facts.access()) {
				case PRIVATE -> chip("private", Tone.ALARM);
				case MISSING -> chip("missing", Tone.ALARM);
				default -> chip("other_world", Tone.IDLE);
			};
			case CHIP -> chip("no_chip", Tone.WARN);
			case CHARGE -> chip("no_power", Tone.ALARM);
			case RECHARGED -> new StationsTabPage.Chip(
					Component.translatable(P + "chip.recharging", facts.cooldownSeconds()), Tone.WARN.ink);
		};
	}

	private static StationsTabPage.Chip chip(String key, Tone tone) {
		return new StationsTabPage.Chip(Component.translatable(P + "chip." + key), tone.ink);
	}

	/** The lamp in the switcher: green ready, red out of power or unusable, amber anything else broken, hollow unknown. */
	private static @Nullable Tone lampTone(RtpChecklist.Facts facts) {
		Check failing = RtpChecklist.firstFailing(facts);
		if (failing == null) {
			return RtpChecklist.allPassed(facts) ? Tone.GOOD : null;
		}
		return switch (failing) {
			case CHARGE -> Tone.ALARM;
			case ACCESS -> facts.access() == RtpChecklist.Access.OTHER_WORLD ? Tone.IDLE : Tone.ALARM;
			case OVERWORLD -> Tone.IDLE;
			case CHIP, RECHARGED -> Tone.WARN;
		};
	}

	// ── Drawing ─────────────────────────────────────────────────────────────────────────────────────

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		TeleporterRemoteMenu menu = screen.getMenu();
		TeleportPoints points = menu.points();
		TeleportStationsPayload snapshot = menu.stations();
		Font font = screen.font();
		int x = screen.left();
		int y = screen.top();
		int selected = menu.getSelected();
		TeleportPoint point = selected >= 0 ? points.get(selected) : null;
		RtpChecklist.Facts facts = facts(menu, selected, screen.minecraftClient());

		drawSwitcher(graphics, font, x, y, menu, point, facts);
		if (point == null) {
			drawBox(graphics, font, x, y, Tone.IDLE, Component.translatable("gui.alaindustrial.teleporter.no_points"),
					List.of());
			return;
		}
		if (snapshot != null) {
			fit(graphics, font, Component.translatable(P + "random.pays",
							ReadoutFormat.exact(RemoteMap.shownPrice(snapshot.rtpCost())),
							ReadoutFormat.exact(snapshot.rtpMinRadius()), ReadoutFormat.exact(snapshot.rtpMaxRadius())),
					x + PAYS_X, y + PAYS_Y, PAYS_W, SMALL, GuiStyle.TEXT_DIM);
		}
		Check[] checks = Check.values();
		for (int i = 0; i < checks.length; i++) {
			drawCheck(graphics, font, x + CHECK_X, y + CHECK_Y + i * CHECK_ROW, checks[i], facts, snapshot);
		}
		drawHint(graphics, font, x, y, facts, snapshot);
	}

	private void drawSwitcher(GuiGraphicsExtractor graphics, Font font, int x, int y, TeleporterRemoteMenu menu,
			@Nullable TeleportPoint point, RtpChecklist.Facts facts) {
		int left = x + NAME_BOX_X;
		int top = y + SWITCH_Y;
		graphics.fill(left, top, left + NAME_BOX_W, top + ARROW_H, PLATE_EDGE);
		graphics.fill(left + 1, top + 1, left + NAME_BOX_W - 1, top + ARROW_H - 1, BOX_BACK);
		if (point == null) {
			return;
		}
		List<Integer> order = order(menu);
		Component position = Component.translatable(P + "random.position", order.indexOf(menu.getSelected()) + 1,
				order.size());
		int positionLeft = x + POSITION_RIGHT - Math.round(font.width(position) * SMALL);
		PageText.scaled(graphics, font, position, positionLeft, y + POSITION_Y - 1, SMALL, ON_DARK_DIM);

		Tone tone = lampTone(facts);
		int lx = x + LAMP_X;
		int ly = y + LAMP_Y;
		graphics.fill(lx, ly, lx + 5, ly + 5, PLATE_EDGE);
		if (tone == null) {
			graphics.fill(lx + 1, ly + 1, lx + 4, ly + 4, BOX_BACK);
			graphics.fill(lx + 2, ly + 2, lx + 3, ly + 3, FILL_IDLE);
		} else {
			graphics.fill(lx + 1, ly + 1, lx + 4, ly + 4, tone.fill);
		}
		int room = positionLeft - 4 - (x + NAME_X);
		graphics.text(font, clip(font, point.displayName().getString(), room), x + NAME_X, y + NAME_Y, ON_DARK, false);
	}

	private static void drawCheck(GuiGraphicsExtractor graphics, Font font, int x, int y, Check check,
			RtpChecklist.Facts facts, @Nullable TeleportStationsPayload snapshot) {
		Mark mark = RtpChecklist.mark(check, facts);
		if (mark == Mark.FAILED) {
			graphics.fill(x - 1, y - 1, x + CHECK_W + 1, y + CHECK_ROW - 1, CHECK_BAD_BACK);
		}
		int plate = switch (mark) {
			case PASSED -> FILL_GREEN;
			case FAILED -> FILL_RED;
			case UNKNOWN -> FILL_UNKNOWN;
		};
		badge(graphics, x, y, plate, mark == Mark.PASSED ? GLYPH_TICK : mark == Mark.FAILED ? GLYPH_CROSS : null);
		int colour = switch (mark) {
			case PASSED -> GuiStyle.TEXT;
			case FAILED -> INK_RED;
			case UNKNOWN -> GuiStyle.TEXT_DIM;
		};
		fit(graphics, font, checkLabel(check, mark, facts, snapshot), x + 13, y + 2, CHECK_W - 14, SMALL, colour);
	}

	private static Component checkLabel(Check check, Mark mark, RtpChecklist.Facts facts,
			@Nullable TeleportStationsPayload snapshot) {
		String price = ReadoutFormat.exact(RemoteMap.shownPrice(facts.cost()));
		return switch (check) {
			case OVERWORLD -> Component.translatable(P + (mark == Mark.FAILED ? "random.check.not_overworld"
					: "random.check.overworld"));
			case ACCESS -> mark != Mark.FAILED ? Component.translatable(P + "random.check.access")
					: switch (facts.access()) {
						case PRIVATE -> Component.translatable(P + "random.check.no_access");
						case OTHER_WORLD -> Component.translatable(P + "random.check.other_world");
						default -> TeleportEngine.Denial.NO_STATION.message();
					};
			case CHIP -> Component.translatable(P + "random.check.chip");
			case CHARGE -> switch (mark) {
				case PASSED -> Component.translatable(P + "random.check.charge", ReadoutFormat.exact(facts.energy()), price);
				case FAILED -> Component.translatable(P + "random.check.charge_short", ReadoutFormat.exact(facts.energy()),
						price);
				case UNKNOWN -> Component.translatable(P + "random.check.charge", "?", price);
			};
			case RECHARGED -> mark == Mark.FAILED
					? Component.translatable(P + "random.check.recharging", facts.cooldownSeconds())
					: Component.translatable(P + "random.check.recharged");
		};
	}

	/**
	 * The box under the list: the server's refusal of the last press if there is one; otherwise what to do about the first
	 * broken row; otherwise where the jump lands.
	 */
	private static void drawHint(GuiGraphicsExtractor graphics, Font font, int x, int y, RtpChecklist.Facts facts,
			@Nullable TeleportStationsPayload snapshot) {
		Component notice = TeleportNotice.current();
		if (notice != null) {
			drawBox(graphics, font, x, y, Tone.ALARM, notice, List.of());
			return;
		}
		Check failing = RtpChecklist.firstFailing(facts);
		String price = ReadoutFormat.exact(RemoteMap.shownPrice(facts.cost()));
		if (failing == null) {
			if (!RtpChecklist.allPassed(facts) || snapshot == null) {
				drawBox(graphics, font, x, y, Tone.IDLE, Component.translatable(P + "card.unknown"),
						List.of(Component.translatable(P + "card.unknown.body")));
				return;
			}
			drawBox(graphics, font, x, y, Tone.GOOD,
					Component.translatable(P + "random.ready.title", ReadoutFormat.exact(snapshot.rtpMinRadius()),
							ReadoutFormat.exact(snapshot.rtpMaxRadius())),
					List.of(Component.translatable(P + "random.ready.body", snapshot.warmupSeconds())));
			return;
		}
		switch (failing) {
			case OVERWORLD -> drawBox(graphics, font, x, y, Tone.WARN, Component.translatable(P + "random.hint.world.title"),
					List.of(Component.translatable(P + "random.hint.world.body")));
			case ACCESS -> drawBox(graphics, font, x, y, Tone.ALARM,
					checkLabel(Check.ACCESS, Mark.FAILED, facts, snapshot), List.of());
			case CHIP -> drawBox(graphics, font, x, y, Tone.WARN, Component.translatable(P + "random.hint.chip.title"),
					List.of(Component.translatable(P + "random.hint.chip.body")));
			case CHARGE -> drawBox(graphics, font, x, y, Tone.ALARM, Component.translatable(P + "random.hint.charge.title"),
					List.of(Component.translatable(P + "random.hint.charge.body", price)));
			case RECHARGED -> drawBox(graphics, font, x, y, Tone.WARN,
					checkLabel(Check.RECHARGED, Mark.FAILED, facts, snapshot), List.of());
		}
	}

	/** The dark box: a tone badge, a title in the tone's colour, and a body wrapped under it — shrunk, never cut short. */
	private static void drawBox(GuiGraphicsExtractor graphics, Font font, int x, int y, Tone tone, Component title,
			List<Component> body) {
		int left = x + BOX_X;
		int top = y + BOX_Y;
		graphics.fill(left, top, left + BOX_W, top + BOX_H, BOX_BACK);
		badge(graphics, left + 5, top + 3, tone.fill, tone.glyph);
		int titleX = left + 19;
		fit(graphics, font, title, titleX, top + 4, left + BOX_W - 4 - titleX, 1.0f, tone.ink);
		if (body.isEmpty()) {
			return;
		}
		int textW = BOX_W - 10;
		int room = BOX_H - 18;
		float scale = SMALL;
		List<FormattedCharSequence> lines = split(font, body, textW, scale);
		if (lines.size() * font.lineHeight * scale > room + 0.01f) {
			scale = 0.5f;
			lines = split(font, body, textW, scale);
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(left + 6, top + 16);
		graphics.pose().scale(scale, scale);
		for (int i = 0; i < lines.size() && (i + 1) * font.lineHeight * scale <= room + 0.5f; i++) {
			graphics.text(font, lines.get(i), 0, i * font.lineHeight, BOX_TEXT, false);
		}
		graphics.pose().popMatrix();
	}

	private static List<FormattedCharSequence> split(Font font, List<Component> paragraphs, int width, float scale) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (Component paragraph : paragraphs) {
			lines.addAll(font.split(paragraph, Math.round(PageText.wrapWidth(paragraph, width) / scale)));
		}
		return lines;
	}

	/** A 10×10 plate with an 8×8 pixel glyph, or none for a row the client knows nothing about. */
	private static void badge(GuiGraphicsExtractor graphics, int x, int y, int plate, String @Nullable [] glyph) {
		graphics.fill(x, y, x + BADGE, y + BADGE, PLATE_EDGE);
		graphics.fill(x + 1, y + 1, x + BADGE - 1, y + BADGE - 1, plate);
		if (glyph == null) {
			return;
		}
		for (int row = 0; row < glyph.length; row++) {
			for (int col = 0; col < glyph[row].length(); col++) {
				if (glyph[row].charAt(col) == '#') {
					graphics.fill(x + 1 + col, y + 1 + row, x + 2 + col, y + 2 + row, GLYPH);
				}
			}
		}
	}

	/** One line at up to {@code scale}, shrunk to its width but never below {@link PageText#MIN_SCALE}. */
	private static void fit(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, float scale,
			int colour) {
		float fitted = Math.min(scale, (float) width / Math.max(1, font.width(text)));
		PageText.scaled(graphics, font, text, x, y, Math.max(PageText.MIN_SCALE, fitted), colour);
	}

	/** A station name cut to what the switcher can show, with an ellipsis when it had to cut. */
	private static String clip(Font font, String name, int room) {
		if (font.width(name) <= room) {
			return name;
		}
		String ellipsis = "…";
		return font.plainSubstrByWidth(name, Math.max(0, room - font.width(ellipsis))) + ellipsis;
	}

	@Override
	public boolean tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		return false;
	}

	@Override
	public void mouseReleased(MouseButtonEvent event) {
	}
}
