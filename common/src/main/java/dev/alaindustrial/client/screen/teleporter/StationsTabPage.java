package dev.alaindustrial.client.screen.teleporter;

import dev.alaindustrial.Config;
import dev.alaindustrial.client.hud.TeleportNotice;
import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.TeleporterRemoteScreen;
import dev.alaindustrial.client.screen.tabs.PageText;
import dev.alaindustrial.client.screen.tabs.TabPage;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.item.teleport.TeleportPoints;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.network.TeleportStationsPayload;
import dev.alaindustrial.registry.ModContent;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The remote's «Stations» tab (MOD-628): the bound stations with a readiness lamp each, renaming, and deleting under a
 * padlock.
 *
 * <p>Every coordinate is the approved mockup's (variant 6, frame E), read from its generator rather than off the
 * picture. Rows are drawn by hand, not as widgets, so they sit inside the dark well.
 *
 * <p>Teleport and Random moved to the «Map» tab when it shipped (MOD-629); the bottom row is the mockup's again —
 * Delete, its padlock, and the hint saying what the padlock is for.
 *
 * <p>Nothing here decides anything: a click sends the server an index and the server re-reads the real remote. The
 * lamps come from {@link TeleportStationsPayload}, which the server builds without loading a chunk.
 */
public final class StationsTabPage implements TabPage {

	// Header and list well, panel-relative.
	private static final int HEADER_Y = 21;
	private static final int CONTENT_LEFT = 8;
	private static final int CONTENT_RIGHT = 228;
	private static final int LIST_TOP = 30;
	private static final int ROW_H = 12;
	private static final int ROWS = 9;
	private static final int LAMP_X = 14;
	private static final int NAME_X = 23;
	private static final int WHERE_RIGHT = 224;
	private static final float SMALL = 0.75f;

	// Name field and Rename.
	private static final int NAME_FIELD_X = 8, NAME_FIELD_Y = 144, NAME_FIELD_W = 150, NAME_FIELD_H = 18;
	private static final int RENAME_X = 162, RENAME_W = 66;

	/** The bottom row: Delete, its padlock, and the hint beside them. */
	private static final int BTN_ROW_Y = 166, BTN_H = 20;
	private static final int DELETE_X = 8, DELETE_W = 58;
	private static final int HINT_X = 86, HINT_Y = 168, HINT_W = 142, HINT_H = 16;

	/** The padlock guarding Delete — the station screen's own two sprites, at the atlas coordinates they always had. */
	private static final int LOCK_X = 70;
	private static final int LOCK_W = 10;
	private static final int LOCK_BASELINE = BTN_ROW_Y + 17;
	private static final int LOCK_CLOSED_U = 96, LOCK_CLOSED_V = 243, LOCK_CLOSED_H = 13;
	private static final int LOCK_OPEN_U = 112, LOCK_OPEN_V = 241, LOCK_OPEN_H = 15;

	private static final int WELL_EDGE = 0xFF111316;
	private static final int WELL_BACK = 0xFF2A2D33;
	private static final int ROW_STRIPE = 0xFF31353C;
	private static final int ROW_HOVER = 0x18FFFFFF;
	/** The selected row: a fill and a 1 px frame a shade lighter — never a stripe down its left edge (owner's call). */
	private static final int SEL_BACK = 0xFF463C5E;
	private static final int SEL_EDGE = 0xFF9A84D0;
	private static final int ON_DARK = 0xFFD7DBE0;
	private static final int ON_DARK_DIM = 0xFF9AA3AB;

	private static final int FILL_GREEN = 0xFF4E9E52;
	private static final int FILL_AMBER = 0xFFD9A33A;
	private static final int FILL_RED = 0xFFD63A2A;
	private static final int FILL_IDLE = 0xFF6B7178;

	/** A refusal, banded across the bottom of the well — opaque so it reads over whatever row is under it. */
	private static final int NOTICE_BG = 0xFF501010;
	private static final int NOTICE_TEXT = 0xFFFFC9C9;

	/** What a station's lamp and the header chip say about it. */
	public enum Readiness {
		READY, NO_CAPSULE, NO_POWER, MISSING, OTHER_WORLD, HIDDEN, UNKNOWN
	}

	/** The header chip: a label and its colour on the dark plate. */
	public record Chip(Component label, int colour) {
	}

