package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

/**
 * Loader-neutral gametest bodies for the Electric Shovel (suite TC-SHOVEL-001) — its right-click
 * interactions (MOD-379) and, since MOD-364, the base tool's own EU contract. Same pattern as
 * {@link ElectricHoeScenarios}: plain {@code GameTestHelper} bodies
 * wrapped by the Fabric {@code ElectricShovelGameTest} suite and registered on the NeoForge
 * {@code gameTestServer} lane via {@code NeoForgeGameTests} — both loaders run the SAME logic.
 *
 * <h2>Why this suite exists at all</h2>
 * The shovel shipped in MOD-338 with <b>no gametest on either loader</b>, and that gap is the entire
 * reason MOD-379 reached players: on NeoForge the shovel could not make a dirt path or douse a campfire
 * from the day it was released. The two loaders reach the same behaviour by different routes — Fabric's
 * vanilla {@code ShovelItem.useOn} reads the static {@code FLATTENABLES} map and handles the campfire
 * inline, while NeoForge's patched copy asks the block through
 * {@code getToolModifiedState(SHOVEL_FLATTEN / SHOVEL_DOUSE)}, which is gated on the held item declaring
 * the ability. These bodies assert the observable result, which is identical on both, so one suite pins
 * both routes.
 *
 * <h2>Why the click is driven through {@code player.gameMode.useItemOn}</h2>
 * Inherited from MOD-310 and re-learned in MOD-378: a shortcut through
 * {@code stack.useOn(new UseOnContext(…))} would pass on both lanes <i>even while the defect is
 * present</i>, because it bypasses the loader's own use hook. Only the full vanilla path exercises what
 * a player's right-click actually does.
 *
 * <h2>Why FUN02 exists</h2>
 * FUN01 alone cannot distinguish "our shovel is broken" from "the rig cannot path grass at all". FUN02
 * runs a vanilla {@code minecraft:diamond_shovel} through the identical fixture: it is the oracle that
 * keeps FUN01 honest, and it was exactly this comparison that proved the sibling hoe defect in-world in
 * MOD-378.
 *
 * <h2>Where the base EU contract lives</h2>
 * The six forms shared with the chainsaw and the hoe (charging, drain, hand-speed collapse, free
 * instant-break blocks, speed/drops, persistence) are written once in
 * {@link ElectricToolEnergyScenarios}. What stays here is {@link #ENERGY}, the one place in the repo
 * that says which tool those forms are run against for the shovel.
 */
public final class ElectricShovelScenarios {

	private ElectricShovelScenarios() {}

	/**
	 * The shovel's slice of the shared EU contract (MOD-364), declared here and nowhere else so that
	 * naming the wrong tool would mean writing {@code ModContent.ELECTRIC_SHOVEL} inside another tool's
	 * suite. {@link ElectricToolEnergyScenarios#energyCaseRosterIsHonest} checks every field of it against
	 * the real item.
	 *
	 * <p>Fixtures: dirt is the shovel's own {@code #mineable/shovel} domain; stone is the negative; a
	 * snow layer (hardness 0.1 in the 26.2 sources) is the soft block, which is exactly the case
	 * {@code ElectricShovelItem.mineBlock}'s javadoc calls out as NOT free after MOD-389 corrected the
	 * opposite claim.
	 */
	public static final ElectricToolEnergyScenarios.ToolCase ENERGY =
			new ElectricToolEnergyScenarios.ToolCase(
					"electric_shovel",
					ModContent.ELECTRIC_SHOVEL,
					() -> Config.electricShovelEuPerBlock,
					() -> Config.electricShovelBuffer,
					() -> Config.electricShovelInputRate,
					() -> Blocks.DIRT,
					() -> Blocks.STONE,
					() -> Blocks.SNOW,
					9.0f);

	/** The block that gets flattened or doused. Air is forced above it: vanilla only paths a block with
	 * air on top ({@code ShovelItem.useOn} gates the flatten branch on
	 * {@code level.getBlockState(pos.above()).isAir()}). */
	private static final BlockPos GROUND = new BlockPos(1, 2, 1);

