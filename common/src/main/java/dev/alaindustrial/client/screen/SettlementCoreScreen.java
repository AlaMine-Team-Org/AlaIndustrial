package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.client.render.SettlementBorderOverlay;
import dev.alaindustrial.client.screen.shiftworker.ShiftWorkerCardClient;
import dev.alaindustrial.client.screen.shiftworker.ShiftWorkerFace;
import dev.alaindustrial.client.screen.tabs.SideTabStrip;
import dev.alaindustrial.client.screen.tabs.TabPage;
import dev.alaindustrial.client.screen.works.ConstructionTab;
import dev.alaindustrial.client.screen.works.TownWorksClient;
import dev.alaindustrial.menu.SettlementCoreMenu;
import dev.alaindustrial.network.ShiftWorkerRosterPayload;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

// size-justified: one screen of four tabs that share the frame, the strip and the panel (core, two worker lists,
// construction); the construction tab already lives in its own class, the rest are short method groups.
/**
 * Screen of the settlement core (MOD-831): the energy bar on the left and the town's readout beside it — whether
 * the core holds its territory, its radius, limits and upkeep, and who owns the town. No slots and no
 * inventory (see {@link SettlementCoreMenu}). The first player to open a lit core becomes the owner on the server,
 * and a dark, unowned core says how that happens under the energy bar. The one control is the eye under the bar:
 * "show borders" (MOD-843), the town's territory drawn for this player alone ({@link SettlementBorderOverlay}).
 *
 * <p><b>The owner's name rides in the title.</b> A channel carries a short, so the name cannot; the server titles a
 * claimed core's screen {@code settlement.alaindustrial.core.title_owned} with the name as its argument (the
 * teleporter station's route for the same problem, MOD-093). This screen prints the block's plain name as its
 * heading and the owner's name in the owner row. A town claimed by somebody else while the screen is open is still
 * answered by the channel, as "another player".
 *
 * <p>Text is laid out by {@link SettlementCoreLayout} with {@link TextFit}: the status row may take two lines, the
 * other rows one, the hint three, each scaled down only when wrapping is not enough (MOD-842). The tier is not
 * shown: in the first slice every core is LV, and the radius and the limits already say what the tier means.
 *
 * <p><b>Roster tabs (MOD-854).</b> Down the left edge, after the core's own tab: «Shift workers» and «Closed» — the
 * town's workers as the server lists them ({@link ShiftWorkerRosterPayload}), asked for when a roster tab opens and
 * again every two seconds while it stays open; a row clicked asks for that worker's card. A request names no core:
 * the server answers for the menu the player has open, which is this core's. The roster kept from before this screen
 * opened is dropped, so the tab reads «loading» until this core's arrives.
 *
 * <p><b>Construction tab (MOD-857).</b> The fourth tab draws the rows of the town's buildings and work queue the server
 * words ({@link ConstructionTab}); the town's owner gets small controls on them (R95).
 *
 * <p><b>«Find» (R4-13).</b> A living worker's row in «Shift workers» ends in a small spyglass button: pressed, the
 * server outlines that worker for a few seconds ({@code ShiftWorkerLocator}).
 */
public class SettlementCoreScreen extends MachineScreen<SettlementCoreMenu> {

	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/settlement_core.png");

	/** "Show borders": the reach-preview eye, lit while this town's border is shown (MOD-843). */
	private static final ZonePreviewButton BORDERS = new ZonePreviewButton(SettlementCoreLayout.BUTTON_X,
			SettlementCoreLayout.BUTTON_Y, SettlementCoreMenu.BUTTON_TOGGLE_BORDERS,
			"settlement.alaindustrial.core.borders.show", "settlement.alaindustrial.core.borders.hide",
			SettlementBorderOverlay::isShownForOpenScreen);

	/** The tabs: the core itself, then the two roster lists (MOD-854). */
	public static final int TAB_CORE = 0;
	public static final int TAB_WORKERS = 1;
	public static final int TAB_CLOSED = 2;
	/** The «Construction» tab (MOD-857): the town's buildings and work queue. */
	public static final int TAB_WORKS = 3;
	private static final int ROSTER_REFRESH_TICKS = 40;

