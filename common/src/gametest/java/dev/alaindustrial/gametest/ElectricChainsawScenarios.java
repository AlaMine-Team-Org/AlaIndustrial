package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.tool.ElectricChainsawDiamondTipItem;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral gametest bodies for the Electric Chainsaw (suite TC-CHAINSAW-001) — the
 * diamond-tipped upgrade (MOD-374) and, since MOD-364, the base tool's own EU contract. Same pattern as
 * {@link ElectricDrillScenarios}: plain {@code GameTestHelper} bodies wrapped by the Fabric
 * {@code ElectricChainsawGameTest} suite and registered on the NeoForge {@code gameTestServer} lane via
 * {@code NeoForgeGameTests} — both loaders exercise the SAME logic.
 *
 * <p>Numbers come from {@link Config} (electricChainsawBuffer, electricChainsawEuPerBlock) — the
 * balance source of truth. Drops are read through {@link Block#getDrops}, i.e. the real vanilla loot
 * tables, not a re-implementation of what they are believed to do.
 *
 * <p>The six base-contract forms (charging, drain, hand-speed collapse, free instant-break blocks,
 * speed/drops, persistence) are shared with the shovel and the hoe and live in
 * {@link ElectricToolEnergyScenarios}; what stays here is {@link #ENERGY}, the one place in the repo
 * that says which tool those forms are run against for the chainsaw.
 */
public final class ElectricChainsawScenarios {

	private ElectricChainsawScenarios() {}

	/**
	 * The chainsaw's slice of the shared EU contract (MOD-364), declared here and nowhere else so that
	 * naming the wrong tool would mean writing {@code ModContent.ELECTRIC_CHAINSAW} inside another tool's
	 * suite. {@link ElectricToolEnergyScenarios#energyCaseRosterIsHonest} checks every field of it against
	 * the real item.
	 *
	 * <p>Fixtures: oak log is the chainsaw's own {@code #mineable/axe} domain; stone is the negative;
	 * oak leaves (hardness 0.2 in the 26.2 sources) is the soft block that must still cost full price and
	 * is also the base tool's only coverage of the third {@code Tool.Rule} — {@code #minecraft:leaves} at
	 * full speed, which vanilla does not file under axes at all.
	 */
	public static final ElectricToolEnergyScenarios.ToolCase ENERGY =
			new ElectricToolEnergyScenarios.ToolCase(
					"electric_chainsaw",
					ModContent.ELECTRIC_CHAINSAW,
					() -> Config.electricChainsawEuPerBlock,
					() -> Config.electricChainsawBuffer,
					() -> Config.electricChainsawInputRate,
					() -> Blocks.OAK_LOG,
					() -> Blocks.STONE,
					() -> Blocks.OAK_LEAVES,
					9.0f);

	private static final BlockPos LEAF = new BlockPos(1, 2, 1);

	private static ItemStack chainsaw(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTRIC_CHAINSAW.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	private static ItemStack diamondTipChainsaw(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/**
	 * A survival ServerPlayer mock with {@code instabuild} forced off — the
	 * {@code makeMockServerPlayerInLevel} default leaves it on (hardcoded CREATIVE gameMode), which
	 * would make every EU assertion meaningless (MOD-081 drops the drain in creative).
	 */
	private static ServerPlayer makeSurvivalPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		return player;
	}

	private static boolean dropsContain(ServerLevel level, BlockState state, BlockPos pos,
			ServerPlayer player, Item expected) {
		for (ItemStack dropped : Block.getDrops(state, level, pos, null, player, player.getMainHandItem())) {
			if (dropped.getItem() == expected) {
				return true;
			}
		}
		return false;
	}

	private static void assertCorrect(GameTestHelper helper, ItemStack saw, BlockState state, String name,
			boolean expected) {
		if (saw.isCorrectToolForDrops(state) != expected) {
			helper.fail("isCorrectToolForDrops(" + name + ") must be " + expected);
		}
	}

	/**
	 * TC-CHAINSAW-001-FUN01 — the upgrade cuts at 10.5 on both of the chainsaw's domains (logs and
	 * leaves), strictly faster than the base chainsaw, still collapses to exactly hand speed when flat,
	 * and does not change the mining tier.
	 *
	 * <p>The speed is asserted twice on purpose: an absolute {@code == 10.5f} alone would stay green if
	 * someone raised the base chainsaw to 10.5 too, so the "strictly faster than base" comparison is the
	 * assertion that actually protects the upgrade's reason to exist. The leaves check matters because
	 * the chainsaw's third {@code Tool.Rule} is its own invention — vanilla files leaves under
	 * {@code #mineable/hoe}, so a rebuilt TOOL component that dropped that rule would silently make
	 * canopy-clearing crawl.
	 */
	public static void fun01DiamondTipSpeedAndTier(GameTestHelper helper) {
		Item tip = ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP.get();
		Item base = ModContent.ELECTRIC_CHAINSAW.get();
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();

		float chargedLog = tip.getDestroySpeed(diamondTipChainsaw(Config.electricChainsawBuffer), log);
		if (chargedLog != 10.5f) {
			helper.fail("a charged diamond-tipped chainsaw must cut oak log at 10.5, got " + chargedLog);
		}
		float chargedLeaves = tip.getDestroySpeed(diamondTipChainsaw(Config.electricChainsawBuffer), leaves);
		if (chargedLeaves != 10.5f) {
			helper.fail("a charged diamond-tipped chainsaw must cut leaves at 10.5, got " + chargedLeaves);
		}
		float baseSpeed = base.getDestroySpeed(chainsaw(Config.electricChainsawBuffer), log);
		if (!(chargedLog > baseSpeed)) {
			helper.fail("the upgrade must out-cut the base chainsaw, got " + chargedLog + " vs base " + baseSpeed);
		}
		float flat = tip.getDestroySpeed(diamondTipChainsaw(0), log);
		if (flat != 1.0f) {
			helper.fail("a flat diamond-tipped chainsaw must cut at exactly hand speed 1.0, got " + flat);
		}

		// The tier is unchanged: it is still an axe, not a pickaxe.
		ItemStack charged = diamondTipChainsaw(Config.electricChainsawBuffer);
		assertCorrect(helper, charged, log, "oak_log", true);
		assertCorrect(helper, charged, Blocks.STONE.defaultBlockState(), "stone", false);
		helper.succeed();
	}

	/**
	 * TC-CHAINSAW-001-FUN02 — sneak + right-click toggles the upgrade's Silk Touch mode, and the mode
	 * flips the real leaf loot table both ways; a plain right-click is inert.
	 *
	 * <p>Both directions are asserted against deterministic outcomes only. With the mode on, the leaf
	 * block is guaranteed and the sapling/stick pools are guaranteed absent (vanilla wraps them in an
	 * {@code inverted} of the very same silk/shears condition). With the mode off, the leaf block is
	 * guaranteed absent — the sapling is a 5 % roll and is deliberately NOT asserted, because a test
	 * that depends on a random roll is a test that fails for the wrong reason.
	 */
	public static void fun02DiamondTipSilkToggleOnLeaves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = makeSurvivalPlayer(helper);
		ItemStack stack = diamondTipChainsaw(Config.electricChainsawBuffer);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);

		// A freshly crafted chainsaw starts in normal mode — sapling farming keeps working out of the box.
		if (ElectricChainsawDiamondTipItem.isSilkMode(stack)) {
			helper.fail("a fresh diamond-tipped chainsaw must start in normal (non-silk) mode");
		}

		// Plain right-click must not toggle: the control is sneak-gated.
		player.setShiftKeyDown(false);
		stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (ElectricChainsawDiamondTipItem.isSilkMode(stack)) {
			helper.fail("a non-sneaking right-click must not toggle Silk Touch mode");
		}

		player.setShiftKeyDown(true);
		stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (!ElectricChainsawDiamondTipItem.isSilkMode(stack)) {
			helper.fail("sneak + right-click must switch Silk Touch mode on");
		}

		BlockPos abs = helper.absolutePos(LEAF);
		helper.setBlock(LEAF, Blocks.OAK_LEAVES);
		BlockState leaves = level.getBlockState(abs);

		if (!dropsContain(level, leaves, abs, player, Blocks.OAK_LEAVES.asItem())) {
			helper.fail("in Silk Touch mode oak leaves must drop as the leaf block");
		}
		if (dropsContain(level, leaves, abs, player, Items.OAK_SAPLING)) {
			helper.fail("in Silk Touch mode oak leaves must NOT drop a sapling");
		}
		if (dropsContain(level, leaves, abs, player, Items.STICK)) {
			helper.fail("in Silk Touch mode oak leaves must NOT drop sticks");
		}

		stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (ElectricChainsawDiamondTipItem.isSilkMode(stack)) {
			helper.fail("a second sneak + right-click must switch Silk Touch mode back off");
		}
		if (dropsContain(level, leaves, abs, player, Blocks.OAK_LEAVES.asItem())) {
			helper.fail("in normal mode oak leaves must NOT drop as the leaf block");
		}
		helper.succeed();
	}

	/**
	 * TC-CHAINSAW-001-FUN03 — the BASE chainsaw has no Silk Touch mode at all: sneaking and
	 * right-clicking leaves it unenchanted, and its leaf drops never include the leaf block.
	 *
	 * <p>This is the negative control for the whole feature. Without it, a refactor that moved the
	 * toggle up into {@link dev.alaindustrial.item.tool.ElectricChainsawItem} would hand every player a
	 * free silk axe and every assertion in FUN02 would still pass.
	 */
	public static void fun03BaseChainsawHasNoSilkMode(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = makeSurvivalPlayer(helper);
		ItemStack stack = chainsaw(Config.electricChainsawBuffer);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);

		if (stack.getItem() instanceof ElectricChainsawDiamondTipItem) {
			helper.fail("the base chainsaw must not be an instance of the diamond-tipped upgrade");
		}

		// Sneak-clicking the base chainsaw as many times as it takes to flip a toggle, if one existed.
		player.setShiftKeyDown(true);
		stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
		stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
		stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (ElectricChainsawDiamondTipItem.isSilkMode(stack)) {
			helper.fail("the base chainsaw must never acquire Silk Touch from a sneak + right-click");
		}

		BlockPos abs = helper.absolutePos(LEAF);
		helper.setBlock(LEAF, Blocks.OAK_LEAVES);
		BlockState leaves = level.getBlockState(abs);
		if (dropsContain(level, leaves, abs, player, Blocks.OAK_LEAVES.asItem())) {
			helper.fail("the base chainsaw must NOT drop oak leaves as the leaf block");
		}
		helper.succeed();
	}

	// ── MOD-364 — the base tool's EU contract (shared forms, chainsaw parameters) ────────────────────

	/** TC-CHAINSAW-001-FUN04 — accepted by the Battery Box charge slot and charged at its intake rate. */
	public static void fun04ChargeInBatteryBox(GameTestHelper helper) {
		ElectricToolEnergyScenarios.chargeInBatteryBox(helper, ENERGY);
	}

	/** TC-CHAINSAW-001-FUN05 — cutting one oak log drains exactly {@code electricChainsawEuPerBlock}. */
	public static void fun05DrainOnMineBlock(GameTestHelper helper) {
		ElectricToolEnergyScenarios.drainOnMineBlock(helper, ENERGY);
	}

	/** TC-CHAINSAW-001-FUN06 — one EU below the cost it cuts for free, at exactly hand speed. */
	public static void fun06NoDrainBelowCost(GameTestHelper helper) {
		ElectricToolEnergyScenarios.noDrainBelowCost(helper, ENERGY);
	}

	/** TC-CHAINSAW-001-FUN07 — a torch (hardness 0.0) is free, oak leaves (0.2) are not. */
	public static void fun07ZeroHardnessFreeLeavesCost(GameTestHelper helper) {
		ElectricToolEnergyScenarios.zeroHardnessFreeSoftBlockCosts(helper, ENERGY);
	}

	/** TC-CHAINSAW-001-FUN08 — 9.0 on logs AND leaves while charged, 1.0f flat, drops kept either way. */
	public static void fun08SpeedAndDrops(GameTestHelper helper) {
		ElectricToolEnergyScenarios.speedAndDrops(helper, ENERGY);
	}

	/** TC-CHAINSAW-001-PER01 — charge survives a copy, 0 EU drops the component, writes clamp. */
	public static void per01ChargeRoundTrip(GameTestHelper helper) {
		ElectricToolEnergyScenarios.chargeRoundTrip(helper, ENERGY);
	}
}
