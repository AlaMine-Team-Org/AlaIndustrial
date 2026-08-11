package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Iron Chest block — a horizontal-facing storage chest that opens a 36-slot GUI (4 rows of 9) on
 * right-click and spills its contents when broken. Everything shared across the chest tiers — the
 * double-chest pair mechanic, waterlogging, comparator output, automation access, shape, tickers —
 * lives in {@link AbstractModChestBlock}; this class only supplies the tier's codec, block entity
 * and double-window title.
 *
 * <p>Rendering: a real 3D chest model with an animated lid (see {@code ChestBlockEntityRenderer}),
 * textured with {@code entity/chest/iron.png} (single) and {@code iron_left/right.png} (double
 * halves). The block model itself is a particle-only shell — only the BER geometry is seen; the
 * block's properties set {@code noOcclusion()} so neighbours keep their inward faces (no X-ray).
 *
 * <p>Inventory drop on break is handled by vanilla: {@code BlockEntity.preRemoveSideEffects} drops
 * any {@code Container} block entity's contents during {@code LevelChunk.setBlockState} — each half
 * of a double drops only its own items.
 */
public class IronChestBlock extends AbstractModChestBlock {
	public static final MapCodec<IronChestBlock> CODEC = simpleCodec(IronChestBlock::new);

	public IronChestBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new IronChestBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected BlockEntityType<? extends AbstractChestBlockEntity> chestBlockEntityType() {
		// The ModContent handle is wildcard-typed (see its javadoc); the registration binds this
		// exact type, so the narrowing is safe.
		return (BlockEntityType<IronChestBlockEntity>) ModContent.IRON_CHEST_BE.get();
	}

	@Override
	protected Component defaultDoubleName() {
		return Component.translatable("container.alaindustrial.iron_chest_double");
	}
}