	private final TeleporterRemoteScreen screen;
	private final ItemStack icon = new ItemStack(ModContent.TELEPORTER_REMOTE.get());

	private @Nullable EditBox nameBox;
	private @Nullable Button renameButton;
	private @Nullable Button deleteButton;
	private boolean shown = true;
	/** First visible row — the scroll position. */
	private int scroll;
	/**
	 * Whether Delete is armed. Deleting a point loses the only record of where home was, so it costs a deliberate second
	 * click. Re-locks after a delete and whenever the selection moves, so an unlock is never spent on a row the player did
	 * not mean.
	 */
	private boolean deleteUnlocked;
	/** Whether the opening auto-selection has happened; it must fire once, not every tick. */
	private boolean autoSelected;
	/**
	 * The selection the name field shows, by index and by point. The «Map» tab moves the selection too, and a delete
	 * shifts the list under the same index: a field that kept the deleted station's name lit Rename, and pressing it
	 * renamed the neighbour.
	 */
	private int shownSelection = -1;
	private @Nullable TeleportPoint shownPoint;

	public StationsTabPage(TeleporterRemoteScreen screen) {
		this.screen = screen;
	}

	@Override
	public Component title() {
		return Component.translatable("gui.alaindustrial.teleporter_remote.tab.stations");
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
		// A bordered EditBox centres its line in its own height (EditBox#updateTextPosition), so the box takes the
		// mockup's field bounds as they are.
		nameBox = screen.addPageWidget(new EditBox(screen.font(), x + NAME_FIELD_X, y + NAME_FIELD_Y, NAME_FIELD_W,
				NAME_FIELD_H, Component.translatable("gui.alaindustrial.teleporter.name")));
		nameBox.setMaxLength(TeleportPoint.MAX_NAME_LENGTH);
		renameButton = screen.addPageWidget(Button.builder(
				Component.translatable("gui.alaindustrial.teleporter.rename"), b -> sendRename())
				.bounds(x + RENAME_X, y + NAME_FIELD_Y, RENAME_W, NAME_FIELD_H).build());
		deleteButton = screen.addPageWidget(Button.builder(
				Component.translatable("gui.alaindustrial.teleporter.delete"), b -> confirmDelete())
				.bounds(x + DELETE_X, y + BTN_ROW_Y, DELETE_W, BTN_H).build());
		// The field is rebuilt on resize; it must show the selection again.
		shownSelection = -1;
		setShown(shown);
	}

	@Override
	public void setShown(boolean shown) {
		this.shown = shown;
		for (var widget : new net.minecraft.client.gui.components.AbstractWidget[] {nameBox, renameButton, deleteButton}) {
			if (widget != null) {
				widget.visible = shown;
			}
		}
	}

