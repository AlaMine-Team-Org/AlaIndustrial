package dev.alaindustrial.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.alaindustrial.compat.LineStructures;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * {@code /ala town edit <style> [piece]} (MOD-845): sets the pieces of an ALA town style out next to the player, each
 * with a structure block in SAVE mode set to the piece's template name, corner and size ({@link TownEditBench}), so
 * the owner edits a building in a test world, presses "Save" and takes the file into the mod with
 * {@code python tools/import_town_templates.py}. Registered only with the dev tree ({@link AlaCommandCommon}),
 * op-only; feedback is a plain English literal by the demo tree's convention.
 */
final class TownEditCommand {

	/** Blocks between the player and the bench's first corner, along +x and +z. */
	private static final int AWAY = 3;

	private TownEditCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> tree() {
		return Commands.literal("town")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("edit")
						.then(Commands.argument("style", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
										TownEditBench.styles(templates(ctx.getSource())), builder))
								.executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "style"),
										null))
								.then(Commands.argument("piece", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
												TownEditBench.pieces(templates(ctx.getSource()),
														StringArgumentType.getString(ctx, "style")), builder))
										.executes(ctx -> run(ctx.getSource(),
												StringArgumentType.getString(ctx, "style"),
												StringArgumentType.getString(ctx, "piece"))))));
	}

	private static StructureTemplateManager templates(CommandSourceStack source) {
		return LineStructures.templates(source.getServer());
	}

	private static int run(CommandSourceStack source, String style, String piece) {
		ServerLevel level = source.getLevel();
		List<String> all = TownEditBench.pieces(templates(source), style);
		if (all.isEmpty()) {
			source.sendFailure(Component.literal("No ALA town style '" + style + "'. Styles: "
					+ String.join(", ", TownEditBench.styles(templates(source))) + "."));
			return 0;
		}
		if (piece != null && !all.contains(piece)) {
			source.sendFailure(Component.literal("Style '" + style + "' has no piece '" + piece + "'. Pieces: "
					+ String.join(", ", all) + "."));
			return 0;
		}
		BlockPos origin = BlockPos.containing(source.getPosition()).offset(AWAY, 0, AWAY);
		List<TownEditBench.Slot> slots = TownEditBench.plan(templates(source), style,
				piece == null ? all : List.of(piece));
		for (TownEditBench.Slot slot : slots) {
			BlockPos at = TownEditBench.place(level, origin, slot);
			source.sendSuccess(() -> Component.literal("  " + slot.piece() + ": structure block at " + at.getX()
					+ " " + at.getY() + " " + at.getZ()).withStyle(ChatFormatting.GRAY), false);
		}
		int count = slots.size();
		source.sendSuccess(() -> Component.literal("Placed " + count + " piece(s) of the ALA town style '" + style
				+ "'. Edit a piece, press SAVE in its structure block (north-west corner), then run "
				+ "python tools/import_town_templates.py.").withStyle(ChatFormatting.AQUA), false);
		return count;
	}
}
