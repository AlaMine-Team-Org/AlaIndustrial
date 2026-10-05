package dev.alaindustrial.worldgen;

import dev.alaindustrial.Industrialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * {@code alaindustrial:oil_lake} (MOD-248) as the game sees it: the per-line adapter between the
 * {@code Feature} API and {@link OilLakePlacer}, which holds the whole placement and is the same source on
 * every Minecraft line (MOD-704). This class only takes the call and hands over what it carries.
 *
 * <p>The adapter is what differs between the lines. On 26.3 a {@code Feature} is an interface and IS its
 * own configuration: the feature is a record of its {@link OilLakeConfiguration}, the loaders register its
 * {@code MapCodec}, {@code place} takes the level, chunk generator, random source and origin as arguments,
 * and the configuration holds its state providers as {@code Holder}s. On 26.2 a {@code Feature} is a class
 * parameterised by its configuration: one stateless instance is registered, {@code place} takes a
 * {@code FeaturePlaceContext}, and the configuration holds its providers directly.
 */
public final class OilLakeFeature extends Feature<OilLakeConfiguration> {

	/** Registry id of the feature type; the configured-feature JSON refers to the feature by this name. */
	public static final Identifier ID = Industrialization.id("oil_lake");

	/**
	 * Stateless, so one shared instance is all either loader ever registers — Fabric eagerly,
	 * NeoForge through a {@code DeferredRegister}. Mirrors how {@link OilLakeFilter} is shared.
	 */
	public static final OilLakeFeature INSTANCE = new OilLakeFeature();

	private OilLakeFeature() {
		super(OilLakeConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<OilLakeConfiguration> context) {
		OilLakeConfiguration config = context.config();
		return OilLakePlacer.place(context.level(), context.random(), context.origin(), config, config.fluid(),
				config.barrier());
	}
}
