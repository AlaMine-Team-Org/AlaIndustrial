package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.ProcessingMachineStatus;
import dev.alaindustrial.core.machine.StatusLine;
import dev.alaindustrial.menu.MachineMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// size-justified: the one base of every machine screen — frame, energy bar and its tooltip, status row,
// ghost hints, overlay replay; the overlays' drawing and input left for their own classes (MOD-716, from 907).
/**
 * Shared base for every machine screen. Owns the GUI rendering hooks ({@link #drawMachineFrame}, the energy
 * bar, the status row, ghost hints) and replays its overlays.
 *
 * <p><b>Overlays are a list, not branches (MOD-716, CLI-2).</b> The upgrade panel (MOD-080), the statistics
 * panel (MOD-125) and the battery drawer (MOD-679) each implement {@link ScreenOverlay}: they draw
 * themselves, route their own clicks, drags and tooltips, and name the rectangles recipe viewers must keep
 * clear. Every hook of this class asks the list in one fixed order — handles first, then bodies, the body
 * drawn last on top and asked for input first — so a new overlay is a class and one entry in
 * {@link #overlays}, and none of the eight hooks names a concrete controller.
 *
 * <p>A screen with controls of its own draws them in {@link #drawUnderPanels} (below every overlay) and asks
 * {@link #frameAcceptsInput} before answering a click, a scroll or a hover.
 */
public abstract class MachineScreen<T extends MachineMenu> extends AbstractContainerScreen<T> {

	/**
	 * Side of every machine GUI atlas — the visible imageWidth × imageHeight region sits at the
	 * top-left of a 256 × 256 PNG. Declared once here so each concrete screen no longer duplicates
	 * its own private {@code TEX_SIZE = 256} constant. {@link ProgressMachineScreen} and all 12
	 * direct subclasses previously re-declared this; they now read this inherited constant.
	 */
	protected static final int TEX_SIZE = 256;

	protected static final Identifier UPGRADES_ATLAS =
			Industrialization.id("textures/gui/container/upgrades_tab_variants/upgrades_tab_small_01_gear.png");

	/** Frame of the statistics panel (MOD-125). Its own PNG so the art can be edited without touching code. */
	protected static final Identifier STATS_PANEL_TEXTURE =
			Industrialization.id("textures/gui/container/stats_panel.png");

	/** What the overlays may ask of this screen; private, so none of it becomes the screen's public API. */
	private final OverlayHost host = new OverlayHost() {
		@Override
		public MachineMenu menu() {
			return MachineScreen.this.menu;
		}

		@Override
		public int left() {
			return MachineScreen.this.leftPos;
		}

		@Override
		public int top() {
			return MachineScreen.this.topPos;
		}

		@Override
		public int screenWidth() {
			return MachineScreen.this.width;
		}

		@Override
		public int screenHeight() {
			return MachineScreen.this.height;
		}

		@Override
		public Font font() {
			return MachineScreen.this.font;
		}

		@Override
		public boolean hasStatsTab() {
			return MachineScreen.this.hasStatsTab();
		}

		@Override
		public void clickSlot(Slot slot, int button, ContainerInput input) {
			MachineScreen.this.slotClicked(slot, slot.index, button, input);
		}

		@Override
		public List<Component> containerTooltip(ItemStack stack) {
			return MachineScreen.this.getTooltipFromContainerItem(stack);
		}

		@Override
		public void toggleBatteryDrawer() {
			MachineScreen.this.toggleBatteryDrawer(true);
		}
	};

	private UpgradePanelController panel;
	private StatsPanelController statsPanel;
	/** The battery drawer's key and geometry (MOD-679); follows whichever energy bar this screen draws. */
	private final BatteryDrawerController drawer = new BatteryDrawerController(host);
	/** Every overlay, in draw order: handles then bodies follow it, input walks the bodies backwards. */
	private List<ScreenOverlay> overlays = List.of();

