package dev.alaindustrial.gametest;

import dev.alaindustrial.block.TrellisBlock;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

/**
 * Loader-neutral gametest bodies for the trellis (MOD-280). The Fabric {@code @GameTest} suite
 * ({@code TrellisGameTest}) and the NeoForge {@code gameTestServer} lane
 * ({@code dev.alaindustrial.gametest.neoforge.NeoForgeGameTests}) run the SAME bodies, so the crop
 * behaves identically on both loaders.
 *
 * <p><b>Four of these are regression guards</b> for bugs found in play testing, and each is written to
 * fail loudly if the bug comes back:
 * <ul>
 *   <li>{@link #reg01FarmlandSurvivesUnderTrellis} — the trellis is a collidable block, and vanilla
 *       farmland turns to dirt under a <em>solid</em> one ({@code FarmlandBlock.canSurvive}). Without
 *       the {@code #minecraft:maintains_farmland} tag the plant lost its soil and destroyed itself one
 *       tick after being placed.</li>
 *   <li>{@link #reg02BreakDropsExactlyOneTrellis} — both halves used to pay out, so every break gave
 *       two trellises. The fix lives in the loot table ({@code half=lower}), which is where vanilla
 *       solves it for its own two-block plants.</li>
 *   <li>{@link #reg03HarvestWorksWithSeedsInHand} — {@code useItemOn} returned {@code PASS}, and the
 *       game only falls through to {@code useWithoutItem} on {@code TRY_WITH_EMPTY_HAND}. Holding
 *       seeds made the harvest unreachable.</li>
 *   <li>{@link #reg04PlantedTrellisReturnsSeed} — breaking a planted trellis must hand the seed back;
 *       a bare one must not, or sticks + string would mint free seeds.</li>
 * </ul>
 *
 * <p><b>Growth is driven through the real {@code randomTick}</b> rather than by calling the mod's own
 * grow helper: a test that only invokes {@code TrellisBlock.grow} would still pass if the block
 * were never wired into the random-tick pool at all (a missing {@code randomTicks()} in the manifest —
 * exactly the kind of silent break this suite exists to catch).
 */
public final class TrellisScenarios {

	private TrellisScenarios() {
	}

	private static final int FLOOR = 1;
	/** Lower half of the trellis; the farmland sits at {@code FLOOR}, the upper half at {@code +2}. */
	private static final BlockPos POS = new BlockPos(2, FLOOR + 1, 2);

	/** Random ticks to drive. At a 1-in-12 rooting chance, missing 300 rolls is ~1e-11 — not flaky. */
	private static final int TICKS = 300;

	// ---------------------------------------------------------------- planting

	/** Right-clicking a seed onto a bare trellis plants it and spends exactly one seed. */
	public static void fun01PlantsSeed(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		placeTrellis(helper, 7);

		ItemStack seeds = new ItemStack(ModContent.COTTON_SEEDS.get(), 4);
		player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
		useOn(helper, player, POS);

		int age = age(helper, POS);
		if (age != 1) {
			helper.fail("seed did not plant: age=" + age + ", expected 1");
			return;
		}
		if (seeds.getCount() != 3) {
			helper.fail("planting spent " + (4 - seeds.getCount()) + " seeds, expected exactly 1");
			return;
		}
		helper.succeed();
	}

