package dev.alaindustrial.fluid;

import dev.alaindustrial.registry.ModContent;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Oil (MOD-238) — the mod's first custom fluid, structured exactly like vanilla
 * {@code WaterFluid}/{@code LavaFluid}: one abstract base with {@link Source} and {@link Flowing}
 * subclasses. Registered per loader (eager on Fabric, {@code DeferredRegister} on NeoForge, which
 * additionally subclasses both to attach its {@code FluidType}); all cross-references (source,
 * flowing, block, bucket) go through the loader-neutral {@link ModContent} facade and are only read
 * at runtime, never during class init.
 *
 * <p>Balance (PERFORMANCE.md): viscous — tick delay {@value #TICK_DELAY}, slope find distance 2,
 * drop-off 2 (water is 5/4/1, Overworld lava 30/2/2). {@link #canConvertToSource} is hard
 * {@code false}: two oil sources never form a third, so a worldgen deposit is finite (the anti-dup
 * decision of the visible-lakes model). Entities inside oil are dragged down to a wade — see
 * {@link #entityInside}.
 */
public abstract class OilFluid extends FlowingFluid {

	/**
	 * Ticks between spread steps. Water is 5 and Overworld lava is 30; oil is deliberately slower
	 * than lava, which is the whole read of the material — a crude that oozes rather than runs.
	 *
	 * <p><b>Invariant:</b> must stay ABOVE {@code OilLiquidBlock.IGNITE_DELAY_TICKS} (10), or oil
	 * re-floods a burnt cell before the next one catches and a chain burn dies after one block.
	 * Raising this value is safe for that invariant; lowering it below 10 is not.
	 */
	public static final int TICK_DELAY = 40;

	@Override
	public Fluid getFlowing() {
		return ModContent.FLOWING_OIL.get();
	}

	@Override
	public Fluid getSource() {
		return ModContent.OIL.get();
	}

	@Override
	public Item getBucket() {
		return ModContent.OIL_BUCKET.get();
	}

	// MOD-498 — deprecated by NeoForge only; vanilla does not mark it. NeoForge points at its own
	// canConvertToSource(FluidState, ServerLevel, BlockPos), but the no-position form stays
	// protected abstract in both, so it MUST be implemented either way. Oil never forms infinite
	// sources, so the answer is a constant and the position-aware overload could not differ.
	@SuppressWarnings("deprecation")
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
	public BlockState createLegacyBlock(FluidState fluidState) {
		return ModContent.OIL_BLOCK.get().defaultBlockState()
				.setValue(LiquidBlock.LEVEL, getLegacyLevel(fluidState));
	}

	@Override
	public boolean isSame(Fluid other) {
		return other == ModContent.OIL.get() || other == ModContent.FLOWING_OIL.get();
	}

	@Override
	public int getDropOff(LevelReader level) {
		return 2;
	}

	@Override
	public int getTickDelay(LevelReader level) {
		return TICK_DELAY;
	}

	/**
	 * Hands crude oil's immersion physics to the shared implementation (MOD-496).
	 *
	 * <p>{@code Fluid#entityInside} is the loader-neutral, side-neutral seat for the effect:
	 * {@code Entity.checkInsideBlocks} fires it on BOTH sides, so client and server damp motion
	 * identically and the local player never fights a server correction. The numbers, the held-jump
	 * ascent and the idempotence guard all live in {@link FluidImmersion} now, so diesel and fuel oil
	 * get the same treatment from the same code — before MOD-496 the mechanic was written here and
	 * reachable only from here, which is exactly why the other two fluids shipped without it.
	 *
	 * <p>The obvious alternative — NeoForge's {@code FluidType.motionScale} — works again since
	 * 26.2.0.67 (the {@code IEntityExtension} fluid integration was re-implemented upstream in
	 * 26.2.0.49-beta; it was dead code on 26.2.0.8-beta, when this was written), but it is
	 * NeoForge-only and Fabric has no equivalent at all. Using it would make the two loaders damp
	 * motion differently, so {@code motionScale} stays 0 and the effect stays here — see
	 * {@code ModFluidsNeoForge} (MOD-495).
	 */
	@Override
	protected void entityInside(Level level, BlockPos pos, Entity entity,
			InsideBlockEffectApplier effectApplier) {
		super.entityInside(level, pos, entity, effectApplier);
		FluidImmersion.applyEntityInside(level, pos, entity);
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

	/** The flowing variant ({@code alaindustrial:flowing_oil}) — carries the LEVEL property, never a source. */
	public static class Flowing extends OilFluid {
		@Override
		protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
			super.createFluidStateDefinition(builder);
			builder.add(LEVEL);
		}

		@Override
		public int getAmount(FluidState fluidState) {
			return fluidState.getValue(LEVEL);
		}

		@Override
		public boolean isSource(FluidState fluidState) {
			return false;
		}
	}

	/** The source variant ({@code alaindustrial:oil}) — always full, the bucket-visible fluid. */
	public static class Source extends OilFluid {
		@Override
		public int getAmount(FluidState fluidState) {
			return 8;
		}

		@Override
		public boolean isSource(FluidState fluidState) {
			return true;
		}
	}
}
