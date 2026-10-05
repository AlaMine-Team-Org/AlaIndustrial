package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.KnobSnapshot;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.network.ConfigSync;
import dev.alaindustrial.network.ConfigSyncPayload;
import dev.alaindustrial.network.PayloadBudget;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-neutral gametest bodies for server→client config sync (MOD-695), run by the Fabric
 * {@code ConfigSyncGameTest} and by {@code NeoForgeGameTests}.
 *
 * <p><b>What is tested here.</b> A server whose balance differs from the defaults builds its payload,
 * the payload crosses the real {@code StreamCodec}, and the client copy ({@code ServerBalance}) ends up
 * showing the server's numbers — while the server's {@code Config} is left exactly as the server set
 * it. Not the join send itself: a mock player's connection never negotiated the mod's channels (see
 * {@code ArchiveRecordScenarios}), so the hooks are only checked for stepping aside without throwing.
 *
 * <p>The config edits are made and undone inside one synchronous body on the server thread, so no
 * other scenario can observe them.
 */
public final class ConfigSyncScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ConfigSyncScenarios::serverBalanceReachesTheClientCopy,
								"config_sync_server_balance_reaches_client_copy")
						.fabricId("ConfigSyncGameTest", "mod695_serverBalanceReachesTheClientCopy").ticks(20, 40),
				RosterEntry.of(ConfigSyncScenarios::oversizedSnapshotIsRefused,
								"config_sync_oversized_snapshot_refused")
						.fabricId("ConfigSyncGameTest", "mod695_oversizedSnapshotIsRefused").ticks(20, 40));

		private Roster() {}
	}

	private ConfigSyncScenarios() {
	}

	/**
	 * MOD-695-L2 — a server with an edited balance: the payload it sends decodes on the client into
	 * exactly the server's client-visible knobs, the client shows them, and applying them did not touch
	 * {@code Config}.
	 *
	 * @implements MOD-695-L2 — a server with an edited balance: its payload crosses the codec and the
	 *     client copy shows the server's numbers, while the server's Config stays untouched
	 */
	public static void serverBalanceReachesTheClientCopy(GameTestHelper helper) {
		int euPerXp = Config.euPerXp;
		int maxPoints = Config.teleporterMaxPoints;
		float multiplier = Config.levelXpMultiplier;
		ServerBalance.reset();
		try (ConfigOverrides server = ConfigOverrides.sync()) {
			server.set("euPerXp", euPerXp * 4 + 1);
			server.set("teleporterMaxPoints", maxPoints + 5);
			server.set("levelXpMultiplier", multiplier + 0.25f);
			int serverEuPerXp = Config.euPerXp;
			int serverMaxPoints = Config.teleporterMaxPoints;
			float serverMultiplier = Config.levelXpMultiplier;
			KnobSnapshot serverSide = KnobSnapshot.capture();

			ConfigSyncPayload received;
			ByteBuf buf = Unpooled.buffer();
			try {
				ConfigSyncPayload.CODEC.encode(buf, ConfigSync.payload());
				received = ConfigSyncPayload.CODEC.decode(buf);
				if (buf.isReadable()) {
					helper.fail(buf.readableBytes() + " byte(s) left unread after decoding the config snapshot");
					return;
				}
			} finally {
				buf.release();
			}

			// The server goes back to its own numbers BEFORE the client applies the snapshot: from here on
			// a client copy that followed Config would be caught showing the wrong value.
			server.close();

			if (!KnobSnapshot.decode(received.data()).map(serverSide::equals).orElse(false)) {
				helper.fail("the snapshot changed on the wire: sent " + serverSide + ", decoded "
						+ KnobSnapshot.decode(received.data()));
				return;
			}
			if (!ServerBalance.receive(received.data())) {
				helper.fail("the client refused the server's snapshot");
				return;
			}
			if (ServerBalance.euPerXp() != serverEuPerXp || ServerBalance.teleporterMaxPoints() != serverMaxPoints
					|| Float.compare(ServerBalance.levelXpMultiplier(), serverMultiplier) != 0) {
				helper.fail("the client shows euPerXp=" + ServerBalance.euPerXp() + ", teleporterMaxPoints="
						+ ServerBalance.teleporterMaxPoints() + ", levelXpMultiplier=" + ServerBalance.levelXpMultiplier()
						+ "; the server sent " + serverEuPerXp + ", " + serverMaxPoints + ", " + serverMultiplier);
				return;
			}
			if (Config.euPerXp != euPerXp || Config.teleporterMaxPoints != maxPoints
					|| Float.compare(Config.levelXpMultiplier, multiplier) != 0) {
				helper.fail("applying the client snapshot rewrote the server's Config");
				return;
			}

			// The hooks run for mock players too; they must step aside rather than throw (NeoForge refuses a
			// payload on a connection that never negotiated the channel).
			ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
			try {
				ConfigSync.sendOnJoin(player);
				ConfigSync.broadcast(helper.getLevel().getServer());
			} catch (RuntimeException e) {
				helper.fail("the config-sync hooks threw for a connection without the channel: " + e);
				return;
			}
		} finally {
			ServerBalance.reset();
		}
		helper.succeed();
	}

	/**
	 * MOD-695-BUDGET — a body over the payload budget is refused while decoding, not stored.
	 *
	 * @implements MOD-695-BUDGET — a body over the payload budget is refused while decoding
	 */
	public static void oversizedSnapshotIsRefused(GameTestHelper helper) {
		ByteBuf oversized = Unpooled.buffer();
		boolean refused = false;
		try {
			ByteBufCodecs.byteArray(PayloadBudget.MAX_CONFIG_SNAPSHOT_BYTES * 2)
					.encode(oversized, new byte[PayloadBudget.MAX_CONFIG_SNAPSHOT_BYTES + 1]);
			ConfigSyncPayload.CODEC.decode(oversized);
		} catch (DecoderException expected) {
			refused = true;
		} finally {
			oversized.release();
		}
		if (!refused) {
			helper.fail("a body over " + PayloadBudget.MAX_CONFIG_SNAPSHOT_BYTES + " bytes decoded as a config snapshot");
			return;
		}
		helper.succeed();
	}
}
