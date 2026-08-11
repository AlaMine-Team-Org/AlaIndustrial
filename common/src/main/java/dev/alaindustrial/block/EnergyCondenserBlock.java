package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Energy Condenser (MOD-393): an open frame with an orb turning inside it, banking the grid's
 * surplus. Behaviour lives in {@link EnergyCondenserBlockEntity}; this class is shape, faces and state.
 *
 * <p>Rotation-symmetric — no {@code FACING}, because which way it points would mean nothing. Cable
 * arms are refused on the four sides so power enters only through top and bottom, matching the block
 * entity's face roles exactly: the cable asks the BLOCK whether it may connect (deliberately, to avoid
 * racing block-entity load), so if the two ever disagreed a cable would draw an arm to a face that
 * carries nothing. Items leave through the sides, so power and logistics never contend for an edge.
 *
 * <p>The shape is a full cube even though the frame is open: the orb the renderer draws does not exist
 * for hit-testing, and a shape cut to the frame's ribs would let clicks pass straight through the orb
 * into whatever is behind it — the exact regression the fluid tank's glass once had.
 */
public class EnergyCondenserBlock extends AbstractMachineBlock {

	public static final MapCodec<EnergyCondenserBlock> CODEC = simpleCodec(EnergyCondenserBlock::new);

	/**
	 * One box a pixel in from the sides, not a full cube: the frame is open, so the block declares
	 * {@code noOcclusion()}, and the block-standards gate rightly refuses "full cube that does not
	 * occlude" — that pair culls neighbours' faces as if solid while showing gaps. Inset instead of cut
	 * to the ribs, because the orb the renderer draws does not exist for hit-testing: a shape following
	 * the gaps would let clicks pass straight through it (the fluid tank's glass regression).
	 */
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

	public EnergyCondenserBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.LIT, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.LIT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
			BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Power enters vertically only; the sides are for items. Must mirror the block entity's roles. */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return side.getAxis() == Direction.Axis.Y;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new EnergyCondenserBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
