package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Silver Chest block — the tier above the iron chest: a 45-slot GUI (5 rows of 9). All shared chest
 * behaviour (double-chest pairing, waterlogging, comparator, automation access, shape, tickers)
 * lives in {@link AbstractModChestBlock}; this class only supplies the tier's codec, block entity
 * and double-window title. Only same-tier chests pair: {@code chestCanConnectTo} is
 * {@code state.is(this)}, so a silver half never joins an iron or gold one.
 *
 * <p>Rendering: same 3D chest model + animated lid as the iron chest, textured with
 * {@code entity/chest/silver.png} (single) and {@code silver_left/right.png} (double halves).
 */
public class SilverChestBlock extends AbstractModChestBlock {
	public static final MapCodec<SilverChestBlock> CODEC = simpleCodec(SilverChestBlock::new);

	public SilverChestBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SilverChestBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected BlockEntityType<? extends AbstractChestBlockEntity> chestBlockEntityType() {
		return (BlockEntityType<SilverChestBlockEntity>) ModContent.SILVER_CHEST_BE.get();
	}

	@Override
	protected Component defaultDoubleName() {
		return Component.translatable("container.alaindustrial.silver_chest_double");
	}
}
