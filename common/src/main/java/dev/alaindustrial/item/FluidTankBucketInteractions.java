package dev.alaindustrial.item;

import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.core.EnergyTransactions;
import dev.alaindustrial.core.FluidAmounts;
import dev.alaindustrial.core.FluidHolder;
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

/** Whole-bucket manual exchange for the portable tank (MOD-111). */
public final class FluidTankBucketInteractions {
	private FluidTankBucketInteractions() {
	}

	public static InteractionResult exchange(Level level, BlockPos pos, Player player, InteractionHand hand,
			FluidTankBlockEntity tank) {
		ItemStack held = player.getItemInHand(hand);
		Fluid incoming = held.is(Items.WATER_BUCKET) ? Fluids.WATER
				: held.is(Items.LAVA_BUCKET) ? Fluids.LAVA : Fluids.EMPTY;
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
		ItemStack filled = stored == Fluids.WATER ? new ItemStack(Items.WATER_BUCKET)
				: stored == Fluids.LAVA ? new ItemStack(Items.LAVA_BUCKET) : ItemStack.EMPTY;
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
