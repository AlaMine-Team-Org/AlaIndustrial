package dev.alaindustrial.block.entity.quarry;

import com.mojang.authlib.GameProfile;
import dev.alaindustrial.Config;
import dev.alaindustrial.core.quarry.QuarryDigOrder;
import dev.alaindustrial.core.quarry.QuarryMotion;
import dev.alaindustrial.core.quarry.QuarryMotion.Leg;
import dev.alaindustrial.core.quarry.QuarryMotion.Phase;
import dev.alaindustrial.core.quarry.QuarryRates;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import dev.alaindustrial.core.world.FakePlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

// size-justified: one operation from start to end — the search for the next block with its skips, the price,
// the four phases, the break as the owner with its drops and the bit's wear — and the state that carries it over a
// save; split, the phases and the state they hand each other would be passed around as arguments.
/**
 * The quarry's work cycle (MOD-951, stage B of MOD-637): one block at a time, in the {@link QuarryDigOrder}, from the
 * pit between the towers into the controller's buffer. The controller holds one of these and hands it the formed
 * frame each tick; everything about digging lives here, so the controller stays a machine with a frame.
 *
 * <p><b>One operation.</b> The next block is found first, passing over the cells the spec skips (air for free,
 * liquids, a block beside lava, a block with contents, an unbreakable one). Then the operation is paid in full —
 * the bridge and cart travelling to the cell, the rope, the drilling by hardness ({@link QuarryRates}) — and runs
 * its four phases: travel, lower, drill, raise. The block breaks at the end of the drilling, as the owner breaking
 * it with a pickaxe: through {@code ServerPlayerGameMode.destroyBlock} on the owner's fake player, the way the block
 * breaker breaks (a land-claim mod may refuse), and its drops go straight into the buffer ({@link #drill}). An
 * operation whose drops would not fit does not start, so nothing is left lying in the pit. Experience is not dropped.
 *
 * <p><b>The parts move</b> (MOD-952): each phase starts at a game time the client is told, with the way of the
 * operation ({@link QuarryMotion.Leg}), and the renderer draws the bridge, the cart and the drill from the phase's
 * progress. The travel and the rope are paid and timed from where the drill last was.
 *
 * <p><b>Progress survives.</b> The step (layer and index) is saved with the frame it belongs to: the same frame
 * formed again goes on from that cell; a different frame starts its own pit from the start.
 */
public final class QuarryDigger {

	/** What the controller lends the digger: its bit slot, its buffer, its energy and its owner. */
	public interface Host {
		ItemStack bit();

		void setBit(ItemStack bit);

		/** Whether every stack would fit into the buffer, all of them together. */
		boolean fits(List<ItemStack> stacks);

		/** Puts what it can of {@code stack} into the buffer; what did not fit is left in {@code stack}. */
		void store(ItemStack stack);

		long energy();

		void spend(long eu);

		@Nullable UUID owner();

		String ownerName();

		void changed();

		/** A phase started: the client has to be told, so the parts move on its screen. */
		void moved();

		/** Whether the drill stands to cool (MOD-952). */
		boolean overheated();

		/** A block was drilled: the drill heats up. */
		void drilled();
	}

	private static final int TICKS_PER_PHASE_NONE = 0;

	private QuarryWorkStatus status = QuarryWorkStatus.NOT_FORMED;
	private Phase phase = Phase.IDLE;
	private int phaseLeft;
	private int travelTicks;
	private int lowerTicks;
	private int drillTicks;
	private int raiseTicks;
	private int opTicks;
	private int opElapsed;
	/** The step the next — or the running — operation works on. */
	private int layer;
	private int index;
	/** Where the drill was last: along the bridge, along the paths, and the height it rests at. */
	private int restU;
	private int restT;
	private int restY = Integer.MIN_VALUE;
	/** The frame the progress belongs to, as {@link QuarryFrameLayout#toArray}; {@code null} before the first. */
	private int @Nullable [] progressFrame;
	private @Nullable QuarryDigOrder order;
	/** The way of the running (or the last) operation, the game time its phase started and how long the phase is. */
	private Leg leg = Leg.resting(0.0F, 0.0F, 0.0F);
	private long phaseStart;
	private int phaseTicks;
	/** Whether {@link #leg} belongs to a frame: false until the first frame, and on a world saved before stage C. */
	private boolean placed;
	private boolean alongX;

	public QuarryWorkStatus status() {
		return status;
	}

	/** The layer of the next (or the running) operation, from 0 at the foundations. */
	public int layer() {
		return layer;
	}

	/** The step inside that layer. */
	public int index() {
		return index;
	}

