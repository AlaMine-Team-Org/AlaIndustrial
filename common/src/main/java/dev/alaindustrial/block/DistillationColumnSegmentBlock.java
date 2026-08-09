package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnSegmentBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shared base of the Distillation Column's middle and top segments (MOD-251). The player never
 * crafts or carries these: the base places them ({@code DistillationColumnBlock.setPlacedBy}) and
 * every teardown removes them — no block item, empty loot tables (the incubator dome's precedent).
 *
 * <p>Unlike the dome, each segment carries a lightweight
 * {@link DistillationColumnSegmentBlockEntity}: both loaders resolve fluid capabilities strictly
 * per-position per-BE-type, so a BE-less segment would be invisible to a pump on its face — the
 * middle could never take oil, the top could never give diesel (2026-08-09 audit; see task.md).
 *
 * <p><b>Any segment down ⇒ the whole tower down, exactly one drop.</b> The segment's own removal
 * hook fires the base's destruction with the drop (loot table + {@code copy_components}); the
 * base's removal hook then clears the remaining segments droplessly. Creative intent travels via
 * the base's suppression handshake, the dome's exact pattern.
 */
public abstract class DistillationColumnSegmentBlock extends BaseEntityBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	protected DistillationColumnSegmentBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false));
	}

	/** How many blocks below this segment the tower's base (master BE) sits: middle 1, top 2. */
	public abstract int offsetToBase();

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LIT);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	/**
	 * Round-2 voxel silhouette shared by both segments: the chamfered column with its junction
	 * flanges and the four port stubs (clicks on a nozzle must land on the tower). Approximate to
	 * the model geometry — shape and renderer geometry are independent.
	 */
	private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
			net.minecraft.world.phys.shapes.Shapes.or(
					Block.box(1, 0, 1, 15, 1, 15),
					Block.box(2, 1, 2, 14, 16, 14),
					Block.box(5, 4, 0, 11, 11, 16),
					Block.box(0, 4, 5, 16, 11, 11));

	@Override
	protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
			net.minecraft.world.level.BlockGetter level, BlockPos pos,
			net.minecraft.world.phys.shapes.CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DistillationColumnSegmentBlockEntity(pos, state);
	}

	/** The tower's base position for a segment at {@code pos}. */
	protected BlockPos basePos(BlockPos pos) {
		return pos.below(offsetToBase());
	}

	/** The master block entity, or {@code null} while the tower is torn down / not yet raised. */
	protected DistillationColumnBlockEntity master(Level level, BlockPos pos) {
		return level.getBlockEntity(basePos(pos)) instanceof DistillationColumnBlockEntity master
				? master : null;
	}

	/** Wrench-clean works on any segment too — see {@code DistillationColumnBlock.tryWrenchClean}. */
	@Override
	protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state,
			Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
			BlockHitResult hit) {
		InteractionResult cleaned =
				DistillationColumnBlock.tryWrenchClean(stack, level, basePos(pos), player);
		if (cleaned != InteractionResult.PASS) {
			return cleaned;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	/**
	 * A segment is half of one machine, so clicking it opens that machine — without this the upper
	 * two thirds of the tower would be dead to the touch (the incubator dome's rationale).
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		DistillationColumnBlockEntity master = master(level, pos);
		if (master == null) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			player.openMenu(master);
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Round 3 (hand-built multiblock): breaking a segment DEGRADES the tower instead of felling it.
	 * The broken segment drops one column-segment item (its loot table), the base flips back to a
	 * blank in place — keeping its tanks and fouling in its block entity — and the other segment
	 * degrades into a blank too. Nothing vanishes, nothing double-drops, and every removal path is
	 * covered here (player break, wrench, explosion; {@code /setblock} bypasses by design).
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		BlockPos base = basePos(pos);
		DistillationColumnBlock.degradeBase(level, base);
		DistillationColumnBlock.degradeSegment(level, base.above());
		DistillationColumnBlock.degradeSegment(level, base.above(2));
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	/** A segment only stands on its designated support: middle on the base, top on the middle. */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return offsetToBase() == 1
				? below.getBlock() instanceof DistillationColumnBlock
				: below.getBlock() instanceof DistillationColumnSegmentBlock;
	}

	/** Lost support from below ⇒ this segment degrades into a lone blank, in place (round 3). */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
			BlockState neighbourState, RandomSource random) {
		if (directionToNeighbour == Direction.DOWN && !canSurvive(state, level, pos)) {
			return dev.alaindustrial.registry.ModContent.DISTILLATION_COLUMN.get().defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos,
				neighbourState, random);
	}
}
