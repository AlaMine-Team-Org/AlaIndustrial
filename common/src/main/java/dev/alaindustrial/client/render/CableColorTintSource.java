package dev.alaindustrial.client.render;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.CableColors;
import dev.alaindustrial.mixin.client.ItemTintSourcesAccessor;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Tints the sleeve layer of an insulated cable item with the stack's dye (MOD-666), through the same
 * {@link CableColors} table the placed block uses.
 *
 * <p>{@code brighten} exists for the inventory icon only. That icon is a pre-rendered 3D picture whose
 * lit faces are brighter than the texture, and a tint can only darken, so the generator
 * ({@code tools/gen_insulated_cable_assets.py}) stores the icon's sleeve divided by its brightest
 * factor and writes that factor here to multiply back. Channels saturate at 255.
 *
 * @param brighten factor applied to the sleeve colour, 1 for the 3D block model
 */
public record CableColorTintSource(float brighten) implements ItemTintSource {

	public static final MapCodec<CableColorTintSource> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.FLOAT.optionalFieldOf("brighten", 1.0f).forGetter(CableColorTintSource::brighten))
			.apply(i, CableColorTintSource::new));

	public static void register() {
		ItemTintSourcesAccessor.alaindustrial$idMapper().put(Industrialization.id("cable_color"), MAP_CODEC);
	}

	@Override
	public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
		int rgb = CableColors.sleeve(stack.get(ModDataComponents.CABLE_COLOR.get()));
		if (brighten == 1.0f) {
			return rgb;
		}
		int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * brighten));
		int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * brighten));
		int b = Math.min(255, Math.round((rgb & 0xFF) * brighten));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	@Override
	public MapCodec<CableColorTintSource> type() {
		return MAP_CODEC;
	}
}
