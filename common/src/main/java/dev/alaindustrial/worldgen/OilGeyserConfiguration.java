package dev.alaindustrial.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Configuration of {@link OilGeyserFeature} (MOD-248) — the rarest oil deposit: a spout above
 * ground, a one-block shaft all the way down, and a flooded dome at the bottom.
 *
 * <p>{@link #domeRadius} is capped at {@value #MAX_DOME_RADIUS} for the chunk-safety reason spelled
 * out in {@link OilLakeConfiguration}: a feature may only write within ±1 chunk, which leaves 16
 * blocks of worst-case slack from the placement origin, and the dome needs one extra layer for its
 * barrier shell. {@value #MAX_DOME_RADIUS} + 1 keeps three blocks of margin. A bigger cavern is not
 * a matter of raising the number — it would have to be split across several features, one per
 * chunk, which is a different piece of work entirely.
 *
 * <p>{@link #domeCenterY} is an absolute world Y rather than an offset from the surface, because
 * "just above bedrock" is a property of the world, not of the terrain height above it. The feature
 * clamps it up if a dimension's floor sits higher than the configured value.
 */
public record OilGeyserConfiguration(
		BlockStateProvider fluid,
		BlockStateProvider barrier,
		IntProvider spoutHeight,
		int domeCenterY,
		IntProvider domeRadius,
		BlockPredicate canReplace) implements FeatureConfiguration {

	/** Largest dome half-extent that still fits the ±1-chunk write window with its shell. */
	public static final int MAX_DOME_RADIUS = 12;

	public static final Codec<OilGeyserConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockStateProvider.CODEC.fieldOf("fluid")
					.forGetter(OilGeyserConfiguration::fluid),
			BlockStateProvider.CODEC.fieldOf("barrier")
					.forGetter(OilGeyserConfiguration::barrier),
			IntProviders.codec(1, 5).fieldOf("spout_height")
					.forGetter(OilGeyserConfiguration::spoutHeight),
			Codec.intRange(-2032, 2016).fieldOf("dome_center_y")
					.forGetter(OilGeyserConfiguration::domeCenterY),
			IntProviders.codec(3, MAX_DOME_RADIUS).fieldOf("dome_radius")
					.forGetter(OilGeyserConfiguration::domeRadius),
			BlockPredicate.CODEC.fieldOf("can_replace")
					.forGetter(OilGeyserConfiguration::canReplace))
			.apply(instance, OilGeyserConfiguration::new));
}
