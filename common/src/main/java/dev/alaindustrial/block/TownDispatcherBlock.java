package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The town dispatcher (MOD-858; epic MOD-824, decisions R84, R91, R95): a full LV machine casing facing the player, a
 * round radar screen on its front (rings of the drone link run across it while the town's drones work — the
 * {@code lit} state), and an antenna with a copper tip at the back corner of its top, six pixels above the block. The
 * work is in {@link TownDispatcherBlockEntity}.
 *
 * <p><b>Shapes.</b> A click finds the casing and the antenna, so the mast is not a hole in the outline; a walking
 * player meets only the casing. Both are built once per facing at class-init (ADR-023).
 *
 * <p><b>Who breaks it.</b> In a town with an owner only the owner (a pickaxe, as any machine); in a town of none,
 * anyone. Takes no cable: the dispatcher runs on no EU.
 */
public class TownDispatcherBlock extends LitMachineBlock implements HasMachineTooltip {

	/** The antenna's top above the block, in pixels: what the outline and the model reach. */
	public static final double ANTENNA_TOP_PX = 22.0;

	private static final Map<Direction, VoxelShape> OUTLINE = Shapes.rotateHorizontal(Shapes.or(
			Shapes.block(),
			Block.box(11, 16, 11, 14, 17, 14),
			Block.box(11.5, 17, 11.5, 13.5, ANTENNA_TOP_PX, 13.5)));

	public TownDispatcherBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TownDispatcherBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return OUTLINE.get(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return Shapes.block();
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
	public boolean isCableConnectable() {
		return false;
	}

	/** In a town with an owner, nothing for anybody else; otherwise as any machine. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof TownDispatcherBlockEntity dispatcher
				&& !dispatcher.mayBeBrokenBy(player)) {
			return 0.0F;
		}
		return super.getDestroyProgress(state, player, level, pos);
	}

	/** Hover tooltip of the item (MOD-716, ADR-040): what it is for. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.town_dispatcher.purpose",
						MachineTooltipSpec.Tone.GRAY)),
				List.of());
	}

	/** Its lines are whole sentences: broken to the width of a vanilla description (R4-3). */
	@Override
	public boolean wrapsTooltipLines() {
		return true;
	}
}
