package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import net.minecraft.core.BlockPos;
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
 * The Garden Drone Station dock (MOD-277): a low plate the drone lands on.
 *
 * <p>Deliberately rotation-symmetric — no {@code FACING}, no {@code lit}. The drone works a circle
 * around the station, so there is no "front" for a player to aim, and the working state is shown by
 * the drone itself (its status light) rather than by a second block model. That keeps the blockstate
 * to a single variant and every face able to take a cable, matching
 * {@link GardenDroneStationBlockEntity#energyRoleForFace}.
 *
 * <p>The plate shape follows the solar panels' precedent: a real, lower-than-full collision box, so
 * the outline the player sees matches what they walk on. The drone above it has no collision at all —
 * it is drawn by the block entity's renderer, not an entity.
 */
public class GardenDroneStationBlock extends AbstractMachineBlock {
	public static final MapCodec<GardenDroneStationBlock> CODEC = simpleCodec(GardenDroneStationBlock::new);

	/** Four-pixel dock plate; the drone parks on top of it. */
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

	public GardenDroneStationBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GardenDroneStationBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
