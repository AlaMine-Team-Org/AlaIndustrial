package dev.alaindustrial.client.screen.teleporter;

import dev.alaindustrial.client.ReadoutFormat;
import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.TeleporterRemoteScreen;
import dev.alaindustrial.client.screen.tabs.PageText;
import dev.alaindustrial.client.screen.tabs.TabPage;
import dev.alaindustrial.core.teleport.RemoteLog;
import dev.alaindustrial.teleporter.TeleportEngine;
import dev.alaindustrial.teleporter.TeleportLogs;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The remote's «Log» tab (MOD-631): what happened to jumps made with this remote, newest first — the approved mockup's
 * frames Zh and Z.
 *
 * <p><b>The log is read off the remote itself.</b> It is a component synced with the stack, so there is no packet to
 * wait for. The page's one message back is "I have shown my owner the newest lines", which puts the tab's badge out.
 *
 * <p><b>New lines stay marked for the whole look.</b> The page tells the server it has shown the log as soon as it is
 * open, so what counts as new is fixed when the tab opens: those lines keep their dot until the screen closes.
 */
public final class LogTabPage implements TabPage {

	private static final String P = "gui.alaindustrial.teleporter_remote.";

	// ── Geometry, relative to the panel, from the approved mockup (frames Zh and Z) ──
	private static final int FILTER_Y = 22, FILTER_H = 14;
	private static final int ALL_X = 8, ALL_W = 40;
	private static final int JUMPS_X = 50, JUMPS_W = 50;
	private static final int REFUSALS_X = 102, REFUSALS_W = 64;
	private static final int COUNT_RIGHT = 228, COUNT_Y = 26;
	private static final int LIST_X = 8, LIST_W = 220, LIST_Y = 40;
	public static final int ROW_H = 11;
	public static final int ROWS = 11;
	private static final int LAMP_X = 13;
	private static final int TEXT_X = 21;
	private static final int TEXT_RIGHT = 224;
	private static final int SCROLLBAR_X = 223, SCROLLBAR_W = 3;
	private static final int FOOTER_X = 8, FOOTER_Y = 166, FOOTER_W = 220;
	private static final int LEGEND_DOT_X = 9, LEGEND_DOT_Y = 177, LEGEND_X = 15, LEGEND_Y = 175, LEGEND_W = 200;
	private static final int TOOLTIP_WIDTH = 170;

	private static final float SMALL = 0.75f;
	/** A row's text shrinks this far before it is cut. */
	private static final float ROW_MIN_SCALE = 0.5f;

	private static final int WELL_EDGE = 0xFF111316;
	private static final int WELL_BACK = 0xFF2A2D33;
	private static final int ROW_STRIPE = 0xFF31353C;
	private static final int ON_DARK = 0xFFD7DBE0;
	private static final int ON_DARK_DIM = 0xFF9AA3AB;
	private static final int UNREAD_TEXT = 0xFFFFFFFF;
	private static final int UNREAD_TIME = 0xFFDDE2E7;
	/**
	 * The "new" dot. White rather than the mockup's amber: amber already means a warning lamp at the row's other end, and
	 * on a recharge refusal two amber squares read as one mark (MOD-631, open question 1).
	 */
	private static final int UNREAD_DOT = 0xFFFFFFFF;
	private static final int THUMB = 0xFF8B939B;
	private static final int FILL_GREEN = 0xFF4E9E52;
	private static final int FILL_AMBER = 0xFFD9A33A;
	private static final int FILL_RED = 0xFFD63A2A;
	private static final int FILL_IDLE = 0xFF6B7178;

	private final TeleporterRemoteScreen screen;
	private final ItemStack icon = new ItemStack(Items.WRITABLE_BOOK);
	private boolean shown;
	/** The filter in force; {@code null} shows everything. */
	private RemoteLog.@Nullable Group filter;
	private int scroll;
	/** The read mark when the tab was opened: lines past it are marked new for the whole look. */
	private int seenWhenOpened;
	/** The newest line this page has already reported as shown; stops a click being sent every tick. */
	private int ackedSeq;
	private @Nullable Button all;
	private @Nullable Button jumps;
	private @Nullable Button refusals;

	public LogTabPage(TeleporterRemoteScreen screen) {
		this.screen = screen;
	}

	@Override
	public Component title() {
		return Component.translatable(P + "tab.log");
	}

	@Override
	public ItemStack icon() {
		return icon;
	}

	/** Amber while a refusal the owner has not seen waits in the log; cancellations and edits do not count. */
	@Override
	public int badgeColour() {
		RemoteLog log = screen.getMenu().log();
		return log.hasRefusalAfter(Math.max(log.seenSeq(), ackedSeq)) ? FILL_AMBER : 0;
	}

