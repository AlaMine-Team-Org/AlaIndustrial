package dev.alaindustrial.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Atomic item-form snapshot of a portable fluid tank (MOD-111).
 *
 * <p>The fluid and amount deliberately travel as one immutable component: two independent components
 * could be split by commands or another mod and produce an impossible {@code amount > 0, fluid = empty}
 * stack. Empty tanks carry no component at all; every value represented by this record is therefore a
 * real, positive amount of a non-empty registry fluid.
 */
public record FluidTankContents(Holder<Fluid> fluid, long amount) {
	private static final Codec<FluidTankContents> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("fluid").forGetter(FluidTankContents::fluid),
			Codec.LONG.fieldOf("amount").forGetter(FluidTankContents::amount))
			.apply(instance, FluidTankContents::new));

	public static final Codec<FluidTankContents> CODEC = RAW_CODEC.validate(FluidTankContents::validate);

	public static final StreamCodec<RegistryFriendlyByteBuf, FluidTankContents> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.holderRegistry(Registries.FLUID), FluidTankContents::fluid,
			ByteBufCodecs.VAR_LONG, FluidTankContents::amount,
			FluidTankContents::new);

	public FluidTankContents {
		if (fluid == null || fluid.value() == Fluids.EMPTY) {
			throw new IllegalArgumentException("Portable tank contents require a non-empty fluid");
		}
		if (amount <= 0) {
			throw new IllegalArgumentException("Portable tank contents require a positive amount");
		}
	}

	private static DataResult<FluidTankContents> validate(FluidTankContents value) {
		return value.amount > 0 && value.fluid.value() != Fluids.EMPTY
				? DataResult.success(value)
				: DataResult.error(() -> "Portable tank contents must contain a non-empty fluid and positive amount");
	}
}
