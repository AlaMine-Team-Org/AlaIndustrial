package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.ConcreteRailBeamBlock;
import dev.alaindustrial.block.FreshConcreteBlock;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * MOD-927 — the quarry's building blocks released as decor: fresh concrete sets into industrial concrete on
 * scheduled ticks, stays wet next to water and comes off by hand; the rail beam lies along the placer's view;
 * the scaffolding lets an entity stand inside it and stand on top of it.
 */
public final class QuarryConstructionScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(QuarryConstructionScenarios::freshConcreteSetsAfterFourStages,
						"quarry_construction_fresh_concrete_sets_after_four_stages").ticks(100)
						.environment(Industrialization.id("config_overrides_3")),
				RosterEntry.of(QuarryConstructionScenarios::freshConcreteWaitsNextToWater,
						"quarry_construction_fresh_concrete_waits_next_to_water"),
				RosterEntry.of(QuarryConstructionScenarios::freshConcreteComesOffByHand,
						"quarry_construction_fresh_concrete_comes_off_by_hand"),
				RosterEntry.of(QuarryConstructionScenarios::railBeamLiesAlongThePlacersView,
						"quarry_construction_rail_beam_lies_along_the_placers_view"),
				RosterEntry.of(QuarryConstructionScenarios::scaffoldLetsAnEntityInAndOnTop,
						"quarry_construction_scaffold_lets_an_entity_in_and_on_top").ticks(40));

		private Roster() {}
	}

	private QuarryConstructionScenarios() {}

	private static final BlockPos CONCRETE = new BlockPos(1, 1, 1);
	private static final BlockPos WATER = new BlockPos(2, 1, 1);
	private static final BlockPos SCAFFOLD = new BlockPos(1, 1, 1);
	/** A stage short enough that four of them fit the tick budget many times over. */
	private static final int SHORT_STAGE_TICKS = 2;

	/**
	 * With a stage of two ticks the block walks setting 0..3 on its own scheduled ticks and then becomes
	 * industrial concrete. The knob is held across ticks, so the scenario runs in its own environment
	 * ({@code ArchitectureRules.FOR_TEST_USERS}).
	 */
	public static void freshConcreteSetsAfterFourStages(GameTestHelper helper) {
		ConfigOverrides.forTest(helper).set("freshConcreteStageTicks", SHORT_STAGE_TICKS);
		helper.setBlock(CONCRETE, ModContent.FRESH_CONCRETE.get());
		helper.succeedWhen(() -> helper.assertBlockPresent(ModContent.INDUSTRIAL_CONCRETE.get(), CONCRETE));
	}

	/**
	 * Water on a face holds the stage, and taking the water away lets the next tick advance it. The tick is
	 * called directly: the scheduled one is a stage away, and what is under test is the rule inside it.
	 */
	public static void freshConcreteWaitsNextToWater(GameTestHelper helper) {
		// A one-block pool walled in on every side but the concrete's, so the water cannot run.
		helper.setBlock(WATER.below(), Blocks.STONE);
		helper.setBlock(WATER.east(), Blocks.STONE);
		helper.setBlock(WATER.north(), Blocks.STONE);
		helper.setBlock(WATER.south(), Blocks.STONE);
		helper.setBlock(WATER, Blocks.WATER);
		helper.setBlock(CONCRETE, ModContent.FRESH_CONCRETE.get());
		BlockPos abs = helper.absolutePos(CONCRETE);
		for (int i = 0; i < 3; i++) {
			helper.getBlockState(CONCRETE).tick(helper.getLevel(), abs, helper.getLevel().getRandom());
		}
		BlockState wet = helper.getBlockState(CONCRETE);
		if (!wet.is(ModContent.FRESH_CONCRETE.get()) || wet.getValue(FreshConcreteBlock.SETTING) != 0) {
			helper.fail("fresh concrete next to water moved on to " + wet + " (expected setting 0)");
			return;
		}
		helper.setBlock(WATER, Blocks.STONE);
		helper.getBlockState(CONCRETE).tick(helper.getLevel(), abs, helper.getLevel().getRandom());
		BlockState dry = helper.getBlockState(CONCRETE);
		if (!dry.is(ModContent.FRESH_CONCRETE.get()) || dry.getValue(FreshConcreteBlock.SETTING) != 1) {
			helper.fail("fresh concrete with the water gone is " + dry + " (expected setting 1)");
			return;
		}
		helper.succeed();
	}

	/** Breaks at once with an empty hand and hands back fresh concrete, also half set. */
	public static void freshConcreteComesOffByHand(GameTestHelper helper) {
		Player hand = helper.makeMockPlayer(GameType.SURVIVAL);
		hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.setBlock(CONCRETE, ModContent.FRESH_CONCRETE.get().defaultBlockState()
				.setValue(FreshConcreteBlock.SETTING, 2));
		BlockPos abs = helper.absolutePos(CONCRETE);
		BlockState state = helper.getBlockState(CONCRETE);
		float destroySpeed = state.getDestroySpeed(helper.getLevel(), abs);
		if (destroySpeed != 0.0f) {
			helper.fail("fresh concrete takes time to break: destroy speed " + destroySpeed);
			return;
		}
		if (!hand.hasCorrectToolForDrops(state)) {
			helper.fail("fresh concrete needs a tool to drop; it must come off by hand");
			return;
		}
		List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), abs, null, hand, ItemStack.EMPTY);
		long self = drops.stream().filter(s -> s.is(ModContent.FRESH_CONCRETE_ITEM.get()))
				.mapToLong(ItemStack::getCount).sum();
		if (self != 1 || drops.size() != 1) {
			helper.fail("fresh concrete broken by hand dropped " + drops + " (expected one fresh concrete)");
			return;
		}
		helper.succeed();
	}

	/**
	 * Looking east lays it along X, looking south along Z; a quarter turn of a structure swaps the two. The plate
	 * rail beam shares the class and lies the same way.
	 */
	public static void railBeamLiesAlongThePlacersView(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos target = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target.below()), Direction.UP, target.below(), false);
		Block beam = ModContent.CONCRETE_RAIL_BEAM.get();
		ItemStack stack = new ItemStack(ModContent.CONCRETE_RAIL_BEAM_ITEM.get());
		player.setYRot(-90.0f);
		BlockState east = beam.getStateForPlacement(
				new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
		player.setYRot(0.0f);
		BlockState south = beam.getStateForPlacement(
				new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
		if (east == null || east.getValue(ConcreteRailBeamBlock.AXIS) != Direction.Axis.X) {
			helper.fail("placed looking east, the rail beam lies along " + axis(east) + " (expected X)");
			return;
		}
		if (south == null || south.getValue(ConcreteRailBeamBlock.AXIS) != Direction.Axis.Z) {
			helper.fail("placed looking south, the rail beam lies along " + axis(south) + " (expected Z)");
			return;
		}
		if (east.rotate(Rotation.CLOCKWISE_90).getValue(ConcreteRailBeamBlock.AXIS) != Direction.Axis.Z
				|| east.rotate(Rotation.CLOCKWISE_180).getValue(ConcreteRailBeamBlock.AXIS) != Direction.Axis.X) {
			helper.fail("a structure rotation does not swap the rail beam's axis on a quarter turn only");
			return;
		}
		player.setYRot(-90.0f);
		BlockState plate = ModContent.PLATE_RAIL_BEAM.get().getStateForPlacement(new BlockPlaceContext(player,
				InteractionHand.MAIN_HAND, new ItemStack(ModContent.PLATE_RAIL_BEAM_ITEM.get()), hit));
		if (plate == null || plate.getValue(ConcreteRailBeamBlock.AXIS) != Direction.Axis.X) {
			helper.fail("placed looking east, the plate rail beam lies along " + axis(plate) + " (expected X)");
			return;
		}
		helper.succeed();
	}

	/**
	 * An armour stand standing inside the scaffolding stays there (no collision pushes it out), the block
	 * is climbable, and an entity whose feet are on the top stands on the top plate.
	 */
	public static void scaffoldLetsAnEntityInAndOnTop(GameTestHelper helper) {
		// Stone under it, so the stand inside has a floor and only the scaffolding could move it.
		helper.setBlock(SCAFFOLD.below(), Blocks.STONE);
		helper.setBlock(SCAFFOLD, ModContent.TEMPERED_IRON_SCAFFOLD.get());
		BlockState state = helper.getBlockState(SCAFFOLD);
		if (!state.is(BlockTags.CLIMBABLE)) {
			helper.fail("tempered iron scaffolding is not in #minecraft:climbable");
			return;
		}
		BlockPos abs = helper.absolutePos(SCAFFOLD);
		ArmorStand inside = helper.spawn(EntityTypes.ARMOR_STAND, Vec3.atBottomCenterOf(SCAFFOLD));
		if (!state.getCollisionShape(helper.getLevel(), abs, CollisionContext.of(inside)).isEmpty()) {
			helper.fail("the scaffolding collides with an entity inside it (expected no collision)");
			return;
		}
		ArmorStand above = helper.spawn(EntityTypes.ARMOR_STAND, Vec3.atBottomCenterOf(SCAFFOLD.above()));
		if (state.getCollisionShape(helper.getLevel(), abs, CollisionContext.of(above)).isEmpty()) {
			helper.fail("an entity on top of the scaffolding has nothing to stand on");
			return;
		}
		above.discard();
		helper.runAfterDelay(20, () -> {
			BlockPos at = inside.blockPosition();
			if (!at.equals(abs) || !inside.isAlive()) {
				helper.fail("the armour stand inside the scaffolding was moved to " + at + " (expected " + abs + ")");
				return;
			}
			helper.succeed();
		});
	}

	private static String axis(BlockState state) {
		return state == null ? "nothing" : String.valueOf(state.getValue(ConcreteRailBeamBlock.AXIS));
	}
}
