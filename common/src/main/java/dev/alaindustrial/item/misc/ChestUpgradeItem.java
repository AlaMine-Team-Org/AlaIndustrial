package dev.alaindustrial.item.misc;

import dev.alaindustrial.block.ChestUpgrade;
import dev.alaindustrial.block.ChestUpgrades;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A chest upgrade (MOD-944): Shift+right-click on a chest of its rung raises that chest one tier in place,
 * contents and all. One item per rung of {@link ChestUpgrade}; the work itself is {@link ChestUpgrades}.
 *
 * <p>Without Shift a click on a chest opens it, as vanilla routes it to the block first; with Shift the
 * server skips the block and asks this item. The client only predicts the arm swing — the server decides.
 */
public class ChestUpgradeItem extends Item {
	private final ChestUpgrade upgrade;

	public ChestUpgradeItem(Properties properties, ChestUpgrade upgrade) {
		super(properties);
		this.upgrade = upgrade;
	}

	/** The rung this item climbs. */
	public ChestUpgrade upgrade() {
		return upgrade;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return upgrade.accepts(context.getLevel().getBlockState(context.getClickedPos()))
					? InteractionResult.SUCCESS
					: InteractionResult.FAIL;
		}
		return ChestUpgrades.upgrade(level, context.getClickedPos(), player, context.getItemInHand(),
				context.getClickedFace(), upgrade);
	}

	// MOD-498 — Item#appendHoverText is soft-deprecated by Mojang ("internal"), but it is the only hook an
	// item has for its own tooltip lines; see HintItem.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		adder.accept(Component.translatable("tooltip.alaindustrial.chest_upgrade.hint", upgrade.sourceName())
				.withStyle(ChatFormatting.GRAY));
		adder.accept(Component.translatable("tooltip.alaindustrial.chest_upgrade.hint2")
				.withStyle(ChatFormatting.GRAY));
		adder.accept(Component.translatable("tooltip.alaindustrial.chest_upgrade.hint3")
				.withStyle(ChatFormatting.GRAY));
	}
}
