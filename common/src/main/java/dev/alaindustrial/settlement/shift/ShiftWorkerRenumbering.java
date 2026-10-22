package dev.alaindustrial.settlement.shift;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.registry.ModMobs;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.record.RecordNumberLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The hidden renumbering of a shift worker (R41 of MOD-824, MOD-834). A player who joins later, a town whose lab
 * generates later, or a town plaque may turn out to carry the number a living worker already holds; the ledger then
 * marks that number ({@link RecordNumberLedger#isDueForRenumbering}), and this service gives the worker a new one —
 * only when no player can see it ({@link RenumberWindow}). Server thread only.
 *
 * <p><b>What changes.</b> The number, in one move: the ledger issues the new number and closes the old one for good,
 * the record takes the new number and keeps the old one in its history ({@link ShiftWorkerRecord#formerNumbers}),
 * the town hall's log gets one dry line about it, and a body in the world shows the new number. The look, the body,
 * the name, the town and the status stay: the player knows the worker by its face and its name.
 *
 * <p><b>When.</b> Every {@value #SWEEP_INTERVAL} ticks the sweep looks at the marked numbers; a worker with no body in
 * the world is renumbered at once, one a player may see waits. A body loading after the mark is renumbered on its
 * first tick, before it shows any number ({@link ShiftWorkers#bind} calls {@link #onJoin}).
 */
public final class ShiftWorkerRenumbering {

	/** Ticks between two sweeps: one second. A world with no marked number pays one empty check per sweep. */
	public static final int SWEEP_INTERVAL = 20;

	/** The translation key of the hall's log line about a re-registration: the old number, then the new one. */
	public static final String LOG_LINE_KEY = "settlement.alaindustrial.hall_log.renumbered";

	private ShiftWorkerRenumbering() {
	}

	/**
	 * The server-tick hook ({@code ServerHookRoster.SERVER_TICK}): a sweep every {@value #SWEEP_INTERVAL} ticks.
	 *
	 * <p>A server with no overworld has no ledger to read (the ledger is seeded from the overworld's seed) and no
	 * worker to renumber, so it is skipped. That server exists: the NeoForge seam lane's ephemeral test server has no
	 * level at all, and an exception thrown from a tick hook stops it — every later test of the lane then gets a dead
	 * server.
	 */
	public static void tick(MinecraftServer server) {
		if (server.overworld() == null) {
			return;
		}
		if (server.getTickCount() % SWEEP_INTERVAL == 0) {
			sweep(server);
		}
	}

	/** One pass over the marked numbers: each worker nobody sees gets its new number. */
	public static void sweep(MinecraftServer server) {
		List<RecordNumber> due = RecordNumberLedger.get(server).dueForRenumbering();
		if (due.isEmpty()) {
			return;
		}
		ShiftWorkerBook book = ShiftWorkers.book(server);
		for (RecordNumber number : due) {
			book.byNumber(number).ifPresent(record -> tryRenumber(server, record));
		}
	}

	/**
	 * Gives the worker of {@code record} its new number, if its number is marked for one and no player can see it.
	 *
	 * @return the new number, or empty when nothing changed
	 */
	public static Optional<RecordNumber> tryRenumber(MinecraftServer server, ShiftWorkerRecord record) {
		if (!RecordNumberLedger.get(server).isDueForRenumbering(record.number())) {
			return Optional.empty();
		}
		ShiftWorker body = findBody(server, record.entity());
		if (body == null) {
			return renumber(server, record, null).map(ShiftWorkerRecord::number);
		}
		RenumberWindow.Body state = body.number().isEmpty() ? RenumberWindow.Body.JOINING : RenumberWindow.Body.SHOWN;
		if (!RenumberWindow.isOpen(state, body.getX(), body.getZ(), observers((ServerLevel) body.level()),
				sightRadius(server))) {
			return Optional.empty();
		}
		return renumber(server, record, body).map(ShiftWorkerRecord::number);
	}

	/**
	 * The record a body joining the world must show: {@code record}, or — when its number is marked — the record under
	 * its new number. Called by {@link ShiftWorkers#bind} before the body shows any number.
	 */
	static ShiftWorkerRecord onJoin(ServerLevel level, ShiftWorkerRecord record) {
		MinecraftServer server = level.getServer();
		if (!RecordNumberLedger.get(server).isDueForRenumbering(record.number())) {
			return record;
		}
		return renumber(server, record, null).orElse(record);
	}

	/**
	 * The words of a hall's log line about a re-registration — "Record K-482 re-registered as K-117." — or empty for a
	 * line about a death. What the town hall's log will show (its screen comes with the profile, stage 4).
	 */
	public static Optional<Component> logLineText(ShiftWorkerBook.LogLine line) {
		return line.renumbering().map(to -> Component.translatable(LOG_LINE_KEY,
				line.number().map(RecordNumber::toString).orElse(""), to.number().toString()));
	}

	/** Moves the worker of {@code record} to its new number; {@code body}, if any, shows it. The moved record. */
	private static Optional<ShiftWorkerRecord> renumber(MinecraftServer server, ShiftWorkerRecord record,
			@Nullable ShiftWorker body) {
		Optional<RecordNumber> fresh = RecordNumberLedger.get(server).renumber(record.number());
		if (fresh.isEmpty()) {
			return Optional.empty();
		}
		long now = server.overworld().getGameTime();
		ShiftWorkerRecord renumbered = record.renumbered(fresh.get());
		// MOD-854: the worker's own file says so too, as a service mark (old number, new number).
		ShiftWorkerRecord moved = renumbered.withProfile(renumbered.profile().marked(new ShiftWorkerProfile.ServiceMark(
				ShiftWorkerProfile.ServiceMark.Kind.RENUMBERED, now,
				List.of(record.number().toString(), fresh.get().toString()))));
		ShiftWorkerBook book = ShiftWorkers.book(server);
		book.put(moved);
		book.addToLog(ShiftWorkerBook.LogLine.renumbered(record.settlement(), record.number(), record.name(),
				new ShiftWorkerBook.Renumbering(fresh.get(), now)));
		if (body != null) {
			body.showRecord(moved);
		}
		Industrialization.LOGGER.info("[ala-towns] shift worker {} re-registered as {}", record.number(), fresh.get());
		return Optional.of(moved);
	}

	/** The loaded body with {@code entity} as UUID, in any level; null when none is in the world. */
	@Nullable
	private static ShiftWorker findBody(MinecraftServer server, UUID entity) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity found = level.getEntity(entity);
			if (found instanceof ShiftWorker worker && !worker.isRemoved()) {
				return worker;
			}
		}
		return null;
	}

	private static List<RenumberWindow.Observer> observers(ServerLevel level) {
		List<RenumberWindow.Observer> out = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			out.add(new RenumberWindow.Observer(player.getX(), player.getZ()));
		}
		return out;
	}

	/** How far a player can be and still be sent a shift worker: the game's own rule, plus the margin. */
	private static double sightRadius(MinecraftServer server) {
		int tracking = server.getScaledTrackingDistance(ModMobs.SHIFT_WORKER.type().clientTrackingRange() * 16);
		return RenumberWindow.sightRadius(tracking, server.getPlayerList().getViewDistance());
	}
}
