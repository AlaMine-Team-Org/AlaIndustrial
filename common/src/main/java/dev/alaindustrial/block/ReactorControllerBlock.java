package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;

/**
 * The brain of a reactor room (MOD-468, stage 1): it scans the shell around it, reports what is wrong,
 * and is the block the player opens to find out.
 *
 * <p>The {@code formed} property is the outward half of that answer — the front texture lights up when
 * the room is sealed, so a correct build is visible from across the base without opening anything. It
 * is a blockstate rather than a block-entity field for the same reason the distillation column keeps
 * {@code formed} in its state: the client needs it for the model, and a blockstate is already synced.
 *
 * <p><b>Stage 1 carries no energy.</b> The block entity is a {@link dev.alaindustrial.block.entity.MachineBlockEntity}
 * (the menu-width gate requires one) but with a zero buffer, and {@code reactor_controller} is listed in
 * {@link dev.alaindustrial.registry.BlockCapabilityRoster#NO_ENERGY_CAPABILITY} so no cable can attach
 * to a controller that has nothing to give yet. Stage 2 gives it a real buffer and removes that line.
 */
public class ReactorControllerBlock extends HorizontalMachineBlock {

	public static final MapCodec<ReactorControllerBlock> CODEC = simpleCodec(ReactorControllerBlock::new);

	/** Whether the room around this controller passed its last scan. Drives the lit front texture. */
	public static final BooleanProperty FORMED = BooleanProperty.create("formed");

	public ReactorControllerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FORMED, false));
	}

	@Override
	protected MapCodec<? extends ReactorControllerBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FORMED);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ReactorControllerBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/**
	 * Any neighbour change may have opened or closed the shell — a wall block mined, a door broken, a
	 * port placed — so the scan is re-armed immediately rather than waiting for the periodic sweep.
	 * The block entity still re-scans on its own timer, because a shell can also change out of range
	 * of this callback (a block broken on the far wall is nobody's neighbour here).
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide()
				&& level.getBlockEntity(pos) instanceof ReactorControllerBlockEntity controller) {
			controller.requestScan();
		}
	}
	/**
	 * Un-forms the room the moment the player starts taking the controller out — before the block, and
	 * with it the block entity that remembers the shell, is gone.
	 *
	 * <p>{@code playerWillDestroy} rather than any of the after-the-fact hooks: it is the one point
	 * where the block entity is still attached AND the world is not in the middle of removing anything,
	 * so painting the shell here is an ordinary edit rather than a re-entrant one.
	 */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide()
				&& level.getBlockEntity(pos) instanceof ReactorControllerBlockEntity brain) {
			brain.unformOnRemoval(level);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
