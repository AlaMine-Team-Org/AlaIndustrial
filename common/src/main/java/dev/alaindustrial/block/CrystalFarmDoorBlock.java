package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The way into a greenhouse (MOD-505) — a glazed door that shuts itself again.
 *
 * <p><b>What an open door does NOT do: breach the room.</b> The scan classifies a door by what it
 * is, not by whether it is ajar — exactly as the reactor's airlock is classified — so a greenhouse
 * goes on growing with its door wide open. This javadoc used to claim the opposite, and an audit
 * caught it: the claim was never true, and the timer below was described as guarding against
 * something that cannot happen.
 *
 * <p>Making an open door breach the room was considered and rejected. It would be honest, but every
 * trip through the door would then grey out the whole shell and repaint it moments later — a
 * greenhouse the size of a hall flickering twice per visit, and a few thousand block updates each
 * way. The cure is worse than the itch.
 *
 * <p><b>So why close it at all.</b> Because a greenhouse standing open is wrong to look at, and
 * because the room is sealed against everything else — mobs walk in through a door nobody shut. It
 * is a convenience and a piece of theatre, not a safety interlock, and it is worth saying so plainly
 * rather than inventing a mechanism for it.
 *
 * <p><b>Nothing holds it open, redstone included (MOD-522).</b> The earlier rule — "a signal means
 * somebody is holding this door open deliberately, so leave it alone" — was dropped by an explicit
 * decision: a greenhouse is meant to be shut, full stop, and a door wired to a lever nobody throws
 * back is exactly the case that leaves one standing open for a whole session. The cost is named
 * rather than hidden: under a signal that stays up the door opens once, closes after its delay, and
 * will not open again until the signal is toggled, because the close deliberately leaves
 * {@code POWERED} alone (see {@link #tick}). A bay driven by a button pulse is unaffected; one
 * driven by a lever left up is.
 *
 * <p><b>It will not close on somebody standing in it.</b> The occupancy check re-arms the timer
 * instead, so a player waiting in the doorway is never shoved; the same courtesy the reactor's
 * airlock extends, for the same reason. Since redstone stopped holding the door, this check is the
 * only thing between the timer and a door shut in somebody's face.
 *
 * <p>Everything else is vanilla {@link DoorBlock}: hinges, the two halves, opening by hand and by
 * redstone. The reactor's airlock is a different animal — it slides, it has its own block entity to
 * time that travel — because a reactor door is a safety device. This one is a door with a spring.
 *
 * <p>{@link BlockSetType#COPPER} rather than a set type of its own: it carries the metal open/close
 * sounds this frame wants, and registering a bespoke type would mean doing it on both loaders for a
 * pair of sound events that already exist.
 */
public class CrystalFarmDoorBlock extends DoorBlock {

	public static final MapCodec<CrystalFarmDoorBlock> CODEC = simpleCodec(CrystalFarmDoorBlock::new);

	public CrystalFarmDoorBlock(Properties properties) {
		super(BlockSetType.COPPER, properties);
	}

	@Override
	public MapCodec<? extends DoorBlock> codec() {
		return CODEC;
	}

	private static int autoCloseTicks() {
		return Math.max(1, Config.crystalFarmDoorAutoCloseTicks);
	}

	private static int occupiedRecheckTicks() {
		return Math.max(1, Config.crystalFarmDoorOccupiedRecheckTicks);
	}

	/**
	 * A hand on the door — and <b>the path that was missing</b> (MOD-522).
	 *
	 * <p>Vanilla's {@link DoorBlock#useWithoutItem} does not call {@link #setOpen}: it cycles
	 * {@code OPEN} and writes the block itself. So a door that armed its timer only from
	 * {@code setOpen} armed it for mobs and for redstone and never once for the player — and every
	 * greenhouse anybody walked into stood open for the rest of the session.
	 *
	 * <p>Both hooks are needed, and neither covers the other: this one is the hand, {@code setOpen} is
	 * everything that is not a hand. An earlier round had exactly one of them at a time and was wrong
	 * in each direction in turn.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		InteractionResult result = super.useWithoutItem(state, level, pos, player, hit);
		if (!level.isClientSide() && opened(level, pos)) {
			armClose(level, pos);
		}
		return result;
	}

	/**
	 * Everything that opens a door without being a hand: a villager, a zombie on a copper door, a
	 * wind charge, another mod. Vanilla funnels all of those through here — {@code DoorInteractGoal}
	 * and {@code onExplosionHit} call it by name.
	 */
	@Override
	public void setOpen(net.minecraft.world.entity.Entity opener, Level level, BlockState state,
			BlockPos pos, boolean open) {
		super.setOpen(opener, level, state, pos, open);
		if (open && !level.isClientSide()) {
			armClose(level, pos);
		}
	}

	/**
	 * Redstone gets its own look-in: a signal opens the door without anything calling
	 * {@link #setOpen}, and a one-tick button pulse would otherwise leave it open forever.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide() && opened(level, pos)) {
			armClose(level, pos);
		}
	}

	/** Whether {@code pos} still holds one of our doors, and that door is open. */
	private boolean opened(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(this) && isOpen(state);
	}

	/**
	 * Schedules the close on the LOWER half only. Both halves receive neighbour updates and both are
	 * clickable, so arming from either would give one door two independent timers racing each other.
	 */
	private void armClose(Level level, BlockPos pos) {
		BlockPos lower = lowerHalf(level, pos);
		if (lower != null && !level.getBlockTicks().hasScheduledTick(lower, this)) {
			level.scheduleTick(lower, this, autoCloseTicks());
		}
	}

	/** The lower half of the door {@code pos} belongs to, or {@code null} if this is not our door. */
	private BlockPos lowerHalf(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(this)) {
			return null;
		}
		return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.is(this) || !isOpen(state)) {
			return;
		}
		if (isOccupied(level, pos)) {
			level.scheduleTick(pos, this, occupiedRecheckTicks());
			return;
		}
		// POWERED is left exactly as it is, on purpose (MOD-522). Clearing it would make vanilla's own
		// neighbourChanged see `signal != POWERED` on the very next neighbour update and open the door
		// straight back up — an open/close loop for as long as the lever is up. Left set, the state
		// reads "shut, and still wired to a live signal", and the next toggle opens it again.
		setOpen(null, level, state, pos, false);
	}

	/**
	 * Whether anything is standing in the doorway.
	 *
	 * <p>The box covers both halves, because a player stands in the lower one and their head is in
	 * the upper: testing only the block that carries the timer would shut the door on somebody
	 * half-way through it.
	 */
	private static boolean isOccupied(ServerLevel level, BlockPos lower) {
		AABB doorway = new AABB(lower.getX(), lower.getY(), lower.getZ(),
				lower.getX() + 1.0, lower.getY() + 2.0, lower.getZ() + 1.0);
		// Living things only. The predicate used to be "alive and not a spectator", which an audit
		// pointed out is also true of a dropped item — one shard fumbled in the doorway would hold the
		// door open for as long as it lay there, re-arming the timer every ten ticks forever.
		return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, doorway,
				entity -> !entity.isSpectator()).isEmpty();
	}
}
