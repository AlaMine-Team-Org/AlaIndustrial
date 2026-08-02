package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus;
import dev.alaindustrial.menu.AssemblerMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Screen for the Assembler (MOD-275) — <b>two tabs in one window</b>.
 *
 * <p>The window used to do two unrelated jobs side by side: the 3×3 grid a recipe is <i>authored</i>
 * in sat next to the machine's live production state, and a playtester could not tell what had been
 * recorded, what was being made, or which grid was which. The two jobs now have a tab each and never
 * share screen space:
 *
 * <ul>
 *   <li><b>Work</b> — the running machine: the blueprint queue with the active one marked, the
 *       output area, the energy bar, the progress arrow, the status line, and a read-only picture of
 *       the active blueprint's layout and product.</li>
 *   <li><b>Record</b> — authoring: the nine ghost cells, the resolved result, the blank blueprint and
 *       the "Write" button.</li>
 * </ul>
 *
 * <p><b>A view switch, not a mode switch.</b> The machine keeps crafting while the player records; the
 * tab changes nothing but what is on screen. It is also why each tab is a whole atlas rather than a
 * region of one — see {@code tools/gen_assembler_gui.py}.
 *
 * <p>The grid cells are menu slots, so the vanilla slot pass draws and hover-highlights them for
 * free; what this class adds is the click routing that turns a click into a
 * {@code handleInventoryButtonClick} — the same channel {@link SawmillScreen} uses — and the buttons.
 *
 * <p>Not a {@link ProgressMachineScreen}: that base pairs its arrow with the standard left-hand energy
 * bar at a fixed anchor, and this frame is 46 pixels taller with its own bar position.
 */
public class AssemblerScreen extends MachineScreen<AssemblerMenu> {
	private static final Identifier WORK_TEXTURE =
			Industrialization.id("textures/gui/container/assembler.png");
	private static final Identifier RECORD_TEXTURE =
			Industrialization.id("textures/gui/container/assembler_record.png");

	/** Its atlases are 46 pixels taller than the machine standard: the tab strip and both bands need it. */
	private static final int GUI_WIDTH = 176;
	private static final int GUI_HEIGHT = 212;

	/** Wrap width of the substitution tooltip, in pixels — the frame's own width, so it never out-grows the GUI. */
	private static final int SUBSTITUTE_TOOLTIP_WIDTH = GUI_WIDTH;

	// --- the tab strip (both tabs) ---
	/**
	 * The tab strip, matching TAB_* in tools/gen_assembler_gui.py — the artwork and the hitboxes have to
	 * agree. Raised off the content band after the MOD-275 playtest: the tabs used to end one pixel
	 * above the first row of slots and read as a header for it rather than as buttons. The band under
	 * the strip line is now 6 px of clear panel; the slots below did not move.
	 */
	private static final int TAB_Y = 14, TAB_H = 16, TAB_W = 80;
	private static final int[] TAB_X = { 8, 88 };

	// --- Work tab ---
	/** Energy bar — hard against the left edge, clear of the queue column (see the atlas generator). */
	private static final EnergyBarSpec ENERGY = new EnergyBarSpec(8, 81, 176, 0);
	/** Progress arrow: dark track baked into the frame at this spot, lit sprite in the service area. */
	private static final int ARROW_X = 102, ARROW_Y = 54, ARROW_W = 24, ARROW_H = 17;
	private static final int ARROW_U = 176, ARROW_V = 48;
	/** Blueprint queue geometry, for the "this is the slot being worked" marker (frame, not item). */
	private static final int QUEUE_X = 24, QUEUE_Y = 36, QUEUE_PITCH = 18, SLOT_BOX = 18;
	/** Read-only copy of the active blueprint's layout: painted artwork, no slots behind it. */
	private static final int WORK_GRID_X = 47, WORK_GRID_Y = 37;
	/** What the active blueprint makes, drawn under the output area it will fill. */
	private static final int MAKES_X = 148, MAKES_Y = 96;
	/** Status line: the clear band under the machine row. */
	private static final int STATUS_X = 8, STATUS_Y = 97;

