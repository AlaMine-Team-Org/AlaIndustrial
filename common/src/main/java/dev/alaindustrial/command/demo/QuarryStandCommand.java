package dev.alaindustrial.command.demo;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ala demo quarry build|clear|tp} (MOD-950): the quarry stand, one complete frame on its own arena. It hangs
 * under the op-only {@code /ala demo} subtree, so it has the same permission (level 2) and the same convention —
 * plain English literals for op output, no lang keys (MOD-058 task log).
 */
public final class QuarryStandCommand {
	private QuarryStandCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> tree() {
		return Commands.literal("quarry")
				.then(Commands.literal("build").executes(QuarryStandCommand::build))
				.then(Commands.literal("clear").executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					QuarryStand.clear(level, QuarryStand.findOrigin(level));
					ctx.getSource().sendSuccess(() -> Component.literal("Quarry stand cleared.")
							.withStyle(ChatFormatting.AQUA), true);
					return Command.SINGLE_SUCCESS;
				}))
				.then(Commands.literal("tp").executes(QuarryStandCommand::tp));
	}

	private static int build(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos origin = QuarryStand.findOrigin(level);
		QuarryStand.build(level, origin, ctx.getSource().getPlayer());
		BlockPos controller = QuarryStand.controller(origin);
		ctx.getSource().sendSuccess(() -> Component.literal("Quarry stand built at " + origin.toShortString()
				+ ": a 9 x 9 tier-1 frame (15 x 15 with its towers), controller head at "
				+ controller.toShortString()
				+ ". It digs with your drill bit; right-click the controller head for its screen. ")
				.withStyle(ChatFormatting.AQUA)
				.append(Component.literal("[tp]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/ala demo quarry tp"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("/ala demo quarry tp"))))), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int tp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerLevel level = ctx.getSource().getLevel();
		double[] at = QuarryStand.camera(QuarryStand.findOrigin(level));
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		// Yaw -45 looks south-east, across the frame from its north-west corner.
		ctx.getSource().getServer().getCommands().performPrefixedCommand(ctx.getSource(), String.format(Locale.ROOT,
				"tp %s %.1f %.1f %.1f -45 30", player.getScoreboardName(), at[0], at[1], at[2]));
		return Command.SINGLE_SUCCESS;
	}
}
