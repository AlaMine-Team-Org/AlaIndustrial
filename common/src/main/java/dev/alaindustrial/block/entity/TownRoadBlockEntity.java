package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.TownRoadBlock;
import dev.alaindustrial.core.energy.CableNode;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The wire of a town road (MOD-861; epic MOD-824, decisions R111, R113): one segment of the energy network, a live
 * buffer the line passes energy through (ADR-001), ticked by the line's flow plan like any cable (ADR-049).
 *
 * <p><b>The grade.</b> Insulated copper, and only that in this release (R113): the throughput of copper and the loss
 * of the insulated copper cable. {@link #GRADE} is the one place it is named; when the town core learns to grow and
 * the drones relay the wire with a better one, the grade becomes a property of the road and this constant its
 * default. A road and a player's cable of any grade on one line take the strongest grade present, the network's
 * ordinary rule.
 *
 * <p><b>No accessories.</b> Unlike a {@link CableBlockEntity} a road has no breaker, no stand and no dye: it is
 * walked on, never touched, and its sleeve is the ground. It is never a shock hazard — the wire is insulated.
 *
 * <p><b>The glow.</b> A committed transfer stamps the game time; the segment's own tick sets the block's
 * {@link TownRoadBlock#POWERED} while that stamp is under {@link #GLOW_HOLD_TICKS} old, and clears it after. The
 * state is written to the clients only, with no neighbour update: the network does not see the change.
 */
public final class TownRoadBlockEntity extends EnergyBlockEntity implements CableNode {

	/** The wire inside every road of this release (R113). */
	public static final CableType GRADE = CableType.INSULATED_COPPER;

	/** How long after the last transfer the wire keeps glowing, in ticks. */
	public static final int GLOW_HOLD_TICKS = 20;

	/** Game tick of the most recent committed energy transfer; transient by design. */
	private long lastTransferTick = Long.MIN_VALUE;

	/** Whether the arms were worked out on this load: a drone or a structure places the bare default state. */
	private boolean armsChecked;

	/** Whether this segment is registered with its level's {@link NetworkManager}. */
	private boolean registered;

	public TownRoadBlockEntity(BlockPos pos, BlockState state) {
		// The same three numbers a cable of GRADE takes (CableBlockEntity): the buffer is the throughput.
		super(ModContent.TOWN_ROAD_BE.get(), pos, state, GRADE.tier(), GRADE.segmentBuffer(), GRADE.packetCap(),
				GRADE.packetCap());
	}

	@Override
	public CableType cableType() {
		return GRADE;
	}

	@Override
	public EnergyTier getTier() {
		return GRADE.tier();
	}

	/** Whether energy moved through this segment in the last {@link #GLOW_HOLD_TICKS} ticks. */
	public boolean carriesCurrent() {
		if (level == null || lastTransferTick == Long.MIN_VALUE) {
			return false;
		}
		long age = level.getGameTime() - lastTransferTick;
		return age >= 0 && age < GLOW_HOLD_TICKS;
	}

	@Override
	protected void onEnergyTransactionCommitted() {
		super.onEnergyTransactionCommitted();
		if (level != null) {
			lastTransferTick = level.getGameTime();
		}
	}

	/** Registers this segment with its level's network manager once; safe to call again. */
	public void ensureRegistered() {
		if (!registered && level instanceof ServerLevel) {
			NetworkManager.register(this);
			registered = true;
		}
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		ensureRegistered();
		BlockState current = state;
		if (!armsChecked) {
			armsChecked = true;
			BlockState wired = TownRoadBlock.withConnections(level, pos, state);
			if (wired != state) {
				level.setBlock(pos, wired, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				current = wired;
			}
		}
		boolean glowing = carriesCurrent();
		if (current.hasProperty(TownRoadBlock.POWERED) && current.getValue(TownRoadBlock.POWERED) != glowing) {
			level.setBlock(pos, current.setValue(TownRoadBlock.POWERED, glowing),
					Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		// A transfer wakes the segment (the buffer's commit hook); asleep, it only has to come back to go dark.
		return glowing ? GLOW_HOLD_TICKS : IDLE_SLEEP_TICKS;
	}

	@Override
	public void setRemoved() {
		if (registered && level instanceof ServerLevel) {
			NetworkManager.unregister(this);
			registered = false;
		}
		super.setRemoved();
	}
}
