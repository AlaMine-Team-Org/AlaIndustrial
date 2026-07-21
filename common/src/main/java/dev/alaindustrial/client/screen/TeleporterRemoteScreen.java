package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.TeleportPoint;
import dev.alaindustrial.item.TeleportPoints;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.TeleportRenamePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;
import dev.alaindustrial.client.hud.TeleportNotice;

/**
 * The remote's screen (MOD-093): the list of stations, and what you can do with one.
 *
 * <p>Every coordinate below was read by scanning the PNG, not judged by eye. The list well's
 * interior is exactly x 9..190, y 24..130 (the mid-grey 139,139,139 field inside the purple rule),
 * and the name recess's interior is x 80..193, y 140..151. If the art moves, re-scan — a highlight
 * that runs from one purple rule to the other is what nudging by eye produced last time.
 *
 * <p>The well fits {@link #VISIBLE_ROWS} rows, while a remote may hold up to 16 stations — hence the
 * scroll wheel. Rows are drawn by hand rather than as widgets so they sit inside the art's well
 * instead of on top of it.
 *
 * <p>Nothing here decides anything: a click sends the server an index and the server re-reads the
 * real remote. Selection is cosmetic.
 */
public class TeleporterRemoteScreen extends AbstractContainerScreen<TeleporterRemoteMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/teleporter_remote.png");
	private static final int TEX_SIZE = 256;

	private static final int PANEL_W = 200, PANEL_H = 190;
	/** List well interior, scanned off the art: the field inside the purple rules, not including them. */
	private static final int LIST_X = 9, LIST_Y = 24, LIST_W = 182, LIST_H = 107;
	private static final int ROW_H = 21;
	/** How many rows fit the well; the rest are reached by scrolling. */
	private static final int VISIBLE_ROWS = LIST_H / ROW_H;
	/** Name recess interior, scanned off the art (the 23,23,23 hollow). */
	private static final int NAME_X = 80, NAME_Y = 140, NAME_W = 114, NAME_H = 12;
	/** Text padding inside a row, left and right. */
	private static final int ROW_PAD = 4;

	/** Buttons along the bottom. Delete is narrowed from 92 to make room for its padlock. */
	private static final int BTN_ROW_Y = 162, BTN_H = 20;
	private static final int DELETE_X = 5, DELETE_W = 78;
	private static final int TELEPORT_X = 103, TELEPORT_W = 92;

	/**
	 * The padlock guarding Delete — the same two sprites, at the same atlas coordinates, as the
	 * station's privacy lock. Sitting next to Delete rather than beside the whole row: it guards that
	 * one button, and a lock that seemed to cover Teleport too would be a lie.
	 */
	private static final int LOCK_W = 10;
	private static final int LOCK_CLOSED_U = 96, LOCK_CLOSED_V = 243, LOCK_CLOSED_H = 13;
	private static final int LOCK_OPEN_U = 112, LOCK_OPEN_V = 241, LOCK_OPEN_H = 15;
	private static final int LOCK_X = DELETE_X + DELETE_W + 3;
	private static final int LOCK_BASELINE = BTN_ROW_Y + 17;

	// The well is mid-grey (139,139,139), so the text on it is dark — light-grey-on-grey was the
	// unreadable combination the player hit. Hover and selection lighten the row rather than dye it,
	// which keeps that same dark text legible on top of them.
	private static final int ROW_HOVER = 0x40FFFFFF;
	private static final int ROW_SELECTED = 0x66D9B8FF;
	private static final int ROW_TEXT = 0xFF101010;
	private static final int ROW_TEXT_DIM = 0xFF3F3F3F;

	/** Refusal banner across the bottom of the well — opaque so it reads over whatever row is under it. */
	private static final int NOTICE_H = 13;
	private static final int NOTICE_BG = 0xF0501010;
	private static final int NOTICE_TEXT = 0xFFFFC9C9;

	@Nullable
	private EditBox nameBox;
	@Nullable
	private Button renameButton;
	@Nullable
	private Button deleteButton;
	@Nullable
	private Button teleportButton;
	/** First visible row — the scroll position. */
	private int scroll;
	/**
	 * Whether Delete is armed. Deleting a point loses the only record of where home was — the player
	 * has no coordinates to walk back to — so it costs a deliberate second click, exactly like the
	 * station's privacy switch. Re-locks after a delete and whenever the selection moves, so an
	 * unlock can never be spent on a row the player did not mean.
	 */
	private boolean deleteUnlocked;
	/** Whether the opening auto-selection has happened; it must fire once, not every tick. */
	private boolean autoSelected;

	public TeleporterRemoteScreen(TeleporterRemoteMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PANEL_W, PANEL_H);
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = 8;
		// A refusal from a previous screen has nothing to say about this one.
		TeleportNotice.clear();

		// An unbordered EditBox does NOT centre its text — verified in 26.2: `textY = bordered ?
		// getY() + (height - 8) / 2 : getY()` (EditBox#updateTextPosition). So the widget's own y IS
		// the text's baseline, and centring an 8px line in the 12px recess is ours to do: 140 + 2.
		nameBox = new EditBox(this.font, this.leftPos + NAME_X + 2, this.topPos + NAME_Y + 2,
				NAME_W - 4, NAME_H - 4, Component.translatable("gui.alaindustrial.teleporter.name"));
		nameBox.setMaxLength(TeleportPoint.MAX_NAME_LENGTH);
		nameBox.setBordered(false);
		addRenderableWidget(nameBox);

		renameButton = addRenderableWidget(Button.builder(
				Component.translatable("gui.alaindustrial.teleporter.rename"), b -> sendRename())
				.bounds(this.leftPos + 5, this.topPos + 136, 70, 20).build());
		deleteButton = addRenderableWidget(Button.builder(
				Component.translatable("gui.alaindustrial.teleporter.delete"), b -> confirmDelete())
				.bounds(this.leftPos + DELETE_X, this.topPos + BTN_ROW_Y, DELETE_W, BTN_H).build());
		teleportButton = addRenderableWidget(Button.builder(
				Component.translatable("gui.alaindustrial.teleporter.teleport"),
				b -> press(TeleporterRemoteMenu.Action.TELEPORT))
				.bounds(this.leftPos + TELEPORT_X, this.topPos + BTN_ROW_Y, TELEPORT_W, BTN_H).build());
	}

	/**
	 * Title only — this screen has no player inventory, so vanilla's second label has nothing to name.
	 *
	 * <p>Parking {@code inventoryLabelY} off the panel is not enough: vanilla draws the label wherever
	 * it is told, and the panel's own edge is still on screen. The label has to not be drawn.
	 */
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, GuiStyle.TEXT, false);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, PANEL_W, PANEL_H, TEX_SIZE, TEX_SIZE);

		TeleportPoints points = this.menu.points();
		if (points.isEmpty()) {
			Component empty = Component.translatable("gui.alaindustrial.teleporter.no_points");
			graphics.text(this.font, empty, x + LIST_X + (LIST_W - this.font.width(empty)) / 2,
					y + LIST_Y + 44, ROW_TEXT_DIM, false);
			return;
		}

		int selected = this.menu.getSelected();
		for (int i = 0; i < VISIBLE_ROWS; i++) {
			int index = scroll + i;
			if (index >= points.size()) {
				break;
			}
			TeleportPoint point = points.get(index);
			int rowY = y + LIST_Y + i * ROW_H;
			boolean hovered = isOverRow(mouseX, mouseY, i);
			if (index == selected) {
				graphics.fill(x + LIST_X, rowY, x + LIST_X + LIST_W, rowY + ROW_H - 1, ROW_SELECTED);
			} else if (hovered) {
				graphics.fill(x + LIST_X, rowY, x + LIST_X + LIST_W, rowY + ROW_H - 1, ROW_HOVER);
			}
			// Clip by measured width, not by character count: a name of CJK glyphs is roughly twice as
			// wide per character as a Latin one, so any character-based cap either overflows the well in
			// Chinese or wastes half of it in English.
			graphics.text(this.font, clipToRow(point.displayName().getString()),
					x + LIST_X + ROW_PAD, rowY + 3, ROW_TEXT, false);
			Component coords = Component.translatable("gui.alaindustrial.teleporter.coords",
					point.pos().getX(), point.pos().getY(), point.pos().getZ());
			graphics.text(this.font, coords, x + LIST_X + ROW_PAD, rowY + 12, ROW_TEXT_DIM, false);
		}

		drawDeleteLock(graphics, x, y);
		drawNotice(graphics, x, y);

		// Only say there is more when there is: a scrollbar hint on an unscrollable list is a lie.
		if (points.size() > VISIBLE_ROWS) {
			Component more = Component.translatable("gui.alaindustrial.teleporter.more",
					scroll + Math.min(VISIBLE_ROWS, points.size() - scroll), points.size());
			graphics.text(this.font, more, x + LIST_X + LIST_W - this.font.width(more) - 2,
					y + LIST_Y + LIST_H - 9, ROW_TEXT_DIM, false);
		}
	}

	/**
	 * Why the last press did nothing, banded across the bottom of the well.
	 *
	 * <p>Inside the panel, because that is the one place the player is looking. The action bar is
	 * underneath this screen, which is how "I pressed Teleport and nothing happened" happened.
	 * Self-expiring ({@link TeleportNotice}), so a solved problem stops being reported.
	 */
	private void drawNotice(GuiGraphicsExtractor graphics, int x, int y) {
		Component notice = TeleportNotice.current();
		if (notice == null) {
			return;
		}
		int top = y + LIST_Y + LIST_H - NOTICE_H;
		graphics.fill(x + LIST_X, top, x + LIST_X + LIST_W, y + LIST_Y + LIST_H, NOTICE_BG);
		String line = clipToRow(notice.getString());
		graphics.text(this.font, Component.literal(line),
				x + LIST_X + (LIST_W - this.font.width(line)) / 2, top + 3, NOTICE_TEXT, false);
	}

	/** The padlock, drawn from the same sprites the station's privacy switch uses. */
	private void drawDeleteLock(GuiGraphicsExtractor graphics, int x, int y) {
		int lockH = deleteUnlocked ? LOCK_OPEN_H : LOCK_CLOSED_H;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + LOCK_X, y + LOCK_BASELINE - lockH,
				(float) (deleteUnlocked ? LOCK_OPEN_U : LOCK_CLOSED_U),
				(float) (deleteUnlocked ? LOCK_OPEN_V : LOCK_CLOSED_V),
				LOCK_W, lockH, TEX_SIZE, TEX_SIZE);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && isOverLock(event.x(), event.y())) {
			deleteUnlocked = !deleteUnlocked;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
					deleteUnlocked ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, 1.0F));
			return true;
		}
		if (event.button() == 0) {
			TeleportPoints points = this.menu.points();
			for (int i = 0; i < VISIBLE_ROWS && scroll + i < points.size(); i++) {
				if (isOverRow(event.x(), event.y(), i)) {
					select(scroll + i);
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean isOverLock(double mouseX, double mouseY) {
		double rx = mouseX - this.leftPos;
		double ry = mouseY - this.topPos;
		int lockH = deleteUnlocked ? LOCK_OPEN_H : LOCK_CLOSED_H;
		return rx >= LOCK_X && rx < LOCK_X + LOCK_W
				&& ry >= LOCK_BASELINE - lockH && ry < LOCK_BASELINE;
	}

	/** The well shows {@link #VISIBLE_ROWS} of up to 16 stations, so the wheel moves the window. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, this.menu.points().size() - VISIBLE_ROWS);
		if (max > 0 && scrollY != 0) {
			scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** True when the field holds something other than the point's stored name — i.e. there is an edit. */
	private boolean isRenameMeaningful() {
		TeleportPoint point = this.menu.points().get(this.menu.getSelected());
		if (point == null || nameBox == null) {
			return false;
		}
		// Compare against the same clamp the server will apply, so a name differing only by whitespace
		// does not light the button up for what would be a no-op.
		return !TeleportPoint.clampName(nameBox.getValue()).equals(point.name());
	}

	/**
	 * Trim a name to what the row can actually show, adding an ellipsis when it had to cut.
	 *
	 * <p>{@code plainSubstrByWidth} is vanilla's own measurer (it is what {@code EditBox} scrolls
	 * with), so it counts a CJK glyph as the ~2× it draws and never splits a surrogate pair — an
	 * emoji or a Chinese name cannot land half-cut, whatever the locale.
	 */
	private String clipToRow(String name) {
		int room = LIST_W - ROW_PAD * 2;
		if (this.font.width(name) <= room) {
			return name;
		}
		String ellipsis = "…";
		return this.font.plainSubstrByWidth(name, room - this.font.width(ellipsis)) + ellipsis;
	}

	private boolean isOverRow(double mouseX, double mouseY, int row) {
		double rx = mouseX - this.leftPos;
		double ry = mouseY - this.topPos - LIST_Y - row * ROW_H;
		return rx >= LIST_X && rx < LIST_X + LIST_W && ry >= 0 && ry < ROW_H - 1;
	}

	private void select(int index) {
		this.menu.setSelected(index);
		// Moving to another row re-locks: an unlock is for the point the player was looking at when
		// they opened it, and must not carry over onto whatever they click next.
		deleteUnlocked = false;
		press(TeleporterRemoteMenu.Action.SELECT, index);
		TeleportPoint point = this.menu.points().get(index);
		if (nameBox != null && point != null) {
			// The raw name, not the display one: an auto-named point shows an empty box, and renaming
			// it back to empty is how a player gets the default back. Pre-filling "Teleporter 1" would
			// let one stray click freeze that default as a literal, in one language, forever.
			nameBox.setValue(point.name());
		}
	}

	private void press(TeleporterRemoteMenu.Action action) {
		int index = this.menu.getSelected();
		if (index >= 0) {
			press(action, index);
		}
	}

	/** Delete only ever runs through here, so it cannot fire while the padlock is shut. */
	private void confirmDelete() {
		if (!deleteUnlocked) {
			return;
		}
		press(TeleporterRemoteMenu.Action.DELETE);
		deleteUnlocked = false; // snap shut behind the deletion
	}

	/** Vanilla's container-button packet — no mod networking needed for an action that is just ints. */
	private void press(TeleporterRemoteMenu.Action action, int index) {
		this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
				TeleporterRemoteMenu.buttonId(action, index));
	}

	/**
	 * Rename is the one action carrying a string, so it is the one that needs the mod's own C2S
	 * payload — a button id holds an int and nothing more.
	 */
	private void sendRename() {
		int index = this.menu.getSelected();
		if (index < 0 || nameBox == null) {
			return;
		}
		NetworkDispatcher.get().sendToServer(new TeleportRenamePayload(index, nameBox.getValue()));
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		TeleportPoints points = this.menu.points();

		// Land on the first station so the common case — open, jump home — is two clicks, not three.
		// Done here rather than in init() because the menu is not populated until the first tick, and
		// only once: re-selecting every tick would fight the player's own clicks.
		if (!autoSelected && !points.isEmpty()) {
			autoSelected = true;
			select(0);
		}

		// The server owns the list: a delete can shrink it under us.
		int max = Math.max(0, points.size() - VISIBLE_ROWS);
		if (scroll > max) {
			scroll = max;
		}
		if (this.menu.getSelected() >= points.size()) {
			this.menu.setSelected(-1);
		}
		boolean hasSelection = this.menu.getSelected() >= 0;
		if (renameButton != null) {
			// Live only when pressing it would actually change something. "Field differs from the stored
			// name" rather than "field is non-empty": an empty field on a named point is a real edit —
			// it is how a player hands the point back to auto-naming — while an empty field on an
			// already-auto point is the no-op the player was complaining about.
			renameButton.active = hasSelection && nameBox != null && isRenameMeaningful();
		}
		if (deleteButton != null) {
			// Greyed until the padlock is open, so the guard is visible rather than a silent no-op.
			deleteButton.active = hasSelection && deleteUnlocked;
		}
		if (teleportButton != null) {
			teleportButton.active = hasSelection;
		}
	}
}
