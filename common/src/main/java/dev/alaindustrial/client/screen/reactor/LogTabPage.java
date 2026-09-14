package dev.alaindustrial.client.screen.reactor;

import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.ReactorControllerScreen;
import dev.alaindustrial.core.structure.ReactorLog;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.network.ReactorLogPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * The reactor's «Log» tab (MOD-622): what happened to the reactor, newest first, with how long ago.
 *
 * <p><b>Nothing is judged here.</b> The server decides what is an event and writes it once, on the transition; the page
 * lists, filters and scrolls. Its one message back is "I have shown my player the newest lines", which clears that
 * player's badge — and only theirs.
 */
public final class LogTabPage implements ReactorTabPage {

	private static final String KEY = "gui.alaindustrial.reactor_controller.";

	// ── Geometry, relative to the panel. Public so an L3 stand can crop a band from the same numbers. ──
	public static final int FILTER_Y = 22;
	private static final int FILTER_H = 14;
	private static final int ALL_W = 52;
	private static final int ATTENTION_W = 72;
	public static final int LIST_Y = 40;
	public static final int ROW_H = 11;
	public static final int ROWS = 12;
	private static final int SCROLLBAR_W = 3;
	private static final int PLATE = 5;
	private static final int TOOLTIP_WIDTH = 200;

	private static final int LIST_BACK = 0xFF2A2D33;
	private static final int ROW_STRIPE = 0xFF31353C;
	private static final int TEXT_ON_DARK = 0xFFD7DBE0;
	private static final int TEXT_ON_DARK_DIM = 0xFF9AA3AB;
	private static final int THUMB = 0xFF8B939B;

	private final ReactorControllerScreen screen;
	private boolean shown;
	private boolean attentionOnly;
	private int scroll;
	/** The newest entry this page has already told the server it showed; stops a click being sent every tick. */
	private int ackedSeq;
	private @Nullable Button all;
	private @Nullable Button attention;

	public LogTabPage(ReactorControllerScreen screen) {
		this.screen = screen;
	}

