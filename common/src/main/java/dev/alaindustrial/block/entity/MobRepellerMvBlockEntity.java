package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.MobRepellerMvMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** MV tier of the {@link MobRepellerBlockEntity Mob Repeller}: radius 16, upkeep 8 EU/t. */
public class MobRepellerMvBlockEntity extends MobRepellerBlockEntity {

	public MobRepellerMvBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.MOB_REPELLER_MV_BE.get(), pos, state, EnergyTier.MV, Config.mobRepellerBufferMv);
	}

	@Override
	public int fieldRange() {
		return Config.mobRepellerRangeMv;
	}

	@Override
	public int fieldEuPerTick() {
		return Config.mobRepellerEuPerTickMv;
	}

	@Override
	public int evolveKillsNeeded() {
		return Config.mobRepellerEvolveKillsHv;
	}

	@Override
	protected Block evolveTarget() {
		return ModContent.MOB_REPELLER_HV.get();
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		announceTo(player);
		return new MobRepellerMvMenu(syncId, inventory, this,
				ContainerLevelAccess.create(level, worldPosition));
	}
}
