package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.MobRepellerBlockEntity;
import dev.alaindustrial.compat.MobSpawns;
import dev.alaindustrial.entity.MobRepellerField;
import dev.alaindustrial.entity.OilSlime;
import dev.alaindustrial.entity.OilSlimeConversion;
import dev.alaindustrial.entity.OilSlimeSpawning;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModMobs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The oil slime (MOD-767, docs/mobs/oil_slime.md), on both gametest lanes.
 *
 * <p>The soak is driven through {@link OilSlimeConversion#soakTick} with an explicit clock: a whole
 * twenty-second soak runs inside one call, so no knob has to be held across ticks. One scenario waits real
 * ticks instead, to prove crude oil's {@code entityInside} really feeds the count.
 */
public final class OilSlimeScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(OilSlimeScenarios::isRegisteredWithAttributesAndSpawns, "oil_slime_is_registered"),
				RosterEntry.of(OilSlimeScenarios::slimeInOilTurnsKeepingItsSize, "oil_slime_soak_turns_slime"),
				RosterEntry.of(OilSlimeScenarios::leavingTheOilStartsTheSoakAgain, "oil_slime_soak_restarts"),
				RosterEntry.of(OilSlimeScenarios::knobOffLeavesSlimesAlone, "oil_slime_knob_off_no_turn"),
				RosterEntry.of(OilSlimeScenarios::onlyTheVanillaSlimeTurns, "oil_slime_only_vanilla_slime_turns"),
				RosterEntry.of(OilSlimeScenarios::oilFeedsTheSoakEveryTick, "oil_slime_oil_feeds_the_soak")
						.ticks(40),
				RosterEntry.of(OilSlimeScenarios::burningSlimeDropsNoRubber, "oil_slime_burning_drops_nothing"),
				RosterEntry.of(OilSlimeScenarios::fireSetsItAlight, "oil_slime_fire_sets_it_alight"),
				RosterEntry.of(OilSlimeScenarios::spawnRuleNeedsOilDarkAndRoom, "oil_slime_spawn_rule")
						.ticks(60),
				RosterEntry.of(OilSlimeScenarios::repellerPushesIt, "oil_slime_repeller_pushes_it"));

		private Roster() {}
	}

	private OilSlimeScenarios() {
	}

	private static final BlockPos POOL = new BlockPos(2, 2, 2);

	/** A one-cell source of crude oil at {@link #POOL}, walled in two blocks high so nothing runs away. */
	private static void oilPool(GameTestHelper helper) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(POOL.offset(dx, -1, dz), Blocks.STONE);
				if (dx != 0 || dz != 0) {
					helper.setBlock(POOL.offset(dx, 0, dz), Blocks.STONE);
					helper.setBlock(POOL.offset(dx, 1, dz), Blocks.STONE);
				}
			}
		}
		helper.setBlock(POOL, ModContent.OIL_BLOCK.get());
	}

	private static Slime slime(GameTestHelper helper, int size) {
		Slime slime = helper.spawnWithNoFreeWill(EntityTypes.SLIME, POOL);
		slime.setSize(size, true);
		return slime;
	}

	/** Runs {@code ticks} soak ticks from game time {@code from}; returns the oil slime it turned into, or null. */
	private static OilSlime soak(ServerLevel level, Slime slime, long from, int ticks) {
		OilSlime turned = null;
		for (int t = 0; t < ticks && turned == null; t++) {
			turned = OilSlimeConversion.soakTick(level, slime, from + t);
		}
		return turned;
	}

	/**
	 * @implements MOD-767-OSL01 — the type, its default attributes, its natural spawn in an Overworld biome and
	 *     its spawn egg exist on this loader
	 */
	public static void isRegisteredWithAttributesAndSpawns(GameTestHelper helper) {
		EntityType<OilSlime> type = ModMobs.OIL_SLIME.type();
		helper.assertTrue(DefaultAttributes.hasSupplier(type), "the oil slime has no default attributes");
		helper.assertTrue(type.getCategory() == MobCategory.MONSTER, "the oil slime must be a monster");
		List<EntityType<?>> monsters = MobSpawns.typesAt(helper.getLevel(), MobCategory.MONSTER,
				helper.absolutePos(POOL));
		helper.assertTrue(monsters.contains(type),
				"the Overworld biome under the test does not list the oil slime among its monsters: " + monsters);
		helper.assertTrue(ModContent.OIL_SLIME_SPAWN_EGG.get() != null, "the spawn egg is missing");
		helper.succeed();
	}

	/**
	 * @implements MOD-767-OSL02 — a slime that stands in oil for the whole soak turns into an oil slime of the
	 *     same size with full health, and not one tick earlier
	 */
	public static void slimeInOilTurnsKeepingItsSize(GameTestHelper helper) {
		oilPool(helper);
		Slime slime = slime(helper, 2);
		slime.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 2.0F);
		int needed = OilSlimeConversion.soakTicks();
		ServerLevel level = helper.getLevel();
		long start = level.getGameTime() + 1000;
		if (soak(level, slime, start, needed - 1) != null) {
			helper.fail("the slime turned before the soak was full (" + needed + " ticks)");
		}
		OilSlime turned = OilSlimeConversion.soakTick(level, slime, start + needed - 1);
		if (turned == null) {
			helper.fail("a slime that soaked " + needed + " ticks did not turn");
		}
		helper.assertTrue(turned.getSize() == 2, "the oil slime must keep the size 2, got " + turned.getSize());
		helper.assertTrue(turned.getHealth() == turned.getMaxHealth(), "the oil slime must come out at full health");
		helper.assertTrue(slime.isRemoved(), "the slime that turned is still in the world");
		helper.succeed();
	}

	/** @implements MOD-767-OSL03 — a slime out of the oil for longer than the grace starts the soak from zero */
	public static void leavingTheOilStartsTheSoakAgain(GameTestHelper helper) {
		oilPool(helper);
		Slime slime = slime(helper, 1);
		ServerLevel level = helper.getLevel();
		int needed = OilSlimeConversion.soakTicks();
		long start = level.getGameTime() + 1000;
		soak(level, slime, start, needed - 1);
		long back = start + needed - 1 + 21;
		if (OilSlimeConversion.soakTick(level, slime, back) != null) {
			helper.fail("a slime that left the oil for 21 ticks kept its soak and turned");
		}
		helper.assertTrue(OilSlimeConversion.soakedTicks(slime) == 1,
				"the soak must restart at 1, got " + OilSlimeConversion.soakedTicks(slime));
		helper.succeed();
	}

	/** @implements MOD-767-OSL04 — with oilSlimeConversion off a slime in oil stays a slime */
	public static void knobOffLeavesSlimesAlone(GameTestHelper helper) {
		oilPool(helper);
		Slime slime = slime(helper, 1);
		ServerLevel level = helper.getLevel();
		try (ConfigOverrides o = ConfigOverrides.sync().set("oilSlimeConversion", false)) {
			if (soak(level, slime, level.getGameTime() + 1000, OilSlimeConversion.soakTicks() * 2) != null) {
				helper.fail("a slime turned while oilSlimeConversion was off");
			}
		}
		helper.assertTrue(!slime.isRemoved(), "the slime vanished while conversion was off");
		helper.succeed();
	}

	/** @implements MOD-767-OSL05 — an oil slime soaking in oil stays as it is (only minecraft:slime turns) */
	public static void onlyTheVanillaSlimeTurns(GameTestHelper helper) {
		oilPool(helper);
		OilSlime oil = helper.spawnWithNoFreeWill(ModMobs.OIL_SLIME.type(), POOL);
		ServerLevel level = helper.getLevel();
		if (soak(level, oil, level.getGameTime() + 1000, OilSlimeConversion.soakTicks() * 2) != null) {
			helper.fail("an oil slime turned again");
		}
		helper.assertTrue(!oil.isRemoved(), "the oil slime vanished");
		helper.succeed();
	}

	/**
	 * @implements MOD-767-OSL06 — a slime standing in a real crude oil source is counted by the oil itself every
	 *     tick (the seat is crude oil's entityInside), and it does not drown while it soaks
	 */
	public static void oilFeedsTheSoakEveryTick(GameTestHelper helper) {
		oilPool(helper);
		// A live slime, not a frozen one: the block-effect pass that calls entityInside runs on a moving mob.
		Slime slime = helper.spawn(EntityTypes.SLIME, POOL);
		slime.setSize(1, true);
		helper.runAfterDelay(20, () -> {
			int soaked = OilSlimeConversion.soakedTicks(slime);
			helper.assertTrue(soaked >= 10, "after 20 ticks in oil the soak counted only " + soaked + " ticks");
			helper.assertTrue(slime.getAirSupply() == slime.getMaxAirSupply(),
					"a soaking slime lost air: " + slime.getAirSupply());
			helper.succeed();
		});
	}

	/**
	 * @implements MOD-767-OSL07 — a small oil slime that dies burning drops nothing; the same slime unburnt
	 *     drops raw rubber (over many kills, so the 0–1 roll cannot hide it)
	 */
	public static void burningSlimeDropsNoRubber(GameTestHelper helper) {
		int burntRubber = killSmall(helper, 24, true);
		helper.assertTrue(burntRubber == 0, "burning oil slimes dropped " + burntRubber + " raw rubber");
		int rubber = killSmall(helper, 24, false);
		helper.assertTrue(rubber > 0, "24 small oil slimes killed unburnt dropped no raw rubber at all");
		helper.succeed();
	}

	/** Kills {@code count} small oil slimes (burning or not) and returns the raw rubber they dropped. */
	private static int killSmall(GameTestHelper helper, int count, boolean burning) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(2, 2, 2);
		AABB area = new AABB(helper.absolutePos(at)).inflate(3.0);
		for (ItemEntity old : level.getEntitiesOfClass(ItemEntity.class, area)) {
			old.discard();
		}
		for (int i = 0; i < count; i++) {
			OilSlime oil = helper.spawnWithNoFreeWill(ModMobs.OIL_SLIME.type(), at);
			oil.setSize(1, true);
			if (burning) {
				oil.setRemainingFireTicks(200);
			}
			oil.kill(level);
		}
		int rubber = 0;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
			if (item.getItem().is(ModContent.RAW_RUBBER.get())) {
				rubber += item.getItem().getCount();
			}
			item.discard();
		}
		return rubber;
	}

	/** @implements MOD-767-OSL08 — any fire damage, a magma block's included, sets an oil slime alight */
	public static void fireSetsItAlight(GameTestHelper helper) {
		OilSlime oil = helper.spawnWithNoFreeWill(ModMobs.OIL_SLIME.type(), POOL);
		oil.setSize(4, true);
		ServerLevel level = helper.getLevel();
		oil.hurtServer(level, level.damageSources().hotFloor(), 1.0F);
		helper.assertTrue(oil.isOnFire(), "a hot floor did not set the oil slime alight");
		Slime plain = helper.spawnWithNoFreeWill(EntityTypes.SLIME, POOL.east());
		plain.setSize(4, true);
		plain.hurtServer(level, level.damageSources().hotFloor(), 1.0F);
		helper.assertTrue(!plain.isOnFire(), "an ordinary slime must not catch fire from a hot floor");
		helper.succeed();
	}

	/**
	 * @implements MOD-767-OSL09 — the natural spawn rule: next to oil in the dark it passes; without oil, in
	 *     light, with the knob off or with the chunk at its cap it refuses
	 */
	public static void spawnRuleNeedsOilDarkAndRoom(GameTestHelper helper) {
		// A sealed stone room: floor at y=1, the spawn cell at (2,2,2), nothing lit. The oil sits IN the floor one
		// cell east, stone under and around it, so it has nowhere to flow.
		BlockPos spawn = new BlockPos(2, 2, 2);
		BlockPos oil = new BlockPos(3, 1, 2);
		for (int x = 0; x <= 4; x++) {
			for (int y = 1; y <= 4; y++) {
				for (int z = 0; z <= 4; z++) {
					boolean wall = x == 0 || x == 4 || y == 1 || y == 4 || z == 0 || z == 4;
					helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
		helper.setBlock(oil.below(), Blocks.STONE);
		helper.setBlock(oil, ModContent.OIL_BLOCK.get());
		helper.runAfterDelay(20, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos at = helper.absolutePos(spawn);
			EntityType<OilSlime> type = ModMobs.OIL_SLIME.type();
			helper.assertTrue(OilSlimeSpawning.oilNearby(level, at), "the oil one cell away was not found");
			helper.assertTrue(level.getMaxLocalRawBrightness(at) <= 7,
					"the sealed room is lit: " + level.getMaxLocalRawBrightness(at));
			// Neighbouring scenarios may hold oil slimes in the same chunk: the cap is set relative to them.
			int present = OilSlimeSpawning.oilSlimesInChunk(level, at);
			try (ConfigOverrides o = ConfigOverrides.sync().set("oilSlimeSpawnCapPerChunk", present + 1)) {
				helper.assertTrue(OilSlimeSpawning.canSpawn(type, level, EntitySpawnReason.NATURAL, at,
						level.getRandom()), "next to oil in the dark the oil slime must be allowed to spawn"
						+ " (difficulty " + level.getDifficulty() + ")");
				try (ConfigOverrides off = ConfigOverrides.sync().set("oilSlimeNaturalSpawn", false)) {
					helper.assertTrue(!OilSlimeSpawning.canSpawn(type, level, EntitySpawnReason.NATURAL, at,
							level.getRandom()), "oilSlimeNaturalSpawn=false must stop natural spawning");
				}
				helper.spawnWithNoFreeWill(type, spawn.north());
				helper.assertTrue(!OilSlimeSpawning.canSpawn(type, level, EntitySpawnReason.NATURAL, at,
						level.getRandom()), "a chunk at its cap must refuse another oil slime");
			}
			try (ConfigOverrides o = ConfigOverrides.sync().set("oilSlimeSpawnCapPerChunk", 1000)) {
				helper.setBlock(oil, Blocks.STONE);
				helper.assertTrue(!OilSlimeSpawning.oilNearby(level, at), "oil found where there is none");
				helper.assertTrue(!OilSlimeSpawning.canSpawn(type, level, EntitySpawnReason.NATURAL, at,
						level.getRandom()), "without oil nearby the oil slime must not spawn");
				helper.assertTrue(OilSlimeSpawning.canSpawn(type, level, EntitySpawnReason.SPAWNER, at,
						level.getRandom()), "a spawner ignores the oil and light rules");
			}
			helper.succeed();
		});
	}

	/** @implements MOD-767-OSL10 — the Mob Repeller expels an oil slime like any other slime */
	public static void repellerPushesIt(GameTestHelper helper) {
		BlockPos repellerPos = new BlockPos(1, 2, 1);
		try (ConfigOverrides o = ConfigOverrides.sync().set("mobRepellerRange", 1)) {
			helper.setBlock(repellerPos, ModContent.MOB_REPELLER.get());
			MobRepellerBlockEntity repeller = helper.getBlockEntity(repellerPos, MobRepellerBlockEntity.class);
			repeller.getEnergyStorage().setAmountUntracked(Config.mobRepellerBuffer);
			helper.spawnWithNoFreeWill(ModMobs.OIL_SLIME.type(), repellerPos.above());
			int repelled = MobRepellerField.sweep(helper.getLevel(), helper.absolutePos(repellerPos),
					Config.mobRepellerRange);
			helper.assertTrue(repelled == 1, "the oil slime in the zone must be expelled, got " + repelled);
		}
		helper.succeed();
	}
}
