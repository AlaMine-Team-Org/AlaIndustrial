package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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
 */
public class RectificationSectionBlock extends Block {
	public static final MapCodec<RectificationSectionBlock> CODEC =
			simpleCodec(RectificationSectionBlock::new);
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** The chamfered column continues, with the tray fins around it (approximate, like the tower). */
	private static final VoxelShape SHAPE = Shapes.or(
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
	protected MapCodec<? extends Block> codec() {
		return CODEC;
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
