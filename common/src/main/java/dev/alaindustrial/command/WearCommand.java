package dev.alaindustrial.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.alaindustrial.block.entity.AbstractGeneratorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * {@code /ala wear <percent>} (MOD-654): sets the durability left in the rotor / wheel of the wind or water mill
 * the player is looking at, as a percent of the part's current max, so the worn-part warning (creak and smoke)
 * can be checked without waiting for the wear. Registered only with the dev tree ({@link AlaCommandCommon}),
 * op-only; feedback is a plain English literal by the same op/diagnostic convention as the demo tree.
 */
final class WearCommand {
	private WearCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> tree() {
		return Commands.literal("wear")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
						.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "percent"))));
	}

	private static int run(CommandSourceStack source, int percent) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		HitResult hit = player.pick(player.blockInteractionRange() + 4.0, 1.0f, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
				|| !(player.level().getBlockEntity(blockHit.getBlockPos())
						instanceof AbstractGeneratorBlockEntity mill)) {
			source.sendFailure(Component.literal("Look at a wind mill or a water mill."));
			return 0;
		}
		int left = mill.setWornComponentRemainingPercent(percent);
		if (left < 0) {
			source.sendFailure(Component.literal("That generator has no rotor / wheel to wear (slot empty?)."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Rotor / wheel durability left: " + left + " point(s) (" + percent
				+ "% of its current max).").withStyle(ChatFormatting.AQUA), false);
		return Command.SINGLE_SUCCESS;
	}
}
