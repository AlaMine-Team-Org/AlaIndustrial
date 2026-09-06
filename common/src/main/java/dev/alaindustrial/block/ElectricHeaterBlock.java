package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
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
 *
 * <p>Audible since MOD-573, and that same four-rung ladder is what the loop runs on: pattern C, with
 * {@link #isWorking} reading {@link #GLOW} rather than a boolean. The coil is heard from the moment it
 * has any colour, not only at the top rung the particles wait for — a warming heater is doing work and
 * costing EU, and silence there would read as "off". MOD-258 had left this block silent on the grounds
 * that the Vulcanizer above it already hisses; the owner revisited that on 2026-09-06. The hiss is
 * exactly what this sound must not be, which is why the coil crackles instead.
 */
public final class ElectricHeaterBlock extends HorizontalMachineBlock implements MachineHumProvider {
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
		// Hum ticker: without this the MachineHumProvider above is dead code and the block stays
		// silent with every gate green (pattern C — the working state comes from GLOW below). MOD-573.
		return humMachineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.ELECTRIC_HEATER_HUM;
	}

	/**
	 * Quieter than a machine of its own (0.18, the level the extractor and the charging station use).
	 *
	 * <p>This block is never listened to alone: it exists to feed the Vulcanizer standing on it, so the
	 * player always hears the pair. At the volume a standalone machine gets, the helper would be the
	 * louder half of a stack whose work is happening in the other block.
	 */
	@Override
	public float humVolume() {
		return 0.18f;
	}

	/**
	 * Audible means the coils have any colour at all — every rung except {@link HeaterGlow#COLD}.
	 *
	 * <p>Read from the blockstate, so it is side-agnostic and free, as the client-tick contract of
	 * {@link MachineHumProvider#isWorking} requires. Deliberately a wider window than
	 * {@link #animateTick}, which waits for {@link HeaterGlow#GLOWING}: the particles announce "the
	 * machine above is getting its x3 right now", while the sound answers a different question — are
	 * these coils hot.
	 *
	 * <p><b>Hot is not the same as spending EU, and this window says hot.</b> Three states sit inside it
	 * where the block draws nothing: the "freeze, don't waste" hold at full temperature; the whole
	 * cool-down, which runs one degree per two ticks and so keeps a fully warmed heater audible for
	 * about twenty seconds after the last work; and {@code NO_ENERGY}, where
	 * {@link dev.alaindustrial.block.entity.ElectricHeaterBlockEntity} freezes {@code heat} while work
	 * is pending, so an unpowered heater stays warm — and audible — indefinitely.
	 *
	 * <p>That is deliberate rather than overlooked: this is the same window the block's LIGHT has used
	 * since MOD-418 ({@code ModBlockProperties::heaterLight} reads the very same ladder), and a block
	 * that glows while it is silent would be the stranger pair. A sound is more intrusive than a glow,
	 * though, so if the twenty-second tail or the unpowered drone reads wrong in play, the fix belongs
	 * in the ladder itself — for the light and the sound together — not in a second definition here.
	 */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return state.getValue(GLOW) != HeaterGlow.COLD;
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
