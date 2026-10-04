package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.core.structure.ReactorMeltdown;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * What a reactor destroys (MOD-469, MOD-660; MOD-713, BE-5): a sealed room melting its own contents, a bare core
 * melting the scenery, a working room melting its plain pipes — one schedule, one victim at a time.
 */
public final class ReactorHazards {

	/** The controller's {@code setChanged}. */
	private final Runnable changed;

	/** Whether the room is melting its own contents right now — the panel's "Meltdown" line. */
	private boolean meltingDown;

	/**
	 * The block marked to melt and the ticks left before it does.
	 *
	 * <p><b>Not persisted, on purpose.</b> A pending melt is at most two seconds of intent; carrying it
	 * through a chunk round-trip would mean writing a position to NBT so that a block the player never
	 * saw marked could melt on a world they have just loaded. Forgetting it and picking again is both
	 * cheaper and fairer.
	 */
	@Nullable
	private BlockPos meltTarget;

	private int meltCountdown;

	/** Ticks until the next victim is chosen. Zero means "pick on the next tick that qualifies". */
	private int meltCooldown;

	/**
	 * Blocks this reactor has marked for melting since it was loaded.
	 *
	 * <p>Counts the CHOICE, not the change, so it moves even with {@code reactorMeltdownMeltsBlocks}
	 * off — the question it answers is "is the hazard running", which is exactly what a switch is not
	 * supposed to alter.
	 *
	 * <p><b>It exists because the hazard is otherwise unobservable except through the world</b>, and the
	 * world is a bad oracle for it: the melt reaches five blocks from any rack, a gametest rig is eight
	 * across, and where the victims land differs between the two loaders. A scenario counting lava
	 * passed on Fabric and failed on NeoForge with nothing between them but structure layout. Not
	 * persisted — it is a live counter, not a record.
	 */
	private int meltsScheduled;

	public ReactorHazards(Runnable changed) {
		this.changed = changed;
	}

	/**
	 * One tick of whatever this reactor is currently destroying (MOD-469); returns the heat after it — a melted
	 * block carries heat out of a melting room. {@code onMeltdown} runs on the edge into a meltdown.
	 *
	 * <p>Three hazards, one schedule, because no two can be running at once: a sealed room melts its
	 * own contents when it is allowed to overheat, a reactor with no room melts the scenery around it,
	 * and a sealed room that is merely WORKING melts the ordinary fluid pipes inside it, one at a time
	 * (MOD-660) — the reason the reinforced pipe exists. A meltdown already takes the plain pipes first,
	 * so it overrides the working-room pass rather than running beside it.
	 *
	 * <p><b>The warning is issued even when the switch is off.</b> An operator who has turned the block
	 * damage off should still be shown that their reactor has reached the state where it would have
	 * melted something — a hazard that goes completely silent teaches nobody anything, and the switch is
	 * meant to protect the world, not to hide the reactor's condition.
	 */
	public long run(ServerLevel serverLevel, BlockPos pos, long heat, boolean reacting, ReactorRoom room,
			ReactorEventLog log, Runnable onMeltdown) {
		// Melting the contents requires the room to still BE a room. A breached shell that is merely
		// still warm melts nothing: there is no containment left, so there is nothing being contained,
		// and its leftover heat simply bleeds away.
		boolean melting = room.isSealed()
				&& ReactorCore.isMeltingDown(ReactorCore.heatPercent(heat, ReactorConfig.reactorHeatCapacity),
						ReactorConfig.reactorMeltdownStartPercent);
		if (melting != meltingDown) {
			meltingDown = melting;
			changed.run();
			// MOD-473: the hidden meltdown step, on the EDGE rather than on a melted block. A room that
			// crosses the line has had its accident whether or not reactorMeltdownMeltsBlocks lets it
			// take the furniture with it, and an edge needs no latch of its own.
			if (melting) {
				onMeltdown.run();
			}
		}
		log.logMeltdown(serverLevel, melting, heat);
		// The scenery hazard runs on the REACTION, not on this tick's output. A core whose buffer is full
		// has stopped selling power and has not stopped being a reactor — hanging the danger on output let
		// a player switch it off by unplugging their machines (playtest finding 1). The redstone scram is
		// still a real safety measure, and still the only one: no signal, no reaction, no melting.
		boolean scenery = room.isBare() && reacting;
		// MOD-660: the working room's radiation, on the same REACTION signal as the scenery hazard and for
		// the same reason — a full buffer does not make a core safe to stand a copper pipe beside.
		boolean irradiating = !melting && room.isSealed() && reacting;
		if (!melting && !scenery && !irradiating) {
			meltTarget = null;
			meltCountdown = 0;
			return heat;
		}
		if (meltTarget != null) {
			if (meltCountdown > 0) {
				meltCountdown--;
				return heat;
			}
			BlockPos victim = meltTarget;
			meltTarget = null;
			if (ReactorConfig.reactorMeltdownMeltsBlocks && ReactorMeltdown.melt(serverLevel, victim) && melting) {
				// Every melted block carries heat out with it, which is what stops a meltdown being a
				// one-way trip: the room eats its own contents and cools as it does, and the player is
				// left with a wrecked interior inside a shell they can refit.
				heat = ReactorCore.heatAfterMelt(heat, ReactorConfig.reactorMeltdownHeatRelief);
				log.countMelt();
				changed.run();
			}
			return heat;
		}
		if (meltCooldown > 0) {
			meltCooldown--;
			return heat;
		}
		pickVictim(serverLevel, pos, melting, irradiating, room);
		return heat;
	}