	private static final List<CoreTab> TABS = List.of(
			new CoreTab(SettlementCoreLayout.TAB_CORE, () -> new ItemStack(ModContent.SETTLEMENT_CORE_ITEM.get())),
			new CoreTab(SettlementCoreLayout.TAB_WORKERS,
					() -> new ItemStack(ModContent.RECEPTION_CAPSULE_ITEM.get())),
			new CoreTab(SettlementCoreLayout.TAB_CLOSED, () -> new ItemStack(Items.BOOK)),
			new CoreTab(SettlementCoreLayout.TAB_WORKS, () -> new ItemStack(Items.BRICKS)));

	/** The owner's name as the title brought it, or null when the town had no owner when the screen opened. */
	@Nullable
	private final String ownerName;
	private final SideTabStrip strip = new SideTabStrip(0);
	private final ConstructionTab works = new ConstructionTab();
	/** The roster rows' faces, dressed once per worker while this screen lives. */
	private final ShiftWorkerFace faces = new ShiftWorkerFace();
	private int selected = TAB_CORE;
	private int rosterScroll;
	private int rosterTicks;

	public SettlementCoreScreen(SettlementCoreMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, SettlementCoreLayout.IMAGE_WIDTH, SettlementCoreLayout.IMAGE_HEIGHT);
		this.ownerName = ownerNameOf(title);
		ShiftWorkerCardClient.forgetRoster();
		TownWorksClient.forget();
	}

	/** The name argument of a claimed core's title ({@link SettlementCoreBlockEntity#OWNED_TITLE_KEY}), or null. */
	@Nullable
	static String ownerNameOf(Component title) {
		if (title.getContents() instanceof TranslatableContents translatable
				&& SettlementCoreBlockEntity.OWNED_TITLE_KEY.equals(translatable.getKey())
				&& translatable.getArgs().length > 0) {
			Object arg = translatable.getArgs()[0];
			String name = arg instanceof Component component ? component.getString() : String.valueOf(arg);
			return name.isEmpty() ? null : name;
		}
		return null;
	}

	/** The open tab. */
	public int selectedTab() {
		return selected;
	}

	/** Opens tab {@code index}; a roster tab asks the server for the list. The strip and the L3 stand use this. */
	public void selectTab(int index) {
		if (index < 0 || index >= TABS.size() || index == selected) {
			return;
		}
		selected = index;
		rosterScroll = 0;
		rosterTicks = 0;
		if (selected == TAB_WORKS) {
			works.opened();
		} else {
			requestRoster();
		}
	}

	private void requestRoster() {
		if (selected == TAB_WORKERS || selected == TAB_CLOSED) {
			ShiftWorkerCardClient.requestRoster();
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (selected == TAB_WORKS) {
			works.tick();
		} else if (selected != TAB_CORE && ++rosterTicks % ROSTER_REFRESH_TICKS == 0) {
			requestRoster();
		}
	}

	/**
	 * The list the open roster tab shows: the latest roster, which since this screen opened can only be this core's
	 * (the server answers for the open menu), or null while none has arrived.
	 */
	@Nullable
	private List<ShiftWorkerRosterPayload.Row> rosterRows() {
		ShiftWorkerRosterPayload roster = ShiftWorkerCardClient.latestRoster();
		if ((selected != TAB_WORKERS && selected != TAB_CLOSED) || roster == null) {
			return null;
		}
		return selected == TAB_CLOSED ? roster.closed() : roster.active();
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	/** The usual left bar, its fill sprite under the panel: a panel 256 wide leaves no room to its right (R2-2). */
	private static final EnergyBarSpec BAR = new EnergyBarSpec(EnergyBarSpec.LEFT.barX(),
			EnergyBarSpec.LEFT.barBottom(), SettlementCoreLayout.ENERGY_SPRITE_U, SettlementCoreLayout.ENERGY_SPRITE_V);

	/** The energy bar and its tooltip, drawn by the base (MOD-716) — on the core's own tab only. */
	@Override
	protected EnergyBarSpec energyBar() {
		return selected == TAB_CORE ? BAR : null;
	}

	/** No statistics tab: the core has no upgrade panel, so the chip that feeds it can never be fitted. */
	@Override
	protected boolean hasStatsTab() {
		return false;
	}

	/** The block's plain name as the heading — the owner's name has a row of its own — and no "Inventory". */
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Component heading = selected == TAB_CORE ? Component.translatable("block.alaindustrial.settlement_core")
				: TABS.get(selected).title();
		graphics.text(this.font, heading, (this.imageWidth - this.font.width(heading)) / 2,
				SettlementCoreLayout.TITLE_Y, GuiStyle.LABEL, false);
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		strip.draw(graphics, this.leftPos, this.topPos, TABS, selected);
		if (selected == TAB_WORKS) {
			drawPanel(graphics);
			works.draw(graphics, this.font, this.leftPos, this.topPos, this.menu.isUnbound(), mouseX, mouseY);
			return;
		}
		if (selected != TAB_CORE) {
			drawRoster(graphics, mouseX, mouseY);
			return;
		}
		boolean active = this.menu.isActive();
		int x = this.leftPos + SettlementCoreLayout.TEXT_LEFT;
		int width = SettlementCoreLayout.columnWidth();
		int statusLines = block(graphics, Component.translatable(statusKey(active)), x,
				this.topPos + SettlementCoreLayout.ROW_TOP, width, SettlementCoreLayout.STATUS_MAX_LINES,
				active ? GuiStyle.TEXT : GuiStyle.STATUS_BLOCKING);
		int y = this.topPos + SettlementCoreLayout.firstNumberRowY(statusLines);
		y = row(graphics, Component.translatable(SettlementCoreLayout.TERRITORY, this.menu.getTerritoryWidth(),
				this.menu.getTerritoryDepth()), x, y, GuiStyle.TEXT);
		y = row(graphics, Component.translatable(SettlementCoreLayout.HOUSES, this.menu.getHouseLimit()), x, y,
				GuiStyle.TEXT);
		y = row(graphics, Component.translatable(SettlementCoreLayout.WORKERS, this.menu.getWorkerLimit()), x, y,
				GuiStyle.TEXT);
		y = row(graphics, Component.translatable(SettlementCoreLayout.UPKEEP, this.menu.getUpkeep()), x, y,
				GuiStyle.TEXT_DIM);
		y = row(graphics, ownerLine(), x, y, GuiStyle.TEXT);
		if (this.menu.getOwnerRelation() == SettlementCoreBlockEntity.OWNER_NONE && !active && !this.menu.isUnbound()) {
			block(graphics, Component.translatable(SettlementCoreLayout.HINT),
					this.leftPos + SettlementCoreLayout.HINT_LEFT,
					this.topPos + SettlementCoreLayout.hintTop(y - this.topPos), SettlementCoreLayout.hintWidth(),
					SettlementCoreLayout.HINT_MAX_LINES, GuiStyle.TEXT_DIM);
		}
	}

	/** The roster of the open tab, one row per worker, or a line saying why there is none. */
	private void drawRoster(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int left = this.leftPos + SettlementCoreLayout.ROSTER_LEFT;
		int right = this.leftPos + SettlementCoreLayout.ROSTER_RIGHT;
		int top = this.topPos + SettlementCoreLayout.ROSTER_TOP;
		drawPanel(graphics);
		List<ShiftWorkerRosterPayload.Row> rows = rosterRows();
		Component empty = emptyRosterLine(rows);
		if (rows == null || empty != null) {
			block(graphics, empty, left + 2, top + 2, right - left - 4, 3, GuiStyle.TEXT_DIM);
			return;
		}
		ShiftWorkerRosterPayload roster = ShiftWorkerCardClient.latestRoster();
		int settlement = roster != null ? roster.settlement() : 0;
		int hovered = rosterRowAt(mouseX, mouseY);
		int shown = Math.min(SettlementCoreLayout.ROSTER_ROWS, rows.size() - rosterScroll);
		for (int i = 0; i < shown; i++) {
			ShiftWorkerRosterPayload.Row row = rows.get(rosterScroll + i);
			int y = top + i * SettlementCoreLayout.ROSTER_ROW;
			if (rosterScroll + i == hovered) {
				graphics.fill(left, y - 1, right, y + SettlementCoreLayout.ROSTER_ROW - 1, GuiStyle.HOVER_WASH);
			}
			drawRosterRow(graphics, row, settlement, left, right, y);
			if (locatable(row)) {
				drawLocateButton(graphics, right, y, locateAt(mouseX, mouseY) == rosterScroll + i);
			}
		}
	}

	/** Whether a row gets the «Find» button (R4-13): a living worker in the «Workers» list. */
	private boolean locatable(ShiftWorkerRosterPayload.Row row) {
		return selected == TAB_WORKERS && row.alive();
	}

	/** The «Find» button at the right end of the row at {@code y}: a spyglass, the works tab's small button. */
	private static void drawLocateButton(GuiGraphicsExtractor graphics, int right, int y, boolean hovered) {
		int size = SettlementCoreLayout.LOCATE_BUTTON;
		int bx = right - size - 1;
		graphics.fill(bx, y - 1, bx + size, y - 1 + size, hovered ? GuiStyle.BUTTON_ACTIVE : GuiStyle.BUTTON);
		graphics.pose().pushMatrix();
		graphics.pose().translate(bx + 1, y);
		graphics.pose().scale(0.5f, 0.5f);
		graphics.item(new ItemStack(Items.SPYGLASS), 0, 0);
		graphics.pose().popMatrix();
	}

	/** The index into the open roster of the row whose «Find» button is under the mouse, or -1. */
	private int locateAt(double mouseX, double mouseY) {
		int row = rosterRowAt(mouseX, mouseY);
		List<ShiftWorkerRosterPayload.Row> rows = rosterRows();
		if (row < 0 || rows == null || !locatable(rows.get(row))) {
			return -1;
		}
		int right = this.leftPos + SettlementCoreLayout.ROSTER_RIGHT;
		int bx = right - SettlementCoreLayout.LOCATE_BUTTON - 1;
		return mouseX >= bx && mouseX < bx + SettlementCoreLayout.LOCATE_BUTTON ? row : -1;
	}

	/** The light panel the list tabs draw on. */
	private void drawPanel(GuiGraphicsExtractor graphics) {
		graphics.fill(this.leftPos + SettlementCoreLayout.ROSTER_LEFT,
				this.topPos + SettlementCoreLayout.ROSTER_TOP - 2,
				this.leftPos + SettlementCoreLayout.ROSTER_RIGHT,
				this.topPos + SettlementCoreLayout.IMAGE_HEIGHT - SettlementCoreLayout.BORDER - 1, GuiStyle.PANEL);
	}

	/** What an empty or absent roster says: no town here, still asking, or no workers in this list. */
	@Nullable
	private Component emptyRosterLine(@Nullable List<ShiftWorkerRosterPayload.Row> rows) {
		if (rows == null) {
			return Component.translatable(this.menu.isUnbound()
					? SettlementCoreLayout.STATUS_UNBOUND : SettlementCoreLayout.ROSTER_LOADING);
		}
		if (rows.isEmpty()) {
			return Component.translatable(selected == TAB_CLOSED ? SettlementCoreLayout.ROSTER_CLOSED_EMPTY
					: SettlementCoreLayout.ROSTER_EMPTY);
		}
		return null;
	}

	private void drawRosterRow(GuiGraphicsExtractor graphics, ShiftWorkerRosterPayload.Row row, int settlement,
			int left, int right, int y) {
		int colour = row.alive() ? GuiStyle.TEXT : GuiStyle.TEXT_DIM;
		faces.drawFramed(graphics, row.number(), row.look(), row.slim(), settlement, row.alive(),
				left + SettlementCoreLayout.COL_FACE, y - 1, SettlementCoreLayout.FACE_SIZE);
		cell(graphics, Component.literal(row.number()), left + SettlementCoreLayout.COL_NUMBER, y,
				SettlementCoreLayout.COL_NAME - SettlementCoreLayout.COL_NUMBER - 2, colour);
		cell(graphics, Component.literal(row.name()), left + SettlementCoreLayout.COL_NAME, y,
				SettlementCoreLayout.COL_LEVEL - SettlementCoreLayout.COL_NAME - 2, colour);
		cell(graphics, Component.translatable("gui.alaindustrial.dashboard.level", row.level()),
				left + SettlementCoreLayout.COL_LEVEL, y,
				SettlementCoreLayout.COL_STATUS - SettlementCoreLayout.COL_LEVEL - 2, colour);
		int button = locatable(row) ? SettlementCoreLayout.LOCATE_BUTTON + 2 : 0;
		cell(graphics, Component.translatable(SettlementCoreLayout.ROSTER_STATUS + row.status().getSerializedName()),
				left + SettlementCoreLayout.COL_STATUS, y, right - left - SettlementCoreLayout.COL_STATUS - 2 - button,
				row.alive() ? GuiStyle.TEXT : GuiStyle.STATUS_BLOCKING);
	}

	/** The index into the open roster of the row under the mouse, or -1. */
	private int rosterRowAt(double mouseX, double mouseY) {
		List<ShiftWorkerRosterPayload.Row> rows = rosterRows();
		if (rows == null) {
			return -1;
		}
		int left = this.leftPos + SettlementCoreLayout.ROSTER_LEFT;
		int right = this.leftPos + SettlementCoreLayout.ROSTER_RIGHT;
		int top = this.topPos + SettlementCoreLayout.ROSTER_TOP - 1;
		if (mouseX < left || mouseX >= right || mouseY < top) {
			return -1;
		}
		int index = (int) ((mouseY - top) / SettlementCoreLayout.ROSTER_ROW);
		if (index >= SettlementCoreLayout.ROSTER_ROWS || rosterScroll + index >= rows.size()) {
			return -1;
		}
		return rosterScroll + index;
	}

	/** The eye has no panel to hide under: the core has no statistics tab and no upgrade panel. */
	@Override
	protected void drawUnderPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (selected == TAB_CORE) {
			BORDERS.draw(graphics, this.leftPos, this.topPos, mouseX, mouseY);
		}
	}

	@Override
	protected void controlTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int tab = strip.tabAt(mouseX, mouseY, this.leftPos, this.topPos, TABS.size());
		if (tab >= 0) {
			graphics.setTooltipForNextFrame(this.font, TABS.get(tab).title(), mouseX, mouseY);
			return;
		}
		if (selected == TAB_WORKS) {
			works.tooltip(this.leftPos, this.topPos, mouseX, mouseY)
					.ifPresent(text -> graphics.setTooltipForNextFrame(this.font, text, mouseX, mouseY));
		} else if (selected == TAB_CORE) {
			BORDERS.tooltip(graphics, this.font, this.leftPos, this.topPos, mouseX, mouseY);
		} else if (locateAt(mouseX, mouseY) >= 0) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable(SettlementCoreLayout.ROSTER_LOCATE),
					mouseX, mouseY);
		} else if (rosterRowAt(mouseX, mouseY) >= 0) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable(SettlementCoreLayout.ROSTER_OPEN),
					mouseX, mouseY);
		}
	}

	@Override
	protected boolean controlClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int tab = strip.tabAt(event.x(), event.y(), this.leftPos, this.topPos, TABS.size());
			if (tab >= 0) {
				if (tab != selected) {
					this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				}
				selectTab(tab);
				return true;
			}
			if (selected == TAB_WORKS) {
				return works.clicked(this.leftPos, this.topPos, event.x(), event.y());
			}
			int row = rosterRowAt(event.x(), event.y());
			List<ShiftWorkerRosterPayload.Row> rows = rosterRows();
			if (locateAt(event.x(), event.y()) >= 0 && rows != null) {
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				ShiftWorkerCardClient.locate(rows.get(row).number());
				return true;
			}
			if (row >= 0 && rows != null) {
				ShiftWorkerCardClient.requestCard(rows.get(row).number());
				return true;
			}
		}
		return selected == TAB_CORE
				&& BORDERS.clicked(this.minecraft, this.menu.containerId, this.leftPos, this.topPos, event);
	}

	/** The wheel scrolls a roster longer than the panel. */
	@Override
	protected boolean controlScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (selected == TAB_WORKS) {
			return works.scrolled(scrollY);
		}
		List<ShiftWorkerRosterPayload.Row> rows = rosterRows();
		if (rows == null || scrollY == 0.0) {
			return false;
		}
		int max = SettlementCoreLayout.maxRosterScroll(rows.size());
		rosterScroll = Math.max(0, Math.min(max, rosterScroll - (int) Math.signum(scrollY)));
		return true;
	}

	/** The strip sticks out to the left of the frame, where JEI and REI park their bookmarks. */
	@Override
	public List<Rect2i> extraGuiAreas() {
		List<Rect2i> areas = super.extraGuiAreas();
		areas.add(strip.area(this.leftPos, this.topPos, TABS.size()));
		return areas;
	}

	/** The first row: lit, dark, a second core of its town (R43), or a core of no town (R53). */
	private String statusKey(boolean active) {
		if (this.menu.isUnbound()) {
			return SettlementCoreLayout.STATUS_UNBOUND;
		}
		if (this.menu.isMisplaced()) {
			return SettlementCoreLayout.STATUS_MISPLACED;
		}
		return active ? SettlementCoreLayout.STATUS_ACTIVE : SettlementCoreLayout.STATUS_DARK;
	}

	/** The owner row: nobody, you, or the owner by name. */
	private Component ownerLine() {
		return switch (this.menu.getOwnerRelation()) {
			case SettlementCoreBlockEntity.OWNER_VIEWER -> Component.translatable(SettlementCoreLayout.OWNER_YOU);
			case SettlementCoreBlockEntity.OWNER_OTHER -> ownerName != null
					? Component.translatable(SettlementCoreLayout.OWNER_NAMED, ownerName)
					: Component.translatable(SettlementCoreLayout.OWNER_OTHER);
			default -> Component.translatable(SettlementCoreLayout.OWNER_NONE);
		};
	}

	/** One one-line row at {@code y}; returns the top of the next row. */
	private int row(GuiGraphicsExtractor graphics, Component text, int x, int y, int colour) {
		block(graphics, text, x, y, SettlementCoreLayout.columnWidth(), SettlementCoreLayout.ROW_MAX_LINES, colour);
		return y + SettlementCoreLayout.ROW_STEP;
	}

	/** One roster cell: one line in {@code width}, scaled down or cut when it must. */
	private void cell(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int colour) {
		String plain = text.getString();
		TextFit.Fit fit = TextFit.fit(plain, width, 1, this.font::width);
		float scale = fit.fits() ? fit.scale() : TextFit.MIN_SCALE;
		String shown = fit.fits() ? plain : this.font.plainSubstrByWidth(plain, (int) (width / scale));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y + (1.0f - scale) * 4.0f);
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, shown, 0, 0, colour, false);
		graphics.pose().popMatrix();
	}

	/**
	 * {@code text} fitted into {@code width} at ({@code x}, {@code y}) on at most {@code maxLines} lines
	 * ({@link TextFit}); returns how many lines it took.
	 */
	private int block(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int maxLines,
			int colour) {
		TextFit.Fit fit = TextFit.fit(text.getString(), width, maxLines, this.font::width);
		for (int i = 0; i < fit.lines().size(); i++) {
			Component line = Component.literal(fit.lines().get(i));
			int top = y + i * SettlementCoreLayout.LINE_STEP;
			if (fit.scale() >= 1.0f) {
				graphics.text(this.font, line, x, top, colour, false);
				continue;
			}
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, top);
			graphics.pose().scale(fit.scale(), fit.scale());
			graphics.text(this.font, line, 0, 0, colour, false);
			graphics.pose().popMatrix();
		}
		return fit.lines().size();
	}

	/** One tab of the strip: a name and an icon; the screen draws what the tab shows. */
	private record CoreTab(String key, Supplier<ItemStack> iconSource) implements TabPage {

		@Override
		public Component title() {
			return Component.translatable(key);
		}

		@Override
		public ItemStack icon() {
			return iconSource.get();
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
