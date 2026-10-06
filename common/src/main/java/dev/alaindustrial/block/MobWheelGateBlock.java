package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MobWheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Mob Wheel Gate (MOD-763, D1/D3): the only way in or out of the wheel, and its only switch.
 *
 * <p>Right-click swings it like a fence gate. On an assembled wheel, closing it shuts one compatible mob in
 * (the drive picks the one standing on the deck) and opening it lets that mob go — there is no other way to
 * stop the wheel. Closed it is a thin panel a block and a half high, like a vanilla gate; open it has no
 * collision. Its {@code facing} is the way the wheel's front faces once assembled.
 */
public class MobWheelGateBlock extends MobWheelMemberBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

	/** Outline of the panel across the doorway, by axis of the facing; shapes built once (ADR-023). */
	private static final VoxelShape OUTLINE_Z = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
	private static final VoxelShape OUTLINE_X = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
	/** Collision of the closed panel: 1.5 blocks high, so nothing jumps it — a vanilla gate's rule. */
	private static final VoxelShape CLOSED_Z = Block.box(0.0, 0.0, 6.0, 16.0, 24.0, 10.0);
	private static final VoxelShape CLOSED_X = Block.box(6.0, 0.0, 0.0, 10.0, 24.0, 16.0);

	public MobWheelGateBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(FACING, Direction.SOUTH)
				.setValue(OPEN, Boolean.FALSE)
				.setValue(MobWheelStructure.FORMED, Boolean.FALSE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN, MobWheelStructure.FORMED);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	private static boolean alongZ(BlockState state) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return alongZ(state) ? OUTLINE_Z : OUTLINE_X;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		if (state.getValue(OPEN)) {
			return Shapes.empty();
		}
		return alongZ(state) ? CLOSED_Z : CLOSED_X;
	}

	/**
	 * Swing the gate. Loose it is just a gate; on an assembled wheel the drive learns of it — closing catches
	 * a mob, opening frees it.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (state.getValue(MobWheelStructure.FORMED)) {
			InteractionResult ledIn = leadIn(level, MobWheelStructure.findFormedController(level, pos), player);
			if (ledIn != null) {
				return ledIn;
			}
		}
		if (!level.isClientSide()) {
			toggle(level, pos, state, player);
		}
		return InteractionResult.SUCCESS;
	}

	/** Open a closed gate or close an open one; {@code public static} so a game test can swing it. */
	public static void toggle(Level level, BlockPos pos, BlockState state, @Nullable Player player) {
		boolean open = !state.getValue(OPEN);
		swing(level, pos, state, open);
		if (!state.getValue(MobWheelStructure.FORMED)) {
			return;
		}
		BlockPos controller = MobWheelStructure.findFormedController(level, pos);
		if (controller != null && level.getBlockEntity(controller) instanceof MobWheelBlockEntity drive) {
			if (open) {
				drive.release();
			} else {
				drive.capture();
			}
		}
	}

	/** Set the gate open or shut with its sound, and nothing else: no mob is caught or let go here. */
	public static void swing(Level level, BlockPos pos, BlockState state, boolean open) {
		level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
		level.playSound(null, pos, open ? WoodType.OAK.fenceGateOpen() : WoodType.OAK.fenceGateClose(),
				SoundSource.BLOCKS, 1.0f, level.getRandom().nextFloat() * 0.1f + 0.9f);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
