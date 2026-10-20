package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.FluidTapBlock;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.fluid.FluidMover;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.fluid.ExperienceFluid;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Tap's block entity (MOD-837): a 1000 mB buffer of one fluid, filled through one face and let out into
 * the world while the tap is open.
 *
 * <p><b>In: the source face only</b> ({@link FluidTapBlock#sourceFace}). A mod fluid pipe there pushes into the
 * buffer through {@link #fluidPort}, which answers on that face alone and reports that it gives nothing back,
 * so the pipe takes the face as a sink without a wrench (the pipe face-role contract). Any other fluid holder
 * there, of this mod or another, is drained directly while the buffer has room, open or closed, up to
 * {@link #PULL_PER_TICK_MB} a tick, so the glass shows what the source holds. One fluid at a time: a different
 * one waits until the buffer is empty. Liquid experience is drained in whole points only
 * ({@link ExperienceFluid#MB_PER_POINT}), so less than a point stays in the source.
 *
 * <p><b>Out, while open:</b>
 * <ul>
 *   <li>liquid experience ({@code c:experience}) becomes vanilla {@link ExperienceOrb}s at the nozzle, one orb
 *       of up to {@link #POUR_POINTS} points every {@link #POUR_INTERVAL} ticks, and only while a player is
 *       within {@link #POUR_RANGE} blocks: an orb on the ground despawns after five minutes. A remainder below
 *       a point stays in the buffer and is never rounded up;</li>
 *   <li>a fluid that has a world block and a bucket goes into a fluid holder in the cell the nozzle points at
 *       ({@link FluidTapBlock#pourCell}), up to {@link #POUR_INTO_HOLDER_MB} a tick;</li>
 *   <li>with no holder there, it places one source block in that cell, at most
 *       one every {@link #PLACE_INTERVAL} ticks, once the buffer and the source behind it hold a bucket
 *       ({@link #PLACE_MB}). It goes through the fluid's bucket as a dispenser does, so vanilla's rules hold:
 *       water evaporates in the Nether, a waterloggable block takes it, an occupied cell refuses it;</li>
 *   <li>a fluid with no block or no bucket (steam) is not poured at all.</li>
 * </ul>
 *
 * <p><b>The client sees the fluid.</b> The renderer draws the buffer's fluid behind the glass; the block entity
 * sends it on a change of fluid at once and on a change of amount at most every {@link #SYNC_INTERVAL} ticks.
 */
public class FluidTapBlockEntity extends BlockEntity implements FluidPortHost {
	/** The buffer: a bucket. */
	public static final long CAPACITY_MB = 1000L;
	/** Most a direct source gives in one tick, so a big tank is not emptied in one go. */
	public static final long PULL_PER_TICK_MB = 200L;
	/** Ticks between two orbs while open. */
	public static final int POUR_INTERVAL = 10;
	/** Most points in one poured orb. */
	public static final int POUR_POINTS = 5;
	/** How near a player must be for the open tap to pour orbs: the distance at which orbs fly to a player. */
	public static final double POUR_RANGE = 8.0;
	/** Most poured into a fluid holder under the nozzle in one tick. */
	public static final long POUR_INTO_HOLDER_MB = 200L;
	/** One source block in the world costs a bucket. */
	public static final long PLACE_MB = 1000L;
	/** Ticks between two placed source blocks. */
	public static final int PLACE_INTERVAL = 20;
	/** Least ticks between two updates of the client's copy while only the amount changes. */
	public static final int SYNC_INTERVAL = 10;

	/** {@code c:experience}: the fluids that leave as orbs. */
	public static final TagKey<Fluid> EXPERIENCE_TAG =
			TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "experience"));

	private static final String TANK_KEY = "Tap";
	private static final String TOGGLED_KEY = "ToggledOpen";
	private static final String POUR_COOLDOWN_KEY = "PourCooldown";

	/** The buffer. It reports that it gives nothing back, so a pipe on the source face fills it unasked. */
	public final FluidTank tank = new FluidTank(CAPACITY_MB, fluid -> !fluid.isEmpty(), fluid -> false,
			this::setChanged) {
		@Override
		public boolean supportsExtraction() {
			return false;
		}
	};
	private boolean toggledOpen;
	private int pourCooldown;
	private FluidHolder syncedFluid = FluidHolder.EMPTY;
	private long syncedAmount;
	private int syncCooldown;

	public FluidTapBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.FLUID_TAP_BE.get(), pos, state);
	}

	/** Whether {@code fluid} is liquid experience by the common tag. */
	public static boolean isExperience(FluidHolder fluid) {
		return !fluid.isEmpty() && fluid.fluid().defaultFluidState().is(EXPERIENCE_TAG);
	}

	/**
	 * Whether the tap can let {@code fluid} out at all: liquid experience as orbs, or a fluid that has a block in
	 * the world. A tank under the nozzle is the second way out of the same fluids, not a way for the rest.
	 */
	public static boolean canPour(FluidHolder fluid) {
		return isExperience(fluid) || (!fluid.isEmpty() && hasWorldForm(fluid.fluid()));
	}

	/** A source block in the world and a bucket that can put it there, as a dispenser would. */
	private static boolean hasWorldForm(Fluid fluid) {
		return !sourceBlock(fluid).isAir() && fluid.getBucket() instanceof DispensibleContainerItem;
	}

	/** The buffer on the source face, nothing anywhere else. */
	@Override
	public FluidPort fluidPort(Direction side) {
		return side == FluidTapBlock.sourceFace(getBlockState()) ? tank : null;
	}

	public boolean isToggledOpen() {
		return toggledOpen;
	}

	// --- the player's click ----------------------------------------------------------------------

	/**
	 * Open or close the tap, and say so above the hotbar. Opening a tap that holds a fluid it cannot pour says
	 * that instead. A redstone signal keeps the tap open whatever the toggle says.
	 */
	public void toggle(ServerLevel level, Player player) {
		toggledOpen = !toggledOpen;
		setChanged();
		FluidTapBlock.refreshOpen(level, getBlockPos());
		boolean open = level.getBlockState(getBlockPos()).getValue(FluidTapBlock.OPEN);
		String key;
		if (open && !tank.fluid.isEmpty() && !canPour(tank.fluid)) {
			key = "message.alaindustrial.fluid_tap.cannot_pour";
		} else if (open) {
			key = toggledOpen ? "message.alaindustrial.fluid_tap.open" : "message.alaindustrial.fluid_tap.powered";
		} else {
			key = "message.alaindustrial.fluid_tap.closed";
		}
		player.sendOverlayMessage(Component.translatable(key));
	}

	// --- tick ------------------------------------------------------------------------------------

	/** Fill from the source, pour while open, keep the client's copy current. */
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		pullFromSource(level, state);
		if (pourCooldown > 0) {
			pourCooldown--;
		}
		if (state.getValue(FluidTapBlock.OPEN)) {
			pour(level, pos, state);
		}
		syncToClient(level, pos, state);
	}

	private void pour(ServerLevel level, BlockPos pos, BlockState state) {
		FluidHolder fluid = tank.fluid;
		if (fluid.isEmpty()) {
			// An empty buffer next to a full source pours what the source holds; pullFromSource fills the
			// buffer first, so empty here means the source has nothing either.
			return;
		}
		if (isExperience(fluid)) {
			if (pourCooldown == 0 && playerNear(level, pos, state)) {
				int points = takePoints(level, state, POUR_POINTS);
				if (points > 0) {
					spawnOrb(level, pos, state, points);
					pourCooldown = POUR_INTERVAL;
				}
			}
			return;
		}
		if (!canPour(fluid)) {
			return;
		}
		BlockPos cell = FluidTapBlock.pourCell(pos, state);
		Direction intoFace = state.getValue(FluidTapBlock.CEILING)
				? state.getValue(FluidTapBlock.FACING).getOpposite() : Direction.UP;
		FluidPort holder = FluidLookup.get().find(level, cell, intoFace);
		if (holder != null) {
			pourInto(holder, fluid);
			return;
		}
		if (pourCooldown == 0 && placeSource(level, state, cell, fluid)) {
			pourCooldown = PLACE_INTERVAL;
		}
	}

	/** A player who can pick orbs up, anyone but a spectator, within {@link #POUR_RANGE} of the nozzle. */
	private static boolean playerNear(Level level, BlockPos pos, BlockState state) {
		Vec3 at = Vec3.atLowerCornerOf(pos).add(FluidTapBlock.nozzle(state));
		return level.getNearestPlayer(at.x, at.y, at.z, POUR_RANGE, EntitySelector.NO_SPECTATORS) != null;
	}

	// --- moving fluid ------------------------------------------------------------------------------

	/** The port behind the tap, or {@code null}: nothing there, or a mod fluid pipe, which fills the buffer itself. */
	private FluidPort sourcePort(Level level, BlockState state) {
		Direction face = FluidTapBlock.sourceFace(state);
		BlockPos at = getBlockPos().relative(face);
		if (level.getBlockState(at).getBlock() instanceof FluidPipeBlock) {
			return null;
		}
		return FluidLookup.get().find(level, at, face.getOpposite());
	}

	/** Top the buffer up from the source behind the tap, if it holds the buffer's fluid (or the buffer is empty). */
	private void pullFromSource(ServerLevel level, BlockState state) {
		long room = tank.capacity - tank.amount;
		if (room <= 0) {
			return;
		}
		FluidPort source = sourcePort(level, state);
		if (source == null) {
			return;
		}
		FluidHolder fluid = source.fluid();
		if (fluid.isEmpty() || (!tank.fluid.isEmpty() && !tank.fluid.equals(fluid))) {
			return;
		}
		boolean points = isExperience(fluid);
		long max = Math.min(room, PULL_PER_TICK_MB);
		long available = EnergyTransactions.get().simulate(txn -> source.extract(fluid, max, txn));
		long take = points ? wholePoints(Math.min(available, max)) : Math.min(available, max);
		if (take <= 0) {
			return;
		}
		EnergyTransactions.get().runCommitting(txn -> FluidMover.move(source, tank, fluid, take, txn));
	}

	/** {@code mb} rounded down to whole points of experience. */
	private static long wholePoints(long mb) {
		return mb / ExperienceFluid.MB_PER_POINT * ExperienceFluid.MB_PER_POINT;
	}

	/** Take {@code mb} out of the buffer, upholding the tank invariant; the buffer's extract refuses everyone. */
	private void spend(long mb) {
		if (mb <= 0) {
			return;
		}
		tank.amount -= mb;
		if (tank.amount <= 0) {
			tank.amount = 0;
			tank.fluid = FluidHolder.EMPTY;
		}
		setChanged();
	}

	/**
	 * Take up to {@code max} whole points of experience, the buffer first, then the source behind the tap.
	 * Returns how many were taken; whatever is less than a point stays where it is.
	 */
	int takePoints(ServerLevel level, BlockState state, int max) {
		int fromBuffer = (int) Math.min(max, tank.amount / ExperienceFluid.MB_PER_POINT);
		spend(fromBuffer * ExperienceFluid.MB_PER_POINT);
		int fromSource = 0;
		if (fromBuffer < max) {
			FluidPort source = sourcePort(level, state);
			if (source != null && isExperience(source.fluid())) {
				FluidHolder fluid = source.fluid();
				long wanted = (max - fromBuffer) * ExperienceFluid.MB_PER_POINT;
				long available = EnergyTransactions.get().simulate(txn -> source.extract(fluid, wanted, txn));
				long whole = wholePoints(Math.min(available, wanted));
				if (whole > 0) {
					long[] moved = {0};
					EnergyTransactions.get().runCommitting(txn -> moved[0] = source.extract(fluid, whole, txn));
					fromSource = (int) (moved[0] / ExperienceFluid.MB_PER_POINT);
				}
			}
		}
		return fromBuffer + fromSource;
	}

	/** Pour the buffer into the holder under the nozzle, up to {@link #POUR_INTO_HOLDER_MB}. */
	private void pourInto(FluidPort holder, FluidHolder fluid) {
		long max = Math.min(tank.amount, POUR_INTO_HOLDER_MB);
		if (max <= 0) {
			return;
		}
		long[] accepted = {0};
		EnergyTransactions.get().runCommitting(txn -> accepted[0] = holder.insert(fluid, max, txn));
		spend(accepted[0]);
	}

	/**
	 * Place one source block of {@code fluid} in {@code cell} once the buffer and the source behind the tap hold
	 * a bucket of it. Returns whether a bucket was spent (placed, or evaporated as a bucket would).
	 */
	private boolean placeSource(ServerLevel level, BlockState state, BlockPos cell, FluidHolder fluid) {
		Fluid raw = fluid.fluid();
		if (!hasWorldForm(raw) || level.getFluidState(cell).isSourceOfType(sourceFluid(raw))) {
			return false;
		}
		long fromBuffer = Math.min(tank.amount, PLACE_MB);
		long fromSource = PLACE_MB - fromBuffer;
		FluidPort source = fromSource > 0 ? sourcePort(level, state) : null;
		if (fromSource > 0) {
			if (source == null || !fluid.equals(source.fluid())) {
				return false;
			}
			long available = EnergyTransactions.get().simulate(txn -> source.extract(fluid, fromSource, txn));
			if (available < fromSource) {
				return false;
			}
		}
		if (!emptyIntoWorld(level, cell, raw)) {
			return false;
		}
		spend(fromBuffer);
		if (fromSource > 0) {
			EnergyTransactions.get().runCommitting(txn -> source.extract(fluid, fromSource, txn));
		}
		return true;
	}

	/**
	 * Empty a bucket of {@code fluid} into {@code cell} the way a dispenser does: through the fluid's own bucket,
	 * so vanilla's rules apply (evaporation in an ultrawarm dimension, waterlogging, a cell it cannot replace).
	 */
	// MOD-498 kind B: NeoForge deprecates this overload for its ItemStack-aware one, which does not exist on
	// Fabric; vanilla does not deprecate it, and this shared code compiles for both loaders.
	@SuppressWarnings("deprecation")
	private static boolean emptyIntoWorld(ServerLevel level, BlockPos cell, Fluid fluid) {
		return fluid.getBucket() instanceof DispensibleContainerItem bucket
				&& bucket.emptyContents(null, level, cell, null);
	}

	/** The still form of {@code fluid}: a flowing fluid's source, any other fluid itself. */
	private static Fluid sourceFluid(Fluid fluid) {
		return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
	}

	/** The block one source of {@code fluid} is in the world; air for a fluid that has none. */
	private static BlockState sourceBlock(Fluid fluid) {
		FluidState still = fluid instanceof FlowingFluid flowing ? flowing.getSource(false) : fluid.defaultFluidState();
		return still.createLegacyBlock();
	}

	private static void spawnOrb(ServerLevel level, BlockPos pos, BlockState state, int points) {
		Vec3 at = Vec3.atLowerCornerOf(pos).add(FluidTapBlock.nozzle(state));
		ExperienceOrb orb = new ExperienceOrb(level, at.x, at.y, at.z, points);
		// Out of the nozzle, not the random toss vanilla gives a fresh orb: straight ahead, a little.
		Direction facing = state.getValue(FluidTapBlock.FACING);
		orb.setDeltaMovement(facing.getStepX() * 0.05, 0.0, facing.getStepZ() * 0.05);
		level.addFreshEntity(orb);
	}

	// --- the client's copy, persistence ------------------------------------------------------------

	/**
	 * Send the buffer to clients: a new fluid (or none) at once, a new amount at most every
	 * {@link #SYNC_INTERVAL} ticks, nothing while nothing changed.
	 */
	private void syncToClient(Level level, BlockPos pos, BlockState state) {
		if (syncCooldown > 0) {
			syncCooldown--;
		}
		boolean fluidChanged = !syncedFluid.equals(tank.fluid);
		if (!fluidChanged && (syncedAmount == tank.amount || syncCooldown > 0)) {
			return;
		}
		syncedFluid = tank.fluid;
		syncedAmount = tank.amount;
		syncCooldown = SYNC_INTERVAL;
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
	}

	/**
	 * The tap leaves the world: liquid experience in its buffer comes out as orbs where it stood, so breaking it
	 * never swallows experience (a remainder below one point is lost). Any other fluid is lost with the tap, as
	 * from a bucket dropped in lava: a bucket's worth at most.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel server && isExperience(tank.fluid)) {
			int left = (int) (tank.amount / ExperienceFluid.MB_PER_POINT);
			tank.amount = 0;
			tank.fluid = FluidHolder.EMPTY;
			if (left > 0) {
				ExperienceOrb.award(server, Vec3.atCenterOf(pos), left);
			}
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		tank.save(output, TANK_KEY);
		output.putBoolean(TOGGLED_KEY, toggledOpen);
		output.putInt(POUR_COOLDOWN_KEY, pourCooldown);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		tank.load(input, TANK_KEY);
		toggledOpen = input.getBooleanOr(TOGGLED_KEY, false);
		pourCooldown = Math.max(0, input.getIntOr(POUR_COOLDOWN_KEY, 0));
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return saveCustomOnly(provider);
	}
}
