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

	/**
	 * Per-tick multiplier applied to horizontal motion inside oil — wading, not walking. See
	 * {@link #entityInside} for why this lives here and not in a loader's fluid-type properties.
	 */
	private static final double HORIZONTAL_DRAG = 0.45D;

	/** Per-tick multiplier applied to vertical motion inside oil: a slow sink, and a slow rise. */
	private static final double VERTICAL_DRAG = 0.72D;

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
	 * Slows every entity standing in oil (MOD-248). This is the loader-neutral seat for the effect:
	 * {@code Fluid#entityInside} is called from {@code Entity.checkInsideBlocks} on BOTH sides, so
	 * client and server damp the motion identically and the local player never fights a server
	 * correction. The obvious alternative — NeoForge's {@code FluidType.motionScale} — is dead code
	 * in 26.2.0.8-beta (the whole {@code IEntityExtension} fluid integration is commented out
	 * upstream, see {@code ModFluidsNeoForge}), and Fabric has no equivalent at all.
	 *
	 * <p>The guard on {@code blockPosition()} is what makes this idempotent: {@code checkInsideBlocks}
	 * fires once per intersected block, so an entity spanning three oil cells would otherwise be
	 * damped three times in one tick and the strength would depend on how deep it happens to be.
	 * Exactly one cell can be the entity's own block position, on either side.
	 *
	 * <p>Falling is damped rather than frozen: the drag gives a terminal speed of roughly
	 * {@code 0.08 / (1 − VERTICAL_DRAG)} blocks per tick, which is slow enough to read as "sinking
	 * through crude" and fast enough that descending a geyser shaft is a descent and not a wait.
	 * {@code resetFallDistance} runs every tick, so oil never deals fall damage however deep the
	 * drop — the shaft is meant to be climbed down.
	 */
	@Override
	protected void entityInside(Level level, BlockPos pos, Entity entity,
			InsideBlockEffectApplier effectApplier) {
		super.entityInside(level, pos, entity, effectApplier);
		if (!pos.equals(entity.blockPosition())) {
			return;
		}
		Vec3 motion = entity.getDeltaMovement();
		double ascent = ascentImpulse(entity);
		entity.setDeltaMovement(motion.x * HORIZONTAL_DRAG, motion.y * VERTICAL_DRAG + ascent,
				motion.z * HORIZONTAL_DRAG);
		entity.resetFallDistance();
	}

	/**
	 * The upward push an entity gets for holding jump inside oil (MOD-250), or zero.
	 *
	 * <p>Playtest found that an entity which reached the bottom of a pool could not get back up: the
	 * vanilla jump branch in {@code LivingEntity#aiStep} only knows {@code isInWater()} and
	 * {@code isInLava()}, so in oil a held jump does nothing beyond the one ground jump, which
	 * {@link #VERTICAL_DRAG} eats within a few ticks. This is the same seat as the drag above — no
	 * mixin needed, because {@code isJumping()} is public — and it runs identically on both sides, so
	 * the local player never fights a server correction.
	 *
	 * <p>The impulse is added <em>after</em> the drag rather than before, so a held jump is worth its
	 * full value on the tick it is made instead of being damped twice. {@code isAffectedByFluids} is
	 * the vanilla gate for "fluids move this entity at all" — false for a creative player in flight,
	 * who would otherwise be shoved upward while flying through a deposit.
	 */
	private static double ascentImpulse(Entity entity) {
		if (entity instanceof LivingEntity living && living.isJumping() && living.isAffectedByFluids()) {
			return OilPhysics.ASCENT_IMPULSE;
		}
		return 0.0D;
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
