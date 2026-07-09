package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.core.SolarSky;
import dev.alaindustrial.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Block for the Daylight Solar Panel — T2 day branch, the stronger day-mirror of {@link SolarPanelBlock}. */
public class DaylightSolarPanelBlock extends AbstractMachineBlock implements MachineHumProvider {
	public static final MapCodec<DaylightSolarPanelBlock> CODEC = simpleCodec(DaylightSolarPanelBlock::new);

	/** Half-block (8px) collision/outline matching the slab model — keeps occlusion off and shape in sync. */
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

	public DaylightSolarPanelBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DaylightSolarPanelBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return humMachineTicker(level);
	}

	// --- Ambient hum (pattern C: lit-less ambient loop, silent when the panel stops producing) ---

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.SOLAR_PANEL_HUM;
	}

	@Override
	public float humVolume() {
		// Quieter than the generator (0.4): solar farms stack many panels, so each is tuned lower.
		return 0.28f;
	}

	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return SolarSky.isDaylitActive(level, pos);
	}
}
