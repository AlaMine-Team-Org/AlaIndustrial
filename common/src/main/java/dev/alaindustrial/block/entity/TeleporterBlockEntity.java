package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.TeleporterStationMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.teleporter.TeleporterRegistry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Teleporter station block entity (MOD-091) — an HV consumer that banks EU for a jump it cannot yet
 * perform. This task ships the "mute" station: it takes HV in, stores up to
 * {@link Config#teleporterBuffer} EU, and remembers who owns it and whether it is private. The jump
 * (MOD-092) will drain {@link #energy} in one lump sum from here — the target station pays, not the
 * remote — and the GUI (MOD-093) will expose the privacy toggle.
 *
 * <p>Deliberately not a {@code MenuProvider}: no GUI, no slots, and therefore no upgrade slots
 * either (the base only appends those to menu-bearing machines). It never sleeps — an idle station
 * still has to accept EU pushed by the network.
 */
public class TeleporterBlockEntity extends MachineBlockEntity {
	// Owner + name now live in the MachineBlockEntity base (MOD-133) — the station inherits setOwner /
	// getOwner / getOwnerName / isOwner and their NBT persistence ("Owner"/"OwnerName", same keys as
	// before, so existing stations load unchanged). Only the station-specific privacy flag stays here.
	/** Private (owner-only) by default; MOD-093 adds the toggle that flips it. */
	private boolean isPrivate = true;
	/**
	 * Whether a Random Jump Chip has been fitted (MOD-116) — the station's one permanent upgrade.
	 *
	 * <p>A flag rather than an inventory slot, because the station is deliberately not a
	 * {@code MenuProvider} and therefore has no upgrade panel (see the class javadoc): giving it one
	 * would hand it four hidden slots a hopper could stuff. The chip is consumed at fit time and is
	 * never returned, so there is nothing for a slot to hold anyway.
	 */
	private boolean hasRtpModule = false;

	/**
	 * How often charging reaches the {@link TeleporterRegistry}, at most, and by how much of the buffer the charge
	 * must have moved (MOD-628). The energy commit is the network's hot path, called for every delivery; the
	 * remote's screen needs a charge level, not a meter.
	 */
	private static final int REGISTRY_ENERGY_INTERVAL_TICKS = 100;
	private static final int REGISTRY_ENERGY_STEP_DIVISOR = 100;
	private static final long NEVER_RECORDED = Long.MIN_VALUE;
	private long registryRecordedAt = NEVER_RECORDED;
	private long registryEnergy = -1;

	public TeleporterBlockEntity(BlockPos pos, BlockState state) {
		// Consumer: HV in, nothing out. maxExtract = 0 — the network must never drain the station's
		// jump fund; the only thing that spends it is the jump itself (MOD-092), directly.
		super(ModContent.TELEPORTER_BE.get(), pos, state, EnergyTier.HV, 0,
				Config.teleporterBuffer, EnergyTier.HV.maxVoltage(), 0);
	}

	/**
	 * Never called in MOD-091: {@link dev.alaindustrial.block.TeleporterBlock#getTicker} returns no
	 * ticker, because the station has no per-tick work — the network pushes EU into {@link #energy}
	 * through the face ports on its own. The body stays here (rather than the block registering a
	 * ticker for nothing) so MOD-092 has one obvious place to put the jump countdown.
	 */
	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		return IDLE_SLEEP_TICKS;
	}

	/**
	 * Accepts EU on the five working faces; the {@code FACING} front is inert (R-NRG-03), so a cable
	 * draws no misleading arm toward the station's facade.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	/**
	 * The station is a storage sink: the network serves working machines first and fills the jump
	 * fund from what is left over (MOD-009).
	 *
	 * <p>This is not a formality — it is what stops the station from starving the rest of the base.
	 * Every other consumer in the mod is LV and asks for at most 32 EU/t; the station is the first
	 * HV block and asks for 512. {@link dev.alaindustrial.core.energy.EnergyShare#split} divides the supply
	 * <em>proportionally to demand</em>, so as a plain machine the station would win a 512:32 split
	 * and take ~94 % of a shared grid — the player would plug in a teleporter and watch every
	 * macerator stall. As a sink it charges from the surplus instead, which also matches what it is:
	 * a fund that banks EU, not a machine that does work.
	 *
	 * <p><b>What this used to cost (MOD-353).</b> This flag also meant the station created no
	 * {@code machineDemand}, so on a segment fed only by a Battery Box the backup-discharge stage never
	 * opened and the station charged at a hard, permanent zero — while an electric furnace on the same
	 * wiring filled normally. The comment here used to claim "it still fills fine"; that was true only of
	 * a generator or a flush-mounted box. Cable-fed storage now reaches it through
	 * {@link #storageFeedRate()}, at a flat rate and only above the donor's reserve.
	 */
	@Override
	public boolean isEnergyStorageSink() {
		return true;
	}

	/**
	 * MOD-353: the station accepts a slow trickle from stores over cable. Flat rate, capped by the
	 * donor's reserve — deliberately NOT the cascade, which stays closed to this block (MOD-314 R3)
	 * because equalising a 20 000 EU box against a 500 000 EU fund by fill fraction would empty the box.
	 */
	@Override
	public long storageFeedRate() {
		return dev.alaindustrial.Config.storageFeedRate;
	}

	public boolean isPrivate() {
		return isPrivate;
	}

	/** Flipped by the owner through the MOD-093 GUI; persisted and carried on the dropped item. */
	public void setPrivate(boolean value) {
		this.isPrivate = value;
		setChanged();
		recordInRegistry();
	}

	/** True once a Random Jump Chip has been fitted; without one the station refuses random jumps. */
	public boolean hasRtpModule() {
		return hasRtpModule;
	}

	/**
	 * Fit the module. One-way on purpose — the chip is consumed and there is no route back to it, so
	 * a player cannot move one upgrade around a base and pretend to have several.
	 */
	public void setRtpModule(boolean value) {
		this.hasRtpModule = value;
		setChanged();
		recordInRegistry();
	}

	/** True when {@code player} may bind to / jump to this station: its owner, or anyone if public. */
	public boolean allowsAccess(UUID player) {
		return !isPrivate || getOwner() == null || getOwner().equals(player);
	}

	// --- the station registry (MOD-628) ---
	//
	// The station tells the registry about itself whenever something the remote's screen shows changes. Every
	// hook below reads only this block entity's own fields, never the world, so none of them can re-enter the
	// chunk operation it is called from.

	/** Writes this station's current state to the {@link TeleporterRegistry}. Server side only; a no-op elsewhere. */
	public void recordInRegistry() {
		if (level instanceof ServerLevel serverLevel) {
			TeleporterRegistry.record(serverLevel, this);
			registryRecordedAt = serverLevel.getGameTime();
			registryEnergy = energy.getAmount();
		}
	}

	/**
	 * A station entering the world — placed, or loaded with its chunk. {@code LevelChunk.setBlockEntity} calls
	 * this on both loaders, which is how a station built before the registry existed gets its first record.
	 */
	@Override
	public void clearRemoved() {
		super.clearRemoved();
		recordInRegistry();
	}

	/**
	 * The capsule assembling or coming apart. Only the {@code FORMED} flag matters to the registry; the chunk calls
	 * this for any change of the same block's state, including the ones {@code TeleporterBlock#updateShape} makes,
	 * where the block itself cannot write.
	 */
	// Vanilla's own soft deprecation (MOD-498 kind A): marked in vanilla and the NeoForge patch alike, with no
	// replacement, and vanilla overrides it itself (HopperBlockEntity). It is the one place a same-block state
	// change reaches the block entity. The overridden method is what is deprecated, so the scope cannot narrow.
	@SuppressWarnings("deprecation")
	@Override
	public void setBlockState(BlockState state) {
		boolean wasFormed = dev.alaindustrial.block.TeleporterBlock.isFormed(getBlockState());
		super.setBlockState(state);
		if (wasFormed != dev.alaindustrial.block.TeleporterBlock.isFormed(state)) {
			recordInRegistry();
		}
	}

	/** Charging, throttled: see {@link #REGISTRY_ENERGY_INTERVAL_TICKS}. */
	@Override
	protected void onEnergyTransactionCommitted() {
		super.onEnergyTransactionCommitted();
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (registryRecordedAt != NEVER_RECORDED
				&& serverLevel.getGameTime() - registryRecordedAt < REGISTRY_ENERGY_INTERVAL_TICKS) {
			return;
		}
		long step = Math.max(1L, energy.getCapacity() / REGISTRY_ENERGY_STEP_DIVISOR);
		if (Math.abs(energy.getAmount() - registryEnergy) < step) {
			return;
		}
		recordInRegistry();
	}

	/**
	 * The three numbers the station's screen needs, for one specific viewer (MOD-093).
	 *
	 * <p>The fund crosses the wire as permille, never as raw EU: a vanilla {@code DataSlot} is a
	 * 16-bit short, and 500 000 would arrive negative — the exact bug the solar panel's evolution bar
	 * hit. {@code isOwner} is baked in per viewer because a {@code ContainerData} is opened per menu,
	 * so each player's screen can honestly grey out a toggle that is not theirs.
	 */
	public ContainerData stationData(UUID viewer) {
		return new ContainerData() {
			@Override
			public int get(int index) {
				return switch (index) {
					case TeleporterStationMenu.DATA_ENERGY_PERMILLE -> {
						long capacity = energy.getCapacity();
						yield capacity <= 0 ? 0
								: (int) Math.min(energy.getAmount() * 1000 / capacity, 1000);
					}
					case TeleporterStationMenu.DATA_PRIVATE -> isPrivate ? 1 : 0;
					case TeleporterStationMenu.DATA_IS_OWNER -> isOwner(viewer) ? 1 : 0;
					default -> 0;
				};
			}

			@Override
			public void set(int index, int value) {
				// Read-only: the privacy flag changes through the menu button, which re-checks
				// ownership server-side. A settable data slot would be a client-trusted write.
			}

			@Override
			public int getCount() {
				return TeleporterStationMenu.DATA_SIZE;
			}
		};
	}

	/**
	 * The screen's title, carrying the owner's name when there is one.
	 *
	 * <p>The name travels in the title rather than through the menu's synced data on purpose: a
	 * {@code ContainerData} moves ints only, and the block entity the menu holds is null on the
	 * client, so a getter reading it would always come up empty. Vanilla ships a menu's title to the
	 * client when the screen opens — the same route a renamed chest uses — so the snapshot taken at
	 * placement (see {@code setPlacedBy}) shows even for an owner who is offline.
	 *
	 * <p>Note this is NOT {@code MenuProvider#getDisplayName}: the station deliberately does not
	 * implement {@code MenuProvider}. {@link MachineBlockEntity} appends four upgrade slots to any BE
	 * that is one ({@code MachineBlockEntity:76}), and the station would silently become a container
	 * with four hidden slots a hopper could stuff. {@link dev.alaindustrial.block.TeleporterBlock}
	 * opens the menu itself instead.
	 */
	public Component menuTitle() {
		return getOwnerName().isEmpty()
				? Component.translatable("block.alaindustrial.teleporter")
				: Component.translatable("block.alaindustrial.teleporter.owned", getOwnerName());
	}

	// --- the capsule door's travel clock (MOD-112) ---
	//
	// Client-side only, like the reactor airlock's (ReactorDoorBlockEntity): nothing here is saved or
	// synced. The door's open state lives in the capsule cell's block state, which the client already
	// receives; this only remembers WHEN it last changed, so the renderer can draw the panels on their
	// way. A client that never saw the change draws the door parked at whichever end the state names.

	private static final long NO_DOOR_TRANSITION = Long.MIN_VALUE;
	private boolean doorSeen;
	private boolean doorWasOpen;
	private long doorTransitionStart = NO_DOOR_TRANSITION;
	/** Where the door stood when its current travel began: a reversed slide starts from here, not an end. */
	private float doorFrom;
	/**
	 * Server side: when the door last moved. A click while its panels are still travelling is ignored —
	 * the playtest's rapid clicking flipped the door every few ticks and the slide never got anywhere.
	 * Not saved: after a reload the door has long since stopped.
	 */
	private long doorToggledAt = NO_DOOR_TRANSITION;

	/** Whether a click may move the door at {@code gameTime}: not while it is still sliding. */
	public boolean doorMayToggle(long gameTime) {
		return doorToggledAt == NO_DOOR_TRANSITION || gameTime - doorToggledAt >= Config.teleporterCapsuleDoorSlideTicks;
	}

	/** Records that the door just moved; see {@link #doorMayToggle}. */
	public void markDoorToggled(long gameTime) {
		doorToggledAt = gameTime;
	}

	/** Keeps the clock while the capsule is off screen; see {@code TeleporterBlock#getTicker}. */
	public void clientTick(Level clientLevel) {
		observeDoor(dev.alaindustrial.block.TeleporterCapsuleBlock.isDoorOpen(clientLevel, worldPosition),
				clientLevel.getGameTime());
	}

	/**
	 * How far the door has sunk: {@code 0} shut, {@code 1} fully in the floor.
	 *
	 * <p>Game time and partial tick arrive apart, because a long-lived world's game time no longer fits a
	 * float precisely — the airlock's clock explains the stutter that adding them first produces.
	 */
	public float doorOpenness(long gameTime, float partialTicks) {
		boolean open = level != null
				&& dev.alaindustrial.block.TeleporterCapsuleBlock.isDoorOpen(level, worldPosition);
		// Caught here as well as in the ticker: a frame landing before the tick that would have noticed
		// the change must start the slide itself rather than draw its far end.
		observeDoor(open, gameTime);
		return doorValueAt(gameTime, partialTicks);
	}

	/**
	 * The door's position on its current travel. The travel runs from wherever the door stood when it
	 * began, and takes time in proportion to the distance left — a door reversed a quarter of the way
	 * down comes back in a quarter of the time instead of jumping to the far end and starting over.
	 */
	private float doorValueAt(long gameTime, float partialTicks) {
		float target = doorWasOpen ? 1.0f : 0.0f;
		float distance = Math.abs(target - doorFrom);
		if (doorTransitionStart == NO_DOOR_TRANSITION || distance < 1.0e-4f) {
			return target;
		}
		float span = Math.max(1.0f, Config.teleporterCapsuleDoorSlideTicks * distance);
		float t = net.minecraft.util.Mth.clamp(((float) (gameTime - doorTransitionStart) + partialTicks) / span,
				0.0f, 1.0f);
		float eased = t * t * (3.0f - 2.0f * t);
		return doorFrom + (target - doorFrom) * eased;
	}

	/** The first reading only takes the state: a door that comes into view already open does not slide. */
	private void observeDoor(boolean open, long gameTime) {
		if (!doorSeen) {
			doorSeen = true;
			doorWasOpen = open;
			doorFrom = open ? 1.0f : 0.0f;
			return;
		}
		if (open != doorWasOpen) {
			doorFrom = doorValueAt(gameTime, 0.0f);
			doorWasOpen = open;
			doorTransitionStart = gameTime;
		}
	}

	// --- persistence (26.2 ValueInput/ValueOutput) ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output); // base persists Energy/Progress/Items + Owner/OwnerName (MOD-133)
		output.putBoolean("Private", isPrivate);
		output.putBoolean("RtpModule", hasRtpModule);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		isPrivate = input.getBooleanOr("Private", true);
		// Default false: a station saved before MOD-116 simply has no module, which is the truth.
		hasRtpModule = input.getBooleanOr("RtpModule", false);
	}

	// R-BRK-07: the dropped station keeps its EU and its privacy flag, so breaking and re-placing a
	// charged station does not burn the fund. The owner is NOT carried — it is re-assigned to the
	// placer (see TeleporterBlock#setPlacedBy).
	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		if (energy.getAmount() > 0) {
			builder.set(ModDataComponents.STORED_ENERGY.get(), energy.getAmount());
		}
		if (!isPrivate) {
			builder.set(ModDataComponents.TELEPORTER_PRIVATE.get(), false);
		}
		// Written only when fitted, like the privacy flag above: an un-upgraded station keeps a bare
		// component map and therefore still stacks with a freshly crafted one.
		if (hasRtpModule) {
			builder.set(ModDataComponents.TELEPORTER_RTP_MODULE.get(), true);
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter getter) {
		super.applyImplicitComponents(getter);
		energy.setAmountUntracked(Math.min(getter.getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0L), energy.getCapacity()));
		isPrivate = getter.getOrDefault(ModDataComponents.TELEPORTER_PRIVATE.get(), true);
		hasRtpModule = getter.getOrDefault(ModDataComponents.TELEPORTER_RTP_MODULE.get(), false);
	}
}