	@Override
	public Component title() {
		return Component.translatable(KEY + "tab.log");
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.WRITABLE_BOOK);
	}

	/** Red while an alarm this player has not seen waits in the log. */
	@Override
	public int badgeColour() {
		ReactorLogPayload log = screen.getMenu().log();
		return log != null && ReactorLog.unseenAlarm(log.entries(), Math.max(log.seenSeq(), ackedSeq))
				? ReactorPageText.FILL_RED : 0;
	}

	@Override
	public void init() {
		int x = screen.left() + ConsoleTabPage.CONTENT_LEFT;
		int y = screen.top() + FILTER_Y;
		all = screen.addPageWidget(Button.builder(Component.translatable(KEY + "log.filter.all"),
				button -> showAttentionOnly(false)).bounds(x, y, ALL_W, FILTER_H).build());
		attention = screen.addPageWidget(Button.builder(Component.translatable(KEY + "log.filter.attention"),
				button -> showAttentionOnly(true)).bounds(x + ALL_W + 2, y, ATTENTION_W, FILTER_H).build());
		updateControls();
	}

	@Override
	public void setShown(boolean shown) {
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

	/** Shows every entry, or only the warnings and alarms — for the filter buttons and for a stand. */
	public void showAttentionOnly(boolean attentionOnly) {
		this.attentionOnly = attentionOnly;
		this.scroll = 0;
		updateControls();
	}

	/** The pressed filter is the disabled one: a vanilla toggle read at a glance. */
	private void updateControls() {
		if (all != null) {
			all.visible = shown;
			all.active = shown && attentionOnly;
		}
		if (attention != null) {
			attention.visible = shown;
			attention.active = shown && !attentionOnly;
		}
	}

	/**
	 * Tells the server this player has seen the log up to the newest entry this page holds, once per newer entry, while
	 * the page is showing — and only once the screen has settled on its opening tab, which for a broken room is not this
	 * one. The server takes the lower of that number and the newest it sent, so a line that arrives later stays unseen.
	 */
	private void acknowledge() {
		ReactorLogPayload log = screen.getMenu().log();
		if (!shown || log == null || !screen.openingPageSettled()) {
			return;
		}
		int newest = log.newestSeq();
		if (newest > Math.max(log.seenSeq(), ackedSeq)) {
			ackedSeq = newest;
			screen.sendButton(ReactorControllerMenu.logSeenButton(newest));
		}
	}

	/** The entries shown: newest first, and only those needing attention when that filter is on. */
	private List<ReactorLog.Entry> visible(ReactorLogPayload log) {
		List<ReactorLog.Entry> entries = log.entries();
		List<ReactorLog.Entry> out = new ArrayList<>();
		for (int i = entries.size() - 1; i >= 0; i--) {
			ReactorLog.Entry entry = entries.get(i);
			if (!attentionOnly || entry.kind().severity().needsAttention()) {
				out.add(entry);
			}
		}
		return out;
	}

	// ── Drawing ──────────────────────────────────────────────────────────────────────────────────

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Font font = screen.font();
		int left = screen.left() + ConsoleTabPage.CONTENT_LEFT;
		int right = screen.left() + ConsoleTabPage.CONTENT_RIGHT;
		int top = screen.top() + LIST_Y;
		int bottom = top + ROWS * ROW_H;
		graphics.fill(left, top - 1, right, bottom + 1, ReactorPageText.PLATE_EDGE);
		graphics.fill(left + 1, top, right - 1, bottom, LIST_BACK);

		ReactorLogPayload log = screen.getMenu().log();
		if (log == null) {
			centred(graphics, Component.translatable(KEY + "log.waiting"), left, right, top, bottom);
			return;
		}
		List<ReactorLog.Entry> entries = visible(log);
		Component count = Component.translatable(KEY + "log.count", entries.size());
		int countW = Math.round(font.width(count) * ReactorPageText.SMALL);
		ReactorPageText.scaled(graphics, font, count, right - countW, screen.top() + FILTER_Y + 4, ReactorPageText.SMALL,
				GuiStyle.TEXT_DIM);
		if (entries.isEmpty()) {
			centred(graphics, Component.translatable(KEY + (attentionOnly ? "log.empty_attention" : "log.empty")), left,
					right, top, bottom);
			return;
		}
		scroll = Math.max(0, Math.min(scroll, entries.size() - ROWS));
		long now = gameTime();
		int textRight = right - 4 - SCROLLBAR_W;
		for (int row = 0; row < ROWS && scroll + row < entries.size(); row++) {
			ReactorLog.Entry entry = entries.get(scroll + row);
			int y = top + row * ROW_H;
			if (row % 2 == 1) {
				graphics.fill(left + 1, y, right - 1, y + ROW_H, ROW_STRIPE);
			}
			int plateY = y + (ROW_H - PLATE) / 2;
			graphics.fill(left + 3, plateY, left + 3 + PLATE, plateY + PLATE, ReactorPageText.PLATE_EDGE);
			graphics.fill(left + 4, plateY + 1, left + 2 + PLATE, plateY + PLATE - 1, severityColour(entry.kind().severity()));
			Component ago = ageText(now - entry.time());
			int agoW = Math.round(font.width(ago) * ReactorPageText.SMALL);
			ReactorPageText.scaled(graphics, font, ago, textRight - agoW, y + 2, ReactorPageText.SMALL, TEXT_ON_DARK_DIM);
			int textLeft = left + 6 + PLATE;
			ReactorPageText.small(graphics, font, eventText(entry), textLeft, y + 2,
					Math.max(1, textRight - agoW - 4 - textLeft), TEXT_ON_DARK);
		}
		if (entries.size() > ROWS) {
			int track = ROWS * ROW_H;
			int thumb = Math.max(6, track * ROWS / entries.size());
			int thumbY = top + (track - thumb) * scroll / (entries.size() - ROWS);
			graphics.fill(right - 1 - SCROLLBAR_W, thumbY, right - 1, thumbY + thumb, THUMB);
		}
	}

	private void centred(GuiGraphicsExtractor graphics, Component text, int left, int right, int top, int bottom) {
		ReactorPageText.centred(graphics, screen.font(), text, left, (top + bottom) / 2 - 4, right - left);
	}

	private static int severityColour(ReactorLog.Severity severity) {
		return switch (severity) {
			case INFO -> ReactorPageText.FILL_IDLE;
			case WARNING -> ReactorPageText.FILL_AMBER;
			case ALARM -> ReactorPageText.FILL_RED;
		};
	}

	/** The line for an entry, built in the player's language from its kind and numbers. */
	static Component eventText(ReactorLog.Entry entry) {
		String id = switch (entry.kind()) {
			case DEPTH_CHANGED -> entry.actor().isEmpty() ? "depth_changed" : "depth_changed_by";
			// A bare pile has no heat: its siren sounds on instability, and the line says which scale it was.
			case OVERHEAT -> entry.b() == 1 ? "overheat_bare" : "overheat";
			default -> entry.kind().id();
		};
		return Component.translatable(KEY + "log.event." + id, entry.a(), entry.b(), entry.c(), entry.actor());
	}

	/** "How long ago", in the largest whole unit. */
	static Component ageText(long ticks) {
		ReactorLog.Age age = ReactorLog.Age.of(ticks);
		return age.unit() == ReactorLog.Age.Unit.NOW
				? Component.translatable(KEY + "log.ago.now")
				: Component.translatable(KEY + "log.ago." + age.unit().name().toLowerCase(Locale.ROOT), age.value());
	}

	/** The client's copy of game time: the server syncs it every second, and it stands still while the game is paused. */
	private static long gameTime() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0 : minecraft.level.getGameTime();
	}

	// ── Input ────────────────────────────────────────────────────────────────────────────────────

	private boolean overList(double mouseX, double mouseY) {
		int left = screen.left() + ConsoleTabPage.CONTENT_LEFT;
		int right = screen.left() + ConsoleTabPage.CONTENT_RIGHT;
		int top = screen.top() + LIST_Y;
		return mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + ROWS * ROW_H;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (scrollY == 0 || !overList(mouseX, mouseY)) {
			return false;
		}
		scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
		return true;
	}

	@Override
	public boolean tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ReactorLogPayload log = screen.getMenu().log();
		if (log == null || !overList(mouseX, mouseY)) {
			return false;
		}
		List<ReactorLog.Entry> entries = visible(log);
		int index = scroll + (mouseY - (screen.top() + LIST_Y)) / ROW_H;
		if (index < 0 || index >= entries.size()) {
			return false;
		}
		ReactorLog.Entry entry = entries.get(index);
		List<Component> lines = new ArrayList<>();
		lines.add(eventText(entry));
		lines.add(ageText(gameTime() - entry.time()));
		if (!entry.actor().isEmpty()) {
			lines.add(Component.translatable(KEY + "log.by", entry.actor()));
		}
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) {
			wrapped.addAll(screen.font().split(line, TOOLTIP_WIDTH));
		}
		graphics.setTooltipForNextFrame(screen.font(), wrapped, mouseX, mouseY);
		return true;
	}
}
