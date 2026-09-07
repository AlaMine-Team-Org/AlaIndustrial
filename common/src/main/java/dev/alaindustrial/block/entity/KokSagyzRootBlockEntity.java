package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.KokSagyzBlock;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The immutable original soil state of a root segment. No ticker or inventory. */
public class KokSagyzRootBlockEntity extends BlockEntity {

	private volatile BlockState soil = Blocks.DIRT.defaultBlockState();

	public KokSagyzRootBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.KOK_SAGYZ_ROOT_BE.get(), pos, state);
	}

	public BlockState soil() {
		return soil;
	}

	public static BlockState soilAt(BlockGetter level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof KokSagyzRootBlockEntity root
				? root.soil() : Blocks.DIRT.defaultBlockState();
	}

	public void setSoil(BlockState state) {
		soil = safeSoil(state);
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	private static BlockState safeSoil(BlockState state) {
		return KokSagyzBlock.isSoil(state) && !state.hasBlockEntity()
				? state : Blocks.DIRT.defaultBlockState();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("soil", BlockState.CODEC, soil);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		soil = safeSoil(input.read("soil", BlockState.CODEC).orElse(Blocks.DIRT.defaultBlockState()));
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return saveWithoutMetadata(provider);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
