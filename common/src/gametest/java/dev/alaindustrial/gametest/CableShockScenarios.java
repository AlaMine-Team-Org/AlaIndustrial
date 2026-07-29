package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.tick;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

/** Loader-neutral MOD-260 acceptance scenarios, invoked by both GameTest lanes. */
public final class CableShockScenarios {
	private static final BlockPos GENERATOR = new BlockPos(1, 2, 1);
	private static final BlockPos CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos BOX = new BlockPos(3, 2, 1);

	private CableShockScenarios() {
	}

	/** Bare needs committed flow; insulation, config-off and stopped flow are all safe. */
	public static void energizedBareOnly(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		player.getAbilities().invulnerable = false;
		player.setInvulnerable(false);
		player.invulnerableTime = 0;

		buildLine(helper, ModContent.COPPER_CABLE.get());
		CableBlock bare = (CableBlock) ModContent.COPPER_CABLE.get();
		var cableState = helper.getBlockState(CABLE);
		var insideShape = cableState.getEntityInsideCollisionShape(
				helper.getLevel(), helper.absolutePos(CABLE), player);
		var solidShape = cableState.getCollisionShape(
				helper.getLevel(), helper.absolutePos(CABLE));
		if (insideShape.isEmpty() || Shapes.equal(insideShape, Shapes.block())) {
			helper.fail("cable contact shape was empty or expanded to a full block");
			return;
		}
		if (!Shapes.joinIsNotEmpty(insideShape, solidShape, BooleanOp.ONLY_FIRST)) {
			helper.fail("cable contact shape has no reachable shell outside solid collision");
			return;
		}
		if (bare.shouldShockPlayer(helper.getLevel(), helper.absolutePos(CABLE), player)) {
			helper.fail("unenergized bare cable was marked hazardous");
			return;
		}

		energize(helper);
		if (!bare.shouldShockPlayer(helper.getLevel(), helper.absolutePos(CABLE), player)) {
			CableBlockEntity segment = helper.getBlockEntity(CABLE, CableBlockEntity.class);
			helper.fail("energized bare shock eligibility was rejected; energized="
					+ (segment != null && segment.isEnergizedForShock()));
			return;
		}
		// Resolve the data-driven type in the live server registry; failure here catches a missing or
		// malformed data/alaindustrial/damage_type/electric_shock.json on either loader.
		ModDamageTypes.electricShock(helper.getLevel());

		player.invulnerableTime = 0;
		boolean shockEnabledBeforeTest = Config.bareCableShockEnabled;
		Config.bareCableShockEnabled = false;
		try {
			if (bare.shouldShockPlayer(helper.getLevel(), helper.absolutePos(CABLE), player)) {
				helper.fail("config-off cable remained hazardous");
				return;
			}
		} finally {
			Config.bareCableShockEnabled = shockEnabledBeforeTest;
		}

		helper.setBlock(CABLE, Blocks.AIR);
		helper.setBlock(CABLE, ModContent.INSULATED_COPPER_CABLE.get());
		tick(helper, be(helper, CABLE));
		NetworkManager.tickAll(helper.getLevel());
		if (!(be(helper, CABLE) instanceof CableBlockEntity insulatedSegment)
				|| !insulatedSegment.isEnergizedForShock()) {
			helper.fail("insulated fixture was not carrying energy");
			return;
		}
		CableBlock insulated = (CableBlock) ModContent.INSULATED_COPPER_CABLE.get();
		if (insulated.shouldShockPlayer(helper.getLevel(), helper.absolutePos(CABLE), player)) {
			helper.fail("energized insulated cable was marked hazardous");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-269: the hazard reaches past the wire model, stops at solid cover, and collapses back to
	 * MOD-260's contact-only rule when the radius is configured to zero.
	 *
	 * <p>Asserts {@link CableBlockEntity#isWithinShockReach} rather than health loss: the reach rule is
	 * what this task added, while whether a reachable player is actually hurt is MOD-260's contract and
	 * is already pinned by {@link #energizedBareOnly}. Testing health here would re-test that and add a
	 * dependency on the invulnerability window's timing.
	 */
	public static void proximityRadiusRespectsCoverAndConfig(GameTestHelper helper) {
		buildLine(helper, ModContent.COPPER_CABLE.get());
		energize(helper);
		BlockPos cable = helper.absolutePos(CABLE);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);

		BlockPos beside = CABLE.offset(0, 0, 1);
		BlockPos twoAway = CABLE.offset(0, 0, 2);
		double radiusBefore = Config.bareCableShockProximityRadius;
		try {
			// Beside the cable, not intersecting its 6px model: out of reach before MOD-269, in reach now.
			Config.bareCableShockProximityRadius = 0.5;
			snapToCentre(helper, player, beside);
			if (!CableBlockEntity.isWithinShockReach(helper.getLevel(), cable, player)) {
				helper.fail("player one cell from an energized bare cable was out of shock reach");
				return;
			}

			// Same spot, radius off: the reach rule must vanish entirely, not merely shrink.
			Config.bareCableShockProximityRadius = 0.0;
			if (CableBlockEntity.isWithinShockReach(helper.getLevel(), cable, player)) {
				helper.fail("radius 0 still reported reach; MOD-260 contact-only behaviour was not restored");
				return;
			}

			// The shipped 0.5 also keeps the hazard off anyone a full cell further out.
			Config.bareCableShockProximityRadius = 0.5;
			snapToCentre(helper, player, twoAway);
			if (CableBlockEntity.isWithinShockReach(helper.getLevel(), cable, player)) {
				helper.fail("radius 0.5 reached two cells; the default is meant to be arm's length");
				return;
			}

			// Cover only becomes reachable once an operator widens the radius: at 0.5 the player is
			// already flush against the cable's own cell, leaving no room for a block in between. Widen
			// it, then assert the clear-line rule at a fixed distance — reachable without cover, not
			// reachable with it. Comparing the same position both ways is what proves the difference is
			// the cover and not the distance.
			Config.bareCableShockProximityRadius = 2.0;
			snapToCentre(helper, player, twoAway);
			if (!CableBlockEntity.isWithinShockReach(helper.getLevel(), cable, player)) {
				helper.fail("widened radius did not reach an uncovered player two cells away");
				return;
			}
			helper.setBlock(beside, Blocks.STONE);
			snapToCentre(helper, player, twoAway);
			if (CableBlockEntity.isWithinShockReach(helper.getLevel(), cable, player)) {
				helper.fail("solid cover between cable and player did not block the shock");
				return;
			}
		} finally {
			Config.bareCableShockProximityRadius = radiusBefore;
			helper.setBlock(beside, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * Places the mock player at the centre of {@code relative}. Uses {@code setPos} rather than
	 * {@code snapTo}: the latter notifies the client through {@code connection}, which a mock player
	 * created by {@code makeMockServerPlayer} does not have.
	 */
	private static void snapToCentre(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
		BlockPos abs = helper.absolutePos(relative);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
	}

	/** A segment with retained EU becomes safe once no transfer has occurred for more than one tick. */
	public static void retainedBufferIsSafe(GameTestHelper helper) {
		buildLine(helper, ModContent.COPPER_CABLE.get());
		energize(helper);
		CableBlockEntity cable = helper.getBlockEntity(CABLE, CableBlockEntity.class);
		if (cable == null || cable.getEnergyStorage().getAmount() <= 0 || !cable.isEnergizedForShock()) {
			helper.fail("fixture cable was not energized with a retained segment buffer");
			return;
		}

		helper.setBlock(GENERATOR, Blocks.AIR);
		helper.setBlock(BOX, Blocks.AIR);
		helper.runAfterDelay(3, () -> {
			if (cable.getEnergyStorage().getAmount() <= 0) {
				helper.fail("fixture drained its retained EU, so the residual-buffer guard was not exercised");
				return;
			}
			if (cable.isEnergizedForShock()) {
				helper.fail("cable remained hazardous without a committed transfer");
				return;
			}
			helper.succeed();
		});
	}

	private static void buildLine(GameTestHelper helper, Block cable) {
		helper.setBlock(GENERATOR, ModContent.GENERATOR.get());
		helper.setBlock(CABLE, cable);
		helper.setBlock(BOX, ModContent.BATTERY_BOX.get());
		tick(helper, be(helper, GENERATOR));
		tick(helper, be(helper, CABLE));
		tick(helper, be(helper, BOX));
		if (be(helper, GENERATOR) instanceof GeneratorBlockEntity generator) {
			generator.getEnergyStorage().amount = 0;
			generator.setChanged();
		}
		if (be(helper, BOX) instanceof BatteryBoxBlockEntity box) {
			box.getEnergyStorage().amount = 0;
			box.setChanged();
		}
	}

	private static void energize(GameTestHelper helper) {
		if (be(helper, GENERATOR) instanceof GeneratorBlockEntity generator) {
			generator.getEnergyStorage().amount = Config.generatorBuffer;
			generator.setChanged();
		}
		for (int i = 0; i < 3; i++) {
			NetworkManager.tickAll(helper.getLevel());
		}
	}

}
