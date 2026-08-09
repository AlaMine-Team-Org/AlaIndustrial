package dev.alaindustrial.item.fluid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Atomic item-form snapshot of the Distillation Column's three tanks (MOD-251), reusing the portable
 * tank's {@link FluidTankContents} per slot. The slots are positional — oil (middle), diesel (top),
 * fuel oil (bottom) — so a datapack fluid in an output tank restores to the right tank without any
 * fluid-identity dispatch.
 *
 * <p>Round 2 also carries the coke {@code fouling} (0..100): a clogged column re-placed is still
 * clogged — otherwise break-and-place would be a free wrench cleaning. A pristine empty tower
 * carries no component at all (all-empty + fouling 0 is never built), which keeps fresh column
 * items stackable and component-identical.
 */
public record DistillationColumnContents(Optional<FluidTankContents> oil,
		Optional<FluidTankContents> diesel, Optional<FluidTankContents> fuelOil, int fouling) {

	public static final Codec<DistillationColumnContents> CODEC =
			RecordCodecBuilder.create(instance -> instance.group(
					FluidTankContents.CODEC.optionalFieldOf("oil")
							.forGetter(DistillationColumnContents::oil),
					FluidTankContents.CODEC.optionalFieldOf("diesel")
							.forGetter(DistillationColumnContents::diesel),
					FluidTankContents.CODEC.optionalFieldOf("fuel_oil")
							.forGetter(DistillationColumnContents::fuelOil),
					Codec.intRange(0, 100).optionalFieldOf("fouling", 0)
							.forGetter(DistillationColumnContents::fouling))
			.apply(instance, DistillationColumnContents::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, DistillationColumnContents> STREAM_CODEC =
			StreamCodec.composite(
					FluidTankContents.STREAM_CODEC.apply(ByteBufCodecs::optional),
					DistillationColumnContents::oil,
					FluidTankContents.STREAM_CODEC.apply(ByteBufCodecs::optional),
					DistillationColumnContents::diesel,
					FluidTankContents.STREAM_CODEC.apply(ByteBufCodecs::optional),
					DistillationColumnContents::fuelOil,
					ByteBufCodecs.VAR_INT,
					DistillationColumnContents::fouling,
					DistillationColumnContents::new);

	public DistillationColumnContents {
		if (oil.isEmpty() && diesel.isEmpty() && fuelOil.isEmpty() && fouling <= 0) {
			throw new IllegalArgumentException("A pristine empty column carries no component at all");
		}
	}
}
