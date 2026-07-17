package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.item.FluidTankBucketInteractions;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Portable passive fluid tank (MOD-111): no EU, GUI or automatic push. Right-click exchanges whole
 * buckets/capsules; every face remains available to loader fluid capabilities.
 */
public final class FluidTankBlock extends BaseEntityBlock {
	public static final MapCodec<FluidTankBlock> CODEC = simpleCodec(FluidTankBlock::new);
	/**
	 * Frame plus the glass walls. The baked model carries only the frame — plates and corner posts —
	 * because the renderer draws the glass, and a shape cannot see it. Tracing the frame alone left
	 * the walls open: a click aimed at the tank passed between the posts and landed on whatever stood
	 * behind it, so a bucket of lava went onto the floor of your house.
	 *
	 * <p>The walls are panes rather than a solid block on purpose: a full cube would have to occlude
	 * its neighbours to satisfy the block standards, and an occluding tank renders its own inside
	 * black.
	 */
	private static final VoxelShape SHAPE = Shapes.or(
			Block.box(0, 0, 0, 16, 3, 16),
			Block.box(0, 13, 0, 16, 16, 16),
			Block.box(0, 3, 0, 3, 13, 3),
			Block.box(13, 3, 0, 16, 13, 3),
			Block.box(0, 3, 13, 3, 13, 16),
			Block.box(13, 3, 13, 16, 13, 16),
			Block.box(0, 3, 0, 16, 13, 1),
			Block.box(0, 3, 15, 16, 13, 16),
			Block.box(0, 3, 0, 1, 13, 16),
			Block.box(15, 3, 0, 16, 13, 16));

	public FluidTankBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FluidTankBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank)) {
			return InteractionResult.PASS;
		}
		if (stack.is(ModContent.VACUUM_CAPSULE.get()) || stack.is(ModContent.FILLED_VACUUM_CAPSULE.get())) {
			InteractionResult capsule = stack.useOn(new UseOnContext(player, hand, hit));
			return capsule.consumesAction() ? capsule : InteractionResult.SUCCESS;
		}
		return FluidTankBucketInteractions.exchange(level, pos, player, hand, tank);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		if (!(level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) || tank.fluidTank.amount <= 0) {
			return 0;
		}
		return 1 + (int) Math.floor(14.0 * tank.fluidTank.amount / tank.fluidTank.capacity);
	}
}
