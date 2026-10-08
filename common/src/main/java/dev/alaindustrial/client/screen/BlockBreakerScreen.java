package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.BlockBreakerBlockEntity;
import dev.alaindustrial.block.entity.BlockBreakerBlockEntity.RedstoneMode;
import dev.alaindustrial.core.machine.StatusLine;
import dev.alaindustrial.menu.BlockBreakerMenu;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Screen of the Block Breaker (MOD-787), in the mod's machine style: the Macerator's frame
 * ({@code tools/gen_block_breaker_gui.py}), whose drill-bit progress sprite is exactly what the auger does.
 * The tool slot stands where the Macerator takes its input, the drill fills as the block in front gives way,
 * and where the Macerator has its output slot the breaker has its redstone-mode button — what it breaks
 * drops into the world.
 *
 * <p>The button shows the vanilla item a player associates with the mode — gunpowder for "the signal does
 * not matter", a redstone torch for "works while powered", a lever for "a lever switches it off" — so it
 * needs no caption and cannot overflow in any language; the tooltip names it.
 */
public class BlockBreakerScreen extends ProgressMachineScreen<BlockBreakerMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/block_breaker.png");

	/** The Macerator's drill-bit progress sprite: atlas u/v/w/h, then where it lands in the frame. */
	private static final ProgressSpec PROGRESS = new ProgressSpec(176, 44, 25, 9, 82, 38, true);

	private static final MachineLayout LAYOUT = MachineLayout.of(TEXTURE, EnergyBarSpec.LEFT)
			.withProgress(PROGRESS)
			.withStatus(MachineLayout.StatusBand.STANDARD);

	/** Redstone-mode button, where the Macerator's output slot well was painted over. */
	private static final int BUTTON_X = 115;
	private static final int BUTTON_Y = 33;
	private static final int BUTTON_SIZE = 20;
	/** The idle button's rim, the Mob Repeller's: visible on the light panel without reading as pressed. */
	private static final int BUTTON_EDGE = 0xFF565B63;

	/** The tools a hint cycles through while the slot is empty. */
	private static final List<Item> TOOL_HINTS = List.of(Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL);

	public BlockBreakerScreen(BlockBreakerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, LAYOUT);
	}

	@Override
	protected StatusLine status() {
		return this.menu.getStatus();
	}

	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, BlockBreakerBlockEntity.TOOL_SLOT, cyclingHint(TOOL_HINTS));
	}

	@Override
	protected void drawUnderPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.menu.isPanelOpen()) {
			return; // the modal upgrade panel owns this area
		}
		int bx = this.leftPos + BUTTON_X;
		int by = this.topPos + BUTTON_Y;
		boolean over = overButton(mouseX, mouseY);
		graphics.fill(bx, by, bx + BUTTON_SIZE, by + BUTTON_SIZE, over ? GuiStyle.BUTTON_ACTIVE_EDGE : BUTTON_EDGE);
		graphics.fill(bx + 1, by + 1, bx + BUTTON_SIZE - 1, by + BUTTON_SIZE - 1,
				over ? GuiStyle.BUTTON_ACTIVE : GuiStyle.BUTTON);
		graphics.item(modeIcon(this.menu.getRedstoneMode()), bx + 2, by + 2);
	}

	private boolean overButton(double mx, double my) {
		int bx = this.leftPos + BUTTON_X;
		int by = this.topPos + BUTTON_Y;
		return mx >= bx && mx < bx + BUTTON_SIZE && my >= by && my < by + BUTTON_SIZE;
	}

	private static ItemStack modeIcon(RedstoneMode mode) {
		return new ItemStack(switch (mode) {
			case IGNORE -> Items.GUNPOWDER;
			case WITH_SIGNAL -> Items.REDSTONE_TORCH;
			case WITHOUT_SIGNAL -> Items.LEVER;
		});
	}

	@Override
	protected void controlTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (overButton(mouseX, mouseY)) {
			String key = "gui.alaindustrial.block_breaker.redstone."
					+ this.menu.getRedstoneMode().name().toLowerCase(Locale.ROOT);
			graphics.setTooltipForNextFrame(this.font, Component.translatable(key), mouseX, mouseY);
		}
	}

	@Override
	protected boolean controlClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overButton(event.x(), event.y())
				&& this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
					BlockBreakerMenu.BUTTON_REDSTONE_MODE);
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return false;
	}
}
