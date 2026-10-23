package dev.alaindustrial.settlement.shift;

import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.stats.PlayerStatsTracker;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/**
 * A shift worker's battery (MOD-854, epic decisions R67, R68, R73): the idle drain of an awake worker, the
 * recharge its town's core pays for, the level and rank marks the drain earns, the owner's credit, and the wound
 * marks. Server thread only; the record in the book is the only state that is saved.
 *
 * <p><b>The drain</b> is settled on the worker's think ticks ({@link ShiftWorkers#think}), not every tick: the
 * elapsed game time times {@link SettlementConfig#shiftWorkerIdleEuPerTick}, the fractional remainder carried on the
 * entity ({@link ShiftWorker#drainCarry()}) so a quarter EU per tick adds up exactly. The carry is not saved: an
 * unload loses less than one EU, never a share of every settle. A hibernating worker runs no goals and so does not
 * settle — asleep costs nothing; the first settle after waking counts at most {@link #MAX_SETTLE_TICKS}. A worker of
 * no town (a stand, a test) neither drains nor sleeps: nothing would recharge it.
 *
 * <p><b>Only the drain counts (R67).</b> Every EU that leaves the battery raises {@code euDrained}, the level's only
 * source, and is credited to the town's owner as useful work — the dashboard's «Used» and mastery (R73) — through
 * {@link PlayerStatsTracker#recordUsefulWork}, which batches it and applies the owner-online rule. Recharging counts
 * for nothing.
 */
public final class ShiftWorkerBattery {

	/** A town's core recharges its workers once every this many ticks. */
	public static final int RECHARGE_INTERVAL = 20;
	/** The most ticks one settle counts — the first think after a long sleep is not billed for the sleep. */
	public static final int MAX_SETTLE_TICKS = 200;
	/** Wounds this close together are one fight, written as one mark (the latest health, the fight's start). */
	public static final long WOUND_FIGHT_TICKS = 600;
	/** The action-bar line of a battery's charge: «W-117: +2000 EU into the battery». */
	public static final String CHARGED_KEY = "message.alaindustrial.shift_worker.charged";
	/** The same line when nothing fitted: «W-117: the battery is already full». */
	public static final String FULL_KEY = "message.alaindustrial.shift_worker.battery_full";

	private ShiftWorkerBattery() {
	}

	/** Settles the idle drain of {@code worker} since its last settle; on its think ticks, while it is awake. */
	public static void settle(ServerLevel level, ShiftWorker worker) {
		long now = level.getGameTime();
		long last = worker.batteryClock();
		worker.setBatteryClock(now);
		if (last < 0 || now <= last || worker.settlement() <= 0) {
			return;
		}
		long elapsed = Math.min(now - last, MAX_SETTLE_TICKS);
		double due = worker.drainCarry() + Math.max(0.0, SettlementConfig.shiftWorkerIdleEuPerTick) * elapsed;
		long eu = (long) Math.floor(due);
		worker.setDrainCarry(due - eu);
		if (eu <= 0) {
			return;
		}
		ShiftWorkerBook book = ShiftWorkers.book(level.getServer());
		book.byEntity(worker.getUUID())
				.filter(record -> record.status() == ShiftWorkerRecord.Status.ALIVE)
				.ifPresent(record -> drain(level.getServer(), record, eu, now));
	}

	/**
	 * Takes up to {@code eu} out of {@code record}'s battery (never below zero), stores the record and returns it:
	 * {@code euDrained} grows by what left, a new level or rank writes its mark, and the EU is credited to the town's
	 * owner (R73). Nothing changes when the battery is empty. The drain of later stages' work comes through here too.
	 */
	public static ShiftWorkerRecord drain(MinecraftServer server, ShiftWorkerRecord record, long eu, long gameTime) {
		ShiftWorkerProfile before = record.profile();
		long spent = Math.max(0L, Math.min(eu, before.chargeNow()));
		if (spent == 0) {
			return record;
		}
		ShiftWorkerProfile after = levelMarks(before, before.drained(spent), gameTime);
		ShiftWorkerRecord drained = record.withProfile(after);
		ShiftWorkers.book(server).put(drained);
		town(server, record.settlement()).flatMap(Settlement::owner)
				.ifPresent(owner -> PlayerStatsTracker.get().recordUsefulWork(server, owner, spent));
		return drained;
	}

