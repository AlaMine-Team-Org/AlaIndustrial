package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.CreativeEnergySourceMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import java.util.List;

/**
 * Screen for the Creative Energy Source (MOD-479): an on/off switch, three output presets, a readout,
 * a fine slider and the charge slot.
 *
 * <p><b>Why the panel is 60 px taller than every other machine.</b> The family default leaves a 54 px
 * content strip (y 16…70, because the inventory label sits at 72). A switch, a row of three presets, a
 * readout, a slider, a slot and a caveat that wraps onto a second line do not fit in it — the Reactor
 * Controller tried the same density and its own class doc records the result as "an unreadable mess".
 * It grew its panel; so does this one, and every row here spans the full content width instead of being
 * squeezed beside a gauge. The atlas is hand-authored, so every coordinate in this class was measured
 * off the PNG by scanning for the vanilla slot grey, not agreed with whoever drew it.
 *
 * <p><b>Why there is no energy bar.</b> Every other machine has one because "how full is the buffer" is
 * the question a player asks it. On an inexhaustible source the honest answer is always "full", and a
 * gauge pinned at one end is worse than no gauge at all. The setting is what matters here, and the
 * slider shows it.
 *
 * <p><b>Why the slider sends nothing while you drag it.</b> The thumb follows the mouse from a local
 * field and only tells the server on release. The mod's one existing drag control — the storage
 * scrollbar — sends on every change, which is fine for its six positions; this slider has 256, and a
 * single sweep across the groove would put over a hundred packets on the wire, per player, per drag.
 * The step is {@link CreativeEnergySourceMenu#OUTPUT_STEP} EU rather than 1 EU for the same reason:
 * a pixel of travel is worth about 55 EU, so exact numbers are what the presets are for.
 */
public class CreativeEnergySourceScreen extends MachineScreen<CreativeEnergySourceMenu> {

	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/creative_energy_source.png");

	/**
	 * On/off switch — the widest control, and the first thing the eye lands on. Its top edge is 19, not
	 * 20: the charge slot beside it holds its ITEM at y=20, and a slot's well is drawn one pixel out, so
	 * matching the item's y left the button sitting a pixel low against the well.
	 */
	private static final int TOGGLE_X = 8, TOGGLE_Y = 19, TOGGLE_W = 126, TOGGLE_H = 18;
	/** Preset row: three equal buttons across the full content width. */
	private static final int PRESET_Y = 44, PRESET_W = 50, PRESET_H = 18;
	private static final int[] PRESET_X = {8, 62, 116};
	/**
	 * Slider well and the track inside it. Both were measured off the atlas rather than agreed with it:
	 * the well is the sunken 160x14 block at (8,82), and the track — the slot-grey interior the thumb may
	 * actually occupy — is the 158x12 region one pixel in. The well is what the mouse hits (a generous
	 * target), the track is what the thumb is drawn in, so the thumb never overlaps the bevel.
	 */
	private static final int SLIDER_X = 8, SLIDER_Y = 82, SLIDER_W = 160, SLIDER_H = 14;
	private static final int TRACK_X = 9, TRACK_Y = 83, TRACK_W = 158, TRACK_H = 12;
	private static final int THUMB_W = 10;
	/** Pixels the thumb's left edge may travel inside the track. */
	private static final int TRAVEL = TRACK_W - THUMB_W;

	private static final int READOUT_Y = 68;
	/** First line of the cable-ceiling caveat; further lines follow every {@link #LINE_H} pixels. */
	private static final int WARNING_Y = 102;
	private static final int LINE_H = 10;
	/** Width the caveat is wrapped to — the panel's content width, minus a pixel of breathing room. */
	private static final int TEXT_W = 158;

	private static final int BUTTON_BG = 0xFF5A5A64;
	private static final int BUTTON_BG_ACTIVE = 0xFFC8781E;
	private static final int BUTTON_BG_OFF = 0xFF6E3232;
	private static final int BUTTON_EDGE_HI = 0xFF9A9AA6;
	private static final int BUTTON_EDGE_LO = 0xFF2A2A30;
	private static final int BUTTON_EDGE_ACTIVE = 0xFFFFD68A;
	private static final int HOVER = 0x33FFFFFF;
	private static final int LABEL = 0xFFFFFFFF;
	private static final int THUMB_FACE = 0xFFC8781E;
	private static final int THUMB_HI = 0xFFFFD68A;
	private static final int THUMB_LO = 0xFF7A430C;