	private static ItemStack shovel(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTRIC_SHOVEL.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/** Simulates a right-click of the main-hand stack on the TOP face of {@link #GROUND} — the face
	 * matters, {@code ShovelItem.useOn} returns {@code PASS} outright for {@code Direction.DOWN}. */
	private static void useOnGround(GameTestHelper helper, ServerPlayer player) {
		BlockPos abs = helper.absolutePos(GROUND);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
				InteractionHand.MAIN_HAND, hit);
	}

	/** Puts {@code ground} at the plot with guaranteed air above it. */
	private static void prepareGround(GameTestHelper helper, Block ground) {
		helper.setBlock(GROUND, ground);
		helper.setBlock(GROUND.above(), Blocks.AIR);
	}

	private static Block blockAtGround(GameTestHelper helper) {
		return helper.getLevel().getBlockState(helper.absolutePos(GROUND)).getBlock();
	}

	private static boolean litAtGround(GameTestHelper helper) {
		BlockState state = helper.getLevel().getBlockState(helper.absolutePos(GROUND));
		if (!state.hasProperty(CampfireBlock.LIT)) {
			helper.fail("expected a campfire at the plot, found " + state.getBlock());
		}
		return state.getValue(CampfireBlock.LIT);
	}

	// ── MOD-379 — right-click interactions on both loaders ───────────────────────────────────────────

	/**
	 * TC-SHOVEL-001-FUN01 — right-clicking grass with the Electric Shovel leaves a dirt path.
	 *
	 * <p>This is the MOD-379 regression itself. Before the fix it is red on the NeoForge lane and green
	 * on Fabric, which is precisely the shape of the shipped defect: the delegation to
	 * {@code Items.DIAMOND_SHOVEL.useOn} does not help there, because the ability gate reads
	 * {@code context.getItemInHand()} — still the electric shovel — and answers {@code false}.
	 */
	public static void fun01ShovelMakesDirtPath(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, shovel(Config.electricShovelBuffer));
		prepareGround(helper, Blocks.GRASS_BLOCK);

		if (blockAtGround(helper) != Blocks.GRASS_BLOCK) {
			helper.fail("fixture error: the plot must start as grass, found " + blockAtGround(helper));
		}

		useOnGround(helper, player);

