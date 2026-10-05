package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.ReactorBlast;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.core.structure.ReactorLog;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The accident at the top of the scale (MOD-471; MOD-713, BE-5): the countdown between a pinned gauge and the
 * explosion, and the explosion itself.
 */
public final class ReactorBlastTimer {

	/** Save key of {@link #blastBelowTicks} (MOD-727): additive, a save without it loads the old way. */
	private static final String BLAST_BELOW_TICKS_KEY = "BlastBelowTicks";

	/** The controller's {@code setChanged}: the timer is saved. */
	private final Runnable changed;

	/**
	 * Ticks left before this core blows up, or zero when no accident is under way.
	 *
	 * <p><b>Persisted, unlike most of the reactor's live state, and for a specific reason.</b> The duration is
	 * rolled per accident; a countdown that reset on restart would turn "log out and back in" into a
	 * way to re-roll a bad number, and a server restart into a free rescue. The heat that caused it is
	 * already saved, so the accident survives anyway — this only keeps it honest about how far along it
	 * had got.
	 */
	private int blastCountdown;

	/** What {@link #blastCountdown} started from, so the panel can draw a share rather than seconds. */
	private int blastCountdownTotal;

	/**
	 * Consecutive ticks the scale has spent under a hundred percent while a countdown is armed.
	 *
	 * <p>Persisted ({@value #BLAST_BELOW_TICKS_KEY}, MOD-727) with the countdown it belongs to: a rescue that
	 * was most of the way through its release window when the chunk unloaded is still most of the way through
	 * it after the reload, rather than starting the window again. A save from before the key loads it as zero.
	 */
	private int blastBelowTicks;

	public ReactorBlastTimer(Runnable changed) {
		this.changed = changed;
	}

	/**
	 * The countdown between a pinned gauge and the explosion (MOD-471). {@code critical} is whether the scale the
	 * reactor is judged on sits at the top this tick; {@code logLine} writes the armed, cancelled and expired lines;
	 * {@code dismantle} is what the controller does to its room before a blast.
	 *
	 * <p><b>Armed by the scale and disarmed by the scale, which is what makes every cancellation work
	 * without any of them being written down.</b> Water, the scram lever, a hole punched in the wall,
	 * even unplugging the machines that were drawing the power — all four end the same way, with the
	 * gauge coming off a hundred percent, and that one condition covers them. There is no point of no
	 * return: the reactor can be saved on the last tick.
	 *
	 * <p>The duration is rolled once, when the countdown arms, somewhere between two and three minutes.
	 * A fixed delay would be memorised within a week and stop being read.
	 */
	public void runCountdown(ServerLevel level, BlockPos pos, boolean critical, ReactorRoom room,
			Consumer<ReactorLog.Kind> logLine, Runnable dismantle) {
		ReactorCore.BlastTimer before =
				new ReactorCore.BlastTimer(blastCountdown, blastCountdownTotal, blastBelowTicks);
		// Rolled every tick and used only on the tick that arms — cheaper than branching, and it keeps
		// the whole transition inside one Minecraft-free function that a unit test can drive.
		int roll = ReactorCore.blastCountdown(ReactorConfig.reactorBlastCountdownMinTicks,
				ReactorConfig.reactorBlastCountdownMaxTicks, level.getRandom().nextInt(Integer.MAX_VALUE));
		ReactorCore.BlastTimer after = ReactorCore.tickBlast(before, critical,
				ReactorConfig.reactorBlastReleaseTicks, roll);
		if (!after.equals(before)) {
			blastCountdown = after.remaining();
			blastCountdownTotal = after.total();
			blastBelowTicks = after.belowTicks();
			changed.run();
		}
		// Armed and disarmed are the log's lines (MOD-622); a pause under the line is not — it flickers with a
		// redstone clock. The timer is persisted, so a reload repeats neither.
		if (before.armed() != after.armed()) {
			logLine.accept(after.armed() ? ReactorLog.Kind.COUNTDOWN_ARMED
					: critical ? ReactorLog.Kind.COUNTDOWN_EXPIRED : ReactorLog.Kind.COUNTDOWN_CANCELLED);
		}
		if (after.armed()) {
			if (critical) {
				ReactorBlast.telegraphCountdown(level, pos, after.remaining(), after.total());
			}
			return;
		}
		// Not armed any more. Either it was never armed, or the core has been held under the line long
		// enough to call the accident off — in both cases there is nothing to do. Only a timer that ran
		// out WHILE the core was still critical detonates.
		if (!before.armed() || !critical) {
			return;
		}
		// The switch is read HERE rather than at the top, so an operator who turned the damage off still
		// gets the whole performance — siren, particles, a panel counting down — and simply no crater. A
		// hazard that goes completely silent teaches nobody anything; MOD-469's rule, kept.
		if (ReactorConfig.reactorBlastEnabled) {
			explode(level, pos, room, dismantle);
		}
	}

