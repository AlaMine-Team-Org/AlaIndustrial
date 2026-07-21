package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Silver Chest block — the next tier above the iron chest, a horizontal-facing storage cube that opens
 * a 45-slot GUI on right-click and spills its contents when broken. One row larger than the iron chest
 * (36 → 45): 5 rows of 9 slots. Like {@link IronChestBlock}, it extends {@link HorizontalMachineBlock}
 * only to inherit the {@code facing} placement + the right-click-to-open-menu behaviour from
 * {@link AbstractMachineBlock#useWithoutItem}; it is <em>not</em> a machine (no energy, no {@code lit}
 * state, no server processing ticker).
 *
 * <p>Rendering: same 3D chest model + animated lid as the iron chest (see
 * {@code ChestBlockEntityRenderer}), textured with {@code entity/chest/silver.png}. The block
 * model is a particle-only shell, so only the 3D BER geometry is seen.
 *
 * <p>Crafted by surrounding an iron chest with silver ingots (see {@code recipe/silver_chest.json}),
 * mirroring the iron chest's own "previous tier + 8 ingots" recipe pattern.
 */
public class SilverChestBlock extends HorizontalMachineBlock {
	public static final MapCodec<SilverChestBlock> CODEC = simpleCodec(SilverChestBlock::new);
	/**
	 * Collision/outline shape — identical to the iron chest / vanilla chest footprint: a 14×14×14
	 * column ({@code box(1,0,1,15,14,15)}). See {@link IronChestBlock#SHAPE} for the occlusion invariant
	 * ({@code noOcclusion()} + non-full-cube shape ⇒ no X-ray gap into neighbours).
	 */
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 14.0);

	public SilverChestBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	/**
	 * Silver chest is a pure 45-slot container with no energy port — its block entity is a vanilla
	 * {@code BaseContainerBlockEntity}, not a {@link dev.alaindustrial.block.entity.MachineBlockEntity}.
	 * A cable must therefore not draw an arm toward it, same as the iron chest (MOD-038).
	 */
	@Override
	public boolean isCableConnectable() {
		return false;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SilverChestBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// Client only — the lid controller ticks on the client (where the renderer reads it). The
		// server never needs a per-tick callback here; opener-count rechecks arrive via the block's
		// scheduledTick → #tick below.
		if (!level.isClientSide()) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof SilverChestBlockEntity chest) {
				SilverChestBlockEntity.lidAnimateTick(lvl, pos, st, chest);
			}
		};
	}

	/**
	 * Scheduled-tick handler: re-evaluates who has the chest open on the server (same mechanism as the
	 * iron chest — see {@link IronChestBlock#tick}).
	 */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getBlockEntity(pos) instanceof SilverChestBlockEntity chest) {
			chest.recheckOpen();
		}
	}
}
