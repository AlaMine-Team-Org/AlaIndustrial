package dev.alaindustrial.teleporter;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.TeleportPoint;
import dev.alaindustrial.item.TeleporterRemoteItem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
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

	/** A jump in progress: where it started (to detect movement), where it goes, and since when. */
	private record Warmup(TeleportPoint point, Vec3 origin, long startTick) {
	}

	private static final Map<UUID, Warmup> WARMUPS = new HashMap<>();
	/** Player → the server tick their cooldown ends. Authoritative; the item cooldown is only a visual. */
	private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

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
		WARMUPS.put(player.getUUID(), new Warmup(point, player.position(), player.level().getGameTime()));
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
			fire(player, warmup.point());
		}
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
