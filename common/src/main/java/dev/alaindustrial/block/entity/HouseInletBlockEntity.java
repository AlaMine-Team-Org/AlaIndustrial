package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.HousePowerSource;
import dev.alaindustrial.settlement.TownPowerLinks;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The house inlet's buffer (MOD-876; epic MOD-824, decisions R7, R90): what a town house holds for the charging beds
 * inside it.
 *
 * <p><b>One face, inward only.</b> The inlet takes EU on its {@link HorizontalMachineBlock#FACING} face, the one that
 * looks out of the wall, and on no other; it gives nothing back to the grid ({@code maxExtract} 0). Power that came in
 * stays in the building: only the house's beds draw it, through {@link HousePowerSource}.
 *
 * <p><b>A store, not a machine.</b> Its EU goes to the workers asleep in the house, never into a machine, so it is an
 * {@link #isEnergyStorageSink() energy storage sink}: the line serves the working machines first and fills the inlet
 * from what is left. It takes no part in the store-to-store cascade, which would level a battery bank down into a
 * house nobody sleeps in.
 *
 * <p><b>Buffer.</b> A constant rather than a knob: 2000 EU, about one minute of an LV line, the same as the
 * settlement core's buffer. The charging beds of MOD-862 set what a night costs; the number is revisited there.
 *
 * <p><b>The town hall's inlet is the core's port</b> (MOD-861; epic decisions R106, R111). An inlet that stands where
 * its town's intact hall has one ({@link TownPowerLinks#hallCoreOf}) shows the line the core's buffer instead of its
 * own: what comes in charges the core, and the core gives the town's roads what it holds above its upkeep reserve
 * ({@link SettlementCoreBlockEntity#townPort}). A house inlet takes that energy through the storage feed
 * ({@link #storageFeedRate}), a flat trickle, since the port is a store to the line and not a generator.
 */
public final class HouseInletBlockEntity extends EnergyBlockEntity implements HousePowerSource {

	/** EU the inlet holds. */
	public static final long BUFFER = 2_000L;

	/** EU a tick a store on the line — the town hall's port among them — may trickle into a house inlet. */
	public static final long FEED_RATE = 8L;

	/** The core this inlet is the port of, when it stands in its town's hall; refreshed on its tick, not saved. */
	private @Nullable BlockPos townCore;

	public HouseInletBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.HOUSE_INLET_BE.get(), pos, state, EnergyTier.LV, BUFFER, EnergyTier.LV.maxVoltage(), 0L);
	}

	/** Takes EU on the outer face and nowhere else. */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		BlockState state = getBlockState();
		if (state.hasProperty(HorizontalMachineBlock.FACING)
				&& worldFace == state.getValue(HorizontalMachineBlock.FACING)) {
			return EnergyRole.IN;
		}
		return EnergyRole.NONE;
	}

	/** The town hall's inlet offers the core's buffer on its outer face; any other inlet its own. */
	@Override
	public @Nullable EnergyPort energyPort(Direction worldFace) {
		if (townCore != null && energyRoleForFace(worldFace) != EnergyRole.NONE && level != null
				&& level.isLoaded(townCore)
				&& level.getBlockEntity(townCore) instanceof SettlementCoreBlockEntity core) {
			return core.townPort();
		}
		return super.energyPort(worldFace);
	}

	/** The core this inlet is the port of, or null for a house's inlet. */
	public @Nullable BlockPos townCore() {
		return townCore;
	}

	/** A store on the line feeds the house at {@link #FEED_RATE}: the way the town's port reaches it. */
	@Override
	public long storageFeedRate() {
		return FEED_RATE;
	}

	/** Filled after the working machines; see the class doc. */
	@Override
	public boolean isEnergyStorageSink() {
		return true;
	}

	@Override
	public long storedEnergy() {
		return energy.getAmount();
	}

	@Override
	public long drawEnergy(long amount) {
		long taken = Math.min(Math.max(0L, amount), energy.getAmount());
		if (taken <= 0L) {
			return 0L;
		}
		energy.setAmountUntracked(energy.getAmount() - taken);
		setChangedQuietly();
		return taken;
	}

	/**
	 * Nothing to do on its own: the grid fills it and the beds drain it. Sleeps until a delivery wakes it, and on
	 * waking looks whether it is its town hall's port; a change makes the line look at its endpoints again.
	 */
	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel serverLevel) {
			BlockPos core = TownPowerLinks.hallCoreOf(serverLevel, pos);
			if (!Objects.equals(core, townCore)) {
				townCore = core;
				if (state.hasProperty(HorizontalMachineBlock.FACING)) {
					Direction outer = state.getValue(HorizontalMachineBlock.FACING);
					NetworkManager.onNeighbourChanged(serverLevel, pos.relative(outer));
				}
			}
		}
		return IDLE_SLEEP_TICKS;
	}
}
