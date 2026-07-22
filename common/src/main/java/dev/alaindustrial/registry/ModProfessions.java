package dev.alaindustrial.registry;

import com.google.common.collect.ImmutableSet;
import dev.alaindustrial.Industrialization;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;

/**
 * Loader-neutral core of the Industrialist villager profession (MOD-062): the registry keys and the
 * profession record factory. Registration itself is loader-specific and lives outside common —
 * Fabric registers eagerly in {@code IndustrializationFabric#registerVillagerProfession()} (PoiHelper +
 * Registry.register), NeoForge via the two {@code DeferredRegister}s in {@code ModProfessionsNeoForge}
 * (built-in registries are frozen before NeoForge mod init, so nothing here may register eagerly —
 * ResourceKey constants are safe, they don't touch registries).
 *
 * <p>API verified against the 26.2 sources: {@code VillagerProfession} is a record in
 * {@code net.minecraft.world.entity.npc.villager}; its trades are data-driven — the
 * {@code tradeSetsByLevel} map points at datapack {@code trade_set} entries
 * ({@code data/alaindustrial/trade_set/industrialist/level_N.json}), resolved per level in
 * {@code AbstractVillager.addOffersFromTradeSet}. The display name follows the vanilla key scheme
 * {@code entity.<namespace>.villager.<path>}.
 */
public final class ModProfessions {
	private ModProfessions() {
	}

	public static final ResourceKey<PoiType> INDUSTRIALIST_POI =
			ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Industrialization.id("industrialist"));

	public static final ResourceKey<VillagerProfession> INDUSTRIALIST =
			ResourceKey.create(Registries.VILLAGER_PROFESSION, Industrialization.id("industrialist"));

	// Vanilla job-site POI shape: one villager per workbench, matched at exact range.
	public static final int POI_MAX_TICKETS = 1;
	public static final int POI_VALID_RANGE = 1;

	public static final ResourceKey<TradeSet> INDUSTRIALIST_LEVEL_1 = tradeSet("industrialist/level_1");
	public static final ResourceKey<TradeSet> INDUSTRIALIST_LEVEL_2 = tradeSet("industrialist/level_2");
	public static final ResourceKey<TradeSet> INDUSTRIALIST_LEVEL_3 = tradeSet("industrialist/level_3");
	public static final ResourceKey<TradeSet> INDUSTRIALIST_LEVEL_4 = tradeSet("industrialist/level_4");
	public static final ResourceKey<TradeSet> INDUSTRIALIST_LEVEL_5 = tradeSet("industrialist/level_5");

	private static ResourceKey<TradeSet> tradeSet(String path) {
		return ResourceKey.create(Registries.TRADE_SET, Industrialization.id(path));
	}

	/**
	 * Builds the profession record both loaders register under {@link #INDUSTRIALIST}. Mirrors the
	 * vanilla single-POI professions: held/acquirable predicates match only our POI, no requested
	 * items, no secondary POI, the toolsmith work sound (decision 2026-07-21 — reuse, not copy).
	 */
	public static VillagerProfession createIndustrialist() {
		return new VillagerProfession(
				Component.translatable("entity." + INDUSTRIALIST.identifier().getNamespace()
						+ ".villager." + INDUSTRIALIST.identifier().getPath()),
				holder -> holder.is(INDUSTRIALIST_POI),
				holder -> holder.is(INDUSTRIALIST_POI),
				ImmutableSet.of(),
				ImmutableSet.of(),
				SoundEvents.VILLAGER_WORK_TOOLSMITH,
				Int2ObjectMap.ofEntries(
						Int2ObjectMap.entry(1, INDUSTRIALIST_LEVEL_1),
						Int2ObjectMap.entry(2, INDUSTRIALIST_LEVEL_2),
						Int2ObjectMap.entry(3, INDUSTRIALIST_LEVEL_3),
						Int2ObjectMap.entry(4, INDUSTRIALIST_LEVEL_4),
						Int2ObjectMap.entry(5, INDUSTRIALIST_LEVEL_5)));
	}
}
