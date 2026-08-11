package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametest bodies for the Diamond-Tipped Electric Hoe (MOD-378, suite TC-HOE-001). Same
 * pattern as {@link ElectricChainsawScenarios}: plain {@code GameTestHelper} bodies wrapped by the Fabric
 * {@code ElectricHoeGameTest} suite and registered on the NeoForge {@code gameTestServer} lane via
 * {@code NeoForgeGameTests} — both loaders run the SAME logic.
 *
 * <p>Numbers come from {@link Config} (electricHoeBuffer, electricHoeEuPerBlock, electricHoeTillEuCost),
 * and moisture is read back off the real {@code FarmlandBlock} state rather than from a re-implementation
 * of what vanilla is believed to store.
 *
 * <p><b>Why FUN03 and FUN04 exist.</b> FUN02 on its own is a test that cannot fail for the right reason:
 * if the perk were accidentally moved into the base hoe, or if the watering fired on any consumed click
 * instead of on a real till, FUN02 would stay green. FUN03 pins the perk to the upgrade (the base hoe must
 * leave the plot dry) and FUN04 pins it to an actually-paid till — see its javadoc for the specific
 * 26.2 trap it guards.
 */
public final class ElectricHoeScenarios {

	private ElectricHoeScenarios() {}

	/** The plot that gets tilled. Air is forced above it: vanilla's grass/dirt tillables are gated on
	 * {@code HoeItem.onlyIfAirAbove}. */
	private static final BlockPos SOIL = new BlockPos(1, 2, 1);

	private static ItemStack hoe(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTRIC_HOE.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	private static ItemStack diamondTipHoe(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTRIC_HOE_DIAMOND_TIP.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/**
	 * A survival ServerPlayer mock with {@code instabuild} forced off — the
	 * {@code makeMockServerPlayerInLevel} default leaves it on (hardcoded CREATIVE gameMode), which would
	 * make every EU assertion meaningless (MOD-081 drops the drain in creative) and would skip the hoe's
	 * charge gate entirely.
	 */
	private static ServerPlayer makeSurvivalPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		return player;
	}

	/**
	 * Simulates a right-click of the main-hand stack on the top face of {@link #SOIL}.
	 *
	 * <p><b>Driven through {@code player.gameMode.useItemOn}, not {@code stack.useOn(new UseOnContext(…))}
	 * — the MOD-310 lesson, re-learned here.</b> The direct-{@code useOn} shortcut passes on Fabric and
	 * fails on NeoForge: NeoForge replaces {@code ItemStack#useOn} with its own hook, so the vanilla
	 * tilling chain never ran and the plot stayed dirt. This body runs on both lanes, so it has to take
	 * the full vanilla path — {@code BlockState.useItemOn} → {@code useWithoutItem} → PASS →
	 * {@code ItemStack.useOn} → our {@code useOn} — which behaves the same on both loaders.
	 */
	private static InteractionResult useOnSoil(GameTestHelper helper, ServerPlayer player) {
		BlockPos abs = helper.absolutePos(SOIL);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		return player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
				InteractionHand.MAIN_HAND, hit);
	}

	/** Clears the plot and puts {@code soil} there with guaranteed air above it. */
	private static void prepareSoil(GameTestHelper helper, Block soil) {
		helper.setBlock(SOIL, soil);
		helper.setBlock(SOIL.above(), Blocks.AIR);
	}

	private static int moistureAtSoil(GameTestHelper helper) {
		BlockState state = helper.getLevel().getBlockState(helper.absolutePos(SOIL));
		if (!state.hasProperty(FarmlandBlock.MOISTURE)) {
			helper.fail("expected farmland at the tilled plot, found " + state.getBlock());
		}
		return state.getValue(FarmlandBlock.MOISTURE);
	}

