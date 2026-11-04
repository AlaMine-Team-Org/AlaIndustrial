package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.ReactorAntennaBlockEntity;
import dev.alaindustrial.core.structure.ReactorCasingPart;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * The reactor antenna (MOD-941) — the part of the shell a reactor tablet listens to.
 *
 * <p>It is a shell block first, exactly like {@link ReactorOutletBlock}: the room scan counts it as casing, so
 * fitting one into a wall is not a breach, and it wears the same seamless art when the room forms. A bare
 * reactor has no wall to fit it into, so there it counts when it touches the controller with a face.
 *
 * <p><b>It faces out.</b> The chosen look (the owner's pick of ten, "Yagi") carries a boom with three elements
 * that stands 14 px out of the wall, so the block has vanilla's six-way {@link #FACING}: set at placement towards
 * the player, who places it from outside the room. The boom is a block of its own in the cell in front
 * ({@link ReactorAntennaMastBlock}), so it has light, collision and an outline, and the cell cannot be built over;
 * the antenna itself stays the full cube of a wall cell.
 *
 * <p>No ticker of its own: the controller that owns it serves it every tick (link and upkeep), the same "the
 * controller pushes" rule the outlet follows.
 */
public class ReactorAntennaBlock extends ReactorShellBlock implements EntityBlock, ReactorCasingPart,
		HasMachineTooltip {

	/** The way the boom points — out of the wall, towards whoever placed it. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	public ReactorAntennaBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	/**
	 * The boom's alarm output (MOD-945), said where the player meets it: the levels, that the alarm must be inverted
	 * to stop the reactor, and that a silent antenna gives no signal.
	 */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.reactor_antenna.alarm",
						MachineTooltipSpec.Tone.GRAY)),
				List.of(MachineTooltipSpec.text("tooltip.alaindustrial.reactor_antenna.alarm_stop",
								MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.text("tooltip.alaindustrial.reactor_antenna.alarm_silent",
								MachineTooltipSpec.Tone.GRAY)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING);
	}

	/** Facing the player; refused when the cell the boom needs is taken (as a bed refuses a blocked head). */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getNearestLookingDirection().getOpposite();
		BlockPos boom = context.getClickedPos().relative(facing);
		return context.getLevel().getBlockState(boom).canBeReplaced(context)
				? defaultBlockState().setValue(FACING, facing) : null;
	}

	/** The boom goes into the cell in front, as the head of a bed does. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		placeMast(level, pos, state);
	}

	/**
	 * Puts the boom in front of the antenna at {@code pos} when that cell is free or already holds it; returns
	 * whether the boom stands. The controller asks this every tick, so a boom lost to an explosion grows back once
	 * its cell is clear — and an antenna whose cell is built over stays silent until it is.
	 */
	public static boolean placeMast(Level level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(FACING);
		BlockPos boom = pos.relative(facing);
		if (!level.isLoaded(boom)) {
			return false;
		}
		BlockState there = level.getBlockState(boom);
		if (there.getBlock() instanceof ReactorAntennaMastBlock) {
			return there.getValue(ReactorAntennaMastBlock.FACING) == facing;
		}
		if (!there.canBeReplaced()) {
			return false;
		}
		level.setBlockAndUpdate(boom, ModContent.REACTOR_ANTENNA_MAST.get().defaultBlockState()
				.setValue(ReactorAntennaMastBlock.FACING, facing));
		return true;
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
		return new ReactorAntennaBlockEntity(pos, state);
	}
}