	/**
	 * The output the player has asked for and the server has not confirmed yet, or -1 when the two agree.
	 *
	 * <p>This exists to stop the readout snapping backwards. The menu's value only changes when the
	 * server's answer arrives, so a screen that reads the menu the instant the mouse is released shows
	 * the OLD number for the length of a round trip and then jumps to the new one — which is what a fast
	 * drag looked like: the thumb and the figure kicking back and forth. Holding the requested value
	 * until the server agrees makes the control feel instant and stays authoritative, because the moment
	 * the two match this goes back to -1 and the menu is the only source again.
	 */
	private int pendingOutput = -1;

	/** True while the mouse button is held on the slider; the thumb follows the mouse, nothing is sent. */
	private boolean dragging;

	/**
	 * Ticks a drag may last before it is abandoned. A drag ends on mouseReleased, and a release that
	 * never arrives — the button let go outside the window, another screen taking over — would otherwise
	 * leave the readout frozen on an unconfirmed number for as long as the screen stays open.
	 */
	private static final int DRAG_WATCHDOG_TICKS = 600;

	private int dragTicks;

	/**
	 * Ticks a pending value may survive unconfirmed. Without it a request the server clamps to a
	 * different number — or drops, because the block was broken while the screen was open — would leave
	 * the screen showing a figure that is simply not true, with nothing to correct it.
	 */
	private static final int PENDING_TIMEOUT_TICKS = 40;

	private int pendingTicks;

