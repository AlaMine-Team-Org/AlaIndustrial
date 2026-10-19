package dev.alaindustrial.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.registry.ModDataRegistries;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.shift.ShiftWorkerLook;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ala worker [look]} (MOD-832): makes a shift worker where the player stands, the way the reception capsule
 * will (MOD-833) — a number from the ledger, a record, a town if the spot is inside a lit one — so the model, the
 * number, the glass and the walk can be looked at in a dev client before the capsule exists. A {@code /summon}ed
 * worker has no record and leaves the world on its first tick, by design. {@code look} picks one of the looks
 * ({@code window_f4}); without it the look follows from the number. Registered only with the dev tree
 * ({@link AlaCommandCommon}), op-only; feedback is a plain English literal by the demo tree's convention.
 */
final class ShiftWorkerCommand {
	private ShiftWorkerCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> tree() {
		return Commands.literal("worker")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> run(ctx.getSource(), null))
				.then(Commands.argument("look", StringArgumentType.word())
						.executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "look"))));
	}

	private static int run(CommandSourceStack source, String lookName) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getLevel();
		BlockPos pos = player.blockPosition();
		ShiftWorkerLook look = null;
		Identifier lookId = null;
		if (lookName != null) {
			lookId = Industrialization.id(lookName);
			look = level.registryAccess().lookupOrThrow(ModDataRegistries.SHIFT_WORKER_LOOK).getValue(lookId);
			if (look == null) {
				source.sendFailure(Component.literal("No shift worker look '" + lookId + "'."));
				return 0;
			}
		}
		int town = SettlementRegistry.get(level.getServer()).territoryAt(level.dimension(), pos)
				.map(Settlement::id).orElse(0);
		Optional<ShiftWorker> made = ShiftWorkers.spawn(level, pos, town);
		if (made.isEmpty()) {
			source.sendFailure(Component.literal("No shift worker could be made (no number left, or it could not "
					+ "join the world)."));
			return 0;
		}
		ShiftWorker worker = made.get();
		if (look != null) {
			ShiftWorkerBook book = ShiftWorkers.book(level.getServer());
			ShiftWorkerRecord record = book.byEntity(worker.getUUID()).orElseThrow().withLook(lookId, look.slim());
			book.put(record);
			worker.showRecord(record);
		}
		String number = worker.number();
		String where = town > 0 ? "of town #" + town : "of no town";
		source.sendSuccess(() -> Component.literal("Shift worker " + number + " (" + worker.lookId() + ") " + where
				+ ".").withStyle(ChatFormatting.AQUA), false);
		return Command.SINGLE_SUCCESS;
	}
}
