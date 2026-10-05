package dev.alaindustrial.attachment;

import net.minecraft.server.level.ServerPlayer;

/**
 * A loader's read/write bridge to one per-player attachment (MOD-708) — the one accessor both loaders
 * implement, generically, for every entry of {@code ModPlayerAttachments.PLAYER_ATTACHMENTS}. The attachment
 * API is loader-specific, so common code (the stores {@code PlayerStatsStore}, {@code SkillStore}) reads and
 * writes through this seam.
 *
 * <p><b>A read is a read (MOD-483).</b> {@link #get} must not install the default: NeoForge's plain
 * {@code getData} creates and syncs the value, which on a player without a connection throws and otherwise
 * writes state and sends a packet several times a second.
 *
 * @param <T> the attachment value
 */
public interface PlayerAttachmentAccessor<T> {
	/** The player's value, or the attachment's empty value when the player has none. */
	T get(ServerPlayer player);

	/** Replaces the player's value (triggers persistence and the owner sync). */
	void set(ServerPlayer player, T value);
}
