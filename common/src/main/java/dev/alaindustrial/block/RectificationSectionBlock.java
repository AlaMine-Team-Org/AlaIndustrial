package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Rectification Section (MOD-251 round 2) — an optional fourth storey the player crafts and
 * places ON TOP of a distillation tower. With it the column's losses drop from 10 % to 5 %: every
 * run condenses an extra {@code +50 mB} of diesel (the light fraction is what better trays
 * recover). "Build taller — distil better", and the upgrade is visible on the horizon.
 *
 * <p>Deliberately the simplest structural block: no block entity, no ports (pumps still talk to
 * the three tower segments), a plain self-drop. It only stands on the tower's top segment —
 * losing that support drops it as an item (door-style {@code updateShape}). The base block entity
 * detects it by looking three blocks up; {@code lit} is mirrored by the base, and once the section
 * is present the steam plume moves up here (the top segment yields it).
 *
 * <p>Like the middle and top segments, a click on the section belongs to the tower (MOD-778): it opens
 * the column's screen and a wrench on it cleans the column — both routed to the base below.
 */
public class RectificationSectionBlock extends Block {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/**
	 * The chamfered column continues, with the tray fins around it (approximate, like the tower). Package
	 * visible for {@link DistillationColumnOutline}, which stacks the storeys' shapes into one contour.
	 */
	static final VoxelShape SHAPE = Shapes.or(
			Block.box(1, 0, 1, 15, 1, 15),
			Block.box(2, 1, 2, 14, 15, 14),
			Block.box(1, 4, 1, 15, 6, 15),
			Block.box(1, 9, 1, 15, 11, 15),
			Block.box(5, 15, 5, 11, 16, 11));

	public RectificationSectionBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LIT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/**
	 * The tower's base under a section standing on the top segment — one storey below the base of that
	 * segment — or {@code null} when the section stands on anything else.
	 */
	static @Nullable BlockPos basePos(BlockGetter level, BlockPos pos) {
		return level.getBlockState(pos.below()).getBlock() instanceof DistillationColumnTopBlock top
				? pos.below(top.offsetToBase() + 1) : null;
	}

	/** A wrench on the section cleans the column, exactly as on any segment ({@code tryWrenchClean}). */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		BlockPos base = basePos(level, pos);
		if (base != null) {
			InteractionResult cleaned = DistillationColumnBlock.tryWrenchClean(stack, level, base, player);
			if (cleaned != InteractionResult.PASS) {
				return cleaned;
			}
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	/**
	 * The section is a storey of the tower, so clicking it opens the tower's screen, as the middle and top
	 * segments do; with no tower below it the click passes on.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		BlockPos base = basePos(level, pos);
		if (base == null || !(level.getBlockEntity(base) instanceof DistillationColumnBlockEntity master)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			player.openMenu(master);
		}
		return InteractionResult.SUCCESS;
	}

	/** Only on the tower's top segment — the section upgrades a column, it is not a free-standing block. */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).getBlock() instanceof DistillationColumnTopBlock;
	}

	/** Tower below gone ⇒ the section pops off as its own item (plain self-drop loot). */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
			BlockState neighbourState, RandomSource random) {
		if (directionToNeighbour == Direction.DOWN && !canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos,
				neighbourState, random);
	}

	/** With the section installed the steam plume rises from up here (the top segment yields it). */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(LIT) && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
					pos.getX() + 0.35D + random.nextDouble() * 0.3D,
					pos.getY() + 1.05D,
					pos.getZ() + 0.35D + random.nextDouble() * 0.3D,
					0.0D, 0.06D + random.nextDouble() * 0.03D, 0.0D);
		}
	}
}
