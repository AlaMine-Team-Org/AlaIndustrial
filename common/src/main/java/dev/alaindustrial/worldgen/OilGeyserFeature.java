package dev.alaindustrial.worldgen;

import dev.alaindustrial.Industrialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * {@code alaindustrial:oil_geyser} (MOD-248) as the game sees it: the per-line adapter between the
 * {@code Feature} API and {@link OilGeyserPlacer}, which holds the whole placement and is the same source
 * on every Minecraft line (MOD-704). This class only takes the call and hands over what it carries.
 *
 * <p>The adapter is what differs between the lines — the same way as for {@link OilLakeFeature}: a record
 * of its {@link OilGeyserConfiguration} whose {@code MapCodec} is registered on 26.3, one registered
 * instance taking a {@code FeaturePlaceContext} on 26.2.
 */
public final class OilGeyserFeature extends Feature<OilGeyserConfiguration> {

	/** Registry id of the feature type; the configured-feature JSON refers to the feature by this name. */
	public static final Identifier ID = Industrialization.id("oil_geyser");

	/** Stateless; one shared instance, registered by each loader. */
	public static final OilGeyserFeature INSTANCE = new OilGeyserFeature();

	private OilGeyserFeature() {
		super(OilGeyserConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<OilGeyserConfiguration> context) {
		OilGeyserConfiguration config = context.config();
		return OilGeyserPlacer.place(context.level(), context.random(), context.origin(), config, config.fluid(),
				config.barrier());
	}
}
