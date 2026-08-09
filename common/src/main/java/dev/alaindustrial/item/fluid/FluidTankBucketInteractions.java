package dev.alaindustrial.item.fluid;

import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Whole-bucket manual exchange for the portable tank (MOD-111).
 *
 * <p>Which buckets are recognised is {@link BucketFluids}' business, not this route's (MOD-380).
 * Both directions used to carry their own water-or-lava list, which left every other fluid — the
 * mod's own oil above all — falling through to {@code PASS} and out onto the floor.
 */
public final class FluidTankBucketInteractions {
	private FluidTankBucketInteractions() {
	}

	public static InteractionResult exchange(Level level, BlockPos pos, Player player, InteractionHand hand,
			FluidTankBlockEntity tank) {
		ItemStack held = player.getItemInHand(hand);
		Fluid incoming = BucketFluids.content(held);
		if (incoming != Fluids.EMPTY) {
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (tank.fluidTank.getCapacity() - tank.fluidTank.getAmount() < FluidAmounts.BUCKET) {
				return InteractionResult.SUCCESS;
			}
			boolean[] moved = {false};
			EnergyTransactions.get().runCommitting(txn ->
					moved[0] = tank.fluidTank.insert(FluidHolder.of(incoming), FluidAmounts.BUCKET, txn)
							== FluidAmounts.BUCKET);
			if (moved[0]) {
				player.setItemInHand(hand,
						ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
				CapsuleInteractions.playEmpty(level, player, pos, incoming);
			}
			return InteractionResult.SUCCESS;
		}

		if (!held.is(Items.BUCKET)) {
			return InteractionResult.PASS;
		}
		Fluid stored = tank.fluidTank.fluid().fluid();
		ItemStack filled = BucketFluids.filledBucket(stored);
		if (filled.isEmpty()) {
			return InteractionResult.SUCCESS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (tank.fluidTank.getAmount() < FluidAmounts.BUCKET) {
			return InteractionResult.SUCCESS;
		}
		boolean[] moved = {false};
		EnergyTransactions.get().runCommitting(txn ->
				moved[0] = tank.fluidTank.extract(FluidHolder.of(stored), FluidAmounts.BUCKET, txn)
						== FluidAmounts.BUCKET);
		if (moved[0]) {
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, filled));
			CapsuleInteractions.playFill(level, player, pos, stored);
		}
		return InteractionResult.SUCCESS;
	}
}
