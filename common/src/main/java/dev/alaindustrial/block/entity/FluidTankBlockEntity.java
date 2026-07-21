package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.item.FluidTankContents;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Passive, portable single-fluid storage (MOD-111). All faces expose the same neutral tank; loader
 * registries publish it through their native fluid capability.
 */
public final class FluidTankBlockEntity extends BlockEntity implements FluidPortHost {
	public final FluidTank fluidTank = new FluidTank(Config.fluidTankCapacity,
			fluid -> !fluid.isEmpty(), fluid -> true, this::tankChanged);

	public FluidTankBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.FLUID_TANK_BE.get(), pos, state);
	}

	@Override
	public FluidPort fluidPort(Direction side) {
		return fluidTank;
	}

	private void tankChanged() {
		setChanged();
		Level level = getLevel();
		if (level != null && !level.isClientSide()) {
			BlockState state = getBlockState();
			level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
			level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("FluidTankMb", fluidTank.amount);
		if (!fluidTank.fluid.isEmpty()) {
			output.putString("FluidTankFluid", BuiltInRegistries.FLUID.getKey(fluidTank.fluid.fluid()).toString());
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		applyStored(resolveFluid(input.getStringOr("FluidTankFluid", "")),
				input.getLongOr("FluidTankMb", 0L));
	}

	private void applyStored(Fluid fluid, long amount) {
		long clamped = Math.max(0L, Math.min(Config.fluidTankCapacity, amount));
		if (fluid == null || fluid == Fluids.EMPTY || clamped == 0) {
			fluidTank.fluid = FluidHolder.EMPTY;
			fluidTank.amount = 0;
			return;
		}
		fluidTank.fluid = FluidHolder.of(fluid);
		fluidTank.amount = clamped;
	}

	private static Fluid resolveFluid(String key) {
		Identifier id = Identifier.tryParse(key);
		if (id == null) {
			return Fluids.EMPTY;
		}
		Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
		return fluid == null ? Fluids.EMPTY : fluid;
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		if (!fluidTank.fluid.isEmpty() && fluidTank.amount > 0) {
			builder.set(ModDataComponents.FLUID_TANK_CONTENTS.get(),
					new FluidTankContents(fluidTank.fluid.fluid().builtInRegistryHolder(), fluidTank.amount));
			// Minecraft 26.2 stores stack size as a data component. A filled tank receives an explicit
			// max of one; a freshly crafted empty tank keeps the BlockItem default and stacks normally.
			builder.set(DataComponents.MAX_STACK_SIZE, 1);
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter getter) {
		super.applyImplicitComponents(getter);
		FluidTankContents contents = getter.get(ModDataComponents.FLUID_TANK_CONTENTS.get());
		if (contents == null) {
			applyStored(Fluids.EMPTY, 0);
		} else {
			applyStored(contents.fluid().value(), contents.amount());
		}
		// Placing a filled tank has to reach the client, or it stands there rendering empty until the
		// chunk reloads: the fluid is on the server (the comparator reads it, a bucket pulls it out),
		// the client just never hears. Vanilla places the block first and applies components after, so
		// the client builds an empty block entity and nothing tells it otherwise — setChanged() only
		// marks the chunk dirty. Blocks that merely store energy get away with this; this one draws
		// what it holds. Safe on the NBT-load path too: tankChanged() no-ops while the level is null.
		tankChanged();
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
