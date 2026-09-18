package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.RecyclerBlockEntity;
import dev.alaindustrial.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Recycler (MOD-145). Beyond the usual {@code lit} state it carries {@link #LAMPS}: how many of the
 * three waste fractions the current batch has collected.
 *
 * <p>That number is on the front panel as lit lamps, so a player walking past a farm can see what the
 * machine is being fed WITHOUT opening it — one lamp means someone is shovelling a single kind of junk
 * in and the batch will pay ballast, three means the mix is right. The rule the machine is built around
 * is otherwise invisible from the outside.
 */
public class RecyclerBlock extends LitMachineBlock implements MachineHumProvider {
	public static final MapCodec<RecyclerBlock> CODEC = simpleCodec(RecyclerBlock::new);

	/** Fractions present in the current batch, 0..3 — the lamp count on the front face. */
	public static final IntegerProperty LAMPS = IntegerProperty.create("lamps", 0, 3);

	public RecyclerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LAMPS, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LAMPS);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RecyclerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// humMachineTicker, not machineTicker: the plain ticker returns null client-side, which
		// is the entire difference between a voiced and a silent machine (MOD-447).
		return humMachineTicker(level);
	}

	// --- Loop hum (pattern A: lit-based, MOD-447) ---

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.RECYCLER_HUM;
	}

	@Override
	public float humVolume() {
		// Quieter than the 0.35 default (owner's call, 2026-09-18): the drum full of tumbling
		// scrap reads as busy work, not as something that should carry as far as a single
		// stationary machine like the macerator. The .ogg itself is still normalised to the
		// mod's usual macerator_grind loudness anchor — this is the separate, code-level knob,
		// the same layered-attenuation idiom charge_pad/electric_heater use at 0.18.
		return 0.25f;
	}
}
