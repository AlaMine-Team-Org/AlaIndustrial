package dev.alaindustrial.fluid;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
 * ({@link OilFluid}) the fractions are <b>harmless</b> — no world burning, no drowning — because they
 * are machine products the player pours between tanks, not a hazard to explore.
 *
 * <p><b>Harmless is not the same as inert (MOD-496).</b> The original wording of this class promised
 * they would "behave like water", but a modded fluid is in neither vanilla tag and so inherits
 * neither water's swimming nor its buoyancy: with no physics at all an entity dropped to the bottom
 * of a pool and could not climb out — a trap, which is the opposite of harmless. They now carry the
 * shared immersion physics ({@link FluidImmersion}), tuned per fluid off the viscosity already fixed
 * in their fluid types: lighter damping than crude, the same held-jump ascent, no drowning.
 * The physics numbers fixed by the task: slope find distance 2, drop-off 2,
 * {@code canConvertToSource} hard {@code false} (a spilled bucket never becomes an infinite
 * source — the anti-dup rule shared with oil).
 *
 * <p>Structured exactly like {@link OilFluid}: one abstract base per fluid with nested
 * {@code Source}/{@code Flowing} subclasses, registered per loader (eager on Fabric,
 * {@code DeferredRegister} + {@code FluidType} subclasses on NeoForge). Cross-references go through
 * the {@code ModContent} facade at runtime only.
 */
public abstract class DistillateFluid extends FlowingFluid {

	// MOD-498 — canConvertToSource(ServerLevel) is protected abstract on FlowingFluid, so it MUST be
	// implemented; vanilla does not deprecate it, only NeoForge's patch does, pointing at its own
	// position-sensitive canConvertToSource(FluidState, ServerLevel, BlockPos). This class is shared code
	// compiled for Fabric too. The answer is a constant false anyway (the fractions never form infinite
	// sources — the anti-dup rule shared with crude oil), so position could not change it.
	@SuppressWarnings("deprecation")
	@Override
	protected boolean canConvertToSource(ServerLevel level) {
		return false;
	}

	/**
	 * Immersion physics for the fractions (MOD-496) — damping, the held-jump ascent and the
	 * fall-distance reset, all from the shared {@link FluidImmersion} profile of this fluid.
	 *
	 * <p>Same seat as crude oil's: {@code Entity.checkInsideBlocks} fires this on BOTH sides, so
	 * client and server agree and the local player never fights a server correction.
	 */
	@Override
	protected void entityInside(Level level, BlockPos pos, Entity entity,
			InsideBlockEffectApplier effectApplier) {
		super.entityInside(level, pos, entity, effectApplier);
		FluidImmersion.applyEntityInside(level, pos, entity);
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
