package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.block.entity.DiamondChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Diamond Chest block (MOD-599) — the tier above electrum: 108 slots behind the same six-row
 * scrolling window, and the first chest of the mod that shrugs off explosions.
 *
 * <p><b>Blast resistance 1200</b> (the ancient-debris figure) is set in {@code BLOCK_PROPS}. At that
 * value an explosion ray loses its whole budget on the first block it meets, so TNT and creepers —
 * charged ones included — leave the chest and its contents alone.
 *
 * <p><b>It is not invulnerable, and the docs must not say so.</b> A wither's ram is gated by the
 * {@code minecraft:wither_immune} tag rather than by resistance, and ancient debris is not in that
 * tag either. Joining the tag was considered and rejected: a chest nothing can break would make the
 * shielding tier pointless.
 *
 * <p>All shared chest behaviour (pairing, waterlogging, comparator, automation, shape, tickers)
 * lives in {@link AbstractModChestBlock}; only same-tier chests pair, so a diamond double is 216
 * slots.
 */
public class DiamondChestBlock extends AbstractModChestBlock {
	public static final MapCodec<DiamondChestBlock> CODEC = simpleCodec(DiamondChestBlock::new);

	public DiamondChestBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DiamondChestBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected BlockEntityType<? extends AbstractChestBlockEntity> chestBlockEntityType() {
		return (BlockEntityType<DiamondChestBlockEntity>) ModContent.DIAMOND_CHEST_BE.get();
	}

	@Override
	protected Component defaultDoubleName() {
		return Component.translatable("container.alaindustrial.diamond_chest_double");
	}
}
