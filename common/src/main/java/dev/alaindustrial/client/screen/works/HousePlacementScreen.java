package dev.alaindustrial.client.screen.works;

import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.network.HousePlacementPayload;
import dev.alaindustrial.network.HousePlacementRequestPayload;
import dev.alaindustrial.settlement.works.ConstructionRow;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The house laying window of a house inlet (MOD-877, R108): a plain screen the server opens when the town's restorer
 * puts an inlet down on the town's territory or right-clicks it. Before the house is laid: the plan (one of the house
 * plans the town knows), its turn, every problem the core names in one sentence each, and «Lay the house» while there
 * is none. After: the house's state, and «Cancel laying» while its building has not started. The house stands as a
 * ghost in the world meanwhile ({@code HousePlacementPreview}); every press is checked again by the server.
 */
public final class HousePlacementScreen extends Screen {

	private static final String KEY = "settlement.alaindustrial.house_site.";
	private static final int PANEL_W = 236;
	private static final int PANEL_H = 168;
	private static final int HEADER = 20;
	private static final int BUTTON_H = 18;
	private static final int[] DEGREES = {0, 90, 180, 270};

	private HousePlacementPayload shown;
	private int panelX;
	private int panelY;

	HousePlacementScreen(HousePlacementPayload payload) {
		super(Component.translatable(KEY + "title"));
		this.shown = payload;
	}

	/** The inlet this window is about. */
	BlockPos inlet() {
		return shown.inlet();
	}

	/** A newer reply for the same inlet. */
	void update(HousePlacementPayload payload) {
		shown = payload;
		rebuildWidgets();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		panelX = (this.width - PANEL_W) / 2;
		panelY = (this.height - PANEL_H) / 2;
		int left = panelX + 8;
		int bottom = panelY + PANEL_H - BUTTON_H - 6;
		int half = (PANEL_W - 20) / 2;
		addRenderableWidget(Button.builder(Component.translatable("gui.alaindustrial.dashboard.close"),
				b -> onClose()).bounds(left + half + 4, bottom, half, BUTTON_H).build());
		if (shown.laid()) {
			Button cancel = addRenderableWidget(Button.builder(Component.translatable(KEY + "cancel"),
					b -> send(HousePlacementRequestPayload.CANCEL, shown.selected(), shown.rotation()))
					.bounds(left, bottom, half, BUTTON_H).build());
			cancel.active = shown.cancellable();
			return;
		}
		Button lay = addRenderableWidget(Button.builder(Component.translatable(KEY + "lay"),
				b -> send(HousePlacementRequestPayload.LAY, shown.selected(), shown.rotation()))
				.bounds(left, bottom, half, BUTTON_H).build());
		lay.active = !shown.types().isEmpty() && shown.problems().isEmpty();
		if (shown.types().isEmpty()) {
			return;
		}
		int row = panelY + HEADER + 6;
		Button next = addRenderableWidget(Button.builder(Component.literal(">"),
				b -> send(HousePlacementRequestPayload.PREVIEW, shown.selected() + 1, shown.rotation()))
				.bounds(panelX + PANEL_W - 8 - BUTTON_H, row, BUTTON_H, BUTTON_H).build());
		next.active = shown.types().size() > 1;
		addRenderableWidget(Button.builder(Component.translatable(KEY + "rotate", degrees()),
				b -> send(HousePlacementRequestPayload.PREVIEW, shown.selected(), shown.rotation() + 1))
				.bounds(panelX + PANEL_W - 8 - 90, row + BUTTON_H + 4, 90, BUTTON_H).build());
	}

	private int degrees() {
		return DEGREES[Math.floorMod(shown.rotation(), DEGREES.length)];
	}

	private void send(int action, int plan, int rotation) {
		HousePlacementClient.request(shown, action, Math.max(0, plan), Math.floorMod(rotation, DEGREES.length));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		GuiStyle.panel(g, panelX, panelY, PANEL_W, PANEL_H);
		g.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + HEADER, GuiStyle.HEADER_BAR);
		g.text(this.font, this.title, panelX + 8, panelY + 6, GuiStyle.HEADER_TEXT, false);
		int left = panelX + 8;
		int width = PANEL_W - 16;
		int y = panelY + HEADER + 11;
		if (shown.laid()) {
			Component plan = plan(shown.types().isEmpty() ? 0 : shown.types().getFirst());
			g.text(this.font, plan, left, y, GuiStyle.TEXT, false);
			y += 14;
			lines(g, Component.translatable(KEY + (shown.cancellable() ? "laid" : "started")), left, y, width,
					GuiStyle.STATUS_GOOD);
			return;
		}
		if (!shown.types().isEmpty()) {
			g.text(this.font, plan(shown.types().get(Math.floorMod(shown.selected(), shown.types().size()))), left, y,
					GuiStyle.TEXT, false);
			y += BUTTON_H + 4 + 6;
			g.text(this.font, Component.translatable(KEY + "turn", degrees()), left, y, GuiStyle.TEXT_DIM, false);
			y += 18;
		}
		if (shown.branch() >= 0) {
			// The road the house takes to join the town's network (round 2 of 0.1.219, step 8).
			y = lines(g, Component.translatable(KEY + "branch", shown.branch()), left, y, width, GuiStyle.TEXT_DIM) + 2;
		} else if (shown.branch() == HousePlacementPayload.NO_WAY) {
			y = lines(g, Component.translatable(KEY + "no_way_to_network"), left, y, width, GuiStyle.STATUS_BLOCKING)
					+ 2;
		}
		if (shown.problems().isEmpty()) {
			lines(g, Component.translatable(KEY + "free"), left, y, width, GuiStyle.STATUS_GOOD);
			return;
		}
		int last = panelY + PANEL_H - BUTTON_H - 16;
		for (ConstructionRow.Text problem : shown.problems()) {
			if (y > last) {
				break;
			}
			y = lines(g, ConstructionTab.component(problem), left, y, width, GuiStyle.STATUS_BLOCKING) + 2;
		}
	}

	private static Component plan(int type) {
		return Component.translatable(KEY + "plan",
				Component.translatable("item.alaindustrial.house_plan.typed", type));
	}

	/** Draws {@code text} wrapped to {@code width} from {@code y}; returns the y under it. */
	private int lines(GuiGraphicsExtractor g, Component text, int x, int y, int width, int colour) {
		for (FormattedCharSequence line : this.font.split(text, width)) {
			g.text(this.font, line, x, y, colour, false);
			y += 10;
		}
		return y;
	}
}
