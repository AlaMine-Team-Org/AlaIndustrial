package dev.alaindustrial.teleporter;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.item.teleport.TeleportPoints;
import dev.alaindustrial.network.TeleportStationsPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds the remote screen's view of its stations (MOD-628) — without loading a chunk.
 *
 * <p>A station whose chunk is loaded is read live; any other comes from the {@link TeleporterRegistry}. The check for
 * a loaded chunk is {@code Level#isLoaded}, which in 26.2 asks {@code ServerChunkCache#hasChunk} for a chunk already in
 * memory and never loads one (verified against the source — see MOD-628 research.md).
 *
 * <p><b>Someone else's private station is hidden here, on the server.</b> The snapshot carries only that it is
 * private; its charge, price, chip and capsule never leave the server, so no client — modified or not — can show them.
 */
public final class TeleportStationSnapshot {

	private TeleportStationSnapshot() {
	}

	public static TeleportStationsPayload build(ServerPlayer player, TeleportPoints points, int containerId) {
		int cooldown = TeleportWarmupManager.isOnCooldown(player) ? TeleportWarmupManager.cooldownSecondsLeft(player) : 0;
		List<TeleportStationsPayload.Station> stations = new ArrayList<>(points.size());
		if (player.level() instanceof ServerLevel here) {
			TeleporterRegistry registry = TeleporterRegistry.get(here.getServer());
			for (int i = 0; i < points.size(); i++) {
				stations.add(station(player, here, registry, points.get(i)));
			}
		}
		// The screen shows the pack's multiplier and draws the random-jump zone ring; both are server settings (MOD-629).
		int weightPermille = (int) Math.round(TeleportEngine.weight(player) * 1000.0);
		// The «Random» tab's charge row and warmup line read the server's price and warmup, not the client's (MOD-630).
		return new TeleportStationsPayload(containerId, cooldown, weightPermille, Config.teleporterBuffer,
				Config.teleporterRtpMinRadius, Config.teleporterRtpRadius, (int) TeleportEngine.rtpCost(),
				(Config.teleporterWarmupTicks + 19) / 20, stations);
	}

	private static TeleportStationsPayload.Station station(ServerPlayer player, ServerLevel here,
			TeleporterRegistry registry, TeleportPoint point) {
		boolean sameDimension = point.dim() == here.dimension();
		int flags = sameDimension ? TeleportStationsPayload.SAME_DIMENSION : 0;

		ServerLevel stationLevel = here.getServer().getLevel(point.dim());
		TeleporterRegistry.Entry entry;
		int ago = 0;
		if (stationLevel != null && stationLevel.isLoaded(point.pos())) {
			if (!(stationLevel.getBlockEntity(point.pos()) instanceof TeleporterBlockEntity live)) {
				// A loaded chunk with no station in it: known to be gone, not merely unknown.
				return new TeleportStationsPayload.Station(flags | TeleportStationsPayload.KNOWN
						| TeleportStationsPayload.LIVE, TeleportEngine.Denial.NO_STATION, 0, 0, 0);
			}
			entry = TeleporterRegistry.Entry.of(live, stationLevel.getGameTime());
			flags |= TeleportStationsPayload.LIVE;
		} else {
			entry = registry.find(point.dim(), point.pos()).orElse(null);
			if (entry == null) {
				// Unknown, but its price is not: it needs only the viewer and the point, so the «Map» card can show an
				// estimate (MOD-629). Readiness stays unknown — the server checks the real station on the jump.
				return new TeleportStationsPayload.Station(flags,
						sameDimension ? TeleportEngine.Denial.OK : TeleportEngine.Denial.CROSS_DIM, 0,
						sameDimension ? TeleportEngine.computeCost(player, point) : 0, 0);
			}
			if (stationLevel != null) {
				ago = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, stationLevel.getGameTime() - entry.updatedGameTime()) / 20L);
			}
		}

		flags |= TeleportStationsPayload.KNOWN;
		if (entry.isPrivate()) {
			flags |= TeleportStationsPayload.PRIVATE;
		}
		if (!entry.allowsAccess(player.getUUID())) {
			flags |= TeleportStationsPayload.HIDDEN;
			return new TeleportStationsPayload.Station(flags,
					sameDimension ? TeleportEngine.Denial.NO_ACCESS : TeleportEngine.Denial.CROSS_DIM, 0, 0, ago);
		}
		if (entry.formed()) {
			flags |= TeleportStationsPayload.FORMED;
		}
		if (entry.hasChip()) {
			flags |= TeleportStationsPayload.CHIP;
		}
		long cost = sameDimension ? TeleportEngine.computeCost(player, point) : 0;
		// The station's side of TeleportEngine#checkPolicy, in its order. The player's own gates — riding, warming
		// up, recharging — are not the station's and are not repeated per row.
		TeleportEngine.Denial denial;
		if (!sameDimension) {
			denial = TeleportEngine.Denial.CROSS_DIM;
		} else if (!entry.formed()) {
			denial = TeleportEngine.Denial.NOT_FORMED;
		} else if (entry.energy() < cost) {
			denial = TeleportEngine.Denial.NOT_ENOUGH_EU;
		} else {
			denial = TeleportEngine.Denial.OK;
		}
		return new TeleportStationsPayload.Station(flags, denial, entry.energy(), cost, ago);
	}
}
