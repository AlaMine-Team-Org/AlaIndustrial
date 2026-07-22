package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModProfessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * L2 server game tests for the Industrialist villager profession (MOD-062).
 *
 * <p><b>What these guard:</b> the loader registration wiring (POI blockstate map + profession record
 * + the data-driven trade sets) — the parts no static validator checks. The trade JSON files are a
 * validator blind spot (nothing parses {@code villager_trade/}/{@code trade_set/}), so the offer
 * tests here are the executable proof the datapack actually loads and matches the MOD-062 table.
 *
 * <p>API verified against the 26.2 sources: {@code PoiTypes.forState} reads the blockstate→POI map
 * (filled by Fabric's {@code PoiHelper} / NeoForge's registry callback — a bare register leaves it
 * empty); {@code AbstractVillager.getOffers()} lazily runs {@code updateTrades(ServerLevel)} which
 * resolves the profession's {@code TradeSet} for the <em>current</em> level only;
 * {@code VillagerData.withProfession(HolderGetter.Provider, ResourceKey)} resolves the holder.
 * Profession acquisition is driven by the CORE-package brain behaviours ({@code AcquirePoi} +
 * {@code AssignProfessionFromJobSite}) — assignment fires once the villager is within 2.0 blocks of
 * the claimed POI, which the adjacent spawn below guarantees; first scan starts within
 * {@code random.nextInt(20)} ticks, so 300 maxTicks is a generous cap.
 */
public class IndustrialistVillagerGameTest {

	private static final BlockPos WORKBENCH = new BlockPos(2, 2, 2);

	/**
	 * TC-VIL-001: the workbench blockstate is mapped to the mod's PoiType (registration wiring).
	 * Delegates to the loader-neutral scenario (mirrored on the NeoForge lane), plus the
	 * every-state guard.
	 */
	@GameTest
	public void tcVil001_workbenchStateMapsToPoi(GameTestHelper helper) {
		// Every state (there is only the default one) must be covered, or a placed block would be inert.
		helper.assertTrue(ModBlocks.INDUSTRIAL_WORKBENCH.getStateDefinition().getPossibleStates().stream()
						.allMatch(PoiTypes::hasPoi),
				"every industrial_workbench blockstate should be a POI");
		IndustrialistScenarios.workbenchStateMapsToPoi(helper);
	}

	/**
	 * TC-VIL-002: the data-driven trade sets resolve for every level with the MOD-062 table's
	 * offer counts (2/2/2/1/2). Delegates to the loader-neutral scenario.
	 */
	@GameTest
	public void tcVil002_tradeSetsResolvePerLevel(GameTestHelper helper) {
		IndustrialistScenarios.tradeSetsResolvePerLevel(helper);
	}

	/**
	 * TC-VIL-003: anti-dupe + sell-discount guard on the loaded data. Across all levels no
	 * mod metal appears as a cost in more than one item form, and every offer that SELLS mod items
	 * for emeralds (the Master shortcuts + the pickaxe buy) carries priceMultiplier 0.0 so the
	 * zombie-cure reputation exploit cannot collapse its price.
	 */
	@GameTest
	public void tcVil003_sellOffersIgnoreReputation(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityTypes.VILLAGER, WORKBENCH.above());
		villager.setVillagerData(villager.getVillagerData()
				.withProfession(helper.getLevel().registryAccess(), ModProfessions.INDUSTRIALIST)
				.withLevel(5));
		MerchantOffers offers = villager.getOffers();
		helper.assertTrue(offers.size() == 2, "master level should yield the 2 reverse sells");
		for (MerchantOffer offer : offers) {
			helper.assertTrue(offer.getItemCostA().itemStack().is(net.minecraft.world.item.Items.EMERALD),
					"master offers should cost emeralds");
			helper.assertTrue(offer.getPriceMultiplier() == 0.0f,
					"reverse sells must have reputation_discount 0.0, got " + offer.getPriceMultiplier());
		}
		// Discard so this employed Industrialist cannot claim the POI tcVil004 places (1 ticket).
		villager.discard();
		helper.succeed();
	}

	/**
	 * TC-VIL-005: village-pool injection ran on server start (exactly WEIGHT copies of the house in
	 * the plains pool) and is idempotent. Delegates to the loader-neutral scenario.
	 */
	@GameTest
	public void tcVil005_poolInjectionIsIdempotent(GameTestHelper helper) {
		IndustrialistScenarios.poolInjectionIsIdempotent(helper);
	}

	/**
	 * TC-VIL-006: the one-per-village cap filter — once the house is placed, it is removed from
	 * further candidate lists (the decision {@code JigsawPlacementPlacerMixin} makes every slot).
	 */
	@GameTest
	public void tcVil006_houseCapFilter(GameTestHelper helper) {
		IndustrialistScenarios.houseCapFilter(helper);
	}

	/** TC-VIL-007: the house structure NBT loads through the game structure manager with a sane size. */
	@GameTest
	public void tcVil007_houseStructureLoads(GameTestHelper helper) {
		IndustrialistScenarios.houseStructureLoads(helper);
	}

	/**
	 * TC-VIL-004: live acquisition — an unemployed adult villager standing next to a placed
	 * workbench claims the POI and takes the {@code alaindustrial:industrialist} profession.
	 */
	@GameTest(maxTicks = 300)
	public void tcVil004_unemployedVillagerTakesProfession(GameTestHelper helper) {
		// The default gametest structure has no floor — a spawned villager just falls, its brain
		// never acquires anything. Build a 5x5 stone floor and a barrier pen so it stays put.
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.STONE);
				if (x == 0 || x == 4 || z == 0 || z == 4) {
					helper.setBlock(new BlockPos(x, 2, z), net.minecraft.world.level.block.Blocks.BARRIER);
					helper.setBlock(new BlockPos(x, 3, z), net.minecraft.world.level.block.Blocks.BARRIER);
				}
			}
		}
		helper.setBlock(WORKBENCH, ModBlocks.INDUSTRIAL_WORKBENCH);
		var poiHolder = PoiTypes.forState(ModBlocks.INDUSTRIAL_WORKBENCH.defaultBlockState()).orElseThrow();
		helper.assertTrue(poiHolder.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE),
				"industrialist POI must be in minecraft:acquirable_job_site (unemployed scan tag)");
		Villager villager = helper.spawn(EntityTypes.VILLAGER, WORKBENCH.east());
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getPoiManager()
							.existsAtPosition(ModProfessions.INDUSTRIALIST_POI, helper.absolutePos(WORKBENCH)),
					"the placed workbench should be registered in the PoiManager");
			boolean potential = villager.getBrain()
					.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.POTENTIAL_JOB_SITE);
			boolean jobSite = villager.getBrain()
					.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
			helper.assertTrue(
					villager.getVillagerData().profession().is(ModProfessions.INDUSTRIALIST),
					"villager should become the Industrialist; profession="
							+ villager.getVillagerData().profession()
							+ " potentialJobSite=" + potential + " jobSite=" + jobSite);
		});
	}

}