	// --- Record tab ---
	/** "Write" button, bottom-right of the recording band. */
	private static final int WRITE_X = 104, WRITE_Y = 95, WRITE_W = 64, WRITE_H = 18;

	/**
	 * Ingredient-substitution toggle, at the left end of the Record tab's bottom row — the strip the
	 * tab layout reserved for it (RESERVED_TOGGLE in tools/gen_assembler_gui.py). One button, not
	 * three: it acts on the blueprint the window is already showing — the one ringed in the queue —
	 * so there is never a question of which blueprint a press applies to, and the queue row keeps its
	 * three plain slots. It sits 62 px clear of "Write", so neither control crowds the other.
	 */
	private static final int SUB_X = 24, SUB_Y = 95, SUB_W = 18, SUB_H = 18;
	/** Glyph sprites in the service area: off at {@code u}, on one button-width to the right. */
	private static final int SUB_U = 176, SUB_V = 96;

	private static final int COLOR_TEXT = 0xFF404040;
	private static final int COLOR_DISABLED = 0x80303030;
	private static final int COLOR_HOVER = 0x40FFFFFF;
	/** MV orange — the queue slot the machine is running from right now. */
	private static final int COLOR_ACTIVE_MARK = 0xFFFF8A3D;
	/**
	 * Pale cyan — the queue slot whose layout the Work tab is showing, when it is not the running one.
	 * Deliberately lighter and cooler than anything in the atlas: the recipe cells are painted slate-blue
	 * inside an orange frame, so a mid-blue marker disappeared into it entirely (seen in the L3
	 * screenshots) and only a value this far from both reads as a mark.
	 */
	private static final int COLOR_SHOWN_MARK = 0xFF9BD6FF;
	/** Dims the read-only items — present, legible, plainly not sitting in a slot. */
	private static final int COLOR_PREVIEW_DIM = 0x8214202E;

	/**
	 * The queue slot whose layout the Work tab is showing. Sticky, and only here: the machine reports the
	 * slot it is <em>working</em> from, which is honestly {@code -1} for the one tick between finishing
	 * an operation and starting the next, and a marker that blinked off every two seconds would be worse
	 * than no marker. Holding the last one across that gap is a view concern, so it lives in the view;
	 * {@link AssemblerMenu#blueprintView} stays a pure function of synced state.
	 */
	private int shownBlueprint = -1;

