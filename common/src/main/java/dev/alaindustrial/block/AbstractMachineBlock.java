package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModCriteria;
import dev.alaindustrial.sound.MachineHum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * is a {@link MenuProvider}). Non-menu blocks (e.g. {@code CableBlock}) fall through to PASS so
 * vanilla placement still runs — see {@link #useWithoutItem}. Concrete blocks supply their codec +
 * block entity factory.
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
		// MOD-039: only "consume" the right-click when this block actually has a menu to open
		// (machines / chests / generators). Cables and other non-menu blocks have no GUI, so they
		// must return PASS — otherwise vanilla's ServerPlayerGameMode treats the click as handled
		// and never falls through to BlockItem.place, which is why a cable could not be placed
		// flush against another cable (RMB was eaten). Returning PASS lets placement proceed while
		// leaving GUI-bearing blocks unchanged (they still return SUCCESS on both sides).
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof MenuProvider provider) {
			if (!level.isClientSide()) {
				player.openMenu(provider);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
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

	/**
	 * Whether a cable should visually connect to this block (draw an arm + set the
	 * {@code PipeBlock.PROPERTY_BY_DIRECTION} flag on the neighbour cable). Default {@code true}:
	 * machines, generators, storage and cables themselves all expose an energy port, so they connect.
	 * Pure-container blocks with no energy contract (e.g. {@link IronChestBlock}) override this to
	 * {@code false} so the cable does not draw a misleading "energy goes here" arm toward them
	 * (MOD-038). Kept at the Block level (not the block entity) so the connection state is correct
	 * the instant the block is placed — no block-entity load race, the reason {@code CableBlock} uses
	 * a block-type check in the first place.
	 */
	public boolean isCableConnectable() {
		return true;
	}

	/**
	 * Whether a cable on the given {@code side} of this block should visually connect to this face.
	 * Face-aware variant of {@link #isCableConnectable()}: a block whose energy role varies per face
	 * (e.g. a wind mill that outputs EU only from its back) overrides this so the cable draws an arm
	 * only toward faces that actually carry an energy port, not toward inert faces.
	 *
	 * <p>{@code side} is the <b>world face of this block</b> the cable touches (the direction from
	 * this block toward the cable), matching the convention of
	 * {@link dev.alaindustrial.core.EnergyPortHost#energyPort(Direction)}. The decision is made purely
	 * from the {@link BlockState} (e.g. {@code FACING}) — no block entity — for the same no-load-race
	 * reason as the face-agnostic marker. Default delegates to {@link #isCableConnectable()} so blocks
	 * with a uniform per-face role (every machine, cable, storage, the iron chest) keep their current
	 * behaviour unchanged.
	 */
	public boolean isCableConnectable(BlockState state, Direction side) {
		return isCableConnectable();
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
