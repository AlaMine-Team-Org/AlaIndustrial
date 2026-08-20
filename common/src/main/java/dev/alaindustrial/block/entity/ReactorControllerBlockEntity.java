package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ReactorControllerBlock;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.core.structure.RoomScan;
import dev.alaindustrial.core.structure.RoomValidator;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The reactor controller's block entity (MOD-468, stage 1): it re-scans the room and publishes the
 * verdict to the screen.
 *
 * <p><b>Why it re-scans on a timer as well as on demand.</b> {@code neighborChanged} only fires for
 * the six blocks touching the controller, and a reactor room is up to 14 blocks across — a wall mined
 * on the far side, a door blown up by a creeper, a port pushed by a piston are all invisible to it. A
 * periodic sweep is the only way a controller notices its own room being taken apart, so the scan runs
 * every {@link Config#reactorScanIntervalTicks} even when nothing nearby changed.
 *
 * <p><b>Positions travel as offsets, not coordinates.</b> {@link ContainerData} ships each channel as a
 * <em>short</em>: an absolute block position (up to ±30 000 000) arrives on the client as garbage. The
 * breach is therefore sent relative to the controller — a range of at most ±14 by construction, which
 * fits with room to spare — and the screen phrases it as a direction ("4 blocks east, 2 up"). That is
 * also the more useful sentence: the player is standing at the controller when they read it.
 *
 * <p><b>Stage 1 has no energy.</b> The buffer is zero and the block is on
 * {@link dev.alaindustrial.registry.BlockCapabilityRoster#NO_ENERGY_CAPABILITY}, so nothing can wire
 * itself to a controller that cannot yet produce. It is still a {@link MachineBlockEntity} because the
 * menu-width sweep requires every machine menu to be backed by one.
 */
public class ReactorControllerBlockEntity extends MachineBlockEntity implements MenuProvider {

	/** Base four channels plus: status, breach offset (3), interior size (3), heat/rods/depth/output (4). */
	public static final int DATA_COUNT = MachineBlockEntity.DATA_COUNT + 17;
	public static final int DATA_STATUS = 4;
	public static final int DATA_BREACH_DX = 5;
	public static final int DATA_BREACH_DY = 6;
	public static final int DATA_BREACH_DZ = 7;
	public static final int DATA_SIZE_X = 8;
	public static final int DATA_SIZE_Y = 9;
	public static final int DATA_SIZE_Z = 10;
	public static final int DATA_HEAT_PERCENT = 11;
	public static final int DATA_RODS = 12;
	public static final int DATA_DEPTH_PERCENT = 13;
	public static final int DATA_OUTPUT = 14;
	/** Coolant left in the loop, as a percentage of what every column in the room could hold. */
	public static final int DATA_WATER_PERCENT = 15;
	/** Coolant boiled last tick, in mB — the loop's demand, and what an inlet has to keep up with. */
	public static final int DATA_WATER_RATE = 16;
	/**
	 * Steam waiting in the columns, as a percentage of what they can hold. Shown nowhere as a number
	 * — there is no room on the panel and it would be one gauge too many — but a full one is why a
	 * loop with plenty of water stops cooling, and the coolant bar changes colour on it. Without
	 * that, a blocked exhaust presents as a full tank next to a rising temperature, which reads as a
	 * bug rather than as a plumbing mistake.
	 */
	public static final int DATA_STEAM_PERCENT = 17;
	/** Ordinal of {@link ReactorIdleReason} — what the output row says instead of a bare dash. */
	public static final int DATA_IDLE_REASON = 18;
	/** Charge in the reactor's buffer, 0…100 — what the gauge fills to. */
	public static final int DATA_ENERGY_PERCENT = 19;
	/**
	 * Charge in HUNDREDS of EU, not in EU.
	 *
	 * <p>{@code ContainerData} is replicated as signed 16-bit shorts, and this buffer holds 200 000:
	 * sent raw it would arrive as a negative number and the readout would show nonsense at exactly the
	 * moment the reactor was doing well. Hundreds keep the whole range inside 2000, and a hundred EU is
	 * far below anything a player can read off a bar anyway.
	 */
	public static final int DATA_ENERGY_HUNDREDS = 20;

	/** Coolant boiled on the last tick, in mB. Zero while the reactor is cold or idle. */
	private int lastWater;

	/**
	 * Why the reactor produced nothing this tick, as an {@link ReactorIdleReason} ordinal.
	 *
	 * <p>A reactor that is built, sealed, fuelled and silent is the worst state this machine can be
	 * in: the panel used to print a dash for the output and leave the player to guess between a
	 * missing redstone signal, a closed throttle, an empty rack and a full buffer. Every one of those
	 * is a different fix.
	 */
	private int idleReason = ReactorIdleReason.RUNNING.ordinal();

	private ReactorRoomStatus status = ReactorRoomStatus.CONTROLLER_NOT_IN_WALL;
	private int breachDx;
	private int breachDy;
	private int breachDz;
	private int sizeX;
	private int sizeY;
	private int sizeZ;

	// ── stage 2: the reactor itself ──
	/** Heat on the 0…{@link Config#reactorHeatCapacity} scale. */
	private long heat;
	/** How deeply the control rods are lowered, 0…1000. The player's throttle. */
	private int depthPermille = ReactorCore.FULL_DEPTH;
	/** Rods burning across the room, refreshed each scan. */
	private int rods;
	/** Adjacent loaded-assembly pairs, refreshed each scan — the neighbour bonus. */
	private int neighbourPairs;
	/** What the last tick actually produced, for the readout. */
	private int lastOutput;
	/** Assemblies found inside the sealed room, refreshed on every scan. */
	private final List<BlockPos> assemblies = new ArrayList<>();

	/** Sockets set into this room's shell. Refreshed by the same sweep that finds the columns. */
	private final List<BlockPos> outlets = new ArrayList<>();

	/** Ticks until the next sweep. Zero means "scan on the next server tick". */
	private int scanCooldown;

	/**
	 * The box this controller last sealed, or an empty one if it never has.
	 *
	 * <p><b>This is what lets a room come apart.</b> A failed scan measures nothing, so a sweep driven
	 * by the scan result can only ever switch the shell ON — punch a hole in a finished room and every
	 * block stayed seamless and lit, which is precisely what the first playtest reported. Remembering
	 * the sealed box gives the controller something to clear.
	 *
	 * <p>Kept in NBT: a chunk can unload while the room is whole and reload after a creeper has opened
	 * it, and a controller that forgot its box on load would leave the shell stuck looking sealed.
	 */
	private int boxMinX;
	private int boxMinY;
	private int boxMinZ;
	private int boxMaxX = Integer.MIN_VALUE;
	private int boxMaxY = Integer.MIN_VALUE;
	private int boxMaxZ = Integer.MIN_VALUE;

	public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
		// Stage 2: a real HV producer. The buffer is sized to a few seconds of full output so a grid
		// that cannot take the power immediately does not stall the reactor mid-tick.
		super(ModContent.REACTOR_CONTROLLER_BE.get(), pos, state, EnergyTier.HV, 0,
				Config.reactorBuffer, 0L, EnergyTier.HV.maxVoltage());
	}

	/**
	 * No upgrade panel. {@link MachineBlockEntity}'s constructor appends four upgrade slots to every
	 * {@link MenuProvider} that says yes, and a controller with a hidden four-slot inventory would both
	 * accept hoppers and promise upgrades it does not have.
	 */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	/** Re-arms the scan for the next tick — called when a neighbour changes or the block is placed. */
	public void requestScan() {
		scanCooldown = 0;
		wake();
	}

	public ReactorRoomStatus getStatus() {
		return status;
	}

	/** Test seam: the interior box measured by the last scan, in blocks. */
	public int getInteriorSizeX() {
		return sizeX;
	}

	public int getInteriorSizeY() {
		return sizeY;
	}

	public int getInteriorSizeZ() {
		return sizeZ;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (scanCooldown > 0) {
			scanCooldown--;
		} else {
			scanCooldown = Config.reactorScanIntervalTicks;
			rescan(level, pos, state);
		}
		runReactor(level, pos);
		// Never sleep: the periodic sweep is the only thing that notices a room being dismantled out of
		// neighbour range, and heat has to keep bleeding away even with the reactor shut down (R-29).
		return 0;
	}

	/**
	 * One tick of the reactor.
	 *
	 * <p><b>Fuel burns only when the energy is wanted</b> — the player's own call for this stage. A
	 * full buffer with nothing drawing from it costs no uranium, exactly as the charging station spends
	 * only per transfer. Heat, by contrast, is settled every tick whether the reactor ran or not: a
	 * shut-down core still has to cool down, and "scram and wait" must actually work.
	 */
	private void runReactor(Level level, BlockPos pos) {
		boolean sealed = status == ReactorRoomStatus.FORMED;
		// No signal is the scram: a lever by the door stops the reaction without dismantling anything.
		boolean allowed = sealed && level.hasNeighborSignal(pos) && depthPermille > 0;
		long produced = 0;
		// Resolved ONCE per tick and handed to all three passes. Burning, boiling and levelling each
		// used to walk `assemblies` and call getBlockEntity themselves, which in a room packed to the
		// 12-block limit is several hundred chunk lookups a tick for a machine that ticks every tick.
		List<FuelRodAssemblyBlockEntity> columns = collectColumns(level);
		// Counted FRESH every tick, not taken from the periodic scan. `rods` is refreshed once every
		// reactorScanIntervalTicks, and output runs every tick — so a room whose last rod had just
		// burnt out went on making full power for up to two seconds, on nothing. Twenty thousand EU
		// out of thin air per refuelling, which is exactly the class of hole the fuel cycle closed
		// everywhere else.
		int liveRods = 0;
		for (FuelRodAssemblyBlockEntity column : columns) {
			liveRods += column.getRods();
		}

		if (allowed && liveRods > 0 && energy.getAmount() < energy.getCapacity()) {
			// What this core could give with the rods all the way down. The tier ceiling is applied to
			// THIS, and the throttle is applied after it — not the other way round. Clipping a
			// depth-scaled figure against the ceiling looked equivalent and was not: on any core whose
			// potential already cleared 512 EU/t every slider stop produced the same 512, so the control
			// the player was given did nothing at exactly the scale it was built for.
			long full = ReactorCore.output(liveRods, neighbourPairs, Config.reactorEuPerRod,
					Config.reactorNeighbourBonusPercent, ReactorCore.FULL_DEPTH);
			// Two ceilings: the tier's voltage (a reactor is an HV machine, and nothing in the mod could
			// carry tens of thousands of EU/t anyway) and whatever room is left in the buffer.
			long ceiling = Math.min(full, EnergyTier.HV.maxVoltage());
			long output = Math.min(ceiling * depthPermille / ReactorCore.FULL_DEPTH,
					energy.getCapacity() - energy.getAmount());
			if (output > 0) {
				energy.setAmountUntracked(energy.getAmount() + output);
				// Heat and fuel are both charged against the ENERGY produced, so neither can be knocked
				// out of range by a core that has left its own potential far behind. Heat is one
				// division against the potential at full depth; because both terms carry the same rods
				// and the same adjacencies, the ratio converges as the room grows instead of diverging.
				long heatFull = ReactorCore.heatProduced(liveRods, neighbourPairs, Config.reactorHeatPerRod,
						Config.reactorHeatNeighbourBonusPercent, ReactorCore.FULL_DEPTH);
				produced = ReactorCore.heatForOutput(heatFull, output, full);
				burnFuel(columns, output);
				lastOutput = (int) Math.min(Short.MAX_VALUE, output);
				idleReason = ReactorIdleReason.RUNNING.ordinal();
			} else {
				idleReason = ReactorIdleReason.BUFFER_FULL.ordinal();
			}
		} else {
			lastOutput = 0;
			idleReason = idleReasonFor(level, pos, sealed).ordinal();
		}

		long cooling = ReactorCore.naturalCooling(heat, Config.reactorPassiveCooling,
				Config.reactorHeatLossPermille);
		produced = coolWithWater(columns, produced, cooling);

		long settled = ReactorCore.settleHeat(heat, produced, cooling, Config.reactorHeatCapacity);
		if (settled != heat || produced > 0) {
			heat = settled;
			setChanged();
		}
		settleStacks(columns);
		feedOutlets(level);
	}

	/**
	 * Tops up the room's sockets from the reactor's buffer, round by round.
	 *
	 * <p>Even-handed rather than first-come: a room with two outlets on opposite walls must not have
	 * the one the scan happened to reach first starve the other whenever the buffer is short. Each
	 * round hands every socket an equal share of what is left, and rounds stop as soon as one changes
	 * nothing — which is also what makes a room whose sockets are all full cost a single pass.
	 */
	private void feedOutlets(Level level) {
		if (outlets.isEmpty() || energy.getAmount() <= 0) {
			return;
		}
		List<ReactorOutletBlockEntity> sockets = new ArrayList<>(outlets.size());
		for (BlockPos at : outlets) {
			if (level.getBlockEntity(at) instanceof ReactorOutletBlockEntity socket) {
				sockets.add(socket);
			}
		}
		while (!sockets.isEmpty() && energy.getAmount() > 0) {
			long share = Math.max(1, energy.getAmount() / sockets.size());
			long moved = 0;
			for (ReactorOutletBlockEntity socket : sockets) {
				if (energy.getAmount() <= 0) {
					break;
				}
				long taken = socket.fillFromReactor(Math.min(share, energy.getAmount()));
				energy.setAmountUntracked(energy.getAmount() - taken);
				moved += taken;
			}
			if (moved == 0) {
				break;
			}
		}
	}

	/**
	 * The live block entities behind {@link #assemblies}, in scan order.
	 *
	 * <p>Entries whose block entity has gone are simply absent: the list is rebuilt every tick, so a
	 * column mined mid-tick drops out immediately rather than waiting for the next room scan.
	 */
	private List<FuelRodAssemblyBlockEntity> collectColumns(Level level) {
		if (assemblies.isEmpty()) {
			return List.of();
		}
		List<FuelRodAssemblyBlockEntity> columns = new ArrayList<>(assemblies.size());
		for (BlockPos rack : assemblies) {
			if (level.getBlockEntity(rack) instanceof FuelRodAssemblyBlockEntity column) {
				columns.add(column);
			}
		}
		return columns;
	}

	/**
	 * Boils as much coolant as this tick's heat calls for and returns the heat left over.
	 *
	 * <p><b>A starter reactor needs no plumbing at all.</b> The shell sheds up to 84 heat a tick by
	 * itself (a flat 4 plus 8‰ of the current temperature), and anything under that simply settles at
	 * its own temperature: one column near 15% of the scale, two side by side near two thirds (dry —
	 * plumbed, they sit at the coolant target instead). The loop
	 * becomes necessary at the third column, which is exactly where the player should meet it. Nothing
	 * here forces plumbing early; scale does.
	 *
	 * <p>Whatever the columns could not boil stays in {@code produced} and goes on the heat scale. That
	 * is the entire failure mode of a starved loop: not an error message, a rising gauge.
	 */
	private long coolWithWater(List<FuelRodAssemblyBlockEntity> columns, long produced, long cooling) {
		// The coolant loop is a SAFETY system, not a radiator: it engages at its own target, which sits
		// DELIBERATELY BELOW the warning line, and takes only the heat that would carry the core past it.
		// Aiming at the warning line itself parked every healthy reactor on amber, so amber stopped
		// meaning "look at me". Below the target the shell sheds everything by itself, so a small reactor
		// runs warm, steady and dry — which is what makes the loop a thing the player builds when they
		// scale up rather than a tax on their first one.
		long coolantTarget = (long) Config.reactorHeatCapacity * Config.reactorCoolantTargetPercent / 100;
		// The INFLOW this tick, not the overshoot accumulated so far. Asking the loop to undo the whole
		// backlog in one tick demanded fifteen hundred millibuckets a tick and drained a flooded core in
		// eight of them — the loop looked useless precisely when it was needed. Countering the ongoing
		// gain instead holds the core at the coolant target and costs tens of millibuckets, which is a
		// rate a pipe can actually sustain.
		// Two terms: hold the line, then walk back to it. The first counters this tick's inflow, which
		// is what keeps a hot core from climbing. The second takes a twentieth of however far past the
		// target it already is, so a core that ran away before the loop was plumbed comes down over a few
		// seconds instead of sitting at the top forever — countering the inflow alone froze it exactly
		// where it was, which looks identical to a loop that does nothing.
		long excess = heat < coolantTarget ? 0 : (produced - cooling) + (heat - coolantTarget) / 20;
		if (excess <= 0 || columns.isEmpty()) {
			lastWater = 0;
			return produced;
		}
		long wanted = ReactorCore.waterForHeat(excess, Config.reactorHeatPerWater);
		long boiled = 0;
		for (FuelRodAssemblyBlockEntity column : columns) {
			if (boiled >= wanted) {
				break;
			}
			boiled += column.boil(wanted - boiled);
		}
		lastWater = (int) Math.min(Short.MAX_VALUE, boiled);
		// May go NEGATIVE, and must: the recovery term deliberately boils more than this tick's heat so a
		// core that ran away comes back down. Clamping the result at zero threw that surplus away — the
		// loop drank the water, the temperature did not move, and the coolant looked useless at exactly
		// the moment it was working hardest. settleHeat clamps the temperature at zero, which is the
		// right place for the floor.
		return produced - ReactorCore.heatRemovedByWater(boiled, Config.reactorHeatPerWater);
	}

	/**
	 * Settles fluid inside each vertical run of columns.
	 *
	 * <p><b>A stack is one vessel, and this is the rule that makes it look like one.</b> Columns joined
	 * top to bottom are already drawn as a single unbroken tower, so a pipe touching any block of that
	 * tower fills the whole tower — connecting to a five-block column at the floor and having only the
	 * bottom block fill would contradict what the player is looking at. Columns standing side by side
	 * are separate vessels and stay separate: coolant appearing in a tower nothing is plumbed to was
	 * the version before this one, and it read as a bug however convenient it was.
	 *
	 * <p><b>Water settles to the bottom, steam collects at the top</b> — filled from one end rather than
	 * shared out evenly, which is both what a liquid does and what makes the tower readable: a half-full
	 * stack shows a solid body of water with one surface, instead of the same puddle repeated in every
	 * block with a gap above each. It also puts the steam where the exhaust is, since only the topmost
	 * block of a stack has a free upper face to vent through.
	 */
	private void settleStacks(List<FuelRodAssemblyBlockEntity> columns) {
		if (columns.size() < 2) {
			return;
		}
		Map<Long, List<FuelRodAssemblyBlockEntity>> byColumn = new HashMap<>();
		for (FuelRodAssemblyBlockEntity column : columns) {
			BlockPos at = column.getBlockPos();
			// One key per (x, z): the vertical runs inside it are separated below, after sorting.
			byColumn.computeIfAbsent(((long) at.getX() << 32) ^ (at.getZ() & 0xFFFFFFFFL),
					key -> new ArrayList<>()).add(column);
		}
		for (List<FuelRodAssemblyBlockEntity> shaft : byColumn.values()) {
			shaft.sort(Comparator.comparingInt(column -> column.getBlockPos().getY()));
			int runStart = 0;
			for (int i = 1; i <= shaft.size(); i++) {
				boolean broken = i == shaft.size()
						|| shaft.get(i).getBlockPos().getY() != shaft.get(i - 1).getBlockPos().getY() + 1;
				if (broken) {
					settleRun(shaft.subList(runStart, i));
					runStart = i;
				}
			}
		}
	}

	/** One unbroken tower: water poured in at the bottom, steam pushed up to the top. */
	private static void settleRun(List<FuelRodAssemblyBlockEntity> run) {
		if (run.size() < 2) {
			return;
		}
		long water = 0;
		long steam = 0;
		for (FuelRodAssemblyBlockEntity column : run) {
			water += column.waterTank.amount;
			steam += column.steamTank.amount;
		}
		for (int i = 0; i < run.size(); i++) {
			FuelRodAssemblyBlockEntity column = run.get(i);
			long take = Math.min(water, column.waterTank.capacity);
			column.setTank(true, take);
			water -= take;
		}
		for (int i = run.size() - 1; i >= 0; i--) {
			FuelRodAssemblyBlockEntity column = run.get(i);
			long take = Math.min(steam, column.steamTank.capacity);
			column.setTank(false, take);
			steam -= take;
		}
	}

	/**
	 * Charges this tick's output against the rods, split across the columns in proportion to how many
	 * each holds.
	 *
	 * <p>Proportional rather than one column at a time: the racks are one core, and draining them in
	 * scan order would empty the corner the scan happens to start from while the rest sat full. The
	 * remainder of the division goes to the first column with fuel, so a room whose rod count does not
	 * divide the output evenly still pays for every EU it made.
	 */
	private void burnFuel(List<FuelRodAssemblyBlockEntity> columns, long output) {
		int total = 0;
		for (FuelRodAssemblyBlockEntity column : columns) {
			total += column.getRods();
		}
		if (total <= 0 || output <= 0) {
			return;
		}
		long handed = 0;
		for (FuelRodAssemblyBlockEntity column : columns) {
			long share = output * column.getRods() / total;
			if (share > 0) {
				column.burn(share);
				handed += share;
			}
		}
		if (handed < output) {
			for (FuelRodAssemblyBlockEntity column : columns) {
				if (column.getRods() > 0) {
					column.burn(output - handed);
					break;
				}
			}
		}
	}

	/** Reads the state once and names the first thing standing between the reactor and running. */
	private ReactorIdleReason idleReasonFor(Level level, BlockPos pos, boolean sealed) {
		if (!sealed) {
			return ReactorIdleReason.NOT_SEALED;
		}
		if (rods <= 0) {
			return ReactorIdleReason.NO_FUEL;
		}
		if (depthPermille <= 0) {
			return ReactorIdleReason.RODS_WITHDRAWN;
		}
		if (!level.hasNeighborSignal(pos)) {
			return ReactorIdleReason.NO_SIGNAL;
		}
		if (energy.getAmount() >= energy.getCapacity()) {
			return ReactorIdleReason.BUFFER_FULL;
		}
		return ReactorIdleReason.RUNNING;
	}

	private int steamPercent() {
		return tankPercent(false);
	}

	private int waterPercent() {
		return tankPercent(true);
	}

	/**
	 * Coolant or steam in the room as a percentage of every column's capacity together.
	 *
	 * <p>Rounded to NEAREST, not truncated. The pipe network stops moving fluid once the imbalance
	 * between a segment and its neighbour is down to a single millibucket, so a loop that is genuinely
	 * full parks a few mB short of capacity — and truncation reported that as 99% forever. A readout
	 * that can never reach its own maximum reads as broken, and here it would be.
	 */
	private int tankPercent(boolean water) {
		if (level == null || assemblies.isEmpty()) {
			return 0;
		}
		long held = 0;
		long capacity = 0;
		for (BlockPos rack : assemblies) {
			if (level.getBlockEntity(rack) instanceof FuelRodAssemblyBlockEntity column) {
				held += water ? column.waterTank.amount : column.steamTank.amount;
				capacity += water ? column.waterTank.capacity : column.steamTank.capacity;
			}
		}
		if (capacity <= 0) {
			return 0;
		}
		return (int) Math.min(100, (held * 200 + capacity) / (capacity * 2));
	}

	private void rescan(Level level, BlockPos pos, BlockState state) {
		RoomScan.Result result = RoomValidator.scan(level, pos, state.getValue(ReactorControllerBlock.FACING),
				Config.reactorRoomMinInner, Config.reactorRoomMaxInner, Config.reactorRoomMaxGlassPercent);
		ReactorRoomStatus scanned = ReactorRoomStatus.of(result.status());

		breachDx = result.x() - pos.getX();
		breachDy = result.y() - pos.getY();
		breachDz = result.z() - pos.getZ();
		boolean measured = scanned.hasSize();
		sizeX = measured ? result.sizeX() : 0;
		sizeY = measured ? result.sizeY() : 0;
		sizeZ = measured ? result.sizeZ() : 0;

		boolean wasFormed = state.getValue(ReactorControllerBlock.FORMED);
		boolean changed = scanned != status;
		status = scanned;

		// The whole shell wears the flag, not just this block: that is what makes a finished room read
		// as one surface instead of a stack of crates, and what lights its lamps.
		int repainted;
		if (result.formed()) {
			repainted = RoomValidator.applyFormed(level, result.minX(), result.minY(), result.minZ(),
					result.maxX(), result.maxY(), result.maxZ(), true);
			rememberBox(result);
		} else {
			// Clear the box we last sealed — not the one this scan measured, which is empty. Without
			// this the shell would stay seamless and lit around a hole (playtest, 2026-08-19).
			repainted = clearRememberedBox(level);
		}
		if (wasFormed != result.formed()) {
			level.setBlock(pos, state.setValue(ReactorControllerBlock.FORMED, result.formed()), 3);
		}
		if (changed) {
			setChanged();
			syncBlockEntityToClient();
		}
		if (result.formed()) {
			collectAssemblies(level, result);
		} else {
			assemblies.clear();
			outlets.clear();
			rods = 0;
			neighbourPairs = 0;
		}

		if (level instanceof ServerLevel serverLevel) {
			if (result.formed() && !wasFormed) {
				announceAssembled(serverLevel, pos, repainted);
			} else if (!result.formed() && scanned.hasLocation()) {
				markProblem(serverLevel, new BlockPos(result.x(), result.y(), result.z()), wasFormed);
			}
		}
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
		neighbourPairs = 0;
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
		// Adjacency is about FUEL, not about racks: two empty columns side by side breed nothing, and
		// counting them would pay a neighbour bonus for scaffolding. Collected separately from the list
		// above now that the list holds empty columns too.
		java.util.Set<BlockPos> loaded = new java.util.HashSet<>();
		for (BlockPos rack : assemblies) {
			if (level.getBlockEntity(rack) instanceof FuelRodAssemblyBlockEntity be && be.hasFuel()) {
				loaded.add(rack);
			}
		}
		for (BlockPos rack : loaded) {
			if (loaded.contains(rack.east())) {
				neighbourPairs++;
			}
			if (loaded.contains(rack.above())) {
				neighbourPairs++;
			}
			if (loaded.contains(rack.south())) {
				neighbourPairs++;
			}
		}
	}

	private void rememberBox(RoomScan.Result result) {
		boxMinX = result.minX();
		boxMinY = result.minY();
		boxMinZ = result.minZ();
		boxMaxX = result.maxX();
		boxMaxY = result.maxY();
		boxMaxZ = result.maxZ();
		setChanged();
	}

	/** Switches the last sealed shell back to its unbuilt look and puts its lights out. */
	/**
	 * Hands the shell back its ordinary look when the controller is mined.
	 *
	 * <p>Without this a room stayed visually sealed forever: the seamless art, the interior light and
	 * the {@code formed} flag are painted by the controller, so removing the one block that maintains
	 * them left a structure that looked assembled and was not, with no way to un-form it short of
	 * breaking a wall.
	 *
	 * <p><b>This has now been in two wrong places, and the second one was worse than the first.</b>
	 * The block's {@code affectNeighborsAfterRemoval} runs after the block entity has been detached, so
	 * the lookup inside it finds nothing and the room simply stays formed. Moving it to
	 * {@code BlockEntity.setRemoved} fixed that and introduced a hang: {@code setRemoved} is also how
	 * every block entity in a chunk is told the chunk is going away, and reading a block state from
	 * inside that teardown makes the server thread wait on the very chunk operation it is running. The
	 * symptom was a client that stopped dead at "Saving worlds" and had to be killed by the shutdown
	 * watchdog.
	 *
	 * <p>So it lives on the player's own removal path, which is the only one where anything is safe to
	 * touch: the block entity is still attached, the chunk is not being torn down, and nothing here can
	 * re-enter the world. A controller taken out by an explosion or a piston leaves the shell painted —
	 * a cosmetic loose end, and a far better failure than a world that will not save.
	 */
	public void unformOnRemoval(Level level) {
		clearRememberedBox(level);
	}

	private int clearRememberedBox(Level level) {
		if (boxMaxX == Integer.MIN_VALUE) {
			return 0;
		}
		int changed = RoomValidator.applyFormed(level, boxMinX, boxMinY, boxMinZ,
				boxMaxX, boxMaxY, boxMaxZ, false);
		boxMaxX = Integer.MIN_VALUE;
		boxMaxY = Integer.MIN_VALUE;
		boxMaxZ = Integer.MIN_VALUE;
		setChanged();
		return changed;
	}

	@Override
	protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("BoxMinX", boxMinX);
		output.putInt("BoxMinY", boxMinY);
		output.putInt("BoxMinZ", boxMinZ);
		output.putInt("BoxMaxX", boxMaxX);
		output.putInt("BoxMaxY", boxMaxY);
		output.putInt("BoxMaxZ", boxMaxZ);
		output.putLong("Heat", heat);
		output.putInt("Depth", depthPermille);
	}

	@Override
	protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
		super.loadAdditional(input);
		boxMinX = input.getIntOr("BoxMinX", 0);
		boxMinY = input.getIntOr("BoxMinY", 0);
		boxMinZ = input.getIntOr("BoxMinZ", 0);
		boxMaxX = input.getIntOr("BoxMaxX", Integer.MIN_VALUE);
		boxMaxY = input.getIntOr("BoxMaxY", Integer.MIN_VALUE);
		boxMaxZ = input.getIntOr("BoxMaxZ", Integer.MIN_VALUE);
		heat = input.getLongOr("Heat", 0L);
		depthPermille = input.getIntOr("Depth", ReactorCore.FULL_DEPTH);
	}

	/**
	 * The moment the last block goes in. A multiblock that silently starts working leaves the player
	 * wondering whether it did, so the completion gets its own cue — the same anvil-land the
	 * distillation column uses when its tower forms, plus a ring of sparkle over the shell.
	 */
	private void announceAssembled(ServerLevel level, BlockPos pos, int repainted) {
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
	private void markProblem(ServerLevel level, BlockPos where, boolean wasFormed) {
		// Smoke only. The angry-villager puffs that used to go with it read as cartoon clouds hanging
		// over the wall rather than as a fault marker (playtest, 2026-08-19).
		level.sendParticles(ParticleTypes.SMOKE,
				where.getX() + 0.5, where.getY() + 0.5, where.getZ() + 0.5,
				16, 0.3, 0.3, 0.3, 0.01);
		if (wasFormed) {
			level.playSound(null, where, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5f, 1.4f);
		}
	}

	private final ContainerData controllerData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_STATUS -> status.ordinal();
				case DATA_BREACH_DX -> breachDx;
				case DATA_BREACH_DY -> breachDy;
				case DATA_BREACH_DZ -> breachDz;
				case DATA_SIZE_X -> sizeX;
				case DATA_SIZE_Y -> sizeY;
				case DATA_SIZE_Z -> sizeZ;
				case DATA_HEAT_PERCENT -> ReactorCore.heatPercent(heat, Config.reactorHeatCapacity);
				case DATA_RODS -> rods;
				case DATA_DEPTH_PERCENT -> depthPermille / 10;
				case DATA_OUTPUT -> lastOutput;
				case DATA_WATER_PERCENT -> waterPercent();
				case DATA_WATER_RATE -> lastWater;
				case DATA_STEAM_PERCENT -> steamPercent();
				case DATA_IDLE_REASON -> idleReason;
				case DATA_ENERGY_PERCENT -> energy.getCapacity() <= 0 ? 0
						: (int) Math.min(100, energy.getAmount() * 100 / energy.getCapacity());
				case DATA_ENERGY_HUNDREDS -> (int) Math.min(Short.MAX_VALUE, energy.getAmount() / 100);
				default -> ReactorControllerBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			// Every readout channel is derived from the scan and server-authoritative.
			if (index < DATA_STATUS) {
				ReactorControllerBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return controllerData;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.reactor_controller");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ReactorControllerMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	/**
	 * The <em>verdict</em> is not stored: the room is the save, and the first tick after load re-derives
	 * the status from whatever the shell actually looks like — persisting "formed" would let a stale yes
	 * survive a world edit that took the walls away.
	 *
	 * <p>The sealed box itself IS stored (see {@code saveAdditional}), and for the opposite reason: it
	 * is not a verdict but the address of the shell this controller must be able to switch back off. A
	 * chunk can unload while the room is whole and reload after a creeper has opened it, and a
	 * controller that forgot its box would leave that shell stuck looking sealed for good.
	 */
	@Override
	public boolean tracksOwner() {
		return true;
	}

	/**
	 * Every face but the screen pushes power out. The front carries the panel the player reads, and
	 * R-NRG-03 keeps it energy-inert so a cable never draws an arm across the interface.
	 */
	@Override
	public dev.alaindustrial.core.energy.EnergyRole energyRoleForFace(Direction worldFace) {
		// The controller is the panel, not the socket: power leaves the room through
		// {@code reactor_outlet} blocks set into the shell, which this block tops up every tick. Its
		// own faces are almost all unreachable anyway — four are buried in the wall and one opens into
		// a sealed room — so publishing them would promise a connection a player cannot make.
		return worldFace == getBlockState().getValue(ReactorControllerBlock.FACING)
				? dev.alaindustrial.core.energy.EnergyRole.NONE
				: dev.alaindustrial.core.energy.EnergyRole.OUT;
	}

	/** Control-rod depth, 0…1000. */
	public int getDepthPermille() {
		return depthPermille;
	}

	/** Moves the throttle. Called from the menu's button handler, clamped here rather than there. */
	public void setDepthPermille(int value) {
		int clamped = Math.min(ReactorCore.FULL_DEPTH, Math.max(0, value));
		if (clamped != depthPermille) {
			depthPermille = clamped;
			setChanged();
			syncBlockEntityToClient();
			wake();
		}
	}

	/** EU produced on the last tick. Zero while idle; {@link #getIdleReason()} then says why. */
	public int getLastOutput() {
		return lastOutput;
	}

	/** Rods racked across the whole room, as the last scan counted them. */
	public int getRods() {
		return rods;
	}

	/** Why the reactor produced nothing, or {@code RUNNING}. */
	public ReactorIdleReason getIdleReason() {
		return ReactorIdleReason.byOrdinal(idleReason);
	}

	public long getHeat() {
		return heat;
	}

	public int getRodCount() {
		return rods;
	}
}
