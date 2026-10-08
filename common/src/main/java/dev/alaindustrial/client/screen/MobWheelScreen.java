package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.MobWheelBlockEntity;
import dev.alaindustrial.core.environment.MobWheelFeed;
import dev.alaindustrial.core.environment.MobWheelProfile;
import dev.alaindustrial.core.environment.MobWheelStatus;
import dev.alaindustrial.menu.MobWheelMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Screen of the mob wheel's drive (MOD-763): the feeder slot, the occupant's stamina and a display of what
 * the wheel is doing — who runs in it, how fast, what it makes, and why it stopped.
 *
 * <p>No energy bar: the drive keeps one tick of output and nothing more (D4), so the bar would only flicker.
 * The gauge on the left is the occupant's stamina instead, the one level a player actually manages here.
 * No statistics tab either — the drive takes no chips, so there is nowhere to fit one.
 */
public class MobWheelScreen extends MachineScreen<MobWheelMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/mob_wheel.png");

	/** Stamina gauge: inner 10×44 column, filled bottom-up from the service area (tools/gen_mob_wheel_gui.py). */
	private static final int STAMINA_X = 9;
	private static final int STAMINA_BOTTOM = 64;
	private static final int STAMINA_UV_X = 176;

	/** The wheel pictogram in the display's corner, and its "turning" version in the service area. */
	private static final int ICON_X = 52;
	private static final int ICON_Y = 21;
	private static final int ICON_SIZE = 16;
	private static final int ICON_UV_X = 186;
	private static final int ICON_UV_Y = 0;

	/** Text band of the display's three readout lines, right of the pictogram, and their baselines. */
	private static final int LINE_LEFT = 72;
	private static final int LINE_RIGHT = 166;
	private static final int SPECIES_Y = 22;
	private static final int SPEED_Y = 34;
	private static final int OUTPUT_Y = 46;

	/**
	 * Baseline of the status row, the full width of the display. Public so an L3 stand crops its comparison
	 * to exactly this row rather than to a copy of the number.
	 */
	public static final int STATUS_TEXT_Y = 58;
	private static final int STATUS_LEFT = 52;
	private static final int STATUS_RIGHT = 166;

	/** One staple food per species, for the empty feeder's hint; every one of them is a real portion. */
	private static final List<Item> STAPLES = List.of(Items.CARROT, Items.WHEAT, Items.BREAD, Items.ROTTEN_FLESH);

	public MobWheelScreen(MobWheelMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected boolean hasStatsTab() {
		return false;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		int fill = Math.min(1000, Math.max(0, this.menu.getStaminaPermille())) * EnergyBarSpec.HEIGHT / 1000;
		if (fill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					this.leftPos + STAMINA_X, this.topPos + STAMINA_BOTTOM - fill,
					STAMINA_UV_X, EnergyBarSpec.HEIGHT - fill,
					EnergyBarSpec.WIDTH, fill, TEX_SIZE, TEX_SIZE);
		}
		if (this.menu.getStatus() == MobWheelStatus.RUNNING) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					this.leftPos + ICON_X, this.topPos + ICON_Y, ICON_UV_X, ICON_UV_Y,
					ICON_SIZE, ICON_SIZE, TEX_SIZE, TEX_SIZE);
		}
	}

	@Override
	protected void drawFrameText(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		MobWheelProfile species = this.menu.getSpecies();
		MobWheelStatus status = this.menu.getStatus();
		String runnerType = this.menu.getRunnerType();
		if (species != null && runnerType != null) {
			drawFittedStatus(graphics, runnerName(runnerType), SPECIES_Y, LINE_LEFT, LINE_RIGHT, GuiStyle.TEXT);
			drawFittedStatus(graphics,
					Component.translatable("gui.alaindustrial.mob_wheel.speed", this.menu.getSpeedPercent()),
					SPEED_Y, LINE_LEFT, LINE_RIGHT, GuiStyle.TEXT);
			drawFittedStatus(graphics,
					Component.translatable("gui.alaindustrial.output", this.menu.getProductionRate()),
					OUTPUT_Y, LINE_LEFT, LINE_RIGHT, GuiStyle.TEXT);
		}
		// Every status gets a line: a blocking one in red says what to fix, the others say what the mob is
		// doing (running, a villager's pause, resting on hay) in the dim colour.
		drawFittedStatus(graphics, Component.translatable(status.translationKey()), STATUS_TEXT_Y,
				STATUS_LEFT, STATUS_RIGHT, status.isBlocking() ? GuiStyle.STATUS_BLOCKING : GuiStyle.TEXT_DIM);
	}

	/** The vanilla name of the occupant's own type ("Pig", "Zombie Villager"), in the player's language. */
	private static Component runnerName(String runnerType) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(runnerType));
		return type.getDescription();
	}

	/**
	 * An empty feeder shows the current occupant's staple food (watching the mob, not a reference), or cycles
	 * four common staples when nobody is in — never the whole feed table (D7).
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		MobWheelProfile species = this.menu.getSpecies();
		ItemStack hint = species == null ? cyclingHint(STAPLES)
				: new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(MobWheelFeed.staple(species))));
		ghostHint(graphics, MobWheelBlockEntity.FEED_SLOT, hint);
	}

	/** The stamina bar is a gauge like any other: no input, so no overlay modality beyond the overlays' footprints. */
	@Override
	protected void gaugeTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.isHovering(STAMINA_X, STAMINA_BOTTOM - EnergyBarSpec.HEIGHT,
				EnergyBarSpec.WIDTH, EnergyBarSpec.HEIGHT, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.mob_wheel.stamina", this.menu.getStaminaPermille() / 10),
					mouseX, mouseY);
		}
	}
}
