package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.MonitorPanelBlockEntity;
import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * One tile of a monitoring wall (MOD-480).
 *
 * <p>A panel is a whole block, not a plate, because the wall is meant to be BUILT: the player stacks
 * these the way they would stack any other block, and adjacent panels join into one network by
 * simple adjacency — free 3D shape, so a wall can turn a corner or wrap a pillar. The face the
 * numbers appear on is the one the player faced when placing it, which is what lets a wrapped wall
 * read correctly from every side.
 *
 * <p>Choosing what a panel watches is the item frame's idiom, deliberately: right-click with an item
 * to set it, empty hand to take it back. One panel shows one kind of item — build another panel to
 * watch another kind, which is the whole growth mechanic of the wall.
 */
public class MonitorPanelBlock extends BaseEntityBlock {

	public static final MapCodec<MonitorPanelBlock> CODEC = simpleCodec(MonitorPanelBlock::new);

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public MonitorPanelBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@SuppressWarnings("deprecation")
	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/**
	 * Set the watched item.
	 *
	 * <p>Returns {@code TRY_WITH_EMPTY_HAND} rather than {@code PASS} when the panel is already
	 * filled: in 26.2 a {@code PASS} here does NOT fall through to {@link #useWithoutItem}, so taking
	 * the filter back while holding anything at all would silently do nothing.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof MonitorPanelBlockEntity panel)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!panel.getFilter().isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		panel.setFilter(stack.copyWithCount(1));
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 1.2f);
		// Say straight away why this panel will (or will not) show a number. Without it the player
		// is left reading a cross with no idea that the answer is a card in the core.
		if (level instanceof ServerLevel serverLevel) {
			reportCapacity(serverLevel, pos, player);
		}
		return InteractionResult.SUCCESS;
	}

	/** Take the watched item back. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof MonitorPanelBlockEntity panel)) {
			return InteractionResult.PASS;
		}
		if (panel.getFilter().isEmpty()) {
			// An empty panel says what it is for. Without this it is a blank slab that swallows clicks.
			if (!level.isClientSide()) {
				player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
						"gui.alaindustrial.monitor_panel.hint"));
			}
			return InteractionResult.SUCCESS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ItemStack filter = panel.getFilter().copy();
		panel.setFilter(ItemStack.EMPTY);
		if (!player.getInventory().add(filter)) {
			player.drop(filter, false);
		}
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 1.2f);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			MonitorNetworkManager.onNeighbourChanged(serverLevel, pos);
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state,
			net.minecraft.world.entity.@Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.getBlockEntity(pos) instanceof MonitorPanelBlockEntity panel) {
			panel.ensureRegistered();
		}
	}

	/** Tell the player, in numbers, whether the wall can serve this panel. */
	private static void reportCapacity(ServerLevel level, BlockPos pos, Player player) {
		dev.alaindustrial.core.monitor.MonitorNetwork network =
				dev.alaindustrial.core.monitor.MonitorNetworkManager.networkAt(level, pos);
		if (network == null) {
			return;
		}
		dev.alaindustrial.core.monitor.MonitorNetwork.Capacity capacity = network.capacity();
		if (!capacity.hasCore()) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
					"gui.alaindustrial.monitor.no_core"));
		} else if (capacity.watchedTypes() > capacity.allowance()) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
					"gui.alaindustrial.monitor.capacity_full",
					capacity.watchedTypes(), capacity.allowance()));
		}
	}

	/** Panels join the network to each other and to the core — never to a wire (see the wire's javadoc). */
	public static boolean isPanelOrCore(net.minecraft.world.level.LevelReader level, BlockPos pos) {
		Block block = level.getBlockState(pos).getBlock();
		return block instanceof MonitorPanelBlock || block instanceof MonitorCoreBlock;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MonitorPanelBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof MonitorPanelBlockEntity panel) {
				panel.ensureRegistered();
			}
		};
	}
}
