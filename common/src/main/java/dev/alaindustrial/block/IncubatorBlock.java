package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModParticles;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.entity.LivingEntity;

/**
 * The incubator base (MOD-118) — the machine half of the 1x2 multiblock.
 *
 * <p>Placing any glass on top assembles the structure: the glass is swapped for the dome block and
 * the original state is remembered in this block entity, so breaking the multiblock hands the player
 * back exactly the glass they used (and a coloured glass tints the dome for free).
 */
public class IncubatorBlock extends LitMachineBlock {

	public static final MapCodec<IncubatorBlock> CODEC = simpleCodec(IncubatorBlock::new);

	public IncubatorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new IncubatorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide()) {
			tryAssemble(level, pos);
		}
	}

	/**
	 * Irradiation shimmer while an operation runs. Particles belong here rather than in the renderer:
	 * the renderer's extract phase runs once per frame, so spawning from there would tie the particle
	 * density to the frame rate. {@code LIT} is already synced by the machine base, so no block entity
	 * lookup is needed — and the dome above has no block entity of its own to hang this on.
	 */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		// Green sparks around the item floating in the chamber above.
		level.addParticle(ParticleTypes.HAPPY_VILLAGER,
				x + (random.nextDouble() - 0.5) * 0.5,
				pos.getY() + 1.25 + random.nextDouble() * 0.5,
				z + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.0, 0.0);
		// A slower plume rising off the emitter ring on the base's top face.
		if (random.nextInt(3) == 0) {
			level.addParticle(ModParticles.ENRICHED_URANIUM_FLAME,
					x + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 1.04,
					z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.01, 0.0);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide()) {
			tryAssemble(level, pos);
		}
	}

	/**
	 * Swaps glass above for the dome (or notices the dome went missing) and records the result on the
	 * block entity, which gates the machine on it.
	 */
	private static void tryAssemble(LevelAccessor level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof IncubatorBlockEntity incubator)) {
			return;
		}
		BlockPos above = pos.above();
		BlockState top = level.getBlockState(above);
		if (top.is(ModContent.INCUBATOR_DOME.get())) {
			incubator.setFormed(true);
			return;
		}
		if (top.is(ModTags.Blocks.INCUBATOR_DOME_GLASS)) {
			incubator.rememberDomeSource(top);
			level.setBlock(above, ModContent.INCUBATOR_DOME.get().defaultBlockState(), Block.UPDATE_ALL);
			incubator.setFormed(true);
			return;
		}
		incubator.setFormed(false);
	}

	/**
	 * Turns the dome back into the glass it was made of. Safe to call when there is no dome.
	 *
	 * <p>The glass is passed in rather than read from the block entity: the only caller is
	 * {@code IncubatorBlockEntity#preRemoveSideEffects}, which runs while the base is already being
	 * removed. The dome's own removal hook recognises this hand-back — the block now standing there is
	 * that very glass — and drops nothing, so the glass is neither duplicated nor lost.
	 */
	public static void releaseDome(LevelAccessor level, BlockPos basePos, BlockState glass) {
		BlockPos above = basePos.above();
		if (!level.getBlockState(above).is(ModContent.INCUBATOR_DOME.get())) {
			return;
		}
		level.setBlock(above, glass, Block.UPDATE_ALL);
	}
}
