package dev.alaindustrial.item.misc;

import dev.alaindustrial.item.misc.ReinforcedBonemeal.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Reinforced bone meal (MOD-906): right-click a plant and it grows without the luck of ordinary bone meal.
 * The growth rules are {@link ReinforcedBonemeal}; this class only turns a click into a call and spends one
 * meal when something grew.
 *
 * <p>Its own {@code useOn}, no early right-click hook: an item's {@code useOn} runs on a sneaking click too,
 * and it is what foreign deployers and fake players call through a {@link UseOnContext} — a context whose
 * player may be {@code null}.
 */
public class ReinforcedBoneMealItem extends HintItem {

	public ReinforcedBoneMealItem(Properties properties) {
		super(properties, "item.alaindustrial.reinforced_bone_meal.hint",
				"item.alaindustrial.reinforced_bone_meal.hint2");
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction face = context.getClickedFace();
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		if (player != null && !player.mayUseItemAt(pos, face, stack)) {
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel server)) {
			return ReinforcedBonemeal.looksApplicable(level, pos, face) ? InteractionResult.SUCCESS
					: InteractionResult.PASS;
		}
		Outcome outcome = ReinforcedBonemeal.apply(server, pos, face, player, stack);
		if (outcome == Outcome.NOTHING) {
			return InteractionResult.PASS;
		}
		if (player != null) {
			stack.causeUseVibration(player, GameEvent.ITEM_INTERACT_FINISH);
		}
		// HANDLED_ELSEWHERE: the mod that took the event over owns the stack (NeoForge's own bone meal does not
		// shrink it either), so spending here would cost the player a second meal.
		if (outcome == Outcome.GREW) {
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS_SERVER;
	}
}
