package dev.alaindustrial.gametest;

import dev.alaindustrial.core.guide.ArchiveRecord;
import dev.alaindustrial.core.guide.ArchiveRecordSync;
import dev.alaindustrial.network.ArchiveRecordPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-neutral gametest bodies for the archive record's server half (MOD-513, suite TC-GUIDE-002),
 * run by the Fabric {@code GuideBookGameTest} and by {@code NeoForgeGameTests}.
 *
 * <p><b>What is tested here, and what is not.</b> The decision — which record a player gets — and the
 * wire format. Not the join hook's send: on NeoForge a mock player's connection never negotiated the
 * mod's channels, so a payload sent to it dies inside the platform. The send is one line on each
 * loader; the L3 lane ({@code GuideBookRecordStand}) watches a real integrated-server login deliver it
 * and the book draw it.
 */
public final class ArchiveRecordScenarios {

	private ArchiveRecordScenarios() {
	}

	/**
	 * TC-GUIDE-002-FUN01 — the server's record is the pure function of the world seed and the player's
	 * profile id: every dimension reports that one seed, the profile id is the player's UUID, asking
	 * again in another order gives the same answers, and the join hook does not throw for a connection
	 * that cannot receive the record.
	 */
	public static void recordComesFromTheWorldSeedAndTheProfile(GameTestHelper helper) {
		ServerPlayer first = AlaGameTestHelper.mockPlayerInLevel(helper);
		ServerPlayer second = AlaGameTestHelper.mockPlayerInLevel(helper);
		MinecraftServer server = helper.getLevel().getServer();
		long seed = server.overworld().getSeed();

		for (ServerLevel level : server.getAllLevels()) {
			if (level.getSeed() != seed) {
				helper.fail(level.dimension().identifier() + " reports seed " + level.getSeed()
						+ ", the overworld " + seed + " — the record would change with the dimension");
				return;
			}
		}

		for (ServerPlayer player : List.of(first, second)) {
			UUID profile = player.getGameProfile().id();
			if (!profile.equals(player.getUUID())) {
				helper.fail("profile id " + profile + " differs from the entity UUID " + player.getUUID());
				return;
			}
			String record = ArchiveRecordSync.recordFor(player);
			if (!ArchiveRecord.isValid(record)) {
				helper.fail("the server produced a malformed record: " + record);
				return;
			}
			String expected = ArchiveRecord.of(seed, profile);
			if (!expected.equals(record)) {
				helper.fail("record " + record + " is not the one the world seed and profile give (" + expected + ")");
				return;
			}
		}

		String firstAgain = ArchiveRecordSync.recordFor(first);
		String secondThen = ArchiveRecordSync.recordFor(second);
		String secondAgain = ArchiveRecordSync.recordFor(second);
		String firstLast = ArchiveRecordSync.recordFor(first);
		if (!firstAgain.equals(firstLast) || !secondThen.equals(secondAgain)) {
			helper.fail("records moved between calls: " + firstAgain + "/" + firstLast + ", "
					+ secondThen + "/" + secondAgain);
			return;
		}

		// The join hook runs for mock players too, whose connection never negotiated the mod's channels.
		// It must step aside rather than throw: on NeoForge the unguarded send failed 176 unrelated
		// scenarios, because creating a mock player in the level fires the login event.
		try {
			ArchiveRecordSync.sendOnJoin(first);
		} catch (RuntimeException e) {
			helper.fail("the join hook threw for a connection without the record channel: " + e);
			return;
		}
		helper.succeed();
	}

	/**
	 * TC-GUIDE-002-FUN02 — the payload carries the server's record through its codec unchanged and
	 * leaves nothing behind; a string longer than the codec's bound is refused while decoding.
	 */
	public static void payloadCarriesTheRecordIntact(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		ArchiveRecordPayload sent = new ArchiveRecordPayload(ArchiveRecordSync.recordFor(player));

		ByteBuf buf = Unpooled.buffer();
		try {
			ArchiveRecordPayload.CODEC.encode(buf, sent);
			ArchiveRecordPayload received = ArchiveRecordPayload.CODEC.decode(buf);
			if (!sent.equals(received)) {
				helper.fail("sent " + sent + ", decoded " + received);
				return;
			}
			if (buf.isReadable()) {
				helper.fail(buf.readableBytes() + " byte(s) left unread after decoding the record");
				return;
			}
		} finally {
			buf.release();
		}

		ByteBuf oversized = Unpooled.buffer();
		boolean refused = false;
		try {
			ByteBufCodecs.stringUtf8(ArchiveRecordPayload.MAX_LENGTH * 8)
					.encode(oversized, "X".repeat(ArchiveRecordPayload.MAX_LENGTH + 1));
			ArchiveRecordPayload.CODEC.decode(oversized);
		} catch (DecoderException expected) {
			refused = true;
		} finally {
			oversized.release();
		}
		if (!refused) {
			helper.fail("a string longer than " + ArchiveRecordPayload.MAX_LENGTH + " characters decoded as a record");
			return;
		}
		helper.succeed();
	}
}
