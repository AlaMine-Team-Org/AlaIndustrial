package dev.alaindustrial.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Configuration of {@link OilLakeFeature} (MOD-248) — the size-parameterised replacement for
 * {@code minecraft:lake}.
 *
 * <p><b>Why this exists.</b> {@code LakeFeature} is {@code @Deprecated} in 26.2 and its extent is
 * hard-coded: {@code place} allocates {@code boolean[2048]} and writes a fixed 16 × 8 × 16 box, with
 * no size anywhere in {@code LakeFeature.Configuration}. Three deposit sizes (surface puddle,
 * mine-depth pool, deep reservoir) are therefore impossible on the vanilla feature — the radii have
 * to become configuration, which means an own feature.
 *
 * <p><b>Why the radii are {@link IntProvider}s and not plain ints.</b> Sampling them per placement
 * gives every deposit of one tier its own shape, so a "medium" lake is a range rather than a stamp.
 *
 * <p><b>The hard ceiling on {@link #horizontalRadius} is a chunk-safety limit, not taste.</b> At
 * generation step {@code FEATURES} the pyramid grants {@code blockStateWriteRadius(1)}, so
 * {@code WorldGenRegion} accepts writes only in the 3 × 3 chunk block {@code [cx·16 − 16,
 * cx·16 + 31]}. {@code InSquarePlacement} puts the origin anywhere in {@code [cx·16, cx·16 + 15]},
 * which leaves exactly 16 blocks of slack in the worst case. Beyond that {@code ensureCanWrite}
 * silently drops the block AND logs an error per attempt (and pauses the game outright in a dev
 * client, where {@code IS_RUNNING_IN_IDE} is set). {@value #MAX_HORIZONTAL_RADIUS} keeps two blocks
 * of margin; the codec range means a modpack gets a parse error rather than a corrupted lake.
 *
 * <p><b>26.3 shape.</b> {@code FeatureConfiguration} no longer exists: a {@code Feature} IS its own
 * configuration, so this record is carried by {@link OilLakeFeature} and its {@link #MAP_CODEC} is
 * inlined into that feature's codec — which is why it is a {@link MapCodec} rather than a
 * {@code Codec}. The field names are unchanged, so the JSON of the three deposits keeps every key it
 * had; only the wrapping {@code "config"} object is gone.
 */
public record OilLakeConfiguration(
		Holder<BlockStateProvider> fluid,
		Holder<BlockStateProvider> barrier,
		IntProvider horizontalRadius,
		IntProvider verticalRadius,
		IntProvider blobCount,
		BlockPredicate canPlaceFeature,
		BlockPredicate canReplaceWithAirOrFluid,
		BlockPredicate canReplaceWithBarrier) {

	/** Largest half-extent on X/Z a lake may claim — see the class doc for the derivation. */
	public static final int MAX_HORIZONTAL_RADIUS = 14;

	/** Smallest half-extent that still leaves room for a blob inside the grid (see the feature). */
	public static final int MIN_HORIZONTAL_RADIUS = 3;

	public static final MapCodec<OilLakeConfiguration> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BlockStateProvider.CODEC.fieldOf("fluid")
					.forGetter(OilLakeConfiguration::fluid),
			BlockStateProvider.CODEC.fieldOf("barrier")
					.forGetter(OilLakeConfiguration::barrier),
			IntProviders.codec(MIN_HORIZONTAL_RADIUS, MAX_HORIZONTAL_RADIUS).fieldOf("horizontal_radius")
					.forGetter(OilLakeConfiguration::horizontalRadius),
			IntProviders.codec(2, 10).fieldOf("vertical_radius")
					.forGetter(OilLakeConfiguration::verticalRadius),
			IntProviders.codec(1, 16).fieldOf("blob_count")
					.forGetter(OilLakeConfiguration::blobCount),
			BlockPredicate.CODEC.fieldOf("can_place_feature")
					.forGetter(OilLakeConfiguration::canPlaceFeature),
			BlockPredicate.CODEC.fieldOf("can_replace_with_air_or_fluid")
					.forGetter(OilLakeConfiguration::canReplaceWithAirOrFluid),
			BlockPredicate.CODEC.fieldOf("can_replace_with_barrier")
					.forGetter(OilLakeConfiguration::canReplaceWithBarrier))
			.apply(instance, OilLakeConfiguration::new));
}
