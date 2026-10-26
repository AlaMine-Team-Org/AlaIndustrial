package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.MobSpawns;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.entity.ShiftWorkerLookGoal;
import dev.alaindustrial.registry.ModDataRegistries;
import dev.alaindustrial.registry.ModMobs;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.record.RecordNumberLedger;
import dev.alaindustrial.settlement.shift.LookPick;
import dev.alaindustrial.settlement.shift.ShiftWorkerLook;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The shift worker (MOD-832, docs/mobs/shift_worker.md) on both gametest lanes: it exists, its number is its record's
 * and the ledger's, a death is written down and closes the number for good — or leaves the worker waiting with
 * permadeath off — a restart keeps everything, a copy that came back is not let in, and a stranger cannot hurt it.
 *
 * <p>Walking (the radius and "never stuck" invariants) and the tick cost of ten workers are in
 * {@code ShiftWorkerWalkScenarios}: they need room, these do not.
 */
public final class ShiftWorkerScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ShiftWorkerScenarios::isRegisteredWithoutANaturalSpawn, "shift_worker_is_registered"),
				RosterEntry.of(ShiftWorkerScenarios::numberOnTheBackIsTheRecordsAndTheLedgers,
						"shift_worker_number_is_its_record").ticks(40),
				RosterEntry.of(ShiftWorkerScenarios::deathClosesTheNumberForGood, "shift_worker_death_is_for_good")
						.ticks(60),
				RosterEntry.of(ShiftWorkerScenarios::withoutPermadeathTheDeadWaitToComeBack,
						"shift_worker_death_without_permadeath_waits"),
				RosterEntry.of(ShiftWorkerScenarios::countsAddUp, "shift_worker_counts_add_up"),
				RosterEntry.of(ShiftWorkerScenarios::recordAndBodySurviveARestart, "shift_worker_survives_a_restart")
						.ticks(40),
				RosterEntry.of(ShiftWorkerScenarios::aWorkerKeepsItsLook, "shift_worker_keeps_its_look").ticks(40),
				RosterEntry.of(ShiftWorkerScenarios::aWorkerWithoutARecordLeaves, "shift_worker_without_record_leaves")
						.ticks(40),
				RosterEntry.of(ShiftWorkerScenarios::strangersCannotHurtIt, "shift_worker_strangers_cannot_hurt_it"),
				RosterEntry.of(ShiftWorkerScenarios::creativeAndSpectatorPlayersAreIgnored,
						"shift_worker_ignores_creative_players"),
				RosterEntry.of(ShiftWorkerScenarios::aWorkerBelongsToItsTown, "shift_worker_belongs_to_its_town"),
				RosterEntry.of(ShiftWorkerScenarios::aDarkTownSleepsAndALitOneWakes,
						"shift_worker_hibernates_in_a_dark_town").ticks(80));

		private Roster() {}
	}

	private ShiftWorkerScenarios() {}

	/** Where a worker stands: on the stone floor {@link #floor} lays. */
	private static final BlockPos STAND = new BlockPos(3, 1, 3);

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** A new worker made the only way a worker is made, standing on the floor; fails the test when none is made. */
	private static ShiftWorker spawn(GameTestHelper helper, int settlement) {
		floor(helper);
		Optional<ShiftWorker> worker = ShiftWorkers.spawn(helper.getLevel(), helper.absolutePos(STAND), settlement);
		if (worker.isEmpty()) {
			throw helper.assertionException("ShiftWorkers.spawn made no worker");
		}
		return worker.get();
	}

	private static ShiftWorkerRecord record(GameTestHelper helper, ShiftWorker worker) {
		return ShiftWorkers.book(helper.getLevel().getServer()).byEntity(worker.getUUID())
				.orElseThrow(() -> helper.assertionException("no record for the worker " + worker.getUUID()));
	}

	private static RecordNumberLedger ledger(GameTestHelper helper) {
		return RecordNumberLedger.get(helper.getLevel().getServer());
	}

	/** The lines of the hall's log about {@code number}, the chronicle's events (MOD-886: arrived, settled) aside. */
	private static long logLines(GameTestHelper helper, RecordNumber number) {
		return ShiftWorkers.book(helper.getLevel().getServer()).log().stream()
				.filter(line -> line.about(number) && line.note().isEmpty()).count();
	}

	/**
	 * The type, its attributes, no natural spawn among the creatures of an Overworld biome, and the twenty-four
	 * built-in looks — the faces of MOD-828 and of MOD-851 under both helmets — loaded from the datapack registry,
	 * each with the body its face says.
	 *
	 * @implements MOD-832-REGISTERED — the shift worker exists on this loader; it never spawns on its own; its
	 *     looks are datapack data
	 */
	public static void isRegisteredWithoutANaturalSpawn(GameTestHelper helper) {
		EntityType<ShiftWorker> type = ModMobs.SHIFT_WORKER.type();
		helper.assertTrue(DefaultAttributes.hasSupplier(type), "the shift worker has no default attributes");
		helper.assertTrue(type.getCategory() == MobCategory.MISC, "the shift worker must be MISC, got "
				+ type.getCategory());
		for (MobCategory category : MobCategory.values()) {
			List<EntityType<?>> natural = MobSpawns.typesAt(helper.getLevel(), category, helper.absolutePos(STAND));
			helper.assertTrue(!natural.contains(type), "the shift worker spawns naturally as " + category);
		}
		Registry<ShiftWorkerLook> looks = helper.getLevel().registryAccess()
				.lookupOrThrow(ModDataRegistries.SHIFT_WORKER_LOOK);
		for (String helmet : new String[] {"open", "window"}) {
			for (String face : new String[] {"m1", "m2", "m3", "m4", "m5", "m6", "f1", "f2", "f3", "f4", "f5", "f6"}) {
				ShiftWorkerLook look = looks.getValue(Industrialization.id(helmet + "_" + face));
				helper.assertTrue(look != null, "the built-in look " + helmet + "_" + face + " is not loaded");
				helper.assertTrue(look.slim() == face.startsWith("f"), helmet + "_" + face + " has the wrong body");
				helper.assertTrue(look.helmet().getSerializedName().equals(helmet),
						helmet + "_" + face + " has the helmet " + look.helmet().getSerializedName());
			}
		}
		helper.succeed();
	}

	/**
	 * The number the clients draw on the back is the record's, and the ledger holds it as a living worker's; the look
	 * is a registered one with the record's body; on its first tick the entity finds its record and stays.
	 *
	 * @implements MOD-832-NUMBER — the number on the back matches the data
	 */
	public static void numberOnTheBackIsTheRecordsAndTheLedgers(GameTestHelper helper) {
		ShiftWorker worker = spawn(helper, 0);
		ShiftWorkerRecord record = record(helper, worker);
		helper.assertTrue(worker.number().equals(record.number().toString()),
				"the back says " + worker.number() + ", the record " + record.number());
		helper.assertTrue(ledger(helper).statusOf(record.number()) == RecordNumber.Status.LIVE,
				"the ledger holds " + record.number() + " as " + ledger(helper).statusOf(record.number()));
		helper.assertTrue(worker.lookId().equals(record.look().toString()), "the synced look " + worker.lookId()
				+ " is not the record's " + record.look());
		ShiftWorkerLook look = helper.getLevel().registryAccess().lookupOrThrow(ModDataRegistries.SHIFT_WORKER_LOOK)
				.getValue(record.look());
		helper.assertTrue(look != null, "the record's look " + record.look() + " is not registered");
		helper.assertTrue(look.slim() == record.slim() && worker.slim() == record.slim(),
				"body: look " + look.slim() + ", record " + record.slim() + ", synced " + worker.slim());
		helper.assertTrue(worker.isPersistenceRequired(), "a shift worker must never despawn");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!worker.isRemoved(), "a worker with a living record left the world on its first tick");
			worker.discard();
			helper.succeed();
		});
	}

	/**
	 * With permadeath on (the default, R30), a death closes the number for good: the record is dead, the ledger
	 * holds a tombstone, the hall's log has one line, the next three hundred numbers never repeat it — and a copy of
	 * the worker saved before its death is not let back into the world (a rolled-back chunk, ADR-050 point 8).
	 *
	 * @implements MOD-832-DEATH — after a death the number is not issued again and the log has its line
	 */
	public static void deathClosesTheNumberForGood(GameTestHelper helper) {
		ShiftWorker worker = spawn(helper, 0);
		CompoundTag before = save(helper, worker);
		RecordNumber number = record(helper, worker).number();
		worker.kill(helper.getLevel());
		ShiftWorkerRecord dead = record(helper, worker);
		helper.assertTrue(dead.status() == ShiftWorkerRecord.Status.DEAD, "the record is " + dead.status());
		helper.assertTrue(dead.death().isPresent() && dead.death().get().cause().getPath().equals("generic_kill"),
				"the death is written as " + dead.death());
		helper.assertTrue(ledger(helper).statusOf(number) == RecordNumber.Status.CLOSED,
				"the ledger holds the dead " + number + " as " + ledger(helper).statusOf(number));
		helper.assertTrue(logLines(helper, number) == 1, "the hall's log has " + logLines(helper, number)
				+ " lines about " + number);
		for (int i = 0; i < 300; i++) {
			Optional<RecordNumber> next = ledger(helper).issue();
			helper.assertTrue(next.isEmpty() || !next.get().equals(number), "the dead number " + number
					+ " was issued again");
			next.ifPresent(ledger(helper)::close);
		}
		helper.runAfterDelay(25, () -> {
			ShiftWorker copy = load(helper, before);
			helper.assertTrue(helper.getLevel().addFreshEntity(copy), "the copy could not be added");
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(copy.isRemoved(), "a copy of the dead " + number + " came back into the world");
				helper.succeed();
			});
		});
	}

	/**
	 * With permadeath off (R39) the worker dies, the log has its line, but the number stays the worker's: the record
	 * waits to be brought back and the ledger still holds it as live.
	 *
	 * @implements MOD-832-NO-PERMADEATH — permadeath is a server setting; off, the dead wait to come back
	 */
	public static void withoutPermadeathTheDeadWaitToComeBack(GameTestHelper helper) {
		ShiftWorker worker = spawn(helper, 0);
		RecordNumber number = record(helper, worker).number();
		try (ConfigOverrides overrides = ConfigOverrides.sync()) {
			overrides.set("shiftWorkerPermadeath", false);
			worker.kill(helper.getLevel());
		}
		ShiftWorkerRecord dead = record(helper, worker);
		helper.assertTrue(dead.status() == ShiftWorkerRecord.Status.AWAITING_RETURN, "the record is " + dead.status());
		helper.assertTrue(ledger(helper).statusOf(number) == RecordNumber.Status.LIVE,
				"with permadeath off the ledger holds " + number + " as " + ledger(helper).statusOf(number));
		helper.assertTrue(logLines(helper, number) == 1, "the hall's log has " + logLines(helper, number)
				+ " lines about " + number);
		helper.succeed();
	}

	/**
	 * The epic's invariants on four workers, two of them dead (one each way): living + dead = issued, log lines =
	 * dead, and every record's status agrees with the ledger.
	 *
	 * @implements MOD-832-COUNTS — living + dead = issued numbers; log lines = dead
	 */
	public static void countsAddUp(GameTestHelper helper) {
		List<ShiftWorker> workers = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			workers.add(spawn(helper, 0));
		}
		workers.get(0).kill(helper.getLevel());
		try (ConfigOverrides overrides = ConfigOverrides.sync()) {
			overrides.set("shiftWorkerPermadeath", false);
			workers.get(1).kill(helper.getLevel());
		}
		int living = 0;
		int dead = 0;
		long lines = 0;
		for (ShiftWorker worker : workers) {
			ShiftWorkerRecord record = record(helper, worker);
			RecordNumber.Status inLedger = ledger(helper).statusOf(record.number());
			switch (record.status()) {
				case ALIVE -> {
					living++;
					helper.assertTrue(inLedger == RecordNumber.Status.LIVE, record.number() + " alive, " + inLedger);
				}
				case AWAITING_RETURN -> {
					dead++;
					helper.assertTrue(inLedger == RecordNumber.Status.LIVE, record.number() + " waiting, " + inLedger);
				}
				case DEAD -> {
					dead++;
					helper.assertTrue(inLedger == RecordNumber.Status.CLOSED, record.number() + " dead, " + inLedger);
				}
			}
			lines += logLines(helper, record.number());
		}
		helper.assertTrue(living + dead == workers.size(), living + " living + " + dead + " dead != "
				+ workers.size() + " issued");
		helper.assertTrue(lines == dead, lines + " log lines for " + dead + " dead");
		workers.forEach(worker -> worker.discard());
		helper.succeed();
	}

	/**
	 * A restart: the registry goes to disk and back through the game's own storage and comes back equal; the entity
	 * goes to its saved form and back, finds its record on its first tick and shows the same number, look and body,
	 * with the same health.
	 *
	 * @implements MOD-832-RESTART — number, sex and look survive a restart of the world
	 * @covers R-PER-01
	 */
	public static void recordAndBodySurviveARestart(GameTestHelper helper) {
		ShiftWorker worker = spawn(helper, 0);
		worker.setHealth(13.0F);
		ShiftWorkerRecord record = record(helper, worker);
		String number = worker.number();
		String look = worker.lookId();
		boolean slim = worker.slim();
		SettlementRegistry reloaded = throughDisk(helper, SettlementRegistry.get(helper.getLevel().getServer()));
		Optional<ShiftWorkerRecord> back = reloaded.workers().byEntity(worker.getUUID());
		helper.assertTrue(back.isPresent() && back.get().equals(record), "the record came back as " + back
				+ ", saved " + record);
		CompoundTag saved = save(helper, worker);
		worker.discard();
		ShiftWorker copy = load(helper, saved);
		helper.assertTrue(helper.getLevel().addFreshEntity(copy), "the reloaded worker could not be added");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!copy.isRemoved(), "the reloaded worker left the world");
			helper.assertTrue(copy.number().equals(number) && copy.lookId().equals(look) && copy.slim() == slim,
					"after the reload: " + copy.number() + " " + copy.lookId() + " slim=" + copy.slim() + ", before: "
							+ number + " " + look + " slim=" + slim);
			helper.assertTrue(copy.getHealth() == 13.0F, "health after the reload " + copy.getHealth());
			copy.discard();
			helper.succeed();
		});
	}

	/**
	 * A worker keeps the look its record holds while that look is in the datapack, even when the look rule now gives
	 * its number another one — new built-in faces move most numbers (MOD-851, {@code LookPickTest}), and a worker
	 * given a face before them must keep it. The record is set to a look of the worker's body that the rule does not
	 * give its number today; the worker goes through a restart and binds to its record again on its first tick.
	 *
	 * @implements MOD-851-LOOK-KEPT — a worker's look does not change when new looks are added
	 */
	public static void aWorkerKeepsItsLook(GameTestHelper helper) {
		ShiftWorker worker = spawn(helper, 0);
		ShiftWorkerRecord record = record(helper, worker);
		Registry<ShiftWorkerLook> looks = helper.getLevel().registryAccess()
				.lookupOrThrow(ModDataRegistries.SHIFT_WORKER_LOOK);
		List<LookPick.Candidate> sameBody = new ArrayList<>();
		for (Map.Entry<ResourceKey<ShiftWorkerLook>, ShiftWorkerLook> entry : looks.entrySet()) {
			if (entry.getValue().slim() == record.slim()) {
				sameBody.add(new LookPick.Candidate(entry.getKey().identifier().toString(), entry.getValue().weight()));
			}
		}
		String today = LookPick.pick(sameBody, helper.getLevel().getSeed(), record.number().index()).orElseThrow();
		Identifier kept = sameBody.stream().map(LookPick.Candidate::id).filter(id -> !id.equals(today)).sorted()
				.findFirst().map(Identifier::tryParse)
				.orElseThrow(() -> helper.assertionException("rig: the body has one look only"));
		ShiftWorkerRecord given = record.withLook(kept, record.slim());
		ShiftWorkers.book(helper.getLevel().getServer()).put(given);
		worker.showRecord(given);
		CompoundTag saved = save(helper, worker);
		worker.discard();
		ShiftWorker copy = load(helper, saved);
		helper.assertTrue(helper.getLevel().addFreshEntity(copy), "the reloaded worker could not be added");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!copy.isRemoved(), "the reloaded worker left the world");
			Optional<Identifier> inRecord = ShiftWorkers.book(helper.getLevel().getServer()).byEntity(copy.getUUID())
					.map(ShiftWorkerRecord::look);
			helper.assertTrue(inRecord.equals(Optional.of(kept)) && copy.lookId().equals(kept.toString()),
					"the worker's look moved: record " + inRecord + ", synced " + copy.lookId() + "; it was given "
							+ kept + ", the rule gives its number " + today);
			copy.discard();
			helper.succeed();
		});
	}

	/**
	 * An entity of the type with no record — summoned by command, or its record lost — leaves the world on its first
	 * tick without a drop (ADR-050 point 8).
	 *
	 * @implements MOD-832-NO-RECORD — a shift worker without a record does not stay in the world
	 */
	public static void aWorkerWithoutARecordLeaves(GameTestHelper helper) {
		floor(helper);
		ShiftWorker stray = helper.spawn(ModMobs.SHIFT_WORKER.type(), STAND);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(stray.isRemoved(), "a shift worker without a record stayed in the world");
			ShiftWorkerBook book = ShiftWorkers.book(helper.getLevel().getServer());
			helper.assertTrue(book.byEntity(stray.getUUID()).isEmpty(), "the stray got a record");
			helper.succeed();
		});
	}

	/**
	 * R34: a player who does not own the worker's town cannot hurt it; the town's owner can, and so can damage that is
	 * not a player's. A worker of no town belongs to nobody and can be hurt by anyone.
	 *
	 * @implements MOD-832-R34 — other players' shift workers cannot be hurt by a player
	 */
	public static void strangersCannotHurtIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		ServerPlayer stranger = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		int town = town(helper, owner);
		ShiftWorker worker = spawn(helper, town);
		float full = worker.getHealth();
		helper.assertTrue(!worker.hurtServer(level, level.damageSources().playerAttack(stranger), 4.0F)
				&& worker.getHealth() == full, "a stranger hurt the worker: health " + worker.getHealth());
		helper.assertTrue(worker.hurtServer(level, level.damageSources().playerAttack(owner), 4.0F)
				&& worker.getHealth() < full, "the town's owner could not hurt the worker");
		// Not a mob's attack: that scales with the difficulty, and the test world may be peaceful. Any damage that is
		// not a player's is what R34 leaves alone. Stronger than the owner's blow, which still holds the cooldown.
		float hurt = worker.getHealth();
		helper.assertTrue(worker.hurtServer(level, level.damageSources().generic(), 8.0F)
				&& worker.getHealth() < hurt, "damage that is not a player's could not hurt the worker");
		ShiftWorker ownerless = ShiftWorkers.spawn(level, helper.absolutePos(new BlockPos(5, 1, 3)), 0)
				.orElseThrow();
		helper.assertTrue(ownerless.hurtServer(level, level.damageSources().playerAttack(stranger), 4.0F),
				"nobody owns this worker, yet a player could not hurt it");
		worker.discard();
		ownerless.discard();
		closeTown(helper, town);
		helper.succeed();
	}

	/**
	 * MOD-847: a worker takes no notice of a player in creative or spectator mode — as a hostile mob leaves a creative
	 * player alone. Its look goal never picks a creative player standing next to it, though the vanilla goal does pick
	 * that very player (so the player is findable and the refusal is the rule, not the rig); and the picking rule takes
	 * a survival player at the same spot and refuses a creative and a spectator one. Looking is the worker's only
	 * reaction to a player: its walk and its planner never read one.
	 *
	 * @implements MOD-847-CREATIVE — a shift worker ignores players in creative and spectator mode
	 */
	public static void creativeAndSpectatorPlayersAreIgnored(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ShiftWorker worker = spawn(helper, 0);
		Vec3 near = helper.absoluteVec(new Vec3(5.5, 1, 3.5));
		ServerPlayer creative = AlaGameTestHelper.mockPlayerInLevel(helper);
		creative.setPos(near);
		helper.assertTrue(creative.isCreative(), "the in-level mock is creative");

		ShiftWorkerLookGoal goal = new ShiftWorkerLookGoal(worker);
		LookAtPlayerGoal vanilla = new LookAtPlayerGoal(worker, Player.class, ShiftWorkerLookGoal.LOOK_DISTANCE);
		boolean vanillaLooked = false;
		for (int i = 0; i < TRIES; i++) {
			helper.assertTrue(!goal.canUse(), "the worker picked a creative player to look at");
			vanillaLooked |= vanilla.canUse();
		}
		helper.assertTrue(vanillaLooked, "the vanilla goal never picked the creative player in " + TRIES
				+ " tries: the rig does not show the refusal is the worker's own");

		ServerPlayer survival = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		ServerPlayer detachedCreative = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
		ServerPlayer spectator = (ServerPlayer) helper.makeMockServerPlayer(GameType.SPECTATOR);
		for (ServerPlayer player : List.of(survival, detachedCreative, spectator)) {
			player.setPos(near);
		}
		helper.assertTrue(goal.wouldLookAt(level, survival), "the worker does not notice a survival player");
		helper.assertTrue(!goal.wouldLookAt(level, detachedCreative), "the worker notices a creative player");
		helper.assertTrue(!goal.wouldLookAt(level, spectator), "the worker notices a spectator");
		helper.assertTrue(!ShiftWorkerLookGoal.notices(creative) && !ShiftWorkerLookGoal.notices(spectator)
				&& ShiftWorkerLookGoal.notices(survival), "notices() disagrees with the game modes");
		worker.discard();
		helper.succeed();
	}

	/** Tries of a look goal's random chance (0.02 a try): the vanilla goal misses all of them about once in 10^8. */
	private static final int TRIES = 1000;

	/**
	 * A worker of a town stands under it: its anchor is the town's core, it walks no further than the knob nor past
	 * the territory, and its death is written in its town's log.
	 *
	 * @implements MOD-832-TOWN — a shift worker belongs to its town: anchor, radius, owner and log
	 */
	public static void aWorkerBelongsToItsTown(GameTestHelper helper) {
		int town = town(helper, AlaGameTestHelper.detachedSurvivalPlayer(helper));
		ShiftWorker worker = spawn(helper, town);
		BlockPos core = SettlementRegistry.get(helper.getLevel().getServer()).find(town).orElseThrow().corePos();
		helper.assertTrue(worker.getHomePosition().equals(core), "the worker's anchor is " + worker.getHomePosition()
				+ ", the town's core is at " + core);
		helper.assertTrue(worker.settlement() == town && record(helper, worker).settlement() == town,
				"the worker is not in town " + town + ": entity " + worker.settlement() + ", record "
						+ record(helper, worker).settlement());
		int radius = Math.min(SettlementConfig.shiftWorkerWanderRadius,
				SettlementRegistry.get(helper.getLevel().getServer()).find(town).orElseThrow().radius());
		helper.assertTrue(worker.wanderRadius() == radius, "wander radius " + worker.wanderRadius() + ", expected "
				+ radius);
		RecordNumber number = record(helper, worker).number();
		worker.kill(helper.getLevel());
		helper.assertTrue(ShiftWorkers.book(helper.getLevel().getServer()).log().stream()
				.anyMatch(line -> line.about(number) && line.settlement() == town),
				"the death of " + number + " is not in town " + town + "'s log");
		closeTown(helper, town);
		helper.succeed();
	}

	/**
	 * R10, R68: a town whose core goes dark puts its workers whose battery is empty to sleep, goals off, at their next
	 * think; when the core lights again the town wakes them; a worker loaded while its town is dark comes back asleep,
	 * whatever it was saved with.
	 *
	 * @implements MOD-832-HIBERNATE — without power the workers hibernate and wake up where they stood
	 */
	public static void aDarkTownSleepsAndALitOneWakes(GameTestHelper helper) {
		SettlementRegistry registry = SettlementRegistry.get(helper.getLevel().getServer());
		int town = town(helper, AlaGameTestHelper.detachedSurvivalPlayer(helper));
		ShiftWorker worker = spawn(helper, town);
		helper.startSequence()
				.thenExecuteAfter(5, () -> {
					helper.assertTrue(!worker.isNoAi(), "a worker of a lit town hibernates");
					emptyBattery(helper, worker); // R68: with charge left a dark town's worker works on (MOD-854)
					registry.setActive(town, false);
				})
				.thenWaitUntil(() -> helper.assertTrue(worker.isNoAi(), "a dark town's worker still runs its goals"))
				.thenExecute(() -> {
					CompoundTag saved = save(helper, worker);
					worker.discard();
					ShiftWorker copy = load(helper, saved);
					copy.setNoAi(false);
					helper.assertTrue(helper.getLevel().addFreshEntity(copy), "the copy could not be added");
					helper.runAfterDelay(3, () -> {
						helper.assertTrue(copy.isNoAi(), "loaded into a dark town, the worker woke up");
						registry.setActive(town, true);
						ShiftWorkers.townLit(helper.getLevel(), town);
						helper.assertTrue(!copy.isNoAi(), "the lit town did not wake its worker");
						copy.discard();
						closeTown(helper, town);
						helper.succeed();
					});
				});
	}

	/** A lit town with its core recorded at the rig's corner and {@code owner} as its owner; returns its number. */
	private static int town(GameTestHelper helper, ServerPlayer owner) {
		SettlementRegistry registry = SettlementRegistry.get(helper.getLevel().getServer());
		int town = registry.place(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0)), 0)
				.settlementId();
		registry.setActive(town, true);
		registry.claim(town, owner.getUUID(), "owner");
		return town;
	}

	/** Empties the worker's battery (MOD-854): a dark town's worker sleeps only once it has no charge left (R68). */
	private static void emptyBattery(GameTestHelper helper, ShiftWorker worker) {
		ShiftWorkerRecord record = record(helper, worker);
		ShiftWorkers.book(helper.getLevel().getServer()).put(record.withProfile(record.profile().withCharge(0)));
	}

	/** Leaves the test's town dark, so its territory holds nothing for the scenarios that run after this one. */
	private static void closeTown(GameTestHelper helper, int town) {
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
	}

	private static CompoundTag save(GameTestHelper helper, ShiftWorker worker) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
				helper.getLevel().registryAccess());
		worker.saveWithoutId(output);
		return output.buildResult();
	}

	private static ShiftWorker load(GameTestHelper helper, CompoundTag tag) {
		ShiftWorker copy = ModMobs.SHIFT_WORKER.type().create(helper.getLevel(), EntitySpawnReason.LOAD);
		copy.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
		return copy;
	}

	/** {@code registry} written by one storage and read by another over the same temporary folder. */
	private static SettlementRegistry throughDisk(GameTestHelper helper, SettlementRegistry registry) {
		MinecraftServer server = helper.getLevel().getServer();
		Path folder;
		try {
			folder = Files.createTempDirectory("alaindustrial-shift-workers");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		try {
			try (SavedDataStorage writer = storageOver(folder, server)) {
				writer.set(SettlementRegistry.TYPE, registry);
			}
			try (SavedDataStorage reader = storageOver(folder, server)) {
				SettlementRegistry loaded = reader.get(SettlementRegistry.TYPE);
				if (loaded == null || loaded == registry) {
					throw helper.assertionException("the shift workers did not come back from " + folder);
				}
				return loaded;
			}
		} finally {
			deleteTree(folder);
		}
	}

	/** See {@code RecordNumberLedgerScenarios#storageOver}: deprecated by NeoForge only. */
	@SuppressWarnings("deprecation")
	private static SavedDataStorage storageOver(Path folder, MinecraftServer server) {
		return new SavedDataStorage(folder, server.getFixerUpper(), server.registryAccess());
	}

	private static void deleteTree(Path folder) {
		try (Stream<Path> paths = Files.walk(folder)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("could not remove the temporary folder " + folder, e);
		}
	}
}
