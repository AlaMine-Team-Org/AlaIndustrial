package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.client.screen.tabs.PageText;
import dev.alaindustrial.client.screen.tabs.SideTabStrip;
import dev.alaindustrial.client.screen.tabs.TabPage;
import dev.alaindustrial.client.screen.works.ConstructionTab;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.item.misc.RestorationPlanItem;
import dev.alaindustrial.menu.TownDispatcherMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.drone.DispatcherStatus;
import dev.alaindustrial.settlement.drone.DispatcherView;
import dev.alaindustrial.settlement.drone.DispatcherViews;
import dev.alaindustrial.settlement.drone.StartReadiness;
import dev.alaindustrial.settlement.plan.PlanSheetHints;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.WorkJournal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

// size-justified: one screen of four tabs that share the panel, the slots and the strip; each tab is a short
// method group, and splitting them into classes would only pass the same fields around.
/**
 * Screen of the town dispatcher (MOD-858, MOD-860; reworked after the owner's games, rounds 1 and 2): a wide window
 * with the mod's side tabs down its left edge, as the settlement core's.
 *
 * <ul>
 *   <li>«Task» (opens first) — the plan row (three sheet slots, «Assemble», the plan slot, «Start») and, in a framed
 *       area above the inventory, a checklist for a player who has never seen the dispatcher: the plan assembled, a
 *       nest with a drone over the hall, the stage's materials in the buffer, the construction started — a tick or a
 *       cross each, and every open step says in one short line what to do next. The checklist is clipped to its area
 *       and scrolls with the wheel when it is taller, so it never reaches the inventory (round 2). «Start»
 *       ({@link TownDispatcherMenu#BUTTON_START}) is drawn disabled until {@link StartReadiness} has nothing against
 *       it, and its tooltip lists what is missing; short materials are a warning there, not a blocker.
 *   <li>«Buffer» — the 27 material slots.
 *   <li>«Materials» — the current stage's bill as item icons with «have / need», red while short, a tick when covered;
 *       hovering an icon shows the item's own tooltip; the wheel scrolls a longer bill; a shift-click in the
 *       inventory puts into the buffer only what the stage still asks for (R2-1).
 *   <li>«Journal» — what the drones did, newest first, wrapped, each line with its game time.
 * </ul>
 *
 * <p>The tabs' data is worded on the server and reaches the client in the dispatcher's update tag
 * ({@link DispatcherView}, {@link WorkJournal}); the counts of nests and the viewer's rights come from the menu's
 * channels. Nothing here changes anything for a viewer who does not control the dispatcher (R95): the slots refuse
 * them and the buttons are drawn disabled. All text wraps inside the panel; nothing is cut.
 */
public class TownDispatcherScreen extends MachineScreen<TownDispatcherMenu> {

	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/town_dispatcher.png");
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
	private static final Identifier BUTTON_OFF = Identifier.withDefaultNamespace("widget/button_disabled");
	private static final Identifier DONE = Identifier.withDefaultNamespace("container/beacon/confirm");
	private static final Identifier OPEN = Identifier.withDefaultNamespace("container/beacon/cancel");

	/** The tabs down the left edge, in their order. */
	public enum Tab {
		TASK(() -> new ItemStack(ModContent.RESTORATION_PLAN.get())),
		ORDERS(() -> new ItemStack(ModContent.HOUSE_PLAN.get())),
		BUFFER(() -> new ItemStack(Items.CHEST)),
		MATERIALS(() -> new ItemStack(Items.BRICKS)),
		JOURNAL(() -> new ItemStack(Items.WRITABLE_BOOK));

		private final Supplier<ItemStack> icon;

		Tab(Supplier<ItemStack> icon) {
			this.icon = icon;
		}

		public String key() {
			return "settlement.alaindustrial.dispatcher.tab." + name().toLowerCase(Locale.ROOT);
		}
	}

	public static final int INNER_LEFT = 8;
	public static final int INNER_RIGHT = TownDispatcherMenu.IMAGE_WIDTH - 8;
	public static final int TITLE_Y = 6;
	public static final int STATUS_Y = 17;
	/** The panel every tab draws in. */
	public static final int PANEL_TOP = 28;
	public static final int PANEL_BOTTOM = TownDispatcherMenu.INVENTORY_Y - 13;
	/** «Assemble the plan»: between the sheets and the plan slot. */
	public static final int ASSEMBLE_X = 74;
	public static final int ASSEMBLE_W = 72;
	public static final int BUTTON_H = 16;
	/** «Start»: the last of the plan row, right of the plan slot. */
	public static final int START_X = 176;
	public static final int START_W = 70;
	/** The checklist's framed area under the plan row, down to the panel's bottom. */
	public static final int STEPS_TOP = TownDispatcherMenu.ROW_Y + 26;
	public static final int STEP_TEXT_X = 22;
	/** Room kept at the right of the checklist for its scroll bar. */
	private static final int SCROLLBAR_W = 4;
	/** Tooltip lines wrap at this width. */
	private static final int TOOLTIP_W = 170;
	/** The material grid: cells of an icon over its «have / need». */
	public static final int CELL_W = 30;
	public static final int CELL_H = 26;
	public static final int GRID_TOP = PANEL_TOP + 22;
	private static final int GRID_COLUMNS = (INNER_RIGHT - INNER_LEFT - 4) / CELL_W;
	/** The grid centred in the panel. */
	private static final int GRID_LEFT = INNER_LEFT + (INNER_RIGHT - INNER_LEFT - GRID_COLUMNS * CELL_W) / 2;
	private static final int GRID_ROWS = (PANEL_BOTTOM - GRID_TOP) / CELL_H;
	private static final float HINT_SCALE = 0.75f;
	private static final float COUNT_SCALE = 0.75f;
	private static final int LINE_H = 10;
	/** The atlas: the window in its left half, the spare slot well right of it (tools/gen_town_drone_assets.py). */
	private static final int ATLAS_W = 512;
	private static final int ATLAS_H = 256;
	private static final int WELL_U = TownDispatcherMenu.IMAGE_WIDTH + 4;