	/** Chooses this round's victim on the schedule of whichever hazard is running, and telegraphs it. */
	private void pickVictim(ServerLevel serverLevel, BlockPos pos, boolean melting, boolean irradiating,
			ReactorRoom room) {
		ReactorBox box = room.box();
		BlockPos victim;
		if (melting) {
			meltCooldown = Math.max(1, ReactorConfig.reactorMeltdownIntervalTicks);
			victim = ReactorMeltdown.pickContentsVictim(serverLevel, box.minX(), box.minY(), box.minZ(),
					box.maxX(), box.maxY(), box.maxZ(), serverLevel.getRandom());
		} else if (irradiating) {
			meltCooldown = Math.max(1, ReactorConfig.reactorPipeMeltIntervalTicks);
			victim = ReactorMeltdown.pickIrradiatedPipe(serverLevel, box.minX(), box.minY(), box.minZ(),
					box.maxX(), box.maxY(), box.maxZ());
		} else {
			meltCooldown = ReactorCore.meltInterval(room.rods(), ReactorConfig.reactorBareMeltIntervalTicks,
					ReactorConfig.reactorBareMeltMinIntervalTicks);
			victim = ReactorMeltdown.pickSceneryVictim(serverLevel, hazardSource(serverLevel, pos, room),
					ReactorConfig.reactorBareMeltRadius, serverLevel.getRandom());
		}
		if (victim == null) {
			return;
		}
		ReactorMeltdown.telegraph(serverLevel, victim);
		meltsScheduled++;
		meltTarget = victim;
		meltCountdown = Math.max(0, ReactorConfig.reactorMeltWarnTicks);
	}

	/**
	 * Where this round's damage radiates from: one of the racks, chosen fresh each time (MOD-469).
	 *
	 * <p><b>The racks, not the controller, and the difference is visible from across the room.</b> A
	 * controller stands in a wall — in a half-built shell it is in ITS wall — so a sphere centred on it
	 * has the reactor's own body filling one half, where everything is either air or an exempt reactor
	 * block. The first playtest showed exactly that: a deliberately leaky reactor with holes on every
	 * side put lava only in front of the controller and left the ground behind it untouched. Rolling a
	 * rack per round instead puts the danger where the fuel is, spreads it evenly around the cluster,
	 * and makes turning the controller round change nothing. It is also the model radiation already
	 * uses, so the two hazards finally answer "how far is it dangerous" the same way.
	 *
	 * <p>Falls back to the controller only when the rack list is momentarily empty, which cannot happen
	 * while the scenery hazard is armed (it needs output, which needs rods) but keeps the method total.
	 */
	private static BlockPos hazardSource(ServerLevel level, BlockPos pos, ReactorRoom room) {
		List<BlockPos> bareRacks = room.bareRacks();
		if (bareRacks.isEmpty()) {
			return pos;
		}
		return bareRacks.get(level.getRandom().nextInt(bareRacks.size()));
	}

	/** Whether the room is melting its own contents right now. */
	public boolean isMeltingDown() {
		return meltingDown;
	}

	/** Blocks marked for melting since load — "is the hazard running". */
	public int meltsScheduled() {
		return meltsScheduled;
	}
}
