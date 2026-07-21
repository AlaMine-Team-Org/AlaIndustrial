package dev.alaindustrial.client.render;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.FluidTankContents;
import dev.alaindustrial.item.FluidTankVisuals;
import dev.alaindustrial.mixin.client.ItemTintSourcesAccessor;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Dynamic item tint for the fluid layer of the portable tank icon. */
public final class FluidTankItemTintSource implements ItemTintSource {
	public static final FluidTankItemTintSource INSTANCE = new FluidTankItemTintSource();
	public static final MapCodec<FluidTankItemTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

	private FluidTankItemTintSource() {
	}

	public static void register() {
		ItemTintSourcesAccessor.alaindustrial$idMapper()
				.put(Industrialization.id("fluid_tank"), MAP_CODEC);
	}

	@Override
	public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
		FluidTankContents contents = stack.get(ModDataComponents.FLUID_TANK_CONTENTS.get());
		if (contents == null) {
			return 0x00FFFFFF;
		}
		var fluid = contents.fluid().value();
		var state = fluid.defaultFluidState().createLegacyBlock();
		var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
				.get(fluid.defaultFluidState());
		var tintSource = model.tintSource();
		int color;
		if (tintSource == null) {
			color = FluidTankVisuals.fallbackColor(fluid);
		} else if (level != null && entity != null) {
			color = tintSource.colorInWorld(state, level, entity.blockPosition());
		} else {
			color = tintSource.color(state);
		}
		return ARGB.opaque(color);
	}

	@Override
	public MapCodec<? extends ItemTintSource> type() {
		return MAP_CODEC;
	}
}
