package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The quarry's controller head (MOD-950, stage A of MOD-637): the head of one tower — the main one — that reads the
 * frame and says whether it stands.
 *
 * <p>{@link #FORMED} is the outward answer: the panel lights when the frame is whole, readable from across the base
 * without clicking. A block state rather than a block-entity field for the reason the reactor and crystal-farm
 * controllers give — the client needs it for the model, and a block state is already synced.
 *
 * <p>No {@code facing}: the head takes power on every face. A front face would be energy-inert
 * ({@code EnergyBlockEntity.facingAwareRole}), and the foundation that lends this block's port asks it through the
 * foundation's own world face — so one of the foundation's sides would draw a cable arm into a refused port.
 */
public class QuarryControllerBlock extends AbstractMachineBlock {

	/**
	 * Whether the frame around this controller passed its last scan — the frame parts' own flag, because the
	 * controller is a cell of the frame and hides under the designer's model with the rest of it.
	 */
	public static final BooleanProperty FORMED = QuarryFramePart.FORMED;

	public QuarryControllerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FORMED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FORMED);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new QuarryControllerBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof QuarryControllerBlockEntity controller) {
			controller.requestScan();
		}
	}

	/**
	 * A neighbour changed — a path or the scaffold under the head — so the scan is re-armed at once. The block entity
	 * still re-scans on its own timer, because most of a frame is nobody's neighbour here: a beam broken at the far
	 * tower reaches this block only through the next sweep.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof QuarryControllerBlockEntity controller) {
			controller.requestScan();
		}
	}

	/** Once the frame forms, the controller head hides under the designer's model like every other cell. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return state.getValue(FORMED) ? RenderShape.INVISIBLE : super.getRenderShape(state);
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return state.getValue(FORMED) ? Shapes.empty() : super.getOcclusionShape(state);
	}

	@Override
	protected int getLightDampening(BlockState state) {
		return state.getValue(FORMED) ? 0 : super.getLightDampening(state);
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return state.getValue(FORMED) || super.propagatesSkylightDown(state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return QuarryFramePart.shape(state, level, pos, super.getShape(state, level, pos, context));
	}
}
