package dev.alaindustrial.teleporter;

import dev.alaindustrial.Config;
import dev.alaindustrial.chat.ModChat;
import dev.alaindustrial.core.net.LevelStateHolder;
import dev.alaindustrial.core.net.LevelStateRegistry;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.item.teleport.TeleporterRemoteItem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The mod's first piece of per-player server state (MOD-092): who is mid-jump, and who is on
 * cooldown.
 *
 * <p>Modelled on {@code NetworkManager} — a static map plus explicit clearing, driven from the
 * existing server tick on both loaders. Deliberately <b>not persisted</b>: a warmup is fifteen
 * seconds of standing still, and carrying it across a restart would mean restoring a countdown for
 * a player who is not there. Cooldowns are dropped on disconnect for the same reason; a player who
 * logs out is not spamming jumps.
 */
public final class TeleportWarmupManager {
	private TeleportWarmupManager() {
	}

	/**
	 * A jump in progress: where it started (to detect movement), where it goes, and since when.
	 *
	 * <p>{@code rtpTarget} is what tells the two kinds of jump apart (MOD-116). When it is null this is
	 * an ordinary jump and {@code point} is the destination station. When it is set this is a random
	 * jump: {@code point} is then the station that PAYS — the row the player had selected — and
	 * {@code rtpTarget} is the spot the search rolled at trigger time.
	 */
	private record Warmup(TeleportPoint point, Vec3 origin, long startTick, @Nullable BlockPos rtpTarget) {
	}

	private static final Map<UUID, Warmup> WARMUPS = new HashMap<>();
	/** Player → the server tick their cooldown ends. Authoritative; the item cooldown is only a visual. */
	private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

	/*
	 * MOD-401: enrol in the shared cleanup roster. `clearAll()` was written here from the start
	 * ("mirroring NetworkManager.clearAll") and, like the fluid manager's, was called from nowhere but
	 * a gametest teardown — so warmups and cooldowns from one world survived into the next one in the
	 * same process, where their absolute game-tick deadlines mean nothing. This state is keyed by
	 * player, not by level, so the level-unload sweep is a no-op for it and only the server-stop sweep
	 * does work.
	 */
	static {
		LevelStateRegistry.register(new LevelStateHolder() {
			@Override
			public String stateId() {
				return "teleport-warmups";
			}

			@Override
			public void clearLevel(Object level) {
				// Keyed by player UUID: a level unload takes none of it away.
			}

			@Override
			public void clearAll() {
				TeleportWarmupManager.clearAll();
			}

			@Override
			public int retained() {
				return WARMUPS.size() + COOLDOWNS.size();
			}
		});
	}

	/** True if this player is mid-warmup — a second trigger must not start another one. */
	public static boolean isWarming(ServerPlayer player) {
		return WARMUPS.containsKey(player.getUUID());
	}

