package dev.alaindustrial.command.demo.energy;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.alaindustrial.Industrialization;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ala demo energy build|clear|check|report|tp <bay>} (MOD-799): the energy test stand. It hangs under the
 * op-only {@code /ala demo} subtree, so it has the same permission (level 2) and the same convention — plain
 * English literals for op/diagnostic output, no lang keys (MOD-058 task log).
 */
public final class EnergyStandCommand {
	private EnergyStandCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> tree() {
		return Commands.literal("energy")
				.then(Commands.literal("build").executes(EnergyStandCommand::build))
				.then(Commands.literal("clear").executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					EnergyStand.clear(level, EnergyStand.findOrigin(level));
					ctx.getSource().sendSuccess(() -> Component.literal("Energy stand cleared.")
							.withStyle(ChatFormatting.AQUA), true);
					return Command.SINGLE_SUCCESS;
				}))
				.then(Commands.literal("check").executes(EnergyStandCommand::check))
				.then(Commands.literal("report").executes(EnergyStandCommand::report))
				.then(Commands.literal("tp").then(Commands.argument("bay", StringArgumentType.word())
						.suggests((ctx, builder) -> {
							EnergyStand.bayIds().forEach(builder::suggest);
							return builder.buildFuture();
						})
						.executes(EnergyStandCommand::tp)));
	}

	private static int build(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos origin = EnergyStand.findOrigin(level);
		int bays = EnergyStand.build(level, origin);
		int problems = EnergyStand.lastBuildProblems().size();
		ctx.getSource().sendSuccess(() -> Component.literal("Energy stand built at " + origin.toShortString()
				+ ": " + bays + " bays" + (problems == 0 ? "." : ", " + problems + " build problems (see log).")
				+ " Run /ala demo energy check to measure it. ").withStyle(ChatFormatting.AQUA)
				.append(link("[E01]", "/ala demo energy tp E01")), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int check(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		if (EnergyStandCheck.running()) {
			source.sendFailure(Component.literal("An energy stand check is already running."));
			return 0;
		}
		ServerLevel level = source.getLevel();
		BlockPos origin = EnergyStand.findOrigin(level);
		EnergyStandCheck.start(level, origin, EnergyStandCheck.WARMUP, EnergyStandCheck.WINDOW, report -> {
			Path file = null;
			try {
				file = report.write(source.getServer().getServerDirectory());
			} catch (IOException e) {
				Industrialization.LOGGER.warn("energy stand: could not write the report", e);
			}
			Path written = file;
			source.sendSuccess(() -> results(report, written), false);
		});
		source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"Energy stand check started: %d-tick warm-up, then %d ticks of measuring. Stay near the arena.",
				EnergyStandCheck.WARMUP, EnergyStandCheck.WINDOW)).withStyle(ChatFormatting.AQUA), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int report(CommandContext<CommandSourceStack> ctx) {
		EnergyStandReport last = EnergyStandCheck.last();
		if (last == null) {
			ctx.getSource().sendFailure(Component.literal("No energy stand check has run since the server started."));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> results(last, null), false);
		return Command.SINGLE_SUCCESS;
	}

	/** The summary, every bay as a clickable id (green PASS, red FAIL), then one line per failed bay. */
	static Component results(EnergyStandReport report, Path file) {
		MutableComponent msg = Component.literal(report.summary()).withStyle(ChatFormatting.AQUA);
		MutableComponent ids = Component.literal("\n");
		for (EnergyStandReport.BayResult bay : report.bays()) {
			ids.append(link(bay.id(), "/ala demo energy tp " + bay.id())
					.withStyle(bay.pass() ? ChatFormatting.GREEN : ChatFormatting.RED)).append(" ");
		}
		msg.append(ids);
		for (EnergyStandReport.BayResult bay : report.bays()) {
			if (!bay.pass()) {
				String known = EnergyStand.knownFinding(bay.id());
				msg.append(Component.literal("\nFAIL " + bay.id() + " " + bay.title() + ": " + bay.firstFailure()
						+ (known == null ? "" : " [known finding]")).withStyle(ChatFormatting.RED));
			}
		}
		if (file != null) {
			msg.append(Component.literal("\nReport: " + file.toAbsolutePath()).withStyle(ChatFormatting.GRAY));
		}
		return msg;
	}

	private static MutableComponent link(String text, String command) {
		return Component.literal(text).withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	private static int tp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String bay = StringArgumentType.getString(ctx, "bay");
		ServerLevel level = ctx.getSource().getLevel();
		double[] at = EnergyStand.camera(EnergyStand.findOrigin(level), bay);
		if (at == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown bay '" + bay + "'."));
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ctx.getSource().getServer().getCommands().performPrefixedCommand(ctx.getSource(), String.format(Locale.ROOT,
				"tp %s %.1f %.1f %.1f 180 45", player.getScoreboardName(), at[0], at[1], at[2]));
		EnergyStandReport last = EnergyStandCheck.last();
		EnergyStandReport.BayResult result = last == null ? null : last.bay(bay);
		String verdict = result == null ? "not checked yet"
				: (result.pass() ? "PASS" : "FAIL — " + result.firstFailure());
		ctx.getSource().sendSuccess(() -> Component.literal("Bay " + bay.toUpperCase(Locale.ROOT) + ": " + verdict)
				.withStyle(ChatFormatting.GRAY), false);
		return Command.SINGLE_SUCCESS;
	}
}