	public CreativeEnergySourceScreen(CreativeEnergySourceMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, CreativeEnergySourceMenu.IMAGE_HEIGHT);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	public void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
	}

	/**
	 * Output the screen should display: what the player asked for, until the server confirms it.
	 *
	 * <p>Held as EU rather than as a slider step on purpose. A preset is whatever {@code EnergyTier}
	 * currently reads out of the config, which a server may have tuned to a number the 32 EU grid of the
	 * slider cannot express; storing the step would round it, the confirmation below would then never
	 * match, and the readout would sit on a wrong figure for the whole timeout.
	 */
	private int displayedOutput() {
		return pendingOutput >= 0 ? pendingOutput : this.menu.getOutputLimit();
	}

	/** The slider step nearest the displayed output, clamped to the track. */
	private int currentStep() {
		int step = displayedOutput() / CreativeEnergySourceMenu.OUTPUT_STEP;
		return Math.max(0, Math.min(CreativeEnergySourceMenu.OUTPUT_MAX_STEP, step));
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// First line, every time: the atlas carries the whole panel, and without this the screen renders
		// as floating text over the world.
		blitStaticFrame(graphics);

		// Slider thumb, drawn into the groove baked in the atlas.
		int offset = TRAVEL * currentStep() / CreativeEnergySourceMenu.OUTPUT_MAX_STEP;
		int tx = this.leftPos + TRACK_X + offset;
		int ty = this.topPos + TRACK_Y;
		graphics.fill(tx, ty, tx + THUMB_W, ty + TRACK_H, THUMB_FACE);
		graphics.fill(tx, ty, tx + THUMB_W, ty + 1, THUMB_HI);
		graphics.fill(tx, ty, tx + 1, ty + TRACK_H, THUMB_HI);
		graphics.fill(tx, ty + TRACK_H - 1, tx + THUMB_W, ty + TRACK_H, THUMB_LO);
		graphics.fill(tx + THUMB_W - 1, ty, tx + THUMB_W, ty + TRACK_H, THUMB_LO);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		boolean on = this.menu.isSourceEnabled();
		int output = displayedOutput();

		button(graphics, TOGGLE_X, TOGGLE_Y, TOGGLE_W, TOGGLE_H, on ? BUTTON_BG_ACTIVE : BUTTON_BG_OFF,
				on, mouseX, mouseY);
		centeredLabel(graphics, TOGGLE_X, TOGGLE_Y, TOGGLE_W, TOGGLE_H,
				Component.translatable(on
						? "gui.alaindustrial.creative_energy_source.power.on"
						: "gui.alaindustrial.creative_energy_source.power.off"));

		for (int i = 0; i < PRESET_X.length; i++) {
			boolean active = output == presetValue(i);
			button(graphics, PRESET_X[i], PRESET_Y, PRESET_W, PRESET_H,
					active ? BUTTON_BG_ACTIVE : BUTTON_BG, active, mouseX, mouseY);
			centeredLabel(graphics, PRESET_X[i], PRESET_Y, PRESET_W, PRESET_H,
					Component.translatable(presetKey(i)));
		}

		centeredText(graphics, READOUT_Y,
				Component.translatable("gui.alaindustrial.creative_energy_source.output", output),
				GuiStyle.TEXT);
		// The ceiling is per RECEIVER, not per block: a cable segment carries at most its packet cap and
		// the direct push is capped by the tier of the source, both 512 at the top of this mod. A higher
		// setting is not wasted — it is shared out among several neighbours — but no single machine will
		// ever see more than this, and saying otherwise sends testers hunting a bug that is not there.
		if (output > (int) EnergyTier.HV.maxVoltage()) {
			wrappedText(graphics, WARNING_Y,
					Component.translatable("gui.alaindustrial.creative_energy_source.cable_warning",
							(int) EnergyTier.HV.maxVoltage()),
					GuiStyle.TEXT_DIM);
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (dragging) {
			if (--dragTicks <= 0) {
				dragging = false;
			}
			return;
		}
		if (pendingOutput < 0) {
			return;
		}
		if (this.menu.getOutputLimit() == pendingOutput || --pendingTicks <= 0) {
			pendingOutput = -1;
		}
	}

	/** Ask the server for a slider step and show it straight away; {@link #containerTick()} retires it. */
	private void requestStep(int step) {
		int clamped = Math.max(0, Math.min(CreativeEnergySourceMenu.OUTPUT_MAX_STEP, step));
		pendingOutput = clamped * CreativeEnergySourceMenu.OUTPUT_STEP;
		pendingTicks = PENDING_TIMEOUT_TICKS;
		send(CreativeEnergySourceMenu.OUTPUT_BASE + clamped);
	}

	/**
	 * Ask the server for a preset. The preset sends its own button id rather than a slider step, because
	 * a preset is a config value and need not sit on the grid of the slider.
	 */
	private void requestPreset(int index) {
		pendingOutput = presetValue(index);
		pendingTicks = PENDING_TIMEOUT_TICKS;
		send(presetButtonId(index));
	}

	/** A raised button: bevelled like the vanilla panel so it reads as pressable, not as a coloured box. */
	private void button(GuiGraphicsExtractor graphics, int rx, int ry, int w, int h, int background,
			boolean active, int mouseX, int mouseY) {
		int bx = this.leftPos + rx;
		int by = this.topPos + ry;
		graphics.fill(bx, by, bx + w, by + h, background);
		int hi = active ? BUTTON_EDGE_ACTIVE : BUTTON_EDGE_HI;
		graphics.fill(bx, by, bx + w, by + 1, hi);
		graphics.fill(bx, by, bx + 1, by + h, hi);
		graphics.fill(bx, by + h - 1, bx + w, by + h, BUTTON_EDGE_LO);
		graphics.fill(bx + w - 1, by, bx + w, by + h, BUTTON_EDGE_LO);
		if (mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + h) {
			graphics.fill(bx + 1, by + 1, bx + w - 1, by + h - 1, HOVER);
		}
	}

	private void centeredLabel(GuiGraphicsExtractor graphics, int rx, int ry, int w, int h, Component label) {
		int width = this.font.width(label);
		graphics.text(this.font, label,
				this.leftPos + rx + (w - width) / 2, this.topPos + ry + (h - 8) / 2, LABEL, false);
	}

	private void centeredText(GuiGraphicsExtractor graphics, int ry, Component text, int colour) {
		int width = this.font.width(text);
		graphics.text(this.font, text,
				this.leftPos + (this.imageWidth - width) / 2, this.topPos + ry, colour, false);
	}

	/**
	 * Draws a line that may not fit, wrapped to the panel's content width and centred line by line.
	 *
	 * <p>Not an ornament: the caveat below the slider is one of the longest strings in the mod, and it
	 * runs off both edges of the panel in English before any translator sees it. Measuring the string and
	 * shrinking the wording would only move the problem to whichever of the twenty-one locales is
	 * wordiest — German and Russian both run longer than English here. Wrapping is the only fix that
	 * holds for a string nobody has written yet.
	 */
	private void wrappedText(GuiGraphicsExtractor graphics, int ry, Component text, int colour) {
		List<FormattedCharSequence> lines = this.font.split(text, TEXT_W);
		int y = ry;
		for (FormattedCharSequence line : lines) {
			int width = this.font.width(line);
			graphics.text(this.font, line,
					this.leftPos + (this.imageWidth - width) / 2, this.topPos + y, colour, false);
			y += LINE_H;
		}
	}

	private static int presetValue(int index) {
		return switch (index) {
			case 0 -> (int) EnergyTier.LV.maxVoltage();
			case 1 -> (int) EnergyTier.MV.maxVoltage();
			default -> (int) EnergyTier.HV.maxVoltage();
		};
	}

	private static String presetKey(int index) {
		return switch (index) {
			case 0 -> "gui.alaindustrial.creative_energy_source.preset.lv";
			case 1 -> "gui.alaindustrial.creative_energy_source.preset.mv";
			default -> "gui.alaindustrial.creative_energy_source.preset.hv";
		};
	}

	private static int presetButtonId(int index) {
		return switch (index) {
			case 0 -> CreativeEnergySourceMenu.BUTTON_PRESET_LV;
			case 1 -> CreativeEnergySourceMenu.BUTTON_PRESET_MV;
			default -> CreativeEnergySourceMenu.BUTTON_PRESET_HV;
		};
	}

	// --- input ---------------------------------------------------------------------------------

	private boolean over(double mx, double my, int rx, int ry, int w, int h) {
		return mx >= this.leftPos + rx && mx < this.leftPos + rx + w
				&& my >= this.topPos + ry && my < this.topPos + ry + h;
	}

	private int stepAtMouse(double mouseX) {
		// Measured from the TRACK, not the well: the thumb is drawn from TRACK_X, and mixing the two put
		// a systematic one-pixel bias in — about 55 EU — so pressing the thumb without moving the mouse
		// nudged the value.
		double local = mouseX - this.leftPos - TRACK_X - THUMB_W / 2.0;
		int step = (int) Math.round(local * CreativeEnergySourceMenu.OUTPUT_MAX_STEP / TRAVEL);
		return Math.max(0, Math.min(CreativeEnergySourceMenu.OUTPUT_MAX_STEP, step));
	}

	private void send(int buttonId) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Both panels are modal over their footprint and the statistics one can be DRAGGED across this
		// screen, so a click meant for it would otherwise land on the switch or the slider underneath.
		// Defer wholesale while either is open, the way the Sawmill defers to the upgrade panel.
		if (event.button() == 0 && !this.menu.isPanelOpen() && !this.menu.isStatsPanelOpen()) {
			if (over(event.x(), event.y(), TOGGLE_X, TOGGLE_Y, TOGGLE_W, TOGGLE_H)) {
				send(CreativeEnergySourceMenu.BUTTON_TOGGLE);
				return true;
			}
			for (int i = 0; i < PRESET_X.length; i++) {
				if (over(event.x(), event.y(), PRESET_X[i], PRESET_Y, PRESET_W, PRESET_H)) {
					// Shown immediately for the same reason the slider is: the readout must not wait a
					// round trip to admit the click happened.
					dragging = false;
					requestPreset(i);
					return true;
				}
			}
			if (over(event.x(), event.y(), SLIDER_X, SLIDER_Y, SLIDER_W, SLIDER_H)) {
				// Claimed even though nothing is sent yet: letting the click fall through would hand it
				// to whatever sits behind the groove, and vanilla's own item drag would start there.
				dragging = true;
				dragTicks = DRAG_WATCHDOG_TICKS;
				pendingOutput = stepAtMouse(event.x()) * CreativeEnergySourceMenu.OUTPUT_STEP;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging && event.button() == 0) {
			pendingOutput = stepAtMouse(event.x()) * CreativeEnergySourceMenu.OUTPUT_STEP;
			dragTicks = DRAG_WATCHDOG_TICKS;
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging && event.button() == 0) {
			dragging = false;
			// The value stays on screen; requestStep arms the timeout that retires it once the server agrees.
			requestStep(currentStep());
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// Ignored mid-drag: the whole contract of the slider is that a drag sends nothing until it ends.
		if (scrollY != 0 && !dragging && !this.menu.isPanelOpen() && !this.menu.isStatsPanelOpen()
				&& over(mouseX, mouseY, SLIDER_X, SLIDER_Y, SLIDER_W, SLIDER_H)) {
			// Reads currentStep(), which is already pending-aware, so a burst of wheel clicks steps from
			// the value on screen instead of bouncing off whatever the server last confirmed.
			requestStep(currentStep() + (int) Math.signum(scrollY));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (over(mouseX, mouseY, SLIDER_X, SLIDER_Y, SLIDER_W, SLIDER_H)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.creative_energy_source.output.tip"),
					mouseX, mouseY);
		}
	}
}
