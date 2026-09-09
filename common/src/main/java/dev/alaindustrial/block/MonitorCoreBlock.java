package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.MonitorCoreBlockEntity;
import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import dev.alaindustrial.item.misc.CapacityCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The monitor core (MOD-480) — the one mandatory block of a monitoring wall.
 *
 * <p>It takes power on any face but its own front (R-NRG-03), joins the smart wire that reads the
 * player's containers, and carries the rack of capacity cards. Cards go in and come out by hand:
 * right-click with a card to slot one, empty hand to take the last one back. That is deliberately
 * not a screen — the rack is meant to be read off the block's face, and a wall the player builds
 * out of blocks should be configured the same way.
 */
public class MonitorCoreBlock extends HorizontalMachineBlock {

	public static final MapCodec<MonitorCoreBlock> CODEC = simpleCodec(MonitorCoreBlock::new);

	/**
	 * How many capacity cards are seated, 0..10 — in the block state so the rack SHOWS it.
	 *
	 * <p>In the palette rather than only in the block entity because that is what lets the face be a
	 * plain model: eleven front textures, no renderer, nothing to draw per frame. Eleven values times
	 * four facings is 44 states, which is nothing next to the geometry-bearing properties elsewhere in
	 * the mod (the item pipe pays 38 416).
	 */
	public static final IntegerProperty CARDS =
			IntegerProperty.create("cards", 0, MonitorCoreBlockEntity.CARD_SLOTS);

	public MonitorCoreBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(CARDS, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(CARDS);
	}

	@Override
	protected MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(stack.getItem() instanceof CapacityCardItem)) {
			// Not PASS: in 26.2 that would not fall through, so taking a card back with anything in
			// hand would silently do nothing.
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof MonitorCoreBlockEntity core && core.insertCard(stack)) {
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
			level.playSound(null, pos, SoundEvents.LODESTONE_PLACE, SoundSource.BLOCKS, 0.7f, 1.6f);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof MonitorCoreBlockEntity core)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ItemStack card = core.removeLastCard();
		if (card.isEmpty()) {
			// Nothing to take out, so the click reports what the wall is doing instead of doing
			// nothing at all — the state of this system is otherwise invisible until a panel lights up.
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
					"gui.alaindustrial.monitor_core.status", core.trackableTypes(), core.servedPanels()));
			return InteractionResult.SUCCESS;
		}
		if (!player.getInventory().add(card)) {
			player.drop(card, false);
		}
		level.playSound(null, pos, SoundEvents.LODESTONE_PLACE, SoundSource.BLOCKS, 0.7f, 1.2f);
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
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.getBlockEntity(pos) instanceof MonitorCoreBlockEntity core) {
			core.ensureRegistered();
		}
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MonitorCoreBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
