package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.ElectricMobTrapBlockEntity;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.world.ZonePreviewBlock;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Electric Mob Trap, tier I (MOD-818): an LV block that pulls hostile mobs towards it, shocks the ones
 * that arrive and keeps what they drop — the opposite of the Mob Repeller, which sends them away.
 *
 * <p><b>No facing.</b> A ring of insulators round a spark gap, the same from every side. {@link #LIT}
 * shows that it has just shocked something.
 *
 * <p><b>Connections.</b> The four sides take cables; the lid and the floor are the two output lines — items
 * on one, liquid experience on the other (a pipe joins whichever face its network meets). The collector
 * gives both its lid and its floor to energy and items; this block has a third output, so energy moves to
 * the sides.
 *
 * <p><b>Light and neighbours.</b> The body is 12 pixels tall and open work, so it is not a full cube and
 * does not occlude ({@code noOcclusion()} on its properties, R-PHY-05): a neighbour keeps drawing the face
 * that looks into the ring, and light passes through.
 */
public class ElectricMobTrapBlock extends AbstractMachineBlock implements HasMachineTooltip, ZonePreviewBlock {

	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** The model is 12 pixels tall; selection and collision follow it. Built once, never inside getShape. */
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

	public ElectricMobTrapBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return SHAPE;
	}

	/** A cable draws an arm only to the four sides: the lid and the floor carry the output pipes. */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return side.getAxis().isHorizontal();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ElectricMobTrapBlockEntity(pos, state);
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
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.electric_mob_trap.what",
								MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.plain("range", ServerBalance::electricMobTrapRadius),
						MachineTooltipSpec.stat("electric_mob_trap.strike", ServerBalance::electricMobTrapDamage,
								ServerBalance::electricMobTrapEuPerStrike)),
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.electric_mob_trap.pipes",
								MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.stat("buffer", ServerBalance::electricMobTrapBuffer)));
	}
}
