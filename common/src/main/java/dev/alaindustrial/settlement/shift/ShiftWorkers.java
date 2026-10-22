package dev.alaindustrial.settlement.shift;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.registry.ModDataRegistries;
import dev.alaindustrial.registry.ModMobs;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.SettlementTerritory;
import dev.alaindustrial.settlement.ShiftWorkerHost;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.record.RecordNumberLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The shift workers' service (MOD-832): the one place that makes a worker, binds a loaded entity to its record, runs
 * the planner, decides who may hurt a worker and writes a death down. Server thread only.
 *
 * <p><b>A worker is made in one order:</b> a number from the ledger (MOD-827), a look from the number
 * ({@link LookPick}), the entity, the record under the entity's UUID in the towns' registry, and only then the
 * entity joins the world — so on its first tick the entity finds its record. Nothing else creates a worker; a worker
 * summoned by command has no record and leaves the world on its first tick (ADR-050 point 8).
 *
 * <p><b>The worker's town</b> ({@code SettlementRegistry}, MOD-831) is where its anchor is (the core), how far it
 * walks (never past the territory), whose it is (the town's owner, R34) and whether it works: a worker of a dark
 * core — out of power or taken away — works on its battery until it is empty, then hibernates (R68, MOD-854,
 * {@link ShiftWorkerBattery}). A worker of no town (number 0: a test, a stand) belongs to nobody and always works.
 */
public final class ShiftWorkers {

	/** The look given when the datapack has none at all — the first built-in one. */
	static final Identifier FALLBACK_LOOK = Industrialization.id("open_m1");

	private ShiftWorkers() {
	}

	/** The shift workers' book of {@code server}'s save: the towns' registry's {@code shift_workers}. */
	public static ShiftWorkerBook book(MinecraftServer server) {
		return SettlementRegistry.get(server).workers();
	}

	/**
	 * Makes a new worker standing at {@code pos}: issues its number, chooses its look, writes its record and adds it
	 * to the world. Its anchor is its town's core, or {@code pos} for a worker of no town. Empty when the world has
	 * no number left to give (logged by the ledger) or the entity could not join the world; in the second case the
	 * number is closed, so it is never handed out twice.
	 *
	 * @param settlement the worker's town number, 0 — none
	 */
	public static Optional<ShiftWorker> spawn(ServerLevel level, BlockPos pos, int settlement) {
		return spawn(level, Vec3.atBottomCenterOf(pos), level.getRandom().nextFloat() * 360.0F, settlement);
	}

	/**
	 * {@link #spawn(ServerLevel, BlockPos, int)} at an exact spot, looking along {@code yaw} — the reception capsule's
	 * way (MOD-833): the worker appears on the capsule's floor, facing out of its open front.
	 */
	public static Optional<ShiftWorker> spawn(ServerLevel level, Vec3 at, float yaw, int settlement) {
		RecordNumberLedger ledger = RecordNumberLedger.get(level.getServer());
		Optional<RecordNumber> issued = ledger.issue();
		if (issued.isEmpty()) {
			return Optional.empty();
		}
		RecordNumber number = issued.get();
		ShiftWorker worker = body(level, at, yaw);
		if (worker == null) {
			ledger.close(number);
			return Optional.empty();
		}
		Choice look = chooseLook(level, number, level.getBiome(BlockPos.containing(at)), null);
		long now = level.getGameTime();
		ShiftWorkerProfile.ServiceMark issuedMark = new ShiftWorkerProfile.ServiceMark(
				ShiftWorkerProfile.ServiceMark.Kind.ISSUED, now, List.of());
		ShiftWorkerRecord record = ShiftWorkerRecord.issued(number, worker.getUUID(), look.id(), look.slim(),
				settlement).withProfile(ShiftWorkerProfile.FRESH.issuedAt(now).marked(issuedMark).withBiography(
						ShiftWorkerProfiles.chooseBiography(level.getServer(), number, settlement, worker.getUUID())));
		if (!enter(level, worker, record, null)) {
			ledger.close(number);
			Industrialization.LOGGER.warn("[ala-towns] shift worker {} could not join the world at {}; its number "
					+ "is closed", number, at);
			return Optional.empty();
		}
		Industrialization.LOGGER.info("[ala-towns] worker restored {} at {}", number, BlockPos.containing(at));
		return Optional.of(worker);
	}

	/**
	 * Brings the dead worker with {@code number} back in a new body standing at {@code at} (R39, MOD-833): the same
	 * record — number, look, name, town — alive again. Only a worker whose record waits to come back (it died with
	 * permadeath off) and whose number the ledger still holds as a living one; empty otherwise, or when the body
	 * could not join the world, in which case the record waits as before.
	 */
	public static Optional<ShiftWorker> bringBack(ServerLevel level, Vec3 at, float yaw, RecordNumber number) {
		ShiftWorkerBook book = book(level.getServer());
		Optional<ShiftWorkerRecord> found = book.byNumber(number);
		if (found.isEmpty() || found.get().status() != ShiftWorkerRecord.Status.AWAITING_RETURN
				|| RecordNumberLedger.get(level.getServer()).statusOf(number) != RecordNumber.Status.LIVE) {
			return Optional.empty();
		}
		ShiftWorkerRecord waiting = found.get();
		ShiftWorker worker = body(level, at, yaw);
		if (worker == null) {
			return Optional.empty();
		}
		if (!enter(level, worker, returnedRecord(waiting, worker, level.getGameTime()), waiting)) {
			Industrialization.LOGGER.warn("[ala-towns] shift worker {} could not come back at {}; it waits on", number,
					at);
			return Optional.empty();
		}
		Industrialization.LOGGER.info("[ala-towns] worker brought back {} at {}", number, BlockPos.containing(at));
		return Optional.of(worker);
	}

	/**
	 * The record of a dead worker brought back in {@code body} (R39, R56): one level down — its last invested point
	 * taken back — with a full battery and a {@code RETURNED} mark naming the level it came back at.
	 */
	static ShiftWorkerRecord returnedRecord(ShiftWorkerRecord waiting, ShiftWorker body, long gameTime) {
		ShiftWorkerProfile down = waiting.profile().returnedOneLevelDown().withCharge(ShiftWorkerProfile.FULL);
		return waiting.returned(body.getUUID()).withProfile(down.marked(new ShiftWorkerProfile.ServiceMark(
				ShiftWorkerProfile.ServiceMark.Kind.RETURNED, gameTime, List.of(Integer.toString(down.level())))));
	}

	/** A new body standing at {@code at}, looking along {@code yaw}, at full health; null if the type made none. */
	@Nullable
	private static ShiftWorker body(ServerLevel level, Vec3 at, float yaw) {
		ShiftWorker worker = ModMobs.SHIFT_WORKER.type().create(level, EntitySpawnReason.TRIGGERED);
		if (worker == null) {
			return null;
		}
		worker.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		worker.setYHeadRot(yaw);
		worker.setYBodyRot(yaw);
		worker.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SettlementConfig.shiftWorkerMaxHealth);
		worker.setHealth(worker.getMaxHealth());
		return worker;
	}

	/**
	 * Writes {@code record} for {@code worker} — in place of {@code replaced}, the record of a returning worker's
	 * old body — puts the worker under its town and adds it to the world; on failure the book is as before.
	 */
	private static boolean enter(ServerLevel level, ShiftWorker worker, ShiftWorkerRecord record,
			@Nullable ShiftWorkerRecord replaced) {
		ShiftWorkerBook book = book(level.getServer());
		if (replaced != null) {
			book.remove(replaced.entity());
		}
		book.put(record);
		Optional<Settlement> town = town(level.getServer(), record.settlement());
		worker.joinTown(record.settlement(), town.map(Settlement::corePos).orElse(worker.blockPosition()),
				wanderRadius(town));
		worker.showRecord(record);
		ShiftWorkerProfiles.applyStats(worker, record.profile(), true);
		if (!level.addFreshEntity(worker)) {
			book.remove(worker.getUUID());
			if (replaced != null) {
				book.put(replaced);
			}
			return false;
		}
		return true;
	}

	/** How many living workers town {@code settlement} has — what its tier's limit is measured against. */
	public static int livingWorkers(MinecraftServer server, int settlement) {
		int living = 0;
		for (ShiftWorkerRecord record : book(server).all()) {
			if (record.settlement() == settlement && record.status() == ShiftWorkerRecord.Status.ALIVE) {
				living++;
			}
		}
		return living;
	}

	/** The dead of town {@code settlement} who wait to be brought back (R39), in number order. */
	public static List<RecordNumber> awaitingReturn(MinecraftServer server, int settlement) {
		List<RecordNumber> waiting = new ArrayList<>();
		for (ShiftWorkerRecord record : book(server).all()) {
			if (record.settlement() == settlement && record.status() == ShiftWorkerRecord.Status.AWAITING_RETURN) {
				waiting.add(record.number());
			}
		}
		waiting.sort(null);
		return waiting;
	}

	/** The capsule's way into this service (MOD-833): new workers and the dead brought back, of the capsule's town. */
	public static final ShiftWorkerHost CAPSULE_HOST = new ShiftWorkerHost() {
		@Override
		public int livingWorkers(MinecraftServer server, int settlementId) {
			return ShiftWorkers.livingWorkers(server, settlementId);
		}

		@Override
		public List<RecordNumber> awaitingReturn(MinecraftServer server, int settlementId) {
			return ShiftWorkers.awaitingReturn(server, settlementId);
		}

		@Override
		public Optional<RecordNumber> releaseNew(ServerLevel level, Vec3 standAt, float yaw, Settlement town) {
			return spawn(level, standAt, yaw, town.id()).map(worker -> RecordNumber.parse(worker.number()))
					.flatMap(number -> number);
		}

		@Override
		public boolean bringBack(ServerLevel level, Vec3 standAt, float yaw, RecordNumber number, Settlement town) {
			return ShiftWorkers.bringBack(level, standAt, yaw, number).isPresent();
		}
	};

	/**
	 * Looks the record of a loaded {@code worker} up and shows it to the clients; false when the entity must leave
	 * the world without a drop: it has no record (summoned by command, or its record was lost), its record is not
	 * alive (a copy that came back after its death, or one of a rolled-back chunk), or the ledger does not hold its
	 * number as a living worker's. A record whose look left the datapack gets a new one by the same rule, and a number
	 * marked for a new one gets it now, before the body shows any (R41, {@link ShiftWorkerRenumbering}). The
	 * worker takes its town's state: hibernating while the core is dark — the {@code NoAI} flag it was saved with
	 * is not what decides (ADR-050 point 8).
	 */
	public static boolean bind(ServerLevel level, ShiftWorker worker) {
		ShiftWorkerBook book = book(level.getServer());
		Optional<ShiftWorkerRecord> found = book.byEntity(worker.getUUID());
		if (found.isEmpty()) {
			Industrialization.LOGGER.info("[ala-towns] a shift worker without a record left the world at {}",
					worker.blockPosition());
			return false;
		}
		ShiftWorkerRecord record = found.get();
		if (worker.isDeadOrDying()) {
			return true; // killed before its first tick: let it fall; the death is already written down
		}
		RecordNumber.Status inLedger = RecordNumberLedger.get(level.getServer()).statusOf(record.number());
		if (record.status() != ShiftWorkerRecord.Status.ALIVE || inLedger != RecordNumber.Status.LIVE) {
			Industrialization.LOGGER.info("[ala-towns] shift worker {} is {} ({} in the ledger); its body left the "
					+ "world", record.number(), record.status().getSerializedName(), inLedger);
			return false;
		}
		if (look(level, record.look()).isEmpty()) {
			Choice again = chooseLook(level, record.number(), level.getBiome(worker.blockPosition()), record.slim());
			record = record.withLook(again.id());
			book.put(record);
		}
		record = ShiftWorkerRenumbering.onJoin(level, record);
		Optional<Settlement> town = town(level.getServer(), record.settlement());
		BlockPos anchor = town.map(Settlement::corePos)
				.orElse(worker.hasHome() ? worker.getHomePosition() : worker.blockPosition());
		worker.joinTown(record.settlement(), anchor, wanderRadius(town));
		worker.showRecord(record);
		ShiftWorkerProfiles.applyStats(worker, record.profile(), false);
		boolean hibernate = !townState(town, record.profile().chargeNow() > 0).powered();
		worker.setMode(hibernate ? ShiftMode.HIBERNATE : ShiftMode.IDLE);
		worker.setNoAi(hibernate);
		return true;
	}

	/** Runs the planner for {@code worker} on the ticks it thinks on (every 20 ticks, offset by its id). */
	public static void think(ServerLevel level, ShiftWorker worker) {
		if (!ShiftSchedule.thinksOn(worker.tickCount, worker.getId(), worker.mode())) {
			return;
		}
		ShiftWorkerBattery.settle(level, worker);
		ShiftSchedule.TownState state = townState(town(level.getServer(), worker.settlement()),
				ShiftWorkerBattery.charged(level.getServer(), worker));
		ShiftMode mode = ShiftSchedule.modeAt(level.getOverworldClockTime(), false, state);
		if (mode == worker.mode()) {
			return;
		}
		worker.setMode(mode);
		if (mode == ShiftMode.HIBERNATE) {
			worker.getNavigation().stop();
			worker.setNoAi(true);
			return;
		}
		worker.setHomeTo(worker.getHomePosition(), ShiftSchedule.strollRadius(mode, worker.wanderRadius()));
	}

	/**
	 * Town {@code settlement}'s core lit up again (called by the core when its town's {@code active} turns true): its
	 * loaded workers wake from hibernation where they stood (R10). A hibernating worker runs no goals, so cannot notice
	 * this itself — the town tells it, once, instead of every worker asking every tick.
	 */
	public static void townLit(ServerLevel level, int settlement) {
		if (settlement <= 0) {
			return;
		}
		for (ShiftWorkerRecord record : book(level.getServer()).all()) {
			if (record.settlement() == settlement && record.status() == ShiftWorkerRecord.Status.ALIVE) {
				Entity entity = level.getEntity(record.entity());
				if (entity instanceof ShiftWorker worker && worker.isNoAi() && !worker.isRemoved()) {
					worker.setMode(ShiftMode.IDLE);
					worker.setNoAi(false);
				}
			}
		}
	}

	/**
	 * The state town {@code town} gives a worker whose battery is {@code charged}: powered while the core is lit, or
	 * while the battery still holds charge (R68) — a dark core's worker works on until it is empty, then sleeps. No
	 * town (or one that is gone) — always running.
	 */
	static ShiftSchedule.TownState townState(Optional<Settlement> town, boolean charged) {
		return town.map(settlement -> new ShiftSchedule.TownState(settlement.active() || charged, true))
				.orElse(ShiftSchedule.TownState.RUNNING);
	}

	private static Optional<Settlement> town(MinecraftServer server, int settlement) {
		return settlement > 0 ? SettlementRegistry.get(server).find(settlement) : Optional.empty();
	}

	/**
	 * How far a worker of {@code town} walks: the knob, and never past the town's territory — no further from the core
	 * than its nearest edge ({@link Settlement#territoryReach()}), since a worker walks a circle around its anchor.
	 */
	private static int wanderRadius(Optional<Settlement> town) {
		int knob = SettlementConfig.shiftWorkerWanderRadius;
		return town.map(settlement -> Math.max(1, Math.min(knob, settlement.territoryReach()))).orElse(knob);
	}

	/**
	 * The territory of {@code worker}'s town, when it has a town in the worker's own level: the box a fleeing worker
	 * keeps to (MOD-824, {@code ShiftWorkerFleeGoal}). Empty for a worker of no town, of a town that is gone, or one
	 * standing in another dimension than its town.
	 */
	public static Optional<SettlementTerritory.Area> territory(ServerLevel level, ShiftWorker worker) {
		return town(level.getServer(), worker.settlement())
				.filter(settlement -> settlement.dimension().equals(level.dimension()))
				.map(Settlement::territory);
	}

	/** Asks the worker's town for one new path on this tick (ADR-050 point 11). */
	public static boolean tryPlanPath(ServerLevel level, ShiftWorker worker) {
		return tryPlanPath(level, worker.settlement());
	}

	/**
	 * Asks town {@code settlement} for one new path on this tick (ADR-050 point 11): the one entry point for a worker's
	 * walk and a builder drone's route alike (MOD-858), so one town plans at most the knob's paths a tick.
	 */
	public static boolean tryPlanPath(ServerLevel level, int settlement) {
		return book(level.getServer()).paths(settlement)
				.tryAcquire(level.getGameTime(), SettlementConfig.shiftWorkerPathsPerTick);
	}

	/**
	 * Whether {@code player} may hurt {@code worker} (R34): the owner of its town may, another player may not. A worker
	 * of no town, or of a town nobody has claimed, belongs to nobody, and anyone may.
	 */
	public static boolean mayHurt(ServerLevel level, ShiftWorker worker, Player player) {
		return town(level.getServer(), worker.settlement()).flatMap(Settlement::owner)
				.map(owner -> owner.equals(player.getUUID())).orElse(true);
	}

	/**
	 * Writes the death of {@code worker} down (R10a, R30, R39): a line of the hall's log always; with permadeath on
	 * the record is dead and the number closed for good, with it off the record waits to be brought back and the
	 * number stays the worker's. What the knob says at the moment of death decides.
	 */
	public static void recordDeath(ServerLevel level, ShiftWorker worker, DamageSource source) {
		ShiftWorkerBook book = book(level.getServer());
		Optional<ShiftWorkerRecord> found = book.byEntity(worker.getUUID());
		if (found.isEmpty() || found.get().status() != ShiftWorkerRecord.Status.ALIVE) {
			return;
		}
		ShiftWorkerRecord record = found.get();
		Identifier cause = source.typeHolder().unwrapKey().map(ResourceKey::identifier)
				.orElse(Identifier.withDefaultNamespace("generic"));
		Optional<Identifier> killer = Optional.ofNullable(source.getEntity())
				.map(entity -> EntityType.getKey(entity.getType()));
		ShiftWorkerRecord.Death death = new ShiftWorkerRecord.Death(cause, killer, level.getGameTime());
		boolean permadeath = SettlementConfig.shiftWorkerPermadeath;
		book.put(record.died(death, permadeath));
		book.addToLog(new ShiftWorkerBook.LogLine(record.settlement(), record.number(), record.name(), death));
		if (permadeath) {
			RecordNumberLedger.get(level.getServer()).close(record.number());
		}
		Industrialization.LOGGER.info("[ala-towns] worker {} died ({}), {}", record.number(), cause,
				permadeath ? "number closed" : "waiting to be brought back");
	}

	/** A chosen look: its id and whether its body is slim. */
	record Choice(Identifier id, boolean slim) {
	}

	/**
	 * The look of the worker with {@code number}, among the looks whose conditions {@code biome} meets and, when
	 * {@code slim} is given, of that body ({@link LookPick}). The fallback when nothing is left is
	 * {@link #FALLBACK_LOOK}.
	 */
	static Choice chooseLook(ServerLevel level, RecordNumber number, Holder<Biome> biome, @Nullable Boolean slim) {
		Optional<Registry<ShiftWorkerLook>> registry =
				level.registryAccess().lookup(ModDataRegistries.SHIFT_WORKER_LOOK);
		List<LookPick.Candidate> candidates = new ArrayList<>();
		if (registry.isPresent()) {
			for (Map.Entry<ResourceKey<ShiftWorkerLook>, ShiftWorkerLook> entry : registry.get().entrySet()) {
				ShiftWorkerLook look = entry.getValue();
				if ((slim == null || look.slim() == slim) && allows(look, biome)) {
					candidates.add(new LookPick.Candidate(entry.getKey().identifier().toString(), look.weight()));
				}
			}
		}
		Optional<String> picked = LookPick.pick(candidates, level.getSeed(), number.index());
		if (picked.isEmpty()) {
			Industrialization.LOGGER.warn("[ala-towns] no shift worker look fits {}; using {}", number, FALLBACK_LOOK);
			return new Choice(FALLBACK_LOOK, slim != null && slim);
		}
		Identifier id = Identifier.parse(picked.get());
		return new Choice(id, registry.get().getValue(id).slim());
	}

	private static boolean allows(ShiftWorkerLook look, Holder<Biome> biome) {
		return look.conditions().flatMap(ShiftWorkerLook.Conditions::biomes).map(biome::is).orElse(true);
	}

	/** The look registered under {@code id} in {@code level}'s datapack registry. */
	static Optional<ShiftWorkerLook> look(ServerLevel level, Identifier id) {
		return level.registryAccess().lookup(ModDataRegistries.SHIFT_WORKER_LOOK)
				.map(registry -> registry.getValue(id));
	}
}
