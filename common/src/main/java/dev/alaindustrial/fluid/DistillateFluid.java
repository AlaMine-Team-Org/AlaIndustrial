package dev.alaindustrial.fluid;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Shared base for the two distillation fractions (MOD-251): diesel and fuel oil. Unlike crude oil
 * ({@link OilFluid}) the fractions deliberately behave <b>like water</b> — no world burning, no
 * entity drag, no fog — because they are machine products the player pours between tanks, not a
 * hazard to explore. The physics numbers fixed by the task: slope find distance 2, drop-off 2,
 * {@code canConvertToSource} hard {@code false} (a spilled bucket never becomes an infinite
 * source — the anti-dup rule shared with oil).
 *
 * <p>Structured exactly like {@link OilFluid}: one abstract base per fluid with nested
 * {@code Source}/{@code Flowing} subclasses, registered per loader (eager on Fabric,
 * {@code DeferredRegister} + {@code FluidType} subclasses on NeoForge). Cross-references go through
 * the {@code ModContent} facade at runtime only.
 */
public abstract class DistillateFluid extends FlowingFluid {

	@Override
	protected boolean canConvertToSource(ServerLevel level) {
		return false;
	}

	@Override
	protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
		Block.dropResources(state, level, pos, blockEntity);
	}

	@Override
	public int getSlopeFindDistance(LevelReader level) {
		return 2;
	}

	@Override
	public int getDropOff(LevelReader level) {
		return 2;
	}

	/** Water's spread rate — the fractions are refined liquids, thinner than the crude they came from. */
	@Override
	public int getTickDelay(LevelReader level) {
		return 5;
	}

	@Override
	public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction) {
		return direction == Direction.DOWN && !isSame(other);
	}

	@Override
	protected float getExplosionResistance() {
		return 100.0F;
	}

	@Override
	public Optional<SoundEvent> getPickupSound() {
		return Optional.of(SoundEvents.BUCKET_FILL);
	}
}
