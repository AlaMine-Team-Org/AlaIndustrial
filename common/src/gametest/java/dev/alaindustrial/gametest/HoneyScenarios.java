package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.fluid.FluidImmersion;
import dev.alaindustrial.fluid.HoneyFluid;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Liquid honey (MOD-922) on both loaders: the bucket places and takes back a honey source that carries the
 * {@code c:honey} tag, a pump drains a pool into a tank, the pool spreads at lava's pace rather than water's,
 * and an entity inside it is held back harder than in crude oil — falling and sliding alike.
 *
 * <p>Every rig is a closed stone basin or shaft inside rel x,z 0..7, the chunks this lane force-loads (MOD-507).
 */
public final class HoneyScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(HoneyScenarios::bucketPlacesAndTakesBackATaggedSource, "honey_bucket_place_and_pickup")
						.ticks(40),
				RosterEntry.of(HoneyScenarios::pumpDrainsHoneyIntoTank, "honey_pump_drains_pool_into_tank").ticks(120),
				RosterEntry.of(HoneyScenarios::spreadsAtLavaPaceNotWaters, "honey_spreads_at_lava_pace").ticks(200),
				RosterEntry.of(HoneyScenarios::fallIsDampedHarderThanInCrude, "honey_fall_damped_harder_than_crude")
						.ticks(60),
				RosterEntry.of(HoneyScenarios::slideIsHeldBack, "honey_slide_held_back").ticks(40),
				RosterEntry.of(HoneyScenarios::mobWalksFourTimesSlower, "honey_mob_walks_far_slower").ticks(40),
				RosterEntry.of(HoneyScenarios::mobClimbsOutOfAOneDeepPool, "honey_mob_climbs_out_of_pool")
						.ticks(240));

		private Roster() {}
	}

	/** The common tag both loaders' conventions name honey by (NeoForge Tags.Fluids.HONEY, Fabric's twin). */
	static final TagKey<Fluid> C_HONEY = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "honey"));

	private HoneyScenarios() {}

	private static BlockState honeySource() {
		return ModContent.HONEY_BLOCK.get().defaultBlockState();
	}

	private static boolean isHoney(FluidState state) {
		return !state.isEmpty() && state.getType().isSame(ModContent.HONEY.get());
	}

	/** A stone floor at rel y=1 and a one-high wall ring at y=2 round the interior rectangle. */
	private static void basin(GameTestHelper helper, int x1, int z1, int x2, int z2) {
		for (int x = x1 - 1; x <= x2 + 1; x++) {
			for (int z = z1 - 1; z <= z2 + 1; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				boolean interior = x >= x1 && x <= x2 && z >= z1 && z <= z2;
				if (!interior) {
					helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
				}
			}
		}
	}

	/** A one-wide stone shaft with its floor at rel y=1, walls up to {@code topY + 1}. */
	private static void shaft(GameTestHelper helper, int x, int z, int topY) {
		helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
		for (int y = 1; y <= topY + 1; y++) {
			helper.setBlock(new BlockPos(x - 1, y, z), Blocks.STONE);
			helper.setBlock(new BlockPos(x + 1, y, z), Blocks.STONE);
			helper.setBlock(new BlockPos(x, y, z - 1), Blocks.STONE);
			helper.setBlock(new BlockPos(x, y, z + 1), Blocks.STONE);
		}
	}

	private static String f(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	/**
	 * @implements MOD-922-FUN01 — the honey bucket places a honey SOURCE that is in {@code c:honey}, and scooping
	 *     it back returns a full honey bucket and leaves the cell empty
	 */
	public static void bucketPlacesAndTakesBackATaggedSource(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos cell = new BlockPos(2, 2, 2);
		basin(helper, 2, 2, 2, 2);
		BlockPos abs = helper.absolutePos(cell);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);

		BucketItem bucket = (BucketItem) ModContent.HONEY_BUCKET.get();
		// The 4-argument emptyContents is vanilla's; NeoForge deprecates it for a stack-aware overload vanilla
		// does not declare, and this body is shared with Fabric (see OilScenarios#fun01BucketPlaceAndPickup).
		@SuppressWarnings("deprecation")
		boolean poured = bucket.emptyContents(player, level, abs, null);
		if (!poured) {
			helper.fail("the honey bucket refused to pour into an empty basin cell");
			return;
		}
		FluidState placed = level.getFluidState(abs);
		if (!placed.isSourceOfType(ModContent.HONEY.get())) {
			helper.fail("the bucket must place a honey SOURCE, got " + placed.getType());
			return;
		}
		if (!placed.is(C_HONEY)) {
			helper.fail("liquid honey must be in #c:honey, so other mods' machines take it — it is not");
			return;
		}
		BlockState state = level.getBlockState(abs);
		if (state.getBlock() != ModContent.HONEY_BLOCK.get()) {
			helper.fail("the placed block must be the honey liquid block, got " + state);
			return;
		}
		ItemStack taken = ((BucketPickup) state.getBlock()).pickupBlock(player, level, abs, state);
		if (!taken.is(ModContent.HONEY_BUCKET.get())) {
			helper.fail("pickupBlock must return a full honey bucket, got " + taken);
			return;
		}
		if (!level.getFluidState(abs).isEmpty()) {
			helper.fail("the cell must be empty after pickup, got " + level.getFluidState(abs).getType());
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-922-FUN02 — a pump drains a honey source in front of it and pushes the bucket into an
	 *     adjacent fluid tank
	 */
	public static void pumpDrainsHoneyIntoTank(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pumpRel = new BlockPos(2, 2, 2);
		BlockPos source = new BlockPos(3, 2, 2);
		BlockPos tankRel = new BlockPos(1, 2, 2);
		basin(helper, 2, 2, 3, 2);
		helper.setBlock(tankRel, ModContent.FLUID_TANK.get());
		helper.setBlock(pumpRel, ModContent.PUMP.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		level.setBlockAndUpdate(helper.absolutePos(source), honeySource());

		PumpBlockEntity pump = helper.getBlockEntity(pumpRel, PumpBlockEntity.class);
		FluidTankBlockEntity tank = helper.getBlockEntity(tankRel, FluidTankBlockEntity.class);
		if (pump == null || tank == null) {
			helper.fail("pump or fluid tank block entity missing after placement");
			return;
		}
		pump.getEnergyStorage().setAmountUntracked(Config.pumpEuPerBucket);

		helper.runAtTickTime(100, () -> {
			boolean drained = !level.getFluidState(helper.absolutePos(source)).isSourceOfType(ModContent.HONEY.get());
			boolean inTank = tank.fluidTank.amount == FluidAmounts.BUCKET
					&& tank.fluidTank.fluid.is(ModContent.HONEY.get());
			if (!drained || !inTank) {
				helper.fail("honey pool drain: sourceGone=" + drained + " tank=" + tank.fluidTank.amount + " mB of "
						+ tank.fluidTank.fluid + " pumpTank=" + pump.fluidTank.amount);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * @implements MOD-922-FUN03 — a honey pool spreads at overworld lava's pace (one step per 30 ticks) and as
	 *     far (flowing amounts 6, 4, 2, then nothing), while a water control has long spread by the first check
	 */
	public static void spreadsAtLavaPaceNotWaters(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		basin(helper, 2, 2, 6, 2);
		basin(helper, 2, 5, 6, 5);
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, 2, 2)), honeySource());
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, 2, 5)), Blocks.WATER.defaultBlockState());

		// Tick 15: water (5 ticks a step) has reached its neighbour; honey (30 a step) has not moved yet.
		helper.runAtTickTime(15, () -> {
			FluidState water = level.getFluidState(helper.absolutePos(new BlockPos(3, 2, 5)));
			if (water.isEmpty()) {
				helper.fail("the water control has not spread in 15 ticks — the rig is not measuring speed");
				return;
			}
			FluidState honey = level.getFluidState(helper.absolutePos(new BlockPos(3, 2, 2)));
			if (!honey.isEmpty()) {
				helper.fail("honey spread within 15 ticks (" + honey.getType() + " amount=" + honey.getAmount()
						+ ") — it must step every " + HoneyFluid.TICK_DELAY + " ticks, like lava, not like water");
			}
		});
		// Three steps at 30 ticks each; tick 160 leaves a wide settle margin.
		helper.runAtTickTime(160, () -> {
			int[] expected = {6, 4, 2};
			for (int i = 0; i < expected.length; i++) {
				FluidState fs = level.getFluidState(helper.absolutePos(new BlockPos(3 + i, 2, 2)));
				if (!isHoney(fs) || fs.isSource() || fs.getAmount() != expected[i]) {
					helper.fail("honey spread at distance " + (i + 1) + ": expected FLOWING honey amount " + expected[i]
							+ ", got " + (fs.isEmpty() ? "empty"
									: fs.getType() + " amount=" + fs.getAmount() + " source=" + fs.isSource()));
					return;
				}
			}
			FluidState beyond = level.getFluidState(helper.absolutePos(new BlockPos(6, 2, 2)));
			if (!beyond.isEmpty()) {
				helper.fail("honey must stop at distance 3, but distance 4 holds " + beyond.getType());
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * @implements MOD-922-FUN04 — an entity falling through honey is damped harder than through crude oil (the
	 *     thickest fluid before it) and keeps no fall distance; an air column is the control
	 */
	public static void fallIsDampedHarderThanInCrude(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		final int topY = 16;
		final BlockPos airColumn = new BlockPos(2, topY, 2);
		final BlockPos oilColumn = new BlockPos(5, topY, 2);
		final BlockPos honeyColumn = new BlockPos(2, topY, 5);
		for (BlockPos column : new BlockPos[] {airColumn, oilColumn, honeyColumn}) {
			shaft(helper, column.getX(), column.getZ(), topY);
		}
		for (int y = 2; y <= topY; y++) {
			level.setBlockAndUpdate(helper.absolutePos(oilColumn.atY(y)),
					ModContent.OIL_BLOCK.get().defaultBlockState());
			level.setBlockAndUpdate(helper.absolutePos(honeyColumn.atY(y)), honeySource());
		}
		Cow inAir = helper.spawn(EntityTypes.COW, airColumn);
		Cow inOil = helper.spawn(EntityTypes.COW, oilColumn);
		Cow inHoney = helper.spawn(EntityTypes.COW, honeyColumn);
		double startY = inAir.getY();

		// Tick 12: nothing has reached the floor of a 16-deep shaft yet.
		helper.runAtTickTime(12, () -> {
			for (Cow cow : new Cow[] {inAir, inOil, inHoney}) {
				if (!level.isPositionEntityTicking(cow.blockPosition())) {
					helper.fail("a column sits outside the force-loaded chunks, so its cow never ticks (MOD-507)");
					return;
				}
			}
			double air = startY - inAir.getY();
			double oil = startY - inOil.getY();
			double honey = startY - inHoney.getY();
			String measured = "air=" + f(air) + " oil=" + f(oil) + " honey=" + f(honey);
			final double margin = 0.2D;
			if (air - oil < margin) {
				helper.fail("the crude-oil control must damp a fall relative to air: " + measured);
				return;
			}
			if (oil - honey < margin) {
				helper.fail("honey (viscosity 6000) must damp a fall harder than crude oil (3000): " + measured
						+ " — honey has no immersion profile, or a thinner one than crude's");
				return;
			}
			if (inHoney.fallDistance > 0.0F) {
				helper.fail("an entity in honey must not build up fall distance, got " + inHoney.fallDistance);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * @implements MOD-922-FUN05 — an entity sliding along a honey-filled channel travels well under half as far
	 *     as the same push carries it along a dry channel (honey holds it back like a honey block)
	 */
	public static void slideIsHeldBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		basin(helper, 1, 2, 6, 2);
		basin(helper, 1, 5, 6, 5);
		for (int x = 1; x <= 6; x++) {
			level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 2, 2)), honeySource());
		}
		ArmorStand inHoney = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(1, 2, 2));
		ArmorStand dry = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(1, 2, 5));
		final double startHoney = inHoney.getX();
		final double startDry = dry.getX();
		final Vec3 push = new Vec3(0.6D, 0.0D, 0.0D);

		helper.runAtTickTime(2, () -> {
			inHoney.setDeltaMovement(push);
			dry.setDeltaMovement(push);
		});
		helper.runAtTickTime(20, () -> {
			double honey = inHoney.getX() - startHoney;
			double air = dry.getX() - startDry;
			String measured = "dry=" + f(air) + " honey=" + f(honey);
			if (air < 0.5D) {
				helper.fail("the dry control barely moved (" + measured + ") — the rig is not measuring a slide");
				return;
			}
			if (honey > air * 0.5D) {
				helper.fail("honey must hold a sliding entity back to well under half the dry distance: " + measured);
				return;
			}
			helper.succeed();
		});
	}

	/** Keeps a mob with no goals of its own walking toward {@code target} (absolute) at {@code speed}, every tick. */
	private static void walkEveryTick(GameTestHelper helper, Cow cow, Vec3 target, double speed) {
		cow.removeFreeWill();
		helper.onEachTick(() -> cow.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed));
	}

	/**
	 * @implements MOD-922-FUN06 — a mob walking along the floor of a honey channel covers well under half the
	 *     distance of the same walk along a dry channel: honey scales the whole move, input included, the way a
	 *     cobweb does, and a drag on the momentum alone cannot get a walk below about half speed
	 */
	public static void mobWalksFourTimesSlower(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		basin(helper, 1, 2, 6, 2);
		basin(helper, 1, 5, 6, 5);
		for (int x = 1; x <= 6; x++) {
			level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 2, 2)), honeySource());
		}
		Cow inHoney = helper.spawn(EntityTypes.COW, new BlockPos(1, 2, 2));
		Cow dry = helper.spawn(EntityTypes.COW, new BlockPos(1, 2, 5));
		walkEveryTick(helper, inHoney, Vec3.atCenterOf(helper.absolutePos(new BlockPos(7, 2, 2))), 1.0D);
		walkEveryTick(helper, dry, Vec3.atCenterOf(helper.absolutePos(new BlockPos(7, 2, 5))), 1.0D);
		final double[] start = new double[2];
		helper.runAtTickTime(5, () -> {
			start[0] = inHoney.getX();
			start[1] = dry.getX();
		});
		helper.runAtTickTime(15, () -> {
			double honey = inHoney.getX() - start[0];
			double air = dry.getX() - start[1];
			String measured = "dry=" + f(air) + " honey=" + f(honey) + " over 10 ticks";
			if (air < 0.5D) {
				helper.fail("the dry control barely walked (" + measured + ") — the rig is not measuring a walk");
				return;
			}
			if (honey <= 0.0D) {
				helper.fail("a mob in honey must still be able to walk, it did not move: " + measured);
				return;
			}
			// Steady state is 0.55 / 2.2 = 0.25 of the dry walk; drag alone (before 2026-10-30) gave about 0.58.
			if (honey > air * 0.40D) {
				helper.fail("honey must slow a walking mob to well under half its dry pace (about a quarter): "
						+ measured + " — stuck share " + FluidImmersion.HONEY.stuckHorizontal());
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * @implements MOD-922-FUN07 — a mob in a one-deep honey pool can still climb onto the rim by jumping: honey
	 *     is slow, never a trap (MOD-496)
	 */
	public static void mobClimbsOutOfAOneDeepPool(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		basin(helper, 2, 2, 4, 2);
		for (int x = 2; x <= 4; x++) {
			level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 2, 2)), honeySource());
		}
		// From the far end: the cow wades two blocks along the floor first, then jumps at the rim, as a player would.
		Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(2, 2, 2));
		// The rim cell's top: the wall of the ring at rel (5, 2, 2) is one block high.
		BlockPos rim = helper.absolutePos(new BlockPos(5, 3, 2));
		walkEveryTick(helper, cow, Vec3.atBottomCenterOf(rim), 1.0D);
		helper.onEachTick(() -> {
			if (cow.horizontalCollision) {
				cow.getJumpControl().jump();
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(
				cow.getY() >= rim.getY() - 1.0E-3D && cow.onGround() && cow.getX() >= rim.getX() - 0.5D,
				"the cow is still in the honey pool: y=" + f(cow.getY()) + " x=" + f(cow.getX()) + ", the rim top is y="
						+ rim.getY() + " — honey must let a mob jump out, slowly"));
	}
}
