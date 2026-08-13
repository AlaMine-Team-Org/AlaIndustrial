package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.MobRepellerHvMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * HV tier of the {@link MobRepellerBlockEntity Mob Repeller}: radius 24, upkeep 32 EU/t. The top of
 * the ladder — {@link #evolveTarget()} is null, so the vessel slot rejects everything.
 */
public class MobRepellerHvBlockEntity extends MobRepellerBlockEntity {

	public MobRepellerHvBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.MOB_REPELLER_HV_BE.get(), pos, state, EnergyTier.HV, Config.mobRepellerBufferHv);
	}

	@Override
	public int fieldRange() {
		return Config.mobRepellerRangeHv;
	}

	@Override
	public int fieldEuPerTick() {
		return Config.mobRepellerEuPerTickHv;
	}

	@Override
	public int evolveKillsNeeded() {
		return 0;
	}

	@Override
	protected Block evolveTarget() {
		return null;
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		announceTo(player);
		return new MobRepellerHvMenu(syncId, inventory, this,
				ContainerLevelAccess.create(level, worldPosition));
	}
}