	/** A trellis that already carries a plant must not silently eat another seed. */
	public static void neg01KeepsSeedOnPlantedTrellis(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		placeTrellis(helper, 7);
		setAge(helper, POS, 2);

		ItemStack seeds = new ItemStack(ModContent.COTTON_SEEDS.get(), 4);
		player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
		useOn(helper, player, POS);

		if (seeds.getCount() != 4) {
			helper.fail("clicking a planted trellis ate a seed (" + seeds.getCount() + " left of 4)");
			return;
		}
		if (age(helper, POS) != 2) {
			helper.fail("clicking a planted trellis changed its age");
			return;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- growth

	/** On moist farmland the plant advances through the real random tick. */
	public static void fun02GrowsOnMoistFarmland(GameTestHelper helper) {
		placeTrellis(helper, 7);
		setAge(helper, POS, 1);

		driveRandomTicks(helper, TICKS);

		if (age(helper, POS) <= 1) {
			helper.fail("plant did not grow on moist farmland after " + TICKS + " random ticks");
			return;
		}
		helper.succeed();
	}

	/** Dry soil pauses growth — the stage must be kept, not lost, and the plant must stay alive. */
	public static void neg02PausesOnDryFarmland(GameTestHelper helper) {
		placeTrellis(helper, 0);
		setAge(helper, POS, 2);

		driveRandomTicks(helper, TICKS);

		if (!isTrellis(helper, POS)) {
			helper.fail("plant died on dry soil — it must only pause");
			return;
		}
		if (age(helper, POS) != 2) {
			helper.fail("dry soil changed the stage to " + age(helper, POS) + ", expected it kept at 2");
			return;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- harvest

	/** Picking a ripe bush yields fibre, leaves the block standing and winds it back to mature. */
	public static void fun03HarvestKeepsPlant(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		placeTrellis(helper, 7);
		setAge(helper, POS, TrellisBlock.MAX_AGE);

		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		useOn(helper, player, POS);

		if (!isTrellis(helper, POS)) {
			helper.fail("harvest destroyed the plant — it must survive being picked");
			return;
		}
		if (age(helper, POS) != TrellisBlock.AGE_MATURE) {
			helper.fail("after harvest age=" + age(helper, POS)
					+ ", expected " + TrellisBlock.AGE_MATURE);
			return;
		}
		if (countDrops(helper, ModContent.COTTON_FIBER.get()) < 1) {
			helper.fail("harvest dropped no cotton fibre");
			return;
		}
		helper.succeed();
	}

	/** An unripe bush must not pay out and must not change state. */
	public static void neg03UnripeYieldsNothing(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		placeTrellis(helper, 7);
		setAge(helper, POS, TrellisBlock.AGE_MATURE);

		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		useOn(helper, player, POS);

		if (countDrops(helper, ModContent.COTTON_FIBER.get()) != 0) {
			helper.fail("an unripe bush dropped fibre");
			return;
		}
		if (age(helper, POS) != TrellisBlock.AGE_MATURE) {
			helper.fail("clicking an unripe bush changed its age");
			return;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- regression guards

	/** Farmland must survive under the (collidable) trellis, or the plant loses its soil and dies. */
	public static void reg01FarmlandSurvivesUnderTrellis(GameTestHelper helper) {
		placeTrellis(helper, 7);
		BlockPos soil = POS.below();

		// A neighbour update is what makes vanilla re-check the farmland, so poke it and tick.
		helper.getLevel().neighborChanged(helper.absolutePos(soil),
				Blocks.AIR, null);
		driveRandomTicks(helper, 20);

		if (!helper.getBlockState(soil).is(Blocks.FARMLAND)) {
			helper.fail("farmland under the trellis turned to "
					+ helper.getBlockState(soil).getBlock() + " — the maintains_farmland tag is missing");
			return;
		}
		if (!isTrellis(helper, POS)) {
			helper.fail("trellis destroyed itself after being placed");
			return;
		}
		helper.succeed();
	}

	/** Breaking the structure must drop exactly ONE trellis — both halves used to pay out. */
	public static void reg02BreakDropsExactlyOneTrellis(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		placeTrellis(helper, 7);

		player.gameMode.destroyBlock(helper.absolutePos(POS));

		int dropped = countDrops(helper, ModContent.TRELLIS_ITEM.get());
		if (dropped != 1) {
			helper.fail("breaking the trellis dropped " + dropped + " items, expected exactly 1");
			return;
		}
		if (isTrellis(helper, POS) || isTrellis(helper, POS.above())) {
			helper.fail("half of the structure survived the break");
			return;
		}
		helper.succeed();
	}

	/** The harvest must work with seeds in hand — PASS used to swallow the interaction. */
	public static void reg03HarvestWorksWithSeedsInHand(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		placeTrellis(helper, 7);
		setAge(helper, POS, TrellisBlock.MAX_AGE);

		ItemStack seeds = new ItemStack(ModContent.COTTON_SEEDS.get(), 4);
		player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
		useOn(helper, player, POS);

		if (countDrops(helper, ModContent.COTTON_FIBER.get()) < 1) {
			helper.fail("no fibre dropped while holding seeds — useItemOn swallowed the interaction");
			return;
		}
		if (seeds.getCount() != 4) {
			helper.fail("harvesting with seeds in hand consumed a seed");
			return;
		}
		helper.succeed();
	}

	/** A planted trellis returns its seed on break; a bare one must not (that would mint seeds). */
	public static void reg04PlantedTrellisReturnsSeed(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		placeTrellis(helper, 7);
		setAge(helper, POS, 3);

		player.gameMode.destroyBlock(helper.absolutePos(POS));

		if (countDrops(helper, ModContent.COTTON_SEEDS.get()) != 1) {
			helper.fail("breaking a planted trellis returned "
					+ countDrops(helper, ModContent.COTTON_SEEDS.get()) + " seeds, expected 1");
			return;
		}
		helper.succeed();
	}

	/** Breaking a BARE trellis must return no seed — otherwise sticks + string mint seeds for free. */
	public static void neg04BareTrellisReturnsNoSeed(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		placeTrellis(helper, 7);

		player.gameMode.destroyBlock(helper.absolutePos(POS));

		int seeds = countDrops(helper, ModContent.COTTON_SEEDS.get());
		if (seeds != 0) {
			helper.fail("breaking a bare trellis minted " + seeds + " seed(s)");
			return;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- bone meal

	/** Bone meal buys exactly one stage — never a jump straight to ripe. */
	public static void fun04BonemealAdvancesOneStage(GameTestHelper helper) {
		placeTrellis(helper, 7);
		setAge(helper, POS, AGE_SET_TEST);

		applyBonemeal(helper);

		if (age(helper, POS) != AGE_SET_TEST + 1) {
			helper.fail("bone meal moved the plant to age=" + age(helper, POS)
					+ ", expected exactly one stage (" + (AGE_SET_TEST + 1) + ")");
			return;
		}
		helper.succeed();
	}

	/**
	 * Bone meal must obey the same water/light gate as ordinary growth. Without this a player could farm
	 * cotton forever on dry farmland, which contradicts what both specs promise.
	 */
	public static void neg05BonemealRefusesDrySoil(GameTestHelper helper) {
		placeTrellis(helper, 0);
		setAge(helper, POS, AGE_SET_TEST);

		applyBonemeal(helper);

		if (age(helper, POS) != AGE_SET_TEST) {
			helper.fail("bone meal grew the plant on dry soil (age=" + age(helper, POS)
					+ ") — it must obey the same gate as normal growth");
			return;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- more coverage

	/** The fruiting cycle repeats: a second harvest works after the plant ripens again. */
	public static void fun05SecondHarvestCycle(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		placeTrellis(helper, 7);
		setAge(helper, POS, TrellisBlock.MAX_AGE);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

		useOn(helper, player, POS);
		int afterFirst = countDrops(helper, ModContent.COTTON_FIBER.get());
		if (afterFirst < 1) {
			helper.fail("first harvest produced nothing");
			return;
		}

		// Let it ripen again through the real random tick, then pick a second time.
		driveRandomTicks(helper, TICKS);
		if (age(helper, POS) != TrellisBlock.MAX_AGE) {
			helper.fail("plant did not ripen again after the first harvest (age=" + age(helper, POS) + ")");
			return;
		}
		useOn(helper, player, POS);

		if (countDrops(helper, ModContent.COTTON_FIBER.get()) <= afterFirst) {
			helper.fail("second harvest produced nothing — the fruiting cycle does not repeat");
			return;
		}
		helper.succeed();
	}

	/**
	 * Breaking the UPPER half must still pay exactly one trellis. That path runs through
	 * {@code updateShape → destroyBlock}, not through the player-break path, so the {@code half=lower}
	 * loot condition is the only thing keeping it at one — and the only thing keeping it above zero.
	 */
	public static void reg05BreakingUpperHalfDropsOne(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		placeTrellis(helper, 7);

		player.gameMode.destroyBlock(helper.absolutePos(POS.above()));

		int dropped = countDrops(helper, ModContent.TRELLIS_ITEM.get());
		if (dropped != 1) {
			helper.fail("breaking the upper half dropped " + dropped + " trellises, expected exactly 1");
			return;
		}
		if (isTrellis(helper, POS) || isTrellis(helper, POS.above())) {
			helper.fail("half of the structure survived breaking the upper half");
			return;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- rig

	/** A mid-rooting stage used by the bone-meal scenarios: far enough from both ends to be unambiguous. */
	private static final int AGE_SET_TEST = 2;

	/** Apply one bone meal through the vanilla item, so the block's own gate decides the outcome. */
	private static void applyBonemeal(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL, 8));
		useOn(helper, player, POS);
	}

	/** Farmland at {@code FLOOR} with the given moisture, and a bare two-block trellis on top. */
	private static void placeTrellis(GameTestHelper helper, int moisture) {
		helper.setBlock(POS.below(),
				Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, moisture));
		DoublePlantBlock.placeAt(helper.getLevel(),
				ModContent.TRELLIS.get().defaultBlockState(),
				helper.absolutePos(POS), 3);
	}

	/** Write the age onto both halves, the way the block itself does. */
	private static void setAge(GameTestHelper helper, BlockPos lower, int age) {
		for (BlockPos p : new BlockPos[]{lower, lower.above()}) {
			BlockState state = helper.getBlockState(p);
			if (state.is(ModContent.TRELLIS.get())) {
				helper.setBlock(p, state.setValue(TrellisBlock.AGE, age));
			}
		}
	}

	private static int age(GameTestHelper helper, BlockPos pos) {
		BlockState state = helper.getBlockState(pos);
		return state.is(ModContent.TRELLIS.get()) ? state.getValue(TrellisBlock.AGE) : -1;
	}

	private static boolean isTrellis(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos).is(ModContent.TRELLIS.get());
	}

	/**
	 * Drive the real {@code BlockState.randomTick} on the lower half. Deterministic seed so a failure
	 * reproduces; the roll count is high enough that a healthy plant cannot miss by chance.
	 */
	private static void driveRandomTicks(GameTestHelper helper, int times) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(POS);
		RandomSource random = RandomSource.create(280L);
		for (int i = 0; i < times; i++) {
			BlockState state = level.getBlockState(abs);
			if (!state.is(ModContent.TRELLIS.get())) {
				return;
			}
			state.randomTick(level, abs, random);
		}
	}

	/**
	 * Total count of {@code item} dropped by THIS rig — {@code assertItemEntityPresent} can only answer
	 * present/absent, and these scenarios need the exact number.
	 *
	 * <p>The radius is deliberate, and both extremes were tried and rejected on the NeoForge lane:
	 * <ul>
	 *   <li>{@code inflate(6.0)} was too wide — gametests sit only ~6 blocks apart, so the box reached
	 *       into the neighbouring test and counted <em>its</em> drops ("a planted trellis returned 2
	 *       seeds" was this rig plus the one next door, each seeing the other's seed). It stayed green
	 *       on Fabric, whose layout is sparser — a loader-specific false pass.</li>
	 *   <li>{@link GameTestHelper#getBounds()} was too tight — the NeoForge lane registers these bodies
	 *       from code with a minimal structure, whose bounds do not cover the rig at all, so every count
	 *       came back 0.</li>
	 * </ul>
	 * A radius of 2 spans well under the inter-test spacing while comfortably containing anything
	 * {@code popResource} scatters around the plant.
	 */
	private static int countDrops(GameTestHelper helper, Item item) {
		AABB box = new AABB(helper.absolutePos(POS)).inflate(2.0);
		int total = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (entity.getItem().is(item)) {
				total += entity.getItem().getCount();
			}
		}
		return total;
	}

	/**
	 * Right-click the block through the real interaction path ({@code ServerPlayerGameMode.useItemOn}),
	 * not through {@code ItemStack.useOn}: only the former reproduces the {@code useItemOn} →
	 * {@code useWithoutItem} fall-through that {@link #reg03HarvestWorksWithSeedsInHand} guards.
	 */
	private static void useOn(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
		BlockPos abs = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
				InteractionHand.MAIN_HAND, hit);
	}

	/** Guard against a silent property rename breaking every scenario above. */
	static {
		if (TrellisBlock.AGE.getPossibleValues().size() != TrellisBlock.MAX_AGE + 1) {
			throw new IllegalStateException("AGE range changed — review the trellis scenarios");
		}
	}
}
