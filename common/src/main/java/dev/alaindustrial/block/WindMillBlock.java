package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wind mill block — a full-cube LV generator that faces the player on placement. Energy is emitted
 * <b>only from the back face</b> (opposite of {@code facing}); the front and the four sides are
 * energy-inert — see {@link dev.alaindustrial.block.entity.WindMillBlockEntity#energyRoleForFace}.
 * No {@code lit} state (production is passive, not fuel-driven), so it extends
 * {@link HorizontalMachineBlock} rather than {@link LitMachineBlock}. The default full-cube shape is
 * inherited (no {@code getShape} override).
 */
public class WindMillBlock extends HorizontalMachineBlock implements MachineHumProvider {
	public static final MapCodec<WindMillBlock> CODEC = simpleCodec(WindMillBlock::new);

	public WindMillBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WindMillBlockEntity(pos, state);
	}

	/**
	 * Restricts the cable arm to the back face alone, matching the single back-face output of
	 * {@link WindMillBlockEntity#energyRoleForFace}: a cable may draw power only from the face
	 * opposite {@code FACING} (the energy port). The front (rotor) face, the four sides and the
	 * top/bottom are energy-inert, so a cable there would draw a misleading "energy goes here" arm
	 * without any EU ever flowing — the same shape as the iron-chest case (MOD-038), now applied per
	 * face. Decided from {@code FACING} alone (a blockstate property) so it is correct the instant
	 * the block is placed, with no block-entity load race.
	 */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return side == state.getValue(HorizontalMachineBlock.FACING).getOpposite();
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// Hum ticker: the client loop starts/stops with isWorking() below (pattern C — no lit state). MOD-143.
		return humMachineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.WIND_MILL_HUM;
	}

	@Override
	public float humVolume() {
		// Quieter than a lone machine: wind farms stack many rotors, like the solar farm case.
		return 0.22f;
	}

	/**
	 * Pattern C working predicate (MOD-143): the wind mill has no {@code lit} state, so derive "working"
	 * from the same client-synced production channel the rotor renderer spins on — slot 2
	 * ({@code progress} = EU/t produced). It is {@code > 0} only when the rotor is installed and actually
	 * turning (weather/altitude ok, unobstructed, no interference — see {@code WindMillBlockEntity}), so
	 * the loop is silent exactly when the blades stand still. Synced via the block-entity update packet
	 * (no menu needed), so this read is valid client-side.
	 */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return level.getBlockEntity(pos) instanceof WindMillBlockEntity mill
				&& mill.getDataAccess().get(2) > 0;
	}
}
