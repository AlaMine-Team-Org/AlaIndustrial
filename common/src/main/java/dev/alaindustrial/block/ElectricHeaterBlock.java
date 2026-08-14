package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Menu-less-no-longer LV consumer that supplies demand-driven heat to the block directly above.
 *
 * <p>Deliberately NOT {@link LitMachineBlock}, which it used to extend: that base contributes the
 * boolean {@code lit}, and since MOD-418 this block shows a four-rung temperature instead — see
 * {@link HeaterGlow} for why the distinction is worth a property of its own.
 */
public final class ElectricHeaterBlock extends HorizontalMachineBlock {
	public static final MapCodec<ElectricHeaterBlock> CODEC = simpleCodec(ElectricHeaterBlock::new);

	/** How hot the coils look; see {@link HeaterGlow} for why this is not the usual boolean LIT. */
	public static final EnumProperty<HeaterGlow> GLOW = EnumProperty.create("glow", HeaterGlow.class);

	/** Heat escaping the seam sits just under the machine above, not on the (covered) top face. */
	private static final double SEAM_Y = 0.94;

	public ElectricHeaterBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(GLOW, HeaterGlow.COLD));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(GLOW);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ElectricHeaterBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/**
	 * Heat bleeding out of the joint between the heater and the machine it is feeding.
	 *
	 * <p>Emitted from the <b>sides</b> at the top edge rather than from the top face, because in the only
	 * arrangement this block is built for the top face is covered by the Vulcanizer — particles spawned
	 * there would render inside another block. Escaping at the seam is also what the effect is meant to
	 * say: the heat is going somewhere, into the thing above.
	 *
	 * <p>Only {@link HeaterGlow#GLOWING} emits. That rung means "fully warmed, the machine above is
	 * getting x3 right now", which is the one moment worth announcing; emitting through the whole ramp
	 * would make the effect ambient and teach the player to stop reading it — the same call the Charging
	 * Station makes for its sparks.
	 *
	 * <p>In {@code animateTick} rather than a renderer for the reason the incubator documents: a
	 * renderer's extract phase runs once per frame, which would tie particle density to the frame rate.
	 */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(GLOW) != HeaterGlow.GLOWING) {
			return;
		}
		double cx = pos.getX() + 0.5;
		double cz = pos.getZ() + 0.5;
		double y = pos.getY() + SEAM_Y;

		// One wisp per tick, walked around the four sides so the seam glows evenly rather than in a
		// corner: pick an edge, then a point along it.
		double along = random.nextDouble() - 0.5;
		boolean onXAxis = random.nextBoolean();
		double edge = random.nextBoolean() ? 0.52 : -0.52;
		double px = cx + (onXAxis ? along : edge);
		double pz = cz + (onXAxis ? edge : along);

		level.addParticle(ParticleTypes.SMOKE, px, y, pz, 0.0, 0.012, 0.0);
		if (random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.SMALL_FLAME, px, y, pz, 0.0, 0.008, 0.0);
		}
	}
}