	@Override
	public void tick() {
		TeleporterRemoteMenu menu = screen.getMenu();
		TeleportPoints points = menu.points();
		// Land on the station picked last time, else the first, so opening the remote and jumping stays two clicks. Here
		// rather than in init: the menu is not populated until the first tick, and only once — re-selecting every tick
		// fights the player.
		if (!autoSelected && !points.isEmpty()) {
			autoSelected = true;
			int remembered = screen.rememberedStation();
			select(remembered >= 0 && remembered < points.size() ? remembered : 0);
		}
		// The server owns the list: a delete can shrink it under us.
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsInList(points) - ROWS)));
		if (menu.getSelected() >= points.size()) {
			menu.setSelected(-1);
		}
		if (menu.getSelected() != shownSelection || !java.util.Objects.equals(points.get(menu.getSelected()), shownPoint)) {
			showSelection(menu.getSelected());
		}
		boolean hasSelection = menu.getSelected() >= 0;
		if (renameButton != null) {
			renameButton.active = hasSelection && isRenameMeaningful();
		}
		if (deleteButton != null) {
			// Greyed until the padlock is open, so the guard is visible rather than a silent no-op.
			deleteButton.active = hasSelection && deleteUnlocked;
		}
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		TeleporterRemoteMenu menu = screen.getMenu();
		TeleportPoints points = menu.points();
		Font font = screen.font();
		int x = screen.left();
		int y = screen.top();

		PageText.scaled(graphics, font, Component.translatable("gui.alaindustrial.teleporter_remote.stations.bound"),
				x + CONTENT_LEFT, y + HEADER_Y, SMALL, GuiStyle.TEXT_DIM);
		Component count = Component.translatable("gui.alaindustrial.teleporter_remote.stations.count", points.size(),
				Config.teleporterMaxPoints);
		PageText.scaled(graphics, font, count, x + CONTENT_RIGHT - Math.round(font.width(count) * SMALL), y + HEADER_Y,
				SMALL, GuiStyle.TEXT_DIM);

		int wellTop = y + LIST_TOP - 1;
		int wellBottom = y + LIST_TOP + ROWS * ROW_H + 1;
		graphics.fill(x + CONTENT_LEFT, wellTop, x + CONTENT_RIGHT, wellBottom, WELL_EDGE);
		graphics.fill(x + CONTENT_LEFT + 1, wellTop + 1, x + CONTENT_RIGHT - 1, wellBottom - 1, WELL_BACK);

		TeleportStationsPayload snapshot = menu.stations();
		int selected = menu.getSelected();
		int free = Math.max(0, Config.teleporterMaxPoints - points.size());
		for (int row = 0; row < ROWS; row++) {
			int index = scroll + row;
			int rowTop = y + LIST_TOP + row * ROW_H;
			int rowLeft = x + CONTENT_LEFT + 1;
			int rowRight = x + CONTENT_RIGHT - 1;
			if (index < points.size()) {
				if (index == selected) {
					graphics.fill(rowLeft, rowTop, rowRight, rowTop + ROW_H, SEL_EDGE);
					graphics.fill(rowLeft + 1, rowTop + 1, rowRight - 1, rowTop + ROW_H - 1, SEL_BACK);
				} else {
					if (index % 2 == 1) {
						graphics.fill(rowLeft, rowTop, rowRight, rowTop + ROW_H, ROW_STRIPE);
					}
					if (isOverRow(mouseX, mouseY, row)) {
						graphics.fill(rowLeft, rowTop, rowRight, rowTop + ROW_H, ROW_HOVER);
					}
				}
				TeleportPoint point = points.get(index);
				drawLamp(graphics, x + LAMP_X, rowTop + 3, readiness(stationAt(snapshot, index)));
				Component where = where(point);
				int whereW = Math.round(font.width(where) * SMALL);
				int whereX = x + WHERE_RIGHT - whereW;
				PageText.scaled(graphics, font, where, whereX, rowTop + 3, SMALL, ON_DARK_DIM);
				int nameRoom = Math.round((whereX - 4 - (x + NAME_X)) / SMALL);
				PageText.scaled(graphics, font, Component.literal(clip(font, point.displayName().getString(), nameRoom)),
						x + NAME_X, rowTop + 3, SMALL, ON_DARK);
			} else if (index == points.size() && free > 0) {
				smallFit(graphics, font, Component.translatable("gui.alaindustrial.teleporter_remote.stations.free", free),
						x + LAMP_X, rowTop + 3, CONTENT_RIGHT - LAMP_X - 6, ON_DARK_DIM);
			} else if (index == points.size() + (free > 0 ? 1 : 0)) {
				graphics.fill(rowLeft, rowTop, rowRight, rowTop + ROW_H, ROW_STRIPE);
				smallFit(graphics, font, Component.translatable("gui.alaindustrial.teleporter_remote.stations.add_hint"),
						x + LAMP_X, rowTop + 3, CONTENT_RIGHT - LAMP_X - 6, ON_DARK_DIM);
			}
		}

		drawLock(graphics, x, y);
		drawHint(graphics, font, x, y);
		drawNotice(graphics, font, x, y);
	}

	@Override
	public boolean tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		return false;
	}

	@Override
	public void mouseReleased(MouseButtonEvent event) {
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (isOverLock(event.x(), event.y())) {
			deleteUnlocked = !deleteUnlocked;
			screen.playUi(deleteUnlocked ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE);
			return true;
		}
		TeleportPoints points = screen.getMenu().points();
		for (int row = 0; row < ROWS && scroll + row < points.size(); row++) {
			if (isOverRow(event.x(), event.y(), row)) {
				select(scroll + row);
				return true;
			}
		}
		return false;
	}

	/** The well shows {@link #ROWS} rows of up to {@code teleporterMaxPoints} stations plus two info rows. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		int max = Math.max(0, rowsInList(screen.getMenu().points()) - ROWS);
		if (max > 0 && scrollY != 0) {
			scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
			return true;
		}
		return false;
	}

	/**
	 * Keys for the name field, the way vanilla's anvil hands them over: the field first, and while it can take input
	 * nothing else — the inventory key must not close the screen mid-name. Enter renames.
	 */
	public boolean keyPressed(KeyEvent event) {
		if (nameBox == null || !nameBox.isFocused()) {
			return false;
		}
		if (event.isConfirmation()) {
			if (renameButton != null && renameButton.active) {
				sendRename();
			}
			return true;
		}
		return nameBox.keyPressed(event) || nameBox.canConsumeInput();
	}

	// ── What the header chip and a lamp say ───────────────────────────────────────────────────────────

	/** The chip for the selected station, or {@code null} when there is no selection and there are stations. */
	public static @Nullable Chip chip(TeleporterRemoteMenu menu) {
		if (menu.points().isEmpty()) {
			return new Chip(Component.translatable("gui.alaindustrial.teleporter_remote.chip.no_stations"), TONE_IDLE);
		}
		int selected = menu.getSelected();
		if (selected < 0) {
			return null;
		}
		TeleportStationsPayload snapshot = menu.stations();
		Readiness readiness = readiness(stationAt(snapshot, selected));
		return switch (readiness) {
			case UNKNOWN -> new Chip(Component.translatable("gui.alaindustrial.teleporter_remote.chip.unknown"), TONE_IDLE);
			case HIDDEN -> new Chip(Component.translatable("gui.alaindustrial.teleporter_remote.chip.private"), TONE_ALARM);
			case OTHER_WORLD -> new Chip(Component.translatable("gui.alaindustrial.teleporter_remote.chip.other_world"),
					TONE_IDLE);
			case MISSING -> new Chip(Component.translatable("gui.alaindustrial.teleporter_remote.chip.missing"), TONE_ALARM);
			case NO_CAPSULE -> new Chip(Component.translatable("gui.alaindustrial.teleporter_remote.chip.no_capsule"),
					TONE_WARN);
			case NO_POWER -> new Chip(Component.translatable("gui.alaindustrial.teleporter_remote.chip.no_power"), TONE_ALARM);
			case READY -> snapshot != null && snapshot.cooldownSeconds() > 0
					? new Chip(Component.translatable("gui.alaindustrial.teleporter_remote.chip.recharging",
							snapshot.cooldownSeconds()), TONE_WARN)
					: new Chip(Component.translatable("gui.alaindustrial.teleporter_remote.chip.ready"), TONE_GOOD);
		};
	}

	/** Colours of a tone on the dark chip — the reactor controller's, so the two screens read alike. */
	private static final int TONE_GOOD = 0xFF7FD08A;
	private static final int TONE_WARN = 0xFFE8B04A;
	private static final int TONE_ALARM = 0xFFFF6B5A;
	private static final int TONE_IDLE = 0xFFB9C0C7;

	static Readiness readiness(TeleportStationsPayload.@Nullable Station station) {
		// Another dimension first: the server refuses that jump whether or not it has a record of the station.
		if (station != null && station.denial() == dev.alaindustrial.teleporter.TeleportEngine.Denial.CROSS_DIM) {
			return Readiness.OTHER_WORLD;
		}
		if (station == null || !station.has(TeleportStationsPayload.KNOWN)) {
			return Readiness.UNKNOWN;
		}
		if (station.has(TeleportStationsPayload.HIDDEN)) {
			return Readiness.HIDDEN;
		}
		return switch (station.denial()) {
			case OK -> Readiness.READY;
			case NOT_FORMED -> Readiness.NO_CAPSULE;
			case NOT_ENOUGH_EU -> Readiness.NO_POWER;
			case NO_STATION -> Readiness.MISSING;
			case CROSS_DIM -> Readiness.OTHER_WORLD;
			default -> Readiness.UNKNOWN;
		};
	}

	/** The snapshot row for a bound point, or {@code null} while the snapshot is missing or behind the list. */
	static TeleportStationsPayload.@Nullable Station stationAt(@Nullable TeleportStationsPayload snapshot, int index) {
		if (snapshot == null || index < 0 || index >= snapshot.stations().size()) {
			return null;
		}
		return snapshot.stations().get(index);
	}

	/**
	 * A 5×5 lamp: green ready, amber no capsule, red no power or gone, grey another world or someone else's private
	 * station, and hollow when the server has no record of the station.
	 */
	private static void drawLamp(GuiGraphicsExtractor graphics, int x, int y, Readiness readiness) {
		graphics.fill(x, y, x + 5, y + 5, WELL_EDGE);
		int fill = switch (readiness) {
			case READY -> FILL_GREEN;
			case NO_CAPSULE -> FILL_AMBER;
			case NO_POWER, MISSING -> FILL_RED;
			case OTHER_WORLD, HIDDEN -> FILL_IDLE;
			case UNKNOWN -> 0;
		};
		if (fill == 0) {
			graphics.fill(x + 1, y + 1, x + 4, y + 4, WELL_BACK);
			graphics.fill(x + 2, y + 2, x + 3, y + 3, FILL_IDLE);
		} else {
			graphics.fill(x + 1, y + 1, x + 4, y + 4, fill);
		}
	}

	// ── Drawing helpers ──────────────────────────────────────────────────────────────────────────────

	/** Coordinates for a station in the player's dimension's family; the dimension's name for the others. */
	private Component where(TeleportPoint point) {
		net.minecraft.client.multiplayer.ClientLevel level = screen.minecraftClient().level;
		if (level != null && point.dim() != level.dimension()) {
			String key = point.dim() == Level.NETHER ? "nether" : point.dim() == Level.END ? "end" : "other";
			return Component.translatable("gui.alaindustrial.teleporter_remote.stations.dim." + key);
		}
		return Component.translatable("gui.alaindustrial.teleporter.coords",
				point.pos().getX(), point.pos().getY(), point.pos().getZ());
	}

	/** One line at {@link #SMALL}, shrunk further only if it would not fit its width. */
	private static void smallFit(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width,
			int colour) {
		float fit = (float) width / Math.max(1, font.width(text));
		PageText.scaled(graphics, font, text, x, y, Math.max(PageText.MIN_SCALE, Math.min(SMALL, fit)), colour);
	}

	/**
	 * Trim a name to what the row can show, with an ellipsis when it had to cut. {@code plainSubstrByWidth} is vanilla's
	 * own measurer, so a CJK glyph counts as the ~2× it draws and a surrogate pair is never split.
	 */
	private static String clip(Font font, String name, int room) {
		if (font.width(name) <= room) {
			return name;
		}
		String ellipsis = "…";
		return font.plainSubstrByWidth(name, Math.max(0, room - font.width(ellipsis))) + ellipsis;
	}

	/** What the padlock is for, beside it: two lines at {@link #SMALL}, or at 0.5 when a translation needs more. */
	private static void drawHint(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		Component hint = Component.translatable("gui.alaindustrial.teleporter_remote.stations.delete_hint");
		float scale = SMALL;
		int wrap = PageText.wrapWidth(hint, HINT_W);
		List<FormattedCharSequence> lines = font.split(hint, Math.round(wrap / scale));
		if (lines.size() * font.lineHeight * scale > HINT_H + 0.01f) {
			scale = 0.5f;
			lines = font.split(hint, Math.round(wrap / scale));
		}
		int shownLines = Math.min(lines.size(), (int) Math.floor((HINT_H + 0.01f) / (font.lineHeight * scale)));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + HINT_X, y + HINT_Y);
		graphics.pose().scale(scale, scale);
		for (int i = 0; i < shownLines; i++) {
			graphics.text(font, lines.get(i), 0, i * font.lineHeight, GuiStyle.TEXT_DIM, false);
		}
		graphics.pose().popMatrix();
	}

	/**
	 * Why the last press did nothing, banded across the bottom of the well — in the panel, because that is where the
	 * player is looking. Wrapped, not cut: a refusal in a long language used to lose its end. Two lines at full size,
	 * three at {@link #SMALL} when two are not enough.
	 */
	private static void drawNotice(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		Component notice = TeleportNotice.current();
		if (notice == null) {
			return;
		}
		int textW = CONTENT_RIGHT - CONTENT_LEFT - 10;
		float scale = 1.0f;
		List<FormattedCharSequence> lines = font.split(notice, textW);
		if (lines.size() > 2) {
			scale = SMALL;
			lines = font.split(notice, Math.round(textW / scale));
		}
		int shownLines = Math.min(lines.size(), scale < 1.0f ? 3 : 2);
		int bandBottom = y + LIST_TOP + ROWS * ROW_H;
		int bandTop = bandBottom - Math.round(shownLines * font.lineHeight * scale) - 4;
		graphics.fill(x + CONTENT_LEFT + 1, bandTop, x + CONTENT_RIGHT - 1, bandBottom, NOTICE_BG);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + CONTENT_LEFT + 5, bandTop + 2);
		graphics.pose().scale(scale, scale);
		for (int i = 0; i < shownLines; i++) {
			graphics.text(font, lines.get(i), 0, i * font.lineHeight, NOTICE_TEXT, false);
		}
		graphics.pose().popMatrix();
	}

	private void drawLock(GuiGraphicsExtractor graphics, int x, int y) {
		int lockH = deleteUnlocked ? LOCK_OPEN_H : LOCK_CLOSED_H;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TeleporterRemoteScreen.TEXTURE, x + LOCK_X, y + LOCK_BASELINE - lockH,
				(float) (deleteUnlocked ? LOCK_OPEN_U : LOCK_CLOSED_U),
				(float) (deleteUnlocked ? LOCK_OPEN_V : LOCK_CLOSED_V),
				LOCK_W, lockH, TeleporterRemoteScreen.TEX_SIZE, TeleporterRemoteScreen.TEX_SIZE);
	}

	// ── Input helpers ────────────────────────────────────────────────────────────────────────────────

	/** Stations, then the free-slots row and the hint row, so the wheel reaches both below a long list. */
	private static int rowsInList(TeleportPoints points) {
		return points.size() + 2;
	}

	private boolean isOverLock(double mouseX, double mouseY) {
		double rx = mouseX - screen.left();
		double ry = mouseY - screen.top();
		int lockH = deleteUnlocked ? LOCK_OPEN_H : LOCK_CLOSED_H;
		return rx >= LOCK_X && rx < LOCK_X + LOCK_W && ry >= LOCK_BASELINE - lockH && ry < LOCK_BASELINE;
	}

	private boolean isOverRow(double mouseX, double mouseY, int row) {
		double rx = mouseX - screen.left();
		double ry = mouseY - screen.top() - LIST_TOP - row * ROW_H;
		return rx >= CONTENT_LEFT + 1 && rx < CONTENT_RIGHT - 1 && ry >= 0 && ry < ROW_H;
	}

	/** True when the field holds something other than the point's stored name — i.e. there is an edit. */
	private boolean isRenameMeaningful() {
		TeleportPoint point = screen.getMenu().points().get(screen.getMenu().getSelected());
		if (point == null || nameBox == null) {
			return false;
		}
		// The same clamp the server applies, so a name differing only by whitespace does not light the button.
		return !TeleportPoint.clampName(nameBox.getValue()).equals(point.name());
	}

	/** Selects a station as a click on its row does; the name field and the padlock follow on the next tick. */
	public void select(int index) {
		TeleporterRemoteMenu menu = screen.getMenu();
		menu.setSelected(index);
		screen.rememberStation(index);
		screen.press(TeleporterRemoteMenu.Action.SELECT, index);
		showSelection(index);
	}

	/** The field takes the selected point's name and the padlock shuts: an unlock is for the row it was opened on. */
	private void showSelection(int index) {
		shownSelection = index;
		deleteUnlocked = false;
		TeleportPoint point = screen.getMenu().points().get(index);
		shownPoint = point;
		if (nameBox != null) {
			// The raw name, not the display one: an auto-named point shows an empty box, and renaming it back to empty is
			// how a player gets the default back.
			nameBox.setValue(point != null ? point.name() : "");
		}
	}

	private void press(TeleporterRemoteMenu.Action action) {
		int index = screen.getMenu().getSelected();
		if (index >= 0) {
			screen.press(action, index);
		}
	}

	/** Delete only ever runs through here, so it cannot fire while the padlock is shut. */
	private void confirmDelete() {
		if (!deleteUnlocked) {
			return;
		}
		press(TeleporterRemoteMenu.Action.DELETE);
		deleteUnlocked = false;
	}

	private void sendRename() {
		int index = screen.getMenu().getSelected();
		if (index >= 0 && nameBox != null) {
			screen.sendRename(index, nameBox.getValue());
		}
	}
}
