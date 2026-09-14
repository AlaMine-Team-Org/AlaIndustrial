package dev.alaindustrial.client.screen.reactor;

import dev.alaindustrial.client.ReadoutFormat;
import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.ReactorControllerScreen;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The reactor's «Console» tab (MOD-618): heat, output, coolant, steam, buffer, the rod depth and what to
 * do next.
 *
 * <p><b>It carries everything the old single-page screen did</b>, in the three shapes the reactor can be
 * in — sealed and running, burning bare in the open, or a shell still being built — and adds what the
 * audit of that screen found missing: the heat marks the reactor acts on, the steam the exhaust is
 * choking on, labelled throttle steps, and a sentence telling the player what to do about the state on
 * the chip. A shell still being built gets only its verdict here: the map and the checklist are on the
 * «Room» tab (MOD-619).
 *
 * <p><b>Every row is a label and a value measured separately</b>, the rule the old screen settled on
 * after Russian ran into its pictogram: the value is what the row is read for, so the value keeps its
 * size and the label gives way.
 */
public final class ConsoleTabPage implements ReactorTabPage {

	private static final String KEY = "gui.alaindustrial.reactor_controller.";

	// ── Geometry, relative to the panel. Public so an L3 stand can crop a band from the same numbers. ──
	public static final int CONTENT_LEFT = 8;
	public static final int CONTENT_RIGHT = 228;
	/** The heat headline (or the builder's verdict, or the bare pile's instability). */
	public static final int HEADLINE_Y = 21;
	public static final int HEAT_BAR_Y = 31;
	public static final int HEAT_BAR_H = 8;
	/** The numbers under the heat marks. */
	public static final int MARK_LABEL_Y = 42;
	public static final int ROW_A_Y = 52;
	public static final int ROW_B_Y = 72;
	public static final int RODS_Y = 92;
	/** Height a row claims for its hover area: a text line plus the bar under it. */
	public static final int ROW_H = 16;
	public static final int COLUMN_W = 106;
	public static final int RIGHT_COLUMN_X = 122;
	public static final int ROW_BAR_OFFSET = 10;
	public static final int ROW_BAR_H = 4;
	/** While the shell is open: the button that leads to the «Room» tab. */
	public static final int BUILD_LINK_Y = 36;
	public static final int CONTROLS_Y = 104;
	public static final int CONTROLS_H = 20;
	public static final int SLIDER_W = 164;
	public static final int STOP_X = 176;
	public static final int STOP_W = 52;
	public static final int ADVICE_Y = 128;
	public static final int ADVICE_BOTTOM = 178;

	private static final int TRACK = 0xFF2A2D33;
	private static final int FILL_GREEN = ReactorPageText.FILL_GREEN;
	private static final int FILL_AMBER = ReactorPageText.FILL_AMBER;
	private static final int FILL_RED = ReactorPageText.FILL_RED;
	private static final int FILL_TEAL = 0xFF3E8FA8;
	private static final int FILL_STEAM = 0xFF98A3AB;
	private static final int MARK_GLOW = 0xB0FFFFFF;
	/** Darker cousins of the fills: the same colours legible as text on the light panel. */
	private static final int INK_RED = 0xFFAA2A1A;
	private static final int INK_AMBER = 0xFF8A5A00;

	private static final float MARK_SCALE = 0.75f;
	private static final float ADVICE_SCALE = ReactorPageText.BODY_SCALE;
	private static final float MIN_SCALE = ReactorPageText.MIN_SCALE;
	private static final int LINE_H = ReactorPageText.LINE_H;
	private static final int TOOLTIP_WIDTH = 200;

	/**
	 * How long a requested depth is held on screen without the server confirming it. Two seconds, then the
	 * console shows whatever the server says — a request it clamped or refused must not stay on screen as a
	 * number the reactor is not running at.
	 */
	private static final int PENDING_TIMEOUT_TICKS = 40;

	private final ReactorControllerScreen screen;
	private final ReactorConsole.HeatTrend heatTrend = new ReactorConsole.HeatTrend();
	private @Nullable DepthSlider slider;
	private @Nullable Button stop;
	private @Nullable Button roomLink;
	private boolean shown;
	/** A depth sent to the server and not yet echoed back in the menu. */
	private @Nullable Integer pendingDepth;
	private int pendingTicks;

	public ConsoleTabPage(ReactorControllerScreen screen) {
		this.screen = screen;
	}

