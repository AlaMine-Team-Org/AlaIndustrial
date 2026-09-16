package dev.alaindustrial.teleporter;

import dev.alaindustrial.core.teleport.RemoteLog;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Writes the remote's log (MOD-631): the one place the server turns a jump, a refusal or an edit into a line.
 *
 * <p><b>The line goes into the remote the event was made with</b>, which the caller names: the one clicked with, the one
 * whose screen is open, or the one a jump was started with. The history belongs to the item, so an empty stack means
 * there is nowhere to write, and nothing is written — nothing fails either.
 *
 * <p>Every write is pushed to the owner at once: while the remote's own screen is open the inventory is not synced on
 * its own, and a refusal the player just caused has to show up in the log they are looking at.
 */
public final class TeleportLogs {

	/** Button ids from here up are "I have read the log up to {@code id - SEEN_BUTTON}", as the reactor's log does. */
	public static final int SEEN_BUTTON = 1_000_000;

	private TeleportLogs() {
	}

	/** A point as the log names it. */
	public static RemoteLog.Station station(@Nullable TeleportPoint point) {
		return point == null ? RemoteLog.Station.NONE : new RemoteLog.Station(point.name(), point.number());
	}

	public static void jumped(ServerPlayer player, ItemStack remote, TeleportPoint point, long cost) {
		write(player, remote, RemoteLog.Kind.JUMPED, false, point, null, clamp(cost), 0, 0);
	}

	public static void randomJumped(ServerPlayer player, ItemStack remote, TeleportPoint payingStation, BlockPos target,
			long cost) {
		write(player, remote, RemoteLog.Kind.RANDOM_JUMPED, true, payingStation, null, target.getX(), target.getZ(),
				clamp(cost));
	}

	/** A refusal the log names; one it does not (a second press mid-warmup, someone else's remote) writes nothing. */
	public static void refused(ServerPlayer player, ItemStack remote, TeleportPoint point, boolean random,
			TeleportEngine.Denial denial) {
		RemoteLog.Kind kind = kindOf(denial);
		if (kind != null) {
			write(player, remote, kind, random, point, null, 0, 0, 0);
		}
	}

	public static void refusedCooldown(ServerPlayer player, ItemStack remote, TeleportPoint point, boolean random,
			int secondsLeft) {
		write(player, remote, RemoteLog.Kind.REFUSED_COOLDOWN, random, point, null, secondsLeft, 0, 0);
	}

	public static void cancelled(ServerPlayer player, ItemStack remote, TeleportPoint point, boolean random, boolean hurt) {
		write(player, remote, hurt ? RemoteLog.Kind.CANCELLED_HURT : RemoteLog.Kind.CANCELLED_MOVED, random, point, null,
				0, 0, 0);
	}

	public static void bound(ServerPlayer player, ItemStack remote, TeleportPoint point, int count, int max) {
		write(player, remote, RemoteLog.Kind.BOUND, false, point, null, count, max, 0);
	}

	public static void renamed(ServerPlayer player, ItemStack remote, TeleportPoint before, TeleportPoint after) {
		write(player, remote, RemoteLog.Kind.RENAMED, false, before, after, 0, 0, 0);
	}

	public static void deleted(ServerPlayer player, ItemStack remote, TeleportPoint point) {
		write(player, remote, RemoteLog.Kind.DELETED, false, point, null, 0, 0, 0);
	}

	/** The owner has seen the log up to {@code seq}: the badge goes out and the lines stop reading as new. */
	public static void markSeen(ServerPlayer player, ItemStack remote, int seq) {
		if (remote.isEmpty()) {
			return;
		}
		RemoteLog log = remote.getOrDefault(ModDataComponents.TELEPORTER_LOG.get(), RemoteLog.EMPTY);
		RemoteLog next = log.markSeen(seq);
		if (next != log) {
			remote.set(ModDataComponents.TELEPORTER_LOG.get(), next);
			player.inventoryMenu.broadcastChanges();
		}
	}

	/** The log's name for a refusal, or {@code null} for one it does not record. */
	public static RemoteLog.@Nullable Kind kindOf(TeleportEngine.Denial denial) {
		return switch (denial) {
			case NOT_ENOUGH_EU -> RemoteLog.Kind.REFUSED_NO_POWER;
			case NO_ACCESS -> RemoteLog.Kind.REFUSED_NO_ACCESS;
			case NO_STATION -> RemoteLog.Kind.REFUSED_NO_STATION;
			case NOT_FORMED -> RemoteLog.Kind.REFUSED_NOT_FORMED;
			case RTP_NO_MODULE -> RemoteLog.Kind.REFUSED_NO_CHIP;
			case COOLDOWN -> RemoteLog.Kind.REFUSED_COOLDOWN;
			case CROSS_DIM -> RemoteLog.Kind.REFUSED_CROSS_DIM;
			case RTP_WRONG_DIMENSION -> RemoteLog.Kind.REFUSED_WRONG_DIMENSION;
			case RTP_NO_SAFE_SPOT -> RemoteLog.Kind.REFUSED_NO_SAFE_SPOT;
			case MOUNTED -> RemoteLog.Kind.REFUSED_MOUNTED;
			case OK, NOT_BOUND, NOT_YOUR_REMOTE, ALREADY_WARMING -> null;
		};
	}

	private static void write(ServerPlayer player, ItemStack remote, RemoteLog.Kind kind, boolean random,
			@Nullable TeleportPoint point, @Nullable TeleportPoint renamedTo, int a, int b, int c) {
		if (remote.isEmpty()) {
			return;
		}
		RemoteLog log = remote.getOrDefault(ModDataComponents.TELEPORTER_LOG.get(), RemoteLog.EMPTY);
		remote.set(ModDataComponents.TELEPORTER_LOG.get(), log.append(player.level().getGameTime(), kind, random,
				station(point), station(renamedTo), a, b, c));
		player.inventoryMenu.broadcastChanges();
	}

	private static int clamp(long value) {
		return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
	}
}
