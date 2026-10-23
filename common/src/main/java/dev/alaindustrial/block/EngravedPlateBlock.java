package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.settlement.works.BuildingCard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One plate of the lab plaque (MOD-513): a digit or a broken prefix letter engraved into slate.
 *
 * <p>No block entity and no recipe. On a town building's plaque a right click opens the building's card
 * (round 2 of 0.1.219, Q2). The engraving sits on the front face only, so
 * the block has a horizontal facing; extending {@link HorizontalDirectionalBlock} rather than adding
 * the property by hand is what makes a rotated lab template turn the plates with it.
 */
public class EngravedPlateBlock extends HorizontalDirectionalBlock {

	public static final MapCodec<EngravedPlateBlock> CODEC = simpleCodec(EngravedPlateBlock::new);

	public EngravedPlateBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** A plate of a town building's plaque opens that building's card (round 2 of 0.1.219, Q2); elsewhere it passes. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer && BuildingCard.open(serverPlayer, pos)) {
			return InteractionResult.SUCCESS;
		}
		return super.useWithoutItem(state, level, pos, player, hit);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}
}