		Block after = blockAtGround(helper);
		if (after != Blocks.DIRT_PATH) {
			helper.fail("right-clicking grass with the electric shovel must leave a dirt path, found " + after);
		}
		helper.succeed();
	}

	/**
	 * TC-SHOVEL-001-FUN02 — oracle for FUN01: a vanilla diamond shovel paths the identical fixture.
	 *
	 * <p>Without this, a red FUN01 would be ambiguous between "our item lost the interaction" and "this
	 * rig cannot path grass" (wrong face, no air above, block replaced by the structure, …). A vanilla
	 * shovel declares {@code DEFAULT_SHOVEL_ACTIONS} itself, so it clears the gate on both loaders and
	 * fails here only if the fixture is broken.
	 */
	public static void fun02VanillaShovelPathsTheSameFixture(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SHOVEL));
		prepareGround(helper, Blocks.GRASS_BLOCK);

		useOnGround(helper, player);

		Block after = blockAtGround(helper);
		if (after != Blocks.DIRT_PATH) {
			helper.fail("fixture error: even a vanilla diamond shovel failed to path grass in this rig, so "
					+ "FUN01 proves nothing about our item; found " + after);
		}
		helper.succeed();
	}

	/**
	 * TC-SHOVEL-001-FUN03 — path-making is free, and a flat shovel still does it.
	 *
	 * <p>Two halves of one contract, and each covers the other's blind spot. The charge assertion alone
	 * would be vacuous while the defect is present (no path made, so of course no EU moved), so it is
	 * paired with the path assertion. The flat half pins the rule that defines this whole tool line — "a
	 * discharged tool still works, it is just slow" — and a dead battery must not leave the shovel worse
	 * than a stick with a plank on it.
	 */
	public static void fun03PathMakingIsFree(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, shovel(Config.electricShovelBuffer));
		prepareGround(helper, Blocks.GRASS_BLOCK);

		useOnGround(helper, player);

		Block after = blockAtGround(helper);
		if (after != Blocks.DIRT_PATH) {
			helper.fail("a charged shovel must make a path before its charge can be judged, found " + after);
		}
		long left = ItemEnergy.get(player.getMainHandItem());
		if (left != Config.electricShovelBuffer) {
			helper.fail("making a path must not cost EU, charge went "
					+ Config.electricShovelBuffer + " → " + left);
		}

		player.setItemInHand(InteractionHand.MAIN_HAND, shovel(0));
		prepareGround(helper, Blocks.GRASS_BLOCK);

		useOnGround(helper, player);

		Block afterFlat = blockAtGround(helper);
		if (afterFlat != Blocks.DIRT_PATH) {
			helper.fail("a fully discharged shovel must still make a path, found " + afterFlat);
		}
		helper.succeed();
	}

	/**
	 * TC-SHOVEL-001-FUN04 — right-clicking a lit campfire douses it.
	 *
	 * <p>The second half of {@code DEFAULT_SHOVEL_ACTIONS}. It is not a bonus assertion: {@code
	 * SHOVEL_DOUSE} rides on the very same NeoForge gate as {@code SHOVEL_FLATTEN}, so a fix that
	 * declared only the flatten ability would leave this red — and a suite that tested only paths would
	 * never notice half the item's contract was still missing.
	 */
	public static void fun04ShovelDousesLitCampfire(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, shovel(Config.electricShovelBuffer));
		helper.setBlock(GROUND, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, Boolean.TRUE));
		helper.setBlock(GROUND.above(), Blocks.AIR);

		if (!litAtGround(helper)) {
			helper.fail("fixture error: the campfire must start lit");
		}

		useOnGround(helper, player);

		if (litAtGround(helper)) {
			helper.fail("right-clicking a lit campfire with the electric shovel must douse it");
		}
		helper.succeed();
	}

	// ── MOD-364 — the base tool's EU contract (shared forms, shovel parameters) ──────────────────────

	/** TC-SHOVEL-001-FUN05 — accepted by the Battery Box charge slot and charged at its intake rate. */
	public static void fun05ChargeInBatteryBox(GameTestHelper helper) {
		ElectricToolEnergyScenarios.chargeInBatteryBox(helper, ENERGY);
	}

	/** TC-SHOVEL-001-FUN06 — digging one dirt block drains exactly {@code electricShovelEuPerBlock}. */
	public static void fun06DrainOnMineBlock(GameTestHelper helper) {
		ElectricToolEnergyScenarios.drainOnMineBlock(helper, ENERGY);
	}

	/** TC-SHOVEL-001-FUN07 — one EU below the cost it digs for free, at exactly hand speed. */
	public static void fun07NoDrainBelowCost(GameTestHelper helper) {
		ElectricToolEnergyScenarios.noDrainBelowCost(helper, ENERGY);
	}

	/** TC-SHOVEL-001-FUN08 — a torch (hardness 0.0) is free, a snow layer (0.1) is not. */
	public static void fun08ZeroHardnessFreeSnowCosts(GameTestHelper helper) {
		ElectricToolEnergyScenarios.zeroHardnessFreeSoftBlockCosts(helper, ENERGY);
	}

	/** TC-SHOVEL-001-FUN09 — 9.0 on shovel blocks while charged, 1.0f flat, drops kept either way. */
	public static void fun09SpeedAndDrops(GameTestHelper helper) {
		ElectricToolEnergyScenarios.speedAndDrops(helper, ENERGY);
	}

	/** TC-SHOVEL-001-PER01 — charge survives a copy, 0 EU drops the component, writes clamp. */
	public static void per01ChargeRoundTrip(GameTestHelper helper) {
		ElectricToolEnergyScenarios.chargeRoundTrip(helper, ENERGY);
	}
}
