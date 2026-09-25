package dev.alaindustrial.client.render;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.fluid.ItemFluid;
import dev.alaindustrial.mixin.client.ItemTintSourcesAccessor;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Item tint for one glass layer of the filled vacuum capsule (MOD-452): the capsule's glass colour at
 * {@code opacity} laid over the colour of the fluid inside, so any fluid — the mod's, a future one or
 * another mod's — colours the capsule with no sprite of its own.
 *
 * <p>The capsule model has one white mask per glass shade of the frame, each with its own instance of
 * this source ({@code items/filled_vacuum_capsule.json}). Every layer is binary-alpha, so the whole
 * icon stays in the cutout pass on every graphics setting — no translucent frame over an opaque fill.
 * Both the model and the {@code glass}/{@code opacity} values are written by
 * {@code tools/textures_work/vacuum_capsule/gen_vacuum_capsule.py}, which proves the result equal to the
 * sprite it would have baked.
 */
public record CapsuleGlassTintSource(int glass, int opacity) implements ItemTintSource {
	/** The neutral grey-blue fill of a capsule whose fluid component is missing. */
	static final int NO_FLUID = 0x96AABA;

	public static final MapCodec<CapsuleGlassTintSource> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			ExtraCodecs.RGB_COLOR_CODEC.fieldOf("glass").forGetter(CapsuleGlassTintSource::glass),
			Codec.intRange(0, 255).fieldOf("opacity").forGetter(CapsuleGlassTintSource::opacity)
	).apply(i, CapsuleGlassTintSource::new));

	public static void register() {
		ItemTintSourcesAccessor.alaindustrial$idMapper()
				.put(Industrialization.id("capsule_glass"), MAP_CODEC);
	}

	@Override
	public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
		Fluid fluid = ItemFluid.get(stack);
		int fluidColor = fluid == Fluids.EMPTY ? NO_FLUID : FluidTankItemTintSource.fluidColor(fluid, level, entity);
		return blend(glass, opacity, fluidColor);
	}

	/**
	 * {@code glass} at {@code opacity} (0..255) over an opaque {@code fluid}, per channel — PIL's
	 * {@code alpha_composite}, to the unit. {@code gen_vacuum_capsule.py#blend} is the Python twin.
	 */
	static int blend(int glass, int opacity, int fluid) {
		return ARGB.color(
				channel(ARGB.red(glass), opacity, ARGB.red(fluid)),
				channel(ARGB.green(glass), opacity, ARGB.green(fluid)),
				channel(ARGB.blue(glass), opacity, ARGB.blue(fluid)));
	}

	private static int channel(int glass, int opacity, int fluid) {
		return (glass * opacity + fluid * (255 - opacity) + 127) / 255;
	}

	@Override
	public MapCodec<CapsuleGlassTintSource> type() {
		return MAP_CODEC;
	}
}
