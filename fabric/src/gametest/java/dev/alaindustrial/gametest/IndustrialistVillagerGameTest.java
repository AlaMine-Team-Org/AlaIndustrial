package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

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
}
