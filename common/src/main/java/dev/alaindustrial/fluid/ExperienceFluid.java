package dev.alaindustrial.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Liquid experience (MOD-818): what the Electric Mob Trap turns the orbs of a mob it killed into. Like
 * {@link SteamFluid} it lives <b>only inside tanks and pipes</b> — a plain {@link Fluid} with no bucket and
 * no liquid block, so it cannot be poured into the world; the three prohibitions fall out of the contract
 * rather than needing guards (see {@code SteamFluid} for the reasoning, which applies unchanged).
 *
 * <p>It is a full source ({@link #isSource} true), because a tank holding it holds a real liquid, and it
 * carries the {@code c:experience} fluid tag so other mods' experience handling can recognise it.
 *
 * <p>{@link #MB_PER_POINT} is the one place the exchange rate is written: the convention among tech mods is
 * 20 mB per point of experience, and a datapack that retags another mod's experience fluid onto this rate
 * should not have to learn ours.
 */
public class ExperienceFluid extends Fluid {

	/** Millibuckets of this fluid per point of experience — the rate other mods use. */
	public static final long MB_PER_POINT = 20L;

	/** No bucket exists: it is a pipe-and-tank fluid. {@code Items.AIR} is vanilla's "no bucket". */
	@Override
	public Item getBucket() {
		return Items.AIR;
	}

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

	/** Air: there is no liquid-experience block, which is exactly what stops it from ever being placed. */
	@Override
	protected BlockState createLegacyBlock(FluidState fluidState) {
		return Blocks.AIR.defaultBlockState();
	}

	/** True: the single state this fluid has IS the fluid, and tanks and pipes read it as one full liquid. */
	@Override
	public boolean isSource(FluidState fluidState) {
		return true;
	}

	@Override
	public int getAmount(FluidState fluidState) {
		return 8;
	}

	@Override
	public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
		return Shapes.empty();
	}
}