	/** The current layer counted from 1, for the screen; 0 before any frame. */
	public int layerShown() {
		return order == null ? 0 : Math.min(layer, order.layers() - 1) + 1;
	}

	/** How many layers the pit may have; 0 before any frame. */
	public int layers() {
		return order == null ? 0 : order.layers();
	}

	/** How far the current layer is done, per mille. */
	public int layerPermille() {
		return order == null ? 0 : status == QuarryWorkStatus.DONE ? 1000 : order.layerPermille(index);
	}

	/** Ticks of the running operation done so far and in total, for the progress bar. */
	public int opElapsed() {
		return opElapsed;
	}

	public int opTicks() {
		return opTicks;
	}

	/** The phase the parts are in, for the renderer. */
	public Phase phase() {
		return phase;
	}

	/** Where the parts stand at game time {@code now} (fractional between ticks), for the renderer. */
	public QuarryMotion.Pose pose(double now) {
		return QuarryMotion.pose(phase, QuarryMotion.progress(phaseStart, phaseTicks, now), leg);
	}

	/** Whether the parts have a place yet: false before the first formed frame. */
	public boolean placed() {
		return placed;
	}

	/** The frame no longer stands: the digger waits, holding its progress and its running operation. */
	public void notFormed() {
		status = QuarryWorkStatus.NOT_FORMED;
	}

	/** Digging is switched off: the digger waits, holding its progress, and says so. */
	public void switchedOff() {
		status = QuarryWorkStatus.DIGGING_OFF;
	}

	/** The redstone mode keeps the quarry standing: it waits between operations, holding its progress. */
	public void redstoneStopped() {
		if (phase == Phase.IDLE) {
			status = QuarryWorkStatus.REDSTONE_STOPPED;
		}
	}

	/**
	 * One tick on a formed frame. {@code tier} picks the depth; {@code controller} the start corner. Spends at most
	 * {@link Config#quarryCellsPerTick} cells looking for the next block, so a pit of air does not stall a tick.
	 */
	public void tick(ServerLevel level, BlockPos controller, QuarryFrameLayout frame, int tier, Host host) {
		follow(level, controller, frame, tier, host);
		QuarryDigOrder pit = order;
		if (pit == null) {
			return;
		}
		keepStepInLayer(pit, host);
		if (host.owner() == null) {
			status = QuarryWorkStatus.NO_OWNER;
			return;
		}
		if (phase != Phase.IDLE) {
			run(level, pit, host);
			return;
		}
		start(level, pit, host);
	}

	/**
	 * A step past its layer's last tile goes back to the layer's first, dropping the operation it was on: a world saved
	 * while the quarry dug one cell a step (before MOD-952) holds a cell's index, and a layer has a quarter as many
	 * tiles. The cells already dug are air and are passed over for free; nothing else is lost but that operation's
	 * price.
	 */
	private void keepStepInLayer(QuarryDigOrder pit, Host host) {
		if (layer >= 0 && layer < pit.layers() && (index < 0 || index >= pit.perLayer())) {
			index = 0;
			phase = Phase.IDLE;
			phaseTicks = 0;
			opTicks = 0;
			opElapsed = 0;
			host.changed();
		}
	}

	/**
	 * Puts the step at {@code (layer, index)} as a load would, whatever the order holds: for gametests of a world
	 * saved by an older build.
	 */
	public void restoreStep(int layer, int index) {
		this.layer = layer;
		this.index = index;
	}

	/** Takes the frame's order, and starts the pit afresh when the frame is not the one the progress belongs to. */
	private void follow(ServerLevel level, BlockPos controller, QuarryFrameLayout frame, int tier, Host host) {
		int[] key = frame.toArray();
		if (progressFrame == null || !java.util.Arrays.equals(progressFrame, key)) {
			progressFrame = key;
			layer = 0;
			index = 0;
			phase = Phase.IDLE;
			opTicks = 0;
			opElapsed = 0;
			restY = Integer.MIN_VALUE;
			placed = false;
			host.changed();
		}
		alongX = frame.alongX();
		if (!placed) {
			// The bridge and the cart where the player set them, the drill pulled up under the cart.
			leg = Leg.resting(frame.bridgeAt() + 0.5F, frame.cartAt() + 0.5F,
					frame.headY() + QuarryFrameScan.HEAD_LAYERS);
			phaseStart = level.getGameTime();
			phaseTicks = 0;
			placed = true;
			host.moved();
		}
		order = QuarryDigOrder.forFrame(frame.minX(), frame.minZ(), frame.maxX(), frame.maxZ(), QuarryFrameScan.TOWER,
				frame.alongX(), controller.getX(), controller.getZ(), frame.baseY(), depth(tier), level.getMinY() + 1);
		if (restY == Integer.MIN_VALUE) {
			restY = frame.headY();
			restU = 0;
			restT = 0;
		}
	}

