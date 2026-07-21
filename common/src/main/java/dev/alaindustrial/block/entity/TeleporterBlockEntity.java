package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.TeleporterStationMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
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
	 * a fund that banks EU, not a machine that does work. It still fills fine — a jump costs
	 * ~10–20 k EU against a 500 k buffer, and a player who wants it charged now can put a generator
	 * or a battery box flush against it (the direct, cable-less path in
	 * {@link dev.alaindustrial.core.energy.DirectAdjacencyDistributor#distribute} ignores this flag entirely).
	 */
	@Override
	public boolean isEnergyStorageSink() {
		return true;
	}

	public boolean isPrivate() {
		return isPrivate;
	}

	/** Flipped by the owner through the MOD-093 GUI; persisted and carried on the dropped item. */
	public void setPrivate(boolean value) {
		this.isPrivate = value;
		setChanged();
	}

	/** True when {@code player} may bind to / jump to this station: its owner, or anyone if public. */
	public boolean allowsAccess(UUID player) {
		return !isPrivate || getOwner() == null || getOwner().equals(player);
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
								: (int) Math.min(energy.amount * 1000 / capacity, 1000);
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

	// --- persistence (26.2 ValueInput/ValueOutput) ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output); // base persists Energy/Progress/Items + Owner/OwnerName (MOD-133)
		output.putBoolean("Private", isPrivate);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		isPrivate = input.getBooleanOr("Private", true);
	}

	// R-BRK-07: the dropped station keeps its EU and its privacy flag, so breaking and re-placing a
	// charged station does not burn the fund. The owner is NOT carried — it is re-assigned to the
	// placer (see TeleporterBlock#setPlacedBy).
	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		if (energy.amount > 0) {
			builder.set(ModDataComponents.STORED_ENERGY.get(), energy.amount);
		}
		if (!isPrivate) {
			builder.set(ModDataComponents.TELEPORTER_PRIVATE.get(), false);
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter getter) {
		super.applyImplicitComponents(getter);
		energy.amount = Math.min(getter.getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0L), energy.getCapacity());
		isPrivate = getter.getOrDefault(ModDataComponents.TELEPORTER_PRIVATE.get(), true);
	}
}
