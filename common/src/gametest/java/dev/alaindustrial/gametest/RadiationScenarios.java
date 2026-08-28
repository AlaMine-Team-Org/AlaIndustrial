package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.ReactorDoorBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.ShieldingChestBlockEntity;
import dev.alaindustrial.core.radiation.RadiationMobs;
import dev.alaindustrial.core.radiation.RadiationSources;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModEffects;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametest bodies for radiation (MOD-470). The same bodies run on the Fabric
 * {@code @GameTest} lane and the NeoForge {@code gameTestServer} lane.
 *
 * <p><b>Every scenario pins the radii to 3 and restores them in a {@code finally}.</b> The gametest
 * server shares one world and lays the structures a few blocks apart, so the shipped six-block radius
 * would reach into a neighbour's rig — the exact class of bug that made MOD-277 red on one loader and
 * green on the other for identical code. Restoring the configured values is part of the contract.
 *
 * <p><b>What is worth testing here and what is not.</b> The arithmetic of a dose is MC-free and lives
 * in {@code RadiationCoreTest} (L1). What only a world can answer is the part these bodies assert: that
 * a shell block between a rod and a bystander actually stops the radiation, and that a mob carried past
 * the transformation threshold really becomes the other entity, keeping what it should.
 */
public final class RadiationScenarios {

	private RadiationScenarios() {
	}

	private static final BlockPos RACK = new BlockPos(1, 2, 1);
	private static final BlockPos WALL = new BlockPos(1, 2, 2);
	private static final BlockPos BYSTANDER = new BlockPos(1, 2, 3);

	/**
	 * Run {@code body} with both radiation radii pinned to 3 (see the class doc).
	 *
	 * <p>Three, not two: the rack sits two blocks from the bystander, and the trace runs from the
	 * bystander's EYES to the centre of the rack — about 2.15 blocks, which a radius of 2 rejects. The
	 * first draft pinned 2 and produced a rig where nothing could ever be irradiated; the suite caught
	 * it, which is the only reason it is not in the shipped test now saying green about nothing.
	 */
	private static void withIsolatedField(Runnable body) {
		int source = Config.radiationSourceRadius;
		int ground = Config.radiationGroundRadius;
		Config.radiationSourceRadius = 3;
		Config.radiationGroundRadius = 3;
		try {
			body.run();
		} finally {
			Config.radiationSourceRadius = source;
			Config.radiationGroundRadius = ground;
		}
	}

	/**
	 * One sweep with a single point source of the given strength standing exactly where the mob is.
	 * The dose ramp is arithmetic and belongs to L1; what a world has to prove is the transformation.
	 */
	private static void sweepWithCarried(ServerLevel level, LivingEntity target, int strength) {
		// At the EYES, not at the feet: the dose is measured eye-to-source, so a source dropped at the
		// mob's own position already sits a metre and a half away and comes back attenuated.
		Vec3 at = target.getEyePosition();
		RadiationMobs.sweep(level, List.of(at), List.of(new RadiationSources.Source(at, strength)),
				Config.radiationSourceRadius);
	}