	/** How far from the player the screen looks for its block when it does not know it. */
	private static final int REACH = 8;

	private static final List<TabPage> PAGES = List.of(new Page(Tab.TASK), new Page(Tab.ORDERS), new Page(Tab.BUFFER),
			new Page(Tab.MATERIALS), new Page(Tab.JOURNAL));
	/** The «Orders» tab: one order a row of two lines, its buttons at the right. */
	public static final int ORDER_ROW_H = 22;
	public static final int ORDER_BUTTON = 12;
	private static final int ORDER_ROWS = (PANEL_BOTTOM - PANEL_TOP - 4) / ORDER_ROW_H;
	private static final String[] ORDER_GLYPHS = {"\u25B2", "\u25BC", "\u2715"};
	private static final TownDispatcherBlockEntity.OrderPress[] ORDER_BUTTONS = {
		TownDispatcherBlockEntity.OrderPress.UP, TownDispatcherBlockEntity.OrderPress.DOWN,
		TownDispatcherBlockEntity.OrderPress.CANCEL};

	private final SideTabStrip strip = new SideTabStrip(0);
	private Tab tab = Tab.TASK;
	/** The dispatcher's position, once known. */
	private @Nullable BlockPos target;
	/** The open list's first row: pixels on «Task», bill rows on «Materials», wrapped lines on «Journal». */
	private int scroll;
	/** How far the checklist scrolls at most, as of the last frame. */
	private int taskScrollMax;