	@Override
	public void init() {
		int x = screen.left();
		int y = screen.top() + FILTER_Y;
		all = screen.addPageWidget(Button.builder(Component.translatable(P + "log.filter.all"), b -> showOnly(null))
				.bounds(x + ALL_X, y, ALL_W, FILTER_H).build());
		jumps = screen.addPageWidget(Button.builder(Component.translatable(P + "log.filter.jumps"),
				b -> showOnly(RemoteLog.Group.JUMP)).bounds(x + JUMPS_X, y, JUMPS_W, FILTER_H).build());
		refusals = screen.addPageWidget(Button.builder(Component.translatable(P + "log.filter.refusals"),
				b -> showOnly(RemoteLog.Group.REFUSAL)).bounds(x + REFUSALS_X, y, REFUSALS_W, FILTER_H).build());
		updateControls();
	}

	@Override
	public void setShown(boolean shown) {
		if (shown && !this.shown) {
			seenWhenOpened = screen.getMenu().log().seenSeq();
		}
		this.shown = shown;
		updateControls();
	}

	@Override
	public void tick() {
		updateControls();
		acknowledge();
	}

	@Override
	public void mouseReleased(MouseButtonEvent event) {
	}

	/** Shows one group, or everything for {@code null} — for the filter buttons and for a stand. */
	public void showOnly(RemoteLog.@Nullable Group group) {
		this.filter = group;
		this.scroll = 0;
		updateControls();
	}

	/** The pressed filter is the disabled one, as on the reactor's log. */
	private void updateControls() {
		setFilterButton(all, null);
		setFilterButton(jumps, RemoteLog.Group.JUMP);
		setFilterButton(refusals, RemoteLog.Group.REFUSAL);
	}

	private void setFilterButton(@Nullable Button button, RemoteLog.@Nullable Group group) {
		if (button != null) {
			button.visible = shown;
			button.active = shown && filter != group;
		}
	}

	/** Reports the newest line as shown, once per newer line and only while the tab is open. */
	private void acknowledge() {
		if (!shown) {
			return;
		}
		RemoteLog log = screen.getMenu().log();
		int newest = log.newestSeq();
		if (newest > Math.max(log.seenSeq(), ackedSeq)) {
			ackedSeq = newest;
			screen.sendButton(TeleportLogs.SEEN_BUTTON + newest);
		}
	}

	// ── Drawing ──────────────────────────────────────────────────────────────────────────────────

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Font font = screen.font();
		int x = screen.left();
		int y = screen.top();
		RemoteLog log = screen.getMenu().log();
		List<RemoteLog.Entry> entries = log.newestFirst(filter);

		Component count = Component.translatable(P + countKey(filter), entries.size());
		int countW = Math.round(font.width(count) * SMALL);
		PageText.scaled(graphics, font, count, x + COUNT_RIGHT - countW, y + COUNT_Y, SMALL, GuiStyle.TEXT_DIM);

		int left = x + LIST_X;
		int right = left + LIST_W;
		int top = y + LIST_Y;
		int bottom = top + ROWS * ROW_H;
		graphics.fill(left, top - 1, right, bottom + 1, WELL_EDGE);
		graphics.fill(left + 1, top, right - 1, bottom, WELL_BACK);

		scroll = Math.max(0, Math.min(scroll, entries.size() - ROWS));
		boolean scrolls = entries.size() > ROWS;
		int textRight = x + TEXT_RIGHT - (scrolls ? 4 : 0);
		long now = gameTime();
		boolean anyNew = false;
		for (int row = 0; row < ROWS; row++) {
			int rowY = top + row * ROW_H;
			if (row % 2 == 1) {
				graphics.fill(left + 1, rowY, right - 1, rowY + ROW_H, ROW_STRIPE);
			}
			if (scroll + row >= entries.size()) {
				continue;
			}
			RemoteLog.Entry entry = entries.get(scroll + row);
			boolean unread = entry.seq() > seenWhenOpened;
			anyNew |= unread;
			graphics.fill(x + LAMP_X, rowY + 3, x + LAMP_X + 5, rowY + 8, WELL_EDGE);
			graphics.fill(x + LAMP_X + 1, rowY + 4, x + LAMP_X + 4, rowY + 7, toneFill(entry.kind().tone()));
			Component ago = ageText(now - entry.time());
			int agoX = textRight - Math.round(font.width(ago) * SMALL);
			PageText.scaled(graphics, font, ago, agoX, rowY + 2, SMALL, unread ? UNREAD_TIME : ON_DARK_DIM);
			int limit = agoX - 4;
			if (unread) {
				graphics.fill(agoX - 6, rowY + 4, agoX - 3, rowY + 7, UNREAD_DOT);
				limit = agoX - 9;
			}
			fitSmall(graphics, font, eventText(entry), x + TEXT_X, rowY + 2, limit - (x + TEXT_X),
					unread ? UNREAD_TEXT : ON_DARK);
		}
		if (entries.isEmpty()) {
			Component empty = Component.translatable(P + "log.empty");
			int emptyW = Math.round(font.width(empty) * SMALL);
			PageText.scaled(graphics, font, empty, (left + right - emptyW) / 2, (top + bottom) / 2 - 4, SMALL, ON_DARK_DIM);
		}
		if (scrolls) {
			int track = ROWS * ROW_H;
			int thumb = Math.max(6, track * ROWS / entries.size());
			int thumbY = top + (track - thumb) * scroll / (entries.size() - ROWS);
			graphics.fill(x + SCROLLBAR_X, thumbY, x + SCROLLBAR_X + SCROLLBAR_W, thumbY + thumb, THUMB);
		}

