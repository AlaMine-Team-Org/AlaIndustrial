package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.core.EnergyTransactions;
import dev.alaindustrial.core.FluidAmounts;
import dev.alaindustrial.core.FluidHolder;
import dev.alaindustrial.item.ItemFluid;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametest bodies for the Vacuum Capsule (MOD-063, suite TC-CAPS-001). Same pattern as
 * {@link CoreFluidScenarios}/{@link PouchScenarios}: plain {@code Consumer<GameTestHelper>} bodies over
 * vanilla {@code GameTestHelper} + neutral content, wrapped by the Fabric {@code CapsuleGameTest} suite
 * and registered on the NeoForge {@code gameTestServer} lane via {@code NeoForgeGameTests} — both loaders
 * exercise the SAME capsule logic (the {@code capsule_fluid} data component resolves per loader).
 *
 * <p>Covers the mod-specific behaviour: the {@code capsule_fluid} component round-trip and absent-means-empty
 * rule, per-fluid stacking, and the exchange with a neighbouring mod tank via {@code FluidLookup} (both
 * directions). World fill/place ports vanilla {@code BucketItem} verbatim and is verified in the dev client.
 */
public final class CapsuleScenarios {

	private CapsuleScenarios() {
	}

	private static final BlockPos PUMP = new BlockPos(1, 2, 1);

	private static ItemStack empty() {
		return new ItemStack(ModContent.VACUUM_CAPSULE.get());
	}

	private static ItemStack filled(net.minecraft.world.level.material.Fluid fluid) {
		ItemStack stack = new ItemStack(ModContent.FILLED_VACUUM_CAPSULE.get());
		ItemFluid.set(stack, fluid);
		return stack;
	}

	private static PumpBlockEntity placePump(GameTestHelper helper) {
		helper.setBlock(PUMP, ModContent.PUMP.get().defaultBlockState());
		PumpBlockEntity be = helper.getBlockEntity(PUMP, PumpBlockEntity.class);
		if (be == null) {
			helper.fail("pump block entity missing after placement");
		}
		return be;
	}

	private static void fill(PumpBlockEntity pump, net.minecraft.world.level.material.Fluid fluid, long mb) {
		EnergyTransactions.get().runCommitting(txn -> pump.fluidTank.insert(FluidHolder.of(fluid), mb, txn));
	}

	private static UseOnContext ctxOn(GameTestHelper helper, Player player, BlockPos rel, Direction face) {
		BlockPos abs = helper.absolutePos(rel);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
		return new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
	}

	// ── PER01: component round-trip + absent-means-empty ──────────────────────────────────────────

	/**
	 * The {@code capsule_fluid} component stores and returns the fluid, and writing empty removes it (a
	 * drained capsule is component-identical to a crafted one). Traced by {@code CapsuleGameTest} PER01.
	 */
	public static void per01ComponentRoundTrip(GameTestHelper helper) {
		ItemStack stack = filled(Fluids.WATER);
		if (ItemFluid.get(stack) != Fluids.WATER || ItemFluid.isEmpty(stack)) {
			helper.fail("capsule did not store water");
			return;
		}
		ItemFluid.set(stack, Fluids.EMPTY);
		if (!ItemFluid.isEmpty(stack) || stack.has(ModDataComponents.CAPSULE_FLUID.get())) {
			helper.fail("writing empty did not remove the capsule_fluid component");
			return;
		}
		if (!ItemFluid.isEmpty(empty())) {
			helper.fail("a fresh empty capsule reported non-empty");
			return;
		}
		helper.succeed();
	}

	// ── FUN01: stacking is per-fluid ──────────────────────────────────────────────────────────────

	/**
	 * Two filled capsules of the same fluid share one component value and stack; different fluids never
	 * merge. This is the whole "16 buckets in one slot, separated by fluid" guarantee, riding vanilla
	 * component-equality (no custom stacking override). Traced by {@code CapsuleGameTest} FUN01.
	 */
	public static void fun01StackingByFluid(GameTestHelper helper) {
		boolean sameMerges = ItemStack.isSameItemSameComponents(filled(Fluids.WATER), filled(Fluids.WATER));
		boolean diffKept = !ItemStack.isSameItemSameComponents(filled(Fluids.WATER), filled(Fluids.LAVA));
		int max = filled(Fluids.WATER).getMaxStackSize();
		if (!sameMerges || !diffKept || max != dev.alaindustrial.item.FilledCapsuleItem.STACK_SIZE) {
			helper.fail("stacking wrong: sameMerges=" + sameMerges + " diffKept=" + diffKept + " max=" + max);
			return;
		}
		helper.succeed();
	}

	// ── FUN02: fill an empty capsule from a mod tank (extraction) ─────────────────────────────────

	/**
	 * Right-clicking a mod tank that holds at least one bucket with an empty capsule pulls exactly one
	 * bucket into the capsule (via {@code FluidLookup}) and swaps it for a filled one. Traced by
	 * {@code CapsuleGameTest} FUN02.
	 */
	public static void fun02FillFromTank(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		fill(pump, Fluids.LAVA, FluidAmounts.BUCKET);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, empty());
		InteractionResult result = ModContent.VACUUM_CAPSULE.get().useOn(ctxOn(helper, player, PUMP, Direction.UP));
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean handFilledLava = hand.is(ModContent.FILLED_VACUUM_CAPSULE.get()) && ItemFluid.get(hand) == Fluids.LAVA;
		boolean tankEmptied = pump.fluidTank.amount == 0;
		if (!result.consumesAction() || !handFilledLava || !tankEmptied) {
			helper.fail("fill-from-tank: result=" + result + " hand=" + hand + " tank=" + pump.fluidTank.amount);
			return;
		}
		helper.succeed();
	}

	// ── FUN03: empty a filled capsule into a mod tank (insertion) ─────────────────────────────────

	/**
	 * Right-clicking a mod tank with a filled capsule pushes its bucket into the tank and swaps back to an
	 * empty capsule. Traced by {@code CapsuleGameTest} FUN03.
	 */
	public static void fun03EmptyIntoTank(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, filled(Fluids.LAVA));
		InteractionResult result = ModContent.FILLED_VACUUM_CAPSULE.get().useOn(ctxOn(helper, player, PUMP, Direction.UP));
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean handEmpty = hand.is(ModContent.VACUUM_CAPSULE.get());
		boolean tankHasLava = pump.fluidTank.amount == FluidAmounts.BUCKET && pump.fluidTank.fluid.is(Fluids.LAVA);
		if (!result.consumesAction() || !handEmpty || !tankHasLava) {
			helper.fail("empty-into-tank: result=" + result + " hand=" + hand
					+ " tank=" + pump.fluidTank.amount + " fluid=" + pump.fluidTank.fluid);
			return;
		}
		helper.succeed();
	}
}