	/** The menu's channels as one snapshot, each share kept on its 0…100 scale. */
	public static ReactorConsole.Readout readout(ReactorControllerMenu menu) {
		return new ReactorConsole.Readout(menu.getStatus(), menu.getIdleReason(), menu.getRods(),
				menu.getOutput(), percent(menu.getHeatPercent()), percent(menu.getWaterPercent()),
				percent(menu.getSteamPercent()), percent(menu.getBlastPercent()), menu.isMeltingDown(),
				percent(menu.getInstabilityPercent()), percent(menu.getCoolantSharePercent()), menu.getHeatWarnPercent(),
				menu.getMeltdownStartPercent());
	}

	@Override
	public Component title() {
		return Component.translatable(KEY + "tab.console");
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(ModContent.REACTOR_CONTROLLER.get());
	}

	@Override
	public int badgeColour() {
		return switch (ReactorConsole.verdict(readout(screen.getMenu())).tone()) {
			case ALARM -> FILL_RED;
			case WARN -> FILL_AMBER;
			case GOOD, IDLE -> 0;
		};
	}

	@Override
	public void init() {
		int x = screen.left();
		int y = screen.top();
		int depth = pendingDepth != null ? pendingDepth : screen.getMenu().getDepthPercent();
		DepthSlider depthSlider = new DepthSlider(x + CONTENT_LEFT, y + CONTROLS_Y, SLIDER_W, CONTROLS_H, depth);
		depthSlider.setTooltip(Tooltip.create(Component.translatable(KEY + "tooltip.depth")));
		slider = screen.addPageWidget(depthSlider);
		stop = screen.addPageWidget(Button.builder(Component.translatable(KEY + "button.stop"), button -> {
			depthSlider.show(0);
			commitDepth(0);
		}).bounds(x + STOP_X, y + CONTROLS_Y, STOP_W, CONTROLS_H)
				.tooltip(Tooltip.create(Component.translatable(KEY + "tooltip.stop"))).build());
		roomLink = screen.addPageWidget(Button.builder(Component.translatable(KEY + "button.open_room"),
				button -> screen.selectPage(ReactorControllerScreen.PAGE_ROOM))
				.bounds(x + CONTENT_LEFT, y + BUILD_LINK_Y, CONTENT_RIGHT - CONTENT_LEFT, CONTROLS_H).build());
		updateControls();
	}

	@Override
	public void setShown(boolean shown) {
		this.shown = shown;
		updateControls();
	}

	@Override
	public void tick() {
		ReactorControllerMenu menu = screen.getMenu();
		heatTrend.sample(menu.getHeatPercent());
		if (pendingDepth != null
				&& (menu.getDepthPercent() == pendingDepth || ++pendingTicks > PENDING_TIMEOUT_TICKS)) {
			pendingDepth = null;
		}
		if (slider != null && !slider.isDragging()) {
			slider.show(pendingDepth != null ? pendingDepth : menu.getDepthPercent());
		}
		updateControls();
	}

	/**
	 * The throttle is shown only for a sealed room. A bare reactor ignores the rods entirely, and a control
	 * that looks live but does nothing is the trap MOD-469 refused to build. The way to the «Room» tab is shown
	 * only while the shell is being built.
	 */
	private void updateControls() {
		ReactorConsole.Readout r = readout(screen.getMenu());
		boolean live = shown && r.formed();
		boolean building = shown && !r.formed() && !r.bare();
		if (roomLink != null) {
			roomLink.visible = building;
			roomLink.active = building;
		}
		if (slider != null) {
			slider.visible = live;
			slider.active = live;
		}
		if (stop != null) {
			stop.visible = live;
			stop.active = live;
		}
	}

	@Override
	public void mouseReleased(MouseButtonEvent event) {
		// Vanilla releases a dragged widget only on some paths of AbstractContainerScreen#mouseReleased, and
		// NeoForge patches in one more. Ending the drag here as well makes both loaders send exactly one
		// request: the slider no longer counts as dragging once either path has released it.
		if (slider != null && slider.isDragging()) {
			slider.onRelease(event);
		}
	}

