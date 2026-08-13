package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.render.RepellerDomeRenderer;
import dev.alaindustrial.item.misc.SoulVesselItem;
import dev.alaindustrial.menu.MobRepellerMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Shared screen of the Mob Repeller family (MOD-278): the standard machine frame with the vessel slot
 * and the energy bar, plus the dome toggle — <b>show my radius</b>.
 *
 * <p>The button rides the vanilla container-button channel
 * ({@code handleInventoryButtonClick} → {@link MobRepellerMenu#clickMenuButton}) exactly like the
 * sawmill's mode buttons: no payload of our own is needed to ASK, because the request carries no
 * data beyond "this container, this button". The answer comes back as a personal
 * {@link dev.alaindustrial.network.RepellerDomePayload} carrying the server-authoritative position
 * and radius, which the client feeds to {@link RepellerDomeRenderer} — so the dome can never
 * disagree with the zone the server actually sweeps, and no other player sees it.
 *
 * <p><b>The button is a real toggle.</b> Its lit/unlit state comes from the renderer, which learns
 * which block the open screen belongs to from a non-toggling dome message the block entity sends on
 * open — a client menu has no block position of its own.
 *
 * <p>Three thin subclasses ride on this one — a screen class and a menu type are a pair the
 * compiler must be able to see, so the tiers cannot share one concrete screen class.
 */
public abstract class AbstractMobRepellerScreen<T extends MobRepellerMenu> extends MachineScreen<T> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/mob_repeller.png");

	/**
	 * Dome toggle, right of the vessel slot — a square EYE button with no caption (design 06 of the
	 * ten mocked up for this): an eye reads the same in every language, so the button needs no
	 * translation and cannot overflow its box the way a worded one did.
	 */
	private static final int BUTTON_X = 134;
	private static final int BUTTON_Y = 32;
	private static final int BUTTON_W = 22;
	private static final int BUTTON_H = 22;

	// Off: dark, quiet. On: the soul-teal the dome itself is drawn in, so the button and the thing it
	// controls are visibly the same feature.
	private static final int OFF_BG = 0xFF2B2B2B;
	private static final int OFF_EDGE = 0xFF565B63;
	private static final int OFF_LABEL = 0xFF9AA0A6;
	private static final int ON_BG = 0xFF14484B;
	private static final int ON_EDGE = 0xFF4FD8DE;
	private static final int ON_LABEL = 0xFFC7FBFF;
	private static final int HOVER = 0x30FFFFFF;

	/** Status line under the slot: what the vessel in it still needs. */
	private static final int STATUS_Y = 60;
	private static final int STATUS_WAIT = 0xFFB0752A;
	private static final int STATUS_READY = 0xFF3FA34D;
	private static final int STATUS_DIM = 0xFF6B6B6B;

	protected AbstractMobRepellerScreen(T menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	/**
	 * Souls a vessel needs to lift THIS tier, or 0 when there is nothing above it. Read from
	 * {@link dev.alaindustrial.Config} by the tier subclass — the client has the config too, the same
	 * way the electromagnet's tooltip reads its own range.
	 */
	protected abstract int killsNeeded();

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	private boolean domeShown() {
		return RepellerDomeRenderer.isShownForOpenScreen();
	}

	private boolean overButton(double mx, double my) {
		int bx = this.leftPos + BUTTON_X;
		int by = this.topPos + BUTTON_Y;
		return mx >= bx && mx < bx + BUTTON_W && my >= by && my < by + BUTTON_H;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		renderEnergyBar(graphics, EnergyBarSpec.LEFT);
	}

	/** The wordless "a Soul Vessel goes here" answer, drawn while the slot is empty. */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, dev.alaindustrial.block.entity.MobRepellerBlockEntity.VESSEL_SLOT,
				new ItemStack(ModContent.SOUL_VESSEL.get()));
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		drawDomeButton(graphics, mouseX, mouseY);
		drawVesselStatus(graphics);
	}

	private void drawDomeButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int bx = this.leftPos + BUTTON_X;
		int by = this.topPos + BUTTON_Y;
		boolean on = domeShown();
		int edge = on ? ON_EDGE : OFF_EDGE;
		graphics.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, on ? ON_BG : OFF_BG);
		graphics.fill(bx, by, bx + BUTTON_W, by + 1, edge);
		graphics.fill(bx, by + BUTTON_H - 1, bx + BUTTON_W, by + BUTTON_H, edge);
		graphics.fill(bx, by, bx + 1, by + BUTTON_H, edge);
		graphics.fill(bx + BUTTON_W - 1, by, bx + BUTTON_W, by + BUTTON_H, edge);
		if (overButton(mouseX, mouseY)) {
			graphics.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, HOVER);
		}
		drawEye(graphics, bx + BUTTON_W / 2, by + BUTTON_H / 2, on ? ON_LABEL : OFF_LABEL, on);
	}

	/**
	 * The eye glyph, drawn from filled rects rather than a texture so it lives in the same file as the
	 * button it belongs to and needs no atlas coordinates to keep in step. Open eye = the dome is up;
	 * struck through = it is hidden.
	 */
	private void drawEye(GuiGraphicsExtractor graphics, int cx, int cy, int colour, boolean on) {
		// Lid: two stepped rows above and below, so the shape reads as an eye and not as a box.
		graphics.fill(cx - 7, cy - 1, cx + 7, cy, colour);
		graphics.fill(cx - 7, cy + 1, cx + 7, cy + 2, colour);
		graphics.fill(cx - 5, cy - 3, cx + 5, cy - 2, colour);
		graphics.fill(cx - 5, cy + 2, cx + 5, cy + 4, colour);
		graphics.fill(cx - 2, cy - 4, cx + 2, cy - 3, colour);
		graphics.fill(cx - 2, cy + 4, cx + 2, cy + 5, colour);
		// Iris — only when the dome is actually shown.
		if (on) {
			graphics.fill(cx - 2, cy - 2, cx + 2, cy + 3, ON_EDGE);
		} else {
			// Struck through: the plain "hidden" language, drawn as a stepped diagonal.
			for (int i = -6; i <= 6; i++) {
				graphics.fill(cx + i, cy + 3 - i / 2, cx + i + 1, cy + 4 - i / 2, colour);
			}
		}
	}

	/**
	 * One line under the slot answering the question the slot itself cannot: how far the vessel in it
	 * is from lifting this block a tier. Before this the slot simply refused an under-filled vessel
	 * and said nothing, which reads as a broken slot rather than as "not yet".
	 */
	private void drawVesselStatus(GuiGraphicsExtractor graphics) {
		int needed = killsNeeded();
		Component line;
		int colour;
		if (needed <= 0) {
			line = Component.translatable("gui.alaindustrial.mob_repeller.top_tier");
			colour = STATUS_DIM;
		} else {
			ItemStack vessel = this.menu.getSlot(0).getItem();
			if (vessel.isEmpty()) {
				line = Component.translatable("gui.alaindustrial.mob_repeller.need_vessel", needed);
				colour = STATUS_DIM;
			} else {
				int have = SoulVesselItem.kills(vessel);
				if (have >= needed) {
					line = Component.translatable("gui.alaindustrial.mob_repeller.ready");
					colour = STATUS_READY;
				} else {
					line = Component.translatable("gui.alaindustrial.mob_repeller.souls_short", needed - have);
					colour = STATUS_WAIT;
				}
			}
		}
		int w = this.font.width(line);
		graphics.text(this.font, line, this.leftPos + (this.imageWidth - w) / 2,
				this.topPos + STATUS_Y, colour, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (overButton(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable(domeShown()
							? "gui.alaindustrial.mob_repeller.dome.hide"
							: "gui.alaindustrial.mob_repeller.dome.show"),
					mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && !this.menu.isPanelOpen() && overButton(event.x(), event.y())
				&& this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
					MobRepellerMenu.BUTTON_TOGGLE_DOME);
			// A click needs to be heard as well as seen — the same UI click the teleporter's toggle uses.
			this.minecraft.getSoundManager().play(
					SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, domeShown() ? 0.9F : 1.1F));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}
}
