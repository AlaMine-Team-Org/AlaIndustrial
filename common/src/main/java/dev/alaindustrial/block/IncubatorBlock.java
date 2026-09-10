package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.item.fluid.BucketFluids;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModParticles;
import dev.alaindustrial.registry.ModSounds;
import dev.alaindustrial.registry.ModTags;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.entity.LivingEntity;

/**
 * The incubator base (MOD-118) — the machine half of the 1x2 multiblock.
 *
 * <p>Placing any glass on top assembles the structure: the glass is swapped for the dome block and
 * the original state is remembered in this block entity, so breaking the multiblock hands the player
 * back exactly the glass they used (and a coloured glass tints the dome for free).
 */
public class IncubatorBlock extends LitMachineBlock implements MachineHumProvider {

	public static final MapCodec<IncubatorBlock> CODEC = simpleCodec(IncubatorBlock::new);

	public IncubatorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	/**
	 * A water bucket in hand tops up the nutrient bath (MOD-605); anything else falls through to the
	 * menu.
	 *
	 * <p>The fallback is {@code TRY_WITH_EMPTY_HAND}, not {@code PASS}: in 26.2 a PASS here does NOT
	 * fall through to {@code useWithoutItem}, so the incubator's screen would become unreachable
	 * whenever the player held anything at all — which is most of the time. The sprinkler and the fuel
	 * rod assembly carry the same note for the same reason. This also leaves dome assembly alone: the
	 * player still sneak-places the glass on top, exactly as before.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof IncubatorBlockEntity incubator)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		Fluid incoming = BucketFluids.content(stack);
		if (incoming != Fluids.WATER) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		boolean[] moved = {false};
		EnergyTransactions.get().runCommitting(txn ->
				moved[0] = incubator.fluidTank.insert(FluidHolder.of(incoming), FluidAmounts.BUCKET, txn)
						== FluidAmounts.BUCKET);
		if (moved[0]) {
			player.setItemInHand(hand,
					ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new IncubatorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// Hum ticker, not the plain machine ticker: the client loop is driven off the vanilla lit
		// blockstate (pattern A). Swapping this back to machineTicker leaves the machine silent with a
		// perfectly good MachineHumProvider implementation below — the failure is quiet, so it is called
		// out here.
		return humMachineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.INCUBATOR_HUM;
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
