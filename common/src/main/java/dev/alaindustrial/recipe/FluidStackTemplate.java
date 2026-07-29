package dev.alaindustrial.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;

/**
 * An exact fluid identity and amount produced by a processing recipe.
 *
 * <p>The amount is expressed in the mod-wide mB unit. Outputs use exact registry holders rather
 * than tags: a recipe may accept interchangeable input fluids, but must produce one deterministic
 * fluid that a machine can put into a tank.
 */
public record FluidStackTemplate(Holder<Fluid> fluid, int amount) {

	public static final Codec<FluidStackTemplate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("fluid").forGetter(FluidStackTemplate::fluid),
			Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(FluidStackTemplate::amount)
	).apply(instance, FluidStackTemplate::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, FluidStackTemplate> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.holderRegistry(Registries.FLUID), FluidStackTemplate::fluid,
					ByteBufCodecs.INT, FluidStackTemplate::amount,
					FluidStackTemplate::new);

	public FluidStackTemplate {
		if (amount <= 0) {
			throw new IllegalArgumentException("fluid output amount must be positive");
		}
	}
}
