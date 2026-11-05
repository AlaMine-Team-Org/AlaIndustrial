package dev.alaindustrial.block.entity.quarry;

import com.mojang.authlib.GameProfile;
import dev.alaindustrial.Config;
import dev.alaindustrial.core.quarry.QuarryDigOrder;
import dev.alaindustrial.core.quarry.QuarryRates;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import dev.alaindustrial.core.world.FakePlayers;
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
 * <p><b>Nothing moves on screen yet</b> (the animation is stage C), but the travel and the rope are paid and take
 * their time already (owner, 2026-11-05), measured from where the drill last was.
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
	}

	private static final int TICKS_PER_PHASE_NONE = 0;

	private enum Phase { IDLE, TRAVEL, LOWER, DRILL, RAISE }

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

	/** The frame no longer stands: the digger waits, holding its progress and its running operation. */
	public void notFormed() {
		status = QuarryWorkStatus.NOT_FORMED;
	}

	/** Digging is switched off: the digger waits, holding its progress, and says so. */
	public void switchedOff() {
		status = QuarryWorkStatus.DIGGING_OFF;
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
			host.changed();
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

	/** Finds the next block to drill and, when everything it needs is there, pays for it and sets off. */
	private void start(ServerLevel level, QuarryDigOrder pit, Host host) {
		int budget = Math.max(1, Config.quarryCellsPerTick);
		for (int looked = 0; looked < budget; looked++) {
			if (pit.done(layer, index)) {
				status = QuarryWorkStatus.DONE;
				return;
			}
			QuarryDigOrder.Cell cell = pit.cell(layer, index);
			BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
			if (!level.isLoaded(pos)) {
				status = QuarryWorkStatus.CHUNK_NOT_LOADED;
				return;
			}
			BlockState state = level.getBlockState(pos);
			if (skipped(level, pos, state)) {
				advance(pit, host);
				continue;
			}
			ItemStack bit = host.bit();
			if (bit.isEmpty()) {
				status = QuarryWorkStatus.NO_BIT;
				return;
			}
			if (!host.fits(Block.getDrops(state, level, pos, null, null, new ItemStack(Items.NETHERITE_PICKAXE)))) {
				status = QuarryWorkStatus.OUTPUT_FULL;
				return;
			}
			QuarryRates rates = rates();
			float hardness = state.getDestroySpeed(level, pos);
			int alongPaths = cell.t() - restT;
			int alongBridge = cell.u() - restU;
			int down = Math.max(0, restY - cell.y());
			long price = rates.travelEu(alongPaths, alongBridge) + rates.ropeEu(down + 1) + rates.drillEu(hardness);
			if (host.energy() < price) {
				status = QuarryWorkStatus.NO_POWER;
				return;
			}
			host.spend(price);
			travelTicks = rates.travelTicks(alongPaths, alongBridge);
			lowerTicks = rates.ropeTicks(down);
			drillTicks = rates.drillTicks(hardness);
			raiseTicks = rates.ropeTicks(1);
			opTicks = travelTicks + lowerTicks + drillTicks + raiseTicks;
			opElapsed = 0;
			restU = cell.u();
			restT = cell.t();
			restY = cell.y() + 1;
			enter(Phase.TRAVEL, travelTicks);
			host.changed();
			return;
		}
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
			case TRAVEL -> enter(Phase.LOWER, lowerTicks);
			case LOWER -> enter(Phase.DRILL, drillTicks);
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
				enter(Phase.RAISE, raiseTicks);
			}
			case RAISE -> {
				phase = Phase.IDLE;
				opElapsed = 0;
				opTicks = 0;
				advance(pit, host);
			}
			case IDLE -> {
			}
		}
		host.changed();
	}

	/** Moves to {@code next}; a phase of no ticks passes on the next tick, so every phase is seen at least once. */
	private void enter(Phase next, int ticks) {
		phase = next;
		phaseLeft = Math.max(1, ticks);
		status = shown(next);
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
	 * Breaks the cell as the owner and puts its drops into the buffer; whether the step is over. The cell is read
	 * again: whatever the world did to it during the operation, a cell that is skipped now is passed over.
	 *
	 * <p><b>The drops never touch the world.</b> They are rolled here, with the pickaxe and the owner, and go straight
	 * into the buffer; the break itself is made by the owner's fake player in creative mode, so the world drops
	 * nothing of its own. Picking up item entities instead lost the haul whenever the world would not take a new
	 * entity — a chunk at the edge of loading — and a quarry must not lose what it digs. The break still runs
	 * through {@code ServerPlayerGameMode.destroyBlock}, so land-claim mods see it and may refuse; experience is not
	 * dropped. A haul that no longer fits holds the drilling, already paid, until the buffer has room.
	 */
	private boolean drill(ServerLevel level, QuarryDigOrder pit, Host host) {
		QuarryDigOrder.Cell cell = pit.cell(layer, index);
		BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
		BlockState state = level.getBlockState(pos);
		if (!level.isLoaded(pos) || skipped(level, pos, state)) {
			return true;
		}
		UUID owner = host.owner();
		if (owner == null) {
			status = QuarryWorkStatus.NO_OWNER;
			return false;
		}
		ServerPlayer player = FakePlayers.get().get(level, new GameProfile(owner, host.ownerName()));
		player.setPos(pos.getX() + 0.5D, pos.getY() + 1.5D, pos.getZ() + 0.5D);
		ItemStack pickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
		List<ItemStack> haul = Block.getDrops(state, level, pos, null, player, pickaxe);
		if (!host.fits(haul)) {
			status = QuarryWorkStatus.OUTPUT_FULL;
			return false;
		}
		player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
		player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
		boolean broken;
		try {
			broken = player.gameMode.destroyBlock(pos);
		} finally {
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			// The block breaker shares this fake player and expects it in survival.
			player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
		}
		if (broken) {
			for (ItemStack stack : haul) {
				host.store(stack.copy());
			}
			wear(level, pos, host);
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
	}
}
