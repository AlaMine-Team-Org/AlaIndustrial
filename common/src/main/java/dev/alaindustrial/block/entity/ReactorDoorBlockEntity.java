package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ReactorDoorBlock;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The airlock panel's travel clock (MOD-493) — the whole reason the door has a block entity at all.
 *
 * <p><b>It stores nothing and syncs nothing.</b> There is no NBT, no {@code getUpdateTag}, no packet:
 * the one fact the animation needs — <em>when did {@code open} last change</em> — is derived on the
 * client by watching the block state it already receives. The server flips {@code open} in a single
 * tick exactly as it always did, that flip travels as an ordinary block update, and this class times
 * the panel's travel from the moment it sees the new value. A synced progress counter would put a
 * per-tick packet on a block whose entire behaviour is two edges every two seconds.
 *
 * <p><b>Nothing here is authoritative.</b> Collision, pathfinding and the radiation trace all read
 * {@code open} straight off the block state, so a client that never saw the transition — one that
 * loaded the chunk mid-slide — simply draws the panel parked at whichever end the state names. The
 * worst case is a missing animation, never a door that is open on one side and shut on the other.
 *
 * <p><b>Only the lower half keeps time</b> ({@link #animationClock()}). Both halves have a block
 * entity, because a state whose block is an {@code EntityBlock} is expected to produce one, but the
 * upper half reads the lower half's clock rather than running its own. Two independent clocks would
 * agree on almost every frame and disagree on the one where a packet lands between two client ticks —
 * and that disagreement is visible as a seam sliding across the middle of the panel.
 */
public class ReactorDoorBlockEntity extends BlockEntity {

	/** No transition seen yet: the panel sits at whichever end its block state names. */
	private static final long NO_TRANSITION = Long.MIN_VALUE;

	private boolean lastOpen;
	private boolean stateSeen;
	private long transitionStart = NO_TRANSITION;

	public ReactorDoorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.REACTOR_DOOR_BE.get(), pos, state);
	}

	/**
	 * Watches {@code open} for an edge from the client tick, so a door nobody is looking at still
	 * keeps its clock — otherwise it would animate the moment it came into view.
	 */
	public void clientTick(BlockState state, long gameTime) {
		observe(state.getValue(ReactorDoorBlock.OPEN), gameTime);
	}

	/**
	 * Records where the door stands, and when it last changed.
	 *
	 * <p>The first call only takes the reading: a door that comes into view already open must not play
	 * its opening slide as if it had just been triggered.
	 *
	 * <p><b>Idempotent, and called from two places on purpose.</b> The client ticks twenty times a
	 * second while the renderer draws every frame, so between a block update landing and the next tick
	 * there are frames that see the NEW {@code open} and the OLD transition start. Left to the ticker
	 * alone, those frames resolved to the far end of the animation instead of its beginning, and the
	 * panel snapped to full height for up to 50 ms before the close began — a flash of a whole shut
	 * door, on roughly every other cycle, depending on where the frames happened to land between ticks.
	 */
	private void observe(boolean open, long gameTime) {
		if (!stateSeen) {
			stateSeen = true;
			lastOpen = open;
			return;
		}
		if (open != lastOpen) {
			lastOpen = open;
			transitionStart = gameTime;
		}
	}

	/**
	 * The block entity whose clock this half should draw by: the lower one, always. Falls back to
	 * {@code this} if the pair is momentarily broken (a half mined this very frame), which parks the
	 * panel rather than throwing inside a renderer.
	 */
	public ReactorDoorBlockEntity animationClock() {
		if (level == null || getBlockState().getValue(ReactorDoorBlock.HALF) == DoubleBlockHalf.LOWER) {
			return this;
		}
		return level.getBlockEntity(worldPosition.below()) instanceof ReactorDoorBlockEntity lower ? lower : this;
	}

	/**
	 * How far the panel has retracted: {@code 0} fully shut, {@code 1} fully sunk into the floor.
	 *
	 * <p>{@code gameTime} arrives as a {@code long} and the partial tick separately, rather than as one
	 * pre-added float, on purpose. A world a few real-world days old has a game time past the point
	 * where a {@code float} can still resolve single ticks, let alone fractions of one — adding first
	 * and subtracting second would quantise the whole animation to a stutter. Subtracting the two longs
	 * first keeps the elapsed count small, and only then is it safe to be a float.
	 */
	public float slideProgress(long gameTime, float partialTicks) {
		boolean open = getBlockState().getValue(ReactorDoorBlock.OPEN);
		// Catch the edge here as well as in the ticker — see observe(). A frame that arrives before the
		// tick which would have noticed the change must start the animation itself, not draw its far end.
		observe(open, gameTime);
		if (transitionStart == NO_TRANSITION) {
			return open ? 1.0f : 0.0f;
		}
		float span = Math.max(1, Config.reactorDoorSlideTicks);
		float elapsed = (float) (gameTime - transitionStart) + partialTicks;
		float t = Mth.clamp(elapsed / span, 0.0f, 1.0f);
		// Smoothstep: the panel leaves and arrives with zero speed. A linear ramp starts and stops
		// dead, which reads as a texture being dragged rather than as a mass being driven.
		float eased = t * t * (3.0f - 2.0f * t);
		return open ? eased : 1.0f - eased;
	}
}