	private static void assertCorrect(GameTestHelper helper, ItemStack stack, BlockState state, String name,
			boolean expected) {
		if (stack.isCorrectToolForDrops(state) != expected) {
			helper.fail("isCorrectToolForDrops(" + name + ") must be " + expected);
		}
	}

	// ── MOD-378 — diamond-tipped upgrade ────────────────────────────────────────────────────────────

	/**
	 * TC-HOE-001-FUN01 — the upgrade breaks {@code #mineable/hoe} at 10.5 against the base hoe's 9.0, is
	 * strictly faster than the base (so the test reddens if the base is ever bumped to match), still drops
	 * to exactly hand speed when flat, and does NOT change mining tier.
	 */
	public static void fun01DiamondTipSpeedAndTier(GameTestHelper helper) {
		Item tip = ModContent.ELECTRIC_HOE_DIAMOND_TIP.get();
		Item base = ModContent.ELECTRIC_HOE.get();
		BlockState hay = Blocks.HAY_BLOCK.defaultBlockState();

		float charged = tip.getDestroySpeed(diamondTipHoe(Config.electricHoeBuffer), hay);
		if (charged != 10.5f) {
			helper.fail("a charged diamond-tipped hoe must break hay at 10.5, got " + charged);
		}
		float baseSpeed = base.getDestroySpeed(hoe(Config.electricHoeBuffer), hay);
		if (!(charged > baseSpeed)) {
			helper.fail("the upgrade must out-cut the base hoe, got " + charged + " vs base " + baseSpeed);
		}
		float flat = tip.getDestroySpeed(diamondTipHoe(0), hay);
		if (flat != 1.0f) {
			helper.fail("a flat diamond-tipped hoe must break at exactly hand speed 1.0, got " + flat);
		}

		// The tier is unchanged: it is still a hoe, and a hoe is not a pickaxe.
		ItemStack chargedStack = diamondTipHoe(Config.electricHoeBuffer);
		assertCorrect(helper, chargedStack, hay, "hay_block", true);
		assertCorrect(helper, chargedStack, Blocks.STONE.defaultBlockState(), "stone", false);
		helper.succeed();
	}

	/**
	 * TC-HOE-001-FUN02 — tilling with the upgrade leaves the fresh plot at full moisture. Asserted against
	 * {@link FarmlandBlock#MAX_MOISTURE} rather than the literal 7, so the test tracks vanilla instead of
	 * freezing a number vanilla owns.
	 */
	public static void fun02DiamondTipTillLeavesPlotWatered(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, diamondTipHoe(Config.electricHoeBuffer));
		prepareSoil(helper, Blocks.DIRT);

		useOnSoil(helper, player);

