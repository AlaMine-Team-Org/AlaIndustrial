package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.tool.MagnetFilter;
import dev.alaindustrial.menu.MagnetMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * A sample dropped onto a magnet filter cell from a recipe viewer (MOD-592).
 *
 * <p>An ordinary click puts the cursor's item into a cell through a container button, and the server
 * reads the cursor itself. A drag from JEI or REI leaves the cursor empty, so the item has to be named
 * — and only the id travels: a filter cell stores nothing else.
 *
 * <p><b>Nothing is trusted.</b> The server applies it only to the magnet screen the player has open,
 * and only through {@link MagnetMenu#setCell}, which ignores a cell out of range and an id this build
 * does not know. Naming an item puts no item anywhere — a cell is a sample, not storage.
 */
public record MagnetFilterSamplePayload(int cell, String itemId) implements CustomPacketPayload {
	public static final Type<MagnetFilterSamplePayload> TYPE = new Type<>(Industrialization.id("magnet_filter_sample"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MagnetFilterSamplePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MagnetFilterSamplePayload::cell,
			ByteBufCodecs.stringUtf8(256), MagnetFilterSamplePayload::itemId,
			MagnetFilterSamplePayload::new);

	public static void handle(MagnetFilterSamplePayload payload, ServerPlayer player) {
		if (player.containerMenu instanceof MagnetMenu menu && menu.stillValid(player)
				&& payload.cell() >= 0 && payload.cell() < MagnetFilter.CELLS) {
			menu.setCell(payload.cell(), payload.itemId());
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
