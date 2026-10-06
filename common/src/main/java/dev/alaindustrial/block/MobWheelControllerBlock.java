package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MobWheelBlockEntity;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Mob Wheel Drive (MOD-763): the wooden drive in the back right corner of the mob wheel — its block entity,
 * its feeder slot and its LV port. {@code facing} is the way the wheel's front (the gate side) faces; the
 * structure sets it when it assembles, a loose drive simply faces the player like any machine.
 *
 * <p>Energy leaves through the two faces that point out of the structure: the canonical {@code +x} face (the
 * copper port of the model) and the back. A loose drive keeps the same two faces, so a cable laid to a
 * half-built wheel is already in the right place.
 */
public class MobWheelControllerBlock extends HorizontalMachineBlock implements HasMachineTooltip {
	public MobWheelControllerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(MobWheelStructure.FORMED, Boolean.FALSE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(MobWheelStructure.FORMED);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MobWheelBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide()) {
			// The wheel's spin, the gate's swing and the occupant's legs (MOD-763): animation only.
			return (lvl, pos, st, be) -> {
				if (be instanceof MobWheelBlockEntity wheel) {
					wheel.clientTick(lvl, st);
				}
			};
		}
		return machineTicker(level);
	}

	/** The loose drive: its model stops a pixel short of the top, so it is not a full cube (ADR-023). */
	private static final VoxelShape LOOSE = Block.box(0, 0, 0, 16, 15, 16);

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(MobWheelStructure.FORMED) ? Shapes.block() : LOOSE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return state.getValue(MobWheelStructure.FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
	}

	/** The faces EU leaves through for a drive with this {@code facing}: the outward side and the back. */
	public static boolean isPortFace(Direction facing, Direction face) {
		return face == MobWheelStructure.runDirection(facing) || face == facing.getOpposite();
	}

	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return isPortFace(state.getValue(FACING), side);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		MobWheelStructure.tryAssemble(level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!state.getValue(MobWheelStructure.FORMED)) {
			MobWheelStructure.tryAssemble(level, pos);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		MobWheelStructure.onMemberRemoved(level, pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (state.getValue(MobWheelStructure.FORMED)) {
			InteractionResult recoloured = MobWheelMemberBlock.recolour(stack, level, pos, player);
			if (recoloured != null) {
				return recoloured;
			}
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/**
	 * Formed: the lead shortcut, else the drive's screen. Loose: an actionbar line saying what the wheel still
	 * lacks.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (state.getValue(MobWheelStructure.FORMED)) {
			InteractionResult ledIn = MobWheelMemberBlock.leadIn(level, pos, player);
			return ledIn != null ? ledIn : super.useWithoutItem(state, level, pos, player, hit);
		}
		if (!level.isClientSide() && !MobWheelStructure.tryAssemble(level, pos)) {
			player.sendOverlayMessage(MobWheelStructure.diagnose(level, pos));
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Hover tooltip of the drive's item (MOD-716, ADR-040): that nothing is stored and how to recolour the
	 * wheel; [SHIFT] adds the LV tier. Which mobs run and what they make is deliberately not told (D7): the
	 * player finds out by trying, and the analyzer (MOD-775) is what reveals the numbers.
	 */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.mob_wheel_no_buffer",
								MachineTooltipSpec.Tone.DARK_GRAY),
						MachineTooltipSpec.note("tooltip.alaindustrial.mob_wheel_recolour",
								MachineTooltipSpec.Tone.DARK_GRAY)),
				List.of());
	}
}
