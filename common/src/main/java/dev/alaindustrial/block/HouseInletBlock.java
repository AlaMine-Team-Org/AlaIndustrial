package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.HouseInletBlockEntity;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.settlement.works.BuildingCard;
import dev.alaindustrial.settlement.works.HousePlacementWindow;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The house inlet (MOD-876; epic MOD-824, decisions R7, R90): the wall block a cable enters a shift worker's house
 * through, as the reactor's port is the one place water crosses its shell. A full cube that stands in the house's wall
 * with its {@link #FACING} face looking out; it is placed facing the player, so a player standing outside the house
 * gets it the right way round.
 *
 * <p><b>Only the outer face is a socket.</b> A cable joins it on that face alone, and only there does it take EU
 * ({@link HouseInletBlockEntity#energyRoleForFace}); the five other faces are wall. Nothing comes back out: the power
 * is for the beds inside.
 *
 * <p><b>The anchor of a house.</b> Put down by a town's restorer on the town's territory, it starts the laying of a
 * new house there (MOD-877, R108): the window of {@link HousePlacementWindow} opens, and the house's template is
 * placed so its inlet cell is this block. A right click opens the window again. The block holds no town binding of
 * its own: the town's building list remembers which house it anchors.
 */
public class HouseInletBlock extends HorizontalMachineBlock implements HasMachineTooltip {

	public HouseInletBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HouseInletBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/** A restorer putting the inlet down on the town's territory starts laying a new house there (MOD-877). */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof ServerPlayer player) {
			HousePlacementWindow.open(player, pos, true);
		}
	}

	/**
	 * A right click opens the laying window again, for the town's restorer; on the inlet of a house of the town's own
	 * the building's card (round 2 of 0.1.219, Q2); elsewhere the click passes.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			// A laid house's inlet opens its laying window; the inlet of any other town house its card (Q2).
			return HousePlacementWindow.open(serverPlayer, pos, false) || BuildingCard.open(serverPlayer, pos)
					? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return super.useWithoutItem(state, level, pos, player, hit);
	}

	/** The cable's arm reaches the outer face only: the inverse of a machine, whose front is the inert one. */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return side == state.getValue(FACING);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** Hover tooltip of the item (MOD-716, ADR-040): what it is for, and its one socket. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.house_inlet.purpose",
						MachineTooltipSpec.Tone.GRAY)),
				List.of(MachineTooltipSpec.text("tooltip.alaindustrial.house_inlet.outer_face",
								MachineTooltipSpec.Tone.GRAY),
						MachineTooltipSpec.statValue("buffer", () -> HouseInletBlockEntity.BUFFER)));
	}

	/** Its lines are whole sentences: broken to the width of a vanilla description (R4-3). */
	@Override
	public boolean wrapsTooltipLines() {
		return true;
	}
}
