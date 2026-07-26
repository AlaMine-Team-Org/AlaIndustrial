package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The incubator's dome (MOD-118) — the visible upper half of the multiblock.
 *
 * <p>The player never crafts or carries this block: placing any glass on an incubator base swaps it
 * for this one, and taking the structure apart hands the original glass back. It therefore has an
 * empty loot table and no creative-tab entry; breaking it returns the glass instead of itself.
 *
 * <p>Purely structural — no block entity. The base owns the inventory, the energy and the renderer
 * that draws the item floating inside this chamber.
 */
public class IncubatorDomeBlock extends Block {

	public static final MapCodec<IncubatorDomeBlock> CODEC = simpleCodec(IncubatorDomeBlock::new);

	/**
	 * One box covering the silhouette. The model tapers towards the top, but a simplified shape keeps
	 * clicks from slipping between the frame ribs — the same call the fluid tank makes.
	 */
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

	public IncubatorDomeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/**
	 * The dome is half of one machine, so clicking it opens that machine. Without this the upper block
	 * is dead to the touch and the player has to find the base — which is often the half buried in the
	 * floor or hidden behind a hopper.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos.below()) instanceof IncubatorBlockEntity incubator)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			player.openMenu(incubator);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// A creative break drops nothing, as everywhere in vanilla. The removal hook below has no
		// player to ask, so the intent is left on the base for it to read back.
		if (!level.isClientSide() && player.isCreative()
				&& level.getBlockEntity(pos.below()) instanceof IncubatorBlockEntity incubator) {
			incubator.suppressDomeDrop();
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/**
	 * The single place the glass is accounted for, so every way of <em>destroying</em> the dome is
	 * covered — a player breaking it, an explosion, a fluid washing it away. Returning the glass only
	 * from the player path used to lose it outright in all the others, the dome having an empty loot
	 * table.
	 *
	 * <p>{@code /setblock} and {@code /fill} are the exception, and deliberately so: both place with
	 * {@code UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS} and without a neighbour update, so neither this hook
	 * nor the base's {@code preRemoveSideEffects} runs. That is the same bargain vanilla makes — a
	 * chest overwritten by {@code /setblock} does not spill its contents either.
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		IncubatorBlockEntity incubator =
				level.getBlockEntity(pos.below()) instanceof IncubatorBlockEntity base ? base : null;
		BlockState original = incubator != null
				? incubator.domeSource()
				: Blocks.GLASS.defaultBlockState();

		// By the time this runs the replacement block is already in place (LevelChunk writes the new
		// state before calling this). Dismantling the multiblock puts the original glass right here, so
		// there is nothing to hand back; anything else destroyed the dome and owes the player an item.
		boolean handedBack = level.getBlockState(pos).is(original.getBlock());
		boolean creativeBreak = incubator != null && incubator.consumeDomeDropSuppression();
		if (!handedBack && !creativeBreak) {
			Block.popResource(level, pos, new ItemStack(original.getBlock()));
		}
		if (incubator != null) {
			incubator.setFormed(false);
		}
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}
}
