package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.ChargePadBlockEntity;
import dev.alaindustrial.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Charging Station (MOD-274) — a low plate that tops up everything powered on whoever stands on it.
 *
 * <p>Rotation-symmetric like the drone dock: no {@code FACING}, so every face takes a cable and the
 * player never has to think about which way it points. The four-pixel plate follows the same precedent
 * — a real, lower-than-full collision box, so the outline matches what is walked on — and is tall
 * enough to read as a piece of equipment rather than a pressure plate, while still being a step the
 * player walks over rather than onto.
 *
 * <p>Detection is {@link #entityInside}, vanilla's own "something is in this block's space" hook, which
 * fires every tick for every entity overlapping the cell. The alternative — an AABB sweep from the
 * block entity's tick — would ask the world a question every tick that vanilla is already answering for
 * free, and would have to run even while the station sleeps. The cell is scanned in full regardless of
 * the plate's visual height: {@code getEntityInsideCollisionShape} is deliberately left at its default
 * full cube, so standing on a 4px plate counts as being inside it.
 */
public class ChargePadBlock extends AbstractMachineBlock implements MachineHumProvider {

	public static final MapCodec<ChargePadBlock> CODEC = simpleCodec(ChargePadBlock::new);

	/** What the indicator shows; see {@link ChargePadState} for why this is not the usual boolean LIT. */
	public static final EnumProperty<ChargePadState> STATE =
			EnumProperty.create("state", ChargePadState.class);

	/** Four-pixel plate — the player stands on top of it. */
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

	public ChargePadBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(STATE, ChargePadState.IDLE));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STATE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ChargePadBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// Hum ticker: drives the client loop off ChargePadState.CHARGING (pattern C). MOD-447.
		return humMachineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.CHARGE_PAD_HUM;
	}

	/**
	 * The station works while energy is actually moving into someone's gear — pattern C: the pad has no
	 * {@code lit} property, so the default lit-based predicate cannot apply. Side-agnostic (blockstate
	 * in the chunk palette), cheap, and the same signal the spark column keys off — sound and particles
	 * agree on what "charging" means. READY (already full) and EMPTY (starved) both stay silent: the
	 * quietest block in the mod should not hum at a player who has nothing to gain from it.
	 */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return state.getValue(STATE) == ChargePadState.CHARGING;
	}

	/**
	 * Deliberately quieter than every other stationary machine (0.18, the extractor's level): the player
	 * stands ON the pad while it charges, so the source sits at their feet rather than across the room.
	 */
	@Override
	public float humVolume() {
		return 0.18f;
	}

	/**
	 * Every tick someone is in this block's space. The side and spectator guards live in the block
	 * entity's {@code chargePlayer}, next to the transfer they protect — this hook is called on both
	 * sides for every entity type, so filtering here as well would only duplicate them.
	 */
	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
			InsideBlockEffectApplier effectApplier, boolean isInside) {
		if (level.getBlockEntity(pos) instanceof ChargePadBlockEntity station) {
			station.chargePlayer(level, entity);
		}
	}

	/**
	 * How tall the charging column is, in blocks — a player's height, so the effect wraps the person
	 * standing on the plate rather than pooling at their feet (MOD-334).
	 */
	private static final double COLUMN_HEIGHT = 2.0;

	/** Sparks emitted per client tick while charging. */
	private static final int RISING_SPARKS = 3;

	/**
	 * The charging effect: a column of sparks in the player's silhouette, plus a ring crawling around
	 * the plate's rim.
	 *
	 * <p>In {@code animateTick} rather than a renderer for the reason the incubator documents: a
	 * renderer's extract phase runs once per frame, which would tie particle density to the frame rate.
	 *
	 * <p>Only {@link ChargePadState#CHARGING} emits. A station that is merely occupied and full stays
	 * visually quiet — the green indicator already says "done", and sparks that never stop would train
	 * the player to ignore them.
	 */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(STATE) != ChargePadState.CHARGING) {
			return;
		}
		double cx = pos.getX() + 0.5;
		double cz = pos.getZ() + 0.5;

		// Sparks rising through the whole column. Spawned at a random height rather than always at the
		// bottom so the column reads as filled the instant it starts, instead of taking a second to
		// build up from the plate.
		for (int i = 0; i < RISING_SPARKS; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double radius = 0.18 + random.nextDouble() * 0.22;
			level.addParticle(ParticleTypes.ELECTRIC_SPARK,
					cx + Math.cos(angle) * radius,
					pos.getY() + 0.26 + random.nextDouble() * COLUMN_HEIGHT,
					cz + Math.sin(angle) * radius,
					0.0, 0.04 + random.nextDouble() * 0.05, 0.0);
		}

		// A slower mote near the top, where the column meets the player's head — gives the effect an
		// end rather than letting it fade out mid-air.
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.END_ROD,
					cx + (random.nextDouble() - 0.5) * 0.5,
					pos.getY() + COLUMN_HEIGHT * (0.7 + random.nextDouble() * 0.3),
					cz + (random.nextDouble() - 0.5) * 0.5,
					0.0, 0.01, 0.0);
		}

		// Rim crawl: one spark travelling around the plate's edge, tying the column to the block.
		double rim = (level.getGameTime() % 40L) / 40.0 * Math.PI * 2.0;
		level.addParticle(ParticleTypes.ELECTRIC_SPARK,
				cx + Math.cos(rim) * 0.42, pos.getY() + 0.28, cz + Math.sin(rim) * 0.42,
				0.0, 0.005, 0.0);
	}
}
