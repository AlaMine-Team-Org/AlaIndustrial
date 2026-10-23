package dev.alaindustrial.client.screen.works;

import dev.alaindustrial.client.render.HallSchematicOverlay;
import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.network.BuildingCardPayload;
import dev.alaindustrial.settlement.works.ConstructionRow;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The card of one town building (round 2 of 0.1.219, Q2): a plain screen the server opens on a right click on a
 * house's inlet or a building's plaque. Its name, state and share restored, its second line (missing key points, or
 * why it cannot be ordered), who lives in it; the one order button its row in the core's «Construction» tab has, for
 * the town's restorer; «Show the schematic» of this one building, for every member.
 */
public final class BuildingCardScreen extends Screen {

	private static final int PANEL_W = 236;
	private static final int PANEL_H = 150;
	private static final int HEADER = 20;
	private static final int BUTTON_H = 18;

	private BuildingCardPayload shown;
	private int panelX;
	private int panelY;

	BuildingCardScreen(BuildingCardPayload payload) {
		super(ConstructionTab.component(payload.row().text()));
		this.shown = payload;
	}

	/** The block this card was opened from. */
	BlockPos anchor() {
		return shown.anchor();
	}

	/** A newer reply for the same building. */
	void update(BuildingCardPayload payload) {
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
		int third = (PANEL_W - 24) / 3;
		ConstructionRow row = shown.row();
		for (ConstructionRow.Action action : ConstructionRow.Action.values()) {
			if ((action.places() || action == ConstructionRow.Action.ORDER_CANCEL) && row.allows(action)) {
				addRenderableWidget(Button.builder(Component.translatable(action.key()),
						b -> BuildingCardClient.press(shown, action.ordinal()))
						.bounds(left, bottom, third, BUTTON_H).build());
				break;
			}
		}
		if (row.allows(ConstructionRow.Action.SCHEMATIC)) {
			boolean on = HallSchematicOverlay.isShown(shown.core(), row.target());
			addRenderableWidget(Button.builder(Component.translatable(on ? HallSchematicOverlay.HIDE_KEY
					: ConstructionRow.Action.SCHEMATIC.key()), b -> {
						HallSchematicOverlay.toggle(shown.core(), row.target());
						rebuildWidgets();
					}).bounds(left + third + 4, bottom, third, BUTTON_H).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.alaindustrial.dashboard.close"),
				b -> onClose()).bounds(left + 2 * (third + 4), bottom, third, BUTTON_H).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		GuiStyle.panel(g, panelX, panelY, PANEL_W, PANEL_H);
		g.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + HEADER, GuiStyle.HEADER_BAR);
		g.text(this.font, ConstructionTab.component(shown.row().text()), panelX + 8, panelY + 6, GuiStyle.HEADER_TEXT,
				false);
		int left = panelX + 8;
		int width = PANEL_W - 16;
		int y = panelY + HEADER + 8;
		ConstructionRow row = shown.row();
		if (row.status().isPresent()) {
			y = lines(g, ConstructionTab.component(row.status().get()), left, y, width, GuiStyle.TEXT);
		}
		for (ConstructionRow.Text line : shown.lines()) {
			y = lines(g, ConstructionTab.component(line), left, y, width, GuiStyle.TEXT_DIM);
		}
		if (row.detail().isPresent()) {
			lines(g, ConstructionTab.component(row.detail().get()), left, y + 2, width,
					row.tone() == ConstructionRow.Tone.BAD ? GuiStyle.STATUS_BLOCKING : GuiStyle.TEXT_DIM);
		}
	}

	/** Draws {@code text} wrapped to {@code width} from {@code y}; returns the y under it. */
	private int lines(GuiGraphicsExtractor g, Component text, int x, int y, int width, int colour) {
		int last = panelY + PANEL_H - BUTTON_H - 16;
		for (FormattedCharSequence line : this.font.split(text, width)) {
			if (y > last) {
				break;
			}
			g.text(this.font, line, x, y, colour, false);
			y += 10;
		}
		return y;
	}
}
