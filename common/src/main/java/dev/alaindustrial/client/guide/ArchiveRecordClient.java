package dev.alaindustrial.client.guide;

import dev.alaindustrial.core.guide.ArchiveRecord;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The client's copy of its archive record (MOD-513): whatever the server last sent this connection, or
 * nothing yet.
 *
 * <p><b>It only keeps what has the record's shape.</b> The value comes over the network, and a string
 * of any other shape is dropped here instead of reaching the book; a valid record already held stays.
 * The client never works a record out for itself — see {@code ArchiveRecordSync}.
 *
 * <p><b>Cleared when the player leaves a world</b> (both loaders' client disconnect hooks call
 * {@link #reset}), so the next world shows its own record, or the pending mark until it arrives,
 * never the last one's.
 *
 * <p>Minecraft-free, so L1 covers it directly; both loaders' receivers call {@link #receive} on the
 * client thread.
 */
public final class ArchiveRecordClient {

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial");

	@Nullable
	private static volatile String current;

	private ArchiveRecordClient() {
	}

	/** Takes a record from the server. Returns whether it was kept; anything malformed is not. */
	public static boolean receive(@Nullable String record) {
		if (!ArchiveRecord.isValid(record)) {
			LOG.warn("Ignoring a malformed archive record from the server: {}", record);
			return false;
		}
		current = record;
		return true;
	}

	/** Forgets the record: the player left the world it belonged to. */
	public static void reset() {
		current = null;
	}

	/** The record for this connection, or {@code null} while none has arrived. */
	@Nullable
	public static String current() {
		return current;
	}
}
