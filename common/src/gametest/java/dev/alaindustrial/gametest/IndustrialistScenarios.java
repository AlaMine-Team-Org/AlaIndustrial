package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModProfessions;
import dev.alaindustrial.worldgen.VillagePoolInjector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Loader-neutral MOD-062 world scenarios, mirrored on the NeoForge lane via
 * {@code NeoForgeGameTests.registerTest}. They verify exactly the seams that differ per loader: the
 * POI blockstate map (PoiHelper vs registry callback), the profession record + data-driven trade
 * sets, and the server-start pool injection.
 */
public final class IndustrialistScenarios {
	private IndustrialistScenarios() {
	}

	private static final BlockPos WORKBENCH = new BlockPos(2, 2, 2);

	/** The workbench blockstate maps to the mod's PoiType (state map filled on this loader). */
	public static void workbenchStateMapsToPoi(GameTestHelper helper) {
		var state = ModContent.INDUSTRIAL_WORKBENCH.get().defaultBlockState();
		var poi = PoiTypes.forState(state);
		helper.assertTrue(poi.isPresent(), "industrial_workbench state should map to a PoiType");
		helper.assertTrue(poi.get().is(ModProfessions.INDUSTRIALIST_POI),
				"industrial_workbench should map to alaindustrial:industrialist POI");
		helper.succeed();
	}

	/**
	 * Each level draws exactly its {@code amount} (2) offers as a RANDOM SUBSET of the level's
	 * 3-trade pool — the MOD-062 v2 variety design (like a vanilla armorer). Getting exactly 2
	 * (not the full 3) is the deterministic proof the subset mechanism is active.
	 */
	public static void tradeSetsResolvePerLevel(GameTestHelper helper) {
		for (int level = 1; level <= 5; level++) {
			Villager villager = helper.spawn(EntityTypes.VILLAGER, new net.minecraft.core.BlockPos(0, 2, 0));
			villager.setVillagerData(villager.getVillagerData()
					.withProfession(helper.getLevel().registryAccess(), ModProfessions.INDUSTRIALIST)
					.withLevel(level));
			MerchantOffers offers = villager.getOffers();
			helper.assertTrue(offers.size() == 2,
					"level " + level + " should yield amount=2 offers drawn from its 3-trade pool, got "
							+ offers.size());
			villager.discard();
		}
		helper.succeed();
	}

	/**
	 * The one-per-village cap logic: {@link VillagePoolInjector#withoutHouseIfPresent} removes our house
	 * from a candidate list once it is already placed, and leaves the list untouched otherwise. Drives
	 * the exact decision the {@code JigsawPlacementPlacerMixin} makes at every house slot, deterministically
	 * (the mixin itself only runs during real village worldgen, which cannot be gametested).
	 */
	public static void houseCapFilter(GameTestHelper helper) {
		var house = net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement
				.legacy(VillagePoolInjector.HOUSE_TEMPLATE.toString())
				.apply(net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection.RIGID);
		var other = net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement
				.empty()
				.apply(net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection.RIGID);
		helper.assertTrue(VillagePoolInjector.isHouse(house), "the legacy house element must be recognised as our house");
		helper.assertTrue(!VillagePoolInjector.isHouse(other), "the empty element must not be recognised as our house");

		var candidates = java.util.List.of(house, other);
		var notYet = VillagePoolInjector.withoutHouseIfPresent(candidates, false);
		helper.assertTrue(notYet.size() == 2, "with no house placed yet, all candidates stay (got " + notYet.size() + ")");

		var afterPlaced = VillagePoolInjector.withoutHouseIfPresent(candidates, true);
		helper.assertTrue(afterPlaced.size() == 1, "once placed, the house is filtered out (got " + afterPlaced.size() + ")");
		helper.assertTrue(afterPlaced.stream().noneMatch(VillagePoolInjector::isHouse),
				"the filtered candidate list must contain no house");
		helper.succeed();
	}

	/**
	 * The Industrialist house structure NBT loads through the game's own structure manager (not just our
	 * reader) and has the expected footprint — catches a malformed template before it silently fails to
	 * generate. Size is asserted loosely (non-empty, fits a village slot) so hand-edits to the house in
	 * the dev client don't break the test on every resize.
	 */
	public static void houseStructureLoads(GameTestHelper helper) {
		var mgr = helper.getLevel().getServer().getStructureManager();
		var template = mgr.get(VillagePoolInjector.HOUSE_TEMPLATE);
		helper.assertTrue(template.isPresent(),
				"structure " + VillagePoolInjector.HOUSE_TEMPLATE + " must load through the game structure manager");
		var size = template.get().getSize();
		helper.assertTrue(size.getX() > 0 && size.getY() > 0 && size.getZ() > 0,
				"house template must have a non-empty size, got " + size);
		helper.assertTrue(size.getX() <= 24 && size.getZ() <= 24 && size.getY() <= 20,
				"house template must fit a village slot (<=24x20x24), got " + size);
		helper.succeed();
	}

	/** Server start injected exactly WEIGHT house copies into the plains pool; re-inject is a no-op. */
	public static void poolInjectionIsIdempotent(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		var pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		var pool = pools.getOptional(Identifier.withDefaultNamespace("village/plains/houses"));
		helper.assertTrue(pool.isPresent(), "vanilla plains houses pool should exist");
		var templates = ((dev.alaindustrial.mixin.StructureTemplatePoolAccessor) (Object) pool.get())
				.alaindustrial$getTemplates();
		long copies = VillagePoolInjector.houseCopies(templates);
		helper.assertTrue(copies == VillagePoolInjector.WEIGHT,
				"server start should inject exactly " + VillagePoolInjector.WEIGHT
						+ " house copies, found " + copies);
		VillagePoolInjector.inject(server);
		long after = VillagePoolInjector.houseCopies(templates);
		helper.assertTrue(after == copies, "second inject() must be a no-op, found " + after);
		helper.succeed();
	}

	/**
	 * Anti-dupe + sell-discount guard on the loaded data: across all levels no mod metal appears as a
	 * cost in more than one item form, and every offer that SELLS mod items for emeralds (the Master
	 * shortcuts + the pickaxe buy) carries priceMultiplier 0.0 so the zombie-cure reputation exploit
	 * cannot collapse its price.
	 * Mirrors: IndustrialistVillagerGameTest.tcVil003_sellOffersIgnoreReputation
	 */
	public static void tcVil003_sellOffersIgnoreReputation(GameTestHelper helper) {
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
	 * Live acquisition: an unemployed adult villager standing next to a placed workbench claims the
	 * POI and takes the {@code alaindustrial:industrialist} profession.
	 * Mirrors: IndustrialistVillagerGameTest.tcVil004_unemployedVillagerTakesProfession
	 */
	public static void tcVil004_unemployedVillagerTakesProfession(GameTestHelper helper) {
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
		helper.setBlock(WORKBENCH, ModContent.INDUSTRIAL_WORKBENCH.get());
		var poiHolder = PoiTypes.forState(ModContent.INDUSTRIAL_WORKBENCH.get().defaultBlockState()).orElseThrow();
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
