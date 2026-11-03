package dev.alaindustrial.client.screen;

import dev.alaindustrial.client.screen.reactor.ConsoleTabPage;
import dev.alaindustrial.client.screen.reactor.ReactorConsole;
import dev.alaindustrial.client.tablet.ReactorTabletBezel;
import dev.alaindustrial.client.tablet.ReactorTabletLayout;
import dev.alaindustrial.client.tablet.ReactorTabletNoSignal;
import dev.alaindustrial.client.tablet.ReactorTabletSignal;
import dev.alaindustrial.client.tablet.ReactorTabletTimeline;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.misc.ReactorTabletItem;
import dev.alaindustrial.menu.ReactorTabletMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The reactor controller's panel as a tier-1 tablet shows it (MOD-941).
 *
 * <p>While the tablet hears its reactor this is the controller's own screen, read-only — the throttle and STOP are
 * hidden by {@link ReactorTabletMenu#isViewOnly}. While it does not, nothing of the panel is drawn: one card per
 * reason ({@link ReactorTabletNoSignal}) says what is wrong and what to do, the tabs and page buttons are gone and
 * nothing answers the mouse but the close keys (owner's review, MOD-946). The panel comes back by itself, on the tab
 * the player left, as soon as the link does.
 *
 * <p><b>Held in the hands (MOD-946).</b> The panel sits in the tablet's casing ({@link ReactorTabletBezel}) and the
 * whole of it is drawn under one scale and shift ({@link ReactorTabletLayout}): it unfolds out of the tablet the hands
 * have just lifted, fills almost the whole screen once open, and folds back on close. One clock drives the hands and
 * the screen ({@link ReactorTabletTimeline}); the screen takes input only while fully open, maps the mouse back into
 * the panel's own layout, and leaves as soon as it has folded, while the hands are still lowering. The casing blinks
 * and flickers from the states the panel itself shows: the menu's link, the console's verdict, the stack's charge.
 */
public class ReactorTabletScreen extends ReactorControllerScreen {

	private static final int POWER_BEAM = 0xFFD8F4F0;

	private final ReactorTabletMenu tabletMenu;
	/** Set once the player has asked to close: the screen folds first, then leaves. */
	private boolean closing;

	/**
	 * Typed on the controller's menu because the tablet's menu type is declared on it (one screen family); the menu
	 * this screen is opened for is always the tablet's.
	 */
	public ReactorTabletScreen(dev.alaindustrial.menu.ReactorControllerMenu menu, Inventory inventory,
			Component title) {
		super(menu, inventory, title);
		this.tabletMenu = (ReactorTabletMenu) menu;
	}

	// ── The tablet in the hands (MOD-946) ───────────────────────────────────────────────────────────

	@Override
	public void added() {
		super.added();
		ReactorTabletTimeline.CLIENT.open(Util.getMillis());
	}

	/** Closed from anywhere (the server too, when the tablet leaves the hands): the hands still lower it. */
	@Override
	public void removed() {
		super.removed();
		ReactorTabletTimeline.CLIENT.close(Util.getMillis());
	}

	/** The first close folds the screen back into the tablet; a second one, while folding, closes at once. */
	@Override
	public void onClose() {
		if (!closing) {
			closing = true;
			ReactorTabletTimeline.CLIENT.close(Util.getMillis());
			return;
		}
		super.onClose();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (closing && ReactorTabletTimeline.CLIENT.isFolded(Util.getMillis())) {
			super.onClose();
		}
	}

	private boolean isOpen() {
		return !closing && ReactorTabletTimeline.CLIENT.isOpen(Util.getMillis());
	}

	/** Whether the panel answers the mouse: open, and showing the reactor rather than a no-signal card. */
	private boolean panelLive() {
		return isOpen() && tabletMenu.isLinked();
	}

	/** Where the casing is drawn this frame. */
	private ReactorTabletLayout.Frame frame() {
		return ReactorTabletLayout.at(ReactorTabletTimeline.CLIENT.unfold(Util.getMillis()), this.width, this.height,
				this.minecraft.getWindow().getGuiScale(), ReactorTabletBezel.bodyX(this.leftPos),
				ReactorTabletBezel.bodyY(this.topPos), ReactorTabletBezel.bodyWidth(IMAGE_WIDTH),
				ReactorTabletBezel.bodyHeight(IMAGE_HEIGHT));
	}

	private static void applyFrame(GuiGraphicsExtractor graphics, ReactorTabletLayout.Frame frame) {
		graphics.pose().translate(frame.originX(), frame.originY());
		graphics.pose().scale(frame.scale(), frame.scale());
		graphics.pose().translate(-frame.pivotX(), -frame.pivotY());
	}

	/** The casing's state, from the sources the panel itself reads. */
	private ReactorTabletBezel.Status status() {
		boolean linked = tabletMenu.isLinked();
		ReactorTabletSignal.Alarm alarm = ReactorTabletSignal.Alarm.NONE;
		if (linked) {
			ReactorConsole.Readout readout = ConsoleTabPage.readout(tabletMenu);
			if (ReactorConsole.isAlarm(readout)) {
				alarm = ReactorTabletSignal.Alarm.ALARM;
			} else if (ReactorConsole.verdict(readout) == ReactorConsole.Verdict.WARNING) {
				alarm = ReactorTabletSignal.Alarm.WARN;
			}
		}
		ItemStack tablet = this.minecraft.player == null ? ItemStack.EMPTY
				: ReactorTabletItem.heldTablet(this.minecraft.player);
		long capacity = ItemEnergy.shownCapacity(tablet);
		float charge = capacity <= 0L ? 0.0f : ItemEnergy.shownGet(tablet) / (float) capacity;
		return new ReactorTabletBezel.Status(linked, alarm, charge, tabletMenu.linkState());
	}

	/** No vanilla dimming: the world dims in {@link #extractBackground} as the screen unfolds. */
	@Override
	public void extractTransparentBackground(GuiGraphicsExtractor graphics) {
	}

	/** Before the unfold, only the hands are on screen; then the dimmed world, the casing and the panel's frame. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float unfold = ReactorTabletTimeline.CLIENT.unfold(Util.getMillis());
		if (unfold <= 0.0f) {
			return;
		}
		graphics.fill(0, 0, this.width, this.height, Math.round(0x90 * unfold) << 24 | 0x05080A);
		ReactorTabletLayout.Frame frame = frame();
		graphics.pose().pushMatrix();
		try {
			applyFrame(graphics, frame);
			ReactorTabletBezel.drawCasing(graphics, this.font,
					Component.translatable("item.alaindustrial.reactor_tablet"), this.leftPos, this.topPos,
					IMAGE_WIDTH, IMAGE_HEIGHT, status(), Util.getMillis());
			super.extractBackground(graphics, panelMouseX(frame, mouseX), panelMouseY(frame, mouseY), partialTick);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (ReactorTabletTimeline.CLIENT.unfold(Util.getMillis()) <= 0.0f) {
			return;
		}
		ReactorTabletLayout.Frame frame = frame();
		graphics.pose().pushMatrix();
		ReactorTabletLayout.tooltipsInPose = true;
		try {
			applyFrame(graphics, frame);
			super.extractRenderState(graphics, panelMouseX(frame, mouseX), panelMouseY(frame, mouseY), partialTick);
			long now = Util.getMillis();
			if (!tabletMenu.isLinked()) {
				// After the page widgets, so the card covers them: nothing of the panel shows without a signal.
				ReactorTabletNoSignal.draw(graphics, this.font, this.leftPos, this.topPos, IMAGE_WIDTH, IMAGE_HEIGHT,
						tabletMenu.linkState(), now);
			}
			ReactorTabletBezel.drawOverGlass(graphics, this.leftPos, this.topPos, IMAGE_WIDTH, IMAGE_HEIGHT, status(),
					now);
			drawPowerOn(graphics, ReactorTabletTimeline.CLIENT.powerOn(now));
		} finally {
			ReactorTabletLayout.tooltipsInPose = false;
			graphics.pose().popMatrix();
		}
	}

	/**
	 * The screen powering on after the unfold, like an old monitor: a bright line across the middle opens into the
	 * picture. Played back as a power-off when the tablet closes.
	 */
	private void drawPowerOn(GuiGraphicsExtractor graphics, float power) {
		if (power >= 1.0f) {
			return;
		}
		int left = this.leftPos;
		int top = this.topPos;
		int mid = top + IMAGE_HEIGHT / 2;
		int half = Math.round(IMAGE_HEIGHT / 2.0f * power * power);
		graphics.fill(left, top, left + IMAGE_WIDTH, mid - half, 0xFF000000);
		graphics.fill(left, mid + half, left + IMAGE_WIDTH, top + IMAGE_HEIGHT, 0xFF000000);
		int beamAlpha = Math.round(0xFF * (1.0f - power));
		int beam = beamAlpha << 24 | POWER_BEAM & 0xFFFFFF;
		graphics.fill(left, mid - half - 1, left + IMAGE_WIDTH, mid - half + 1, beam);
		graphics.fill(left, mid + half - 1, left + IMAGE_WIDTH, mid + half + 1, beam);
	}

	/** The mouse in the panel's layout; far off the panel while it moves or shows no signal, so nothing lights up. */
	private int panelMouseX(ReactorTabletLayout.Frame frame, int mouseX) {
		return panelLive() ? (int) Math.floor(frame.toPanelX(mouseX)) : -10_000;
	}

	private int panelMouseY(ReactorTabletLayout.Frame frame, int mouseY) {
		return panelLive() ? (int) Math.floor(frame.toPanelY(mouseY)) : -10_000;
	}

	@Override
	protected boolean acceptsMouse() {
		return panelLive();
	}

	@Override
	protected double layoutX(double x) {
		return frame().toPanelX(x);
	}

	@Override
	protected double layoutY(double y) {
		return frame().toPanelY(y);
	}

	@Override
	protected double layoutScale() {
		return frame().scale();
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		if (panelLive()) {
			ReactorTabletLayout.Frame frame = frame();
			super.mouseMoved(frame.toPanelX(mouseX), frame.toPanelY(mouseY));
		}
	}

	/** JEI and REI keep clear of the whole casing as drawn on screen; the panel's own areas are scaled with it. */
	@Override
	public List<Rect2i> extraGuiAreas() {
		ReactorTabletLayout.Frame frame = frame();
		int x0 = ReactorTabletBezel.bodyX(this.leftPos);
		int y0 = ReactorTabletBezel.bodyY(this.topPos);
		int left = (int) Math.floor(frame.toScreenX(x0));
		int top = (int) Math.floor(frame.toScreenY(y0));
		int right = (int) Math.ceil(frame.toScreenX(x0 + ReactorTabletBezel.bodyWidth(IMAGE_WIDTH)));
		int bottom = (int) Math.ceil(frame.toScreenY(y0 + ReactorTabletBezel.bodyHeight(IMAGE_HEIGHT)));
		List<Rect2i> areas = new ArrayList<>();
		areas.add(new Rect2i(left, top, right - left, bottom - top));
		return areas;
	}

	// ── The panel (MOD-941) ─────────────────────────────────────────────────────────────────────────

	/** The panel only while it hears the reactor; without a signal the render pass covers it with a card. */
	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (tabletMenu.isLinked()) {
			super.drawMachineFrame(graphics, mouseX, mouseY, partialTick);
		}
	}

	/** No tab title without a signal: it would show grey on grey through the card's edge. */
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (tabletMenu.isLinked()) {
			super.extractLabels(graphics, mouseX, mouseY);
		}
	}
}
