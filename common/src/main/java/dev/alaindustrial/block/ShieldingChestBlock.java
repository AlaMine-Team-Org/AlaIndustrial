package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.block.entity.ShieldingChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shielding Chest (MOD-474) — a lead-lined container that stops the radiation of whatever is inside
 * it. Storage-wise it is the iron chest exactly: 36 slots, same block stats, same pair mechanic, same
 * sounds. The whole point of the block is the one thing that does NOT show in its inventory.
 *
 * <p><b>Why the capacity is not a tier up.</b> This is not the next rung of the iron → silver → gold →
 * electrum ladder and deliberately gives no storage advantage: it is bought for the shielding, and a
 * player who wants more room buys a bigger chest instead. Making it roomier as well would collapse two
 * separate decisions into one.
 *
 * <p><b>Where the shielding actually lives.</b> Not here. A chest is a dumb container, and the block
 * has no code that knows about radiation at all — the exemption is one line in
 * {@code RadiationSources.collectContainers}, which sweeps the world's containers and skips this block
 * entity's type. Keeping the rule at the source rather than on the block is what stops it from
 * silently drifting: there is exactly one place that decides what radiates and what does not.
 *
 * <p>Everything shared with the storage tiers — the double-chest pair mechanic, waterlogging,
 * comparator output, automation access, shape, tickers — lives in {@link AbstractModChestBlock}; this
 * class only supplies the codec, block entity and double-window title, like every other chest.
 */
public class ShieldingChestBlock extends AbstractModChestBlock {
	public static final MapCodec<ShieldingChestBlock> CODEC = simpleCodec(ShieldingChestBlock::new);

	public ShieldingChestBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ShieldingChestBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected BlockEntityType<? extends AbstractChestBlockEntity> chestBlockEntityType() {
		// The ModContent handle is wildcard-typed (see its javadoc); the registration binds this
		// exact type, so the narrowing is safe.
		return (BlockEntityType<ShieldingChestBlockEntity>) ModContent.SHIELDING_CHEST_BE.get();
	}

	@Override
	protected Component defaultDoubleName() {
		return Component.translatable("container.alaindustrial.shielding_chest_double");
	}
}