		int moisture = moistureAtSoil(helper);
		if (moisture != FarmlandBlock.MAX_MOISTURE) {
			helper.fail("a plot tilled by the upgrade must start at moisture "
					+ FarmlandBlock.MAX_MOISTURE + ", got " + moisture);
		}
		helper.succeed();
	}

	/**
	 * TC-HOE-001-FUN03 — negative control for FUN02: the BASE hoe waters nothing. Vanilla farmland is
	 * created dry ({@code MOISTURE = 0} is {@code FarmlandBlock}'s registered default), so if this ever
	 * goes green with a non-zero reading, the perk has leaked into the base class and FUN02 alone would
	 * never have noticed.
	 */
	public static void fun03BaseHoeLeavesPlotDry(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, hoe(Config.electricHoeBuffer));
		prepareSoil(helper, Blocks.DIRT);

		useOnSoil(helper, player);

		int moisture = moistureAtSoil(helper);
		if (moisture != 0) {
			helper.fail("a plot tilled by the BASE hoe must stay dry (moisture 0), got " + moisture);
		}
		helper.succeed();
	}

	/**
	 * TC-HOE-001-FUN04 — a flat upgrade cannot water existing farmland.
	 *
	 * <p>This is the guard for a real 26.2 trap, not a hypothetical one: {@code InteractionResult.CONSUME}
	 * is an {@code InteractionResult.Success}, so {@code consumesAction()} returns {@code true} for it —
	 * and {@code ElectricHoeItem.useOn} returns exactly {@code CONSUME} on its "not enough charge" path.
	 * An implementation that watered on {@code consumesAction()} alone would therefore hand a player with
	 * a dead battery unlimited free irrigation of any plot they click. The upgrade instead only waters a
	 * block that was not farmland before the click, which is what this asserts.
	 *
	 * <p>Since MOD-389 the click in this scenario also stops one step earlier — farmland is not tillable,
	 * so {@code useOn} answers {@code PASS} before the charge gate is even reached. The assertion is kept
	 * exactly as it was: it must hold whichever layer stops the exploit, and it is the only one of the two
	 * that survives a future reordering of the gate.
	 */
	public static void fun04FlatUpgradeCannotWaterExistingFarmland(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, diamondTipHoe(0));
		prepareSoil(helper, Blocks.FARMLAND);

		if (moistureAtSoil(helper) != 0) {
			helper.fail("fixture error: freshly placed farmland must start dry");
		}

		useOnSoil(helper, player);

		int moisture = moistureAtSoil(helper);
		if (moisture != 0) {
			helper.fail("a flat upgrade must not water existing farmland, moisture became " + moisture);
		}
		helper.succeed();
	}

	// ── MOD-389 — a flat hoe must not swallow every right-click ─────────────────────────────────────

	/**
	 * TC-HOE-001-NEG01 — a flat hoe right-clicking a block no hoe can till answers {@code PASS}.
	 *
	 * <p>The shipped defect this pins: the charge gate used to run <b>before</b> anything looked at the
	 * block, so an empty hoe returned {@code CONSUME} for a click on stone — which in 26.2 is an
	 * {@code InteractionResult.Success}, i.e. "handled". The player got a red "not enough charge" line
	 * they never asked for, and the click never reached the off-hand: no block placed, no food eaten.
	 *
	 * <p>Asserting on the {@code InteractionResult} <b>is</b> the off-hand assertion — that result is
	 * exactly what {@code ServerPlayerGameMode.useItemOn} returns to the interaction chain, and
	 * {@code PASS} is the only value that lets the chain continue. Run against the pre-MOD-389 code this
	 * body fails on the first check.
	 */
	public static void neg01FlatHoeOnNonTillableDoesNotSwallowClick(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, hoe(0));
		prepareSoil(helper, Blocks.STONE);

		InteractionResult result = useOnSoil(helper, player);

		if (result != InteractionResult.PASS) {
			helper.fail("a flat hoe clicking stone must return PASS so the off-hand still runs, got " + result);
		}
		helper.assertBlockPresent(Blocks.STONE, SOIL);
		if (ItemEnergy.get(player.getMainHandItem()) != 0) {
			helper.fail("fixture error: the hoe under test must be flat");
		}
		helper.succeed();
	}

	/**
	 * TC-HOE-001-FUN05 — the other half of NEG01: on a block a hoe <i>can</i> till, a flat hoe still
	 * refuses loudly. {@code CONSUME} is the "handled, and I told you why" answer, and the plot must stay
	 * dirt.
	 *
	 * <p>Without this, MOD-389 could have been "fixed" by returning {@code PASS} everywhere when flat —
	 * green NEG01, and the player silently loses the only feedback that says the hoe needs charging.
	 */
	public static void fun05FlatHoeOnTillableStillRefuses(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, hoe(0));
		prepareSoil(helper, Blocks.DIRT);

		InteractionResult result = useOnSoil(helper, player);

		if (result != InteractionResult.CONSUME) {
			helper.fail("a flat hoe clicking tillable dirt must return CONSUME (refusal + message), got " + result);
		}
		helper.assertBlockPresent(Blocks.DIRT, SOIL);
		helper.succeed();
	}
}
