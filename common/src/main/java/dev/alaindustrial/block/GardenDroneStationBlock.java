package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
public class GardenDroneStationBlock extends AbstractMachineBlock implements MachineHumProvider {
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
		return humMachineTicker(level);
	}

	// ---------------------------------------------------------------- flight loop (MOD-329)

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.GARDEN_DRONE_FLY;
	}

	/**
	 * Quieter than any of the fixed machines (they sit at 0.28–0.35). Two reasons: the drone is airborne
	 * for most of every work cycle, so this is the mod's most-heard loop by a wide margin, and unlike a
	 * machine it comes to the player rather than the player to it — a farm's drone will pass right over
	 * someone's head. Loud enough to notice, quiet enough to stand next to a farm.
	 */
	@Override
	public float humVolume() {
		return 0.22f;
	}

	/**
	 * Pattern C: there is no {@code lit} state to read, and the thing that makes the noise is the drone,
	 * so "working" means <b>airborne</b> — a target is assigned. A docked drone is silent, including
	 * during the beat it stands on the tile it just worked (MOD-317), which is exactly right: rotors
	 * spun down on the ground.
	 */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return level.getBlockEntity(pos) instanceof GardenDroneStationBlockEntity station
				&& station.droneTarget() != null;
	}

	/**
	 * The drone's own position, so the loop travels with it instead of staying on the dock.
	 *
	 * <p>Follows the same interpolation the renderer draws — {@code travel} along the leg, run through
	 * the flight's shared easing curve ({@link GardenDroneStationBlockEntity#accelerate}), reversed on
	 * the way home. The renderer's purely cosmetic touches (the climb arc, the sideways sway, the hover
	 * bob) are deliberately left out: they move the drone by at most a block or so, which is below what
	 * anyone can localise by ear, and copying them here would be three more things to keep in step with
	 * the renderer for no audible gain.
	 */
	@Override
	public Vec3 humPosition(Level level, BlockPos pos, BlockState state) {
		if (!(level.getBlockEntity(pos) instanceof GardenDroneStationBlockEntity station)) {
			return null;
		}
		BlockPos target = station.droneTarget();
		if (target == null) {
			return null;
		}
		float eased = GardenDroneStationBlockEntity.accelerate(station.flightProgress(level.getGameTime()));
		float travel = station.isReturning() ? 1.0f - eased : eased;
		return new Vec3(
				Mth.lerp(travel, pos.getX(), target.getX()) + 0.5,
				Mth.lerp(travel, pos.getY(), target.getY()) + 0.5,
				Mth.lerp(travel, pos.getZ(), target.getZ()) + 0.5);
	}
}