		fitSmall(graphics, font, Component.translatable(P + "log.kept", RemoteLog.CAPACITY), x + FOOTER_X, y + FOOTER_Y,
				FOOTER_W, GuiStyle.TEXT_DIM);
		if (anyNew) {
			graphics.fill(x + LEGEND_DOT_X, y + LEGEND_DOT_Y, x + LEGEND_DOT_X + 3, y + LEGEND_DOT_Y + 3, UNREAD_DOT);
			fitSmall(graphics, font, Component.translatable(P + "log.unread"), x + LEGEND_X, y + LEGEND_Y, LEGEND_W,
					GuiStyle.TEXT_DIM);
		}
	}

	private static String countKey(RemoteLog.@Nullable Group group) {
		if (group == null) {
			return "log.count.events";
		}
		return group == RemoteLog.Group.JUMP ? "log.count.jumps" : "log.count.refusals";
	}

	/**
	 * A row's text at the list's size, shrunk to its room — and past {@link #ROW_MIN_SCALE} cut with an ellipsis. A
	 * station name is up to 24 characters, so the longest refusal line has to give somewhere.
	 */
	private static void fitSmall(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width,
			int colour) {
		if (width <= 0) {
			return;
		}
		int textW = font.width(text);
		float scale = SMALL;
		if (textW * scale > width) {
			scale = Math.max(ROW_MIN_SCALE, (float) width / textW);
		}
		Component shown = text;
		if (textW * scale > width) {
			int room = Math.max(0, Math.round(width / scale) - font.width("…"));
			shown = Component.literal(font.plainSubstrByWidth(text.getString(), room) + "…");
		}
		PageText.scaled(graphics, font, shown, x, y, scale, colour);
	}

	private static int toneFill(RemoteLog.Tone tone) {
		return switch (tone) {
			case GOOD -> FILL_GREEN;
			case WARN -> FILL_AMBER;
			case ALARM -> FILL_RED;
			case IDLE -> FILL_IDLE;
		};
	}

	/** A station as the log names it: its own name, or its default name in the reader's language. */
	static Component label(RemoteLog.Station station) {
		return station.name().isEmpty()
				? Component.translatable("alaindustrial.teleporter.default_name", station.number())
				: Component.literal(station.name());
	}

	/** The line for an entry, in the player's language. */
	static Component eventText(RemoteLog.Entry entry) {
		Component station = label(entry.station());
		return switch (entry.kind()) {
			case JUMPED -> tr("log.event.jumped", station, ReadoutFormat.exact(entry.a()));
			case RANDOM_JUMPED -> tr("log.event.rtp_jumped", ReadoutFormat.exact(entry.a()), ReadoutFormat.exact(entry.b()));
			case REFUSED_NO_POWER -> tr("log.event.refused.no_power", station);
			case REFUSED_NO_ACCESS -> tr("log.event.refused.no_access", station);
			case REFUSED_NO_STATION -> tr("log.event.refused.no_station", station);
			case REFUSED_NOT_FORMED -> tr("log.event.refused.not_formed", station);
			case REFUSED_NO_CHIP -> tr("log.event.refused.no_chip", station);
			case REFUSED_COOLDOWN -> tr("log.event.refused.cooldown", entry.a());
			case REFUSED_CROSS_DIM -> tr("log.event.refused.cross_dim", station);
			case REFUSED_WRONG_DIMENSION -> tr("log.event.refused.wrong_dimension");
			case REFUSED_NO_SAFE_SPOT -> tr("log.event.refused.no_safe_spot");
			case REFUSED_MOUNTED -> tr("log.event.refused.mounted");
			case CANCELLED_MOVED -> tr("log.event.cancelled_moved");
			case CANCELLED_HURT -> tr("log.event.cancelled_hurt");
			case BOUND -> tr("log.event.bound", station, entry.a(), entry.b());
			case RENAMED -> tr("log.event.renamed", station, label(entry.renamedTo()));
			case DELETED -> tr("log.event.deleted", station);
			case UNKNOWN -> tr("log.event.unknown");
		};
	}

	/** The server's own words for a refusal — what the player saw when it happened — or {@code null}. */
	static @Nullable Component reason(RemoteLog.Entry entry) {
		return switch (entry.kind()) {
			case REFUSED_NO_POWER -> TeleportEngine.Denial.NOT_ENOUGH_EU.message();
			case REFUSED_NO_ACCESS -> TeleportEngine.Denial.NO_ACCESS.message();
			case REFUSED_NO_STATION -> TeleportEngine.Denial.NO_STATION.message();
			case REFUSED_NOT_FORMED -> TeleportEngine.Denial.NOT_FORMED.message();
			case REFUSED_NO_CHIP -> TeleportEngine.Denial.RTP_NO_MODULE.message();
			case REFUSED_COOLDOWN -> Component.translatable("alaindustrial.teleporter.cooldown", entry.a());
			case REFUSED_CROSS_DIM -> TeleportEngine.Denial.CROSS_DIM.message();
			case REFUSED_WRONG_DIMENSION -> TeleportEngine.Denial.RTP_WRONG_DIMENSION.message();
			case REFUSED_NO_SAFE_SPOT -> TeleportEngine.Denial.RTP_NO_SAFE_SPOT.message();
			case REFUSED_MOUNTED -> TeleportEngine.Denial.MOUNTED.message();
			default -> null;
		};
	}

	/** "How long ago", in the largest whole unit — the shared keys, not the reactor's. */
	static Component ageText(long ticks) {
		dev.alaindustrial.core.structure.ReactorLog.Age age = dev.alaindustrial.core.structure.ReactorLog.Age.of(ticks);
		return age.unit() == dev.alaindustrial.core.structure.ReactorLog.Age.Unit.NOW
				? Component.translatable("gui.alaindustrial.ago.now")
				: Component.translatable("gui.alaindustrial.ago." + age.unit().name().toLowerCase(Locale.ROOT), age.value());
	}

	private static Component tr(String key, Object... args) {
		return Component.translatable(P + key, args);
	}

	/** The client's copy of game time — the same clock in every dimension (all worlds read the overworld's). */
	private static long gameTime() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0 : minecraft.level.getGameTime();
	}

	// ── Input ────────────────────────────────────────────────────────────────────────────────────

	private boolean overList(double mouseX, double mouseY) {
		int left = screen.left() + LIST_X;
		int top = screen.top() + LIST_Y;
		return mouseX >= left && mouseX < left + LIST_W && mouseY >= top && mouseY < top + ROWS * ROW_H;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (scrollY == 0 || !overList(mouseX, mouseY)) {
			return false;
		}
		scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
		return true;
	}

	/** The whole line, the server's reason, and how long ago with which kind of jump — never where the player stood. */
	@Override
	public boolean tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!overList(mouseX, mouseY)) {
			return false;
		}
		List<RemoteLog.Entry> entries = screen.getMenu().log().newestFirst(filter);
		int index = scroll + (mouseY - (screen.top() + LIST_Y)) / ROW_H;
		if (index < 0 || index >= entries.size()) {
			return false;
		}
		RemoteLog.Entry entry = entries.get(index);
		List<Component> lines = new ArrayList<>();
		lines.add(eventText(entry));
		Component reason = reason(entry);
		if (reason != null) {
			lines.add(reason);
		}
		if (entry.kind() == RemoteLog.Kind.RANDOM_JUMPED) {
			lines.add(tr("log.tooltip.rtp_cost", ReadoutFormat.exact(entry.c())));
		}
		Component ago = ageText(gameTime() - entry.time());
		boolean aboutAJump = entry.kind().group() != RemoteLog.Group.OTHER
				|| entry.kind() == RemoteLog.Kind.CANCELLED_MOVED || entry.kind() == RemoteLog.Kind.CANCELLED_HURT;
		Component meta = aboutAJump
				? tr("log.tooltip.meta", ago, tr(entry.random() ? "log.tooltip.kind.rtp" : "log.tooltip.kind.jump"))
				: ago;
		lines.add(meta.copy().withStyle(ChatFormatting.GRAY));
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) {
			wrapped.addAll(screen.font().split(line, TOOLTIP_WIDTH));
		}
		graphics.setTooltipForNextFrame(screen.font(), wrapped, mouseX, mouseY);
		return true;
	}
}
