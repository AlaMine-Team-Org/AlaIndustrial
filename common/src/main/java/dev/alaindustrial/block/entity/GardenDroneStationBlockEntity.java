package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.crop.CropMaturity;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.GardenDroneStationMenu;
import dev.alaindustrial.registry.ModTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The Garden Drone Station (MOD-277): a dock block whose drone tends the farmland around it —
 * tilling, planting, fertilizing and harvesting, one tile per working tick.
 *
 * <p>The drone itself is <b>not an entity</b>. It is drawn by this block entity's renderer, so the
 * server-side state below (target position + phase) is the whole drone: it cannot be lost, killed,
 * duplicated, or stranded in an unloaded chunk. See the OKF spec for why that beats a real entity
 * here.
 *
 * <p>Three things are worth knowing before changing this class:
 * <ul>
 * <li><b>EU is charged per completed action, never per tick.</b> An idle station (nothing to do, or
 *     out of seeds) spends nothing and sleeps, like {@link ElectricHeaterBlockEntity} — a station
 *     that drew power while idle would poison the network's flow field (MOD-214).</li>
 * <li><b>The harvest never spawns an {@code ItemEntity}.</b> Drops are computed server-side and
 *     inserted straight into the output slots; the crop block is only removed once every stack has
 *     landed. A full output means the crop simply stays in the ground.</li>
 * <li><b>The zone is cached.</b> A full re-scan of a radius-4 zone is ~240 positions; doing that
 *     every tick on a large farm is exactly the kind of thing that eats a server. The cache holds
 *     the tiles worth looking at and is rebuilt on an interval (player edits) or invalidated in
 *     place when the station itself changes a tile.</li>
 * </ul>
 */
public final class GardenDroneStationBlockEntity extends MachineBlockEntity implements MenuProvider {

	public static final int SEED_SLOT = 0;
	public static final int FERTILIZER_SLOT = 1;
	/** First of the four output slots; a crop can drop more than one item type (wheat + seeds). */
	public static final int OUTPUT_SLOT_START = 2;
	public static final int OUTPUT_SLOT_COUNT = 4;
	/**
	 * The drone itself, docked. The station is the base station and nothing more — without a drone in
	 * this slot it has nothing to send out, exactly like a wind mill without its rotor. Appended after
	 * the outputs rather than inserted at 0 so existing slot indices (and their tests) keep their
	 * meaning.
	 */
	public static final int DRONE_SLOT = OUTPUT_SLOT_START + OUTPUT_SLOT_COUNT;
	/**
	 * The hoe the drone tills with. Tilling is the one action that is a <em>tool</em> job rather than a
	 * consumable one, so it costs the hoe durability exactly as it would in the player's hand — a farm
	 * that expands itself for free would make the hoe pointless.
	 */
	public static final int HOE_SLOT = DRONE_SLOT + 1;
	public static final int SLOT_COUNT = HOE_SLOT + 1;

	/** Extra {@link ContainerData} channel: what the station is doing, for the screen's status line. */
	public static final int DATA_STATUS = 4;
	/**
	 * Number of sync channels, stated once (MOD-235). The client menu stub sizes its
	 * {@code SimpleContainerData} from this same constant, so the two cannot drift apart.
	 */
	public static final int DATA_COUNT = 5;

	/**
	 * Shortest a leg can be, in ticks. Distance alone is not enough: the tile next to the station is a
	 * fraction of a block away, and the drone would cover it faster than the eye can follow.
	 *
	 * <p>Public so the gametests can time a job off the same number instead of copying it — the literal
	 * lived in both places once, and raising it here silently broke every scenario that drove a fixed
	 * count of ticks.
	 */
	public static final int MIN_FLIGHT_TICKS = 26;

