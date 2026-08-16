package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ElectricHeaterBlock;
import dev.alaindustrial.block.HeaterGlow;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.heat.HeatConsumer;
import dev.alaindustrial.menu.ElectricHeaterMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Demand-driven LV heat source. It spends EU only through {@link #consumeHeatTick()}, called by the
 * {@link HeatConsumer} directly above that is about to advance one useful processing tick — the
 * Vulcanizer, or since MOD-424 the Thermal Centrifuge.
 *
 * <p><b>Warm-up (MOD-418).</b> A cold heater is not a heat source at all: it must spend
 * {@link Config#electricHeaterWarmupTicks} ticks lighting itself before the machine above can run, and
 * it runs at full tier-3 from the first working tick thereafter. It cools back at half that rate once
 * nothing is waiting on it.
 *
 * <p>Warming is its own draw, not a rider on the machine's paid ticks — it has to be, because until it
 * finishes there are no paid ticks to ride on. What keeps that from breaking the block's founding
 * promise is {@link #hasWorkPending}: the heater lights up only while a machine above is genuinely
 * blocked on heat, so one sitting under nothing, or under a machine with no inputs, still costs exactly
 * zero and stays cold.
 */
public final class ElectricHeaterBlockEntity extends MachineBlockEntity implements MenuProvider {
	/** Seven-wide data: base 0..3 plus warm-up, rate and status. */
	public static final int DATA_COUNT = MachineBlockEntity.DATA_COUNT + 3;

	/** Warm-up as permille of {@link Config#electricHeaterWarmupTicks}. */
	public static final int DATA_HEAT = 4;
	/** EU per tick this heater would bill right now, including the chips of the machine above. */
	public static final int DATA_RATE = 5;
	/** {@link ElectricHeaterStatus} ordinal. */
	public static final int DATA_STATUS = 6;

	/**
	 * Two ticks keep the working state stable regardless of whether this BE or the Vulcanizer is ticked
	 * first. Each successful consumer call renews the lease.
	 */
	private int litLeaseTicks;

	/** Warm-up progress in paid ticks, 0..{@link Config#electricHeaterWarmupTicks}. Persisted. */
	private int heat;

	/** Parity counter for the half-rate cooling (heat drops one tick every two idle ticks). */
	private int coolParity;

	/** Readout channels, recomputed every server tick; server-authoritative, never set from outside. */
	private ElectricHeaterStatus status = ElectricHeaterStatus.COLD;
	private int costEuPerTick = Config.electricHeaterEuPerTick;

	public ElectricHeaterBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ELECTRIC_HEATER_BE.get(), pos, state, EnergyTier.LV, 0,
				Config.electricHeaterBuffer, EnergyTier.LV.maxVoltage(), 0L);
	}

	/**
	 * No upgrade panel — and this is load-bearing, not cosmetic. {@code MachineBlockEntity}'s constructor
	 * sizes the inventory as {@code slots + (this instanceof MenuProvider && hasUpgradePanel() ? 4 : 0)},
	 * so the moment this class gained a menu (MOD-418) the heater would silently have grown four slots:
	 * hoppers would start filling a support block, and the NBT layout would change under existing worlds.
	 * {@link ElectricHeaterMenu#hasUpgradePanel()} must keep answering the same, or the slot indices
	 * derived from it drift between client and server.
	 *
	 * <p>It would also be the wrong panel to offer: the chips that scale this block's rate live in the
	 * machine above (MOD-392), and a second set here would be two independent multipliers on one
	 * operation.
	 */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	public boolean canSupplyHeatTick() {
		return energy.getAmount() >= Config.electricHeaterEuPerTickEffective();
	}

	/** Whether the warm-up has finished, i.e. whether this heater supplies tier 3 rather than tier 2. */
	public boolean isHot() {
		return heat >= warmupTicks();
	}

	/** Warm-up as permille, the wire format for both the screen and {@link HeaterGlow}. */
	public int heatPermille() {
		return heat <= 0 ? 0 : Math.max(1, Math.min(heat * 1000 / warmupTicks(), 1000));
	}

	/**
	 * Atomically pays for one useful heat tick. A competing consumer can make this return false after
	 * discovery, so the Vulcanizer must only advance when it returns true.
	 */
	public boolean consumeHeatTick() {
		return consumeHeatTick(0);
	}

	/**
	 * As above, but billed at the overclocker multiple of the machine it heats (MOD-392). The heater has
	 * no upgrade panel of its own — see {@link #hasUpgradePanel()} — so the rate rides in from above.
	 *
	 * <p>This is also the only place the warm-up advances (MOD-418): a tick that was paid for is exactly
	 * a tick of real heating, so the ramp cannot run on energy nobody was billed for.
	 */
	public boolean consumeHeatTick(int overclockers) {
		int cost = heatTickCost(overclockers);
		if (energy.getAmount() < cost) {
			return false;
		}
		energy.drainInternal(cost);
		litLeaseTicks = 2;
		costEuPerTick = cost;
		// The warm-up does NOT advance here: this call only ever happens once the heater is already hot
		// (a cold one is not a heat source at all, so the machine above never gets this far). Heating is
		// its own idle draw in onServerTick.
		status = ElectricHeaterStatus.HOT;
		updateGlow();
		setChanged();
		wake();
		return true;
	}

	/**
	 * EU this heater bills for one heat tick at the given chip count.
	 *
	 * <p>Deliberately not routed through {@code effectiveEuPerTick}: that helper reads the chips in
	 * <em>this</em> block's own upgrade panel, and this block has none — the count belongs to the machine
	 * above and arrives as an argument.
	 */
	private int heatTickCost(int overclockers) {
		return Math.max(1, Math.round(Config.electricHeaterEuPerTickEffective()
				* (float) Math.pow(Config.overclockerEuFactor, Math.max(0, overclockers))));
	}

	private static int warmupTicks() {
		return Math.max(1, Config.electricHeaterWarmupTicks);
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// A lowered config value must not leave phantom heat behind.
		heat = Math.min(heat, warmupTicks());

		HeatConsumer consumer = level.getBlockEntity(pos.above()) instanceof HeatConsumer c ? c : null;
		costEuPerTick = heatTickCost(consumer != null ? consumer.overclockerCount() : 0);

		if (litLeaseTicks > 0) {
			litLeaseTicks--;
			status = ElectricHeaterStatus.HOT;
			updateGlow();
			return 0;
		}

		if (hasWorkPending(consumer)) {
			if (isHot()) {
				/*
				 * Hot with work pending but not being drawn from this tick: HOLD, spending nothing. This
				 * is the mod-wide "freeze, don't waste" rule, and here it is also what keeps the block
				 * from oscillating. Without it a Vulcanizer that is stocked but out of EU of its own
				 * would let the heater cool, go cold, get asked for heat again, and re-warm — burning a
				 * full 1200 EU ramp on a loop that never produces anything.
				 */
				status = ElectricHeaterStatus.HOT;
				updateGlow();
				return 0;
			}
			if (energy.getAmount() >= costEuPerTick) {
				energy.drainInternal(costEuPerTick);
				heat++;
				status = ElectricHeaterStatus.WARMING;
				setChanged();
				updateGlow();
				if (isHot()) {
					// Just crossed the line: the machine above went to sleep on NO_HEAT, so tell it
					// rather than leaving it to notice on its 40-tick safety poll.
					consumer.onHeatNeighbourChanged();
				}
				return 0;
			}
			status = ElectricHeaterStatus.NO_ENERGY;
			updateGlow();
			return 0;
		}

		// Nothing waiting on us: cool at half the warming rate, so a hopper pausing between batches
		// costs the player a slice of the ramp rather than all of it. Cooling is free.
		if (heat > 0) {
			coolParity++;
			if (coolParity >= 2) {
				coolParity = 0;
				heat--;
				setChanged();
			}
		}
		status = idleStatus(consumer);
		updateGlow();

		// Still warm means still cooling, and cooling is a tick-by-tick job — only a stone-cold heater
		// has nothing left to do and may sleep.
		return heat > 0 ? 0 : IDLE_SLEEP_TICKS;
	}

	/**
	 * Whether the machine above is waiting on heat right now — the single gate on both warming and
	 * holding, and therefore on every EU this block spends outside a served tick.
	 *
	 * <p>What counts as "waiting" is the consumer's own business — see
	 * {@link HeatConsumer#isWaitingOnHeat()}, which spells out that both "stocked and blocked only by us"
	 * and "already being served" must answer true, while missing inputs, a jammed output or a machine
	 * switched off at the lever must answer false. That is the block's founding promise — a heater with
	 * nothing to heat costs exactly zero — and the warm-up does not get to break it.
	 */
	private static boolean hasWorkPending(HeatConsumer consumer) {
		return consumer != null && consumer.isWaitingOnHeat();
	}

	private ElectricHeaterStatus idleStatus(HeatConsumer consumer) {
		if (consumer == null) {
			return ElectricHeaterStatus.NO_CONSUMER;
		}
		return heat > 0 ? ElectricHeaterStatus.COOLING : ElectricHeaterStatus.COLD;
	}

	/**
	 * Writes the visible temperature rung, skipping the write when it already agrees. Same mechanism as
	 * {@code updateLit}, which cannot be reused: it is typed against {@code BlockStateProperties.LIT} and
	 * this block deliberately shows four temperatures rather than two states (see {@link HeaterGlow}).
	 */
	private void updateGlow() {
		if (level == null || level.isClientSide()) {
			return;
		}
		BlockState state = getBlockState();
		if (!state.hasProperty(ElectricHeaterBlock.GLOW)) {
			return;
		}
		HeaterGlow next = HeaterGlow.forPermille(heatPermille());
		if (state.getValue(ElectricHeaterBlock.GLOW) != next) {
			level.setBlock(worldPosition, state.setValue(ElectricHeaterBlock.GLOW, next), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("Heat", heat);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		heat = Math.max(0, Math.min(input.getIntOr("Heat", 0), warmupTicks()));
	}

	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	/**
	 * Seven-wide data — hides {@link MachineBlockEntity#DATA_COUNT} on purpose so
	 * {@code ElectricHeaterBlockEntity.DATA_COUNT} always names <em>this</em> block's width, for the
	 * client stub in {@link ElectricHeaterMenu} as well (MOD-235).
	 */
	private final ContainerData heaterData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_HEAT -> heatPermille();
				case DATA_RATE -> costEuPerTick;
				case DATA_STATUS -> status.ordinal();
				default -> ElectricHeaterBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			// The three readout channels are derived and server-authoritative.
			if (index < DATA_HEAT) {
				ElectricHeaterBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return heaterData;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.electric_heater");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ElectricHeaterMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
