package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.BlockBreakerBlockEntity;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Block Breaker (MOD-787): an LV machine that breaks the block in front of it with the tool in its
 * slot.
 *
 * <p><b>Six directions, like a dispenser.</b> It is the first machine with a menu that faces up and
 * down, so it does not extend {@link HorizontalMachineBlock}: its {@link #FACING} is vanilla's six-way
 * property, set once on placement towards where the player looks — to turn it, break it and place it
 * again. The energy core's facing-aware helpers key on {@code HorizontalMachineBlock.FACING} and would
 * not see this one, so the block entity declares its own inert front and this block keeps the cable
 * arm off it.
 *
 * <p>The look — a plank crate with a stone-bladed auger at the front — was chosen by the owner from five
 * candidates shown in game (MOD-787).
 */
public class BlockBreakerBlock extends AbstractMachineBlock implements HasMachineTooltip {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public BlockBreakerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	/** The working face points away from the player, towards what they were looking at. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
	}

	/**
	 * A block appearing or vanishing in front, or a lever thrown beside it, wakes a sleeping machine at
	 * once instead of after its idle sleep.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BlockBreakerBlockEntity breaker) {
			breaker.wake();
		}
	}

	/**
	 * What the breaker hides of its neighbours' faces: nothing. Its working part is recessed into the
	 * front, so a full-cube occlusion shape would make the block in front stop drawing its face and the
	 * player would see through it. {@code noOcclusion()} is not the answer (R-PHY-05, MOD-776): the block
	 * still occludes, it only stops culling the faces next to it.
	 */
	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	/**
	 * Light passes through, as through glass. The working face is recessed, so the open front of the cell
	 * is part of what the player sees: a cell that blocked light (the first in-game look) went black there
	 * and took the face of the block in front with it.
	 */
	@Override
	protected int getLightDampening(BlockState state) {
		return 0;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	/**
	 * No ambient-occlusion shade on the neighbours. A full collision box makes the vanilla default 0.2, the
	 * darkening a solid cube casts into the corners next to it; on a block that lets light through, that
	 * read in game as a shadow around the machine.
	 */
	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}

	/** No cable arm reaches the working face: nothing there takes energy. */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return side != state.getValue(FACING);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BlockBreakerBlockEntity(pos, state);
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
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.block_breaker.what",
								MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.stat("energy_input", ServerBalance::blockBreakerEuPerTick)),
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.block_breaker.tools",
								MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.stat("buffer", ServerBalance::blockBreakerBuffer)));
	}
}