	/**
	 * The test command's lift ({@code /ala worker xp}, MOD-854): {@code eu} counted into {@code record}'s drained total
	 * as if it had left the battery, the charge untouched. Levels, the LEVEL and RANK marks and the free points follow
	 * exactly as from a real drain; the town's owner is credited nothing, since no work was done. Stores the record
	 * and returns it.
	 */
	public static ShiftWorkerRecord train(MinecraftServer server, ShiftWorkerRecord record, long eu, long gameTime) {
		if (eu <= 0) {
			return record;
		}
		ShiftWorkerProfile before = record.profile();
		ShiftWorkerRecord trained = record.withProfile(levelMarks(before, before.trained(eu), gameTime));
		ShiftWorkers.book(server).put(trained);
		return trained;
	}

	/** {@code after} with the LEVEL mark of a new level and the RANK mark of a new rank since {@code before}. */
	private static ShiftWorkerProfile levelMarks(ShiftWorkerProfile before, ShiftWorkerProfile after, long gameTime) {
		int oldLevel = before.level();
		int newLevel = after.level();
		if (newLevel <= oldLevel) {
			return after;
		}
		ShiftWorkerProfile marked = after.marked(new ShiftWorkerProfile.ServiceMark(
				ShiftWorkerProfile.ServiceMark.Kind.LEVEL, gameTime, List.of(Integer.toString(newLevel))));
		int newRank = ShiftWorkerLevels.rankIndex(newLevel);
		if (newRank > ShiftWorkerLevels.rankIndex(oldLevel)) {
			marked = marked.marked(new ShiftWorkerProfile.ServiceMark(ShiftWorkerProfile.ServiceMark.Kind.RANK,
					gameTime, List.of(Integer.toString(newRank + 1))));
		}
		return marked;
	}

	/**
	 * Recharges the living homeless workers of town {@code settlement} (a resident charges at home, MOD-862) from
	 * {@code available} EU of its core's buffer, in the
	 * book's order, each up to its capacity. Returns the EU used, which the core drains on top of its upkeep. A worker
	 * whose battery reads full writes nothing.
	 */
	public static long recharge(MinecraftServer server, int settlement, long available) {
		if (settlement <= 0 || available <= 0) {
			return 0L;
		}
		ShiftWorkerBook book = ShiftWorkers.book(server);
		long used = 0L;
		for (ShiftWorkerRecord record : List.copyOf(book.all())) {
			// A resident charges on its house's bed (MOD-862, R101); the core charges the homeless.
			if (record.settlement() != settlement || record.status() != ShiftWorkerRecord.Status.ALIVE
					|| record.housed()) {
				continue;
			}
			ShiftWorkerProfile profile = record.profile();
			long charge = profile.chargeNow();
			long give = Math.min(profile.capacity() - charge, available - used);
			if (give <= 0) {
				if (used >= available) {
					break;
				}
				continue;
			}
			book.put(record.withProfile(profile.withCharge(charge + give)));
			used += give;
		}
		return used;
	}

	/** Whether the record of {@code worker} has charge left; a worker without a record has none. */
	public static boolean charged(MinecraftServer server, ShiftWorker worker) {
		return ShiftWorkers.book(server).byEntity(worker.getUUID())
				.map(record -> record.profile().chargeNow() > 0).orElse(false);
	}

