package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.misc.SoulVesselItem;
import dev.alaindustrial.menu.MobRepellerMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
 * {@link dev.alaindustrial.network.ZonePreviewPayload} carrying the server-authoritative position
 * and reach, which the client feeds to {@code ZonePreviewRenderer} — so the dome can never
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
	 * Dome toggle, right of the vessel slot — the shared eye button (design 06 of the ten mocked up for
	 * this), {@link ZonePreviewButton}.
	 */
	private static final ZonePreviewButton DOME = new ZonePreviewButton(134, 32, MobRepellerMenu.BUTTON_TOGGLE_DOME,
			"gui.alaindustrial.mob_repeller.dome.show", "gui.alaindustrial.mob_repeller.dome.hide");

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

	/** The energy bar and its tooltip, drawn by the base (MOD-716). */
	@Override
	protected EnergyBarSpec energyBar() {
		return EnergyBarSpec.LEFT;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
	}

	/** The wordless "a Soul Vessel goes here" answer, drawn while the slot is empty. */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, dev.alaindustrial.block.entity.MobRepellerBlockEntity.VESSEL_SLOT,
				new ItemStack(ModContent.SOUL_VESSEL.get()));
	}

	/** Under both panels, so an open statistics panel covers the dome button and the status (MOD-693). */
	@Override
	protected void drawUnderPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.menu.isPanelOpen()) {
			return; // the modal upgrade panel owns this area
		}
		DOME.draw(graphics, this.leftPos, this.topPos, mouseX, mouseY);
		drawVesselStatus(graphics);
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
	protected void controlTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		DOME.tooltip(graphics, this.font, this.leftPos, this.topPos, mouseX, mouseY);
	}

	/** The base calls this only where the open statistics panel does not cover the button (MOD-693, MOD-762). */
	@Override
	protected boolean controlClicked(MouseButtonEvent event, boolean doubleClick) {
		return DOME.clicked(this.minecraft, this.menu.containerId, this.leftPos, this.topPos, event);
	}
}
