package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorOutletBlockEntity;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.BareReactorScan;
import dev.alaindustrial.core.structure.RoomScan;
import dev.alaindustrial.core.structure.RoomValidator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * What a reactor controller knows about its room (MOD-713, BE-5): the last scan's verdict and measurements, the
 * racks and sockets it found, the bare racks it drives when there is no room, and the box it last sealed.
 *
 * <p>The controller decides when a scan runs and in which order the room, the voice and the log answer it
 * ({@code ReactorControllerBlockEntity.rescan}); this class does the parts that belong to the shell — measuring it,
 * painting it sealed or unsealed, sweeping the drone flag out of the remembered box, and marking a finished room or
 * a hole in the world.
 */
public final class ReactorRoom {

	private ReactorRoomStatus status = ReactorRoomStatus.CONTROLLER_NOT_IN_WALL;

	/** What the last scan measured, for the console. */
	private ReactorRoomReadout readout = ReactorRoomReadout.EMPTY;

	/** The box this controller last sealed — see {@link ReactorBox}. */
	private final ReactorBox box;

	/** Rods burning across the room, refreshed each scan. */
	private int rods;
	/** Assemblies found inside the sealed room, refreshed on every scan. */
	private final List<BlockPos> assemblies = new ArrayList<>();

	/** Sockets set into this room's shell. Refreshed by the same sweep that finds the columns. */
	private final List<BlockPos> outlets = new ArrayList<>();

	// ── MOD-469: the bare reactor ──
	/**
	 * Racks a controller with no sealed room drives, refreshed by the same sweep as {@link #assemblies}
	 * and empty whenever the room is formed. The two lists are never both populated: a controller is
	 * either running a room or running in the open.
	 */
	private final List<BlockPos> bareRacks = new ArrayList<>();

	/**
	 * Whether this controller is running without a room around it.
	 *
	 * <p>Decided by the scan, not by the status alone: <em>every</em> status but {@code FORMED} could be
	 * a player halfway through building their shell, and those two cases want different things from the
	 * panel. A controller only counts as bare once the sweep has actually found racks to burn.
	 */
	private boolean bare;

	/**
	 * Every rack the bare sweep reached that nobody else claims, fuelled or not — what the «Core» tab shows while there
	 * is no sealed room (MOD-620). {@link #bareRacks} is what burns; a rack of spent casings is only here.
	 */
	private final List<BlockPos> bareShown = new ArrayList<>();

	/** {@code changed} is the controller's {@code setChanged}: the box is saved, so a change must reach the chunk. */
	public ReactorRoom(Runnable changed) {
		this.box = new ReactorBox(changed);
	}

	/**
	 * Runs the room scan and keeps what the readouts need: the breach and the box as offsets from the controller,
	 * and the holes. The verdict itself is taken by {@link #adopt}, so the caller still knows the old one.
	 */
	public RoomScan.Result measure(Level level, BlockPos pos, Direction facing) {
		RoomScan.Result result = RoomValidator.scan(level, pos, facing,
				ReactorConfig.reactorRoomMinInner, ReactorConfig.reactorRoomMaxInner,
						ReactorConfig.reactorRoomMaxGlassPercent);
		readout = ReactorRoomReadout.of(result, pos);
		return result;
	}

	/** Takes the scan's verdict; true when it differs from the last one. */
	public boolean adopt(ReactorRoomStatus scanned) {
		boolean changed = scanned != status;
		status = scanned;
		return changed;
	}

	/**
	 * Paints the shell for this verdict and returns how many blocks changed: the whole shell wears the flag, not just
	 * the controller — that is what makes a finished room read as one surface instead of a stack of crates, and what
	 * lights its lamps.
	 */
	public int paintShell(Level level, RoomScan.Result result) {
		if (result.formed()) {
			int repainted = RoomValidator.applyFormed(level, result.minX(), result.minY(), result.minZ(),
					result.maxX(), result.maxY(), result.maxZ(), true);
			// A room can shrink without ever failing its scan — wall a running interior in two and the
			// near half is still a valid room. Racks left on the far side drop out of the sweep with the
			// drone flag still on them and nothing left that would ever take it off, so the OLD box gets
			// swept before it is forgotten. Racks still inside are repainted in the same tick, so the
			// clearing is invisible (MOD-472).
			if (box.changesTo(result)) {
				box.clearActive(level);
			}
			box.remember(result);
			return repainted;
		}
		// Clear the box we last sealed — not whatever this scan measured, which is empty or never sealed. Without
		// this the shell would stay seamless and lit around a hole (playtest, 2026-08-19).
		return box.clear(level);
	}

	/** A sealed room: no bare racks any more, and the interior's racks and sockets collected afresh. */
	public void claimRoom(Level level, RoomScan.Result result) {
		bare = false;
		bareRacks.clear();
		bareShown.clear();
		collectAssemblies(level, result);
	}

	/** Forgets the room's racks and sockets — only once they have been silenced (see the controller's rescan). */
	public void forgetRoom() {
		assemblies.clear();
		outlets.clear();
		rods = 0;
	}

	/**
	 * Looks for racks to burn with no room to walk (MOD-469).
	 *
	 * <p>Runs on the same timer as the room sweep and for the same reason: a radius scan every tick would
	 * be absurd, and a rack racked by hand counting from the next sweep is at most two seconds of delay
	 * against a rod that burns for minutes.
	 *
	 * <p><b>A controller is bare only once it has actually found something.</b> An empty-handed sweep
	 * leaves {@code bare} false, so a player halfway through building a shell keeps the whole building
	 * layout on their panel instead of being told they are running a reactor they have not started.
	 */
	public void rescanBare(Level level, BlockPos pos) {
		bareRacks.clear();
		bareShown.clear();
		if (!(level instanceof ServerLevel serverLevel)) {
			bare = false;
			return;
		}
		BareReactorScan.Result found = BareReactorScan.scan(serverLevel, pos,
				ReactorConfig.reactorBareSearchRadius);
		bareRacks.addAll(found.racks());
		bareShown.addAll(found.shown());
		rods = found.rods();
		bare = !found.isEmpty();
	}

