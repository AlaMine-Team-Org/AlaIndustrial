package dev.alaindustrial.item;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.ceramic.QuenchPress;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The carbon briquette (MOD-590): fired in the alloy smelter, and split into ceramic plates by
 * quenching.
 *
 * <p>Right-clicking a water source quenches ONE briquette by hand — see {@link QuenchPress} for the
 * three ways and what each pays. The full yield needs the press (a piston aimed into water), which is
 * built out of vanilla blocks and handled by the piston hook, not by this class.
 *
 * <p>Modelled on the raytrace half of the empty {@code VacuumCapsuleItem}: {@code SOURCE_ONLY} so a
 * flowing edge does not count, plus the two permission checks a bucket also makes.
 */
public class CarbonBriquetteItem extends Item {

	public CarbonBriquetteItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack)) {
			return InteractionResult.PASS;
		}
		if (!QuenchPress.isQuenchWater(level, pos)) {
			return InteractionResult.PASS;
		}
		// The client predicts the swing; only the server mints plates, or the player sees them twice.
		if (level instanceof ServerLevel server) {
			QuenchPress.dropPlates(server, pos, QuenchPress.waterYield());
			QuenchPress.steam(server, pos);
			stack.consume(1, player);
			player.awardStat(Stats.ITEM_USED.get(this));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> lines, TooltipFlag flag) {
		for (String key : List.of("item.alaindustrial.carbon_briquette.hint",
				"item.alaindustrial.carbon_briquette.hint2")) {
			lines.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		}
		lines.accept(Component.translatable("item.alaindustrial.carbon_briquette.press",
				QuenchPress.pressYield(), Config.ceramicPressRedstoneCost, QuenchPress.waterYield())
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
