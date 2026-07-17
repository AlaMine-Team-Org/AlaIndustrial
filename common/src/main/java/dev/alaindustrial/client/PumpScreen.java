package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.menu.PumpMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Texture-backed screen for the Pump.
 *
 * <p>Left bar: fluid level (bottom-up), drawn with the fluid's <b>real block texture</b>. MOD-099: the pump
 * now holds any fluid, so the two baked lava/water sprites in our own atlas could not cover it — but a flat
 * colour rectangle looked cheap. Instead the tank is tiled with the fluid's own still texture, taken from
 * vanilla's fluid model, so water looks like water and a modded fluid looks like itself, with no per-fluid
 * asset of ours. Texture, tint and name are all derived <em>here</em> from the synced fluid registry id
 * (channel 6); none is sent over the wire, as all are pure functions of the fluid type. The fill height
 * comes from the permille ratio (channels 4/5), independent of the 10-bucket capacity.
 * Right bar: energy (orange sprite, bottom-up) — stored EU / buffer capacity.
 * Slots: fluid-bucket input at (60,23), empty-bucket output at (98,23).
 */
public class PumpScreen extends MachineScreen<PumpMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/pump.png");
	private static final int TEX_SIZE = 256;

	// Fluid level fill (LEFT bar): the fluid's own texture, tiled, growing bottom-up. inner trough
	// x=16-26 (11px wide), y=19-65 (46px tall). Texture/tint derive from the synced fluid id (channel 6).
	private static final int FLUID_W = 11, FLUID_H = 46;
	private static final int FLUID_X = 16, FLUID_BOTTOM = 65;

	// Block textures are 16×16. The trough is narrower (11) and taller (46) than one tile, so the sprite is
	// tiled at its native size and clipped to the trough — stretching one tile to 11×46 would visibly smear
	// it (fluid textures are regular patterns, and the distortion reads as a rendering bug).
	private static final int TILE = 16;

	// Multiplicative identity for blitSprite's colour argument: draw the sprite exactly as-is (used for
	// fluids that declare no tint source, e.g. lava).
	private static final int NO_TINT = 0xFFFFFFFF;

	// Energy fill (RIGHT bar): orange sprite at (176, 0), 8px wide × 42px tall; inner x=149-158, y=20-64.
	private static final int EFILL_SU = 176, EFILL_SV = 0, EFILL_W = 10, EFILL_H = 44;
	private static final int EFILL_X = 149, EFILL_BOTTOM = 64;

	// Tank capacity in mB for the tooltip (matches PumpBlockEntity.TANK_CAPACITY).
	private static final int TANK_MB = 10_000;

	public PumpScreen(PumpMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	public void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		// Static frame: 176×166 region at (0,0) of the 256×256 atlas.
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F,
				this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);

		// Fluid level fill: grows bottom-up proportional to the tank permille, in the fluid's own texture.
		// Only drawn when a fluid is present (registry id != NONE).
		int fluidId = this.menu.getFluidRegistryId();
		int denom = this.menu.getFluidPermilleMax();
		int permille = denom > 0 ? this.menu.getFluidPermille() : 0;
		if (fluidId != PumpBlockEntity.FLUID_ID_NONE && permille > 0) {
			int fluidFill = (int) ((long) permille * FLUID_H / denom);
			if (fluidFill > 0) {
				drawFluid(graphics, BuiltInRegistries.FLUID.byId(fluidId),
						x + FLUID_X, y + FLUID_BOTTOM - fluidFill, fluidFill);
			}
		}

		// Energy fill: grows bottom-up proportional to stored EU.
		int capacity = this.menu.getCapacity();
		int eFill = capacity > 0 ? (int) ((long) this.menu.getEnergy() * EFILL_H / capacity) : 0;
		if (eFill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + EFILL_X, y + EFILL_BOTTOM - eFill,
					(float) EFILL_SU, (float) (EFILL_SV + EFILL_H - eFill),
					EFILL_W, eFill, TEX_SIZE, TEX_SIZE);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// Right bar — stored EU / buffer.
		if (this.isHovering(EFILL_X, EFILL_BOTTOM - EFILL_H, EFILL_W, EFILL_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.energy", this.menu.getEnergy(), this.menu.getCapacity()),
					mouseX, mouseY);
		}
		// Left bar — fluid name + level as millibuckets. The name is resolved client-side from the synced
		// fluid registry id (channel 6), so it shows the right label for any fluid, not just lava/water.
		int fluidId = this.menu.getFluidRegistryId();
		int denom = this.menu.getFluidPermilleMax();
		int permille = denom > 0 ? this.menu.getFluidPermille() : 0;
		if (fluidId != IdMap.DEFAULT
				&& this.isHovering(FLUID_X, FLUID_BOTTOM - FLUID_H, FLUID_W, FLUID_H, mouseX, mouseY)) {
			Fluid fluid = BuiltInRegistries.FLUID.byId(fluidId);
			Component name = fluidDisplayName(fluid);
			int mb = (int) ((long) permille * TANK_MB / denom);
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.fluid", name, mb, TANK_MB),
					mouseX, mouseY);
		}
	}

	/**
	 * Draw {@code fluid} filling the trough from {@code topY} down to the bar's bottom, using the fluid's own
	 * still texture tiled at its native 16×16 and clipped to the trough.
	 *
	 * <p>The texture and tint come from vanilla's baked fluid model
	 * ({@code ModelManager.getFluidStateModelSet()}), which is the same lookup the world renderer uses — so
	 * any fluid a loader registers a model for (both Fabric and NeoForge feed their modded fluids into this
	 * very set) renders correctly here with no per-loader code and no asset of ours. {@code get} returns a
	 * missing-texture model rather than null for an unknown fluid, so there is no null path to guard.
	 *
	 * <p>The tint is what makes water blue: {@code water_still} is a greyscale texture. {@code tintSource()}
	 * is <b>nullable</b> and is null for exactly the fluids that need no tint (lava — its texture is already
	 * red); those draw with their texture untouched. Dereferencing it unconditionally crashed the screen with
	 * an NPE the moment a lava-filled pump was opened, which no compile or server test can catch.
	 *
	 * <p>The tint must be sampled <em>in the world</em>: vanilla's water tint returns {@code -1} (i.e. "no
	 * tint", leaving the tank grey) from the world-less {@code color(BlockState)} and only yields a real
	 * colour from {@code colorInWorld}, which reads the biome. We sample at the player's position — they are
	 * standing at the pump to have this screen open, so it is their biome's water colour — because the client
	 * menu does not know the block's own position ({@code ContainerLevelAccess.NULL} client-side). A pump
	 * right on a biome border can therefore show the neighbouring biome's shade; harmless, and invisible
	 * without a side-by-side comparison.
	 */
	private void drawFluid(GuiGraphicsExtractor graphics, Fluid fluid, int leftX, int topY, int height) {
		Minecraft minecraft = Minecraft.getInstance();
		FluidState state = fluid.defaultFluidState();
		FluidModel model = minecraft.getModelManager().getFluidStateModelSet().get(state);
		TextureAtlasSprite sprite = model.stillMaterial().sprite();
		int tint = fluidTint(model.tintSource(), state, minecraft);

		int bottomY = topY + height;
		// Clip to the exact trough rect, then lay whole tiles from the bottom edge upward: the fill height is
		// rarely a multiple of 16, and the top tile must be cut off mid-texture rather than squashed.
		graphics.enableScissor(leftX, topY, leftX + FLUID_W, bottomY);
		for (int tileY = bottomY - TILE; tileY > topY - TILE; tileY -= TILE) {
			for (int tileX = leftX; tileX < leftX + FLUID_W; tileX += TILE) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, tileX, tileY, TILE, TILE, tint);
			}
		}
		graphics.disableScissor();
	}

	/**
	 * The opaque ARGB to multiply a fluid's texture by: {@link #NO_TINT} when the fluid declares no tint
	 * source (lava), otherwise its biome-sampled colour, falling back to the world-less variant when there is
	 * no level/player to sample against (e.g. a screen opened outside a world).
	 */
	private static int fluidTint(BlockTintSource tintSource, FluidState state, Minecraft minecraft) {
		if (tintSource == null) {
			return NO_TINT;
		}
		BlockState legacy = state.createLegacyBlock();
		int rgb = minecraft.level != null && minecraft.player != null
				? tintSource.colorInWorld(legacy, minecraft.level, minecraft.player.blockPosition())
				: tintSource.color(legacy);
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	/**
	 * A player-facing name for {@code fluid}: its placed block's name (e.g. "Water", "Lava"), mirroring
	 * {@code CapsuleInteractions.fluidDisplayName}. Falls back to a generic label for fluids with no
	 * placeable block (exotic modded fluids only reachable via tanks).
	 */
	private static Component fluidDisplayName(Fluid fluid) {
		BlockState legacy = fluid.defaultFluidState().createLegacyBlock();
		if (!legacy.isAir()) {
			return legacy.getBlock().getName();
		}
		return Component.translatable("item.alaindustrial.filled_vacuum_capsule.fluid_unknown");
	}
}
