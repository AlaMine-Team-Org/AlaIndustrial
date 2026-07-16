package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.TeleportPoint;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rename a bound station (MOD-093) — the mod's first client-to-server payload.
 *
 * <p>It exists for one reason: a name is a string, and vanilla's container-button packet carries a
 * single int. Every other button on the remote's screen (select, delete, teleport) rides that
 * vanilla packet instead, which needs no registration on either loader — so this payload stays as
 * small as the problem.
 *
 * <p>Nothing here is trusted. The name is length-capped by the codec itself, so an oversized string
 * never even decodes, and {@link #handle} re-reads the real remote from the player's hand and
 * bounds-checks the index against the live list before writing anything.
 */
public record TeleportRenamePayload(int index, String name) implements CustomPacketPayload {
	public static final Type<TeleportRenamePayload> TYPE = new Type<>(Industrialization.id("teleport_rename"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TeleportRenamePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TeleportRenamePayload::index,
			ByteBufCodecs.stringUtf8(TeleportPoint.NAME_WIRE_LIMIT), TeleportRenamePayload::name,
			TeleportRenamePayload::new);

	/**
	 * Apply the rename, server-side.
	 *
	 * <p>Routed through the open menu rather than straight at the item: the menu is what proves the
	 * player has the remote open and in hand, and it owns the validation both this and the screen's
	 * buttons share.
	 */
	public static void handle(TeleportRenamePayload payload, ServerPlayer player) {
		if (player.containerMenu instanceof TeleporterRemoteMenu menu) {
			menu.rename(player, payload.index(), payload.name());
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
