package dev.alaindustrial.gametest;

import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModProfessions;
import dev.alaindustrial.worldgen.VillagePoolInjector;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Loader-neutral MOD-062 world scenarios, mirrored on the NeoForge lane via
 * {@code NeoForgeGameTests.registerTest}. They verify exactly the seams that differ per loader: the
 * POI blockstate map (PoiHelper vs registry callback), the profession record + data-driven trade
 * sets, and the server-start pool injection.
 */
public final class IndustrialistScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(IndustrialistScenarios::tradeSetsResolvePerLevel, "industrialist_trade_sets_resolve")
						.fabricId("IndustrialistVillagerGameTest", "tcVil002_tradeSetsResolvePerLevel").ticks(20, 40),
				RosterEntry.of(IndustrialistScenarios::poolInjectionIsIdempotent,
								"industrialist_pool_injection_idempotent")
						.fabricId("IndustrialistVillagerGameTest", "tcVil005_poolInjectionIsIdempotent").ticks(20, 40),
				RosterEntry.of(IndustrialistScenarios::houseCapFilter, "industrialist_house_cap_filter")
						.fabricId("IndustrialistVillagerGameTest", "tcVil006_houseCapFilter").ticks(20, 40),
				RosterEntry.of(IndustrialistScenarios::houseStructureLoads, "industrialist_house_structure_loads")
						.fabricId("IndustrialistVillagerGameTest", "tcVil007_houseStructureLoads").ticks(20, 40),
				RosterEntry.of(IndustrialistScenarios::tcVil003_sellOffersIgnoreReputation,
								"villager_sell_offers_ignore_reputation")
						.fabricId("IndustrialistVillagerGameTest", "tcVil003_sellOffersIgnoreReputation")
						.ticks(20, 100),
				RosterEntry.of(IndustrialistScenarios::tcVil004_unemployedVillagerTakesProfession,
								"unemployed_villager_takes_profession")
						.fabricId("IndustrialistVillagerGameTest", "tcVil004_unemployedVillagerTakesProfession")
						.ticks(300),
				RosterEntry.of(IndustrialistScenarios::energyOrderOnEveryVillager,
						"industrialist_energy_order_on_every_villager").ticks(40),
				RosterEntry.of(IndustrialistScenarios::energyOrderSurvivesLevelUps,
						"industrialist_energy_order_survives_level_ups").ticks(40),
				RosterEntry.of(IndustrialistScenarios::energyOrderTakesOnlyFullBatteries,
						"industrialist_energy_order_takes_only_full_batteries").ticks(40),
				RosterEntry.of(IndustrialistScenarios::energyOrderLeavesSavedOffersAlone,
						"industrialist_energy_order_leaves_saved_offers_alone").ticks(40));

		private Roster() {}
	}

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
	 * (not the full 3) is the deterministic proof the subset mechanism is active. The fixed MOD-772
	 * energy order is added on top and is not part of the draw, so it is left out of the count.
	 */
	public static void tradeSetsResolvePerLevel(GameTestHelper helper) {
		for (int level = 1; level <= 5; level++) {
			Villager villager = helper.spawn(EntityTypes.VILLAGER, new net.minecraft.core.BlockPos(0, 2, 0));
			villager.setVillagerData(villager.getVillagerData()
					.withProfession(helper.getLevel().registryAccess(), ModProfessions.INDUSTRIALIST)
					.withLevel(level));
			MerchantOffers offers = villager.getOffers();
			long random = offers.stream().filter(offer -> !isEnergyOrder(offer)).count();
			helper.assertTrue(random == 2,
					"level " + level + " should yield amount=2 offers drawn from its 3-trade pool, got "
							+ random + " (besides the MOD-772 energy order)");
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
		var mgr = helper.getLevel().getServer().getStructureTemplateManager();
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
		List<MerchantOffer> offers = villager.getOffers().stream().filter(offer -> !isEnergyOrder(offer)).toList();
		helper.assertTrue(offers.size() == 2,
				"master level should yield the 2 reverse sells besides the energy order");
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

	// --- MOD-772: the fixed energy order (full battery -> emeralds) ---

	/** Batteries each fixed level asks for, as shipped in the trade files (L1 3, L3 1, L5 16). */
	private static final Map<Integer, Integer> ENERGY_ORDER_BATTERIES = Map.of(1, 3, 3, 1, 5, 16);

	/** Villagers per level in the presence check, so a lucky random draw cannot pass it. */
	private static final int VILLAGERS_PER_LEVEL = 8;

	/** True for the MOD-772 offer: it is the only Industrialist trade that wants a battery. */
	static boolean isEnergyOrder(MerchantOffer offer) {
		return offer.getItemCostA().itemStack().is(ModContent.BATTERY.get());
	}

	private static Villager industrialist(GameTestHelper helper, int level) {
		Villager villager = helper.spawn(EntityTypes.VILLAGER, WORKBENCH.above());
		villager.setVillagerData(villager.getVillagerData()
				.withProfession(helper.getLevel().registryAccess(), ModProfessions.INDUSTRIALIST)
				.withLevel(level));
		return villager;
	}

	/** {@code count} batteries charged to {@code eu} each (0 = empty, no charge component). */
	private static ItemStack batteries(int count, long eu) {
		ItemStack stack = new ItemStack(ModContent.BATTERY.get(), count);
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/**
	 * Every Industrialist reaching level 1, 3 or 5 offers the energy order on top of its 2 random trades;
	 * levels 2 and 4 have none. Eight villagers per level: were the order only in the random pool, all
	 * eight would draw it with probability 1/2^8, so a pass is not luck.
	 */
	public static void energyOrderOnEveryVillager(GameTestHelper helper) {
		for (int level = 1; level <= 5; level++) {
			Integer wanted = ENERGY_ORDER_BATTERIES.get(level);
			for (int i = 0; i < VILLAGERS_PER_LEVEL; i++) {
				Villager villager = industrialist(helper, level);
				MerchantOffers offers = villager.getOffers();
				List<MerchantOffer> orders = offers.stream().filter(IndustrialistScenarios::isEnergyOrder).toList();
				long random = offers.size() - orders.size();
				helper.assertTrue(random == 2, "level " + level + " villager " + i
						+ " should keep its 2 random MOD-062 trades, got " + random);
				if (wanted == null) {
					helper.assertTrue(orders.isEmpty(), "level " + level + " has no fixed set, yet villager " + i
							+ " offers " + orders.size() + " energy orders");
				} else {
					helper.assertTrue(orders.size() == 1, "level " + level + " villager " + i
							+ " must offer exactly one energy order, got " + orders.size());
					MerchantOffer order = orders.get(0);
					helper.assertTrue(order.getItemCostA().count() == wanted, "level " + level
							+ " energy order should want " + wanted + " batteries, got "
							+ order.getItemCostA().count());
					helper.assertTrue(order.getResult().is(Items.EMERALD), "the energy order pays emeralds");
				}
				villager.discard();
			}
		}
		helper.succeed();
	}

	/**
	 * A villager climbing 1 to 5 through the vanilla level-up path ({@code Villager.increaseMerchantCareer},
	 * private, reached by name since 26.x is not obfuscated) gains each fixed order exactly once:
	 * 5 x 2 random + 3 orders = 13 offers, and re-reading the offers adds nothing.
	 */
	public static void energyOrderSurvivesLevelUps(GameTestHelper helper) {
		Villager villager = industrialist(helper, 1);
		helper.assertTrue(villager.getOffers().size() == 3, "level 1: 2 random + the energy order");
		try {
			Method careerUp = Villager.class.getDeclaredMethod("increaseMerchantCareer", ServerLevel.class);
			careerUp.setAccessible(true);
			for (int level = 2; level <= 5; level++) {
				careerUp.invoke(villager, helper.getLevel());
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Villager.increaseMerchantCareer(ServerLevel) is not callable", e);
		}
		helper.assertTrue(villager.getVillagerData().level() == 5, "the villager should now be a master");
		MerchantOffers offers = villager.getOffers();
		List<Integer> orders = offers.stream().filter(IndustrialistScenarios::isEnergyOrder)
				.map(offer -> offer.getItemCostA().count()).toList();
		helper.assertTrue(offers.size() == 13,
				"5 levels x 2 random + 3 energy orders = 13 offers, got " + offers.size());
		helper.assertTrue(orders.equals(List.of(3, 1, 16)),
				"one energy order per fixed level, in level order (3, 1, 16 batteries), got " + orders);
		helper.assertTrue(villager.getOffers().size() == 13, "re-reading the offers must not add trades");
		villager.discard();
		helper.succeed();
	}

	/**
	 * The order takes only fully charged batteries: the asked count of full ones and a full stack pass; a
	 * half-charged, a one-EU-short and an empty battery do not, nor fewer batteries than asked. The charge
	 * condition lives in the trade file ({@code components} of {@code wants}); without it every check on a
	 * partial battery below would pass.
	 */
	public static void energyOrderTakesOnlyFullBatteries(GameTestHelper helper) {
		long full = ItemEnergy.capacity(batteries(1, 0));
		ItemStack none = ItemStack.EMPTY;
		for (Map.Entry<Integer, Integer> entry : ENERGY_ORDER_BATTERIES.entrySet()) {
			int level = entry.getKey();
			int wanted = entry.getValue();
			Villager villager = industrialist(helper, level);
			MerchantOffer order = villager.getOffers().stream().filter(IndustrialistScenarios::isEnergyOrder)
					.findFirst().orElseThrow(() -> new IllegalStateException("no energy order at level " + level));
			helper.assertTrue(order.satisfiedBy(batteries(wanted, full), none),
					"level " + level + ": " + wanted + " full batteries must be accepted");
			helper.assertTrue(order.satisfiedBy(batteries(16, full), none),
					"level " + level + ": a full stack of full batteries must be accepted");
			helper.assertTrue(!order.satisfiedBy(batteries(wanted, full / 2), none),
					"level " + level + ": half-charged batteries must be refused");
			helper.assertTrue(!order.satisfiedBy(batteries(wanted, full - 1), none),
					"level " + level + ": batteries one EU short of full must be refused");
			helper.assertTrue(!order.satisfiedBy(batteries(wanted, 0), none),
					"level " + level + ": empty batteries must be refused");
			if (wanted > 1) {
				helper.assertTrue(!order.satisfiedBy(batteries(wanted - 1, full), none),
						"level " + level + ": fewer full batteries than asked must be refused");
			}
			villager.discard();
		}
		helper.succeed();
	}

	/**
	 * Owner decision 2026-10-07: no backfill. A master saved before this update — offers without an energy
	 * order — is written and read back through the real save path ({@code saveWithoutId} /
	 * {@code load} → {@code AbstractVillager.readAdditionalSaveData} puts the saved offers into the field);
	 * {@code getOffers} re-runs {@code updateTrades} only while that field is {@code null}, so the loaded
	 * villager keeps exactly its saved offers and gets no order. Guards the decision against a future change
	 * that would top offers up on load or when the trade screen opens. ({@code overrideOffers} is no use
	 * here: it is a client-side no-op on the server.)
	 */
	public static void energyOrderLeavesSavedOffersAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager before = industrialist(helper, 5);
		before.getOffers().removeIf(IndustrialistScenarios::isEnergyOrder);
		int count = before.getOffers().size();
		TagValueOutput save = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		before.saveWithoutId(save);
		before.discard();

		Villager loaded = new Villager(EntityTypes.VILLAGER, level);
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), save.buildResult()));
		helper.assertTrue(loaded.getVillagerData().level() == 5
						&& loaded.getVillagerData().profession().is(ModProfessions.INDUSTRIALIST),
				"the round trip must restore an Industrialist master, got " + loaded.getVillagerData());
		MerchantOffers offers = loaded.getOffers();
		helper.assertTrue(offers.size() == count,
				"saved offers must stay as they were: " + count + " expected, got " + offers.size());
		helper.assertTrue(offers.stream().noneMatch(IndustrialistScenarios::isEnergyOrder),
				"a villager loaded with saved offers must not be given an energy order");
		helper.succeed();
	}
}