	/**
	 * The accident itself.
	 *
	 * <p><b>The room is taken apart BEFORE the blast, and that order is load-bearing.</b> The controller's
	 * {@code unformOnRemoval} only ever runs from the player's own mining hook, because touching the
	 * world from a block entity's removal path deadlocks the server on chunk unload — something this
	 * repository has already paid for once. A controller destroyed by an explosion therefore never runs
	 * it, and the racks it painted with the drone flag would hum for the rest of the world's life with
	 * nothing left able to switch them off. Here we ARE the explosion, so it can be done properly: while
	 * the controller is still standing — that is {@code dismantle}, which also hands out the hidden accident
	 * step FIRST (MOD-473): everything after it dismantles the reactor, and the last statement destroys the
	 * controller itself, so a trigger fired later would come from a block entity the world has already dropped.
	 */
	private void explode(ServerLevel level, BlockPos pos, ReactorRoom room, Runnable dismantle) {
		dismantle.run();
		BlockPos epicentre = blastEpicentre(room, pos);
		float power = ReactorCore.blastPower(room.rods(), ReactorConfig.reactorBlastBasePower,
				ReactorConfig.reactorBlastPowerPerTenRods, ReactorConfig.reactorBlastMaxPower);
		Set<BlockPos> before = ReactorBlast.snapshotSolids(level, epicentre, ReactorConfig.reactorFalloutRadius);
		ReactorBlast.detonate(level, Vec3.atCenterOf(epicentre), power, ReactorConfig.reactorBlastFire);
		// Everything after this is keyed to what the blast ACTUALLY destroyed, never to a radius. If a
		// land-claim mod refused the explosion, this list comes back empty and there is no aftermath at
		// all — the protection is honoured without this class knowing such mods exist.
		List<BlockPos> destroyed = ReactorBlast.destroyedSince(level, before);
		ReactorBlast.pourLava(level, destroyed, epicentre, ReactorConfig.reactorBlastLavaCells);
		ReactorBlast.scatterFallout(level, destroyed, epicentre, ReactorConfig.reactorFalloutRadius);
		// The controller goes LAST, and by hand rather than by hoping the blast reaches it.
		//
		// It is built of the same shielding alloy as the wall, so at the powers a small core produces
		// only a lucky ray breaks it — the first run of the gametest found the controller standing in a
		// gutted room. That is not a cosmetic loose end: the core it is still driving is still at a
		// hundred percent, so it would re-arm the countdown and explode again, and again, for ever. A
		// reactor gets to have exactly one accident.
		level.destroyBlock(pos, false);
	}

	/**
	 * Where the blast is centred.
	 *
	 * <p>The middle of the sealed interior for a room — the point furthest from every wall, so the shell
	 * gets its fair chance to contain the thing it was built to contain, and a fixed point a gametest can
	 * assert. For a bare core, the middle of the pile it was driving: there is no shell to be fair to,
	 * and the fuel is what exploded.
	 */
	private static BlockPos blastEpicentre(ReactorRoom room, BlockPos pos) {
		ReactorBox box = room.box();
		if (!room.isBare() && box.isSet()) {
			return new BlockPos((box.minX() + box.maxX()) / 2, (box.minY() + box.maxY()) / 2,
					(box.minZ() + box.maxZ()) / 2);
		}
		List<BlockPos> bareRacks = room.bareRacks();
		if (!bareRacks.isEmpty()) {
			long x = 0;
			long y = 0;
			long z = 0;
			for (BlockPos at : bareRacks) {
				x += at.getX();
				y += at.getY();
				z += at.getZ();
			}
			int count = bareRacks.size();
			return new BlockPos((int) (x / count), (int) (y / count), (int) (z / count));
		}
		return pos;
	}

	/** How much of the countdown is left, 0…100 — a share, never the seconds (see the console's channel). */
	public int percentLeft() {
		return blastCountdownTotal <= 0 || blastCountdown <= 0
				? 0 : Math.max(1, blastCountdown * 100 / blastCountdownTotal);
	}

	/** Ticks left before this core blows up; zero when no accident is under way. */
	public int remaining() {
		return blastCountdown;
	}

	/** What the countdown started from — the duration this particular accident rolled. */
	public int total() {
		return blastCountdownTotal;
	}

	/** Writes the countdown, its rolled length and the release counter. */
	public void save(ValueOutput output) {
		output.putInt("BlastCountdown", blastCountdown);
		output.putInt("BlastCountdownTotal", blastCountdownTotal);
		output.putInt(BLAST_BELOW_TICKS_KEY, blastBelowTicks);
	}

	public void load(ValueInput input) {
		blastCountdown = input.getIntOr("BlastCountdown", 0);
		blastCountdownTotal = input.getIntOr("BlastCountdownTotal", 0);
		blastBelowTicks = input.getIntOr(BLAST_BELOW_TICKS_KEY, 0);
	}
}
