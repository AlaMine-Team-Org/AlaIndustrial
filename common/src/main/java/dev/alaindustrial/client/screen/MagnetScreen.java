package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.tool.MagnetFilter;
import dev.alaindustrial.item.tool.MagnetItem;
import dev.alaindustrial.menu.MagnetMenu;
import dev.alaindustrial.network.MagnetFilterSamplePayload;
import dev.alaindustrial.network.NetworkDispatcher;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The electromagnet's screen (MOD-592) — one window, no tabs: charge on the left, the module slots next
 * to it, the filter cells in the middle, three buttons on the right, the inventory below.
 *
 * <p>Everything it shows is read off the magnet stack and the filter module's stack, which vanilla
 * already keeps in sync as slot contents — the screen owns no state of its own. Clicks on the buttons and
 * the cells become container buttons ({@link MagnetMenu#clickMenuButton}); the server reads the sample
 * off the cursor, so the client never tells it what item to write.
 */
public class MagnetScreen extends AbstractContainerScreen<MagnetMenu> {

	public static final Identifier TEXTURE = Industrialization.id("textures/gui/container/magnet.png");
	private static final int TEX_SIZE = 256;
	private static final int WIDTH = 176;
	private static final int HEIGHT = 186;

	private static final int BAR_X = 8;
	private static final int BAR_Y = 17;
	private static final int BAR_W = 10;
	private static final int BAR_H = 44;
	private static final int BAR_UV_X = 176;

	private static final int LOCK_UV_X = 192;
	private static final int BUTTON_X = 134;
	private static final int[] BUTTON_Y = {17, 40, 63};
	private static final int BUTTON = 20;
	private static final int GRID_W = MagnetMenu.CELL_COLUMNS * MagnetMenu.STRIDE;
	private static final int GRID_H = (MagnetFilter.CELLS / MagnetMenu.CELL_COLUMNS) * MagnetMenu.STRIDE;

	private static final int COLOR_HOVER = 0x40FFFFFF;
	private static final int COLOR_VEIL = 0x90404A58;
	private static final int COLOR_DISABLED = 0xA0AAAAAA;
	private static final int COLOR_TAG_MARK = 0xFF46AA46;
	private static final int TOOLTIP_WIDTH = 180;

	private final ItemStack filterIcon = new ItemStack(dev.alaindustrial.registry.ModContent.MAGNET_FILTER_MODULE.get());

	public MagnetScreen(MagnetMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		this.inventoryLabelY = MagnetMenu.INV_Y - 11;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, WIDTH, HEIGHT, TEX_SIZE, TEX_SIZE);

		ItemStack host = this.menu.host();
		long capacity = ItemEnergy.capacity(host);
		int fill = capacity > 0 ? (int) Math.min(BAR_H, ItemEnergy.get(host) * BAR_H / capacity) : 0;
		if (fill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + BAR_X, y + BAR_Y + BAR_H - fill,
					BAR_UV_X, BAR_H - fill, BAR_W, fill, TEX_SIZE, TEX_SIZE);
		}

		for (int i = this.menu.activeModuleSlots(); i < MagnetMenu.MAX_MODULE_SLOTS; i++) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + MagnetMenu.MODULE_X,
					y + MagnetMenu.MODULE_Y + i * MagnetMenu.STRIDE, LOCK_UV_X, 0, 16, 16, TEX_SIZE, TEX_SIZE);
		}

		MagnetFilter filter = this.menu.filter();
		MagnetFilter shown = filter == null ? MagnetFilter.EMPTY : filter;
		icon(graphics, 0, MagnetItem.isEnabled(host) ? 208 : 224, 0);
		icon(graphics, 1, shown.allowList() ? 208 : 224, 16);
		icon(graphics, 2, 208 + shown.match().ordinal() * 16, 32);
		if (filter == null) {
			// The two filter buttons stay visible but greyed: they belong to a module that is not there.
			for (int b = 1; b < BUTTON_Y.length; b++) {
				int bx = x + BUTTON_X;
				int by = y + BUTTON_Y[b];
				graphics.fill(bx + 1, by + 1, bx + BUTTON - 1, by + BUTTON - 1, COLOR_DISABLED);
			}
		}
	}

	private void icon(GuiGraphicsExtractor graphics, int button, int u, int v) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos + BUTTON_X + 2,
				this.topPos + BUTTON_Y[button] + 2, u, v, 16, 16, TEX_SIZE, TEX_SIZE);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		MagnetFilter filter = this.menu.filter();
		if (filter == null) {
			// No filter fitted: the cells are dimmed and show the module that opens them; the words are in
			// the tooltip, because a sentence does not fit a 4x4 grid in every language.
			int gx = x + MagnetMenu.CELL_X - 1;
			int gy = y + MagnetMenu.CELL_Y - 1;
			graphics.fill(gx, gy, gx + GRID_W, gy + GRID_H, COLOR_VEIL);
			graphics.item(filterIcon, gx + (GRID_W - 16) / 2, gy + (GRID_H - 16) / 2);
		} else if (filter.match() == MagnetFilter.Match.TAG) {
			// A cell with a category chosen gets a small mark, so the grid shows which cells are wide.
			for (int i = 0; i < MagnetFilter.CELLS; i++) {
				if (!filter.cell(i).tag().isEmpty()) {
					int cx = x + MagnetMenu.CELL_X + (i % MagnetMenu.CELL_COLUMNS) * MagnetMenu.STRIDE;
					int cy = y + MagnetMenu.CELL_Y + (i / MagnetMenu.CELL_COLUMNS) * MagnetMenu.STRIDE;
					graphics.fill(cx + 12, cy, cx + 16, cy + 4, COLOR_TAG_MARK);
				}
			}
		}
		int hovered = buttonAt(mouseX, mouseY);
		if (hovered >= 0 && (hovered == 0 || filter != null)) {
			int bx = x + BUTTON_X;
			int by = y + BUTTON_Y[hovered];
			graphics.fill(bx + 1, by + 1, bx + BUTTON - 1, by + BUTTON - 1, COLOR_HOVER);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		MagnetFilter filter = this.menu.filter();
		int cell = filter == null ? -1 : cellAt(mouseX, mouseY);
		if (cell >= 0) {
			// Instead of vanilla's item tooltip, not on top of it: a cell is a rule, and the lines say how
			// it matches and how to change it.
			if (this.menu.getCarried().isEmpty()) {
				cellTooltip(graphics, mouseX, mouseY, filter, cell);
			}
			return;
		}
		super.extractTooltip(graphics, mouseX, mouseY);
		ItemStack host = this.menu.host();
		if (isHovering(BAR_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable("gui.alaindustrial.energy",
					ItemEnergy.get(host), ItemEnergy.capacity(host)), mouseX, mouseY);
			return;
		}
		int button = buttonAt(mouseX, mouseY);
		if (button == 0) {
			tooltip(graphics, mouseX, mouseY,
					Component.translatable(MagnetItem.isEnabled(host)
							? "gui.alaindustrial.magnet.power_on" : "gui.alaindustrial.magnet.power_off"),
					Component.translatable("gui.alaindustrial.magnet.power_hint").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (button == 1 && filter != null) {
			tooltip(graphics, mouseX, mouseY,
					Component.translatable(filter.allowList()
							? "gui.alaindustrial.magnet.mode_allow" : "gui.alaindustrial.magnet.mode_deny"),
					Component.translatable(filter.allowList()
							? "gui.alaindustrial.magnet.mode_allow.hint" : "gui.alaindustrial.magnet.mode_deny.hint")
							.withStyle(ChatFormatting.GRAY),
					Component.translatable("gui.alaindustrial.magnet.click_to_switch").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		if (button == 2 && filter != null) {
			String key = "gui.alaindustrial.magnet.match_" + filter.match().key();
			tooltip(graphics, mouseX, mouseY, Component.translatable(key),
					Component.translatable(key + ".hint").withStyle(ChatFormatting.GRAY),
					Component.translatable("gui.alaindustrial.magnet.click_to_switch").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		if (filter == null && isHovering(MagnetMenu.CELL_X, MagnetMenu.CELL_Y, GRID_W - 2, GRID_H - 2, mouseX, mouseY)) {
			tooltip(graphics, mouseX, mouseY, Component.translatable("gui.alaindustrial.magnet.no_filter"));
			return;
		}
		int locked = lockedModuleSlotAt(mouseX, mouseY);
		if (locked >= 0) {
			tooltip(graphics, mouseX, mouseY, Component.translatable("gui.alaindustrial.magnet.slot_locked")
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		Slot hoveredSlot = this.hoveredSlot;
		if (hoveredSlot instanceof MagnetMenu.ModuleSlot && !hoveredSlot.hasItem()) {
			tooltip(graphics, mouseX, mouseY, Component.translatable("gui.alaindustrial.magnet.slot_empty"),
					Component.translatable("gui.alaindustrial.magnet.slot_empty.hint").withStyle(ChatFormatting.GRAY));
		}
	}

	private void cellTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, MagnetFilter filter, int cell) {
		MagnetFilter.Cell sample = filter.cell(cell);
		List<Component> lines = new ArrayList<>(4);
		ItemStack shown = sample.stack();
		if (shown.isEmpty()) {
			lines.add(Component.translatable("gui.alaindustrial.magnet.cell_empty"));
			lines.add(Component.translatable("gui.alaindustrial.magnet.cell_empty.hint").withStyle(ChatFormatting.GRAY));
		} else {
			lines.add(shown.getHoverName());
			if (filter.match() == MagnetFilter.Match.TAG) {
				lines.add(sample.tag().isEmpty()
						? Component.translatable("gui.alaindustrial.magnet.cell_no_tag").withStyle(ChatFormatting.GRAY)
						: Component.translatable("gui.alaindustrial.magnet.cell_tag", "#" + sample.tag())
								.withStyle(ChatFormatting.DARK_GREEN));
				lines.add(Component.translatable("gui.alaindustrial.magnet.cell_tag.hint").withStyle(ChatFormatting.DARK_GRAY));
			} else if (filter.match() == MagnetFilter.Match.MOD) {
				Identifier id = Identifier.tryParse(sample.item());
				lines.add(Component.translatable("gui.alaindustrial.magnet.cell_mod",
						id == null ? "?" : id.getNamespace()).withStyle(ChatFormatting.GOLD));
			}
			lines.add(Component.translatable("gui.alaindustrial.magnet.cell_clear.hint").withStyle(ChatFormatting.DARK_GRAY));
		}
		tooltip(graphics, mouseX, mouseY, lines.toArray(Component[]::new));
	}

	private void tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Component... lines) {
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) {
			wrapped.addAll(this.font.split(line, TOOLTIP_WIDTH));
		}
		graphics.setTooltipForNextFrame(this.font, wrapped, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.minecraft != null && this.minecraft.gameMode != null
				&& (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT)) {
			int button = buttonAt(event.x(), event.y());
			if (button >= 0) {
				if (button == 0 || this.menu.filter() != null) {
					this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
							MagnetMenu.BUTTON_POWER + button);
				}
				return true;
			}
			int cell = this.menu.filter() == null ? -1 : cellAt(event.x(), event.y());
			if (cell >= 0) {
				// Shift-click walks the item's categories; any other click takes the cursor's item, and an
				// empty cursor clears the cell. The server reads the cursor itself.
				int id = event.hasShiftDown() ? MagnetMenu.BUTTON_CELL_TAG + cell : MagnetMenu.BUTTON_CELL + cell;
				this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private int buttonAt(double mx, double my) {
		for (int i = 0; i < BUTTON_Y.length; i++) {
			if (isHovering(BUTTON_X, BUTTON_Y[i], BUTTON, BUTTON, mx, my)) {
				return i;
			}
		}
		return -1;
	}

	/** Index of the filter cell under the cursor, or -1. */
	public int cellAt(double mx, double my) {
		for (int i = 0; i < MagnetFilter.CELLS; i++) {
			Rect2i area = cellArea(i);
			if (mx >= area.getX() && mx < area.getX() + area.getWidth()
					&& my >= area.getY() && my < area.getY() + area.getHeight()) {
				return i;
			}
		}
		return -1;
	}

	/** Screen rectangle of filter cell {@code i} — also the drop target a recipe viewer highlights. */
	public Rect2i cellArea(int i) {
		return new Rect2i(this.leftPos + MagnetMenu.CELL_X + (i % MagnetMenu.CELL_COLUMNS) * MagnetMenu.STRIDE,
				this.topPos + MagnetMenu.CELL_Y + (i / MagnetMenu.CELL_COLUMNS) * MagnetMenu.STRIDE, 16, 16);
	}

	/** Whether a filter is fitted — recipe viewers offer drop targets only then. */
	public boolean acceptsSamples() {
		return this.menu.filter() != null;
	}

	private int lockedModuleSlotAt(double mx, double my) {
		for (int i = this.menu.activeModuleSlots(); i < MagnetMenu.MAX_MODULE_SLOTS; i++) {
			if (isHovering(MagnetMenu.MODULE_X, MagnetMenu.MODULE_Y + i * MagnetMenu.STRIDE, 16, 16, mx, my)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Drop {@code sample} into cell {@code cell} from a recipe viewer. The cursor is empty in that case,
	 * so the item id travels in a payload instead of being read off the cursor.
	 */
	public static void sendSample(int cell, ItemStack sample) {
		if (sample.isEmpty()) {
			return;
		}
		NetworkDispatcher.get().sendToServer(new MagnetFilterSamplePayload(cell,
				BuiltInRegistries.ITEM.getKey(sample.getItem()).toString()));
	}
}
