package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.ReactorDoorBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.ShieldingChestBlockEntity;
import dev.alaindustrial.item.energy.PouchContents;
import dev.alaindustrial.item.energy.PouchItem;
import dev.alaindustrial.item.misc.ShieldingPouchItem;
import dev.alaindustrial.core.radiation.RadiationDose;
import dev.alaindustrial.core.radiation.RadiationMobs;
import dev.alaindustrial.core.radiation.RadiationSources;
import dev.alaindustrial.core.radiation.RadiationTicker;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModEffects;
import dev.alaindustrial.registry.ModTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
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
	 * A full suit is COMPLETE protection on a mob (MOD-535): the villager takes no dose at all — not
	 * from the rack's field, and not from the exact carried exposure that converts a bare villager
	 * (R-RAD-03). The rig proves it could irradiate by measuring the raw dose on the same villager
	 * before the suit goes on.
	 *
	 * <p><b>Why a mob gets more than a player.</b> The player's 95 % rod cap makes a live core
	 * survivable-but-scary for somebody who can walk away from it; a villager cannot back off, and
	 * the first live test showed a suited one still converting beside scattered uranium through the
	 * 5 % leak. So on a mob the ceiling is a flat 100: a full set stops everything, a partial one
	 * still cuts its 25 % per piece.
	 *
	 * <p>Equipping is done straight to the slots rather than through a dispenser: the dispense path
	 * is vanilla's ({@code EquipmentDispenseItemBehavior} equips any living entity), and duplicating
	 * it here would test vanilla, not the mod. The suit does not RENDER on a villager — vanilla's
	 * villager model has no armour layer at all (the zombie villager does, which is why conversion
	 * makes it appear) — but that is a rendering gap tracked separately, never a reason for the dose
	 * to be anything but zero.
	 *
	 * @implements R-RAD-13 — see docs/testing/RULES.md
	 */
	public static void suitedVillagerTakesNoDose(GameTestHelper helper) {
		withIsolatedField(() -> {
			placeFuelledRack(helper);
			Villager villager = helper.spawn(EntityTypes.VILLAGER, BYSTANDER);
			ServerLevel level = helper.getLevel();
			int radius = Config.radiationSourceRadius;

			int raw = RadiationSources.exposureAt(level, villager, radius);
			if (raw < 20) {
				helper.fail("rig is wrong: the rack must reach this villager with room to spare (got "
						+ raw + "), or the zeros below would be the rig's doing and not the suit's");
				return;
			}

			villager.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModContent.SHIELDING_HELMET.get()));
			villager.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModContent.SHIELDING_CHESTPLATE.get()));
			villager.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModContent.SHIELDING_LEGGINGS.get()));
			villager.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModContent.SHIELDING_BOOTS.get()));

			// The field first: a full suit must stop it entirely on a mob.
			RadiationMobs.sweep(level, List.of(villager.position()), List.of(), radius);
			int fieldDose = RadiationDose.of(villager);
			if (fieldDose != 0) {
				helper.fail("a full suit must stop the whole field on a mob: raw " + raw
						+ ", leaked " + fieldDose);
				return;
			}

			// Then the exact exposure that converts a bare villager (R-RAD-03): same answer — nothing.
			sweepWithCarried(level, villager, Config.radiationDoseCapacity);
			int both = RadiationDose.of(villager);
			if (both != 0) {
				helper.fail("a converting carried exposure must not pass a full suit: leaked " + both);
				return;
			}
			if (villager.isRemoved() || !villager.isAlive()) {
				helper.fail("the villager should still be standing — this carried source converts a bare one");
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * The player's own scenario, played by a REAL dispenser (MOD-535): a dispenser fires the helmet
	 * at a villager, the villager ends up wearing it, and the fully suited villager takes no dose
	 * from the exposure that converts a bare one.
	 *
	 * <p><b>Why a real dispenser instead of {@code setItemSlot}.</b> The first live test read as
	 * "the shielding is broken": the pieces never landed on the villager (vanilla equips only an
	 * entity standing INSIDE the single facing block, and a villager wanders), so a bare villager
	 * converted and the zombie villager later showed up wearing armor it had picked off the floor.
	 * {@link dev.alaindustrial.item.wearable.SuitDispenseBehavior} widens the target and prefers the
	 * convertible species — this rig is what proves the whole chain end to end: redstone fires the
	 * dispenser, the helmet lands in the HEAD slot, and the suit then answers a converting exposure
	 * with zero.
	 *
	 * <p>The sequence body runs AFTER {@code withIsolatedField} has restored the radii, so the sweep
	 * half re-pins them itself — the same isolation, restated where it is actually consumed.
	 *
	 * @implements R-RAD-14 — see docs/testing/RULES.md
	 */
	public static void dispenserDressesTheVillagerForRadiation(GameTestHelper helper) {
		helper.setBlock(WALL, Blocks.DISPENSER.defaultBlockState()
				.setValue(DispenserBlock.FACING, Direction.SOUTH));
		DispenserBlockEntity dispenser = helper.getBlockEntity(WALL, DispenserBlockEntity.class);
		dispenser.setItem(0, new ItemStack(ModContent.SHIELDING_HELMET.get()));
		Villager villager = helper.spawn(EntityTypes.VILLAGER, BYSTANDER);
		// Power from behind — the dispenser fires a tick or two later.
		helper.setBlock(RACK, Blocks.REDSTONE_BLOCK);

		helper.startSequence()
				.thenExecuteFor(6, () -> { })
				.thenExecute(() -> {
					ItemStack worn = villager.getItemBySlot(EquipmentSlot.HEAD);
					if (!worn.is(ModContent.SHIELDING_HELMET.get())) {
						helper.fail("the dispenser must put the helmet on the villager in front of it; "
								+ "HEAD holds " + worn);
						return;
					}
					// The dispenser proved its half; finish the set and ask the converting question.
					villager.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModContent.SHIELDING_CHESTPLATE.get()));
					villager.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModContent.SHIELDING_LEGGINGS.get()));
					villager.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModContent.SHIELDING_BOOTS.get()));
					withIsolatedField(() -> {
						sweepWithCarried(helper.getLevel(), villager, Config.radiationDoseCapacity);
						int dose = RadiationDose.of(villager);
						if (dose != 0) {
							helper.fail("a dispenser-suited villager must take no dose from a converting "
									+ "exposure; leaked " + dose);
							return;
						}
						if (villager.isRemoved() || !villager.isAlive()) {
							helper.fail("the dispenser-suited villager should still be standing");
						}
					});
				})
				.thenSucceed();
	}

	/**
	 * A miss must be a VISIBLE eject, never a silent wrong wearer (MOD-535). A pig stands where the
	 * dispenser aims — vanilla's own equipment behavior would happily dress it — but the suit is for
	 * convertible mobs only, so the piece must NOT land on the pig.
	 *
	 * <p>This is the regression rig for the second live report: the first forgiving version of
	 * {@code SuitDispenseBehavior} accepted any living entity, and while the villager had wandered
	 * out of the target box it quietly dressed the PLAYER standing at the rig — four equip sounds,
	 * a bare villager, and a report that read as "the shielding is broken". A pig is the bystander
	 * of choice because it also does not PICK UP the ejected piece (a zombie would, within ticks,
	 * and a picked-up helmet would fake the very failure this test guards against).
	 *
	 * @implements R-RAD-15 — see docs/testing/RULES.md
	 */
	public static void dispenserRefusesToDressAnyoneButConvertibleMobs(GameTestHelper helper) {
		helper.setBlock(WALL, Blocks.DISPENSER.defaultBlockState()
				.setValue(DispenserBlock.FACING, Direction.SOUTH));
		DispenserBlockEntity dispenser = helper.getBlockEntity(WALL, DispenserBlockEntity.class);
		dispenser.setItem(0, new ItemStack(ModContent.SHIELDING_HELMET.get()));
		Pig pig = helper.spawn(EntityTypes.PIG, BYSTANDER);
		helper.setBlock(RACK, Blocks.REDSTONE_BLOCK);

		helper.startSequence()
				.thenExecuteFor(12, () -> { })
				.thenExecute(() -> {
					// An empty slot means the piece LEFT the dispenser — it fired (onto someone, or
					// ejected as the spit-out item). A piece still inside means the rig never ran.
					if (!dispenser.getItem(0).isEmpty()) {
						helper.fail("rig is wrong: the dispenser never fired (piece still inside), so "
								+ "the empty pig slot above proves nothing");
						return;
					}
					if (!pig.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
						helper.fail("the suit must never land on a non-convertible mob: a miss has to "
								+ "be a spat-out item, not a silently dressed bystander");
					}
				})
				.thenSucceed();
	}

	/**
	 * The LIVE tick chain, not the direct call (MOD-535): a suited villager and a bare control beside
	 * a fuelled rack, a mock player standing in the level as the anchor, and
	 * {@code RadiationTicker.tickAll} driven across real ticks. The bare villager MUST convert (the
	 * chain runs and the rig is hot) while the suited one stays at zero dose and on its feet.
	 *
	 * <p>Every other radiation scenario calls {@code RadiationMobs.sweep} directly; this is the only
	 * one that exercises the loader server-tick wiring end to end. Radii are re-pinned inside each
	 * driven tick — the sequence body runs after any {@code withIsolatedField} around the setup has
	 * already restored them.
	 *
	 * @implements R-RAD-16 — see docs/testing/RULES.md
	 */
	public static void liveTickChainShieldsTheSuitedVillager(GameTestHelper helper) {
		placeFuelledRack(helper);
		ServerPlayer anchor = AlaGameTestHelper.survivalPlayer(helper);
		// The sweep is anchored on PLAYERS; put the mock right beside the villagers so both are
		// inside the (pinned, 3-block) sweep box whichever corner the mock spawned in.
		BlockPos at = helper.absolutePos(BYSTANDER);
		anchor.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
		Villager suited = helper.spawn(EntityTypes.VILLAGER, BYSTANDER);
		suited.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModContent.SHIELDING_HELMET.get()));
		suited.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModContent.SHIELDING_CHESTPLATE.get()));
		suited.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModContent.SHIELDING_LEGGINGS.get()));
		suited.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModContent.SHIELDING_BOOTS.get()));
		Villager bare = helper.spawn(EntityTypes.VILLAGER, RACK.above());

		helper.startSequence()
				.thenExecuteFor(100, () -> withIsolatedField(() ->
						RadiationTicker.tickAll(helper.getLevel().getServer())))
				.thenExecute(() -> {
					if (bare.isAlive() && !bare.isRemoved()) {
						helper.fail("the bare control villager must convert — if it does not, neither "
								+ "did the chain run nor the rig radiate, and the zeros below prove "
								+ "nothing");
						return;
					}
					int dose = RadiationDose.of(suited);
					if (dose != 0) {
						helper.fail("the live tick chain must leave a suited villager at zero dose; "
								+ "leaked " + dose);
						return;
					}
					if (suited.isRemoved() || !suited.isAlive()) {
						helper.fail("the suited villager should still be standing");
					}
				})
				.thenSucceed();
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

	// --- MOD-545: the shielding pouch, the portable counterpart of the shielding chest ---

	/** A shielding pouch holding {@code stack}. */
	private static ItemStack shieldingPouchOf(ItemStack stack) {
		ItemStack pouch = new ItemStack(ModContent.SHIELDING_POUCH.get());
		PouchItem.setContents(pouch,
				PouchContents.EMPTY.insert(stack, ShieldingPouchItem.storageCapacity()).contents());
		return pouch;
	}

	/** A battery pouch holding {@code stack} — the unshielded pouch, used as a control. */
	private static ItemStack batteryPouchOf(ItemStack stack) {
		ItemStack pouch = new ItemStack(ModContent.BATTERY_POUCH.get());
		PouchItem.setContents(pouch,
				PouchContents.EMPTY.insert(stack, Config.lvPouchCapacity).contents());
		return pouch;
	}

	/** A vanilla bundle holding {@code stack} — free shielding until MOD-545 closed it. */
	private static ItemStack bundleOf(ItemStack stack) {
		ItemStack bundle = new ItemStack(Items.BUNDLE);
		bundle.set(DataComponents.BUNDLE_CONTENTS,
				new BundleContents(List.of(new ItemStackTemplate(stack.getItem(), stack.getCount()))));
		return bundle;
	}

	/**
	 * Uranium of every tag is invisible to the sweep inside a shielding pouch, and visible everywhere
	 * else — including the two carriers that used to hide it for free.
	 *
	 * <p>The loose stack is the positive control: "the pouch reads zero" on its own would pass just as
	 * happily against a mod that never counted carried items at all. The bundle and the battery pouch
	 * are the other half of MOD-545 — until it, the sweep read only {@code CONTAINER}, so a bundle of
	 * leather and string shielded uranium better than a lead chest did, for free, and this item would
	 * have had nothing to be better than.
	 *
	 * @implements R-RAD-17 — see docs/testing/RULES.md
	 */
	public static void pouchHidesEveryTagFromTheCarrier(GameTestHelper helper) {
		ServerPlayer carrier = AlaGameTestHelper.survivalPlayer(helper);
		List<TagKey<Item>> tags = List.of(ModTags.Items.RADIOACTIVE_LOW,
				ModTags.Items.RADIOACTIVE_MEDIUM, ModTags.Items.RADIOACTIVE_HIGH);
		List<ItemStack> samples = List.of(new ItemStack(ModContent.RAW_URANIUM.get(), 8),
				new ItemStack(ModContent.URANIUM_INGOT.get(), 8),
				new ItemStack(ModContent.REFINED_URANIUM.get(), 8));
		for (int i = 0; i < tags.size(); i++) {
			TagKey<Item> tag = tags.get(i);
			ItemStack sample = samples.get(i);

			carrier.getInventory().clearContent();
			carrier.getInventory().add(sample.copy());
			int loose = RadiationSources.carried(carrier, tag);
			if (loose <= 0) {
				helper.fail("rig is wrong: loose " + sample.getItem() + " must be counted, otherwise the "
						+ "pouch assertion below proves nothing; got " + loose);
			}

			carrier.getInventory().clearContent();
			carrier.getInventory().add(shieldingPouchOf(sample.copy()));
			int pouched = RadiationSources.carried(carrier, tag);
			if (pouched != 0) {
				helper.fail(sample.getItem() + " in a shielding pouch must not be counted; got " + pouched);
			}

			// The closed hole: both of these counted as zero before MOD-545.
			carrier.getInventory().clearContent();
			carrier.getInventory().add(bundleOf(sample.copy()));
			int bundled = RadiationSources.carried(carrier, tag);
			if (bundled != loose) {
				helper.fail(sample.getItem() + " in a vanilla bundle must count the same as loose ("
						+ loose + "); got " + bundled);
			}

			carrier.getInventory().clearContent();
			carrier.getInventory().add(batteryPouchOf(sample.copy()));
			int battery = RadiationSources.carried(carrier, tag);
			if (battery != loose) {
				helper.fail(sample.getItem() + " in a battery pouch must count the same as loose ("
						+ loose + "); got " + battery);
			}
		}
		carrier.getInventory().clearContent();
		helper.succeed();
	}

	/**
	 * The rule holds one level down, where the OTHER {@code countTagged} overload answers.
	 *
	 * <p>A pouch inside a shulker box is read as an {@code ItemStackTemplate}, never as an
	 * {@code ItemStack}, and at the shipped {@code radiationContainerDepth} of 1 that path is
	 * unreachable through a world at all — a pouch in a chest is already answered at depth 0. So a
	 * world-level test cannot tell "both overloads guarded" from "only the stack one guarded", and
	 * this scenario asks the depth-2 question directly. The shulker holding bare uranium is the
	 * positive control: it proves the depth budget really does reach that far.
	 *
	 * @implements R-RAD-18 — see docs/testing/RULES.md
	 */
	public static void pouchRuleHoldsInsideAnotherContainer(GameTestHelper helper) {
		ItemStack fuel = new ItemStack(ModContent.REFINED_URANIUM.get(), 8);

		ItemStack shulkerWithUranium = new ItemStack(Items.SHULKER_BOX);
		shulkerWithUranium.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(fuel.copy())));
		int nested = RadiationSources.countTagged(shulkerWithUranium, ModTags.Items.RADIOACTIVE_HIGH, 2);
		if (nested <= 0) {
			helper.fail("rig is wrong: a shulker of uranium must count at depth 2, otherwise the pouch "
					+ "assertion below proves nothing; got " + nested);
		}

		ItemStack shulkerWithPouch = new ItemStack(Items.SHULKER_BOX);
		shulkerWithPouch.set(DataComponents.CONTAINER,
				ItemContainerContents.fromItems(List.of(shieldingPouchOf(fuel.copy()))));
		int shielded = RadiationSources.countTagged(shulkerWithPouch, ModTags.Items.RADIOACTIVE_HIGH, 2);
		if (shielded != 0) {
			helper.fail("a shielding pouch inside another container must still shield; got " + shielded);
		}
		helper.succeed();
	}

	/**
	 * A miner carrying uranium in a shielding pouch does not irradiate the bystanders they walk past:
	 * the carrier never becomes a source at all.
	 *
	 * @implements R-RAD-19 — see docs/testing/RULES.md
	 */
	public static void pouchCarrierDoesNotIrradiateBystanders(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow bystander = helper.spawn(EntityTypes.COW, BYSTANDER);
			Vec3 at = bystander.getEyePosition();
			ItemStack fuel = new ItemStack(ModContent.REFINED_URANIUM.get(), 8);

			// Control: the same uranium loose in the pockets is a source, and it does reach the mob.
			int loose = RadiationSources.strengthOf(fuel);
			if (loose <= 0) {
				helper.fail("rig is wrong: loose uranium must have strength; got " + loose);
			}
			RadiationMobs.sweep(level, List.of(at), List.of(new RadiationSources.Source(at, loose)),
					Config.radiationSourceRadius);
			int dosed = RadiationDose.of(bystander);
			if (dosed <= 0) {
				helper.fail("rig is wrong: a carried source at the mob's eyes must dose it; got " + dosed);
			}

			// The pouch: strength zero is what keeps the carrier out of carriedSources entirely, so the
			// sweep has nothing to deliver and the bystander's dose only decays from here.
			int shielded = RadiationSources.strengthOf(shieldingPouchOf(fuel.copy()));
			if (shielded != 0) {
				helper.fail("a shielding pouch must not be a source; got " + shielded);
			}
			RadiationMobs.sweep(level, List.of(at), List.of(), Config.radiationSourceRadius);
			int after = RadiationDose.of(bystander);
			if (after > dosed) {
				helper.fail("a pouch carrier must not raise a bystander's dose; it went from " + dosed
						+ " to " + after);
			}
			helper.succeed();
		});
	}

	/**
	 * The pouch shields what is INSIDE it and nothing else: a fuelled rack irradiates exactly as hard
	 * with one in the pocket as without. The suit remains the only answer to the field — the division
	 * of labour this feature was asked for.
	 *
	 * @implements R-RAD-20 — see docs/testing/RULES.md
	 */
	public static void pouchDoesNotShieldTheField(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer carrier = AlaGameTestHelper.survivalPlayer(helper);
			BlockPos at = helper.absolutePos(BYSTANDER);
			carrier.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
			placeFuelledRack(helper);

			carrier.getInventory().clearContent();
			int bare = RadiationSources.exposureAt(level, carrier, Config.radiationSourceRadius);
			if (bare <= 0) {
				helper.fail("rig is wrong: a fuelled rack must irradiate the carrier; got " + bare);
			}

			carrier.getInventory().add(shieldingPouchOf(new ItemStack(ModContent.REFINED_URANIUM.get(), 8)));
			int withPouch = RadiationSources.exposureAt(level, carrier, Config.radiationSourceRadius);
			if (withPouch != bare) {
				helper.fail("a shielding pouch must not shield the field: the rack read " + bare
						+ " without it and " + withPouch + " with it");
			}
			carrier.getInventory().clearContent();
			helper.succeed();
		});
	}

	/**
	 * A closed pouch is quiet wherever it lies — dropped on the floor or stored in somebody's chest —
	 * while the same uranium in the open goes on radiating from both places.
	 *
	 * @implements R-RAD-21 — see docs/testing/RULES.md
	 */
	public static void pouchIsQuietOnTheFloorAndInAChest(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);
			ItemStack fuel = new ItemStack(ModContent.REFINED_URANIUM.get(), 16);
			BlockPos abs = helper.absolutePos(RACK);

			// Control on the floor: a spilled pile radiates.
			ItemEntity loose = new ItemEntity(level, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5,
					fuel.copy());
			level.addFreshEntity(loose);
			int spilled = RadiationSources.exposureAt(level, viewer, Config.radiationSourceRadius);
			if (spilled <= 0) {
				helper.fail("rig is wrong: uranium on the floor must irradiate; got " + spilled);
			}
			// Remove it, or it goes on radiating through every phase below.
			loose.discard();

			ItemEntity dropped = new ItemEntity(level, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5,
					shieldingPouchOf(fuel.copy()));
			level.addFreshEntity(dropped);
			int onFloor = RadiationSources.exposureAt(level, viewer, Config.radiationSourceRadius);
			if (onFloor != 0) {
				helper.fail("a dropped shielding pouch must not radiate; got " + onFloor);
			}
			dropped.discard();

			// Control in a chest: an ordinary chest is not a shield.
			helper.setBlock(RACK, ModContent.IRON_CHEST.get());
			Container chest = helper.getBlockEntity(RACK, IronChestBlockEntity.class);
			chest.setItem(0, fuel.copy());
			int stored = RadiationSources.exposureAt(level, viewer, Config.radiationSourceRadius);
			if (stored <= 0) {
				helper.fail("rig is wrong: uranium in an ordinary chest must irradiate; got " + stored);
			}

			// Empty it before the next phase, or the loose stack keeps radiating from the same chest.
			chest.clearContent();
			chest.setItem(0, shieldingPouchOf(fuel.copy()));
			int pouched = RadiationSources.exposureAt(level, viewer, Config.radiationSourceRadius);
			if (pouched != 0) {
				helper.fail("a shielding pouch in a chest must not radiate; got " + pouched);
			}
			chest.clearContent();
			helper.succeed();
		});
	}
}