	/**
	 * Sends a depth and holds it on screen until the menu echoes it.
	 *
	 * <p>Sent even when it equals what the menu shows while an earlier request is outstanding: a player who
	 * drags away and back before the server answers wants the second value, and the menu still showing the
	 * old one is exactly the case where skipping would lose it.
	 */
	private void commitDepth(int depth) {
		boolean outstanding = pendingDepth != null;
		pendingDepth = depth;
		pendingTicks = 0;
		if (outstanding || depth != screen.getMenu().getDepthPercent()) {
			screen.sendButton(depth);
		}
	}

	// ── Drawing ──────────────────────────────────────────────────────────────────────────────────

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ReactorControllerMenu menu = screen.getMenu();
		ReactorConsole.Readout r = readout(menu);
		if (r.formed()) {
			drawRunning(graphics, r, menu);
		} else if (r.bare()) {
			drawBare(graphics, r, menu);
		} else {
			drawBuilding(graphics, r, menu);
		}
		drawAdvice(graphics, r);
	}

	private void drawRunning(GuiGraphicsExtractor graphics, ReactorConsole.Readout r, ReactorControllerMenu menu) {
		Font font = screen.font();
		Component heatLabel = Component.translatable(KEY + "label.heat", r.heat());
		graphics.text(font, heatLabel, screen.left() + CONTENT_LEFT, screen.top() + HEADLINE_Y,
				GuiStyle.TEXT_DIM, false);
		drawTrendArrow(graphics, screen.left() + CONTENT_LEFT + font.width(heatLabel) + 3,
				screen.top() + HEADLINE_Y, heatTrend.direction());
		int heatColour = r.heat() >= r.meltdownPercent() ? FILL_RED
				: r.heat() >= r.warnPercent() ? FILL_AMBER : FILL_GREEN;
		bar(graphics, CONTENT_LEFT, HEAT_BAR_Y, CONTENT_RIGHT - CONTENT_LEFT, HEAT_BAR_H, r.heat(), heatColour);
		mark(graphics, r.warnPercent(), FILL_AMBER, INK_AMBER);
		mark(graphics, r.meltdownPercent(), FILL_RED, INK_RED);

		drawOutputAndBuffer(graphics, r, menu);

		line(graphics, CONTENT_LEFT, ROW_B_Y, COLUMN_W,
				Component.translatable(KEY + "label.water", r.water(), menu.getWaterRate()), GuiStyle.TEXT_DIM);
		bar(graphics, CONTENT_LEFT, ROW_B_Y + ROW_BAR_OFFSET, COLUMN_W, ROW_BAR_H, r.water(), FILL_TEAL);
		boolean blocked = r.steamBlocked();
		line(graphics, RIGHT_COLUMN_X, ROW_B_Y, COLUMN_W,
				Component.translatable(KEY + (blocked ? "label.steam_blocked" : "label.steam"), r.steam()),
				blocked ? INK_AMBER : GuiStyle.TEXT_DIM);
		bar(graphics, RIGHT_COLUMN_X, ROW_B_Y + ROW_BAR_OFFSET, COLUMN_W, ROW_BAR_H, r.steam(),
				blocked ? FILL_AMBER : FILL_STEAM);

		row(graphics, CONTENT_LEFT, RODS_Y, COLUMN_W, Component.translatable(KEY + "label.rods"),
				Component.literal(Integer.toString(r.rods())), GuiStyle.TEXT);
	}

	/**
	 * A reactor burning in the open: instability instead of heat, no coolant, no throttle. Leaving the
	 * missing gauges out is deliberate — an empty heat bar reads as a cold reactor rather than as one that
	 * has no thermometer.
	 */
	private void drawBare(GuiGraphicsExtractor graphics, ReactorConsole.Readout r, ReactorControllerMenu menu) {
		int instability = r.instability();
		boolean limit = instability >= ReactorConsole.INSTABILITY_LIMIT_PERCENT;
		row(graphics, CONTENT_LEFT, HEADLINE_Y, CONTENT_RIGHT - CONTENT_LEFT,
				Component.translatable(KEY + "label.instability"),
				Component.translatable(KEY + "percent", instability),
				instability >= 100 ? INK_RED : limit ? INK_AMBER : GuiStyle.TEXT);
		bar(graphics, CONTENT_LEFT, HEAT_BAR_Y, CONTENT_RIGHT - CONTENT_LEFT, HEAT_BAR_H, instability,
				instability >= 100 ? FILL_RED : limit ? FILL_AMBER : FILL_GREEN);

		drawOutputAndBuffer(graphics, r, menu);
		row(graphics, CONTENT_LEFT, ROW_B_Y, COLUMN_W, Component.translatable(KEY + "label.rods"),
				Component.literal(Integer.toString(r.rods())), GuiStyle.TEXT);
		wrapped(graphics, Component.translatable(KEY + "note.bare_depth"), CONTENT_LEFT, CONTROLS_Y + 2,
				CONTENT_RIGHT - CONTENT_LEFT, CONTROLS_H, GuiStyle.TEXT_DIM);
	}

	/**
	 * A shell still being built: the verdict and the buffer. What is wrong in detail — the map, the checklist and
	 * where to walk — is the «Room» tab's, and the button under the verdict leads there (MOD-619).
	 */
	private void drawBuilding(GuiGraphicsExtractor graphics, ReactorConsole.Readout r, ReactorControllerMenu menu) {
		screen.drawFitted(graphics, Component.translatable(r.status().translationKey()), HEADLINE_Y,
				CONTENT_LEFT, CONTENT_RIGHT, INK_RED);
		int stored = percent(menu.getStoredPercent());
		row(graphics, CONTENT_LEFT, ROW_B_Y, COLUMN_W, Component.translatable(KEY + "label.buffer"),
				Component.translatable(KEY + "percent", stored), GuiStyle.TEXT);
		bar(graphics, CONTENT_LEFT, ROW_B_Y + ROW_BAR_OFFSET, COLUMN_W, ROW_BAR_H, stored, FILL_AMBER);
	}

	private void drawOutputAndBuffer(GuiGraphicsExtractor graphics, ReactorConsole.Readout r,
			ReactorControllerMenu menu) {
		// Not a dash when the output is zero: a sealed, fuelled, silent reactor is the state that most needs
		// an explanation, and every cause has a different fix.
		row(graphics, CONTENT_LEFT, ROW_A_Y, COLUMN_W, Component.translatable(KEY + "label.output"),
				r.output() > 0
						? Component.translatable(KEY + "eu_per_tick", r.output())
						: Component.translatable(r.idle().translationKey()),
				r.output() > 0 ? GuiStyle.TEXT : INK_RED);
		int stored = percent(menu.getStoredPercent());
		row(graphics, RIGHT_COLUMN_X, ROW_A_Y, COLUMN_W, Component.translatable(KEY + "label.buffer"),
				Component.translatable(KEY + "percent", stored), GuiStyle.TEXT);
		bar(graphics, RIGHT_COLUMN_X, ROW_A_Y + ROW_BAR_OFFSET, COLUMN_W, ROW_BAR_H, stored, FILL_AMBER);
	}

	/**
	 * The box under the controls: a title in the advice's tone and the advice itself, shrunk as a whole if
	 * a translation runs long rather than cut mid-sentence. An accident's three ways out are numbered.
	 */
	private void drawAdvice(GuiGraphicsExtractor graphics, ReactorConsole.Readout r) {
		ReactorConsole.Advice advice = ReactorConsole.advice(r);
		List<Component> paragraphs = new ArrayList<>();
		paragraphs.add(advice.bodyArg() >= 0
				? Component.translatable(advice.bodyKey(), advice.bodyArg())
				: Component.translatable(advice.bodyKey()));
		for (int i = 0; i < advice.stepKeys().size(); i++) {
			paragraphs.add(Component.literal((i + 1) + ". ").append(Component.translatable(advice.stepKeys().get(i))));
		}
		ReactorPageText.messageBox(graphics, screen.font(), screen.left() + CONTENT_LEFT, screen.top() + ADVICE_Y,
				CONTENT_RIGHT - CONTENT_LEFT, ADVICE_BOTTOM - ADVICE_Y, advice.tone(),
				Component.translatable(advice.titleKey()), paragraphs);
	}

	/** A tick on the heat bar where the reactor changes behaviour, with the number under it. */
	private void mark(GuiGraphicsExtractor graphics, int percent, int colour, int ink) {
		int width = CONTENT_RIGHT - CONTENT_LEFT;
		int mx = screen.left() + CONTENT_LEFT + Math.min(width - 1, width * percent(percent) / 100);
		int barTop = screen.top() + HEAT_BAR_Y;
		// Coloured where it sticks out of the bar, pale where it crosses the fill: an amber line on an amber
		// fill would otherwise vanish exactly when the heat has reached it.
		graphics.fill(mx, barTop - 2, mx + 1, barTop, colour);
		graphics.fill(mx, barTop, mx + 1, barTop + HEAT_BAR_H, MARK_GLOW);
		graphics.fill(mx, barTop + HEAT_BAR_H, mx + 1, barTop + HEAT_BAR_H + 2, colour);
		Component label = Component.literal(Integer.toString(percent));
		int labelW = Math.round(screen.font().width(label) * MARK_SCALE);
		scaled(graphics, label, mx - labelW / 2, screen.top() + MARK_LABEL_Y, MARK_SCALE, ink);
	}

	private static void drawTrendArrow(GuiGraphicsExtractor graphics, int x, int y, int direction) {
		if (direction == 0) {
			return;
		}
		int colour = direction > 0 ? FILL_RED : FILL_GREEN;
		for (int i = 0; i < 4; i++) {
			int half = direction > 0 ? i : 3 - i;
			int rowY = y + 2 + i;
			graphics.fill(x + 3 - half, rowY, x + 4 + half, rowY + 1, colour);
		}
	}

	private void bar(GuiGraphicsExtractor graphics, int relX, int relY, int width, int height, int share, int colour) {
		int x = screen.left() + relX;
		int y = screen.top() + relY;
		graphics.fill(x, y, x + width, y + height, TRACK);
		int filled = width * percent(share) / 100;
		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + height, colour);
		}
	}

	/**
	 * Label on the left, value on the right. The value keeps its size — it is what the row is read for —
	 * and only shrinks if it alone would not fit; the label takes whatever is left and shrinks into it.
	 */
	private void row(GuiGraphicsExtractor graphics, int relX, int relY, int width, Component label,
			Component value, int valueColour) {
		Font font = screen.font();
		int x = screen.left() + relX;
		int y = screen.top() + relY;
		int valueW = font.width(value);
		float valueScale = valueW > width ? Math.max(MIN_SCALE, (float) width / valueW) : 1.0f;
		int shownValueW = Math.round(valueW * valueScale);
		scaled(graphics, value, x + width - shownValueW, y, valueScale, valueColour);
		int room = width - shownValueW - 4;
		int labelW = font.width(label);
		if (room <= 0 || labelW * MIN_SCALE > room) {
			return;
		}
		float labelScale = labelW > room ? (float) room / labelW : 1.0f;
		scaled(graphics, label, x, y, labelScale, GuiStyle.TEXT_DIM);
	}

	/** One left-aligned line, shrunk to fit its column. */
	private void line(GuiGraphicsExtractor graphics, int relX, int relY, int width, Component text, int colour) {
		scaledFit(graphics, text, screen.left() + relX, screen.top() + relY, width, colour);
	}

	private void scaledFit(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int colour) {
		ReactorPageText.scaledFit(graphics, screen.font(), text, x, y, width, colour);
	}

	/** A short paragraph wrapped at the advice scale inside a box, clipped at its height. */
	private void wrapped(GuiGraphicsExtractor graphics, Component text, int relX, int relY, int width, int height,
			int colour) {
		Font font = screen.font();
		List<FormattedCharSequence> lines = font.split(text, (int) (width / ADVICE_SCALE));
		graphics.pose().pushMatrix();
		graphics.pose().translate(screen.left() + relX, screen.top() + relY);
		graphics.pose().scale(ADVICE_SCALE, ADVICE_SCALE);
		for (int i = 0; i < lines.size() && (i + 1) * LINE_H * ADVICE_SCALE <= height; i++) {
			graphics.text(font, lines.get(i), 0, i * LINE_H, colour, false);
		}
		graphics.pose().popMatrix();
	}

	private void scaled(GuiGraphicsExtractor graphics, Component text, int x, int y, float scale, int colour) {
		ReactorPageText.scaled(graphics, screen.font(), text, x, y, scale, colour);
	}

	// ── Tooltips ─────────────────────────────────────────────────────────────────────────────────

	@Override
	public boolean tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ReactorControllerMenu menu = screen.getMenu();
		ReactorConsole.Readout r = readout(menu);
		int rx = mouseX - screen.left();
		int ry = mouseY - screen.top();
		List<Component> lines = null;
		if (r.formed() || r.bare()) {
			if (inside(rx, ry, CONTENT_LEFT, HEADLINE_Y, CONTENT_RIGHT - CONTENT_LEFT, MARK_LABEL_Y + 8 - HEADLINE_Y)) {
				lines = r.bare() ? List.of(Component.translatable(KEY + "tooltip.instability")) : heatTooltip(r);
			} else if (inside(rx, ry, CONTENT_LEFT, ROW_A_Y, COLUMN_W, ROW_H)) {
				lines = List.of(Component.translatable(KEY + "tooltip.output"));
			} else if (inside(rx, ry, RIGHT_COLUMN_X, ROW_A_Y, COLUMN_W, ROW_H)) {
				lines = bufferTooltip(menu);
			} else if (r.formed() && inside(rx, ry, CONTENT_LEFT, ROW_B_Y, COLUMN_W, ROW_H)) {
				lines = List.of(Component.translatable(KEY + "tooltip.coolant", r.coolantShare()));
			} else if (r.formed() && inside(rx, ry, RIGHT_COLUMN_X, ROW_B_Y, COLUMN_W, ROW_H)) {
				lines = List.of(Component.translatable(KEY + "tooltip.steam"));
			}
		} else if (inside(rx, ry, CONTENT_LEFT, ROW_B_Y, COLUMN_W, ROW_H)) {
			lines = bufferTooltip(menu);
		}
		if (lines == null) {
			return false;
		}
		Font font = screen.font();
		List<FormattedCharSequence> wrappedLines = new ArrayList<>();
		for (Component line : lines) {
			wrappedLines.addAll(font.split(line, TOOLTIP_WIDTH));
		}
		graphics.setTooltipForNextFrame(font, wrappedLines, mouseX, mouseY);
		return true;
	}

	/** The marks, in the colours they are drawn in, from the server's own thresholds. */
	private static List<Component> heatTooltip(ReactorConsole.Readout r) {
		return List.of(
				Component.translatable(KEY + "tooltip.heat.title"),
				Component.translatable(KEY + "tooltip.heat.warn", r.warnPercent()).withStyle(ChatFormatting.GOLD),
				Component.translatable(KEY + "tooltip.heat.meltdown", r.meltdownPercent()).withStyle(ChatFormatting.RED),
				Component.translatable(KEY + "tooltip.heat.top").withStyle(ChatFormatting.DARK_RED));
	}

	private static List<Component> bufferTooltip(ReactorControllerMenu menu) {
		return List.of(
				Component.translatable(KEY + "label.stored", percent(menu.getStoredPercent()),
						ReadoutFormat.exact(menu.getStoredEu())),
				Component.translatable(KEY + "tooltip.buffer").withStyle(ChatFormatting.GRAY));
	}

	private static boolean inside(int x, int y, int left, int top, int width, int height) {
		return x >= left && x < left + width && y >= top && y < top + height;
	}

	private static int percent(int value) {
		return Math.max(0, Math.min(100, value));
	}

	/**
	 * The vanilla slider, snapped to the console's depth grid and sending one request per gesture: on
	 * release, or per arrow-key step. Sending on every drag step would be a packet for every pixel of travel.
	 */
	private final class DepthSlider extends AbstractSliderButton {

		/**
		 * Whether a press on the slider has not been released yet. Tracked here because the vanilla
		 * {@code dragging} field is private in the game jar Fabric compiles against — only NeoForge's patched
		 * copy widens it — and because it makes a release idempotent: the second of two releases finds the
		 * flag already down and sends nothing.
		 */
		private boolean held;

		DepthSlider(int x, int y, int width, int height, int depth) {
			super(x, y, width, height, Component.empty(), depth / 100.0);
			updateMessage();
		}

		int depth() {
			return ReactorConsole.snapDepth(this.value);
		}

		boolean isDragging() {
			return held;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			super.onClick(event, doubleClick);
			held = this.active;
		}

		/** Shows a depth without treating it as the player's request. */
		void show(int depth) {
			this.value = depth / 100.0;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(KEY + "slider.depth", depth()));
		}

		/** The handle moves on the grid, so where it stands is the depth it names. */
		@Override
		protected void applyValue() {
			this.value = depth() / 100.0;
		}

		@Override
		public void onRelease(MouseButtonEvent event) {
			super.onRelease(event);
			if (held) {
				held = false;
				commitDepth(depth());
			}
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (this.canChangeValue && (event.isLeft() || event.isRight())) {
				int next = ReactorConsole.stepDepth(depth(), event.isLeft() ? -1 : 1);
				show(next);
				commitDepth(next);
				return true;
			}
			return super.keyPressed(event);
		}
	}
}