	/**
	 * Server-authoritative cooldown check.
	 *
	 * <p>The vanilla item cooldown is a client-visible sweep on the icon and nothing more: it lives
	 * on the stack, so dropping the remote or handing it to a friend would clear it. The rule the
	 * server enforces is this map, keyed by player.
	 */
	public static boolean isOnCooldown(ServerPlayer player) {
		Long until = COOLDOWNS.get(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	/** Remaining cooldown in seconds, for the message that tells the player how long to wait. */
	public static int cooldownSecondsLeft(ServerPlayer player) {
		Long until = COOLDOWNS.get(player.getUUID());
		if (until == null) {
			return 0;
		}
		long ticks = until - player.level().getGameTime();
		return ticks <= 0 ? 0 : (int) Math.ceil(ticks / 20.0);
	}

	/** Begin the countdown. The caller has already run {@link TeleportEngine#checkPolicy}. */
	public static void start(ServerPlayer player, TeleportPoint point) {
		WARMUPS.put(player.getUUID(), new Warmup(point, player.position(), player.level().getGameTime(), null));
	}

	/**
	 * Begin a random jump's countdown (MOD-116). The caller has already run
	 * {@link TeleportEngine#checkRtpPolicy} and rolled {@code target}.
	 *
	 * <p>The destination is fixed here, at trigger time, rather than at the end of the warmup: the roll
	 * is what pays for a chunk, and doing it up front means a player who is going to be told "nowhere
	 * safe" hears it immediately instead of after standing still for five seconds.
	 */
	public static void startRtp(ServerPlayer player, TeleportPoint payingStation, BlockPos target) {
		WARMUPS.put(player.getUUID(),
				new Warmup(payingStation, player.position(), player.level().getGameTime(), target));
	}

	/**
	 * Abort this player's warmup, if any, and tell them why. Called from the damage / death /
	 * disconnect hooks and from the movement check.
	 */
	public static void cancel(ServerPlayer player, @Nullable Component reason) {
		if (WARMUPS.remove(player.getUUID()) != null) {
			TeleportEffects.clear(player);
			if (reason != null) {
				player.sendSystemMessage(reason.copy().withStyle(ChatFormatting.RED), true);
			}
		}
	}

	/** Silent variant for disconnect, where there is nobody left to message. */
	public static void cancel(ServerPlayer player) {
		if (WARMUPS.remove(player.getUUID()) != null) {
			TeleportEffects.clear(player);
		}
	}

	/** Forget a player entirely on disconnect — warmup and cooldown both. */
	public static void forget(UUID player) {
		WARMUPS.remove(player);
		COOLDOWNS.remove(player);
	}

	/** Drop all state (server stop / test teardown), mirroring {@code NetworkManager.clearAll}. */
	public static void clearAll() {
		WARMUPS.clear();
		COOLDOWNS.clear();
	}

	/**
	 * Advance every warmup by one tick: cancel the ones whose player walked off, count the rest down,
	 * and fire the ones that are done.
	 *
	 * <p>Movement is checked here rather than through an event because neither loader has a cheap
	 * "player moved" hook, and this way the cost is paid only for players actually mid-jump instead
	 * of for everyone on the server every tick.
	 */
	public static void tickAll(MinecraftServer server) {
		if (WARMUPS.isEmpty()) {
			return;
		}
		int warmupTicks = Config.teleporterWarmupTicks;
		double cancelRadiusSq = (double) Config.teleporterWarmupCancelRadius * Config.teleporterWarmupCancelRadius;

		for (Iterator<Map.Entry<UUID, Warmup>> it = WARMUPS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Warmup> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove(); // logged out between the hook and now
				continue;
			}
			Warmup warmup = entry.getValue();

			if (player.position().distanceToSqr(warmup.origin()) > cancelRadiusSq) {
				it.remove();
				TeleportEffects.clear(player);
				player.sendSystemMessage(Component.translatable("alaindustrial.teleporter.cancelled_moved")
						.withStyle(ChatFormatting.RED), true);
				continue;
			}

			long elapsed = player.level().getGameTime() - warmup.startTick();
			if (elapsed < warmupTicks) {
				int secondsLeft = (int) Math.ceil((warmupTicks - elapsed) / 20.0);
				player.sendSystemMessage(Component.translatable(
						"alaindustrial.teleporter.warmup_countdown", secondsLeft), true);
				TeleportEffects.tickWarmup(player, elapsed, warmupTicks);
				continue;
			}

			// Done counting: re-check with the final numbers, then jump. The state is removed first
			// so a failure here cannot leave a stuck warmup behind.
			it.remove();
			if (warmup.rtpTarget() != null) {
				fireRtp(player, warmup.point(), warmup.rtpTarget());
			} else {
				fire(player, warmup.point());
			}
		}
	}

	/**
	 * The authoritative re-check plus the random jump itself (MOD-116).
	 *
	 * <p>The destination column stays the one rolled at trigger time, but its height is measured
	 * again. Five seconds is long enough for a spot to stop being safe — lava spreading, a mob walking
	 * a block into place — and re-measuring costs nothing, because the chunk it needs is the one the
	 * trigger-time roll already pulled in. If the column has gone bad the jump is refused for free,
	 * which is strictly better than honouring a stale answer and dropping somebody into lava.
	 */
	private static void fireRtp(ServerPlayer player, TeleportPoint payingStation, BlockPos rolled) {
		TeleportEngine.Denial denial = TeleportEngine.checkRtpPolicy(player, payingStation);
		if (!denial.allowed()) {
			TeleportEffects.clear(player);
			player.sendSystemMessage(denial.message().copy().withStyle(ChatFormatting.RED), true);
			return;
		}
		BlockPos target = TeleportEngine.revalidateRtpSite(player, rolled);
		if (target == null) {
			TeleportEffects.clear(player);
			player.sendSystemMessage(TeleportEngine.Denial.RTP_NO_SAFE_SPOT.message()
					.copy().withStyle(ChatFormatting.RED), true);
			return;
		}
		long cost = TeleportEngine.rtpCost();
		if (!TeleportEngine.executeRtp(player, payingStation, target, cost)) {
			TeleportEffects.clear(player);
			player.sendSystemMessage(TeleportEngine.Denial.RTP_NO_SAFE_SPOT.message()
					.copy().withStyle(ChatFormatting.RED), true);
			return;
		}
		TeleportEffects.arrived(player);
		COOLDOWNS.put(player.getUUID(), player.level().getGameTime() + Config.teleporterCooldownTicks);
		// The coordinates go in the message because a random jump is the one case where the player has
		// no idea where they ended up, and the line lands in chat (not the action bar) precisely so it
		// stays scrollable: this is the only record of the spot until they get their bearings.
		player.sendSystemMessage(ModChat.line(
				Component.translatable("alaindustrial.teleporter.rtp_jumped",
								target.getX(), target.getY(), target.getZ(), cost)
						.withStyle(ChatFormatting.GREEN)), false);
	}

	/** The authoritative re-check plus the jump itself. */
	private static void fire(ServerPlayer player, TeleportPoint point) {
		// Either hand — the same lookup the menu used to start this jump. Re-checking the main hand
		// would refuse an off-hand jump as somebody else's remote right at the finish line.
		TeleportEngine.Denial denial =
				TeleportEngine.checkPolicy(player, TeleporterRemoteItem.heldRemote(player), point);
		if (!denial.allowed()) {
			TeleportEffects.clear(player);
			player.sendSystemMessage(denial.message().copy().withStyle(ChatFormatting.RED), true);
			return;
		}
		long cost = TeleportEngine.computeCost(player, point);
		if (!TeleportEngine.execute(player, point, cost)) {
			TeleportEffects.clear(player);
			player.sendSystemMessage(Component.translatable("alaindustrial.teleporter.no_station")
					.withStyle(ChatFormatting.RED), true);
			return;
		}
		// The screen is at its darkest right now; clearing it here is what makes the arrival read as
		// eyes opening on somewhere new.
		TeleportEffects.arrived(player);
		COOLDOWNS.put(player.getUUID(), player.level().getGameTime() + Config.teleporterCooldownTicks);
		player.sendSystemMessage(Component.translatable("alaindustrial.teleporter.jumped", cost)
				.withStyle(ChatFormatting.GREEN), true);
	}
}