	private static int depth(int tier) {
		return switch (tier) {
			case 1 -> Config.quarryMaxDepthTier1;
			case 2 -> Config.quarryMaxDepthTier2;
			default -> Config.quarryMaxDepthTier3;
		};
	}

	private static QuarryRates rates() {
		return new QuarryRates(Config.quarryDrillEuPerHardness, Config.quarryDrillMinEu, Config.quarryStoneDrillTicks,
				Config.quarryDrillMinTicks, Config.quarryTravelBlocksPerSecond, Config.quarryTravelEuPerBlock,
				Config.quarryRopeBlocksPerSecond, Config.quarryRopeEuPerBlock);
	}

	/**
	 * Finds the next tile with something to drill and, when everything it needs is there, pays for all of it and sets
	 * off (MOD-952: the drill takes a 2 x 2 tile at once). Every cell is priced as a block of its own - drilling by its
	 * hardness, its drops in the buffer's room - so a tile costs what its blocks cost; the travel and the rope are paid
	 * once.
	 */
	private void start(ServerLevel level, QuarryDigOrder pit, Host host) {
		int budget = Math.max(1, Config.quarryCellsPerTick);
		for (int looked = 0; looked < budget; looked++) {
			if (pit.done(layer, index)) {
				status = QuarryWorkStatus.DONE;
				return;
			}
			QuarryDigOrder.Tile tile = pit.tile(layer, index);
			List<BlockPos> targets = targets(level, tile);
			if (targets == null) {
				status = QuarryWorkStatus.CHUNK_NOT_LOADED;
				return;
			}
			if (targets.isEmpty()) {
				advance(pit, host);
				continue;
			}
			if (host.bit().isEmpty()) {
				status = QuarryWorkStatus.NO_BIT;
				return;
			}
			if (host.overheated()) {
				status = QuarryWorkStatus.OVERHEATED;
				return;
			}
			QuarryRates rates = rates();
			List<ItemStack> haul = new ArrayList<>();
			long drillEu = 0L;
			int drillSum = 0;
			for (BlockPos pos : targets) {
				BlockState state = level.getBlockState(pos);
				haul.addAll(Block.getDrops(state, level, pos, null, null, new ItemStack(Items.NETHERITE_PICKAXE)));
				float hardness = state.getDestroySpeed(level, pos);
				drillEu += rates.drillEu(hardness);
				drillSum += rates.drillTicks(hardness);
			}
			if (!host.fits(haul)) {
				status = QuarryWorkStatus.OUTPUT_FULL;
				return;
			}
			int alongPaths = tile.t() - restT;
			int alongBridge = tile.u() - restU;
			int down = Math.max(0, restY - tile.y());
			long price = rates.travelEu(alongPaths, alongBridge) + rates.ropeEu(down + 1) + drillEu;
			if (host.energy() < price) {
				status = QuarryWorkStatus.NO_POWER;
				return;
			}
			host.spend(price);
			setOff(level, tile, rates.travelTicks(alongPaths, alongBridge), rates.ropeTicks(down), drillSum,
					rates.ropeTicks(1), host);
			return;
		}
	}

	/** The cells of {@code tile} the drill takes - those the spec does not skip - or null if one is unloaded. */
	private static @Nullable List<BlockPos> targets(ServerLevel level, QuarryDigOrder.Tile tile) {
		List<BlockPos> targets = new ArrayList<>(tile.cells().size());
		for (QuarryDigOrder.Cell cell : tile.cells()) {
			BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
			if (!level.isLoaded(pos)) {
				return null;
			}
			if (!skipped(level, pos, level.getBlockState(pos))) {
				targets.add(pos);
			}
		}
		return targets;
	}

	/** Starts the paid operation over {@code tile}: its phases' lengths, where the drill now rests, and its way. */
	private void setOff(ServerLevel level, QuarryDigOrder.Tile tile, int travel, int lower, int drilling, int raise,
			Host host) {
		travelTicks = travel;
		lowerTicks = lower;
		drillTicks = drilling;
		raiseTicks = raise;
		opTicks = travelTicks + lowerTicks + drillTicks + raiseTicks;
		opElapsed = 0;
		restU = tile.u();
		restT = tile.t();
		restY = tile.y() + 1;
		QuarryMotion.Pose at = QuarryMotion.pose(phase, 1.0F, leg);
		float toAlong = alongX ? tile.centreX() : tile.centreZ();
		float toAcross = alongX ? tile.centreZ() : tile.centreX();
		leg = new Leg(at.along(), at.across(), at.tip(), toAlong, toAcross, tile.y() + 1.0F);
		enter(level, Phase.TRAVEL, travelTicks, host);
		host.changed();
	}

