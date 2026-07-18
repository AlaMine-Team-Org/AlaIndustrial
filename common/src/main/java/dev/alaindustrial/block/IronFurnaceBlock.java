package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.IronFurnaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Iron Furnace block — a fuel-burning smelter one tier above the vanilla stone furnace. Extends
 * vanilla {@link AbstractFurnaceBlock}, so it inherits {@code facing}+{@code lit} placement, the
 * right-click-to-open behaviour ({@code useWithoutItem} → {@link #openContainer}), redstone
 * comparator output, and rotation/mirror for free. Only three things are custom: the block entity
 * ({@link IronFurnaceBlockEntity}, a hand-rolled smelter — see that class for why we don't extend
 * {@code AbstractFurnaceBlockEntity}), the server ticker that drives it, and the codec.
 *
 * <p>The GUI is the stock vanilla furnace screen (the BE hands back a plain {@link
 * net.minecraft.world.inventory.FurnaceMenu}); only the title differs.
 */
public class IronFurnaceBlock extends AbstractFurnaceBlock {
	public static final MapCodec<IronFurnaceBlock> CODEC = simpleCodec(IronFurnaceBlock::new);
	/** Alias of the vanilla furnace {@code lit} property, so the BE and light-level helper read one name. */
	public static final BooleanProperty LIT = AbstractFurnaceBlock.LIT;

	public IronFurnaceBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends AbstractFurnaceBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new IronFurnaceBlockEntity(pos, state);
	}

	@Override
	protected void openContainer(Level level, BlockPos pos, Player player) {
		if (level.getBlockEntity(pos) instanceof IronFurnaceBlockEntity furnace) {
			player.openMenu(furnace);
		}
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// Server only — the smelting loop runs on the server; the GUI reads progress via ContainerData.
		// The type guard is an instanceof at call time because ModContent suppliers are wildcard-typed.
		if (level.isClientSide()) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (lvl instanceof ServerLevel server && be instanceof IronFurnaceBlockEntity furnace) {
				IronFurnaceBlockEntity.serverTick(server, pos, st, furnace);
			}
		};
	}
}
