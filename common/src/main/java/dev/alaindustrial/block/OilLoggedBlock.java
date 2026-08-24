package dev.alaindustrial.block;

import dev.alaindustrial.registry.ModContent;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * "Waterlogging, but for oil" (MOD-250) — a block that a pool of oil flows <em>through</em> instead of
 * flowing around.
 *
 * <p><b>The bug this fixes.</b> The enriched uranium torch is a {@link SimpleWaterloggedBlock}, whose
 * default {@code canPlaceLiquid} is hardcoded to {@code type == Fluids.WATER}. Oil therefore could not
 * enter the torch's cell at all: it stopped at the boundary, the cell stayed air, and the surrounding
 * oil dutifully rendered its side faces into that pocket — a black picture frame standing in the pool
 * with the torch invisible behind it. (Torches surviving in oil is correct and unchanged; only the
 * hole around them was wrong.)
 *
 * <p><b>Why this works.</b> {@code LiquidBlockContainer} itself is fluid-agnostic — {@code
 * FlowingFluid.spreadTo} and {@code canHoldSpecificFluid} ask the block, and only the default methods
 * of {@code SimpleWaterloggedBlock} name water. Extending that interface and widening those defaults
 * keeps every vanilla water behaviour intact while adding oil beside it.
 *
 * <p><b>Two properties, not one enum.</b> The vanilla {@code WATERLOGGED} stays exactly as it is,
 * because foreign code reads it by name; {@link #OILLOGGED} is added next to it. The pair has one
 * meaningless combination (both true) and it is simply never reachable: each {@code placeLiquid} arm
 * refuses while the other property is set, and a cell can only hold one fluid to begin with.
 */
public interface OilLoggedBlock extends SimpleWaterloggedBlock {

	/** Set when this block's cell is filled with oil; the mirror of vanilla {@code WATERLOGGED}. */
	BooleanProperty OILLOGGED = BooleanProperty.create("oillogged");

	@Override
	default boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos,
			BlockState state, Fluid type) {
		if (isOilSource(type)) {
			return !state.getValue(OILLOGGED) && !state.getValue(BlockStateProperties.WATERLOGGED);
		}
		return type == Fluids.WATER && !state.getValue(OILLOGGED);
	}

	@Override
	default boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
		if (isOilSource(fluidState.getType()) && !state.getValue(OILLOGGED)
				&& !state.getValue(BlockStateProperties.WATERLOGGED)) {
			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(OILLOGGED, true), 3);
				level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
			}
			return true;
		}
		return !state.getValue(OILLOGGED) && SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluidState);
	}

	@Override
	default ItemStack pickupBlock(@Nullable LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state) {
		if (state.getValue(OILLOGGED)) {
			level.setBlock(pos, state.setValue(OILLOGGED, false), 3);
			if (!state.canSurvive(level, pos)) {
				level.destroyBlock(pos, true);
			}
			return new ItemStack(ModContent.OIL_BUCKET.get());
		}
		return SimpleWaterloggedBlock.super.pickupBlock(user, level, pos, state);
	}

	// MOD-498 — BucketPickup#getPickupSound() is deprecated by NeoForge only; vanilla does not deprecate
	// it. The replacement it names is IBucketPickupExtension#getPickupSound(BlockState), a NeoForge-only
	// interface that Fabric does not have, and this block is shared common/ code. The vanilla method is
	// also what the NeoForge variant delegates to by default, so overriding it stays correct on both.
	@SuppressWarnings("deprecation")
	@Override
	default Optional<SoundEvent> getPickupSound() {
		// Both fluids use the vanilla bucket-fill sound (see OilFluid#getPickupSound), so one answer
		// covers the interface's stateless getter, which is not told which fluid was taken.
		return Fluids.WATER.getPickupSound();
	}

	/** The fluid state this block shows, honouring whichever of the two properties is set. */
	static FluidState fluidState(BlockState state, FluidState fallback) {
		if (state.getValue(BlockStateProperties.WATERLOGGED)) {
			return Fluids.WATER.getSource(false);
		}
		if (state.getValue(OILLOGGED)) {
			return ModContent.OIL.get().defaultFluidState();
		}
		return fallback;
	}

	/**
	 * Keeps a logged cell's fluid ticking after a neighbour changes — the oil half of what vanilla does
	 * for water in every waterloggable block's {@code updateShape}.
	 */
	static void scheduleFluidTick(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos) {
		if (state.getValue(BlockStateProperties.WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		} else if (state.getValue(OILLOGGED)) {
			Fluid oil = ModContent.OIL.get();
			ticks.scheduleTick(pos, oil, oil.getTickDelay(level));
		}
	}

	/** Whether a state placed at this position should start out logged, and with which fluid. */
	static BlockState loggedForPlacement(BlockState state, FluidState fluid) {
		if (fluid.is(Fluids.WATER)) {
			return state.setValue(BlockStateProperties.WATERLOGGED, true);
		}
		if (isOilSource(fluid.getType())) {
			return state.setValue(OILLOGGED, true);
		}
		return state;
	}

	/**
	 * Only a full oil <em>source</em> logs a block. Flowing oil is deliberately excluded: a logged cell
	 * always renders as a full cell, so accepting a trickle would show a full block of oil where the
	 * pool has a thin film — and a bucket would then pick a whole source out of it.
	 */
	static boolean isOilSource(Fluid fluid) {
		return fluid == ModContent.OIL.get();
	}
}