	/**
	 * The live block entities behind {@link #assemblies}, in scan order.
	 *
	 * <p>Entries whose block entity has gone are simply absent: the list is rebuilt every tick, so a
	 * column mined mid-tick drops out immediately rather than waiting for the next room scan.
	 */
	public List<FuelRodAssemblyBlockEntity> collectColumns(Level level) {
		// Whichever list this controller is actually driving. The two are never both populated, so this
		// is a switch rather than a merge — see rescanBare.
		List<BlockPos> racked = bare ? bareRacks : assemblies;
		if (racked.isEmpty()) {
			return List.of();
		}
		List<FuelRodAssemblyBlockEntity> columns = new ArrayList<>(racked.size());
		for (BlockPos rack : racked) {
			if (level.getBlockEntity(rack) instanceof FuelRodAssemblyBlockEntity column) {
				columns.add(column);
			}
		}
		return columns;
	}

	/**
	 * Walks the room's interior and records every loaded fuel assembly, the total rod count and how
	 * many of those racks stand next to each other.
	 *
	 * <p>Done on the scan timer rather than per tick: a 12³ interior is 1728 cells, which is fine every
	 * couple of seconds and absurd sixty times a second. The cost of that choice is that a rod inserted
	 * by hand counts from the next sweep, which is at most two seconds — imperceptible next to a rod
	 * that burns for two minutes.
	 */
	private void collectAssemblies(Level level, RoomScan.Result box) {
		assemblies.clear();
		outlets.clear();
		rods = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		// The scan reports the INTERIOR box; the shell is the ring one block outside it. Sweeping the
		// interior alone found every column and no socket at all — a reactor outlet lives in the wall by
		// definition, so it can only ever be on that ring. Widening by one covers both without a second
		// pass, and cannot pick up strays: the ring is shell blocks, which are never columns.
		for (int y = box.minY() - 1; y <= box.maxY() + 1; y++) {
			for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
				for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
					// EVERY column, loaded or not. Filtering on hasFuel() looked harmless and was not: an
					// empty column was invisible to the controller, so it was left out of the coolant
					// readout and — worse — out of the pass that settles a stack, which is why water
					// pumped into the bottom of a tower never rose past the first block that happened to
					// hold rods. A column is part of the machine because it is in the room, not because
					// somebody has fuelled it yet.
					if (level.getBlockEntity(cursor.set(x, y, z))
							instanceof FuelRodAssemblyBlockEntity rack) {
						assemblies.add(cursor.immutable());
						rods += rack.getRods();
					} else if (level.getBlockEntity(cursor) instanceof ReactorOutletBlockEntity) {
						outlets.add(cursor.immutable());
					}
				}
			}
		}
		// Adjacency is NOT counted here any more (MOD-476). It used to be cached by this periodic scan
		// while the rods were counted every tick, so for up to reactorScanIntervalTicks after a column was
		// pulled the survivors went on being paid a neighbour bonus for a rack that was no longer there.
		// countNeighbourPairs does it per tick from the columns runReactor already has in hand.
	}

	/**
	 * The moment the last block goes in. A multiblock that silently starts working leaves the player
	 * wondering whether it did, so the completion gets its own cue — the same anvil-land the
	 * distillation column uses when its tower forms, plus a ring of sparkle over the shell.
	 */
	public static void announceAssembled(ServerLevel level, BlockPos pos, int repainted) {
		level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6f, 1.6f);
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
				12, 0.4, 0.4, 0.4, 0.02);
	}

	/**
	 * Marks the problem in the world. The screen names the offset, but a 14³ shell has 1016 cells and
	 * reading "4 east, 2 up" off a number line is not how anyone finds a missing block — walking to the
	 * smoke is.
	 *
	 * <p>Loud on purpose the first time: a room that just came apart plays a short alarm, because the
	 * player is usually looking somewhere else when a creeper opens their wall. Afterwards it is only
	 * the particles — repeating the sound every two seconds would turn a helpful cue into a nuisance.
	 */
	public static void markProblem(ServerLevel level, BlockPos where, boolean wasFormed) {
		// Smoke only. The angry-villager puffs that used to go with it read as cartoon clouds hanging
		// over the wall rather than as a fault marker (playtest, 2026-08-19).
		level.sendParticles(ParticleTypes.SMOKE,
				where.getX() + 0.5, where.getY() + 0.5, where.getZ() + 0.5,
				16, 0.3, 0.3, 0.3, 0.01);
		if (wasFormed) {
			level.playSound(null, where, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5f, 1.4f);
		}
	}

	// ── what the rest of the controller reads ──

	public ReactorRoomStatus status() {
		return status;
	}

	/** Whether the last scan found a sealed room. */
	public boolean isSealed() {
		return status == ReactorRoomStatus.FORMED;
	}

	public boolean isBare() {
		return bare;
	}

	public int rods() {
		return rods;
	}

	public List<BlockPos> assemblies() {
		return assemblies;
	}

	public List<BlockPos> outlets() {
		return outlets;
	}

	public List<BlockPos> bareRacks() {
		return bareRacks;
	}

	public List<BlockPos> bareShown() {
		return bareShown;
	}

	/** The box this controller last sealed. */
	public ReactorBox box() {
		return box;
	}

	/** What the last scan measured, as the console's channels carry it. */
	public ReactorRoomReadout readout() {
		return readout;
	}

}
