package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tempered iron scaffolding (MOD-927): walked into from the side, climbed like a ladder, stood on from above.
 *
 * <p>The collision follows vanilla scaffolding without its stability: no distance, no support needed, never
 * pops off. An entity whose feet are above the block stands on its top plate unless it is sneaking, which
 * lets it drop through; from anywhere else the block does not collide, so the entity walks in. Climbing comes
 * from the {@code #minecraft:climbable} tag, not from this class. The outline stays the full cube.
 *
 * <p>Vanilla's own {@code ScaffoldingBlock} is not reused: its distance chain reads the vanilla block by
 * identity, so a subclass without solid support below would crumble.
 */
public class TemperedIronScaffoldBlock extends Block {

	/** The top plate an entity stands on: the upper two pixels of the cube, as on vanilla scaffolding. */
	private static final VoxelShape TOP = Block.box(0.0, 14.0, 0.0, 16.0, 16.0, 16.0);

	public TemperedIronScaffoldBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(QuarryFramePart.FORMED, false));
	}

	/** MOD-950: a quarry tower's scaffolding hides under the designer's model once the frame forms. */
	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(QuarryFramePart.FORMED);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		if (QuarryFramePart.formed(state)) {
			// MOD-950: a formed tower's lattice is solid where the designer's model draws it, and only there.
			return QuarryFramePart.shape(state, level, pos, Shapes.block());
		}
		if (context.isPlacement()) {
			return Shapes.empty();
		}
		if (context.isAbove(Shapes.block(), pos, true) && !context.isDescending()) {
			return TOP;
		}
		return Shapes.empty();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return QuarryFramePart.renderShape(state, super.getRenderShape(state));
	}

	@Override
	protected int getLightDampening(BlockState state) {
		return QuarryFramePart.lightDampening(state, super.getLightDampening(state));
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return QuarryFramePart.skylight(state, super.propagatesSkylightDown(state));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return QuarryFramePart.shape(state, level, pos, super.getShape(state, level, pos, context));
	}

	/** A formed cell opens the frame's controller screen ({@link QuarryFramePart#openController}). */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		return QuarryFramePart.openController(state, level, pos, player);
	}

	/** Metal underfoot once formed ({@link QuarryFramePart#sound}). */
	@Override
	protected SoundType getSoundType(BlockState state) {
		return QuarryFramePart.sound(state, super.getSoundType(state));
	}
}