	/**
	 * How long the drone stands on the tile it has just worked before it flies home, in ticks. Without
	 * it the action landed and the return leg began in the same tick: the drone touched down and lifted
	 * off inside a single frame, which read as a bounce rather than as a machine doing a job (MOD-317).
	 *
	 * <p>Deliberately only at the far end. A matching pause on the pad would push back the tick the
	 * action lands on, and that tick is what every scenario in the drone suite counts to — the beat the
	 * player actually reads as "it is working" is the one out in the field, so the dock gets nothing.
	 *
	 * <p>Public so a gametest can time the return leg off the same number instead of copying it, for
	 * the same reason {@link #MIN_FLIGHT_TICKS} is.
	 */
	public static final int LANDING_PAUSE_TICKS = 6;

	/**
	 * How many of the nearest candidate tiles the drone picks its next errand from at random. One would
	 * be strictly-nearest (and visibly robotic); the whole zone would be aimless wandering. A handful
	 * keeps the work local while making the route look like a decision rather than a sort order.
	 */
	private static final int TARGET_CHOICE_POOL = 3;

	/** Vertical span of the scan, relative to the station: one below through one above. */
	private static final int SCAN_Y_BELOW = 1;
	private static final int SCAN_Y_ABOVE = 1;

	/** What the station is currently able to do — drives the status line and the drone's indicator. */
	private GardenDroneStatus status = GardenDroneStatus.IDLE;

	/**
	 * Tiles worth looking at, from the last full scan. Holding positions (not resolved actions) is
	 * deliberate: a position stays valid across ticks, while an action decided a second ago may not be
	 * (the player could have harvested the crop by hand). Every entry is re-validated before use.
	 */
	private final List<BlockPos> zoneCache = new ArrayList<>();
	/** Ticks until the cache is rebuilt from scratch; also set to 0 whenever it is invalidated. */
	private int rescanCountdown;

	/** The tile the drone is currently flying to, or {@code null} when it is parked. Synced for the BER. */
	@Nullable
	private BlockPos droneTarget;
	/** The action waiting to land when the drone arrives; meaningless while {@link #droneTarget} is null. */
	@Nullable
	private GardenDroneAction droneJob;
	/**
	 * Ticks left on the current leg. This — not the world clock — is what decides when the action
	 * lands: a block entity that timed itself off {@code getGameTime()} would stall the moment its tick
	 * is driven without the world advancing (every gametest does exactly that) and would also lurch if
	 * a player ran {@code /time set}. The counter is the authority.
	 */
	private int flightRemaining;
	/**
	 * World time the current leg started, kept <b>only</b> so the renderer can interpolate. The client
	 * gets the leg once and works out where the drone is from its own clock, which is what keeps the
	 * flight smooth with no per-tick packets. Server logic never reads this.
	 */
	private long flightStart;
	/** Length of the current leg in ticks. */
	private int flightTotal;
	/** True while the drone is on its way home after the action landed. */
	private boolean returning;