	/**
	 * Writes a {@code WOUNDED} mark for {@code worker}, hurt by {@code source} and still alive: the attacker's entity
	 * type (empty — none), health left, maximum health. Wounds within {@link #WOUND_FIGHT_TICKS} of the previous one
	 * are the same fight and update its mark, keeping the time the fight started.
	 */
	public static void noteWound(ServerLevel level, ShiftWorker worker, DamageSource source) {
		long now = level.getGameTime();
		long lastHurt = worker.lastHurtAt();
		worker.setLastHurtAt(now);
		if (worker.isDeadOrDying()) {
			return;
		}
		ShiftWorkerBook book = ShiftWorkers.book(level.getServer());
		Optional<ShiftWorkerRecord> found = book.byEntity(worker.getUUID())
				.filter(record -> record.status() == ShiftWorkerRecord.Status.ALIVE);
		if (found.isEmpty()) {
			return;
		}
		ShiftWorkerRecord record = found.get();
		ShiftWorkerProfile profile = record.profile();
		String attacker = Optional.ofNullable(source.getEntity())
				.map(entity -> EntityType.getKey(entity.getType()).toString()).orElse("");
		List<String> args = List.of(attacker, Integer.toString((int) Math.ceil(worker.getHealth())),
				Integer.toString((int) Math.ceil(worker.getMaxHealth())));
		List<ShiftWorkerProfile.ServiceMark> marks = profile.marks();
		ShiftWorkerProfile.ServiceMark newest = marks.isEmpty() ? null : marks.get(marks.size() - 1);
		boolean sameFight = lastHurt >= 0 && now - lastHurt <= WOUND_FIGHT_TICKS && newest != null
				&& newest.kind() == ShiftWorkerProfile.ServiceMark.Kind.WOUNDED;
		ShiftWorkerProfile next = sameFight
				? profile.withNewestMark(new ShiftWorkerProfile.ServiceMark(
						ShiftWorkerProfile.ServiceMark.Kind.WOUNDED, newest.gameTime(), args))
				: profile.marked(new ShiftWorkerProfile.ServiceMark(ShiftWorkerProfile.ServiceMark.Kind.WOUNDED,
						now, args));
		book.put(record.withProfile(next));
	}

	/**
	 * A player's right click with a charged battery (MOD-854, owner's decision 2026-10-21): EU from {@code source} —
	 * any powered item that gives charge back ({@link ItemEnergy#canDischarge}), the mod's battery first of all — into
	 * the battery of the living {@code worker}, up to its free capacity, in one click. What did not fit stays in the
	 * item; a stack of batteries pays by the stack rule of {@link ItemEnergy#discharge}. Anyone may do it: it only
	 * helps the worker. A charge moved into a hibernating worker wakes it where it stands, as a lit core does
	 * ({@link ShiftWorkers#townLit}); the planner decides the rest on its next think. Charging is not drain: it
	 * counts towards no level and no credit (R67).
	 *
	 * @return the EU moved, 0 when the battery was already full; empty when this is not a charge — the item holds
	 *         nothing to give, or the worker has no living record — and the click opens the card instead
	 */
	public static OptionalLong chargeFrom(ServerLevel level, ShiftWorker worker, ItemStack source) {
		if (!ItemEnergy.canDischarge(source) || ItemEnergy.stackGet(source) <= 0 || worker.isDeadOrDying()) {
			return OptionalLong.empty();
		}
		ShiftWorkerBook book = ShiftWorkers.book(level.getServer());
		Optional<ShiftWorkerRecord> found = book.byEntity(worker.getUUID())
				.filter(record -> record.status() == ShiftWorkerRecord.Status.ALIVE);
		if (found.isEmpty()) {
			return OptionalLong.empty();
		}
		ShiftWorkerRecord record = found.get();
		ShiftWorkerProfile profile = record.profile();
		long charge = profile.chargeNow();
		long room = profile.capacity() - charge;
		long moved = room <= 0 ? 0L : ItemEnergy.discharge(source, room);
		if (moved > 0) {
			book.put(record.withProfile(profile.withCharge(charge + moved)));
			if (worker.mode() == ShiftMode.HIBERNATE) {
				worker.setMode(ShiftMode.IDLE);
				worker.setNoAi(false);
			}
		}
		return OptionalLong.of(moved);
	}

	/** The action-bar line after {@link #chargeFrom}: how much went in, or that the battery was full. */
	public static Component chargeMessage(String number, long moved) {
		return moved > 0 ? Component.translatable(CHARGED_KEY, number, moved)
				: Component.translatable(FULL_KEY, number);
	}

	private static Optional<Settlement> town(MinecraftServer server, int settlement) {
		return settlement > 0 ? SettlementRegistry.get(server).find(settlement) : Optional.empty();
	}
}
