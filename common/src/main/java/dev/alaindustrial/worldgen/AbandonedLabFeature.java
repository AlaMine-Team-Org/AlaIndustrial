package dev.alaindustrial.worldgen;

import dev.alaindustrial.Industrialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * {@code alaindustrial:abandoned_lab} (MOD-513) as the game sees it: the per-line adapter between the
 * {@code Feature} API and {@link AbandonedLabPlacer}, which holds the whole placement and is the same
 * source on every Minecraft line (MOD-704). This class only takes the call and hands over what it needs.
 *
 * <p>The adapter is what differs between the lines — the same way as for {@link OilLakeFeature}, with no
 * configuration (an empty record and a unit codec on 26.3, {@code NoneFeatureConfiguration} on 26.2) —
 * plus the server's structure-template manager, {@code getStructureTemplateManager()} on 26.3 and
 * {@code getStructureManager()} on 26.2.
 */
public final class AbandonedLabFeature extends Feature<NoneFeatureConfiguration> {

	/** Registry id of the feature type; the configured-feature JSON refers to the feature by this name. */
	public static final Identifier ID = Industrialization.id("abandoned_lab");

	/** Stateless; one shared instance, registered by each loader. */
	public static final AbandonedLabFeature INSTANCE = new AbandonedLabFeature();

	private AbandonedLabFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		return AbandonedLabPlacer.place(level, context.random(), context.origin(),
				level.getLevel().getServer().getStructureManager());
	}
}
