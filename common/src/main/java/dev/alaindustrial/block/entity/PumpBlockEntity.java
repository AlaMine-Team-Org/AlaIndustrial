package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.EnergyTransactions;
import dev.alaindustrial.core.FluidAmounts;
import dev.alaindustrial.core.FluidHolder;
import dev.alaindustrial.core.FluidLookup;
import dev.alaindustrial.core.FluidMover;
import dev.alaindustrial.core.FluidPort;
import dev.alaindustrial.core.FluidPortHost;
import dev.alaindustrial.core.FluidTank;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV electric pump: an EU consumer with no GUI and no item slots. Each tick, if it holds enough EU and has
 * tank room, it acquires one bucket of lava from a lava source block directly below or orthogonally
 * adjacent (or pulls from an adjacent extractable {@link FluidPort}), then pushes its tank lava into any
 * adjacent insertable {@link FluidPort} (e.g. the geothermal generator's tank). Draws EU from the energy
 * network like other machines: it exposes a neutral {@link dev.alaindustrial.core.EnergyPort} with
 * {@code maxInsert > 0}, so the {@link dev.alaindustrial.core.EnergyNetwork} discovers it as a consumer
 * automatically.
 *
 * <p><b>MOD-028 multiloader migration.</b> Moved from {@code fabric} to {@code common}: the Fabric Fluid
 * Transfer API ({@code FluidVariant}/{@code Storage}/{@code Transaction}) is replaced by the neutral
 * {@link FluidTank}/{@link FluidPort}/{@link FluidLookup}/{@link EnergyTransactions}, so this class no
 * longer imports any loader-specific fluid or transaction type. Each loader supplies its own adapter
 * ({@code FabricFluidPort}/{@code NeoForgeFluidPort}) — see {@link FluidPort} class doc.
 */
public class PumpBlockEntity extends MachineBlockEntity implements FluidPortHost {
	/** Internal lava tank: 4 buckets, extractable from any side. */
	public static final long TANK_CAPACITY = FluidAmounts.BUCKET * 4;

	public final FluidTank fluidTank = new FluidTank(TANK_CAPACITY,
			fluid -> fluid.is(Fluids.LAVA),
			fluid -> true,
			this::setChanged);

	public PumpBlockEntity(BlockPos pos, BlockState state) {
		// EU consumer: maxInsert = tier voltage (so the network sees a consumer), maxExtract = 0.
		super(ModContent.PUMP_BE.get(), pos, state, EnergyTier.LV, 0,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
	}

	/** EU consumer: every face accepts energy, none emits (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.IN;
	}

	/** Every face exposes the same single tank — the pump has no per-face fluid restriction. */
	@Override
	public FluidPort fluidPort(Direction side) {
		return fluidTank;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		boolean worked = false;
		int euPerBucket = Math.max(1, Config.pumpEuPerBucket);

		// 1) Acquire lava if we have power and tank room.
		if (energy.amount >= euPerBucket && fluidTank.amount + FluidAmounts.BUCKET <= TANK_CAPACITY) {
			if (acquireLava(level, pos)) {
				energy.amount -= euPerBucket;
				worked = true;
			}
		}

		// 2) Push tank lava into adjacent insertable fluid ports (e.g. the geothermal tank).
		if (fluidTank.amount > 0) {
			worked |= pushLava(level, pos);
		}

		updateLit(worked);
		if (worked) {
			setChanged();
		}
		// The pump reacts to world fluid state (lava sources / adjacent tanks) that change without a
		// block-entity wake event, so it stays awake every tick rather than sleep (R-29).
		return 0;
	}

	/** Try to put one bucket of lava into the tank from a source block or an adjacent fluid port. */
	private boolean acquireLava(Level level, BlockPos pos) {
		FluidHolder lava = FluidHolder.of(Fluids.LAVA);
		// Source block directly below or orthogonally adjacent.
		for (Direction dir : Direction.values()) {
			BlockPos np = pos.relative(dir);
			if (level.getFluidState(np).isSourceOfType(Fluids.LAVA)) {
				boolean[] acquired = {false};
				EnergyTransactions.get().runCommitting(txn -> {
					long inserted = fluidTank.insert(lava, FluidAmounts.BUCKET, txn);
					acquired[0] = inserted >= FluidAmounts.BUCKET;
				});
				if (acquired[0]) {
					level.setBlockAndUpdate(np, Blocks.AIR.defaultBlockState());
					return true;
				}
			}
		}
		// Otherwise pull from an adjacent extractable fluid port.
		for (Direction dir : Direction.values()) {
			BlockPos np = pos.relative(dir);
			FluidPort src = FluidLookup.get().find(level, np, dir.getOpposite());
			if (src == null || !src.supportsExtraction()) {
				continue;
			}
			long[] moved = {0};
			EnergyTransactions.get().runCommitting(
					txn -> moved[0] = FluidMover.move(src, fluidTank, lava, FluidAmounts.BUCKET, txn));
			if (moved[0] > 0) {
				return true;
			}
		}
		return false;
	}

	/** Push tank lava into any adjacent insertable fluid port. */
	private boolean pushLava(Level level, BlockPos pos) {
		boolean moved = false;
		FluidHolder lava = FluidHolder.of(Fluids.LAVA);
		for (Direction dir : Direction.values()) {
			if (fluidTank.amount <= 0) {
				break;
			}
			BlockPos np = pos.relative(dir);
			FluidPort dst = FluidLookup.get().find(level, np, dir.getOpposite());
			if (dst == null || !dst.supportsInsertion()) {
				continue;
			}
			long amountToPush = fluidTank.amount;
			long[] pushed = {0};
			EnergyTransactions.get().runCommitting(
					txn -> pushed[0] = FluidMover.move(fluidTank, dst, lava, amountToPush, txn));
			if (pushed[0] > 0) {
				moved = true;
			}
		}
		return moved;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("FluidTankMb", fluidTank.amount);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		// MOD-028: prefer the new mB-valued key; fall back to the legacy Fabric v0.1.0 droplet-valued
		// "FluidTank" key, converting ÷81 (81000 droplets/bucket ÷ 81 = 1000 mB/bucket, exact — machine
		// transactions always move whole buckets, so legacy values are always bucket-multiples).
		long amount = input.getLong("FluidTankMb")
				.orElseGet(() -> input.getLong("FluidTank")
						.map(dr -> dr / FluidAmounts.FABRIC_DROPLETS_PER_MB).orElse(0L));
		fluidTank.amount = Math.max(0L, Math.min(TANK_CAPACITY, amount));
		fluidTank.fluid = fluidTank.amount > 0 ? FluidHolder.of(Fluids.LAVA) : FluidHolder.EMPTY;
	}
}
