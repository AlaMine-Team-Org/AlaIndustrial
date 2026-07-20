package dev.alaindustrial.stats;

import dev.alaindustrial.Config;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Server-side accumulator for {@link PlayerModStats} (MOD-133). High-frequency events (a generator
 * crediting EU every tick, a machine finishing an operation) land in an in-memory per-player delta;
 * the delta is folded into the player's attachment on a fixed cadence
 * ({@link Config#statsFlushTicks}) and on logout / server stop — never per tick, so there is no
 * per-tick NBT write or sync. Single server thread, so plain maps/longs suffice (no LongAdder).
 *
 * <p><b>Attribution rules (all enforced here, one place):</b> stats accrue only to a machine's
 * {@code owner}, only while that owner is <em>online</em> ({@code getPlayer(uuid) != null}) and
 * <em>not in creative</em> ({@code hasInfiniteMaterials()}). A null/offline/creative owner is a
 * silent no-op — this is what keeps the {@code /ala demo} stand, structure-placed machines and
 * creative testing out of career stats. Both career totals advance levels, but not equally: completed
 * machine work ({@link #recordUsefulWork}) is the dominant source, while raw generation
 * ({@link #recordProduction}) only trickles at {@link Config#euPerXpGenerated} — deliberately far
 * weaker, since a generator runs without the player (the anti-AFK weighting). Note the weighting is
 * what carries that rule, not a hard zero: a farm wired to consumers never fills its buffers, so an
 * idle online owner does keep earning — just ~3 orders of magnitude too slowly to matter.
 */
public final class PlayerStatsTracker {

	private static final PlayerStatsTracker INSTANCE = new PlayerStatsTracker();

	/** The single server-lifetime tracker (cleared on server stop). */
	public static PlayerStatsTracker get() {
		return INSTANCE;
	}

	/** Pending, not-yet-persisted changes for one player since the last flush. */
	private static final class Delta {
		long euProduced;
		long euConsumed;
		long activeTicks;
		final Map<Identifier, Long> byGenerator = new HashMap<>();
	}

	private final Map<UUID, Delta> pending = new HashMap<>();
	/** Players whose generators already counted an "active tick" this server tick (dedup, R: activeTicks). */
	private final Set<UUID> activeThisTick = new HashSet<>();
	private int tickCounter;

	private PlayerStatsTracker() {
	}

	/** The owner if they are online and not in creative, else null (the single eligibility gate). */
	private static ServerPlayer eligibleOwner(MinecraftServer server, UUID owner) {
		if (server == null || owner == null) {
			return null;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player == null || player.hasInfiniteMaterials()) {
			return null; // offline or creative → do not accrue career stats
		}
		return player;
	}

	/**
	 * Record EU a generator credited to its buffer this tick (career statistics + per-generator
	 * breakdown + "active in the mod" time). A weak XP source at {@link Config#euPerXpGenerated}; the
	 * dominant one is {@link #recordUsefulWork}. Note the caller passes only EU that actually fit in the
	 * buffer, so generating into a full buffer earns nothing.
	 *
	 * <p>Active time is NOT counted here — see {@link #recordActive}. Production attribution is gated on
	 * buffer room, but "time active in the mod" must not be: a saturated network is a normal steady state
	 * for a mature base, and letting it stall the clock froze the dashboard's uptime readout indefinitely.
	 */
	public void recordProduction(MinecraftServer server, UUID owner, Identifier generatorId, long eu) {
		if (eu <= 0 || eligibleOwner(server, owner) == null) {
			return;
		}
		Delta delta = pending.computeIfAbsent(owner, k -> new Delta());
		delta.euProduced += eu;
		if (generatorId != null) {
			delta.byGenerator.merge(generatorId, eu, Long::sum);
		}
	}

	/**
	 * Record that the owner had a generator running this tick — the "active in the mod" clock, independent
	 * of whether the produced EU actually fit in a buffer. Counts one tick per player regardless of how
	 * many generators fired (R: activeTicks must not scale with base size — 10 generators is still one
	 * active tick, not ten); the per-tick dedup set is reset by {@link #onServerTick}.
	 */
	public void recordActive(MinecraftServer server, UUID owner) {
		if (eligibleOwner(server, owner) == null) {
			return;
		}
		if (activeThisTick.add(owner)) {
			pending.computeIfAbsent(owner, k -> new Delta()).activeTicks++;
		}
	}

	/**
	 * Record the full EU cost of a completed machine operation — the sole XP source. Called once, on
	 * completion, so a redstone contraption that aborts operations before they finish burns EU but
	 * earns no XP (the MOD-133 anti-AFK rule).
	 */
	public void recordUsefulWork(MinecraftServer server, UUID owner, long eu) {
		if (eu <= 0 || eligibleOwner(server, owner) == null) {
			return;
		}
		pending.computeIfAbsent(owner, k -> new Delta()).euConsumed += eu;
	}

	/** Server-tick driver: resets the per-tick active-dedup set and flushes on the configured cadence. */
	public void onServerTick(MinecraftServer server) {
		activeThisTick.clear();
		if (++tickCounter >= Math.max(1, Config.statsFlushTicks)) {
			tickCounter = 0;
			flush(server);
		}
	}

	/** Fold every online player's pending delta into their attachment. Offline players' deltas are dropped. */
	public void flush(MinecraftServer server) {
		if (server == null || pending.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Delta> entry : pending.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null) {
				applyDelta(player, entry.getValue());
			}
		}
		pending.clear();
	}

	/** Flush a single player immediately (on their logout, while still online) so their tail is saved. */
	public void flushPlayer(ServerPlayer player) {
		if (player == null) {
			return;
		}
		Delta delta = pending.remove(player.getUUID());
		if (delta != null) {
			applyDelta(player, delta);
		}
	}

	private void applyDelta(ServerPlayer player, Delta delta) {
		PlayerModStats before = PlayerStatsStore.get(player);
		Map<Identifier, Long> mergedGenerators = new HashMap<>(before.producedByGenerator());
		delta.byGenerator.forEach((id, eu) -> mergedGenerators.merge(id, eu, Long::sum));

		long newConsumed = before.euUsefulConsumedTotal() + delta.euConsumed;
		long newProduced = before.euProducedTotal() + delta.euProduced;
		// Both career totals feed the level, so a generator-only gain can also trigger the chime.
		long newXp = LevelMath.xpOf(newConsumed, newProduced, Config.euPerXp, Config.euPerXpGenerated);
		int newLevel = LevelMath.levelForXp(newXp, Config.xpLevelOneCost, Config.levelXpMultiplier);
		// Everyone starts at level 1 — never chime for "reaching" level 1 from a fresh (0) state.
		int prevHighest = Math.max(1, before.highestLevelReached());
		int highest = Math.max(prevHighest, newLevel);

		PlayerStatsStore.set(player, new PlayerModStats(
				newProduced,
				newConsumed,
				highest,
				mergedGenerators,
				before.activeTicks() + delta.activeTicks));

		if (highest > prevHighest) {
			playLevelUp(player); // one chime per flush, regardless of how many levels were crossed
		}
	}

	/**
	 * One level-up chime, heard only by the leveling player (targeted packet, not a world sound).
	 *
	 * <p>Uses the vanilla level-up event rather than a bundled asset: the mod ships no original audio,
	 * and referencing a registered vanilla sound is not copying one. Pitched up a little so a mastery
	 * level does not sound identical to gaining a vanilla XP level — the two progressions are
	 * deliberately separate systems and should not be confused by ear.
	 */
	private static void playLevelUp(ServerPlayer player) {
		player.connection.send(new ClientboundSoundPacket(
				Holder.direct(SoundEvents.PLAYER_LEVELUP), SoundSource.PLAYERS,
				player.getX(), player.getY(), player.getZ(), 1.0f, 1.2f, 0L));
	}

	/**
	 * Total EU pending (recorded but not yet flushed) for {@code owner} — machine work plus generation.
	 *
	 * <p>Exists because attribution is guarded <em>twice</em> and the two guards are indistinguishable
	 * from the attachment: {@link #eligibleOwner} refuses to record for an offline owner, and
	 * {@link #flush} additionally drops any delta whose owner is not online by then. Both leave the
	 * attachment untouched, so an attachment-level assertion cannot tell "the gate refused" from "the
	 * flush dropped it" and would stay green with the record-time gate deleted. This pending view is the
	 * only place the two differ, so it is what the L2 offline-attribution scenario asserts on.
	 */
	public long pendingEuFor(UUID owner) {
		Delta delta = pending.get(owner);
		return delta == null ? 0L : delta.euConsumed + delta.euProduced;
	}

	/** Drop all in-memory state (server stop / integrated-server world switch). */
	public void clear() {
		pending.clear();
		activeThisTick.clear();
		tickCounter = 0;
	}
}
