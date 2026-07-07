package dev.alaindustrial.client;

import dev.alaindustrial.client.AlaClientConfig.Snapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Dependency-free client settings screen shared by Fabric Mod Menu and NeoForge's Mods screen. */
public final class AlaConfigScreen extends Screen {
	private static final int PANEL = 0xAA101820;
	private static final int BORDER = 0xFF5D7488;
	private static final int TEXT = 0xFFE8EEF2;
	private static final int TEXT_DIM = 0xFF9AA7B0;
	private static final int PANEL_HEIGHT = 234;

	private final Screen parent;
	private Snapshot draft;

	public AlaConfigScreen(Screen parent) {
		super(Component.translatable("config.alaindustrial.title"));
		this.parent = parent;
		this.draft = AlaClientConfig.snapshot();
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(360, this.width - 32);
		int x = (this.width - panelWidth) / 2;
		int y = panelY();
		int buttonWidth = panelWidth - 32;
		int bx = x + 16;
		int row = y + 32;

		addToggle(bx, row, buttonWidth, "config.alaindustrial.network_overlay.enabled",
				draft.networkOverlayEnabled(), value -> draft = draft.withNetworkOverlayEnabled(value));
		row += 22;
		addToggle(bx, row, buttonWidth, "config.alaindustrial.network_overlay.through_blocks",
				draft.networkOverlayThroughBlocks(), value -> draft = draft.withNetworkOverlayThroughBlocks(value));
		row += 22;
		addToggle(bx, row, buttonWidth, "config.alaindustrial.network_overlay.flow_dots",
				draft.networkOverlayFlowDots(), value -> draft = draft.withNetworkOverlayFlowDots(value));
		row += 22;
		addRenderableWidget(Button.builder(colorMessage(), b -> {
			draft = draft.withNetworkOverlayColor(AlaClientConfig.nextNetworkColor(draft.networkOverlayColor()));
			b.setMessage(colorMessage());
		}).bounds(bx, row, buttonWidth - 28, 20).build());
		row += 22;
		addRenderableWidget(Button.builder(alphaMessage(), b -> {
			int next = draft.networkOverlayAlpha() <= 64 ? 255 : draft.networkOverlayAlpha() - 64;
			draft = draft.withNetworkOverlayAlpha(next);
			b.setMessage(alphaMessage());
		}).bounds(bx, row, buttonWidth, 20).build());
		row += 38;
		addToggle(bx, row, buttonWidth, "config.alaindustrial.tooltips.always_detailed",
				draft.alwaysDetailedTooltips(), value -> draft = draft.withAlwaysDetailedTooltips(value));
		row += 22;
		addToggle(bx, row, buttonWidth, "config.alaindustrial.tooltips.show_eu_numbers",
				draft.showEuNumbers(), value -> draft = draft.withShowEuNumbers(value));

		int footerY = y + PANEL_HEIGHT - 28;
		int w = (buttonWidth - 16) / 3;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
			AlaClientConfig.apply(draft);
			this.minecraft.setScreenAndShow(parent);
		}).bounds(bx, footerY, w, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("controls.reset"), b -> {
			draft = Snapshot.defaults();
			rebuildWidgets();
		}).bounds(bx + w + 8, footerY, w, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.minecraft.setScreenAndShow(parent))
				.bounds(bx + 2 * w + 16, footerY, buttonWidth - 2 * w - 16, 20).build());
	}

	private void addToggle(int x, int y, int width, String key, boolean value, ToggleSetter setter) {
		addRenderableWidget(CycleButton.onOffBuilder(value)
				.create(x, y, width, 20, Component.translatable(key), (button, newValue) -> setter.set(newValue)));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int panelWidth = Math.min(360, this.width - 32);
		int x = (this.width - panelWidth) / 2;
		int y = panelY();
		graphics.fill(x, y, x + panelWidth, y + PANEL_HEIGHT, PANEL);
		graphics.outline(x, y, panelWidth, PANEL_HEIGHT, BORDER);
		graphics.centeredText(this.font, this.title, this.width / 2, y + 10, TEXT);
		graphics.text(this.font, Component.translatable("config.alaindustrial.category.network_overlay"),
				x + 16, y + 22, TEXT_DIM, false);
		graphics.text(this.font, Component.translatable("config.alaindustrial.category.tooltips"),
				x + 16, y + 148, TEXT_DIM, false);
		int colorY = y + 98;
		int colorX = x + 16 + panelWidth - 32 - 20;
		graphics.fill(colorX, colorY, colorX + 20, colorY + 20, draft.networkOverlayColor());
		graphics.outline(colorX, colorY, 20, 20, BORDER);
	}

	private int panelY() {
		return Math.max(18, (this.height - PANEL_HEIGHT) / 2);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(parent);
	}

	private Component colorMessage() {
		return Component.translatable("config.alaindustrial.network_overlay.color",
				String.format(java.util.Locale.ROOT, "#%06X", draft.networkOverlayColor() & 0x00FFFFFF));
	}

	private Component alphaMessage() {
		return Component.translatable("config.alaindustrial.network_overlay.alpha", draft.networkOverlayAlpha());
	}

	private interface ToggleSetter {
		void set(boolean value);
	}
}