	public MachineScreen(T menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	/** For machines with a non-default GUI size (the windmills are 176×178). */
	public MachineScreen(T menu, Inventory inventory, Component title, int imageWidth, int imageHeight) {
		super(menu, inventory, title, imageWidth, imageHeight);
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
		// (Re)build the panel controller — re-clamps the persisted offset to this screen size on resize.
		this.panel = new UpgradePanelController(host, this.menu, this.leftPos, this.topPos, this.width, this.height);
		this.statsPanel = new StatsPanelController(host, this.menu, this.leftPos, this.topPos, this.width, this.height);
		this.overlays = List.of(panel, statsPanel, drawer);
		// A player who opened the drawer on the last machine finds it open on this one (session memory).
		if (this.menu.hasBatteryDrawer() && BatteryDrawerController.rememberedOpen && !this.menu.isBatteryDrawerOpen()) {
			toggleBatteryDrawer(false);
		}
	}

	// --- Rendering: subclass frame in the background, upgrade panel as a top overlay ---

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		// An overlay may sit behind the frame (the drawer slides out from under its edge) or on it (the key).
		for (ScreenOverlay overlay : overlays) {
			overlay.drawBehindFrame(graphics, mouseX, mouseY);
		}
		drawMachineFrame(graphics, mouseX, mouseY, partialTick);
		// The energy bar right after the frame: it also tells the battery drawer where it lives (MOD-679).
		EnergyBarSpec bar = energyBar();
		if (bar != null) {
			renderEnergyBar(graphics, bar);
		}
		drawFrameText(graphics, mouseX, mouseY);
		for (ScreenOverlay overlay : overlays) {
			overlay.drawOnFrame(graphics, mouseX, mouseY);
		}
	}

	/**
	 * Text a frame writes across its own gauges — a status row centred over the whole frame. It is submitted
	 * AFTER the energy bar on purpose: the GUI render state stacks an element above any earlier one it
	 * intersects, so a fill blitted after the text would cover the first letters of a long label (the
	 * lightning rod's no-tip line in fr_fr, it_it, id_id, pl_pl starts inside the bar column). Before the bar
	 * was declared through {@link #energyBar()} each screen drew its bar first and this text last.
	 */
	protected void drawFrameText(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	}

