package dev.alaindustrial.compat.rei;

import dev.alaindustrial.client.screen.FluidGauge;
import dev.architectury.fluid.FluidStack;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer;
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Draws fluid entries in REI (MOD-250) — a stand-in for REI's own fluid renderer, which draws nothing.
 *
 * <p><b>Why this exists.</b> In REI 26.2.820 the body of
 * {@code FluidEntryDefinition$FluidEntryRenderer#render} is commented out and its missing-sprite
 * supplier returns {@code null}: REI of this build renders <em>no</em> fluid at all, not just modded
 * ones — vanilla water in a vanilla category comes out as an empty slot too. Found by playtest on the
 * Polymerizer's card, where the oil input read as a blank square. JEI on NeoForge is unaffected (it
 * draws fluids through vanilla's {@code FluidStateModelSet}), so this is a Fabric-only patch.
 *
 * <p>Registered for the whole {@code VanillaEntryTypes.FLUID} type rather than patched into our one
 * category, so every place REI shows a fluid — recipe cards, the entry list, favourites — gets a
 * texture, and so a REI release that restores its own renderer can be adopted by deleting one
 * registration.
 *
 * <p>The drawing itself is {@link FluidGauge}, the same loader-neutral helper the machine screens use,
 * so a fluid looks identical in a tank gauge and on a recipe card. REI's {@code GuiGraphics} extends
 * vanilla's {@code GuiGraphicsExtractor}, so it is passed straight through with no adapter.
 *
 * <p>Tooltips are delegated to whatever renderer REI had before us: that part of REI works, and it
 * carries the fluid name, the "1000 mB" line and the advanced-tooltip id.
 */
public class ReiFluidEntryRenderer implements EntryRenderer<FluidStack> {

	private final EntryRenderer<FluidStack> delegate;

	public ReiFluidEntryRenderer(EntryRenderer<FluidStack> delegate) {
		this.delegate = delegate;
	}

	@Override
	public void render(EntryStack<FluidStack> entry, GuiGraphics graphics, Rectangle bounds, int mouseX,
			int mouseY, float delta) {
		FluidStack stack = entry.getValue();
		if (stack.isEmpty()) {
			return;
		}
		// REI's own convention for a partly filled entry: the fluid occupies the bottom fraction of the
		// slot. Nothing in the mod sets it, but a foreign plugin may, and honouring it is one clamp.
		float ratio = Mth.clamp(entry.get(EntryStack.Settings.FLUID_RENDER_RATIO), 0.0F, 1.0F);
		int height = Mth.floor(bounds.height * ratio);
		if (height <= 0) {
			return;
		}
		FluidGauge.draw(graphics, stack.getFluid(), bounds.x, bounds.getMaxY() - height, bounds.width, height);
	}

	@Nullable
	@Override
	public Tooltip getTooltip(EntryStack<FluidStack> entry, TooltipContext context) {
		return delegate.getTooltip(entry, context);
	}
}
