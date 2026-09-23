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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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

	/**
	 * Whether the core has power to run on (MOD-650). The designer's model has a lit and an unlit skin:
	 * amber eyes and the seated cards' cyan glow go dark when the buffer is empty. Defaults to unlit,
	 * so a core placed before this property existed loads dark and lights at its first server tick.
	 */
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public MonitorCoreBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(CARDS, 0).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(CARDS, LIT);
	}

	@Override
	protected MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		// Everything now happens on the screen (MOD-480): a card goes into a socket the player can see,
		// and a full rack refuses it visibly instead of swallowing the click. Sneaking with a card in
		// hand still seats it in one move, because that is the gesture the player already learned.
		if (player.isSecondaryUseActive() && stack.getItem() instanceof CapacityCardItem) {
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
			// Rack full: say so rather than eating the click in silence.
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
					"gui.alaindustrial.monitor_core.rack_full"));
			return InteractionResult.SUCCESS;
		}
		return openScreen(state, level, pos, player);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		return openScreen(state, level, pos, player);
	}

	/** Open the rack. The core is a MenuProvider, so the screen is what answers every question now. */
	private static InteractionResult openScreen(BlockState state, Level level, BlockPos pos, Player player) {
		if (!(level.getBlockEntity(pos) instanceof MonitorCoreBlockEntity core)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		player.openMenu(core);
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
