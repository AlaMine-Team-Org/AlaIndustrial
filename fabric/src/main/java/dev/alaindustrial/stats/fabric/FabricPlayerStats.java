package dev.alaindustrial.stats.fabric;

import dev.alaindustrial.registry.ModAttachments;
import dev.alaindustrial.registry.ModPlayerAttachments;
import dev.alaindustrial.stats.PlayerModStats;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Typed handle on the Fabric {@link PlayerModStats} player attachment (MOD-133). The attachment is declared
 * once in {@link ModPlayerAttachments#PLAYER_STATS} and created by the {@link ModAttachments} replay
 * (MOD-708) — persistent through the record's {@code CODEC}, copied on death, synced to its owner only;
 * this field only names the result for the code that reads it by type.
 */
public final class FabricPlayerStats {

	/** The player attachment holding one {@link PlayerModStats} per player. */
	public static final AttachmentType<PlayerModStats> TYPE = ModAttachments.type(ModPlayerAttachments.PLAYER_STATS);

	private FabricPlayerStats() {
	}
}
