package dev.alaindustrial.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.EmptyFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Steam (MOD-468) — the working fluid of the nuclear reactor room: the reactor boils water into it,
 * the steam nozzle vents it. It lives <b>only inside tanks and pipes</b>.
 *
 * <p><b>Why this is a plain {@link Fluid} and not a {@code FlowingFluid}</b> like the mod's other four
 * (oil, diesel, fuel oil, distillate). Steam has no terrain form at all: no bucket, no
 * {@code LiquidBlock}, nothing to place. Extending {@code FlowingFluid} would mean inventing a
 * source/flowing pair and a liquid block for a fluid that must never reach the world, and every one of
 * those would be a way for it to leak out. Staying on the bare {@link Fluid} contract makes the three
 * prohibitions fall out of the existing code instead of needing guards of their own:
 * <ul>
 *   <li><b>no bucket</b> — {@link #getBucket()} is {@link Items#AIR}, so
 *       {@code BucketFluids.filledBucket} hands back an empty stack (it already documents this case);</li>
 *   <li><b>cannot be poured from a capsule</b> — {@code FilledCapsuleItem} places only a
 *       {@code FlowingFluid}, and steam is not one;</li>
 *   <li><b>does not exist in the world</b> — no liquid block is registered, and
 *       {@link #createLegacyBlock(FluidState)} is air.</li>
 * </ul>
 *
 * <p>Shaped after vanilla {@link EmptyFluid}, which is the only other fluid in the game with no block
 * behind it — with two deliberate departures: this fluid is a full source ({@link #isSource} true,
 * {@link #getAmount} 8, and no {@code isEmpty()} override), because a tank holding steam holds a real
 * liquid, and {@code FluidState.isEmpty()} deciding otherwise would make every tank read as empty.
 *
 * <p>The world-physics answers below are therefore dead code in practice — nothing can put a steam
 * fluid state into a level — and are pinned at the inert values rather than left to guesswork.
 * Registered per loader like the other fluids: eager in {@code ModFluids} on Fabric, through
 * {@code DeferredRegister} plus a {@code getFluidType()}-carrying subclass in
 * {@code ModFluidsNeoForge}.
 */
public class SteamFluid extends Fluid {

	/** No bucket exists: steam is a pipe-and-tank fluid. {@code Items.AIR} is vanilla's "no bucket". */
	@Override
	public Item getBucket() {
		return Items.AIR;
	}

	/** Never in the world, so nothing ever asks — answered as vanilla answers for a blockless fluid. */
	@Override
	public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other,
			Direction direction) {
		return true;
	}

	@Override
	public Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState fluidState) {
		return Vec3.ZERO;
	}

	@Override
	public int getTickDelay(LevelReader level) {
		return 0;
	}

	@Override
	protected float getExplosionResistance() {
		return 0.0F;
	}

	@Override
	public float getHeight(FluidState fluidState, BlockGetter level, BlockPos pos) {
		return 0.0F;
	}

	@Override
	public float getOwnHeight(FluidState fluidState) {
		return 0.0F;
	}

	/** Air: there is no steam block, which is exactly what stops steam from ever being placed. */
	@Override
	protected BlockState createLegacyBlock(FluidState fluidState) {
		return Blocks.AIR.defaultBlockState();
	}

	/**
	 * True, unlike {@link EmptyFluid}: the single state this fluid has IS the fluid, and tanks, pipes
	 * and capsules all read the default state as "one real liquid, full".
	 */
	@Override
	public boolean isSource(FluidState fluidState) {
		return true;
	}

	/** A full source's level, matching {@link #isSource}. */
	@Override
	public int getAmount(FluidState fluidState) {
		return 8;
	}

	@Override
	public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
		return Shapes.empty();
	}
}