	/** The cells the spec passes over: air, liquids, a block beside lava, a block with contents, an unbreakable one. */
	private static boolean skipped(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
			return true;
		}
		if (state.getDestroySpeed(level, pos) < 0) {
			return true;
		}
		for (Direction side : Direction.values()) {
			if (level.getFluidState(pos.relative(side)).is(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	/** One tick of the running operation; the block breaks as the drilling ends. */
	private void run(ServerLevel level, QuarryDigOrder pit, Host host) {
		opElapsed = Math.min(opTicks, opElapsed + 1);
		status = shown(phase);
		if (--phaseLeft > 0) {
			return;
		}
		switch (phase) {
			case TRAVEL -> enter(level, Phase.LOWER, lowerTicks, host);
			case LOWER -> enter(level, Phase.DRILL, drillTicks, host);
			case DRILL -> {
				if (host.bit().isEmpty()) {
					// The bit was taken out mid-operation: the drilling waits, already paid, for a new one.
					phaseLeft = 1;
					status = QuarryWorkStatus.NO_BIT;
					return;
				}
				if (!drill(level, pit, host)) {
					phaseLeft = 1;
					return;
				}
				enter(level, Phase.RAISE, raiseTicks, host);
			}
			case RAISE -> {
				phase = Phase.IDLE;
				phaseStart = level.getGameTime();
				phaseTicks = 0;
				opElapsed = 0;
				opTicks = 0;
				advance(pit, host);
				host.moved();
			}
			case IDLE -> {
			}
		}
		host.changed();
	}

	/** Moves to {@code next}; a phase of no ticks passes on the next tick, so every phase is seen at least once. */
	private void enter(ServerLevel level, Phase next, int ticks, Host host) {
		phase = next;
		phaseLeft = Math.max(1, ticks);
		phaseStart = level.getGameTime();
		phaseTicks = phaseLeft;
		status = shown(next);
		host.moved();
	}

	private static QuarryWorkStatus shown(Phase phase) {
		return switch (phase) {
			case TRAVEL -> QuarryWorkStatus.TRAVELLING;
			case LOWER -> QuarryWorkStatus.LOWERING;
			case DRILL -> QuarryWorkStatus.DRILLING;
			case RAISE, IDLE -> QuarryWorkStatus.RAISING;
		};
	}

	/**
	 * Breaks the tile's cells as the owner and puts their drops into the buffer; whether the step is over. Each cell
	 * is read again: whatever the world did to it during the operation, a cell that is skipped now is passed over.
	 *
	 * <p><b>The drops never touch the world.</b> They are rolled here, with the pickaxe and the owner, and go straight
	 * into the buffer; the break itself is made by the owner's fake player in creative mode, so the world drops
	 * nothing of its own. Picking up item entities instead lost the haul whenever the world would not take a new
	 * entity — a chunk at the edge of loading — and a quarry must not lose what it digs. The break still runs
	 * through {@code ServerPlayerGameMode.destroyBlock}, so land-claim mods see it and may refuse; experience is not
	 * dropped. A haul that no longer fits holds the drilling, already paid, until the buffer has room; a bit that
	 * wears out on one cell holds the rest of the tile for a new bit, the cells it already took being air by then.
	 */
	private boolean drill(ServerLevel level, QuarryDigOrder pit, Host host) {
		QuarryDigOrder.Tile tile = pit.tile(layer, index);
		List<BlockPos> targets = new ArrayList<>(tile.cells().size());
		for (QuarryDigOrder.Cell cell : tile.cells()) {
			BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
			if (level.isLoaded(pos) && !skipped(level, pos, level.getBlockState(pos))) {
				targets.add(pos);
			}
		}
		if (targets.isEmpty()) {
			return true;
		}
		UUID owner = host.owner();
		if (owner == null) {
			status = QuarryWorkStatus.NO_OWNER;
			return false;
		}
		ServerPlayer player = FakePlayers.get().get(level, new GameProfile(owner, host.ownerName()));
		ItemStack pickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
		List<List<ItemStack>> hauls = new ArrayList<>(targets.size());
		List<ItemStack> all = new ArrayList<>();
		for (BlockPos pos : targets) {
			player.setPos(pos.getX() + 0.5D, pos.getY() + 1.5D, pos.getZ() + 0.5D);
			List<ItemStack> haul = Block.getDrops(level.getBlockState(pos), level, pos, null, player, pickaxe);
			hauls.add(haul);
			all.addAll(haul);
		}
		if (!host.fits(all)) {
			status = QuarryWorkStatus.OUTPUT_FULL;
			return false;
		}
		player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
		player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
		try {
			for (int i = 0; i < targets.size(); i++) {
				if (host.bit().isEmpty()) {
					status = QuarryWorkStatus.NO_BIT;
					return false;
				}
				BlockPos pos = targets.get(i);
				player.setPos(pos.getX() + 0.5D, pos.getY() + 1.5D, pos.getZ() + 0.5D);
				if (player.gameMode.destroyBlock(pos)) {
					for (ItemStack stack : hauls.get(i)) {
						host.store(stack.copy());
					}
					wear(level, pos, host);
					host.drilled();
				}
			}
		} finally {
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			// The block breaker shares this fake player and expects it in survival.
			player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
		}
		return true;
	}

	/** One point of wear on the bit; a bit that wears out breaks with a sound and leaves the slot empty. */
	private static void wear(ServerLevel level, BlockPos pos, Host host) {
		ItemStack bit = host.bit().copy();
		bit.hurtAndBreak(1, level, null, item -> level.playSound(null, pos, SoundEvents.ITEM_BREAK.value(),
				SoundSource.BLOCKS, 0.8F, 0.9F));
		host.setBit(bit.isEmpty() ? ItemStack.EMPTY : bit);
	}

	private void advance(QuarryDigOrder pit, Host host) {
		int[] next = pit.next(layer, index);
		layer = next[0];
		index = next[1];
		host.changed();
	}

	public void save(ValueOutput output) {
		output.putInt("DigLayer", layer);
		output.putInt("DigIndex", index);
		output.putInt("DigPhase", phase.ordinal());
		output.putInt("DigPhaseLeft", phaseLeft);
		output.putIntArray("DigTicks", new int[] {travelTicks, lowerTicks, drillTicks, raiseTicks, opTicks,
				opElapsed});
		output.putIntArray("DigRest", new int[] {restU, restT, restY});
		if (progressFrame != null) {
			output.putIntArray("DigFrame", progressFrame);
		}
		if (placed) {
			// Floats as their bits: the storage has no float array.
			output.putIntArray("DigLeg", new int[] {bits(leg.fromAlong()), bits(leg.fromAcross()), bits(leg.fromTip()),
					bits(leg.toAlong()), bits(leg.toAcross()), bits(leg.contactTip())});
			output.putLong("DigPhaseStart", phaseStart);
			output.putInt("DigPhaseTicks", phaseTicks);
			output.putBoolean("DigAlongX", alongX);
		}
	}

	private static int bits(float value) {
		return Float.floatToRawIntBits(value);
	}

	private static float unbits(int bits) {
		return Float.intBitsToFloat(bits);
	}

	public void load(ValueInput input) {
		layer = input.getIntOr("DigLayer", 0);
		index = input.getIntOr("DigIndex", 0);
		int phaseOrdinal = input.getIntOr("DigPhase", 0);
		phase = phaseOrdinal >= 0 && phaseOrdinal < Phase.values().length ? Phase.values()[phaseOrdinal] : Phase.IDLE;
		phaseLeft = input.getIntOr("DigPhaseLeft", TICKS_PER_PHASE_NONE);
		int[] ticks = input.getIntArray("DigTicks").orElse(new int[6]);
		if (ticks.length == 6) {
			travelTicks = ticks[0];
			lowerTicks = ticks[1];
			drillTicks = ticks[2];
			raiseTicks = ticks[3];
			opTicks = ticks[4];
			opElapsed = ticks[5];
		}
		int[] rest = input.getIntArray("DigRest").orElse(null);
		if (rest != null && rest.length == 3) {
			restU = rest[0];
			restT = rest[1];
			restY = rest[2];
		}
		progressFrame = input.getIntArray("DigFrame").orElse(null);
		int[] way = input.getIntArray("DigLeg").orElse(null);
		placed = way != null && way.length == 6;
		if (placed) {
			leg = new Leg(unbits(way[0]), unbits(way[1]), unbits(way[2]), unbits(way[3]), unbits(way[4]),
					unbits(way[5]));
			phaseStart = input.getLongOr("DigPhaseStart", 0L);
			phaseTicks = input.getIntOr("DigPhaseTicks", 0);
			alongX = input.getBooleanOr("DigAlongX", false);
		}
	}
}
