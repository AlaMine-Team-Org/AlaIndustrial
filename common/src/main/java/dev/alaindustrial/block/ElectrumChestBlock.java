package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.block.entity.ElectrumChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Electrum Chest block (MOD-409) — the tier above the gold chest: 81 slots behind a six-row
 * scrolling window. All shared chest behaviour (double-chest pairing, waterlogging, comparator,
 * automation access, shape, tickers) lives in {@link AbstractModChestBlock}; this class only
 * supplies the tier's codec, block entity and double-window title. Only same-tier chests pair.
 *
 * <p><b>A pair scrolls too, for free.</b> {@code AbstractModChestBlock} joins two of these into a
 * double the vanilla way, and {@code DoubleChestMenu} already reads its row count from the container
 * rather than from the tier — so a double electrum chest is 162 slots (eighteen rows) through the
 * same window, without a line of menu code here.
 *
 * <p>Rendering: same 3D chest model + animated lid as the other tiers, textured with
 * {@code entity/chest/electrum.png} (single) and {@code electrum_left/right.png} (double halves).
 */
public class ElectrumChestBlock extends AbstractModChestBlock {
	public static final MapCodec<ElectrumChestBlock> CODEC = simpleCodec(ElectrumChestBlock::new);

	public ElectrumChestBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ElectrumChestBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected BlockEntityType<? extends AbstractChestBlockEntity> chestBlockEntityType() {
		return (BlockEntityType<ElectrumChestBlockEntity>) ModContent.ELECTRUM_CHEST_BE.get();
	}

	@Override
	protected Component defaultDoubleName() {
		return Component.translatable("container.alaindustrial.electrum_chest_double");
	}
}
