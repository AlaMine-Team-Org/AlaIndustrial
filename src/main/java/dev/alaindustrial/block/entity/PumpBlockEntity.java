package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV electric pump: an EU consumer with no GUI and no item slots. Each tick, if it holds enough EU
 * and has tank room, it acquires one bucket of lava from a lava source block directly below or
 * orthogonally adjacent (or pulls from an adjacent extractable {@code FluidStorage.SIDED}), then
 * pushes its tank lava into any adjacent insertable {@code FluidStorage.SIDED} (e.g. the geothermal
 * generator's tank). Draws EU from the energy network like other machines: it exposes
 * {@code EnergyStorage.SIDED} with {@code maxInsert > 0}, so the {@link dev.alaindustrial.core.EnergyNetwork}
 * discovers it as a consumer automatically.
 */
public class PumpBlockEntity extends MachineBlockEntity {
	/** Internal lava tank: a few buckets, extractable from any side via {@code FluidStorage.SIDED}. */
	public static final long TANK_CAPACITY = FluidConstants.BUCKET * 4;

	public final SingleVariantStorage<FluidVariant> fluidTank = new SingleVariantStorage<>() {
		@Override
		protected FluidVariant getBlankVariant() {
			return FluidVariant.blank();
		}

		@Override
		protected long getCapacity(FluidVariant variant) {
			return TANK_CAPACITY;
		}

		@Override
		protected boolean canInsert(FluidVariant variant) {
			return variant.isOf(Fluids.LAVA);
		}

		@Override
		protected boolean canExtract(FluidVariant variant) {
			return true;
		}

		@Override
		protected void onFinalCommit() {
			setChanged();
		}
	};

	public PumpBlockEntity(BlockPos pos, BlockState state) {
		// EU consumer: maxInsert = tier voltage (so the network sees a consumer), maxExtract = 0.
		super(ModBlockEntities.PUMP, pos, state, EnergyTier.LV, 0,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
	}

	/** EU consumer: every face accepts energy, none emits (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.IN;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		boolean worked = false;
		int euPerBucket = Math.max(1, Config.pumpEuPerBucket);

		// 1) Acquire lava if we have power and tank room.
		if (energy.amount >= euPerBucket && fluidTank.amount + FluidConstants.BUCKET <= TANK_CAPACITY) {
			if (acquireLava(level, pos)) {
				energy.amount -= euPerBucket;
				worked = true;
			}
		}

		// 2) Push tank lava into adjacent insertable fluid storages (e.g. the geothermal tank).
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

	/** Try to put one bucket of lava into the tank from a source block or an adjacent fluid storage. */
	private boolean acquireLava(Level level, BlockPos pos) {
		// Source block directly below or orthogonally adjacent.
		for (Direction dir : Direction.values()) {
			BlockPos np = pos.relative(dir);
			if (level.getFluidState(np).isSourceOfType(Fluids.LAVA)) {
				try (Transaction tx = Transaction.openOuter()) {
					long inserted = fluidTank.insert(FluidVariant.of(Fluids.LAVA), FluidConstants.BUCKET, tx);
					if (inserted >= FluidConstants.BUCKET) {
						tx.commit();
						level.setBlockAndUpdate(np, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
						return true;
					}
					tx.abort();
				}
			}
		}
		// Otherwise pull from an adjacent extractable fluid storage.
		for (Direction dir : Direction.values()) {
			BlockPos np = pos.relative(dir);
			Storage<FluidVariant> src = FluidStorage.SIDED.find(level, np, dir.getOpposite());
			if (src == null || !src.supportsExtraction()) {
				continue;
			}
			try (Transaction tx = Transaction.openOuter()) {
				long moved = StorageUtil.move(src, fluidTank,
						v -> v.isOf(Fluids.LAVA), FluidConstants.BUCKET, tx);
				if (moved > 0) {
					tx.commit();
					return true;
				}
				tx.abort();
			}
		}
		return false;
	}

	/** Push tank lava into any adjacent insertable fluid storage. */
	private boolean pushLava(Level level, BlockPos pos) {
		boolean moved = false;
		for (Direction dir : Direction.values()) {
			if (fluidTank.amount <= 0) {
				break;
			}
			BlockPos np = pos.relative(dir);
			Storage<FluidVariant> dst = FluidStorage.SIDED.find(level, np, dir.getOpposite());
			if (dst == null || !dst.supportsInsertion()) {
				continue;
			}
			try (Transaction tx = Transaction.openOuter()) {
				long pushed = StorageUtil.move(fluidTank, dst,
						v -> v.isOf(Fluids.LAVA), fluidTank.amount, tx);
				if (pushed > 0) {
					tx.commit();
					moved = true;
				} else {
					tx.abort();
				}
			}
		}
		return moved;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("FluidTank", fluidTank.amount);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		long amount = input.getLongOr("FluidTank", 0L);
		fluidTank.amount = Math.max(0L, Math.min(TANK_CAPACITY, amount));
		fluidTank.variant = amount > 0 ? FluidVariant.of(Fluids.LAVA) : FluidVariant.blank();
	}
}
