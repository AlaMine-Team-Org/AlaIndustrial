package dev.alaindustrial.block;

import dev.alaindustrial.core.world.ZonePreviewBlock;
import dev.alaindustrial.block.entity.VacuumCollectorBlockEntity;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Vacuum Collector, tier I (MOD-802): an LV block that draws loose drops within reach into its nine-slot
 * buffer, for item pipes to carry away.
 *
 * <p><b>No facing.</b> Four equal funnels, one in each side, so the block looks and works the same whichever
 * way it was placed. The sides are only intakes: cables and item pipes connect to the lid and the floor.
 * {@link #LIT} shows that it is pulling.
 *
 * <p><b>Light and neighbours, as the block breaker learned in game (MOD-787).</b> The funnels are recessed
 * five pixels into the sides, so the block is not a full occluder: a full-cube occlusion shape would make a
 * neighbour stop drawing the face that looks into a funnel, and the player would see through the world.
 * {@code noOcclusion()} is not the answer (R-PHY-05, MOD-776). The cell lets light through like glass and
 * casts no corner shade on its neighbours, because a cell that blocked light went black inside its recess and
 * darkened the block next to it. The collision box stays a full cube.
 */
public class VacuumCollectorBlock extends AbstractMachineBlock
		implements HasMachineTooltip, ZonePreviewBlock {

	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public VacuumCollectorBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	/** What the collector hides of its neighbours' faces: nothing — its sides are open funnels. */
	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	/** Light passes through, so the recessed funnels are lit by the cell they sit in. */
	@Override
	protected int getLightDampening(BlockState state) {
		return 0;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	/** No ambient-occlusion shade on the neighbours, which a full collision box would otherwise cast. */
	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}

	/** A cable draws an arm only to the lid and the floor: the sides are the funnels. */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return side.getAxis().isVertical();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VacuumCollectorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.vacuum_collector.what",
								MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.stat("energy_input", ServerBalance::vacuumCollectorEuPerTick),
						MachineTooltipSpec.plain("range", ServerBalance::vacuumCollectorRadius)),
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.vacuum_collector.pipes",
								MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.stat("vacuum_collector.pulse", ServerBalance::vacuumCollectorItemsPerPulse,
								ServerBalance::vacuumCollectorPulseTicks),
						MachineTooltipSpec.stat("buffer", ServerBalance::vacuumCollectorBuffer)));
	}
}
