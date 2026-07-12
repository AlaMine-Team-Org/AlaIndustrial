package dev.alaindustrial.item;

import dev.alaindustrial.entity.StockDisplayFrameEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemFrameItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Placement item for the Stock Display Frame (MOD-066). Vanilla {@link ItemFrameItem}/
 * {@code HangingEntityItem#useOn} hardcodes which entity it spawns (painting / item frame / glow
 * frame by identity check — verified against the decompiled 26.2 source) and silently spawns
 * <em>nothing</em> for any other {@code EntityType}, so this subclass reimplements {@code useOn}
 * with the same flow but constructing {@link StockDisplayFrameEntity}. {@code mayPlace} is
 * inherited from {@link ItemFrameItem} (frames may hang on all six faces, build-height checked).
 */
public class StockDisplayFrameItem extends ItemFrameItem {
	private final EntityType<StockDisplayFrameEntity> frameType;

	public StockDisplayFrameItem(EntityType<StockDisplayFrameEntity> frameType, Item.Properties properties) {
		super(frameType, properties);
		this.frameType = frameType;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		BlockPos clickedPos = context.getClickedPos();
		Direction clickedFace = context.getClickedFace();
		BlockPos placePos = clickedPos.relative(clickedFace);
		Player player = context.getPlayer();
		ItemStack itemInHand = context.getItemInHand();
		if (player != null && !this.mayPlace(player, clickedFace, itemInHand, placePos)) {
			return InteractionResult.FAIL;
		}
		Level level = context.getLevel();
		StockDisplayFrameEntity entity = new StockDisplayFrameEntity(this.frameType, level, placePos, clickedFace);
		EntityType.<HangingEntity>createDefaultStackConfig(level, itemInHand, player).apply(entity);
		if (!entity.survives()) {
			return InteractionResult.CONSUME;
		}
		if (!level.isClientSide()) {
			entity.playPlacementSound();
			level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
			level.addFreshEntity(entity);
		}
		itemInHand.shrink(1);
		return InteractionResult.SUCCESS;
	}
}
