package dev.alaindustrial.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.alaindustrial.compat.LineStructures;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * {@code /ala town edit <style> [piece]} (MOD-845): sets the pieces of an ALA town style out next to the player, each
 * with a structure block in SAVE mode set to the piece's template name, corner and size ({@link TownEditBench}), so
 * the owner edits a building in a test world, presses "Save" and takes the file into the mod with
 * {@code python tools/import_town_templates.py}. Registered only with the dev tree ({@link AlaCommandCommon}),
 * op-only; feedback is a plain English literal by the demo tree's convention.
 *
 * <p>{@code /ala town edit <style> <piece> intact} sets out the intact bench of one piece instead: the building the
 * drone restores the ruin to, saved as {@code alaindustrial:ala_town/<style>/intact/<piece>} and taken into the mod
 * with {@code python tools/import_town_templates.py --intact}; {@code ... compare} puts the ruin beside it as a
 * reference ({@link TownEditBench#planIntact}); {@code ... intact candidate <variant>} starts that bench from a
 * candidate written into the world by {@code tools/gen_town_house_candidates.py}
 * ({@link TownEditBench#planCandidate}).
 *
 * <p>{@code /ala town supplies} (0.1.219) is the branch of {@link TownSuppliesCommand}.
 */
final class TownEditCommand {

	/** Blocks between the player and the bench's first corner, along +x and +z. */
	private static final int AWAY = 3;

	private TownEditCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> tree() {
		return Commands.literal("town")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(TownSuppliesCommand.tree())
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
												StringArgumentType.getString(ctx, "piece")))
										.then(Commands.literal("intact").executes(ctx -> runIntact(ctx, false))
													.then(Commands.literal("candidate").then(Commands
															.argument("variant", StringArgumentType.word())
															.suggests((ctx, builder) -> SharedSuggestionProvider
																	.suggest(candidates(ctx), builder))
															.executes(TownEditCommand::runCandidate))))
										.then(Commands.literal("compare").executes(ctx -> runIntact(ctx, true))))));
	}

	private static StructureTemplateManager templates(CommandSourceStack source) {
		return LineStructures.templates(source.getServer());
	}

	private static int run(CommandSourceStack source, String style, String piece) {
		if (!known(source, style, piece)) {
			return 0;
		}
		List<String> all = TownEditBench.pieces(templates(source), style);
		BlockPos origin = BlockPos.containing(source.getPosition()).offset(AWAY, 0, AWAY);
		List<TownEditBench.Slot> slots = TownEditBench.plan(templates(source), style,
				piece == null ? all : List.of(piece));
		ServerLevel level = source.getLevel();
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

	/** The intact bench of one piece, with the ruin beside it as a reference when {@code withRuin}. */
	private static int runIntact(CommandContext<CommandSourceStack> ctx, boolean withRuin) {
		CommandSourceStack source = ctx.getSource();
		String style = StringArgumentType.getString(ctx, "style");
		String piece = StringArgumentType.getString(ctx, "piece");
		if (!known(source, style, piece)) {
			return 0;
		}
		List<TownEditBench.Slot> slots = TownEditBench.planIntact(templates(source), style, piece, withRuin);
		if (slots.isEmpty()) {
			source.sendFailure(Component.literal("The ruin of '" + style + "/" + piece + "' does not load."));
			return 0;
		}
		BlockPos origin = BlockPos.containing(source.getPosition()).offset(AWAY, 0, AWAY);
		ServerLevel level = source.getLevel();
		for (TownEditBench.Slot slot : slots) {
			BlockPos at = TownEditBench.place(level, origin, slot);
			String what = slot.mode() == StructureMode.LOAD ? "ruin (reference, LOAD mode)" : "intact";
			source.sendSuccess(() -> Component.literal("  " + slot.piece() + " " + what + ": structure block at "
					+ at.getX() + " " + at.getY() + " " + at.getZ()).withStyle(ChatFormatting.GRAY), false);
		}
		TownEditBench.Slot bench = slots.getLast();
		boolean continued = bench.template().equals(bench.saveAs());
		source.sendSuccess(() -> Component.literal("Placed the intact bench of '" + style + "/" + piece
				+ "', starting from " + (continued ? "the intact building saved so far" : "the ruin")
				+ ". Its frame is " + bench.size().getY() + " blocks high. Build the intact building in the frame, "
				+ "keep the jigsaw blocks, the core, the capsule and the hatch marker where they are, press SAVE in "
				+ "its structure block (north-west corner), then run python tools/import_town_templates.py --intact."
				+ (withRuin ? " The ruin to the west is a reference: its structure block is in LOAD mode, leave it."
						: "")).withStyle(ChatFormatting.AQUA), false);
		return slots.size();
	}

	private static List<String> candidates(CommandContext<CommandSourceStack> ctx) {
		return TownEditBench.candidates(templates(ctx.getSource()), StringArgumentType.getString(ctx, "style"),
				StringArgumentType.getString(ctx, "piece"));
	}

	/**
	 * {@code ... intact candidate <variant>}: the intact bench of one piece started from a candidate written into the
	 * world by {@code tools/gen_town_house_candidates.py}, with the ruin beside it as a reference.
	 */
	private static int runCandidate(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String style = StringArgumentType.getString(ctx, "style");
		String piece = StringArgumentType.getString(ctx, "piece");
		String variant = StringArgumentType.getString(ctx, "variant");
		if (!known(source, style, piece)) {
			return 0;
		}
		Identifier candidate = TownEditBench.candidate(style, piece, variant);
		List<TownEditBench.Slot> slots = TownEditBench.planCandidate(templates(source), style, piece, candidate);
		if (slots.isEmpty()) {
			source.sendFailure(Component.literal("No candidate " + candidate + " in this world. Write the candidates "
					+ "with python tools/gen_town_house_candidates.py --world <world folder>. Candidates of '" + style
					+ "/" + piece + "': " + String.join(", ", candidates(ctx)) + "."));
			return 0;
		}
		BlockPos origin = BlockPos.containing(source.getPosition()).offset(AWAY, 0, AWAY);
		ServerLevel level = source.getLevel();
		for (TownEditBench.Slot slot : slots) {
			BlockPos at = TownEditBench.place(level, origin, slot);
			String what = slot.mode() == StructureMode.LOAD ? "ruin (reference, LOAD mode)" : "candidate " + variant;
			source.sendSuccess(() -> Component.literal("  " + slot.piece() + " " + what + ": structure block at "
					+ at.getX() + " " + at.getY() + " " + at.getZ()).withStyle(ChatFormatting.GRAY), false);
		}
		source.sendSuccess(() -> Component.literal("Placed the intact bench of '" + style + "/" + piece
				+ "', starting from " + candidate + ". Finish the building in the frame, press SAVE in its structure "
				+ "block (north-west corner) to save it as " + TownEditBench.intact(style, piece)
				+ ", then run python tools/import_town_templates.py --intact. The ruin to the west is a reference: its "
				+ "structure block is in LOAD mode, leave it.").withStyle(ChatFormatting.AQUA), false);
		return slots.size();
	}

	/** Whether {@code style} has pieces and {@code piece} (when given) is one of them; tells the player if not. */
	private static boolean known(CommandSourceStack source, String style, String piece) {
		List<String> all = TownEditBench.pieces(templates(source), style);
		if (all.isEmpty()) {
			source.sendFailure(Component.literal("No ALA town style '" + style + "'. Styles: "
					+ String.join(", ", TownEditBench.styles(templates(source))) + "."));
			return false;
		}
		if (piece != null && !all.contains(piece)) {
			source.sendFailure(Component.literal("Style '" + style + "' has no piece '" + piece + "'. Pieces: "
					+ String.join(", ", all) + "."));
			return false;
		}
		return true;
	}
}