	public TownDispatcherScreen(TownDispatcherMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, TownDispatcherMenu.IMAGE_WIDTH, TownDispatcherMenu.IMAGE_HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		this.inventoryLabelX = TownDispatcherMenu.INVENTORY_X;
		this.inventoryLabelY = TownDispatcherMenu.INVENTORY_Y - 11;
		if (this.minecraft != null && this.minecraft.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK) {
			target = hit.getBlockPos();
		}
		this.menu.billSource(() -> view().bill());
		openTab(tab);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	/** The window is 256 wide, so its atlas is twice the usual size (owner's round 2). */
	@Override
	protected void blitStaticFrame(GuiGraphicsExtractor graphics) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth,
				this.imageHeight, ATLAS_W, ATLAS_H);
	}

	@Override
	protected boolean hasStatsTab() {
		return false;
	}

	/** Which tab is open; a test frame opens one directly. */
	public void openTab(Tab next) {
		this.tab = next;
		this.scroll = 0;
		this.menu.showSlots(next == Tab.TASK, next == Tab.BUFFER);
		this.menu.showMaterials(next == Tab.MATERIALS);
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, next == Tab.MATERIALS
					? TownDispatcherMenu.BUTTON_MATERIALS_SHOWN : TownDispatcherMenu.BUTTON_MATERIALS_HIDDEN);
		}
	}

	/** The block's name over the «Task» tab, the tab's name over the others; the inventory label as usual. */
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Component heading = tab == Tab.TASK ? this.title : Component.translatable(tab.key());
		graphics.text(this.font, heading, (this.imageWidth - this.font.width(heading)) / 2, TITLE_Y, GuiStyle.LABEL,
				false);
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
				GuiStyle.LABEL, false);
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		strip.draw(graphics, this.leftPos, this.topPos, PAGES, tab.ordinal());
		for (Slot slot : this.menu.slots) {
			if (!(slot.container instanceof Inventory) && slot.isActive()) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos + slot.x - 1,
						this.topPos + slot.y - 1, WELL_U, 0, 18, 18, ATLAS_W, ATLAS_H);
			}
		}
		if (tab == Tab.MATERIALS || tab == Tab.JOURNAL || tab == Tab.ORDERS) {
			card(graphics, PANEL_TOP);
		}
		if (tab == Tab.TASK) {
			card(graphics, STEPS_TOP - 4);
			button(graphics, ASSEMBLE_X, TownDispatcherMenu.ROW_Y, ASSEMBLE_W,
					Component.translatable("settlement.alaindustrial.dispatcher.assemble"), this.menu.viewerControls(),
					mouseX, mouseY);
			button(graphics, START_X, TownDispatcherMenu.ROW_Y, START_W, Component.translatable(key("start")),
					startBlockers(view()).isEmpty(), mouseX, mouseY);
		}
	}

	@Override
	protected void drawFrameText(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Component reason = reasonText();
		DispatcherStatus status = this.menu.getStatus();
		Component line = reason != null ? reason : Component.translatable(status.key());
		int colour = reason != null || status.blocking() ? GuiStyle.STATUS_BLOCKING : GuiStyle.TEXT_DIM;
		drawFittedStatus(graphics, line, STATUS_Y, INNER_LEFT, INNER_RIGHT, colour);
		DispatcherView view = view();
		switch (tab) {
			case TASK -> drawChecklist(graphics, view);
			case BUFFER -> paragraph(graphics, Component.translatable(key("buffer.hint")), INNER_LEFT + 4,
					TownDispatcherMenu.BUFFER_Y + 3 * 18 + 4, INNER_RIGHT - INNER_LEFT - 8, 1.0f,
					GuiStyle.TEXT_DIM, 4);
			case ORDERS -> drawOrders(graphics, view, mouseX, mouseY);
			case MATERIALS -> drawMaterials(graphics, view);
			case JOURNAL -> drawJournal(graphics);
		}
	}

	// --- «Task»: the checklist ---

	// size-justified: four steps drawn one after the other in one scissored, scrolled area; splitting them would only
	// pass the running y and the scroll around.
	/**
	 * The four steps of a first restoration; an open step says what to do next. Drawn inside the framed area only
	 * (scissor), shifted up by the scroll; a taller list gets a scroll bar and the wheel.
	 */
	private void drawChecklist(GuiGraphicsExtractor graphics, DispatcherView view) {
		DispatcherView.Checklist list = view.checklist();
		boolean owner = this.menu.viewerControls();
		scroll = Math.max(0, Math.min(scroll, taskScrollMax));
		graphics.enableScissor(this.leftPos + INNER_LEFT + 1, this.topPos + STEPS_TOP - 3,
				this.leftPos + INNER_RIGHT - 1, this.topPos + PANEL_BOTTOM - 1);
		int first = STEPS_TOP - scroll;
		int y = first;
		String planTemplate = planTemplate();
		// A house of a type the town has learned needs nothing in the slot (round 2 of 0.1.219, Q4).
		boolean plan = list.planKnown() || planTemplate != null && (list.fits(planTemplate) || !list.town());
		String planNote = plan ? "step.plan.done" : planTemplate != null ? "step.plan.wrong" : "step.plan.todo";
		// The server's own note, when it knows which plan the building needs (R2-6).
		Component planLine = view.planNote().isEmpty() ? Component.translatable(key(planNote))
				: ConstructionTab.component(view.planNote().getFirst());
		y = step(graphics, y, plan, Component.translatable(key("step.plan")), List.of(planLine));
		int ready = this.menu.getReadyNests();
		boolean nests = ready > 0;
		y = step(graphics, y, nests, Component.translatable(key("step.nests"), ready, this.menu.getNestLimit()),
				nests ? List.of() : List.of(Component.translatable(key("step.nests.todo"))));
		boolean materials = list.town() && (!list.stage() || view.billCovered());
		y = step(graphics, y, materials, Component.translatable(key("step.materials")),
				materials ? List.of() : List.of(Component.translatable(key("step.materials.todo"))));
		List<Component> notes = new ArrayList<>();
		if (list.jobs()) {
			// Owner's round 1 of 0.1.219 (R1-5): the work runs, so «Start» has nothing to start; a stop says why and
			// what to do, in red.
			notes.add(Component.translatable(key("step.build.started")));
			for (ConstructionRow.Text text : view.task()) {
				Component line = ConstructionTab.component(text);
				notes.add(text.key().startsWith(REASON_PREFIX)
						? line.copy().withColor(GuiStyle.STATUS_BLOCKING & 0xFFFFFF) : line);
			}
		} else if (list.restored()) {
			notes.add(Component.translatable(key("step.build.restored")));
			view.task().forEach(text -> notes.add(ConstructionTab.component(text))); // R2-6: «type N restored»
		} else if (!owner) {
			notes.add(Component.translatable(key("step.build.owner")));
		} else {
			notes.add(Component.translatable(key(startBlockers(view).isEmpty() ? "step.build.todo"
					: "step.build.wait")));
			// A called-off order, and why a start would raise nothing — the block in the way, in red (round 5 of
			// 0.1.219).
			for (ConstructionRow.Text text : view.task()) {
				if (text.key().startsWith(REASON_PREFIX)) {
					notes.add(ConstructionTab.component(text).copy().withColor(GuiStyle.STATUS_BLOCKING & 0xFFFFFF));
				} else if (text.key().equals(DispatcherViews.ORDER_CALLED_OFF)) {
					notes.add(ConstructionTab.component(text));
				}
			}
		}
		Component title = Component.translatable(key("step.build"));
		for (DispatcherView.OrderLine order : view.orders()) {
			if (order.selected()) {
				title = title.copy().append(": ").append(ConstructionTab.component(order.name()));
			}
		}
		y = step(graphics, y, list.restored() || list.jobs() && !list.stalled(), title, notes);
		graphics.disableScissor();
		int area = PANEL_BOTTOM - STEPS_TOP - 2;
		int content = y - first;
		taskScrollMax = Math.max(0, content - area);
		if (taskScrollMax > 0) {
			int trackTop = this.topPos + STEPS_TOP - 2;
			int thumb = Math.max(8, area * area / content);
			int thumbTop = trackTop + (area - thumb) * Math.min(scroll, taskScrollMax) / taskScrollMax;
			int x = this.leftPos + INNER_RIGHT - 2 - SCROLLBAR_W;
			graphics.fill(x, trackTop, x + SCROLLBAR_W, trackTop + area, GuiStyle.SLOT);
			graphics.fill(x, thumbTop, x + SCROLLBAR_W, thumbTop + thumb, GuiStyle.TEXT_DIM);
		}
	}

	/**
	 * One step at {@code y}: the tick or cross, the title, the notes under it (wrapped, smaller). Returns the next
	 * step's top.
	 */
	private int step(GuiGraphicsExtractor graphics, int y, boolean done, Component title, List<Component> notes) {
		if (done) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DONE, this.leftPos + INNER_LEFT + 4, this.topPos + y - 1,
					10, 10, GuiStyle.STATUS_GOOD_TICK);
		} else {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OPEN, this.leftPos + INNER_LEFT + 4, this.topPos + y - 1,
					10, 10);
		}
		int width = INNER_RIGHT - STEP_TEXT_X - 4 - SCROLLBAR_W;
		int lines = paragraph(graphics, title, STEP_TEXT_X, y, width, 1.0f, done ? GuiStyle.STATUS_GOOD : GuiStyle.TEXT,
				2);
		int at = y + lines * LINE_H;
		for (Component note : notes) {
			int used = paragraph(graphics, note, STEP_TEXT_X, at, width, HINT_SCALE, GuiStyle.TEXT_DIM, 3);
			at += Math.round(used * LINE_H * HINT_SCALE);
		}
		return at + 4;
	}

	/** The keys of a job's stop and its hint: drawn in red under the «Construction» step (R1-5). */
	private static final String REASON_PREFIX = "settlement.alaindustrial.works.reason.";

	/** The intact template the plan in the plan slot names, or null for an empty slot. */
	private @Nullable String planTemplate() {
		Slot slot = this.menu.machineSlot(TownDispatcherBlockEntity.PLAN_SLOT);
		RestorationPlan plan = slot == null ? null : RestorationPlanItem.planOf(slot.getItem());
		return plan == null ? null : plan.template().toString();
	}

	/** What stands between the viewer and «Start» this frame; empty when it may be pressed. */
	private List<StartReadiness.Blocker> startBlockers(DispatcherView view) {
		return StartReadiness.blockers(this.menu.viewerControls(), view.checklist(), planTemplate(),
				this.menu.getReadyNests());
	}

	/** «Start»'s tooltip: what it does, or what is missing; short materials as a warning under it, wrapped. */
	private List<FormattedCharSequence> startTooltip() {
		DispatcherView view = view();
		List<StartReadiness.Blocker> blockers = startBlockers(view);
		List<Component> lines = new ArrayList<>();
		if (blockers.isEmpty()) {
			lines.add(Component.translatable(key("start.hint")));
		} else {
			lines.add(Component.translatable(key("start.missing")).withStyle(ChatFormatting.GRAY));
			blockers.forEach(blocker -> lines.add(Component.translatable(blocker.key()).withStyle(ChatFormatting.RED)));
		}
		if (StartReadiness.materialsShort(view.checklist(), view.billCovered())
				&& !blockers.contains(StartReadiness.Blocker.STARTED)) {
			lines.add(Component.translatable(StartReadiness.MATERIALS_SHORT_KEY).withStyle(ChatFormatting.GOLD));
		}
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) {
			wrapped.addAll(this.font.split(line, PageText.wrapWidth(line, TOOLTIP_W)));
		}
		return wrapped;
	}

	/** A framed card from {@code top} to the panel's bottom, the width of the panel. */
	private void card(GuiGraphicsExtractor graphics, int top) {
		int x0 = this.leftPos + INNER_LEFT;
		int y0 = this.topPos + top;
		graphics.fill(x0, y0, this.leftPos + INNER_RIGHT, this.topPos + PANEL_BOTTOM, GuiStyle.SLOT_EDGE);
		graphics.fill(x0 + 1, y0 + 1, this.leftPos + INNER_RIGHT - 1, this.topPos + PANEL_BOTTOM - 1, GuiStyle.CARD);
	}

	// --- «Orders» (round 2 of 0.1.219, Q3) ---

	/**
	 * The orders, first first: the building's name — highlighted for the one the «Task» and «Materials» tabs show —
	 * and under it where its work stands, red while it stands still; the owner's buttons «earlier», «later» and
	 * «cancel» at the right. A click on a row shows that order on the other tabs.
	 */
	private void drawOrders(GuiGraphicsExtractor graphics, DispatcherView view, int mouseX, int mouseY) {
		List<DispatcherView.OrderLine> orders = view.orders();
		if (orders.isEmpty()) {
			paragraph(graphics, Component.translatable(key("no_orders")), INNER_LEFT + 4, PANEL_TOP + 4,
					INNER_RIGHT - INNER_LEFT - 8, 1.0f, GuiStyle.TEXT_DIM, 4);
			return;
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, orders.size() - ORDER_ROWS)));
		for (int row = 0; row < ORDER_ROWS && scroll + row < orders.size(); row++) {
			DispatcherView.OrderLine order = orders.get(scroll + row);
			int y = PANEL_TOP + 3 + row * ORDER_ROW_H;
			// Every order on a light card, the shown one framed (owner's round 3 of 0.1.219, R3-1): dark and red ink
			// read on it, as they did not on the brown highlight.
			int left = this.leftPos + INNER_LEFT + 2;
			int top = this.topPos + y - 1;
			int right = this.leftPos + INNER_RIGHT - 2;
			int bottom = this.topPos + y + ORDER_ROW_H - 2;
			graphics.fill(left, top, right, bottom, order.selected() ? GuiStyle.BUTTON_ACTIVE : GuiStyle.PANEL_LO);
			graphics.fill(left + 1, top + 1, right - 1, bottom - 1, GuiStyle.PANEL_HI);
			int textRight = orderButtonsLeft() - 4;
			Component name = Component.literal((scroll + row + 1) + ". ")
					.append(ConstructionTab.component(order.name()));
			drawFittedStatus(graphics, name, y + 1, INNER_LEFT + 4, textRight,
					order.taken() ? GuiStyle.STATUS_GOOD : GuiStyle.TEXT);
			paragraph(graphics, ConstructionTab.component(order.step()), INNER_LEFT + 10, y + 11,
					textRight - INNER_LEFT - 10, HINT_SCALE,
					order.blocked() ? GuiStyle.STATUS_BLOCKING : GuiStyle.TEXT_DIM, 1);
			if (this.menu.viewerControls()) {
				for (int b = 0; b < ORDER_BUTTONS.length; b++) {
					int x = orderButtonsLeft() + b * (ORDER_BUTTON + 2);
					boolean hovered = isHovering(x, y + 3, ORDER_BUTTON, ORDER_BUTTON, mouseX, mouseY);
					graphics.fill(this.leftPos + x, this.topPos + y + 3, this.leftPos + x + ORDER_BUTTON,
							this.topPos + y + 3 + ORDER_BUTTON, hovered ? GuiStyle.BUTTON_ACTIVE : GuiStyle.BUTTON);
					String glyph = ORDER_GLYPHS[b];
					graphics.text(this.font, glyph,
							this.leftPos + x + (ORDER_BUTTON - this.font.width(glyph) + 1) / 2,
							this.topPos + y + 5, GuiStyle.HEADER_TEXT, false);
				}
			}
		}
	}

	private static int orderButtonsLeft() {
		return INNER_RIGHT - 4 - ORDER_BUTTONS.length * (ORDER_BUTTON + 2);
	}

	/** The order row under the mouse on the «Orders» tab, as an index into the view's orders, or -1. */
	private int orderRowAt(double mouseX, double mouseY) {
		if (tab != Tab.ORDERS || mouseX < this.leftPos + INNER_LEFT || mouseX >= this.leftPos + INNER_RIGHT) {
			return -1;
		}
		double dy = mouseY - (this.topPos + PANEL_TOP + 2);
		if (dy < 0) {
			return -1;
		}
		int row = (int) (dy / ORDER_ROW_H);
		int index = scroll + row;
		return row < ORDER_ROWS && index < view().orders().size() ? index : -1;
	}

	/** The owner's button under the mouse in order row {@code index}, or null. */
	private TownDispatcherBlockEntity.@Nullable OrderPress orderButtonAt(int index, double mouseX, double mouseY) {
		if (index < 0 || !this.menu.viewerControls()) {
			return null;
		}
		int y = PANEL_TOP + 3 + (index - scroll) * ORDER_ROW_H;
		for (int b = 0; b < ORDER_BUTTONS.length; b++) {
			int x = orderButtonsLeft() + b * (ORDER_BUTTON + 2);
			if (isHovering(x, y + 3, ORDER_BUTTON, ORDER_BUTTON, mouseX, mouseY)) {
				return ORDER_BUTTONS[b];
			}
		}
		return null;
	}

	// --- «Materials»: the bill as a grid ---

	private void drawMaterials(GuiGraphicsExtractor graphics, DispatcherView view) {
		Component header = view.materials().isEmpty()
				? Component.translatable("settlement.alaindustrial.dispatcher.materials.none")
				: ConstructionTab.component(view.materials().getFirst());
		paragraph(graphics, header, INNER_LEFT + 4, PANEL_TOP + 4, INNER_RIGHT - INNER_LEFT - 8, 1.0f, GuiStyle.TEXT,
				2);
		List<DispatcherView.BillLine> bill = view.bill();
		int rows = (bill.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows - GRID_ROWS)));
		for (int i = scroll * GRID_COLUMNS; i < bill.size() && i < (scroll + GRID_ROWS) * GRID_COLUMNS; i++) {
			int cell = i - scroll * GRID_COLUMNS;
			int x = this.leftPos + GRID_LEFT + (cell % GRID_COLUMNS) * CELL_W;
			int y = this.topPos + GRID_TOP + (cell / GRID_COLUMNS) * CELL_H;
			DispatcherView.BillLine line = bill.get(i);
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + (CELL_W - 18) / 2, y, WELL_U, 0, 18, 18, ATLAS_W,
					ATLAS_H);
			graphics.item(stackOf(line.item()), x + (CELL_W - 16) / 2, y + 1);
			if (line.covered()) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DONE, x + CELL_W - 10, y - 2, 9, 9,
						GuiStyle.STATUS_GOOD_TICK);
			}
			String count = Math.min(line.have(), 9999) + "/" + line.need();
			int width = Math.round(this.font.width(count) * COUNT_SCALE);
			PageText.scaled(graphics, this.font, Component.literal(count), x + (CELL_W - width) / 2, y + 18,
					COUNT_SCALE, line.covered() ? GuiStyle.STATUS_GOOD : GuiStyle.STATUS_BLOCKING);
		}
	}

	/** The bill's item under the mouse, or null. */
	private DispatcherView.@Nullable BillLine billAt(double mouseX, double mouseY) {
		List<DispatcherView.BillLine> bill = view().bill();
		double dx = mouseX - (this.leftPos + GRID_LEFT);
		double dy = mouseY - (this.topPos + GRID_TOP);
		if (dx < 0 || dy < 0) {
			return null;
		}
		int column = (int) (dx / CELL_W);
		int row = (int) (dy / CELL_H);
		if (column >= GRID_COLUMNS || row >= GRID_ROWS || dy - row * CELL_H > 18) {
			return null;
		}
		int index = (scroll + row) * GRID_COLUMNS + column;
		return index < bill.size() ? bill.get(index) : null;
	}

	private static ItemStack stackOf(String item) {
		Identifier id = Identifier.tryParse(item);
		return id == null ? ItemStack.EMPTY
				: new ItemStack(BuiltInRegistries.ITEM.getValue(id));
	}

	// --- «Journal» ---

	private void drawJournal(GuiGraphicsExtractor graphics) {
		List<Component> lines = journalLines();
		int width = INNER_RIGHT - INNER_LEFT - 8;
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) {
			wrapped.addAll(this.font.split(line, PageText.wrapWidth(line, width)));
		}
		int shown = (PANEL_BOTTOM - PANEL_TOP - 6) / LINE_H;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, wrapped.size() - shown)));
		for (int i = 0; i < shown && scroll + i < wrapped.size(); i++) {
			graphics.text(this.font, wrapped.get(scroll + i), this.leftPos + INNER_LEFT + 4,
					this.topPos + PANEL_TOP + 4 + i * LINE_H, GuiStyle.TEXT, false);
		}
	}

	/** The journal, newest first: the game time, then what happened. */
	private List<Component> journalLines() {
		List<Component> out = new ArrayList<>();
		TownDispatcherBlockEntity dispatcher = dispatcher();
		if (dispatcher != null) {
			List<WorkJournal.Entry> entries = dispatcher.journal().entries();
			for (int i = entries.size() - 1; i >= 0; i--) {
				WorkJournal.Entry entry = entries.get(i);
				out.add(ConstructionTab.component(entry.timeText()).copy().withStyle(ChatFormatting.DARK_GRAY)
						.append(" ").append(ConstructionTab.component(entry.text()).copy()
								.withStyle(ChatFormatting.RESET)));
			}
		}
		if (out.isEmpty()) {
			out.add(Component.translatable("settlement.alaindustrial.dispatcher.journal_empty"));
		}
		return out;
	}

	// --- shared drawing ---

	/**
	 * {@code text} wrapped into {@code width} at ({@code x}, {@code y}) relative to the window, at {@code scale}, on at
	 * most {@code maxLines} lines; returns the lines drawn.
	 */
	private int paragraph(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, float scale,
			int colour, int maxLines) {
		int wrapAt = PageText.wrapWidth(text, Math.round(width / scale));
		List<FormattedCharSequence> lines = this.font.split(text, wrapAt);
		int count = Math.min(maxLines, lines.size());
		for (int i = 0; i < count; i++) {
			int top = this.topPos + y + Math.round(i * LINE_H * scale);
			if (scale >= 1.0f) {
				graphics.text(this.font, lines.get(i), this.leftPos + x, top, colour, false);
				continue;
			}
			graphics.pose().pushMatrix();
			graphics.pose().translate(this.leftPos + x, top);
			graphics.pose().scale(scale, scale);
			graphics.text(this.font, lines.get(i), 0, 0, colour, false);
			graphics.pose().popMatrix();
		}
		return count;
	}

	/** A vanilla button at ({@code x}, {@code y}) of the window, its label fitted. */
	private void button(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, boolean enabled,
			int mouseX, int mouseY) {
		boolean hovered = enabled && isHovering(x, y, width, BUTTON_H, mouseX, mouseY);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, !enabled ? BUTTON_OFF : hovered ? BUTTON_HOVER : BUTTON,
				this.leftPos + x, this.topPos + y, width, BUTTON_H);
		drawFittedStatus(graphics, label, y + 4, x + 3, x + width - 3,
				enabled ? GuiStyle.HEADER_TEXT : GuiStyle.TEXT_DIM);
	}

	private static String key(String suffix) {
		return "settlement.alaindustrial.dispatcher." + suffix;
	}

	private DispatcherView view() {
		TownDispatcherBlockEntity dispatcher = dispatcher();
		return dispatcher == null ? DispatcherView.EMPTY : dispatcher.view();
	}

	/**
	 * The dispatcher's block entity on this client: the one the player was looking at when the screen opened, else the
	 * nearest within reach. The menu carries no position (a channel would put the block's coordinates into every
	 * snapshot of the menus); null before the block entity synced.
	 */
	private @Nullable TownDispatcherBlockEntity dispatcher() {
		if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
			return null;
		}
		if (target != null && this.minecraft.level.getBlockEntity(target) instanceof TownDispatcherBlockEntity found) {
			return found;
		}
		BlockPos around = this.minecraft.player.blockPosition();
		TownDispatcherBlockEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(around.offset(-REACH, -REACH, -REACH),
				around.offset(REACH, REACH, REACH))) {
			if (this.minecraft.level.getBlockEntity(pos) instanceof TownDispatcherBlockEntity found
					&& pos.distSqr(around) < best) {
				best = pos.distSqr(around);
				nearest = found;
			}
		}
		if (nearest != null) {
			target = nearest.getBlockPos();
		}
		return nearest;
	}

	/** Why the last assembly was refused, while the viewer controls the dispatcher. */
	private @Nullable Component reasonText() {
		int reason = this.menu.getReason();
		if (reason == TownDispatcherBlockEntity.REASON_NONE || !this.menu.viewerControls()) {
			return null;
		}
		if (reason == TownDispatcherBlockEntity.REASON_PLAN_SLOT_FULL) {
			return Component.translatable(TownDispatcherBlockEntity.PLAN_SLOT_FULL_KEY);
		}
		PlanSheetRules.Reason[] all = PlanSheetRules.Reason.values();
		if (reason - 1 >= all.length) {
			return null;
		}
		PlanSheetRules.Reason which = all[reason - 1];
		if (which == PlanSheetRules.Reason.MISSING_SHEET) {
			List<String> numbers = new ArrayList<>();
			for (int n = 1; n <= PlanSheetRules.SHEETS; n++) {
				if ((this.menu.getMissing() & (1 << (n - 1))) != 0) {
					numbers.add(Integer.toString(n));
				}
			}
			return Component.translatable(which.key(), String.join(", ", numbers));
		}
		return Component.translatable(which.key());
	}

	// --- input ---

	private boolean overAssemble(double mouseX, double mouseY) {
		return tab == Tab.TASK && isHovering(ASSEMBLE_X, TownDispatcherMenu.ROW_Y, ASSEMBLE_W, BUTTON_H, mouseX,
				mouseY);
	}

	private boolean overStart(double mouseX, double mouseY) {
		return tab == Tab.TASK && isHovering(START_X, TownDispatcherMenu.ROW_Y, START_W, BUTTON_H, mouseX, mouseY);
	}

	@Override
	protected boolean controlClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || this.minecraft == null) {
			return false;
		}
		int clicked = strip.tabAt(event.x(), event.y(), this.leftPos, this.topPos, PAGES.size());
		if (clicked >= 0) {
			if (clicked != tab.ordinal()) {
				openTab(Tab.values()[clicked]);
				AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
			}
			return true;
		}
		int row = orderRowAt(event.x(), event.y());
		if (row >= 0 && this.minecraft.gameMode != null) {
			TownDispatcherBlockEntity.OrderPress press = orderButtonAt(row, event.x(), event.y());
			DispatcherView.OrderLine order = view().orders().get(row);
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, TownDispatcherMenu.orderButton(
					order.building(), press == null ? TownDispatcherBlockEntity.OrderPress.SELECT : press));
			AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
			return true;
		}
		int button = overAssemble(event.x(), event.y()) ? TownDispatcherMenu.BUTTON_ASSEMBLE
				: overStart(event.x(), event.y()) && startBlockers(view()).isEmpty() ? TownDispatcherMenu.BUTTON_START
				: -1;
		if (button >= 0 && this.menu.viewerControls() && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
			AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
			return true;
		}
		return false;
	}

	@Override
	protected boolean controlScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (tab == Tab.BUFFER || scrollY == 0.0
				|| !isHovering(INNER_LEFT, PANEL_TOP, INNER_RIGHT - INNER_LEFT, PANEL_BOTTOM - PANEL_TOP, mouseX,
						mouseY)) {
			return false;
		}
		int step = tab == Tab.TASK ? LINE_H : 1;
		scroll = Math.max(0, scroll - step * (int) Math.signum(scrollY));
		return true;
	}

	@Override
	protected void controlTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int over = strip.tabAt(mouseX, mouseY, this.leftPos, this.topPos, PAGES.size());
		if (over >= 0) {
			graphics.setTooltipForNextFrame(this.font, PAGES.get(over).title(), mouseX, mouseY);
			return;
		}
		if (overAssemble(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable(this.menu.viewerControls()
					? "settlement.alaindustrial.dispatcher.assemble.hint"
					: "settlement.alaindustrial.dispatcher.owner_only"), mouseX, mouseY);
		} else if (overStart(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font, startTooltip(), mouseX, mouseY);
		} else if (orderRowAt(mouseX, mouseY) >= 0) {
			int row = orderRowAt(mouseX, mouseY);
			TownDispatcherBlockEntity.OrderPress press = orderButtonAt(row, mouseX, mouseY);
			DispatcherView.OrderLine order = view().orders().get(row);
			Component text = press != null
					? Component.translatable(key("order." + press.name().toLowerCase(Locale.ROOT)))
					: ConstructionTab.component(order.name()).copy().append(": ")
							.append(ConstructionTab.component(order.step()));
			List<FormattedCharSequence> lines = this.font.split(text, PageText.wrapWidth(text, TOOLTIP_W));
			graphics.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
		} else if (emptySheetSlot() > 0) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable(PlanSheetHints.hintKey(emptySheetSlot())), mouseX, mouseY);
		} else if (tab == Tab.MATERIALS) {
			DispatcherView.BillLine line = billAt(mouseX, mouseY);
			if (line != null) {
				ItemStack stack = stackOf(line.item());
				if (!stack.isEmpty()) {
					graphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
				}
			}
		}
	}

	/**
	 * The empty sheet slots show a sheet with the slot's number over it — sheet N goes into slot N only, so the
	 * player sees which sheet is still to be found (owner's round 1 of 0.1.219) — the plan slot a plan.
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		for (int i = 0; i < TownDispatcherBlockEntity.SHEET_SLOTS; i++) {
			int containerSlot = TownDispatcherBlockEntity.SHEET_SLOT_FIRST + i;
			ghostHint(graphics, containerSlot, new ItemStack(ModContent.TOWN_HALL_PLAN_SHEET.get()));
			Slot slot = this.menu.machineSlot(containerSlot);
			if (slot != null && slot.isActive() && !slot.hasItem()) {
				String number = Integer.toString(i + 1);
				graphics.text(this.font, number, this.leftPos + slot.x + (16 - this.font.width(number)) / 2,
						this.topPos + slot.y + 4, GuiStyle.LABEL, false);
			}
		}
		ghostHint(graphics, TownDispatcherBlockEntity.PLAN_SLOT, new ItemStack(ModContent.RESTORATION_PLAN.get()));
	}

	/** The number of the empty sheet slot under the mouse, or 0: its tooltip says where that sheet lies. */
	private int emptySheetSlot() {
		Slot slot = this.hoveredSlot;
		if (slot == null || slot.hasItem() || !slot.isActive()) {
			return 0;
		}
		for (int i = 0; i < TownDispatcherBlockEntity.SHEET_SLOTS; i++) {
			if (slot == this.menu.machineSlot(TownDispatcherBlockEntity.SHEET_SLOT_FIRST + i)) {
				return i + 1;
			}
		}
		return 0;
	}

	/** The strip sticks out to the left of the frame, where JEI and REI park their bookmarks. */
	@Override
	public List<Rect2i> extraGuiAreas() {
		List<Rect2i> areas = super.extraGuiAreas();
		areas.add(strip.area(this.leftPos, this.topPos, PAGES.size()));
		return areas;
	}

	/** One tab of the strip: a name and an icon; the screen draws what the tab shows. */
	private record Page(Tab tab) implements TabPage {

		@Override
		public Component title() {
			return Component.translatable(tab.key());
		}

		@Override
		public ItemStack icon() {
			return tab.icon.get();
		}

		@Override
		public int badgeColour() {
			return 0;
		}

		@Override
		public void init() {
		}

		@Override
		public void setShown(boolean shown) {
		}

		@Override
		public void tick() {
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		}

		@Override
		public boolean tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
			return false;
		}

		@Override
		public void mouseReleased(MouseButtonEvent event) {
		}
	}
}
