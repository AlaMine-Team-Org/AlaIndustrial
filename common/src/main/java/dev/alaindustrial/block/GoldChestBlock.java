package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gold Chest block — the tier above the silver chest: a 54-slot GUI (6 rows of 9). All shared chest
 * behaviour (double-chest pairing, waterlogging, comparator, automation access, shape, tickers)
 * lives in {@link AbstractModChestBlock}; this class only supplies the tier's codec, block entity
 * and double-window title. Only same-tier chests pair.
 *
 * <p>Rendering: same 3D chest model + animated lid as the other tiers, textured with
 * {@code entity/chest/gold.png} (single) and {@code gold_left/right.png} (double halves).
 */
public class GoldChestBlock extends AbstractModChestBlock {
	public static final MapCodec<GoldChestBlock> CODEC = simpleCodec(GoldChestBlock::new);

	public GoldChestBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GoldChestBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected BlockEntityType<? extends AbstractChestBlockEntity> chestBlockEntityType() {
		return (BlockEntityType<GoldChestBlockEntity>) ModContent.GOLD_CHEST_BE.get();
	}

	@Override
	protected Component defaultDoubleName() {
		return Component.translatable("container.alaindustrial.gold_chest_double");
	}
}