	public GardenDroneStationBlockEntity(BlockPos pos, BlockState state) {
		super(dev.alaindustrial.registry.ModContent.GARDEN_DRONE_STATION_BE.get(), pos, state,
				EnergyTier.LV, SLOT_COUNT, Config.gardenDroneBuffer,
				EnergyTier.LV.maxVoltage(), 0);
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return IDLE_SLEEP_TICKS;
		}
		if (rescanCountdown > 0) {
			rescanCountdown--;
		}
		if (rescanCountdown <= 0) {
			rebuildZoneCache(serverLevel, pos);
			rescanCountdown = Math.max(1, Config.gardenDroneScanIntervalTicks);
		}
		// No drone, no work. The dock keeps its inventory and buffer, it just has nothing to fly.
		if (!hasDrone()) {
			return setStatus(GardenDroneStatus.NO_DRONE);
		}
		// Energy is checked before any world work: a station without the price of one action has
		// nothing to do this tick, and saying so costs one comparison instead of a zone walk.
		if (energy.getAmount() < Config.gardenDroneEuPerAction) {
			return setStatus(GardenDroneStatus.NO_ENERGY);
		}
		return performOneAction(serverLevel, pos);
	}

	/**
	 * Walks the cached tiles from the round-robin cursor and performs the first action that applies,
	 * in the spec's priority order (harvest before plant before fertilize before till). Returns the
	 * number of ticks the station may sleep.
	 */
	private int performOneAction(ServerLevel level, BlockPos stationPos) {
		// A leg already in the air: the action lands only when the drone actually arrives, and after it
		// the drone flies home. Without both legs the station tends a farm in a couple of ticks and the
		// drone appears welded to its dock.
		if (droneTarget != null) {
			if (flightRemaining > 0) {
				flightRemaining--;
				return 0; // still flying
			}
			if (returning) {
				clearJob();
			} else {
				// Arrived. The world may have changed during the flight (the player harvested it by hand,
				// a piston moved it), so the job is re-validated rather than trusted.
				if (droneJob != null && level.isLoaded(droneTarget)
						&& applies(level, droneJob, droneTarget)
						&& apply(level, droneJob, droneTarget)) {
					energy.drainInternal(Config.gardenDroneEuPerAction);
					creditUsefulWork(level, Config.gardenDroneEuPerAction);
				}
				// Head home along the same leg length, so the return reads as the same flight reversed —
				// but stand on the tile for a beat first. Starting the leg in the FUTURE is the whole
				// mechanism: flightProgress() clamps a negative elapsed time to 0, so for those ticks the
				// renderer holds the drone exactly where it landed (travel 1, arc 0) with no new field to
				// persist or sync. The countdown carries the pause as well as the leg, so a chunk that
				// unloads mid-pause resumes it on load rather than landing the action a second time —
				// which a separate, unsaved timer would have done (MOD-317).
				returning = true;
				droneJob = null;
				flightRemaining = flightTotal + LANDING_PAUSE_TICKS;
				flightStart = level.getGameTime() + LANDING_PAUSE_TICKS;
			}
			setChangedQuietly();
			syncBlockEntityToClient();
			return 0;
		}
		if (zoneCache.isEmpty()) {
			return setStatus(GardenDroneStatus.IDLE);
		}
		// Priority is a property of the whole zone, not of a single tile: the drone must harvest a ripe
		// crop anywhere in range before it tills dirt next door. So each pass looks for one action kind
		// at a time, in order, rather than taking whatever the first tile happens to offer.
		for (GardenDroneAction action : GardenDroneAction.PRIORITY_ORDER) {
			BlockPos target = findTarget(level, action);
			if (target == null) {
				continue;
			}
			startJob(level, action, target, stationPos);
			setChangedQuietly();
			syncBlockEntityToClient();
			return setStatus(GardenDroneStatus.WORKING);
		}
		clearJob();
		// Distinguish "nothing left to do" from "I would work but the hoppers are empty", so the player
		// can tell a finished farm from a stalled one without opening the GUI.
		return setStatus(needsResources() ? GardenDroneStatus.NO_RESOURCES : GardenDroneStatus.IDLE);
	}

	/** Sends the drone out: records the job and how long the flight takes at the configured speed. */
	private void startJob(ServerLevel level, GardenDroneAction action, BlockPos target, BlockPos stationPos) {
		droneJob = action;
		droneTarget = target;
		returning = false;
		double distance = Math.sqrt(target.distSqr(stationPos));
		// The floor matters as much as the rate: an adjacent tile is a fraction of a block away, and
		// without a minimum the drone would blink there and back before the eye caught it.
		flightTotal = Math.max(MIN_FLIGHT_TICKS,
				(int) Math.round(distance * Config.gardenDroneFlightTicksPerBlock));
		flightRemaining = flightTotal;
		flightStart = level.getGameTime();
	}

	/** Parks the drone: no job, no target, nothing for the renderer to fly. */
	private void clearJob() {
		droneJob = null;
		droneTarget = null;
		returning = false;
		flightRemaining = 0;
		flightTotal = 0;
		flightStart = 0L;
	}

	/**
	 * One tile the given action applies to, drawn from the closest handful; {@code null} if none does.
	 *
	 * <p>Strictly-nearest was the first attempt and it looked like a script: with a settled farm the
	 * same tile wins every comparison, so the drone flew the identical errand over and over. Pure
	 * random looked worse — the drone wandered across the field past work at its feet. So: rank the
	 * candidates by distance, keep the closest {@link #TARGET_CHOICE_POOL}, and pick one of those at
	 * random. Near work still gets done first, but the route stops being predictable.
	 *
	 * <p>The pool is kept as a small insertion-sorted array rather than a sort of the whole zone —
	 * this runs inside the machine tick, and the zone can hold a few hundred tiles.
	 */
	@Nullable
	private BlockPos findTarget(ServerLevel level, GardenDroneAction action) {
		BlockPos[] best = new BlockPos[TARGET_CHOICE_POOL];
		double[] bestDistance = new double[TARGET_CHOICE_POOL];
		java.util.Arrays.fill(bestDistance, Double.MAX_VALUE);
		int found = 0;
		BlockPos station = getBlockPos();

		for (BlockPos candidate : zoneCache) {
			// R-277: never touch a position in an unloaded chunk — reading it would force a synchronous
			// chunk load on every scan (the lesson StockDisplayFrameEntity left behind).
			if (!level.isLoaded(candidate)) {
				continue;
			}
			double distance = candidate.distSqr(station);
			if (distance >= bestDistance[TARGET_CHOICE_POOL - 1]) {
				continue; // the pool is already full of closer tiles; skip the expensive check
			}
			if (!applies(level, action, candidate)) {
				continue;
			}
			// Insertion sort into the pool, dropping whatever fell off the end.
			int slot = TARGET_CHOICE_POOL - 1;
			while (slot > 0 && bestDistance[slot - 1] > distance) {
				bestDistance[slot] = bestDistance[slot - 1];
				best[slot] = best[slot - 1];
				slot--;
			}
			bestDistance[slot] = distance;
			best[slot] = candidate;
			found = Math.min(TARGET_CHOICE_POOL, found + 1);
		}
		if (found == 0) {
			return null;
		}
		return best[level.getRandom().nextInt(found)];
	}

	/** Whether {@code action} is currently possible at {@code target}, resources included. */
	private boolean applies(ServerLevel level, GardenDroneAction action, BlockPos target) {
		BlockState state = level.getBlockState(target);
		return switch (action) {
			// Harvesting wears the hoe exactly as tilling does, so it needs one for the same reason:
			// a tool that is consumed by an action but not required for it makes no sense to the player.
			case HARVEST -> hasUsableHoe() && CropMaturity.isHarvestable(level, target, state);
			case PLANT -> state.isAir()
					&& level.getBlockState(target.below()).is(Blocks.FARMLAND)
					&& seedBlock() != null;
			case FERTILIZE -> !items.get(FERTILIZER_SLOT).isEmpty()
					&& CropMaturity.isFertilizable(level, target, state)
					&& state.getBlock() instanceof BonemealableBlock bonemealable
					&& bonemealable.isValidBonemealTarget(level, target, state);
			case TILL -> isTillable(state)
					&& level.getBlockState(target.above()).isAir()
					&& hasUsableHoe();
		};
	}

	/** Performs {@code action} at {@code target}; {@code false} means it could not be completed. */
	private boolean apply(ServerLevel level, GardenDroneAction action, BlockPos target) {
		BlockState state = level.getBlockState(target);
		return switch (action) {
			case HARVEST -> harvest(level, target, state);
			case PLANT -> plant(level, target);
			case FERTILIZE -> fertilize(level, target, state);
			case TILL -> till(level, target);
		};
	}

	// ---------------------------------------------------------------- the four actions

	/**
	 * Takes a ripe crop without a player and without dropping anything in the world.
	 *
	 * <p>{@code Block.getDrops} computes the loot table's output and touches nothing else — unlike
	 * {@code Block.dropResources}, whose sibling name hides a {@code popResource} call that spawns real
	 * {@link net.minecraft.world.entity.item.ItemEntity}s. The drop is inserted two-pass: every stack is
	 * checked for room first, and the crop block is removed only once all of them have landed. A full
	 * output therefore leaves the crop standing rather than voiding it — the anti-dupe / anti-loss
	 * contract this whole class is built around.
	 */
	private boolean harvest(ServerLevel level, BlockPos target, BlockState state) {
		List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target));
		if (!insertAll(drops, true)) {
			return false; // no room — leave the crop in the ground, spend nothing
		}
		insertAll(drops, false);
		if (!level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState())) {
			return false;
		}
		wearHoe(level);
		return true;
	}

	/** Plants one seed from the seed slot onto the tilled tile below {@code target}. */
	private boolean plant(ServerLevel level, BlockPos target) {
		Block crop = seedBlock();
		if (crop == null) {
			return false;
		}
		BlockState planted = crop.defaultBlockState();
		if (!planted.canSurvive(level, target)) {
			return false;
		}
		if (!level.setBlockAndUpdate(target, planted)) {
			return false;
		}
		items.get(SEED_SLOT).shrink(1);
		return true;
	}

	/**
	 * Applies one bone meal to an immature crop through vanilla's own {@link BonemealableBlock} path —
	 * the same three calls the item makes, minus the player and the particles. The fertilizer is spent
	 * even when {@code isBonemealSuccess} rolls false, exactly like hand-applied bone meal.
	 */
	private boolean fertilize(ServerLevel level, BlockPos target, BlockState state) {
		if (!(state.getBlock() instanceof BonemealableBlock bonemealable)) {
			return false;
		}
		RandomSource random = level.getRandom();
		if (bonemealable.isBonemealSuccess(level, random, target, state)) {
			bonemealable.performBonemeal(level, random, target, state);
		}
		items.get(FERTILIZER_SLOT).shrink(1);
		return true;
	}

	/** Turns ground into farmland and wears the hoe by one point, breaking it when it runs out. */
	private boolean till(ServerLevel level, BlockPos target) {
		if (!level.setBlockAndUpdate(target, Blocks.FARMLAND.defaultBlockState())) {
			return false;
		}
		// The same feedback a hoe gives in the player's hand, on the tile that was actually turned
		// (MOD-328). Parameters are vanilla's own, from HoeItem: BLOCKS, volume and pitch 1, no jitter.
		// The listener argument is null rather than a player: vanilla passes the player so they are
		// EXCLUDED from the packet, having already predicted the sound client-side — a block entity
		// predicts nothing, so excluding anyone would silence it for whoever stood closest.
		level.playSound(null, target, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
		wearHoe(level);
		return true;
	}

	/**
	 * Spends one point of the loaded hoe's durability, emptying the slot when it is used up.
	 *
	 * <p>The wear goes through vanilla's own {@code hurtAndBreak} rather than a hand-rolled counter.
	 * The overload taking a {@code ServerPlayer} accepts {@code null}, so a machine can use it without
	 * inventing a fake entity — and that matters for more than tidiness: vanilla routes the damage
	 * through {@code EnchantmentHelper.processDurabilityChange}, so a counter of its own would silently
	 * ignore Unbreaking and burn an enchanted hoe about four times too fast. It also owns the break
	 * threshold, which is what a duplicated one got wrong before (MOD-319): the guard refused the last
	 * use the consumer needed to break the tool, so the hoe froze one point short of breaking and
	 * blocked the slot against hopper refills forever.
	 */
	private void wearHoe(ServerLevel level) {
		ItemStack hoe = items.get(HOE_SLOT);
		if (hoe.isEmpty() || !hoe.isDamageableItem()) {
			return;
		}
		// Snapshot before the hit: on the last point hurtAndBreak empties the stack, and the effect needs
		// the hoe's own components (a modded hoe may carry its own break sound) rather than a bare Item.
		ItemStack broken = hoe.copy();
		hoe.hurtAndBreak(1, level, null, item -> playBreakEffect(level, broken));
		if (hoe.isEmpty()) {
			// hurtAndBreak shrinks the stack to a count of 0; normalise so the slot reads as truly empty.
			items.set(HOE_SLOT, ItemStack.EMPTY);
		}
		setChanged();
	}

	/**
	 * Reproduces vanilla's tool-break feedback at the station.
	 *
	 * <p>Vanilla's own effect lives in {@code LivingEntity#breakItem} and reaches the client through an
	 * entity event, so a block entity gets nothing for free. The parameters are copied from it: the
	 * sound comes off the stack ({@code BREAK_SOUND} is per-item and nullable, so a modded hoe keeps its
	 * own) at volume 0.8 with a jittered pitch, followed by five item particles. Only the particle
	 * velocities differ — vanilla's are rotated by the entity's facing, which a block has none of.
	 */
	private void playBreakEffect(ServerLevel level, ItemStack broken) {
		double x = worldPosition.getX() + 0.5;
		double y = worldPosition.getY() + 0.5;
		double z = worldPosition.getZ() + 0.5;
		Holder<SoundEvent> sound = broken.get(DataComponents.BREAK_SOUND);
		if (sound != null) {
			RandomSource random = level.getRandom();
			level.playSound(null, x, y, z, sound, SoundSource.BLOCKS,
					0.8F, 0.8F + random.nextFloat() * 0.4F);
		}
		level.sendParticles(
				new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(broken)),
				x, y, z, 5, 0.1, 0.1, 0.1, 0.05);
	}

	/**
	 * Whether a hoe with life left is loaded; tilling and harvesting refuse to run without one.
	 *
	 * <p>Stated in vanilla's terms ({@link ItemStack#isBroken()}) instead of arithmetic on the damage
	 * value: a second copy of the break threshold is exactly what drifted out of step with
	 * {@link #wearHoe(ServerLevel)} in MOD-319.
	 */
	private boolean hasUsableHoe() {
		ItemStack hoe = items.get(HOE_SLOT);
		return !hoe.isEmpty() && !hoe.isBroken();
	}

	// ---------------------------------------------------------------- output insertion

	/**
	 * Inserts every stack into the output slots, all-or-nothing.
	 *
	 * <p>There is no "insert this list atomically" helper in the mod — {@code ItemMover} moves items
	 * <em>between</em> containers and is the wrong tool for a machine filling its own inventory (every
	 * existing machine writes its own output with a check-then-act pair). So this is the check-then-act
	 * pair, generalised to a list: call it once with {@code simulate = true} and only commit when it
	 * answers true.
	 *
	 * @param simulate when true, no slot is modified — the answer only reports whether they would fit
	 * @return whether every stack fits (simulate) / was inserted (commit)
	 */
	private boolean insertAll(List<ItemStack> drops, boolean simulate) {
		// Simulation works on copies so a multi-stack drop sees the room its earlier stacks consumed;
		// summing against the untouched slots would happily "fit" two stacks into one slot's worth.
		ItemStack[] scratch = new ItemStack[OUTPUT_SLOT_COUNT];
		for (int i = 0; i < OUTPUT_SLOT_COUNT; i++) {
			ItemStack current = items.get(OUTPUT_SLOT_START + i);
			scratch[i] = simulate ? current.copy() : current;
		}
		for (ItemStack drop : drops) {
			ItemStack remaining = simulate ? drop.copy() : drop;
			for (int i = 0; i < OUTPUT_SLOT_COUNT && !remaining.isEmpty(); i++) {
				ItemStack slot = scratch[i];
				if (slot.isEmpty()) {
					if (!simulate) {
						items.set(OUTPUT_SLOT_START + i, remaining.copy());
					}
					scratch[i] = remaining.copy();
					remaining = ItemStack.EMPTY;
				} else if (ItemStack.isSameItemSameComponents(slot, remaining)) {
					int room = slot.getMaxStackSize() - slot.getCount();
					int moved = Math.min(room, remaining.getCount());
					if (moved > 0) {
						slot.grow(moved);
						remaining.shrink(moved);
					}
				}
			}
			if (!remaining.isEmpty()) {
				return false;
			}
		}
		if (!simulate) {
			setChanged();
		}
		return true;
	}

	// ---------------------------------------------------------------- zone cache

	/**
	 * Rebuilds {@link #zoneCache} with every tile in range that could ever need work: a crop, bare
	 * farmland, or tillable ground. Filtering here (rather than walking the raw cube every tick) is the
	 * whole point of the cache — a radius-4 zone (9×9×3) is ~240 positions, while the farm inside it is a few
	 * dozen tiles.
	 */
	private void rebuildZoneCache(ServerLevel level, BlockPos stationPos) {
		zoneCache.clear();
		int radius = Math.max(1, Config.gardenDroneRange);
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -SCAN_Y_BELOW; dy <= SCAN_Y_ABOVE; dy++) {
					probe.set(stationPos.getX() + dx, stationPos.getY() + dy, stationPos.getZ() + dz);
					if (probe.equals(stationPos) || !level.isLoaded(probe)) {
						continue;
					}
					if (isInteresting(level, probe)) {
						zoneCache.add(probe.immutable());
					}
				}
			}
		}
	}

	/** Whether a tile is worth keeping in the cache — i.e. any of the four actions could ever apply. */
	private boolean isInteresting(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(ModTags.Blocks.SCYTHE_CROPS)) {
			return true; // harvest or fertilize target
		}
		if (isTillable(state)) {
			return true; // till target
		}
		// A bare tile above farmland is where a seed goes.
		return state.isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND);
	}

	/** Ground the drone converts into farmland. Kept to the two unambiguous cases (MVP). */
	private static boolean isTillable(BlockState state) {
		return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK);
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * The crop block the seed in the seed slot plants, or {@code null} when the slot holds something
	 * that is not a plantable seed. Vanilla models seeds as {@link BlockItem}s whose block is the crop
	 * (wheat seeds → wheat), so no lookup table of our own is needed — and mod crops that follow the
	 * same vanilla shape work for free.
	 */
	@Nullable
	private Block seedBlock() {
		ItemStack seed = items.get(SEED_SLOT);
		return seed.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
	}

	/** Whether a drone is docked and therefore available to fly. */
	public boolean hasDrone() {
		return !items.get(DRONE_SLOT).isEmpty();
	}

	/** Whether the station is only idle because a consumable ran out (as opposed to being finished). */
	private boolean needsResources() {
		return items.get(SEED_SLOT).isEmpty() || items.get(FERTILIZER_SLOT).isEmpty();
	}

	/** Records the status and returns how long the station may sleep in it. */
	private int setStatus(GardenDroneStatus next) {
		if (status != next) {
			status = next;
			setChangedQuietly();
			syncBlockEntityToClient();
		}
		// A working station re-checks every tick; an idle one backs off to the shared idle interval so a
		// finished farm costs nothing (R-29).
		return next == GardenDroneStatus.WORKING ? 0 : IDLE_SLEEP_TICKS;
	}

	/** What the station is doing — read by the screen and, in MOD-277 stage 3, the drone's indicator. */
	public GardenDroneStatus status() {
		return status;
	}

	/** The tile the drone is flying to, or {@code null} when parked. Client-visible via the update tag. */
	@Nullable
	public BlockPos droneTarget() {
		return droneTarget;
	}

	/**
	 * How far along the current leg the drone is, 0 (just departed) through 1 (arrived), for a given
	 * client-side time. Computed rather than synced: the leg is sent once, and both sides can work out
	 * where the drone is from the clock.
	 */
	public float flightProgress(float gameTimeWithPartials) {
		if (flightTotal <= 0) {
			return 0.0f;
		}
		return Math.clamp((gameTimeWithPartials - flightStart) / flightTotal, 0.0f, 1.0f);
	}

	/** True while the drone is heading back to the dock (the action has already landed). */
	public boolean isReturning() {
		return returning;
	}

	/**
	 * The flight's speed curve: slow off the pad, fast across the middle, braking onto the target.
	 * Smootherstep (the quintic one) rather than plain smoothstep — over a leg as short as
	 * {@link #MIN_FLIGHT_TICKS} the cubic curve still reads as near-constant speed, and the whole point
	 * of the easing is that the player can see the drone accelerate and brake the way a real one does.
	 *
	 * <p>Its derivative is zero at both 0 and 1, which is what gives the drone zero vertical speed as it
	 * leaves and meets the ground however steep the arc's own ends are (MOD-317).
	 *
	 * <p>Lives here, on the flight model, rather than in the renderer: the renderer positions the drone
	 * and the flight-loop sound follows it, and two copies of this curve would let the sound drift away
	 * from the thing making it (MOD-329).
	 */
	public static float accelerate(float rawProgress) {
		float c = Math.clamp(rawProgress, 0.0f, 1.0f);
		return c * c * c * (c * (c * 6.0f - 15.0f) + 10.0f);
	}

	// ---------------------------------------------------------------- container

	@Override
	public void setItem(int slot, ItemStack stack) {
		super.setItem(slot, stack);
		// Pulling the drone out mid-errand must not leave one painted in the sky, nor a job that lands
		// with nothing to carry it.
		if (slot == DRONE_SLOT && stack.isEmpty()) {
			clearJob();
			syncBlockEntityToClient();
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			// Anything vanilla treats as a plantable seed; the crop it maps to is resolved at plant time.
			case SEED_SLOT -> stack.getItem() instanceof BlockItem;
			case FERTILIZER_SLOT -> stack.is(Items.BONE_MEAL);
			// One drone at a time; without the empty-slot guard a hopper would stuff a whole stack in.
			case DRONE_SLOT -> items.get(DRONE_SLOT).isEmpty()
					&& stack.is(dev.alaindustrial.registry.ModContent.GARDEN_DRONE.get());
			// An already-broken hoe (damage == maxDamage, reachable via /give or another mod) would pass
			// the tag check, fail hasUsableHoe forever and re-block the slot the MOD-319 fix just freed.
			case HOE_SLOT -> items.get(HOE_SLOT).isEmpty() && stack.is(ItemTags.HOES) && !stack.isBroken();
			// Output slots are filled by the station alone — no manual or automated insertion.
			default -> false;
		};
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot >= OUTPUT_SLOT_START && slot < OUTPUT_SLOT_START + OUTPUT_SLOT_COUNT;
	}

	@Override
	public EnergyRole energyRoleForFace(Direction face) {
		// The dock has no FACING (it is rotation-symmetric, like a solar panel), so every face takes EU.
		return EnergyRole.IN;
	}

	// ---------------------------------------------------------------- data + persistence

	private final ContainerData stationData = new ContainerData() {
		@Override
		public int get(int index) {
			return index == DATA_STATUS ? status.ordinal() : dataAccess.get(index);
		}

		@Override
		public void set(int index, int value) {
			if (index == DATA_STATUS) {
				status = GardenDroneStatus.byOrdinal(value);
			} else {
				dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return stationData;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("Status", status.ordinal());
		// The drone's target rides the block entity rather than a per-tick packet: it changes once per
		// job, and getUpdateTag carries it to the renderer, which interpolates the flight itself.
		output.storeNullable("DroneTarget", BlockPos.CODEC, droneTarget);
		output.putInt("DroneJob", droneJob == null ? -1 : droneJob.ordinal());
		output.putInt("FlightRemaining", flightRemaining);
		output.putLong("FlightStart", flightStart);
		output.putInt("FlightTotal", flightTotal);
		output.putBoolean("Returning", returning);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		status = GardenDroneStatus.byOrdinal(input.getIntOr("Status", 0));
		droneTarget = input.read("DroneTarget", BlockPos.CODEC).orElse(null);
		int job = input.getIntOr("DroneJob", -1);
		droneJob = job >= 0 && job < GardenDroneAction.PRIORITY_ORDER.length
				? GardenDroneAction.values()[job] : null;
		flightRemaining = input.getIntOr("FlightRemaining", 0);
		flightStart = input.getLongOr("FlightStart", 0L);
		flightTotal = input.getIntOr("FlightTotal", 0);
		returning = input.getBooleanOr("Returning", false);
	}

	// ---------------------------------------------------------------- menu

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.garden_drone_station");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new GardenDroneStationMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