	public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, GUI_WIDTH, GUI_HEIGHT);
		// The title moves up with the tab strip: vanilla's default 6 would run into a tab that now starts
		// at 14, and the point of raising the tabs was to give them room, not to take it from the title.
		this.titleLabelY = 3;
	}

	/** One atlas per tab: the two content bands are whole frames, not overlays. */
	@Override
	protected Identifier texture() {
		return onRecordTab() ? RECORD_TEXTURE : WORK_TEXTURE;
	}

	private boolean onRecordTab() {
		return this.menu.getActiveTab() == AssemblerMenu.TAB_RECORD;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		drawTabLabels(graphics, mouseX, mouseY);
		if (onRecordTab()) {
			return;
		}

		int x = this.leftPos;
		int y = this.topPos;
		renderEnergyBar(graphics, ENERGY);

		int max = this.menu.getMaxProgress();
		int filled = max > 0 ? this.menu.getProgress() * ARROW_W / max : 0;
		if (filled == 0 && this.menu.getProgress() > 0) {
			filled = 1; // immediate feedback rather than waiting for the integer fill to reach 1
		}
		if (filled > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, WORK_TEXTURE, x + ARROW_X, y + ARROW_Y,
					(float) ARROW_U, (float) ARROW_V, filled, ARROW_H, TEX_SIZE, TEX_SIZE);
		}

		refreshShownBlueprint();

		// Mark the queue. Two different claims, so two different marks: MV orange rings the blueprint the
		// machine is running from right now, blue rings the one whose layout the panel beside it shows.
		// They are the same slot while the machine works, and the orange is drawn last so it wins there.
		// Rings rather than fills, so the blueprint inside stays legible.
		int active = this.menu.getActiveBlueprintSlot();
		if (this.shownBlueprint >= 0 && this.shownBlueprint != active) {
			drawQueueRing(graphics, x, y, this.shownBlueprint, COLOR_SHOWN_MARK);
		}
		if (active >= 0 && active < AssemblerBlockEntity.BLUEPRINT_SLOT_COUNT) {
			drawQueueRing(graphics, x, y, active, COLOR_ACTIVE_MARK);
		}
	}

	/**
	 * The two tab labels, and the hover tint. The labels are text rather than baked art because they are
	 * translatable; the shading that says which tab is in front is baked into each atlas.
	 */
	private void drawTabLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int selected = this.menu.getActiveTab();
		for (int tab = 0; tab < TAB_X.length; tab++) {
			int tx = this.leftPos + TAB_X[tab];
			int ty = this.topPos + TAB_Y;
			if (tab != selected && isOverTab(tab, mouseX, mouseY)) {
				graphics.fill(tx + 1, ty + 1, tx + TAB_W - 1, ty + TAB_H - 1, COLOR_HOVER);
			}
			Component label = Component.translatable(tab == AssemblerMenu.TAB_RECORD
					? "gui.alaindustrial.assembler.tab.record" : "gui.alaindustrial.assembler.tab.work");
			graphics.text(this.font, label, tx + (TAB_W - this.font.width(label)) / 2,
					ty + (TAB_H - this.font.lineHeight) / 2 + 1,
					tab == selected ? COLOR_TEXT : 0xFF6E6E6E, false);
		}
	}

	/**
	 * Keep {@link #shownBlueprint} pointing at a queue slot that still holds a blueprint: the working one
	 * whenever the machine names one, otherwise whatever it was, otherwise the first recorded blueprint.
	 */
	private void refreshShownBlueprint() {
		int active = this.menu.getActiveBlueprintSlot();
		if (this.menu.isRecordedBlueprint(active)) {
			this.shownBlueprint = active;
		} else if (!this.menu.isRecordedBlueprint(this.shownBlueprint)) {
			this.shownBlueprint = this.menu.firstRecordedBlueprintSlot();
		}
	}

	/** One-pixel ring around queue slot {@code index}. */
	private static void drawQueueRing(GuiGraphicsExtractor graphics, int x, int y, int index, int colour) {
		int sx = x + QUEUE_X;
		int sy = y + QUEUE_Y + index * QUEUE_PITCH;
		graphics.fill(sx, sy, sx + SLOT_BOX, sy + 1, colour);
		graphics.fill(sx, sy + SLOT_BOX - 1, sx + SLOT_BOX, sy + SLOT_BOX, colour);
		graphics.fill(sx, sy, sx + 1, sy + SLOT_BOX, colour);
		graphics.fill(sx + SLOT_BOX - 1, sy, sx + SLOT_BOX, sy + SLOT_BOX, colour);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		if (this.menu.isPanelOpen()) {
			return; // the modal upgrade panel owns this area
		}
		if (onRecordTab()) {
			drawWriteButton(graphics, mouseX, mouseY);
			drawSubstituteToggle(graphics, mouseX, mouseY);
		} else {
			drawActiveBlueprint(graphics);
			graphics.text(this.font, statusLine(), this.leftPos + STATUS_X, this.topPos + STATUS_Y,
					COLOR_TEXT, false);
		}
	}

	/**
	 * "Write": greyed out when it would do nothing (no recipe in the grid, or no blank in the slot
	 * beside it), so the player learns the precondition from the button instead of from a click that
	 * does nothing.
	 */
	private void drawWriteButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		boolean enabled = this.menu.canWrite();
		int bx = this.leftPos + WRITE_X;
		int by = this.topPos + WRITE_Y;
		if (!enabled) {
			graphics.fill(bx + 1, by + 1, bx + WRITE_W - 1, by + WRITE_H - 1, COLOR_DISABLED);
		} else if (isOverWrite(mouseX, mouseY)) {
			graphics.fill(bx + 1, by + 1, bx + WRITE_W - 1, by + WRITE_H - 1, COLOR_HOVER);
		}
		Component label = Component.translatable("gui.alaindustrial.assembler.write");
		graphics.text(this.font, label, bx + (WRITE_W - this.font.width(label)) / 2, by + 5,
				enabled ? COLOR_TEXT : 0xFF6E6E6E, false);
	}

	/**
	 * The substitution toggle: greyed out with no blueprint to apply it to, and otherwise showing the
	 * state of the blueprint the window is displaying — green arrows for on, grey for off.
	 */
	private void drawSubstituteToggle(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int sx = this.leftPos + SUB_X;
		int sy = this.topPos + SUB_Y;
		boolean enabled = this.menu.isRecordedBlueprint(this.shownBlueprint);
		boolean on = enabled && this.menu.substitutes(this.shownBlueprint);
		// RECORD_TEXTURE, not the tab-dependent one: the toggle is only ever drawn on the Record tab,
		// and its two glyph sprites are baked into that atlas's service area.
		graphics.blit(RenderPipelines.GUI_TEXTURED, RECORD_TEXTURE, sx, sy,
				(float) (SUB_U + (on ? SUB_W : 0)), (float) SUB_V, SUB_W, SUB_H, TEX_SIZE, TEX_SIZE);
		if (!enabled) {
			graphics.fill(sx + 1, sy + 1, sx + SUB_W - 1, sy + SUB_H - 1, COLOR_DISABLED);
		} else if (isOverSubstitute(mouseX, mouseY)) {
			graphics.fill(sx + 1, sy + 1, sx + SUB_W - 1, sy + SUB_H - 1, COLOR_HOVER);
		}
	}

	/**
	 * The Work tab's answer to "the machine is clearly building something, but what?": the active
	 * blueprint's nine cells painted into the recipe-grid artwork, and its product under the output area
	 * it is filling.
	 *
	 * <p><b>Drawn, never stored.</b> There is no menu slot anywhere in this region — the cells are
	 * artwork in {@code assembler.png} and the items are pixels on top of it. Nothing can be picked up
	 * from here, on any client, because there is nothing here to pick up. Every item is dimmed so it
	 * never reads as an item sitting in a slot.
	 */
	private void drawActiveBlueprint(GuiGraphicsExtractor graphics) {
		AssemblerMenu.BlueprintView view = this.menu.blueprintView(this.shownBlueprint);
		if (!view.isPresent()) {
			return;
		}
		for (int i = 0; i < AssemblerBlockEntity.PATTERN_GRID_SIZE; i++) {
			ItemStack cell = view.cells().get(i);
			if (cell.isEmpty()) {
				continue;
			}
			drawDimmedItem(graphics, cell,
					this.leftPos + WORK_GRID_X + (i % 3) * QUEUE_PITCH,
					this.topPos + WORK_GRID_Y + (i / 3) * QUEUE_PITCH, false);
		}
		ItemStack result = view.result();
		if (!result.isEmpty()) {
			drawDimmedItem(graphics, result, this.leftPos + MAKES_X, this.topPos + MAKES_Y, true);
		}
	}

	/**
	 * One read-only item: drawn, washed over, and only then decorated. The count is painted on top of the
	 * wash on purpose — "×4" is the half of "Blueprint: Stick ×4" that the grid can show, and dimming it
	 * along with the icon would cost the reading for no gain.
	 */
	private void drawDimmedItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y,
			boolean withCount) {
		graphics.item(stack, x, y);
		graphics.fill(x, y, x + 16, y + 16, COLOR_PREVIEW_DIM);
		if (withCount) {
			graphics.itemDecorations(this.font, stack, x, y, null);
		}
	}

	/** What the machine is doing, or the reason it is not. Red where only the player can unstick it. */
	private Component statusLine() {
		AssemblerStatus status = this.menu.getStatus();
		ChatFormatting colour = switch (status) {
			case READY -> ChatFormatting.DARK_GREEN;
			case OUTPUT_FULL, NO_RECIPE -> ChatFormatting.DARK_RED;
			default -> ChatFormatting.DARK_GRAY;
		};
		return Component.translatable(status.translationKey()).withStyle(colour);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (this.menu.isPanelOpen()) {
			return;
		}
		int tab = tabAt(mouseX, mouseY);
		if (tab >= 0) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable(
					tab == AssemblerMenu.TAB_RECORD
							? "gui.alaindustrial.assembler.tab.record.hint"
							: "gui.alaindustrial.assembler.tab.work.hint"), mouseX, mouseY);
			return;
		}
		if (onRecordTab()) {
			recordTooltip(graphics, mouseX, mouseY);
		} else {
			workTooltip(graphics, mouseX, mouseY);
		}
	}

	private void recordTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (isOverWrite(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.assembler.write.hint"), mouseX, mouseY);
			return;
		}
		if (isOverSubstitute(mouseX, mouseY)) {
			// Three lines, because the risk is the part a player cannot see: what it does, what state it
			// is in, and the warning that a plain Ingredient ignores components. Each is wrapped to
			// SUBSTITUTE_TOOLTIP_WIDTH first: a translation is free to be longer than English, and an
			// unwrapped tooltip runs off the edge on a small screen instead of folding.
			List<Component> lines = new ArrayList<>(3);
			lines.add(Component.translatable("gui.alaindustrial.assembler.substitute"));
			lines.add(Component.translatable(this.menu.substitutes(this.shownBlueprint)
					? "gui.alaindustrial.assembler.substitute.on"
					: "gui.alaindustrial.assembler.substitute.off").withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("gui.alaindustrial.assembler.substitute.warning")
					.withStyle(ChatFormatting.DARK_GRAY));
			List<FormattedCharSequence> wrapped = new ArrayList<>(lines.size());
			for (Component line : lines) {
				wrapped.addAll(this.font.split(line, SUBSTITUTE_TOOLTIP_WIDTH));
			}
			graphics.setTooltipForNextFrame(this.font, wrapped, mouseX, mouseY);
			return;
		}
		if (ghostSlotAt(mouseX, mouseY) >= 0 && this.menu.getPatternResult().isEmpty()) {
			// An empty grid says nothing by itself; spell out that the cells are filled from the cursor.
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.assembler.pattern.hint"), mouseX, mouseY);
		}
	}

	private void workTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		renderEnergyTooltip(graphics, mouseX, mouseY, ENERGY);
		AssemblerMenu.BlueprintView view = this.menu.blueprintView(this.shownBlueprint);
		if (!view.isPresent()) {
			return;
		}
		int cell = workGridCellAt(mouseX, mouseY);
		boolean overMakes = isOverBox(MAKES_X, MAKES_Y, mouseX, mouseY);
		if (cell < 0 && !overMakes) {
			return;
		}
		// A read-only layout: name the item under the cursor (it is drawn, not in a slot, so the vanilla
		// slot tooltip never fires) and say whose layout this is and that it is not editable.
		ItemStack shown = overMakes ? view.result() : view.cells().get(cell);
		List<Component> lines = new ArrayList<>(2);
		if (!shown.isEmpty()) {
			lines.add(shown.getHoverName());
		}
		lines.add(Component.translatable("gui.alaindustrial.assembler.pattern.preview",
				view.blueprintSlot() + 1).withStyle(ChatFormatting.GRAY));
		graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
	}

	private boolean isOverBox(int x, int y, double mx, double my) {
		int sx = this.leftPos + x;
		int sy = this.topPos + y;
		return mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16;
	}

	private boolean isOverSubstitute(double mx, double my) {
		int bx = this.leftPos + SUB_X;
		int by = this.topPos + SUB_Y;
		return mx >= bx && mx < bx + SUB_W && my >= by && my < by + SUB_H;
	}

	private boolean isOverWrite(double mx, double my) {
		int bx = this.leftPos + WRITE_X;
		int by = this.topPos + WRITE_Y;
		return mx >= bx && mx < bx + WRITE_W && my >= by && my < by + WRITE_H;
	}

	private boolean isOverTab(int tab, double mx, double my) {
		int tx = this.leftPos + TAB_X[tab];
		int ty = this.topPos + TAB_Y;
		return mx >= tx && mx < tx + TAB_W && my >= ty && my < ty + TAB_H;
	}

	/** Index of the tab under the cursor, or {@code -1}. */
	private int tabAt(double mx, double my) {
		for (int tab = 0; tab < TAB_X.length; tab++) {
			if (isOverTab(tab, mx, my)) {
				return tab;
			}
		}
		return -1;
	}

	/** Index (0..8) of the Work tab's painted read-only cell under the cursor, or {@code -1}. */
	private int workGridCellAt(double mx, double my) {
		for (int i = 0; i < AssemblerBlockEntity.PATTERN_GRID_SIZE; i++) {
			if (isOverBox(WORK_GRID_X + (i % 3) * QUEUE_PITCH, WORK_GRID_Y + (i / 3) * QUEUE_PITCH, mx, my)) {
				return i;
			}
		}
		return -1;
	}

	/** Index (0..8) of the ghost cell under the cursor, or {@code -1}. */
	private int ghostSlotAt(double mx, double my) {
		for (int i = 0; i < AssemblerBlockEntity.PATTERN_GRID_SIZE; i++) {
			Slot slot = this.menu.slots.get(AssemblerMenu.GRID_SLOT_START + i);
			if (isOverBox(slot.x, slot.y, mx, my)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Tab switches, pattern-grid clicks and "Write" all become container-button presses. Intercepted
	 * before {@code super}, so the vanilla slot path never sees them — the ghost slots would refuse the
	 * click anyway, but the round trip would be wasted and a shift-click would be ambiguous.
	 *
	 * <p>The tab is applied locally <em>and</em> sent: {@code handleInventoryButtonClick} only puts a
	 * packet on the wire, and both sides need the new tab — the client to draw it, the server to know
	 * which slots the player can now reach.
	 *
	 * <p>Deferred while the modal upgrade panel is open: it is drawn over this area and owns clicks
	 * inside its footprint.
	 */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && !this.menu.isPanelOpen() && this.minecraft != null
				&& this.minecraft.gameMode != null) {
			int tab = tabAt(event.x(), event.y());
			if (tab >= 0) {
				if (tab != this.menu.getActiveTab()) {
					this.menu.setActiveTab(tab);
					this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
							AssemblerMenu.BUTTON_TAB_BASE + tab);
				}
				return true;
			}
			if (onRecordTab()) {
				if (isOverWrite(event.x(), event.y())) {
					if (this.menu.canWrite()) {
						this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
								AssemblerMenu.BUTTON_WRITE);
					}
					return true;
				}
				// The toggle lives on the Record tab only, so it is inside this branch: on the Work tab
				// those pixels are the status band and must stay inert.
				if (isOverSubstitute(event.x(), event.y())) {
					if (this.menu.isRecordedBlueprint(this.shownBlueprint)) {
						this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
								AssemblerMenu.BUTTON_SUBSTITUTE_BASE + this.shownBlueprint);
					}
					return true;
				}
				int cell = ghostSlotAt(event.x(), event.y());
				if (cell >= 0) {
					// The server reads the item off the cursor itself; an empty cursor clears the cell.
					this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
							AssemblerMenu.BUTTON_CELL_BASE + cell);
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
}