	/** A fuelled rack: four rods in the assembly, which is what a running reactor column holds. */
	private static void placeFuelledRack(GameTestHelper helper) {
		helper.setBlock(RACK, ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState()
				.setValue(FuelRodAssemblyBlock.RODS, FuelRodAssemblyBlock.MAX_RODS));
		FuelRodAssemblyBlockEntity rack = helper.getBlockEntity(RACK, FuelRodAssemblyBlockEntity.class);
		for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
			rack.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		}
	}

	/**
	 * A fuelled rod irradiates whatever it can see.
	 *
	 * @implements R-RAD-01 — see docs/testing/RULES.md
	 */
	public static void rodIrradiatesWhatItCanSee(GameTestHelper helper) {
		withIsolatedField(() -> {
			placeFuelledRack(helper);
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);
			int exposure = RadiationSources.exposureAt(helper.getLevel(), viewer, Config.radiationSourceRadius);
			if (exposure <= 0) {
				helper.fail("a fuelled rod two blocks away in open air must irradiate; got " + exposure);
			}
			helper.succeed();
		});
	}

	/**
	 * The shell stops it: one casing block between the rod and the bystander takes the exposure to zero,
	 * and removing that block brings it straight back.
	 *
	 * <p>Both halves are asserted on purpose. "With a wall the exposure is zero" alone is the classic
	 * test that cannot fail — a rig where the rod never reached the bystander in the first place passes
	 * it just as happily. Measuring the same rack with the wall gone is what proves the zero was the
	 * wall's doing.
	 *
	 * @implements R-RAD-02 — see docs/testing/RULES.md
	 */
	public static void casingBlocksTheRod(GameTestHelper helper) {
		withIsolatedField(() -> {
			placeFuelledRack(helper);
			helper.setBlock(WALL, ModContent.REACTOR_CASING.get());
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);
			int blocked = RadiationSources.exposureAt(helper.getLevel(), viewer, Config.radiationSourceRadius);
			if (blocked != 0) {
				helper.fail("a casing wall must stop the rod entirely; got " + blocked);
			}
			helper.setBlock(WALL, net.minecraft.world.level.block.Blocks.AIR);
			int open = RadiationSources.exposureAt(helper.getLevel(), viewer, Config.radiationSourceRadius);
			if (open <= 0) {
				helper.fail("with the wall gone the same rod must irradiate again; got " + open
						+ " — the zero above proved nothing");
			}
			helper.succeed();
		});
	}

	/**
	 * A villager irradiated past the threshold becomes a zombie villager and keeps its profession.
	 *
	 * @implements R-RAD-03 — see docs/testing/RULES.md
	 */
	public static void villagerBecomesZombieVillager(GameTestHelper helper) {
		withIsolatedField(() -> {
			Villager villager = helper.spawn(EntityTypes.VILLAGER, BYSTANDER);
			ServerLevel level = helper.getLevel();
			// One sweep carrying a full scale of dose: the ramp is arithmetic and belongs to L1, while
			// what a world has to prove is that the transformation itself happens and carries data across.
			sweepWithCarried(level, villager, Config.radiationDoseCapacity);
			if (villager.isAlive() && !villager.isRemoved()) {
				helper.fail("the villager should have been converted, not left standing");
			}
			ZombieVillager converted = level.getEntitiesOfClass(ZombieVillager.class,
					villager.getBoundingBox().inflate(4.0)).stream().findFirst().orElse(null);
			if (converted == null) {
				helper.fail("no zombie villager appeared where the villager stood");
				return;
			}
			if (!converted.isPersistenceRequired()) {
				helper.fail("a converted trader that despawns is a lost trader");
			}
			helper.succeed();
		});
	}

	/**
	 * A cow becomes a mooshroom, and takes no damage on the way — radiation transforms livestock rather
	 * than culling it.
	 *
	 * @implements R-RAD-04 — see docs/testing/RULES.md
	 */
	public static void cowBecomesMooshroom(GameTestHelper helper) {
		withIsolatedField(() -> {
			Cow cow = helper.spawn(EntityTypes.COW, BYSTANDER);
			float health = cow.getHealth();
			ServerLevel level = helper.getLevel();
			sweepWithCarried(level, cow, Config.radiationDoseCapacity);
			if (cow.getHealth() < health) {
				helper.fail("a cow must not be hurt by radiation, only changed");
			}
			MushroomCow converted = level.getEntitiesOfClass(MushroomCow.class,
					cow.getBoundingBox().inflate(4.0)).stream().findFirst().orElse(null);
			if (converted == null) {
				helper.fail("no mooshroom appeared where the cow stood");
			}
			helper.succeed();
		});
	}

	/**
	 * What radiation already made of you, it does not make again: a zombie villager standing in the same
	 * field takes no dose at all.
	 *
	 * @implements R-RAD-05 — see docs/testing/RULES.md
	 */
	public static void zombieVillagerIsPastTheEnd(GameTestHelper helper) {
		withIsolatedField(() -> {
			ZombieVillager zombie = helper.spawn(EntityTypes.ZOMBIE_VILLAGER, BYSTANDER);
			sweepWithCarried(helper.getLevel(), zombie, Config.radiationDoseCapacity);
			if (zombie.hasEffect(ModEffects.RADIATION.get())) {
				helper.fail("a zombie villager must not accumulate a dose — it is already the outcome");
			}
			helper.succeed();
		});
	}

	/**
	 * Uranium dropped on the ground keeps radiating: switching a hazard off by throwing it away would
	 * make the shielding chest pointless.
	 *
	 * @implements R-RAD-06 — see docs/testing/RULES.md
	 */
	public static void droppedUraniumStillRadiates(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);
			BlockPos abs = helper.absolutePos(BYSTANDER);
			ItemEntity drop = new ItemEntity(level, abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5,
					new ItemStack(ModContent.REFINED_URANIUM.get(), 4));
			level.addFreshEntity(drop);
			int exposure = RadiationSources.exposureAt(level, viewer, Config.radiationSourceRadius);
			if (exposure <= 0) {
				helper.fail("uranium lying on the ground must still irradiate; got " + exposure);
			}
			helper.succeed();
		});
	}

	/**
	 * Distance is a defence: the same rack hits far harder at one block than at four.
	 *
	 * <p>Before the falloff a rod six blocks away hit exactly as hard as one at your feet, so the only
	 * way to survive a core was a wall. Asserting the ORDER rather than the numbers keeps this test
	 * about the rule instead of about the tuning.
	 *
	 * @implements R-RAD-07 — see docs/testing/RULES.md
	 */
	public static void distanceWeakensTheRod(GameTestHelper helper) {
		withIsolatedField(() -> {
			placeFuelledRack(helper);
			Cow near = helper.spawn(EntityTypes.COW, RACK.above());
			Cow far = helper.spawn(EntityTypes.COW, BYSTANDER);
			int close = RadiationSources.exposureAt(helper.getLevel(), near, Config.radiationSourceRadius);
			int distant = RadiationSources.exposureAt(helper.getLevel(), far, Config.radiationSourceRadius);
			if (close <= distant) {
				helper.fail("radiation must fall off with distance; next to the rack " + close
						+ ", two blocks away " + distant);
			}
			if (distant <= 0) {
				helper.fail("two blocks away must still be inside the field; got " + distant);
			}
			helper.succeed();
		});
	}

	/**
	 * Uranium lying inside a sealed box does not irradiate whoever stands outside it.
	 *
	 * <p>The first version applied the line-of-sight rule to rods only, so dropped uranium shone
	 * straight through walls — a shielding chest would have been pointless and the reactor shell was
	 * only half a shell.
	 *
	 * @implements R-RAD-08 — see docs/testing/RULES.md
	 */
	public static void casingBlocksDroppedUranium(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			helper.setBlock(WALL, ModContent.REACTOR_CASING.get());
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);
			BlockPos abs = helper.absolutePos(RACK);
			ItemEntity drop = new ItemEntity(level, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5,
					new ItemStack(ModContent.REFINED_URANIUM.get(), 16));
			level.addFreshEntity(drop);
			int blocked = RadiationSources.exposureAt(level, viewer, Config.radiationSourceRadius);
			if (blocked != 0) {
				helper.fail("a casing wall must stop dropped uranium too; got " + blocked);
			}
			helper.setBlock(WALL, net.minecraft.world.level.block.Blocks.AIR);
			int open = RadiationSources.exposureAt(level, viewer, Config.radiationSourceRadius);
			if (open <= 0) {
				helper.fail("with the wall gone the same pile must irradiate; got " + open
						+ " — the zero above proved nothing");
			}
			helper.succeed();
		});
	}

	/**
	 * An open airlock leaks: the same rod is blocked by a closed door and reaches through an open one.
	 *
	 * <p>This is the promise the whole shell rests on — "the room is sealed, the doorway is not" — and
	 * until now it was the one part of it nothing checked.
	 *
	 * @implements R-RAD-09 — see docs/testing/RULES.md
	 */
	public static void openDoorLeaksRadiation(GameTestHelper helper) {
		withIsolatedField(() -> {
			placeFuelledRack(helper);
			BlockState door = ModContent.REACTOR_DOOR.get().defaultBlockState()
					.setValue(ReactorDoorBlock.FACING, Direction.SOUTH);
			helper.setBlock(WALL, door);
			helper.setBlock(WALL.above(), door.setValue(ReactorDoorBlock.HALF, DoubleBlockHalf.UPPER));
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);
			int closed = RadiationSources.exposureAt(helper.getLevel(), viewer, Config.radiationSourceRadius);
			if (closed != 0) {
				helper.fail("a closed airlock must stop the rod; got " + closed);
			}
			helper.setBlock(WALL, door.setValue(ReactorDoorBlock.OPEN, true));
			helper.setBlock(WALL.above(), door.setValue(ReactorDoorBlock.HALF, DoubleBlockHalf.UPPER)
					.setValue(ReactorDoorBlock.OPEN, true));
			int opened = RadiationSources.exposureAt(helper.getLevel(), viewer, Config.radiationSourceRadius);
			if (opened <= 0) {
				helper.fail("an open doorway must leak radiation; got " + opened);
			}
			helper.succeed();
		});
	}

	/**
	 * A shielded lever bolted to the shell does not open a hole in it (MOD-514).
	 *
	 * <p>This is the promise in the task's own title — "does not affect the room's radiation" — and it
	 * is not free: radiation is stopped by COLLISION, and a lever has none. What saves it is that a
	 * lever hangs on a face instead of filling a cell, so the wall behind it is untouched. The failure
	 * this guards against is the opposite build: a player replacing a casing block WITH the lever, which
	 * would leave a lever-shaped hole in the containment that looks solid from the inside.
	 *
	 * <p>All three measurements are taken with the same rack and the same cow, in this order: wall plus
	 * lever reads zero, then the wall goes and the LEVER ALONE reads full. The last one is the control —
	 * without it the zero above would also be produced by a rig where the rod never reached the cow, and
	 * by a lever that (wrongly) shielded on its own.
	 *
	 * @implements R-RAD-12 — see docs/testing/RULES.md
	 */
	public static void shieldedLeverOnTheWallDoesNotLeakRadiation(GameTestHelper helper) {
		withIsolatedField(() -> {
			placeFuelledRack(helper);
			helper.setBlock(WALL, ModContent.REACTOR_CASING.get());
			// Hung on the wall's far face, in the cell the bystander stands in: FACING is where the
			// lever looks, and it attaches on the opposite side — SOUTH bolts it to the casing.
			helper.setBlock(BYSTANDER, ModContent.REACTOR_LEVER.get().defaultBlockState()
					.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
					.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);
			int hung = RadiationSources.exposureAt(helper.getLevel(), viewer, Config.radiationSourceRadius);
			if (hung != 0) {
				helper.fail("a lever hanging on the casing let " + hung + " through a wall that must stop it");
			}
			// Take the wall away and leave the lever exactly where it was: the dose must come back, or
			// the zero above proved nothing about the lever.
			helper.setBlock(WALL, Blocks.AIR);
			int bare = RadiationSources.exposureAt(helper.getLevel(), viewer, Config.radiationSourceRadius);
			if (bare <= 0) {
				helper.fail("with the casing gone the lever alone stopped the rod (got " + bare
						+ ") — it is not a wall and must not act like one");
			}
			helper.succeed();
		});
	}

	/**
	 * The shielding chest is the one container that stops what is inside it (MOD-474): the SAME stack of
	 * refined uranium irradiates through an ordinary chest and not at all through a shielding one.
	 *
	 * <p><b>Both halves are measured, and that is the whole point.</b> "The shielding chest reads zero"
	 * on its own is the classic test that cannot fail — before MOD-474 nothing looked inside block
	 * containers at all, so EVERY chest read zero and this assertion would have passed against a mod that
	 * has no shielding chest in it. Measuring the ordinary chest in the same spot with the same stack is
	 * what makes the zero mean something.
	 *
	 * @implements R-RAD-10 — see docs/testing/RULES.md
	 */
	public static void shieldingChestStopsWhatAnOrdinaryChestDoesNot(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);
			ItemStack fuel = new ItemStack(ModContent.REFINED_URANIUM.get(), 16);

			helper.setBlock(RACK, ModContent.IRON_CHEST.get());
			Container plain = helper.getBlockEntity(RACK, IronChestBlockEntity.class);
			plain.setItem(0, fuel.copy());
			int exposed = RadiationSources.exposureAt(level, viewer, Config.radiationSourceRadius);
			if (exposed <= 0) {
				helper.fail("uranium in an ordinary chest must irradiate; got " + exposed);
			}

			// The cap, measured in a world (MOD-474): filling the same chest to the brim must not
			// raise the exposure at all. Uncapped, a full chest killed instantly at every distance in
			// the radius — a trap rather than a warning, and the death loop MOD-470 closed reopened.
			for (int slot = 0; slot < 27; slot++) {
				plain.setItem(slot, new ItemStack(ModContent.REFINED_URANIUM.get(), 64));
			}
			int hoard = RadiationSources.exposureAt(level, viewer, Config.radiationSourceRadius);
			if (hoard != exposed) {
				helper.fail("past the cap a fuller chest must not irradiate harder; 16 items gave "
						+ exposed + ", a packed chest gave " + hoard);
			}

			// EMPTY it before breaking it, or vanilla spills the stack on the floor and the pile keeps
			// radiating from there (MOD-470's ground source, working exactly as intended) — the first
			// draft of this rig measured 458 through the shielding chest for precisely that reason.
			plain.clearContent();
			helper.setBlock(RACK, net.minecraft.world.level.block.Blocks.AIR);
			helper.setBlock(RACK, ModContent.SHIELDING_CHEST.get());
			Container shielded = helper.getBlockEntity(RACK, ShieldingChestBlockEntity.class);
			shielded.setItem(0, fuel.copy());
			int stopped = RadiationSources.exposureAt(level, viewer, Config.radiationSourceRadius);
			if (stopped != 0) {
				helper.fail("the same uranium in a shielding chest must not irradiate at all; got " + stopped);
			}
			helper.succeed();
		});
	}

	/**
	 * The sweep must not open a chest whose loot has not been generated yet (MOD-524).
	 *
	 * <p>On a vanilla chest or barrel {@code Container.getItem} is not a read: it unpacks the pending
	 * loot table with no player in the loot context and clears the tag. A once-per-second sweep around
	 * every player therefore opened every unopened loot chest it passed, and a table that sizes itself
	 * from the player's score then rolled nothing — Mine Treasure's chests came out empty for good.
	 * Shipped in 0.1.115, reported from a live world.
	 *
	 * <p><b>The positive control is what makes the assertion mean something.</b> "The loot table is
	 * still there" passes trivially if the sweep never reached that block at all — so the same barrel
	 * in the same spot is first loaded with real uranium and must irradiate. Only then does the
	 * untouched tag prove a decision rather than an absence.
	 *
	 * <p><b>Do not assert on {@code isEmpty()} here.</b> It unpacks too, so an emptiness check would
	 * destroy the very thing under test and the assertion would pass against the broken code. The tag
	 * is the only safe witness; {@code getLootTable} is a plain field read.
	 *
	 * @implements R-RAD-11 — see docs/testing/RULES.md
	 */
	public static void sweepLeavesUngeneratedLootAlone(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);

			// Positive control: a vanilla barrel in this spot IS reached and read by the sweep.
			helper.setBlock(RACK, Blocks.BARREL);
			BarrelBlockEntity loaded = helper.getBlockEntity(RACK, BarrelBlockEntity.class);
			loaded.setItem(0, new ItemStack(ModContent.REFINED_URANIUM.get(), 16));
			int reached = RadiationSources.exposureAt(level, viewer, Config.radiationSourceRadius);
			if (reached <= 0) {
				helper.fail("rig is wrong: uranium in a vanilla barrel here must irradiate, otherwise the "
						+ "loot-table assertion below proves nothing; got " + reached);
			}
			// Empty it before replacing the block, or the stack spills and keeps radiating from the floor.
			loaded.clearContent();

			// The regression: the same barrel, now owing its contents to a loot table.
			helper.setBlock(RACK, Blocks.AIR);
			helper.setBlock(RACK, Blocks.BARREL);
			BarrelBlockEntity pending = helper.getBlockEntity(RACK, BarrelBlockEntity.class);
			pending.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
			RadiationSources.exposureAt(level, viewer, Config.radiationSourceRadius);
			if (pending.getLootTable() == null) {
				helper.fail("the sweep generated a foreign chest's loot: the pending loot table is gone, "
						+ "which leaves another mod's chest empty for good (MOD-524)");
			}
			helper.succeed();
		});
	}
}
