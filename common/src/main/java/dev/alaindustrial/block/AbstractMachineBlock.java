package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModCriteria;
import dev.alaindustrial.sound.MachineHum;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shared base for Industrialization machine blocks: model rendering, a server ticker that drives the
 * {@link MachineBlockEntity}, and right-click to open the block's menu (when its block entity
 * is a {@link MenuProvider}). Concrete blocks supply their codec + block entity factory.
 */
public abstract class AbstractMachineBlock extends BaseEntityBlock {
	protected AbstractMachineBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof MenuProvider provider) {
				player.openMenu(provider);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Checks whether placing this block (a producer/consumer machine, generator, storage or cable)
	 * completed a network — a cable is itself a network member (checked at {@code pos} once
	 * registered by {@link dev.alaindustrial.block.CableBlock#setPlacedBy}); any other machine block
	 * is only ever a neighbour endpoint, never a member, so {@link ModCriteria#tryFireNetworkEnergized}
	 * checks both {@code pos} and its neighbours to cover the "player plugs the last machine into an
	 * already-built cable run" case too (MOD-015 "Closed circuit" — see the code review note: without
	 * this, the achievement could be permanently unobtainable if a machine, not a cable, is the last
	 * piece placed).
	 */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player) {
			ModCriteria.tryFireNetworkEnergized(serverLevel, pos, player);
		}
	}

	/**
	 * Drop the machine's inventory contents when broken, so items are recovered (not lost) and
	 * not duplicated. The block item itself is dropped by the loot table; this only adds the
	 * inventory, so there is no overlap / dupe.
	 */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MachineBlockEntity machine
				&& machine.getContainerSize() > 0) {
			Containers.dropContents(level, pos, machine);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** Server-only ticker that forwards to {@link MachineBlockEntity#serverTick}. */
	protected static <T extends BlockEntity> BlockEntityTicker<T> machineTicker(Level level) {
		if (level.isClientSide()) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof MachineBlockEntity machine) {
				machine.serverTick(lvl, pos, st);
			}
		};
	}

	/**
	 * Ticker for machines that emit a looping ambient hum ({@link MachineHumProvider}). Identical to
	 * {@link #machineTicker} on the server; on the client it drives the hum (via the loader-installed
	 * {@link MachineHum#CLIENT} hook) instead of returning null, so the client-side looping sound starts
	 * and stops with the block's {@code lit} state. References only {@link MachineHum} (no client imports),
	 * so it is server-safe — on a dedicated server {@code CLIENT} is null and no client class is touched.
	 */
	protected static <T extends BlockEntity> BlockEntityTicker<T> humMachineTicker(Level level) {
		if (level.isClientSide()) {
			MachineHum.ClientHook hook = MachineHum.CLIENT;
			return hook == null ? null : (lvl, pos, st, be) -> hook.tick(lvl, pos, st);
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof MachineBlockEntity machine) {
				machine.serverTick(lvl, pos, st);
			}
		};
	}
}
