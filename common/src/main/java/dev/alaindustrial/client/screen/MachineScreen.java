package dev.alaindustrial.client.screen;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.ProcessingMachineStatus;
import dev.alaindustrial.client.ReadoutFormat;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.network.MachineStatsPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Shared base for every machine screen. Owns the GUI rendering hooks ({@link #drawMachineFrame},
 * energy bar, slot overlay for the upgrade slots) and delegates the floating upgrade panel
 * (MOD-080) — its drag state, hit-testing and geometry — to an {@link UpgradePanelController}.
 *
 * <p><b>Why the controller split.</b> Before this refactor the screen was ~470 lines mixing three
 * concerns: machine rendering (frame + energy bar), slot rendering, and the panel's input + drag +
 * positioning logic. The panel code was the largest of the three (~250 lines) and unrelated to the
 * machine's own look — every edit there meant navigating interleaved rendering constants and drag
 * state. Pulling the state + hit-tests into {@link UpgradePanelController} makes this file about the
 * machine again; the controller owns "where is the panel / what is the user doing to it", and the
 * screen owns "given that, draw the frame and paint the panel overlay".
 *
 * <p>The rendering of the panel itself stays here (see {@link #drawPanel}) because it is deeply
 * intertwined with {@link GuiGraphicsExtractor} and per-frame slot iteration — pulling it out would
 * mean passing the graphics + slot list back into the controller and duplicating the slot-paint loop.
 *
 * <p>The controller does NOT do click routing itself — it answers geometry and drag state; the
 * screen's {@code mouseClicked} keeps the close-X / slot / drag dispatch inline so {@link #slotClicked}
 * stays callable without re-exports.
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

	private UpgradePanelController panel;
	private StatsPanelController statsPanel;

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
		this.panel = new UpgradePanelController(this.menu, this.leftPos, this.topPos, this.width, this.height);
		this.statsPanel = new StatsPanelController(this.menu, this.leftPos, this.topPos, this.width, this.height);
	}

	// --- Rendering: subclass frame in the background, upgrade panel as a top overlay ---

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		drawMachineFrame(graphics, mouseX, mouseY, partialTick);
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
	 * <p>{@code gui.alaindustrial.output} states the rate the generator <em>could</em> deliver, and on a
	 * full buffer that reads as a plain lie: the block is delivering nothing, and the player sees a
	 * healthy number next to 8000/8000. On a farm with more generation than the grid consumes — the
	 * normal state of any solar array — this is what makes idle panels look broken. They are not: the
	 * grid moves only what something asks for, and a generator whose buffer is full has nowhere to put
	 * the next EU.
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

	/** Dark red, matching every other machine's blocking caption. */
	private static final int STATUS_ROW_COLOUR = 0xFFAA0000;

	/**
	 * Draw the one-line caption for a {@link ProcessingMachineStatus}, or nothing when the state is a
	 * silent one. The family's five screens differ only in the row's height, so that is the only parameter.
	 *
	 * <p>Lives on the shared machine screen rather than a screen class of its own because the family is
	 * split across two bases: four of the five extend {@link ProgressMachineScreen}, while the Compressor
	 * extends this class directly (its converging twin arrows are not the shared progress sprite).
	 */
	protected void drawProcessingStatus(GuiGraphicsExtractor graphics, ProcessingMachineStatus status, int y) {
		if (!status.isBlocking()) {
			return;
		}
		drawFittedStatus(graphics, Component.translatable(status.translationKey()),
				y, STATUS_ROW_LEFT, STATUS_ROW_RIGHT, STATUS_ROW_COLOUR);
	}

	/**
	 * Draw a status row centred in the band between {@code bandLeft} and {@code bandRight}, shrinking it if
	 * it does not fit rather than letting it escape over whatever flanks the band.
	 *
	 * <p><b>A status row cannot be truncated</b> — reading the reason is the entire point of it — and there
	 * is no second line to wrap onto, so scaling is the only approach a future translation cannot re-break.
	 *
	 * <p><b>Both band edges are load-bearing and both were got wrong once</b>, in the two screens this
	 * method was lifted from. Centring across the whole window printed the caption over the energy bar (and,
	 * on the Thermal Centrifuge, over the rotor gauge on the other side too). Clamping only the left edge
	 * then pushed long locales out through the right border instead — Russian ran past the frame in the dev
	 * client. Hence a band, not an origin.
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
	 * Draw {@code hint} translucently in the machine's {@code containerSlot} while that slot is empty,
	 * as the wordless answer to "what goes here". Occupied slots are left alone, so a real item is
	 * never drawn over.
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
		panel.finishCloseIfReady();
		statsPanel.finishCloseIfReady();
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		// Above the slots (so a hint is not hidden by the slot art), below the panel (so a dragged
		// panel covers it like it covers everything else).
		drawGhostHints(graphics);
		// MOD-125 draw order: BOTH tabs first, then whichever panels are open. A tab is a handle, a panel
		// is content, and content wins — otherwise the gear printed over the statistics panel's text (and
		// the statistics tab over the upgrade panel's art). The upgrade panel keeps a transparent corner
		// exactly so its own gear still shows through it.
		drawStatsTab(graphics);
		// Overlay pass — above the GUI's slots, items and labels.
		if (this.menu.hasUpgradePanel() && this.menu.isPanelOpen()) {
			drawPanel(graphics, mouseX, mouseY);
		}
		// The gear tab is drawn and clickable on every machine that HAS a panel. It sits at a fixed
		// position anchored to the screen (panel.gearX/gearY take leftPos/topPos), tucked into the
		// panel's transparent top-left corner when open, so it never covers panel content. A machine
		// that opted out (MOD-393) shows no gear — a button that opens nothing reads as broken.
		if (this.menu.hasUpgradePanel()) {
			drawTabButton(graphics);
		}
		if (this.menu.isStatsPanelOpen()) {
			drawStatsPanel(graphics, mouseX, mouseY);
		}
	}

	/** Skip the upgrade slots in the normal slot pass — they are painted in the panel overlay instead. */
	@Override
	protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
		if (slot instanceof MachineMenu.UpgradeSlot) {
			return;
		}
		super.extractSlot(graphics, slot, mouseX, mouseY);
	}

	private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// Anchor-aware (not the flat PANEL_X constant): a wider GUI overrides panelAnchorX(), and the
		// hit-boxes, slots and close button already follow it — the background used to be the one piece
		// that did not, so on the 200px distillation column the panel art sat 24px left of its own slots.
		int px = this.leftPos + this.menu.panelAnchorX() + panel.panelDX();
		int py = this.topPos + MachineMenu.PANEL_Y + panel.panelDY();
		graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS, px, py,
				(float) UpgradePanelController.PANEL_U, (float) UpgradePanelController.PANEL_V,
				UpgradePanelController.PANEL_W, UpgradePanelController.PANEL_H,
				UpgradePanelController.ATLAS, UpgradePanelController.ATLAS);

		Slot hovered = upgradeSlotAt(mouseX, mouseY);
		int upgradeIndex = 0;
		for (Slot slot : this.menu.slots) {
			if (!(slot instanceof MachineMenu.UpgradeSlot up) || !up.isActive()) {
				continue;
			}
			int sx = this.leftPos + slot.x;
			int sy = this.topPos + slot.y;
			ItemStack item = slot.getItem();
			if (up.isLocked()) {
				// Reserved for a MOD-286 module: dim it so the baked hint reads as "later", not "broken".
				graphics.fill(sx, sy, sx + 16, sy + 16, UpgradePanelController.LOCK_TINT);
			}
			if (slot == hovered) {
				graphics.fill(sx, sy, sx + 16, sy + 16, UpgradePanelController.HOVER_TINT);
			}
			if (!item.isEmpty()) {
				graphics.item(item, sx, sy);
				graphics.itemDecorations(this.font, item, sx, sy, null);
				// Light THIS arm's rivet, not a fixed one: the slots are added in panel order, so the
				// running index maps straight onto IND_XY.
				if (upgradeIndex < UpgradePanelController.IND_XY.length) {
					int[] ind = UpgradePanelController.IND_XY[upgradeIndex];
					graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS,
							px + ind[0], py + ind[1],
							(float) UpgradePanelController.ACT_U, (float) UpgradePanelController.ACT_V,
							UpgradePanelController.ACT_W, UpgradePanelController.ACT_H,
							UpgradePanelController.ATLAS, UpgradePanelController.ATLAS);
				}
			}
			upgradeIndex++;
		}

		int cx = panel.closeX(this.leftPos);
		int cy = panel.closeY(this.topPos);
		boolean flash = System.currentTimeMillis() < panel.closePressUntil();
		int off = flash ? 1 : 0;
		graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS, cx + off, cy + off,
				(float) UpgradePanelController.CLOSE_U, (float) UpgradePanelController.CLOSE_V,
				UpgradePanelController.CLOSE_W, UpgradePanelController.CLOSE_H,
				UpgradePanelController.ATLAS, UpgradePanelController.ATLAS);
		if (flash) {
			graphics.fill(cx + off, cy + off, cx + off + UpgradePanelController.CLOSE_W,
					cy + off + UpgradePanelController.CLOSE_H, UpgradePanelController.PRESS_DARKEN);
		}
	}

	// --- Statistics panel (MOD-125) ---------------------------------------------------------------

	/** Row pitch inside the statistics panel: 8px glyphs with a 3px gutter, the vanilla readout rhythm. */
	private static final int STAT_ROW_H = 11;

	/**
	 * Paint the statistics panel.
	 *
	 * <p>Drawn with {@code fill} rather than blitted from the atlas: the upgrade panel's art is a cross
	 * shaped around four slots, and a readout needs a plain rectangle. Building that from the same
	 * {@link GuiStyle} tones the rest of the mod uses costs four rectangles and keeps the two docks
	 * looking like one system without a second 159×145 sprite that would have to be redrawn every time a
	 * row is added.
	 */
	private void drawStatsPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int px = statsPanel.panelX(this.leftPos);
		int py = statsPanel.panelY(this.topPos);
		int pw = StatsPanelController.PANEL_W;
		int ph = StatsPanelController.PANEL_H;

		// Blitted from its own texture rather than drawn with fill(): the frame is artwork, and artwork
		// belongs in a PNG an artist can open. Everything below only writes text into it.
		graphics.blit(RenderPipelines.GUI_TEXTURED, STATS_PANEL_TEXTURE, px, py,
				0.0F, 0.0F, pw, ph, TEX_SIZE, TEX_SIZE);

		graphics.text(this.font, Component.translatable("gui.alaindustrial.stats.title"),
				px + 8, py + 7, GuiStyle.TEXT, false);

		// The × itself lives in the panel texture, centred there by construction. Only the press flash is
		// drawn here — the font's "x" glyph sat low and left of the plate, which is what showed in game.
		if (System.currentTimeMillis() < statsPanel.closePressUntil()) {
			int cx = statsPanel.closeX(this.leftPos);
			int cy = statsPanel.closeY(this.topPos);
			graphics.fill(cx, cy, cx + StatsPanelController.CLOSE_SIZE, cy + StatsPanelController.CLOSE_SIZE,
					UpgradePanelController.PRESS_DARKEN);
		}

		MachineStatsPayload stats = this.menu.stats();
		if (stats == null) {
			// Two different silences, and telling them apart is the difference between "wait a moment" and
			// "this machine will never show you anything until you fit a chip".
			boolean chipFitted = this.menu.hasStatsChipInPanel();
			int wrapY = wrappedText(graphics, Component.translatable(chipFitted
					? "gui.alaindustrial.stats.waiting" : "gui.alaindustrial.stats.no_chip"), px, py + 26);
			if (!chipFitted) {
				wrappedText(graphics, Component.translatable("gui.alaindustrial.stats.no_chip_hint"),
						px, wrapY + 2);
			}
			return;
		}

		int y = py + 26;
		long[] parts = ReadoutFormat.durationParts(stats.activeTicks());
		y = statRow(graphics, px, y, "gui.alaindustrial.stats.uptime",
				Component.translatable("gui.alaindustrial.stats.duration",
						parts[0], ReadoutFormat.clock(parts[1], parts[2])).getString(),
				stats.activeTicks(), mouseX, mouseY, false);
		// Processed count sits with the working time: both answer "what has this machine actually done",
		// as opposed to the energy block below, which answers "at what cost". Shown only where it can be
		// non-zero — a generator processes nothing and would just carry a permanent 0.
		if (stats.itemsProcessed() > 0) {
			y = statRow(graphics, px, y, "gui.alaindustrial.stats.processed",
					ReadoutFormat.compact(stats.itemsProcessed()),
					stats.itemsProcessed(), mouseX, mouseY, false);
		}

		// Only the rows this block can actually answer. A generator has no "received", a consumer has no
		// "generated", and a row of zeroes is worse than no row: it invites the player to look for a fault.
		if (stats.energyGenerated() > 0) {
			y = statRow(graphics, px, y, "gui.alaindustrial.stats.generated",
					ReadoutFormat.compact(stats.energyGenerated()) + " EU",
					stats.energyGenerated(), mouseX, mouseY, true);
		}
		if (stats.energyOut() > 0) {
			y = statRow(graphics, px, y, "gui.alaindustrial.stats.sent",
					ReadoutFormat.compact(stats.energyOut()) + " EU",
					stats.energyOut(), mouseX, mouseY, true);
		}
		if (stats.energyIn() > 0) {
			y = statRow(graphics, px, y, "gui.alaindustrial.stats.received",
					ReadoutFormat.compact(stats.energyIn()) + " EU",
					stats.energyIn(), mouseX, mouseY, true);
		}
		if (stats.energyConsumed() > 0) {
			y = statRow(graphics, px, y, "gui.alaindustrial.stats.spent",
					ReadoutFormat.compact(stats.energyConsumed()) + " EU",
					stats.energyConsumed(), mouseX, mouseY, true);
		}

		y += 3;
		graphics.fill(px + 7, y, px + pw - 7, y + 1, GuiStyle.PANEL_LO);
		y += 4;

		boolean stale = this.menu.statsAreStale();
		y = statRow(graphics, px, y, "gui.alaindustrial.stats.now",
				stats.euRate() + " EU/t", stats.euRate(), mouseX, mouseY, false, stale);
		if (stats.peakEuRate() > 0) {
			y = statRow(graphics, px, y, "gui.alaindustrial.stats.peak",
					stats.peakEuRate() + " EU/t", stats.peakEuRate(), mouseX, mouseY, false);
		}

		y += 3;
		graphics.fill(px + 7, y, px + pw - 7, y + 1, GuiStyle.PANEL_LO);
		y += 4;

		y = statRow(graphics, px, y, "gui.alaindustrial.stats.connections",
				stats.sources() + " / " + stats.sinks(), 0, mouseX, mouseY, false);

		graphics.text(this.font, Component.translatable(stale
						? "gui.alaindustrial.stats.idle" : "gui.alaindustrial.stats.live"),
				px + 9, py + ph - 14, GuiStyle.TEXT_DIM, false);
	}

	/** Inner width available to panel text: the box minus the same gutter on both sides. */
	private static final int STAT_TEXT_W = StatsPanelController.PANEL_W - 18;

	/**
	 * Draw a line that may not fit, wrapped at the panel's inner width, and return the y below it.
	 *
	 * <p>Needed because the panel is a fixed 159px while its strings are translated into twenty
	 * languages: the Russian hint for fitting a chip already overflowed it, and German and Turkish
	 * run longer still. Wrapping is measured, not guessed at authoring time.
	 */
	private int wrappedText(GuiGraphicsExtractor graphics, Component text, int px, int y) {
		for (FormattedCharSequence line : this.font.split(text, STAT_TEXT_W)) {
			graphics.text(this.font, line, px + 9, y, GuiStyle.TEXT_DIM, false);
			y += 10;
		}
		return y;
	}

	private int statRow(GuiGraphicsExtractor graphics, int px, int y, String labelKey, String value,
			long exact, int mouseX, int mouseY, boolean tooltip) {
		return statRow(graphics, px, y, labelKey, value, exact, mouseX, mouseY, tooltip, false);
	}

	/**
	 * One label/value line. The value is right-aligned so the column of numbers can be scanned vertically,
	 * and an abbreviated figure carries its exact value in a tooltip — the panel has room for "1.2M EU",
	 * the player checking a build needs the digits.
	 */
	private int statRow(GuiGraphicsExtractor graphics, int px, int y, String labelKey, String value,
			long exact, int mouseX, int mouseY, boolean tooltip, boolean dim) {
		int pw = StatsPanelController.PANEL_W;
		int color = dim ? GuiStyle.TEXT_DIM : GuiStyle.TEXT;
		int valueW = this.font.width(value);
		int valueX = px + pw - 9 - valueW;
		// The value is the number the player came for, so the LABEL is what gives way when a translation
		// is too long for the row: it is ellipsised to whatever space the value leaves, instead of the two
		// running into each other.
		int labelRoom = Math.max(0, STAT_TEXT_W - valueW - 4);
		FormattedCharSequence label = this.font.split(Component.translatable(labelKey), labelRoom)
				.stream().findFirst().orElse(FormattedCharSequence.EMPTY);
		graphics.text(this.font, label, px + 9, y, color, false);
		graphics.text(this.font, Component.literal(value), valueX, y, color, false);
		if (tooltip && mouseY >= y - 1 && mouseY < y + STAT_ROW_H - 1 && mouseX >= px + 7 && mouseX < px + pw - 7) {
			graphics.setTooltipForNextFrame(this.font,
					Component.literal(ReadoutFormat.exact(exact) + " EU"), mouseX, mouseY);
		}
		return y + STAT_ROW_H;
	}

	private void drawStatsTab(GuiGraphicsExtractor graphics) {
		int bx = statsPanel.tabX(this.leftPos);
		int by = statsPanel.tabY(this.topPos);
		boolean flash = System.currentTimeMillis() < statsPanel.tabPressUntil();
		int off = flash ? 1 : 0;
		graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS, bx + off, by + off,
				(float) StatsPanelController.TAB_U, (float) StatsPanelController.TAB_V,
				StatsPanelController.TAB_W, StatsPanelController.TAB_H,
				UpgradePanelController.ATLAS, UpgradePanelController.ATLAS);
		if (flash) {
			graphics.fill(bx + off, by + off, bx + off + StatsPanelController.TAB_W,
					by + off + StatsPanelController.TAB_H, UpgradePanelController.PRESS_DARKEN);
		}
	}

	private void drawTabButton(GuiGraphicsExtractor graphics) {
		int bx = panel.gearX(this.leftPos);
		int by = panel.gearY(this.topPos);
		boolean flash = System.currentTimeMillis() < panel.gearPressUntil();
		int off = flash ? 1 : 0;
		graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS, bx + off, by + off,
				(float) UpgradePanelController.BTN_U, (float) UpgradePanelController.BTN_V,
				UpgradePanelController.BTN_W, UpgradePanelController.BTN_H,
				UpgradePanelController.ATLAS, UpgradePanelController.ATLAS);
		if (flash) {
			graphics.fill(bx + off, by + off, bx + off + UpgradePanelController.BTN_W,
					by + off + UpgradePanelController.BTN_H, UpgradePanelController.PRESS_DARKEN);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.menu.hasUpgradePanel() && panel.isOverGear(mouseX, mouseY, this.leftPos, this.topPos)) {
			int chips = this.menu.installedOverclockerTier();
			if (chips <= 0) {
				graphics.setTooltipForNextFrame(this.font,
						Component.translatable("gui.alaindustrial.upgrades"), mouseX, mouseY);
				return;
			}
			// Relative multipliers only — never an absolute EU/t figure. Config is not synced to the
			// client, so an absolute number would quietly lie on a server that retuned its balance;
			// the ratios are pure functions of the chip tier and hold everywhere.
			double speed = 1.0 / Math.pow(Config.overclockerSpeedFactor, chips);
			double draw = Math.pow(Config.overclockerEuFactor, chips);
			List<FormattedCharSequence> lines = new ArrayList<>();
			lines.addAll(this.font.split(Component.translatable("gui.alaindustrial.upgrades"), 200));
			lines.addAll(this.font.split(Component.translatable("gui.alaindustrial.upgrades.overclock",
					chips, String.format(Locale.ROOT, "%.2f", speed),
					String.format(Locale.ROOT, "%.0f", draw)).withStyle(ChatFormatting.GRAY), 200));
			graphics.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			return;
		}
		if (statsPanel.isOverTab(mouseX, mouseY, this.leftPos, this.topPos)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.stats.title"), mouseX, mouseY);
			return;
		}
		if (this.menu.isStatsPanelOpen()
				&& statsPanel.isOverPanel(mouseX, mouseY, this.leftPos, this.topPos)) {
			// Modal over its own footprint: the row tooltips are emitted while drawing, and nothing from
			// the GUI beneath may show through.
			return;
		}
		if (this.menu.isPanelOpen() && panel.isOverPanel(mouseX, mouseY, this.leftPos, this.topPos)) {
			// Modal: show only the panel's own tooltips, nothing from the GUI beneath it.
			Slot hovered = upgradeSlotAt(mouseX, mouseY);
			if (hovered != null && !hovered.getItem().isEmpty()) {
				ItemStack item = hovered.getItem();
				graphics.setTooltipForNextFrame(this.font, getTooltipFromContainerItem(item),
						item.getTooltipImage(), mouseX, mouseY);
			}
			return;
		}
		super.extractTooltip(graphics, mouseX, mouseY);
	}

	/** Suppress the machine's bar tooltips (energy/fluid) when the mouse is over an open panel. */
	@Override
	protected boolean isHovering(int left, int top, int w, int h, double mx, double my) {
		if (this.menu.isPanelOpen() && panel.isOverPanel(mx, my, this.leftPos, this.topPos)) {
			return false;
		}
		if (this.menu.isStatsPanelOpen() && statsPanel.isOverPanel(mx, my, this.leftPos, this.topPos)) {
			return false;
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
		areas.add(panel.gearArea(this.leftPos, this.topPos));
		if (this.menu.isPanelOpen()) {
			areas.add(panel.panelArea(this.leftPos, this.topPos));
		}
		areas.add(statsPanel.tabArea(this.leftPos, this.topPos));
		if (this.menu.isStatsPanelOpen()) {
			areas.add(statsPanel.panelArea(this.leftPos, this.topPos));
		}
		return areas;
	}

	// --- Input: gear, modal panel (buttons, slots, drag), click routing ---

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int btn = event.button();
		// The gear always toggles the panel (it stays visible in the panel's transparent corner).
		if (btn == 0 && this.menu.hasUpgradePanel()
				&& panel.isOverGear(event.x(), event.y(), this.leftPos, this.topPos)) {
			panel.onGearClick();
			return true;
		}
		// MOD-125: the statistics tab. No opt-out check — every machine has statistics to show.
		if (btn == 0 && statsPanel.isOverTab(event.x(), event.y(), this.leftPos, this.topPos)) {
			statsPanel.onTabClick();
			return true;
		}
		// The statistics panel is modal over its footprint, like the upgrade one, but has nothing to click
		// inside it except the close button — a readout takes no input.
		if (this.menu.isStatsPanelOpen()
				&& statsPanel.isOverPanel(event.x(), event.y(), this.leftPos, this.topPos)) {
			if (btn == 0 && statsPanel.isOverClose(event.x(), event.y(), this.leftPos, this.topPos)) {
				statsPanel.onCloseClick();
				return true;
			}
			// A readout has nothing to click, so the whole surface is a drag handle.
			if (btn == 0) {
				statsPanel.beginDrag(event.x(), event.y());
			}
			return true;
		}
		// The open panel is modal over its footprint: consume every click so nothing beneath reacts.
		if (this.menu.isPanelOpen() && panel.isOverPanel(event.x(), event.y(), this.leftPos, this.topPos)) {
			if (btn == 0 && panel.isOverClose(event.x(), event.y(), this.leftPos, this.topPos)) {
				panel.onCloseClick();
				return true;
			}
			MachineMenu.UpgradeSlot slot = (btn == 0 || btn == 1) ? upgradeSlotAt(event.x(), event.y()) : null;
			if (slot != null) {
				ContainerInput input = (btn == 0 && event.hasShiftDown())
						? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
				this.slotClicked(slot, slot.index, btn, input);
				return true;
			}
			if (btn == 0) {
				panel.beginDrag(event.x(), event.y());
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (panel.dragging() && event.button() == 0) {
			panel.dragTo(event.x(), event.y(), this.leftPos, this.topPos, this.width, this.height);
			return true;
		}
		if (statsPanel.dragging() && event.button() == 0) {
			statsPanel.dragTo(event.x(), event.y(), this.leftPos, this.topPos, this.width, this.height);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (panel.dragging() && event.button() == 0) {
			panel.endDrag();
			return true;
		}
		if (statsPanel.dragging() && event.button() == 0) {
			statsPanel.endDrag();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop) {
		if (this.menu.isPanelOpen() && panel.isOverPanel(mx, my, this.leftPos, this.topPos)) {
			return false;
		}
		return super.hasClickedOutside(mx, my, guiLeft, guiTop);
	}

	private MachineMenu.UpgradeSlot upgradeSlotAt(double mx, double my) {
		for (Slot slot : this.menu.slots) {
			if (slot instanceof MachineMenu.UpgradeSlot up && up.isActive()) {
				int sx = this.leftPos + slot.x;
				int sy = this.topPos + slot.y;
				if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
					return up;
				}
			}
		}
		return null;
	}
}
