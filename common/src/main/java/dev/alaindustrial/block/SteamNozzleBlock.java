package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.SteamNozzleBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The steam nozzle (MOD-468, stage 3) — the far end of the cooling loop.
 *
 * <p><b>Why the exhaust is a block and not a rule.</b> Water that boils has to go somewhere. Letting
 * steam simply vanish inside the column would make the loop half a loop: the player would plumb water
 * in and never think about it again. Making the steam a fluid that has to be piped out and released
 * turns cooling into an actual circuit with two ends, and gives the room its silhouette — pipes going
 * in low, pipes coming out high, and something venting at the end of them.
 *
 * <p><b>It has to face open air.</b> Steam is released into the block in front; if that block is solid
 * the nozzle stalls, the line behind it backs up and the reactor heats. No error text says so, and
 * none is needed — a nozzle buried in a wall does nothing, which is exactly what a real one would do.
 *
 * <p><b>Nothing consumes the steam yet, and that is deliberate.</b> Stage 5 replaces this block with a
 * turbine at the same place in the same circuit; a player who plumbs an exhaust today does not rebuild
 * anything then. Venting first, harvesting later — the pipework is the part that has to be right.
 */
public class SteamNozzleBlock extends BaseEntityBlock {

	public static final MapCodec<SteamNozzleBlock> CODEC = simpleCodec(SteamNozzleBlock::new);

	/** Which way the mouth points. Set from the face the player clicked, so it always faces outward. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	public SteamNozzleBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends SteamNozzleBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	/**
	 * A stub sticking out of a surface, ten pixels long — not a cube.
	 *
	 * <p>Without this the nozzle kept the default full-block shape: it looked like a small flange and
	 * collided like a crate, and a player walking past an exhaust bank would snag on thin air. The
	 * BLOCK_STANDARDS sweep catches exactly this pairing (a full-cube shape declared
	 * {@code noOcclusion}) because it is invisible in a screenshot and obvious the moment you try to
	 * walk through it.
	 */
	private static final VoxelShape[] SHAPES = new VoxelShape[Direction.values().length];

	static {
		// UP and DOWN were the wrong way round. FACING is the clicked face, so the stub always grows AWAY
		// from the surface it was placed against: facing=up means it stands on a floor and rises from
		// y=0, facing=down means it hangs from a ceiling and reaches to y=16. The horizontal four had
		// this right, which is exactly why the mistake survived — it only shows on a vertical nozzle,
		// where the model and the hitbox sat six pixels apart.
		SHAPES[Direction.DOWN.ordinal()] = Block.box(3, 6, 3, 13, 16, 13);
		SHAPES[Direction.UP.ordinal()] = Block.box(3, 0, 3, 13, 10, 13);
		SHAPES[Direction.NORTH.ordinal()] = Block.box(3, 3, 6, 13, 13, 16);
		SHAPES[Direction.SOUTH.ordinal()] = Block.box(3, 3, 0, 13, 13, 10);
		SHAPES[Direction.WEST.ordinal()] = Block.box(6, 3, 3, 16, 13, 13);
		SHAPES[Direction.EAST.ordinal()] = Block.box(0, 3, 3, 10, 13, 13);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return SHAPES[state.getValue(FACING).ordinal()];
	}

	/**
	 * Points away from the surface it was placed against — {@code getClickedFace()} is the face of the
	 * neighbour being clicked, so the mouth ends up sticking out into the open rather than into the
	 * block the player just aimed at.
	 */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SteamNozzleBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide() || type != ModContent.STEAM_NOZZLE_BE.get()) {
			return null;
		}
		// Server only. The plume is spawned with ServerLevel.sendParticles, which broadcasts it to
		// everyone in range on its own — so the steam level never has to be synced to clients at all.
		return (world, pos, blockState, entity) ->
				((SteamNozzleBlockEntity) entity).tick(world, pos, blockState);
	}
}