	/**
	 * Open or close the battery drawer (MOD-679): locally at once, and on the server through the vanilla
	 * menu-button channel, since shift-click routing runs there and must know the drawer is open.
	 */
	private void toggleBatteryDrawer(boolean sound) {
		boolean open = !this.menu.isBatteryDrawerOpen();
		this.menu.setBatteryDrawerOpen(open);
		BatteryDrawerController.rememberedOpen = open;
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MachineMenu.BUTTON_BATTERY_DRAWER);
		}
		if (sound) {
			AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
		}
	}

	/**
	 * This screen's vertical energy bar, or null for a screen without one (MOD-716, CLI-3). The base draws it
	 * right after {@link #drawMachineFrame} and shows its "X / max EU" tooltip, so a standard machine names its
	 * bar here instead of calling {@link #renderEnergyBar} and {@link #renderEnergyTooltip} itself.
	 */
	protected EnergyBarSpec energyBar() {
		return null;
	}

	/** Whether the base shows the bar's tooltip; false for a screen that shows it itself, on its own terms. */
	protected boolean energyTooltip() {
		return true;
	}

	/** Each machine screen draws its own frame + dynamic sprites here (was its {@code extractBackground} body). */
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	/**
	 * The 256 × 256 GUI atlas PNG for this machine. Each concrete subclass implements this with a
	 * one-line {@code Industrialization.id("textures/gui/container/<name>.png")} — replacing the
	 * private {@code TEXTURE} constant every screen used to redeclare. Read by {@link #blitStaticFrame}
	 * and by {@link #renderEnergyBar} (via the subclass's drawMachineFrame call site).
	 */
	protected abstract Identifier texture();

	/**
	 * A generator's output line — or the reason there is no output.
	 *
	 * <p>{@code gui.alaindustrial.output} states the rate the generator <em>could</em> deliver, and on a full buffer
	 * that reads as a plain lie: the block is delivering nothing, and the player sees a healthy number next to
	 * 8000/8000. On a farm with more generation than the grid consumes — the normal state of any solar array — this is
	 * what makes idle panels look broken. They are not: the grid moves only what something asks for, and a generator
	 * whose buffer is full has nowhere to put the next EU.
	 *
	 * <p>So a full buffer says so instead. The moment anything draws the buffer down, the rate returns
	 * on its own; nothing here changes how much energy moves.
	 */
	protected Component outputLine(int productionRate) {
		int capacity = this.menu.getCapacity();
		if (capacity > 0 && this.menu.getEnergy() >= capacity) {
			return Component.translatable("gui.alaindustrial.output_idle");
		}
		return Component.translatable("gui.alaindustrial.output", productionRate);
	}

	/**
	 * Standard opening blit — the visible {@code imageWidth × imageHeight} region at the top-left of
	 * the {@value #TEX_SIZE} × {@value #TEX_SIZE} atlas. Call once at the top of {@link #drawMachineFrame}
	 * for machines whose atlas has an opaque interior. Skip for {@code BatteryBoxScreen} — its atlas
	 * has a transparent interior, so the frame must be blitted <em>after</em> the orange fill.
	 */
	protected void blitStaticFrame(GuiGraphicsExtractor graphics) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture(),
				this.leftPos, this.topPos, 0.0F, 0.0F,
				this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);
	}

	/**
	 * Vertical energy-bar geometry shared by every machine screen with a bottom-up orange fill (the only
	 * outlier is {@code BatteryBoxScreen}, which is horizontal). Holds the four numbers that differ per
	 * machine: the bar's on-screen anchor, its UV anchor in the GUI atlas, and its inner size. Width and
	 * height stay constant across machines (10×44); only the anchors move (left bar X=17 vs right bar
	 * X=149 for Pump/GeothermalGenerator, UV-top 0 for most vs 48 for the taller windmill GUIs).
	 */
	public record EnergyBarSpec(int barX, int barBottom, int uvX, int uvTop) {
		/** The default left-side bar shared by most machines (furnace, macerator, generators, solar). */
		public static final EnergyBarSpec LEFT = new EnergyBarSpec(17, 64, 176, 0);
		/** The right-side bar used by the Pump and the Geothermal Generator (their left slot is a fluid/lava gauge). */
		public static final EnergyBarSpec RIGHT = new EnergyBarSpec(149, 64, 176, 0);
		/** The left bar offset into the windmill atlas (taller 178-tall GUI → service fill starts at UV 48). */
		public static final EnergyBarSpec LEFT_WINDMILL = new EnergyBarSpec(17, 76, 176, 48);
		/** The incubator's own bar: hard against the left edge of its taller 176×180 atlas. */
		public static final EnergyBarSpec INCUBATOR = new EnergyBarSpec(9, 68, 176, 0);

		/** Bar inner size — constant across every machine (the orange segmented fill is a 10×44 sprite). */
		public static final int WIDTH = 10;
		public static final int HEIGHT = 44;
	}

	/**
	 * Draw the bottom-up energy fill and return the computed fill height so the caller can pair it with
	 * {@link #renderEnergyTooltip}. Replaces the copy-pasted 7-line block that lived in every screen.
	 * Call from {@link #drawMachineFrame} after the static frame is blitted. Reads {@link #texture()}
	 * internally so callers no longer pass the atlas explicitly.
	 */
	protected int renderEnergyBar(GuiGraphicsExtractor graphics, EnergyBarSpec spec) {
		if (this.menu.hasBatteryDrawer() && drawer.track(spec, this.imageWidth)) {
			// The bar tells the drawer where it lives; the slot follows (MOD-679).
			this.menu.placeBatterySlot(drawer.slotItemX(), drawer.slotItemY());
		}
		int capacity = this.menu.getCapacity();
		int energy = this.menu.getEnergy();
		int eFill = capacity > 0 ? (int) ((long) energy * EnergyBarSpec.HEIGHT / capacity) : 0;
		if (eFill > 0) {
			int x = this.leftPos;
			int y = this.topPos;
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture(),
					x + spec.barX(), y + spec.barBottom() - eFill,
					spec.uvX(), spec.uvTop() + (EnergyBarSpec.HEIGHT - eFill),
					EnergyBarSpec.WIDTH, eFill, TEX_SIZE, TEX_SIZE);
		}
		return eFill;
	}

	/**
	 * Hover tooltip "X / max EU" (lang key {@code gui.alaindustrial.energy}) over the bar interior.
	 * Mirrors the per-screen {@code isHovering(...)} block that every bar screen duplicated. Call from
	 * an {@code extractTooltip} override after {@code super}.
	 */
	protected void renderEnergyTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, EnergyBarSpec spec) {
		if (this.isHovering(spec.barX(), spec.barBottom() - EnergyBarSpec.HEIGHT,
				EnergyBarSpec.WIDTH, EnergyBarSpec.HEIGHT, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.energy", this.menu.getEnergy(), this.menu.getCapacity()),
					mouseX, mouseY);
		}
	}

	// --- Status row: the one-line "why am I idle" caption (MOD-458 lifted the two shipped copies here) ---

	/**
	 * Smallest the status row may shrink to before it stops being readable at GUI scale 2.
	 *
	 * <p>0.7, not 0.75, and the difference is not arbitrary: the longest shipped translation of the Repair
	 * Bench's caption — the screen that hit this first — runs about 176 px against a 136 px band, i.e. it
	 * needs 0.77, and a 0.75 floor would have clipped the very string that exposed the bug.
	 */
	protected static final float MIN_STATUS_SCALE = 0.7f;

	/**
	 * Where the status row sits on a standard 176×166 machine frame: below the slot row (which ends at
	 * y=51) and above the "Inventory" label (y=72). Public because an L3 stand may crop its pixel
	 * comparison to exactly this row, the way the generator screens' status constants already are.
	 */
	public static final int STATUS_ROW_Y = 61;

	/**
	 * Left edge of the status band — clear of the energy bar, which occupies x=15..28 down to y=65.
	 */
	public static final int STATUS_ROW_LEFT = 32;

	/** Right edge of the status band — the frame's inner border on a screen with no second gauge. */
	public static final int STATUS_ROW_RIGHT = 168;

	/**
	 * Draw the one-line caption for a {@link StatusLine} ({@link ProcessingMachineStatus} on the five screens
	 * that call it), or nothing when it is not blocking. The row's height is the only parameter.
	 *
	 * <p>Lives on the shared machine screen rather than a screen class of its own because the family is
	 * split across two bases: four of the five extend {@link ProgressMachineScreen}, while the Compressor
	 * extends this class directly (its converging twin arrows are not the shared progress sprite).
	 */
	protected void drawProcessingStatus(GuiGraphicsExtractor graphics, StatusLine status, int y) {
		if (!status.isBlocking()) {
			return;
		}
		drawFittedStatus(graphics, Component.translatable(status.translationKey()),
				y, STATUS_ROW_LEFT, STATUS_ROW_RIGHT, GuiStyle.STATUS_BLOCKING);
	}

	/**
	 * Draw a status row centred in the band between {@code bandLeft} and {@code bandRight}, shrinking it if
	 * it does not fit rather than letting it escape over whatever flanks the band.
	 *
	 * <p><b>A status row cannot be truncated</b> — reading the reason is the entire point of it — and there
	 * is no second line to wrap onto, so scaling is the only approach a future translation cannot re-break.
	 *
	 * <p><b>Both band edges are load-bearing and both were got wrong once</b>, in the two screens this method was
	 * lifted from. Centring across the whole window printed the caption over the energy bar (and, on the Thermal
	 * Centrifuge, over the rotor gauge on the other side too). Clamping only the left edge then pushed long locales out
	 * through the right border instead — Russian ran past the frame in the dev client. Hence a band, not an origin.
	 */
	protected void drawFittedStatus(GuiGraphicsExtractor graphics, Component label,
			int y, int bandLeft, int bandRight, int colour) {
		int band = bandRight - bandLeft;
		int width = this.font.width(label);
		int top = this.topPos + y;
		if (width <= band) {
			graphics.text(this.font, label, this.leftPos + bandLeft + (band - width) / 2, top, colour, false);
			return;
		}
		float scale = Math.max(MIN_STATUS_SCALE, (float) band / width);
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.leftPos + bandLeft, top);
		graphics.pose().scale(scale, scale);
		// Centre inside the band measured in the SCALED coordinate space, or the row drifts left.
		int tx = Math.max(0, (int) ((band / scale - width) / 2.0f));
		graphics.text(this.font, label, tx, 0, colour, false);
		graphics.pose().popMatrix();
	}

	// --- Ghost hints: "what goes here" pictures in empty machine slots (MOD-251, generalised MOD-387) ---

	/**
	 * Background-tinted wash laid over a hint item so it reads as a suggestion, not as contents. The
	 * colour is the GUI's own slot grey at ~69 % alpha: the item keeps its silhouette but loses its
	 * saturation, which is what separates "put a bucket here" from "there is a bucket here".
	 */
	private static final int GHOST_WASH = 0xB0C6C6C6;

	/** Inner side of a vanilla slot — the area an item (and therefore a hint) occupies. */
	private static final int SLOT_INNER = 16;

	/**
	 * How long each item stays up in a cycling ghost hint. Was 1200 ms; playtesting called that
	 * flicker, so it is three times slower — long enough to read one answer before the next. Lives
	 * here rather than per screen so every cycling hint in the mod beats in step.
	 */
	private static final long GHOST_CYCLE_MS = 3600L;

	/**
	 * Declare this machine's ghost hints with {@link #ghostHint} calls. Called every frame after the
	 * slots and their items are drawn, and <em>before</em> the upgrade panel — so a hint never paints
	 * over a panel the player has dragged across the slot it belongs to.
	 */
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
	}

	/**
	 * Draw {@code hint} translucently in the machine's {@code containerSlot} while that slot is empty, as the wordless
	 * answer to "what goes here". Occupied slots are left alone, so a real item is never drawn over.
	 *
	 * <p>{@code containerSlot} is the block entity's own {@code *_SLOT} constant; the on-screen position
	 * comes from the resolved {@link Slot} rather than from repeated coordinates, so moving a slot in the
	 * menu moves its hint with it and the two cannot drift apart.
	 *
	 * <p>Only hint slots the <em>player</em> fills. A machine-filled slot ({@code mayPlace == false})
	 * showing a picture of what will appear there reads as a promise the player is meant to act on.
	 */
	protected void ghostHint(GuiGraphicsExtractor graphics, int containerSlot, ItemStack hint) {
		Slot slot = this.menu.machineSlot(containerSlot);
		if (slot == null || !slot.isActive() || slot.hasItem()) {
			return;
		}
		int x = this.leftPos + slot.x;
		int y = this.topPos + slot.y;
		graphics.item(hint, x, y);
		graphics.fill(x, y, x + SLOT_INNER, y + SLOT_INNER, GHOST_WASH);
	}

	/**
	 * Pick the entry of {@code options} whose turn it is, so a {@link #ghostHint} can name several
	 * equally valid answers instead of freezing on one of them.
	 *
	 * <p>Use it wherever a slot takes a family of items and no member is the "right" one: a single
	 * frozen picture there reads as "only this one fits". Where one answer IS right — the slot's
	 * contents already narrowed it down — show that one instead and stop cycling.
	 */
	protected static ItemStack cyclingHint(List<Item> options) {
		if (options.isEmpty()) {
			return ItemStack.EMPTY;
		}
		// System.currentTimeMillis() is the clock the rest of this package already animates on
		// (the press flashes below, UpgradePanelController) — one time source, not two.
		int i = (int) ((System.currentTimeMillis() / GHOST_CYCLE_MS) % options.size());
		return new ItemStack(options.get(i));
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		for (ScreenOverlay overlay : overlays) {
			overlay.beginFrame();
		}
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		// Above the slots (so a hint is not hidden by the slot art), below the panel (so a dragged
		// panel covers it like it covers everything else).
		drawGhostHints(graphics);
		// A screen's own controls (mode buttons, tabs, toggles) go under every overlay, so an open or dragged
		// panel covers them like it covers the slots (MOD-693).
		drawUnderPanels(graphics, mouseX, mouseY);
		// MOD-125 draw order: every handle first, then every body. A handle opens something, a body is
		// content, and content wins — otherwise the gear printed over the statistics panel's text (and the
		// statistics tab over the upgrade panel's art). The upgrade panel draws its own gear with its body,
		// into a transparent corner kept for it.
		for (ScreenOverlay overlay : overlays) {
			overlay.drawHandle(graphics, mouseX, mouseY);
		}
		for (ScreenOverlay overlay : overlays) {
			overlay.drawBody(graphics, mouseX, mouseY);
		}
	}

	/**
	 * A screen's own controls and overlay text, drawn above the slots and ghost hints but below both tabs
	 * and both panels (MOD-693). Drawing them after {@code super.extractContents} instead printed a mode
	 * button or a status line over an open statistics panel.
	 */
	protected void drawUnderPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	}

	/**
	 * Whether the point lies on the open statistics panel. The panel is modal over its footprint: a
	 * screen's own button under it must neither take the click nor show its tooltip (MOD-693).
	 */
	protected final boolean isOverOpenStatsPanel(double mx, double my) {
		return statsPanel.coversPoint(mx, my);
	}

	/**
	 * How an open overlay silences this screen's own controls (MOD-716, CLI-2). Each value is the rule one
	 * family of screens shipped with; the batch that moved them here kept every one of them as it was, and
	 * bringing them to a single rule is a change of behaviour for the owner to decide (scope MOD-716).
	 */
	protected enum OverlayModality {
		/** Silent while the upgrade panel is open anywhere, or under the open statistics panel. */
		UPGRADES_OPEN_OR_UNDER_STATS,
		/** Silent while either panel is open, wherever it is (the creative source's switch and slider). */
		ANY_PANEL_OPEN,
		/** Silent while the statistics panel is open, wherever it is (a screen without an upgrade panel). */
		STATS_PANEL_OPEN
	}

	/** This screen's rule for {@link #frameAcceptsInput}; the default is the one most screens use. */
	protected OverlayModality overlayModality() {
		return OverlayModality.UPGRADES_OPEN_OR_UNDER_STATS;
	}

	/**
	 * Whether a click, a scroll or a hover at the point may reach this screen's own controls — the one
	 * modality check every screen with buttons of its own asks, instead of reading the panel flags itself.
	 */
	protected final boolean frameAcceptsInput(double mx, double my) {
		return switch (overlayModality()) {
			case UPGRADES_OPEN_OR_UNDER_STATS -> !this.menu.isPanelOpen() && !isOverOpenStatsPanel(mx, my);
			case ANY_PANEL_OPEN -> !this.menu.isPanelOpen() && !this.menu.isStatsPanelOpen();
			case STATS_PANEL_OPEN -> !this.menu.isStatsPanelOpen();
		};
	}

	/** Skip the upgrade slots in the normal slot pass — they are painted in the panel overlay instead. */
	@Override
	protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
		if (slot instanceof MachineMenu.UpgradeSlot) {
			return;
		}
		super.extractSlot(graphics, slot, mouseX, mouseY);
	}

	/**
	 * Whether this screen shows the statistics tab. Default true. A screen whose machine can never hold a
	 * statistics chip — no upgrade panel to fit it in — overrides it: the tab would only ever ask for a chip
	 * there is nowhere to put. Without the tab the panel cannot be opened, so nothing else needs a guard.
	 */
	protected boolean hasStatsTab() {
		return true;
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// A handle's tooltip is the whole tooltip; an open body is modal over its footprint and shows only its
		// own — the top body is asked first.
		for (ScreenOverlay overlay : overlays) {
			if (overlay.handleTooltip(graphics, mouseX, mouseY)) {
				return;
			}
		}
		for (int i = overlays.size() - 1; i >= 0; i--) {
			if (overlays.get(i).bodyTooltip(graphics, mouseX, mouseY)) {
				return;
			}
		}
		super.extractTooltip(graphics, mouseX, mouseY);
		// Hovering the energy bar shows the exact buffer as "X / max EU" (R-GUI-14).
		EnergyBarSpec bar = energyBar();
		if (bar != null && energyTooltip()) {
			renderEnergyTooltip(graphics, mouseX, mouseY, bar);
		}
	}

	/** Suppress the machine's bar tooltips (energy/fluid) when the mouse is over an open panel. */
	@Override
	protected boolean isHovering(int left, int top, int w, int h, double mx, double my) {
		for (ScreenOverlay overlay : overlays) {
			if (overlay.coversPoint(mx, my)) {
				return false;
			}
		}
		return super.isHovering(left, top, w, h, mx, my);
	}

	// --- Recipe-viewer exclusion (MOD-080): absolute screen rects the viewers must keep clear ---

	/**
	 * The tabs (always) and whichever panel is open (dynamic, drag-aware) as absolute screen rectangles.
	 *
	 * <p>The statistics dock must be declared here in the same breath as it is drawn (MOD-125): both tabs
	 * stick out to the RIGHT of the frame, which is exactly where REI/JEI park their item list. A dock the
	 * viewer does not know about gets overdrawn by that list — the well-known failure this hook exists to
	 * prevent.
	 */
	public List<Rect2i> extraGuiAreas() {
		List<Rect2i> areas = new ArrayList<>(4);
		for (ScreenOverlay overlay : overlays) {
			overlay.addExclusionAreas(areas);
		}
		return areas;
	}

	// --- Input: gear, modal panel (buttons, slots, drag), click routing ---

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Handles first (the drawer key, the gear, the statistics tab — they never overlap), then the open
		// bodies from the top one down: a body is modal over its footprint and takes every click there.
		for (ScreenOverlay overlay : overlays) {
			if (overlay.clickHandle(event)) {
				return true;
			}
		}
		for (int i = overlays.size() - 1; i >= 0; i--) {
			if (overlays.get(i).clickBody(event)) {
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		for (ScreenOverlay overlay : overlays) {
			if (overlay.drag(event)) {
				return true;
			}
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		for (ScreenOverlay overlay : overlays) {
			if (overlay.release(event)) {
				return true;
			}
		}
		return super.mouseReleased(event);
	}

	@Override
	protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop) {
		// An open panel or drawer sticks out of the frame; a click on it does not drop the held stack.
		for (ScreenOverlay overlay : overlays) {
			if (overlay.keepsClickInside(mx, my)) {
				return false;
			}
		}
		return super.hasClickedOutside(mx, my, guiLeft, guiTop);
	}
}
