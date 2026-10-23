package dev.alaindustrial.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.registry.ModDataRegistries;
import dev.alaindustrial.settlement.ReceptionCapsuleRules;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.ShiftWorkerBattery;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.shift.ShiftWorkerLook;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import dev.alaindustrial.settlement.works.TownHousing;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /ala worker [look]} (MOD-832): makes a shift worker where the player stands, the way the reception capsule
 * will (MOD-833) — a number from the ledger, a record, a town if the spot is inside a lit one — so the model, the
 * number, the glass and the walk can be looked at in a dev client before the capsule exists. A {@code /summon}ed
 * worker has no record and leaves the world on its first tick, by design. {@code look} picks one of the looks
 * ({@code window_f4}); without it the look follows from the number. Registered only with the dev tree
 * ({@link AlaCommandCommon}), op-only; feedback is a plain English literal by the demo tree's convention.
 *
 * <p>{@code /ala worker xp <number> <eu>} (MOD-854) lifts a living worker for a test: {@code eu} counts into its
 * drained total as if it had left the battery ({@link ShiftWorkerBattery#train}) — levels, marks and free points as
 * from real work, no credit to the town's owner — and the reply gives the new level and free points.
 *
 * <p>{@code /ala worker spawn [force]} (0.1.219) restores a worker the capsule's way, without the capsule and its
 * price ({@link #spawn}); its replies go through lang keys, the owner's call for a test command players may see.
 */
public final class ShiftWorkerCommand {
	private static final String SPAWN_KEY = "command.alaindustrial.worker.spawn.";

	private ShiftWorkerCommand() {
	}

	/** The {@code worker} branch of the dev tree; public so a gametest can run it on a dispatcher of its own. */
	public static LiteralArgumentBuilder<CommandSourceStack> tree() {
		return Commands.literal("worker")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> run(ctx.getSource(), null))
				.then(Commands.literal("spawn")
						.executes(ctx -> spawn(ctx.getSource(), false))
						.then(Commands.literal("force").executes(ctx -> spawn(ctx.getSource(), true))))
				.then(Commands.literal("xp")
						.then(Commands.argument("number", StringArgumentType.word())
								.then(Commands.argument("eu", LongArgumentType.longArg(1))
										.executes(ctx -> xp(ctx.getSource(),
												StringArgumentType.getString(ctx, "number"),
												LongArgumentType.getLong(ctx, "eu"))))))
				.then(Commands.argument("look", StringArgumentType.word())
						.executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "look"))));
	}

	/** {@code /ala worker xp <number> <eu>}: the lift; a number that is no living worker's fails. */
	static int xp(CommandSourceStack source, String numberText, long eu) {
		ServerLevel level = source.getLevel();
		Optional<ShiftWorkerRecord> found = RecordNumber.parse(numberText)
				.flatMap(number -> ShiftWorkers.book(level.getServer()).byNumber(number));
		if (found.isEmpty()) {
			source.sendFailure(Component.literal("No shift worker '" + numberText + "'."));
			return 0;
		}
		if (found.get().status() != ShiftWorkerRecord.Status.ALIVE) {
			source.sendFailure(Component.literal("Shift worker " + numberText + " is not alive."));
			return 0;
		}
		ShiftWorkerProfile profile = ShiftWorkerBattery.train(level.getServer(), found.get(), eu, level.getGameTime())
				.profile();
		String number = found.get().number().toString();
		source.sendSuccess(() -> Component.literal("Shift worker " + number + ": +" + eu + " EU drained, level "
				+ profile.level() + ", free points " + profile.freePoints() + ".")
				.withStyle(ChatFormatting.AQUA), true);
		return Command.SINGLE_SUCCESS;
	}

	/**
	 * {@code /ala worker spawn [force]}: the reception capsule's own way to a new worker
	 * ({@link ShiftWorkers#CAPSULE_HOST}, then {@link TownHousing#settle}) two blocks in front of the player, for the
	 * town whose territory they stand in, at no price. Without {@code force} the capsule's rules decide
	 * ({@link ReceptionCapsuleRules#verdict}: a lit, claimed core, the tier's limit, a free house) and a refusal names
	 * the capsule's reason; {@code force} skips them, and outside every territory takes the nearest town.
	 */
	static int spawn(CommandSourceStack source, boolean force) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getLevel();
		BlockPos feet = player.blockPosition();
		Optional<Settlement> inside = SettlementRegistry.get(level.getServer()).territoryAt(level.dimension(), feet);
		Optional<Settlement> town = inside.isPresent() || !force ? inside
				: TownSuppliesCommand.nearestTown(level, feet, TownSuppliesCommand.NEAR_TOWN);
		if (town.isEmpty()) {
			source.sendFailure(Component.translatable(SPAWN_KEY + "no_town"));
			return 0;
		}
		if (!force) {
			ReceptionCapsuleRules.Verdict verdict = ReceptionCapsuleRules.verdict(situation(level, town.get()));
			if (verdict.blocking()) {
				source.sendFailure(Component.translatable(SPAWN_KEY + "refused", Component.translatable(
						"settlement.alaindustrial.capsule.status." + verdict.name().toLowerCase(Locale.ROOT))));
				return 0;
			}
		}
		Optional<RecordNumber> released = ShiftWorkers.CAPSULE_HOST.releaseNew(level, standSpot(level, player),
				player.getYRot() + 180.0F, town.get());
		if (released.isEmpty()) {
			source.sendFailure(Component.translatable(SPAWN_KEY + "failed"));
			return 0;
		}
		int id = town.get().id();
		TownHousing.settle(level, id);
		boolean housed = ShiftWorkers.book(level.getServer()).byNumber(released.get())
				.map(ShiftWorkerRecord::housed).orElse(false);
		String number = released.get().toString();
		source.sendSuccess(() -> Component.translatable(SPAWN_KEY + (housed ? "done_housed" : "done_homeless"),
				number, id).withStyle(ChatFormatting.AQUA), true);
		return Command.SINGLE_SUCCESS;
	}

	/** What the capsule would see for a new worker of {@code town} with its price paid. */
	private static ReceptionCapsuleRules.Situation situation(ServerLevel level, Settlement town) {
		int[] price = ReceptionCapsuleRules.materialPrice(false);
		long eu = ReceptionCapsuleRules.euPrice(false);
		return new ReceptionCapsuleRules.Situation(true, town.corePlaced(), true, town.active(),
				town.owner().isPresent(), ShiftWorkers.livingWorkers(level.getServer(), town.id()),
				town.tier().workerLimit(), eu, eu, price, price, true,
				TownHousing.roomForNewWorker(level, town.id()));
	}

	/**
	 * Where the new worker stands: two blocks in front of the player on a free cell with a floor, a block up or down
	 * if need be; the player's own spot when there is none.
	 */
	private static Vec3 standSpot(ServerLevel level, ServerPlayer player) {
		BlockPos ahead = player.blockPosition().relative(player.getDirection(), 2);
		for (int dy : new int[] {0, 1, -1}) {
			BlockPos cell = ahead.above(dy);
			BlockPos below = cell.below();
			if (level.getBlockState(cell).canBeReplaced() && level.getBlockState(cell.above()).canBeReplaced()
					&& level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				return Vec3.atBottomCenterOf(cell);
			}
		}
		return player.position();
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
