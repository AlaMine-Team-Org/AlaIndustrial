package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		if (context.isPlacement()) {
			return Shapes.empty();
		}
		if (context.isAbove(Shapes.block(), pos, true) && !context.isDescending()) {
			return TOP;
		}
		return Shapes.empty();
	}
}
